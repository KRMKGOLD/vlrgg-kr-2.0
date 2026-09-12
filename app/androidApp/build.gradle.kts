import com.android.build.api.variant.BuildConfigField
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

private fun javaStringLiteral(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\u000C' -> append("\\f")
            '\r' -> append("\\r")
            else -> if (character.isISOControl()) {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

private fun releaseApiBaseUrl(rawValue: String?): String {
    if (rawValue.isNullOrBlank()) {
        throw GradleException("Android Release requires API_BASE_URL.")
    }

    val uri = try {
        URI(rawValue)
    } catch (_: Exception) {
        null
    }
    if (
        rawValue != rawValue.trim() ||
        uri == null ||
        !uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrBlank() ||
        (uri.port != -1 && uri.port !in 1..65535) ||
        (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") ||
        uri.rawUserInfo != null ||
        uri.rawQuery != null ||
        uri.rawFragment != null
    ) {
        throw GradleException(
            "Android Release API_BASE_URL must be a valid HTTPS URL without credentials, query, or fragment.",
        )
    }
    return rawValue
}

private fun releaseVersion(rawValue: String?): String {
    if (
        rawValue == null ||
        !Regex("[0-9]+(?:\\.[0-9]+){0,2}").matches(rawValue)
    ) {
        throw GradleException("Android Release requires a valid APP_VERSION.")
    }
    return rawValue
}

private fun releaseBuildNumber(rawValue: String?): Int = rawValue
    ?.toIntOrNull()
    ?.takeIf { it in 1..2_100_000_000 }
    ?: throw GradleException("Android Release requires APP_BUILD_NUMBER as a positive integer. Maximum: 2100000000.")

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.app.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

val apiBaseUrl = providers.gradleProperty("API_BASE_URL")
    .orElse(providers.environmentVariable("API_BASE_URL"))
val appVersion = providers.gradleProperty("APP_VERSION")
    .orElse(providers.environmentVariable("APP_VERSION"))
val appBuildNumber = providers.gradleProperty("APP_BUILD_NUMBER")
    .orElse(providers.environmentVariable("APP_BUILD_NUMBER"))
val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val keystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
val keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")

val validatedReleaseApiBaseUrl = providers.provider {
    releaseApiBaseUrl(apiBaseUrl.orNull)
}
val validatedReleaseVersion = providers.provider {
    releaseVersion(appVersion.orNull)
}
val validatedReleaseBuildNumber = providers.provider {
    releaseBuildNumber(appBuildNumber.orNull)
}
val releaseSigningValues = listOf(
    keystorePath.orNull,
    keystorePassword.orNull,
    keyAlias.orNull,
    keyPassword.orNull,
)

android {
    namespace = "kr.co.cotton.vlrgg_mobile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "kr.co.cotton.vlrgg_mobile"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    val releaseSigningConfig = if (releaseSigningValues.all { !it.isNullOrBlank() }) {
        signingConfigs.create("release") {
            storeFile = file(checkNotNull(releaseSigningValues[0]))
            storePassword = releaseSigningValues[1]
            keyAlias = releaseSigningValues[2]
            keyPassword = releaseSigningValues[3]
        }
    } else {
        null
    }
    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "API_BASE_URL",
                javaStringLiteral(apiBaseUrl.orNull ?: "http://10.0.2.2:8080"),
            )
        }
        getByName("release") {
            signingConfig = releaseSigningConfig
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.buildConfigFields?.put(
            "API_BASE_URL",
            validatedReleaseApiBaseUrl.map { value ->
                BuildConfigField("String", javaStringLiteral(value), "Release API base URL")
            },
        )
        variant.outputs.forEach { output ->
            output.versionName.set(validatedReleaseVersion)
            output.versionCode.set(validatedReleaseBuildNumber)
        }
    }
}

val validateReleaseConfiguration = tasks.register("validateReleaseConfiguration") {
    group = "verification"
    description = "Validates Android Release URL, version, build number, and signing inputs."
    notCompatibleWithConfigurationCache("Release signing validation reads protected environment variables.")

    doLast {
        validatedReleaseApiBaseUrl.get()
        validatedReleaseVersion.get()
        validatedReleaseBuildNumber.get()

        if (releaseSigningValues.any { it.isNullOrBlank() }) {
            throw GradleException(
                "Android Release requires all four ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
                    "ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD values.",
            )
        }
        val releaseKeystore = file(checkNotNull(releaseSigningValues[0]))
        if (!releaseKeystore.isFile || !releaseKeystore.canRead()) {
            throw GradleException("Android Release keystore file is missing or invalid.")
        }
    }
}

tasks.configureEach {
    if (name != validateReleaseConfiguration.name && name.contains("Release")) {
        dependsOn(validateReleaseConfiguration)
    }
}
