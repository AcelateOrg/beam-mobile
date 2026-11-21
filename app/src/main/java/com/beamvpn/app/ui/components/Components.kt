package com.beamvpn.app.ui.components

import com.beamvpn.app.ui.theme.scaled
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme

/**
 * Round country flag. Desktop renders flag-icons SVGs (react-country-flag) cropped
 * to a circle with a 1px white/15% inner ring; the same SVG set ships in assets/flags.
 */
@Composable
fun CountryFlag(countryCode: String?, size: Dp, modifier: Modifier = Modifier, ring: Boolean = true) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (countryCode != null) {
            AsyncImage(
                model = "file:///android_asset/flags/${countryCode.lowercase()}.svg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (ring) Box(Modifier.fillMaxSize().border(1.dp, BeamTheme.extra.tint.copy(alpha = 0.15f), CircleShape))
        } else {
            Icon(BeamIcons.Servers, null, Modifier.size(size * 0.45f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** shadcn Badge: h-5 pill, px-2, medium text. [secondary] = bg-secondary, otherwise bg-primary. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, secondary: Boolean = true, fontSize: TextUnit = 10.sp) {
    val extra = BeamTheme.extra
    Box(
        modifier
            .height(20.dp)
            .background(if (secondary) extra.accentFill else extra.highlight, RoundedCornerShape(50))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Medium,
            color = if (secondary) MaterialTheme.colorScheme.onSurface else extra.onHighlight,
            maxLines = 1,
        )
    }
}

/** Desktop PageHeader: px-4 py-3, 16dp muted icon, 18sp semibold title, actions on the right, border-b/50. */
@Composable
fun PageHeader(
    icon: ImageVector,
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = if (onBack != null) 8.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(scaled(10.dp))).clickable(onClickLabel = "Назад", onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(BeamIcons.ArrowLeft, contentDescription = "Назад", Modifier.size(18.dp)) }
            }
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
        HorizontalDivider(color = BeamTheme.extra.border.let { it.copy(alpha = it.alpha * 0.05f) })
    }
}
