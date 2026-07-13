package kr.co.cotton.vlrgg_mobile

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kr.co.cotton.vlrgg_mobile.common.scraping.createUpstreamHtmlTransport
import kr.co.cotton.vlrgg_mobile.feature.news.NewsMapper
import kr.co.cotton.vlrgg_mobile.feature.news.NewsParser
import kr.co.cotton.vlrgg_mobile.feature.news.NewsScraper
import kr.co.cotton.vlrgg_mobile.feature.news.NewsService
import kr.co.cotton.vlrgg_mobile.plugins.configureErrorHandling
import kr.co.cotton.vlrgg_mobile.plugins.configureMonitoring
import kr.co.cotton.vlrgg_mobile.plugins.configureSerialization
import kr.co.cotton.vlrgg_mobile.routing.configureRouting

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

internal fun Application.module(newsService: NewsService? = null) {
    configureSerialization()
    configureMonitoring()
    configureErrorHandling()
    configureRouting(
        newsService = newsService ?: NewsService(
            scraper = NewsScraper(createUpstreamHtmlTransport()),
            parser = NewsParser(),
            mapper = NewsMapper(),
        ),
    )
}
