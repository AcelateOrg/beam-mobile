package com.beamvpn.app.settings

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beamvpn.app.ui.theme.CustomTheme
import com.beamvpn.app.ui.theme.ThemeBase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class PingMethod(val title: String) { Tcp("TCP"), Http("HTTP") }

enum class DnsPreset(val title: String, val caption: String) {
    Google("Google", "8.8.8.8"),
    Cloudflare("Cloudflare", "1.1.1.1"),
    Quad9("Quad9", "9.9.9.9"),
    AdGuard("AdGuard", "Без рекламы"),
    Yandex("Яндекс", "77.88.8.8"),
    /** The user's own resolvers, [AppSettings.dnsPrimary] and [AppSettings.dnsSecondary]. */
    Custom("Свой", "Указать адрес"),
}

enum class Fragmentation(val title: String, val caption: String) {
    Off("Выкл", "Как есть"),
    Standard("Обычная", "Подходит почти всем"),
    Strong("Сильная", "Если не помогает"),
    Custom("Свои", "Вручную"),
}

/** Fragment parameters for [Fragmentation.Custom], as beam-bridge's FragmentSettings returns them. */
data class FragmentCustom(
    /** "tlshello" or a packet range like "1-3". */
    val packets: String = "tlshello",
    /** Bytes, "100-200". */
    val length: String = "100-200",
    /** Milliseconds, "10-20". */
    val interval: String = "10-20",
    /** Optional range; empty for none. */
    val maxSplit: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("packets", packets).put("length", length).put("interval", interval).put("maxSplit", maxSplit)

    companion object {
        fun parse(json: String?): FragmentCustom? = runCatching {
            val o = JSONObject(json ?: return null)
            val d = FragmentCustom()
            FragmentCustom(o.optString("packets", d.packets), o.optString("length", d.length), o.optString("interval", d.interval), o.optString("maxSplit"))
        }.getOrNull()
    }
}

/** "Скругление углов": the base corner radius, desktop's --radius. */
enum class RadiusOption(val title: String, val dp: Dp, val previewCorner: Int) {
    Small("Маленькое", 4.dp, 2),
    Medium("Среднее", 12.dp, 9),
    Large("Большое", 15.dp, 16),
    Round("Круглое", 20.dp, 26),
}

/** "Анимация переходов" between the app's tabs. */
enum class AnimationOption(val title: String, val caption: String, val glyph: String) {
    Slide("Слайд", "Справа", "→"),
    SlideUp("Снизу", "Снизу вверх", "↑"),
    Fade("Фейд", "Плавное", "◎"),
    None("Нет", "Мгновенно", "—"),
}

/** A font the user added from a .ttf/.otf file; the file lives in the app's private storage. */
data class CustomFont(val id: String, val name: String)

data class AppSettings(
    /** "system", "wallpaper" (Material You), a built-in id, or "custom:<id>". */
    val themeId: String = "dark",
    /** Accent override as "#rrggbb" over a built-in theme; null = the theme's own. */
    val accent: String? = null,
    /** A [com.beamvpn.app.ui.theme.BuiltinFont] id or "custom:<id>". */
    val font: String = "rubik",
    val radius: RadiusOption = RadiusOption.Medium,
    val animation: AnimationOption = AnimationOption.SlideUp,
    /** Profiles and their servers live on the home screen and their tabs leave the bottom bar. */
    val compactMode: Boolean = false,
    /** The bottom bar as a floating island (with its own back button) instead of the classic edge-to-edge bar. */
    val floatingNav: Boolean = true,
    /** "Облегчённые эффекты": no blur, particles or heavy glow on the connect button. */
    val reducedEffects: Boolean = false,
    val customThemes: List<CustomTheme> = emptyList(),
    val customFonts: List<CustomFont> = emptyList(),
    val connectOnLaunch: Boolean = false,
    /** Rebuilds the core when the tunnel stops passing traffic or the phone changes networks. */
    val autoReconnect: Boolean = true,
    val autoUpdateSubscriptions: Boolean = true,
    val serverCheck: PingMethod = PingMethod.Tcp,
    val connectionPing: PingMethod = PingMethod.Http,
    val sendHwid: Boolean = true,
    /** Send [hwidValue] instead of the device hash. */
    val hwidManual: Boolean = false,
    val hwidValue: String = "",
    val dns: DnsPreset = DnsPreset.Cloudflare,
    val dnsPrimary: String = "",
    /** Optional; empty means only [dnsPrimary]. */
    val dnsSecondary: String = "",
    /** Use the DNS of the profile's own Xray config; [dns] stays the fallback. */
    val profileDns: Boolean = false,
    val fragmentation: Fragmentation = Fragmentation.Off,
    val fragmentCustom: FragmentCustom = FragmentCustom(),
    val mux: Boolean = false,
    val ipv6: Boolean = false,
    /** The first-run wizard was finished or skipped. */
    val onboardingDone: Boolean = false,
)

/** App preferences in SharedPreferences, exposed as a StateFlow. Bridge-backed options will move to beam-bridge later. */
class SettingsStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("beam_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        write(_settings.value)
    }

    fun saveCustomTheme(theme: CustomTheme) = update { s ->
        val exists = s.customThemes.any { it.id == theme.id }
        s.copy(customThemes = if (exists) s.customThemes.map { if (it.id == theme.id) theme else it } else s.customThemes + theme)
    }

    fun deleteCustomTheme(id: String) = update { s ->
        s.copy(
            customThemes = s.customThemes.filterNot { it.id == id },
            themeId = if (s.themeId == "custom:$id") "dark" else s.themeId,
        )
    }

    /** Copies a font file into app storage and selects it. Returns an error message or null. */
    fun addCustomFont(displayName: String, bytes: ByteArray): String? {
        val font = CustomFonts.save(context, displayName, bytes) ?: return "Не удалось прочитать шрифт. Подойдут файлы TTF и OTF до 10 МБ."
        update { it.copy(customFonts = it.customFonts + font, font = "custom:${font.id}") }
        return null
    }

    fun deleteCustomFont(id: String) {
        CustomFonts.delete(context, id)
        update { s -> s.copy(customFonts = s.customFonts.filterNot { it.id == id }, font = if (s.font == "custom:$id") "rubik" else s.font) }
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeId = prefs.getString("themeId", null) ?: legacyTheme(prefs.getString("palette", null)),
            accent = prefs.getString("accent", null),
            font = prefs.getString("font", null) ?: d.font,
            radius = enumOr(prefs.getString("radius", null), d.radius),
            animation = enumOr(prefs.getString("animation", null), d.animation),
            compactMode = prefs.getBoolean("compactMode", d.compactMode),
            floatingNav = prefs.getBoolean("floatingNav", d.floatingNav),
            reducedEffects = prefs.getBoolean("reducedEffects", d.reducedEffects),
            customThemes = parseThemes(prefs.getString("customThemes", null)),
            customFonts = parseFonts(prefs.getString("customFonts", null)),
            connectOnLaunch = prefs.getBoolean("connectOnLaunch", d.connectOnLaunch),
            autoReconnect = prefs.getBoolean("autoReconnect", d.autoReconnect),
            autoUpdateSubscriptions = prefs.getBoolean("autoUpdateSubscriptions", d.autoUpdateSubscriptions),
            serverCheck = enumOr(prefs.getString("serverCheck", null), d.serverCheck),
            connectionPing = enumOr(prefs.getString("connectionPing", null), d.connectionPing),
            sendHwid = prefs.getBoolean("sendHwid", d.sendHwid),
            hwidManual = prefs.getBoolean("hwidManual", d.hwidManual),
            hwidValue = prefs.getString("hwidValue", null) ?: d.hwidValue,
            dns = enumOr(prefs.getString("dns", null), d.dns),
            dnsPrimary = prefs.getString("dnsPrimary", null) ?: d.dnsPrimary,
            dnsSecondary = prefs.getString("dnsSecondary", null) ?: d.dnsSecondary,
            profileDns = prefs.getBoolean("profileDns", d.profileDns),
            fragmentation = enumOr(prefs.getString("fragmentation", null), d.fragmentation),
            fragmentCustom = FragmentCustom.parse(prefs.getString("fragmentCustom", null)) ?: d.fragmentCustom,
            mux = prefs.getBoolean("mux", d.mux),
            ipv6 = prefs.getBoolean("ipv6", d.ipv6),
            onboardingDone = prefs.getBoolean("onboardingDone", d.onboardingDone),
        )
    }

    private fun write(s: AppSettings) = prefs.edit()
        .putString("themeId", s.themeId)
        .putString("accent", s.accent)
        .putString("font", s.font)
        .putString("radius", s.radius.name)
        .putString("animation", s.animation.name)
        .putBoolean("compactMode", s.compactMode)
        .putBoolean("floatingNav", s.floatingNav)
        .putBoolean("reducedEffects", s.reducedEffects)
        .putString("customThemes", themesJson(s.customThemes))
        .putString("customFonts", fontsJson(s.customFonts))
        .putBoolean("connectOnLaunch", s.connectOnLaunch)
        .putBoolean("autoReconnect", s.autoReconnect)
        .putBoolean("autoUpdateSubscriptions", s.autoUpdateSubscriptions)
        .putString("serverCheck", s.serverCheck.name)
        .putString("connectionPing", s.connectionPing.name)
        .putBoolean("sendHwid", s.sendHwid)
        .putBoolean("hwidManual", s.hwidManual)
        .putString("hwidValue", s.hwidValue)
        .putString("dns", s.dns.name)
        .putString("dnsPrimary", s.dnsPrimary)
        .putString("dnsSecondary", s.dnsSecondary)
        .putBoolean("profileDns", s.profileDns)
        .putString("fragmentation", s.fragmentation.name)
        .putString("fragmentCustom", s.fragmentCustom.toJson().toString())
        .putBoolean("mux", s.mux)
        .putBoolean("ipv6", s.ipv6)
        .putBoolean("onboardingDone", s.onboardingDone)
        .apply()

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback

    /** Before desktop's themes the app stored a four-value palette enum. */
    private fun legacyTheme(palette: String?) = when (palette) {
        "Wallpaper" -> "wallpaper"
        "Amoled" -> "amoled"
        "Cosmic" -> "cosmic"
        else -> "dark"
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) { instance ?: SettingsStore(context.applicationContext).also { instance = it } }

        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
    }
}

private fun themesJson(themes: List<CustomTheme>) = JSONArray().apply {
    themes.forEach { t ->
        put(
            JSONObject()
                .put("id", t.id).put("name", t.name).put("color", t.color).put("base", t.base.name)
                .put("tint", t.tint).put("depth", t.depth)
                .apply { t.surfaceHue?.let { put("surfaceHue", it) } },
        )
    }
}.toString()

private fun parseThemes(json: String?): List<CustomTheme> = runCatching {
    val array = JSONArray(json ?: return emptyList())
    List(array.length()) { i ->
        val o = array.getJSONObject(i)
        CustomTheme(
            id = o.getString("id"),
            name = o.optString("name", "Тема"),
            color = o.optString("color", "#3b82f6"),
            base = runCatching { ThemeBase.valueOf(o.optString("base")) }.getOrDefault(ThemeBase.Dark),
            tint = o.optDouble("tint", 0.4),
            surfaceHue = if (o.has("surfaceHue")) o.getDouble("surfaceHue") else null,
            depth = o.optDouble("depth", 0.0),
        )
    }
}.getOrDefault(emptyList())

private fun fontsJson(fonts: List<CustomFont>) = JSONArray().apply {
    fonts.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
}.toString()

private fun parseFonts(json: String?): List<CustomFont> = runCatching {
    val array = JSONArray(json ?: return emptyList())
    List(array.length()) { i -> array.getJSONObject(i).let { CustomFont(it.getString("id"), it.optString("name", "Шрифт")) } }
}.getOrDefault(emptyList())

/** Fonts the user added: validated TTF/OTF files under filesDir/fonts. */
object CustomFonts {
    private const val MAX_BYTES = 10 * 1024 * 1024

    private fun dir(context: Context) = File(context.filesDir, "fonts")

    fun file(context: Context, id: String): File? =
        File(dir(context), "$id.ttf").takeIf { id.matches(Regex("[a-f0-9]{12}")) && it.isFile }

    fun save(context: Context, displayName: String, bytes: ByteArray): CustomFont? {
        if (bytes.size < 12 || bytes.size > MAX_BYTES) return null
        // sfnt magic: TrueType 00010000, "true", OpenType "OTTO"; WOFF/WOFF2 are web-only and Android cannot load them.
        val magic = String(bytes, 0, 4, Charsets.ISO_8859_1)
        if (magic != "\u0000\u0001\u0000\u0000" && magic != "true" && magic != "OTTO") return null
        val id = SettingsStore.newId()
        return runCatching {
            dir(context).mkdirs()
            File(dir(context), "$id.ttf").writeBytes(bytes)
            CustomFont(id, displayName.substringBeforeLast('.').ifBlank { "Шрифт" })
        }.getOrNull()
    }

    fun delete(context: Context, id: String) {
        file(context, id)?.delete()
    }
}
