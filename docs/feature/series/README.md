# Series

Series Detail은 같은 대회 체계의 Event를 예정·완료로 나누고 Event Detail로 연결하는 Phase 5 화면이다. 서버·앱과 자동화 검증은 완료됐고 실제 양 플랫폼 screenshot·실기기 접근성 확인은 남아 있다.

## MVP 범위

- Search의 Series 결과에서 진입
- Series 기본 정보
- `Upcoming Events`, `Completed Events` 순서의 독립 섹션
- stable Event ID로 Event Detail 이동과 Search까지의 상태 복원

Standings·ranking·포인트, 고급 진행 상태, 즐겨찾기·알림, 필터·pagination, bottom destination, Team/Player 우회 경로는 제외하며 placeholder로 표시하지 않는다.

## 화면과 상태

- Back app bar 아래 Series identity와 Upcoming, Completed를 표시한다.
- 한 섹션만 비면 그 섹션의 Empty를 표시하고 다른 콘텐츠를 유지한다. 둘 다 비면 전체 Empty다.
- optional metadata는 row의 missing marker로 처리한다. ID나 이름이 없어 식별할 수 없는 항목은 노출하지 않는다.
- response는 atomic이다. generic Partial이나 section transport error를 만들지 않고 조회·해석 실패는 identity와 섹션을 대체하는 full Error+Retry로 처리한다.
- Event 왕복 뒤 scroll과 섹션 상태, Series 종료 뒤 Search query·결과·scroll을 복원한다.

## 서버 API 계약

```text
GET /api/v1/series/{seriesId}
```

- `seriesId`는 Search `eventgroup` 결과의 선행 0 없는 1~10자리 양의 10진 String이다. trailing slash, 추가 segment와 query는 upstream 요청 전에 `400 INVALID_REQUEST`다.
- 응답은 필수 `id`, `name`, nullable `description`, `upcomingEvents`, `completedEvents`다. Event item은 Events API의 `id`, `name`, `status`, `dateLabel`, `regionCode`, `imageUrl` 의미를 재사용한다.
- `ONGOING`, `UPCOMING`은 upcoming에, `COMPLETED`, `PAUSED`는 completed에 둔다. 실제 status는 보존한다.
- 중복 ID는 첫 source 순서로 한 번만 반환한다. 같은 ID의 status가 충돌하면 parsing failure다.
- request마다 `https://www.vlr.gg/series/{seriesId}`를 한 번 조회하며 cache, retry, stale-success fallback이 없다.
- identity/container 누락, unknown status, 필수 Event 값 누락과 구조 변경은 `SOURCE_PARSING_FAILURE`다. 검증된 빈 섹션만 빈 list이며 optional row field만 `null`이다.

대표 fixture는 Upcoming-only, Completed-only, 전체 Empty를 포함하고 상태·일정 정보로 그룹을 검증한다. `CancellationException`은 public failure로 바꾸지 않는다.

## 수용 기준

- [x] Search → Series → Event 이동과 역방향 상태 복원이 동작한다.
- [x] Upcoming을 먼저 표시하고 section Empty와 전체 Empty를 구분한다.
- [x] optional 누락, 정상 Empty, 필수 구조 failure를 구분하며 generic Partial이 없다.
- [x] unknown/conflicting status를 fail closed 하고 내부 오류 정보를 노출하지 않는다.
- [ ] 실제 기기 시각·접근성 검증을 완료한다.
