package com.beamvpn.app

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beamvpn.app.settings.SettingsStore
import com.beamvpn.app.ui.BeamApp
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.isLightTheme

class MainActivity : ComponentActivity() {
    /** A link handed to the app (VIEW or SEND) waiting for the import sheet. */
    private var sharedLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) sharedLink = intent.importLink()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val settings by SettingsStore.get(this).settings.collectAsStateWithLifecycle()
            // Light themes need dark system-bar icons.
            val light = isLightTheme(settings)
            DisposableEffect(light) {
                enableEdgeToEdge(
                    statusBarStyle = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT),
                )
                onDispose { }
            }
            BeamTheme(settings = settings) {
                BeamApp(sharedLink = sharedLink, onSharedLinkHandled = { sharedLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.importLink()?.let { sharedLink = it }
    }

    private fun Intent.importLink(): String? = when (action) {
        Intent.ACTION_VIEW -> dataString
        Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }
}
