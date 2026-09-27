# Search

Search는 모든 root에서 Series, Event, Team, Player를 찾아 Detail로 이동하는 공통 overlay다. 서버와 앱 구현은 완료됐다. 공통 탐색·상태·오류 규칙은 [Feature Guide](../README.md)를 따른다.

## MVP 범위

- `News`, `Matches`, `MyPage`, `Events`, `About` Top App Bar에서 full-screen push
- 최대 30자 입력과 keyboard/visible Search action의 명시적 제출
- Series → Event → Team → Player 순서의 결과 그룹
- Detail 왕복 뒤 검색어·결과 복원, Search 종료 뒤 이전 root 상태 복원
- Initial, Loading, Populated, Empty, Error 다섯 화면 상태

bottom navigation item, News/Match 결과, 필터·정렬·자동완성·추천, 기록·인기 검색어·동기화, 외부 링크 preview는 제외한다.

## 표시와 인터랙션

결과는 이미지 없는 divider row와 text type label을 사용하며 전체 row가 대응 Detail을 연다. 타입을 색상만으로 전달하지 않는다. 안정적인 ID·이름·타입은 필수고, source가 제공하는 scope/period/tag·region/identity는 선택 값이다.

- 앞뒤 공백을 정리한 뒤에만 제출한다. blank 또는 문자·숫자가 없는 입력은 action을 비활성화하고 요청하지 않는다.
- 입력마다 요청하거나 debounce하지 않는다. 입력 지우기는 검색어와 결과를 Initial로 되돌린다.
- Loading은 입력을 유지한다. 이전 결과를 유지한다면 새 결과로 오인되지 않게 표시한다.
- Empty는 성공한 검색 결과 없음이며 Error와 구분한다. Error는 입력을 보존하고 같은 query Retry를 제공한다.
- optional metadata 누락은 row annotation으로 처리한다. ID·이름·타입이 없으면 정상 결과로 노출하지 않는다.

## 서버 API 계약

```text
GET /api/v1/search?q={query}
```

- `q`는 유일한 필수 query parameter다. 서버는 trim한 1~80자 문자열에 문자나 숫자가 하나 이상 있을 때만 요청한다. 앱은 더 좁은 30자 제한을 적용한다.
- 제어 문자, malformed percent encoding, blank/symbol-only query는 upstream 요청 전에 `400 INVALID_REQUEST`로 거절한다.
- 응답은 `{ query, results }`이며 각 결과는 `type`, `{ resource, id }` reference, `name`, 타입별 optional metadata를 가진다.
- `type`/`resource`는 `series`, `event`, `team`, `player` 중 하나고 ID는 선행 0 없는 양의 10진 String이다. VLR.GG `eventgroup`은 public `series`로 정규화한다.
- optional field는 Series `scope`, Event `period`, Team `tagOrRegion`, Player `identity`다. Event `period`에 상금 등 이웃 metadata를 섞지 않는다.
- 지원하지 않는 타입만 있으면 빈 배열이다. 지원 결과가 모두 malformed이거나 필수 container를 해석하지 못하면 `SOURCE_PARSING_FAILURE`다.
- 결과 수 sentinel은 canonical source element 수와 일치해야 한다. 중복·미지원 링크도 source element 하나로 세며 불일치는 fail closed 한다.

```json
{
  "query": "Sentinels",
  "results": [{
    "type": "team",
    "reference": { "resource": "team", "id": "2" },
    "name": "Sentinels",
    "tagOrRegion": "SEN · United States"
  }]
}
```

query는 URL encode하고 입력을 selector/path로 조합하지 않는다. parser는 링크 path와 DOM 문맥을 함께 사용하며 알 수 없는 타입을 지원 타입으로 추정하지 않는다. 대표 fixture는 네 타입 혼합, eventgroup, 단일 타입, 결과 없음, 보조 정보 누락, 미지원 타입과 구조 변경을 포함한다.

## 수용 기준

- [x] 다섯 root에서 Search를 열어도 bottom selection이 바뀌지 않는다.
- [x] 네 타입을 그룹·text label로 구분하고 stable ID로 Detail을 연다.
- [x] Search/Detail/원래 root 왕복 뒤 입력·결과·이전 화면 상태가 복원된다.
- [x] 30자, 명시적 제출, blank/symbol-only 차단 규칙을 지킨다.
- [x] Initial/Loading/Populated/Empty/Error와 optional annotation을 구분한다.
- [x] parsing drift를 빈 결과로 숨기거나 내부 오류 정보를 노출하지 않는다.
