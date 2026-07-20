package kr.co.cotton.vlrgg_mobile.feature.news

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr.co.cotton.vlrgg_mobile.common.http.InvalidInputFailure
import kr.co.cotton.vlrgg_mobile.common.scraping.NewsReference

private val pagePattern = Regex("[1-9][0-9]{0,4}")

internal fun Route.configureNewsRoutes(service: NewsService) {
    route("/api/v1/news") {
        get {
            call.respond(service.getList(call.requireNewsPage()))
        }
        get("/{articleId}/{slug}") {
            call.respond(service.getArticle(call.requireNewsReference()))
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
