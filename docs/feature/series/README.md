# Series

## 목적과 사용자 가치

Series Detail은 같은 대회 체계에 속한 Event를 예정·완료 기준으로 모아 보여주고, 사용자가 개별 Event Detail로 이어서 탐색하게 한다. Series 자체의 복잡한 순위 화면을 만드는 대신 Event discovery에 집중하는 간결한 상세 화면이다.

이 문서는 제품 동작을 정의한다. 시각 언어와 공통 접근성 기준은 루트 [`DESIGN.md`](../../../DESIGN.md)를 따른다.

## 구현 상태 (2026-09-03)

- **Backend: 구현 완료.** `GET /api/v1/series/{seriesId}`가 Series와 Upcoming/Completed Event 그룹을 반환하며 parser/mapper/service/route 테스트가 있다.
- **App D2: 구현 완료.** Series 응답 매핑과 repository/Metro binding, Loading/Content/Error 상태, Populated/Upcoming Only/Completed Only/전체 Empty 화면, Search → Series → Event 이동과 back/root 상태 복원을 구현했다.
- **검증 범위:** Android host 및 iOS simulator 자동화 테스트와 양 플랫폼 compile, Android debug assemble을 통과했다. 실제 양 플랫폼 screenshot과 실기기 접근성 검증은 아직 수행하지 않았다.

## MVP 범위

Series Detail은 **Phase 5** 기능이며 Phase 1~5 전체로 구성되는 1차 MVP에 포함된다.

- Series 기본 식별 정보
- `Upcoming Events`
- `Completed Events`
- Event 선택 시 Event Detail 이동

## 명시적 제외 범위

- Standings
- Series ranking, 팀 순위 또는 포인트 표
- 진행 중 Event를 위한 별도 고급 상태 화면
- Series 즐겨찾기와 알림
- 전체 Event 고급 필터와 페이지네이션
- Series를 하단 navigation destination으로 제공하는 것
- Team Detail 또는 Player Detail에서 Series를 거쳐 Event로 이동하는 별도 경로

## 진입과 종료 내비게이션

### 진입

- Search의 Series 결과에서 Series Detail로 진입한다.
- Series는 하단 navigation destination이 아니다.

다른 기능이 Series 참조를 실제로 제공하게 되면 같은 Series Detail을 재사용할 수 있지만, 1차 MVP의 필수 진입 경로는 Search다.

### 종료와 하위 이동

- `Upcoming Events` 또는 `Completed Events`의 Event를 선택하면 Event Detail을 push한다.
- Event Detail에서 뒤로 가면 Series Detail의 스크롤 위치와 섹션 상태를 복원한다.
- Series Detail에서 뒤로 가면 Search 검색어와 결과 상태를 복원한다.

## 화면과 콘텐츠 계층

1. 뒤로 가기 액션
2. Series 이름과 기본 식별 정보
3. `Upcoming Events`
4. `Completed Events`

예정 Event가 우선 발견되도록 `Upcoming Events`를 먼저 배치한다. 한 섹션이 비어 있어도 다른 섹션의 콘텐츠를 유지하며, 두 섹션이 모두 비었을 때만 Series 전체의 empty 상태를 표시한다.

## 노출 데이터

### Series 기본 정보

- Series 이름
- Series를 이해하는 데 필요한 기본 정보

### Event 요약

- Event 이름
- Event 상태
- 예정 또는 완료를 판단할 수 있는 일정/기간 정보
- Event를 구분하는 데 필요한 보조 정보

Event row의 세부 표현은 Events 기능 문서와 같은 Event 요약 규칙을 사용한다.

## 화면 상태

### Loading

- Back app bar 아래 full-content loading을 표시한다. 세부 section skeleton을 독립 화면으로 만들지 않는다.

### Empty

- `Upcoming Events`만 비면 해당 섹션의 빈 상태를 표시하고 완료 Event는 유지한다.
- `Completed Events`만 비면 해당 섹션의 빈 상태를 표시하고 예정 Event는 유지한다.
- 두 섹션 모두 비면 Event가 없다는 Series 전체 안내를 표시한다.

### Populated

- Upcoming과 Completed의 구분이 text label과 구조로 명확해야 한다.
- 각 Event는 선택 가능하며 Event Detail로 연결된다.

### Optional row omission

- 서버 응답은 atomic이므로 generic Partial 화면은 만들지 않는다. 일부 Event의 optional metadata가 누락되면 해당 row 안에서만 missing-value marker를 표시한다.
- ID나 이름이 없어 Event를 식별할 수 없는 항목은 정상 Event로 노출하지 않는다.

### Error

- Series를 불러오지 못한 상태를 Event가 없는 상태와 구분하는 full-content Error Screen을 표시한다.
- Back은 유지하고 같은 Series를 다시 요청하는 Retry action을 제공한다.
- raw exception, HTTP status, selector 또는 upstream URL을 노출하지 않는다.

## 사용자 인터랙션

- Event 항목 선택: Event Detail 이동
- Error Screen 재시도: 현재 Series를 같은 `seriesId`로 다시 요청
- 뒤로 가기: Event → Series → Search 순서와 각 화면 상태 복원

Standings나 지원하지 않는 고급 기능은 비활성 탭 또는 placeholder로 노출하지 않는다.

## 앱과 서버 책임 경계

### 앱

- app-facing Series Response를 app Domain Model로 매핑한다.
- Event를 Upcoming과 Completed 섹션에 표시하고 날짜·상태를 화면용으로 포맷한다.
- full-content loading, empty, populated, error 화면 상태를 표현한다. generic Partial은 사용하지 않는다.
- Event 선택을 Navigation 3 Screen callback으로 전달한다.
- VLR.GG HTML과 selector를 직접 해석하지 않는다.

### 서버

- Series source를 요청 시점에 가져온다.
- Jsoup parser로 Series 정보와 Event 그룹을 server-internal `SourceModel`로 해석한다.
- 앱이 사용할 수 있는 Series Response를 제공한다.
- Event 상태를 Upcoming 또는 Completed로 매핑하며 알 수 없는 상태를 임의 분류하지 않는다.
- upstream 통신 실패와 parsing 실패를 각각 `UPSTREAM_NETWORK_FAILURE`, `SOURCE_PARSING_FAILURE`로 반환한다.
- raw HTML, selector, canonical upstream URL과 원본 예외를 앱에 노출하지 않는다.

## 확정 서버 API 계약

### 요청

```text
GET /api/v1/series/{seriesId}
```

- `seriesId`는 Search의 `eventgroup` 결과가 반환하는 것과 같은 canonical ID다. 선행 0이 없는 양의 10진 JSON String이며 길이는 1~10자다.
- Search의 Series `reference.id`는 변환 없이 이 endpoint의 `{seriesId}`로 사용할 수 있다.
- 정확한 path만 허용한다. trailing slash 또는 추가 segment와 모든 query parameter는 upstream 요청 전에 `400 INVALID_REQUEST`로 거절한다.

### 성공 response

```json
{
  "id": "85",
  "name": "Valorant Challengers League 2026",
  "description": "Riot's official Tier 2 tournament circuit.",
  "upcomingEvents": [],
  "completedEvents": []
}
```

- `id`와 `name`은 필수다. `description`과 원본이 실제로 제공하는 기본 메타데이터는 nullable이다.
- Event item은 Events API의 `EventSummaryResponse`와 같은 `id`, `name`, `status`, `dateLabel`, `regionCode`, `imageUrl` 의미를 사용한다. 별도의 Series 전용 fake field를 만들지 않는다.
- Event `id`는 String이며 `GET /api/v1/events/{eventId}`에 직접 사용할 수 있다.
- `ONGOING`, `UPCOMING`은 `upcomingEvents`에, `COMPLETED`, `PAUSED`는 `completedEvents`에 둔다. 각 item의 실제 `status`는 보존한다.
- 동일 Event ID가 반복되면 첫 source 순서를 유지해 한 번만 반환한다. 같은 ID가 서로 다른 status로 나타나면 source parsing failure다.

### upstream, parsing, 실패

- 각 요청은 `https://www.vlr.gg/series/{seriesId}`를 정확히 한 번 request-time GET한다. cache, retry, stale-success fallback, 이전 응답 재사용은 없다.
- series identity 또는 event container가 없거나, 관찰된 알 수 없는 status, 필수 Event ID/name/status 누락, 선택된 row/section의 구조 오류, 중복 Event의 상충 status는 `SOURCE_PARSING_FAILURE`로 fail closed 한다.
- 명시적으로 비었거나 검증된 빈 섹션은 빈 list가 된다. 누락된 optional row field만 `null`이며, parser drift를 empty/partial success로 숨기지 않는다.
- invalid request는 `400 INVALID_REQUEST`, upstream GET 실패는 `502 UPSTREAM_NETWORK_FAILURE`, DOM 해석 실패는 `502 SOURCE_PARSING_FAILURE`다. 모든 실패는 공통 안전 envelope를 사용하며 raw HTML, selector, 임의 upstream URL, 내부 예외 message를 노출하지 않는다.
- `CancellationException`은 public failure envelope로 바꾸지 않고 전파한다.

## Upstream 및 parser 참고

제품 화면의 app route/API endpoint와 아래 VLR.GG upstream URL을 혼동하지 않는다.

```text
https://www.vlr.gg/vct
https://www.vlr.gg/series/85/valorant-challengers-league-2026
```

- Upcoming/Completed 구분은 DOM 위치만 믿지 말고 상태·일정 정보와 대표 fixture로 검증한다.
- 대표 fixture에는 Upcoming만 있는 경우, Completed만 있는 경우, 두 섹션 모두 비어 있는 경우를 포함한다.

## 수용 기준

- [x] Series Detail이 Phase 5 및 1차 MVP 범위로 문서와 구현 계획에 포함된다.
- [x] Search의 Series 결과에서 Series Detail에 진입할 수 있다.
- [x] Series Detail이 `Upcoming Events`와 `Completed Events`를 명확히 구분한다.
- [x] 두 섹션의 Event를 선택하면 Event Detail로 이동한다.
- [x] Event Detail에서 뒤로 가면 Series의 스크롤과 섹션 상태가 복원된다.
- [x] Series에서 뒤로 가면 Search 검색어, 결과와 스크롤이 복원된다.
- [x] 한 섹션만 비어 다른 섹션의 Event가 유지되는 section Empty와 두 섹션 모두 비는 전체 Empty를 구분한다.
- [x] Series response는 atomic이므로 section-specific transport failure는 제공하지 않으며, transport/parsing failure는 Series identity와 섹션을 대체하는 full-content Error Screen과 Retry로 처리한다.
- [x] full-content loading, empty, populated, error 상태가 정상 콘텐츠와 구분되고 generic Partial screen은 없다.
- [x] Standings와 기타 제외 기능이 placeholder로 노출되지 않는다.
- [x] parser fixture가 Upcoming/Completed Event 상태 그룹을 검증한다.
- [x] 서버 오류가 raw HTML, selector 또는 내부 예외 정보를 노출하지 않는다.
