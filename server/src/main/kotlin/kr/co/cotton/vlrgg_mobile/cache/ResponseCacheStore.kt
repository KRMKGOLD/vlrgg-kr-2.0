package kr.co.cotton.vlrgg_mobile.cache

/**
 * Shared response store seam. Implementations only talk to the store and report every failure as an exception;
 * timeouts, miss conversion, TTL limits and logging belong to [ResponseCache].
 */
internal interface ResponseCacheStore {
    /** Returns the stored value, or null when the key is absent. */
    suspend fun get(key: String): String?

    /** Stores [value] for [ttlSeconds]. Returns false only when [onlyIfAbsent] is set and the key already exists. */
    suspend fun set(key: String, value: String, ttlSeconds: Int, onlyIfAbsent: Boolean): Boolean
}

/** Fixed failure kinds; the only failure detail that may reach a log line. */
internal enum class ResponseCacheFailureKind(val label: String) {
    TIMEOUT("timeout"),
    HTTP("http"),
    IO("io"),
    DECODE("decode"),
}

/** Carries no message: store responses can echo keys or values, which never belong in a log or exception. */
internal class ResponseCacheStoreException(val kind: ResponseCacheFailureKind) : Exception(null, null)
