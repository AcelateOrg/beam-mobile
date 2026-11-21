package com.beamvpn.app.ui.profiles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.beamvpn.app.ui.home.BeamViewModel
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.settings.Segmented
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.scaled
import com.beamvpn.app.vpn.Profile
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun Context.copyText(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ shows its own clipboard confirmation.
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
}

private fun safeFileName(name: String) = name.trim().replace(Regex("""[^\p{L}\p{N}_ -]+"""), "_").trim('_', ' ').ifEmpty { "profile" }

/** A QR code of [text], black on white so any camera reads it. */
private fun qrBitmap(text: String, size: Int = 512): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}.getOrNull()

@Composable
private fun SheetAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(scaled(14.dp)),
        modifier = modifier.height(48.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Text(label, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
    }
}

/** One way to share, as a row of a Material action sheet: icon, what it does, a short hint. */
@Composable
private fun ExportRow(icon: ImageVector, title: String, hint: String, enabled: Boolean = true, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(scaled(16.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(scaled(12.dp))).background(BeamTheme.extra.highlight.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(18.dp), tint = BeamTheme.extra.highlight.copy(alpha = if (enabled) 1f else 0.4f)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
            Text(hint, fontSize = 12.sp, lineHeight = 16.sp, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
    )
}

/**
 * "Экспорт профиля" as an action sheet: one row per way to share, grouped by what is shared (the subscription
 * link, the .beam file, the server links). The QR code and the link list open on their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportProfileSheet(profile: Profile, viewModel: BeamViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var links by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var showLinks by remember { mutableStateOf(false) }
    val fileName = "${safeFileName(profile.name)}.beam"
    LaunchedEffect(profile.id) { links = runCatching { viewModel.exportLinks(profile.id) }.getOrDefault(emptyList()) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val bytes = viewModel.exportBeam(profile.id)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            }.isSuccess
            Toast.makeText(context, if (ok) "Файл сохранён" else "Не удалось сохранить файл", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareBeam() = scope.launch {
        runCatching {
            val bytes = viewModel.exportBeam(profile.id)
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "exports").apply { mkdirs() }.resolve(fileName).apply { writeBytes(bytes) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Отправить профиль").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(context, it.message ?: "Не удалось экспортировать", Toast.LENGTH_SHORT).show() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                Text("Поделиться профилем", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(profile.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            profile.url?.let { url ->
                SectionTitle("Ссылка подписки")
                ExportRow(BeamIcons.QrCode, "Показать QR-код", "Отсканировать в BEAM на другом телефоне") { showQr = true }
                ExportRow(BeamIcons.Copy, "Скопировать ссылку", url) { context.copyText("Подписка", url) }
            }

            SectionTitle("Файл .beam")
            ExportRow(BeamIcons.Share, "Отправить файл", "Весь профиль: серверы, группы, оформление") { shareBeam() }
            ExportRow(BeamIcons.FileUp, "Сохранить на устройство", fileName) { save.launch(fileName) }

            val list = links
            if (list == null || list.isNotEmpty()) {
                SectionTitle("Ссылки серверов")
                ExportRow(
                    BeamIcons.Link,
                    "Ссылки серверов",
                    if (list == null) "Загружаю…" else "${list.size} шт. Скопировать по одной или все сразу",
                    enabled = list != null,
                ) { showLinks = true }
            }
        }
    }

    if (showQr) profile.url?.let { url -> QrDialog(url) { showQr = false } }
    if (showLinks) links?.let { LinksSheet(it) { showLinks = false } }
}

/** The subscription link as a large QR code, alone on screen so it is easy to scan. */
@Composable
private fun QrDialog(
    url: String,
    title: String = "QR-код подписки",
    hint: String = "В BEAM на другом телефоне: «Добавить профиль» → «QR-код».",
    onDismiss: () -> Unit,
) {
    val bitmap = remember(url) { qrBitmap(url, 640) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(scaled(16.dp))).background(Color.White).padding(12.dp),
                    )
                }
                Text(
                    hint,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

/** The original links of the profile's servers: tap one to copy it, or copy and share all of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinksSheet(links: List<Pair<String, String>>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val all = links.joinToString("\n") { it.second }
    var qrFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ссылки серверов", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            Text("Нажмите на сервер, чтобы скопировать ссылку, или откройте его QR-код.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                itemsIndexed(links) { _, (name, link) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(scaled(12.dp))).clickable { context.copyText(name, link) }.padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(link, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        // One server as a QR code: the other phone gets just this key.
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = "QR-код") { qrFor = name to link },
                            contentAlignment = Alignment.Center,
                        ) { Icon(BeamIcons.QrCode, "QR-код", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Icon(BeamIcons.Copy, "Скопировать", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction(BeamIcons.Copy, "Скопировать все", Modifier.weight(1f)) { context.copyText("Ссылки", all) }
                SheetAction(BeamIcons.Share, "Поделиться", Modifier.weight(1f)) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, all)
                    context.startActivity(Intent.createChooser(send, "Отправить ссылки").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }

    qrFor?.let { (name, link) ->
        QrDialog(
            url = link,
            title = name.ifBlank { "QR-код сервера" },
            hint = "Ключ одного сервера. В BEAM на другом телефоне: «Добавить профиль» → «QR-код».",
        ) { qrFor = null }
    }
}

private const val MAX_OVERRIDE_HOURS = 720

/** "Параметры профиля": automatic subscription updates and an own schedule, as in desktop's ProfileSettingsModal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileParamsSheet(profile: Profile, viewModel: BeamViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extra = BeamTheme.extra
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var hours by remember(profile.id) { mutableStateOf(if (profile.updateIntervalOverrideHours > 0) profile.updateIntervalOverrideHours.toString() else "") }

    fun commit() {
        val value = (hours.toIntOrNull() ?: 0).coerceIn(0, MAX_OVERRIDE_HOURS)
        hours = if (value > 0) value.toString() else ""
        if (value != profile.updateIntervalOverrideHours) viewModel.setUpdateInterval(profile.id, value)
    }

    ModalBottomSheet(
        onDismissRequest = { commit(); onDismiss() },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Параметры профиля", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(profile.name, fontSize = 13.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (!profile.hasSubscription) {
                Text("У этого профиля нет подписки, поэтому автообновление недоступно.", fontSize = 13.sp, color = muted)
            } else {
                Surface(shape = RoundedCornerShape(scaled(18.dp)), color = extra.shell, border = BorderStroke(1.dp, extra.border), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Автообновление подписки", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    when {
                                        profile.autoRefreshDisabled -> "Выключено, подписка сама не обновляется"
                                        profile.updateIntervalOverrideHours > 0 -> "Раз в ${profile.updateIntervalOverrideHours} ч по своему расписанию"
                                        profile.updateIntervalHours > 0 -> "Раз в ${profile.updateIntervalHours} ч по расписанию провайдера"
                                        else -> "По расписанию провайдера"
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = muted,
                                )
                            }
                            Switch(
                                checked = !profile.autoRefreshDisabled,
                                onCheckedChange = { viewModel.setAutoRefresh(profile.id, it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = extra.highlight, checkedThumbColor = extra.onHighlight),
                            )
                        }
                        if (!profile.autoRefreshDisabled) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(extra.border.let { it.copy(alpha = it.alpha * 0.5f) }))
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Своё расписание", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (profile.updateIntervalHours > 0) "Часы. Пусто — как у провайдера (${profile.updateIntervalHours} ч)" else "Часы. Пусто — как у провайдера",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = muted,
                                    )
                                }
                                OutlinedTextField(
                                    value = hours,
                                    onValueChange = { v -> hours = v.filter { it.isDigit() }.take(3) },
                                    placeholder = { Text("—") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { commit() }),
                                    shape = RoundedCornerShape(scaled(12.dp)),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = extra.highlight.copy(alpha = 0.7f), unfocusedBorderColor = extra.border),
                                    modifier = Modifier.size(width = 88.dp, height = 56.dp),
                                )
                            }
                        }
                    }
                }
                Text("Общий выключатель автообновления — в «Настройки → Приложение».", fontSize = 11.sp, color = muted)
            }
            Button(
                onClick = { commit(); onDismiss() },
                shape = RoundedCornerShape(scaled(14.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Готово", fontWeight = FontWeight.SemiBold) }
        }
    }
}
