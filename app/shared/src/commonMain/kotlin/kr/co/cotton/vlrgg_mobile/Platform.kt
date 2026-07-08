package kr.co.cotton.vlrgg_mobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform