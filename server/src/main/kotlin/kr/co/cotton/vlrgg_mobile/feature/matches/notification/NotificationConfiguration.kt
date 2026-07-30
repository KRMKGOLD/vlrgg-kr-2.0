package kr.co.cotton.vlrgg_mobile.feature.matches.notification

import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest

/** Immutable listener inputs shared by Netty construction and notification API preflight. */
data class ServerListenerConfiguration(
    val host: String,
    val port: Int,
) {
    companion object {
        private val hostPattern = Regex("^[A-Za-z0-9.:-]{1,253}$")

        fun fromEnvironment(environment: Map<String, String>): ServerListenerConfiguration {
            val host = environment["VLRGG_SERVER_HOST"] ?: "0.0.0.0"
            if (!hostPattern.matches(host)) {
                throw NotificationConfigurationException(ConfigurationCategory.INVALID_SYNTAX, ConfigurationField.LISTENER_HOST)
            }
            return ServerListenerConfiguration(host, strictUnsignedInt(environment["VLRGG_SERVER_PORT"] ?: "8080", ConfigurationField.LISTENER_PORT, 1, 65535))
        }
    }
}

enum class ConfigurationCategory {
    MISSING_REQUIRED, INVALID_BOOLEAN, INVALID_INTEGER, OUT_OF_RANGE, INVALID_SYNTAX, INVALID_CROSS_FIELD,
    UNSAFE_LISTENER, UNSAFE_STORAGE, UNSUPPORTED_OWNERSHIP, UNSUPPORTED_TARGET_MODE, TARGET_MODE_MISMATCH,
    FIREBASE_APP_NAME_COLLISION, INLINE_CREDENTIAL_UNSUPPORTED, DIGEST_KEY_MISMATCH,
}

enum class ConfigurationField {
    LISTENER_HOST, LISTENER_PORT, FEATURE_ENABLED, API_ENABLED, EXPOSURE, OWNERSHIP, STORAGE_PATH, JDBC_POOL_SIZE,
    REQUEST_BODY_BYTES, REGISTRATION_VALUE_BYTES, ACTIVE_SUBSCRIPTIONS, POLL_DELAY_MILLIS, DELIVERY_TIMEOUT_MILLIS,
    CLAIM_LEASE_MILLIS, MAX_APPLICATION_ATTEMPTS, INITIAL_RETRY_MILLIS, MAX_RETRY_MILLIS, RETRY_JITTER_MILLIS,
    PROVIDER_RETRY_CEILING_MILLIS, FIREBASE_PROJECT_ID, APP_INSTANCE_ID, TARGET_MODE, LOOKUP_DIGEST_KEY, CREDENTIAL_SOURCE,
}

class NotificationConfigurationException(
    val category: ConfigurationCategory,
    val field: ConfigurationField,
    cause: Throwable? = null,
) : IllegalArgumentException("notification configuration rejected: $category/$field", cause)

enum class NotificationExposure { LOCAL, PUBLIC, PRODUCTION }
enum class NotificationOwnership { SINGLE_PROCESS, MULTI_INSTANCE }
enum class FirebaseTargetMode { FID, LEGACY_TOKEN }

data class NotificationConfiguration(
    val enabled: Boolean,
    val apiEnabled: Boolean,
    val exposure: NotificationExposure,
    val ownership: NotificationOwnership,
    val databasePath: Path?,
    val jdbcPoolSize: Int,
    val requestBodyBytes: Int,
    val registrationValueMaxBytes: Int,
    val activeSubscriptionsMax: Int,
    val pollDelayMillis: Long,
    val deliveryTimeoutMillis: Long,
    val claimLeaseMillis: Long,
    val maxApplicationAttempts: Int,
    val initialRetryMillis: Long,
    val maxRetryMillis: Long,
    val retryJitterMillis: Long,
    val providerRetryCeilingMillis: Long,
    val firebaseProjectId: String?,
    val appInstanceId: String,
    val targetMode: FirebaseTargetMode,
    val lookupDigestKey: ByteArray?,
    val firebaseAppName: String?,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String>, listener: ServerListenerConfiguration): NotificationConfiguration {
            val feature = parseFeature(environment)
            val limits = parseLimits(environment)
            validateCrossFields(feature, limits, listener)
            if (!feature.enabled) return disabledConfiguration(feature, limits)
            validateEnabledFeature(feature)
            val path = safeStoragePath(requireNotNull(feature.storage))
            return NotificationConfiguration(true, feature.apiEnabled, feature.exposure, feature.ownership, path, limits.pool, limits.requestBody, limits.registration, limits.subscriptions, limits.pollDelay, limits.timeout, limits.lease, limits.attempts, limits.initialRetry, limits.maxRetry, limits.jitter, limits.providerCeiling, requireNotNull(feature.project), feature.instance, feature.mode, requireNotNull(feature.lookupKey), firebaseAppName(requireNotNull(feature.project), feature.instance))
        }

        private fun parseFeature(environment: Map<String, String>): ParsedFeature {
            val enabled = strictBoolean(environment["VLRGG_NOTIFICATIONS_ENABLED"] ?: "false", ConfigurationField.FEATURE_ENABLED)
            val apiEnabled = strictBoolean(environment["VLRGG_NOTIFICATIONS_API_ENABLED"] ?: "false", ConfigurationField.API_ENABLED)
            val exposure = enumValue(environment["VLRGG_NOTIFICATIONS_EXPOSURE"] ?: "LOCAL", NotificationExposure::valueOf, ConfigurationField.EXPOSURE, ConfigurationCategory.INVALID_SYNTAX)
            val ownership = enumValue(environment["VLRGG_NOTIFICATIONS_OWNERSHIP"] ?: "SINGLE_PROCESS", NotificationOwnership::valueOf, ConfigurationField.OWNERSHIP, ConfigurationCategory.UNSUPPORTED_OWNERSHIP)
            val mode = enumValue(environment["VLRGG_NOTIFICATIONS_FIREBASE_TARGET_MODE"] ?: "FID", FirebaseTargetMode::valueOf, ConfigurationField.TARGET_MODE, ConfigurationCategory.UNSUPPORTED_TARGET_MODE)
            val storage = if (enabled) environment["VLRGG_NOTIFICATIONS_STORAGE_PATH"] ?: throw NotificationConfigurationException(ConfigurationCategory.MISSING_REQUIRED, ConfigurationField.STORAGE_PATH) else null
            val project = if (enabled) environment["VLRGG_NOTIFICATIONS_FIREBASE_PROJECT_ID"] ?: throw NotificationConfigurationException(ConfigurationCategory.MISSING_REQUIRED, ConfigurationField.FIREBASE_PROJECT_ID) else null
            if (project != null && !Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$").matches(project)) throw NotificationConfigurationException(ConfigurationCategory.INVALID_SYNTAX, ConfigurationField.FIREBASE_PROJECT_ID)
            val instance = environment["VLRGG_NOTIFICATIONS_APP_INSTANCE_ID"] ?: "main"
            if (enabled && !Regex("^[a-z](?:[a-z0-9-]{0,30}[a-z0-9])?$").matches(instance)) throw NotificationConfigurationException(ConfigurationCategory.INVALID_SYNTAX, ConfigurationField.APP_INSTANCE_ID)
            if (enabled && environment.containsKey("VLRGG_NOTIFICATIONS_CREDENTIAL_JSON")) throw NotificationConfigurationException(ConfigurationCategory.INLINE_CREDENTIAL_UNSUPPORTED, ConfigurationField.CREDENTIAL_SOURCE)
            val key = if (enabled) decodeLookupKey(environment["VLRGG_NOTIFICATION_LOOKUP_DIGEST_KEY"] ?: throw NotificationConfigurationException(ConfigurationCategory.MISSING_REQUIRED, ConfigurationField.LOOKUP_DIGEST_KEY)) else null
            return ParsedFeature(enabled, apiEnabled, exposure, ownership, storage, project, instance, mode, key)
        }

        private fun parseLimits(environment: Map<String, String>): ParsedLimits {
            val requestBody = strictUnsignedInt(environment["VLRGG_NOTIFICATIONS_REQUEST_BODY_BYTES"] ?: "8192", ConfigurationField.REQUEST_BODY_BYTES, 1024, 65536)
            val registration = strictUnsignedInt(environment["VLRGG_NOTIFICATIONS_REGISTRATION_VALUE_BYTES"] ?: "4096", ConfigurationField.REGISTRATION_VALUE_BYTES, 256, 16384)
            val subscriptions = strictUnsignedInt(environment["VLRGG_NOTIFICATIONS_ACTIVE_SUBSCRIPTIONS"] ?: "100", ConfigurationField.ACTIVE_SUBSCRIPTIONS, 1, 1000)
            val pool = strictUnsignedInt(environment["VLRGG_NOTIFICATIONS_JDBC_POOL_SIZE"] ?: "4", ConfigurationField.JDBC_POOL_SIZE, 1, 8)
            val pollDelay = strictUnsignedLong(environment["VLRGG_NOTIFICATIONS_POLL_DELAY_MILLIS"] ?: "600000", ConfigurationField.POLL_DELAY_MILLIS, 60000, 86400000)
            val timeout = strictUnsignedLong(environment["VLRGG_NOTIFICATIONS_DELIVERY_TIMEOUT_MILLIS"] ?: "30000", ConfigurationField.DELIVERY_TIMEOUT_MILLIS, 1000, 60000)
            val lease = strictUnsignedLong(environment["VLRGG_NOTIFICATIONS_CLAIM_LEASE_MILLIS"] ?: "120000", ConfigurationField.CLAIM_LEASE_MILLIS, 10000, 600000)
            val attempts = strictUnsignedInt(environment["VLRGG_NOTIFICATIONS_MAX_APPLICATION_ATTEMPTS"] ?: "5", ConfigurationField.MAX_APPLICATION_ATTEMPTS, 1, 10)
            val initialRetry = strictUnsignedLong(environment["VLRGG_NOTIFICATIONS_INITIAL_RETRY_MILLIS"] ?: "30000", ConfigurationField.INITIAL_RETRY_MILLIS, 1000, 600000)
            val maxRetry = strictUnsignedLong(environment["VLRGG_NOTIFICATIONS_MAX_RETRY_MILLIS"] ?: "3600000", ConfigurationField.MAX_RETRY_MILLIS, initialRetry, 86400000)
            val jitter = strictUnsignedLong(environment["VLRGG_NOTIFICATIONS_RETRY_JITTER_MILLIS"] ?: "5000", ConfigurationField.RETRY_JITTER_MILLIS, 0, 60000)
            val providerCeiling = strictUnsignedLong(environment["VLRGG_NOTIFICATIONS_PROVIDER_RETRY_CEILING_MILLIS"] ?: "86400000", ConfigurationField.PROVIDER_RETRY_CEILING_MILLIS, 60000, 604800000)
            return ParsedLimits(pool, requestBody, registration, subscriptions, pollDelay, timeout, lease, attempts, initialRetry, maxRetry, jitter, providerCeiling)
        }

        private fun validateCrossFields(feature: ParsedFeature, limits: ParsedLimits, listener: ServerListenerConfiguration) {
            if (limits.lease <= limits.timeout) throw NotificationConfigurationException(ConfigurationCategory.INVALID_CROSS_FIELD, ConfigurationField.CLAIM_LEASE_MILLIS)
            if (limits.jitter > limits.maxRetry) throw NotificationConfigurationException(ConfigurationCategory.INVALID_CROSS_FIELD, ConfigurationField.RETRY_JITTER_MILLIS)
            if (feature.apiEnabled && listener.host !in setOf("127.0.0.1", "::1")) throw NotificationConfigurationException(ConfigurationCategory.UNSAFE_LISTENER, ConfigurationField.LISTENER_HOST)
            if (feature.apiEnabled && !feature.enabled) throw NotificationConfigurationException(ConfigurationCategory.INVALID_CROSS_FIELD, ConfigurationField.API_ENABLED)
        }

        private fun validateEnabledFeature(feature: ParsedFeature) {
            if (feature.exposure != NotificationExposure.LOCAL) throw NotificationConfigurationException(ConfigurationCategory.UNSAFE_STORAGE, ConfigurationField.EXPOSURE)
            if (feature.ownership != NotificationOwnership.SINGLE_PROCESS) throw NotificationConfigurationException(ConfigurationCategory.UNSUPPORTED_OWNERSHIP, ConfigurationField.OWNERSHIP)
        }

        private fun disabledConfiguration(feature: ParsedFeature, limits: ParsedLimits) = NotificationConfiguration(false, feature.apiEnabled, feature.exposure, feature.ownership, null, limits.pool, limits.requestBody, limits.registration, limits.subscriptions, limits.pollDelay, limits.timeout, limits.lease, limits.attempts, limits.initialRetry, limits.maxRetry, limits.jitter, limits.providerCeiling, null, feature.instance, feature.mode, null, null)

        private data class ParsedFeature(val enabled: Boolean, val apiEnabled: Boolean, val exposure: NotificationExposure, val ownership: NotificationOwnership, val storage: String?, val project: String?, val instance: String, val mode: FirebaseTargetMode, val lookupKey: ByteArray?)
        private data class ParsedLimits(val pool: Int, val requestBody: Int, val registration: Int, val subscriptions: Int, val pollDelay: Long, val timeout: Long, val lease: Long, val attempts: Int, val initialRetry: Long, val maxRetry: Long, val jitter: Long, val providerCeiling: Long)

        private fun decodeLookupKey(value: String): ByteArray = try {
            if (!Regex("^[A-Za-z0-9_-]{43}$").matches(value)) throw IllegalArgumentException()
            java.util.Base64.getUrlDecoder().decode(value).also { if (it.size != 32) throw IllegalArgumentException() }
        } catch (_: IllegalArgumentException) {
            throw NotificationConfigurationException(ConfigurationCategory.INVALID_SYNTAX, ConfigurationField.LOOKUP_DIGEST_KEY)
        }

        private fun firebaseAppName(project: String, instance: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("firebase-app-name-v1\u0000$project\u0000$instance".toByteArray())
            return "vlrgg-mn-s1-" + digest.take(12).joinToString("") { "%02x".format(it) }
        }

        private fun safeStoragePath(value: String): Path {
            if (
                value.startsWith("jdbc:", ignoreCase = true) ||
                value.startsWith("//") || value.startsWith("\\\\") ||
                value.contains(';') || value.contains('?') || value.contains('#') ||
                value.any { it.code < 32 || it.code == 127 }
            ) throw NotificationConfigurationException(ConfigurationCategory.UNSAFE_STORAGE, ConfigurationField.STORAGE_PATH)
            val path = try { Path.of(value).normalize() } catch (_: InvalidPathException) {
                throw NotificationConfigurationException(ConfigurationCategory.UNSAFE_STORAGE, ConfigurationField.STORAGE_PATH)
            }
            if (!path.isAbsolute) throw NotificationConfigurationException(ConfigurationCategory.UNSAFE_STORAGE, ConfigurationField.STORAGE_PATH)
            return path
        }
    }
}

private fun strictBoolean(value: String, field: ConfigurationField): Boolean = when (value) {
    "true" -> true
    "false" -> false
    else -> throw NotificationConfigurationException(ConfigurationCategory.INVALID_BOOLEAN, field)
}

private fun strictUnsignedInt(value: String, field: ConfigurationField, minimum: Int, maximum: Int): Int {
    if (!Regex("^[0-9]+$").matches(value)) throw NotificationConfigurationException(ConfigurationCategory.INVALID_INTEGER, field)
    val result = value.toIntOrNull() ?: throw NotificationConfigurationException(ConfigurationCategory.INVALID_INTEGER, field)
    if (result !in minimum..maximum) throw NotificationConfigurationException(ConfigurationCategory.OUT_OF_RANGE, field)
    return result
}

private fun strictUnsignedLong(value: String, field: ConfigurationField, minimum: Long, maximum: Long): Long {
    if (!Regex("^[0-9]+$").matches(value)) throw NotificationConfigurationException(ConfigurationCategory.INVALID_INTEGER, field)
    val result = value.toLongOrNull() ?: throw NotificationConfigurationException(ConfigurationCategory.INVALID_INTEGER, field)
    if (result !in minimum..maximum) throw NotificationConfigurationException(ConfigurationCategory.OUT_OF_RANGE, field)
    return result
}

private fun <T> enumValue(value: String, parser: (String) -> T, field: ConfigurationField, category: ConfigurationCategory): T = try {
    parser(value)
} catch (_: IllegalArgumentException) {
    throw NotificationConfigurationException(category, field)
}
