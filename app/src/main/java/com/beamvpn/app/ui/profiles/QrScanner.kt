package com.beamvpn.app.ui.profiles

import com.beamvpn.app.ui.theme.scaled
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beamvpn.app.ui.theme.BeamTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Live camera preview that reports every QR code it reads to [onCode]. The caller decides what to do
 * with a code and when to ignore repeats; frames keep flowing while the composable is in the tree.
 * Decoding is ZXing on the luminance plane of CameraX frames, so it works without Google Play services.
 */
@Composable
fun QrScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest = rememberUpdatedState(onCode)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }
        providerFuture.addListener({
            val cameraProvider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener
            provider = cameraProvider
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { a ->
                    a.setAnalyzer(executor) { image ->
                        decode(reader, image)?.let { text -> previewView.post { latest.value(text) } }
                    }
                }
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier.clip(RoundedCornerShape(scaled(20.dp))).border(1.dp, BeamTheme.extra.border, RoundedCornerShape(scaled(20.dp)))) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        // Viewfinder frame.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(180.dp)
                .padding(2.dp)
                .border(2.dp, BeamTheme.extra.highlight.copy(alpha = 0.85f), RoundedCornerShape(scaled(20.dp))),
        )
    }
}

private fun decode(reader: MultiFormatReader, image: ImageProxy): String? = image.use {
    val plane = it.planes[0]
    val buffer = plane.buffer
    val width = it.width
    val height = it.height
    val rowStride = plane.rowStride
    // Copy the luminance plane without the row padding the camera adds.
    val data = ByteArray(width * height)
    buffer.rewind()
    for (row in 0 until height) {
        buffer.position(row * rowStride)
        buffer.get(data, row * width, width)
    }
    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

fun Context.hasCameraFeature(): Boolean = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
