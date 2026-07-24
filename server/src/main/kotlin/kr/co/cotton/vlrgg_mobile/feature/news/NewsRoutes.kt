package kr.co.cotton.vlrgg_mobile.feature.news

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.NewsReference
import kr.co.cotton.vlrgg_mobile.routing.describePublicGet

private val pagePattern = Regex("[1-9][0-9]{0,4}")

internal fun Route.configureNewsRoutes(service: NewsService) {
    route("/api/v1/news") {
        get {
            call.respond(service.getList(call.requireNewsPage()))
        }.describePublicGet<NewsListResponse>(
            operationId = "getNewsList",
            summary = "Get news articles",
            operationDescription = "Returns a page of current news articles. Only the optional page query parameter is accepted.",
            tag = "News",
        ) {
            query("page") {
                description = "Optional decimal page number from 1 through 99,999 without leading zeroes. Defaults to 1."
                required = false
            }
        }
        get("/{articleId}/{slug}") {
            call.respond(service.getArticle(call.requireNewsReference()))
        }.describePublicGet<NewsArticleResponse>(
            operationId = "getNewsArticle",
            summary = "Get a news article",
            operationDescription = "Returns an article identified by its canonical numeric ID and slug. Query parameters are not accepted.",
            tag = "News",
        ) {
            path("articleId") {
                description = "Positive decimal article ID containing up to 10 digits and no leading zeroes."
                required = true
            }
            path("slug") {
                description = "Canonical article slug paired with the article ID."
                required = true
            }
        }
    }
}

private fun ApplicationCall.requireNewsPage(): Int {
    val query = request.queryParameters
    if (!query.names().all { it == "page" }) {
        throw InvalidInputFailure()
    }
    val values = query.getAll("page") ?: return DEFAULT_NEWS_PAGE
    if (values.size != 1 || !pagePattern.matches(values.single())) {
        throw InvalidInputFailure()
    }
    return values.single().toIntOrNull()?.takeIf { it in DEFAULT_NEWS_PAGE..MAX_NEWS_PAGE }
        ?: throw InvalidInputFailure()
}

private fun ApplicationCall.requireNewsReference(): NewsReference =
    NewsReference.fromPath(
        articleId = parameters["articleId"] ?: throw InvalidInputFailure(),
        slug = parameters["slug"] ?: throw InvalidInputFailure(),
    ) ?: throw InvalidInputFailure()
