package kr.co.cotton.vlrgg_mobile.cache

internal const val DEFAULT_RESPONSE_CACHE_MAX_BYTES = 32 * 1024 * 1024

/**
 * Process-local response cache settings. The cache is on by default in every environment; an unknown flag value
 * or a non-positive capacity refuses server start, like [kr.co.cotton.vlrgg_mobile.protection.PublicApiProtectionConfig].
 */
internal data class ResponseCacheConfig(
    val enabled: Boolean = true,
    val maxBytes: Int = DEFAULT_RESPONSE_CACHE_MAX_BYTES,
) {
    init {
        require(maxBytes > 0) { "Response cache capacity must be positive." }
    }

    companion object {
        const val ENABLED_ENVIRONMENT_VARIABLE = "VLRGG_RESPONSE_CACHE_ENABLED"
        const val MAX_BYTES_ENVIRONMENT_VARIABLE = "VLRGG_RESPONSE_CACHE_MAX_BYTES"

        val DISABLED = ResponseCacheConfig(enabled = false)

        fun fromEnvironment(environment: Map<String, String>): ResponseCacheConfig = ResponseCacheConfig(
            enabled = when (environment[ENABLED_ENVIRONMENT_VARIABLE]) {
                null, "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("$ENABLED_ENVIRONMENT_VARIABLE must be true or false.")
            },
            maxBytes = environment[MAX_BYTES_ENVIRONMENT_VARIABLE]?.let { raw ->
                raw.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException("$MAX_BYTES_ENVIRONMENT_VARIABLE must be a positive integer.")
            } ?: DEFAULT_RESPONSE_CACHE_MAX_BYTES,
        )
    }
}
