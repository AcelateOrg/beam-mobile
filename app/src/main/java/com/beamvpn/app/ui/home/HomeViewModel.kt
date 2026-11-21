package com.beamvpn.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beamvpn.app.settings.DnsPreset
import com.beamvpn.app.settings.FragmentCustom
import com.beamvpn.app.settings.Fragmentation
import com.beamvpn.app.settings.PingMethod
import com.beamvpn.app.settings.SettingsStore
import com.beamvpn.app.settings.effectiveHwid
import com.beamvpn.app.vpn.BridgeVpnController
import com.beamvpn.app.vpn.VpnController
import com.beamvpn.app.vpn.VpnUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class ConnectionOptions(
    val dns: String,
    val dnsPrimary: String,
    val dnsSecondary: String,
    val profileDns: Boolean,
    val fragmentation: String,
    val fragmentCustom: FragmentCustom,
    val mux: Boolean,
    val autoRefresh: Boolean,
    val serverCheck: PingMethod,
    val connectionPing: PingMethod,
) {
    /** What goes into the Xray config; a change rebuilds a live connection. */
    val coreKey get() = listOf(dns, if (dns == DNS_CUSTOM) dnsPrimary to dnsSecondary else null, profileDns, fragmentation, if (fragmentation == CUSTOM) fragmentCustom else null, mux)

    fun json(): String = org.json.JSONObject()
        .put("dns", dns)
        .apply { if (dns == DNS_CUSTOM) put("dnsPrimary", dnsPrimary).put("dnsSecondary", dnsSecondary) }
        .put("profileDns", profileDns)
        .put("fragmentation", fragmentation)
        .apply { if (fragmentation == CUSTOM) put("fragmentCustom", fragmentCustom.toJson()) }
        .put("mux", mux)
        .put("autoRefresh", autoRefresh)
        // HTTP server checks are real requests through each server, falling back to TCP when one cannot be made.
        .put("nodePing", if (serverCheck == PingMethod.Tcp) "tcp" else "auto")
        .put("connectionPing", if (connectionPing == PingMethod.Tcp) "tcp" else "http")
        .toString()

    companion object {
        val CUSTOM = Fragmentation.Custom.name.lowercase()
        val DNS_CUSTOM = DnsPreset.Custom.name.lowercase()
    }
}

/** Activity-scoped state shared by every tab. */
class BeamViewModel(app: Application) : AndroidViewModel(app) {
    private val vpn: VpnController = BridgeVpnController(app, viewModelScope)

    val state: StateFlow<VpnUiState> = vpn.state

    init {
        // "Соединение" and "Приложение" options live in beam-bridge's settings, where the config builder reads them.
        // DNS, fragmentation and Mux are part of the core config, so changing them rebuilds a live connection.
        viewModelScope.launch {
            val store = SettingsStore.get(app)
            var lastCore: List<Any?>? = null
            store.settings.map { s ->
                ConnectionOptions(
                    s.dns.name.lowercase(), s.dnsPrimary.trim(), s.dnsSecondary.trim(), s.profileDns, s.fragmentation.name.lowercase(), s.fragmentCustom,
                    s.mux, s.autoUpdateSubscriptions, s.serverCheck, s.connectionPing,
                )
            }.distinctUntilChanged().collect { o ->
                val rebuild = lastCore != null && lastCore != o.coreKey
                lastCore = o.coreKey
                val stored = FragmentCustom.parse(vpn.applyConnectionOptions(o.json(), rebuild))
                // Keep the custom fields as the bridge normalized them; the core already runs with these, so no rebuild.
                if (o.fragmentation == ConnectionOptions.CUSTOM && stored != null && stored != o.fragmentCustom) {
                    lastCore = o.copy(fragmentCustom = stored).coreKey
                    store.update { if (it.fragmentCustom == o.fragmentCustom) it.copy(fragmentCustom = stored) else it }
                }
            }
        }
        // Subscription requests carry X-Hwid only while "Отправлять HWID" is on.
        viewModelScope.launch {
            SettingsStore.get(app).settings.map { it.sendHwid to app.effectiveHwid(it) }.distinctUntilChanged().collect { (enabled, value) ->
                vpn.setHwid(enabled, value)
            }
        }
    }

    fun connect() = vpn.connect()
    fun disconnect() = vpn.disconnect()
    suspend fun importProfile(input: String): String? = vpn.importProfile(input)
    suspend fun importBeam(data: ByteArray): String? = vpn.importBeam(data)
    fun selectProfile(id: String) = vpn.selectProfile(id)
    fun refreshProfile(id: String) = vpn.refreshProfile(id)
    fun deleteProfile(id: String) = vpn.deleteProfile(id)
    fun selectNode(id: String) {
        state.value.activeProfile?.let { vpn.selectNode(it.id, id) }
    }
    fun selectNode(profileId: String, nodeId: String) = vpn.selectNode(profileId, nodeId)
    fun ping(groupId: String?) {
        state.value.activeProfile?.let { vpn.ping(it.id, groupId) }
    }
    fun ping(profileId: String, groupId: String?) = vpn.ping(profileId, groupId)
    fun consumeMessage() = vpn.consumeMessage()
    fun applyRouting() = vpn.applyRouting()
    suspend fun exportBeam(id: String) = vpn.exportBeam(id)
    suspend fun exportLinks(id: String) = vpn.exportLinks(id)
    fun setAutoRefresh(id: String, enabled: Boolean) = vpn.setAutoRefresh(id, enabled)
    fun setUpdateInterval(id: String, hours: Int) = vpn.setUpdateInterval(id, hours)
    suspend fun nodeConfig(profileId: String, nodeId: String) = vpn.nodeConfig(profileId, nodeId)
    suspend fun saveNodeConfig(profileId: String, nodeId: String, name: String, overrides: Map<String, Any?>, pinned: Boolean) =
        vpn.saveNodeConfig(profileId, nodeId, name, overrides, pinned)
}
