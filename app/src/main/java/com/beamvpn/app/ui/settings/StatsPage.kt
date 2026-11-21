package com.beamvpn.app.ui.settings

import com.beamvpn.app.ui.components.bottomBarSpace
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.beamvpn.app.ui.components.PageHeader
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.scaled
import com.beamvpn.app.vpn.BridgeRuntime
import com.beamvpn.app.vpn.PROXY_APP
import com.beamvpn.app.vpn.UsageItem
import com.beamvpn.app.vpn.UsageReport
import com.beamvpn.app.vpn.parseUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobile.Mobile
import java.time.LocalDate
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private val Periods = listOf("today" to "Сегодня", "week" to "7 дней", "month" to "30 дней", "all" to "Всё время")

/** Statistics accrue every 10 seconds, so polling faster is pointless. */
private const val REFRESH_MS = 10_000L

// ---- Formatting, as in desktop's Analytics page ----

private val ByteUnits = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")

private fun fmtBytes(bytes: Long): String {
    if (bytes < 1) return "0 Б"
    val i = min(floor(ln(bytes.toDouble()) / ln(1024.0)).toInt(), ByteUnits.size - 1)
    val value = bytes / 1024.0.pow(i)
    val digits = if (value >= 100 || i == 0) 0 else 1
    return String.format(Locale("ru"), "%.${digits}f", value) + " " + ByteUnits[i]
}

private fun min(a: Int, b: Int) = if (a < b) a else b

private fun fmtTime(seconds: Long): String {
    if (seconds < 60) return if (seconds > 0) "$seconds с" else "0 мин"
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    return when {
        h == 0L -> "$m мин"
        h >= 100 || m == 0L -> "$h ч"
        else -> "$h ч $m мин"
    }
}

private fun plural(n: Long, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1L && mod100 != 11L -> one
        mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> few
        else -> many
    }
}

private fun bucketLabel(key: String, hourly: Boolean): String =
    if (hourly) "${key.padStart(2, '0')}:00" else runCatching { LocalDate.parse(key).let { "%02d.%02d".format(it.dayOfMonth, it.monthValue) } }.getOrDefault(key)

private fun bucketTitle(key: String, hourly: Boolean): String {
    if (hourly) {
        val h = key.toIntOrNull() ?: return key
        return "%02d:00 – %02d:00".format(h, (h + 1) % 24)
    }
    return runCatching {
        val d = LocalDate.parse(key)
        val month = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")[d.monthValue - 1]
        "${d.dayOfMonth} $month"
    }.getOrDefault(key)
}

private fun sinceText(since: String): String? = runCatching {
    val d = LocalDate.parse(since)
    val month = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")[d.monthValue - 1]
    "с ${d.dayOfMonth} $month"
}.getOrNull()

// ---- App labels and icons ----

private fun Context.appLabel(pkg: String): String = runCatching {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

private fun UsageItem.displayName(context: Context): String = when {
    id == PROXY_APP || name == PROXY_APP -> "Через прокси"
    name.isEmpty() -> "Системные"
    else -> context.appLabel(name)
}

@Composable
private fun AppIcon(pkg: String, fallbackColor: Color, fallbackText: String, isProxy: Boolean) {
    val context = LocalContext.current
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pkg) {
        value = if (pkg.isEmpty()) null else withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(scaled(8.dp))).background(
            if (icon != null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else fallbackColor.copy(alpha = 0.15f),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        when {
            bitmap != null -> Image(bitmap, null, Modifier.size(22.dp))
            isProxy -> Icon(BeamIcons.Globe, null, Modifier.size(16.dp), tint = fallbackColor)
            else -> Text(fallbackText.take(1).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fallbackColor)
        }
    }
}

// ---- Building blocks ----

@Composable
private fun StatBlock(icon: ImageVector, title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val extra = BeamTheme.extra
    Surface(
        shape = RoundedCornerShape(scaled(24.dp)),
        color = extra.shell,
        border = BorderStroke(1.dp, extra.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(30.dp).background(extra.highlight.copy(alpha = 0.10f), RoundedCornerShape(scaled(10.dp))),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(14.dp), tint = extra.highlight) }
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            HorizontalDivider(color = extra.border.let { it.copy(alpha = it.alpha * 0.04f) })
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun EmptyBlock(text: String) {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Kpi(icon: ImageVector, label: String, value: String, sub: String?, color: Color, modifier: Modifier = Modifier) {
    val extra = BeamTheme.extra
    Surface(
        shape = RoundedCornerShape(scaled(20.dp)),
        color = extra.shell,
        border = BorderStroke(1.dp, extra.border),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(24.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(scaled(8.dp))),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(13.dp), tint = color) }
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
            Text(
                sub.orEmpty(),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(16.dp).padding(top = 2.dp),
            )
        }
    }
}

private class Slice(val name: String, val value: Long, val color: Color)

@Composable
private fun Donut(slices: List<Slice>, center: String, caption: String) {
    val track = BeamTheme.extra.tint.copy(alpha = 0.06f)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.17f
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                val sum = slices.sumOf { it.value }.toFloat().coerceAtLeast(1f)
                var start = -90f
                slices.forEach { s ->
                    val sweep = s.value / sum * 360f
                    val gap = if (slices.size > 1) 2f else 0f
                    drawArc(s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(center, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(caption, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, name: String, sub: String?, value: String, share: Double?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (share != null) Text("${share.roundToInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
    }
}

/** The biggest [n] by [value] plus the sum of the rest, like desktop's topWithOthers. */
private fun topWithOthers(items: List<UsageItem>, n: Int, value: (UsageItem) -> Long): Triple<List<UsageItem>, Long, Int> {
    val sorted = items.filter { value(it) > 0 }.sortedByDescending(value)
    val rest = sorted.drop(n)
    return Triple(sorted.take(n), rest.sumOf(value), rest.size)
}

// ---- Blocks ----

@Composable
private fun TrafficBlock(report: UsageReport) {
    val charts = BeamTheme.extra.charts
    val down = charts[0]
    val up = charts[1]
    val data = report.series
    val hasData = data.any { it.up + it.down > 0 }
    var selected by remember(report.period, data.size) { mutableStateOf<Int?>(null) }
    val gridColor = BeamTheme.extra.tint.copy(alpha = 0.08f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    StatBlock(BeamIcons.BarChart, "Трафик") {
        if (!hasData) {
            EmptyBlock("За этот период трафика не было")
            return@StatBlock
        }
        val maxValue = data.maxOf { it.up + it.down }.coerceAtLeast(1)
        Column {
            Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxHeight().width(54.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                    Text(fmtBytes(maxValue), fontSize = 10.sp, color = muted, maxLines = 1)
                    Text(fmtBytes(maxValue / 2), fontSize = 10.sp, color = muted, maxLines = 1)
                    Text("0", fontSize = 10.sp, color = muted)
                }
                Canvas(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(data.size) {
                            detectTapGestures { pos ->
                                val slot = size.width / data.size
                                selected = (pos.x / slot).toInt().coerceIn(0, data.size - 1).takeIf { i -> data[i].up + data[i].down > 0 }
                            }
                        },
                ) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    listOf(0f, 0.5f, 1f).forEach { f ->
                        val y = size.height * (1 - f) - if (f == 0f) 0.5f else 0f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = dash)
                    }
                    val slot = size.width / data.size
                    val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                    data.forEachIndexed { i, b ->
                        val x = slot * i + (slot - barW) / 2
                        val downH = size.height * b.down / maxValue
                        val upH = size.height * b.up / maxValue
                        val dim = if (selected != null && selected != i) 0.35f else 1f
                        val r = 3.dp.toPx()
                        if (downH > 0) drawRoundRect(down.copy(alpha = dim), Offset(x, size.height - downH), Size(barW, downH), androidx.compose.ui.geometry.CornerRadius(r))
                        if (upH > 0) drawRoundRect(up.copy(alpha = dim), Offset(x, size.height - downH - upH), Size(barW, upH), androidx.compose.ui.geometry.CornerRadius(r))
                    }
                }
            }
            // Four evenly spread labels under the bars.
            Row(Modifier.fillMaxWidth().padding(start = 62.dp, top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val labelCount = minOf(5, data.size)
                (0 until labelCount).map { (it * (data.size - 1)).toDouble() / max(labelCount - 1, 1) }.forEach { pos ->
                    Text(bucketLabel(data[pos.roundToInt()].key, report.hourly), fontSize = 10.sp, color = muted, maxLines = 1)
                }
            }
            val sel = selected?.let { data[it] }
            Text(
                if (sel != null) "${bucketTitle(sel.key, report.hourly)} · ↓ ${fmtBytes(sel.down)}  ↑ ${fmtBytes(sel.up)}" else "Нажмите на столбец, чтобы увидеть значения",
                fontSize = 12.sp,
                color = if (sel != null) MaterialTheme.colorScheme.onSurface else muted,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                Legend(down, "Скачано")
                Box(Modifier.width(16.dp))
                Legend(up, "Отдано")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RoutesBlock(report: UsageReport) {
    val charts = BeamTheme.extra.charts
    val proxy = report.routeProxy
    val direct = report.routeDirect
    val sum = proxy + direct
    StatBlock(BeamIcons.Globe, "Куда идёт трафик") {
        if (sum <= 0) {
            EmptyBlock("Появится после подключения")
            return@StatBlock
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Donut(
                listOf(Slice("Через VPN", proxy, charts[0]), Slice("Напрямую", direct, charts[2])).filter { it.value > 0 },
                "${(proxy * 100.0 / sum).roundToInt()}%",
                "через VPN",
            )
            Column {
                LegendRow(charts[0], "Через VPN", null, fmtBytes(proxy), proxy * 100.0 / sum)
                LegendRow(charts[2], "Напрямую", null, fmtBytes(direct), direct * 100.0 / sum)
                if (report.routeBlocked > 0) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(scaled(8.dp)))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Заблокировано", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        Text(
                            "${report.routeBlocked} ${plural(report.routeBlocked, "соединение", "соединения", "соединений")}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilesBlock(report: UsageReport) {
    val charts = BeamTheme.extra.charts
    val (top, others, othersCount) = topWithOthers(report.profiles, 4) { it.total }
    val sum = top.sumOf { it.total } + others
    StatBlock(BeamIcons.Folder, "Профили") {
        if (sum <= 0) {
            EmptyBlock("Нет данных за этот период")
            return@StatBlock
        }
        val rows = top.mapIndexed { i, item -> Triple(item.name.ifEmpty { "Без названия" }, item.total, charts[i % charts.size]) to item.seconds } +
            if (others > 0) listOf(Triple("Остальные ($othersCount)", others, MaterialTheme.colorScheme.onSurfaceVariant) to 0L) else emptyList()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Donut(rows.map { (r, _) -> Slice(r.first, r.second, r.third) }, fmtBytes(sum), "всего")
            Column {
                rows.forEach { (r, seconds) ->
                    LegendRow(r.third, r.first, if (seconds > 0) fmtTime(seconds) else null, fmtBytes(r.second), r.second * 100.0 / sum)
                }
            }
        }
    }
}

@Composable
private fun NodesBlock(report: UsageReport) {
    val charts = BeamTheme.extra.charts
    var byTime by rememberSaveable { mutableStateOf(false) }
    val value: (UsageItem) -> Long = if (byTime) { i -> i.seconds } else { i -> i.total }
    val (top, _, _) = topWithOthers(report.nodes, 5, value)
    val maxValue = max(1L, top.maxOfOrNull(value) ?: 1L)
    StatBlock(
        BeamIcons.Servers,
        "Конфиги",
        action = { Segmented(listOf(false to "Трафик", true to "Время"), byTime, compact = true) { byTime = it } },
    ) {
        if (top.isEmpty()) {
            EmptyBlock("Нет данных за этот период")
            return@StatBlock
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            top.forEachIndexed { index, item ->
                val v = value(item)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (item.sub.isNotEmpty()) {
                            Text(item.sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        Box(Modifier.weight(0.01f))
                        Text(if (byTime) fmtTime(v) else fmtBytes(v), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(BeamTheme.extra.tint.copy(alpha = 0.08f))) {
                        Box(
                            Modifier
                                .fillMaxWidth(((v.toFloat() / maxValue).coerceIn(0f, 1f)).coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(charts[index % charts.size]),
                        )
                    }
                }
            }
        }
    }
}

private const val APPS_VISIBLE = 8

@Composable
private fun AppsBlock(report: UsageReport) {
    val context = LocalContext.current
    val charts = BeamTheme.extra.charts
    var expanded by rememberSaveable { mutableStateOf(false) }
    val items = report.apps
    val (top, _, _) = topWithOthers(items, if (expanded) items.size else APPS_VISIBLE) { it.total }
    val sum = items.sumOf { it.total }.coerceAtLeast(1)
    val maxValue = max(1L, top.maxOfOrNull { it.total } ?: 1L)
    StatBlock(BeamIcons.Apps, "Приложения") {
        if (top.isEmpty()) {
            EmptyBlock("Нет данных за этот период")
            return@StatBlock
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            top.forEachIndexed { index, item ->
                val color = charts[index % charts.size]
                val name = item.displayName(context)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(item.name, color, name, isProxy = item.id == PROXY_APP || item.name == PROXY_APP)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(fmtBytes(item.total), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${(item.total * 100.0 / sum).roundToInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(BeamTheme.extra.tint.copy(alpha = 0.08f))) {
                            Box(Modifier.fillMaxWidth((item.total.toFloat() / maxValue).coerceAtLeast(0.02f)).fillMaxHeight().clip(CircleShape).background(color))
                        }
                    }
                }
            }
            if (items.size > APPS_VISIBLE) {
                Text(
                    if (expanded) "Свернуть" else "Показать все (${items.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(scaled(10.dp))).clickable { expanded = !expanded }.padding(vertical = 8.dp),
                )
            }
        }
    }
}

// ---- Page ----

@Composable
fun StatsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by rememberSaveable { mutableStateOf("today") }
    var report by remember { mutableStateOf<UsageReport?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val charts = BeamTheme.extra.charts

    LaunchedEffect(period) {
        while (true) {
            // Keep the previous numbers on a failed read: statistics are not worth an error.
            withContext(Dispatchers.IO) {
                runCatching {
                    BridgeRuntime.ensureInit(context)
                    parseUsage(Mobile.analytics(period))
                }.getOrNull()
            }?.let { report = it }
            delay(REFRESH_MS)
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(BeamIcons.BarChart, "Статистика", onBack = onBack) {
            IconButton(onClick = { confirmReset = true }, modifier = Modifier.size(32.dp)) {
                Icon(BeamIcons.Trash, "Сбросить статистику", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp).bottomBarSpace(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Segmented(Periods, period) { period = it }
            val r = report
            when {
                r == null -> Unit
                r.isEmpty -> StatBlock(BeamIcons.BarChart, "Пока пусто") {
                    Text(
                        "Подключитесь к VPN: трафик, время и самые используемые серверы появятся здесь. Данные хранятся только на этом устройстве.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi(BeamIcons.ArrowDown, "Скачано", fmtBytes(r.down), if (period == "all") sinceText(r.since) else null, charts[0], Modifier.weight(1f))
                        Kpi(BeamIcons.ArrowUp, "Отдано", fmtBytes(r.up), null, charts[1], Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi(
                            BeamIcons.Clock, "Время в VPN", fmtTime(r.seconds),
                            if (period != "today" && r.activeDays > 0) "${r.activeDays} ${plural(r.activeDays.toLong(), "день", "дня", "дней")} с подключением" else null,
                            charts[2], Modifier.weight(1f),
                        )
                        Kpi(
                            BeamIcons.Signal, "Подключений", r.sessions.toString(),
                            if (r.sessions > 0 && r.seconds > 0) "в среднем ${fmtTime(r.seconds / r.sessions)}" else null,
                            charts[3], Modifier.weight(1f),
                        )
                    }
                    TrafficBlock(r)
                    RoutesBlock(r)
                    ProfilesBlock(r)
                    NodesBlock(r)
                    AppsBlock(r)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Сбросить статистику?") },
            text = { Text("Вся история трафика и времени будет удалена. Это нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                Mobile.resetAnalytics()
                                parseUsage(Mobile.analytics(period))
                            }.getOrNull()
                        }?.let { report = it }
                    }
                }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }
}
