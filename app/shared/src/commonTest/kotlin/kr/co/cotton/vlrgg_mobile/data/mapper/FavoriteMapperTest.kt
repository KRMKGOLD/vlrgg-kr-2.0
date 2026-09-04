package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoritePlayerStorage
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoriteTeamStorage
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteMapperTest {

    @Test
    fun teamRoundTripPreservesNullableAndEmptyStrings() {
        val favorite = FavoriteTeam(
            id = "2",
            name = "",
            tag = null,
            country = "",
            imageUrl = "https://cdn.example.com/drx.png",
        )

        assertEquals(favorite, favorite.toStorage().toDomain())
    }

    @Test
    fun playerRoundTripPreservesNullableAndEmptyStrings() {
        val favorite = FavoritePlayer(
            id = "100",
            handle = "",
            realName = null,
            countryCode = "",
            countryName = null,
        )

        assertEquals(favorite, favorite.toStorage().toDomain())
    }

    @Test
    fun teamAndPlayerUseSeparateStorageModels() {
        val teamStorage = FavoriteTeamStorage("2", "DRX", "DRX", "Korea", "https://cdn.example.com/drx.png")
        val playerStorage = FavoritePlayerStorage("100", "stax", null, "KR", "Korea")

        assertEquals("2", teamStorage.toDomain().id)
        assertEquals("https://cdn.example.com/drx.png", teamStorage.toDomain().imageUrl)
        assertEquals("100", playerStorage.toDomain().id)
    }
}
