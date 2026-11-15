package kr.co.cotton.vlrgg_mobile.ui.navigation

data class DestinationDescriptor(
    val marker: String,
    val title: String,
    val showBottomBar: Boolean,
)

val AppNavKey.destinationDescriptor: DestinationDescriptor
    get() = when (this) {
        NewsRoot -> rootDescriptor(
            marker = "news",
            title = "뉴스",
        )

        MatchesRoot -> rootDescriptor(
            marker = "matches",
            title = "경기",
        )

        MyPageRoot -> rootDescriptor(
            marker = "my_page",
            title = "마이페이지",
        )

        EventsRoot -> rootDescriptor(
            marker = "events",
            title = "대회",
        )

        AboutRoot -> rootDescriptor(
            marker = "about",
            title = "정보",
        )

        Search -> pushedDescriptor(
            marker = "search",
            title = "Search",
        )

        OpenSourceLicenses -> pushedDescriptor(
            marker = "open_source_licenses",
            title = "Open Source Licenses",
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
) = DestinationDescriptor(
    marker = marker,
    title = title,
    showBottomBar = true,
)

private fun pushedDescriptor(
    marker: String,
    title: String,
) = DestinationDescriptor(
    marker = marker,
    title = title,
    showBottomBar = false,
)
