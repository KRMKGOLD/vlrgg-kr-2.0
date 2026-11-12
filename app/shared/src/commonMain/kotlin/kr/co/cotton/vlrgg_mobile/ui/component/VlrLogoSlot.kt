package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_trophy

/** What a [LogoSlot] shows; decides the image scale, the shape and the placeholder. */
enum class LogoSlotKind {
    /** Team logo: Fit, square, tag or initials placeholder. */
    Team,

    /** Event or series logo: Fit, square, `trophy` placeholder. */
    Event,

    /** Player photo: Crop, circle, initials placeholder. */
    Player,
}

/** Light surfaces use `surface-sunken` with a 1dp `line`; the dark hero uses a plain `surface` slot. */
enum class LogoSlotSurface {
    Light,
    Dark,
}

/**
 * Image slot for team, event and player identity (`DESIGN.md` 8.3).
 *
 * The slot is decorative: images and placeholders are never announced, so rows and heroes name
 * the entity in their own text.
 */
@Composable
fun LogoSlot(
    imageUrl: String?,
    kind: LogoSlotKind,
    modifier: Modifier = Modifier,
    placeholderText: String? = null,
    size: Dp = LogoSlotDefaults.RowSize,
    surface: LogoSlotSurface = LogoSlotSurface.Light,
    imageTestTag: String? = null,
    placeholderTestTag: String? = null,
) {
    val colors = VlrTheme.colors
    val shape = logoSlotShape(kind)
    val url = imageUrl?.takeIf(String::isNotBlank)
    var imageFailed by remember(url) { mutableStateOf(false) }
    val container = when (surface) {
        LogoSlotSurface.Light -> Modifier
            .background(colors.surfaceSunken, shape)
            .border(VlrDimensions.OutlineWidth, colors.line, shape)
        LogoSlotSurface.Dark -> Modifier.background(colors.surface, shape)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(container),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null && !imageFailed) {
            val isPhoto = kind == LogoSlotKind.Player
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = if (isPhoto) ContentScale.Crop else ContentScale.Fit,
                onError = { imageFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isPhoto) Modifier else Modifier.padding(size / 10))
                    .then(imageTestTag?.let(Modifier::testTag) ?: Modifier),
            )
        } else {
            LogoSlotPlaceholder(
                kind = kind,
                text = placeholderText,
                size = size,
                modifier = placeholderTestTag?.let(Modifier::testTag) ?: Modifier,
            )
        }
    }
}

@Composable
private fun LogoSlotPlaceholder(
    kind: LogoSlotKind,
    text: String?,
    size: Dp,
    modifier: Modifier,
) {
    val large = size > LogoSlotDefaults.TileSize
    if (kind == LogoSlotKind.Event) {
        Icon(
            imageVector = vectorResource(Res.drawable.ic_trophy),
            contentDescription = null,
            modifier = modifier.size(if (large) 32.dp else 20.dp),
            tint = VlrTheme.colors.muted,
        )
        return
    }
    Text(
        text = text?.trim().orEmpty(),
        modifier = modifier.semantics { hideFromAccessibility() },
        style = VlrTheme.typography.tag.copy(
            fontSize = when {
                large -> 22.sp
                size == LogoSlotDefaults.TileSize -> 16.sp
                else -> 14.sp
            },
        ),
        color = VlrTheme.colors.muted,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

internal fun logoSlotShape(kind: LogoSlotKind): Shape =
    if (kind == LogoSlotKind.Player) CircleShape else RoundedCornerShape(VlrDimensions.CornerRadius)

/**
 * Placeholder text for a missing image: the first letters of up to two words, or the first two
 * letters of a single word (`Rb` → `RB`).
 */
fun logoInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    val letters = when {
        words.size >= 2 -> words.take(2).mapNotNull { it.firstOrNull() }.joinToString("")
        else -> words.firstOrNull()?.take(2).orEmpty()
    }
    return letters.uppercase()
}

object LogoSlotDefaults {
    /** Identity row slot. */
    val RowSize: Dp = 40.dp

    /** Favorite tile slot; larger slots use the hero placeholder sizes. */
    val TileSize: Dp = 48.dp

    /** Entity hero slot. */
    val HeroSize: Dp = 72.dp
}

@Preview(showBackground = true)
@Composable
private fun LogoSlotPreview() {
    VlrTheme {
        Column {
            Row {
                LogoSlot(imageUrl = null, kind = LogoSlotKind.Team, placeholderText = "DRX")
                LogoSlot(imageUrl = null, kind = LogoSlotKind.Event)
                LogoSlot(imageUrl = null, kind = LogoSlotKind.Player, placeholderText = "RB")
            }
            Row(Modifier.background(VlrTheme.colors.panel).padding(8.dp)) {
                LogoSlot(
                    imageUrl = null,
                    kind = LogoSlotKind.Team,
                    placeholderText = "DRX",
                    size = LogoSlotDefaults.HeroSize,
                    surface = LogoSlotSurface.Dark,
                )
            }
        }
    }
}
