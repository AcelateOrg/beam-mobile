package com.beamvpn.app.vpn

import org.json.JSONArray
import org.json.JSONObject

/**
 * A server's editable configuration, core/types NodeConfig. [outbound] is what the core gets (the provider's data
 * with the user's edits), [original] the provider's alone; [overrides] maps dotted paths ("tls.server_name") to the
 * user's values, null meaning "remove the field".
 */
data class NodeConfig(
    val nodeId: String,
    val name: String,
    /** The provider's name, what an emptied name falls back to. */
    val baseName: String,
    val type: String,
    val outbound: JSONObject,
    val original: JSONObject,
    val overrides: Map<String, Any?>,
    val isPinned: Boolean,
    val hasSubscription: Boolean,
    /** The server's original link no longer describes it, so it is left out of exported links. */
    val rawLinkStale: Boolean,
)

/** Reads a value at a dotted path; null when any step is missing. */
fun JSONObject.atPath(path: String): Any? {
    var current: Any? = this
    for (key in path.split('.')) {
        current = (current as? JSONObject)?.opt(key) ?: return null
    }
    return current.takeUnless { it == JSONObject.NULL }
}

/** One representation per JSON value, so a typed-in "443" and the provider's 443 compare equal. */
fun jsonKey(value: Any?): String = JSONArray().put(jsonValue(value)).toString()

/** Kotlin values (lists, maps, numbers) as org.json stores them. */
fun jsonValue(value: Any?): Any = when (value) {
    null, JSONObject.NULL -> JSONObject.NULL
    is List<*> -> JSONArray(value.map { jsonValue(it) })
    is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> put(k.toString(), jsonValue(v)) } }
    is Number -> if (value.toDouble() == Math.floor(value.toDouble()) && !value.toDouble().isInfinite()) value.toLong() else value.toDouble()
    else -> value
}

internal fun parseNodeConfig(json: String): NodeConfig {
    val o = JSONObject(json)
    val overrides = o.optJSONObject("overrides")
    return NodeConfig(
        nodeId = o.optString("nodeId"),
        name = o.optString("name"),
        baseName = o.optString("baseName"),
        type = o.optString("type"),
        outbound = o.optJSONObject("outbound") ?: JSONObject(),
        original = o.optJSONObject("original") ?: JSONObject(),
        overrides = overrides?.keys()?.asSequence()?.associateWith { key -> overrides.opt(key).takeUnless { it == JSONObject.NULL } }.orEmpty(),
        isPinned = o.optBoolean("isPinned"),
        hasSubscription = o.optBoolean("hasSubscription"),
        rawLinkStale = o.optBoolean("rawLinkStale"),
    )
}
