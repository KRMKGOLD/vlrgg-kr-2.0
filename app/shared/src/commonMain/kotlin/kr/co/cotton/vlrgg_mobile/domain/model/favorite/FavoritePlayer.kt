package kr.co.cotton.vlrgg_mobile.domain.model.favorite

data class FavoritePlayer(
    val id: String,
    val handle: String,
    val realName: String?,
    val countryCode: String?,
    val countryName: String?,
)
