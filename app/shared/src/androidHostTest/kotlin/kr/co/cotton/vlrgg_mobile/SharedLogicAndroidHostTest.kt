package kr.co.cotton.vlrgg_mobile

import kr.co.cotton.vlrgg_mobile.ui.navigation.MyPageRoot
import kr.co.cotton.vlrgg_mobile.ui.navigation.OverlayNavEntry
import kr.co.cotton.vlrgg_mobile.ui.navigation.Search
import kr.co.cotton.vlrgg_mobile.ui.navigation.contentKeyFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SharedLogicAndroidHostTest {
    @Test
    fun navigationEntryContentKeyUsesBundleCompatibleString() {
        val contentKey = OverlayNavEntry(Search, entryId = 42).contentKeyFor(MyPageRoot)

        assertIs<String>(contentKey)
        assertEquals("my-page:42", contentKey)
    }
}
