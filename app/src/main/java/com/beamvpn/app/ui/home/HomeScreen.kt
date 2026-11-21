package com.beamvpn.app.ui.home

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.em
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beamvpn.app.ui.components.CountryFlag
import com.beamvpn.app.ui.components.ProfileArt
import com.beamvpn.app.ui.components.ProfileIcon
import com.beamvpn.app.ui.components.openLink
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.vpn.ConnectionPhase
import com.beamvpn.app.vpn.Node
import com.beamvpn.app.vpn.NodeGroup
import com.beamvpn.app.vpn.Profile
import com.beamvpn.app.vpn.ProfileNodes
import com.beamvpn.app.vpn.ProfileSummary
import com.beamvpn.app.vpn.ServerSummary
import com.beamvpn.app.vpn.SwitchPhase
import com.beamvpn.app.vpn.VpnUiState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: BeamViewModel = viewModel(),
    onAddProfile: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onPickServer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onToggle = rememberConnectToggle(viewModel, state.phase)
    HomeContent(
        state = state,
        onToggle = onToggle,
        onAddProfile = onAddProfile,
        onOpenProfiles = onOpenProfiles,
        onPickServer = onPickServer,
    )
}

/** Connects or disconnects, asking for VPN consent (and notifications) the first time. */
@Composable
internal fun rememberConnectToggle(viewModel: BeamViewModel, phase: ConnectionPhase): () -> Unit {
    val context = LocalContext.current
    // Android asks once for VPN consent; after that prepare() returns null.
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.connect()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        when (phase) {
            ConnectionPhase.Connected, ConnectionPhase.Connecting -> viewModel.disconnect()
            else -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                VpnService.prepare(context)?.let(consent::launch) ?: viewModel.connect()
            }
        }
    }
}

@Composable
fun HomeContent(
    state: VpnUiState,
    onToggle: () -> Unit,
    onAddProfile: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onPickServer: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        ProfileCard(state.activeProfile, onAddProfile, onOpenProfiles, Modifier.padding(12.dp))
        state.server?.let {
            // The pill is 28dp like the desktop trigger; Material's 48dp touch-target
            // inflation would otherwise add 10dp of air above and below it.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                ServerChip(it, onPickServer, Modifier.align(Alignment.End).padding(horizontal = 12.dp))
            }
        }
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Same rule as --connect-btn-size in the desktop's narrow window.
            val buttonSize = minOf(240.dp, maxWidth * 0.6f, maxHeight * 0.45f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // During a live server switch the button replays the connecting animation, like desktop.
                val switching = state.switch.phase == SwitchPhase.Switching
                ConnectButton(
                    phase = if (switching) ConnectionPhase.Connecting else state.phase,
                    onClick = onToggle,
                    size = buttonSize,
                )
                Spacer(Modifier.height(52.dp))
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
    }
}

// Mirrors the desktop Profiles card: m-3, rounded-xl, bg-card/50, no border,
// 14sp semibold name, 10sp muted subtitle, and a full-height "+" segment on the right.
@Composable
private fun ProfileCard(profile: Profile?, onAdd: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer.copy(alpha = 0.7f),
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        Box {
            ProfileArt(profile?.branding?.bannerImage, Modifier.matchParentSize())
        Row {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Открыть профили", onClick = onOpen)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileIcon(profile?.branding?.iconUrl, 16.dp)
                    Text(
                        profile?.name ?: "Нет профиля",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (profile?.unlimited == true) {
                        Icon(BeamIcons.Infinity, null, Modifier.size(12.dp), tint = colors.onSurfaceVariant)
                    }
                    Text(
                        when {
                            profile == null -> "Добавить профиль"
                            profile.unlimited -> "Безлимитный"
                            else -> ""
                        },
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            profile?.branding?.communityUrl?.let { url ->
                Box(
                    Modifier.fillMaxHeight().clickable(onClickLabel = "Сообщество") { context.openLink(url) }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(BeamIcons.Send, contentDescription = "Сообщество", Modifier.size(16.dp)) }
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .clickable(onClickLabel = "Добавить профиль", onClick = onAdd)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BeamIcons.Plus, contentDescription = "Добавить профиль", Modifier.size(16.dp))
            }
        }
        }
    }
}

// Mirrors the desktop NodeSelector trigger: a white/5% pill with a round flag,
// 12sp medium name and a dim chevron.
@Composable
internal fun ServerChip(server: ServerSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = BeamTheme.extra.tint.copy(alpha = 0.05f),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CountryFlag(server.countryCode, 16.dp)
            Text(
                server.name,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.025).em,
                color = colors.onSurface.copy(alpha = 0.9f),
                maxLines = 1,
            )
            Icon(BeamIcons.ChevronDown, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
internal fun StatusText(state: VpnUiState, switchPhase: SwitchPhase = SwitchPhase.Idle) {
    val colors = MaterialTheme.colorScheme
    val text = when {
        state.phase == ConnectionPhase.Error -> "Ошибка подключения"
        state.phase == ConnectionPhase.Connecting -> "Подключение..."
        state.phase == ConnectionPhase.Connected && state.reconnecting -> "Переподключение..."
        state.phase == ConnectionPhase.Connected && !state.healthy -> "Проблемы с соединением"
        state.phase == ConnectionPhase.Connected -> "Вы подключены!"
        state.profile == null -> "Добавьте профиль"
        else -> "Нажмите для подключения"
    }
    val color by animateColorAsState(
        when {
            state.phase == ConnectionPhase.Error -> colors.error
            state.phase == ConnectionPhase.Connected && state.reconnecting -> colors.onSurfaceVariant
            state.phase == ConnectionPhase.Connected && !state.healthy -> BeamTheme.extra.warning
            // A breath of the accent in the text, like the desktop status.
            state.phase == ConnectionPhase.Connected -> lerp(colors.onSurface, colors.primary, 0.15f)
            else -> colors.onSurface
        },
        tween(300),
        label = "statusColor",
    )
    val style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
    if (state.phase == ConnectionPhase.Connected && state.healthy && !state.reconnecting) {
        DecryptText(text, color, Modifier, style, replay = switchPhase)
    } else {
        Text(text, color = color, style = style, textAlign = TextAlign.Center)
    }
}

/** Letters resolve one by one out of random glyphs, like the desktop's EncryptedText. */
@Composable
private fun DecryptText(text: String, color: Color, modifier: Modifier, style: androidx.compose.ui.text.TextStyle, replay: Any? = null) {
    // Saveable, so the reveal plays once: the compact home's hero is a lazy item, and scrolling it out and
    // back recomposes it from scratch, which with a plain remember replayed the whole animation.
    var revealed by rememberSaveable(text, replay) { mutableIntStateOf(0) }
    LaunchedEffect(text, replay) {
        while (revealed < text.length) {
            delay(40)
            revealed++
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = color)) { append(text.take(revealed)) }
            withStyle(SpanStyle(color = muted)) {
                text.drop(revealed).forEach { append(if (it == ' ') ' ' else GLYPHS.random()) }
            }
        },
        modifier = modifier,
        style = style,
        textAlign = TextAlign.Center,
    )
}

private const val GLYPHS = "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЭЮЯ0123456789#%&*"

@Composable
internal fun ErrorNote(message: String) {
    Row(
        Modifier
            .padding(top = 12.dp)
            .widthIn(max = 280.dp)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BeamIcons.AlertCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
    }
}

@Preview(widthDp = 380, heightDp = 720)
@Composable
private fun HomePreview() {
    BeamTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            HomeContent(
                state = VpnUiState(
                    phase = ConnectionPhase.Connected,
                    profiles = listOf(Profile("p", "Демо-профиль", isActive = true)),
                    nodes = mapOf("p" to ProfileNodes(listOf(NodeGroup("g", "", listOf(Node("fi", "🇫🇮 Финляндия")), ungrouped = true)), "fi")),
                ),
                onToggle = {},
            )
        }
    }
}
