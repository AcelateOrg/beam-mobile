package com.beamvpn.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamvpn.app.ui.components.PageHeader
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.scaled
import com.beamvpn.app.vpn.BridgeRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobile.Mobile
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One journal line, beam-bridge's types.LogEntry. */
private data class LogLine(val id: Long, val time: Long, val level: String, val message: String, val source: String)

private val Levels = listOf("off" to "Выкл", "error" to "Ошибки", "info" to "Основные", "debug" to "Подробные")

private const val KEEP = 2000
private const val POLL_MS = 1_000L

private fun parse(json: String): List<LogLine> {
    val a = JSONArray(json)
    return List(a.length()) { i ->
        val o = a.getJSONObject(i)
        LogLine(o.optLong("id"), o.optLong("timestamp"), o.optString("level"), o.optString("message"), o.optString("source"))
    }
}

private val TimeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

private fun LogLine.plain() = "${TimeFormat.format(Date(time))} ${level.uppercase().padEnd(5)} ${if (source.isNotEmpty()) "[$source] " else ""}$message"

@Composable
private fun levelColor(level: String): Color = when (level) {
    "error", "fatal" -> MaterialTheme.colorScheme.error
    "warn", "warning" -> BeamTheme.extra.warning
    "info" -> BeamTheme.extra.highlight
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private enum class LogsTab { Logs, Connections }

/** "Логи", as on the desktop: the core's journal and, on the second tab, the live connections. */
@Composable
fun LogsHub(onBack: () -> Unit, viewModel: com.beamvpn.app.ui.home.BeamViewModel) {
    var tab by rememberSaveable { mutableStateOf(LogsTab.Logs) }
    val tabs: @Composable () -> Unit = {
        PageTabs(listOf(LogsTab.Logs to "Логи", LogsTab.Connections to "Соединения"), tab) { tab = it }
    }
    when (tab) {
        LogsTab.Logs -> LogsPage(onBack, tabs)
        LogsTab.Connections -> ConnectionsPage(onBack, viewModel, tabs)
    }
}

/** "Логи": the core's journal, live, with desktop's levels, a filter, copy, share and clear. */
@Composable
fun LogsPage(onBack: () -> Unit, tabs: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var level by remember { mutableStateOf("info") }
    var query by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val extra = BeamTheme.extra
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(Unit) {
        level = withContext(Dispatchers.IO) { runCatching { BridgeRuntime.ensureInit(context); Mobile.logLevel() }.getOrDefault("info") }
        while (true) {
            val after = lines.lastOrNull()?.id ?: 0L
            val fresh = withContext(Dispatchers.IO) { runCatching { parse(Mobile.logs(after, 500)) }.getOrDefault(emptyList()) }
            if (fresh.isNotEmpty()) {
                // Stick to the bottom only when the reader already is there.
                val atBottom = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= lines.size - 2 } ?: true
                lines = (lines + fresh).takeLast(KEEP)
                if (atBottom && query.isBlank()) list.scrollToItem((lines.size - 1).coerceAtLeast(0))
            }
            delay(POLL_MS)
        }
    }

    val shown by remember(lines, query) {
        derivedStateOf {
            val q = query.trim().lowercase()
            if (q.isEmpty()) lines else lines.filter { it.message.lowercase().contains(q) || it.source.lowercase().contains(q) }
        }
    }

    fun allText() = shown.joinToString("\n") { it.plain() }

    Column(Modifier.fillMaxSize()) {
        PageHeader(BeamIcons.ScrollText, "Логи", onBack = onBack) {
            HeaderAction(BeamIcons.Copy, "Скопировать") {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("BEAM logs", allText()))
                if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
            }
            HeaderAction(BeamIcons.Share, "Поделиться") {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, allText())
                context.startActivity(Intent.createChooser(send, "Отправить логи").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            HeaderAction(BeamIcons.Trash, "Очистить") {
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { Mobile.clearLogs() } }
                    lines = emptyList()
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tabs()
            Segmented(Levels, level) { next ->
                level = next
                scope.launch { withContext(Dispatchers.IO) { runCatching { Mobile.setLogLevel(next) } } }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Фильтр по тексту", fontSize = 14.sp) },
                leadingIcon = { Icon(BeamIcons.Search, null, Modifier.size(16.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(scaled(14.dp)),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = extra.highlight.copy(alpha = 0.7f), unfocusedBorderColor = extra.border),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        level == "off" -> "Журнал выключен. Выберите уровень, чтобы ядро снова писало сообщения."
                        query.isNotBlank() -> "Ничего не найдено"
                        else -> "Журнал пуст. Сообщения ядра появятся здесь после подключения."
                    },
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = muted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp + com.beamvpn.app.ui.components.LocalBottomInset.current),
            ) {
                items(shown, key = { it.id }) { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(levelColor(line.level)))
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(TimeFormat.format(Date(line.time)), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = muted)
                                if (line.source.isNotEmpty()) Text(line.source, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = muted)
                            }
                            Text(
                                line.message,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (line.level in setOf("error", "fatal", "warn", "warning")) levelColor(line.level) else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
