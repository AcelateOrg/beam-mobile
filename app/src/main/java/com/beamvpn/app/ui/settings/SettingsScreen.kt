package com.beamvpn.app.ui.settings

import com.beamvpn.app.ui.components.bottomBarSpace
import com.beamvpn.app.ui.theme.scaled
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beamvpn.app.R
import com.beamvpn.app.settings.AppSettings
import com.beamvpn.app.settings.DnsPreset
import com.beamvpn.app.settings.FragmentCustom
import com.beamvpn.app.settings.Fragmentation
import com.beamvpn.app.settings.PingMethod
import com.beamvpn.app.settings.SettingsStore
import com.beamvpn.app.ui.components.Badge
import com.beamvpn.app.ui.components.PageHeader
import com.beamvpn.app.ui.home.BeamViewModel
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.settings.effectiveHwid
import com.beamvpn.app.settings.hwid
import com.beamvpn.app.settings.randomHwid
import com.beamvpn.app.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SettingsPage(val icon: ImageVector, val title: String, val description: String) {
    Appearance(BeamIcons.Palette, "Внешний вид", "Темы, шрифт, скругление"),
    App(BeamIcons.Smartphone, "Приложение", "Запуск, подписки, пинг, HWID"),
    Connection(BeamIcons.Wifi, "Соединение", "DNS, фрагментация, защита"),
    Routing(BeamIcons.Split, "Маршрутизация", "Приложения, домены и IP-адреса"),
    Stats(BeamIcons.BarChart, "Статистика", "Трафик, время, серверы"),
    Logs(BeamIcons.ScrollText, "Логи", "Журнал ядра и активные соединения"),
    About(BeamIcons.Info, "О приложении", "Версия и информация"),
    Licenses(BeamIcons.Scale, "Лицензии", ""),
}

/** Settings tab: the desktop list, with each section opening as its own screen (system back returns). */
@Composable
fun SettingsScreen(reselect: Int = 0, viewModel: BeamViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.settings.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    // A tap on the Settings tab while inside a section returns to the list. Only a new tap counts, so the
    // section restored after rotation stays open.
    var seenReselect by rememberSaveable { mutableStateOf(reselect) }
    LaunchedEffect(reselect) {
        if (reselect != seenReselect) {
            seenReselect = reselect
            page = null
        }
    }

    BackHandler(enabled = page != null) {
        page = if (page == SettingsPage.Licenses) SettingsPage.About else null
    }
    // The floating bar's back island does the same as the system back.
    com.beamvpn.app.ui.components.RegisterNavBack(page != null) {
        page = if (page == SettingsPage.Licenses) SettingsPage.About else null
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = initialState == null || (targetState == SettingsPage.Licenses)
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(200))) togetherWith
                (slideOutHorizontally(tween(220)) { -it / 10 * dir } + fadeOut(tween(120)))
        },
        label = "settings",
    ) { current ->
        val back = { page = if (current == SettingsPage.Licenses) SettingsPage.About else null }
        when (current) {
            null -> SettingsRoot(onOpen = { page = it })
            SettingsPage.Appearance -> SubPage(current, back) { AppearancePage(settings, store) }
            SettingsPage.App -> SubPage(current, back) { AppPage(settings, store) }
            SettingsPage.Connection -> SubPage(current, back) { ConnectionPage(settings, store) }
            SettingsPage.Routing -> RoutingPage(onBack = back, viewModel = viewModel)
            SettingsPage.Stats -> StatsPage(onBack = back)
            SettingsPage.Logs -> LogsHub(onBack = back, viewModel = viewModel)
            SettingsPage.About -> AboutPage(back, onLicenses = { page = SettingsPage.Licenses })
            SettingsPage.Licenses -> SubPage(current, back) { LicensesPage() }
        }
    }
}

@Composable
private fun SettingsRoot(onOpen: (SettingsPage) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(BeamIcons.Sliders, "Настройки")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(12.dp).bottomBarSpace(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            RootGroups.forEachIndexed { g, group ->
                if (g > 0) Spacer(Modifier.height(10.dp))
                group.forEachIndexed { i, item ->
                    RootItem(item, groupShape(i, group.size)) { onOpen(item) }
                }
            }
        }
    }
}

/** The sections in Pixel-style groups: look and behaviour, the connection, what happened, the app itself. */
private val RootGroups = listOf(
    listOf(SettingsPage.Appearance, SettingsPage.App),
    listOf(SettingsPage.Connection, SettingsPage.Routing),
    listOf(SettingsPage.Stats, SettingsPage.Logs),
    listOf(SettingsPage.About),
)

/** One section of a group: muted icon tile, title, description, chevron, on the group's tonal tile. */
@Composable
private fun RootItem(item: SettingsPage, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(item.icon, null, Modifier.size(18.dp), tint = muted) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                Text(item.description, fontSize = 14.sp, lineHeight = 19.sp, color = muted)
            }
            Icon(BeamIcons.ChevronRight, null, Modifier.size(16.dp), tint = muted)
        }
    }
}

@Composable
private fun SubPage(page: SettingsPage, onBack: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(page.icon, page.title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(12.dp)
                .bottomBarSpace(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

// ---- App ----

@Composable
private fun AppPage(settings: AppSettings, store: SettingsStore) {
    val context = LocalContext.current
    var editingHwid by rememberSaveable { mutableStateOf(false) }
    SettingsCard(BeamIcons.Smartphone, "Поведение", "Запуск и работа клиента на телефоне") {
        SwitchRow(BeamIcons.Power, "Подключаться при запуске", "Активировать последний профиль сразу после открытия.", settings.connectOnLaunch) { v ->
            store.update { it.copy(connectOnLaunch = v) }
        }
        RowDivider()
        SwitchRow(
            BeamIcons.Wifi,
            "Автопереподключение",
            "Перезапускать соединение, если оно перестало работать или телефон сменил сеть.",
            settings.autoReconnect,
        ) { v -> store.update { it.copy(autoReconnect = v) } }
        RowDivider()
        SwitchRow(BeamIcons.Refresh, "Автообновление подписок", "Периодически проверять обновления узлов по ссылкам.", settings.autoUpdateSubscriptions) { v ->
            store.update { it.copy(autoUpdateSubscriptions = v) }
        }
    }
    SettingsCard(BeamIcons.Zap, "Пинг и диагностика", "Методы проверки задержки и доступности серверов") {
        SettingsRow(BeamIcons.Servers, "Проверка серверов", "Метод проверки узлов в списке.") {
            Segmented(PingMethod.entries, settings.serverCheck, { it.title }) { v -> store.update { it.copy(serverCheck = v) } }
        }
        RowDivider()
        SettingsRow(BeamIcons.Wifi, "Пинг подключения", "HTTP замеряет реальный туннель, TCP — прямой маршрут.") {
            Segmented(PingMethod.entries, settings.connectionPing, { it.title }) { v -> store.update { it.copy(connectionPing = v) } }
        }
    }
    SettingsCard(BeamIcons.Fingerprint, "Идентификация (HWID)", "Для подписок с ограничением по устройствам") {
        SwitchRow(BeamIcons.Fingerprint, "Отправлять HWID", "Передавать хеш устройства в запросах подписки.", settings.sendHwid) { v ->
            store.update { it.copy(sendHwid = v) }
        }
        if (settings.sendHwid) {
            RowDivider()
            SettingsRow(
                BeamIcons.Sliders,
                "Источник",
                if (settings.hwidManual) "Своё значение вместо хеша устройства" else "Хеш устройства, считается сам",
            ) {
                Segmented(listOf(false, true), settings.hwidManual, { if (it) "Вручную" else "Авто" }) { manual ->
                    if (manual == settings.hwidManual) return@Segmented
                    store.update { s -> s.copy(hwidManual = manual, hwidValue = if (manual && s.hwidValue.isBlank()) context.hwid() else s.hwidValue) }
                    // Choosing «Вручную» is asking to type one: the editor opens right away.
                    if (manual) editingHwid = true
                }
            }
            RowDivider()
            val value = context.effectiveHwid(settings)
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            SettingsRow(
                BeamIcons.Fingerprint,
                if (settings.hwidManual) "Свой HWID" else "HWID",
                value,
                onClick = if (settings.hwidManual) ({ editingHwid = true }) else ({ context.copy("HWID", value) }),
            ) {
                Row {
                    if (settings.hwidManual) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Изменить") { editingHwid = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(BeamIcons.Pencil, "Изменить", Modifier.size(16.dp), tint = BeamTheme.extra.highlight) }
                    }
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Скопировать") { context.copy("HWID", value) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(BeamIcons.Copy, "Скопировать", Modifier.size(16.dp), tint = muted) }
                }
            }
        }
    }
    if (editingHwid) {
        HwidEditorSheet(context.effectiveHwid(settings), onDismiss = { editingHwid = false }) { value ->
            store.update { it.copy(hwidManual = true, hwidValue = value) }
            editingHwid = false
        }
    }
    SettingsCard(BeamIcons.Info, "Экран приветствия", "Первое знакомство с приложением") {
        SettingsRow(
            BeamIcons.Refresh,
            "Пройти заново",
            "Снова показать мастер первоначальной настройки.",
            onClick = { store.update { it.copy(onboardingDone = false) } },
        )
    }
}

// ---- Connection ----

@Composable
private fun ConnectionPage(settings: AppSettings, store: SettingsStore) {
    val context = LocalContext.current
    var editingDns by rememberSaveable { mutableStateOf(false) }
    SettingsCard(BeamIcons.Shield, "Защита от утечек", "Весь трафик телефона идёт через VPN-интерфейс") {
        SwitchRow(BeamIcons.Globe, "IPv6 через VPN", "Пускать IPv6 в туннель, а не блокировать его. Действует со следующего подключения.", settings.ipv6) { v ->
            store.update { it.copy(ipv6 = v) }
        }
    }
    SettingsCard(BeamIcons.Globe, "DNS", "Запросы идут через VPN") {
        TileGrid(
            DnsPreset.entries,
            settings.dns,
            { it.title },
            { if (it == DnsPreset.Custom && settings.dns == it && settings.dnsPrimary.isNotBlank()) settings.dnsPrimary else it.caption },
        ) { v ->
            if (v == DnsPreset.Custom) editingDns = true else store.update { it.copy(dns = v) }
        }
        RowDivider()
        SwitchRow(
            BeamIcons.Profiles,
            "DNS из профиля",
            "Если в подписке есть свой DNS — использовать его. Выбранный выше останется для серверов без своего.",
            settings.profileDns,
        ) { v -> store.update { it.copy(profileDns = v) } }
    }
    if (editingDns) {
        DnsEditorSheet(settings.dnsPrimary, settings.dnsSecondary, onDismiss = { editingDns = false }) { primary, secondary ->
            store.update { it.copy(dns = DnsPreset.Custom, dnsPrimary = primary, dnsSecondary = secondary) }
            editingDns = false
        }
    }
    var editingFragment by rememberSaveable { mutableStateOf(false) }
    SettingsCard(BeamIcons.Zap, "Фрагментация", "Дробит первые пакеты, чтобы обойти блокировки по DPI") {
        val custom = settings.fragmentCustom
        TileGrid(
            Fragmentation.entries,
            settings.fragmentation,
            { it.title },
            { if (it == Fragmentation.Custom && settings.fragmentation == it) "${custom.length} Б · ${custom.interval} мс" else it.caption },
        ) { v ->
            if (v == Fragmentation.Custom) editingFragment = true else store.update { it.copy(fragmentation = v) }
        }
    }
    if (editingFragment) {
        FragmentEditorSheet(settings.fragmentCustom, onDismiss = { editingFragment = false }) { values ->
            store.update { it.copy(fragmentation = Fragmentation.Custom, fragmentCustom = values) }
            editingFragment = false
        }
    }
    SettingsCard(BeamIcons.Layers, "Мультиплексирование", null) {
        SwitchRow(BeamIcons.Layers, "Mux", "Несколько соединений в одном. Сервер должен это поддерживать.", settings.mux) { v ->
            store.update { it.copy(mux = v) }
        }
    }
}

@Composable
private fun <T> TileGrid(options: List<T>, selected: T, title: (T) -> String, caption: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    OptionTile(title(option), caption(option), option == selected, Modifier.weight(1f)) { onSelect(option) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val RangePattern = Regex("""^\d+(-\d+)?$""")

/** «Свои» fragmentation: the desktop «Вручную» fields. The bridge normalizes what it gets; the tile shows the result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FragmentEditorSheet(initial: FragmentCustom, onDismiss: () -> Unit, onSave: (FragmentCustom) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extra = BeamTheme.extra
    var packets by rememberSaveable { mutableStateOf(initial.packets) }
    var length by rememberSaveable { mutableStateOf(initial.length) }
    var interval by rememberSaveable { mutableStateOf(initial.interval) }
    var maxSplit by rememberSaveable { mutableStateOf(initial.maxSplit) }

    val packetsOk = packets.trim().let { it == "tlshello" || RangePattern.matches(it) }
    val lengthOk = RangePattern.matches(length.trim())
    val intervalOk = RangePattern.matches(interval.trim())
    val maxSplitOk = maxSplit.isBlank() || RangePattern.matches(maxSplit.trim())

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
            Text("Своя фрагментация", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Диапазоны пишутся как «от-до». Неверные значения заменятся стандартными.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FragmentField("Пакеты", "tlshello или 1-3", packets, packetsOk, KeyboardType.Text) { packets = it }
            FragmentField("Длина, байт", "100-200", length, lengthOk) { length = it }
            FragmentField("Интервал, мс", "10-20", interval, intervalOk) { interval = it }
            FragmentField("Макс. частей (необязательно)", "Не задано", maxSplit, maxSplitOk) { maxSplit = it }
            val scope = rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { sheet.hide(); onDismiss() } },
                    shape = RoundedCornerShape(scaled(14.dp)),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Отмена") }
                Button(
                    onClick = { onSave(FragmentCustom(packets.trim(), length.trim(), interval.trim(), maxSplit.trim())) },
                    enabled = packetsOk && lengthOk && intervalOk && maxSplitOk,
                    shape = RoundedCornerShape(scaled(14.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Сохранить", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun FragmentField(
    label: String,
    placeholder: String,
    value: String,
    valid: Boolean,
    // The phone pad has the "-" of a range; the number pad often does not.
    keyboard: KeyboardType = KeyboardType.Phone,
    onChange: (String) -> Unit,
) {
    val extra = BeamTheme.extra
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(16)) },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(scaled(14.dp)),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = extra.highlight.copy(alpha = 0.7f), unfocusedBorderColor = extra.border),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun isIpAddress(raw: String): Boolean {
    val v = raw.trim()
    if (Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(v)?.groupValues?.drop(1)?.all { (it.toIntOrNull() ?: 999) <= 255 } == true) return true
    return v.contains(':') && v.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' } && runCatching { java.net.InetAddress.getByName(v) }.isSuccess
}

/** «Свой» DNS: the resolver addresses. The secondary is optional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsEditorSheet(initialPrimary: String, initialSecondary: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extra = BeamTheme.extra
    var primary by rememberSaveable { mutableStateOf(initialPrimary) }
    var secondary by rememberSaveable { mutableStateOf(initialSecondary) }
    val primaryOk = isIpAddress(primary)
    val secondaryOk = secondary.isBlank() || isIpAddress(secondary)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Свой DNS", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "IP-адреса серверов DNS, IPv4 или IPv6. Запросы к ним идут через VPN.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FragmentField("Основной", "1.1.1.1", primary, primary.isEmpty() || primaryOk, KeyboardType.Uri) { primary = it.trim() }
            FragmentField("Запасной (необязательно)", "1.0.0.1", secondary, secondaryOk, KeyboardType.Uri) { secondary = it.trim() }
            val scope = rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { sheet.hide(); onDismiss() } },
                    shape = RoundedCornerShape(scaled(14.dp)),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Отмена") }
                Button(
                    onClick = { onSave(primary.trim(), secondary.trim()) },
                    enabled = primaryOk && secondaryOk,
                    shape = RoundedCornerShape(scaled(14.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Сохранить", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** The HWID set by hand: typed in or generated anew. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HwidEditorSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extra = BeamTheme.extra
    var value by rememberSaveable { mutableStateOf(initial) }
    val ok = value.isNotBlank() && value.none { it.isWhitespace() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("HWID", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Подписки с ограничением по устройствам узнают телефон по этому значению. Новое значение провайдер посчитает новым устройством.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(128) },
                label = { Text("Значение") },
                isError = !ok,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(scaled(14.dp)),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = extra.highlight.copy(alpha = 0.7f), unfocusedBorderColor = extra.border),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { value = randomHwid() },
                shape = RoundedCornerShape(scaled(14.dp)),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(BeamIcons.Refresh, null, Modifier.size(16.dp))
                Text("Сгенерировать", modifier = Modifier.padding(start = 8.dp))
            }
            val scope = rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { sheet.hide(); onDismiss() } },
                    shape = RoundedCornerShape(scaled(14.dp)),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Отмена") }
                Button(
                    onClick = { onSave(value.trim()) },
                    enabled = ok,
                    shape = RoundedCornerShape(scaled(14.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Сохранить", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ---- About ----

/** Desktop AboutApp: wordmark, version in a gradient, build line, licences pill, footer. */
@Composable
private fun AboutPage(onBack: () -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().bottomBarSpace()) {
        PageHeader(SettingsPage.About.icon, SettingsPage.About.title, onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.beam_wordmark), contentDescription = "BEAM", modifier = Modifier.width(240.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                buildAnnotatedString {
                    append("Версия ")
                    withStyle(
                        SpanStyle(
                            brush = Brush.linearGradient(listOf(Color(0xFFFDC700), Color(0xFFFF8904))),
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) { append(info.versionName ?: "—") }
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = muted,
            )
            Spacer(Modifier.height(6.dp))
            var copied by remember { mutableStateOf(false) }
            LaunchedEffect(copied) {
                if (copied) {
                    delay(1800)
                    copied = false
                }
            }
            Text(
                buildAnnotatedString {
                    append("сборка ")
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(BuildConfig.BUILD_HASH) }
                },
                fontSize = 12.sp,
                color = if (copied) BeamTheme.extra.pingGood else muted.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(scaled(6.dp)))
                    .clickable(onClickLabel = "Скопировать хэш сборки") {
                        context.copy("Хэш сборки", BuildConfig.BUILD_HASH)
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                onClick = onLicenses,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, BeamTheme.extra.border.let { it.copy(alpha = it.alpha * 0.06f) }),
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(BeamIcons.Scale, null, Modifier.size(14.dp), tint = muted)
                    Text("Лицензии", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = muted)
                    Icon(BeamIcons.ChevronRight, null, Modifier.size(14.dp), tint = muted.copy(alpha = 0.5f))
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val faint = muted.copy(alpha = 0.5f)
            Text("Powered by Xray & Jetpack Compose", fontSize = 11.sp, color = faint)
            Text("© 2026 Acelate. Все права защищены.", fontSize = 11.sp, color = faint)
            Text(
                buildAnnotatedString {
                    append("Создано с любовью к своему продукту ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("♥") }
                },
                fontSize = 11.sp,
                color = faint,
            )
        }
    }
}

@Composable
private fun EmptyNote(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        Text(
            body,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun Context.copy(label: String, text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
}
