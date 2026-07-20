package kr.co.cotton.vlrgg_mobile.feature.teams

import kr.co.cotton.vlrgg_mobile.common.scraping.UpstreamHtmlTransport

/** Feature-local composition; application lifecycle owns the shared upstream transport. */
internal fun createTeamDetailService(transport: UpstreamHtmlTransport): TeamDetailService = TeamDetailService(
    scraper = TeamDetailScraper(transport),
    parser = TeamDetailParser(),
    mapper = TeamDetailMapper(),
)
