package kr.co.cotton.vlrgg_mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.saira_condensed_bold
import vlrggmobile.app.shared.generated.resources.saira_condensed_semibold

/**
 * v3 type roles (`DESIGN.md` section 5).
 *
 * Text roles use the platform sans-serif. Numeric roles (`score*`, `stat`, `tag`) use the bundled
 * Saira Condensed. Roles with a weight range carry the lightest weight; screens raise it with
 * `copy(fontWeight = ...)`.
 */
data class VlrTypeScale(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val article: TextStyle,
    val bodySm: TextStyle,
    val meta: TextStyle,
    val label: TextStyle,
    val scoreXl: TextStyle,
    val scoreL: TextStyle,
    val scoreM: TextStyle,
    val stat: TextStyle,
    val tag: TextStyle,
)

fun vlrTypeScale(numericFontFamily: FontFamily): VlrTypeScale = VlrTypeScale(
    display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        lineHeight = 31.sp,
    ),
    headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
    ),
    section = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    article = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 27.sp,
    ),
    bodySm = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    meta = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    scoreXl = TextStyle(
        fontFamily = numericFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
    ),
    scoreL = TextStyle(
        fontFamily = numericFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
    ),
    scoreM = TextStyle(
        fontFamily = numericFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 22.sp,
    ),
    stat = TextStyle(
        fontFamily = numericFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    tag = TextStyle(
        fontFamily = numericFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    ),
)

/** Compose resource fonts load only in composition, so the scale is built inside [VlrTheme]. */
@Composable
fun rememberVlrTypography(): VlrTypeScale {
    val sairaCondensed = FontFamily(
        Font(Res.font.saira_condensed_semibold, FontWeight.SemiBold),
        Font(Res.font.saira_condensed_bold, FontWeight.Bold),
    )
    return remember(sairaCondensed) { vlrTypeScale(sairaCondensed) }
}

fun vlrMaterialTypography(scale: VlrTypeScale): Typography = Typography(
    displayLarge = scale.display,
    headlineLarge = scale.title,
    headlineMedium = scale.headline,
    titleMedium = scale.section,
    bodyLarge = scale.bodySm,
    bodyMedium = scale.bodySm,
    labelLarge = scale.meta,
    labelMedium = scale.label,
    labelSmall = scale.label,
)
