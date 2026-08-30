package kr.co.cotton.vlrgg_mobile.data.repository

import dev.zacsweers.metro.Inject
import kr.co.cotton.vlrgg_mobile.data.mapper.toDomain
import kr.co.cotton.vlrgg_mobile.data.remote.RemoteMatchDataSource
import kr.co.cotton.vlrgg_mobile.domain.AppResult
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchPage
import kr.co.cotton.vlrgg_mobile.domain.model.matches.MatchDetail
import kr.co.cotton.vlrgg_mobile.domain.repository.MatchRepository

@Inject
internal class MatchRepositoryImpl(
    private val remoteMatchDataSource: RemoteMatchDataSource,
) : MatchRepository {

    override suspend fun getUpcomingMatches(page: Int): AppResult<MatchPage> =
        wrapAsAppResult {
            remoteMatchDataSource.getUpcomingMatches(page).toDomain()
        }

    override suspend fun getResults(page: Int): AppResult<MatchPage> =
        wrapAsAppResult {
            remoteMatchDataSource.getResults(page).toDomain()
        }

    override suspend fun getMatchDetail(matchId: String): AppResult<MatchDetail> =
        wrapAsAppResult {
            remoteMatchDataSource.getMatchDetail(matchId).toDomain()
        }
}
