package com.beamvpn.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Port of beam-desktop's themes/palette.ts. Every theme, built-in or custom, is a seed (base, accent and
// background in OKLCH); the palette is built from it on one lightness ladder, so no theme can forget a
// token. Only the surfaces the phone UI uses are kept.

enum class ThemeBase { Dark, Light, Amoled }

data class Oklch(val l: Double, val c: Double, val h: Double)

data class ThemeSeed(val base: ThemeBase, val accent: Oklch, val surface: Oklch)

/** The desktop CSS tokens the phone UI maps to Material roles. */
@Immutable
data class ThemeColors(
    val isLight: Boolean,
    val background: Color,
    val foreground: Color,
    val card: Color,
    val popover: Color,
    /** Desktop --primary: buttons, badges, selection (near-white on dark themes). */
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val muted: Color,
    val mutedForeground: Color,
    val destructive: Color,
    val border: Color,
    val input: Color,
    /** Desktop --sidebar: the shell around the page panel. */
    val sidebar: Color,
    /** Desktop --sidebar-primary: the accent (connect button, glow, selection). */
    val accent: Color,
    val accentForeground: Color,
)

private const val NEUTRAL_CHROMA = 0.03
private const val PRIMARY_DARK_TEXT_FROM = 0.72
private const val GRAY_HUE = 286.0

/* ---- Colour maths (OKLab, Björn Ottosson) ---- */

private fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))

private fun normalizeHue(h: Double): Double {
    val x = h % 360.0
    return if (x < 0) x + 360.0 else x
}

private fun srgbToLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
private fun linearToSrgb(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055

private val HexRe = Regex("^#?([0-9a-fA-F]{6})$")

/** "#rrggbb" in lower case, or null when [value] is not a 6-digit hex colour. */
fun normalizeHex(value: String): String? = HexRe.find(value.trim())?.let { "#" + it.groupValues[1].lowercase() }

fun hexToColor(hex: String): Color {
    val n = (normalizeHex(hex) ?: "#000000").substring(1).toLong(16)
    return Color(((n shr 16) and 255).toInt(), ((n shr 8) and 255).toInt(), (n and 255).toInt())
}

fun colorToHex(c: Color): String =
    "#%02x%02x%02x".format((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

fun hexToOklch(hex: String): Oklch {
    val n = (normalizeHex(hex) ?: "#000000").substring(1).toLong(16)
    val r = srgbToLinear(((n shr 16) and 255) / 255.0)
    val g = srgbToLinear(((n shr 8) and 255) / 255.0)
    val b = srgbToLinear((n and 255) / 255.0)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val bigL = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
    val a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
    return Oklch(bigL, sqrt(a * a + bb * bb), normalizeHue(Math.toDegrees(atan2(bb, a))))
}

private fun oklchToLinearRgb(l: Double, c: Double, h: Double): DoubleArray {
    val rad = Math.toRadians(h)
    val a = c * cos(rad)
    val b = c * sin(rad)
    val l3 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m3 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s3 = (l - 0.0894841775 * a - 1.291485548 * b).pow(3)
    return doubleArrayOf(
        4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
        -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
        -0.0041960863 * l3 - 0.7034186147 * m3 + 1.707614701 * s3,
    )
}

private fun inGamut(l: Double, c: Double, h: Double): Boolean {
    val eps = 1e-4
    return oklchToLinearRgb(l, c, h).all { it >= -eps && it <= 1 + eps }
}

/** The largest chroma <= [c] that stays inside sRGB. */
private fun fitChroma(l: Double, c: Double, h: Double): Double {
    if (c <= 0) return 0.0
    if (inGamut(l, c, h)) return c
    var lo = 0.0
    var hi = c
    repeat(18) {
        val mid = (lo + hi) / 2
        if (inGamut(l, mid, h)) lo = mid else hi = mid
    }
    return lo
}

/** An OKLCH colour as an sRGB [Color], chroma fitted into the gamut. */
internal fun ok(l: Double, c: Double, h: Double, alpha: Double = 1.0): Color {
    val rgb = oklchToLinearRgb(l, fitChroma(l, c, h), h)
    fun ch(v: Double) = clamp(linearToSrgb(clamp(v, 0.0, 1.0)), 0.0, 1.0).toFloat()
    return Color(ch(rgb[0]), ch(rgb[1]), ch(rgb[2]), alpha.toFloat())
}

/* ---- HSV, for the colour picker ---- */

data class Hsv(val h: Double, val s: Double, val v: Double)

fun hexToHsv(hex: String): Hsv {
    val c = hexToColor(hex)
    val r = c.red.toDouble()
    val g = c.green.toDouble()
    val b = c.blue.toDouble()
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    var h = 0.0
    if (d != 0.0) {
        h = when (mx) {
            r -> ((g - b) / d) % 6
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60
    }
    return Hsv(normalizeHue(h), if (mx == 0.0) 0.0 else d / mx, mx)
}

fun hsvToHex(hsv: Hsv): String {
    val c = hsv.v * hsv.s
    val hh = normalizeHue(hsv.h) / 60
    val x = c * (1 - abs(hh % 2 - 1))
    val (r, g, b) = when {
        hh < 1 -> Triple(c, x, 0.0)
        hh < 2 -> Triple(x, c, 0.0)
        hh < 3 -> Triple(0.0, c, x)
        hh < 4 -> Triple(0.0, x, c)
        hh < 5 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    val m = hsv.v - c
    fun byte(v: Double) = (clamp(v + m, 0.0, 1.0) * 255).roundToInt()
    return "#%02x%02x%02x".format(byte(r), byte(g), byte(b))
}

/* ---- Palette ladder ---- */

private class SurfaceSteps(
    val sidebar: Double, val card: Double, val popover: Double, val muted: Double, val input: Double,
    val secondary: Double, val accent: Double, val border: Double,
)

private class BaseSpec(
    val background: Double, val backgroundMin: Double, val backgroundMax: Double,
    val steps: SurfaceSteps, val absolute: Boolean, val borderAlpha: Double,
    val foreground: Double, val mutedForeground: Double,
    val primaryMin: Double, val primaryMax: Double, val neutralPrimary: Double,
)

private val Bases = mapOf(
    ThemeBase.Dark to BaseSpec(
        0.16, 0.08, 0.34, SurfaceSteps(0.025, 0.05, 0.06, 0.10, 0.12, 0.13, 0.15, 0.20),
        false, 0.7, 0.97, 0.72, 0.6, 0.85, 0.92,
    ),
    ThemeBase.Amoled to BaseSpec(
        0.0, 0.0, 0.0, SurfaceSteps(0.10, 0.14, 0.16, 0.20, 0.22, 0.24, 0.26, 0.30),
        true, 0.7, 0.96, 0.70, 0.6, 0.85, 0.92,
    ),
    ThemeBase.Light to BaseSpec(
        0.985, 0.93, 1.0, SurfaceSteps(-0.02, 0.015, 0.015, -0.04, -0.105, -0.055, -0.065, -0.105),
        false, 1.0, 0.22, 0.50, 0.4, 0.62, 0.3,
    ),
)

fun backgroundChroma(tint: Double) = 0.004 + clamp(tint, 0.0, 1.0) * 0.046

fun buildPalette(seed: ThemeSeed): ThemeColors {
    val spec = Bases.getValue(seed.base)
    val isLight = seed.base == ThemeBase.Light
    val isAmoled = seed.base == ThemeBase.Amoled
    val accentHue = normalizeHue(seed.accent.h)
    val surfaceHue = normalizeHue(seed.surface.h)
    val bgC = max(0.0, seed.surface.c)
    val bgL = if (spec.absolute) spec.background else clamp(seed.surface.l, spec.backgroundMin, spec.backgroundMax)

    fun levelOf(step: Double) = clamp(if (spec.absolute) step else bgL + step, 0.0, 1.0)
    fun surface(step: Double, mul: Double = 1.0, alpha: Double = 1.0) = ok(levelOf(step), bgC * mul, surfaceHue, alpha)

    // AMOLED: raised surfaces are tinted less or they read as coloured patches on black; light cards are near white.
    val raisedMul = if (isAmoled) 0.7 else 1.0
    val paperMul = if (isLight) 0.3 else raisedMul * 1.1
    val st = spec.steps

    val textC = if (isLight) min(0.03, bgC * 0.8 + 0.005) else min(0.015, bgC * 0.35)
    val foreground = ok(spec.foreground, textC, surfaceHue)
    val mutedForeground = ok(
        spec.mutedForeground,
        if (isLight) min(0.035, bgC + 0.01) else min(0.03, bgC * 0.8 + 0.005),
        surfaceHue,
    )

    val neutral = seed.accent.c < NEUTRAL_CHROMA
    val primaryL = if (neutral) spec.neutralPrimary else clamp(seed.accent.l, spec.primaryMin, spec.primaryMax)
    val primaryC = fitChroma(primaryL, max(0.0, seed.accent.c), accentHue)
    val primary = ok(primaryL, primaryC, accentHue)
    val primaryForeground = if (primaryL > PRIMARY_DARK_TEXT_FROM) {
        ok(if (isAmoled) 0.12 else 0.18, min(0.04, primaryC * 0.25 + 0.01), accentHue)
    } else {
        ok(0.985, 0.0, 0.0)
    }

    return ThemeColors(
        isLight = isLight,
        background = if (isAmoled) Color.Black else ok(bgL, bgC, surfaceHue),
        foreground = foreground,
        card = surface(st.card, paperMul),
        popover = surface(st.popover, paperMul),
        primary = primary,
        primaryForeground = primaryForeground,
        secondary = surface(st.secondary, raisedMul * 1.1),
        muted = surface(st.muted, raisedMul),
        mutedForeground = mutedForeground,
        destructive = if (isLight) ok(0.6, 0.2, 22.0) else ok(0.66, 0.2, 22.0),
        border = surface(st.border, raisedMul * 1.2, spec.borderAlpha),
        input = surface(st.input, raisedMul),
        sidebar = surface(st.sidebar, raisedMul),
        accent = primary,
        accentForeground = primaryForeground,
    )
}

/* ---- Custom themes ---- */

fun accentHueOf(color: String): Double {
    val o = hexToOklch(color)
    return if (o.c < 0.01) GRAY_HUE else o.h
}

fun surfaceLightness(base: ThemeBase, depth: Double): Double {
    val d = clamp(depth, -1.0, 1.0)
    return when (base) {
        ThemeBase.Light -> 0.975 + d * 0.02
        ThemeBase.Amoled -> 0.0
        ThemeBase.Dark -> 0.16 + d * 0.07
    }
}

/** A user theme: accent colour, base, background tint and depth. Mirrors desktop's CustomTheme seed fields. */
data class CustomTheme(
    val id: String,
    val name: String,
    val color: String,
    val base: ThemeBase,
    /** Background saturation 0..1. */
    val tint: Double = 0.4,
    /** Background hue 0..360, null = same as the accent. */
    val surfaceHue: Double? = null,
    /** Background lightness -1..1. */
    val depth: Double = 0.0,
)

fun CustomTheme.colors(): ThemeColors {
    val accent = hexToOklch(color)
    val hue = accentHueOf(color)
    return buildPalette(
        ThemeSeed(
            base = base,
            accent = accent.copy(h = hue),
            surface = Oklch(surfaceLightness(base, depth), backgroundChroma(tint), surfaceHue ?: hue),
        ),
    )
}

/* ---- Built-in themes ---- */

/** Theme ids as stored in settings; "system", "wallpaper" and "custom:<id>" are resolved elsewhere. */
val BuiltinThemeIds = listOf(
    "light", "dark", "amoled", "cosmic", "purple", "blue", "tokyo", "dracula", "mocha", "latte",
    "nord", "gruvbox", "slate", "stone", "emerald", "sunset", "rose",
)

fun isBuiltinThemeId(id: String) = id in BuiltinThemeIds

val BuiltinThemeTitles = mapOf(
    "light" to "Светлая", "dark" to "Тёмная", "amoled" to "AMOLED", "cosmic" to "Космическая",
    "purple" to "Фиолетовая", "blue" to "Синяя", "tokyo" to "Tokyo Night", "dracula" to "Dracula",
    "mocha" to "Catppuccin Mocha", "latte" to "Catppuccin Latte", "nord" to "Норд", "gruvbox" to "Gruvbox",
    "slate" to "Slate", "stone" to "Stone", "emerald" to "Изумрудная", "sunset" to "Закат", "rose" to "Розовая",
)

private fun seed(base: ThemeBase, bgL: Double, bgC: Double, bgH: Double, accL: Double, accC: Double, accH: Double) =
    ThemeSeed(base, Oklch(accL, accC, accH), Oklch(bgL, bgC, bgH))

private val BuiltinSeeds = mapOf(
    "light" to seed(ThemeBase.Light, 0.985, 0.001, 286.4, 0.546, 0.245, 262.9),
    "dark" to seed(ThemeBase.Dark, 0.141, 0.005, 285.8, 0.488, 0.243, 264.4),
    "amoled" to seed(ThemeBase.Amoled, 0.0, 0.004, 255.0, 0.714, 0.143, 254.6),
    "cosmic" to seed(ThemeBase.Dark, 0.15, 0.035, 265.0, 0.8, 0.13, 200.0),
    "purple" to seed(ThemeBase.Dark, 0.244, 0.05, 300.5, 0.722, 0.177, 305.5),
    "blue" to seed(ThemeBase.Dark, 0.258, 0.03, 259.1, 0.714, 0.143, 254.6),
    "tokyo" to seed(ThemeBase.Dark, 0.226, 0.021, 280.5, 0.719, 0.132, 264.2),
    "dracula" to seed(ThemeBase.Dark, 0.288, 0.022, 277.5, 0.742, 0.149, 301.9),
    "mocha" to seed(ThemeBase.Dark, 0.243, 0.03, 283.9, 0.787, 0.119, 304.8),
    "latte" to seed(ThemeBase.Light, 0.958, 0.006, 264.5, 0.555, 0.25, 297.0),
    "nord" to seed(ThemeBase.Dark, 0.324, 0.023, 264.2, 0.775, 0.062, 217.5),
    "gruvbox" to seed(ThemeBase.Dark, 0.277, 0.004, 85.0, 0.832, 0.159, 83.0),
    "slate" to seed(ThemeBase.Dark, 0.272, 0.016, 264.3, 0.711, 0.035, 256.8),
    "stone" to seed(ThemeBase.Dark, 0.287, 0.007, 67.5, 0.836, 0.035, 78.6),
    "emerald" to seed(ThemeBase.Dark, 0.251, 0.02, 168.9, 0.773, 0.153, 163.2),
    "sunset" to seed(ThemeBase.Dark, 0.239, 0.032, 44.7, 0.758, 0.159, 55.9),
    "rose" to seed(ThemeBase.Dark, 0.211, 0.041, 356.3, 0.719, 0.169, 13.4),
)

/** The classic Beam dark look the app shipped with: lifted zinc surfaces, white buttons, BEAM-blue accent. */
private val ClassicDark = ThemeColors(
    isLight = false,
    background = Color(0xFF0E0E11),
    foreground = Color(0xFFFAFAFA),
    card = Color(0xFF18181B),
    popover = Color(0xFF18181B),
    primary = Color(0xFFE4E4E7),
    primaryForeground = Color(0xFF18181B),
    secondary = Color(0xFF27272A),
    muted = Color(0xFF27272A),
    mutedForeground = Color(0xFF9F9FA9),
    destructive = Color(0xFFFF6467),
    border = Color.White.copy(alpha = 0.10f),
    input = Color.White.copy(alpha = 0.15f),
    sidebar = Color(0xFF121215),
    accent = Color(0xFF1447E6),
    accentForeground = Color.White,
)

/** Light pair of the classic look: neutral zinc, dark buttons, blue only on the accent. */
private val ClassicLight = ThemeColors(
    isLight = true,
    background = ok(0.985, 0.001, 286.375),
    foreground = ok(0.141, 0.005, 285.823),
    card = Color.White,
    popover = Color.White,
    primary = ok(0.21, 0.006, 285.885),
    primaryForeground = ok(0.985, 0.0, 0.0),
    secondary = ok(0.945, 0.003, 286.32),
    muted = ok(0.945, 0.003, 286.32),
    mutedForeground = ok(0.552, 0.016, 285.938),
    destructive = ok(0.577, 0.245, 27.325),
    border = ok(0.9, 0.004, 286.32),
    input = ok(0.9, 0.004, 286.32),
    sidebar = ok(0.96, 0.002, 286.32),
    accent = ok(0.546, 0.245, 262.881),
    accentForeground = ok(0.985, 0.0, 0.0),
)

private val BuiltinCache = HashMap<String, ThemeColors>()

fun builtinColors(id: String): ThemeColors = BuiltinCache.getOrPut(id) {
    when (id) {
        "dark" -> ClassicDark
        "light" -> ClassicLight
        else -> buildPalette(BuiltinSeeds.getValue(id))
    }
}

/** A user accent over a built-in theme: surfaces stay, whatever is coloured in the theme takes the new accent. */
fun builtinColorsWithAccent(id: String, hex: String?): ThemeColors {
    val normal = hex?.let { normalizeHex(it) } ?: return builtinColors(id)
    val accent = hexToOklch(normal).let { it.copy(h = accentHueOf(normal)) }
    if (id == "dark" || id == "light") {
        val base = builtinColors(id)
        val l = clamp(accent.l, 0.45, 0.8)
        return base.copy(
            accent = ok(l, accent.c, accent.h),
            accentForeground = if (l > PRIMARY_DARK_TEXT_FROM) ok(0.21, 0.006, 285.885) else ok(0.985, 0.0, 0.0),
        )
    }
    return buildPalette(BuiltinSeeds.getValue(id).copy(accent = accent))
}

/** The theme's own accent, for the "as in the theme" swatch. */
fun themeAccentHex(id: String): String {
    val c = builtinColors(id).accent
    return colorToHex(c)
}

/** Preferred accent swatches, shared with the desktop editor. */
val AccentSwatches = listOf(
    "#6366f1", "#3b82f6", "#06b6d4", "#14b8a6", "#10b981", "#84cc16",
    "#f59e0b", "#f97316", "#ef4444", "#ec4899", "#a855f7", "#64748b",
)

/** Five chart colours from an accent, as desktop's --chart-1..5: the accent first, then its neighbours on the hue wheel. */
fun chartPalette(accent: Color, isLight: Boolean): List<Color> {
    val o = hexToOklch(colorToHex(accent))
    val neutral = o.c < NEUTRAL_CHROMA
    val l = if (isLight) 0.6 else 0.74
    val c = if (neutral) 0.14 else clamp(o.c, 0.1, 0.16)
    val hues = if (neutral) listOf(25.0, 180.0, 220.0, 80.0, 40.0) else listOf(o.h, o.h + 40, o.h - 40, o.h + 80, o.h + 160)
    return hues.mapIndexed { i, h -> if (i == 0 && !neutral) accent else ok(l, c, normalizeHue(h)) }
}
