package com.beamvpn.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beamvpn.app.ui.components.AppIcon
import com.beamvpn.app.ui.components.PageHeader
import com.beamvpn.app.ui.components.appLabel
import com.beamvpn.app.ui.home.BeamViewModel
import com.beamvpn.app.ui.home.formatBytes
import com.beamvpn.app.ui.home.formatDuration
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.scaled
import com.beamvpn.app.vpn.ConnectionPhase
import com.beamvpn.app.vpn.ConnectionsApi
import com.beamvpn.app.vpn.Flow
import com.beamvpn.app.vpn.FlowRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REFRESH_MS = 1_000L

/** Desktop's connections view: what every app is talking to right now, and what it closed a moment ago. */
@Composable
fun ConnectionsPage(onBack: () -> Unit, viewModel: BeamViewModel, tabs: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected = viewModel.state.collectAsStateWithLifecycle().value.phase == ConnectionPhase.Connected
    var flows by remember { mutableStateOf<List<Flow>?>(null) }
    var recent by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmCloseAll by remember { mutableStateOf(false) }
    val labels = remember { HashMap<String, String>() }
    fun label(pkg: String) = if (pkg.isEmpty()) "Система" else labels.getOrPut(pkg) { context.appLabel(pkg) }

    LaunchedEffect(connected) {
        if (!connected) {
            flows = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            flows = runCatching { ConnectionsApi.list(context) }.getOrDefault(flows.orEmpty())
            delay(REFRESH_MS)
        }
    }

    val all = flows.orEmpty()
    val active = all.filterNot { it.closed }.sortedByDescending { it.startedAt }
    val closed = all.filter { it.closed }.sortedByDescending { it.endedAt }
    val q = query.trim().lowercase()
    val shown = (if (recent) closed else active).filter { f ->
        q.isEmpty() || q in f.target.lowercase() || q in f.destination.lowercase() || q in label(f.app).lowercase() || q in f.app.lowercase()
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(BeamIcons.ScrollText, "Логи", onBack = onBack) {
            if (active.isNotEmpty()) {
                TextButton(onClick = { confirmCloseAll = true }) { Text("Разорвать все", color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        }
        Box(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) { tabs() }
        when {
            !connected -> Placeholder(BeamIcons.Power, "VPN не подключён", "Соединения видны, пока работает туннель")
            flows == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp + com.beamvpn.app.ui.components.LocalBottomInset.current),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "tabs") {
                    Segmented(listOf(false to "Активные · ${active.size}", true to "Недавние · ${closed.size}"), recent) { recent = it }
                }
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Приложение, домен или адрес", fontSize = 14.sp) },
                        leadingIcon = { Icon(BeamIcons.Search, null, Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(scaled(16.dp)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BeamTheme.extra.highlight.copy(alpha = 0.7f),
                            unfocusedBorderColor = BeamTheme.extra.border,
                            cursorColor = BeamTheme.extra.highlight,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        Placeholder(
                            BeamIcons.Layers,
                            when {
                                q.isNotEmpty() -> "Ничего не найдено"
                                recent -> "Закрытых соединений нет"
                                else -> "Нет активных соединений"
                            },
                            if (recent) "Здесь видны соединения, закрытые за последние минуты" else null,
                            Modifier.height(240.dp),
                        )
                    }
                } else {
                    items(shown, key = { it.id + if (it.closed) "/c" else "" }) { flow ->
                        FlowRow(flow, label(flow.app), Modifier.animateItem()) { openId = flow.id }
                    }
                }
            }
        }
    }

    openId?.let { id -> all.firstOrNull { it.id == id } }?.let { flow ->
        FlowSheet(
            flow = flow,
            appName = label(flow.app),
            onDismiss = { openId = null },
            onClose = {
                scope.launch {
                    ConnectionsApi.close(context, listOf(flow.id))
                    openId = null
                }
            },
        )
    }

    if (confirmCloseAll) {
        AlertDialog(
            onDismissRequest = { confirmCloseAll = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Разорвать все соединения?") },
            text = { Text("Приложения переподключатся сами, но загрузки и звонки могут прерваться.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCloseAll = false
                    val ids = active.map { it.id }
                    scope.launch {
                        val count = runCatching { ConnectionsApi.close(context, ids) }.getOrDefault(0)
                        Toast.makeText(context, "Разорвано: $count", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Разорвать", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmCloseAll = false }) { Text("Отмена") } },
        )
    }
}

private fun routeLabel(flow: Flow): String = when (flow.route) {
    FlowRoute.Proxy -> "VPN"
    FlowRoute.Direct -> "Напрямую"
    FlowRoute.Block -> "Блок"
    FlowRoute.Dns -> "DNS"
    FlowRoute.Unknown -> flow.outbound.ifEmpty { "—" }
}

private fun ruleLabel(flow: Flow): String = when (flow.ruleKind) {
    "app" -> "Правило приложения"
    "rules" -> "Правило маршрутизации"
    "default" -> "По умолчанию"
    "lan" -> "Локальная сеть"
    "dns" -> "DNS"
    else -> flow.rule.ifEmpty { "—" }
}

@Composable
private fun routeColor(route: FlowRoute): Color = when (route) {
    FlowRoute.Proxy -> BeamTheme.extra.highlight
    FlowRoute.Block -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun FlowRow(flow: Flow, appName: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(scaled(16.dp)))
            .background(colors.surfaceContainer.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(flow.app, fallback = BeamIcons.Smartphone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(appName, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RouteChip(flow)
                Text(flow.target, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("↓ ${formatBytes(flow.download)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = BeamTheme.extra.download)
            Text("↑ ${formatBytes(flow.upload)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = BeamTheme.extra.upload)
        }
    }
}

@Composable
private fun RouteChip(flow: Flow) {
    val tone = routeColor(flow.route)
    Text(
        routeLabel(flow),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = tone,
        maxLines = 1,
        modifier = Modifier.background(tone.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowSheet(flow: Flow, appName: String, onDismiss: () -> Unit, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(flow.app, 44.dp, fallback = BeamIcons.Smartphone)
                Column(Modifier.weight(1f)) {
                    Text(appName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (flow.app.isNotEmpty() && flow.app != appName) {
                        Text(flow.app, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                RouteChip(flow)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(scaled(16.dp))).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(vertical = 4.dp),
            ) {
                Detail("Адрес", flow.target)
                if (flow.destination.isNotEmpty() && flow.destination != flow.target) Detail("Назначение", flow.destination)
                Detail("Протокол", listOf(flow.network.uppercase(), flow.protocol.uppercase()).filter { it.isNotEmpty() }.joinToString(" · "))
                Detail("Маршрут", if (flow.route == FlowRoute.Proxy && flow.nodeName.isNotEmpty()) "VPN · ${flow.nodeName}" else routeLabel(flow))
                Detail("Причина", ruleLabel(flow))
                Detail("Скачано", formatBytes(flow.download))
                Detail("Отдано", formatBytes(flow.upload))
                Detail(if (flow.closed) "Длилось" else "Идёт", formatDuration(flow.durationSec))
            }
            if (!flow.closed) {
                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(scaled(16.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Разорвать соединение", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        Text(value, fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Placeholder(icon: ImageVector, title: String, body: String?, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        if (body != null) {
            Text(body, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
