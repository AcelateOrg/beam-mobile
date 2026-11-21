package com.beamvpn.app.ui.components

import com.beamvpn.app.ui.theme.scaled
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.beamvpn.app.ui.icons.BeamIcons
import com.beamvpn.app.ui.theme.BeamTheme
import com.beamvpn.app.vpn.Profile

/**
 * Touch target and step of the icon buttons on a profile card's right edge. The card header and the
 * announcement inside it share them, so both button groups line up on one vertical axis.
 */
val CardActionSize = 40.dp
val CardActionGap = 2.dp

/** Opens a link from provider branding in the browser or the app that owns it (Telegram, ...). */
fun Context.openLink(url: String) {
    val uri = Uri.parse(if ("://" in url) url else "https://$url")
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * A dismissed announcement stays hidden per profile and text, like desktop's
 * `beam:announce-hidden:`: a changed text shows up again.
 */
private object AnnounceStore {
    private fun prefs(context: Context) = context.getSharedPreferences("beam_announce", Context.MODE_PRIVATE)

    fun hidden(context: Context, profileId: String): String? = prefs(context).getString(profileId, null)

    fun hide(context: Context, profileId: String, hash: String) {
        prefs(context).edit().putString(profileId, hash).apply()
    }
}

private fun textHash(s: String): String {
    var h = 5381
    for (c in s) h = (h shl 5) + h + c.code
    return (h.toLong() and 0xFFFFFFFFL).toString(36)
}

private val LinkPattern = Regex("""(https?://\S+|t\.me/\S+|@\w{3,})""")

/** Plain text with links, t.me/... and @handles made tappable. */
private fun announceText(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    val linkStyle = TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline))
    for (m in LinkPattern.findAll(text)) {
        append(text.substring(last, m.range.first))
        val raw = m.value.trimEnd('.', ',', ')', '!', ';')
        val url = when {
            raw.startsWith("@") -> "https://t.me/${raw.drop(1)}"
            raw.startsWith("http") -> raw
            else -> "https://$raw"
        }
        withLink(LinkAnnotation.Url(url, linkStyle)) { append(raw) }
        last = m.range.first + raw.length
    }
    append(text.substring(last))
}

private fun parseAccent(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/**
 * Provider announcement, port of desktop's AnnounceBanner: the text clamps to two lines and expands,
 * links are tappable, the text opens branding.bannerUrl when the provider set one, the support button
 * opens branding.supportUrl, and the cross hides this text for this profile for good.
 * Without [framed] it drops its own box and icon tile, to sit inside a card that already frames it.
 */
@Composable
fun AnnounceBanner(profile: Profile, modifier: Modifier = Modifier, framed: Boolean = true) {
    val text = profile.announce?.trim().orEmpty()
    if (text.isEmpty()) return
    val context = LocalContext.current
    val hash = remember(text) { textHash(text) }
    var hiddenHash by remember(profile.id) { mutableStateOf(AnnounceStore.hidden(context, profile.id)) }
    if (hiddenHash == hash) return

    val extra = BeamTheme.extra
    val branding = profile.branding
    val accent = parseAccent(branding.bannerColor) ?: extra.highlight
    var expanded by rememberSaveable(profile.id, hash) { mutableStateOf(false) }
    var overflows by remember(hash) { mutableStateOf(false) }
    val shape = RoundedCornerShape(scaled(16.dp))
    val canToggle = overflows || expanded
    val bannerUrl = branding.bannerUrl

    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (framed) {
                    Modifier
                        .clip(shape)
                        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.12f), accent.copy(alpha = 0.03f))))
                        .border(1.dp, accent.copy(alpha = 0.3f), shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(if (framed) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (framed) {
            Box(
                Modifier.size(28.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(scaled(12.dp))),
                contentAlignment = Alignment.Center,
            ) { Icon(BeamIcons.Info, null, Modifier.size(14.dp), tint = accent) }
        }
        Text(
            announceText(text, accent),
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = bannerUrl != null || canToggle) {
                    if (bannerUrl != null) context.openLink(bannerUrl) else expanded = !expanded
                },
        )
        // Unframed it sits under the profile card's header, so it takes the header's button size and step:
        // both groups end on one vertical axis, the banner's chevron under the header's, the cross under the menu.
        val buttonSize = if (framed) 28.dp else CardActionSize
        Row(horizontalArrangement = Arrangement.spacedBy(if (framed) 0.dp else CardActionGap)) {
            branding.supportUrl?.let { url ->
                val telegram = "t.me" in url || "telegram" in url
                BannerButton(if (telegram) BeamIcons.Send else BeamIcons.ExternalLink, "Поддержка", buttonSize) { context.openLink(url) }
            }
            if (canToggle) {
                BannerButton(BeamIcons.ChevronDown, "Развернуть", buttonSize, Modifier.rotate(if (expanded) 180f else 0f)) { expanded = !expanded }
            }
            BannerButton(BeamIcons.Close, "Скрыть", buttonSize) {
                AnnounceStore.hide(context, profile.id, hash)
                hiddenHash = hash
            }
        }
    }
}

@Composable
private fun BannerButton(icon: ImageVector, label: String, size: Dp, iconModifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(scaled(12.dp))).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, iconModifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

/**
 * Provider art behind a profile card (see beam-bridge/docs/banner-guide.md): fitted to the card
 * height, pinned right, the left part fading out under the text. Place it first inside the card.
 * [fadeBottom] also fades it out downwards, for a card taller than the art.
 */
@Composable
fun ProfileArt(url: String?, modifier: Modifier = Modifier, fadeBottom: Boolean = false) {
    if (url.isNullOrBlank()) return
    val card = MaterialTheme.colorScheme.surfaceContainer
    Box(
        if (!fadeBottom) modifier else modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), blendMode = BlendMode.DstIn)
            },
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            alignment = Alignment.CenterEnd,
            contentScale = ContentScale.FillHeight,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(listOf(card.copy(alpha = 0.92f), card.copy(alpha = 0.55f), Color.Transparent)),
            ),
        )
    }
}

/** Provider icon in front of a profile name; nothing is drawn without one or when it fails to load. */
@Composable
fun ProfileIcon(url: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(size / 4)),
    )
}
