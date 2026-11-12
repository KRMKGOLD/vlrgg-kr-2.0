package kr.co.cotton.vlrgg_mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.error as semanticsError
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.LocalVlrTypography
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrColors
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrDimensions
import org.jetbrains.compose.resources.vectorResource
import vlrggmobile.app.shared.generated.resources.Res
import vlrggmobile.app.shared.generated.resources.ic_cancel
import vlrggmobile.app.shared.generated.resources.ic_search

internal const val VLR_SEARCH_FIELD_CLEAR_TAG = "vlr-search-field-clear"
internal const val VlrSearchFieldClearLabel = "검색어 지우기"

/** Visual height of the v3 search field (`DESIGN.md` 8.4). */
internal val VlrSearchFieldHeight: Dp = 48.dp

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
    val color: Color,
    val width: Dp,
)

/** Focus draws a 2dp `ink` border; an unfocused, submitted value keeps a 1dp `line-strong` border. */
internal fun searchFieldOutline(
    isFocused: Boolean,
    hasValue: Boolean,
    errorMessage: String?,
    colors: VlrColors,
): VlrSearchFieldOutline? = when {
    !errorMessage.isNullOrBlank() -> VlrSearchFieldOutline(colors.accent, VlrDimensions.FocusOutlineWidth)
    isFocused -> VlrSearchFieldOutline(colors.ink, VlrDimensions.FocusOutlineWidth)
    hasValue -> VlrSearchFieldOutline(colors.lineStrong, VlrDimensions.OutlineWidth)
    else -> null
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
    val shape = RoundedCornerShape(VlrDimensions.CornerRadius)
    val outline = searchFieldOutline(isFocused, value.isNotEmpty(), errorMessage, colors)
    val textStyle = typography.body.copy(fontWeight = FontWeight.Normal)

    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .defaultMinSize(minHeight = VlrSearchFieldHeight)
                .semantics {
                    contentDescription = label
                    if (hasError) semanticsError(errorMessage.orEmpty())
                    state.stateDescription?.let { stateDescription = it }
                },
            enabled = state.enabled,
            singleLine = true,
            textStyle = textStyle.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = state.imeAction),
            keyboardActions = KeyboardActions(onSearch = { submitSearch(state.value, onSearch) }),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = VlrSearchFieldHeight)
                        .background(colors.surfaceSunken, shape)
                        .then(outline?.let { Modifier.border(it.width, it.color, shape) } ?: Modifier)
                        .then(if (enabled) Modifier else Modifier.alpha(0.5f))
                        .padding(start = VlrDimensions.Space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.muted,
                    )
                    Spacer(Modifier.width(VlrDimensions.Space2))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(text = placeholder, color = colors.muted, style = textStyle)
                        }
                        innerTextField()
                    }
                    when {
                        state.isLoading -> Box(
                            modifier = Modifier.size(VlrDimensions.MinimumTouchTarget),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colors.muted,
                                strokeWidth = 2.dp,
                            )
                        }

                        state.hasClearAction -> ClearSearchButton(
                            onClear = { clearSearch(onValueChange) },
                        )

                        else -> Spacer(Modifier.width(VlrDimensions.Space3))
                    }
                }
            },
        )
        if (hasError) {
            Text(
                text = errorMessage.orEmpty(),
                modifier = Modifier.padding(start = VlrDimensions.Space3, top = VlrDimensions.Space1),
                color = colors.accent,
                style = typography.label,
            )
        }
    }
}

@Composable
private fun ClearSearchButton(onClear: () -> Unit) {
    val colors = LocalVlrColors.current
    Box(
        modifier = Modifier
            .size(VlrDimensions.MinimumTouchTarget)
            .testTag(VLR_SEARCH_FIELD_CLEAR_TAG)
            .semantics { contentDescription = VlrSearchFieldClearLabel }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = VlrDimensions.MinimumTouchTarget / 2),
                role = Role.Button,
                onClick = onClear,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = vectorResource(Res.drawable.ic_cancel),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.muted,
        )
    }
}
