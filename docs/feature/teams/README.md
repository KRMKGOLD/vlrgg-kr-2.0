# Team 기능 기획

## 구현 상태 (2026-08-30)

- **Backend: 구현 완료.** `GET /api/v1/teams/{teamId}`가 Team overview와 news를 요청 시점에 수집해 app-facing response로 반환하며 parser/route 테스트가 있다.
- **App data 연동: 구현 완료.** API DTO, remote data source, Domain Model, Repository와 Metro binding이 `app/shared`에 연결되어 있다.
- **App UI T1 Team Detail: 구현 완료.** Team Detail의 loading/content/error, 섹션별 빈 상태, Match·Player·News Detail navigation이 구현되어 있다.
- **Favorite #43 Team Detail: 구현 완료.** 기기 로컬 즐겨찾기 상태를 복원하고, star의 등록·해제를 optimistic하게 처리한다. 실패하면 Add는 OFF, Remove는 ON으로 되돌린 뒤 actionable Retry Snackbar를 표시하며 mutation 중에도 화면 전체를 막지 않는다. 이 동작은 notification permission이나 서버 notification subscription을 만들거나 변경하지 않는다.
- **#44 MyPage: 예정.** Team/Player 로컬 persistence 기반만 준비되었으며, MyPage 즐겨찾기 집계·목록·Detail navigation·제거 UI는 아직 구현하지 않았다.
- **자동화 검증: 완료.** #43 Detail favorite 동작은 Compose UI 및 iOS simulator 자동화 검증을 통과했다. Android/iOS 실기기 screenshot 및 접근성 검증은 이 범위에서 수행하지 않았다.
- **Player Detail P1: 구현 완료.** Team Detail에서 Player Detail destination으로 이동하며, Player Detail 자체 구현도 완료되었다.
- **#42 전체: 완료/종료.** T1 Team Detail UI/navigation과 후속 Player Detail P1 범위가 구현 완료되었다. Favorite는 별도 Issue #43에서 구현 완료되었으며, MyPage 범위는 #44에 남아 있다.

## 목적과 사용자 가치

Team Detail은 사용자가 팀의 정체성, 예정·최근 경기, 현재 로스터, 관련 뉴스를 한 화면에서 확인하고 연결된 Match, Player, News로 탐색하게 한다. 정보가 적은 일회성 팀도 실제로 존재하는 범위만 정직하게 보여준다.

## 1차 MVP 범위

- 팀 기본 정보
- Upcoming Matches
- Recent Matches
- Current Roster의 Players와 Staff
- 관련 News
- Team 즐겨찾기 등록 및 해제 (#43 구현 완료)
- 각 콘텐츠에서 Match Detail, Player Detail, News Detail로 이동

Team 즐겨찾기는 #43에서 구현되었으며 기기 로컬에만 저장하고 알림 구독을 만들거나 변경하지 않는다.

## 명시적 제외 범위

- Team 알림
- Transactions 전체 이력
- 팀 상세 Stats
- Ranking History
- Team Detail에서 Event Detail로 직접 이동하는 기능
- 계정 기반 동기화와 기기 간 즐겨찾기 공유

## 진입과 이탈 경로

### 진입

- Search의 Team 결과
- News 본문의 지원되는 내부 Team 링크
- Match Detail 또는 Match 목록의 Team 항목
- MyPage의 즐겨찾기 Team

### 이탈

- Upcoming/Recent Match 선택 → Match Detail
- Current Roster의 Player 선택 → Player Detail
- News 선택 → News Detail
- Back → 직전 화면과 해당 화면의 상태

Team Detail에서는 Event Detail로 직접 이동하지 않는다.

## 화면과 콘텐츠 계층

1. Top App Bar
   - Back
   - Team Detail title
   - Team favorite star (#43 구현 완료)
2. Team header
   - 팀 로고 또는 안정적인 placeholder
   - 팀 이름
   - 제공 가능한 기본 정보
3. Upcoming Matches
4. Recent Matches
5. Current Roster
   - Players
   - Staff
6. News

섹션이 비어 있으면 화면 전체를 실패로 처리하지 않고 해당 섹션의 빈 상태를 표시한다. 팀 이름은 현재 화면의 핵심 식별 요소이며, 즐겨찾기 개인화는 #43에서 추가한다.

## 표시 데이터

| 영역 | 표시 데이터 |
| --- | --- |
| Team header | Team을 식별하고 이해하는 데 필요한 기본 정보 |
| Match | Upcoming/Recent Match를 구분하고 상세로 이동하는 데 필요한 요약 정보 |
| Player | Current Roster의 Player를 식별하는 요약 정보 |
| Staff | Staff를 식별하는 요약 정보 |
| News | News 목록 계약을 따르는 요약 정보 |
| Favorite (#43 구현 완료) | 현재 Team의 로컬 즐겨찾기 여부 |

source에 존재하지 않는 정보를 빈 문자열이나 임의 값으로 만들어 표시하지 않는다.

## 화면 상태

| 상태 | 동작 |
| --- | --- |
| Loading | header와 주요 섹션의 안정적인 skeleton을 표시한다. |
| Populated | 존재하는 기본 정보와 섹션을 계층에 맞게 표시한다. |
| Sparse / Empty section | 현재 server response는 atomic이므로 generic Partial 화면을 만들지 않는다. Match, Roster, News의 누락은 section-level Empty로 표시하고 missing value는 marker로 표시한다. |
| Empty section | Match, Roster, News가 없으면 섹션별 명시적 빈 상태를 표시한다. |
| Error | Team Detail 자체를 불러오지 못하면 일반화된 오류와 재시도 동작을 표시한다. raw exception이나 파서 정보를 노출하지 않는다. |
| Add favorite error (#43 구현 완료) | star를 OFF로 되돌리고 actionable Retry Snackbar를 표시한다. |
| Remove favorite error (#43 구현 완료) | star를 ON으로 유지하고 actionable Retry Snackbar를 표시한다. |
| Stale | 앱이 이전 데이터를 유지해 표시하도록 구현하는 경우 마지막 갱신 시각과 오래된 데이터임을 명시한다. silent stale fallback은 사용하지 않는다. |

존재하는 정보가 적은 팀은 오류가 아니라 정상적인 부분/빈 콘텐츠로 처리한다.

## 사용자 인터랙션

- #43에서 즐겨찾기 토글을 누르면 해당 Team을 기기 로컬 즐겨찾기에 추가하거나 제거하며, Detail star를 즉시 optimistic하게 갱신한다.
- #43의 즐겨찾기 mutation은 화면 전체 action을 막지 않는다. Add 실패는 star를 OFF로 되돌리고 Retry Snackbar를, Remove 실패는 star를 ON으로 유지하고 Retry Snackbar를 표시한다.
- #43의 즐겨찾기 등록·해제는 notification permission을 요구하거나 서버 알림 구독을 만들거나 변경하지 않는다.
- Match, Player, News 항목을 누르면 대응하는 Detail로 이동한다.
- 오류 상태는 Retry/Back만 제공하는 modal error dialog로 표시하며 generic Partial screen은 만들지 않는다.

## 앱·서버 책임 경계

### 앱

- 서버 Response를 app remote DTO로 역직렬화하고 Domain Model로 매핑한다.
- #43에서 Team 즐겨찾기를 기기 로컬 persistence에 저장하고 Detail에서 복원한다. MyPage의 즐겨찾기 집계·목록·navigation·제거 UI는 #44 범위다.
- 화면 상태, 날짜/시간 표시, 섹션 구성, navigation callback을 관리한다.
- #43의 Team 즐겨찾기와 notification 상태를 연결하지 않는다.

### 서버

- Team 페이지를 요청 시점에 수집하고 `Scraper → Parser → SourceModel → Mapper → Response` 경계를 지킨다.
- 기본 정보, 경기, 로스터, 뉴스 데이터를 앱에 적합한 응답으로 가공한다.
- upstream DOM 구조, selector, 원본 HTML, 내부 오류를 앱 응답에 노출하지 않는다.
- 일반 조회 실패 시 이전 결과를 성공 응답으로 반환하는 stale fallback을 사용하지 않는다.

## Server API 계약

`GET /api/v1/teams/{teamId}`는 Team overview와 Team news를 요청 시점에 각각 조회해 하나의 응답으로 반환한다. `teamId`와 응답 안의 Team·Match·Player·Staff·News 식별자는 모두 JSON String이며, path의 Team ID는 `[1-9][0-9]{0,9}`에 맞는 canonical decimal만 허용한다. 따라서 Search의 Team `reference.id` String은 변환 없이 이 endpoint path에 사용할 수 있다.

성공 응답은 다음 field를 가진다.

```json
{
  "id": "8185",
  "name": "KIWOOM DRX",
  "tag": "KRX",
  "country": "South Korea",
  "upcomingMatches": [{ "id": "698887", "eventName": "...", "eventStage": "...", "teamName": "...", "opponentName": "...", "statusText": "...", "scheduledAtText": "..." }],
  "recentMatches": [],
  "players": [{ "id": "4462", "handle": "MaKo", "realName": "...", "roleLabels": [] }],
  "staff": [{ "id": "775", "handle": "termi", "realName": "...", "roleLabels": ["head coach"] }],
  "news": [{ "reference": "700755/kiwoom-drx-releases-rookie-hermes", "title": "...", "publishedDateText": "..." }]
}
```

`name`, match `teamName`/`opponentName`, roster `handle`, and news `title` are required when their supported source item exists. Each Team news `reference` is the canonical `articleId/slug` String required directly by `GET /api/v1/news/{articleId}/{slug}`; it is not reconstructed from missing source values. `tag`, `country`, match metadata, roster `realName`, and news publication text are nullable only when the source omits them. On the live Team-news page, `.team-header` is inside its header `wf-card` and the adjacent sibling `wf-card` is the verified news container; the verified container classifies its direct children in order: known VLR match structures (`match-item`/descendants or overview `m-item`/descendants) are non-news contamination and excluded even without the news class, then only `a.wf-module-item` is parsed as News; every other observed child is structural drift and returns `502 SOURCE_PARSING_FAILURE`. A malformed relative or accepted VLR-host news link in that verified card returns `502 SOURCE_PARSING_FAILURE`, while untrusted external links remain excluded. An absent or verified-empty optional section, including a Team news page with no news container, serializes as its corresponding empty array; an observed section/container/candidate that has drifted or is malformed returns `502 SOURCE_PARSING_FAILURE` rather than silently returning false partial data, while unrelated or contaminated links outside the verified section are excluded.

Invalid/missing/duplicate/unknown query input and malformed, leading-zero, or overlong Team IDs (including `/api/v1/teams` with no ID) return `400 INVALID_REQUEST` before any upstream request. Either upstream fetch failure returns `502 UPSTREAM_NETWORK_FAILURE`; parser failures return `502 SOURCE_PARSING_FAILURE`. These common envelopes contain only the stable code and safe message, never upstream URLs, selectors, raw HTML, or exception text.

## Upstream URL 및 파서 메모

제품 화면 계약과 분리해 parser fixture 선정에만 사용한다.

```text
https://www.vlr.gg/team/8185/kiwoom-drx
https://www.vlr.gg/team/19296/team-korea
```

- 일반적인 활동 팀과 이력이 적은 일회성 팀을 각각 fixture로 검증한다.
- 섹션 존재 여부와 필드 누락을 DOM 위치만으로 동일시하지 않는다.
- selector, 원본 HTML, canonical upstream URL은 public response나 client log에 노출하지 않는다.

## 테스트 가능한 수용 기준

- [x] Team Detail은 기본 정보, Upcoming Matches, Recent Matches, Players, Staff, News를 서로 구분해 표시한다.
- [x] 섹션 하나가 비어도 다른 성공 섹션은 계속 표시된다.
- [x] 정보가 적은 일회성 팀이 전체 오류로 잘못 처리되지 않는다.
- [x] Match, Player, News 항목은 각각 올바른 Detail로 이동한다.
- [x] Team Detail에는 Event로 직접 이동하는 인터랙션이 없다.
- [x] #43: Team Detail의 즐겨찾기 상태는 기기 로컬 persistence에서 복원된다.
- [x] #43: Team favorite Add 실패는 star OFF와 actionable Retry Snackbar를 표시한다.
- [x] #43: Team favorite Remove 실패는 star ON을 유지하고 actionable Retry Snackbar를 표시한다.
- [x] #43: Team favorite mutation은 전체 화면을 block하지 않는다.
- [x] #43: Team 즐겨찾기 등록·해제는 notification permission이나 서버 notification subscription을 만들거나 변경하지 않는다.
- [ ] #44: Team 즐겨찾기는 MyPage의 Team 그룹에 집계되고, 항목 Detail navigation과 제거 UI를 제공한다.
- [x] loading, empty section, error dialog가 유효 콘텐츠와 시각적으로 구분되고 generic Partial screen은 없다. stale 화면은 현재 범위에 없다.
- [x] 서버 parser test는 일반 팀과 이력이 적은 팀 fixture를 모두 검증한다.
