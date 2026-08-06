package kr.co.cotton.vlrgg_mobile.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

internal expect fun getHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient