package com.beamvpn.app.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.scaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's label, or the package name itself when the app is not installed. */
internal fun Context.appLabel(pkg: String): String = runCatching {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

/** Decoded launcher icons and labels, so long app lists do not ask the package manager again on each scroll. */
private object AppIcons {
    private const val SIZE_PX = 96
    private val icons = LruCache<String, ImageBitmap>(200)

    fun cached(pkg: String): ImageBitmap? = icons.get(pkg)

    suspend fun load(context: Context, pkg: String): ImageBitmap? = icons.get(pkg) ?: withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(SIZE_PX, SIZE_PX).asImageBitmap() }
            .getOrNull()
            ?.also { icons.put(pkg, it) }
    }
}

/** The launcher icon of an installed app on a tile; [fallback] when the app is unknown or gone. */
@Composable
internal fun AppIcon(pkg: String, size: Dp = 40.dp, fallback: ImageVector = BeamIcons.Apps) {
    val context = LocalContext.current
    val icon by produceState(if (pkg.isEmpty()) null else AppIcons.cached(pkg), pkg) {
        if (value == null && pkg.isNotEmpty()) value = AppIcons.load(context, pkg)
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(scaled(10.dp))).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) Image(bitmap, null, Modifier.size(size * 0.75f))
        else Icon(fallback, null, Modifier.size(size * 0.45f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
