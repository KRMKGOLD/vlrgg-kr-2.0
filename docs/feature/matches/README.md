# Matches

## 문서 역할

이 문서는 Upcoming/Live, Results, Match Detail, Match 즐겨찾기와 시작·종료 알림의 제품 요구사항을 정의한다. 공통 시각 언어와 상태 표현은 루트 [`DESIGN.md`](../../../DESIGN.md), 전체 내비게이션과 즐겨찾기 관계는 상위 [`docs/feature/README.md`](../README.md)를 따른다.

## 구현 상태 (2026-07-29)

- **Backend 콘텐츠 조회: 구현 완료.** `GET /api/v1/matches/upcoming`, `GET /api/v1/matches/results`, `GET /api/v1/matches/{matchId}`와 해당 parser/route 테스트가 구현되어 있다.
- **Backend Match 알림/구독: Wave B 서버 전용 기반 구현.** default-disabled loopback 구독 API, 영속 desired state, 10분 fixed-delay observation과 중복 없는 START/END intent 생성까지 구현했다. Firebase delivery, retry/claim, App 연동과 public authority는 아직 없으며, 전체 Stage 1 계약은 [server-fcm-stage1.md](../../architecture/server-fcm-stage1.md)와 [ADR-0001](../../architecture/adr/0001-match-notification-stage1-storage-and-provider-boundary.md)을 따른다.
- **App: 미구현.** 목록·상세 UI, 내비게이션, 로컬 Match 즐겨찾기, 권한 및 전역 알림 흐름은 아직 구현되어 있지 않다.

## 목적과 사용자 가치

- 예정, 진행 중, 완료 경기를 시간과 상태 중심으로 빠르게 확인하게 한다.
- Match에서 관련 Event와 Team으로 이어지는 탐색 경로를 제공한다.
- 사용자가 선택한 Match를 즐겨찾기에 보관하고, 앱을 계속 열어두지 않아도 시작과 종료를 각각 알 수 있게 한다.

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
- Past Matches
- Match 알림/즐겨찾기 설정 및 해제

### Match 즐겨찾기와 알림

- Match 알림 설정과 Match 즐겨찾기 생성을 하나의 사용자 동작으로 처리
- 로컬 Match 즐겨찾기 저장과 MyPage 노출
- `FCM registration value`가 나타내는 익명 push target을 이용한 서버 알림 구독
- 서버가 활성 구독의 고유 Match ID를 고정 10분 주기로 상태 확인
- Match 시작 알림 1회와 종료 알림 1회
- 최초 앱 실행의 알림 권한 요청, MyPage 전역 알림 ON/OFF, 비활성 상태의 안내 dialog와 system settings fallback

## 제외 범위

- 맵별 상세 스탯과 선수별 Agent, Rating, K/D/A, KAST, ADR, HS, FK, FD
- Team 또는 Player 즐겨찾기에 따른 알림
- 알림함과 알림 이력 화면
- 계정, 사용자 DB, 기기 간 즐겨찾기 동기화
- 공개 서비스 규모를 위한 동적 polling 간격이나 분산 scheduler 최적화
- 경기 시간 변경, 연기, 취소 자체에 대한 별도 사용자 알림
- VLR.GG 원본 BO 구조를 그대로 노출하는 화면

BO1/BO3/BO5와 몰수승·패처럼 정보가 제한된 경기는 MVP parser가 구분해서 안전하게 표현해야 하지만, 고급 스탯을 보완해 만들지는 않는다.

## 진입과 이탈 내비게이션

### 진입

- Bottom navigation의 `Matches` 탭에서 진입한다.
- Matches는 최상위 탭이므로 공통 Top App Bar와 Search 액션을 제공한다.
- Event Detail, Team Detail, MyPage의 Match 즐겨찾기에서도 Match Detail로 진입할 수 있다.

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
   - 경기 시각 또는 완료 시각
   - 양 팀 이름과 식별 이미지
   - 예정 경기의 남은 시간 또는 완료 경기의 스코어
   - Event 이름
5. 다음 페이지 로딩 또는 페이지 로딩 실패 표시

Live 상태는 색상만으로 전달하지 않고 텍스트 label을 함께 사용한다. 시간, 상태, 팀, Event 순으로 scan할 수 있어야 한다.

### Match Detail

1. 뒤로가기, Match 즐겨찾기/알림 action
2. 경기 상태와 예정/시작 시각
3. Event 이름과 경기 설명
4. 양 팀과 스코어
5. 맵 목록
6. Head to Head
7. Past Matches

정보가 존재하지 않는 FFW 등의 terminal Match는 비어 있는 정상 스탯 화면처럼 보이지 않아야 한다. 확인 가능한 팀, 결과, 상태를 우선 표시하고 사용할 수 없는 section은 명시적으로 생략하거나 unavailable로 표현한다.

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
- Past Matches 목록

Match Detail Basic에 필수인 팀과 상태를 해석하지 못하면 parsing failure로 처리한다. 경기 전이라 스코어가 없거나 FFW로 맵 정보가 없는 경우는 정상적인 상태별 선택성이다.

### 즐겨찾기/구독 상태

- 로컬 Match 즐겨찾기 여부
- 앱 전역 알림 ON/OFF
- platform system permission 상태
- 서버 구독 생성/해제 작업 상태

FCM registration value, scheduler 내부 delivery marker와 다른 서버 전용 상태는 UI 모델로 노출하지 않는다.

## 화면 상태

### Matches List

- `Loading`: 최초 목록 skeleton을 표시한다.
- `Populated`: 선택한 목록의 경기가 날짜별로 표시된다.
- `Empty`: 정상 응답이지만 선택한 Upcoming/Live 또는 Results에 경기가 없다.
- `Partial`: 기존 페이지는 유지되지만 다음 페이지 또는 일부 선택적 Team 이미지 로딩이 실패했다.
- `Error`: 최초 목록 요청 실패로 표시할 경기가 없다. 일반 오류 문구와 재시도를 제공한다.
- `Stale`: 서버는 이전 scraping 결과를 fallback으로 반환하지 않는다. 향후 앱이 기존 목록을 유지한다면 마지막 확인 시각과 갱신 실패를 명시해야 한다.

### Match Detail

- `Loading`: 상태/팀/스코어/section skeleton을 안정적으로 표시한다.
- `Populated`: Match 상태에 맞는 필수 정보와 사용 가능한 section을 표시한다.
- `Partial`: 기본 Match 정보는 유효하지만 map, Head to Head, Past Matches 중 선택적 section 일부를 사용할 수 없다.
- `Empty/Unavailable`: Match는 식별되지만 표시 가능한 필수 정보가 없거나 upstream에서 더 이상 정상 제공되지 않는다.
- `Error`: network 또는 필수 구조 parsing 실패로 상세를 표시할 수 없다.
- `Stale`: 목록과 동일하게 MVP server stale fallback은 없으며, 새로고침 실패를 최신 상태로 위장하지 않는다.

### 알림 설정

- `Ready`: 전역 알림과 system permission이 활성화되어 설정 가능
- `ActivationRequired`: 앱 전역 알림 또는 system permission이 비활성화
- `Subscribing`: 로컬 favorite와 서버 subscription을 일관되게 만들기 위한 처리 중
- `Subscribed`: local favorite와 활성 server subscription이 일치
- `Unsubscribing`: 해제 처리 중
- `SubscriptionError`: 생성 또는 해제가 완료되지 않았으며 사용자에게 재시도 가능한 상태를 제공

Match 알림 설정과 즐겨찾기 생성은 하나의 원자적 사용자 동작이다. 서버 subscription 생성에 실패하면 새 local favorite를 되돌리고 전체 설정을 실패 처리한다. 해제 시 서버 unsubscribe가 실패하면 local favorite 제거를 확정하지 않고 기존 favorite/subscribed 상태를 유지한다. 두 경우 모두 실패 범위와 재시도 동작을 제공한다.

## 사용자 인터랙션

### 목록과 상세

- Upcoming/Live와 Results 전환
- 목록 끝에서 다음 페이지 추가 로딩
- 경기 선택으로 Match Detail 이동
- Event 선택으로 Event Detail 이동
- Team 선택으로 Team Detail 이동
- 실패한 최초/추가 페이지 재시도

### Match 알림 설정

1. 사용자가 Match Detail에서 알림 action을 선택한다.
2. 앱 전역 알림과 system permission이 모두 활성화된 경우 local favorite 저장과 server subscription 생성을 진행한다.
3. 하나라도 비활성화된 경우 알림 활성화가 필요하다는 dialog를 표시한다.
4. dialog에서 활성화를 선택하면 system permission을 확인하고, 요청 가능한 상태면 permission을 요청한다.
5. permission이 허용된 뒤에만 앱 전역 알림을 ON으로 변경하고 Match 설정을 계속한다.
6. 앱 안에서 다시 요청할 수 없는 상태면 이유와 함께 system settings 이동 action을 제공한다.
7. 사용자가 취소하면 Match favorite/subscription을 생성하지 않고 상세 화면에 남는다.

하나의 논리적 Match subscription은 앱이 제시한 한 `FCM registration value`의 push target과 한 Match의 쌍이다. 같은 쌍의 설정을 반복하면 중복 subscription을 만들지 않고 alarm ON으로 수렴한다. 응답을 받지 못해 성공 여부가 불확실해도 같은 설정 요청을 안전하게 재시도할 수 있다.

server subscription 생성이 확정적으로 실패하면 local favorite를 남기지 않고 전체 설정 실패를 표시한다. 성공한 것으로 보이는 중간 상태를 유지하지 않으며 사용자는 같은 동작을 재시도할 수 있다.

앱 최초 실행에서도 platform이 요청을 허용하는 상태라면 알림 권한을 요청한다. 권한 거부 자체가 News/Matches 등 비알림 기능 사용을 막아서는 안 된다.

### Match 알림 해제

- Match favorite 해제는 local favorite 제거와 현재 앱이 제시할 수 있는 registration value에 대응하는 해당 Match subscription 취소를 함께 요청한다.
- MyPage와 Match Detail 어디에서 해제해도 같은 결과가 되어야 한다.
- Team/Player favorite에는 이 흐름을 적용하지 않는다.
- server unsubscribe가 실패하면 local favorite 제거도 확정하지 않고 기존 favorite/subscribed 상태와 재시도 동작을 유지한다.
- 같은 target/Match의 해제를 반복하면 alarm OFF로 수렴한다. 한 registration value의 해제 요청은 다른 value의 subscription을 변경하지 않는다.
- 같은 target/Match의 설정과 해제가 교차하면 최종 상태는 앱이 발행한 최신 사용자 의도에 수렴해야 한다. 늦게 도착한 이전 요청이나 그 재시도가 이후 의도를 되돌려서는 안 된다. 이를 증명할 version/generation 또는 request ordering과 endpoint field는 Match 알림 구현 계획에서 정한다.

### 전역 알림 OFF

- MyPage의 전역 OFF는 기존 Match favorite를 Team/Player favorite로 변환하거나 삭제하지 않는다.
- 전역 OFF는 현재 앱이 제시할 수 있는 registration value의 push target에 연결된 Match 알림만 비활성화한다.
- 현재 target에 연결된 여러 Match subscription 중 일부만 OFF로 확인되거나 응답이 불확실하면 앱은 전역 OFF를 완료 상태로 표시하지 않는다. 이미 OFF로 확인된 subscription은 그대로 유지하고 미확정 subscription만 pending으로 표시해 재시도·재동기화하며, 이 과정에서도 모든 favorite는 보존한다.
- 전역 OFF가 pending인 동안 사용자가 개별 Match 알림을 다시 ON으로 선택하면 그 선택이 최신 전역·Match 의도가 된다. 앱은 남은 전역 OFF 재시도를 중단하고 전역 ON 활성화 흐름 뒤 해당 Match를 설정하며, 지연된 이전 bulk OFF 요청이나 응답이 이 ON을 되돌려서는 안 된다.
- 앱과 서버는 현재 값만으로 알 수 없는 이전 registration value를 같은 물리 기기의 target으로 추론하거나 해제하지 않는다. 이전 target의 구독은 독립적으로 완료·명시적 해제·provider invalid 처리될 때까지 유효할 수 있고, 이전 target과 현재 target의 일시적 중복 전달은 MVP에서 허용한다.
- OFF 상태에서 새 Match 알림을 요청하면 activation-required dialog를 표시한다.

여러 subscription을 비활성화하는 endpoint 형태와 서버 transaction 경계는 Match 알림 구현 계획에서 정하되, 부분 성공을 전체 성공으로 보고해서는 안 된다.

## 10분 Match 추적 및 알림 contract

### 구독

- `FCM registration value`는 계정 없는 한 익명 push target의 전송 주소이며 사용자 인증이나 물리 기기 소유 증명이 아니다.
- 서로 다른 registration value는 같은 물리 기기에서 생성되었더라도 독립 target이다. 서버는 FID나 다른 기기 식별자로 값을 병합·대체·이관하지 않는다.
- 이전 registration value로 이미 만든 subscription은 해당 Match 알림 의무가 끝나거나 그 값을 사용해 명시적으로 해제하거나 provider/runtime 처리로 전송 불가능해질 때까지 독립적으로 유효하다.
- 서버는 `(FCM registration value, Match)` 쌍을 하나의 논리적 subscription으로 다룬다. 이는 제품 invariant이며 특정 table key나 persistence schema를 미리 정하지 않는다.
- 같은 Match를 여러 target이 구독해도 upstream 상태 확인은 고유 Match ID 기준으로 중복 제거한다.

registration value의 SDK 획득·서버 동기화, provider가 증명한 invalid-target 삭제, Firebase credential 경계는 [Stage 1 서버 계약](../../architecture/server-fcm-stage1.md)이 소유한다.

### 추적

- 서버 scheduler는 활성 구독이 있는 고유 Match ID의 상태를 고정 10분마다 확인한다.
- Match 시작/종료 감지는 VLR.GG에 표시된 상태를 기준으로 하므로 사용자에게 보이는 알림은 실제 시점보다 scheduler 주기와 upstream 반영만큼 늦을 수 있다.
- upcoming, live, completed 외에도 time-changed, postponed, cancelled, unavailable/missing 상태를 내부 contract에서 구분한다.
- 일시적인 network/parsing failure나 upstream missing을 completed로 간주하지 않는다.
- terminal 상태이며 필요한 알림 처리까지 끝난 Match는 polling 대상에서 제거한다.

### 전달

- 각 subscription에는 Match 시작 알림을 사용자에게 1회만 보내려는 delivery marker가 필요하다.
- 각 subscription에는 Match 종료 알림을 사용자에게 1회만 보내려는 delivery marker가 필요하다.
- scheduler 재시도, 서버 재시작, 동일 상태 반복 관찰이 중복 사용자 알림을 만들지 않도록 idempotent하게 처리한다.
- 여기서 `1회`는 서버가 관리하는 사용자-visible 알림의 exactly-once intent다. 외부 push transport 자체의 절대적 exactly-once 전달 보장을 뜻하지 않는다.
- 취소, 연기, 시간 변경, upstream missing은 상태로 기록하지만 MVP의 사용자 알림은 시작과 종료 두 종류만 제공한다.

이 영속 구독, scheduler, delivery marker는 일반 scraping 기능의 request-time/no-database 기준에 대한 Match 알림 전용 예외다.

## 앱과 서버 책임 경계

### 서버

- 목록/상세 요청에서 `Scraper → Parser → SourceModel → Mapper → Response` 경계를 유지한다.
- Upcoming/Live, Results, Match Detail HTML을 app-facing response로 가공한다.
- DOM selector, raw HTML, Jsoup type을 public response에 노출하지 않는다.
- 알림 기능에 한해 registration-value target별 subscription persistence, 10분 scheduler, 상태 비교, idempotent start/end delivery를 소유한다.
- network/parsing failure를 안전한 공통 error envelope로 반환하고 실패를 terminal Match 상태로 오인하지 않는다.

### 앱

- remote DTO를 app Domain Model로 매핑하고 목록/상세 UiState를 관리한다.
- local Match favorite와 전역 알림 설정을 저장한다.
- platform permission 확인/요청과 system settings 이동 bridge를 제공한다.
- 서버 구독 생성/해제 결과를 반영해 로컬과 서버 상태가 일치하도록 조정한다.
- push credential이나 raw server failure를 UI에 노출하지 않는다.

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
- 성공 목록 response는 `{ category, page, groups }`이고, group은 `{ dateLabel, matches }`다. `category`는 `upcoming` 또는 `results`다.
- summary는 `{ id, status, timeLabel, relativeTimeLabel?, homeTeam, awayTeam, homeScore?, awayScore?, event }`를 사용한다. team은 `{ name, id? }`, event는 `{ name, series?, id? }`다. ID가 있으면 Team/Event Detail navigation에 사용하며, 없으면 해당 이동 action을 노출하지 않는다.
- detail은 summary의 핵심 필드와 `scheduledAt?`, `description?`, `seriesFormat?`, `maps`, `headToHead`, `pastMatches`를 flat하게 제공한다. map은 `{ name, homeScore?, awayScore? }`이며 관련 경기는 `{ id, homeTeamName, awayTeamName, homeScore?, awayScore? }` 형태를 사용한다.
- `status`는 `upcoming`, `live`, `completed`, `postponed`, `cancelled`, `unavailable` 중 하나다. 상태상 없는 score/map은 오류가 아니라 optional field 또는 빈 list로 표현한다.

### 현재 source 한계와 확장 지점

- VLR.GG 목록 markup은 team/event의 안정적인 식별자와 절대 시각을 제공하지 않는다. 따라서 목록에서는 이름과 source 표시 문자열(`dateLabel`, `timeLabel`, `relativeTimeLabel`)만 제공한다. Detail markup의 안정적인 Team/Event ID는 `id`로 전달하지만, source에 없는 ID·image URL·추정 timestamp를 만들거나 upstream asset URL을 노출하지 않는다.
- Detail의 `scheduledAt`은 upstream `data-utc-ts`를 안전하게 ISO-8601 UTC로 바꿀 수 있을 때만 포함한다. `timeLabel`은 source에서 읽은 사람이 읽을 수 있는 날짜/시간 label이며 UI가 locale/timezone 표시를 결정한다.
- Detail의 `pastMatches`는 각 team history block 안에서 canonical numeric match link를 가진 `.match-histories-item`만 source 순서대로 전달한다. link가 없거나 상대 팀명이 빠진 item은 match identity를 합성하지 않고 제외하며, detail header의 team 순서와 item의 상대 팀/score만 사용한다.
- `headToHead`는 `.match-h2h-matches`의 row가 canonical numeric match link를 직접 제공할 때만 source 순서대로 전달한다. canonical row reference가 없는 H2H row는 안정 식별자가 없다는 좁은 source limit 때문에 제외하며, event·team·score로 ID를 만들지 않는다; 유효한 row가 없으면 list는 빈 배열이다.
- 10분 상태 추적, anonymous subscription persistence, delivery marker, scheduler, push provider와 구독 endpoint는 이 조회 slice에 포함하지 않는다. 미래 구현은 기존 문서의 `upcoming/live/completed/postponed/cancelled/unavailable` 내부 상태와 subscription별 start/end 1회 delivery intent를 계약 경계로 사용하되, 조회 response freshness 정책을 바꾸지 않는다.

실패는 서버 공통 error envelope를 그대로 사용한다. transport failure는 `502 UPSTREAM_NETWORK_FAILURE`, 필수 DOM structure failure는 `502 SOURCE_PARSING_FAILURE`이며, 안전한 message 외의 내부 원인은 public response에 포함하지 않는다.

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
https://www.vlr.gg/581310/diutu-vs-101-outlaws-ccce-city-cup-2025-ro23
https://www.vlr.gg/675209/nongshim-redforce-vs-kiwoom-drx-esports-world-cup-2026-pacific-qualifier-stage-2-lr2
https://www.vlr.gg/666497/paper-rex-vs-kiwoom-drx-vct-2026-pacific-stage-1-lr2
https://www.vlr.gg/590032/leviat-n-vs-drx-china-esports-festival-super-champions-cup-gf
https://www.vlr.gg/13247/vision-strikers-vs-nuturn-champions-tour-korea-stage-1-masters-gf
https://www.vlr.gg/98525/kings-man-vs-hayabusa-gaming-champions-tour-korea-stage-2-challengers-closed-qualifier-ro12-gr
```

fixture는 최소한 BO1, BO3 2:0, BO3 2:1, BO5 3:1, BO5 3:2, FFW/정보 제한 경기를 포함한다. parser는 상태상 존재하지 않는 값과 실제 구조 손상을 구분해야 하며 selector와 보정 규칙은 parser 내부에만 둔다. 알림 scheduler도 Match ID로 canonical 대상 문서를 찾되 slug를 subscription identity로 사용하지 않는다.

## 검증 가능한 수용 기준

### 목록과 상세

- [ ] Matches 탭에서 Upcoming/Live와 Results를 명확히 전환할 수 있다.
- [ ] Upcoming/Live는 날짜, 경기 시각, 남은 시간, 양 팀, Event, 상태를 표시한다.
- [ ] Results는 날짜, 완료 시각, 양 팀, 스코어, Event를 표시한다.
- [ ] 목록 pagination이 중복 항목 없이 동작하고 추가 페이지 실패 시 기존 목록을 유지한다.
- [ ] 경기 항목은 Match Detail로, Event reference는 Event Detail로 이동한다.
- [ ] Match Detail은 Event, 경기 설명, 양 팀, 스코어, 상태, 맵, Head to Head, Past Matches를 사용 가능한 범위에서 표시한다.
- [x] BO1/BO3/BO5와 FFW fixture에서 상태별 선택성이 parsing failure와 구분된다.
- [ ] loading, empty, partial, error, stale/unavailable 표현이 정상 populated 상태와 구분된다.

### 즐겨찾기, 권한, 전역 설정

- [ ] 활성 권한/전역 ON 상태에서 Match 알림을 설정하면 local Match favorite와 server subscription이 모두 생성된다.
- [ ] Match favorite는 MyPage에 표시되고 MyPage와 Match Detail 모두에서 같은 상세 화면으로 이동한다.
- [ ] 같은 registration value와 Match의 알림 설정을 반복하면 중복 없이 alarm ON으로 수렴하고, 응답이 불확실한 요청을 안전하게 재시도할 수 있다.
- [ ] Match favorite 해제는 현재 registration value의 대응 subscription도 취소하며 반복 해제는 alarm OFF로 수렴한다.
- [ ] 같은 target/Match의 설정과 해제가 교차하거나 이전 요청이 지연되어도 최종 상태는 최신 사용자 의도에 수렴한다.
- [ ] Team/Player favorite는 Match 알림 subscription을 만들지 않는다.
- [ ] 최초 앱 실행에서 platform이 허용하면 알림 권한을 요청하며, 거부해도 비알림 기능을 사용할 수 있다.
- [ ] 전역 OFF 또는 권한 비활성 상태에서 Match 알림을 누르면 activation-required dialog가 표시된다.
- [ ] dialog 활성화 흐름은 permission 성공 뒤에만 전역 설정을 ON으로 바꾸고 구독을 생성한다.
- [ ] 앱 내 재요청이 불가능하면 명확한 안내와 system settings 이동 action을 제공한다.
- [ ] 전역 알림 OFF는 기존 Match favorite를 삭제하지 않고 현재 registration value의 알림만 비활성화하며, 알 수 없는 이전 target까지 해제했다고 표현하지 않는다.
- [ ] 전역 알림 OFF가 부분 성공하거나 응답이 불확실하면 완료로 표시하지 않고, 이미 OFF인 subscription을 되돌리지 않으면서 미확정 subscription만 재시도·재동기화한다.
- [ ] 전역 OFF pending 중 개별 Match를 ON으로 선택하면 남은 OFF 재시도를 중단하고 해당 ON 의도를 우선하며, 지연된 bulk OFF가 이를 되돌리지 않는다.
- [ ] server subscription 생성에 실패하면 local Match favorite가 남지 않고 전체 설정 실패와 재시도가 표시된다.
- [ ] server unsubscribe가 실패하면 기존 Match favorite/subscribed 상태가 유지되고 해제 실패와 재시도가 표시된다.
- [ ] MyPage와 Match Detail은 같은 성공·실패 결과를 표시한다.

### 서버 추적과 전달

- [ ] scheduler가 활성 구독을 기기별이 아닌 고유 Match ID별로 10분마다 확인한다.
- [ ] 서로 다른 registration value는 독립 target이며 이전·현재 target의 같은 Match 중복 전달 가능성을 허용한다.
- [ ] 동일 Match 상태를 반복 관찰하거나 job을 재시도해도 subscription별 시작 알림 intent가 1회를 넘지 않는다.
- [ ] 동일 조건에서 종료 알림 intent가 1회를 넘지 않는다.
- [ ] network/parsing failure와 upstream missing을 경기 시작·종료로 오인하지 않는다.
- [ ] postponed, cancelled, time-changed, missing 상태가 internal contract에서 terminal completed와 구분된다.
- [ ] terminal 상태와 알림 의무가 끝난 Match는 polling에서 제거된다.
- [ ] push provider의 절대적 exactly-once 보장이 아니라 서버 idempotency에 의한 exactly-once intent임을 구현 테스트가 반영한다.
