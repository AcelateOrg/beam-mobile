package com.beamvpn.app.settings

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/** Stable per-install device hash sent as X-Hwid: SHA-256 of ANDROID_ID, shortened like the desktop value. */
@SuppressLint("HardwareIds")
fun Context.hwid(): String {
    val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    return MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)
}

/** What subscription requests send: the user's own value when they set one, otherwise the device hash. */
fun Context.effectiveHwid(settings: AppSettings): String =
    settings.hwidValue.trim().takeIf { settings.hwidManual && it.isNotEmpty() } ?: hwid()

/** A fresh random identifier in the device hash's format, for "Сгенерировать". */
fun randomHwid(): String = UUID.randomUUID().toString().replace("-", "")
