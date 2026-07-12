# Series

## 목적과 사용자 가치

Series Detail은 같은 대회 체계에 속한 Event를 예정·완료 기준으로 모아 보여주고, 사용자가 개별 Event Detail로 이어서 탐색하게 한다. Series 자체의 복잡한 순위 화면을 만드는 대신 Event discovery에 집중하는 간결한 상세 화면이다.

이 문서는 제품 동작을 정의한다. 시각 언어와 공통 접근성 기준은 루트 [`DESIGN.md`](../../../DESIGN.md)를 따른다.

## MVP 범위

Series Detail은 **Phase 5** 기능이며 Phase 1~5 전체로 구성되는 1차 MVP에 포함된다.

- Series 기본 식별 정보
- `Upcoming Events`
- `Completed Events`
- Event 선택 시 Event Detail 이동
- `/series/{id}/{slug}` 형태뿐 아니라 VLR.GG가 전용 short path로 제공하는 Series source도 처리할 수 있는 제품 경계

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

- 안정적인 Series 식별자 또는 서버가 정규화한 source key
- Series 이름
- 원본에 존재하고 Series 식별에 필요한 설명·지역·시즌 정보

### Event 요약

- 안정적인 Event 식별자
- Event 이름
- Event 상태
- 예정 또는 완료를 판단할 수 있는 일정/기간 정보
- 원본에 존재하고 Event 구분에 필요한 이미지·지역 등의 보조 정보

Event row의 세부 표현은 Events 기능 문서와 같은 Event 요약 규칙을 사용한다. VLR.GG 전용 short path는 숫자 ID가 없을 수 있으므로, 앱과 서버가 공유할 안정적인 식별 방식은 parser contract에서 명시해야 한다.

## 화면 상태

### Loading

- Series 정체성과 두 Event 섹션의 안정적인 자리를 유지하는 skeleton을 표시한다.

### Empty

- `Upcoming Events`만 비면 해당 섹션의 빈 상태를 표시하고 완료 Event는 유지한다.
- `Completed Events`만 비면 해당 섹션의 빈 상태를 표시하고 예정 Event는 유지한다.
- 두 섹션 모두 비면 Event가 없다는 Series 전체 안내를 표시한다.

### Populated

- Upcoming과 Completed의 구분이 text label과 구조로 명확해야 한다.
- 각 Event는 선택 가능하며 Event Detail로 연결된다.

### Partial

- 한 Event 섹션만 정상적으로 해석되거나 일부 Event의 보조 정보가 누락되면 유효한 데이터는 유지하고 누락 상태를 명확히 표시한다.
- ID나 이름이 없어 Event를 식별할 수 없는 항목은 정상 Event로 노출하지 않는다.

### Error

- Series를 불러오지 못한 상태를 Event가 없는 상태와 구분한다.
- 같은 Series를 다시 요청하는 재시도 액션을 제공한다.
- raw exception, HTTP status, selector 또는 upstream URL을 노출하지 않는다.

### Stale

- 서버는 이전 scraping 결과를 실패 fallback으로 반환하지 않는다.
- 앱이 새로고침 중 기존 Series를 일시 유지하는 경우 마지막 갱신 상태를 표시하고 이전 Event 상태를 최신으로 오인하게 하지 않는다.

## 사용자 인터랙션

- Event 항목 선택: Event Detail 이동
- 재시도 또는 새로고침: 현재 Series 다시 요청
- 뒤로 가기: Event → Series → Search 순서와 각 화면 상태 복원

Standings나 지원하지 않는 고급 기능은 비활성 탭 또는 placeholder로 노출하지 않는다.

## 앱과 서버 책임 경계

### 앱

- app-facing Series Response를 app Domain Model로 매핑한다.
- Event를 Upcoming과 Completed 섹션에 표시하고 날짜·상태를 화면용으로 포맷한다.
- loading, empty, populated, partial, error, stale 화면 상태를 표현한다.
- Event 선택을 Navigation 3 Screen callback으로 전달한다.
- VLR.GG의 short path, HTML, selector를 직접 해석하지 않는다.

### 서버

- 일반 Series URL과 전용 short path의 source를 요청 시점에 가져온다.
- Jsoup parser로 Series 정보와 Event 그룹을 server-internal `SourceModel`로 해석한다.
- 서로 다른 upstream URL 형태를 일관된 app-facing Series Response로 정규화한다.
- Event 상태를 Upcoming 또는 Completed로 매핑하며 알 수 없는 상태를 임의 분류하지 않는다.
- upstream 통신 실패와 parsing 실패를 각각 `UPSTREAM_NETWORK_FAILURE`, `SOURCE_PARSING_FAILURE`로 반환한다.
- raw HTML, selector, canonical upstream URL과 원본 예외를 앱에 노출하지 않는다.

## Upstream 및 parser 참고

제품 화면의 app route/API endpoint와 아래 VLR.GG upstream URL을 혼동하지 않는다.

```text
https://www.vlr.gg/vct
https://www.vlr.gg/series/85/valorant-challengers-league-2026
```

- VLR.GG Series source는 전용 short path가 있는 경우와 `/series/{id}/{slug}` 형태가 있는 경우를 모두 고려한다.
- short path와 numeric ID path가 같은 개념을 가리키는지, 서로 다른 식별 체계인지는 fixture 분석으로 확인하고 server response에서 정규화한다.
- Upcoming/Completed 구분은 DOM 위치만 믿지 말고 상태·일정 정보와 대표 fixture로 검증한다.
- slug는 표시·식별의 유일한 근거로 사용하지 않는다.
- 대표 fixture에는 일반 Series, short-path Series, Upcoming만 있는 경우, Completed만 있는 경우, 두 섹션 모두 비어 있는 경우를 포함한다.

## 수용 기준

- [ ] Series Detail이 Phase 5 및 1차 MVP 범위로 문서와 구현 계획에 포함된다.
- [ ] Search의 Series 결과에서 Series Detail에 진입할 수 있다.
- [ ] Series Detail이 `Upcoming Events`와 `Completed Events`를 명확히 구분한다.
- [ ] 두 섹션의 Event를 선택하면 Event Detail로 이동한다.
- [ ] Event Detail에서 뒤로 가면 Series의 스크롤과 섹션 상태가 복원된다.
- [ ] Series에서 뒤로 가면 Search 검색어와 결과가 복원된다.
- [ ] 한 섹션만 비거나 실패한 상태가 전체 empty/error와 구분된다.
- [ ] loading, empty, populated, partial, error, stale 상태가 정상 콘텐츠와 구분된다.
- [ ] Standings와 기타 제외 기능이 placeholder로 노출되지 않는다.
- [ ] 서버가 일반 Series URL과 short path의 차이를 앱에 누출하지 않고 일관된 Response로 정규화한다.
- [ ] parser fixture가 두 upstream URL 형태와 Event 상태 그룹을 검증한다.
- [ ] 서버 오류가 raw HTML, selector 또는 내부 예외 정보를 노출하지 않는다.

## 열린 결정

Series 구현을 시작할 때 VLR.GG 전용 short path와 `/series/{id}/{slug}` resource 사이의 안정적인 identity 규칙을 fixture 분석으로 확정해야 한다. 이 결정은 numeric ID가 없는 short-path Series의 API 식별자와 Navigation key 구현을 차단하지만, Series 화면의 정보 구조와 MVP 범위는 변경하지 않는다.
