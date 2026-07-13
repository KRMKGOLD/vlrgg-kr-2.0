package kr.co.cotton.vlrgg_mobile.feature.news

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val VLR_PRIMARY_ORIGIN = "https://www.vlr.gg/"
private val whitespace = Regex("\\s+")
private val publicationAuthor = Regex("(?i)\\bby\\s+(.+)$")
private val entityLink = Regex("^/(team|player|event|match)/([1-9][0-9]{0,9})/([a-z0-9][a-z0-9-]{0,127})/?$")

/** Owns all Jsoup and VLR.GG DOM assumptions for the News feature. */
internal class NewsParser {
    fun parseList(html: String, currentPage: Int): NewsListSource {
        val document = Jsoup.parse(html, VLR_PRIMARY_ORIGIN)
        val articles = document.select("a.news-item[href]").map { item ->
            val reference = NewsReference.fromHref(item.attr("href"))
                ?: throw IllegalStateException("News item reference is missing.")
            val title = requiredText(item.selectFirst(".news-item-title"), "News item title is missing.")
            val metadata = requiredText(
                item.selectFirst(".news-item-desc, .news-item-meta, .ge-text-light"),
                "News item metadata is missing.",
            )
            val (publishedAt, author) = parsePublicationMetadata(metadata)

            NewsSummarySource(
                reference = reference,
                title = title,
                author = author,
                publishedAt = publishedAt,
            )
        }

        return NewsListSource(
            articles = articles,
            hasNextPage = document.select("a[href]").any { link ->
                parseNewsPage(link.attr("href"))?.let { it > currentPage } == true
            },
        )
    }

    fun parseArticle(html: String, reference: NewsReference): NewsArticleSource {
        val document = Jsoup.parse(html, VLR_PRIMARY_ORIGIN)
        val title = requiredText(
            document.selectFirst(".article-header-title, .article-title, .wf-title-med, h1"),
            "Article title is missing.",
        )
        val metadata = parseArticleMetadata(document)
        val body = document.selectFirst(".article-body")
            ?: throw IllegalStateException("Article body is missing.")

        body.select(
            "style, script, .wf-hover-card, .article-ref-card, .sidebar, .comments, .comment, " +
                "iframe, embed, object, .article-embed, .wf-embed, .twitter-tweet, [aria-hidden=true]",
        ).remove()
        val blocks = parseBlocks(body)
        if (blocks.isEmpty()) {
            throw IllegalStateException("Article body has no supported content blocks.")
        }

        return NewsArticleSource(
            reference = reference,
            title = title,
            author = metadata.author,
            publishedAt = metadata.publishedAt,
            blocks = blocks,
        )
    }

    private fun parseArticleMetadata(document: org.jsoup.nodes.Document): ArticleMetadata {
        val metadata = document.selectFirst(
            ".article-header-desc, .article-meta, .article-header .ge-text-light, .wf-title-med + .ge-text-light",
        )
            ?: throw IllegalStateException("Article metadata is missing.")
        val explicitAuthor = metadata.selectFirst(".article-meta-author, [data-news-author]")?.cleanText()
        val explicitPublishedAt = metadata.selectFirst(".article-meta-time, [data-news-published-at], time")?.cleanText()

        if (explicitAuthor != null && explicitPublishedAt != null) {
            return ArticleMetadata(author = explicitAuthor, publishedAt = explicitPublishedAt)
        }

        val (publishedAt, author) = parsePublicationMetadata(requiredText(metadata, "Article metadata is missing."))
        return ArticleMetadata(
            author = explicitAuthor ?: author,
            publishedAt = explicitPublishedAt ?: publishedAt,
        )
    }

    private fun parseBlocks(container: Element): List<NewsSourceBlock> = buildList {
        container.children().forEach { element ->
            when (element.normalName()) {
                "p", "blockquote", "h2", "h3", "h4", "h5", "h6" -> {
                    parseInline(element).takeIf { it.isNotEmpty() }?.let { content ->
                        add(NewsParagraphSourceBlock(content))
                    }
                }

                "figure" -> parseImage(element)?.let(::add)
                "img" -> parseImageElement(element, caption = null)?.let(::add)
                "ol", "ul" -> parseList(element)?.let(::add)
                "div", "section", "main" -> addAll(parseBlocks(element))
            }
        }
    }

    private fun parseImage(figure: Element): NewsImageSourceBlock? =
        figure.selectFirst("img[src]")?.let { image ->
            parseImageElement(image, figure.selectFirst("figcaption")?.cleanText())
        }

    private fun parseImageElement(image: Element, caption: String?): NewsImageSourceBlock? {
        val imageUrl = image.absUrl("src").ifBlank { image.attr("src").trim() }
        return imageUrl.takeIf { it.isNotBlank() }?.let {
            NewsImageSourceBlock(imageUrl = it, caption = caption)
        }
    }

    private fun parseList(element: Element): NewsListSourceBlock? {
        val items = element.children()
            .filter { it.normalName() == "li" }
            .mapNotNull { item -> parseInline(item).takeIf { it.isNotEmpty() } }

        return items.takeIf { it.isNotEmpty() }?.let {
            NewsListSourceBlock(ordered = element.normalName() == "ol", items = it)
        }
    }

    private fun parseInline(element: Element): List<NewsSourceInline> {
        val content = mutableListOf<NewsSourceInline>()
        element.childNodes().forEach { node -> appendInlineNode(node, content) }
        return content.trimTextEdges()
    }

    private fun appendInlineNode(node: Node, content: MutableList<NewsSourceInline>) {
        when (node) {
            is TextNode -> content.appendText(node.wholeText)
            is Element -> when (node.normalName()) {
                "a" -> {
                    val label = node.cleanText()
                    if (label != null) {
                        content += classifyLink(label = label, href = node.attr("href"))
                    }
                }

                "br" -> content.appendText("\n")
                "style", "script", "iframe", "embed", "object", "ol", "ul", "figure" -> Unit
                else -> node.childNodes().forEach { child -> appendInlineNode(child, content) }
            }
        }
    }

    private fun classifyLink(label: String, href: String): NewsLinkSourceInline {
        val localPath = href.toVlrPathOrNull()
        val match = localPath?.let(entityLink::matchEntire)
        if (match != null) {
            val kind = when (match.groupValues[1]) {
                "team" -> NewsLinkKindSource.TEAM
                "player" -> NewsLinkKindSource.PLAYER
                "event" -> NewsLinkKindSource.EVENT
                else -> NewsLinkKindSource.MATCH
            }
            return NewsLinkSourceInline(
                label = label,
                kind = kind,
                reference = "${match.groupValues[2]}/${match.groupValues[3]}",
            )
        }

        return NewsLinkSourceInline(
            label = label,
            kind = if (localPath != null) NewsLinkKindSource.INTERNAL_UNSUPPORTED else NewsLinkKindSource.EXTERNAL,
            reference = null,
        )
    }

    private fun parsePublicationMetadata(metadata: String): Pair<String, String> {
        val authorMatch = publicationAuthor.find(metadata)
            ?: throw IllegalStateException("Article author is missing.")
        val author = authorMatch.groupValues[1].trimSourceDecoration()
        val publishedAt = metadata.substring(0, authorMatch.range.first).trimSourceDecoration()

        if (author.isBlank() || publishedAt.isBlank()) {
            throw IllegalStateException("Article publication metadata is incomplete.")
        }
        return publishedAt to author
    }

    private fun parseNewsPage(href: String): Int? =
        Regex("[?&]page=([1-9][0-9]{0,4})(?:&|$)").find(href)?.groupValues?.get(1)?.toIntOrNull()

    private fun requiredText(element: Element?, message: String): String =
        element?.cleanText() ?: throw IllegalStateException(message)

    private fun Element.cleanText(): String? = text().normalizeWhitespace().takeIf { it.isNotEmpty() }

    private fun String.normalizeWhitespace(): String = replace(whitespace, " ").trim()

    private fun String.trimSourceDecoration(): String =
        trim { character -> character.isWhitespace() || character == '•' || character == '-' }

    private fun String.toVlrPathOrNull(): String? = when {
        startsWith("/") -> substringBefore('?').substringBefore('#')
        startsWith("https://www.vlr.gg/") -> removePrefix("https://www.vlr.gg").substringBefore('?').substringBefore('#')
        startsWith("https://vlr.gg/") -> removePrefix("https://vlr.gg").substringBefore('?').substringBefore('#')
        else -> null
    }

    private fun MutableList<NewsSourceInline>.appendText(rawText: String) {
        val normalized = rawText.replace(whitespace, " ")
        if (normalized.isBlank()) return

        val last = lastOrNull()
        if (last is NewsTextSourceInline) {
            this[lastIndex] = last.copy(text = last.text + normalized)
        } else {
            add(NewsTextSourceInline(normalized))
        }
    }

    private fun List<NewsSourceInline>.trimTextEdges(): List<NewsSourceInline> {
        val result = toMutableList()
        (result.firstOrNull() as? NewsTextSourceInline)?.let { first ->
            if (first.text.trimStart().isEmpty()) result.removeAt(0) else result[0] = first.copy(text = first.text.trimStart())
        }
        (result.lastOrNull() as? NewsTextSourceInline)?.let { last ->
            val index = result.lastIndex
            if (last.text.trimEnd().isEmpty()) result.removeAt(index) else result[index] = last.copy(text = last.text.trimEnd())
        }
        return result
    }

    private data class ArticleMetadata(
        val author: String,
        val publishedAt: String,
    )
}
