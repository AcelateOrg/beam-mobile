package com.beamvpn.app.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.beamvpn.app.MainActivity
import com.beamvpn.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that switches the VPN. It only drives [BeamVpnService]; the UI process does
 * not have to be open. The app itself is needed when Android has not granted VPN consent yet or
 * when there is no profile to connect to.
 */
class BeamTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch { BeamVpnService.status.collect { render(it) } }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (BeamVpnService.status.value.phase) {
            ConnectionPhase.Connected, ConnectionPhase.Connecting -> BeamVpnService.stop(this)
            else -> connect()
        }
    }

    private fun connect() {
        // The consent dialog and the empty-profile case both need the app's UI.
        if (VpnService.prepare(this) != null || !BridgeRuntime.hasActiveProfile(this)) {
            openApp()
            return
        }
        BeamVpnService.start(this)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(status: TunnelStatus) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.app_name)
        tile.state = when (status.phase) {
            ConnectionPhase.Connected, ConnectionPhase.Connecting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (status.phase) {
                ConnectionPhase.Connected -> getString(R.string.tile_connected)
                ConnectionPhase.Connecting -> getString(R.string.tile_connecting)
                ConnectionPhase.Error -> getString(R.string.tile_error)
                ConnectionPhase.Disconnected -> getString(R.string.tile_off)
            }
        }
        tile.updateTile()
    }
}
