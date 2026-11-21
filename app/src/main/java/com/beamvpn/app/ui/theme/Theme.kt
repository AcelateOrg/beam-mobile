@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.beamvpn.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamvpn.app.R
import com.beamvpn.app.settings.AppSettings
import com.beamvpn.app.settings.CustomFonts
import com.beamvpn.app.settings.RadiusOption

/** BEAM tokens that have no Material 3 colour role. */
@Immutable
data class BeamExtraColors(
    val shell: Color = Color(0xFF121215),
    val download: Color = Color(0xFF05DF72).copy(alpha = 0.8f),
    val upload: Color = Color(0xFF51A2FF).copy(alpha = 0.8f),
    /** Desktop --primary: the near-white used for badges, selection borders and highlights. */
    val highlight: Color = Color(0xFFE4E4E7),
    val onHighlight: Color = Color(0xFF18181B),
    /** Desktop --accent / --secondary fills (badges, the "..." button). */
    val accentFill: Color = Color(0xFF27272A),
    /** Desktop --border at full strength. */
    val border: Color = Color.White.copy(alpha = 0.10f),
    val pingGood: Color = Color(0xFF00C758),
    val pingMid: Color = Color(0xFFEDB200),
    val pingBad: Color = Color(0xFFFB2C36),
    val warning: Color = Color(0xFFFDC700),
    val noInternet: Color = Color(0xFFFE6E00),
    val isLight: Boolean = false,
    /** What hairlines and soft overlays are drawn with: white on dark themes, black on light ones. */
    val tint: Color = Color.White,
    /** Corner scale of the chosen radius, 1 = the default 12dp. */
    val radiusScale: Float = 1f,
    /** Five colours for charts, derived from the accent. */
    val charts: List<Color> = chartPalette(Color(0xFF1447E6), false),
)

private val LocalBeamColors = staticCompositionLocalOf { BeamExtraColors() }

object BeamTheme {
    val extra: BeamExtraColors
        @Composable @ReadOnlyComposable get() = LocalBeamColors.current

    /** How rich the decorative animations may be; see [Effects]. */
    val effects: Effects
        @Composable @ReadOnlyComposable get() = LocalEffects.current
}

/** A designed corner radius adjusted to the chosen "Скругление углов" setting. */
@Composable
@ReadOnlyComposable
fun scaled(radius: Dp): Dp = radius * BeamTheme.extra.radiusScale

/** Built-in looks go through the same Material mapping: panel = background, shell = sidebar, cards = surfaceContainer. */
private fun ThemeColors.toScheme(): ColorScheme {
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
            primary = accent,
            onPrimary = accentForeground,
            primaryContainer = accent,
            onPrimaryContainer = accentForeground,
            secondary = primary,
            onSecondary = primaryForeground,
            secondaryContainer = foreground.copy(alpha = 0.15f),
            onSecondaryContainer = foreground,
            background = background,
            onBackground = foreground,
            surface = background,
            onSurface = foreground,
            surfaceVariant = muted,
            onSurfaceVariant = mutedForeground,
            surfaceContainerLowest = background,
            surfaceContainerLow = sidebar,
            surfaceContainer = card,
            surfaceContainerHigh = muted,
            surfaceContainerHighest = muted,
            outline = border,
            outlineVariant = border.copy(alpha = border.alpha * 0.7f),
            error = destructive,
            onError = Color.White,
        )
}

private fun ThemeColors.toExtra(radiusScale: Float) = BeamExtraColors(
    shell = sidebar,
    highlight = primary,
    onHighlight = primaryForeground,
    accentFill = muted,
    border = border,
    isLight = isLight,
    tint = if (isLight) Color.Black else Color.White,
    radiusScale = radiusScale,
    charts = chartPalette(accent, isLight),
)

/**
 * Material You: keep every role of the system's dynamic scheme (so text, containers and the nav indicator
 * carry the wallpaper tint) and only remap the surfaces onto BEAM's layout: panel = surface,
 * shell = surfaceContainerLow, cards = surfaceContainer. Hairlines use onSurface at the desktop's 10%/7%.
 */
private fun wallpaperScheme(d: ColorScheme) = d.copy(
    background = d.surface,
    surfaceContainerLowest = d.surface,
    outline = d.onSurface.copy(alpha = 0.10f),
    outlineVariant = d.onSurface.copy(alpha = 0.07f),
)

private fun wallpaperExtra(d: ColorScheme, light: Boolean, radiusScale: Float) = BeamExtraColors(
    shell = d.surfaceContainerLow,
    highlight = d.primary,
    onHighlight = d.onPrimary,
    accentFill = d.surfaceContainerHighest,
    border = d.onSurface.copy(alpha = 0.10f),
    isLight = light,
    tint = if (light) Color.Black else Color.White,
    radiusScale = radiusScale,
    charts = chartPalette(d.primary, light),
)

/** The colours a theme choice resolves to, or null for "Из обоев" (Material You, taken from the system). */
fun resolveThemeColors(settings: AppSettings, systemDark: Boolean): ThemeColors? {
    val id = settings.themeId
    return when {
        id == "wallpaper" -> null
        id == "system" -> builtinColorsWithAccent(if (systemDark) "dark" else "light", settings.accent)
        id.startsWith("custom:") -> settings.customThemes.firstOrNull { "custom:${it.id}" == id }?.colors()
            ?: builtinColorsWithAccent("dark", settings.accent)
        isBuiltinThemeId(id) -> builtinColorsWithAccent(id, settings.accent)
        else -> builtinColorsWithAccent("dark", settings.accent)
    }
}

/** Whether the current choice draws a light surface; used for the system bars' icon colour. */
@Composable
fun isLightTheme(settings: AppSettings): Boolean {
    val systemDark = isSystemInDarkTheme()
    val colors = resolveThemeColors(settings, systemDark)
    return colors?.isLight ?: (!systemDark)
}

// ---- Typography ----

// beam-desktop ships one static face, Rubik Light (public/rubik.woff2): regular and
// medium text renders as Light, and the browser fakes 600+ from it, which looks
// close to Rubik Medium. Android's fake bold is thinner, so the variable font is
// pinned to those visual weights instead.
@OptIn(ExperimentalTextApi::class)
private fun rubik(weight: FontWeight, visual: Int) = Font(
    R.font.rubik,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(visual)),
)

val Rubik = FontFamily(
    rubik(FontWeight.Light, 300),
    rubik(FontWeight.Normal, 300),
    rubik(FontWeight.Medium, 400),
    rubik(FontWeight.SemiBold, 500),
    rubik(FontWeight.Bold, 500),
    rubik(FontWeight.ExtraBold, 500),
)

private val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_600, FontWeight.SemiBold),
)

@OptIn(ExperimentalTextApi::class)
private fun geologica(weight: FontWeight) = Font(
    R.font.geologica,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Geologica = FontFamily(
    geologica(FontWeight.Light), geologica(FontWeight.Normal), geologica(FontWeight.Medium),
    geologica(FontWeight.SemiBold), geologica(FontWeight.Bold), geologica(FontWeight.ExtraBold),
)

private val Onest = FontFamily(Font(R.font.onest, FontWeight.Normal))

private val Nunito = FontFamily(
    Font(R.font.nunito_400, FontWeight.Normal),
    Font(R.font.nunito_600, FontWeight.SemiBold),
    Font(R.font.nunito_800, FontWeight.ExtraBold),
)

/** Built-in interface fonts, as in desktop's Appearance page. */
enum class BuiltinFont(val id: String, val title: String, val family: FontFamily) {
    Rubik("rubik", "Rubik", com.beamvpn.app.ui.theme.Rubik),
    Manrope("manrope", "Manrope", com.beamvpn.app.ui.theme.Manrope),
    Geologica("geologica", "Geologica", com.beamvpn.app.ui.theme.Geologica),
    Onest("onest", "Onest", com.beamvpn.app.ui.theme.Onest),
    Nunito("nunito", "Nunito", com.beamvpn.app.ui.theme.Nunito),
}

private fun TextStyle.withFamily(f: FontFamily) = copy(fontFamily = f)

private fun typographyOf(f: FontFamily) = Typography().run {
    copy(
        displayLarge = displayLarge.withFamily(f), displayMedium = displayMedium.withFamily(f), displaySmall = displaySmall.withFamily(f),
        headlineLarge = headlineLarge.withFamily(f), headlineMedium = headlineMedium.withFamily(f), headlineSmall = headlineSmall.withFamily(f),
        titleLarge = titleLarge.withFamily(f).copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.withFamily(f).copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.withFamily(f),
        bodyLarge = bodyLarge.withFamily(f), bodyMedium = bodyMedium.withFamily(f), bodySmall = bodySmall.withFamily(f),
        labelLarge = labelLarge.withFamily(f), labelMedium = labelMedium.withFamily(f), labelSmall = labelSmall.withFamily(f),
    )
}

// Corner radius 12dp by default (desktop --radius 0.75rem): sm = r-4, md = r-2, lg = r+4 (cards and the content
// panel), xl = r+8. The setting moves r; designed one-off corners go through [scaled].
private fun shapesOf(radius: Dp) = Shapes(
    extraSmall = RoundedCornerShape(radius * 0.5f),
    small = RoundedCornerShape((radius - 2.dp).coerceAtLeast(2.dp)),
    medium = RoundedCornerShape(radius),
    large = RoundedCornerShape(radius + 4.dp),
    extraLarge = RoundedCornerShape(radius + 8.dp),
)

@Composable
fun BeamTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val radius = settings.radius.dp
    val radiusScale = radius.value / RadiusOption.Medium.dp.value

    val colors = resolveThemeColors(settings, systemDark)
    val (scheme, extra) = if (colors != null) {
        colors.toScheme() to colors.toExtra(radiusScale)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        wallpaperScheme(dynamic) to wallpaperExtra(dynamic, !systemDark, radiusScale)
    } else {
        // Material You needs Android 12; older phones fall back to the classic dark look.
        val fallback = builtinColors("dark")
        fallback.toScheme() to fallback.toExtra(radiusScale)
    }

    val family = remember(settings.font, settings.customFonts) { fontFamilyOf(context, settings) }
    val effects = rememberEffects(settings.reducedEffects)
    CompositionLocalProvider(LocalBeamColors provides extra, LocalEffects provides effects) {
        // Material 3 Expressive: the spring motion scheme for every Material component.
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            typography = remember(family) { typographyOf(family) },
            shapes = remember(radius) { shapesOf(radius) },
            content = content,
        )
    }
}

private fun fontFamilyOf(context: android.content.Context, settings: AppSettings): FontFamily {
    val id = settings.font
    if (id.startsWith("custom:")) {
        CustomFonts.file(context, id.removePrefix("custom:"))?.let { file ->
            // One file stands for every weight; bold is synthesised, as for desktop's own fonts.
            runCatching { return FontFamily(Font(file, FontWeight.Normal)) }
        }
        return Rubik
    }
    return BuiltinFont.entries.firstOrNull { it.id == id }?.family ?: Rubik
}
