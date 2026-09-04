package kr.co.cotton.vlrgg_mobile.domain.model.favorite

data class FavoriteTeam(
    val id: String,
    val name: String,
    val tag: String?,
    val country: String?,
    val imageUrl: String? = null,
)
