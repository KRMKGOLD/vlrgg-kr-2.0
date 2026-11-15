package kr.co.cotton.vlrgg_mobile.domain.model.events

data class EventSummary(
    val id: String,
    val name: String,
    val status: EventStatus,
    val dateLabel: String? = null,
    val regionCode: String? = null,
    val imageUrl: String? = null,
    /** 기간 시작. 서버가 주지 않거나 형식을 알 수 없으면 [endDate]와 함께 null이다. */
    val startDate: EventMonthDay? = null,
    /** 기간 끝. 끝이 미정(`TBD`)이면 [startDate]만 있고 이 값은 null이다. */
    val endDate: EventMonthDay? = null,
)
