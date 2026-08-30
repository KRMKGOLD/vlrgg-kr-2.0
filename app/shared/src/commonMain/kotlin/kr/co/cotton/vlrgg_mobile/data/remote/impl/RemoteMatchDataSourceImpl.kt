package kr.co.cotton.vlrgg_mobile.data.remote.impl

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteMatchDataSource
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchesPageResponseDto
import kr.co.cotton.vlrgg_mobile.data.remote.model.matches.MatchDetailResponseDto

@Inject
internal class RemoteMatchDataSourceImpl(
    private val httpClient: HttpClient,
) : RemoteMatchDataSource {

    override suspend fun getUpcomingMatches(page: Int): MatchesPageResponseDto =
        getMatchPage(path = UPCOMING_MATCHES_PATH, page = page)

    override suspend fun getResults(page: Int): MatchesPageResponseDto =
        getMatchPage(path = RESULTS_PATH, page = page)

    override suspend fun getMatchDetail(matchId: String): MatchDetailResponseDto =
        httpClient.get("$MATCHES_PATH/$matchId").body()

    private suspend fun getMatchPage(
        path: String,
        page: Int,
    ): MatchesPageResponseDto = httpClient.get(path) {
        parameter("page", page)
    }.body()

    private companion object {
        const val MATCHES_PATH = "/api/v1/matches"
        const val UPCOMING_MATCHES_PATH = "$MATCHES_PATH/upcoming"
        const val RESULTS_PATH = "$MATCHES_PATH/results"
    }
}
