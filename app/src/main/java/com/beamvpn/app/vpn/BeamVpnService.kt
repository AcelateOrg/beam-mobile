package com.beamvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.beamvpn.app.MainActivity
import com.beamvpn.app.R
import com.beamvpn.app.settings.SettingsStore
import com.beamvpn.app.ui.home.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mobile.Mobile
import mobile.SocketProtector

/** What the tunnel is doing, as the service sees it. */
data class TunnelStatus(
    val phase: ConnectionPhase = ConnectionPhase.Disconnected,
    /** elapsedRealtime when the tunnel came up. */
    val since: Long = 0,
    val error: String? = null,
    /** False while requests through the tunnel fail. */
    val healthy: Boolean = true,
    /** The core is being rebuilt on its own (dead tunnel or a new network); the interface stays up. */
    val reconnecting: Boolean = false,
)

/**
 * The VPN itself: Android builds the TUN interface (address, routes, DNS),
 * and beam-bridge runs the beam-tun stack and Xray on its descriptor. Every
 * socket the core opens is passed through [protect] so it leaves through the
 * real network instead of looping back into the tunnel.
 */
class BeamVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statsJob: Job? = null
    private var watchdog: TunnelWatchdog? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTunnel()
            else -> startTunnel()
        }
        // With auto-reconnect a tunnel killed along with the process comes back; a stopped one does not,
        // since the service stops itself on disconnect and only a started service is restarted.
        return if (SettingsStore.get(this).settings.value.autoReconnect) START_STICKY else START_NOT_STICKY
    }

    private fun startTunnel() {
        if (_status.value.phase == ConnectionPhase.Connecting || _status.value.phase == ConnectionPhase.Connected) return
        _status.value = TunnelStatus(ConnectionPhase.Connecting)
        goForeground(getString(R.string.vpn_connecting), null)

        scope.launch {
            val result = runCatching {
                // The quick-settings tile can start the service before the UI ever opened the bridge.
                BridgeRuntime.ensureInit(this@BeamVpnService)
                Mobile.setProcessResolver(AppOwners(this@BeamVpnService))
                Mobile.setIPv6(SettingsStore.get(this@BeamVpnService).settings.value.ipv6)
                Mobile.setProtector(object : SocketProtector {
                    override fun protect(fd: Long): Boolean = this@BeamVpnService.protect(fd.toInt())
                })
                // Easy rules that only name apps are applied by Android itself: those apps never enter the tunnel.
                val routing = runCatching { RoutingApi.state(this@BeamVpnService) }.getOrNull()
                val pfd = buildInterface(routing) ?: error("Android не дал создать VPN-интерфейс. Проверьте, что разрешение VPN выдано BEAM.")
                // detachFd hands ownership to Go: the bridge closes it on disconnect.
                Mobile.connect(pfd.detachFd().toLong(), MTU.toLong())
            }
            result.onSuccess {
                _status.value = TunnelStatus(ConnectionPhase.Connected, since = SystemClock.elapsedRealtime())
                startStatsLoop()
                startWatchdog()
            }.onFailure { e ->
                runCatching { Mobile.disconnect() }
                _status.value = TunnelStatus(ConnectionPhase.Error, error = e.message ?: e::class.java.simpleName)
                stopForegroundAndSelf()
            }
        }
    }

    private fun buildInterface(routing: RoutingState?) = Builder()
        .apply { applySplitTunnel(routing) }
        .setSession(getString(R.string.app_name))
        .setMtu(MTU)
        .addAddress(TUN_IPV4, 30)
        .addRoute("0.0.0.0", 0)
        .addDnsServer(Mobile.TunDNS)
        .apply {
            // IPv6 is routed in and dropped by the stack unless the user allowed it,
            // so apps fall back to IPv4 through the tunnel instead of leaking around it.
            // Always captured, so IPv6 never leaks around the tunnel. With "IPv6 через VPN" off the stack drops it
            // and apps fall back to IPv4.
            addAddress("fdfe:dcba:9876::1", 126)
            addRoute("::", 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
            setConfigureIntent(openAppIntent())
        }
        .establish()

    /**
     * System-level split tunneling for Easy rules, so the cheapest path is taken:
     * "Мимо VPN" apps are excluded from the interface (their traffic never reaches the core), and "Через VPN"
     * with only apps listed makes the interface carry just those apps. Domain and IP rules still run inside the
     * core, so an "include" list with domains or IPs keeps every app in the tunnel and lets the core decide.
     */
    private fun Builder.applySplitTunnel(routing: RoutingState?) {
        val split = routing?.split ?: return
        if (routing.mode != "easy" || !split.enabled || split.apps.isEmpty()) return
        val apps = split.apps.filter { it != packageName }
        when {
            split.mode == "exclude" -> apps.forEach { runCatching { addDisallowedApplication(it) } }
            split.domains.isEmpty() && split.ips.isEmpty() -> {
                var any = false
                apps.forEach { pkg -> if (runCatching { addAllowedApplication(pkg) }.isSuccess) any = true }
                // None of the listed apps is installed: an empty allow list would mean "everyone", keep it closed instead.
                if (!any) runCatching { addAllowedApplication(packageName) }
            }
        }
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                val text = "↓ ${formatBytes(Mobile.trafficDown())}  ↑ ${formatBytes(Mobile.trafficUp())}"
                val title = if (_status.value.reconnecting) R.string.vpn_reconnecting else R.string.vpn_connected
                goForeground(getString(title), text)
                delay(2_000)
            }
        }
    }

    private fun startWatchdog() {
        watchdog?.stop()
        watchdog = TunnelWatchdog(
            context = this,
            scope = scope,
            autoReconnect = { SettingsStore.get(this).settings.value.autoReconnect },
            onUnderlyingNetwork = { network -> runCatching { setUnderlyingNetworks(network?.let { arrayOf(it) }) } },
            onHealth = { healthy, reconnecting ->
                _status.update { if (it.phase == ConnectionPhase.Connected) it.copy(healthy = healthy, reconnecting = reconnecting) else it }
            },
        ).also { it.start() }
    }

    private fun stopWatchdog() {
        watchdog?.stop()
        watchdog = null
    }

    private fun stopTunnel() {
        // Stopped first, so a disconnect the user asked for is never followed by a recovery attempt.
        stopWatchdog()
        statsJob?.cancel()
        scope.launch {
            runCatching { Mobile.disconnect() }
            _status.value = TunnelStatus(ConnectionPhase.Disconnected)
            stopForegroundAndSelf()
        }
    }

    override fun onRevoke() {
        // Another VPN took over or the user revoked BEAM in system settings.
        stopTunnel()
    }

    override fun onDestroy() {
        running = null
        stopWatchdog()
        statsJob?.cancel()
        runCatching { Mobile.disconnect() }
        if (_status.value.phase != ConnectionPhase.Error) _status.value = TunnelStatus(ConnectionPhase.Disconnected)
        scope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(title: String, text: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.vpn_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BeamVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_beam)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.vpn_disconnect), stop)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val ACTION_STOP = "com.beamvpn.app.STOP"
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 1
        /** beam-tun's default MTU: TCP is re-segmented by the core, so a large tunnel MTU just means fewer packets. */
        private const val MTU = 9000
        private val TUN_IPV4 = Mobile.TunAddress.substringBefore('/')

        private val _status = MutableStateFlow(TunnelStatus())
        val status: StateFlow<TunnelStatus> = _status.asStateFlow()

        /** Call after VpnService.prepare() returned null or its consent activity said OK. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BeamVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BeamVpnService::class.java).setAction(ACTION_STOP))
        }

        /** A request through the tunnel succeeded elsewhere, so the watchdog's next routine check can wait. */
        fun reportAlive() {
            running?.watchdog?.reportAlive()
        }

        @Volatile private var running: BeamVpnService? = null
    }
}
