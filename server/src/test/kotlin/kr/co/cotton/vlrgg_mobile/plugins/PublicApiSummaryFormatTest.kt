package kr.co.cotton.vlrgg_mobile.plugins

import kr.co.cotton.vlrgg_mobile.common.http.RateLimitedFailure
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicApiSummaryFormatTest {
    @Test
    fun `summary keeps every existing field and appends the cache counters last`() {
        val observability = PublicApiObservability { 0L }
        observability.requestStarted(PublicRouteClass.API)
        observability.rejected(RateLimitedFailure(), elapsedMillis = 1)
        repeat(3) { observability.responseCache(ResponseCacheCounter.HIT) }
        observability.responseCache(ResponseCacheCounter.NEGATIVE_HIT)
        repeat(2) { observability.responseCache(ResponseCacheCounter.MISS) }
        repeat(4) { observability.responseCache(ResponseCacheCounter.JOINED) }

        assertEquals(
            "public_api_summary requests=1 routes={api=1,other=0} status={0xx=0,1xx=0,2xx=0,3xx=0,4xx=1,5xx=0} " +
                "rejections={INVALID_REQUEST=0,NOT_FOUND=0,UPSTREAM_NETWORK_FAILURE=0,SOURCE_PARSING_FAILURE=0," +
                "INTERNAL_ERROR=0,REQUEST_TOO_LARGE=0,RESPONSE_TOO_LARGE=0,RATE_LIMITED=1,SERVER_BUSY=0,REQUEST_TIMEOUT=0} " +
                "latency={lt_100ms=1,100ms_to_lt_1s=0,1s_to_lt_3s=0,gte_3s=0} active_api=0 active_upstream=0 " +
                "joins=0 upstream_failures=0 " +
                "diagnostics_emitted={EXPECTED=0,UPSTREAM_NETWORK=0,INTERNAL=0,SOURCE_PARSING=0} " +
                "diagnostics_suppressed={EXPECTED=0,UPSTREAM_NETWORK=0,INTERNAL=0,SOURCE_PARSING=0} " +
                "telemetry_failures=0 cache={hit=3,negative_hit=1,miss=2,joined=4}",
            formatPublicApiSummary(observability.snapshot()),
        )
    }
}
