package com.beamvpn.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import com.beamvpn.app.settings.AnimationOption
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.VpnService
import androidx.compose.ui.platform.LocalContext
import com.beamvpn.app.settings.SettingsStore
import com.beamvpn.app.ui.onboarding.Onboarding
import com.beamvpn.app.vpn.ConnectionPhase
import com.beamvpn.app.ui.home.BeamViewModel
import com.beamvpn.app.ui.home.CompactHomeScreen
import com.beamvpn.app.ui.home.HomeScreen
import com.beamvpn.app.ui.profiles.AddProfileSheet
import com.beamvpn.app.ui.profiles.ProfilesScreen
import com.beamvpn.app.ui.servers.ServersScreen
import com.beamvpn.app.ui.settings.SettingsScreen
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme

enum class BeamTab(val label: String, val icon: ImageVector) {
    Home("Главная", BeamIcons.Home),
    Profiles("Профили", BeamIcons.Profiles),
    Servers("Серверы", BeamIcons.Servers),
    Settings("Настройки", BeamIcons.Settings),
}

private val CompactTabs = listOf(BeamTab.Home, BeamTab.Settings)

/**
 * App shell, after the desktop's narrow-window layout: the status bar and the
 * bottom bar sit on one shell colour and the page is a rounded panel floating
 * between them, so the top of the screen blends into the panel's edge.
 */
@Composable
fun BeamApp(sharedLink: String? = null, onSharedLinkHandled: () -> Unit = {}) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }
    val viewModel: BeamViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(BeamTab.Home) }
    // Tapping the tab that is already open takes it back to its first screen (Settings' list from a section).
    var reselect by rememberSaveable { mutableStateOf(0) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var prefill by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(sharedLink) {
        if (sharedLink != null) {
            val link = sharedLink
            onSharedLinkHandled()
            val err = viewModel.importProfile(link)
            if (err != null) {
                prefill = unwrapBeamImportUrl(link)
                adding = true
                snackbar.showSnackbar(err)
            }
        }
    }
    val select: (BeamTab) -> Unit = {
        if (it == tab) reselect++
        tab = it
    }
    val navBack = remember { com.beamvpn.app.ui.components.NavBack() }
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.settings.collectAsStateWithLifecycle()

    // "Подключаться при запуске": once per launch, when the profile store is loaded and consent is already granted.
    var launchConnectDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.ready, state.activeProfile?.id, settings.onboardingDone) {
        if (launchConnectDone || !state.ready || !settings.onboardingDone) return@LaunchedEffect
        if (state.activeProfile == null) return@LaunchedEffect
        launchConnectDone = true
        if (settings.connectOnLaunch && state.phase == ConnectionPhase.Disconnected && VpnService.prepare(context) == null) {
            viewModel.connect()
        }
    }

    // Compact mode folds Profiles and Servers into the home screen; their tabs leave the bar.
    val tabs = if (settings.compactMode) CompactTabs else BeamTab.entries
    LaunchedEffect(settings.compactMode) {
        if (tab !in tabs) tab = BeamTab.Home
    }

    // Bridge errors (failed import, unreachable subscription, ...) surface as a snackbar.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(com.beamvpn.app.ui.components.LocalNavBack provides navBack) {
    Box(Modifier.fillMaxSize()) {
    // After the onboarding the app rises in under the fading wizard instead of popping in on one frame.
    // When the app starts with the onboarding already done the target is reached at once and nothing animates.
    val reveal by androidx.compose.animation.core.animateFloatAsState(
        if (settings.onboardingDone) 1f else 0f,
        androidx.compose.animation.core.tween(700, delayMillis = 150, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "appReveal",
    )
    Scaffold(
        modifier = Modifier.graphicsLayer {
            alpha = reveal
            val scale = 0.97f + 0.03f * reveal
            scaleX = scale
            scaleY = scale
        },
        // Islands: no frame around the page and no band under the bar, the page runs edge to edge under it.
        containerColor = if (settings.floatingNav) MaterialTheme.colorScheme.background else BeamTheme.extra.shell,
        bottomBar = {
            if (!settings.floatingNav) BeamNavigationBar(tabs, tab, onSelect = select)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
    // Islands float over the page: it runs to the bottom edge and scrolls under them.
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    val floating = settings.floatingNav
    Box(Modifier.fillMaxSize()) {
    androidx.compose.runtime.CompositionLocalProvider(com.beamvpn.app.ui.components.LocalBottomInset provides if (floating) barHeight else 0.dp) {
        ContentPanel(
            if (floating) Modifier.padding(top = padding.calculateTopPadding()) else Modifier.padding(padding),
            framed = !floating,
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // Direction follows tab order for the sliding styles, like desktop's page transitions.
                    val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    when (settings.animation) {
                        AnimationOption.Slide ->
                            (fadeIn(tween(220, delayMillis = 40)) + slideInHorizontally(tween(280)) { it / 12 * dir }) togetherWith
                                fadeOut(tween(90))
                        AnimationOption.SlideUp ->
                            (fadeIn(tween(240, delayMillis = 40)) + slideInVertically(tween(280)) { it / 48 }) togetherWith
                                fadeOut(tween(90))
                        AnimationOption.Fade -> fadeIn(tween(220)) togetherWith fadeOut(tween(90))
                        AnimationOption.None -> EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "page",
            ) { current ->
                when (current) {
                    BeamTab.Home ->
                        if (settings.compactMode) CompactHomeScreen(viewModel, onAddProfile = { adding = true })
                        // The home screen does not scroll: it keeps clear of the islands instead.
                        else Box(Modifier.fillMaxSize().then(with(com.beamvpn.app.ui.components.LocalBottomInset.current) { Modifier.padding(bottom = this) })) {
                            HomeScreen(viewModel, onAddProfile = { adding = true }, onOpenProfiles = { tab = BeamTab.Profiles }, onPickServer = { tab = BeamTab.Servers })
                        }
                    BeamTab.Profiles -> ProfilesScreen(viewModel, onAdd = { adding = true })
                    BeamTab.Servers -> ServersScreen(viewModel)
                    BeamTab.Settings -> SettingsScreen(reselect = reselect)
                }
            }
        }
    }
        if (floating) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
            ) { FloatingNavBar(tabs, tab, select, onBack = navBack.current) }
        }
    }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = !settings.onboardingDone,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(550)),
    ) {
        Onboarding(
            settings = settings,
            store = store,
            hasProfile = state.profiles.isNotEmpty(),
            onAddProfile = { adding = true },
            onFinish = { store.update { it.copy(onboardingDone = true) } },
        )
    }
    }
    }

    if (adding) {
        AddProfileSheet(
            initialText = prefill,
            onDismiss = { adding = false; prefill = "" },
            importText = viewModel::importProfile,
            importFile = viewModel::importBeam,
        )
    }
}

private fun unwrapBeamImportUrl(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("beam:", ignoreCase = true)) return trimmed
    return runCatching {
        val uri = android.net.Uri.parse(trimmed)
        val queryUrl = uri.getQueryParameter("url")?.trim()
        if (!queryUrl.isNullOrEmpty()) return@runCatching queryUrl
        val host = uri.host?.lowercase()
        if (host == "import" || host == "add") {
            val path = uri.encodedPath?.trimStart('/') ?: ""
            if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
                val q = uri.encodedQuery
                return@runCatching if (!q.isNullOrEmpty()) "$path?$q" else path
            }
        }
        trimmed
    }.getOrDefault(trimmed)
}

@Composable
private fun ContentPanel(modifier: Modifier = Modifier, framed: Boolean = true, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .fillMaxSize()
            .then(if (framed) Modifier.padding(horizontal = 8.dp).clip(shape) else Modifier)
            .background(MaterialTheme.colorScheme.background),
    ) { content() }
}

@Composable
private fun BeamNavigationBar(tabs: List<BeamTab>, selected: BeamTab, onSelect: (BeamTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null, Modifier.size(18.dp)) },
                label = {
                    Text(tab.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onSurface,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.secondaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}
