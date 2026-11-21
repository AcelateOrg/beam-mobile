package com.beamvpn.app.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beamvpn.app.ui.components.CountryFlag
import com.beamvpn.app.ui.home.BeamViewModel
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.scaled
import com.beamvpn.app.vpn.NodeConfig
import com.beamvpn.app.vpn.atPath
import com.beamvpn.app.vpn.jsonKey
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// The fields of desktop's NodeConfigDialog, keyed by the outbound's dotted paths.

private enum class FieldKind { Text, Number, Switch, List, Select }

private class FieldSpec(
    val path: String,
    val label: String,
    val kind: FieldKind,
    val help: String,
    val always: Boolean = false,
    val options: List<String> = emptyList(),
    val placeholder: String? = null,
)

private class FieldGroup(val title: String, val icon: ImageVector, val fields: List<FieldSpec>)

private val Fingerprints = listOf("chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized")

private val FieldGroups = listOf(
    FieldGroup(
        "Сервер", BeamIcons.Servers,
        listOf(
            FieldSpec("server", "Адрес", FieldKind.Text, "Домен или IP-адрес, куда подключаться.", always = true, placeholder = "example.com"),
            FieldSpec("server_port", "Порт", FieldKind.Number, "Порт сервера. Для REALITY почти всегда 443.", always = true, placeholder = "443"),
        ),
    ),
    FieldGroup(
        "Доступ", BeamIcons.Lock,
        listOf(
            FieldSpec("uuid", "UUID", FieldKind.Text, "Идентификатор пользователя для VLESS и VMess."),
            FieldSpec("password", "Пароль", FieldKind.Text, "Пароль для Trojan и Shadowsocks."),
            FieldSpec("method", "Шифр", FieldKind.Text, "Метод шифрования Shadowsocks, например aes-256-gcm."),
            FieldSpec("encryption", "Шифрование", FieldKind.Text, "У VLESS шифрования нет, поэтому нормальное значение — none."),
            FieldSpec("flow", "Поток XTLS", FieldKind.Select, "Должен совпадать с настройкой сервера: лишний или пропущенный flow ломает соединение.", options = listOf("xtls-rprx-vision")),
            FieldSpec("packet_encoding", "Упаковка UDP", FieldKind.Select, "Как передавать UDP внутри соединения. Для VLESS обычный выбор — xudp.", options = listOf("xudp", "packetaddr")),
        ),
    ),
    FieldGroup(
        "TLS", BeamIcons.Shield,
        listOf(
            FieldSpec("tls.enabled", "Использовать TLS", FieldKind.Switch, "Шифровать соединение по TLS."),
            FieldSpec("tls.server_name", "SNI", FieldKind.Text, "Имя сервера в рукопожатии. Для REALITY это чужой сайт-маскировка, например dl.google.com.", placeholder = "dl.google.com"),
            FieldSpec("tls.alpn", "ALPN", FieldKind.List, "Протоколы через запятую, например h2, http/1.1.", placeholder = "h2, http/1.1"),
            FieldSpec("tls.utls.enabled", "Маскировка рукопожатия", FieldKind.Switch, "Притворяться браузером на уровне TLS (uTLS)."),
            FieldSpec("tls.utls.fingerprint", "Отпечаток браузера", FieldKind.Select, "Под какой браузер маскироваться. Если соединение не поднимается, попробуйте chrome.", options = Fingerprints),
            FieldSpec("tls.insecure", "Не проверять сертификат", FieldKind.Switch, "Отключает проверку сертификата. Небезопасно, только для диагностики."),
        ),
    ),
    FieldGroup(
        "REALITY", BeamIcons.Shield,
        listOf(
            FieldSpec("tls.reality.enabled", "Использовать REALITY", FieldKind.Switch, "Маскировка под чужой сайт вместо обычного TLS-сертификата."),
            FieldSpec("tls.reality.public_key", "Публичный ключ", FieldKind.Text, "Ключ сервера. В ссылках подписки обозначается как pbk."),
            FieldSpec("tls.reality.short_id", "Короткий ID", FieldKind.Text, "Короткий идентификатор клиента. В ссылках подписки обозначается как sid."),
        ),
    ),
    FieldGroup(
        "Транспорт", BeamIcons.Layers,
        listOf(
            FieldSpec("transport.type", "Тип", FieldKind.Text, "ws, grpc, httpupgrade, xhttp. Пусто — обычный TCP."),
            FieldSpec("transport.path", "Путь", FieldKind.Text, "Путь запроса для WebSocket, HTTPUpgrade и XHTTP."),
            FieldSpec("transport.mode", "Режим XHTTP", FieldKind.Text, "auto, packet-up, stream-up или stream-one. Пусто — auto."),
            FieldSpec("transport.service_name", "Сервис gRPC", FieldKind.Text, "Имя gRPC-сервиса."),
        ),
    ),
)

// BEAM's own options of a server, not protocol parameters.
private const val FRAGMENT_PATH = "beam.fragment"
private const val MUX_PATH = "beam.mux"
private const val ENTRY_PATH = "beam.entry"

private val KnownTopLevel = setOf("beam", "tag", "type") + FieldGroups.flatMap { g -> g.fields.map { it.path.substringBefore('.') } }

private fun inputText(value: Any?): String = when (value) {
    null -> ""
    is JSONArray -> List(value.length()) { value.opt(it).toString() }.joinToString(", ")
    is List<*> -> value.joinToString(", ")
    is Double -> if (value == Math.floor(value)) value.toLong().toString() else value.toString()
    else -> value.toString()
}

private fun parseInput(kind: FieldKind, raw: String): Any? {
    if (raw.isBlank()) return null
    return when (kind) {
        FieldKind.Number -> raw.trim().toLongOrNull() ?: raw.trim().toDoubleOrNull() ?: raw
        FieldKind.List -> raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> raw
    }
}

/** A server picked as the entry of a chain, core/types ProTunServerRef. */
private class ServerRef(val profileId: String, val nodeId: String, val name: String, val profileName: String) {
    fun toJson(): JSONObject = JSONObject().put("profileId", profileId).put("nodeId", nodeId).put("name", name).put("profileName", profileName)
}

/**
 * Desktop's NodeConfigDialog as a sheet: every field shows the provider's value until it is changed; a changed one
 * gets a dot and a reset button. Saving on a live tunnel of the same profile rebuilds the core.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorSheet(profileId: String, nodeId: String, viewModel: BeamViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val extra = BeamTheme.extra
    var config by remember { mutableStateOf<NodeConfig?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }
    val overrides = remember { mutableStateMapOf<String, Any?>() }
    var pickingEntry by remember { mutableStateOf(false) }

    LaunchedEffect(profileId, nodeId) {
        runCatching { viewModel.nodeConfig(profileId, nodeId) }
            .onSuccess { c ->
                config = c
                name = c.name
                pinned = c.isPinned
                overrides.clear()
                overrides.putAll(c.overrides)
            }
            .onFailure { failure = it.message ?: "Сервер не найден" }
    }

    fun close() {
        scope.launch {
            sheet.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding().imePadding()) {
            val c = config
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(BeamIcons.Sliders, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    c?.name?.ifEmpty { null } ?: "Конфигурация",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (c != null && c.type.isNotEmpty()) {
                    Text(
                        c.type.uppercase(),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.background(extra.accentFill, RoundedCornerShape(scaled(6.dp))).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Закрыть") { close() }, contentAlignment = Alignment.Center) {
                    Icon(BeamIcons.Close, "Закрыть", Modifier.size(18.dp))
                }
            }

            when {
                failure != null -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(failure!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                c == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                else -> {
                    fun valueOf(path: String): Any? = if (path in overrides) overrides[path] else c.outbound.atPath(path)
                    // BEAM options exist only as edits; a pinned server has its edits baked into the outbound.
                    fun nodeOption(path: String): Any? = if (path in overrides) overrides[path] else if (c.isPinned) c.outbound.atPath(path) else null
                    val visible = FieldGroups.map { g ->
                        g to g.fields.filter { f ->
                            f.always || f.path in overrides || c.outbound.atPath(f.path) != null ||
                                (f.path.startsWith("tls.") && c.outbound.atPath("tls") != null)
                        }
                    }.filter { it.second.isNotEmpty() }
                    val others = c.outbound.keys().asSequence().filter { it !in KnownTopLevel }.toList()

                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Section("Основное", BeamIcons.Sliders) {
                            TextFieldRow(
                                label = "Название",
                                help = "Только для отображения в списке, на подключение не влияет.",
                                value = name,
                                placeholder = c.baseName,
                                changed = name != c.baseName,
                                onReset = { name = c.baseName },
                                onChange = { name = it },
                            )
                        }
                        if (c.rawLinkStale) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(scaled(14.dp))).background(extra.warning.copy(alpha = 0.08f)).padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(BeamIcons.AlertCircle, null, Modifier.size(16.dp), tint = extra.warning)
                                Text(
                                    "Сервер исключён из экспорта ссылок: исходная ссылка описывает конфиг до правок.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        visible.forEach { (group, fields) ->
                            Section(group.title, group.icon) {
                                fields.forEachIndexed { i, f ->
                                    if (i > 0) RowSeparator()
                                    val value = valueOf(f.path)
                                    val changed = jsonKey(value) != jsonKey(c.original.atPath(f.path))
                                    val reset = { overrides.remove(f.path); Unit }
                                    when (f.kind) {
                                        FieldKind.Switch -> SwitchFieldRow(f.label, f.help, value == true, changed, reset) { overrides[f.path] = it }
                                        FieldKind.Select -> SelectFieldRow(f.label, f.help, inputText(value), f.options, "не задано", changed, reset) {
                                            overrides[f.path] = it
                                        }
                                        else -> TextFieldRow(
                                            label = f.label,
                                            help = f.help,
                                            value = inputText(value),
                                            placeholder = f.placeholder ?: "не задано",
                                            changed = changed,
                                            numeric = f.kind == FieldKind.Number,
                                            onReset = reset,
                                            onChange = { overrides[f.path] = parseInput(f.kind, it) },
                                        )
                                    }
                                }
                            }
                        }
                        Section("Ядро", BeamIcons.Zap) {
                            SwitchFieldRow(
                                "Фрагментация",
                                "Выключено — сервер не фрагментируется, даже если фрагментация включена в настройках соединения.",
                                nodeOption(FRAGMENT_PATH) != false,
                                FRAGMENT_PATH in overrides,
                                { overrides.remove(FRAGMENT_PATH) },
                            ) { on ->
                                when {
                                    !on -> overrides[FRAGMENT_PATH] = false
                                    c.isPinned -> overrides[FRAGMENT_PATH] = true
                                    else -> overrides.remove(FRAGMENT_PATH)
                                }
                            }
                            RowSeparator()
                            val mux = nodeOption(MUX_PATH)?.toString()?.takeIf { it == "on" || it == "off" }.orEmpty()
                            SelectFieldRow(
                                "Mux",
                                "Переопределяет общий выключатель Mux. На XTLS Vision и Hysteria Mux не работает никогда.",
                                mux,
                                listOf("on", "off"),
                                "как в настройках",
                                MUX_PATH in overrides,
                                { overrides.remove(MUX_PATH) },
                                optionLabel = { if (it == "on") "Включён" else "Выключен" },
                            ) { next ->
                                when {
                                    next != null -> overrides[MUX_PATH] = next
                                    c.isPinned -> overrides[MUX_PATH] = ""
                                    else -> overrides.remove(MUX_PATH)
                                }
                            }
                        }
                        Section("Цепочка", BeamIcons.Link) {
                            val entry = nodeOption(ENTRY_PATH) as? JSONObject
                            FieldRowFrame(
                                label = "Через сервер",
                                help = "Трафик сначала идёт через выбранный сервер, затем через этот. Работает по TCP: Hysteria2 и WireGuard как выход не поддерживаются.",
                                changed = ENTRY_PATH in overrides,
                                onReset = { if (c.isPinned) overrides[ENTRY_PATH] = null else overrides.remove(ENTRY_PATH) },
                            ) {
                                OutlinedButton(
                                    onClick = { pickingEntry = true },
                                    shape = RoundedCornerShape(scaled(14.dp)),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        entry?.let { "${it.optString("name")} · ${it.optString("profileName")}" } ?: "Не выбран",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (c.hasSubscription) {
                            Section("Подписка", BeamIcons.Refresh) {
                                SwitchFieldRow(
                                    "Не обновлять из подписки",
                                    "Включено — конфиг фиксируется целиком. Выключено — правки накладываются поверх данных провайдера.",
                                    pinned,
                                    changed = false,
                                    onReset = null,
                                ) { pinned = it }
                            }
                        }
                        if (others.isNotEmpty()) {
                            Section("Прочее", BeamIcons.Info) {
                                others.forEachIndexed { i, key ->
                                    if (i > 0) RowSeparator()
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Text(key, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(c.outbound.opt(key)?.toString().orEmpty(), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        Box(Modifier.height(8.dp))
                    }

                    val dirty = name != c.name || pinned != c.isPinned ||
                        overrides.keys != c.overrides.keys || overrides.any { (k, v) -> jsonKey(v) != jsonKey(c.overrides[k]) }
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = ::close,
                            shape = RoundedCornerShape(scaled(14.dp)),
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("Отмена") }
                        Button(
                            onClick = {
                                saving = true
                                scope.launch {
                                    val error = viewModel.saveNodeConfig(profileId, nodeId, name.trim(), overrides.toMap(), pinned)
                                    saving = false
                                    if (error == null) close() else failure = error
                                }
                            },
                            enabled = dirty && !saving,
                            shape = RoundedCornerShape(scaled(14.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = extra.onHighlight)
                            else Text("Сохранить", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (pickingEntry) {
        EntryPickerSheet(
            viewModel = viewModel,
            exclude = profileId to nodeId,
            onDismiss = { pickingEntry = false },
            onPick = { ref ->
                when {
                    ref != null -> overrides[ENTRY_PATH] = ref.toJson()
                    config?.isPinned == true -> overrides[ENTRY_PATH] = null
                    else -> overrides.remove(ENTRY_PATH)
                }
                pickingEntry = false
            },
        )
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(scaled(16.dp))).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            content = content,
        )
    }
}

@Composable
private fun RowSeparator() = HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = BeamTheme.extra.border.let { it.copy(alpha = it.alpha * 0.5f) })

/** Label, help, the changed dot and reset; [control] goes under them, full width, where a thumb can reach it. */
@Composable
private fun FieldRowFrame(label: String, help: String, changed: Boolean, onReset: (() -> Unit)?, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (changed) Box(Modifier.size(6.dp).background(BeamTheme.extra.highlight, CircleShape))
                }
                Text(help, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onReset != null && changed) ResetButton(onReset)
        }
        Box(Modifier.padding(end = 10.dp)) { control() }
    }
}

@Composable
private fun ResetButton(onReset: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Вернуть значение провайдера", onClick = onReset),
        contentAlignment = Alignment.Center,
    ) { Icon(BeamIcons.Refresh, "Вернуть значение провайдера", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun TextFieldRow(
    label: String,
    help: String,
    value: String,
    placeholder: String,
    changed: Boolean,
    onReset: () -> Unit,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    val extra = BeamTheme.extra
    FieldRowFrame(label, help, changed, onReset) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Ascii, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(scaled(14.dp)),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = extra.highlight.copy(alpha = 0.7f), unfocusedBorderColor = extra.border, cursorColor = extra.highlight),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchFieldRow(label: String, help: String, checked: Boolean, changed: Boolean, onReset: (() -> Unit)?, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onChange(!checked) }.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (changed) Box(Modifier.size(6.dp).background(BeamTheme.extra.highlight, CircleShape))
            }
            Text(help, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onReset != null && changed) ResetButton(onReset)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = BeamTheme.extra.highlight, checkedThumbColor = BeamTheme.extra.onHighlight),
        )
    }
}

@Composable
private fun SelectFieldRow(
    label: String,
    help: String,
    value: String,
    options: List<String>,
    noneLabel: String,
    changed: Boolean,
    onReset: () -> Unit,
    optionLabel: (String) -> String = { it },
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    FieldRowFrame(label, help, changed, onReset) {
        Box {
            OutlinedButton(
                onClick = { open = true },
                shape = RoundedCornerShape(scaled(14.dp)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (value.isEmpty()) noneLabel else optionLabel(value), modifier = Modifier.weight(1f), maxLines = 1)
                Icon(BeamIcons.ChevronDown, null, Modifier.size(16.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                DropdownMenuItem(text = { Text(noneLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { open = false; onSelect(null) })
                // A value the provider set that is not in the list stays selectable.
                (if (value.isNotEmpty() && value !in options) listOf(value) + options else options).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        trailingIcon = if (option == value) ({ Icon(BeamIcons.Check, null, Modifier.size(16.dp)) }) else null,
                        onClick = { open = false; onSelect(option) },
                    )
                }
            }
        }
    }
}

/** Every server of every profile, to pick the entry of a chain. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryPickerSheet(viewModel: BeamViewModel, exclude: Pair<String, String>, onDismiss: () -> Unit, onPick: (ServerRef?) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val refs = remember(state.profiles, state.nodes) {
        state.profiles.flatMap { p ->
            state.nodes[p.id]?.groups.orEmpty().flatMap { g -> g.nodes }
                .filter { p.id to it.id != exclude }
                .map { n -> ServerRef(p.id, n.id, n.parsed.displayName, p.name) to n.parsed.countryCode }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxHeight(0.8f).navigationBarsPadding().padding(horizontal = 12.dp)) {
            Text("Через сервер", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                item(key = "none") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(scaled(14.dp))).clickable { onPick(null) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text("Без цепочки", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(refs, key = { (ref, _) -> "${ref.profileId}/${ref.nodeId}" }) { (ref, country) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(scaled(14.dp))).clickable { onPick(ref) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CountryFlag(country, 24.dp)
                        Column(Modifier.weight(1f)) {
                            Text(ref.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ref.profileName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
