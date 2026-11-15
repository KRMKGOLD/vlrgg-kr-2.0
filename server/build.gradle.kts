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
    implementation(libs.jspecify)
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

tasks.register<Jar>("observabilityValidationJar") {
    description = "Builds the opt-in private observability harness; excluded from production distributions."
    dependsOn(tasks.testClasses)
    archiveFileName.set("server-observability-validation.jar")
    from(sourceSets["test"].output) {
        include("kr/co/cotton/vlrgg_mobile/observability/validation/**")
    }
}
