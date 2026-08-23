package kr.co.cotton.vlrgg_mobile.data.remote.model.matches

import kotlinx.serialization.Serializable

@Serializable
internal data class MatchesPageResponseDto(
    val category: MatchListCategoryDto,
    val page: Int,
    val groups: List<MatchDateGroupDto>,
)
