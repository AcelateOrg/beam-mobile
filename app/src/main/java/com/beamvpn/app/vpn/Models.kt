package com.beamvpn.app.vpn

/** A profile: a subscription or a single imported key. Mirrors desktop's ProfileSummary. */
data class Profile(
    val id: String,
    val name: String,
    val provider: String? = null,
    val isActive: Boolean = false,
    val dataUsed: Long = 0,
    /** <= 0 means unlimited. */
    val dataLimit: Long = 0,
    /** < 0 means it never expires. */
    val daysLeft: Int = -1,
    val isExpired: Boolean = false,
    val hasSubscription: Boolean = true,
    /** Provider announcement (branding.bannerText or announce). */
    val announce: String? = null,
    val branding: Branding = Branding(),
    /** Subscription URL; null for a profile imported from keys or a file. */
    val url: String? = null,
    /** The provider's update interval, hours (0 = unknown). */
    val updateIntervalHours: Int = 0,
    val autoRefreshDisabled: Boolean = false,
    /** The user's own interval, hours; 0 = the provider's schedule. */
    val updateIntervalOverrideHours: Int = 0,
) {
    val unlimited: Boolean get() = dataLimit <= 0
    val usedFraction: Float get() = if (unlimited) 0f else (dataUsed.toFloat() / dataLimit).coerceIn(0f, 1f)
}

/** Provider branding of a profile (core/types Branding, with the legacy flat fields folded in). */
data class Branding(
    val iconUrl: String? = null,
    /** Tapping the announcement opens this link. */
    val bannerUrl: String? = null,
    /** Provider accent as "#RRGGBB", overrides the theme on the announcement. */
    val bannerColor: String? = null,
    /** Art behind the profile card, fitted to the card height and pinned right. */
    val bannerImage: String? = null,
    val supportUrl: String? = null,
    val communityUrl: String? = null,
)

data class Node(
    val id: String,
    /** Raw name from the subscription, usually starting with a flag emoji. */
    val name: String,
    val type: String = "",
    val transport: String = "",
    val security: String = "",
    val description: String? = null,
    val badge: String? = null,
    val hideProtocol: Boolean = false,
    /** The user edited it and froze it: subscription updates leave it as it is. */
    val pinned: Boolean = false,
) {
    val parsed: ParsedNodeName by lazy { parseNodeName(name) }
    val protocol: String get() = listOf(type, transport, security).filter { it.isNotEmpty() }.joinToString(" / ")
}

data class NodeGroup(
    val id: String,
    val name: String,
    val nodes: List<Node>,
    val emoji: String? = null,
    /** Servers without a group are listed without a header, like on desktop. */
    val ungrouped: Boolean = false,
    /** The provider asked for this group to start folded. */
    val collapsed: Boolean = false,
)

/** A profile's servers by group and the one it connects through. */
data class ProfileNodes(val groups: List<NodeGroup> = emptyList(), val selectedId: String? = null) {
    val count: Int get() = groups.sumOf { it.nodes.size }
}

sealed interface PingResult {
    data class Ok(val latencyMs: Int) : PingResult
    data object Timeout : PingResult
}

data class ParsedNodeName(val countryCode: String?, val displayName: String)

private val FlagPattern = Regex("""[\x{1F1E6}-\x{1F1FF}]{2}""")
private val LoneIndicator = Regex("""[\x{1F1E6}-\x{1F1FF}]""")
private val EmptyBrackets = Regex("""(?:\(\s*\)|\[\s*\]|\{\s*\}|【\s*】)""")
private val EdgeSeparators = Regex("""^[\s|/\\·•‧,~—–-]+|[\s|/\\·•‧,~—–-]+$""")
private val TextPrefix = Regex("""^([A-Z]{2})\s+""")

/** Port of beam-desktop's lib/nodeName.ts: pulls the country out of a flag emoji or "FI " prefix. */
fun parseNodeName(name: String): ParsedNodeName {
    if (name.isBlank()) return ParsedNodeName(null, "")
    FlagPattern.find(name)?.let { match ->
        val code = match.value.codePoints().toArray()
            .joinToString("") { (it - 0x1F1E6 + 'A'.code).toChar().toString() }
        val display = name
            .replace(FlagPattern, " ")
            .replace(LoneIndicator, " ")
            .replace(EmptyBrackets, " ")
            .replace(Regex("""\s{2,}"""), " ")
            .replace(EdgeSeparators, "")
            .trim()
        return ParsedNodeName(code, display.ifEmpty { name.trim() })
    }
    TextPrefix.find(name)?.let { return ParsedNodeName(it.groupValues[1], name.replace(TextPrefix, "").trim()) }
    return ParsedNodeName(null, name)
}
