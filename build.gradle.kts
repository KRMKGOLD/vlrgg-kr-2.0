buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            // Ktor Gradle plugin 3.5.0 requests commons-lang3:[3.18.0,). A version range makes every
            // build list versions on Maven Central, which fails on 429 responses, so pin the version
            // the range currently resolves to.
            if (requested.group == "org.apache.commons" && requested.name == "commons-lang3") {
                useVersion("3.22.0")
                because("avoid dynamic version listing on Maven Central during configuration")
            }
        }
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.aboutLibrariesAndroid) apply false
}
