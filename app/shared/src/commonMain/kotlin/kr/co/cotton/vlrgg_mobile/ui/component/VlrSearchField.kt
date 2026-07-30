package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

enum class VlrSearchFieldVariant(val visualHeight: Dp) {
    Standard(56.dp),
    Compact(40.dp),
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
) {
    val colors = LocalVlrColors.current
    val typography = LocalVlrTypography.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val hasError = !errorMessage.isNullOrBlank()
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
    val borderColor = when {
        hasError -> colors.actionPrimary
        isFocused -> colors.focusOutline
        else -> colors.outline
    }
    val borderWidth = if (hasError || isFocused) {
        VlrDimensions.FocusOutlineWidth
    } else {
        VlrDimensions.OutlineWidth
    }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(
                    minWidth = VlrDimensions.MinimumTouchTarget,
                    minHeight = maxOf(VlrDimensions.MinimumTouchTarget, variant.visualHeight),
                )
                .semantics {
                    contentDescription = label
                    if (hasError) semanticsError(errorMessage.orEmpty())
                    if (isLoading) stateDescription = "로딩 중"
                },
            enabled = enabled,
            singleLine = true,
            textStyle = typography.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.actionPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
                            .clip(shape)
                            .background(container)
                            .border(borderWidth, borderColor, shape)
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
                            isLoading -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colors.actionPrimary,
                                strokeWidth = 2.dp,
                            )

                            enabled && value.isNotEmpty() -> ClearSearchButton(
                                onClear = { onValueChange("") },
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
