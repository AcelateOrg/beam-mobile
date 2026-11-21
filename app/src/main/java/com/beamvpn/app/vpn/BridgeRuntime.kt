package com.beamvpn.app.vpn

import android.content.Context
import android.os.Build
import mobile.Mobile
import java.util.Locale

/**
 * Opens beam-bridge's profile store. [Mobile.init] is idempotent, so the UI
 * controller, the VPN service and the quick-settings tile can each call this:
 * the tile may start the service in a process whose UI was never opened.
 */
object BridgeRuntime {
    @Volatile private var routingChecked = false

    fun ensureInit(context: Context) {
        val app = context.applicationContext
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
        Mobile.init(app.filesDir.resolve("beam").absolutePath, version, "Android ${Build.VERSION.RELEASE}", Locale.getDefault().toLanguageTag())
        // Before anything builds a config from the stored settings.
        if (!routingChecked) {
            synchronized(this) {
                if (!routingChecked) {
                    RoutingApi.enforceEasyMode()
                    routingChecked = true
                }
            }
        }
    }

    /** True when a profile is active, i.e. there is something to connect to. */
    fun hasActiveProfile(context: Context): Boolean = runCatching {
        ensureInit(context)
        parseProfiles(Mobile.profiles()).any { it.isActive }
    }.getOrDefault(false)
}
