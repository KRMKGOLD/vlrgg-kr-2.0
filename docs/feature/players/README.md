# Player 기능 기획

## 구현 상태 (2026-07-22)

- **Backend: 구현 완료.** `GET /api/v1/players/{playerId}`가 전체 기간 Player 정보, 현재 팀, Agent Stats, 최근 경기 최대 5개를 반환하며 parser/mapper/service/route 테스트가 있다.
- **App: 미구현.** Player Detail UI, 로컬 즐겨찾기, MyPage 연동과 Team/Match navigation은 아직 구현되어 있지 않다.

## 목적과 사용자 가치

Player Detail은 사용자가 선수의 기본 정보, 현재 소속, 주로 사용한 Agent와 핵심 성과 지표, 최근 경기를 빠르게 파악하고 관련 Team과 Match로 탐색하게 한다.

## 1차 MVP 범위

- 선수 기본 정보
- 현재 팀
- 전체 기간(`timespan=all`)의 Agent별 기본 스탯
- 최근 경기 5개
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
   - Back
   - 화면 제목 또는 Player handle
2. Player header
   - Player 이미지 또는 안정적인 placeholder
   - handle
   - 제공 가능한 기본 정보
   - 즐겨찾기 토글
3. Current Team
4. Agent Stats
5. Recent Matches

기본 정보와 현재 팀을 먼저 보여주고, 표 형태의 Agent Stats는 작은 화면에서도 지표와 행의 관계가 유지되도록 구성한다. Recent Matches는 최대 5개만 표시한다.

## 표시 데이터

| 영역 | 표시 데이터 |
| --- | --- |
| Player header | Player를 식별하고 이해하는 데 필요한 기본 정보 |
| Current Team | 현재 Team을 식별하고 상세로 이동하는 데 필요한 요약 정보 |
| Agent Stats | Agent별 기본 성과를 비교하는 데 필요한 요약 정보 |
| Recent Match | Match 요약 계약을 따르는 최근 경기 정보 |
| Favorite | 현재 Player의 로컬 즐겨찾기 여부 |

upstream에 없는 기본 정보나 Stats 값을 `0` 또는 임의 값으로 만들어 표시하지 않는다.

## 화면 상태

| 상태 | 동작 |
| --- | --- |
| Loading | header, stats, 최근 경기 영역의 안정적인 skeleton을 표시한다. |
| Populated | 기본 정보, 현재 팀, Agent Stats, 최근 경기를 표시한다. |
| Partial | 일부 기본 정보, 현재 팀, 특정 지표 또는 최근 경기만 누락되면 나머지 콘텐츠를 유지하고 누락을 명시한다. |
| Empty section | 현재 팀, Stats, Recent Matches가 없으면 해당 섹션별 빈 상태를 표시한다. |
| Error | Player Detail 자체를 불러오지 못하면 일반화된 오류와 재시도 동작을 표시한다. |
| Stale | 이전 데이터를 유지해 표시하도록 구현하는 경우 마지막 갱신 시각과 오래된 데이터임을 명시한다. silent stale fallback은 사용하지 않는다. |

Stats가 없거나 현재 팀이 없는 Player도 유효한 Player Detail로 표현할 수 있어야 한다.

## 사용자 인터랙션

- 즐겨찾기 토글을 누르면 해당 Player를 로컬 즐겨찾기에 추가하거나 제거한다.
- 즐겨찾기 변경은 즉시 화면과 MyPage에 일관되게 반영한다.
- 즐겨찾기 등록은 notification permission을 요구하거나 서버 알림 구독을 만들지 않는다.
- Current Team을 누르면 Team Detail로 이동한다.
- Recent Match를 누르면 Match Detail로 이동한다.
- 오류 상태에서 Player Detail을 다시 요청할 수 있다.

## 앱·서버 책임 경계

### 앱

- 서버 Response를 app remote DTO로 역직렬화하고 Domain Model로 매핑한다.
- Player 즐겨찾기를 로컬 persistence에 저장하고 MyPage와 상태를 공유한다.
- Stats의 화면 상태와 navigation callback을 관리한다.
- Player 즐겨찾기와 notification 상태를 연결하지 않는다.

### 서버

- Player 페이지를 전체 기간 조건으로 수집하고 `Scraper → Parser → SourceModel → Mapper → Response` 경계를 지킨다.
- Player 기본 정보, 현재 팀, Agent Stats, 최근 경기 5개를 앱에 적합한 응답으로 가공한다.
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
    "countryName": "SOUTH KOREA"
  },
  "currentTeam": { "id": "11060", "name": "Nongshim RedForce" },
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

- `currentTeam`은 없을 수 있고, `agentStats`와 `recentMatches`는 빈 배열일 수 있다.
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

- [ ] Player Detail은 기본 정보, 현재 팀, Agent Stats, 최근 경기 영역을 구분해 표시한다.
- [ ] Agent Stats가 없는 상태와 Player Detail 조회 실패를 구분한다.
- [ ] 누락된 Stats 값은 `0` 또는 임의 값으로 표시되지 않는다.
- [ ] Recent Matches는 최대 5개만 표시되고 전체 목록 더보기는 제공하지 않는다.
- [ ] Current Team과 Recent Match는 각각 Team Detail과 Match Detail로 이동한다.
- [ ] Player Detail에는 Event로 직접 이동하는 인터랙션이 없다.
- [ ] 즐겨찾기 등록 후 Player가 MyPage의 Player 그룹에 나타나고, 제거 후 사라진다.
- [ ] Player 즐겨찾기 등록·해제는 서버 notification subscription을 생성하거나 변경하지 않는다.
- [ ] 현재 팀, Stats 또는 최근 경기가 없어도 나머지 Player 정보는 정상 표시된다.
- [ ] loading, empty section, partial, error, stale 상태가 유효 콘텐츠와 시각적으로 구분된다.
