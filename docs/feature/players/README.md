# Player

Player Detail은 기본 정보, 현재 Team, 전체 기간 Agent Stats와 최근 경기로 연결하는 Phase 4 기능이다. 서버·앱과 자동화 검증은 완료됐고 실기기 screenshot·접근성 확인은 남아 있다. 즐겨찾기는 [공통 계약](../README.md#즐겨찾기-계약)을 따른다.

## MVP 범위

- nullable face image, handle과 기본 정보
- nullable current Team과 Team Detail 이동
- `timespan=all` Agent Stats
- source 순서의 Recent Matches 최대 5개와 Match Detail 이동
- 로컬 Player favorite

Search, News 본문, Team roster, 지원되는 Match Player link, MyPage에서 진입한다. Event 직접 이동, Player 알림, Agent 고급 지표 확장, Recent Matches 검색·더보기·pagination·infinite scroll, 계정 동기화는 제외한다.

## 화면과 상태

Back/favorite star, Player header, Current Team logo card, Agent Stats, Recent Matches outlined card 순서다.

- Current Team, Agent Stats, Recent Matches 누락은 독립 section Empty다. atomic response이므로 generic Partial을 만들지 않는다.
- 전체 조회 실패만 Retry/Back modal error다. source에 없는 metric은 `—`이며 실제 `0`과 구분한다.
- nullable face/team image의 blank·load failure는 text placeholder와 layout을 유지한다. Agent icon은 지원하지 않는다.
- Recent Match outcome은 server의 `WIN`/`LOSS`/`UNKNOWN`만 사용한다. team 위치나 score로 추정하지 않으며 각각 `승리`/`패배`/`결과 미정` text를 함께 표시한다.
- favorite mutation과 MyPage 반영은 공통 계약대로 처리하며 notification에 영향을 주지 않는다.

## Agent Stats 표

Agent identity column은 고정하고 metric만 수평 스크롤한다. 열은 `Maps`, `Pick Rate`, `Rating`, `ACS`, `K/D`, `KAST`, `ADR` 순서이며 Agent 이름은 표시할 때 첫 글자만 대문자로 만든다.

- header 선택은 내림차순 → 오름차순 → 해제로 순환한다. 새 열은 내림차순부터 시작한다.
- 표시 문자열이 아닌 domain 숫자를 비교하고 누락 값은 양 방향 모두 마지막, 동률은 source 순서를 유지한다.
- 정렬은 추가 API 요청 없이 UiState callback으로 처리한다. Player ID별 열·방향과 가로 scroll을 보존하고 새 데이터에도 적용하며 해제하면 새 source 순서로 돌아간다.
- invalid saved value는 정렬 없음으로 복원한다. header target은 48dp 이상이며 화살표와 방향 설명을 제공한다.

## 서버 API 계약

```text
GET /api/v1/players/{playerId}
```

- `playerId`는 선행 0 없는 1~10자리 양의 10진 String이다. Search와 Team roster ID를 그대로 사용한다. invalid path/query는 upstream 요청 전에 `400 INVALID_REQUEST`다.
- 매 요청마다 `https://www.vlr.gg/player/{playerId}/?timespan=all`을 한 번 조회하며 cache, retry, stale-success fallback이 없다.
- 응답은 필수 `id`/`profile`, nullable `currentTeam`, `agentStats`, `recentMatches`다. Profile은 `handle`, `realName`, `aliases`, `countryCode`, `countryName`, `imageUrl`, Team은 `id`, `name`, `imageUrl`을 가진다.
- Agent Stats는 `agentName`, `mapsPlayed`, `pickRatePercent`, `roundsPlayed`, `rating`, `averageCombatScore`, `killDeathRatio`, `kastPercent`, `averageDamagePerRound`, `killsPerRound`, `assistsPerRound`, `firstKillDeathRatio`, `kills`, `deaths`, `assists`, `firstKills`, `firstDeaths`를 제공한다.
- Recent Match는 `id`, `eventName`, `eventStage`, `teamA`, `teamB`, `teamAScore`, `teamBScore`, `outcome`, `playedOn`을 사용한다.
- `profile.imageUrl`, `currentTeam.imageUrl`, optional numeric metric과 score/date/stage는 source 미제공 또는 안전하게 해석할 수 없으면 `null`이다.
- recentMatches는 source 순서의 최대 5개다. ID, timestamp, metric 값을 만들지 않는다.
- Player face URL을 public HTTPS로 정규화하지 못하면 `null`이며 parsing failure가 아니다.
- 필수 구조 실패는 `SOURCE_PARSING_FAILURE`, network 실패는 `UPSTREAM_NETWORK_FAILURE`다.

대표 fixture는 Stats가 없거나 일부 정보가 누락된 Player를 포함하며 null metric을 `0`으로 바꾸지 않는다. URL·slug·selector·raw HTML·내부 오류는 public response에 포함하지 않는다.

## 수용 기준

- [x] 기본 정보, Current Team, Stats, Recent Matches와 독립 Empty를 구분한다.
- [x] Recent Matches는 최대 5개며 검색·더보기·pagination이 없다.
- [x] Team·Match만 대응 Detail로 이동하고 Event 직접 이동은 없다.
- [x] 표 정렬, 누락 metric, source 순서와 화면 복원을 계약대로 처리한다.
- [x] favorite·MyPage 저장 순서·rollback이 공통 계약대로 동작한다.
- [ ] Android/iOS 실기기 시각·접근성을 검증한다.
