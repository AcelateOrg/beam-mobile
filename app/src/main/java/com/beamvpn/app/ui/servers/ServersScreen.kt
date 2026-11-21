@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.beamvpn.app.ui.servers

import com.beamvpn.app.ui.theme.scaled
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beamvpn.app.ui.components.AnnounceBanner
import com.beamvpn.app.ui.components.Badge
import com.beamvpn.app.ui.components.CountryFlag
import com.beamvpn.app.ui.components.PageHeader
import com.beamvpn.app.ui.home.BeamViewModel
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.vpn.Node
import com.beamvpn.app.vpn.NodeGroup
import com.beamvpn.app.vpn.PingResult

/** Port of beam-desktop pages/Nodes.tsx ("Конфигурации"), single-column like the mini window. */
@Composable
fun ServersScreen(viewModel: BeamViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val total = state.groups.sumOf { it.nodes.size }
    val profileId = state.activeProfile?.id
    val pingingAll = state.isPinging(profileId)
    val showPingAll = state.groups.size > 1 || state.groups.any { it.ungrouped }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        PageHeader(BeamIcons.Servers, "Конфигурации") {
            if (total > 0 && showPingAll) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(scaled(8.dp)))
                        .clickable(enabled = !pingingAll) { viewModel.ping(null) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PingIcon(pingingAll)
                    Text(
                        if (pingingAll) "Проверка..." else "Пинг всех",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Badge(total.toString(), fontSize = 12.sp)
        }

        if (total == 0) {
            EmptyState()
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp + com.beamvpn.app.ui.components.LocalBottomInset.current)) {
            state.activeProfile?.takeIf { !it.announce.isNullOrBlank() }?.let { profile ->
                item(key = "announce-${profile.id}") { AnnounceBanner(profile, Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) }
            }
            item(key = "top-gap") { Box(Modifier.height(12.dp)) }
            state.groups.forEach { group ->
                groupSection(
                    group = group,
                    collapsed = collapsed[group.id] ?: group.collapsed,
                    onToggle = { collapsed[group.id] = !(collapsed[group.id] ?: group.collapsed) },
                    selectedNodeId = state.selectedNodeId,
                    pings = state.pings,
                    pending = profileId?.let { state.pendingPings[it] }.orEmpty(),
                    pinging = state.isPinging(profileId, group.id),
                    onPing = { viewModel.ping(group.id) },
                    onSelect = viewModel::selectNode,
                    onEdit = { editing = it },
                )
            }
        }
    }

    val editingProfile = profileId
    editing?.let { nodeId ->
        if (editingProfile != null) NodeEditorSheet(editingProfile, nodeId, viewModel, onDismiss = { editing = null })
    }
}

private fun LazyListScope.groupSection(
    group: NodeGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    selectedNodeId: String?,
    pings: Map<String, PingResult>,
    pending: Set<String>,
    pinging: Boolean,
    onPing: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    if (!group.ungrouped) {
        item(key = "h-${group.id}") {
            GroupHeader(
                group = group,
                collapsed = collapsed,
                hasSelected = collapsed && group.nodes.any { it.id == selectedNodeId },
                pinging = pinging,
                onToggle = onToggle,
                onPing = onPing,
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (group.ungrouped || !collapsed) {
        val count = group.nodes.size
        itemsIndexed(group.nodes, key = { _, it -> "${group.id}/${it.id}" }) { index, node ->
            NodeCard(
                node = node,
                selected = node.id == selectedNodeId,
                ping = pings[node.id],
                pending = node.id in pending,
                onClick = { onSelect(node.id) },
                onLongClick = { onEdit(node.id) },
                position = index to count,
                // A group is one stack of tiles: 2dp of page between them, round only at its ends.
                modifier = Modifier.animateItem().padding(horizontal = 12.dp, vertical = 1.dp),
            )
        }
    }
    // mb-5 after every group.
    item(key = "gap-${group.id}") { Box(Modifier.height(16.dp)) }
}

/** h-8 row: chevron, folder (or emoji), 13sp name, count, a fading hairline and the group ping button. */
@Composable
internal fun GroupHeader(
    group: NodeGroup,
    collapsed: Boolean,
    hasSelected: Boolean,
    pinging: Boolean,
    onToggle: () -> Unit,
    onPing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = BeamTheme.extra
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp)
            .height(32.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(BeamIcons.ChevronDown, null, Modifier.size(14.dp).rotate(rotation), tint = muted)
        Box(Modifier.size(16.dp, 24.dp), contentAlignment = Alignment.Center) {
            if (group.emoji != null) Text(group.emoji, fontSize = 14.sp)
            else Icon(BeamIcons.Folder, null, Modifier.size(14.dp), tint = extra.highlight)
        }
        Text(
            group.name,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.025).em,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            maxLines = 1,
        )
        Text(
            group.nodes.size.toString(),
            fontSize = 11.sp,
            style = LocalTextStyle.current.merge(TextStyle(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")),
            color = muted,
        )
        if (hasSelected) {
            Box(
                Modifier
                    .size(6.dp)
                    .dropShadow(RoundedCornerShape(50), Shadow(radius = 6.dp, color = extra.highlight))
                    .background(extra.highlight, RoundedCornerShape(50)),
            )
        }
        WavyDivider(pinging, Modifier.weight(1f))
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(scaled(6.dp)))
                .clickable(enabled = !pinging, onClick = onPing),
            contentAlignment = Alignment.Center,
        ) { PingIcon(pinging) }
    }
}

/** "Не обновляется из подписки": the user froze this server's config. */
@Composable
internal fun PinnedMark() {
    Icon(BeamIcons.Lock, "Не обновляется из подписки", Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The hairline after a group's name. At rest it is straight and fades out to the right; while the group is being
 * pinged it rises into a wave that runs along it, and settles flat again when the check ends.
 */
@Composable
private fun WavyDivider(active: Boolean, modifier: Modifier = Modifier) {
    val extra = BeamTheme.extra
    val amplitude by animateFloatAsState(if (active) 1f else 0f, androidx.compose.animation.core.tween(450), label = "waveHeight")
    val phase = if (active || amplitude > 0f) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
        t.animateFloat(
            0f,
            1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)),
            label = "wavePhase",
        ).value
    } else 0f
    val line = if (active) extra.highlight.copy(alpha = 0.7f) else extra.border
    val path = remember { androidx.compose.ui.graphics.Path() }
    androidx.compose.foundation.Canvas(modifier.height(10.dp)) {
        val mid = size.height / 2
        val brush = Brush.horizontalGradient(listOf(line, Color.Transparent))
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if (amplitude <= 0.001f) {
            drawLine(brush, androidx.compose.ui.geometry.Offset(0f, mid), androidx.compose.ui.geometry.Offset(size.width, mid), strokeWidth = 1.dp.toPx())
            return@Canvas
        }
        val wave = 14.dp.toPx()
        val amp = 2.6.dp.toPx() * amplitude
        val shift = phase * wave
        path.rewind()
        var x = 0f
        val step = 2.dp.toPx()
        while (x <= size.width) {
            val y = mid + amp * kotlin.math.sin(((x + shift) / wave) * 2f * Math.PI.toFloat())
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += step
        }
        drawPath(path, brush, style = stroke)
    }
}

/** Busy: Material's loading indicator, a shape that morphs between Material shapes. */
@Composable
internal fun PingIcon(busy: Boolean) {
    if (busy) {
        androidx.compose.material3.LoadingIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Icon(BeamIcons.Wifi, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * NodeCard: rounded-xl, px-3 py-2.5, min-h 60, 36dp round flag, 14sp medium name,
 * a mono uppercase protocol line or the description, and the ping on the right.
 * Selected: bg-primary/10, border-primary/70, ring-1 primary/25 and a soft shadow.
 */
@Composable
internal fun NodeCard(
    node: Node,
    selected: Boolean,
    ping: PingResult?,
    pending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** Index and size of the server's group: the tile is round at the group's ends and tight in between. */
    position: Pair<Int, Int> = 0 to 1,
) {
    val haptic = LocalHapticFeedback.current
    val extra = BeamTheme.extra
    val colors = MaterialTheme.colorScheme
    val (index, count) = position
    val outer = 24.dp
    val inner = 6.dp
    // The chosen server springs into a fully rounded tile, the Expressive way of marking a selection.
    val spring = androidx.compose.animation.core.spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.6f, stiffness = 400f)
    val top by androidx.compose.animation.core.animateDpAsState(if (selected || index == 0) outer else inner, spring, label = "cardTop")
    val bottom by androidx.compose.animation.core.animateDpAsState(if (selected || index == count - 1) outer else inner, spring, label = "cardBottom")
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    val parsed = node.parsed
    val description = node.description?.trim()?.takeIf { it.isNotEmpty() }
    val showProtocol = description == null && !node.hideProtocol && node.protocol.isNotEmpty()

    Surface(
        shape = shape,
        // Opaque so the shadow underneath does not tint the card.
        color = (if (selected) extra.highlight.copy(alpha = 0.14f) else colors.surfaceContainer).compositeOver(colors.background),
        border = if (selected) BorderStroke(1.5.dp, extra.highlight.copy(alpha = 0.7f)) else null,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.dropShadow(shape, Shadow(radius = 10.dp, color = extra.highlight.copy(alpha = 0.14f), offset = DpOffset(0.dp, 4.dp)))
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClickLabel = if (onLongClick != null) "Изменить" else null,
                    onLongClick = onLongClick?.let { edit -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); edit() } },
                )
                .heightIn(min = 60.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CountryFlag(parsed.countryCode, 36.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        parsed.displayName,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface.copy(alpha = if (selected) 1f else 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (node.pinned) PinnedMark()
                }
                if (node.badge != null || description != null || showProtocol) {
                    Row(
                        Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Provider chip ("NEW", "🔥"): bg-primary/15, 8.5sp bold.
                        node.badge?.let {
                            Text(
                                it,
                                fontSize = 8.5.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = extra.highlight,
                                maxLines = 1,
                                modifier = Modifier
                                    .background(extra.highlight.copy(alpha = 0.15f), RoundedCornerShape(scaled(4.dp)))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                        when {
                            description != null -> Text(
                                description,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            showProtocol -> Text(
                                node.protocol.uppercase(),
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.05.em,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            PingSlot(ping, pending)
        }
    }
}

/** The ping on the right of a server row; while a new measurement runs the previous one stays, dimmed. */
@Composable
internal fun PingSlot(ping: PingResult?, pending: Boolean) {
    when {
        // One per row would be dozens of morphing shapes at once while a group is pinged: rows get a light
        // pulsing dot, the morphing indicator stays on the group's ping button.
        pending && ping == null -> PendingDot()
        ping != null -> Box(Modifier.graphicsLayer { alpha = if (pending) 0.4f else 1f }) { PingLabel(ping) }
    }
}

@Composable
private fun PendingDot() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pending")
    val a by t.animateFloat(
        0.25f,
        0.9f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pendingAlpha",
    )
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { alpha = a }
            .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(50)),
    )
}

@Composable
private fun PingLabel(ping: PingResult) {
    val extra = BeamTheme.extra
    val (text, color) = when (ping) {
        PingResult.Timeout -> "Таймаут" to extra.pingBad
        is PingResult.Ok -> "${ping.latencyMs}мс" to when {
            ping.latencyMs < 150 -> extra.pingGood
            ping.latencyMs < 300 -> extra.pingMid
            else -> extra.pingBad
        }
    }
    Text(text, fontSize = 11.sp, lineHeight = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = color)
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) { Icon(BeamIcons.Servers, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("Нет серверов", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Добавьте профиль с серверами",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
