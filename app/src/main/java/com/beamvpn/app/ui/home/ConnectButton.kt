@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.beamvpn.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.toPath
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.vpn.ConnectionPhase
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * The big round BEAM button, after beam-desktop's ConnectButton: a dark card at
 * rest, the accent colour with a pulsing glow when connected, dust streaking
 * inwards while connecting and a burst with a bloom flash once it connects.
 *
 * Every animated value is read in the draw or layer phase, so the glow, the pulse and the colour fades redraw
 * without recomposing. The blurred shadows keep fixed colours and fade through their layer's alpha: Compose caches
 * a shadow's blurred bitmap by its colour, and an alpha animated inside the colour blurred (and kept) a new
 * full-size bitmap on the CPU every frame of the connect animation. With [BeamTheme.effects] reduced there is no
 * blur and no particles at all.
 */
@Composable
fun ConnectButton(
    phase: ConnectionPhase,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    noInternet: Boolean = false,
    /** Servers on the hold-to-pick ring; fewer than two disables the ring. */
    radialItems: List<RadialItem> = emptyList(),
    onPickServer: (RadialItem) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val effects = BeamTheme.effects
    val reduced = effects.reduced
    val connected = phase == ConnectionPhase.Connected
    val loading = phase == ConnectionPhase.Connecting
    val error = phase == ConnectionPhase.Error
    val activeGlow = connected || error

    val glowColor = when {
        error -> colors.error
        noInternet -> BeamTheme.extra.noInternet
        else -> colors.primary
    }
    val glow by animateFloatAsState(
        targetValue = if (activeGlow) 1f else 0f,
        animationSpec = tween(if (activeGlow) 800 else 300),
        label = "glow",
    )
    val glowScale by animateFloatAsState(
        targetValue = if (activeGlow) 1.25f else 0.95f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow),
        label = "glowScale",
    )
    // One looping value drives both the glow's pulse and the button's breath, and only while there is a glow:
    // the old pair of always-on infinite animations redrew the screen every frame even when disconnected.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(activeGlow, effects.animationsOff) {
        when {
            effects.animationsOff -> pulse.snapTo(PULSE_REST)
            activeGlow -> pulse.animateTo(0.5f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse))
        }
    }

    var pressed by remember { mutableStateOf(false) }
    val radial = remember { RadialState() }
    val radialScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val ringGeometry = rememberRadialGeometry()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentItems by rememberUpdatedState(radialItems)
    val currentOnPick by rememberUpdatedState(onPickServer)
    val currentSize by rememberUpdatedState(size)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val controller = remember {
        RadialController(
            state = radial,
            scope = radialScope,
            items = { currentItems },
            geometry = { center -> ringGeometry(center, with(density) { currentSize.toPx() } / 2f) },
            haptic = { opening -> haptic.performHapticFeedback(if (opening) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) },
            onPick = { currentOnPick(it) },
        )
    }
    val scale by animateFloatAsState(
        targetValue = when {
            loading -> 0.96f
            pressed -> 0.98f
            else -> 1f
        },
        animationSpec = tween(if (loading) 450 else 260),
        label = "scale",
    )
    val breathes = connected && !reduced
    // Only called from layer blocks; the breath goes 1 -> 1.01 as the pulse goes 1 -> 0.5.
    fun buttonScale() = scale * if (breathes) 1f + (1f - pulse.value) * 0.02f else 1f

    // Expressive shape: a plain circle at rest. While connecting it flows from one Material shape to the next and
    // spins up; once connected it settles into the 9-sided cookie and slows to a gentle turn. Off, it is a circle.
    val morphState = remember { ShapeMorph() }
    val morphProgress = remember { Animatable(1f) }
    LaunchedEffect(phase, reduced) {
        suspend fun goTo(target: androidx.graphics.shapes.RoundedPolygon, durationMs: Int) {
            if (morphState.to === target && morphProgress.value >= 1f) return
            morphState.retarget(target, morphProgress.value)
            morphProgress.snapTo(0f)
            morphProgress.animateTo(1f, tween(durationMs, easing = MorphEasing))
        }
        when {
            loading && !reduced -> {
                var i = 0
                while (true) {
                    goTo(ConnectingShapes[i % ConnectingShapes.size], 650)
                    i++
                }
            }
            connected -> goTo(androidx.compose.material3.MaterialShapes.Cookie9Sided, 900)
            else -> goTo(androidx.compose.material3.MaterialShapes.Circle, 700)
        }
    }
    // The turn: an angle that keeps its momentum across phases, fast while connecting, slow when connected.
    val spinSpeed by animateFloatAsState(
        when {
            reduced -> 0f
            loading -> 240f
            connected -> 15f
            else -> 0f
        },
        tween(1200, easing = FastOutSlowInEasing),
        label = "spinSpeed",
    )
    val currentSpeed by rememberUpdatedState(spinSpeed)
    var spin by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            // Asleep while there is nothing to turn, so an idle button costs no frames.
            if (currentSpeed <= 0.01f) {
                androidx.compose.runtime.snapshotFlow { currentSpeed }.first { it > 0.01f }
                last = 0L
            }
            withFrameNanos { now ->
                if (last != 0L) spin = (spin + currentSpeed * (now - last) / 1_000_000_000f) % 360f
                last = now
            }
        }
    }
    val shapePath = remember { androidx.compose.ui.graphics.Path() }
    val shapeMatrix = remember { androidx.compose.ui.graphics.Matrix() }
    // The light comes on slowly as the cookie settles, not in one blink.
    val container by animateColorAsState(if (activeGlow) glowColor else colors.surfaceContainer, tween(if (activeGlow) 800 else 300), label = "container")
    val border by animateColorAsState(if (activeGlow) glowColor else colors.outline, tween(if (activeGlow) 800 else 300), label = "border")
    // The resting shadow takes the theme's own hue: pure black looked like a dirty halo on light and coloured
    // themes. It is the page background pushed towards black, lighter on a light page.
    val bg = colors.background
    val restingTone = remember(bg) { com.beamvpn.app.ui.theme.themedShadow(bg) }
    val restingShadow = remember(restingTone) {
        Shadow(radius = 28.dp, color = restingTone, spread = (-8).dp, offset = DpOffset(0.dp, 10.dp))
    }
    val glowShadows = remember(glowColor) {
        // No spread: a spread shadow is a solid disk before the blur, which read as a hard bright rim.
        Shadow(radius = 36.dp, color = glowColor.copy(alpha = 0.35f)) to Shadow(radius = 110.dp, color = glowColor.copy(alpha = 0.14f))
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // Glow behind the button: the desktop's blurred blobs, drawn as a radial gradient. It is drawn past the
        // bounds (nothing up to the list clips it) and reaches ~1.9 button radii at full scale; the stops ease out
        // to zero so there is no visible rim. The button's edge falls at ~0.65 of the gradient radius. The brush is
        // built once per size and colour; fade, pulse and growth are layer properties, so a frame records nothing.
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = glowScale
                    scaleY = glowScale
                    alpha = (glow * pulse.value).coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .drawWithCache {
                    val r = this.size.minDimension / 2 * GLOW_REACH
                    val brush = Brush.radialGradient(
                        0f to glowColor.copy(alpha = 0.28f),
                        0.5f to glowColor.copy(alpha = 0.22f),
                        0.65f to glowColor.copy(alpha = 0.16f),
                        0.75f to glowColor.copy(alpha = 0.09f),
                        0.85f to glowColor.copy(alpha = 0.04f),
                        0.93f to glowColor.copy(alpha = 0.012f),
                        1f to Color.Transparent,
                        center = this.size.center,
                        radius = r,
                    )
                    onDrawBehind { drawCircle(brush, radius = r) }
                },
        )

        if (!reduced) {
            ParticleLayer(phase = phase, accent = colors.primary, buttonSize = size, modifier = Modifier.matchParentSize())
        }

        ServerRadialOverlay(radial, radialItems, connected)

        if (reduced) {
            // No blur: the resting shadow as a soft gradient disc under the button.
            Spacer(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val s = buttonScale()
                        scaleX = s
                        scaleY = s
                        alpha = 1f - glow
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .drawWithCache {
                        val r = this.size.minDimension / 2
                        val reach = r + 24.dp.toPx()
                        val c = this.size.center + Offset(0f, 10.dp.toPx())
                        val brush = Brush.radialGradient(
                            0f to restingTone.copy(alpha = restingTone.alpha * 0.9f),
                            (r * 0.8f / reach) to restingTone.copy(alpha = restingTone.alpha * 0.8f),
                            (r / reach) to restingTone.copy(alpha = restingTone.alpha * 0.45f),
                            1f to Color.Transparent,
                            center = c,
                            radius = reach,
                        )
                        onDrawBehind { drawCircle(brush, radius = reach, center = c) }
                    },
            )
        } else {
            // Fixed shadows, each blurred once; the glow fades them through their layer.
            Spacer(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val s = buttonScale()
                        scaleX = s
                        scaleY = s
                        alpha = 1f - glow
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .dropShadow(CircleShape, restingShadow),
            )
            Spacer(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val s = buttonScale()
                        scaleX = s
                        scaleY = s
                        alpha = glow
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .dropShadow(CircleShape, glowShadows.first)
                    .dropShadow(CircleShape, glowShadows.second),
            )
        }

        Box(
            Modifier
                .matchParentSize()
                .onGloballyPositioned { origin = it.positionInWindow() }
                .radialGesture(
                    enabled = { !loading },
                    usable = { !loading && currentItems.size >= 2 },
                    toWindow = { it + origin },
                    onPressed = { pressed = it },
                    onClick = { currentOnClick() },
                    onOpen = { finger ->
                        controller.open(origin + Offset(with(density) { currentSize.toPx() } / 2f, with(density) { currentSize.toPx() } / 2f), finger)
                    },
                    onMove = controller::move,
                    onRelease = controller::release,
                    onCancel = controller::cancel,
                )
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick { if (!loading) currentOnClick(); true }
                }
                .graphicsLayer {
                    val s = buttonScale()
                    scaleX = s
                    scaleY = s
                }
                // The card and its hairline, drawn here so the colour fades redraw instead of recomposing a Surface.
                .drawBehind {
                    val w = 1.dp.toPx()
                    val morph = morphState.morph
                    if (morph == null) {
                        drawCircle(container)
                        drawCircle(border, radius = this.size.minDimension / 2 - w / 2, style = Stroke(w))
                    } else {
                        // The morph is in a unit square; scale it to the button and turn it about its centre.
                        morph.toPath(morphProgress.value, shapePath.apply { rewind() })
                        shapeMatrix.reset()
                        shapeMatrix.scale(this.size.width, this.size.height)
                        shapePath.transform(shapeMatrix)
                        val angle = spin
                        withTransform({ rotate(angle, pivot = this@drawBehind.center) }) {
                            drawPath(shapePath, container)
                            drawPath(shapePath, border, style = Stroke(w))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val logoAlpha by animateFloatAsState(
                when {
                    loading -> 0.25f
                    activeGlow -> 1f
                    else -> 0.75f
                },
                tween(300),
                label = "logoAlpha",
            )
            val logoScale by animateFloatAsState(if (loading) 0.9f else 1f, tween(300), label = "logoScale")
            Icon(
                imageVector = BeamIcons.Star,
                contentDescription = "BEAM",
                tint = when {
                    error -> Color.White
                    activeGlow -> colors.onPrimary
                    else -> colors.onSurface
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.21f)
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                    },
            )
        }
    }
}


/** The pulse's middle, held when the system has animations off. */
private const val PULSE_REST = 0.75f

/** Gradient radius of the glow in button radii, before the 1.25 grow-in scale. */
private const val GLOW_REACH = 1.55f

// Particles. Units follow the desktop canvas, where the button radius is ~140px;
// everything is scaled by u = radius / 140 so the effect keeps its proportions.

private class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val baseSpeed: Float,
    var length: Float,
    var alpha: Float,
    var decay: Float,
    /** Index into [ParticleField.tails]. */
    val color: Int,
    var burst: Boolean,
)

private class ParticleField(palette: List<Color>) {
    /**
     * One streak brush per colour, transparent to opaque over x -1..0, stretched to each streak's length when
     * drawn. The shader is made once; a fresh gradient per streak per frame was 60 shaders a frame.
     */
    val tails = palette.map { Brush.horizontalGradient(listOf(it.copy(alpha = 0f), it), startX = -1f, endX = 0f) }
    val particles = ArrayList<Particle>()
    var bloom = 0f
    var loadStartNanos = 0L

    fun active(loading: Boolean) = loading || bloom > 0.01f || particles.isNotEmpty()

    private fun randomColor() = Random.nextInt(tails.size)

    fun spawnInward(u: Float) {
        val angle = Random.nextFloat() * TAU
        val dist = (360f + Random.nextFloat() * 80f) * u
        particles += Particle(
            x = cos(angle) * dist, y = sin(angle) * dist,
            vx = -cos(angle), vy = -sin(angle),
            baseSpeed = (2.5f + Random.nextFloat() * 1.5f) * u,
            length = (8f + Random.nextFloat() * 12f) * u,
            alpha = 0.08f, decay = 0f,
            color = randomColor(), burst = false,
        )
    }

    fun burst(radius: Float, u: Float) {
        bloom = 1f
        particles.forEach { p ->
            val d = hypot(p.x, p.y).coerceAtLeast(1f)
            val speed = (4.5f + Random.nextFloat() * 6.5f) * u
            p.vx = p.x / d * speed
            p.vy = p.y / d * speed
            p.burst = true
            p.decay = 0.02f + Random.nextFloat() * 0.02f
            p.alpha = 0.95f
            p.length = (16f + Random.nextFloat() * 20f) * u
        }
        repeat(min(45, MAX_PARTICLES - particles.size)) {
            val angle = Random.nextFloat() * TAU
            val speed = (3.5f + Random.nextFloat() * 8f) * u
            particles += Particle(
                x = cos(angle) * radius * 0.5f, y = sin(angle) * radius * 0.5f,
                vx = cos(angle) * speed, vy = sin(angle) * speed,
                baseSpeed = speed,
                length = (12f + Random.nextFloat() * 16f) * u,
                alpha = 1f, decay = 0.018f + Random.nextFloat() * 0.02f,
                color = randomColor(), burst = true,
            )
        }
    }

    /** Advances one step; [dt] is in 60 fps frames. */
    fun step(loading: Boolean, nowNanos: Long, radius: Float, u: Float, dt: Float) {
        val progress = if (loading) min(1f, (nowNanos - loadStartNanos) / 1e6f / ACCEL_RAMP_MS) else 0f
        val acceleration = 1f + progress.pow(1.8f) * 2.5f
        if (loading && particles.size < MAX_PARTICLES && Random.nextFloat() < (0.25f + progress * 0.45f) * dt) {
            spawnInward(u)
        }
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (!p.burst) {
                val dist = hypot(p.x, p.y)
                val boost = 1f + (380f * u - dist) / (220f * u)
                val speed = min(12f * u, p.baseSpeed * acceleration * boost)
                p.x += p.vx * speed * dt
                p.y += p.vy * speed * dt
                p.alpha = min(0.75f, p.alpha + 0.03f * dt)
                p.length = p.length.coerceAtLeast(1f)
                if (dist <= radius * 0.45f) it.remove()
            } else {
                p.x += p.vx * dt
                p.y += p.vy * dt
                val damping = 0.955f.pow(dt)
                p.vx *= damping
                p.vy *= damping
                p.alpha -= p.decay * dt
                if (p.alpha <= 0f) it.remove()
            }
        }
        bloom *= 0.88f.pow(dt)
    }

    /**
     * Streaks as stretched rects under one cached gradient each, and an additive blend: Screen is an advanced
     * blend mode that many mobile GPUs can only do by copying the frame behind every streak. On the dark
     * surface the button sits on, Plus looks the same.
     */
    fun DrawScope.draw(radius: Float, u: Float, bloomBrush: Brush) {
        val c = center
        val width = 1.5f * u
        val streak = Size(1f, width)
        val topLeft = Offset(-1f, -width / 2)
        particles.forEach { p ->
            val speed = hypot(p.vx, p.vy).coerceAtLeast(0.001f)
            val tail = if (p.burst) p.length * (speed / (4.5f * u)) else p.length * 1.5f
            val degrees = atan2(p.vy, p.vx) * RAD_TO_DEG
            withTransform({
                translate(c.x + p.x, c.y + p.y)
                rotate(degrees, pivot = Offset.Zero)
                scale(tail, 1f, pivot = Offset.Zero)
            }) {
                drawRect(tails[p.color], topLeft, streak, alpha = p.alpha.coerceIn(0f, 1f), blendMode = BlendMode.Plus)
            }
        }
        if (bloom > 0.01f) {
            drawCircle(bloomBrush, radius = radius + 150f * u, center = c, alpha = bloom, blendMode = BlendMode.Plus)
        }
    }

    companion object {
        const val MAX_PARTICLES = 60
        const val ACCEL_RAMP_MS = 3000f
        const val TAU = (Math.PI * 2).toFloat()
        const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
    }
}

@Composable
private fun ParticleLayer(phase: ConnectionPhase, accent: Color, buttonSize: Dp, modifier: Modifier) {
    val field = remember(accent) {
        ParticleField(listOf(accent, lerp(accent, Color.White, 0.25f), Color.White, lerp(accent, Color.White, 0.55f)))
    }
    val frame = remember { mutableLongStateOf(0L) }
    val radius = with(LocalDensity.current) { buttonSize.toPx() } / 2
    val u = radius / 140f
    val previous = remember { arrayOf(phase) }

    LaunchedEffect(phase) {
        val loading = phase == ConnectionPhase.Connecting
        if (previous[0] == ConnectionPhase.Connecting && phase == ConnectionPhase.Connected) field.burst(radius, u)
        previous[0] = phase
        var last = withFrameNanos { it }
        if (loading) field.loadStartNanos = last
        while (field.active(loading)) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 16_666_667f).coerceIn(0.1f, 4f)
            last = now
            field.step(loading, now, radius, u, dt)
            frame.longValue++
        }
        frame.longValue++
    }

    Spacer(
        modifier
            // Its own layer, so a particle frame re-records only the particles and not the screen around them.
            .graphicsLayer()
            .drawWithCache {
                val r = radius + 150f * u
                val bloomBrush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.8f),
                    0.35f to accent.copy(alpha = 0.45f),
                    1f to Color.Transparent,
                    center = this.size.center, radius = r,
                )
                onDrawBehind {
                    frame.longValue
                    with(field) { draw(radius, u, bloomBrush) }
                }
            },
    )
}

/** The shapes the button flows through while it connects. */
private val ConnectingShapes by lazy {
    with(androidx.compose.material3.MaterialShapes) {
        listOf(Cookie4Sided, SoftBurst, Cookie6Sided, Sunny, Cookie7Sided, Clover4Leaf, Cookie12Sided, Puffy)
    }
}

private val MorphEasing = androidx.compose.animation.core.CubicBezierEasing(0.42f, 0f, 0.2f, 1f)

/**
 * The button's current morph: from the shape it was showing to the next one. Retargeting mid-way starts the new
 * morph from where the old one stood (approximated by its nearer end), so a change of phase never jumps.
 */
private class ShapeMorph {
    var from: androidx.graphics.shapes.RoundedPolygon = androidx.compose.material3.MaterialShapes.Circle
        private set
    var to: androidx.graphics.shapes.RoundedPolygon = androidx.compose.material3.MaterialShapes.Circle
        private set
    /** Null while resting on the circle, which is drawn as a plain circle. */
    var morph by mutableStateOf<androidx.graphics.shapes.Morph?>(null)
        private set

    fun retarget(target: androidx.graphics.shapes.RoundedPolygon, progress: Float) {
        from = if (progress >= 0.5f) to else from
        to = target
        val circle = androidx.compose.material3.MaterialShapes.Circle
        morph = if (from === circle && to === circle) null else androidx.graphics.shapes.Morph(from, to)
    }
}
