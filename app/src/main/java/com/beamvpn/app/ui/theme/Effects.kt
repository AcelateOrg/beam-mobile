package com.beamvpn.app.ui.theme

import android.app.ActivityManager
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Why the lighter effects are on without the user asking, for the settings caption. */
enum class EffectsReason(val title: String) {
    LowRam("слабое устройство"),
    AnimationsOff("анимации отключены в системе"),
    PowerSave("режим энергосбережения"),
}

/**
 * How much the decorative animations may cost. [reduced]: no blurred shadows, fewer glow layers, no particles and
 * a plainer pulse. [animationsOff]: the system's animator scale is 0, so nothing loops at all.
 */
@Immutable
data class Effects(
    val reduced: Boolean = false,
    val animationsOff: Boolean = false,
    /** Set when [reduced] came from the system rather than the user's switch. */
    val autoReason: EffectsReason? = null,
)

internal val LocalEffects = staticCompositionLocalOf { Effects() }

/** The user's switch ([manual]) or any of: a low-RAM device, system animations off, battery saver. */
@Composable
internal fun rememberEffects(manual: Boolean): Effects {
    val context = LocalContext.current.applicationContext
    val lowRam = remember { context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true }
    val animatorScale = rememberAnimatorScale(context)
    val powerSave = rememberPowerSave(context)
    val reason = when {
        lowRam -> EffectsReason.LowRam
        animatorScale == 0f -> EffectsReason.AnimationsOff
        powerSave -> EffectsReason.PowerSave
        else -> null
    }
    return remember(manual, reason, animatorScale) {
        Effects(reduced = manual || reason != null, animationsOff = animatorScale == 0f, autoReason = reason)
    }
}

private fun readAnimatorScale(context: Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

@Composable
private fun rememberAnimatorScale(context: Context): Float {
    var scale by remember { mutableStateOf(readAnimatorScale(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(context)
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return scale
}

@Composable
private fun rememberPowerSave(context: Context): Boolean {
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var on by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                on = power?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        on = power?.isPowerSaveMode == true
        onDispose { context.unregisterReceiver(receiver) }
    }
    return on
}

/**
 * A shadow colour in the theme's own hue: the page background pushed towards black (less on a light page), so
 * shadows read as depth instead of a dirty black halo on light and coloured themes.
 */
fun themedShadow(background: Color, strength: Float = 1f): Color {
    val light = background.luminance() > 0.5f
    val base = lerp(background, Color.Black, if (light) 0.35f else 0.6f)
    return base.copy(alpha = ((if (light) 0.22f else 0.5f) * strength).coerceIn(0f, 1f))
}
