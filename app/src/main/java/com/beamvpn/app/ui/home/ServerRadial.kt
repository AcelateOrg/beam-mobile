package com.beamvpn.app.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.compositeOver
import com.beamvpn.app.ui.components.CountryFlag
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.vpn.NodeGroup
import com.beamvpn.app.vpn.PingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// Radial server menu around the connect button, port of beam-desktop's ServerRadial:
// hold the button, drag towards a server, release. The sector is picked by direction, so there is
// no need to aim; releasing in the middle cancels.

private const val HOLD_MS = 280L
private const val MAX_SLOTS = 8
private const val CLOSE_MS = 160L
private val DragOpen = 14.dp

/** One slot of the ring: a server, or "all servers" when there are more than fit. */
data class RadialItem(
    val id: String,
    val name: String,
    val countryCode: String?,
    val current: Boolean,
    val pingMs: Int?,
    /** Servers that did not fit; > 0 only on the "all servers" slot. */
    val rest: Int = 0,
) {
    val isMore: Boolean get() = rest > 0
}

const val RADIAL_MORE_ID = "@more"

/** Servers in provider order; past [MAX_SLOTS] the tail folds into one "all servers" slot, keeping the current one visible. */
fun buildRadialItems(groups: List<NodeGroup>, selectedId: String?, pings: Map<String, PingResult>): List<RadialItem> {
    val all = groups.flatMap { it.nodes }
    if (all.isEmpty()) return emptyList()
    var picked = all
    var rest = 0
    if (all.size > MAX_SLOTS) {
        val head = all.take(MAX_SLOTS - 1).toMutableList()
        all.firstOrNull { it.id == selectedId }?.let { if (it !in head) head[head.lastIndex] = it }
        picked = head
        rest = all.size - head.size
    }
    val items = picked.map { n ->
        val parsed = n.parsed
        val ping = (pings[n.id] as? PingResult.Ok)?.latencyMs?.takeIf { it > 0 }
        RadialItem(n.id, parsed.displayName, parsed.countryCode, n.id == selectedId, ping)
    }
    return if (rest > 0) items + RadialItem(RADIAL_MORE_ID, "Все", null, false, null, rest) else items
}

/** What the gesture and the overlay share: whether the ring is open, the picked slot and the ring geometry in window pixels. */
@Stable
class RadialState {
    var open by mutableStateOf(false)
    var closing by mutableStateOf(false)
    var active by mutableIntStateOf(-1)
    var center by mutableStateOf(Offset.Zero)
    var buttonRadius by mutableFloatStateOf(0f)
    var ringRadius by mutableFloatStateOf(0f)

    /** The slot under a finger at [position] (window pixels), or -1 in the dead zone around the button. */
    fun slotAt(position: Offset, count: Int): Int {
        if (count == 0) return -1
        val dx = position.x - center.x
        val dy = position.y - center.y
        if (hypot(dx, dy) <= min(buttonRadius * 0.38f, ringRadius * 0.45f)) return -1
        var a = atan2(dx, -dy)
        if (a < 0f) a += (2 * PI).toFloat()
        return (a / ((2 * PI).toFloat() / count)).roundToInt() % count
    }
}

private enum class Outcome { Click, Drag, Cancel }

/**
 * Tap = [onClick]. Hold for [HOLD_MS] or drag away = open the ring ([onOpen]), then [onMove] follows the finger and
 * [onRelease] fires on lift. Without a ring ([usable] false) a hold is just a slow tap.
 */
internal fun Modifier.radialGesture(
    enabled: () -> Boolean,
    usable: () -> Boolean,
    toWindow: (Offset) -> Offset,
    onPressed: (Boolean) -> Unit,
    onClick: () -> Unit,
    onOpen: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
): Modifier = pointerInput(Unit) {
    val dragPx = DragOpen.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!enabled()) {
            waitForUpOrCancellation()
            return@awaitEachGesture
        }
        onPressed(true)
        var last: PointerInputChange = down
        val early = withTimeoutOrNull(HOLD_MS) {
            var result: Outcome? = null
            while (result == null) {
                val change = awaitPointerEventChange(down)
                if (change == null) {
                    result = Outcome.Cancel
                } else {
                    last = change
                    result = when {
                        change.changedToUpIgnoreConsumed() -> Outcome.Click
                        (change.position - down.position).getDistance() > dragPx -> Outcome.Drag
                        else -> null
                    }
                }
            }
            result
        }
        when {
            early == Outcome.Click -> {
                onPressed(false)
                onClick()
                return@awaitEachGesture
            }
            early == Outcome.Cancel -> {
                onPressed(false)
                return@awaitEachGesture
            }
            !usable() -> {
                // Nothing to pick from: finish as a tap, but only if the finger did not wander off.
                val up = if (early == Outcome.Drag) null else waitForUpOrCancellation()
                onPressed(false)
                if (up != null) onClick()
                return@awaitEachGesture
            }
        }
        // Timeout or a drag: the ring opens and the finger drives it.
        onOpen(toWindow(last.position))
        while (true) {
            val change = awaitPointerEventChange(down)
            if (change == null) {
                onCancel()
                break
            }
            // Check the lift before consuming: changedToUp() is false for a consumed change, which turned every release into a cancel.
            if (change.changedToUpIgnoreConsumed()) {
                change.consume()
                onRelease()
                break
            }
            change.consume()
            if (!change.pressed) {
                onCancel()
                break
            }
            onMove(toWindow(change.position))
        }
        onPressed(false)
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitPointerEventChange(down: PointerInputChange): PointerInputChange? {
    val event = awaitPointerEvent()
    return event.changes.firstOrNull { it.id == down.id }
}

/** Opens, drives and closes a [RadialState]; shared by the gesture callbacks. */
@Stable
internal class RadialController(
    private val state: RadialState,
    private val scope: CoroutineScope,
    private val items: () -> List<RadialItem>,
    private val geometry: (center: Offset) -> Pair<Float, Float>,
    private val haptic: (Boolean) -> Unit,
    private val onPick: (RadialItem) -> Unit,
) {
    fun open(center: Offset, finger: Offset) {
        val (button, ring) = geometry(center)
        state.center = center
        state.buttonRadius = button
        state.ringRadius = ring
        state.active = -1
        state.closing = false
        state.open = true
        haptic(true)
        move(finger)
    }

    fun move(finger: Offset) {
        val next = state.slotAt(finger, items().size)
        if (next != state.active) {
            state.active = next
            if (next >= 0) haptic(false)
        }
    }

    fun release() {
        val picked = items().getOrNull(state.active)
        close()
        if (picked != null) onPick(picked)
    }

    fun cancel() = close()

    private fun close() {
        state.closing = true
        scope.launch {
            delay(CLOSE_MS)
            state.open = false
            state.closing = false
            state.active = -1
        }
    }
}

private object ZeroPosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset.Zero
}

/** The full-screen ring. A popup, so it is not clipped to the page panel; the gesture keeps running in the button's window. */
@Composable
internal fun ServerRadialOverlay(state: RadialState, items: List<RadialItem>, connected: Boolean) {
    if (!state.open || items.isEmpty()) return
    Popup(
        popupPositionProvider = ZeroPosition,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        RadialContent(state, items, connected)
    }
}

@Composable
private fun RadialContent(state: RadialState, items: List<RadialItem>, connected: Boolean) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val extra = BeamTheme.extra
    val accent = colors.primary
    val n = items.size
    val visible = !state.closing
    val enter by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "enter")
    val fade by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "fade")

    val chipPx = with(density) { (if (state.ringRadius - state.buttonRadius < with(density) { 40.dp.toPx() }) 38.dp else 44.dp).toPx() }
    val chipDp = with(density) { chipPx.toDp() }
    val ringR = state.ringRadius
    val centerR = max(with(density) { 36.dp.toPx() }, min(state.buttonRadius, ringR - chipPx / 2 - with(density) { 10.dp.toPx() }))
    val glowR = ringR + chipPx + with(density) { 24.dp.toPx() }
    val step = (2 * PI).toFloat() / n

    // The highlight turns the short way round: from slot 7 to 0 forward, not back through the whole circle.
    val rotation = remember { floatArrayOf(0f) }
    if (state.active >= 0) {
        val target = state.active * 360f / n
        var delta = ((target - rotation[0]) % 360f + 540f) % 360f - 180f
        if (kotlin.math.abs(delta) < 0.001f) delta = 0f
        rotation[0] += delta
    }
    val rot by animateFloatAsState(rotation[0], spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "rot")
    val wedge by animateFloatAsState(if (state.active >= 0) 1f else 0f, tween(180), label = "wedge")

    val ringTint = BeamTheme.extra.tint
    val dim = colors.background
    // The open/close springs are read only in draw, layer and placement blocks below, so the ring animates
    // without recomposing every chip on every frame. Brushes and the wedge path are built once per geometry.
    Box(Modifier.fillMaxSize()) {
        Spacer(Modifier.fillMaxSize().drawBehind { drawRect(dim, alpha = 0.84f * fade) })

        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val c = state.center
                val glowBrush = Brush.radialGradient(0f to accent, 1f to Color.Transparent, center = c, radius = glowR)
                val outer = ringR + chipPx / 2 + 14.dp.toPx()
                val half = min(step * 0.42f, 0.5f)
                val path = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + sin(-half) * outer, c.y - cos(-half) * outer)
                    arcTo(
                        androidx.compose.ui.geometry.Rect(c.x - outer, c.y - outer, c.x + outer, c.y + outer),
                        startAngleDegrees = -90f - half * 180f / PI.toFloat(),
                        sweepAngleDegrees = 2f * half * 180f / PI.toFloat(),
                        forceMoveTo = false,
                    )
                    close()
                }
                val wedgeBrush = Brush.radialGradient(
                    0f to Color.Transparent,
                    (centerR / outer).coerceIn(0f, 0.9f) to Color.Transparent,
                    0.86f to accent.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                    center = c,
                    radius = outer,
                )
                val ringStroke = Stroke(1.dp.toPx())
                onDrawBehind {
                    // The gradient grows in with the ring: scaling the disc is the same as shrinking its radius.
                    scale(0.6f + 0.4f * enter, pivot = c) {
                        drawCircle(glowBrush, radius = glowR, center = c, alpha = (if (state.active >= 0) 0.12f else 0.08f) * fade)
                    }
                    drawCircle(ringTint, radius = ringR * (0.8f + 0.2f * enter), center = c, alpha = 0.08f * fade, style = ringStroke)
                    if (wedge > 0f) {
                        rotate(rot, pivot = c) { drawPath(path, wedgeBrush, alpha = (wedge * fade).coerceIn(0f, 1f)) }
                    }
                }
            },
        )

        // Centre: a glass disc previewing the pick.
        val discPx = (centerR * 2).roundToInt()
        Box(
            Modifier
                .offset { IntOffset((state.center.x - centerR).roundToInt(), (state.center.y - centerR).roundToInt()) }
                .size(with(density) { discPx.toDp() })
                .graphicsLayer {
                    val s = 0.9f + 0.1f * enter
                    scaleX = s
                    scaleY = s
                    alpha = fade
                }
                .clip(CircleShape)
                .background(colors.surfaceContainer.copy(alpha = 0.85f))
                .border(1.dp, BeamTheme.extra.tint.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = state.active, animationSpec = tween(140), label = "centre") { index ->
                val item = items.getOrNull(index)
                Column(
                    Modifier.padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when {
                        item == null -> {
                            Icon(BeamIcons.Star, null, Modifier.size(26.dp), tint = colors.onSurface.copy(alpha = 0.2f))
                            Text("Тяни к серверу", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                        }
                        item.isMore -> {
                            Icon(BeamIcons.Servers, null, Modifier.size(24.dp), tint = accent)
                            Text("Все серверы", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("ещё ${item.rest}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                        }
                        else -> {
                            CountryFlag(item.countryCode, 32.dp)
                            Text(
                                item.name,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                buildString {
                                    item.pingMs?.let { append("$it ms · ") }
                                    append(
                                        when {
                                            item.current && connected -> "уже здесь"
                                            connected -> "переключиться"
                                            else -> "подключиться"
                                        },
                                    )
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = item.pingMs?.let { pingColor(it) } ?: colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Servers around the ring.
        val labelW = 80.dp
        val labelH = 30.dp
        val gap = 4.dp
        items.forEachIndexed { i, item ->
            val a = i * step
            // Placement-time reads of the spring, see above.
            fun x() = state.center.x + sin(a) * ringR * (0.4f + 0.6f * enter)
            fun y() = state.center.y - cos(a) * ringR * (0.4f + 0.6f * enter)
            val isActive = i == state.active
            val dimmed = state.active >= 0 && !isActive
            val chipScale by animateFloatAsState(if (isActive) 1.1f else 1f, spring(dampingRatio = 0.6f), label = "chip")
            val itemAlpha by animateFloatAsState(if (dimmed) 0.5f else 1f, tween(160), label = "dim")
            val labelAbove = cos(a) > 0.35f

            Box(
                Modifier
                    .offset { IntOffset((x() - chipPx / 2).roundToInt(), (y() - chipPx / 2).roundToInt()) }
                    .size(chipDp)
                    .graphicsLayer {
                        alpha = itemAlpha * fade
                        scaleX = chipScale
                        scaleY = chipScale
                    }
                    .clip(CircleShape)
                    .background(if (isActive) accent.copy(alpha = 0.16f).compositeOver(colors.surfaceContainer) else colors.surfaceContainer)
                    .border(if (isActive) 1.5.dp else 1.dp, if (isActive) accent.copy(alpha = 0.85f) else BeamTheme.extra.tint.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (item.isMore) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(BeamIcons.Servers, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        Text("+${item.rest}", fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                    }
                } else {
                    CountryFlag(item.countryCode, chipDp - 16.dp)
                }
                if (item.current) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(8.dp)
                            .background(accent, CircleShape)
                            .border(2.dp, colors.surfaceContainer, CircleShape),
                    )
                }
            }
            // The label sits outside the ring: above the chip for the upper slots, below it for the rest.
            val labelAbovePx = with(density) { (gap + labelH).toPx() }
            val labelBelowPx = with(density) { gap.toPx() }
            val labelHalfW = with(density) { labelW.toPx() } / 2
            Box(
                Modifier
                    .offset {
                        val labelTop = if (labelAbove) y() - chipPx / 2 - labelAbovePx else y() + chipPx / 2 + labelBelowPx
                        IntOffset((x() - labelHalfW).roundToInt(), labelTop.roundToInt())
                    }
                    .size(labelW, labelH)
                    .graphicsLayer { alpha = itemAlpha * fade },
                contentAlignment = if (labelAbove) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        item.name,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isActive) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.pingMs?.let {
                        Text("$it ms", fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = pingColor(it))
                    }
                }
            }
        }
    }
}

@Composable
private fun pingColor(ms: Int): Color = when {
    ms < 150 -> BeamTheme.extra.pingGood
    ms < 300 -> BeamTheme.extra.pingMid
    else -> BeamTheme.extra.pingBad
}

/** Ring geometry for a button of [button] px radius at [center]: the ring must stay on screen. */
@Composable
internal fun rememberRadialGeometry(): (Offset, Float) -> Pair<Float, Float> {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    return remember(density) {
        { center, buttonRadius ->
            val size = window.containerSize
            val maxH = min(center.x, size.width - center.x) - with(density) { 56.dp.toPx() }
            val maxV = min(center.y, size.height - center.y) - with(density) { 56.dp.toPx() }
            val ring = max(buttonRadius * 0.78f, minOf(buttonRadius + with(density) { 62.dp.toPx() }, maxH, maxV))
            buttonRadius to ring
        }
    }
}
