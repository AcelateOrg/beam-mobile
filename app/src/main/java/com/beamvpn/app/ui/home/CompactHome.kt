package com.beamvpn.app.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beamvpn.app.ui.components.AnnounceBanner
import com.beamvpn.app.ui.components.CardActionGap
import com.beamvpn.app.ui.components.CardActionSize
import com.beamvpn.app.ui.components.CountryFlag
import com.beamvpn.app.ui.components.ProfileArt
import com.beamvpn.app.ui.components.ProfileIcon
import com.beamvpn.app.ui.components.openLink
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.profiles.DeleteProfileDialog
import com.beamvpn.app.ui.profiles.ExportProfileSheet
import com.beamvpn.app.ui.profiles.MenuItem
import com.beamvpn.app.ui.profiles.ProfileParamsSheet
import com.beamvpn.app.ui.servers.GroupHeader
import com.beamvpn.app.ui.servers.NodeEditorSheet
import com.beamvpn.app.ui.servers.PinnedMark
import com.beamvpn.app.ui.servers.PingIcon
import com.beamvpn.app.ui.servers.PingSlot
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.ui.theme.scaled
import com.beamvpn.app.vpn.ConnectionPhase
import com.beamvpn.app.vpn.Node
import com.beamvpn.app.vpn.NodeGroup
import com.beamvpn.app.vpn.PingResult
import com.beamvpn.app.vpn.Profile
import com.beamvpn.app.vpn.ProfileNodes
import com.beamvpn.app.vpn.SwitchPhase
import com.beamvpn.app.vpn.VpnUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Компактный режим": the connect button on top and, under it, every profile with its servers, so Profiles and
 * Servers need no tabs. A profile is a quiet panel (name, one line of status, one menu) holding one-line server
 * rows; each panel is drawn as slices across lazy items, which keeps long server lists cheap to scroll.
 */
@Composable
fun CompactHomeScreen(viewModel: BeamViewModel = viewModel(), onAddProfile: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onToggle = rememberConnectToggle(viewModel, state.phase)
    val folds = rememberFoldStore()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var exportingId by remember { mutableStateOf<String?>(null) }
    var paramsId by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    val rows = remember(state.profiles, state.nodes, folds.value) { buildRows(state.profiles, state.nodes, folds.value) }
    val currentRows by rememberUpdatedState(rows)
    val allCollapsed = state.profiles.isNotEmpty() && state.profiles.all { folds.value.profile(it.id) }

    /** Unfolds the way to a profile (and one of its groups) and scrolls there once the list has it. */
    fun reveal(profileId: String, group: NodeGroup?, key: String) {
        folds.reveal(profileId, group)
        scope.launch {
            val index = withTimeoutOrNull(1_000) {
                snapshotFlow { currentRows.indexOfFirst { it.key == key } }.first { it >= 0 }
            } ?: return@launch
            listState.animateScrollToItem(index, scrollOffset = -SCROLL_MARGIN_PX)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp + com.beamvpn.app.ui.components.LocalBottomInset.current)) {
            items(rows, key = { it.key }, contentType = { it::class }) { row ->
                when (row) {
                    CompactRow.Hero -> Hero(
                        state = state,
                        onToggle = onToggle,
                        onPickServer = { item ->
                            if (item.isMore) {
                                state.activeProfile?.let { reveal(it.id, null, headerKey(it.id)) }
                            } else {
                                if (!item.current) viewModel.selectNode(item.id)
                                if (state.phase == ConnectionPhase.Disconnected || state.phase == ConnectionPhase.Error) onToggle()
                            }
                        },
                    )
                    CompactRow.Title -> ListTitle(
                        allCollapsed = allCollapsed,
                        onToggleAll = { folds.setProfiles(state.profiles.map { it.id }, collapsed = !allCollapsed) },
                        onAdd = onAddProfile,
                    )
                    CompactRow.Empty -> EmptyProfiles(onAddProfile)
                    is CompactRow.Header -> ProfileHeader(
                        profile = row.profile,
                        servers = row.servers,
                        collapsed = row.collapsed,
                        refreshing = row.profile.id in state.refreshing,
                        pinging = state.isPinging(row.profile.id),
                        onToggle = { folds.toggleProfile(row.profile.id) },
                        onRefresh = { viewModel.refreshProfile(row.profile.id) },
                        onPing = { viewModel.ping(row.profile.id, null) },
                        onDelete = { deleting = row.profile },
                        onExport = { exportingId = row.profile.id },
                        onParams = { paramsId = row.profile.id },
                        joined = row.joined,
                        modifier = Modifier.animateItem(),
                    )
                    is CompactRow.NoServers -> Text(
                        "В профиле нет серверов",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .animateItem()
                            .panelSlice(Slice.Middle, panelStyle(row.active))
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                    is CompactRow.Group -> GroupHeader(
                        group = row.group,
                        collapsed = row.collapsed,
                        hasSelected = row.hasSelected,
                        pinging = state.isPinging(row.profileId, row.group.id),
                        onToggle = { folds.toggleGroup(row.profileId, row.group) },
                        onPing = { viewModel.ping(row.profileId, row.group.id) },
                        modifier = Modifier.animateItem().panelSlice(Slice.Middle, panelStyle(row.active)).padding(top = 4.dp),
                    )
                    is CompactRow.Server -> ServerRow(
                        node = row.node,
                        selected = row.selected,
                        ping = state.profilePings[row.profileId]?.get(row.node.id),
                        pending = state.isPingPending(row.profileId, row.node.id),
                        onClick = { if (!row.selected) viewModel.selectNode(row.profileId, row.node.id) },
                        onLongClick = { editing = row.profileId to row.node.id },
                        roundTop = row.top,
                        roundBottom = row.bottom,
                        // Connected tiles, as on the Servers page: 2dp of page between them.
                        modifier = Modifier
                            .animateItem()
                            .panelSlice(Slice.Middle, panelStyle(row.active))
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                    is CompactRow.Footer -> Box(
                        Modifier
                            .animateItem()
                            .padding(bottom = PANEL_GAP)
                            .panelSlice(Slice.Bottom, panelStyle(row.active))
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                }
            }
        }

        val heroGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
        AnimatedVisibility(
            visible = heroGone,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { -it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        ) {
            MiniStatus(state, onToggle, onClick = { scope.launch { listState.animateScrollToItem(0) } })
        }
    }

    exportingId?.let { id -> state.profiles.firstOrNull { it.id == id } }?.let { profile ->
        ExportProfileSheet(profile, viewModel, onDismiss = { exportingId = null })
    }
    paramsId?.let { id -> state.profiles.firstOrNull { it.id == id } }?.let { profile ->
        ProfileParamsSheet(profile, viewModel, onDismiss = { paramsId = null })
    }
    deleting?.let { profile ->
        DeleteProfileDialog(profile, onConfirm = { viewModel.deleteProfile(profile.id) }, onDismiss = { deleting = null })
    }
    editing?.let { (profileId, nodeId) ->
        NodeEditorSheet(profileId, nodeId, viewModel, onDismiss = { editing = null })
    }
}

private const val SCROLL_MARGIN_PX = 48
private val PANEL_GAP = 12.dp
private val ART_HEIGHT = 96.dp

// ---- List model ----

private fun headerKey(profileId: String) = "p:$profileId"
private fun nodeKey(profileId: String, groupId: String, nodeId: String) = "n:$profileId/$groupId/$nodeId"

private sealed interface CompactRow {
    val key: String

    data object Hero : CompactRow { override val key = "hero" }
    data object Title : CompactRow { override val key = "title" }
    data object Empty : CompactRow { override val key = "empty" }
    data class Header(val profile: Profile, val servers: Int, val collapsed: Boolean, val joined: Boolean = false) : CompactRow {
        override val key = headerKey(profile.id)
    }
    data class NoServers(val profileId: String, val active: Boolean) : CompactRow { override val key = "e:$profileId" }
    data class Group(val profileId: String, val active: Boolean, val group: NodeGroup, val collapsed: Boolean, val hasSelected: Boolean) : CompactRow {
        override val key = "g:$profileId/${group.id}"
    }
    data class Server(
        val profileId: String,
        val active: Boolean,
        val groupId: String,
        val node: Node,
        val selected: Boolean,
        val top: Boolean = false,
        val bottom: Boolean = false,
    ) : CompactRow {
        override val key = nodeKey(profileId, groupId, node.id)
    }
    data class Footer(val profileId: String, val active: Boolean) : CompactRow { override val key = "f:$profileId" }
}

/**
 * Gives every tile its corners: a run of servers is one connected stack, round where it starts after a group header
 * and where it ends; the profile header joins the servers right under it.
 */
private fun shapeRows(rows: List<CompactRow>): List<CompactRow> = rows.mapIndexed { i, row ->
    val prev = rows.getOrNull(i - 1)
    val next = rows.getOrNull(i + 1)
    when (row) {
        is CompactRow.Header -> row.copy(joined = !row.collapsed && (next is CompactRow.Server || next is CompactRow.NoServers))
        is CompactRow.Server -> row.copy(top = prev !is CompactRow.Server, bottom = next !is CompactRow.Server)
        else -> row
    }
}

private fun buildRows(profiles: List<Profile>, nodes: Map<String, ProfileNodes>, folds: Folds): List<CompactRow> = shapeRows(buildRowsFlat(profiles, nodes, folds))

private fun buildRowsFlat(profiles: List<Profile>, nodes: Map<String, ProfileNodes>, folds: Folds): List<CompactRow> = buildList {
    add(CompactRow.Hero)
    if (profiles.isEmpty()) {
        add(CompactRow.Empty)
        return@buildList
    }
    add(CompactRow.Title)
    profiles.forEach { profile ->
        val id = profile.id
        val active = profile.isActive
        val profileNodes = nodes[id] ?: ProfileNodes()
        val collapsed = folds.profile(id)
        add(CompactRow.Header(profile, profileNodes.count, collapsed))
        if (collapsed) return@forEach
        if (profileNodes.count == 0) add(CompactRow.NoServers(id, active))
        // A profile's remembered server is only marked while that profile is the one in use.
        val selectedId = profileNodes.selectedId.takeIf { active }
        profileNodes.groups.forEach { group ->
            val groupCollapsed = !group.ungrouped && folds.group(id, group)
            if (!group.ungrouped) {
                add(CompactRow.Group(id, active, group, groupCollapsed, groupCollapsed && group.nodes.any { it.id == selectedId }))
            }
            if (!groupCollapsed) group.nodes.forEach { add(CompactRow.Server(id, active, group.id, it, it.id == selectedId)) }
        }
        add(CompactRow.Footer(id, active))
    }
}

// ---- Folding, kept across launches ----

@Immutable
private data class Folds(val profiles: Map<String, Boolean> = emptyMap(), val groups: Map<String, Boolean> = emptyMap()) {
    fun profile(id: String) = profiles[id] ?: false

    /** Without the user's own choice a group starts the way the provider asked. */
    fun group(profileId: String, group: NodeGroup) = groups[groupKey(profileId, group)] ?: group.collapsed

    companion object {
        fun groupKey(profileId: String, group: NodeGroup) = "$profileId/${group.id}"
    }
}

@Stable
private class FoldStore(private val prefs: SharedPreferences) {
    var value by mutableStateOf(Folds(read(PROFILES), read(GROUPS)))
        private set

    fun toggleProfile(id: String) = save(value.copy(profiles = value.profiles + (id to !value.profile(id))))

    fun toggleGroup(profileId: String, group: NodeGroup) =
        save(value.copy(groups = value.groups + (Folds.groupKey(profileId, group) to !value.group(profileId, group))))

    fun setProfiles(ids: List<String>, collapsed: Boolean) = save(value.copy(profiles = value.profiles + ids.associateWith { collapsed }))

    fun reveal(profileId: String, group: NodeGroup?) {
        var next = value.copy(profiles = value.profiles + (profileId to false))
        if (group != null && !group.ungrouped) next = next.copy(groups = next.groups + (Folds.groupKey(profileId, group) to false))
        if (next != value) save(next)
    }

    private fun save(folds: Folds) {
        value = folds
        prefs.edit().putStringSet(PROFILES, encode(folds.profiles)).putStringSet(GROUPS, encode(folds.groups)).apply()
    }

    private fun read(key: String): Map<String, Boolean> =
        prefs.getStringSet(key, null).orEmpty().associate { it.substringBeforeLast('=') to (it.substringAfterLast('=') == "1") }

    private fun encode(map: Map<String, Boolean>): Set<String> = map.mapTo(HashSet()) { (k, v) -> "$k=${if (v) 1 else 0}" }

    private companion object {
        const val PROFILES = "collapsedProfiles"
        const val GROUPS = "collapsedGroups"
    }
}

@Composable
private fun rememberFoldStore(): FoldStore {
    val context = LocalContext.current
    return remember { FoldStore(context.applicationContext.getSharedPreferences("beam_compact", Context.MODE_PRIVATE)) }
}

// ---- Profile panel ----

private enum class Slice { Top, Middle, Bottom, Single }

@Immutable
private data class PanelStyle(val fill: Color, val stroke: Color, val radius: Dp)

/** A filled panel; only the active profile gets an outline, a soft one, so nothing nests border in border. */
@Composable
private fun panelStyle(active: Boolean): PanelStyle {
    val extra = BeamTheme.extra
    val fill by animateColorAsState(
        if (active) extra.highlight.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow) else MaterialTheme.colorScheme.surfaceContainerLow,
        tween(300),
        label = "panelFill",
    )
    val stroke by animateColorAsState(if (active) extra.highlight.copy(alpha = 0.22f) else Color.Transparent, tween(300), label = "panelStroke")
    return PanelStyle(fill, stroke, com.beamvpn.app.ui.settings.GroupOuter)
}

/**
 * Paints this row's part of a rounded card: a middle slice gets only the side edges, the top and bottom
 * slices the rounded ends, so consecutive rows read as one card.
 */
private fun Modifier.panelSlice(slice: Slice, style: PanelStyle): Modifier = padding(horizontal = 12.dp).drawBehind {
    val r = style.radius.toPx()
    val w = 1.dp.toPx()
    val reach = r + w
    val top = if (slice == Slice.Top || slice == Slice.Single) 0f else -reach
    val bottom = if (slice == Slice.Bottom || slice == Slice.Single) size.height else size.height + reach
    clipRect {
        drawRoundRect(style.fill, Offset(0f, top), Size(size.width, bottom - top), CornerRadius(r))
        if (style.stroke.alpha > 0f) {
            drawRoundRect(
                style.stroke,
                Offset(w / 2, top + w / 2),
                Size(size.width - w, bottom - top - w),
                CornerRadius(r - w / 2),
                style = Stroke(w),
            )
        }
    }
}

/** Name, one line of status and one menu; tapping the header folds the profile. */
@Composable
private fun ProfileHeader(
    profile: Profile,
    servers: Int,
    collapsed: Boolean,
    refreshing: Boolean,
    pinging: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onParams: () -> Unit,
    joined: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val extra = BeamTheme.extra
    val colors = MaterialTheme.colorScheme
    val style = panelStyle(profile.isActive)
    val r = style.radius
    val bottomR = if (collapsed) r else 0.dp
    val shape = RoundedCornerShape(topStart = r, topEnd = r, bottomStart = bottomR, bottomEnd = bottomR)
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "profileChevron")

    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = if (collapsed) PANEL_GAP else 0.dp)
            .panelSlice(if (collapsed) Slice.Single else Slice.Top, style),
    ) {
        // The art keeps the Profiles card's height instead of stretching over a taller panel, and fades out below.
        Box(Modifier.matchParentSize().padding(1.dp).clip(shape)) {
            ProfileArt(profile.branding.bannerImage, Modifier.fillMaxWidth().height(ART_HEIGHT), fadeBottom = true)
        }
        Column(
            Modifier
                .clip(shape)
                .clickable(onClickLabel = if (collapsed) "Развернуть" else "Свернуть", onClick = onToggle)
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileAvatar(profile)
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        profile.name,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (profile.isActive) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .dropShadow(CircleShape, Shadow(radius = 6.dp, color = extra.highlight))
                                .background(extra.highlight, CircleShape),
                        )
                    }
                    if (refreshing) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = colors.onSurfaceVariant)
                }
                // One tight group on the right edge: ping, fold, menu. The menu stays outermost as everywhere
                // else; the fold sits next to it, over the banner's own chevron below.
                Row(horizontalArrangement = Arrangement.spacedBy(CardActionGap), verticalAlignment = Alignment.CenterVertically) {
                    if (servers > 0) {
                        Box(
                            Modifier.size(CardActionSize).clip(CircleShape).clickable(enabled = !pinging, onClickLabel = "Проверить пинг", onClick = onPing),
                            contentAlignment = Alignment.Center,
                        ) { PingIcon(pinging) }
                    }
                    Box(
                        Modifier.size(CardActionSize).clip(CircleShape).clickable(onClickLabel = if (collapsed) "Развернуть" else "Свернуть", onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) { Icon(BeamIcons.ChevronDown, null, Modifier.size(16.dp).rotate(rotation), tint = colors.onSurfaceVariant) }
                    ProfileOverflow(profile, refreshing, onRefresh, onParams, onExport, onDelete)
                }
            }
            // The banner shares the header's right edge so its buttons stand under the header's.
            AnnounceBanner(profile, framed = false)
            Box(Modifier.padding(end = 10.dp)) { ProfileTraffic(profile) }
        }
    }
}

/** The plan at the bottom of the card: a traffic bar for a limited one, the infinity sign for an unlimited one. */
@Composable
private fun ProfileTraffic(profile: Profile) {
    val extra = BeamTheme.extra
    val colors = MaterialTheme.colorScheme
    val days = if (profile.daysLeft >= 0 || profile.isExpired) formatDaysLeft(profile.daysLeft, profile.isExpired) else null
    val overLimit = !profile.unlimited && profile.usedFraction >= 1f
    val daysColor = if (profile.isExpired) colors.error else colors.onSurfaceVariant
    if (profile.unlimited) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(BeamIcons.Infinity, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
            Text("Безлимит", fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            days?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = daysColor) }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { profile.usedFraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (overLimit) colors.error else extra.highlight,
            trackColor = extra.accentFill,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatBytes(profile.dataUsed)} из ${formatBytes(profile.dataLimit)}",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (overLimit) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            days?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = daysColor) }
        }
    }
}

/** The profile's less frequent actions, behind one button of the card header's group. */
@Composable
private fun ProfileOverflow(
    profile: Profile,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onParams: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(CardActionSize).clip(CircleShape).clickable(onClickLabel = "Действия с профилем") { open = true },
            contentAlignment = Alignment.Center,
        ) { Icon(BeamIcons.More, "Действия с профилем", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(scaled(12.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, BeamTheme.extra.border),
        ) {
            if (profile.hasSubscription && !refreshing) MenuItem(BeamIcons.Refresh, "Обновить") { open = false; onRefresh() }
            profile.branding.communityUrl?.let { url ->
                MenuItem(BeamIcons.Send, "Сообщество") { open = false; context.openLink(url) }
            }
            if (profile.hasSubscription) MenuItem(BeamIcons.Settings, "Параметры") { open = false; onParams() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = BeamTheme.extra.border)
            MenuItem(BeamIcons.Share, "Экспортировать") { open = false; onExport() }
            MenuItem(BeamIcons.Trash, "Удалить", destructive = true) { open = false; onDelete() }
        }
    }
}

/** The provider's icon, or the profile's initial on a tile when there is none. */
@Composable
private fun ProfileAvatar(profile: Profile) {
    val icon = profile.branding.iconUrl
    if (!icon.isNullOrBlank()) {
        ProfileIcon(icon, 28.dp)
        return
    }
    Box(
        Modifier.size(28.dp).background(BeamTheme.extra.accentFill, RoundedCornerShape(scaled(8.dp))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile.name.trim().take(1).uppercase().ifEmpty { "•" },
            fontSize = 13.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

/** One line per server: flag, name (with the provider's badge) and the ping; the chosen one is tinted. */
@Composable
private fun ServerRow(
    node: Node,
    selected: Boolean,
    ping: PingResult?,
    pending: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    roundTop: Boolean = false,
    roundBottom: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val extra = BeamTheme.extra
    val colors = MaterialTheme.colorScheme
    val outer = 18.dp
    val inner = 5.dp
    val spring = androidx.compose.animation.core.spring<Dp>(dampingRatio = 0.6f, stiffness = 400f)
    val top by androidx.compose.animation.core.animateDpAsState(if (selected || roundTop) outer else inner, spring, label = "rowTop")
    val bottom by androidx.compose.animation.core.animateDpAsState(if (selected || roundBottom) outer else inner, spring, label = "rowBottom")
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHigh)
            .combinedClickable(
                onClickLabel = if (selected) null else "Выбрать сервер",
                onClick = onClick,
                onLongClickLabel = "Изменить",
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CountryFlag(node.parsed.countryCode, 26.dp)
        // Name and marks take the free width, so the ping stands at the row's end as on the Servers page.
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                node.parsed.displayName,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = colors.onSurface.copy(alpha = if (selected) 1f else 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (node.pinned) PinnedMark()
            node.badge?.let {
                Text(
                    it,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = extra.highlight,
                    maxLines = 1,
                    modifier = Modifier
                        .background(extra.highlight.copy(alpha = 0.15f), RoundedCornerShape(scaled(4.dp)))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        PingSlot(ping, pending)
        if (selected) Icon(BeamIcons.Check, null, Modifier.size(16.dp), tint = colors.onSecondaryContainer)
    }
}

// ---- Top of the page ----

/**
 * The home screen's button and status at a size that leaves room for the list. The top inset is what the
 * button's glow needs to fade out before the list's edge, which would otherwise cut it off.
 */
@Composable
private fun Hero(state: VpnUiState, onToggle: () -> Unit, onPickServer: (RadialItem) -> Unit) {
    val switching = state.switch.phase == SwitchPhase.Switching
    Column(
        Modifier.fillMaxWidth().padding(top = 112.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val radialItems = remember(state.groups, state.selectedNodeId, state.pings) {
            buildRadialItems(state.groups, state.selectedNodeId, state.pings)
        }
        ConnectButton(
            phase = if (switching) ConnectionPhase.Connecting else state.phase,
            onClick = onToggle,
            size = 200.dp,
            radialItems = radialItems,
            onPickServer = onPickServer,
        )
        Spacer(Modifier.height(48.dp))
        StatusText(if (switching) state.copy(phase = ConnectionPhase.Connecting) else state, state.switch.phase)
        ServerHop(state.switch, state.server)
        state.error?.takeIf { state.phase == ConnectionPhase.Error }?.let { ErrorNote(it) }
        ConnectionStats(
            visible = state.phase == ConnectionPhase.Connected,
            stats = state.stats,
            healthy = state.healthy || state.reconnecting,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** "Профили" with its two list-wide actions: fold everything and add a profile. */
@Composable
private fun ListTitle(allCollapsed: Boolean, onToggleAll: () -> Unit, onAdd: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Профили", fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = muted, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = if (allCollapsed) "Развернуть всё" else "Свернуть всё", onClick = onToggleAll),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (allCollapsed) BeamIcons.ChevronsUpDown else BeamIcons.ChevronsDownUp,
                if (allCollapsed) "Развернуть всё" else "Свернуть всё",
                Modifier.size(16.dp),
                tint = muted,
            )
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Добавить профиль", onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).background(BeamTheme.extra.accentFill, CircleShape), contentAlignment = Alignment.Center) {
                Icon(BeamIcons.Plus, "Добавить профиль", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EmptyProfiles(onAdd: () -> Unit) {
    val extra = BeamTheme.extra
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(scaled(24.dp)))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).background(extra.highlight.copy(alpha = 0.10f), RoundedCornerShape(scaled(16.dp))),
            contentAlignment = Alignment.Center,
        ) { Icon(BeamIcons.Profiles, null, Modifier.size(22.dp), tint = extra.highlight) }
        Text("Пока нет профилей", fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Text(
            "Добавьте подписку или ключ — серверы появятся прямо здесь",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(scaled(14.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
            modifier = Modifier.padding(top = 18.dp).height(48.dp),
        ) {
            Icon(BeamIcons.Plus, null, Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text("Добавить профиль", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** A pill that stays on top once the button scrolls away: state, time and a power toggle. */
@Composable
private fun MiniStatus(state: VpnUiState, onToggle: () -> Unit, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = BeamTheme.extra
    val switching = state.switch.phase == SwitchPhase.Switching
    val phase = if (switching) ConnectionPhase.Connecting else state.phase
    val connected = phase == ConnectionPhase.Connected
    val (label, tone) = when (phase) {
        ConnectionPhase.Connected -> when {
            state.reconnecting -> "Переподключение..." to colors.onSurfaceVariant
            state.healthy -> "Подключено" to colors.primary
            else -> "Нестабильно" to extra.warning
        }
        ConnectionPhase.Connecting -> "Подключение..." to extra.warning
        ConnectionPhase.Error -> "Ошибка" to colors.error
        ConnectionPhase.Disconnected -> "Отключено" to colors.onSurfaceVariant
    }
    val dot by animateColorAsState(tone, tween(300), label = "miniDot")
    val power by animateColorAsState(if (connected) colors.primary else extra.accentFill, tween(300), label = "miniPower")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.surfaceContainerHighest,
        modifier = Modifier.dropShadow(CircleShape, Shadow(radius = 16.dp, color = com.beamvpn.app.ui.theme.themedShadow(colors.background, 0.8f), offset = DpOffset(0.dp, 6.dp))),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The halo takes the target tone, not the animated one: a shadow's blurred bitmap is cached per colour,
            // so a fading colour made (and kept) a new one every frame.
            val halo = remember(tone, connected) { if (connected) Shadow(radius = 6.dp, color = tone.copy(alpha = 0.9f)) else null }
            Box(
                Modifier
                    .size(7.dp)
                    .then(if (halo != null && !BeamTheme.effects.reduced) Modifier.dropShadow(CircleShape, halo) else Modifier)
                    .drawBehind { drawCircle(dot) },
            )
            Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (connected) {
                Text(
                    formatDuration(state.stats.durationSec),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    color = colors.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(power)
                    .clickable(enabled = phase != ConnectionPhase.Connecting, onClickLabel = if (connected) "Отключиться" else "Подключиться", onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BeamIcons.Power, null, Modifier.size(15.dp), tint = if (connected) colors.onPrimary else colors.onSurface)
            }
        }
    }
}
