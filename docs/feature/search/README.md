# Search

## 목적과 사용자 가치

Search는 사용자가 이름이나 키워드로 Series, Event, Team, Player를 한곳에서 찾고 해당 상세 화면으로 이동하게 한다. 검색은 독립 하단 탭이 아니라 현재 탐색 문맥 위에 열리는 공통 discovery 화면이며, 닫았을 때 사용자가 보던 위치로 정확히 돌아가야 한다.

이 문서는 제품 동작을 정의한다. 시각 언어와 공통 접근성 기준은 루트 [`DESIGN.md`](../../../DESIGN.md)를 따른다.

## MVP 범위

Search는 Phase 4 기능이며 Phase 1~5 전체로 구성되는 1차 MVP에 포함된다.

- 모든 최상위 탭의 공유 Top App Bar에서 Search Screen 진입
- 텍스트 검색어 입력과 검색 실행
- 결과를 `Series`, `Event`, `Team`, `Player` 타입으로 분류
- 각 결과에서 대응하는 Detail 화면으로 이동
- 뒤로 가기 시 Search를 열기 전 화면과 상태 복원
- 검색 결과 없음, 입력 전, loading, partial, error 상태 구분

## 명시적 제외 범위

- Search를 하단 navigation destination으로 제공하는 것
- News 또는 Match를 검색 결과 타입으로 제공하는 것
- 고급 필터, 정렬, 자동완성, 추천 검색어
- 검색 기록, 인기 검색어, 계정 기반 동기화
- 외부 링크 preview
- Team/Player 상세에서 Event로 직접 연결하기 위한 우회 검색 동작

## 진입과 종료 내비게이션

### 진입

- `News`, `Matches`, `MyPage`, `Events`, `About`의 공유 Top App Bar Search 액션에서 진입한다.
- Search Screen은 현재 destination 위에 별도 화면으로 push된다.
- Search는 bottom navigation 선택 상태를 변경하지 않는다.

### 결과 이동

- Series 결과 → Series Detail
- Event 결과 → Event Detail
- Team 결과 → Team Detail
- Player 결과 → Player Detail

### 종료

- Search Screen에서 뒤로 가면 검색을 열기 전 destination으로 돌아간다.
- Search 결과의 Detail에서 뒤로 가면 Search 결과와 입력 검색어를 복원한다.
- Search Screen을 닫아 원래 top-level destination으로 돌아가면 그 destination의 스크롤, 선택, 필터 등 기존 상태를 보존한다.

## 화면과 콘텐츠 계층

1. 뒤로 가기 액션
2. 검색 입력 필드
3. 검색 실행 또는 입력 지우기 액션
4. 검색 상태 안내
5. 타입별 결과 그룹

결과 타입은 한 목록에서 명확한 label로 구분하거나 타입별 섹션으로 제공한다. 어떤 구성을 사용하더라도 각 결과의 타입을 색상만으로 전달하지 않고 text label 또는 접근 가능한 동등 표현을 사용한다.

## 노출 데이터

### 공통

- 결과 타입: Series, Event, Team, Player
- 안정적인 대상 식별자
- 대상 이름
- 원본에 존재하고 결과 식별에 필요한 보조 정보

### 타입별 보조 정보

- Series: Series를 구분할 수 있는 이름과 원본이 제공하는 범위 정보
- Event: Event 이름과 원본이 제공하는 상태·기간·지역 등의 식별 정보
- Team: Team 이름과 원본이 제공하는 tag·국가/지역 등의 식별 정보
- Player: handle과 원본이 제공하는 실명·국가·현재 Team 등의 식별 정보

보조 필드는 upstream 검색 결과가 실제 제공하는지를 fixture로 확인한 뒤 선택적으로 계약한다. Search가 Detail 화면을 대신하지 않도록 결과 선택에 필요한 최소 정보만 표시한다.

## 화면 상태

### Initial

- 아직 유효한 검색을 실행하지 않은 상태다.
- 결과 없음으로 표현하지 않고 검색어 입력을 안내한다.
- 공백만 있는 검색어는 네트워크 요청을 만들지 않는다.

### Loading

- 현재 검색어를 유지하면서 요청 진행 상태를 표시한다.
- 이전 검색 결과를 유지하는 경우 새 검색 결과가 아님을 분명히 표시하고 결과 선택의 오해를 만들지 않는다.

### Empty

- 유효한 검색은 성공했지만 모든 타입의 결과가 없는 상태다.
- 사용한 검색어를 포함한 안전한 안내와 검색어 수정 경로를 제공한다.

### Populated

- 각 결과의 타입과 이름이 명확하며 대응하는 Detail로 이동할 수 있다.
- 결과가 없는 타입을 가짜 빈 row로 채우지 않는다.

### Partial

- upstream 검색 결과에서 일부 항목의 보조 정보가 누락돼도 식별자·이름·타입이 유효하면 해당 결과를 제공할 수 있다.
- 결과 항목 자체를 식별할 수 없거나 Detail 이동에 필요한 ID가 없으면 정상 결과로 노출하지 않는다.

### Error

- 검색 요청 실패를 결과 없음과 구분한다.
- 입력 검색어를 보존하고 같은 검색을 다시 시도할 수 있게 한다.
- raw exception, HTTP status, selector 또는 upstream URL을 노출하지 않는다.

### Stale

- 서버는 이전 검색 결과를 실패 fallback으로 반환하지 않는다.
- 앱이 새 요청 중 이전 결과를 일시 유지한다면 해당 결과가 이전 검색어의 결과인지 명확히 표시한다. 서로 다른 검색어의 결과를 현재 검색 결과처럼 표시하지 않는다.

## 사용자 인터랙션

- Top App Bar Search 선택: Search Screen push와 입력 focus
- 검색어 입력 및 제출: 앞뒤 공백을 사용자 입력 의미를 해치지 않는 범위에서 정리한 뒤 검색
- 입력 지우기: 현재 검색어와 결과를 초기 상태로 되돌림
- 결과 선택: 결과 타입에 맞는 Detail 화면 push
- 재시도: 유지된 검색어로 동일 요청 재실행
- 뒤로 가기: Search stack을 역순으로 닫고 이전 화면 상태 복원

검색 요청을 매 keystroke마다 보낼지 명시적 제출로 보낼지는 구현 시 입력 방식과 부하를 함께 결정할 수 있다. MVP 수용 기준은 공백 요청 방지, 결과 상태의 정확성, 탐색 복원에 둔다.

## 앱과 서버 책임 경계

### 앱

- 검색어 입력, focus, 제출, 지우기와 화면 상태를 관리한다.
- Search Response를 app Domain Model로 매핑하고 타입별 결과를 렌더링한다.
- 결과 타입에 따라 Navigation 3 Screen callback을 호출한다.
- Search 진입 전 back stack과 화면 상태를 보존한다.
- HTML, selector, VLR.GG URL pattern을 해석하지 않는다.

### 서버

- 유효한 검색 query를 검증하고 VLR.GG 검색 결과를 요청 시점에 조회한다.
- Jsoup parser로 결과 타입과 안정적인 식별자를 추출해 server-internal `SourceModel`로 만든다.
- `Series`, `Event`, `Team`, `Player`를 app-facing Response로 정규화한다.
- 알 수 없는 결과 타입을 잘못된 지원 타입으로 추정하지 않는다.
- upstream 통신 실패와 parsing 실패를 각각 `UPSTREAM_NETWORK_FAILURE`, `SOURCE_PARSING_FAILURE`로 반환한다.
- raw HTML, selector, canonical upstream URL과 원본 예외를 앱에 노출하지 않는다.

## 확정 서버 API 계약

### 요청

```text
GET /api/v1/search?q={query}
```

- `q`는 필수 단일 query parameter이며 다른 query parameter는 허용하지 않는다.
- 서버는 앞뒤 공백을 제거한 query를 검색과 성공 response에 사용한다.
- 정규화한 query는 1~80자이고 문자 또는 숫자를 하나 이상 포함해야 한다.
- 제어 문자, malformed percent encoding을 포함하거나 공백·기호만으로 구성된 query는 upstream 요청 없이 거절한다.

### 성공 response

```json
{
  "query": "Sentinels",
  "results": [
    {
      "type": "team",
      "reference": {
        "resource": "team",
        "id": "2"
      },
      "name": "Sentinels",
      "tagOrRegion": "SEN · United States"
    }
  ]
}
```

- `type`과 `reference.resource`는 `series`, `event`, `team`, `player` 중 하나다.
- `reference.id`는 VLR.GG 결과 링크에서 추출한 선행 0 없는 양의 10진 식별자이며 JSON string으로 반환한다.
- 모든 결과는 `reference`와 `name`을 가진다.
- 타입별 optional 보조 필드는 Series `scope`, Event `period`, Team `tagOrRegion`, Player `identity`다. 원본에 값이 없으면 `null`이다.
- Event `period`는 검색 결과가 직접 제공하는 기간 문자열만 포함하며 같은 영역에 중첩된 상금 등 다른 metadata는 포함하지 않는다.
- 정상적인 결과 없음과 지원하지 않는 결과 타입만 존재하는 경우 `results`는 빈 배열이다.
- 지원 타입을 알 수 없는 타입으로 추정하지 않는다. 지원 결과가 전부 malformed이거나 필수 결과 container를 해석할 수 없으면 빈 결과가 아니라 parsing failure다.
- 결과 수 sentinel과 canonical 검색 링크가 불일치하거나 지원 링크의 class/path 구조가 바뀌면 빈 결과가 아니라 parsing failure다.

### 실패 response

- query validation 실패: `400 Bad Request`, `INVALID_REQUEST`
- upstream 조회 실패: `502 Bad Gateway`, `UPSTREAM_NETWORK_FAILURE`
- source DOM 해석 실패: `502 Bad Gateway`, `SOURCE_PARSING_FAILURE`
- 모든 실패는 공통 `ApiErrorResponse`를 사용하며 raw HTML, selector, upstream URL, 내부 예외 문구를 포함하지 않는다.

## Upstream 및 parser 참고

제품 화면의 app route/API endpoint와 아래 VLR.GG upstream URL을 혼동하지 않는다.

```text
https://www.vlr.gg/search/?q=search_keyword
```

- query는 URL encoding이 필요하며 parser가 입력 문자열을 HTML selector나 path로 조합하지 않게 한다.
- 결과 링크의 path와 DOM 문맥을 함께 사용해 Series, Event, Team, Player 타입을 식별한다. VLR.GG의 `eventgroup` path token은 public `series` 타입으로 정규화한다.
- slug나 표시 이름보다 안정적인 ID를 Detail navigation 식별자로 사용한다.
- 지원하지 않는 결과 타입이나 구조가 추가되면 조용히 잘못 분류하지 말고 fixture와 parser contract를 갱신한다.
- 대표 fixture는 네 타입이 섞인 결과, 실제 `eventgroup` Series DOM, 단일 타입 결과, 결과 없음, 보조 정보 누락, 예상하지 못한 결과 타입, selector/path drift를 포함한다.

## 수용 기준

- [ ] `News`, `Matches`, `MyPage`, `Events`, `About` 모든 최상위 Top App Bar에서 Search에 진입할 수 있다.
- [ ] Search 진입이 하단 navigation의 선택 상태를 바꾸지 않는다.
- [ ] 검색 결과가 Series, Event, Team, Player 타입으로 명확히 구분된다.
- [ ] 각 결과가 타입에 맞는 Detail 화면으로 이동한다.
- [ ] Search에서 뒤로 가면 진입 전 화면과 스크롤·선택·필터 상태가 복원된다.
- [ ] Detail에서 Search로 돌아오면 검색어와 결과가 복원된다.
- [ ] 입력 전 상태, 결과 없음, loading, populated, partial, error, stale 상태가 서로 구분된다.
- [ ] 공백만 있는 검색어는 서버 요청을 만들지 않는다.
- [ ] 검색 실패가 검색 결과 없음으로 표시되지 않는다.
- [ ] parser fixture가 네 지원 타입, 결과 없음, 누락 보조 정보, 예상하지 못한 타입을 검증한다.
- [ ] 서버 오류가 raw HTML, selector 또는 내부 예외 정보를 노출하지 않는다.

## 열린 결정

현재 Search 구현을 차단하는 제품 결정은 없다. 검색 실행 시점(명시적 제출 또는 debounce)과 타입별 보조 필드의 정확한 선택성은 대표 upstream fixture를 확인해 구현 contract에서 확정한다.
