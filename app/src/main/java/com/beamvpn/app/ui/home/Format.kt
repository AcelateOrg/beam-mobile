package com.beamvpn.app.ui.home

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

// Ports of beam-desktop's lib/format.ts so numbers read the same on both apps.

internal fun formatPing(ms: Long): String = if (ms <= 0) "-- ms" else "$ms ms"

internal fun formatDuration(sec: Long): String {
    if (sec < 0) return "--:--"
    val h = sec / 3600
    val m = sec % 3600 / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatBytes(bytes: Long): String {
    if (bytes == 0L) return "0 B"
    if (bytes < 0) return "∞"
    val sizes = arrayOf("B", "KB", "MB", "GB", "TB")
    val i = floor(ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(sizes.lastIndex)
    // parseFloat(x.toFixed(2)): two decimals, trailing zeros dropped.
    val value = BigDecimal(bytes / 1024.0.pow(i)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    return "$value ${sizes[i]}"
}

internal fun formatDaysLeft(days: Int, isExpired: Boolean): String = when {
    isExpired -> "Истекло"
    days < 0 -> "∞"
    days == 0 -> "Истекает сегодня"
    days == 1 -> "1 день"
    days < 5 -> "$days дня"
    else -> "$days дней"
}
