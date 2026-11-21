package com.beamvpn.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The "back" of whatever nested screen is open (a settings section, a routing list), for the floating bar's back
 * button. Screens register while they are deeper than their root; the latest registration is the innermost one.
 */
@Stable
class NavBack {
    private val handlers = mutableStateListOf<() -> Unit>()

    /** The innermost screen's back, or null on a root screen. */
    val current: (() -> Unit)? get() = handlers.lastOrNull()

    internal fun add(handler: () -> Unit) = handlers.add(handler)
    internal fun remove(handler: () -> Unit) = handlers.remove(handler)
}

val LocalNavBack = staticCompositionLocalOf { NavBack() }

/** Offers [onBack] to the floating bar while [enabled]; the system back is still the screen's own BackHandler. */
@Composable
fun RegisterNavBack(enabled: Boolean, onBack: () -> Unit) {
    val nav = LocalNavBack.current
    val latest by rememberUpdatedState(onBack)
    DisposableEffect(nav, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val handler: () -> Unit = { latest() }
        nav.add(handler)
        onDispose { nav.remove(handler) }
    }
}

/**
 * How much of the bottom of the page the floating bar covers (zero with the classic bar). Scrolling screens add
 * it to their bottom padding, so their content runs under the islands yet its end can still be scrolled clear.
 */
val LocalBottomInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Bottom room for the floating bar, for a scrolling column's last child or padding. */
@Composable
fun Modifier.bottomBarSpace(): Modifier = this.then(Modifier.padding(bottom = LocalBottomInset.current))
