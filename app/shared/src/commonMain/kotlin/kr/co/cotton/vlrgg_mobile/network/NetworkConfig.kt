package kr.co.cotton.vlrgg_mobile.network

import io.ktor.http.URLProtocol
import io.ktor.http.Url

data class NetworkConfig(
    val baseUrl: String,
) {
    init {
        val parsedUrl = Url(baseUrl)

        require(
            parsedUrl.protocol == URLProtocol.HTTP ||
                    parsedUrl.protocol == URLProtocol.HTTPS,
        ) {
            "API URL이 http, https 로 시작하지 않습니다."
        }

        require(parsedUrl.host.isNotBlank()) {
            "API URL의 host가 비어 있습니다."
        }
    }
}