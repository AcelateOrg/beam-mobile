package com.beamvpn.app.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobile.Mobile
import org.json.JSONArray
import org.json.JSONObject

/** Where a flow goes, desktop's ActiveConnection.route. */
enum class FlowRoute { Proxy, Direct, Block, Dns, Unknown }

/** One tunnel flow, desktop's types.ActiveConnection. [app] is the owning package, "" when unknown. */
data class Flow(
    val id: String,
    val network: String,
    val host: String,
    val destination: String,
    val domain: String,
    val protocol: String,
    val app: String,
    val route: FlowRoute,
    /** Why it went that way: app, rules, default, lan, dns. */
    val ruleKind: String,
    val rule: String,
    val outbound: String,
    val nodeName: String,
    val upload: Long,
    val download: Long,
    /** Epoch ms. */
    val startedAt: Long,
    val durationSec: Long,
    val closed: Boolean,
    val endedAt: Long,
) {
    /** What to call the far end: the sniffed domain, else the host the app asked for, else the address. */
    val target: String get() = domain.ifEmpty { host.ifEmpty { destination } }
}

/** The tunnel's live and recently closed flows, as desktop's connections view shows them. */
object ConnectionsApi {
    suspend fun list(context: Context): List<Flow> = withContext(Dispatchers.IO) {
        BridgeRuntime.ensureInit(context)
        val array = JSONArray(Mobile.connections())
        List(array.length()) { array.getJSONObject(it).toFlow() }
    }

    /** Tears the flows down; returns how many were still open. */
    suspend fun close(context: Context, ids: Collection<String>): Int = withContext(Dispatchers.IO) {
        BridgeRuntime.ensureInit(context)
        Mobile.closeConnections(JSONArray(ids).toString()).toInt()
    }
}

private fun JSONObject.toFlow() = Flow(
    id = optString("id"),
    network = optString("network").ifEmpty { "tcp" },
    host = optString("host"),
    destination = optString("destination"),
    domain = optString("domain"),
    protocol = optString("protocol"),
    app = optString("process"),
    route = when (optString("route")) {
        "proxy" -> FlowRoute.Proxy
        "direct" -> FlowRoute.Direct
        "block" -> FlowRoute.Block
        "dns" -> FlowRoute.Dns
        else -> FlowRoute.Unknown
    },
    ruleKind = optString("ruleKind"),
    rule = optString("rule"),
    outbound = optString("outbound"),
    nodeName = optString("nodeName"),
    upload = optLong("upload"),
    download = optLong("download"),
    startedAt = optLong("startedAt"),
    durationSec = optLong("duration"),
    closed = optBoolean("closed"),
    endedAt = optLong("endedAt"),
)
