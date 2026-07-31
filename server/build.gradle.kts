plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "kr.co.cotton.vlrgg_mobile"
version = "1.0.0"
application {
    mainClass = "kr.co.cotton.vlrgg_mobile.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.jspecify)
    implementation(libs.google.cloud.firestore)
    implementation(libs.jsoup)
    implementation(libs.logback)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverRoutingOpenapi)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverSwagger)
    testImplementation(libs.ktor.clientMock)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

val firestoreEmulatorCategory = "kr.co.cotton.vlrgg_mobile.feature.matches.notification.FirestoreEmulatorCategory"

tasks.test {
    useJUnit {
        excludeCategories(firestoreEmulatorCategory)
    }
}

val firestoreEmulatorTest by tasks.registering(Test::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs Firestore Emulator integration tests."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnit {
        includeCategories(firestoreEmulatorCategory)
    }
    filter {
        includeTestsMatching("*EmulatorTest")
        isFailOnNoMatchingTests = true
    }
    doFirst {
        require(!System.getenv("FIRESTORE_EMULATOR_HOST").isNullOrBlank()) {
            "FIRESTORE_EMULATOR_HOST is required for firestoreEmulatorTest"
        }
        require(!System.getenv("VLRGG_FIRESTORE_TEST_PROJECT_ID").isNullOrBlank()) {
            "VLRGG_FIRESTORE_TEST_PROJECT_ID is required for firestoreEmulatorTest"
        }
    }
}
