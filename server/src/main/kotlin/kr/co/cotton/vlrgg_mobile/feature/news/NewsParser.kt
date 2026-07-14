package kr.co.cotton.vlrgg_mobile.feature.news

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private val whitespace = Regex("\\s+")
private val publicationAuthor = Regex("(?i)\\bby\\s+(.+)$")
private val entityLink = Regex("^/(team|player|event)/([1-9][0-9]{0,9})/([a-z0-9][a-z0-9-]{0,127})/?$")
private val matchLink = Regex("^/([1-9][0-9]{0,9})/([a-z0-9][a-z0-9-]{0,127})/?$")
private val canonicalNewsPageTarget = Regex("^/news/\\?page=([1-9][0-9]{0,4})$")
private const val legacyNewsListItemSelector = "a.news-item[href]"
private const val operationalNewsActionSelector = "#wrapper .col-container > .col.mod-1 > .action-container"
private const val newsPaginationSelector =
    ".wf-pagination a[href], [data-news-pagination] a[href], " +
        "#wrapper .col-container > .col.mod-1 > .action-container > .action-container-pages a[href]"

internal class NewsParsingException(message: String) : IllegalStateException(message)

/** Owns all Jsoup and VLR.GG DOM assumptions for the News feature. */
internal class NewsParser {
    fun parseList(html: String, currentPage: Int): NewsListSource {
        val document = Jsoup.parse(html, VLR_PRIMARY_ORIGIN)
        val articles = findNewsListItems(document).map { item ->
            val reference = NewsReference.fromHref(item.attr("href"))
                ?: throw NewsParsingException("News item reference is missing.")
            val metadataElement = item.selectFirst(".news-item-desc, .news-item-meta, .ge-text-light")
            val metadata = requiredText(metadataElement, "News item metadata is missing.")
            val title = requiredText(findNewsItemTitle(item, metadataElement), "News item title is missing.")
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
            nextPage = document.select(newsPaginationSelector)
                .mapNotNull { link -> parseCanonicalNewsPage(link.attr("href")) }
                .firstOrNull { candidate -> candidate == currentPage + 1 && candidate <= MAX_NEWS_PAGE },
        )
    }

    fun parseArticle(html: String, reference: NewsReference): NewsArticleSource {
        val document = Jsoup.parse(html, VLR_PRIMARY_ORIGIN)
        val body = document.selectFirst(".article-body")
            ?: throw NewsParsingException("Article body is missing.")
        val header = findArticleHeader(body)
        val title = requiredText(
            header.selectFirst(".article-header-title, .article-title, .wf-title-med, h1"),
            "Article title is missing.",
        )
        val metadata = parseArticleMetadata(header)

        body.select(
            "style, script, .wf-hover-card, .article-ref-card, .sidebar, .comments, .comment, " +
                "iframe, embed, object, .article-embed, .wf-embed, .twitter-tweet, [aria-hidden=true]",
        ).remove()
        val blocks = parseBlocks(body)
        if (blocks.isEmpty()) {
            throw NewsParsingException("Article body has no supported content blocks.")
        }

        return NewsArticleSource(
            reference = reference,
            title = title,
            author = metadata.author,
            publishedAt = metadata.publishedAt,
            blocks = blocks,
        )
    }

    private fun parseArticleMetadata(header: Element): ArticleMetadata {
        val metadata = header.selectFirst(
            ".article-header-desc, .article-meta, .ge-text-light, .wf-title-med + .ge-text-light",
        )
            ?: throw NewsParsingException("Article metadata is missing.")
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

    private fun findArticleHeader(body: Element): Element {
        var child = body
        var parent = body.parent()

        while (parent != null) {
            val siblings = parent.children()
            val childIndex = siblings.indexOf(child)
            siblings.take(childIndex).asReversed()
                .firstOrNull { sibling -> sibling.hasClass("article-header") }
                ?.let { return it }
            child = parent
            parent = parent.parent()
        }

        throw NewsParsingException("Article header is missing.")
    }

    private fun findNewsListItems(document: Document): List<Element> = buildList {
        addAll(document.select(legacyNewsListItemSelector))
        document.select(operationalNewsActionSelector)
            .filter { action -> action.children().any { it.hasClass("action-container-pages") } }
            .mapNotNull { action -> action.previousElementSibling() }
            .filter { card -> card.hasClass("wf-card") }
            .forEach { card ->
                addAll(
                    card.children().filter { item ->
                        item.normalName() == "a" && item.hasClass("wf-module-item") && item.hasAttr("href")
                    },
                )
            }
    }.distinct()

    private fun findNewsItemTitle(item: Element, metadata: Element?): Element? =
        item.selectFirst(".news-item-title") ?: metadata?.parent()
            ?.takeIf { content -> content.parent() === item }
            ?.children()
            ?.firstOrNull { candidate ->
                candidate.normalName() == "div" && !candidate.hasAttr("class") && candidate !== metadata
            }

    private fun parseBlocks(container: Element): List<NewsSourceBlock> = buildList {
        container.childNodes().forEach { node ->
            when (node) {
                is TextNode -> parseDirectTextBlock(node)?.let(::add)
                is Element -> when (node.normalName()) {
                    "p", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        parseInline(node).takeIf { it.isNotEmpty() }?.let { content ->
                            add(NewsParagraphSourceBlock(content))
                        }
                    }

                    "figure" -> parseImage(node)?.let(::add)
                    "img" -> parseImageElement(node, caption = null)?.let(::add)
                    "ol", "ul" -> parseList(node)?.let(::add)
                    "div", "section", "main" -> addAll(parseBlocks(node))
                    else -> addAll(parseBlocks(node))
                }
            }
        }
    }

    private fun parseDirectTextBlock(node: TextNode): NewsParagraphSourceBlock? =
        node.wholeText.normalizeWhitespace().takeIf { it.isNotEmpty() }?.let { text ->
            NewsParagraphSourceBlock(content = listOf(NewsTextSourceInline(text)))
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
                else -> NewsLinkKindSource.EVENT
            }
            return NewsLinkSourceInline(
                label = label,
                kind = kind,
                reference = "${match.groupValues[2]}/${match.groupValues[3]}",
            )
        }

        val matchPath = localPath?.let(matchLink::matchEntire)
        if (matchPath != null) {
            return NewsLinkSourceInline(
                label = label,
                kind = NewsLinkKindSource.MATCH,
                reference = "${matchPath.groupValues[1]}/${matchPath.groupValues[2]}",
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
            ?: throw NewsParsingException("Article author is missing.")
        val author = authorMatch.groupValues[1].trimSourceDecoration()
        val publishedAt = metadata.substring(0, authorMatch.range.first).trimSourceDecoration()

        if (author.isBlank() || publishedAt.isBlank()) {
            throw NewsParsingException("Article publication metadata is incomplete.")
        }
        return publishedAt to author
    }

    private fun parseCanonicalNewsPage(href: String): Int? =
        href.toCanonicalNewsPathOrNull()?.let(canonicalNewsPageTarget::matchEntire)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private fun requiredText(element: Element?, message: String): String =
        element?.cleanText() ?: throw NewsParsingException(message)

    private fun Element.cleanText(): String? = text().normalizeWhitespace().takeIf { it.isNotEmpty() }

    private fun String.normalizeWhitespace(): String = replace(whitespace, " ").trim()

    private fun String.trimSourceDecoration(): String =
        trim { character -> character.isWhitespace() || character == '•' || character == '-' }

    private fun String.toVlrPathOrNull(): String? = when {
        startsWith("//") -> null
        startsWith("/") -> substringBefore('?').substringBefore('#')
        startsWith("https://www.vlr.gg/") -> removePrefix("https://www.vlr.gg").substringBefore('?').substringBefore('#')
        startsWith("https://vlr.gg/") -> removePrefix("https://vlr.gg").substringBefore('?').substringBefore('#')
        else -> null
    }

    private fun String.toCanonicalNewsPathOrNull(): String? = when {
        startsWith("//") -> null
        startsWith("/") -> this
        startsWith("https://www.vlr.gg/") -> removePrefix("https://www.vlr.gg")
        else -> null
    }

    private fun MutableList<NewsSourceInline>.appendText(rawText: String) {
        val normalized = rawText.replace(whitespace, " ")
        if (normalized.isEmpty()) return

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
