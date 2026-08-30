package kr.co.cotton.vlrgg_mobile.data.local.datastore.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteTeamStorage(
    val id: String,
    val name: String,
    val tag: String?,
    val country: String?,
)

@Serializable
data class FavoritePlayerStorage(
    val id: String,
    val handle: String,
    val realName: String?,
    val countryCode: String?,
    val countryName: String?,
)

@Serializable
data class FavoriteTeamsStorage(
    val favorites: List<FavoriteTeamStorage> = emptyList(),
)

@Serializable
data class FavoritePlayersStorage(
    val favorites: List<FavoritePlayerStorage> = emptyList(),
)
