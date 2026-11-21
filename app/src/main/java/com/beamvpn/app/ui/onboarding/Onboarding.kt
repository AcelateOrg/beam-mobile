package com.beamvpn.app.ui.onboarding

import com.beamvpn.app.ui.theme.scaled
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamvpn.app.R
import com.beamvpn.app.settings.AppSettings
import com.beamvpn.app.settings.SettingsStore
import com.beamvpn.app.ui.components.AnimatedBeamLogo
import com.beamvpn.app.ui.components.BeamLogo
import com.beamvpn.app.ui.settings.LayoutPicker
import com.beamvpn.app.ui.settings.RowDivider
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.settings.SettingsCard
import com.beamvpn.app.ui.settings.SwitchRow
import com.beamvpn.app.ui.settings.ThemeGrid
import com.beamvpn.app.ui.theme.BeamTheme

private enum class Step(val title: String, val text: String) {
    Welcome("Добро пожаловать в BEAM", "Настроим приложение перед первым подключением. Это займёт меньше минуты."),
    Look("Внешний вид", "Тема и устройство главного экрана. Всё это можно поменять потом в настройках."),
    Profile("Добавьте профиль", "Подключите подписку, ключ, QR-код или BEAM-файл. Без профиля подключаться не к чему."),
    Behavior("Последние настройки", "Как приложению вести себя каждый день."),
}

/**
 * First-run wizard, a phone cut of beam-desktop's Onboarding page: welcome, look (theme and layout), profile, behaviour.
 * [onAddProfile] opens the shared add sheet; [hasProfile] flips the profile step to its done state.
 */
@Composable
fun Onboarding(
    settings: AppSettings,
    store: SettingsStore,
    hasProfile: Boolean,
    onAddProfile: () -> Unit,
    onFinish: () -> Unit,
) {
    val steps = Step.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    var forward by rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    val step = steps[index]
    val last = index == steps.lastIndex

    fun go(delta: Int) {
        forward = delta > 0
        index = (index + delta).coerceIn(0, steps.lastIndex)
    }
    BackHandler(enabled = index > 0) { go(-1) }

    // "Начать" plays desktop's finale: the wizard fades out, the wordmark draws itself and fills, then the app opens.
    var finishing by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val wizardAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (finishing) 0f else 1f,
        androidx.compose.animation.core.tween(520),
        label = "wizardExit",
    )

    // Outside a Surface the default content colour is black: set it so titles are readable on the dark shell.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxSize().background(BeamTheme.extra.shell)) {
        Column(
            Modifier
                .fillMaxSize()
                .alpha(wizardAlpha)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (!last) TextButton(onClick = onFinish) { Text("Пропустить", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(280)) { it / 10 * dir }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { -it / 10 * dir })
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "step",
            ) { current ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (current) {
                        // The wordmark stands still here; it draws itself only in the finale.
                        Step.Welcome -> BeamLogo(Modifier.width(240.dp).padding(bottom = 28.dp))
                        Step.Look -> StepIcon(BeamIcons.Palette)
                        Step.Profile -> StepIcon(BeamIcons.Profiles)
                        Step.Behavior -> StepIcon(BeamIcons.Sliders)
                    }
                    Text(current.title, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text(
                        current.text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                    when (current) {
                        Step.Welcome -> Unit
                        Step.Look -> LookChoice(settings, store)
                        Step.Profile -> ProfileChoice(hasProfile, onAddProfile)
                        Step.Behavior -> BehaviorChoice(settings, store)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (index > 0) TextButton(onClick = { go(-1) }) { Text("Назад", fontSize = 14.sp) }
                }
                Dots(count = steps.size, selected = index)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val extra = BeamTheme.extra
                    Button(
                        onClick = { if (last) finishing = true else go(1) },
                        shape = RoundedCornerShape(scaled(14.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = extra.highlight, contentColor = extra.onHighlight),
                        modifier = Modifier.height(44.dp),
                    ) {
                        Text(
                            when {
                                last -> "Начать"
                                step == Step.Profile && !hasProfile -> "Позже"
                                else -> "Далее"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        if (finishing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AnimatedBeamLogo(Modifier.width(260.dp), startDelayMs = 740) {
                    onFinish()
                }
            }
        }
    }
    }
}

@Composable
private fun StepIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.padding(bottom = 20.dp).size(64.dp).background(BeamTheme.extra.highlight.copy(alpha = 0.1f), RoundedCornerShape(scaled(22.dp))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(28.dp), tint = BeamTheme.extra.highlight) }
}

@Composable
private fun Dots(count: Int, selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == selected) 20.dp else 6.dp, tween(220), label = "dotWidth")
            val color by animateColorAsState(
                if (i == selected) BeamTheme.extra.highlight else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                tween(220),
                label = "dotColor",
            )
            Box(Modifier.height(6.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

/** Theme and home layout on one screen, so the wizard stays four steps long. */
@Composable
private fun LookChoice(settings: AppSettings, store: SettingsStore) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceLabel("Вид главного экрана")
        LayoutPicker(settings, store, Modifier.fillMaxWidth())
        ChoiceLabel("Тема")
        ThemeGrid(settings, store, limit = 8)
    }
}

@Composable
private fun ChoiceLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun ProfileChoice(hasProfile: Boolean, onAdd: () -> Unit) {
    val extra = BeamTheme.extra
    if (hasProfile) {
        Row(
            Modifier
                .background(extra.pingGood.copy(alpha = 0.1f), RoundedCornerShape(scaled(14.dp)))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(BeamIcons.Check, null, Modifier.size(16.dp), tint = extra.pingGood)
            Text("Профиль добавлен", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        TextButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) { Text("Добавить ещё", fontSize = 13.sp) }
    } else {
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(scaled(14.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = extra.accentFill, contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.height(48.dp),
        ) {
            Icon(BeamIcons.Plus, null, Modifier.size(16.dp))
            Text("Добавить профиль", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun BehaviorChoice(settings: AppSettings, store: SettingsStore) {
    SettingsCard(BeamIcons.Smartphone, "Поведение", "Запуск и работа клиента на телефоне") {
        SwitchRow(BeamIcons.Power, "Подключаться при запуске", "Сразу подключаться к последнему серверу при открытии.", settings.connectOnLaunch) { v ->
            store.update { it.copy(connectOnLaunch = v) }
        }
        RowDivider()
        SwitchRow(BeamIcons.Wifi, "Автопереподключение", "Восстанавливать соединение, если оно пропало или сменилась сеть.", settings.autoReconnect) { v ->
            store.update { it.copy(autoReconnect = v) }
        }
        RowDivider()
        SwitchRow(BeamIcons.Refresh, "Автообновление подписок", "Периодически подтягивать новые серверы по ссылкам.", settings.autoUpdateSubscriptions) { v ->
            store.update { it.copy(autoUpdateSubscriptions = v) }
        }
        RowDivider()
        SwitchRow(BeamIcons.Star, "Облегчённые эффекты", "Без размытия и частиц, если телефон слабый.", settings.reducedEffects) { v ->
            store.update { it.copy(reducedEffects = v) }
        }
    }
}
