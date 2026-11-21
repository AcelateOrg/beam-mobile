package com.beamvpn.app.ui.home

import com.beamvpn.app.ui.theme.BeamTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamvpn.app.ui.components.CountryFlag
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.vpn.ServerSummary
import com.beamvpn.app.vpn.ServerSwitch
import com.beamvpn.app.vpn.SwitchPhase

/**
 * "🇫🇮 ···→ 🇩🇪 Германия": where the live connection is switching from and to. Port of desktop's ServerHop:
 * shown while the core swaps and for a couple of seconds after, the bar runs while switching and fills when done.
 */
@Composable
fun ServerHop(switch: ServerSwitch, to: ServerSummary?, modifier: Modifier = Modifier) {
    val visible = switch.phase != SwitchPhase.Idle && to != null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
        exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
        modifier = modifier,
    ) {
        val done = switch.phase == SwitchPhase.Switched
        val primary = MaterialTheme.colorScheme.primary
        val flagScale by animateFloatAsState(if (done) 0.75f else 1f, tween(500), label = "fromScale")
        val flagAlpha by animateFloatAsState(if (done) 0.4f else 1f, tween(500), label = "fromAlpha")
        val ring by animateColorAsState(if (done) primary else Color.Transparent, tween(500), label = "ring")
        val checkWidth by animateDpAsState(if (done) 14.dp else 0.dp, tween(300), label = "checkWidth")
        val checkAlpha by animateFloatAsState(if (done) 1f else 0f, tween(300), label = "checkAlpha")

        Row(
            Modifier
                .padding(top = 12.dp)
                .clip(CircleShape)
                .background(BeamTheme.extra.tint.copy(alpha = 0.05f))
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            switch.from?.let { from ->
                CountryFlag(
                    from.countryCode,
                    20.dp,
                    Modifier.scale(flagScale).alpha(flagAlpha),
                )
                HopBar(done = done, color = primary)
            }
            CountryFlag(to!!.countryCode, 20.dp, Modifier.border(2.dp, ring, CircleShape), ring = false)
            Text(
                to.name,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                BeamIcons.Check,
                null,
                Modifier.size(checkWidth).alpha(checkAlpha),
                tint = primary,
            )
        }
    }
}

/** 32x4 track with a third-width segment running across while switching; full width once done. */
@Composable
private fun HopBar(done: Boolean, color: Color) {
    val infinite = rememberInfiniteTransition(label = "hop")
    val run by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "run",
    )
    val fill by animateFloatAsState(if (done) 1f else 0f, tween(300), label = "fill")
    val track = BeamTheme.extra.tint.copy(alpha = 0.08f)
    Canvas(Modifier.size(32.dp, 4.dp).clip(CircleShape)) {
        drawRoundRect(track, size = size, cornerRadius = CornerRadius(size.height / 2))
        val segment = size.width / 3f
        if (fill < 1f) {
            // translateX(-100%) -> translateX(300%) of the segment's own width.
            val x = -segment + run * (size.width + segment)
            drawRoundRect(
                color.copy(alpha = 1f - fill),
                topLeft = Offset(x, 0f),
                size = Size(segment, size.height),
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
        if (fill > 0f) {
            drawRoundRect(color, size = Size(size.width * fill, size.height), cornerRadius = CornerRadius(size.height / 2))
        }
    }
}
