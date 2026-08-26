package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error as semanticsError
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrTypography
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions

enum class VlrSearchFieldVariant(
    val visualHeight: Dp,
    internal val isPill: Boolean,
) {
    Standard(visualHeight = 56.dp, isPill = true),
    Compact(visualHeight = 40.dp, isPill = false),
}

internal data class VlrSearchFieldState(
    val value: String,
    val enabled: Boolean,
    val isLoading: Boolean,
    val errorMessage: String?,
) {
    val hasClearAction: Boolean = enabled && value.isNotEmpty() && !isLoading
    val stateDescription: String? = if (isLoading) "로딩 중" else null
    val imeAction: ImeAction = ImeAction.Search
    val minimumTouchTarget: Dp = VlrDimensions.MinimumTouchTarget
}

internal data class VlrSearchFieldOutline(
    val color: androidx.compose.ui.graphics.Color,
    val width: Dp,
    val isOutsideVisualBounds: Boolean,
)

internal fun searchFieldOutline(
    isFocused: Boolean,
    errorMessage: String?,
    colors: kr.co.cotton.vlrgg_mobile.ui.theme.VlrColors,
): VlrSearchFieldOutline {
    val hasError = !errorMessage.isNullOrBlank()
    return VlrSearchFieldOutline(
        color = when {
            hasError -> colors.actionPrimary
            isFocused -> colors.focusOutline
            else -> colors.outline
        },
        width = if (hasError || isFocused) {
            VlrDimensions.FocusOutlineWidth
        } else {
            VlrDimensions.OutlineWidth
        },
        isOutsideVisualBounds = hasError || isFocused,
    )
}

internal fun submitSearch(value: String, onSearch: (String) -> Unit) {
    onSearch(value)
}

internal fun clearSearch(onValueChange: (String) -> Unit) {
    onValueChange("")
}

/**
 * Platform-neutral, state-hoisted search input. It deliberately does not own navigation or search execution.
 */
@Composable
fun VlrSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: VlrSearchFieldVariant = VlrSearchFieldVariant.Standard,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    placeholder: String = "팀, 선수, 대회 검색…",
    label: String = placeholder,
    focusRequester: FocusRequester? = null,
    onSearch: (String) -> Unit = {},
) {
    val colors = LocalVlrColors.current
    val typography = LocalVlrTypography.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val state = VlrSearchFieldState(value, enabled, isLoading, errorMessage)
    val hasError = !state.errorMessage.isNullOrBlank()
    val shape = when (variant) {
        VlrSearchFieldVariant.Standard -> androidx.compose.foundation.shape.CircleShape
        VlrSearchFieldVariant.Compact -> androidx.compose.foundation.shape.RoundedCornerShape(
            VlrDimensions.DefaultCornerRadius,
        )
    }
    val container = when (variant) {
        VlrSearchFieldVariant.Standard -> colors.surfaceSubtle
        VlrSearchFieldVariant.Compact -> colors.surface
    }
    val outline = searchFieldOutline(isFocused, errorMessage, colors)
    val fieldOutline = if (outline.isOutsideVisualBounds) {
        Modifier.outsideOutline(outline.width, outline.color, variant)
    } else {
        Modifier.border(outline.width, outline.color, shape)
    }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                )
                .defaultMinSize(
                    minWidth = VlrDimensions.MinimumTouchTarget,
                    minHeight = maxOf(VlrDimensions.MinimumTouchTarget, variant.visualHeight),
                )
                .semantics {
                    contentDescription = label
                    if (hasError) semanticsError(errorMessage.orEmpty())
                    state.stateDescription?.let { stateDescription = it }
                },
            enabled = state.enabled,
            singleLine = true,
            textStyle = typography.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.actionPrimary),
            keyboardOptions = KeyboardOptions(imeAction = state.imeAction),
            keyboardActions = KeyboardActions(onSearch = { submitSearch(state.value, onSearch) }),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(variant.visualHeight)
                            .then(fieldOutline)
                            .clip(shape)
                            .background(container)
                            .then(if (enabled) Modifier else Modifier.alpha(0.5f)),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = VlrDimensions.Space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
                            SearchGlyph(modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(VlrDimensions.Space2))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = colors.textSecondary,
                                    style = typography.body,
                                )
                            }
                            innerTextField()
                        }
                        when {
                            state.isLoading -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colors.actionPrimary,
                                strokeWidth = 2.dp,
                            )

                            state.hasClearAction -> ClearSearchButton(
                                onClear = { clearSearch(onValueChange) },
                                contentDescription = "검색어 지우기",
                            )
                        }
                    }
                }
            },
        )
        if (hasError) {
            Text(
                text = errorMessage.orEmpty(),
                modifier = Modifier.padding(
                    start = VlrDimensions.Space3,
                    top = VlrDimensions.Space1,
                ),
                color = colors.actionPrimary,
                style = typography.labelSmall,
            )
        }
    }
}

private fun Modifier.outsideOutline(
    width: Dp,
    color: androidx.compose.ui.graphics.Color,
    variant: VlrSearchFieldVariant,
): Modifier = drawBehind {
    val strokeWidth = width.toPx()
    val halfStroke = strokeWidth / 2f
    translate(left = -halfStroke, top = -halfStroke) {
        val outlineHeight = size.height + strokeWidth
        val radius = if (variant.isPill) {
            outlineHeight / 2f
        } else {
            VlrDimensions.DefaultCornerRadius.toPx() + halfStroke
        }
        drawRoundRect(
            color = color,
            size = Size(size.width + strokeWidth, outlineHeight),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
private fun ClearSearchButton(
    onClear: () -> Unit,
    contentDescription: String,
) {
    val colors = LocalVlrColors.current
    Box(
        modifier = Modifier
            .size(VlrDimensions.MinimumTouchTarget)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
            ClearGlyph(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SearchGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val radius = size.minDimension * 0.25f
        val center = Offset(size.width * 0.43f, size.height * 0.43f)
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = strokeWidth))
        drawLine(
            color = color,
            start = Offset(size.width * 0.61f, size.height * 0.61f),
            end = Offset(size.width * 0.82f, size.height * 0.82f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ClearGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val inset = size.minDimension * 0.27f
        val strokeWidth = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
