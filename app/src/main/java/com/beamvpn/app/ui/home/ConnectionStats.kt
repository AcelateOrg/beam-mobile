package com.beamvpn.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.vpn.TrafficStats

/** Ping, time, download and upload under the button; items stagger in like on desktop. */
@Composable
fun ConnectionStats(visible: Boolean, stats: TrafficStats, healthy: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(200, delayMillis = 300)),
    ) {
        val extra = BeamTheme.extra
        val pingColor = when {
            stats.pingMs > 500 -> MaterialTheme.colorScheme.error
            stats.pingMs > 200 -> extra.warning
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!healthy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(4.dp).background(extra.warning.copy(alpha = 0.8f), CircleShape))
                    Text("Нестабильное соединение", fontSize = 10.sp, color = extra.warning.copy(alpha = 0.8f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stat(0, BeamIcons.Signal, "Пинг", formatPing(stats.pingMs), pingColor)
                Divider(1)
                Stat(1, BeamIcons.Clock, "Время", formatDuration(stats.durationSec), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                Divider(2)
                Stat(2, BeamIcons.ArrowDown, "Скачано", formatBytes(stats.downloadBytes), extra.download)
                Divider(3)
                Stat(3, BeamIcons.ArrowUp, "Отдано", formatBytes(stats.uploadBytes), extra.upload)
            }
        }
    }
}

private fun AnimatedVisibilityScope.staggered(index: Int, enterStep: Int = 95, exitStep: Int = 70) =
    Modifier.animateEnterExit(
        enter = fadeIn(tween(320, delayMillis = index * enterStep)) +
            slideInVertically(tween(320, delayMillis = index * enterStep)) { it / 2 },
        exit = fadeOut(tween(220, delayMillis = index * exitStep)) +
            slideOutVertically(tween(220, delayMillis = index * exitStep)) { it / 3 },
    )

@Composable
private fun AnimatedVisibilityScope.Stat(index: Int, icon: ImageVector, label: String, value: String, valueColor: Color) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Column(
        Modifier.width(76.dp).then(staggered(index)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(10.dp), tint = labelColor)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.merge(LabelStyle), color = labelColor)
        }
        Text(value, style = MaterialTheme.typography.labelSmall.merge(ValueStyle), color = valueColor, maxLines = 1)
    }
}

@Composable
private fun AnimatedVisibilityScope.Divider(index: Int) {
    Box(
        Modifier
            .then(staggered(index, enterStep = 95, exitStep = 65))
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(BeamTheme.extra.tint.copy(alpha = 0.06f)),
    )
}

private val LabelStyle = TextStyle(fontSize = 9.sp, letterSpacing = 0.1.em, lineHeight = 9.sp)
private val ValueStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", lineHeight = 14.sp)
