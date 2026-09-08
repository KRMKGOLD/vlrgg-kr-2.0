package kr.co.cotton.vlrgg_mobile.data.remote

import kotlin.time.Duration

/** A server-declared, retryable admission response with no transport details exposed to Domain. */
internal class PublicApiBusyException(
    val retryDelay: Duration,
) : RuntimeException()

/** A non-success public API response that is deliberately kept generic for Domain. */
internal class PublicApiResponseException : RuntimeException()
