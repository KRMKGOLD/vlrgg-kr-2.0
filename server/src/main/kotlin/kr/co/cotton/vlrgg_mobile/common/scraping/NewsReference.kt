package kr.co.cotton.vlrgg_mobile.common.scraping

/** A validated, canonical relative VLR.GG news path, never a caller-supplied URL. */
internal data class NewsReference(
    val articleId: String,
    val slug: String,
) {
    val value: String = "$articleId/$slug"

    companion object {
        private val articleIdPattern = Regex("[1-9][0-9]{0,9}")
        private val slugPattern = Regex("[a-z0-9][a-z0-9-]{0,127}")

        fun fromPath(articleId: String, slug: String): NewsReference? =
            if (articleIdPattern.matches(articleId) && slugPattern.matches(slug)) {
                NewsReference(articleId = articleId, slug = slug)
            } else {
                null
            }

        fun fromHref(href: String): NewsReference? {
            val path = (href.toVlrPathOrNull()
                ?: return null)
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
            val segments = path.split('/')

            return if (segments.size == 3 && segments.first().isEmpty()) {
                fromPath(segments[1], segments[2])
            } else {
                null
            }
        }

        private fun String.toVlrPathOrNull(): String? = when {
            startsWith("/") && !startsWith("//") -> this
            startsWith("https://www.vlr.gg/") -> removePrefix("https://www.vlr.gg")
            startsWith("https://vlr.gg/") -> removePrefix("https://vlr.gg")
            startsWith("http://www.vlr.gg/") -> removePrefix("http://www.vlr.gg")
            startsWith("http://vlr.gg/") -> removePrefix("http://vlr.gg")
            startsWith("//www.vlr.gg/") -> removePrefix("//www.vlr.gg")
            startsWith("//vlr.gg/") -> removePrefix("//vlr.gg")
            else -> null
        }
    }
}
