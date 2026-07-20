package kr.co.cotton.vlrgg_mobile.feature.news

import kr.co.cotton.vlrgg_mobile.common.scraping.NewsReference

internal fun newsReference(articleId: String, slug: String): NewsReference =
    requireNotNull(NewsReference.fromPath(articleId, slug)) { "Test reference must be canonical." }
