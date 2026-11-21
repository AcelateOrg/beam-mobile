package com.beamvpn.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import mobile.ProcessResolver
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Tells beam-bridge which app owns a connection that entered the tunnel, so the statistics can show traffic per
 * app. A VPN app may ask Android for the owner UID of connections through its own interface (API 29+); older
 * phones report nothing and everything lands in "Системные".
 */
class AppOwners(context: Context) : ProcessResolver {
    private val app = context.applicationContext
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val packages = ConcurrentHashMap<Int, String>()

    override fun owner(network: String, srcIP: String, srcPort: Long, dstIP: String, dstPort: Long): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ""
        return runCatching {
            val protocol = if (network.startsWith("udp")) 17 else 6
            val uid = connectivity.getConnectionOwnerUid(
                protocol,
                InetSocketAddress(srcIP, srcPort.toInt()),
                InetSocketAddress(dstIP, dstPort.toInt()),
            )
            if (uid < 0) "" else packages.getOrPut(uid) { packageOf(uid) }
        }.getOrDefault("")
    }

    private fun packageOf(uid: Int): String =
        app.packageManager.getPackagesForUid(uid)?.firstOrNull().orEmpty()
}
