package kr.co.cotton.vlrgg_mobile.data.mapper

import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoritePlayerStorage
import kr.co.cotton.vlrgg_mobile.data.local.datastore.model.FavoriteTeamStorage
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoritePlayer
import kr.co.cotton.vlrgg_mobile.domain.model.favorite.FavoriteTeam

internal fun FavoriteTeamStorage.toDomain(): FavoriteTeam = FavoriteTeam(
    id = id,
    name = name,
    tag = tag,
    country = country,
    imageUrl = imageUrl,
)

internal fun FavoriteTeam.toStorage(): FavoriteTeamStorage = FavoriteTeamStorage(
    id = id,
    name = name,
    tag = tag,
    country = country,
    imageUrl = imageUrl,
)

internal fun FavoritePlayerStorage.toDomain(): FavoritePlayer = FavoritePlayer(
    id = id,
    handle = handle,
    realName = realName,
    countryCode = countryCode,
    countryName = countryName,
    imageUrl = imageUrl,
)

internal fun FavoritePlayer.toStorage(): FavoritePlayerStorage = FavoritePlayerStorage(
    id = id,
    handle = handle,
    realName = realName,
    countryCode = countryCode,
    countryName = countryName,
    imageUrl = imageUrl,
)
