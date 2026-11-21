package com.beamvpn.app.ui.profiles

import com.beamvpn.app.ui.theme.scaled
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AddMethod(val title: String, val hint: String, val icon: ImageVector) {
    Link("Ссылка", "Подписка или ключ", BeamIcons.Link),
    Qr("QR-код", "Сканер камерой", BeamIcons.QrCode),
    File("Файл", "Импорт .beam", BeamIcons.FileUp),
}

/** A .beam file picked by the user, read into memory. */
private class PickedFile(val name: String, val bytes: ByteArray)

/**
 * Add a profile, after the desktop's AddModal as a bottom sheet: three method cards (link, QR, .beam
 * file) and the form of the chosen one. A scanned code is imported right away. beam-bridge does the parsing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProfileSheet(
    onDismiss: () -> Unit,
    initialText: String = "",
    importText: suspend (String) -> String?,
    importFile: suspend (ByteArray) -> String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extra = BeamTheme.extra
    var method by rememberSaveable { mutableStateOf(AddMethod.Link) }
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraDenied by remember { mutableStateOf(false) }

    fun run(block: suspend () -> String?) {
        busy = true
        error = null
        scope.launch {
            val failure = block()
            busy = false
            if (failure == null) {
                sheet.hide()
                onDismiss()
            } else {
                error = failure
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                    PickedFile(context.displayName(uri) ?: "profile.beam", bytes)
                }.getOrNull()
            }
            file = picked
            error = if (picked == null) "Не удалось прочитать файл" else null
        }
    }

    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        cameraDenied = !granted
    }

    fun select(next: AddMethod) {
        method = next
        error = null
        if (next == AddMethod.Qr && !cameraGranted) askCamera.launch(Manifest.permission.CAMERA)
    }

    // A code stays ignored for a few seconds after it failed, so the camera does not re-submit it every frame.
    var lastRejected by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val onCode: (String) -> Unit = scan@{ raw ->
        val code = raw.trim()
        val rejected = lastRejected
        if (busy || code.isEmpty()) return@scan
        if (rejected != null && rejected.first == code && SystemClock.elapsedRealtime() - rejected.second < 4_000) return@scan
        run {
            val failure = importText(code)
            if (failure != null) lastRejected = code to SystemClock.elapsedRealtime()
            failure
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(BeamIcons.Plus, null, Modifier.size(16.dp))
                Text("Добавить профиль", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AddMethod.entries.forEach { item ->
                    MethodCard(item, selected = item == method, enabled = !busy, modifier = Modifier.weight(1f)) { select(item) }
                }
            }

            when (method) {
                AddMethod.Link -> LinkForm(
                    text = text,
                    onText = { text = it; error = null },
                    onPaste = { context.clipboardText()?.let { text = it.trim(); error = null } },
                    busy = busy,
                    isError = error != null,
                )
                AddMethod.Qr -> QrPane(
                    granted = cameraGranted,
                    denied = cameraDenied,
                    busy = busy,
                    onAsk = { askCamera.launch(Manifest.permission.CAMERA) },
                    onCode = onCode,
                )
                AddMethod.File -> FilePane(file = file, busy = busy, onPick = { pickFile.launch(arrayOf("*/*")) })
            }

            error?.let {
                Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.error)
            }

            // The scanner imports by itself, the other two methods confirm with the button.
            if (method != AddMethod.Qr) {
                val canSubmit = !busy && if (method == AddMethod.Link) text.isNotBlank() else file != null
                Button(
                    onClick = {
                        if (method == AddMethod.Link) run { importText(text) }
                        else file?.let { f -> run { importFile(f.bytes) } }
                    },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(scaled(14.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = extra.onHighlight)
                    } else {
                        Text("Импортировать", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Desktop method card: 2px border, primary tint when chosen, icon over title over hint. */
@Composable
private fun MethodCard(method: AddMethod, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val extra = BeamTheme.extra
    val shape = RoundedCornerShape(scaled(14.dp))
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (selected) extra.highlight.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(2.dp, if (selected) extra.highlight else extra.border),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                method.icon,
                null,
                Modifier.size(20.dp),
                tint = if (selected) extra.highlight else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                method.title,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1,
            )
            Text(
                method.hint,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun LinkForm(text: String, onText: (String) -> Unit, onPaste: () -> Unit, busy: Boolean, isError: Boolean) {
    val extra = BeamTheme.extra
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            placeholder = { Text("https://… или vless://…", fontSize = 14.sp) },
            textStyle = MaterialTheme.typography.bodyMedium,
            minLines = 3,
            maxLines = 6,
            isError = isError,
            enabled = !busy,
            shape = RoundedCornerShape(scaled(16.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = extra.highlight.copy(alpha = 0.7f),
                unfocusedBorderColor = extra.border,
                cursorColor = extra.highlight,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "vless://, vmess://, trojan://, ss://, hy2:// или ссылка на подписку",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(scaled(10.dp)))
                    .background(extra.accentFill)
                    .clickable(enabled = !busy, onClick = onPaste)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(BeamIcons.Copy, null, Modifier.size(13.dp))
                Text("Вставить", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (text.trim().startsWith("http://", ignoreCase = true)) {
            Text("Соединение без шифрования", fontSize = 10.sp, color = extra.warning)
        }
    }
}

@Composable
private fun QrPane(granted: Boolean, denied: Boolean, busy: Boolean, onAsk: () -> Unit, onCode: (String) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(scaled(20.dp))
    if (!granted) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(shape)
                .border(1.dp, BeamTheme.extra.border, shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(BeamIcons.QrCode, null, Modifier.size(28.dp), tint = muted)
            Text(
                if (denied) "Доступ к камере запрещён. Разрешите его в настройках приложения или нажмите ниже." else "Для сканирования нужен доступ к камере.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
            )
            Button(onClick = onAsk, shape = RoundedCornerShape(scaled(12.dp))) { Text("Разрешить камеру", fontSize = 13.sp) }
        }
        return
    }
    Box(Modifier.fillMaxWidth().height(280.dp)) {
        QrScanner(onCode = onCode, modifier = Modifier.fillMaxWidth().height(280.dp))
        if (busy) {
            Box(
                Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = BeamTheme.extra.highlight) }
        } else {
            Text(
                "Наведите на QR-код подписки или ключа",
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun Modifier.matchParentSize(): Modifier = this.then(Modifier.fillMaxWidth().height(280.dp))

@Composable
private fun FilePane(file: PickedFile?, busy: Boolean, onPick: () -> Unit) {
    val extra = BeamTheme.extra
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(scaled(16.dp))
    val picked = file != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(2.dp, if (picked) extra.pingGood.copy(alpha = 0.5f) else extra.border, shape)
            .background(if (picked) extra.pingGood.copy(alpha = 0.05f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = !busy, onClick = onPick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (picked) BeamIcons.Check else BeamIcons.FileUp, null, Modifier.size(24.dp), tint = if (picked) extra.pingGood else muted)
        Column(Modifier.weight(1f)) {
            Text(file?.name ?: "Выберите .beam файл", fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1)
            Text(
                if (picked) "Нажмите, чтобы выбрать другой" else "Файл профиля от провайдера или с другого устройства",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = muted,
            )
        }
    }
}

private fun Context.displayName(uri: android.net.Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

private fun Context.clipboardText(): String? =
    getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()
        ?.takeIf { it.isNotBlank() }
