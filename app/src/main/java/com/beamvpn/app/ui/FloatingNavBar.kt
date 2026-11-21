package com.beamvpn.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme

private val IslandHeight = 64.dp
private val ItemHeight = 52.dp
private val PillPadding = 6.dp

// Calm motion that matches the rest of the app: Material's emphasized curve, no overshoot or bounce. The
// indicator uses a critically damped spring because its target moves while the labels open and close.
private val Emphasized = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val MOTION_MS = 300
private val Slide = spring<Float>(dampingRatio = 1f, stiffness = 650f)
private val Press = tween<Float>(140)

/**
 * The floating ("островки") bottom bar: the tabs on one pill island with a lit indicator that glides from tab to
 * tab, the chosen tab opening up to show its name, and, while a nested screen is open, a round back island that
 * pops out to the left of it.
 */
@Composable
fun FloatingNavBar(tabs: List<BeamTab>, selected: BeamTab, onSelect: (BeamTab) -> Unit, onBack: (() -> Unit)?) {
    val haptic = LocalHapticFeedback.current
    // Kept while the back island animates away, so a tap during its exit still lands somewhere.
    var lastBack by remember { mutableStateOf(onBack) }
    if (onBack != null) lastBack = onBack

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = onBack != null,
                // It slides out from behind the tabs island while the bar makes room, as if it had been tucked
                // under it, and slides back in the same way: one piece of the bar, not a button popping up.
                enter = expandHorizontally(tween(380, easing = Emphasized), expandFrom = Alignment.End, clip = false) +
                    androidx.compose.animation.slideInHorizontally(tween(380, easing = Emphasized)) { it } +
                    fadeIn(tween(260, delayMillis = 40)),
                exit = shrinkHorizontally(tween(300, easing = Emphasized), shrinkTowards = Alignment.End, clip = false) +
                    androidx.compose.animation.slideOutHorizontally(tween(300, easing = Emphasized)) { it } +
                    fadeOut(tween(180)),
            ) {
                Box(Modifier.padding(end = 10.dp)) {
                    BackIsland {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        lastBack?.invoke()
                    }
                }
            }
            val pick: (BeamTab) -> Unit = { tab ->
                if (tab != selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect(tab)
            }
            // Settings is not a place you go between the others: it stands on its own round island at the end.
            // With only Home besides Settings (compact mode) a lone tab on its own pill looks orphaned: both share
            // one island then, and Settings stands apart only when there are places to move between.
            val split = BeamTab.Settings in tabs && tabs.size > 2
            val main = if (split) tabs.filter { it != BeamTab.Settings } else tabs
            Box(Modifier.zIndex(1f)) { TabsIsland(main, selected, pick) }
            if (split) {
                Box(Modifier.padding(start = 10.dp)) { SettingsIsland(selected == BeamTab.Settings) { pick(BeamTab.Settings) } }
            }
        }
    }
}

/** The look every island shares: a calm tonal surface a step above the cards, no outline, a soft shadow under it. */
@Composable
private fun Modifier.island(shape: Shape): Modifier {
    val colors = MaterialTheme.colorScheme
    val shadow = remember(colors.background) {
        Shadow(radius = 22.dp, color = com.beamvpn.app.ui.theme.themedShadow(colors.background, 0.9f), offset = DpOffset(0.dp, 6.dp))
    }
    return this
        .then(if (BeamTheme.effects.reduced) Modifier else Modifier.dropShadow(shape, shadow))
        .clip(shape)
        // A step above the cards (surfaceContainer), so the bar never melts into a tile scrolling under it.
        .drawBehind { drawRect(colors.surfaceContainerHighest) }
}

/** Settings on its own round island; chosen, it fills with the same tonal disc as a chosen tab. */
@Composable
private fun SettingsIsland(selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, Press, label = "settingsPress")
    val fill by animateColorAsState(if (selected) colors.secondaryContainer else Color.Transparent, tween(MOTION_MS, easing = Emphasized), label = "settingsFill")
    val tint by animateColorAsState(if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, tween(MOTION_MS, easing = Emphasized), label = "settingsTint")
    Box(
        Modifier
            .size(IslandHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .island(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = BeamTab.Settings.label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(ItemHeight).clip(CircleShape).drawBehind { drawRect(fill) }, contentAlignment = Alignment.Center) {
            Icon(BeamTab.Settings.icon, BeamTab.Settings.label, Modifier.size(22.dp), tint = tint)
        }
    }
}

@Composable
private fun BackIsland(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, Press, label = "backPress")
    Box(
        Modifier
            .size(IslandHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .island(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = "Назад", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BeamIcons.ArrowLeft, "Назад", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TabsIsland(tabs: List<BeamTab>, selected: BeamTab, onSelect: (BeamTab) -> Unit) {
    // Where each tab sits inside the pill, for the indicator to travel between. With Settings chosen (on its own
    // island) the indicator stays where it was and fades out instead of sliding off.
    val bounds = remember { mutableStateMapOf<BeamTab, Pair<Float, Float>>() }
    var lastTarget by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    bounds[selected]?.let { lastTarget = it }
    val target = lastTarget
    val x by animateFloatAsState(target?.first ?: 0f, Slide, label = "indicatorX")
    val w by animateFloatAsState(target?.second ?: 0f, Slide, label = "indicatorW")
    val shown by animateFloatAsState(if (selected in tabs) 1f else 0f, tween(MOTION_MS, easing = Emphasized), label = "indicatorShown")
    val fill = MaterialTheme.colorScheme.secondaryContainer

    Row(
        Modifier
            .height(IslandHeight)
            .island(CircleShape)
            .drawBehind {
                if (w <= 0f || shown <= 0f) return@drawBehind
                val inset = (size.height - ItemHeight.toPx()) / 2
                val h = size.height - inset * 2
                // Tab bounds are inside the pill's horizontal padding; the indicator is drawn outside it.
                val left = x + PillPadding.toPx()
                drawRoundRect(fill, Offset(left, inset), Size(w, h), CornerRadius(h / 2), alpha = shown)
            }
            .padding(horizontal = PillPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEach { tab ->
            TabItem(
                tab = tab,
                selected = tab == selected,
                modifier = Modifier.onPlaced { c -> c.boundsInParent().let { bounds[tab] = it.left to it.width } },
            ) { onSelect(tab) }
        }
    }
}

@Composable
private fun TabItem(tab: BeamTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val extra = BeamTheme.extra
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, Press, label = "tabPress")
    val tint by animateColorAsState(if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, tween(MOTION_MS, easing = Emphasized), label = "tabTint")

    Row(
        modifier
            .heightIn(min = ItemHeight)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = tab.label, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            tab.icon,
            contentDescription = if (selected) null else tab.label,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(tween(MOTION_MS, easing = Emphasized), expandFrom = Alignment.Start) + fadeIn(tween(220, delayMillis = 80)),
            exit = shrinkHorizontally(tween(MOTION_MS, easing = Emphasized), shrinkTowards = Alignment.Start) + fadeOut(tween(100)),
        ) {
            Text(
                tab.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSecondaryContainer,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
