package com.beamvpn.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamvpn.app.ui.components.openLink
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.scaled

// Port of beam-desktop's Licenses page and BeamLicenseCard. The Beam Source License 1.0 is retold briefly and
// plainly; legally the English text in LICENSE (beam-mobile, beam-desktop, beam-bridge) applies. When the licence
// changes, update these points too.

private val Allowed = Color(0xFF10B981)
private val Forbidden = Color(0xFFF43F5E)
private val Mpl = Color(0xFFF97316)

private class Component(val name: String, val role: String, val license: String, val open: Boolean)

private val Components = listOf(
    Component("beam-mobile", "Приложение", "Beam Source 1.0", false),
    Component("beam-bridge", "Мост к ядру", "Beam Source 1.0", false),
    Component("beam-core", "Ядро на Xray", "MPL-2.0", true),
    Component("beam-tun", "Туннель", "MPL-2.0", true),
)

private val AllowedRules = listOf(
    "Пользоваться" to "Официальными сборками — дома, на работе, в компании",
    "Читать код" to "Изучать и проверять, что и как делает Beam",
    "Собирать и менять для себя" to "На своих устройствах, для личного или внутреннего использования",
    "Предлагать правки" to "Pull request'ами на условиях CLA",
    "Форкать на GitHub" to "Чтобы подготовить и отправить свои правки",
    "Цитировать" to "Короткие фрагменты в статьях и обсуждениях — со ссылкой",
    "Рекомендовать клиентам" to "Провайдерам: ссылки на сборки и заголовки подписки",
)

private val ForbiddenRules = listOf(
    "Распространять и продавать" to "Beam и его изменённые версии — в коде или сборках",
    "Выкладывать в сторы" to "И в зеркала, пакетные репозитории, сайты с APK",
    "Ребрендить" to "Другое название, логотип или свой продукт на основе Beam",
    "Делать конкурента" to "Брать код, дизайн или ассеты для своего или чужого клиента",
    "Запускать как сервис" to "Облачный или управляемый Beam для других людей",
    "Убирать копирайты" to "Удалять или прятать уведомления об авторстве и лицензии",
    "Обходить защиту" to "Подпись, обновления и проверки целостности — чтобы раздавать",
)

private val FinePrint = listOf(
    "Названия «Beam», «Acelate» и логотипы — товарные знаки, лицензия прав на них не даёт.",
    "Нарушил — права пропадают. Впервые и исправил за 30 дней после уведомления — возвращаются.",
    "Beam предоставляется «как есть», без гарантий.",
    "Нужно что-то из запрещённого? Можно с письменного разрешения Acelate.",
    "Юридически действует полный текст в файле LICENSE репозитория.",
)

// ---- Third-party components that ship in the Android app ----

private val LicenseUrls = mapOf(
    "MIT" to "https://opensource.org/license/mit",
    "Apache-2.0" to "https://www.apache.org/licenses/LICENSE-2.0",
    "BSD-2-Clause" to "https://opensource.org/license/bsd-2-clause",
    "BSD-3-Clause" to "https://opensource.org/license/bsd-3-clause",
    "MPL-2.0" to "https://www.mozilla.org/MPL/2.0/",
    "ISC" to "https://opensource.org/license/isc-license-txt",
    "OFL-1.1" to "https://openfontlicense.org",
    "LGPL-3.0" to "https://www.gnu.org/licenses/lgpl-3.0.html",
    "GPL-3.0" to "https://www.gnu.org/licenses/gpl-3.0.html",
    "MIT / Apache-2.0" to "https://opensource.org/license/mit",
)

private class Item(val name: String, val holder: String, val license: String, val url: String, val note: String? = null)
private class Section(val id: String, val title: String, val hint: String, val items: List<Item>)

private fun gh(repo: String) = "https://github.com/$repo"

private val ThirdParty = listOf(
    Section(
        "core", "Ядро и сеть", "Xray, протоколы, туннель",
        listOf(
            Item("Xray-core", "Project X (XTLS)", "MPL-2.0", gh("XTLS/Xray-core"), "Модифицированная версия"),
            Item("REALITY", "Project X (XTLS)", "MPL-2.0", gh("XTLS/REALITY")),
            Item("uTLS", "Refraction Networking", "BSD-3-Clause", gh("refraction-networking/utls")),
            Item("quic-go", "The quic-go Authors, Aperture Internet Lab", "MIT", gh("apernet/quic-go")),
            Item("qpack", "Marten Seemann", "MIT", gh("quic-go/qpack")),
            Item("gVisor", "The gVisor Authors", "Apache-2.0", gh("google/gvisor"), "Сетевой стек туннеля"),
            Item("wireguard-go", "WireGuard LLC", "MIT", "https://git.zx2c4.com/wireguard-go"),
            Item("DNS", "Miek Gieben", "BSD-3-Clause", gh("miekg/dns")),
            Item("gRPC-Go", "The gRPC Authors", "Apache-2.0", gh("grpc/grpc-go")),
            Item("Protocol Buffers", "The Go Authors", "BSD-3-Clause", gh("protocolbuffers/protobuf-go")),
            Item("Google APIs genproto", "Google LLC", "Apache-2.0", gh("googleapis/go-genproto")),
            Item("CIRCL", "Cloudflare, Inc.", "BSD-3-Clause", gh("cloudflare/circl")),
            Item("BLAKE3", "Luke Champine", "MIT", gh("lukechampine/blake3")),
            Item("go-proxyproto", "Paulo Pires", "Apache-2.0", gh("pires/go-proxyproto")),
            Item("compress", "Klaus Post", "Apache-2.0", gh("klauspost/compress")),
            Item("cpuid", "Klaus Post", "MIT", gh("klauspost/cpuid")),
            Item("brotli", "Andy Balholm", "MIT", gh("andybalholm/brotli")),
            Item("netipx", "The Go Authors, Tailscale", "BSD-3-Clause", gh("go4org/netipx")),
            Item("btree", "Google LLC", "Apache-2.0", gh("google/btree")),
            Item("ratelimit", "Canonical Ltd.", "LGPL-3.0", gh("juju/ratelimit"), "С исключением для статической линковки"),
            Item("websocket", "The Gorilla Authors", "BSD-2-Clause", gh("gorilla/websocket")),
            Item("yaml.v2", "Canonical Ltd.", "Apache-2.0", gh("go-yaml/yaml")),
            Item("go-toml", "Thomas Pelletier", "MIT / Apache-2.0", gh("pelletier/go-toml")),
            Item("errors", "Dave Cheney", "BSD-2-Clause", gh("pkg/errors")),
            Item("Go x/net, x/sys, x/crypto, x/text…", "The Go Authors", "BSD-3-Clause", "https://go.googlesource.com"),
            Item("gomobile", "The Go Authors", "BSD-3-Clause", "https://go.googlesource.com/mobile", "Связка Go и Android"),
        ),
    ),
    Section(
        "android", "Android", "Приложение и система",
        listOf(
            Item("Kotlin", "JetBrains s.r.o.", "Apache-2.0", "https://kotlinlang.org"),
            Item("kotlinx.coroutines", "JetBrains s.r.o.", "Apache-2.0", gh("Kotlin/kotlinx.coroutines")),
            Item("Jetpack Compose", "The Android Open Source Project", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
            Item("Material 3 for Compose", "The Android Open Source Project", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/compose-material3"),
            Item("AndroidX Activity, Lifecycle, Core", "The Android Open Source Project", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
            Item("CameraX", "The Android Open Source Project", "Apache-2.0", "https://developer.android.com/media/camera/camerax", "Сканер QR"),
            Item("ZXing", "ZXing authors", "Apache-2.0", gh("zxing/zxing"), "QR-коды"),
            Item("Coil", "Coil Contributors", "Apache-2.0", "https://coil-kt.github.io/coil/"),
        ),
    ),
    Section(
        "ui", "Иконки и флаги", "Графика интерфейса",
        listOf(
            Item("Lucide", "Lucide Contributors", "ISC", "https://lucide.dev"),
            Item("flag-icons", "Panayiotis Lipiridis", "MIT", gh("lipis/flag-icons")),
        ),
    ),
    Section(
        "fonts", "Шрифты", "Google Fonts",
        listOf(
            Item("Rubik", "Hubert & Fischer", "OFL-1.1", "https://fonts.google.com/specimen/Rubik"),
            Item("Manrope", "Mikhail Sharanda", "OFL-1.1", "https://fonts.google.com/specimen/Manrope"),
            Item("Onest", "Dmitri Voloshin", "OFL-1.1", "https://fonts.google.com/specimen/Onest"),
            Item("Geologica", "Monokrom", "OFL-1.1", "https://fonts.google.com/specimen/Geologica"),
            Item("Nunito", "Vernon Adams, Jacques Le Bailly", "OFL-1.1", "https://fonts.google.com/specimen/Nunito"),
        ),
    ),
    Section(
        "data", "Данные", "Загружаются отдельно",
        listOf(
            Item("russia-v2ray-rules-dat", "runetfreedom", "GPL-3.0", gh("runetfreedom/russia-v2ray-rules-dat"), "geoip.dat и geosite.dat"),
        ),
    ),
)

private val LicenseTones = mapOf(
    "MIT" to Color(0xFF10B981), "Apache" to Color(0xFF0EA5E9), "BSD" to Color(0xFF8B5CF6), "MPL" to Color(0xFFF97316),
    "ISC" to Color(0xFF14B8A6), "OFL" to Color(0xFFEC4899), "GPL" to Color(0xFFF43F5E), "LGPL" to Color(0xFFF59E0B),
)

private fun familyOf(license: String) = if (license.startsWith("LGPL")) "LGPL" else license.split('-', ' ', '/').first()

@Composable
private fun toneOf(license: String): Color = LicenseTones[familyOf(license)] ?: MaterialTheme.colorScheme.onSurfaceVariant

// ---- Beam's own licence ----

@Composable
private fun Pill(text: String, tone: Color) {
    Box(Modifier.clip(CircleShape).background(tone.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = tone, maxLines = 1)
    }
}

@Composable
private fun RuleList(rules: List<Pair<String, String>>, allowed: Boolean) {
    val tone = if (allowed) Allowed else Forbidden
    val shape = RoundedCornerShape(scaled(14.dp))
    Column(
        Modifier.fillMaxWidth().clip(shape).background(tone.copy(alpha = 0.04f)).border(1.dp, tone.copy(alpha = 0.2f), shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (allowed) "МОЖНО" else "НЕЛЬЗЯ",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = tone,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        rules.forEach { (title, hint) ->
            Row(Modifier.padding(horizontal = 4.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 1.dp).size(20.dp).clip(CircleShape).background(tone.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(if (allowed) BeamIcons.Check else BeamIcons.Close, null, Modifier.size(12.dp), tint = tone)
                }
                Column {
                    Text(title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                    Text(hint, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BeamLicenseCard() {
    val extra = BeamTheme.extra
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var more by rememberSaveable { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(scaled(24.dp)), color = extra.shell, border = BorderStroke(1.dp, extra.border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(scaled(12.dp))).background(extra.highlight.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(BeamIcons.Shield, null, Modifier.size(18.dp), tint = extra.highlight)
                }
                Column {
                    Text("Лицензия Beam", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Код открыт для просмотра, но это не open source. Ядро и туннель — открытые.", fontSize = 12.sp, lineHeight = 16.sp, color = muted)
                }
            }

            // What is under which licence.
            Components.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(scaled(14.dp)))
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
                                .border(1.dp, extra.border, RoundedCornerShape(scaled(14.dp)))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(c.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(c.role, fontSize = 11.sp, color = muted, maxLines = 1)
                            Box(Modifier.padding(top = 3.dp)) { Pill(c.license, if (c.open) Mpl else extra.highlight) }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Приложение и мост · Beam Source License 1.0", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = muted)
                RuleList(AllowedRules, allowed = true)
                RuleList(ForbiddenRules, allowed = false)
            }

            // The MPL parts live by their own, freer rules.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(scaled(14.dp)))
                    .background(Mpl.copy(alpha = 0.05f))
                    .border(1.dp, Mpl.copy(alpha = 0.2f), RoundedCornerShape(scaled(14.dp)))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Mpl.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(BeamIcons.Check, null, Modifier.size(12.dp), tint = Mpl)
                }
                Column {
                    Text("beam-core и beam-tun — MPL-2.0", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Можно использовать, менять и распространять, в том числе в своих продуктах. Изменённые файлы — открытые под MPL-2.0, копирайты сохранить.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = muted,
                    )
                }
            }

            Column {
                Row(
                    Modifier.clip(RoundedCornerShape(scaled(8.dp))).clickable { more = !more }.padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(BeamIcons.Info, null, Modifier.size(14.dp), tint = muted)
                    Text("Важно знать", fontSize = 12.sp, color = muted)
                    Icon(BeamIcons.ChevronDown, null, Modifier.size(12.dp).rotate(if (more) 180f else 0f), tint = muted)
                }
                AnimatedVisibility(more) {
                    Column(Modifier.padding(start = 8.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FinePrint.forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", fontSize = 12.sp, color = muted.copy(alpha = 0.5f))
                                Text(line, fontSize = 12.sp, lineHeight = 16.sp, color = muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Page ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LicensesPage() {
    val context = LocalContext.current
    val extra = BeamTheme.extra
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf<String?>(null) }

    val total = ThirdParty.sumOf { it.items.size }
    val stats = remember {
        ThirdParty.flatMap { it.items }.groupingBy { familyOf(it.license) }.eachCount().entries.sortedByDescending { it.value }
    }
    val q = query.trim().lowercase()
    val sections = ThirdParty
        .filter { section == null || it.id == section }
        .map { s -> s to s.items.filter { i -> q.isEmpty() || listOf(i.name, i.holder, i.license, i.note.orEmpty()).any { it.lowercase().contains(q) } } }
        .filter { it.second.isNotEmpty() }

    if (q.isEmpty() && section == null) {
        BeamLicenseCard()

        // Thanks, and how the licences of the components split.
        Surface(shape = RoundedCornerShape(scaled(20.dp)), color = extra.shell, border = BorderStroke(1.dp, extra.border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(BeamIcons.Star, null, Modifier.size(14.dp), tint = Color(0xFF00C8FF))
                    Text("Beam построен на открытом ПО. Спасибо авторам!", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("$total", fontSize = 12.sp, color = muted)
                }
                Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                    stats.forEach { (key, n) ->
                        Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(LicenseTones[key] ?: muted))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stats.forEach { (key, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(LicenseTones[key] ?: muted))
                            Text("$key $n", fontSize = 11.sp, color = muted)
                        }
                    }
                }
            }
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Поиск по названию, автору или лицензии", fontSize = 14.sp, maxLines = 1) },
        leadingIcon = { Icon(BeamIcons.Search, null, Modifier.size(16.dp)) },
        trailingIcon = if (query.isNotEmpty()) ({
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { query = "" }, contentAlignment = Alignment.Center) {
                Icon(BeamIcons.Close, "Очистить", Modifier.size(14.dp))
            }
        }) else null,
        singleLine = true,
        shape = RoundedCornerShape(scaled(16.dp)),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = extra.highlight.copy(alpha = 0.7f), unfocusedBorderColor = extra.border),
        modifier = Modifier.fillMaxWidth(),
    )

    // Section chips narrow the list to one group.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<Section?>(null) + ThirdParty).forEach { s ->
            val on = section == s?.id
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) extra.highlight.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { section = s?.id }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(s?.title ?: "Все", fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }

    sections.forEach { (s, items) ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${s.hint} · ${items.size}", fontSize = 11.sp, color = muted)
            }
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(scaled(14.dp)))
                        .background(extra.shell)
                        .border(1.dp, extra.border, RoundedCornerShape(scaled(14.dp)))
                        .clickable(onClickLabel = "Открыть страницу проекта") { context.openLink(item.url) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.holder + (item.note?.let { " · $it" } ?: ""),
                            fontSize = 12.sp,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Текст лицензии") { LicenseUrls[item.license]?.let(context::openLink) }) {
                        Pill(item.license, toneOf(item.license))
                    }
                }
            }
        }
    }

    if (sections.isEmpty()) {
        Text("Ничего не найдено", fontSize = 13.sp, color = muted, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
    }
}
