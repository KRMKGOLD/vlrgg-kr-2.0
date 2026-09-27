# Team

Team Detail은 기본 정보, 경기, 현재 roster와 News를 한 화면에서 연결하는 Phase 4 기능이다. 서버·앱과 자동화 검증은 완료됐고 실기기 screenshot·접근성 확인은 남아 있다. 즐겨찾기는 [공통 계약](../README.md#즐겨찾기-계약)을 따른다.

## MVP 범위

- Team header와 nullable logo
- Upcoming/Recent Matches
- Current Roster의 Player와 Staff
- 관련 News
- 로컬 Team favorite
- Match, Player, News Detail 이동

Search, News 본문, Match, MyPage에서 진입한다. Event 직접 이동, Team 알림, Transactions 전체 이력, 상세 Stats, Ranking History, 계정 동기화는 제외한다. Staff는 Player Detail로 이동하지 않는다.

## 화면과 상태

Back/favorite star, header, Upcoming Matches, Recent Matches, Current Roster, News 순서다. source에 없는 정보를 빈 문자열이나 임의 값으로 만들지 않는다.

- Loading은 안정적인 skeleton, 전체 조회 실패는 Retry/Back modal error다.
- Match·Roster·News 누락은 section Empty로 처리하고 다른 성공 콘텐츠를 유지한다. atomic response이므로 generic Partial을 만들지 않는다.
- 정보가 적은 일회성 Team도 정상 sparse content다.
- favorite star는 optimistic하게 갱신하고 Add 실패는 OFF, Remove 실패는 ON으로 rollback한다. notification permission이나 subscription에는 영향을 주지 않는다.
- nullable logo/roster image의 blank·load failure는 기존 placeholder와 layout을 유지한다.

## 서버 API 계약

```text
GET /api/v1/teams/{teamId}
```

`teamId`와 응답의 Team·Match·Player·Staff 식별자는 선행 0 없는 1~10자리 10진 String이다. Search reference를 그대로 사용한다. malformed ID, 누락·추가 path, 중복·미지원 query는 upstream 요청 전에 `400 INVALID_REQUEST`다.

응답은 `id`, `name`, nullable `tag`/`country`/`logoUrl`, `upcomingMatches`, `recentMatches`, `players`, `staff`, `news`다. Match는 `id`, `eventName`, `eventStage`, `teamName`, `opponentName`, `statusText`, `scheduledAtText`, roster는 `id`, `handle`, `realName`, `roleLabels`, `imageUrl`, News는 `reference`, `title`, `publishedDateText`를 사용한다. Team news `reference`는 News Detail이 그대로 사용하는 canonical `articleId/slug`다. Team image는 기존 `https://`, protocol-relative, root-relative VLR URL만 HTTPS로 정규화하고 `http`, `data`, `javascript`, bare-relative는 `null`이다.

- 존재하는 Team의 `name`, Match team names, roster handle, News title은 필수다. 나머지 source 미제공 field만 nullable이다.
- overview와 news를 요청 시점에 각각 조회한다. 하나의 fetch 실패도 `UPSTREAM_NETWORK_FAILURE`이며 stale-success fallback이 없다.
- Team-news page에서 header card의 인접 `wf-card`만 검증된 news container다. 직접 child 중 알려진 Match 구조는 제외하고 `a.wf-module-item`만 News로 파싱한다. 다른 관찰 child나 malformed VLR news link는 `SOURCE_PARSING_FAILURE`, untrusted external link는 제외한다.
- optional section/container가 없거나 검증된 빈 상태면 빈 array다. 관찰한 container/candidate가 malformed이면 false partial로 숨기지 않고 fail closed 한다.

대표 fixture는 활동 Team과 정보가 적은 일회성 Team을 포함한다. selector, raw HTML, canonical upstream URL과 내부 오류는 public response나 client log에 노출하지 않는다.

## 수용 기준

- [x] header, 경기, Players, Staff, News와 section Empty를 구분한다.
- [x] Match·Player·News만 대응 Detail로 이동하고 Event/Staff 이동은 제공하지 않는다.
- [x] favorite·MyPage 저장 순서·rollback이 공통 계약대로 동작한다.
- [x] optional empty와 parser drift를 구분하고 unsafe image URL을 노출하지 않는다.
- [ ] Android/iOS 실기기 시각·접근성을 검증한다.
