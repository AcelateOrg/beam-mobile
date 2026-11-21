package com.beamvpn.app.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobile.Mobile
import org.json.JSONArray
import org.json.JSONObject

/** Easy split tunneling rules, desktop's SplitTunnelSettings. Apps are package names. */
data class SplitRules(
    val enabled: Boolean = false,
    /** "exclude": the listed go direct, the rest through VPN. "include": the listed go through VPN, the rest direct. */
    val mode: String = "exclude",
    val apps: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val ips: List<String> = emptyList(),
) {
    val count: Int get() = apps.size + domains.size + ips.size
    val viaVpn: Boolean get() = mode == "include"
}

data class RoutingState(val mode: String = "easy", val split: SplitRules = SplitRules())

/** beam-bridge's routing settings. The phone offers Easy rules only; ProTun stays a desktop feature. */
object RoutingApi {
    private suspend fun <T> bridge(context: Context, block: () -> T): T = withContext(Dispatchers.IO) {
        BridgeRuntime.ensureInit(context)
        block()
    }

    suspend fun state(context: Context): RoutingState = bridge(context) { read() }

    suspend fun saveSplit(context: Context, rules: SplitRules) = bridge(context) {
        Mobile.setSplitTunnel(
            JSONObject()
                .put("enabled", rules.enabled)
                .put("mode", rules.mode)
                .put("apps", JSONArray(rules.apps))
                .put("domains", JSONArray(rules.domains))
                .put("ips", JSONArray(rules.ips))
                .toString(),
        )
    }

    /**
     * Downloads geosite.dat / geoip.dat when [rules] use geosite: / geoip: entries and the file is not there yet.
     * The core is rebuilt without such entries until then, so call this before applying the rules.
     */
    suspend fun prepareGeo(context: Context, rules: SplitRules) = bridge(context) {
        val sites = rules.domains.any { it.trim().lowercase().startsWith("geosite:") }
        val ips = rules.ips.any { it.trim().lowercase().startsWith("geoip:") }
        if (sites || ips) runCatching { Mobile.prepareGeo(sites, ips) }
    }

    /**
     * A ProTun mode stored by an older build (or synced from a desktop export) would leave the phone routing by a
     * schema it can no longer show or turn off, so the bridge is put back into Easy mode. Call after Mobile.init.
     */
    internal fun enforceEasyMode() {
        if (read().mode != "easy") Mobile.setRoutingMode("easy")
    }

    private fun read(): RoutingState {
        val o = JSONObject(Mobile.routing())
        val s = o.getJSONObject("split")
        return RoutingState(
            mode = if (o.optString("mode") == "pro") "pro" else "easy",
            split = SplitRules(
                enabled = s.optBoolean("enabled"),
                mode = if (s.optString("mode") == "include") "include" else "exclude",
                apps = s.optJSONArray("apps").strings(),
                domains = s.optJSONArray("domains").strings(),
                ips = s.optJSONArray("ips").strings(),
            ),
        )
    }
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
