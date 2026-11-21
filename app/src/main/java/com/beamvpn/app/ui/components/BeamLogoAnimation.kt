package com.beamvpn.app.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import com.beamvpn.app.ui.theme.BeamTheme
import kotlinx.coroutines.delay

// The BEAM wordmark, path data copied from res/drawable/beam_wordmark.xml (itself 1:1 from desktop's beam.svg):
// the four letters, then the star.
private val LogoPaths = listOf(
    "M289.309 74.5094C296.5 78.9371 301.755 85.3019 305.074 93.6038C306.18 95.956 306.941 98.5157 307.356 101.283C307.771 104.05 307.978 106.956 307.978 110C307.978 120.101 305.281 128.61 299.888 135.528C294.495 142.308 286.612 147.358 276.24 150.679C269.74 152.893 262.065 154 253.214 154H187.042C185.106 154 183.377 153.308 181.856 151.925C180.473 150.403 179.781 148.673 179.781 146.736V131.792C179.781 129.717 180.473 127.987 181.856 126.604C183.377 125.082 185.106 124.321 187.042 124.321H254.874C262.757 124.321 268.703 122.868 272.714 119.962C276.033 117.333 277.692 113.597 277.692 108.755C277.692 103.912 275.964 99.9686 272.506 96.9245C268.081 93.1887 261.65 91.3208 253.214 91.3208H187.042C185.106 91.3208 183.377 90.6289 181.856 89.2453C180.473 87.7233 179.781 85.9245 179.781 83.8491V69.5283C179.781 67.5912 180.473 65.9308 181.856 64.5472C183.377 63.0252 185.106 62.2641 187.042 62.2641H249.481C252.8 62.2641 255.565 61.9874 257.778 61.434C260.129 60.8805 261.996 60.1887 263.379 59.3585C264.9 58.3899 266.076 57.3522 266.905 56.2453C267.735 55.1384 268.427 54.1006 268.98 53.1321C269.948 51.195 270.432 48.912 270.432 46.283C270.432 43.7925 270.086 41.5094 269.395 39.434C268.841 37.7736 267.873 36.2516 266.49 34.8679C264.831 32.9308 262.757 31.6164 260.267 30.9245C257.778 30.0943 254.805 29.6792 251.347 29.6792H187.042C185.106 29.6792 183.377 28.9874 181.856 27.6038C180.473 26.0818 179.781 24.283 179.781 22.2076V7.26416C179.781 5.32705 180.473 3.66667 181.856 2.28303C183.377 0.761009 185.106 0 187.042 0H249.481C266.629 0 279.836 4.84277 289.101 14.5283C296.984 22.4151 300.925 32.3082 300.925 44.2076C300.925 56.9371 297.053 67.0377 289.309 74.5094Z",
    "M441.825 0C443.899 0 445.628 0.761009 447.01 2.28303C448.393 3.66667 449.085 5.32705 449.085 7.26416V22.2076C449.085 24.1447 448.393 25.8742 447.01 27.3962C445.628 28.7799 443.899 29.4717 441.825 29.4717H363.413V61.2264H432.697C434.772 61.2264 436.5 61.9874 437.883 63.5094C439.266 64.8931 439.958 66.5535 439.958 68.4906V83.6415C439.958 85.5786 439.266 87.3082 437.883 88.8302C436.5 90.2138 434.772 90.9057 432.697 90.9057H363.413V122.868H441.825C443.899 122.868 445.628 123.629 447.01 125.151C448.393 126.535 449.085 128.195 449.085 130.132V145.075C449.085 147.013 448.393 148.742 447.01 150.264C445.628 151.648 443.899 152.34 441.825 152.34H340.595C338.659 152.34 336.93 151.648 335.409 150.264C334.026 148.742 333.334 147.013 333.334 145.075V7.26416C333.334 5.32705 334.026 3.66667 335.409 2.28303C336.93 0.761009 338.659 0 340.595 0H441.825Z",
    "M608.545 141.547C609.236 142.654 609.582 143.83 609.582 145.075C609.582 147.013 608.891 148.742 607.508 150.264C606.125 151.648 604.396 152.34 602.322 152.34H585.104C583.583 152.34 582.2 151.994 580.956 151.302C579.849 150.472 579.02 149.365 578.466 147.981L563.323 112.283H504.618L489.683 147.981C489.13 149.365 488.231 150.472 486.986 151.302C485.88 151.994 484.566 152.34 483.045 152.34H465.827C463.2 152.34 461.195 151.233 459.812 149.019C458.152 146.943 457.945 144.73 459.189 142.377L517.687 4.3585C518.24 2.97485 519.07 1.93711 520.176 1.24529C521.421 0.415097 522.804 0 524.325 0H543.824C545.345 0 546.659 0.415097 547.766 1.24529C549.01 1.93711 549.909 2.97485 550.462 4.3585L608.545 141.547ZM516.857 83.434H551.292L534.075 42.1321L516.857 83.434Z",
    "M768.741 0C770.677 0 772.337 0.761009 773.719 2.28303C775.241 3.66667 776.001 5.32705 776.001 7.26416V145.075C776.001 147.013 775.241 148.742 773.719 150.264C772.337 151.648 770.677 152.34 768.741 152.34H753.183C751.247 152.34 749.518 151.648 747.997 150.264C746.614 148.742 745.923 147.013 745.923 145.075V52.0943L707.754 101.075C706.371 102.874 704.504 103.774 702.153 103.774C699.802 103.774 697.935 102.874 696.552 101.075L657.554 51.8868V145.075C657.554 147.013 656.793 148.742 655.272 150.264C653.889 151.648 652.23 152.34 650.294 152.34H634.736C632.8 152.34 631.071 151.648 629.55 150.264C628.167 148.742 627.475 147.013 627.475 145.075V7.26416C627.475 5.32705 628.167 3.66667 629.55 2.28303C631.071 0.761009 632.8 0 634.736 0H652.783C655.134 0 657.07 0.968553 658.591 2.90566L702.153 60.6038L744.885 2.90566C746.13 0.968553 748.066 0 750.694 0H768.741Z",
    "M76.0607 0L97.6815 54.4685L152.121 76.1006L97.6815 97.7328L76.0607 152.201L54.4399 97.7328L0 76.1006L54.4399 54.4685L76.0607 0Z",
)

private const val STAR = 4
private val StarBlue = Color(0xFF00C8FF)

// Desktop's finale (Onboarding.tsx + style.css), in milliseconds from the start of the trace phase.
private const val TRACE_MS = 1500f
private const val SOLID_MS = 1820f
private const val TOTAL_MS = TRACE_MS + SOLID_MS

/** When each letter starts, as the rolling star passes over it (beamLetterDelayMs): 0 B, 1 E, 2 A, 3 M. */
private val LetterDelay = floatArrayOf(545f, 420f, 330f, 140f)

private val OutlineEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val RiseEasing = CubicBezierEasing(0.22f, 1.25f, 0.36f, 1f)
private val StarDrawEasing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
private val RollIn = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val RollSettle = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
private val PulseEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val SweepEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val SwellEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val SettleEasing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
private val ExitEasing = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)

private fun Easing.at(t: Float) = transform(t.coerceIn(0f, 1f))
private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** One glyph split into its contours, each with its own length, so a dash can run across all of them like SVG pathLength=1. */
private class Glyph(val full: android.graphics.Path, val contours: List<Pair<android.graphics.PathMeasure, Float>>) {
    val length = contours.sumOf { it.second.toDouble() }.toFloat()
    val fullPath = full.asComposePath()
    val bounds = android.graphics.RectF().also { full.computeBounds(it, true) }

    // Reused every frame instead of a new path per glyph per frame.
    val lineOut = android.graphics.Path()
    val line = lineOut.asComposePath()

    /** Writes the first [to] fraction of the outline into [line]. */
    fun updateLine(to: Float) {
        lineOut.rewind()
        var end = to.coerceIn(0f, 1f) * length
        for ((measure, len) in contours) {
            if (end <= 0f) break
            measure.getSegment(0f, end.coerceAtMost(len), lineOut, true)
            end -= len
        }
    }
}

private fun glyph(data: String): Glyph {
    val path = PathParser().parsePathString(data).toPath().asAndroidPath()
    val measure = android.graphics.PathMeasure(path, false)
    val contours = mutableListOf<Pair<android.graphics.PathMeasure, Float>>()
    do {
        val len = measure.length
        if (len > 0f) {
            val piece = android.graphics.Path()
            measure.getSegment(0f, len, piece, true)
            contours += android.graphics.PathMeasure(piece, false) to len
        }
    } while (measure.nextContour())
    return Glyph(path, contours)
}

/**
 * Desktop's onboarding finale. Trace: the star rolls in from the right and settles with a small overshoot while
 * the letters rise and draw themselves behind it, M to B, with a blue wave running along the outline. Solid: the
 * star pulses, the fill sweeps from it across the letters with a flash of glow, the wordmark swells and settles,
 * then leaves forward. [onFinished] runs once it is gone.
 */
@Composable
fun AnimatedBeamLogo(modifier: Modifier = Modifier, startDelayMs: Long = 150, onFinished: () -> Unit = {}) {
    val glyphs = remember { LogoPaths.map(::glyph) }
    val clock = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    val reduced = BeamTheme.effects.reduced
    val glowPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val traceGlow = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3.2f
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            color = Color(0xFF00C8FF).copy(alpha = 0.45f).toArgb()
        }
    }
    val blurs = remember { HashMap<Int, BlurMaskFilter>() }
    val layerPaint = remember { Paint() }

    LaunchedEffect(Unit) {
        delay(startDelayMs)
        clock.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
        finished()
    }

    val fg = MaterialTheme.colorScheme.onSurface
    val dark = fg.luminance() > 0.5f
    val base = fg.copy(alpha = 0.7f)
    val highlight = if (dark) Color(0xFFC4F3FF) else Color(0xFF0092C4)
    val glowColor = if (dark) Color(0xFF00C8FF).copy(alpha = 0.65f) else Color(0xFF00B4F0).copy(alpha = 0.32f)

    Canvas(modifier.aspectRatio(776f / 154f)) {
        val k = size.width / 776f
        val t = clock.value
        val s = t - TRACE_MS // time into the solid phase
        // The logo leaving at the very end.
        val exit = ExitEasing.at((s - 1360f) / 420f)
        val exitScale = 1f + 0.045f * exit
        val exitAlpha = 1f - exit

        withTransform({
            scale(k, k, pivot = Offset.Zero)
            scale(exitScale, exitScale, pivot = Offset(388f, 77f))
        }) {
            // ---- Trace ----
            val traceAlpha = (if (s < 520f) 1f else 1f - (s - 520f) / 380f).coerceIn(0f, 1f) * exitAlpha
            if (traceAlpha > 0f) {
                // The wave: a gradient twice the logo wide, mirrored, sliding two widths every 1.8 s.
                val shift = -1552f * ((t % 1800f) / 1800f)
                val wave = Brush.linearGradient(
                    0f to base, 0.42f to StarBlue, 0.5f to highlight, 0.58f to StarBlue, 1f to base,
                    start = Offset(shift, 0f),
                    end = Offset(shift + 776f, 0f),
                    tileMode = TileMode.Mirror,
                )
                for (i in 0 until STAR) {
                    val g = glyphs[i]
                    val lt = t - LetterDelay[i]
                    if (lt <= 0f) continue
                    val draw = OutlineEasing.at(lt / 950f)
                    val opacity = (lt / 95f).coerceIn(0f, 1f)
                    val rise = RiseEasing.transform((lt / 900f).coerceIn(0f, 1f))
                    val dy = 16f * (1f - rise)
                    val sc = mix(0.94f, 1f, rise)
                    g.updateLine(draw)
                    withTransform({
                        translate(0f, dy)
                        scale(sc, sc, pivot = Offset(g.bounds.centerX(), g.bounds.bottom))
                    }) {
                        if (!reduced) drawIntoCanvas { traceGlow.alpha = (115 * opacity * traceAlpha).toInt(); it.nativeCanvas.drawPath(g.lineOut, traceGlow) }
                        drawPath(g.line, wave, alpha = opacity * traceAlpha, style = Stroke(3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }

                // The star: rolls in from the right, then pulses when the fill begins.
                val star = glyphs[STAR]
                val p = (t / 1300f).coerceIn(0f, 1f)
                val (dx, rot) = if (p < 0.84f) {
                    val u = RollIn.at(p / 0.84f)
                    mix(640f, -14f, u) to mix(493f, -10f, u)
                } else {
                    val u = RollSettle.at((p - 0.84f) / 0.16f)
                    mix(-14f, 0f, u) to mix(-10f, 0f, u)
                }
                var pulseScale = 1f
                var pulseRot = 0f
                if (s in 0f..520f) {
                    val q = PulseEasing.at(s / 520f)
                    if (q < 0.35f) {
                        pulseScale = mix(1f, 1.22f, q / 0.35f); pulseRot = mix(0f, 18f, q / 0.35f)
                    } else {
                        pulseScale = mix(1.22f, 1f, (q - 0.35f) / 0.65f); pulseRot = mix(18f, 0f, (q - 0.35f) / 0.65f)
                    }
                }
                star.updateLine(StarDrawEasing.at(t / 700f))
                val starOpacity = (t / 70f).coerceIn(0f, 1f) * traceAlpha
                val c = Offset(star.bounds.centerX(), star.bounds.centerY())
                withTransform({
                    translate(dx, 0f)
                    rotate(rot + pulseRot, pivot = c)
                    scale(pulseScale, pulseScale, pivot = c)
                }) {
                    if (!reduced) drawIntoCanvas { traceGlow.alpha = (115 * starOpacity).toInt(); it.nativeCanvas.drawPath(star.lineOut, traceGlow) }
                    drawPath(star.line, StarBlue, alpha = starOpacity, style = Stroke(3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            // ---- Solid ----
            if (s > 150f && exitAlpha > 0f) {
                val r = s / 1500f
                val swell: Float
                val bright: Float
                val glow: Float
                val glowStrength: Float
                when {
                    r < 0.12f -> { swell = 0.985f; bright = 0f; glow = 0f; glowStrength = 0f }
                    r < 0.42f -> {
                        val u = SwellEasing.at((r - 0.12f) / 0.30f)
                        swell = mix(0.985f, 1.014f, u); bright = 0.2f * u; glow = 16f * u; glowStrength = u
                    }
                    else -> {
                        val u = SettleEasing.at((r - 0.42f) / 0.58f)
                        swell = mix(1.014f, 1f, u); bright = 0.2f * (1f - u); glow = mix(16f, 3f, u); glowStrength = mix(1f, 0.3f, u)
                    }
                }
                // Where the fill has got to: a soft edge moving from the star across to the M.
                val pos = 1f - SweepEasing.at((s - 150f) / 680f)
                val edge = 776f * (1.5f - 2f * pos)
                withTransform({ scale(swell, swell, pivot = Offset(388f, 77f)) }) {
                    drawIntoCanvas { canvas ->
                        val room = Rect(-60f, -60f, 836f, 214f)
                        canvas.saveLayer(room, layerPaint)
                        if (glow > 0.5f && !reduced) {
                            val radius = glow.toInt().coerceAtLeast(1)
                            glowPaint.color = glowColor.copy(alpha = glowColor.alpha * glowStrength).toArgb()
                            glowPaint.maskFilter = blurs.getOrPut(radius) { BlurMaskFilter(radius.toFloat(), BlurMaskFilter.Blur.NORMAL) }
                            glyphs.forEach { canvas.nativeCanvas.drawPath(it.full, glowPaint) }
                        }
                        glyphs.forEachIndexed { i, g ->
                            val tone = if (i == STAR) StarBlue else fg
                            drawPath(g.fullPath, lerp(tone, Color.White, bright))
                        }
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Black, 1f to Color.Transparent,
                                startX = edge - 0.3f * 776f,
                                endX = edge + 0.3f * 776f,
                            ),
                            topLeft = Offset(room.left, room.top),
                            size = room.size,
                            blendMode = BlendMode.DstIn,
                            alpha = exitAlpha,
                        )
                        canvas.restore()
                    }
                }
            }
        }
    }
}

/** The wordmark at rest, drawn from the same paths: the first screen shows it still. */
@Composable
fun BeamLogo(modifier: Modifier = Modifier) {
    val glyphs = remember { LogoPaths.map { PathParser().parsePathString(it).toPath() } }
    val fg = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.aspectRatio(776f / 154f)) {
        val k = size.width / 776f
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            glyphs.forEachIndexed { i, path -> drawPath(path, if (i == STAR) StarBlue else fg) }
        }
    }
}
