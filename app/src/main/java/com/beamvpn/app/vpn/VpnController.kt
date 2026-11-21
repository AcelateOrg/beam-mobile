package com.beamvpn.app.vpn

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.beamvpn.app.settings.PingMethod
import com.beamvpn.app.settings.SettingsStore
import mobile.Mobile
import mobile.PingListener
import org.json.JSONArray

enum class ConnectionPhase { Disconnected, Connecting, Connected, Error }

data class TrafficStats(
    val pingMs: Long = 0,
    val durationSec: Long = 0,
    val downloadBytes: Long = 0,
    val uploadBytes: Long = 0,
)

data class ProfileSummary(val name: String, val unlimited: Boolean)

data class ServerSummary(val name: String, val countryCode: String?)

enum class SwitchPhase { Idle, Switching, Switched }

/** A server change on a live connection: [from] is where the tunnel was, the target is the selected server. */
data class ServerSwitch(val phase: SwitchPhase = SwitchPhase.Idle, val from: ServerSummary? = null)

data class VpnUiState(
    /** False until beam-bridge has opened the profile store. */
    val ready: Boolean = false,
    val phase: ConnectionPhase = ConnectionPhase.Disconnected,
    val healthy: Boolean = true,
    /** The tunnel is rebuilding its core on its own; the VPN stays connected meanwhile. */
    val reconnecting: Boolean = false,
    val error: String? = null,
    val stats: TrafficStats = TrafficStats(),
    val profiles: List<Profile> = emptyList(),
    /** Servers of every profile, by profile id. */
    val nodes: Map<String, ProfileNodes> = emptyMap(),
    /** Ping results by profile id, then node id. */
    val profilePings: Map<String, Map<String, PingResult>> = emptyMap(),
    /** [pingKey]s of the groups being pinged. */
    val pinging: Set<String> = emptySet(),
    /** Servers whose ping is still being measured, by profile id. */
    val pendingPings: Map<String, Set<String>> = emptyMap(),
    /** Profile ids being refreshed. */
    val refreshing: Set<String> = emptySet(),
    val switch: ServerSwitch = ServerSwitch(),
    /** One-off message for a snackbar (errors from the bridge). */
    val message: String? = null,
) {
    val activeProfile: Profile? get() = profiles.firstOrNull { it.isActive }
    val groups: List<NodeGroup> get() = activeProfile?.let { nodes[it.id] }?.groups.orEmpty()
    val selectedNodeId: String? get() = activeProfile?.let { nodes[it.id] }?.selectedId
    val pings: Map<String, PingResult> get() = activeProfile?.let { profilePings[it.id] }.orEmpty()
    val selectedNode: Node? get() = groups.asSequence().flatMap { it.nodes }.firstOrNull { it.id == selectedNodeId }
    val announce: String? get() = activeProfile?.announce?.takeIf { it.isNotBlank() }

    val profile: ProfileSummary? get() = activeProfile?.let { ProfileSummary(it.name, it.unlimited) }
    val server: ServerSummary? get() = selectedNode?.parsed?.let { ServerSummary(it.displayName, it.countryCode) }

    fun isPingPending(profileId: String, nodeId: String): Boolean = pendingPings[profileId]?.contains(nodeId) == true

    /** Whether [groupId] of the profile is being pinged; null asks about "ping all". */
    fun isPinging(profileId: String?, groupId: String? = null): Boolean {
        if (profileId == null) return false
        return pingKey(profileId, null) in pinging || (groupId != null && pingKey(profileId, groupId) in pinging)
    }

    companion object {
        fun pingKey(profileId: String, groupId: String?) = "$profileId/${groupId ?: "*"}"
    }
}

/** What the UI needs from the VPN layer. */
interface VpnController {
    val state: StateFlow<VpnUiState>
    /** Starts the tunnel. VPN consent (VpnService.prepare) must already be granted. */
    fun connect()
    fun disconnect()
    /** Imports a subscription URL, a protocol link or a .beam document. Returns an error message or null. */
    suspend fun importProfile(input: String): String?
    /** Imports the bytes of a .beam file. Returns an error message or null. */
    suspend fun importBeam(data: ByteArray): String?
    fun selectProfile(id: String)
    fun refreshProfile(id: String)
    fun deleteProfile(id: String)
    /** Selects a server of any profile; a server of another profile makes that profile active. */
    fun selectNode(profileId: String, nodeId: String)
    /** Pings one group of a profile, or every group when [groupId] is null. */
    fun ping(profileId: String, groupId: String?)
    fun setHwid(enabled: Boolean, value: String)
    fun consumeMessage()
    /** Rebuilds the core of a live connection after routing rules changed; does nothing when disconnected. */
    fun applyRouting()
    /**
     * Stores DNS, fragmentation, Mux, refresh and ping options; [rebuild] restarts a live core to apply them.
     * Returns the fragment parameters as the bridge normalized them (FragmentSettings JSON), or null on failure.
     */
    suspend fun applyConnectionOptions(json: String, rebuild: Boolean): String?
    suspend fun exportBeam(id: String): ByteArray
    /** The profile's original server links as name to link. */
    suspend fun exportLinks(id: String): List<Pair<String, String>>
    fun setAutoRefresh(id: String, enabled: Boolean)
    fun setUpdateInterval(id: String, hours: Int)
    /** Throws when the server is gone. */
    suspend fun nodeConfig(profileId: String, nodeId: String): NodeConfig
    /**
     * Stores the user's edits of a server and, when it belongs to the profile of a live tunnel, rebuilds the core
     * so they apply at once. Returns an error message or null.
     */
    suspend fun saveNodeConfig(profileId: String, nodeId: String, name: String, overrides: Map<String, Any?>, pinned: Boolean): String?
}

/**
 * Profiles, subscriptions and nodes come from beam-bridge (gomobile AAR, Go
 * package beam-bridge/mobile). Every bridge call does disk or network I/O, so
 * it runs on [Dispatchers.IO] behind one mutex. The tunnel itself lives in
 * [BeamVpnService]; this class mirrors its status into the UI state.
 */
class BridgeVpnController(context: Context, private val scope: CoroutineScope) : VpnController {
    private val app = context.applicationContext
    private val _state = MutableStateFlow(VpnUiState())
    override val state: StateFlow<VpnUiState> = _state.asStateFlow()
    private val lock = Mutex()

    init {
        bridge {
            BridgeRuntime.ensureInit(app)
            _state.update { it.copy(ready = true) }
            reload()
        }
        observeTunnel()
    }

    override fun connect() {
        if (_state.value.activeProfile == null) {
            _state.update { it.copy(message = "Сначала добавьте профиль") }
            return
        }
        // Queue behind pending bridge writes (a server picked a moment ago) so the tunnel starts on the new node.
        scope.launch(Dispatchers.IO) {
            lock.withLock { }
            BeamVpnService.start(app)
        }
    }

    override fun disconnect() = BeamVpnService.stop(app)

    /**
     * Mirrors the service's tunnel status and, while connected, samples traffic and the connection ping.
     * Health comes from the service's watchdog, which also runs while no screen is open.
     */
    private fun observeTunnel() {
        scope.launch {
            BeamVpnService.status.collect { tunnel ->
                val connected = tunnel.phase == ConnectionPhase.Connected
                _state.update {
                    it.copy(
                        phase = tunnel.phase,
                        error = tunnel.error,
                        healthy = !connected || tunnel.healthy,
                        reconnecting = connected && tunnel.reconnecting,
                        stats = if (connected) it.stats else TrafficStats(),
                        switch = if (connected) it.switch else ServerSwitch(),
                    )
                }
            }
        }
        scope.launch {
            BeamVpnService.status.map { it.phase to it.since }.distinctUntilChanged().collectLatest { (phase, since) ->
                if (phase != ConnectionPhase.Connected) return@collectLatest
                coroutineScope {
                    launch(Dispatchers.IO) {
                        while (isActive) {
                            val down = Mobile.trafficDown()
                            val up = Mobile.trafficUp()
                            val seconds = (SystemClock.elapsedRealtime() - since) / 1000
                            _state.update { s -> s.copy(stats = s.stats.copy(durationSec = seconds, downloadBytes = down, uploadBytes = up)) }
                            delay(1_000)
                        }
                    }
                    launch(Dispatchers.IO) {
                        while (isActive) {
                            if (!BeamVpnService.status.value.reconnecting) {
                                val ms = Mobile.delay(5_000)
                                _state.update { s -> s.copy(stats = s.stats.copy(pingMs = ms)) }
                                if (ms >= 0 && SettingsStore.get(app).settings.value.connectionPing == PingMethod.Http) BeamVpnService.reportAlive()
                            }
                            delay(15_000)
                        }
                    }
                }
            }
        }
    }

    override suspend fun importProfile(input: String): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                Mobile.importProfile(input)
                reload()
            }.exceptionOrNull()?.let { humanize(it) }
        }
    }

    override suspend fun importBeam(data: ByteArray): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                Mobile.importBeam(data)
                reload()
            }.exceptionOrNull()?.let { humanize(it) }
        }
    }

    override fun selectProfile(id: String) {
        val live = beginSwitch()
        bridge {
            abortSwitchOnError(live) {
                Mobile.setActiveProfile(id)
                reload()
                if (live) switchLive()
            }
        }
    }

    override fun refreshProfile(id: String) {
        _state.update { it.copy(refreshing = it.refreshing + id) }
        bridge(onDone = { _state.update { it.copy(refreshing = it.refreshing - id) } }) {
            Mobile.refreshProfile(id)
            reload()
        }
    }

    override fun deleteProfile(id: String) = bridge {
        Mobile.deleteProfile(id)
        reload()
    }

    override fun selectNode(profileId: String, nodeId: String) {
        val switchesProfile = _state.value.activeProfile?.id != profileId
        val live = beginSwitch()
        _state.update { s ->
            s.copy(
                nodes = s.nodes[profileId]?.let { s.nodes + (profileId to it.copy(selectedId = nodeId)) } ?: s.nodes,
                profiles = if (switchesProfile) s.profiles.map { it.copy(isActive = it.id == profileId) } else s.profiles,
            )
        }
        bridge {
            abortSwitchOnError(live) {
                try {
                    Mobile.selectNode(profileId, nodeId)
                    if (switchesProfile) {
                        Mobile.setActiveProfile(profileId)
                        reload()
                    }
                } catch (e: Throwable) {
                    // Put back what the bridge actually has instead of the optimistic choice.
                    runCatching { reload() }
                    throw e
                }
                if (live) switchLive()
            }
        }
    }

    private var switchJob: Job? = null

    private inline fun abortSwitchOnError(live: Boolean, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (live) _state.update { it.copy(switch = ServerSwitch()) }
            throw e
        }
    }

    /**
     * On a live connection the next profile or server is applied without dropping the tunnel (the desktop's
     * seamless switch): returns true and shows the hop from the current server. Otherwise only the choice is saved.
     */
    private fun beginSwitch(): Boolean {
        val s = _state.value
        if (s.phase != ConnectionPhase.Connected) return false
        switchJob?.cancel()
        // A second pick during a switch keeps the original starting point.
        val from = if (s.switch.phase == SwitchPhase.Switching) s.switch.from else s.server
        _state.update { it.copy(switch = ServerSwitch(SwitchPhase.Switching, from)) }
        return true
    }

    /** Rebuilds the core for the saved choice (under [lock], on the IO thread) and winds the hop animation down. */
    private fun switchLive() {
        val started = SystemClock.elapsedRealtime()
        val failure = runCatching { Mobile.reconnect() }.exceptionOrNull()
        if (failure != null) {
            // The old core is gone and the new one did not start: there is no tunnel worth keeping.
            _state.update { it.copy(switch = ServerSwitch(), message = humanize(failure)) }
            BeamVpnService.stop(app)
            return
        }
        switchJob = scope.launch {
            delay((MIN_SWITCH_MS - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(0))
            _state.update { if (it.switch.phase == SwitchPhase.Switching) it.copy(switch = it.switch.copy(phase = SwitchPhase.Switched)) else it }
            delay(SWITCHED_MS)
            _state.update { if (it.switch.phase == SwitchPhase.Switched) it.copy(switch = ServerSwitch()) else it }
        }
    }

    override fun ping(profileId: String, groupId: String?) {
        val s = _state.value
        val key = VpnUiState.pingKey(profileId, groupId)
        if (key in s.pinging) return
        val ids = s.nodes[profileId]?.groups.orEmpty().filter { groupId == null || it.id == groupId }.flatMap { g -> g.nodes.map { it.id } }
        if (ids.isEmpty()) return
        _state.update {
            it.copy(
                pinging = it.pinging + key,
                pendingPings = it.pendingPings + (profileId to it.pendingPings[profileId].orEmpty() + ids),
            )
        }
        // Each server's row updates the moment its own result arrives, as desktop's ping events do.
        val listener = object : PingListener {
            override fun onPing(profileID: String, nodeID: String, latency: Long, status: String) {
                _state.update { it.withPings(profileID, mapOf(nodeID to pingResult(status, latency))) }
            }
        }
        // Pings do not touch the store, so they run outside the mutex.
        scope.launch(Dispatchers.IO) {
            val results = runCatching { parsePings(Mobile.pingNodesStream(profileId, JSONArray(ids).toString(), 5000, listener)) }
            _state.update { st ->
                val next = st.withPings(profileId, results.getOrDefault(emptyMap()))
                val pending = next.pendingPings[profileId].orEmpty() - ids.toSet()
                next.copy(
                    pinging = next.pinging - key,
                    pendingPings = if (pending.isEmpty()) next.pendingPings - profileId else next.pendingPings + (profileId to pending),
                    message = results.exceptionOrNull()?.let(::humanize) ?: next.message,
                )
            }
        }
    }

    private fun VpnUiState.withPings(profileId: String, results: Map<String, PingResult>): VpnUiState {
        if (results.isEmpty()) return this
        val pending = pendingPings[profileId].orEmpty() - results.keys
        return copy(
            profilePings = profilePings + (profileId to profilePings[profileId].orEmpty() + results),
            pendingPings = if (pending.isEmpty()) pendingPings - profileId else pendingPings + (profileId to pending),
        )
    }

    override fun setHwid(enabled: Boolean, value: String) = bridge { Mobile.setHwid(enabled, value) }

    override fun consumeMessage() = _state.update { it.copy(message = null) }

    override suspend fun applyConnectionOptions(json: String, rebuild: Boolean): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            val stored = runCatching {
                Mobile.setConnectionOptions(json)
                Mobile.fragmentSettings()
            }.onFailure { e -> _state.update { it.copy(message = humanize(e)) } }
            if (stored.isSuccess && rebuild && _state.value.phase == ConnectionPhase.Connected) {
                runCatching { Mobile.reconnect() }.onFailure { e ->
                    _state.update { it.copy(message = humanize(e)) }
                    BeamVpnService.stop(app)
                }
            }
            stored.getOrNull()
        }
    }

    override suspend fun exportBeam(id: String): ByteArray = withContext(Dispatchers.IO) { Mobile.exportBeam(id) }

    override suspend fun exportLinks(id: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val array = JSONArray(Mobile.exportLinks(id))
        List(array.length()) { i -> array.getJSONObject(i).let { it.optString("name") to it.optString("link") } }
    }

    override fun setAutoRefresh(id: String, enabled: Boolean) = bridge {
        Mobile.setAutoRefresh(id, !enabled)
        reload()
    }

    override fun setUpdateInterval(id: String, hours: Int) = bridge {
        Mobile.setUpdateInterval(id, hours.toLong())
        reload()
    }

    override suspend fun nodeConfig(profileId: String, nodeId: String): NodeConfig = withContext(Dispatchers.IO) {
        parseNodeConfig(Mobile.nodeConfig(profileId, nodeId))
    }

    override suspend fun saveNodeConfig(profileId: String, nodeId: String, name: String, overrides: Map<String, Any?>, pinned: Boolean): String? =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val failure = runCatching {
                    Mobile.updateNodeConfig(profileId, nodeId, name, jsonValue(overrides).toString(), pinned)
                    reload()
                }.exceptionOrNull()
                if (failure != null) return@withLock humanize(failure)
                val s = _state.value
                if (s.phase == ConnectionPhase.Connected && s.activeProfile?.id == profileId) {
                    runCatching { Mobile.reconnect() }.onFailure { e ->
                        _state.update { it.copy(message = humanize(e)) }
                        BeamVpnService.stop(app)
                    }
                }
                null
            }
        }

    override fun applyRouting() {
        if (_state.value.phase != ConnectionPhase.Connected) return
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                val failure = runCatching { Mobile.reconnect() }.exceptionOrNull() ?: return@withLock
                // The rules did not build into a config (a contradiction, a missing server): there is no core left.
                _state.update { it.copy(message = humanize(failure)) }
                BeamVpnService.stop(app)
            }
        }
    }

    /** Reads profiles and every profile's nodes back from the bridge. Call under [lock]. */
    private fun reload() {
        val profiles = parseProfiles(Mobile.profiles())
        // The bridge keeps the unsupported servers of the last profile it listed, so the active profile goes last.
        val nodes = profiles.sortedBy { it.isActive }
            .mapNotNull { p -> runCatching { p.id to parseNodes(Mobile.profileNodes(p.id)) }.getOrNull() }
            .toMap()
        val ids = profiles.mapTo(HashSet()) { it.id }
        _state.update {
            it.copy(
                profiles = profiles,
                nodes = nodes,
                profilePings = it.profilePings.filterKeys { id -> id in ids },
            )
        }
    }

    private companion object {
        /** A switch is shown for at least this long, otherwise it does not read. */
        const val MIN_SWITCH_MS = 900L
        /** How long "server changed" stays on screen. */
        const val SWITCHED_MS = 2600L
    }

    private fun bridge(onDone: () -> Unit = {}, block: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                runCatching(block).exceptionOrNull()?.let { e -> _state.update { it.copy(message = humanize(e)) } }
            }
            onDone()
        }
    }

    private fun humanize(e: Throwable): String {
        val raw = e.message.orEmpty().ifBlank { e::class.java.simpleName }
        return when {
            "no such host" in raw || "dial tcp" in raw -> "Не удалось связаться с сервером подписки"
            "unsupported" in raw.lowercase() -> "Формат не поддерживается: $raw"
            "has no servers" in raw -> "В профиле нет серверов"
            else -> raw
        }
    }
}
