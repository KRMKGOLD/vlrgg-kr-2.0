package kr.co.cotton.vlrgg_mobile.network.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kr.co.cotton.vlrgg_mobile.network.NetworkConfig
import kr.co.cotton.vlrgg_mobile.network.getHttpClient

@BindingContainer
object NetworkBinding {

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(
        config: NetworkConfig
    ) : HttpClient = getHttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }

        defaultRequest {
            url(config.baseUrl)
            accept(ContentType.Application.Json)
        }
    }
}