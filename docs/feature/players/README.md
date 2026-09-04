# Player 기능 기획

## 구현 상태 (2026-09-03)

- **Backend: 구현 완료.** `GET /api/v1/players/{playerId}`가 nullable `profile.imageUrl`, `currentTeam.imageUrl`을 포함한 전체 기간 Player 정보, Agent Stats, 최근 경기 최대 5개를 반환하며 parser/mapper/service/route 테스트가 있다.
- **App data 연동: 구현 완료.** API DTO, remote data source, Domain Model, Repository와 Metro binding이 `app/shared`에 연결되어 있으며 `currentTeam.imageUrl`을 서버 Response에서 앱 Domain Model까지 전달한다.
- **App UI P1 sections/navigation: 구현 완료.** Player Detail의 loading/content/error, Current Team·Agent Stats·Recent Matches의 독립 empty state, `currentTeam.imageUrl`을 사용하는 Current Team logo card, outlined Recent Match card, 고정 Agent identity column과 수평 스크롤 metric table, UI Agent 이름 첫 글자 대문자 표시, Agent icon 미사용, Team/Match navigation과 state restoration 회귀 테스트가 구현되어 있다. Android/iOS 실기기 screenshot 및 접근성 검증은 아직 수행하지 않았다.
- **Favorite #43 Player Detail: 구현 완료.** 기기 로컬 즐겨찾기 상태를 복원하고, star의 등록·해제를 optimistic하게 처리한다. 실패하면 Add는 OFF, Remove는 ON으로 되돌린 뒤 actionable Retry Snackbar를 표시하며 mutation 중에도 화면 전체를 막지 않는다. 이 동작은 notification permission이나 서버 notification subscription을 만들거나 변경하지 않는다.
- **#44 MyPage: 구현 완료.** 로컬 Team/Player favorite를 독립 섹션에 저장 순서대로 표시하고, Player Detail navigation과 optimistic 제거·실패 rollback·Retry를 제공한다.
- **자동화 검증: 완료.** #43 Detail favorite와 #44 MyPage Player 목록·navigation·제거 상태는 common 및 iOS Compose 자동화 검증을 통과했다. Android/iOS 실기기 screenshot 및 pixel-perfect golden 비교는 수행하지 않았다.
- **이미지 계약 경계.** `profile.imageUrl`과 `currentTeam.imageUrl`은 서버→앱 계약에 포함된다. `currentTeam.imageUrl`은 값이 있으면 Current Team logo card에 사용하고 `null`이면 안정적인 text placeholder를 표시한다. Player face URL은 서버가 지원하지만 KMP의 표시와 DTO/Domain 전달·로컬 저장은 별도 작업 범위이므로 P1은 계속 Player face text placeholder를 사용한다. Agent icon URL은 지원하지 않으며 Agent identity는 text-only로 표시한다. Issue #68은 Team logo·roster image, Player profile·current Team image, Match Detail 팀 이미지와 Match 목록의 null 정책을 함께 정하는 강화된 server Team/Player/Match 이미지 계약이며, Issue #70은 해당 Team 이미지의 앱 적용만 다룬다. 두 이슈 모두 이 문서에서 완료로 표시하지 않는다.

## 목적과 사용자 가치

Player Detail은 사용자가 선수의 기본 정보, 현재 소속, 주로 사용한 Agent와 핵심 성과 지표, 최근 경기를 빠르게 파악하고 관련 Team과 Match로 탐색하게 한다.

## 1차 MVP 범위

- 선수 기본 정보
- 현재 팀
- 전체 기간(`timespan=all`)의 Agent별 기본 스탯
- 최근 경기 5개
- Recent Matches는 최대 5개를 표시하며 검색, 더보기, pagination, infinite scroll을 제공하지 않는다.
- Player 즐겨찾기 등록 및 해제
- 현재 팀과 최근 경기 Detail로 이동

Player 즐겨찾기는 기기 로컬에만 저장하며 알림 구독을 만들지 않는다.

## 명시적 제외 범위

- Player 알림
- Agent Stats 고급 지표 확장
- Recent Matches 전체 목록 및 추가 페이지 수집
- 계정 기반 동기화와 기기 간 즐겨찾기 공유
- Player Detail에서 Event Detail로 직접 이동하는 기능

## 진입과 이탈 경로

### 진입

- Search의 Player 결과
- News 본문의 지원되는 내부 Player 링크
- Team Detail의 Current Roster
- Match Detail의 Player 링크가 해당 화면 범위에서 제공되는 경우
- MyPage의 즐겨찾기 Player

### 이탈

- 현재 팀 선택 → Team Detail
- 최근 경기 선택 → Match Detail
- Back → 직전 화면과 해당 화면의 상태

Player Detail에서는 Event Detail로 직접 이동하지 않는다.

## 화면과 콘텐츠 계층

1. Top App Bar
   - P1: Back
   - #43: Player favorite star 구현 완료
2. Player header
   - Player face: KMP UI 적용 전까지 안정적인 text placeholder (`profile.imageUrl` 서버 지원)
   - handle
   - 제공 가능한 기본 정보
3. Current Team logo card
4. Agent Stats
5. Recent Matches outlined card

기본 정보와 현재 팀을 먼저 보여주고, Current Team은 `imageUrl` 기반 logo card로 표시한다. 표 형태의 Agent Stats는 Agent identity column을 고정하고 metric table만 수평 스크롤한다. Agent icon은 사용하지 않으며, API의 `agentName`은 유지하되 UI 표시명은 첫 글자를 대문자로 변환한다. metric 순서는 `Maps`, `Pick Rate`, `Rating`, `ACS`, `K/D`, `KAST`, `ADR`이며 Recent Matches는 outlined card로 최대 5개만 표시한다.

## 표시 데이터

| 영역 | 표시 데이터 |
| --- | --- |
| Player header | `handle`, 기본 정보, nullable `imageUrl` |
| Current Team | `id`, `name`, nullable `imageUrl`과 함께 현재 Team을 식별하고 상세로 이동하는 데 필요한 요약 정보 |
| Agent Stats | Agent별 기본 성과를 비교하는 데 필요한 요약 정보 |
| Recent Match | Match 요약 계약을 따르는 최근 경기 정보 |
| Favorite | 현재 Player의 로컬 즐겨찾기 여부 |

upstream에 없는 기본 정보나 Stats 값을 `0` 또는 임의 값으로 만들어 표시하지 않는다.

## 화면 상태

| 상태 | 동작 |
| --- | --- |
| Loading | header, stats, 최근 경기 영역의 안정적인 skeleton을 표시한다. |
| Populated | 기본 정보, 현재 팀, Agent Stats, 최근 경기를 표시한다. |
| Sparse / Empty section | 현재 server response는 atomic이므로 generic Partial 화면을 만들지 않는다. Current Team, Agent Stats, Recent Matches의 누락은 section-level Empty로 표시하고 missing metric은 `—` marker로 표시한다. |
| Empty section | 현재 팀, Stats, Recent Matches가 없으면 해당 섹션별 빈 상태를 표시한다. |
| Error | Player Detail 자체를 불러오지 못하면 일반화된 오류와 재시도 동작을 표시한다. |
| Add favorite error | star를 OFF로 되돌리고 actionable Retry Snackbar를 표시한다. |
| Remove favorite error | star를 ON으로 유지하고 actionable Retry Snackbar를 표시한다. |
| Stale | 이전 데이터를 유지해 표시하도록 구현하는 경우 마지막 갱신 시각과 오래된 데이터임을 명시한다. silent stale fallback은 사용하지 않는다. |

Stats가 없거나 현재 팀이 없는 Player도 유효한 Player Detail로 표현할 수 있어야 한다.

## 사용자 인터랙션

- 즐겨찾기 토글을 누르면 해당 Player를 로컬 즐겨찾기에 추가하거나 제거한다.
- 즐겨찾기 변경은 Detail star에 즉시 optimistic하게 반영하며 mutation 중에도 화면 전체 action을 막지 않는다. #44 MyPage 즐겨찾기 UI도 구현 완료되었다.
- 즐겨찾기 Add 실패는 star를 OFF로 되돌리고 Retry Snackbar를, Remove 실패는 star를 ON으로 유지하고 Retry Snackbar를 표시한다.
- 즐겨찾기 등록은 notification permission을 요구하거나 서버 알림 구독을 만들지 않는다.
- Current Team을 누르면 Team Detail로 이동한다.
- Recent Match를 누르면 Match Detail로 이동한다.
- 오류 상태는 Retry/Back만 제공하는 modal error dialog로 표시하며 generic Partial screen은 만들지 않는다.

## 앱·서버 책임 경계

### 앱

- 서버 Response를 app remote DTO로 역직렬화하고 Domain Model로 매핑한다.
- Player 즐겨찾기를 기기 로컬 persistence에 저장하고 Detail에서 복원한다. #44 MyPage는 이 저장소를 관찰해 Player 목록·Detail navigation·제거 UI를 제공한다.
- Stats의 화면 상태와 navigation callback을 관리한다.
- Player 즐겨찾기와 notification 상태를 연결하지 않는다.

### 서버

- Player 페이지를 전체 기간 조건으로 수집하고 `Scraper → Parser → SourceModel → Mapper → Response` 경계를 지킨다.
- Player 기본 정보, 현재 팀, Agent Stats, 최근 경기 5개를 앱에 적합한 응답으로 가공한다.
- Player face URL은 `profile.imageUrl`로 전달한다. 값이 없거나 public HTTPS URL로 정규화할 수 없으면 `null`이며, 이는 DOM parsing failure가 아니다.
- upstream에서 제공되지 않는 지표를 임의의 값으로 보정하지 않는다.
- upstream DOM 구조, selector, 원본 HTML, 내부 오류를 앱 응답에 노출하지 않는다.
- 일반 조회 실패 시 이전 결과를 성공 응답으로 반환하는 stale fallback을 사용하지 않는다.

## Server public API contract

### GET /api/v1/players/{playerId}

- playerId는 `[1-9][0-9]{0,9}` 형식의 canonical decimal String이다. 선행 0, 0, 11자리 이상, 비숫자 값은 허용하지 않는다.
- 경로 ID는 Search의 Player 결과 `reference.id`와 Team Detail Current Roster의 `players[].id`를 변환 없이 그대로 사용할 수 있다.
- query parameter는 지원하지 않는다. 누락된 ID, trailing path, 중복 또는 알려지지 않은 query를 포함한 모든 잘못된 입력은 upstream 요청 전에 `400 INVALID_REQUEST`를 반환한다.
- 매 요청마다 `https://www.vlr.gg/player/{playerId}/?timespan=all`을 한 번 조회한다. cache, retry, stale-success fallback은 사용하지 않는다.

성공 응답 shape는 아래와 같다. 모든 ID는 앱 navigation을 위한 String이고, upstream slug나 URL은 노출하지 않는다.

```json
{
  "id": "488",
  "profile": {
    "handle": "Rb",
    "realName": "Goo Sang-min",
    "aliases": ["ClokingRb"],
    "countryCode": "kr",
    "countryName": "SOUTH KOREA",
    "imageUrl": "https://owcdn.net/img/69d5f87b7c32d.png"
  },
  "currentTeam": { "id": "11060", "name": "Nongshim RedForce", "imageUrl": "https://owcdn.net/img/6399bb707aacb.png" },
  "agentStats": [{
    "agentName": "jett", "mapsPlayed": 134, "pickRatePercent": 25,
    "roundsPlayed": 2680, "rating": 1.07, "averageCombatScore": 235.1,
    "killDeathRatio": 1.3, "kastPercent": 72, "averageDamagePerRound": 140.5,
    "killsPerRound": 0.83, "assistsPerRound": 0.13, "firstKillDeathRatio": 1.25,
    "kills": 2224, "deaths": 1712, "assists": 355, "firstKills": 545, "firstDeaths": 435
  }],
  "recentMatches": [{
    "id": "708427", "eventName": "EWC 2026", "eventStage": "Playoffs · CF",
    "teamA": { "name": "Nongshim RedForce", "tag": "NS" },
    "teamB": { "name": "BBL Esports", "tag": "BBL" },
    "teamAScore": 2, "teamBScore": 0, "outcome": "WIN", "playedOn": "2026-07-12"
  }]
}
```

- `profile.imageUrl`과 `currentTeam.imageUrl`은 nullable이다. `profile.imageUrl`의 `null`은 upstream에서 사용할 수 있는 Player face URL이 없다는 뜻이며 서버 parsing failure가 아니다. 서버가 값을 제공해도 KMP 표시와 저장은 별도 작업 범위이며, 그 전까지 앱은 Player face text placeholder를 표시한다. `currentTeam`은 없을 수 있고, `agentStats`와 `recentMatches`는 빈 배열일 수 있다. `currentTeam.imageUrl`이 `null`이면 앱은 현재 팀이 없다고 처리하지 않고 logo card의 안정적인 text placeholder를 표시한다.
- agentStats의 optional numeric metric과 Recent Match score/date/stage는 source에 없거나 유효하게 해석할 수 없으면 `null`이다. 값 0을 임의로 만들지 않는다.
- recentMatches는 source 순서의 최대 5개다. source에서 제공하지 않는 ID나 timestamp는 만들지 않는다.
- upstream network failure는 `502 UPSTREAM_NETWORK_FAILURE`, DOM parsing failure는 `502 SOURCE_PARSING_FAILURE` 공통 envelope로만 노출한다. URL, slug, selector, raw HTML, 내부 exception text는 public response에 포함하지 않는다.

## Upstream URL 및 파서 메모

제품 화면 계약과 분리해 parser fixture 선정에만 사용한다.

```text
https://www.vlr.gg/player/488/rb/?timespan=all
```

- 1차 MVP는 전체 기간 데이터를 필요로 하므로 `timespan=all` 조건을 유지한다.
- Stats가 없거나 일부 정보가 누락된 Player 사례를 별도 fixture로 포함한다.
- Agent별 통계의 누락을 값 `0`으로 오인하지 않는다.
- selector와 원본 HTML은 public response나 client log에 노출하지 않는다.

## 테스트 가능한 수용 기준

- [x] Player Detail은 기본 정보, 현재 팀, Agent Stats, 최근 경기 영역을 구분해 표시한다.
- [x] Agent Stats가 없는 상태와 Player Detail 조회 실패를 구분한다.
- [x] 누락된 Stats 값은 `0` 또는 임의 값으로 표시되지 않는다.
- [x] Recent Matches는 최대 5개를 source 순서로 표시하고 전체 목록 더보기는 제공하지 않는다.
- [x] Recent Matches에는 검색, 더보기, pagination, infinite scroll이 모두 없다.
- [x] Current Team과 Recent Match는 각각 Team Detail과 Match Detail로 이동한다.
- [x] Player Detail에는 Event로 직접 이동하는 인터랙션이 없다.
- [x] #43: Player Detail의 즐겨찾기 상태는 기기 로컬 persistence에서 복원된다.
- [x] #43: Player favorite Add 실패는 star OFF와 actionable Retry Snackbar를 표시한다.
- [x] #43: Player favorite Remove 실패는 star ON을 유지하고 actionable Retry Snackbar를 표시한다.
- [x] #43: Player favorite mutation은 전체 화면을 block하지 않는다.
- [x] #43: Player 즐겨찾기 등록·해제는 notification permission이나 서버 notification subscription을 만들거나 변경하지 않는다.
- [x] #44: Player 즐겨찾기는 MyPage의 Player 그룹에 저장 순서대로 집계되고, 항목 Detail navigation과 제거·실패 Retry UI를 제공한다.
- [x] 현재 팀, Stats 또는 최근 경기가 없어도 나머지 Player 정보는 정상 표시된다.
- [x] loading, empty section, error dialog가 유효 콘텐츠와 시각적으로 구분되고 generic Partial screen은 없다.
- [ ] stale 상태는 현재 범위에 없으며, 향후 도입 시 마지막 갱신 시각과 오래된 데이터를 명시한다.
