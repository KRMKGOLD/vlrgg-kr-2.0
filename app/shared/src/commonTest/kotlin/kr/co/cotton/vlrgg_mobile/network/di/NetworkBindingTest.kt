package kr.co.cotton.vlrgg_mobile.network.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kr.co.cotton.vlrgg_mobile.network.NetworkConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class NetworkBindingTest {

    @Test
    fun clientIsSharedWithinGraphAndIndependentAcrossGraphs() {
        val firstGraph = createTestNetworkGraph()
        val secondGraph = createTestNetworkGraph()
        val firstClient = firstGraph.httpClient
        val secondClient = secondGraph.httpClient

        try {
            assertSame(firstClient, firstGraph.httpClient)
            assertSame(secondClient, secondGraph.httpClient)
            assertNotSame(firstClient, secondClient)
        } finally {
            firstClient.close()
            secondClient.close()
        }
    }

    @Test
    fun baseUrlIsAppliedToTheRequest() = runTest {
        var requestedUrl: Url? = null
        val client = HttpClient(
            MockEngine { request ->
                requestedUrl = request.url
                respondOk()
            },
        ) {
            configureNetwork(NetworkConfig(baseUrl = TEST_BASE_URL))
        }

        try {
            client.get("/api/v1/news") {
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
            }

            assertEquals("$TEST_BASE_URL/api/v1/news", requestedUrl.toString())
        } finally {
            client.close()
        }
    }

    @Test
    fun nonSuccessfulResponsesAreRejected() = runTest {
        val client = HttpClient(
            MockEngine {
                respond(
                    content = "",
                    status = HttpStatusCode.BadRequest,
                )
            },
        ) {
            configureNetwork(NetworkConfig(baseUrl = TEST_BASE_URL))
        }

        try {
            assertFailsWith<ClientRequestException> {
                client.get("/api/v1/news") {
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    }
                }
            }
        } finally {
            client.close()
        }
    }

    private fun createTestNetworkGraph(): TestNetworkGraph =
        createGraphFactory<TestNetworkGraph.Factory>()
            .create(NetworkConfig(baseUrl = TEST_BASE_URL))

    private companion object {
        const val TEST_BASE_URL = "https://example.invalid"
    }
}

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [NetworkBinding::class],
)
internal interface TestNetworkGraph : ViewModelGraph {
    val httpClient: HttpClient

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides networkConfig: NetworkConfig,
        ): TestNetworkGraph
    }
}
