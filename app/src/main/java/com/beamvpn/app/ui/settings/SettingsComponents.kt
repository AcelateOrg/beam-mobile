package com.beamvpn.app.ui.settings

import com.beamvpn.app.ui.theme.scaled
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.sp
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme

/**
 * Desktop SettingsCard (components/ui/settings-card.tsx): bg-sidebar card with a
 * tinted header (icon chip, title, description) and rows underneath. On the phone
 * the rows are full-width touch targets of at least 64dp.
 */
/** Corners of a Pixel-style grouped list: round at the ends of a group, tight between its rows. */
val GroupOuter = 28.dp
val GroupInner = 6.dp

/** The shape of row [index] of [count] in a group, as Android's own Settings draws it. */
fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) GroupOuter else GroupInner
    val bottom = if (index == count - 1) GroupOuter else GroupInner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * A titled group of settings as Expressive grouped tiles: the familiar header (icon, title, description) on its own
 * tile, the rows on a second one right under it, the pair rounded as one group with tight corners where they meet.
 */
@Composable
fun SettingsCard(
    icon: ImageVector,
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = BeamTheme.extra
    val tile = MaterialTheme.colorScheme.surfaceContainer
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(groupShape(0, 2))
                .background(tile)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.padding(top = 2.dp).size(30.dp).background(extra.highlight.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(14.dp), tint = extra.highlight) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                if (description != null) {
                    Text(description, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(groupShape(1, 2))
                .background(tile),
            content = content,
        )
    }
}

/** One row: icon tile, title and description, and an optional trailing control. The whole row is tappable. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val alpha = if (enabled) 1f else 0.45f
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(scaled(8.dp))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(16.dp), tint = BeamTheme.extra.highlight.copy(alpha = alpha)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (description != null) {
                Text(description, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(icon, title, description, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            // Expressive switch: a check on the thumb while on.
            thumbContent = if (checked) {
                { Icon(BeamIcons.Check, null, Modifier.size(SwitchDefaults.IconSize), tint = BeamTheme.extra.highlight) }
            } else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BeamTheme.extra.onHighlight,
                checkedTrackColor = BeamTheme.extra.highlight,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = BeamTheme.extra.border,
            ),
        )
    }
}

@Composable
fun RowDivider() = HorizontalDivider(Modifier.padding(start = 60.dp), color = BeamTheme.extra.border.let { it.copy(alpha = it.alpha * 0.05f) })

/** A choice of a few values: an Expressive connected button group (see [ToggleGroup]). */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) =
    ToggleGroup(options.map { it to label(it) }, selected, fill = false, onSelect = onSelect)

/**
 * Material 3 Expressive connected button group: the buttons sit side by side with tight inner corners; the chosen
 * one goes round and filled, and each one squeezes its shape while pressed. [fill] stretches it to the full width.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ToggleGroup(options: List<Pair<T, String>>, selected: T, fill: Boolean, onSelect: (T) -> Unit) {
    val extra = BeamTheme.extra
    val colors = androidx.compose.material3.ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = extra.highlight,
        checkedContentColor = extra.onHighlight,
    )
    Row(
        if (fill) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { i, (value, text) ->
            val shapes = when (i) {
                0 -> androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> androidx.compose.material3.ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            androidx.compose.material3.ToggleButton(
                checked = value == selected,
                onCheckedChange = { onSelect(value) },
                shapes = shapes,
                colors = colors,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (fill) 8.dp else 14.dp),
                modifier = (if (fill) Modifier.weight(1f) else Modifier).heightIn(min = 40.dp).semantics { role = androidx.compose.ui.semantics.Role.RadioButton },
            ) {
                Text(text, fontSize = 13.sp, maxLines = 1, fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Option tile used for DNS and fragmentation presets: label plus caption, outlined when chosen. */
@Composable
fun OptionTile(title: String, caption: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val extra = BeamTheme.extra
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(scaled(14.dp)),
        color = if (selected) extra.highlight.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, if (selected) extra.highlight.copy(alpha = 0.7f) else extra.border.let { it.copy(alpha = it.alpha * 0.06f) }),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (selected) Icon(BeamIcons.Check, null, Modifier.size(14.dp), tint = extra.highlight)
            }
            Text(caption, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Full-width page tabs (the Routing page's look): each a 48dp target, the chosen one tinted. */
@Composable
fun <T> PageTabs(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val extra = BeamTheme.extra
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(scaled(14.dp)))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, title) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(scaled(10.dp)))
                    .background(if (on) extra.highlight.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(onClickLabel = title) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(title, fontSize = 13.sp, maxLines = 1, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}
