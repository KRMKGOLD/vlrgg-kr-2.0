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
    implementation(libs.firebase.admin)
    implementation(libs.flyway.core)
    implementation(libs.h2)
    implementation(libs.hikari)
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
