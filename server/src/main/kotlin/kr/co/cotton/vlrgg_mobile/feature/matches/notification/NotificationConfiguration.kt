package kr.co.cotton.vlrgg_mobile.feature.matches.notification

/** Cloud Run compatible listener configuration; notification runtime is deliberately disabled in main. */
data class ServerListenerConfiguration(
    val host: String,
    val port: Int,
) {
    companion object {
        private val hostPattern = Regex("^[A-Za-z0-9.:-]{1,253}$")

        fun fromEnvironment(environment: Map<String, String>): ServerListenerConfiguration {
            val host = environment["VLRGG_SERVER_HOST"] ?: "0.0.0.0"
            require(hostPattern.matches(host)) { "VLRGG_SERVER_HOST is invalid" }
            val rawPort = environment["PORT"] ?: environment["VLRGG_SERVER_PORT"] ?: "8080"
            val port = rawPort.toIntOrNull()?.takeIf { it in 1..65535 }
                ?: throw IllegalArgumentException("server port is invalid")
            return ServerListenerConfiguration(host, port)
        }
    }
}
