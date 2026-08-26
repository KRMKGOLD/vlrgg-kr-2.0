package kr.co.cotton.vlrgg_mobile.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
sealed interface AppNavKey : NavKey

@Serializable
sealed interface RootNavKey : AppNavKey

@Serializable
data object NewsRoot : RootNavKey

@Serializable
data object MatchesRoot : RootNavKey

@Serializable
data object MyPageRoot : RootNavKey

@Serializable
data object EventsRoot : RootNavKey

@Serializable
data object AboutRoot : RootNavKey

@Serializable
data object Search : AppNavKey

@Serializable
data class NewsDetail(
    val articleId: String,
    val slug: String,
) : AppNavKey

@Serializable
data class MatchDetail(
    val matchId: String,
) : AppNavKey

@Serializable
data class EventDetail(
    val eventId: String,
) : AppNavKey

@Serializable
data class TeamDetail(
    val teamId: String,
) : AppNavKey

@Serializable
data class PlayerDetail(
    val playerId: String,
) : AppNavKey

@Serializable
data class SeriesDetail(
    val seriesId: String,
) : AppNavKey

/**
 * A persisted overlay instance in a root stack.
 *
 * A destination can appear more than once in the same stack, so the destination alone cannot
 * identify the Navigation 3 entry that owns saveable state and a ViewModelStore.
 */
@Serializable
internal data class OverlayNavEntry(
    val destination: AppNavKey,
    val entryId: Long,
) : NavKey {
    init {
        require(destination !is RootNavKey) {
            "An overlay entry cannot contain a root destination."
        }
        require(entryId > 0) {
            "An overlay entry ID must be positive."
        }
    }
}

val rootNavKeys: List<RootNavKey> = listOf(
    NewsRoot,
    MatchesRoot,
    MyPageRoot,
    EventsRoot,
    AboutRoot,
)

internal val appNavKeySavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(NewsRoot.serializer())
            subclass(MatchesRoot.serializer())
            subclass(MyPageRoot.serializer())
            subclass(EventsRoot.serializer())
            subclass(AboutRoot.serializer())
            subclass(Search.serializer())
            subclass(NewsDetail.serializer())
            subclass(MatchDetail.serializer())
            subclass(EventDetail.serializer())
            subclass(TeamDetail.serializer())
            subclass(PlayerDetail.serializer())
            subclass(SeriesDetail.serializer())
            subclass(OverlayNavEntry.serializer())
        }
    }
}
