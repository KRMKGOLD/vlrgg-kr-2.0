package kr.co.cotton.vlrgg_mobile.ui.navigation

data class DestinationDescriptor(
    val marker: String,
    val title: String,
    val isRoot: Boolean,
    val rootOrder: Int?,
    val showBottomBar: Boolean,
    val searchAvailable: Boolean,
    val requiresEntryScope: Boolean,
)

val AppNavKey.destinationDescriptor: DestinationDescriptor
    get() = when (this) {
        NewsRoot -> rootDescriptor(
            marker = "news",
            title = "News",
            rootOrder = 0,
        )

        MatchesRoot -> rootDescriptor(
            marker = "matches",
            title = "Matches",
            rootOrder = 1,
        )

        MyPageRoot -> rootDescriptor(
            marker = "my_page",
            title = "My Page",
            rootOrder = 2,
            requiresEntryScope = true,
        )

        EventsRoot -> rootDescriptor(
            marker = "events",
            title = "Events",
            rootOrder = 3,
        )

        AboutRoot -> rootDescriptor(
            marker = "about",
            title = "About",
            rootOrder = 4,
        )

        Search -> pushedDescriptor(
            marker = "search",
            title = "Search",
        )

        is NewsDetail -> pushedDescriptor(
            marker = "news_detail",
            title = "News Detail",
        )

        is MatchDetail -> pushedDescriptor(
            marker = "match_detail",
            title = "Match Detail",
        )

        is EventDetail -> pushedDescriptor(
            marker = "event_detail",
            title = "Event Detail",
        )

        is TeamDetail -> pushedDescriptor(
            marker = "team_detail",
            title = "Team Detail",
        )

        is PlayerDetail -> pushedDescriptor(
            marker = "player_detail",
            title = "Player Detail",
        )

        is SeriesDetail -> pushedDescriptor(
            marker = "series_detail",
            title = "Series Detail",
        )
    }

private fun rootDescriptor(
    marker: String,
    title: String,
    rootOrder: Int,
    requiresEntryScope: Boolean = false,
) = DestinationDescriptor(
    marker = marker,
    title = title,
    isRoot = true,
    rootOrder = rootOrder,
    showBottomBar = true,
    searchAvailable = true,
    requiresEntryScope = requiresEntryScope,
)

private fun pushedDescriptor(
    marker: String,
    title: String,
) = DestinationDescriptor(
    marker = marker,
    title = title,
    isRoot = false,
    rootOrder = null,
    showBottomBar = false,
    searchAvailable = false,
    requiresEntryScope = false,
)
