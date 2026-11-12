package kr.co.cotton.vlrgg_mobile.data.remote.impl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteSeriesDataSourceImplTest {

    @Test
    fun getSeriesDetailRequestsTheSeriesEndpointAndPreservesNullables() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/series/2", request.url.encodedPath)
            respond(SERIES_JSON, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://example.invalid") }
        }

        try {
            val response = RemoteSeriesDataSourceImpl(client).getSeriesDetail("2")

            assertEquals("Champions Tour", response.name)
            assertNull(response.description)
            assertEquals(listOf("200", "201"), response.upcomingEvents.map { it.id })
            assertEquals(listOf("100"), response.completedEvents.map { it.id })
            assertNull(response.upcomingEvents.first().imageUrl)
            // Period fields are absent on the first event and present on the others.
            assertNull(response.upcomingEvents.first().startDate)
            assertEquals("--09-12" to "--10-04", response.upcomingEvents[1].let { it.startDate to it.endDate })
            assertEquals("--01-15" to null, response.completedEvents.single().let { it.startDate to it.endDate })
        } finally {
            client.close()
        }
    }

    private companion object {
        val SERIES_JSON = """
            {"id":"2","name":"Champions Tour","description":null,
             "upcomingEvents":[
               {"id":"200","name":"Masters","status":"upcoming","dateLabel":null,"regionCode":null,"imageUrl":null},
               {"id":"201","name":"Champions","status":"ongoing","dateLabel":"Sep","regionCode":"INT","imageUrl":null,
                "startDate":"--09-12","endDate":"--10-04"}],
             "completedEvents":[
               {"id":"100","name":"Kickoff","status":"completed","dateLabel":"Jan","regionCode":"KR","imageUrl":null,
                "startDate":"--01-15","endDate":null}]}
        """.trimIndent()
    }
}
