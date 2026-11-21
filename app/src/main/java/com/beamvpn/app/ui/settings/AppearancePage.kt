package com.beamvpn.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamvpn.app.settings.AnimationOption
import com.beamvpn.app.settings.AppSettings
import com.beamvpn.app.settings.CustomFonts
import com.beamvpn.app.settings.RadiusOption
import com.beamvpn.app.settings.SettingsStore
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.AccentSwatches
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.BuiltinFont
import com.beamvpn.app.ui.theme.BuiltinThemeIds
import com.beamvpn.app.ui.theme.BuiltinThemeTitles
import com.beamvpn.app.ui.theme.CustomTheme
import com.beamvpn.app.ui.theme.Hsv
import com.beamvpn.app.ui.theme.ThemeBase
import com.beamvpn.app.ui.theme.ThemeColors
import com.beamvpn.app.ui.theme.builtinColors
import com.beamvpn.app.ui.theme.builtinColorsWithAccent
import com.beamvpn.app.ui.theme.colors
import com.beamvpn.app.ui.theme.hexToColor
import com.beamvpn.app.ui.theme.hexToHsv
import com.beamvpn.app.ui.theme.hsvToHex
import com.beamvpn.app.ui.theme.isBuiltinThemeId
import com.beamvpn.app.ui.theme.normalizeHex
import com.beamvpn.app.ui.theme.scaled
import com.beamvpn.app.ui.theme.themeAccentHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val INITIAL_VISIBLE_THEMES = 8

// ---- The page ----

/** Port of desktop's Appearance page without the backdrop image: themes (with Material You), accent, font, corners, transitions. */
@Composable
fun AppearancePage(settings: AppSettings, store: SettingsStore) {
    LayoutCard(settings, store)
    ThemesCard(settings, store)
    AccentCard(settings, store)
    FontCard(settings, store)
    RadiusCard(settings, store)
    AnimationCard(settings, store)
    EffectsCard(settings, store)
}

@Composable
private fun EffectsCard(settings: AppSettings, store: SettingsStore) {
    val auto = BeamTheme.effects.autoReason
    SettingsCard(BeamIcons.Star, "Эффекты", "Свечение, тени и частицы кнопки подключения.") {
        SwitchRow(
            BeamIcons.Star,
            "Облегчённые эффекты",
            if (auto != null && !settings.reducedEffects) "Сейчас включены автоматически: ${auto.title}"
            else "Без размытия и частиц, если анимация подтормаживает",
            settings.reducedEffects,
        ) { on -> store.update { it.copy(reducedEffects = on) } }
    }
}

/** The home screen's layout, chosen by a picture of each: plain words alone did not say what "compact" changes. */
@Composable
private fun LayoutCard(settings: AppSettings, store: SettingsStore) {
    SettingsCard(BeamIcons.LayoutList, "Вид главного экрана", "Как устроены главная и нижняя панель.") {
        LayoutPicker(settings, store, Modifier.padding(12.dp))
        RowDivider()
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Нижняя панель", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionTile(
                    "Классическая",
                    "Вплотную к экрану, для любителей классики",
                    !settings.floatingNav,
                    Modifier.weight(1f),
                ) { store.update { it.copy(floatingNav = false) } }
                OptionTile(
                    "Островками",
                    "Плавающая, с кнопкой «назад»",
                    settings.floatingNav,
                    Modifier.weight(1f),
                ) { store.update { it.copy(floatingNav = true) } }
            }
        }
    }
}

/** Tabs or compact, each shown as a tiny phone; shared by Appearance and the first-run wizard. */
@Composable
fun LayoutPicker(settings: AppSettings, store: SettingsStore, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LayoutOption(
            title = "По вкладкам",
            caption = "Профили и серверы на своих вкладках",
            compact = false,
            selected = !settings.compactMode,
            modifier = Modifier.weight(1f),
        ) { store.update { it.copy(compactMode = false) } }
        LayoutOption(
            title = "Компактный",
            caption = "Всё на главной, две вкладки внизу",
            compact = true,
            selected = settings.compactMode,
            modifier = Modifier.weight(1f),
        ) { store.update { it.copy(compactMode = true) } }
    }
}

@Composable
private fun LayoutOption(title: String, caption: String, compact: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val extra = BeamTheme.extra
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(scaled(16.dp)),
        color = if (selected) extra.highlight.copy(alpha = 0.08f) else colors.surfaceContainer.copy(alpha = 0.4f),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) extra.highlight.copy(alpha = 0.7f) else extra.border),
        modifier = modifier,
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LayoutPreview(compact, selected, Modifier.fillMaxWidth().aspectRatio(0.72f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (selected) Icon(BeamIcons.Check, null, Modifier.size(14.dp), tint = extra.highlight)
            }
            Text(caption, fontSize = 11.sp, lineHeight = 14.sp, color = colors.onSurfaceVariant)
        }
    }
}

/** A tiny phone: the connect button, what sits under it, and the tab bar with its number of tabs. */
@Composable
private fun LayoutPreview(compact: Boolean, selected: Boolean, modifier: Modifier) {
    val extra = BeamTheme.extra
    val colors = MaterialTheme.colorScheme
    val accent = if (selected) extra.highlight else colors.onSurfaceVariant
    val frame = colors.onSurfaceVariant.copy(alpha = 0.35f)
    val block = colors.onSurfaceVariant.copy(alpha = 0.18f)
    val screen = colors.surfaceVariant.copy(alpha = 0.35f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = w * 0.08f
        drawRoundRect(screen, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        drawRoundRect(frame, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(1.dp.toPx()))
        val pad = w * 0.1f
        val barH = h * 0.12f
        val barTop = h - barH
        // The connect button.
        val btnR = if (compact) w * 0.13f else w * 0.2f
        val btnY = if (compact) h * 0.17f else h * 0.32f
        drawCircle(accent.copy(alpha = 0.25f), btnR * 1.25f, Offset(w / 2, btnY))
        drawCircle(accent, btnR, Offset(w / 2, btnY))
        val cr = androidx.compose.ui.geometry.CornerRadius(w * 0.03f)
        if (compact) {
            // Profiles with their servers, straight under the button.
            var y = h * 0.36f
            repeat(2) { card ->
                val rows = if (card == 0) 3 else 2
                val cardH = h * 0.045f * (rows + 1) + h * 0.02f
                drawRoundRect(block, Offset(pad, y), androidx.compose.ui.geometry.Size(w - 2 * pad, cardH), cr)
                drawRoundRect(accent.copy(alpha = 0.5f), Offset(pad * 1.6f, y + h * 0.018f), androidx.compose.ui.geometry.Size(w * 0.35f, h * 0.025f), cr)
                repeat(rows) { i ->
                    val ry = y + h * 0.06f + i * h * 0.045f
                    drawRoundRect(block, Offset(pad * 1.6f, ry), androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.022f), cr)
                }
                y += cardH + h * 0.025f
            }
        } else {
            // Status lines and a single server card.
            drawRoundRect(block, Offset(w * 0.3f, h * 0.55f), androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.03f), cr)
            drawRoundRect(block, Offset(w * 0.22f, h * 0.61f), androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.022f), cr)
            drawRoundRect(block, Offset(pad, h * 0.7f), androidx.compose.ui.geometry.Size(w - 2 * pad, h * 0.1f), cr)
        }
        // Tab bar: four tabs or two.
        drawLine(frame, Offset(0f, barTop), Offset(w, barTop), 1.dp.toPx())
        val tabs = if (compact) 2 else 4
        repeat(tabs) { i ->
            val cx = w * (i + 0.5f) / tabs
            drawCircle(if (i == 0) accent else frame, w * 0.035f, Offset(cx, barTop + barH / 2))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val line = Modifier.weight(1f).height(1.dp).background(BeamTheme.extra.border)
        Box(line)
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(line)
    }
}

// ---- Themes ----

/** A theme choice with the gradient shown on its tile. */
private class ThemeOption(val id: String, val title: String, val preview: Brush, val enabled: Boolean = true)

private fun previewBrush(c: ThemeColors) =
    Brush.linearGradient(listOf(c.background, c.sidebar, lerp(c.sidebar, c.accent, 0.45f)), Offset.Zero, Offset(400f, 400f))

@Composable
private fun themeOptions(accent: String?): List<ThemeOption> {
    val context = LocalContext.current
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dark = isSystemInDarkTheme()
    val wallpaper = if (dynamicOk) {
        val d = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        Brush.linearGradient(listOf(d.surface, d.surfaceContainerLow, lerp(d.surfaceContainerLow, d.primary, 0.45f)), Offset.Zero, Offset(400f, 400f))
    } else {
        Brush.linearGradient(listOf(Color(0xFF0E1A1F), Color(0xFF2B3A40), Color(0xFF8FA3AA)))
    }
    val light = builtinColors("light")
    val night = builtinColors("dark")
    val system = Brush.linearGradient(
        0f to light.background, 0.495f to light.sidebar, 0.505f to night.sidebar, 1f to night.background,
        start = Offset.Zero, end = Offset(400f, 400f),
    )
    return buildList {
        add(ThemeOption("wallpaper", "Из обоев", wallpaper, enabled = dynamicOk))
        add(ThemeOption("system", "Системная", system))
        BuiltinThemeIds.forEach { id ->
            add(ThemeOption(id, BuiltinThemeTitles.getValue(id), previewBrush(builtinColorsWithAccent(id, accent))))
        }
    }
}

/** Desktop theme tile: a gradient preview of the palette over its name; the chosen one gets a light ring. */
@Composable
fun ThemeTile(
    title: String,
    preview: Brush,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    onClick: () -> Unit,
) {
    val extra = BeamTheme.extra
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(scaled(20.dp)),
        color = if (selected) extra.highlight.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) extra.highlight.copy(alpha = 0.8f) else extra.border.let { it.copy(alpha = it.alpha * 0.6f) }),
        modifier = modifier,
    ) {
        Column(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(scaled(14.dp)))
                    .background(preview)
                    .border(1.dp, extra.tint.copy(alpha = 0.08f), RoundedCornerShape(scaled(14.dp))),
                content = overlay,
            )
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
            )
        }
    }
}

/** The ready-made themes (and optionally the user's own) as a two-column grid; used by the page and the onboarding. */
@Composable
fun ThemeGrid(settings: AppSettings, store: SettingsStore, limit: Int? = null) {
    val options = themeOptions(settings.accent)
    val shown = if (limit != null) options.take(limit) else options
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { o ->
                    ThemeTile(o.title, o.preview, settings.themeId == o.id, Modifier.weight(1f), o.enabled) {
                        store.update { it.copy(themeId = o.id) }
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemesCard(settings: AppSettings, store: SettingsStore) {
    val options = themeOptions(settings.accent)
    var expanded by rememberSaveable {
        mutableStateOf(options.indexOfFirst { it.id == settings.themeId }.let { it == -1 || it >= INITIAL_VISIBLE_THEMES })
    }
    var editing by remember { mutableStateOf<CustomTheme?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    val visible = if (expanded) options else options.take(INITIAL_VISIBLE_THEMES)

    SettingsCard(BeamIcons.Palette, "Темы", "Выберите визуальный стиль приложения или создайте свой.") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Готовые")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visible.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { o ->
                            ThemeTile(o.title, o.preview, settings.themeId == o.id, Modifier.weight(1f), o.enabled) {
                                store.update { it.copy(themeId = o.id) }
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Text("«Из обоев» работает на Android 12 и новее.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(scaled(12.dp)),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (expanded) "Свернуть" else "Показать ещё", fontSize = 13.sp) }

            SectionLabel("Пользовательские")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (settings.customThemes.map { it as CustomTheme? } + null).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { custom ->
                            if (custom == null) {
                                AddThemeTile(Modifier.weight(1f)) { editing = null; editorOpen = true }
                            } else {
                                val id = "custom:${custom.id}"
                                ThemeTile(
                                    custom.name,
                                    previewBrush(custom.colors()),
                                    settings.themeId == id,
                                    Modifier.weight(1f),
                                    overlay = {
                                        Row(Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TileAction(BeamIcons.Settings, "Редактировать") { editing = custom; editorOpen = true }
                                            TileAction(BeamIcons.Trash, "Удалить", destructive = true) { store.deleteCustomTheme(custom.id) }
                                        }
                                    },
                                ) { store.update { it.copy(themeId = id) } }
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (editorOpen) {
        ThemeEditorSheet(
            initial = editing,
            onDismiss = { editorOpen = false },
            onSave = { theme ->
                store.saveCustomTheme(theme)
                store.update { it.copy(themeId = "custom:${theme.id}") }
                editorOpen = false
            },
        )
    }
}

@Composable
private fun TileAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(14.dp), tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AddThemeTile(modifier: Modifier, onClick: () -> Unit) {
    val extra = BeamTheme.extra
    val shape = RoundedCornerShape(scaled(20.dp))
    Column(
        modifier
            .heightIn(min = 120.dp)
            .clip(shape)
            .border(1.dp, extra.border, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(BeamIcons.Plus, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Новая тема", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---- Accent ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentCard(settings: AppSettings, store: SettingsStore) {
    val applies = settings.themeId != "wallpaper" && !settings.themeId.startsWith("custom:")
    val accent = settings.accent
    val themeId = if (isBuiltinThemeId(settings.themeId)) settings.themeId else "dark"
    val themeColor = hexToColor(themeAccentHex(themeId))
    val isPreset = accent != null && accent in AccentSwatches
    val isCustom = accent != null && !isPreset
    var picking by remember { mutableStateOf(false) }

    SettingsCard(BeamIcons.Palette, "Акцентный цвет", "Кнопки, выделение и графики. Тема остаётся та же.") {
        Column(Modifier.padding(16.dp).then(if (applies) Modifier else Modifier.background(Color.Transparent)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatch(Brush.linearGradient(listOf(themeColor, themeColor)), accent == null, applies, "Как в теме") {
                    store.update { it.copy(accent = null) }
                }
                AccentSwatches.forEach { hex ->
                    val c = hexToColor(hex)
                    Swatch(Brush.linearGradient(listOf(c, c)), accent == hex, applies, hex) { store.update { it.copy(accent = hex) } }
                }
                val sweep = Brush.sweepGradient(
                    listOf(Color(0xFFF43F5E), Color(0xFFF59E0B), Color(0xFF84CC16), Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFF43F5E)),
                )
                val custom = if (isCustom) hexToColor(accent!!) else null
                Swatch(if (custom != null) Brush.linearGradient(listOf(custom, custom)) else sweep, isCustom, applies, "Свой цвет") { picking = true }
            }
            if (!applies) {
                Text(
                    if (settings.themeId == "wallpaper") "«Из обоев» берёт акцент из цветов системы." else "У своей темы акцент задаётся в её редакторе.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (picking) {
        ColorPickerDialog(
            initial = accent ?: themeAccentHex(themeId),
            onDismiss = { picking = false },
            onPick = { hex -> store.update { it.copy(accent = hex) }; picking = false },
        )
    }
}

@Composable
private fun Swatch(fill: Brush, selected: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(fill)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else BeamTheme.extra.tint.copy(alpha = 0.1f), CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(BeamIcons.Check, null, Modifier.size(16.dp), tint = Color.White)
    }
}

// ---- Colour picker ----

/** A track painted with [brush] and a draggable thumb; [value] is 0..1. */
@Composable
private fun GradientSlider(value: Float, onChange: (Float) -> Unit, brush: Brush, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange((down.position.x / size.width).coerceIn(0f, 1f))
                    drag(down.id) { change ->
                        onChange((change.position.x / size.width).coerceIn(0f, 1f))
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(brush))
        val thumb = 24.dp
        Box(
            Modifier
                .offset { IntOffset(((maxWidth - thumb).toPx() * value).roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
        )
    }
}

private val HueBrush = Brush.horizontalGradient(List(13) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) })

@Composable
private fun ColorPicker(hsv: Hsv, onChange: (Hsv) -> Unit) {
    val hue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv.h.toFloat(), 1f, 1f)))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientSlider(hsv.h.toFloat() / 360f, { onChange(hsv.copy(h = it * 360.0)) }, HueBrush)
        GradientSlider(hsv.s.toFloat(), { onChange(hsv.copy(s = it.toDouble())) }, Brush.horizontalGradient(listOf(Color.White, hue)))
        GradientSlider(hsv.v.toFloat(), { onChange(hsv.copy(v = it.toDouble())) }, Brush.horizontalGradient(listOf(Color.Black, hue)))
    }
}

/** HSV sliders and a hex field; the colour is applied with "Готово". */
@Composable
fun ColorPickerDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var hsv by remember { mutableStateOf(hexToHsv(initial)) }
    var hexText by remember { mutableStateOf(normalizeHex(initial) ?: "#3b82f6") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Свой цвет") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ColorPicker(hsv) { next ->
                    hsv = next
                    hexText = hsvToHex(next)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(hexToColor(hsvToHex(hsv))).border(1.dp, BeamTheme.extra.border, RoundedCornerShape(10.dp)))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text
                            normalizeHex(text)?.let { hsv = hexToHsv(it) }
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(hsvToHex(hsv)) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ---- Custom theme editor ----

@Composable
internal fun <T> Segmented(options: List<Pair<T, String>>, selected: T, compact: Boolean = false, onSelect: (T) -> Unit) =
    ToggleGroup(options, selected, fill = !compact, onSelect = onSelect)

@Composable
private fun LabeledSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = BeamTheme.extra.highlight, activeTrackColor = BeamTheme.extra.highlight),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ThemeEditorSheet(initial: CustomTheme?, onDismiss: () -> Unit, onSave: (CustomTheme) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial?.name ?: "Моя тема") }
    var color by remember { mutableStateOf(initial?.color ?: "#6366f1") }
    var base by remember { mutableStateOf(initial?.base ?: ThemeBase.Dark) }
    var tint by remember { mutableStateOf((initial?.tint ?: 0.4).toFloat()) }
    var ownHue by remember { mutableStateOf(initial?.surfaceHue != null) }
    var surfaceHue by remember { mutableStateOf((initial?.surfaceHue ?: 260.0).toFloat()) }
    var depth by remember { mutableStateOf((initial?.depth ?: 0.0).toFloat()) }
    var tab by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf(false) }

    val draft = CustomTheme(
        id = initial?.id ?: SettingsStore.newId(),
        name = name.trim().ifEmpty { "Тема" },
        color = color,
        base = base,
        tint = tint.toDouble(),
        surfaceHue = if (ownHue) surfaceHue.toDouble() else null,
        depth = depth.toDouble(),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (initial == null) "Новая тема" else "Редактировать тему", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(scaled(16.dp)))
                    .background(previewBrush(draft.colors()))
                    .border(1.dp, BeamTheme.extra.border, RoundedCornerShape(scaled(16.dp))),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text("Название") },
                singleLine = true,
                shape = RoundedCornerShape(scaled(14.dp)),
                modifier = Modifier.fillMaxWidth(),
            )
            Segmented(listOf(0 to "Акцент", 1 to "Фон"), tab) { tab = it }
            if (tab == 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentSwatches.forEach { hex ->
                        val c = hexToColor(hex)
                        Swatch(Brush.linearGradient(listOf(c, c)), color == hex, true, hex) { color = hex }
                    }
                    val custom = color !in AccentSwatches
                    val c = hexToColor(color)
                    Swatch(
                        if (custom) Brush.linearGradient(listOf(c, c)) else Brush.sweepGradient(List(7) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 0.8f, 0.95f))) }),
                        custom, true, "Свой цвет",
                    ) { picking = true }
                }
            } else {
                Segmented(listOf(ThemeBase.Dark to "Тёмная", ThemeBase.Light to "Светлая", ThemeBase.Amoled to "AMOLED"), base) { base = it }
                LabeledSlider("Насыщенность фона", tint, 0f..1f) { tint = it }
                Segmented(listOf(false to "Как у акцента", true to "Свой оттенок"), ownHue) { ownHue = it }
                if (ownHue) GradientSlider(surfaceHue / 360f, { surfaceHue = it * 360f }, HueBrush)
                if (base != ThemeBase.Amoled) LabeledSlider("Глубина: темнее — светлее", depth, -1f..1f) { depth = it }
            }
            val scope = rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { sheet.hide(); onDismiss() } },
                    shape = RoundedCornerShape(scaled(14.dp)),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Отмена") }
                Button(
                    onClick = { onSave(draft) },
                    shape = RoundedCornerShape(scaled(14.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = BeamTheme.extra.highlight, contentColor = BeamTheme.extra.onHighlight),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Сохранить", fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (picking) {
        ColorPickerDialog(initial = color, onDismiss = { picking = false }, onPick = { color = it; picking = false })
    }
}

// ---- Font ----

@Composable
private fun FontTile(label: String, family: FontFamily, selected: Boolean, modifier: Modifier = Modifier, onDelete: (() -> Unit)? = null, onClick: () -> Unit) {
    val extra = BeamTheme.extra
    val shape = RoundedCornerShape(scaled(14.dp))
    Box(modifier) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = if (selected) extra.highlight.copy(alpha = 0.06f) else Color.Transparent,
            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) extra.highlight.copy(alpha = 0.8f) else extra.border),
            modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
        ) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Привет", fontFamily = family, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (onDelete != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { TileAction(BeamIcons.Trash, "Удалить шрифт", destructive = true, onClick = onDelete) }
        }
    }
}

@Composable
private fun FontCard(settings: AppSettings, store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Шрифт"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) null else name to bytes
                }.getOrNull()
            }
            error = if (read == null) "Не удалось прочитать файл" else store.addCustomFont(read.first, read.second)
        }
    }

    SettingsCard(BeamIcons.Palette, "Шрифт интерфейса", "Шрифт для отображения текста во всём приложении.") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val tiles = buildList<@Composable (Modifier) -> Unit> {
                BuiltinFont.entries.forEach { f ->
                    add { m -> FontTile(f.title, f.family, settings.font == f.id, m) { store.update { it.copy(font = f.id) } } }
                }
                settings.customFonts.forEach { f ->
                    add { m ->
                        val family = remember(f.id) {
                            CustomFonts.file(context, f.id)?.let { file -> runCatching { FontFamily(Font(file, FontWeight.Normal)) }.getOrNull() } ?: FontFamily.Default
                        }
                        FontTile(f.name, family, settings.font == "custom:${f.id}", m, onDelete = { store.deleteCustomFont(f.id) }) {
                            store.update { it.copy(font = "custom:${f.id}") }
                        }
                    }
                }
                add { m ->
                    Column(
                        m
                            .heightIn(min = 84.dp)
                            .clip(RoundedCornerShape(scaled(14.dp)))
                            .border(1.dp, BeamTheme.extra.border, RoundedCornerShape(scaled(14.dp)))
                            .clickable { pick.launch(arrayOf("*/*")) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(BeamIcons.Plus, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Свой шрифт", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile -> tile(Modifier.weight(1f)) }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            Text("TTF или OTF до 10 МБ.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        }
    }
}

// ---- Corners and transitions ----

/** A corner drawn with Apple-style continuous rounding, like desktop's preview. */
@Composable
private fun CornerPreview(r: Int, color: Color) {
    Canvas(Modifier.size(44.dp)) {
        val k = size.width / 48f
        val o = 8f
        val end = 44f
        val reach = r * 1.28f
        val line = Path().apply {
            moveTo(o * k, end * k)
            lineTo(o * k, (o + reach) * k)
            cubicTo(o * k, (o + r * 0.38f) * k, (o + r * 0.38f) * k, o * k, (o + reach) * k, o * k)
            lineTo(end * k, o * k)
        }
        val fill = Path().apply { addPath(line); lineTo(end * k, end * k); close() }
        drawPath(fill, color.copy(alpha = 0.08f))
        drawPath(line, color, style = Stroke(3f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun RadiusCard(settings: AppSettings, store: SettingsStore) {
    SettingsCard(BeamIcons.Sliders, "Скругление углов", "Радиус скругления кнопок, карточек и элементов интерфейса.") {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadiusOption.entries.forEach { option ->
                val on = settings.radius == option
                val tint = if (on) BeamTheme.extra.highlight else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    onClick = { store.update { it.copy(radius = option) } },
                    shape = RoundedCornerShape(scaled(12.dp)),
                    color = if (on) BeamTheme.extra.highlight.copy(alpha = 0.06f) else Color.Transparent,
                    border = BorderStroke(if (on) 1.5.dp else 1.dp, if (on) BeamTheme.extra.highlight.copy(alpha = 0.8f) else BeamTheme.extra.border),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CornerPreview(option.previewCorner, tint)
                        Text(option.title, fontSize = 11.sp, lineHeight = 14.sp, color = tint, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimationCard(settings: AppSettings, store: SettingsStore) {
    SettingsCard(BeamIcons.Zap, "Анимация переходов", "Стиль анимации при смене вкладок.") {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimationOption.entries.forEach { option ->
                val on = settings.animation == option
                Surface(
                    onClick = { store.update { it.copy(animation = option) } },
                    shape = RoundedCornerShape(scaled(14.dp)),
                    color = if (on) BeamTheme.extra.highlight.copy(alpha = 0.06f) else Color.Transparent,
                    border = BorderStroke(if (on) 1.5.dp else 1.dp, if (on) BeamTheme.extra.highlight.copy(alpha = 0.8f) else BeamTheme.extra.border),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(option.glyph, fontSize = 20.sp, lineHeight = 22.sp)
                        Text(option.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(option.caption, fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}
