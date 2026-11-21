package com.beamvpn.app.vpn

import mobile.Mobile
import org.json.JSONArray
import org.json.JSONObject

// Parsers for the JSON beam-bridge/mobile returns. Shapes match beam-bridge's
// core/types (ProfileSummary, ProfileNodesResponse) and profile.PingResult.

/** Category ids desktop treats as "no group" (pages/Nodes.tsx UNGROUPED_IDS). */
private val UngroupedIds = setOf("default", "__default__")

internal fun parseProfiles(json: String): List<Profile> {
    val array = JSONArray(json)
    return List(array.length()) { i ->
        val o = array.getJSONObject(i)
        val branding = o.optJSONObject("branding")
        Profile(
            id = o.getString("id"),
            name = o.optString("name"),
            provider = o.optString("provider").takeIf { it.isNotBlank() && !o.optBoolean("hideProvider") },
            isActive = o.optBoolean("isActive"),
            dataUsed = o.optLong("dataUsed"),
            dataLimit = o.optLong("dataLimit"),
            daysLeft = o.optInt("daysLeft", -1),
            isExpired = o.optBoolean("isExpired"),
            hasSubscription = o.optBoolean("hasSubscription"),
            announce = branding?.optString("bannerText")?.takeIf { it.isNotBlank() } ?: o.optString("announce"),
            url = o.optString("url").takeIf { it.isNotBlank() },
            updateIntervalHours = o.optInt("updateIntervalHours"),
            autoRefreshDisabled = o.optBoolean("autoRefreshDisabled"),
            updateIntervalOverrideHours = o.optInt("updateIntervalOverrideHours"),
            branding = Branding(
                iconUrl = o.link(branding, "iconUrl")?.let(::resolveMedia),
                bannerUrl = branding?.optString("bannerUrl")?.takeIf { it.isNotBlank() },
                bannerColor = branding?.optString("bannerColor")?.takeIf { it.isNotBlank() },
                bannerImage = branding?.optString("bannerImage")?.takeIf { it.isNotBlank() }?.let(::resolveMedia),
                supportUrl = o.link(branding, "supportUrl"),
                communityUrl = o.link(branding, "communityUrl"),
            ),
        )
    }
}

/**
 * Provider art the bridge already downloaded comes as a desktop asset-server path ("/beam-media/..."); a phone
 * has no such server, so it is turned into the cached file. Anything else (not cached yet) stays a remote URL.
 */
private fun resolveMedia(url: String): String {
    if (!url.startsWith("/beam-media/")) return url
    return Mobile.mediaPath(url).takeIf { it.isNotEmpty() }?.let { "file://$it" } ?: ""
}

/** The branding block wins over the legacy flat field of the same name. */
private fun JSONObject.link(branding: JSONObject?, key: String): String? =
    (branding?.optString(key)?.takeIf { it.isNotBlank() } ?: optString(key)).takeIf { it.isNotBlank() }

internal fun parseNodes(json: String): ProfileNodes {
    val o = JSONObject(json)
    val categories = o.optJSONArray("categories") ?: JSONArray()
    val groups = List(categories.length()) { i ->
        val c = categories.getJSONObject(i)
        val category = c.getJSONObject("category")
        val nodes = c.optJSONArray("nodes") ?: JSONArray()
        NodeGroup(
            id = category.getString("id"),
            name = category.optString("name"),
            emoji = category.optString("emoji").takeIf { it.isNotBlank() },
            ungrouped = category.getString("id") in UngroupedIds,
            collapsed = category.optBoolean("collapsed"),
            nodes = List(nodes.length()) { j -> nodes.getJSONObject(j).toNode() },
        )
    }
    return ProfileNodes(groups, o.optString("selectedId").takeIf { it.isNotBlank() })
}

private fun JSONObject.toNode() = Node(
    id = getString("id"),
    name = optString("name"),
    type = optString("type"),
    transport = optString("transport"),
    security = optString("security"),
    description = optString("description").takeIf { it.isNotBlank() },
    badge = optString("badge").takeIf { it.isNotBlank() },
    hideProtocol = optBoolean("hideProtocol"),
    pinned = optBoolean("isPinned"),
)

/** status "ok" carries the latency; timeout, error, invalid and cancelled all read as "Таймаут" on desktop. */
internal fun parsePings(json: String): Map<String, PingResult> {
    val array = JSONArray(json)
    return (0 until array.length()).associate { i ->
        val o = array.getJSONObject(i)
        o.getString("nodeId") to pingResult(o.optString("status"), o.optLong("latency"))
    }
}

internal fun pingResult(status: String, latency: Long): PingResult =
    if (status == "ok") PingResult.Ok(latency.toInt()) else PingResult.Timeout
