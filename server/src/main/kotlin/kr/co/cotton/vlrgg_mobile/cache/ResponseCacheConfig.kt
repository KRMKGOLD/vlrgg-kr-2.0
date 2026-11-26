package kr.co.cotton.vlrgg_mobile.cache

import io.ktor.http.*

internal const val RESPONSE_CACHE_ENABLED_ENVIRONMENT_VARIABLE = "VLRGG_RESPONSE_CACHE_ENABLED"
internal const val UPSTASH_REST_URL_ENVIRONMENT_VARIABLE = "VLRGG_UPSTASH_REDIS_REST_URL"
internal const val UPSTASH_REST_TOKEN_ENVIRONMENT_VARIABLE = "VLRGG_UPSTASH_REDIS_REST_TOKEN"
internal const val DEPLOY_SHA_ENVIRONMENT_VARIABLE = "VLRGG_DEPLOY_SHA"

private val DEPLOY_SHA_REGEX = Regex("[A-Za-z0-9._-]{1,64}")
private val REST_HOST_REGEX = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+")
private const val MAX_TOKEN_LENGTH = 1_024
private const val MAX_URL_LENGTH = 512

/**
 * The cache is on only when [RESPONSE_CACHE_ENABLED_ENVIRONMENT_VARIABLE] is exactly `true`.
 * An enabled cache with a missing or malformed value refuses to start instead of silently running without
 * a deploy-scoped key space. Exception messages name the variable only, never its value.
 */
internal sealed interface ResponseCacheConfig {
    data object Disabled : ResponseCacheConfig

    class Enabled(val restUrl: Url, val token: String, val deploySha: String) : ResponseCacheConfig {
        init {
            require(restUrl.isAllowedUpstashRestUrl()) { "$UPSTASH_REST_URL_ENVIRONMENT_VARIABLE is invalid." }
            require(token.isValidToken()) { "$UPSTASH_REST_TOKEN_ENVIRONMENT_VARIABLE is invalid." }
            require(DEPLOY_SHA_REGEX.matches(deploySha)) { "$DEPLOY_SHA_ENVIRONMENT_VARIABLE is invalid." }
        }

        /** The URL and token are secrets, so they never appear through string conversion. */
        override fun toString(): String = "ResponseCacheConfig.Enabled(deploySha=$deploySha)"
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String>): ResponseCacheConfig {
            if (environment[RESPONSE_CACHE_ENABLED_ENVIRONMENT_VARIABLE] != "true") return Disabled
            val rawUrl = environment.required(UPSTASH_REST_URL_ENVIRONMENT_VARIABLE)
            val token = environment.required(UPSTASH_REST_TOKEN_ENVIRONMENT_VARIABLE)
            val deploySha = environment.required(DEPLOY_SHA_ENVIRONMENT_VARIABLE)
            return Enabled(restUrl = parseRestUrl(rawUrl), token = token, deploySha = deploySha)
        }

        private fun Map<String, String>.required(name: String): String =
            this[name]?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("$name is required when the response cache is enabled.")

        /** Parse failures are not chained: a parser exception message can contain the raw URL. */
        private fun parseRestUrl(raw: String): Url {
            val invalid = { IllegalArgumentException("$UPSTASH_REST_URL_ENVIRONMENT_VARIABLE is invalid.") }
            if (raw.length > MAX_URL_LENGTH || !raw.startsWith("https://")) throw invalid()
            val url = try {
                Url(raw)
            } catch (_: Exception) {
                throw invalid()
            }
            if (!url.isAllowedUpstashRestUrl()) throw invalid()
            return url
        }
    }
}

/** HTTPS on the default port with no user info, path, query or fragment, so requests go only to the database root. */
private fun Url.isAllowedUpstashRestUrl(): Boolean =
    protocol == URLProtocol.HTTPS &&
        specifiedPort.let { it == 0 || it == URLProtocol.HTTPS.defaultPort } &&
        user.isNullOrEmpty() &&
        password.isNullOrEmpty() &&
        REST_HOST_REGEX.matches(host) &&
        (encodedPath.isEmpty() || encodedPath == "/") &&
        encodedQuery.isEmpty() &&
        encodedFragment.isEmpty()

/** Visible ASCII only, so the token cannot split or extend the Authorization header. */
private fun String.isValidToken(): Boolean =
    length in 1..MAX_TOKEN_LENGTH && all { it in '!'..'~' }
