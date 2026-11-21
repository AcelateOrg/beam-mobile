package com.beamvpn.app.ui.profiles

import com.beamvpn.app.ui.theme.scaled
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beamvpn.app.ui.components.Badge
import com.beamvpn.app.ui.components.ProfileArt
import com.beamvpn.app.ui.components.ProfileIcon
import com.beamvpn.app.ui.components.PageHeader
import com.beamvpn.app.ui.home.BeamViewModel
import com.beamvpn.app.ui.home.formatBytes
import com.beamvpn.app.ui.home.formatDaysLeft
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.vpn.Profile

/** Port of beam-desktop pages/Profiles.tsx. */
@Composable
fun ProfilesScreen(viewModel: BeamViewModel = viewModel(), onAdd: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var exportingId by remember { mutableStateOf<String?>(null) }
    var paramsId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        PageHeader(BeamIcons.Profiles, "Профили") {
            IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                Icon(BeamIcons.Plus, contentDescription = "Добавить профиль", Modifier.size(14.dp))
            }
        }
        if (state.profiles.isEmpty()) {
            Text(
                "Нет профилей. Добавьте первый!",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 40.dp + com.beamvpn.app.ui.components.LocalBottomInset.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    onSelect = { viewModel.selectProfile(profile.id) },
                    onRefresh = { viewModel.refreshProfile(profile.id) },
                    onDelete = { deleting = profile },
                    onExport = { exportingId = profile.id },
                    onParams = { paramsId = profile.id },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    // Sheets read the live profile, so switches reflect what the bridge saved.
    exportingId?.let { id -> state.profiles.firstOrNull { it.id == id } }?.let { profile ->
        ExportProfileSheet(profile, viewModel, onDismiss = { exportingId = null })
    }
    paramsId?.let { id -> state.profiles.firstOrNull { it.id == id } }?.let { profile ->
        ProfileParamsSheet(profile, viewModel, onDismiss = { paramsId = null })
    }

    deleting?.let { profile ->
        DeleteProfileDialog(profile, onConfirm = { viewModel.deleteProfile(profile.id) }, onDismiss = { deleting = null })
    }
}

@Composable
internal fun DeleteProfileDialog(profile: Profile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить профиль?") },
        text = { Text("Профиль «${profile.name}» будет удалён безвозвратно. Это действие нельзя отменить.") },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
}

/**
 * Card: rounded-3xl (24dp), p-3, bg-card with a 1px white/10 ring; the active one
 * gets a near-white border, bg-primary/5 and a faint primary/20 shadow.
 */
@Composable
private fun ProfileCard(
    profile: Profile,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onParams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = BeamTheme.extra
    val shape = RoundedCornerShape(scaled(24.dp))
    Surface(
        onClick = onSelect,
        shape = shape,
        // Opaque so the glow shadow underneath does not show through the tint.
        color = if (profile.isActive) extra.highlight.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.background) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (profile.isActive) extra.highlight else extra.border),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (profile.isActive) Modifier.dropShadow(shape, Shadow(radius = 3.dp, color = extra.highlight.copy(alpha = 0.2f), offset = androidx.compose.ui.unit.DpOffset(0.dp, 1.dp)))
                else Modifier,
            ),
    ) {
        Box {
        ProfileArt(profile.branding.bannerImage, Modifier.matchParentSize())
        Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileIcon(profile.branding.iconUrl, 20.dp)
                    Text(
                        profile.name,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    profile.provider?.let { Badge(it) }
                    if (profile.isActive) Badge("Активен", secondary = false)
                }
                ProfileUsage(profile)
            }
            ProfileMenu(profile, onRefresh, onDelete, onExport, onParams)
        }
        }
    }
}

/** Traffic bar with used / limit and the time left, or the unlimited line. */
@Composable
internal fun ProfileUsage(profile: Profile, modifier: Modifier = Modifier) {
    val extra = BeamTheme.extra
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!profile.unlimited) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(
                progress = { profile.usedFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = extra.highlight,
                trackColor = extra.accentFill,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Row(Modifier.fillMaxWidth()) {
                InfoText("${formatBytes(profile.dataUsed)} / ${formatBytes(profile.dataLimit)}", Modifier.weight(1f))
                InfoText(formatDaysLeft(profile.daysLeft, profile.isExpired))
            }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(BeamIcons.Infinity, null, Modifier.size(12.dp), tint = muted)
            InfoText("Безлимитный трафик")
            if (profile.daysLeft >= 0 || profile.isExpired) InfoText(formatDaysLeft(profile.daysLeft, profile.isExpired))
        }
    }
}

@Composable
private fun InfoText(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** The "..." button (h-8, bg-accent, rounded-lg) and its dropdown. */
@Composable
internal fun ProfileMenu(profile: Profile, onRefresh: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit, onParams: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(scaled(12.dp)))
                .background(BeamTheme.extra.accentFill)
                .clickable(onClickLabel = "Действия") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(BeamIcons.More, null, Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(scaled(12.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, BeamTheme.extra.border),
        ) {
            MenuItem(BeamIcons.Refresh, "Обновить") { open = false; onRefresh() }
            if (profile.hasSubscription) MenuItem(BeamIcons.Settings, "Параметры") { open = false; onParams() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = BeamTheme.extra.border)
            MenuItem(BeamIcons.Share, "Экспортировать") { open = false; onExport() }
            MenuItem(BeamIcons.Trash, "Удалить", destructive = true) { open = false; onDelete() }
        }
    }
}

@Composable
internal fun MenuItem(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, Modifier.size(14.dp)) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.height(36.dp),
    )
}
