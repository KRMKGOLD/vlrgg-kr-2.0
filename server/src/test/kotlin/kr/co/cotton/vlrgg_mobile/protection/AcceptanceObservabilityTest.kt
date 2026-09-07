package kr.co.cotton.vlrgg_mobile.protection

import io.ktor.http.Url
import kr.co.cotton.vlrgg_mobile.common.http.RateLimitedFailure
import kr.co.cotton.vlrgg_mobile.common.http.ServerBusyFailure
import kr.co.cotton.vlrgg_mobile.common.http.UpstreamNetworkFailure
import kr.co.cotton.vlrgg_mobile.plugins.PublicApiObservability
import kr.co.cotton.vlrgg_mobile.plugins.PublicRouteClass
import kotlin.test.*

class AcceptanceObservabilityTest {
    @Test
    fun `rejection counters match 429 503 and 502 statuses without retaining unsafe request strings`() {
        val emitted = mutableListOf<PublicApiObservability.Snapshot>()
        val observability = PublicApiObservability(emit = { emitted += it }) { 0L }
        val sentinel = "credential-sentinel-123"

        observability.requestStarted(PublicRouteClass.API)
        observability.rejected(RateLimitedFailure(), elapsedMillis = 1)
        observability.requestStarted(PublicRouteClass.API)
        observability.rejected(ServerBusyFailure(), elapsedMillis = 2)
        observability.requestStarted(PublicRouteClass.OTHER)
        observability.rejected(UpstreamNetworkFailure(Url("https://www.vlr.gg/private?$sentinel")), elapsedMillis = 3)

        val snapshot = observability.snapshot()
        assertEquals(3, snapshot.requests)
        assertEquals(1, snapshot.statusClasses[4])
        assertEquals(2, snapshot.statusClasses[5])
        assertEquals(1, snapshot.rejections.getValue(kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode.RATE_LIMITED))
        assertEquals(1, snapshot.rejections.getValue(kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode.SERVER_BUSY))
        assertEquals(1, snapshot.rejections.getValue(kr.co.cotton.vlrgg_mobile.common.http.ApiErrorCode.UPSTREAM_NETWORK_FAILURE))
        assertEquals(1, snapshot.upstreamFailures)
        assertFalse(snapshot.toString().contains(sentinel))
        assertEquals(1, emitted.size, "the first completion emits an initial aggregate summary")
        assertEquals(1, emitted.single().requests)
        assertEquals(1, emitted.single().statusClasses[4])
        assertEquals(0, emitted.single().statusClasses[5])
    }
}
