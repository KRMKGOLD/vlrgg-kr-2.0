package kr.co.cotton.vlrgg_mobile.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android

internal actual fun getHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient {
    val client = HttpClient(Android) {
        configure()
    }
    return client
}