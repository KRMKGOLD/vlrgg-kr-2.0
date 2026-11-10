# Matches

## 문서 역할

이 문서는 Upcoming/Live, Results, Match Detail의 제품 요구사항을 정의한다. 공통 시각 언어와 상태 표현은 루트 [`DESIGN.md`](../../../DESIGN.md), 전체 내비게이션과 즐겨찾기 관계는 상위 [`docs/feature/README.md`](../README.md)를 따른다.

2026-09-27 범위 결정: 목록·상세 조회는 1차 MVP에 포함한다. 경기 알림은 현재 제품 범위 밖이다([제외 범위](#제외-범위)).

## 구현 상태

Matches 조회 API, Upcoming/Live·Results 목록과 Match Detail Basic은 구현되어 있다. 목록의 독립 pagination·refresh, 상태별 Detail, Team/Event/H2H 이동과 화면 복원은 자동 검증을 통과했지만 실제 양 플랫폼 screenshot·실기기 접근성 확인은 남아 있다.

## 목적과 사용자 가치

- 예정, 진행 중, 완료 경기를 시간과 상태 중심으로 빠르게 확인하게 한다.
- Match에서 관련 Event와 Team으로 이어지는 탐색 경로를 제공한다.

## MVP 범위

### Upcoming / Live

- 날짜별 예정 및 진행 중 경기 목록
- 경기 시각과 예정 경기의 남은 시간
- 양 팀 정보
- Event 이름
- 명시적인 경기 상태
- 페이지 기반 추가 로딩

### Results

- 날짜별 완료 경기 목록
- 경기 완료 시각
- 양 팀 정보와 스코어
- Event 이름
- 페이지 기반 추가 로딩

### Match Detail Basic

- Event 이름과 경기 설명
- A Team 대 B Team
- 스코어와 경기 상태
- 맵 목록
- Head to Head

## 제외 범위

다음 항목은 현재 범위에서 제외한다.

- 경기 알림 전체. 범위 결정은 [Feature Guide](../README.md#mvp-이후-stage-2-경기-알림)를 따른다.
- 맵별 상세 스탯과 선수별 Agent, Rating, K/D/A, KAST, ADR, HS, FK, FD
- Team 또는 Player 즐겨찾기에 따른 알림
- 알림함과 알림 이력 화면
- 계정, 사용자 DB, 기기 간 즐겨찾기 동기화
- 공개 서비스 규모를 위한 동적 polling 간격이나 분산 scheduler 최적화
- 경기 시간 변경, 연기, 취소 자체에 대한 별도 사용자 알림
- Match 종료 알림
- VLR.GG 원본 BO 구조를 그대로 노출하는 화면

BO1/BO3/BO5와 몰수승·패처럼 정보가 제한된 경기는 MVP parser가 구분해서 안전하게 표현해야 하지만, 고급 스탯을 보완해 만들지는 않는다.

## 진입과 이탈 내비게이션

### 진입

- Bottom navigation의 `Matches` 탭에서 진입한다.
- Matches는 최상위 탭이므로 공통 Top App Bar와 Search 액션을 제공한다.
- Event Detail, Team Detail과 Player Detail의 지원되는 Match 항목에서도 Match Detail로 진입할 수 있다.

### 내부 이동과 이탈

- Matches 안에서 Upcoming/Live와 Results를 명확히 전환한다.
- 경기 항목은 Match Detail로 이동한다.
- Match의 Event reference는 Event Detail로 이동한다.
- Match의 Team reference는 Team Detail로 이동할 수 있다.
- Back은 직전 화면으로 돌아가며 선택한 목록 종류, 로딩된 페이지, 스크롤 위치를 보존한다.
- Search를 열었다가 Back으로 돌아오는 경우에도 Matches 상태를 보존한다.

Match Detail과 관련 상세 화면은 bottom-navigation 목적지가 아니다.

## 화면 및 콘텐츠 계층

### Matches List

1. Top App Bar: 화면 제목, Search 액션
2. `Upcoming / Live`와 `Results` 전환
3. 날짜 구분
4. Match row/card
   - Live 또는 예정/완료 상태
   - 경기 시각 또는 완료 시각 (형식과 fallback은 [앱 표시 규칙](#앱-표시-규칙))
   - 양 팀 이름 (목록 응답의 `team.imageUrl`은 source에 없어 null이며 목록 card는 팀 이름만 표시)
   - 예정 경기의 남은 시간 또는 완료 경기의 스코어
   - Event 이름
5. 다음 페이지 로딩 또는 페이지 로딩 실패 표시

Live 상태는 색상만으로 전달하지 않고 텍스트 label을 함께 사용한다. 시간, 상태, 팀, Event 순으로 scan할 수 있어야 한다.

### Match Detail

1. MVP의 D1에서는 뒤로가기만 제공한다.
2. 경기 상태와 예정/시작 시각
3. Event 이름과 경기 설명
4. 양 팀과 스코어
5. 맵 목록
6. Head to Head

정보가 존재하지 않는 FFW 등의 terminal Match는 비어 있는 정상 스탯 화면처럼 보이지 않아야 한다. 확인 가능한 팀, 결과, 상태를 우선 표시하고 사용할 수 없는 section은 명시적으로 생략하거나 unavailable로 표현한다.
Upcoming/Postponed pre-match에서는 Maps와 Head to Head를 section-level Empty로 표시할 수 있다. Match Detail의 Event identity와 Team identity는 각각 Event/Team Detail로 이동한다.
Team hero는 양쪽 모두 로고 영역을 위에, Team name을 아래에 쌓는 대칭 구조이며, score/result는 두 Team hero 사이의 수평 중앙에 배치한다. 서버 Detail 응답은 검증된 team header 이미지가 있을 때만 HTTPS `imageUrl`을 제공하고 KMP가 이를 표시한다. 목록 응답은 source 제약으로 `imageUrl`이 null이다.

## 표시 데이터와 선택성

### Match summary

- 안정적인 Match ID
- 경기 상태: 최소 upcoming, live, completed와 upstream에서 확인된 postponed, cancelled 또는 unavailable 계열 상태
- 예정/시작/완료를 해석할 수 있는 시각 정보
- Team A와 Team B의 식별 정보
- Results의 스코어
- Event 식별 정보와 이름

예정 경기의 스코어와 완료 경기의 남은 시간처럼 상태상 존재하지 않는 값은 결측 오류가 아니다. 팀 미정(TBD), 몰수승·패, 취소·연기는 null 남용이 아니라 명시적인 상태 또는 availability로 표현한다. 시간대와 상대 시간 문자열은 UI 경계에서 사용자의 locale/timezone에 맞춰 표시한다.

### Match detail

- summary 정보
- 경기 설명
- series format 또는 확인 가능한 BO 정보
- 맵 목록과 각 맵의 기본 결과(원문에 존재하는 범위)
- Head to Head 목록
- (기존 서버 응답에 `pastMatches`가 있으면 server 구현 사실은 보존하되 UI에서는 비노출하며 후속 계약에서 정리한다.)

Match Detail Basic에 필수인 팀과 상태를 해석하지 못하면 parsing failure로 처리한다. 경기 전이라 스코어가 없거나 FFW로 맵 정보가 없는 경우는 정상적인 상태별 선택성이다.

## 화면 상태

### Matches List

- `Initial loading`: 최초 목록 skeleton을 표시한다.
- `Populated`: 선택한 목록의 경기가 날짜별로 표시된다.
- `Empty`: 정상 응답이지만 선택한 Upcoming/Live 또는 Results에 경기가 없다.
- `Pagination error`: 기존 페이지는 유지하지만 다음 페이지 요청이 실패했다. footer Retry를 제공한다.
- `Initial error`: 최초 목록 요청 실패로 표시할 경기가 없다. 전체 화면 Retry를 제공한다.
- `Stale`: 서버는 이전 scraping 결과를 fallback으로 반환하지 않는다. 향후 앱이 기존 목록을 유지한다면 마지막 확인 시각과 갱신 실패를 명시해야 한다.

### Match Detail

- `Loading`: 상태/팀/스코어/section skeleton을 안정적으로 표시한다.
- `Populated`: Match 상태에 맞는 필수 정보와 사용 가능한 section을 표시한다.
- `Partial`: 기본 Match 정보는 유효하지만 map 또는 Head to Head 중 선택적 section 일부를 사용할 수 없다.
- `Empty/Unavailable`: Match는 식별되지만 표시 가능한 필수 정보가 없거나 upstream에서 더 이상 정상 제공되지 않는다.
- `Error`: network 또는 필수 구조 parsing 실패로 상세를 표시할 수 없다.
- `Stale`: 목록과 동일하게 MVP server stale fallback은 없으며, 새로고침 실패를 최신 상태로 위장하지 않는다.

## 사용자 인터랙션

### 목록과 상세

- Upcoming/Live와 Results 전환
- 목록 끝에서 다음 페이지 추가 로딩
- 경기 선택으로 Match Detail 이동
- Event 선택으로 Event Detail 이동
- Team 선택으로 Team Detail 이동
- 실패한 최초/추가 페이지 재시도

## 앱과 서버 책임 경계

조회 기능은 [공통 데이터 경계](../README.md#데이터와-서버-경계)를 따른다. Match는 로컬 favorite를 저장하지 않는다. raw failure는 UI에 노출하지 않는다.

## 서버 조회 API 계약 (Matches slice)

이 계약은 Matches 목록과 Match Detail Basic 구현에서 확정한 v1 public response다. 모든 조회는 request-time에 upstream을 새로 읽고, cache·stale fallback·raw HTML·Jsoup type·CSS selector·upstream URL을 response에 포함하지 않는다.

### Endpoint와 입력 검증

```text
GET /api/v1/matches/upcoming?page={page}
GET /api/v1/matches/results?page={page}
GET /api/v1/matches/{matchId}
```

- `page`는 생략하면 `1`이며, 단 하나의 10진 정수 `1..1000`만 허용한다. 알려지지 않은 query parameter, 중복 `page`, 선행 0, 범위 밖 값은 `400 INVALID_REQUEST`다.
- `matchId`는 선행 0이 없는 1~10자리 10진수만 허용한다. slug, upstream path, URL은 client 입력이나 API identity로 사용하지 않는다.
- 성공 목록 response는 `{ category, page, groups }`이고, group은 `{ dateLabel, date?, matches }`다. `category`는 `upcoming` 또는 `results`다.
- summary는 `{ id, status, timeLabel, time?, relativeTimeLabel?, homeTeam, awayTeam, homeScore?, awayScore?, event }`를 사용한다. team은 `{ name, id?, imageUrl? }`, event는 `{ name, series?, id? }`다. ID가 있으면 Team/Event Detail navigation에 사용하며, 없으면 해당 이동 action을 노출하지 않는다.
- detail은 summary의 핵심 필드와 `scheduledAt?`, `scheduledAtUtc?`, `description?`, `seriesFormat?`, `maps`, `headToHead`, `pastMatches`를 flat하게 제공한다. map은 `{ name, homeScore?, awayScore? }`이며 관련 경기는 `{ id, homeTeamName, awayTeamName, homeScore?, awayScore? }` 형태를 사용한다.
- `status`는 `upcoming`, `live`, `completed`, `postponed`, `cancelled`, `unavailable` 중 하나다. 상태상 없는 score/map은 오류가 아니라 optional field 또는 빈 list로 표현한다.

### 현재 source 한계와 확장 지점

- VLR.GG 목록 markup은 team/event의 안정적인 식별자와 절대 시각을 제공하지 않는다. 따라서 목록에서는 이름, source 표시 문자열(`dateLabel`, `timeLabel`, `relativeTimeLabel`)과 그 문자열을 구조화한 `date`·`time`만 제공하고 `team.imageUrl`은 null이다. 목록의 `.match-item-icon img`는 event icon이므로 team image로 사용하지 않는다. Detail markup의 검증된 team header link 안 `img[src]`는 `//`·`/`·`https` source만 HTTPS public URL로 정규화해 KMP가 표시한다. source에 없는 ID·image URL·추정 timestamp를 만들지 않으며, HTTP·blank·data·javascript·bare-relative image source는 null이다.
- Detail의 `scheduledAt`·`scheduledAtUtc`는 upstream `data-utc-ts`를 안전하게 ISO-8601 UTC로 바꿀 수 있을 때만 포함한다. `data-utc-ts`는 이름과 달리 미국 동부 현지 시각이므로 `America/New_York`로 해석한다([날짜·시간 계약](#날짜시간-계약)). `timeLabel`은 source에서 읽은 사람이 읽을 수 있는 날짜/시간 label이며 UI가 locale/timezone 표시를 결정한다.
- Detail의 `pastMatches`는 각 team history block 안에서 canonical numeric match link를 가진 `.match-histories-item`만 source 순서대로 전달한다. link가 없거나 상대 팀명이 빠진 item은 match identity를 합성하지 않고 제외하며, detail header의 team 순서와 item의 상대 팀/score만 사용한다.
- `headToHead`는 `.match-h2h-matches`의 row가 canonical numeric match link를 직접 제공할 때만 source 순서대로 전달한다. canonical row reference가 없는 H2H row는 안정 식별자가 없다는 좁은 source limit 때문에 제외하며, event·team·score로 ID를 만들지 않는다; 유효한 row가 없으면 list는 빈 배열이다.

실패는 서버 공통 error envelope를 그대로 사용한다. transport failure는 `502 UPSTREAM_NETWORK_FAILURE`, 필수 DOM structure failure는 `502 SOURCE_PARSING_FAILURE`이며, 안전한 message 외의 내부 원인은 public response에 포함하지 않는다.

### 날짜·시간 계약

2026-10-26 날짜·시간 표시 수정(서버 1/2)에서 정한 계약이다. 앱이 사용자 시간대와 언어에 맞춰 날짜·시간을 포맷할 수 있도록, 기존 표시 문자열은 그대로 두고 기계 판독 가능한 값을 nullable field로 추가했다. Matches 외 화면의 값도 이 절에서 함께 정의한다.

#### Upstream 조사 (2026-10-26)

서울에서 vlr.gg를 직접 요청해 실제 HTML을 비교했다. 서버의 production 리전(`asia-northeast3`)과 로컬 개발 환경 모두 서울이다.

| 화면 (endpoint) | vlr.gg가 주는 기계 판독 값 | 표시 문자열과 시간대 | 서버 결정 |
|---|---|---|---|
| Matches 목록 날짜 그룹 (`/matches/{upcoming\|results}`) | 없음 | `.wf-label` `Tue, October 27, 2026` + 선택적 `Today`/`Yesterday` 태그. 요청자 위치 기준 시간대(서울=KST) | `dateLabel` 유지, `date` 추가 (local date) |
| Matches 목록 행 | 없음. `data-utc-ts`도, 시간대 표기도 없다 | `.match-item-time` `3:00 AM`, 같은 시간대 | `timeLabel` 유지, `time` 추가 (local time). 행 instant는 근거가 없어 추가하지 않음 |
| Matches 목록 행 ETA | 없음 | `.ml-eta` `22m`, `10h 32m`, `1d 1h`. 예정 경기는 남은 시간, 종료 경기(`ml-eta mod-completed`)는 **시작 후 지난 시간**이다. 실측: 05:45 KST 경기가 16:27 KST에 `10h 43m`. Live 행 ETA는 이번 조사에서 관측하지 못했다 | `relativeTimeLabel` 원문 유지. 구조화 값 없음 (아래 참고) |
| Match Detail (`/matches/{id}`) | `.match-header-date [data-utc-ts]` `2026-10-26 14:00:00`. **이름과 달리 UTC가 아니라 미국 동부 현지 시각(EDT/EST)** | `.moment-tz-convert` `Tuesday, October 27` + `3:00 AM KST`. 요청자 위치 기준 시간대 | `scheduledAt` 값 버그 수정 (`America/New_York`로 해석), 같은 값을 `scheduledAtUtc`로 추가 |
| Event Matches (`/events/{id}/matches`) | 없음 | `.match-item-time` `5:00 PM`, Matches 목록과 같은 시간대. 날짜 그룹은 응답에 없음 | `MatchSummaryResponse` 재사용이라 `time` 추가 |
| Event Detail bracket | `.bracket-item-status[data-utc-ts]`에 unix seconds (`1778140800`) | `5:00 pm KST, May 7` | 서버가 bracket을 파싱하지 않으므로 변경 없음 |
| News 기사 (`/news/{articleId}/{slug}`) | header `time.js-date-toggle[datetime]` `2026-10-26T00:09:14+09:00` (offset 포함 ISO-8601) | `time` 텍스트 `16 hours ago`, title `Oct 26, 2026 at 00:09 KST` | `publishedAt` 원문 유지, `publishedAtUtc` 추가 |
| News 목록 (`/news`) | 없음 | `October 26, 2026` 날짜만 | 변경 없음 |
| Event News, Team news | 없음 | 날짜 텍스트만 | 변경 없음 |
| Team Detail 경기 행 (`scheduledAtText`) | 없음 | `.m-item-date` `2026/08/28` + `7:05 pm`, 요청자 위치 기준 시간대 | 변경 없음 |
| Events 목록·Event Detail·Series의 `dateLabel` | 없음 | `May 1—20`, `May 1 – May 20, 2026` 같은 자유 형식 기간 | 변경 없음 |

**4시간 차이의 원인**: vlr.gg의 `data-utc-ts`는 이름과 달리 미국 동부 현지 시각이다. 실측 두 건으로 확인했다.

- 서머타임: `2026-10-26 14:00:00`인 경기를 vlr.gg가 `3:00 AM KST`(Oct 27)로 표시한다. 실제 시각은 `2026-10-26T18:00:00Z`이고, 차이는 EDT(UTC−4)다.
- 표준시: `2025-01-09 04:20:00`인 경기를 `6:20 PM KST`로 표시한다. 실제 시각은 `2025-01-09T09:20:00Z`이고, 차이는 EST(UTC−5)다.

기존 parser는 이 값을 UTC로 읽어서, `scheduledAt`이 서머타임 기간에는 4시간, 표준시 기간에는 5시간 일렀다. 그래서 같은 화면의 `timeLabel`과 맞지 않았다.

**목록 표시 시간대**: 목록·Team 화면의 표시 문자열은 vlr.gg가 요청 IP 위치로 고른 시간대로 서버에서 렌더된다. 목록 HTML에는 그 시간대 표기가 없다. Detail의 `KST` 표기로 서울 요청이 KST로 렌더됨을 확인했다. 서버를 다른 리전에서 실행하면 같은 field의 시간대가 바뀔 수 있다.

#### 새 field

| Endpoint | Field | 형식 | 의미 | null 조건 |
|---|---|---|---|---|
| `GET /api/v1/matches/{upcoming\|results}` group | `date` | ISO-8601 local date `2026-10-27` | `dateLabel`의 달력 날짜. vlr.gg 표시 시간대(요청 위치 기준, 서울 리전·로컬은 KST) 기준이며 **instant가 아니다** | label에 `EEE, MMMM d, yyyy` 영어 날짜가 없거나, 요일과 날짜가 맞지 않음 |
| `GET /api/v1/matches/{upcoming\|results}` summary, `GET /api/v1/events/{id}/matches` item | `time` | ISO-8601 local time `HH:mm` (`03:00`, `19:05`) | `timeLabel`의 시각. `date`와 같은 표시 시간대 기준이며 **instant가 아니다** | `timeLabel`이 `h:mm AM/PM` 형식이 아님 (`TBD` 등) |
| `GET /api/v1/matches/{id}` | `scheduledAt` (기존) | ISO-8601 UTC instant `2026-10-26T18:00:00Z` | 경기 예정 시작 시각. 이번에 값만 고쳤고 형식·의미는 그대로다. `0.1.0(2)`가 원문으로 표시한다 | `data-utc-ts` 속성이 없거나 `yyyy-MM-dd HH:mm:ss` 형식이 아님, 달력에 없는 날짜·시각(`2026-02-30` 등), 또는 봄 DST gap에 있는 현지 시각 |
| `GET /api/v1/matches/{id}` | `scheduledAtUtc` | ISO-8601 UTC instant `2026-10-26T18:00:00Z` | 고친 `scheduledAt`과 항상 같은 값. 앱이 시간대 변환에 쓰는 field다 | `scheduledAt`과 같은 조건 |
| `GET /api/v1/news/{articleId}/{slug}` | `publishedAtUtc` | ISO-8601 UTC instant `2026-10-25T15:09:14Z` | 기사 게시 시각 | header `time[datetime]`이 없거나 offset 포함 ISO-8601이 아님 |

- 모든 instant는 `Z`로 끝나는 UTC ISO-8601이며 초까지 표기한다(`Instant.toString()`). 서버 JSON은 null field도 명시적으로 내보낸다.
- 시각 파싱 실패는 해당 field만 null로 둔다. 필수 field가 아니므로 응답 전체를 실패시키지 않는다.
- `America/New_York`의 서머타임 종료일 01:00~02:00처럼 두 번 나타나는 현지 시각(overlap)은 앞쪽 offset(EDT)으로 해석한다. `2026-11-01 01:30:00` → `2026-11-01T05:30:00Z`.
- 달력에 없는 날짜·시각(`2026-02-30 04:00:00` 등)은 가까운 날짜로 보정하지 않고 null로 둔다. 파서는 strict 해석을 쓰며, null로 바꾸는 예외는 날짜·시각 형식 오류(`DateTimeException`)로 한정한다.
- 서머타임 시작일 02:00~03:00처럼 존재하지 않는 현지 시각(gap)은 다음 유효 시각으로 보정하지 않고 null로 둔다. `2026-03-08 02:30:00` → null. `scheduledAt`과 `scheduledAtUtc` 모두 같은 규칙을 따른다.

**앱은 `scheduledAt`이 아니라 `scheduledAtUtc`를 써야 한다.** `scheduledAt`은 새 field가 아니라 구 production 서버도 내려주는 field이고, 구서버 값은 형식상 정상 ISO-8601이면서 4~5시간 틀리다. 앱은 값만 보고 맞는 값인지 구분할 수 없다. 구서버는 `scheduledAtUtc`를 보내지 않으므로, 이 field가 있다는 사실이 "고친 값"임을 알려 주는 표지가 된다. 앱은 `scheduledAtUtc`만 신뢰하고, 없거나 null이면 `timeLabel`로 돌아간다. `scheduledAt`은 `0.1.0(2)`의 원문 표시를 위해 계속 내려준다.

#### 기존 label field와의 관계

- `dateLabel`, `timeLabel`, `relativeTimeLabel`, `publishedAt`, `scheduledAtText`, `publishedDateText`는 삭제하지 않고 의미도 바꾸지 않는다. 배포된 앱 `0.1.0(2)`가 이 field를 사용한다.
- `date`·`time`은 같은 표시 시간대의 label을 구조화한 값이라, 앱이 언어에 맞게 포맷할 수 있다(`10월 27일 (화)`, `오전 3:00`). 시간대 변환에는 쓸 수 없다. 사용자 시간대로 바꾸려면 instant가 필요한데, 목록에는 instant가 없다.
- `Today`/`Yesterday` 태그는 `dateLabel`에만 남고 `date`에는 없다. 앱이 오늘·어제 표시를 원하면 `date`를 기기 날짜와 비교해야 하는데, 표시 시간대(KST)와 기기 시간대가 다르면 결과가 vlr.gg 태그와 다를 수 있다.
- `relativeTimeLabel`은 원문 그대로다. 예정 경기는 남은 시간, 종료 경기는 시작 후 지난 시간이며 단위는 `m`, `h m`, `d h`로 정밀도가 다르다. 서버 조회 시각 기준이라 구조화하지 않았다. 앱은 `status`로 의미를 구분하거나(예: 종료 경기에 `전` 표기 또는 숨김) 표시하지 않을 수 있다.
- Detail의 `timeLabel`(`Tuesday, October 27 3:00 AM KST`)은 원문 그대로다. 앱은 `scheduledAtUtc`가 있으면 그것으로 사용자 시간대에 맞게 포맷하고, 없을 때만 `timeLabel`을 표시한다.

#### 앱 호환성

- 앱은 새 field가 없거나 null인 응답에서도 동작해야 한다. 현재 production 서버는 이 field를 내려주지 않는다. 새 field가 없으면 기존 label을 그대로 표시한다.
- 앱 JSON 설정은 `ignoreUnknownKeys = true`라서 `0.1.0(2)`는 추가 field를 무시한다.
- 새 앱 + 구서버 조합에서 구서버의 `scheduledAt`은 틀린 값이므로 앱이 쓰지 않는다. 구서버에는 `scheduledAtUtc`가 없어서 앱이 `timeLabel`로 돌아간다.
- 새 서버를 배포하면 `0.1.0(2)`가 원문으로 표시하는 Match Detail `scheduledAt`이 서머타임 기간 4시간 늦어진다. 이것은 실제 시작 시각으로 바로잡히는 변화다.

#### 앱 표시 규칙

2026-10-26 날짜·시간 표시 수정(앱 2/2)에서 정한 규칙이다. 표시 문자열은 UI layer의 `ui/format/KoreanDateTimeFormat`에서만 만든다. Domain Model에는 파싱한 값(`LocalDate`, `LocalTime`, `Instant`)만 두고, 파싱에 실패한 값은 null로 둔다.

| 화면 | 사용 값 | 표시 형식 | 값이 없을 때 |
|---|---|---|---|
| Matches 목록 날짜 헤더 | group `date` | `10월 27일 (화)` | `dateLabel` 원문 (`Tue, October 27, 2026`) |
| Matches 목록·Event Matches 예정 경기 시각 | summary `time` | `오전 3:00`, `오후 7:05` | `timeLabel` 원문 (`3:00 AM`, `TBD`) |
| Matches 목록·Event Matches 예정 경기 남은 시간 | `relativeTimeLabel` | `22m` → `22분 후`, `10h 32m` → `10시간 32분 후`, `1d 1h` → `1일 1시간 후` | 표시하지 않음 |
| Match Detail hero 시각 | `scheduledAtUtc` | 기기 시간대 기준 `2026년 10월 27일 (화) 오전 3:00` | `timeLabel` 원문 (`scheduledAtUtc`가 없거나 null이거나 파싱 실패) |
| Match Detail hero 보조 정보 | `relativeTimeLabel`, `seriesFormat` | 예정 경기만 `22분 후 · Bo3`, 그 외 `Bo3` | 값이 있는 항목만 표시 |
| News 기사 게시 시각 | `publishedAtUtc` | 기기 시간대 기준 `2026년 10월 26일 오전 12:09` | `publishedAt` 원문 (`16 hours ago`) |

- 시간대 변환은 instant(`scheduledAtUtc`, `publishedAtUtc`)에만 적용하고 기기 시스템 시간대를 쓴다. 목록의 `date`·`time`은 이미 vlr.gg 표시 시간대(KST) 값이라 변환하지 않는다.
- 요일(`월`~`일`)과 `오전`/`오후`는 플랫폼 locale을 쓰지 않고 직접 만든다. Android와 iOS의 결과가 같다. 12시간제이며 00시는 `오전 12:00`, 12시는 `오후 12:00`이다.
- 남은 시간은 `status = upcoming`인 경기에만 표시한다. 종료 경기 ETA는 시작 후 지난 시간이라 표시하지 않고 스코어를 보여준다. Live·연기·취소·정보 없음 경기는 상태 chip만 표시한다.
- 남은 시간 원문은 `d`→`h`→`m` 순서의 1~3개 토큰(`22m`, `3h`, `2d`, `10h 32m`, `1d 1h`, `1d 2h 3m`)만 인식한다. 대문자, 순서가 다른 단위, `ago`·`LIVE` 같은 문구, 이미 번역된 문자열처럼 형식을 알 수 없는 원문은 표시하지 않는다.
- Match Detail은 `scheduledAt`을 표시 로직에 쓰지 않는다. 구서버가 형식만 정상이고 값이 4~5시간 틀린 `scheduledAt`을 보내기 때문이다. ISO 원문(`scheduledAt`·`scheduledAtUtc`)은 어떤 경우에도 표시하지 않는다.
- instant를 기기 시간대로 바꾸려면 stdlib에 없는 시간대 정보가 필요해 `kotlinx-datetime`을 사용한다. iOS에는 Material3 `1.11.0-alpha07`이 이미 전이 의존성으로 가져오는 `kotlinx-datetime` 0.7.1에 버전을 맞춘 직접 선언이고, Android에는 `kotlinx-datetime-jvm` 0.7.1 artifact가 새로 추가된다. 버전 상승은 없다.

## Upstream 및 parser 메모

이 절은 제품 동작이 아니라 구현 시 검증할 외부 문서 가정이다.

### 목록 URL

```text
https://www.vlr.gg/matches
https://www.vlr.gg/matches/?page=2
https://www.vlr.gg/matches/results
https://www.vlr.gg/matches/results/?page=2
```

### Match Detail URL 형태와 fixture 예시

VLR.GG Match URL은 `/match/{id}/{slug}`가 아니라 `/{matchId}/{slug}` 형태다. 앱 내부 route는 `/matches/{matchId}`처럼 안정적인 ID만 사용한다.

```text
https://www.vlr.gg/675209/nongshim-redforce-vs-kiwoom-drx-esports-world-cup-2026-pacific-qualifier-stage-2-lr2
https://www.vlr.gg/98525/kings-man-vs-hayabusa-gaming-champions-tour-korea-stage-2-challengers-closed-qualifier-ro12-gr
```

fixture는 최소한 BO1, BO3 2:0, BO3 2:1, BO5 3:1, BO5 3:2, FFW/정보 제한 경기를 포함한다. parser는 상태상 존재하지 않는 값과 실제 구조 손상을 구분해야 하며 selector와 보정 규칙은 parser 내부에만 둔다.

## 검증 가능한 수용 기준

체크된 서버 항목은 `main`에서 실제로 증명된 현재 기능 범위다. MVP 완료 판정에는 목록과 상세·Match Detail Basic D1 및 관련 품질 검증을 사용한다.

### 목록과 상세

Matches 목록은 #38에서 구현 완료되었고, Match Detail Basic D1과 구분해 아래 현재 완료 상태를 기록한다.

- [x] Matches 탭에서 Upcoming/Live와 Results를 명확히 전환할 수 있다.
- [x] Upcoming/Live는 날짜, 경기 시각, 남은 시간, 양 팀, Event, 상태를 표시한다.
- [x] Results는 날짜, 완료 시각, 양 팀, 스코어, Event를 표시한다.
- [x] 목록 pagination이 중복 항목 없이 동작하고 추가 페이지 실패 시 기존 목록을 유지한다.
- [x] 경기 항목은 실제 Match Detail로, Match Detail의 Event reference는 Event Detail로 이동한다.
- [x] Match Detail은 Event, 경기 설명, 양 팀, 스코어, 상태, 맵, Head to Head를 사용 가능한 범위에서 표시하고 기존 서버 `pastMatches` field를 UI section으로 렌더링하지 않는다.
- [x] Match Detail의 양 Team hero는 로고 영역 위·이름 아래의 대칭 구조를 유지하고 score/result를 두 Team 사이 중앙에 배치하며 검증된 remote logo를 표시한다.
- [x] BO1/BO3/BO5와 FFW fixture에서 상태별 선택성이 parsing failure와 구분된다.
- [x] initial loading, empty, initial error, pagination error, unavailable 표현이 정상 populated 상태와 구분된다.
- [ ] stale 데이터 표시는 현재 범위에 없으며, 향후 도입 시 마지막 확인 시각과 갱신 실패를 명시한다.

### Match Detail Basic D1

- [x] 전체 요청은 `Loading`, `Content`, `Error`로 구분하고 Error에서 같은 Match ID 재시도와 Back을 제공한다.
- [x] Upcoming/Postponed, Live, Completed, Cancelled/Unavailable을 색상 외 상태 text와 함께 구분한다.
- [x] Content는 `Match hero/basic info → Maps → Head to Head` 순서를 유지하며 Maps/H2H의 한쪽 또는 양쪽이 비어도 기본 Match와 사용 가능한 section을 보존한다.
- [x] map/H2H의 nullable score는 `—`, 실제 numeric zero는 `0`으로 구분한다.
- [x] Team/Event ID가 있을 때만 전체 identity를 활성화하고 H2H 전체 surface는 관련 Match Detail로 이동한다.
- [x] Matches Upcoming/Results, Event, Team, Player의 기존 Match route가 같은 실제 destination으로 해석되고 Back·root 전환 뒤 loaded/scroll overlay state를 보존한다.
- [x] bottom navigation, notification bell·mutation·Snackbar, Match favorite, `pastMatches`를 Match Detail D1에 노출하지 않는다.
- [x] Compose UI 테스트에서 48dp interactive target, 접근 가능한 label, 긴 한국어 Team/Event/description의 안전한 배치를 검증한다.
- [ ] Android/iOS 실제 기기 screenshot 비교와 실기기 접근성 검증은 별도 수행이 필요하다.
