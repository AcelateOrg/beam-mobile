package com.beamvpn.app.vpn

import org.json.JSONArray
import org.json.JSONObject

/** One row of a statistics block: a profile, a server or an app. [path] holds the package name of an app. */
data class UsageItem(val id: String, val name: String, val sub: String, val path: String, val up: Long, val down: Long, val seconds: Long) {
    val total: Long get() = up + down
}

data class UsageBucket(val key: String, val up: Long, val down: Long)

/** beam-bridge's analytics.Report: totals, a series (24 hours for today, days otherwise) and top lists. */
data class UsageReport(
    val period: String = "today",
    val hourly: Boolean = true,
    val up: Long = 0,
    val down: Long = 0,
    val seconds: Long = 0,
    val sessions: Long = 0,
    val activeDays: Int = 0,
    val series: List<UsageBucket> = emptyList(),
    val profiles: List<UsageItem> = emptyList(),
    val nodes: List<UsageItem> = emptyList(),
    val apps: List<UsageItem> = emptyList(),
    val routeProxy: Long = 0,
    val routeDirect: Long = 0,
    val routeBlocked: Long = 0,
    /** First day with any data, "YYYY-MM-DD", or "". */
    val since: String = "",
) {
    val isEmpty: Boolean get() = since.isEmpty() && up + down == 0L && seconds == 0L && sessions == 0L
}

const val PROXY_APP = "@proxy"

internal fun parseUsage(json: String): UsageReport {
    val o = JSONObject(json)
    val totals = o.optJSONObject("totals")
    val routes = o.optJSONObject("routes")
    return UsageReport(
        period = o.optString("period"),
        hourly = o.optBoolean("hourly"),
        up = totals?.optLong("up") ?: 0,
        down = totals?.optLong("down") ?: 0,
        seconds = totals?.optLong("seconds") ?: 0,
        sessions = totals?.optLong("sessions") ?: 0,
        activeDays = totals?.optInt("activeDays") ?: 0,
        series = o.optJSONArray("series").mapObjects { UsageBucket(it.getString("key"), it.optLong("up"), it.optLong("down")) },
        profiles = o.optJSONArray("profiles").mapObjects(::item),
        nodes = o.optJSONArray("nodes").mapObjects(::item),
        apps = o.optJSONArray("apps").mapObjects(::item),
        routeProxy = routes?.optLong("proxy") ?: 0,
        routeDirect = routes?.optLong("direct") ?: 0,
        routeBlocked = routes?.optLong("blocked") ?: 0,
        since = o.optString("since"),
    )
}

private fun item(o: JSONObject) = UsageItem(
    id = o.optString("id"),
    name = o.optString("name"),
    sub = o.optString("sub"),
    path = o.optString("path"),
    up = o.optLong("up"),
    down = o.optLong("down"),
    seconds = o.optLong("seconds"),
)

private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else List(length()) { transform(getJSONObject(it)) }
