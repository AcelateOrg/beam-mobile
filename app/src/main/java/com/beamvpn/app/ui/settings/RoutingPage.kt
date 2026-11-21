package com.beamvpn.app.ui.settings

import com.beamvpn.app.ui.components.bottomBarSpace
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beamvpn.app.ui.components.AppIcon
import com.beamvpn.app.ui.components.PageHeader
import com.beamvpn.app.ui.components.appLabel
import com.beamvpn.app.ui.home.BeamViewModel
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.scaled
import com.beamvpn.app.vpn.ConnectionPhase
import com.beamvpn.app.vpn.RoutingApi
import com.beamvpn.app.vpn.RoutingState
import com.beamvpn.app.vpn.SplitRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

// ---- Installed apps ----

private class InstalledApp(val pkg: String, val label: String, val system: Boolean) {
    val search = label.lowercase()
}

private suspend fun Context.installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(launcher, 0)
        .mapNotNull { it.activityInfo?.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != packageName }
        .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString(), it.flags and ApplicationInfo.FLAG_SYSTEM != 0) }
        .sortedBy { it.search }
}

// ---- Input checks ----

private val DomainMatchers = listOf("domain:", "full:", "keyword:", "regexp:", "geosite:")

/** Domain rules take a plain domain or one of the core's matchers; a pasted URL is cut down to its host. */
private fun normalizeDomain(raw: String): String {
    var v = raw.trim().lowercase()
    if (v.isEmpty()) return ""
    if (DomainMatchers.none { v.startsWith(it) }) {
        v = v.removePrefix("http://").removePrefix("https://").removePrefix("www.")
        v = v.substringBefore('/').substringBefore('?').substringBefore(':')
    }
    return v
}

private fun isValidDomain(v: String): Boolean =
    v.isNotEmpty() && v.none { it.isWhitespace() } && (DomainMatchers.any { v.startsWith(it) && v.length > it.length } || v.contains('.'))

private fun isValidIp(raw: String): Boolean {
    val v = raw.trim().lowercase()
    if (v.startsWith("geoip:")) return v.length > 6
    val addr = v.substringBefore('/')
    val bits = if (v.contains('/')) v.substringAfter('/').toIntOrNull() ?: return false else null
    val v4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(addr)?.groupValues?.drop(1)?.all { (it.toIntOrNull() ?: 999) <= 255 } == true
    val v6 = !v4 && addr.contains(':') && runCatching { InetAddress.getByName(addr) }.isSuccess
    if (!v4 && !v6) return false
    return bits == null || bits in 0..(if (v4) 32 else 128)
}

private fun plural(n: Int, one: String, few: String, many: String) = when {
    n % 10 == 1 && n % 100 != 11 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

// ---- Page ----

private enum class RuleTab(val title: String) { Apps("Приложения"), Domains("Сайты"), Ips("IP-адреса") }

private fun SplitRules.items(tab: RuleTab) = when (tab) {
    RuleTab.Apps -> apps
    RuleTab.Domains -> domains
    RuleTab.Ips -> ips
}

private fun SplitRules.without(tab: RuleTab, item: String) = when (tab) {
    RuleTab.Apps -> copy(apps = apps - item)
    RuleTab.Domains -> copy(domains = domains - item)
    RuleTab.Ips -> copy(ips = ips - item)
}

private val RuleTab.icon: ImageVector
    get() = when (this) {
        RuleTab.Apps -> BeamIcons.Apps
        RuleTab.Domains -> BeamIcons.Globe
        RuleTab.Ips -> BeamIcons.Signal
    }

/** The one question this page answers: what goes through the VPN. */
private enum class RouteMode(val title: String, val caption: String, val icon: ImageVector) {
    All("Всё через VPN", "Весь трафик телефона идёт в туннель", BeamIcons.Shield),
    Except("Всё, кроме выбранного", "Выбранные приложения и сайты идут напрямую", BeamIcons.Split),
    Only("Только выбранное", "Через VPN идут только выбранные, остальное напрямую", BeamIcons.Zap),
}

private val SplitRules.routeMode: RouteMode
    get() = when {
        !enabled -> RouteMode.All
        viaVpn -> RouteMode.Only
        else -> RouteMode.Except
    }

private fun SplitRules.withMode(mode: RouteMode) = when (mode) {
    RouteMode.All -> copy(enabled = false)
    RouteMode.Except -> copy(enabled = true, mode = "exclude")
    RouteMode.Only -> copy(enabled = true, mode = "include")
}

/** A ready set of rules for the usual reason to open this page; on while all its entries are in the lists. */
private class Preset(val title: String, val caption: String, val domains: List<String>, val ips: List<String>) {
    fun isOn(r: SplitRules) = r.domains.containsAll(domains) && r.ips.containsAll(ips)
    fun toggle(r: SplitRules) = if (isOn(r)) {
        r.copy(domains = r.domains - domains.toSet(), ips = r.ips - ips.toSet())
    } else {
        r.copy(domains = r.domains + domains.filter { it !in r.domains }, ips = r.ips + ips.filter { it !in r.ips })
    }
}

private val ExceptPresets = listOf(
    Preset("Российские сайты напрямую", "Банки, госуслуги и маркетплейсы без VPN", listOf("geosite:category-ru"), listOf("geoip:ru")),
)
private val OnlyPresets = listOf(
    Preset("Заблокированное в России", "Через VPN только то, что недоступно без него", listOf("geosite:ru-blocked"), listOf("geoip:ru-blocked")),
)

/** Human names for the database entries the presets add. */
private fun presetName(item: String): String? = when (item) {
    "geosite:category-ru" -> "Российские сайты"
    "geoip:ru" -> "Российские IP-адреса"
    "geosite:ru-blocked" -> "Сайты, заблокированные в России"
    "geoip:ru-blocked" -> "IP-адреса, заблокированные в России"
    else -> null
}

/**
 * Split tunneling on one screen: a mode, then the three lists as entries that open their own screen. Every change
 * is stored at once; a live tunnel is rebuilt once, when the page is left, instead of after each tap.
 */
@Composable
fun RoutingPage(onBack: () -> Unit, viewModel: BeamViewModel) {
    val context = LocalContext.current
    val phase = viewModel.state.collectAsStateWithLifecycle().value.phase
    var rules by remember { mutableStateOf<SplitRules?>(null) }
    var stored by remember { mutableStateOf<SplitRules?>(null) }
    var initial by remember { mutableStateOf<SplitRules?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableStateOf<RuleTab?>(null) }

    LaunchedEffect(Unit) {
        runCatching { RoutingApi.state(context) }
            .onSuccess { rules = it.split; stored = it.split; initial = it.split }
            .onFailure { failure = it.message }
    }
    // Stored a moment after the last tap, so a burst of toggles is one write.
    LaunchedEffect(rules) {
        val next = rules ?: return@LaunchedEffect
        if (next == stored) return@LaunchedEffect
        delay(400)
        runCatching { RoutingApi.saveSplit(context, next) }
            .onSuccess {
                stored = next
                // Fetched as soon as a preset is chosen, so leaving the page rarely has to wait for it.
                RoutingApi.prepareGeo(context, next)
            }
            .onFailure { failure = it.message }
    }
    // Leaving the page writes what is still pending and rebuilds a live tunnel once.
    val latest by rememberUpdatedState(rules)
    val saved by rememberUpdatedState(stored)
    val before by rememberUpdatedState(initial)
    val app = context.applicationContext
    DisposableEffect(Unit) {
        onDispose {
            val last = latest ?: return@onDispose
            if (last == before) return@onDispose
            viewModel.viewModelScope.launch {
                if (last != saved) runCatching { RoutingApi.saveSplit(app, last) }
                // Rules on geosite:/geoip: need their database first, or the core would skip them.
                RoutingApi.prepareGeo(app, last)
                viewModel.applyRouting()
            }
        }
    }

    BackHandler(enabled = section != null) { section = null }
    com.beamvpn.app.ui.components.RegisterNavBack(section != null) { section = null }

    val open = section
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            open?.icon ?: BeamIcons.Split,
            open?.title ?: "Маршрутизация",
            onBack = { if (open != null) section = null else onBack() },
        )
        val current = rules
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                current == null -> if (failure == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp)
                open == null -> Overview(current, connected = phase == ConnectionPhase.Connected, onChange = { rules = it }, onOpen = { section = it })
                else -> SectionBody(open, current, onChange = { rules = it })
            }
        }
    }

    failure?.let {
        AlertDialog(
            onDismissRequest = { failure = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Не получилось") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text("Понятно") } },
        )
    }
}

@Composable
private fun Overview(rules: SplitRules, connected: Boolean, onChange: (SplitRules) -> Unit, onOpen: (RuleTab) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val mode = rules.routeMode
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).bottomBarSpace(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Что пускать через VPN")
        RouteMode.entries.forEach { m ->
            ModeCard(m, selected = m == mode) { onChange(rules.withMode(m)) }
        }

        Spacer(Modifier.height(8.dp))
        SectionLabel(if (mode == RouteMode.Only) "Что идёт через VPN" else "Что идёт напрямую")
        if (mode == RouteMode.All) {
            Text(
                if (rules.count > 0) {
                    "Списки сохранены и снова заработают, если выбрать один из режимов выше."
                } else {
                    "Выберите режим выше, чтобы пустить часть трафика мимо VPN или только часть через него."
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        val dim = if (mode == RouteMode.All) 0.5f else 1f
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = dim }
                .clip(RoundedCornerShape(scaled(20.dp)))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)),
        ) {
            RuleTab.entries.forEachIndexed { i, tab ->
                if (i > 0) RowDivider()
                ListEntry(tab, rules.items(tab)) { onOpen(tab) }
            }
        }

        val presets = when (mode) {
            RouteMode.Except -> ExceptPresets
            RouteMode.Only -> OnlyPresets
            RouteMode.All -> emptyList()
        }
        if (presets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Готовые наборы")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(scaled(20.dp)))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)),
            ) {
                presets.forEachIndexed { i, p ->
                    if (i > 0) RowDivider()
                    SwitchRow(BeamIcons.Star, p.title, p.caption, p.isOn(rules)) { onChange(p.toggle(rules)) }
                }
            }
        }

        Text(
            if (connected) {
                "Изменения сохраняются сами. VPN переподключится на секунду, когда вы выйдете с этого экрана."
            } else {
                "Изменения сохраняются сами и применятся при подключении."
            },
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

/** A large, plainly worded choice; the chosen one is outlined and ticked. */
@Composable
private fun ModeCard(mode: RouteMode, selected: Boolean, onClick: () -> Unit) {
    val extra = BeamTheme.extra
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(scaled(20.dp)),
        color = if (selected) extra.highlight.copy(alpha = 0.10f) else colors.surfaceContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (selected) extra.highlight.copy(alpha = 0.55f) else Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (selected) extra.highlight.copy(alpha = 0.18f) else colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(scaled(12.dp))),
                contentAlignment = Alignment.Center,
            ) { Icon(mode.icon, null, Modifier.size(18.dp), tint = if (selected) extra.highlight else colors.onSurfaceVariant) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(mode.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                Text(mode.caption, fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
            }
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) extra.highlight else Color.Transparent)
                    .border(1.5.dp, if (selected) extra.highlight else colors.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(BeamIcons.Check, null, Modifier.size(13.dp), tint = extra.onHighlight) }
        }
    }
}

/** One list as a row: what it holds at a glance, how many, and a chevron into it. */
@Composable
private fun ListEntry(tab: RuleTab, items: List<String>, onClick: () -> Unit) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val summary = remember(tab, items) {
        when {
            items.isEmpty() -> if (tab == RuleTab.Apps) "Не выбраны" else "Не добавлены"
            tab == RuleTab.Apps -> items.take(3).joinToString(", ") { context.appLabel(it) }
            else -> items.take(3).joinToString(", ") { presetName(it) ?: it }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = tab.title, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(scaled(8.dp))),
            contentAlignment = Alignment.Center,
        ) { Icon(tab.icon, null, Modifier.size(16.dp), tint = BeamTheme.extra.highlight) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tab.title, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            Text(summary, fontSize = 12.sp, lineHeight = 16.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (items.isNotEmpty()) {
            Text(
                items.size.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = muted,
                modifier = Modifier.background(BeamTheme.extra.accentFill, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Icon(BeamIcons.ChevronRight, null, Modifier.size(16.dp), tint = muted)
    }
}

/** One list on its own screen: what it does, how to add to it, and what is in it. */
@Composable
private fun SectionBody(tab: RuleTab, rules: SplitRules, onChange: (SplitRules) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val items = rules.items(tab)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp + com.beamvpn.app.ui.components.LocalBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hint") {
            Text(
                directionHint(tab, rules),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item(key = "add") {
            when (tab) {
                RuleTab.Apps -> PickAppsButton(rules.apps.isEmpty()) { picking = true }
                RuleTab.Domains -> AddField(
                    placeholder = "example.com",
                    hint = "Действует и на поддомены. Можно вставить ссылку целиком.",
                    normalize = ::normalizeDomain,
                    valid = ::isValidDomain,
                    invalidText = "Введите домен, например example.com",
                    onAdd = { value -> if (value !in rules.domains) onChange(rules.copy(domains = rules.domains + value)) },
                )
                RuleTab.Ips -> AddField(
                    placeholder = "1.2.3.4 или 10.0.0.0/8",
                    hint = "Один адрес или подсеть, IPv4 и IPv6.",
                    normalize = { it.trim().lowercase() },
                    valid = ::isValidIp,
                    invalidText = "Это не похоже на IP-адрес или подсеть",
                    onAdd = { value -> if (value !in rules.ips) onChange(rules.copy(ips = rules.ips + value)) },
                )
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") { EmptyRules(tab) }
        } else {
            items(items, key = { it }) { item ->
                if (tab == RuleTab.Apps) {
                    RuleRow(
                        leading = { AppIcon(item) },
                        title = remember(item) { context.appLabel(item) },
                        subtitle = item,
                        onRemove = { onChange(rules.without(tab, item)) },
                        modifier = Modifier.animateItem(),
                    )
                } else {
                    val named = presetName(item)
                    RuleRow(
                        leading = { RuleIcon(tab.icon) },
                        title = named ?: item,
                        subtitle = if (named != null) item else null,
                        onRemove = { onChange(rules.without(tab, item)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (picking) {
        AppPickerSheet(
            selected = rules.apps,
            onDismiss = { picking = false },
            onDone = { onChange(rules.copy(apps = it)); picking = false },
        )
    }
}

private fun directionHint(tab: RuleTab, rules: SplitRules): String {
    val what = when (tab) {
        RuleTab.Apps -> "Эти приложения"
        RuleTab.Domains -> "Эти сайты"
        RuleTab.Ips -> "Эти адреса"
    }
    return when (rules.routeMode) {
        RouteMode.Only -> "$what пойдут через VPN, всё остальное напрямую."
        RouteMode.Except -> "$what пойдут напрямую, всё остальное через VPN."
        RouteMode.All -> "Сейчас выбран режим «Всё через VPN», поэтому список не действует. Он заработает, если выбрать другой режим."
    }
}

@Composable
private fun PickAppsButton(empty: Boolean, onClick: () -> Unit) {
    val extra = BeamTheme.extra
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(scaled(16.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = extra.accentFill, contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(if (empty) BeamIcons.Plus else BeamIcons.Sliders, null, Modifier.size(16.dp))
        Text(if (empty) "Выбрать приложения" else "Изменить список", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun EmptyRules(tab: RuleTab) {
    val (icon, title, text) = when (tab) {
        RuleTab.Apps -> Triple(BeamIcons.Apps, "Приложения не выбраны", "Отметьте нужные в списке установленных")
        RuleTab.Domains -> Triple(BeamIcons.Globe, "Сайтов нет", "Впишите адрес сайта выше и нажмите «+»")
        RuleTab.Ips -> Triple(BeamIcons.Signal, "Адресов нет", "Впишите адрес или подсеть выше и нажмите «+»")
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RuleIcon(icon: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(scaled(10.dp))).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun RuleRow(
    leading: @Composable () -> Unit,
    title: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(scaled(16.dp)))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Удалить", onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(BeamIcons.Trash, "Удалить", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AddField(
    placeholder: String,
    hint: String,
    normalize: (String) -> String,
    valid: (String) -> Boolean,
    invalidText: String,
    onAdd: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val extra = BeamTheme.extra
    fun submit() {
        val value = normalize(text)
        if (value.isEmpty()) return
        if (!valid(value)) {
            error = true
            return
        }
        onAdd(value)
        text = ""
        error = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = false },
                placeholder = { Text(placeholder, fontSize = 14.sp) },
                singleLine = true,
                isError = error,
                shape = RoundedCornerShape(scaled(16.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extra.highlight.copy(alpha = 0.7f),
                    unfocusedBorderColor = extra.border,
                    cursorColor = extra.highlight,
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = ::submit,
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(scaled(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(56.dp),
            ) { Icon(BeamIcons.Plus, "Добавить", Modifier.size(20.dp)) }
        }
        Text(
            if (error) invalidText else hint,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(selected: List<String>, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initial = remember { selected.toSet() }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(initial) }
    LaunchedEffect(Unit) {
        // What was already chosen comes first, so a long list does not have to be searched to undo a choice.
        apps = context.installedApps().sortedByDescending { it.pkg in initial }
    }

    // Keyed on the choice made when the sheet opened, not the live one, so ticking a box never refilters the list.
    val shown = remember(apps, query, showSystem) {
        val q = query.trim().lowercase()
        apps.orEmpty().filter { (showSystem || !it.system || it.pkg in initial) && (q.isEmpty() || q in it.search || q in it.pkg) }
    }
    val extra = BeamTheme.extra
    fun toggle(pkg: String) {
        chosen = if (pkg in chosen) chosen - pkg else chosen + pkg
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxHeight(0.92f).navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Приложения", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск по названию", fontSize = 14.sp) },
                leadingIcon = { Icon(BeamIcons.Search, null, Modifier.size(18.dp)) },
                trailingIcon = if (query.isEmpty()) null else {
                    {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Очистить") { query = "" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(BeamIcons.Close, "Очистить", Modifier.size(16.dp)) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(scaled(16.dp)),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = extra.highlight.copy(alpha = 0.7f), unfocusedBorderColor = extra.border, cursorColor = extra.highlight),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(scaled(12.dp)))
                    .clickable { showSystem = !showSystem },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(showSystem, null, colors = CheckboxDefaults.colors(checkedColor = extra.highlight, checkmarkColor = extra.onHighlight))
                Text("Показывать системные", fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (chosen.isNotEmpty()) {
                    Text("Выбрано: ${chosen.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val list = apps
                when {
                    list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp)
                    shown.isEmpty() -> Text(
                        if (query.isBlank()) "Нет приложений" else "Ничего не найдено по «${query.trim()}»",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.pkg }, contentType = { "app" }) { app ->
                            val on = app.pkg in chosen
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .clip(RoundedCornerShape(scaled(12.dp)))
                                    .clickable(onClickLabel = if (on) "Убрать" else "Выбрать") { toggle(app.pkg) }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppIcon(app.pkg)
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = extra.highlight, checkmarkColor = extra.onHighlight))
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onDone(selected.filter { it in chosen } + chosen.filter { it !in initial }.sorted()) },
                shape = RoundedCornerShape(scaled(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (chosen.isEmpty()) "Готово" else "Готово · ${chosen.size}", fontWeight = FontWeight.SemiBold) }
        }
    }
}
