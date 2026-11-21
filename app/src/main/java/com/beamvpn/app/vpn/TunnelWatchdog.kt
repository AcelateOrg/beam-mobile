package com.beamvpn.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mobile.Mobile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps a live tunnel working without the user. A dead core (consecutive failed requests through the proxy) and a
 * change of the phone's physical network both rebuild the core with [Mobile.reconnect]; the VPN interface stays,
 * so apps never see a disconnect. Recovery backs off exponentially and gives up after a few attempts until the
 * network changes or a check passes again, so a server that is simply down cannot keep the radio busy.
 */
internal class TunnelWatchdog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val autoReconnect: () -> Boolean,
    private val onUnderlyingNetwork: (Network?) -> Unit,
    private val onHealth: (healthy: Boolean, reconnecting: Boolean) -> Unit,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val networkChanged = AtomicBoolean(false)
    private var job: Job? = null
    @Volatile private var network: Network? = null
    @Volatile private var lostSince = 0L
    @Volatile private var lastProbeAt = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = this@TunnelWatchdog.network
            this@TunnelWatchdog.network = network
            onUnderlyingNetwork(network)
            // The first callback only reports the network the tunnel was started on.
            if ((previous != null && previous != network) || lostSince != 0L) {
                lostSince = 0L
                networkChanged.set(true)
                wake.trySend(Unit)
            }
        }

        override fun onLost(network: Network) {
            if (network != this@TunnelWatchdog.network) return
            this@TunnelWatchdog.network = null
            lostSince = SystemClock.elapsedRealtime()
            onUnderlyingNetwork(null)
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val awake = when (intent.action) {
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> !power.isDeviceIdleMode
                else -> true
            }
            if (awake) wake.trySend(Unit)
        }
    }

    fun start() {
        if (job != null) return
        // The app itself is inside its own VPN, so its default network is the tunnel: ask for the best network
        // that is not a VPN, which is the physical one the core's protected sockets leave through.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val handler = Handler(Looper.getMainLooper())
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) connectivity.registerBestMatchingNetworkCallback(request, networkCallback, handler)
            else connectivity.requestNetwork(request, networkCallback, handler)
        }
        ContextCompat.registerReceiver(
            context,
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        job = scope.launch(Dispatchers.IO) { watch() }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        runCatching { context.unregisterReceiver(powerReceiver) }
        network = null
    }

    /** A request through the tunnel just succeeded elsewhere (the UI's ping), so the next routine check can wait. */
    fun reportAlive() {
        lastProbeAt = SystemClock.elapsedRealtime()
    }

    private suspend fun watch() {
        var failures = 0
        var attempts = 0
        while (currentCoroutineContext().isActive) {
            val wait = when {
                attempts >= MAX_ATTEMPTS -> BACKOFF_CAP_MS
                attempts > 0 -> backoff(attempts)
                failures in 1 until FAILURES_BEFORE_RECOVERY -> CONFIRM_MS
                else -> CHECK_INTERVAL_MS
            }
            val woken = withTimeoutOrNull(wait) { wake.receive() } != null

            if (networkChanged.getAndSet(false)) {
                // Handover bursts (Wi-Fi gone, mobile up, Wi-Fi back) settle before the core is rebuilt once.
                delay(SETTLE_MS)
                networkChanged.set(false)
                if (network != null && autoReconnect()) {
                    attempts = 1
                    failures = 0
                    if (recover()) attempts = 0
                    continue
                }
            }

            val now = SystemClock.elapsedRealtime()
            val routine = failures == 0 && attempts == 0
            if (routine && (!power.isInteractive || now - lastProbeAt < if (woken) WAKE_DEBOUNCE_MS else CHECK_INTERVAL_MS)) continue
            if (network == null) continue

            if (probe()) {
                if (failures > 0 || attempts > 0) onHealth(true, false)
                failures = 0
                attempts = 0
                continue
            }
            failures++
            if (failures < FAILURES_BEFORE_RECOVERY) continue
            if (!autoReconnect() || attempts >= MAX_ATTEMPTS) {
                onHealth(false, false)
                continue
            }
            attempts++
            if (recover()) {
                failures = 0
                attempts = 0
            } else if (attempts >= MAX_ATTEMPTS) {
                onHealth(false, false)
            }
        }
    }

    private fun recover(): Boolean {
        onHealth(true, true)
        runCatching { Mobile.reconnect() }
        val ok = probe()
        onHealth(ok, !ok)
        return ok
    }

    private fun probe(): Boolean {
        lastProbeAt = SystemClock.elapsedRealtime()
        return Mobile.proxyDelay(PROBE_TIMEOUT_MS) >= 0
    }

    private fun backoff(attempt: Int): Long = (BACKOFF_BASE_MS shl (attempt - 1).coerceAtMost(8)).coerceAtMost(BACKOFF_CAP_MS)

    private companion object {
        const val CHECK_INTERVAL_MS = 30_000L
        const val CONFIRM_MS = 5_000L
        const val WAKE_DEBOUNCE_MS = 15_000L
        const val SETTLE_MS = 1_500L
        const val PROBE_TIMEOUT_MS = 6_000L
        const val FAILURES_BEFORE_RECOVERY = 2
        const val MAX_ATTEMPTS = 6
        const val BACKOFF_BASE_MS = 2_000L
        const val BACKOFF_CAP_MS = 300_000L
    }
}
