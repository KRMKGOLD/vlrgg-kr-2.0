# Events

## 목적과 사용자 가치

Events 기능은 사용자가 Valorant 대회의 현재 진행 상태를 훑고, 선택한 Event의 경기와 관련 뉴스를 한곳에서 탐색하게 한다. Event List는 진행 중·예정·종료/중단 Event를 구분해 발견하는 진입점이며, Event Detail은 해당 대회의 기본 정보와 `Matches All`, `News List`, 기본 통계를 연결하는 허브다.

이 문서는 제품 동작을 정의한다. 색상, 타이포그래피, 공통 컴포넌트와 접근성 기준은 루트 [`DESIGN.md`](../../../DESIGN.md)를 따른다.

## 구현 상태 (2026-07-22)

- **Backend: 구현 완료.** Event 목록, 상세, Matches, News, Stats endpoint와 scraper/parser/mapper 및 fixture/route 테스트가 구현되어 있다.
- **App: 미구현.** Event 목록·상세 Compose UI, 섹션별 상태/재시도, 관련 Detail navigation은 아직 구현되어 있지 않다.

## MVP 범위

Events는 Phase 3 기능이며 Phase 1~5 전체로 구성되는 1차 MVP에 포함된다.

### Event List

- VLR.GG Events의 `All` 기준 첫 페이지만 제공한다.
- Event를 `Ongoing`, `Upcoming`, `Completed / Paused` 상태 그룹으로 구분한다.
- 각 항목은 Event를 식별하고 상태를 판단하는 데 필요한 요약 정보를 제공한다.
- 항목 선택 시 Event Detail로 이동한다.

### Event Detail

- Event 기본 정보
- 해당 Event의 전체 경기 목록인 `Matches All`
- 해당 Event의 `News List`
- Event 기본 통계
- Match 선택 시 Match Detail 이동
- News 선택 시 News Detail 이동

## 명시적 제외 범위

- Event List 2페이지 이후의 페이지네이션
- VCT, VCL, Game Changers, Americas, EMEA, Pacific, China 필터
- Overview 확장 콘텐츠
- 브래킷
- 참가 팀 전용 섹션
- Agent 통계
- 고급 통계
- Team Detail 또는 Player Detail에서 Event로 직접 이동하는 경로

## 진입과 종료 내비게이션

### 진입

- 기본 진입: 하단 `Events` 탭
- 문맥 진입: Matches의 Event 참조
- 탐색 진입: Search의 Event 결과

Team Detail과 Player Detail은 1차 MVP에서 Event로 직접 연결하지 않는다.

### 종료와 하위 이동

- Event List의 Event를 선택하면 Event Detail을 push한다.
- Event Detail의 Match를 선택하면 Match Detail을 push한다.
- Event Detail의 News를 선택하면 News Detail을 push한다.
- 뒤로 가기는 직전 화면과 그 화면의 목록 위치·필터·탭 상태를 복원한다.
- 하단 `Events` 탭에서 진입한 루트 화면에는 공유 Top App Bar의 Search 액션을 노출한다.

## 화면과 콘텐츠 계층

### Event List

1. 공유 Top App Bar와 Search 액션
2. `Ongoing`
3. `Upcoming`
4. `Completed / Paused`
5. 각 상태 그룹의 Event 요약 목록

비어 있는 상태 그룹은 다른 그룹에 데이터가 있다면 화면 전체 오류로 취급하지 않는다. 상태 그룹 자체를 숨길지 빈 안내를 표시할지는 Stitch 화면 설계에서 일관된 한 방식으로 정하되, 전체 목록이 비었는지는 명확하게 구분한다.

### Event Detail

1. Event 기본 정보와 현재 상태
2. `Matches All`
3. `News List`
4. 기본 통계

정보가 많은 경우 위 섹션은 탭 또는 명확히 구분된 세로 섹션으로 표현할 수 있다. 어떤 표현을 사용하더라도 Event 정체성과 상태가 가장 먼저 읽혀야 하며, Matches와 News로의 이동 경로가 통계보다 우선한다.

## 노출 데이터

### Event 요약

- 안정적인 Event 식별자
- Event 이름
- Event 상태: 진행 중, 예정, 종료 또는 중단
- 목록에서 제공되는 일정 또는 기간 정보
- 목록에서 제공되는 Event 이미지·지역·시리즈 등 식별 보조 정보가 실제 원본에 존재하는 경우 해당 정보

### Event 기본 정보

- Event 식별자와 이름
- Event 상태
- 원본에 존재하는 일정 또는 기간
- Event를 식별하는 이미지·지역·시리즈 등 기본 메타데이터는 parser contract에서 존재 여부와 선택성을 확인한 뒤 노출한다.

### Matches All

- Match 식별자
- 경기 시간과 상태
- 참가 Team
- 스코어 또는 예정 상태
- Match를 선택하는 데 필요한 설명 정보

Match 표시에 관한 세부 규칙은 Match 기능 문서가 소유하며 Event Detail은 같은 요약 표현을 재사용한다.

### News List

- News 식별자
- 제목
- 작성자
- 작성 시각

News 표시에 관한 세부 규칙은 News 기능 문서가 소유한다.

### Event 기본 통계

Event Stats는 1차 MVP에 포함되는 정식 섹션이다. upstream Stats resource가 정상 응답하면서 `No stats available`을 나타내면 아직 제공할 통계가 없는 정상 empty state로 처리한다. network 실패, 예상하지 못한 응답, parsing 실패는 통계 없음으로 위장하지 않고 별도 error state와 재시도를 제공한다.

## 화면 상태

### Loading

- 최초 진입 시 Event 정체성 또는 Event row의 안정적인 자리를 유지하는 skeleton을 표시한다.
- Event Detail의 섹션을 병렬로 불러오더라도 어떤 섹션이 로딩 중인지 구분한다.

### Empty

- Event List 전체 결과가 없으면 Event가 없다는 안내를 표시한다.
- Event Detail의 Matches 또는 News가 비어 있으면 해당 섹션 안에서 빈 상태를 표시하며 Event Detail 전체를 실패 처리하지 않는다.
- Stats resource가 `No stats available`을 나타내면 값을 `0`으로 추정하지 않고 통계가 아직 없다는 empty state를 표시한다.

### Populated

- Event 상태 그룹과 각 항목의 경계가 명확해야 한다.
- Event Detail에서 Event 기본 정보, Matches, News, 통계를 서로 구분한다.

### Partial

- Event 기본 정보는 성공했지만 Matches, News 또는 Stats 일부가 실패한 경우 성공한 콘텐츠는 유지하고 실패한 섹션을 명확히 표시한다.
- 원본에 존재하지 않는 선택 필드를 임의 값으로 채우지 않는다.

### Error

- 최초 요청이 실패하면 유효한 빈 목록처럼 보이지 않는 오류 상태와 재시도 액션을 제공한다.
- UI는 raw exception, HTTP status, selector 또는 upstream URL을 노출하지 않는다.

### Stale

- 서버는 이전 scraping 결과를 실패 fallback으로 반환하지 않는다.
- 앱이 새로고침 중 기존 화면을 일시 유지하는 정책을 도입하는 경우에만 마지막 갱신 시점과 갱신 중임을 명시한다. 이전 데이터를 최신 데이터처럼 조용히 표시하지 않는다.

## 사용자 인터랙션

- Event 항목 선택: Event Detail 이동
- Match 항목 선택: Match Detail 이동
- News 항목 선택: News Detail 이동
- 당겨서 새로고침 또는 명시적 재시도: 현재 화면 데이터를 다시 요청
- 공유 Top App Bar Search 선택: Search Screen을 현재 화면 위에 push
- 시스템 뒤로 가기: 이전 화면과 탐색 상태 복원

MVP에서 제공하지 않는 필터나 브래킷 탭은 비활성 placeholder로 노출하지 않는다.

## 앱과 서버 책임 경계

### 앱

- app-facing Response를 app Domain Model로 매핑한다.
- Event 상태별 그룹화와 화면용 날짜·시간 포맷을 담당한다.
- loading, empty, populated, partial, error, stale 화면 상태를 표현한다.
- Event, Match, News 선택을 Navigation 3 Screen callback으로 전달한다.
- VLR.GG HTML, selector, Jsoup 타입을 알지 않는다.

### 서버

- VLR.GG Event List와 Event Detail HTML을 요청 시점에 가져온다.
- DOM을 Jsoup으로 해석하고 server-internal `SourceModel`로 변환한다.
- 원본 구조를 앱에 적합한 Event List/Detail Response로 정규화한다.
- Matches, News, Stats의 정상 empty와 조회·해석 실패를 구분하며 누락 값을 임의 생성하지 않는다.
- upstream 통신 실패는 `UPSTREAM_NETWORK_FAILURE`, DOM 해석 실패는 `SOURCE_PARSING_FAILURE` 공통 오류로 반환한다.
- raw HTML, selector, 원본 예외 문구를 앱에 노출하지 않는다.

### 서버 API 계약

Event Detail의 섹션별 partial 상태와 독립 재시도를 지원하기 위해 기본 정보, 경기, 뉴스, 통계를 각각 조회한다.

```text
GET /api/v1/events
GET /api/v1/events/{eventId}
GET /api/v1/events/{eventId}/matches
GET /api/v1/events/{eventId}/news
GET /api/v1/events/{eventId}/stats
```

- `matches`는 upstream Event Matches의 `series_id=all` 결과를 사용한다.
- `matches.items`는 Matches 목록의 `MatchSummaryResponse` 계약을 재사용한다. `event`는 현재 Event의 `id`와 이름을 담고, Event 내부 단계 표시는 `event.series`에 둔다.
- Event Detail 원본에는 상태가 항상 존재하지 않으므로 상세 응답의 `status`는 선택 값이다. 목록 응답의 상태 그룹은 필수다.
- Event News 원본에는 작성자가 항상 존재하지 않으므로 Event News 요약의 `author`는 선택 값이다. 제목, canonical News reference, 작성 시각은 필수다.
- 섹션 하나의 network 또는 parsing 실패가 다른 섹션의 성공 응답을 대체하지 않는다. 각 실패는 공통 오류 envelope로 독립 반환한다.

## Upstream 및 parser 참고

제품 화면의 app route/API endpoint와 아래 VLR.GG upstream URL을 혼동하지 않는다.

```text
https://www.vlr.gg/events
https://www.vlr.gg/events/?page=2
https://www.vlr.gg/event/2955/esports-world-cup-2026-pacific-qualifier
https://www.vlr.gg/event/stats/2955/esports-world-cup-2026-pacific-qualifier
```

- MVP Event List는 `All` 첫 페이지를 대상으로 하며 `?page=2`는 향후 페이지네이션 분석용 예시다.
- Event 상태와 섹션 존재 여부는 표시 텍스트만 가정하지 말고 대표 fixture로 검증한다.
- `Ongoing`, `Upcoming`, `Completed / Paused` 분류 규칙을 parser/mapper 테스트에서 고정한다.
- Event Detail의 기본 정보, Matches, News, 통계는 DOM 경계가 다를 수 있으므로 parser 내부에서 책임을 분리한다.
- selector, DOM 보정, slug는 server-internal 세부사항이다. 앱 식별과 내비게이션은 안정적인 Event ID를 기준으로 한다.
- 대표 fixture에는 진행 중, 예정, 종료, 중단 Event와 일부 섹션이 없는 Event를 포함한다.

## 수용 기준

- [ ] Event List가 `All` 첫 페이지의 Event를 `Ongoing`, `Upcoming`, `Completed / Paused`로 구분해 표시한다.
- [ ] 하단 `Events` 탭, Matches의 Event 참조, Search의 Event 결과에서 Event Detail에 진입할 수 있다.
- [ ] Team Detail과 Player Detail에는 Event 직접 이동 요소가 없다.
- [ ] Event Detail이 Event 기본 정보, `Matches All`, `News List`, 기본 통계 영역을 구분한다.
- [ ] Event Stats가 `No stats available`을 반환하면 정상 empty state를 표시한다.
- [ ] Event Stats의 network 또는 parsing 실패는 empty와 구분된 error state와 재시도를 표시한다.
- [ ] Event Detail의 Match와 News가 각각 대응하는 Detail 화면으로 이동한다.
- [ ] loading, 전체 empty, 섹션 empty, partial, error, stale 상태가 정상 콘텐츠와 시각·의미적으로 구분된다.
- [ ] 누락된 선택 데이터를 `0`, 빈 문자열 또는 추정 값으로 위장하지 않는다.
- [x] 서버 parser fixture가 Event 상태 분류와 Detail 섹션 추출을 검증한다.
- [x] 서버 오류에 raw HTML, selector, 원본 예외 또는 민감한 내부 정보가 포함되지 않는다.
- [ ] 뒤로 가기 시 직전 화면의 탐색 상태가 보존된다.
