package kr.co.cotton.vlrgg_mobile.ui.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.EventSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.PlayerSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.SearchResults
import kr.co.cotton.vlrgg_mobile.domain.model.search.SeriesSearchResult
import kr.co.cotton.vlrgg_mobile.domain.model.search.TeamSearchResult
import kr.co.cotton.vlrgg_mobile.domain.repository.SearchRepository
import kr.co.cotton.vlrgg_mobile.ui.component.VLR_SEARCH_FIELD_CLEAR_TAG
import kr.co.cotton.vlrgg_mobile.ui.theme.VlrTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v3 search (`.pen` 60): query section, Korean type rows, states, submit rules, focus, large fonts and RTL. */
@OptIn(ExperimentalTestApi::class)
class SearchV3UiTest {
    @Test
    fun resultsShowTheQuerySectionAndKoreanTypeRowsThatOpenTheirDetail() = runComposeUiTest {
        var clicked: SearchResult? = null
        setContent { Fixture(populated("DRX"), onResultClick = { clicked = it }) }

        onNode(hasText("‘DRX’ 검색 결과") and isHeading(), useUnmergedTree = true).assertExists()
        onNode(hasText("4") and hasAnyAncestor(hasTestTag(SEARCH_RESULTS_SECTION_TAG)), useUnmergedTree = true)
            .assertExists()
        listOf("Series", "Event", "Team", "Player").forEach { english ->
            onNodeWithText(english, useUnmergedTree = true).assertDoesNotExist()
        }

        // Series → Event → Team → Player, each with its Korean type label inside the row.
        val tops = listOf(series to "시리즈", event to "대회", team to "팀", player to "선수").map { (result, label) ->
            onNode(hasText(label) and hasAnyAncestor(hasTestTag(searchRowTag(result))), useUnmergedTree = true)
                .assertExists("'$label' is not in ${result.name}")
            onNodeWithTag(searchRowTag(result)).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)

        onNodeWithTag(searchRowTag(team))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText("팀") and hasText("T1") and hasText("Pacific"))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(team, clicked)
    }

    @Test
    fun typeLabelsShareOneColumnSoTitlesLineUp() = runSkikoComposeUiTest(size = Size(360f, 1200f)) {
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { Fixture(populated("DRX")) } }

        val labels = listOf(series to "시리즈", event to "대회", team to "팀", player to "선수").map { (result, label) ->
            textNode(label, result).boundsInRoot
        }
        val titles = items.map { result -> textNode(result.name, result).boundsInRoot }
        labels.forEach { label ->
            assertEquals(16f, label.left, 0.5f)
            assertTrue(label.width >= SearchTypeLabelMinWidth.value - 0.5f, "type column is narrower than 32dp: $label")
        }
        assertEquals(1, titles.map { it.left.toInt() }.toSet().size, "titles do not line up: $titles")
        assertTrue(titles.first().left >= labels.first().left + SearchTypeLabelMinWidth.value + 12f - 0.5f)
    }

    @Test
    fun duplicateResultsKeepDistinctLazyRows() = runComposeUiTest {
        setContent {
            Fixture(SearchUiState(query = "T1", contentState = SearchContentState.Populated(listOf(team, team))))
        }
        onAllNodesWithTag(searchRowTag(team)).assertCountEquals(2)
        onNode(hasText("2") and hasAnyAncestor(hasTestTag(SEARCH_RESULTS_SECTION_TAG)), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun statesMatchThePenCopyAndKeepTheClearActionWhileLoading() = runComposeUiTest {
        var state by mutableStateOf(SearchUiState())
        setContent { Fixture(state) }

        onNodeWithText("검색어를 입력해 주세요", useUnmergedTree = true).assertExists()
        onNodeWithText("팀, 선수, 대회, 시리즈를 검색할 수 있습니다.", useUnmergedTree = true).assertExists()
        onNodeWithTag(VLR_SEARCH_FIELD_CLEAR_TAG).assertDoesNotExist()

        state = SearchUiState(query = "DRX", contentState = SearchContentState.Loading)
        onNodeWithTag(SEARCH_LOADING_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
            .assert(hasText("DRX 검색 중…"))
        // `.pen` 60-3: the field keeps its clear action; progress is shown once, in the panel.
        onNodeWithContentDescription("검색어 지우기").assertExists()
        onNodeWithContentDescription("검색").assertIsNotEnabled()

        state = SearchUiState(query = "DRX", contentState = SearchContentState.Empty)
        onNodeWithText("DRX에 대한 검색 결과가 없어요.", useUnmergedTree = true).assertExists()
        onNodeWithText("검색어를 바꿔 다시 시도해 주세요.", useUnmergedTree = true).assertExists()
        onNodeWithTag(SEARCH_RETRY_TAG).assertDoesNotExist()

        state = SearchUiState(query = "DRX", contentState = SearchContentState.Error)
        onNodeWithText("검색 결과를 불러오지 못했습니다.", useUnmergedTree = true).assertExists()
        onNodeWithTag(SEARCH_RETRY_TAG).assert(hasText("다시 시도"))
    }

    @Test
    fun onlyAnExplicitValidSubmitRequestsAndInputStopsAtThirtyCharacters() = runComposeUiTest {
        val repository = RecordingSearchRepository()
        setContent {
            VlrTheme {
                val viewModel = remember { SearchViewModel(repository) }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                SearchContent(
                    uiState = uiState,
                    onQueryChange = viewModel::onQueryChange,
                    onSubmit = viewModel::submit,
                    onRetry = viewModel::retry,
                    onBack = {},
                    onResultClick = {},
                )
            }
        }
        val field = onNodeWithContentDescription("검색어")
        val submit = onNodeWithContentDescription("검색")

        field.performTextInput("DRX")
        waitForIdle()
        assertEquals(emptyList(), repository.queries, "typing must not search")
        submit.assertIsEnabled()

        field.performTextClearance()
        field.performTextInput("  !!  ")
        submit.assertIsNotEnabled()
        field.performImeAction()
        waitForIdle()
        assertEquals(emptyList(), repository.queries, "symbol-only input must not search")

        field.performTextClearance()
        field.performTextInput("   ")
        submit.assertIsNotEnabled()

        field.performTextClearance()
        field.performTextInput("a".repeat(31))
        assertEquals("a".repeat(30), field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        field.performImeAction()
        waitUntil { repository.queries.isNotEmpty() }
        assertEquals(listOf("a".repeat(30)), repository.queries)

        field.performTextClearance()
        field.performTextInput(" DRX ")
        submit.performClick()
        waitUntil { repository.queries.size == 2 }
        assertEquals("DRX", repository.queries.last())
        onNode(hasText("‘DRX’ 검색 결과") and isHeading(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun keyboardFocusMovesBackInputClearSubmitThenResults() = runComposeUiTest {
        setContent { Fixture(populated("DRX")) }

        val field = onNodeWithContentDescription("검색어").assertIsFocused()
        onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        onNodeWithContentDescription("검색 화면 닫기").assertIsFocused()

        listOf(
            field,
            onNodeWithContentDescription("검색어 지우기"),
            onNodeWithContentDescription("검색"),
            onNodeWithTag(searchRowTag(series)),
            onNodeWithTag(searchRowTag(event)),
        ).forEach { next ->
            onRoot().performKeyInput { pressKey(Key.Tab) }
            next.assertIsFocused()
        }
    }

    @Test
    fun screenReaderOrderIsBackInputClearSubmitThenResults() = runComposeUiTest {
        setContent { Fixture(populated("DRX")) }

        // The header is a traversal group placed before the body, which Scaffold lays out first.
        onNodeWithTag(SEARCH_HEADER_TAG, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, -1f))
        val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        fun names(matcher: SemanticsMatcher) = onAllNodes(matcher, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node ->
                assertEquals(0f, node.config.getOrNull(SemanticsProperties.TraversalIndex) ?: 0f)
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                    ?: node.config.getOrNull(SemanticsProperties.TestTag)
            }
        assertEquals(
            listOf("검색 화면 닫기", "검색어", "검색어 지우기", "검색"),
            names(clickable and hasAnyAncestor(hasTestTag(SEARCH_HEADER_TAG))),
        )
        assertEquals(
            listOf(searchRowTag(series), searchRowTag(event), searchRowTag(team), searchRowTag(player)),
            names(clickable and hasAnyAncestor(hasTestTag(SEARCH_HEADER_TAG)).not()),
        )
    }

    @Test
    fun largeFontsAndNarrowWidthsKeepEveryTextWhole() {
        val query = "Sentinels Sentinels Sentinels!"
        val results = listOf(
            SeriesSearchResult("1", "Champions Tour 2026: Pacific Stage 1 Playoffs", "Pacific · 2026"),
            EventSearchResult("2", "VCT 2026: Pacific Stage 1 Lower Bracket Final", "Jun 15, 2026 – Jul 21, 2026"),
            TeamSearchResult("3", "Sentinels Academy Development Squad", "SEN · United States"),
            PlayerSearchResult("4", "TenZ", "Tyson Ngo · Canada"),
        )
        val texts = listOf("‘$query’ 검색 결과", "4", "시리즈", "대회", "팀", "선수") +
            results.flatMap { listOfNotNull(it.name, it.metadata) }
        listOf(320f, 360f).forEach { width ->
            listOf(1f, 2f).forEach { scale ->
                runSkikoComposeUiTest(size = Size(width, 4000f)) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                            Fixture(SearchUiState(query = query, contentState = SearchContentState.Populated(results)))
                        }
                    }
                    val label = "width=$width fontScale=$scale"
                    texts.forEach { text -> assertWhole(text, label, width) }
                    assertWhole("검색", label, width)
                    // A long query wraps the section title instead of squeezing the result count.
                    val count = onNode(
                        hasText("4") and hasAnyAncestor(hasTestTag(SEARCH_RESULTS_SECTION_TAG)),
                        useUnmergedTree = true,
                    ).fetchSemanticsNode()
                    val countWidth = count.textLayout().multiParagraph.intrinsics.maxIntrinsicWidth
                    assertTrue(count.boundsInRoot.width >= countWidth - 0.5f, "$label result count is squeezed")
                    // The type column grows with the font so `시리즈` never breaks into two lines.
                    listOf("시리즈", "대회", "팀", "선수").forEach { type ->
                        onAllNodes(hasText(type), useUnmergedTree = true).fetchSemanticsNodes()
                            .filter { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
                            .forEach { node -> assertEquals(1, node.textLayout().lineCount, "$label '$type' wraps") }
                    }
                    val fieldBounds = onNodeWithContentDescription("검색어").fetchSemanticsNode().boundsInRoot
                    assertTrue(fieldBounds.width >= 120f, "$label search field is squeezed: $fieldBounds")
                }
            }
        }
    }

    @Test
    fun largeFontsAndNarrowWidthsKeepStatePanelsWhole() {
        val states = listOf(
            SearchUiState() to listOf("검색어를 입력해 주세요", "팀, 선수, 대회, 시리즈를 검색할 수 있습니다."),
            SearchUiState(query = "DRX", contentState = SearchContentState.Loading) to listOf("DRX 검색 중…", "잠시만 기다려 주세요."),
            SearchUiState(query = "DRX", contentState = SearchContentState.Empty) to
                listOf("DRX에 대한 검색 결과가 없어요.", "검색어를 바꿔 다시 시도해 주세요."),
            SearchUiState(query = "DRX", contentState = SearchContentState.Error) to
                listOf("검색 결과를 불러오지 못했습니다.", "네트워크 상태를 확인하고 다시 시도해 주세요.", "다시 시도"),
        )
        states.forEach { (state, texts) ->
            runSkikoComposeUiTest(size = Size(320f, 2400f)) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { Fixture(state) }
                }
                texts.forEach { text -> assertWhole(text, "${state.contentState} 320dp fontScale=2", 320f) }
            }
        }
    }

    @Test
    fun rtlMirrorsTheHeaderSectionTitleAndRows() = runSkikoComposeUiTest(size = Size(360f, 1200f)) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                Fixture(populated("DRX"))
            }
        }

        val back = onNodeWithContentDescription("검색 화면 닫기").fetchSemanticsNode().boundsInRoot
        val field = onNodeWithContentDescription("검색어").fetchSemanticsNode().boundsInRoot
        assertTrue(back.left > field.left, "Back is not on the right in RTL")
        val section = onNode(hasText("‘DRX’ 검색 결과") and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(360f - 16f, section.right, 1f)
        val type = textNode("팀", team).boundsInRoot
        val title = textNode("T1", team).boundsInRoot
        assertEquals(360f - 16f, type.right, 1f)
        assertTrue(title.right < type.left, "title is not left of the type label in RTL")
    }

    private fun populated(query: String) = SearchUiState(query = query, contentState = SearchContentState.Populated(items))

    private fun ComposeUiTest.textNode(text: String, result: SearchResult): SemanticsNode =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(searchRowTag(result))), useUnmergedTree = true)
            .fetchSemanticsNode()

    private fun ComposeUiTest.assertWhole(text: String, label: String, width: Float) {
        val nodes = onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
        assertTrue(nodes.isNotEmpty(), "$label '$text' is missing")
        nodes.forEach { node ->
            val layout = node.textLayout()
            if (layout.layoutInput.text.text != text) return@forEach
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1), "$label '$text' is truncated")
            assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$label '$text' is ellipsized")
            assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$label '$text' is cut vertically")
            assertTrue(node.boundsInRoot.right <= width + 0.5f, "$label '$text' leaves the screen")
            assertTrue(node.boundsInRoot.left >= -0.5f, "$label '$text' leaves the screen")
        }
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first()
    }

    @Composable
    private fun Fixture(
        uiState: SearchUiState,
        onResultClick: (SearchResult) -> Unit = {},
    ) {
        VlrTheme {
            SearchContent(
                uiState = uiState,
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onBack = {},
                onResultClick = onResultClick,
            )
        }
    }

    private class RecordingSearchRepository : SearchRepository {
        val queries = mutableListOf<String>()

        override suspend fun getSearch(query: String): AppResult<SearchResults> {
            queries += query
            return AppResult.Success(SearchResults(query = query, items = listOf(TeamSearchResult("3", "DRX", null))))
        }
    }

    private companion object {
        val series = SeriesSearchResult("1", "T1 vs GEN", "Group Stage")
        val event = EventSearchResult("2", "VCT Pacific", "06.15–07.21")
        val team = TeamSearchResult("3", "T1", "Pacific")
        val player = PlayerSearchResult("4", "T1 Sayaplayer", "Ha Jung-woo")
        val items = listOf(player, team, series, event)
    }
}
