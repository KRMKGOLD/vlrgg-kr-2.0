package kr.co.cotton.vlrgg_mobile.domain.model.search

sealed interface SearchResult {
    val id: String
    val name: String
    val metadata: String?

    /** Thumbnail: absolute https URL, or null when the source has no usable image. */
    val imageUrl: String?
}
