# Matches

## 문서 역할

이 문서는 Upcoming/Live, Results, Match Detail과 경기 시작 알림의 제품 요구사항을 정의한다. 공통 시각 언어와 상태 표현은 루트 [`DESIGN.md`](../../../DESIGN.md), 전체 내비게이션과 즐겨찾기 관계는 상위 [`docs/feature/README.md`](../README.md)를 따른다.

## 구현 상태 (2026-08-29)

- **Backend 콘텐츠 조회: 구현 완료.** `GET /api/v1/matches/upcoming`, `GET /api/v1/matches/results`, `GET /api/v1/matches/{matchId}`와 해당 parser/route 테스트가 구현되어 있다.
- **Backend Match 알림/구독: Stage 1.1 server offline GREEN.** Firestore Emulator, 익명 Target ID/Secret, START-only, request-bound scheduler로 Stage 1 runtime을 교체했고 credential-free contract/emulator/package evidence가 GREEN이다. 전체 경계는 [server-fcm-stage1.md](../../architecture/server-fcm-stage1.md), [ADR-0001](../../architecture/adr/0001-match-notification-stage1-storage-and-provider-boundary.md), [ADR-0002](../../architecture/adr/0002-match-notification-stage1-1-offline-firestore-boundary.md)를 따른다.
- **App Matches 목록: 구현 완료.** Upcoming/Live와 Results의 독립 상태·페이지네이션·스크롤, 최초 로딩/빈 상태/오류/새로고침/추가 로딩 상태, 날짜 그룹과 공통 Match card, 실제 Match Detail 진입 및 root/detail 왕복 복원이 구현되어 있다.
- **App Match Detail Basic D1: 구현 완료.** Loading/Content/Error, Upcoming/Postponed/Live/Completed/Cancelled/Unavailable, optional section Partial, `Match hero → Maps → Head to Head`, Team/Event/H2H 이동과 overlay/root 왕복 상태 복원을 구현했다. Android host 테스트·컴파일과 iOS simulator Compose UI 테스트로 검증했으며 실제 양 플랫폼 기기 screenshot·실기기 접근성 검증 완료를 주장하지 않는다.
- **App Match 알림: Stage 2 후속 범위.** notification bell, 구독 mutation, Target credential, App Check/FCM, 권한·settings dialog와 전역 알림 흐름은 Match Detail Basic D1에 포함하지 않는다. Match favorite와 `pastMatches` UI도 구현하지 않았다.

## 목적과 사용자 가치

- 예정, 진행 중, 완료 경기를 시간과 상태 중심으로 빠르게 확인하게 한다.
- Match에서 관련 Event와 Team으로 이어지는 탐색 경로를 제공한다.
- 사용자가 선택한 Upcoming/Postponed Match의 서버 알림을 구독하고, 앱을 계속 열어두지 않아도 경기 시작을 알 수 있게 한다.

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

### Match 알림

- Match 알림 설정은 로컬 즐겨찾기 없이 서버 notification subscription만 생성
- Match 알림 해제는 로컬 즐겨찾기 부작용 없이 서버 subscription만 해제
- 앱 설치 단위의 익명 Target ID/Secret과 opaque FCM registration token을 이용한 서버 알림 구독
- 외부 Scheduler가 10분 schedule slot마다 활성 구독의 고유 Match ID 상태 확인을 요청
- Match 시작 알림 intent 1회
- 최초 앱 실행의 알림 권한 요청, MyPage 전역 알림 ON/OFF, 비활성 상태의 안내 dialog와 system settings fallback

## 제외 범위

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
- Event Detail, Team Detail, MyPage의 planned `Next Matches`에서도 Match Detail로 진입할 수 있다.

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
   - 양 팀 이름 (현재 목록 DTO/domain 계약에는 이미지 URL이 없어 목록 card는 팀 이름만 표시)
   - 예정 경기의 남은 시간 또는 완료 경기의 스코어
   - Event 이름
5. 다음 페이지 로딩 또는 페이지 로딩 실패 표시

Live 상태는 색상만으로 전달하지 않고 텍스트 label을 함께 사용한다. 시간, 상태, 팀, Event 순으로 scan할 수 있어야 한다.

### Match Detail

1. D1에서는 뒤로가기만 제공한다. Upcoming/Postponed Match 알림 action은 후속 범위다.
2. 경기 상태와 예정/시작 시각
3. Event 이름과 경기 설명
4. 양 팀과 스코어
5. 맵 목록
6. Head to Head

정보가 존재하지 않는 FFW 등의 terminal Match는 비어 있는 정상 스탯 화면처럼 보이지 않아야 한다. 확인 가능한 팀, 결과, 상태를 우선 표시하고 사용할 수 없는 section은 명시적으로 생략하거나 unavailable로 표현한다.
Upcoming/Postponed pre-match에서는 Maps와 Head to Head를 section-level Empty로 표시할 수 있다. Match Detail의 Event identity와 Team identity는 각각 Event/Team Detail로 이동한다.
Team hero는 양쪽 모두 로고 영역을 위에, Team name을 아래에 쌓는 대칭 구조이며, score/result는 두 Team hero 사이의 수평 중앙에 배치한다. 현재 public contract에는 Team logo URL이 없으므로 D1은 저강조 placeholder만 사용하고 remote image를 추정하지 않는다.

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

### 알림 구독 상태

- 앱 전역 알림 ON/OFF
- platform system permission 상태
- 서버 구독 생성/해제 작업 상태

FCM registration token, Target Secret, scheduler 내부 delivery marker와 다른 서버 전용 상태는 UI 모델로 노출하지 않는다.

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

### 알림 설정

- `Ready`: 전역 알림과 system permission이 활성화되어 설정 가능
- `ActivationRequired`: 앱 전역 알림 또는 system permission이 비활성화
- `Subscribing`: 서버 subscription 생성 처리 중
- `Subscribed`: 활성 server subscription이 확인된 상태
- `Unsubscribing`: 해제 처리 중
- `SubscriptionError`: 생성 또는 해제가 완료되지 않았으며 사용자에게 재시도 가능한 상태를 제공

Match 알림 설정은 로컬 즐겨찾기 없이 서버 subscription을 생성하는 하나의 원자적 사용자 동작이다. Subscribe/unsubscribe 실패 시 Match Detail을 유지하고, subscribe는 confirmed bell OFF, unsubscribe는 confirmed bell ON을 유지한다. 두 경우 모두 full-content/initial Error로 전환하지 않고 actionable Snackbar `재시도`를 제공한다.
Mutation 중에는 modal scrim·center spinner를 표시하고 화면 전체 action을 disable한다. 실패 시에도 Match Detail을 유지하며 subscribe는 벨 OFF, unsubscribe는 벨 ON을 유지하고 actionable Snackbar `재시도`를 제공한다.

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
2. 앱 전역 알림과 system permission이 모두 활성화된 경우 server subscription 생성을 진행한다.
3. 하나라도 비활성화된 경우 알림 활성화가 필요하다는 dialog를 표시한다.
4. dialog에서 활성화를 선택하면 system permission을 확인하고, 요청 가능한 상태면 permission을 요청한다.
5. permission이 허용된 뒤에만 앱 전역 알림을 ON으로 변경하고 Match 설정을 계속한다.
6. 앱 안에서 다시 요청할 수 없는 상태면 이유와 함께 system settings 이동 action을 제공한다.
7. 사용자가 취소하면 subscription을 생성하지 않고 상세 화면에 남는다.

하나의 논리적 Match subscription은 서버가 발급한 한 anonymous Target과 한 Match의 쌍이다. 같은 쌍의 설정을 반복하면 중복 subscription을 만들지 않고 alarm ON으로 수렴한다. 응답을 받지 못해 성공 여부가 불확실해도 같은 revision/operation을 안전하게 재시도할 수 있다.

server subscription 생성이 확정적으로 실패하면 벨을 OFF로 유지하고 전체 설정 실패를 표시한다. 성공한 것으로 보이는 중간 상태를 유지하지 않으며 사용자는 같은 동작을 재시도할 수 있다.

앱 최초 실행에서도 platform이 요청을 허용하는 상태라면 알림 권한을 요청한다. 권한 거부 자체가 News/Matches 등 비알림 기능 사용을 막아서는 안 된다.

### Match 알림 해제

- Match 알림 해제는 current Target의 해당 Match subscription 취소만 요청한다.
- 해제 action은 Match Detail에서만 제공한다.
- Team/Player 즐겨찾기에는 이 흐름을 적용하지 않는다.
- server unsubscribe가 실패하면 기존 subscribed 상태와 재시도 동작을 유지한다.
- 같은 Target/Match의 해제를 반복하면 alarm OFF로 수렴한다. 한 Target의 해제 요청은 다른 Target의 subscription을 변경하지 않는다.
- Stage 1.1 서버는 같은 Target의 모든 mutation에 positive `Long` revision을 사용하고 stale·replay·conflict를 구분한다. 앱이 revision을 발행·영속·재시도하는 동작은 Stage 2다.

### 전역 알림 OFF

- MyPage의 전역 OFF는 Team/Player 즐겨찾기를 삭제하지 않는다.
- 전역 OFF는 current Target에 연결된 Match 알림만 비활성화한다.
- 현재 target에 연결된 여러 Match subscription 중 일부만 OFF로 확인되거나 응답이 불확실하면 앱은 전역 OFF를 완료 상태로 표시하지 않는다. 이미 OFF로 확인된 subscription은 그대로 유지하고 미확정 subscription만 pending으로 표시해 재시도·재동기화하며, 이 과정에서도 Team/Player 즐겨찾기는 보존한다.
- 전역 OFF가 pending인 동안 사용자가 개별 Match 알림을 다시 ON으로 선택하면 그 선택이 최신 전역·Match 의도가 된다. 앱은 남은 전역 OFF 재시도를 중단하고 system permission 확인과 앱의 전역 알림 설정 활성화 흐름 뒤 해당 Match를 개별 설정하며, 지연된 이전 bulk OFF 요청이나 응답이 이 ON을 되돌려서는 안 된다. 서버의 false-only global-OFF endpoint에 전체 ON을 요청하지 않는다.
- 앱과 서버는 잃어버린 이전 Target을 같은 물리 기기나 사용자로 추론하거나 해제하지 않는다. 이전 Target의 구독은 명시적 revoke, provider invalid 또는 정리 정책까지 유효할 수 있고 일시적 중복 전달은 MVP에서 허용한다.
- OFF 상태에서 새 Match 알림을 요청하면 activation-required dialog를 표시한다.

Stage 1.1 서버 계약은 `PUT /api/v1/notification-targets/{targetId}/match-subscriptions`의 `enabled=false` 요청으로 current Target의 최대 100개 subscription을 하나의 Firestore transaction에서 비활성화하고 target-scoped revision을 적용한다. `enabled=true` 전체 ON은 지원하지 않는다. 서버 구현과 App의 pending/reconciliation 흐름은 아직 미구현이다.

## 10분 Match 추적 및 알림 contract

### 구독

- 서버가 발급한 Target ID와 one-time Target Secret이 로그인 없는 설치 단위 Target을 구분한다. App Check는 앱 진위, Target Secret은 해당 Target 권한을 별도로 증명한다.
- FCM registration token은 opaque 전달 주소이며 사용자 인증, Target 권한, FID나 물리 기기 소유 증명이 아니다.
- 같은 Target에서 token이 갱신되어도 Target/Match 설정은 유지한다. 서로 다른 Target은 같은 물리 기기에서 생성되었더라도 병합·대체·이관하지 않는다.
- 서버는 `(targetId, Match)` 쌍을 하나의 논리적 subscription으로 다룬다.
- 같은 Match를 여러 target이 구독해도 upstream 상태 확인은 고유 Match ID 기준으로 중복 제거한다.

token SDK 획득·Target credential 보관·서버 동기화는 Stage 2 App이, authority·provider-invalid Target 정리와 persistence는 [Stage 1.1 서버 계약](../../architecture/server-fcm-stage1.md)이 소유한다.

### 추적

- 외부 Scheduler는 활성 구독이 있는 고유 Match ID를 처리하도록 기본 10분 간격의 schedule slot 요청을 보낸다. 서버 내부 process loop는 이 주기를 소유하지 않는다.
- Match 시작 감지는 VLR.GG에 표시된 상태를 기준으로 하므로 사용자에게 보이는 알림은 실제 시점보다 scheduler 주기와 upstream 반영만큼 늦을 수 있다.
- upcoming, live, completed 외에도 time-changed, postponed, cancelled, unavailable/missing 상태를 내부 contract에서 구분한다.
- 일시적인 network/parsing failure나 upstream missing을 completed로 간주하지 않는다.
- terminal 상태인 Match와 활성 Target이 없는 Match는 추적 대상에서 제거한다.

### 전달

- 각 subscription에는 Match 시작 알림을 사용자에게 1회만 보내려는 delivery marker가 필요하다.
- scheduler 재시도, 서버 재시작, 동일 상태 반복 관찰이 중복 사용자 알림을 만들지 않도록 idempotent하게 처리한다.
- 여기서 `1회`는 서버가 관리하는 사용자-visible 알림의 exactly-once intent다. 외부 push transport 자체의 절대적 exactly-once 전달 보장을 뜻하지 않는다.
- 취소, 연기, 시간 변경, upstream missing은 상태로 기록하지만 MVP 사용자 알림은 START만 제공한다.

이 영속 구독, scheduler, delivery marker는 일반 scraping 기능의 request-time/no-database 기준에 대한 Match 알림 전용 예외다.

## 앱과 서버 책임 경계

### 서버

- 목록/상세 요청에서 `Scraper → Parser → SourceModel → Mapper → Response` 경계를 유지한다.
- Upcoming/Live, Results, Match Detail HTML을 app-facing response로 가공한다.
- DOM selector, raw HTML, Jsoup type을 public response에 노출하지 않는다.
- 알림 기능에 한해 Target별 subscription persistence, 10분 schedule slot 처리, 상태 비교와 idempotent START delivery를 소유한다.
- network/parsing failure를 안전한 공통 error envelope로 반환하고 실패를 terminal Match 상태로 오인하지 않는다.

### 앱

- remote DTO를 app Domain Model로 매핑하고 목록/상세 UiState를 관리한다.
- 앱이 저장하지 않는 로컬 즐겨찾기는 Match뿐이다. Team·Player 로컬 즐겨찾기의 저장 책임은 [`my-page/README.md`](../my-page/README.md) 계약을 따른다.
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
- 10분 상태 추적, anonymous subscription persistence, delivery marker, scheduler, push provider와 구독 endpoint는 별도의 Match notification Stage 1 slice로 구현되어 있으며 이 콘텐츠 조회 API 계약에는 포함되지 않는다. 알림 저장소와 scheduler는 일반 콘텐츠 조회의 request-time freshness, cache 부재, stale fallback 부재를 바꾸지 않는다.

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

체크된 서버 항목은 `main`에서 실제로 증명된 현재 기능 범위이며, Stage 1.1 Target/Firestore/START-only/scheduler 항목도 offline GREEN evidence가 확인된 구현 사실이다. App 연동, 실제 Firebase/GCP와 기기 표시는 Stage 2다.

### 목록과 상세

- [ ] Matches 탭에서 Upcoming/Live와 Results를 명확히 전환할 수 있다.
- [ ] Upcoming/Live는 날짜, 경기 시각, 남은 시간, 양 팀, Event, 상태를 표시한다.
- [ ] Results는 날짜, 완료 시각, 양 팀, 스코어, Event를 표시한다.
- [ ] 목록 pagination이 중복 항목 없이 동작하고 추가 페이지 실패 시 기존 목록을 유지한다.
- [x] 경기 항목은 실제 Match Detail로, Match Detail의 Event reference는 Event Detail로 이동한다.
- [x] Match Detail은 Event, 경기 설명, 양 팀, 스코어, 상태, 맵, Head to Head를 사용 가능한 범위에서 표시하고 기존 서버 `pastMatches` field를 UI section으로 렌더링하지 않는다.
- [x] Match Detail의 양 Team hero는 로고 영역 위·이름 아래의 대칭 구조를 유지하고 score/result를 두 Team 사이 중앙에 배치한다. 계약에 없는 remote logo는 추정하지 않는다.
- [x] BO1/BO3/BO5와 FFW fixture에서 상태별 선택성이 parsing failure와 구분된다.
- [ ] initial loading, empty, initial error, pagination error, stale/unavailable 표현이 정상 populated 상태와 구분된다.

### Match Detail Basic D1

- [x] 전체 요청은 `Loading`, `Content`, `Error`로 구분하고 Error에서 같은 Match ID 재시도와 Back을 제공한다.
- [x] Upcoming/Postponed, Live, Completed, Cancelled/Unavailable을 색상 외 상태 text와 함께 구분한다.
- [x] Content는 `Match hero/basic info → Maps → Head to Head` 순서를 유지하며 Maps/H2H의 한쪽 또는 양쪽이 비어도 기본 Match와 사용 가능한 section을 보존한다.
- [x] map/H2H의 nullable score는 `—`, 실제 numeric zero는 `0`으로 구분한다.
- [x] Team/Event ID가 있을 때만 전체 identity를 활성화하고 H2H 전체 surface는 관련 Match Detail로 이동한다.
- [x] Matches Upcoming/Results, Event, Team, Player의 기존 Match route가 같은 실제 destination으로 해석되고 Back·root 전환 뒤 loaded/scroll overlay state를 보존한다.
- [x] bottom navigation, notification bell·mutation·Snackbar, Match favorite, `pastMatches`, 추정 remote Team image를 Match Detail D1에 노출하지 않는다.
- [x] Compose UI 테스트에서 48dp interactive target, 접근 가능한 label, 긴 한국어 Team/Event/description의 안전한 배치를 검증한다.
- [ ] Android/iOS 실제 기기 screenshot 비교와 실기기 접근성 검증은 별도 수행이 필요하다.

### 즐겨찾기, 권한, 전역 설정

- [ ] 활성 system permission과 앱 전역 알림 설정 ON 상태에서 Upcoming/Postponed Match 알림을 설정하면 server subscription만 생성되고 로컬 즐겨찾기는 생성되지 않는다.
- [ ] Match 알림은 MyPage에 별도 경기 즐겨찾기 그룹으로 표시되지 않으며, MyPage의 Next Matches는 즐겨찾기 Team 기반 계획 계약이다.
- [ ] 허용된 App Check evidence로 Target을 생성하면 Target ID와 one-time Target Secret을 받는다. FCM registration token은 서버 저장소의 전달 주소로만 보관하고 후속 public response, UI/public state, log에는 노출하지 않는다.
- [ ] 같은 Target/Match의 서버 알림 설정을 반복하면 중복 없이 alarm ON으로 수렴하고 같은 revision/operation을 안전하게 replay한다.
- [ ] Upcoming/Postponed Match 알림 해제는 current Target의 대응 subscription만 취소하며 반복 해제는 alarm OFF로 수렴한다.
- [ ] 같은 Target의 설정·해제·global OFF는 revision ordering으로 최신 승인 의도에 수렴하며 stale·replay·conflict·exhaustion을 구분한다.
- [ ] Team/Player 즐겨찾기는 Match 알림 subscription을 만들지 않는다.
- [ ] 최초 앱 실행에서 platform이 허용하면 알림 권한을 요청하며, 거부해도 비알림 기능을 사용할 수 있다.
- [ ] 전역 OFF 또는 권한 비활성 상태에서 Match 알림을 누르면 activation-required dialog가 표시된다.
- [ ] dialog 활성화 흐름은 permission 성공 뒤에만 전역 설정을 ON으로 바꾸고 구독을 생성한다.
- [ ] 앱 내 재요청이 불가능하면 명확한 안내와 system settings 이동 action을 제공한다.
- [ ] 전역 알림 OFF는 Team/Player 즐겨찾기를 삭제하지 않고 current Target의 알림만 비활성화하며, 잃어버린 이전 Target까지 해제했다고 표현하지 않는다.
- [ ] 전역 알림 OFF가 부분 성공하거나 응답이 불확실하면 완료로 표시하지 않고, 이미 OFF인 subscription을 되돌리지 않으면서 미확정 subscription만 재시도·재동기화한다.
- [ ] 전역 OFF pending 중 개별 Match를 ON으로 선택하면 남은 OFF 재시도를 중단하고 해당 ON 의도를 우선하며, 지연된 bulk OFF가 이를 되돌리지 않는다.
- [ ] server subscription 생성에 실패해도 Match Detail을 유지하고 confirmed bell OFF와 actionable Snackbar Retry를 표시한다.
- [ ] server unsubscribe가 실패해도 Match Detail을 유지하고 confirmed bell ON과 actionable Snackbar Retry를 표시한다.
- [ ] Match Detail의 벨은 Live/Completed/Cancelled/Unavailable/FFW에는 노출되지 않는다.

### 서버 추적과 전달

- [ ] request-bound scheduler가 활성 구독을 Target별이 아닌 고유 Match ID별 10분 schedule slot로 확인한다.
- [ ] Target별·전체 active unique Match 상한 100을 Firestore transaction과 concurrency test로 지킨다.
- [ ] 서로 다른 Target은 독립적이며 이전·현재 Target의 같은 Match 중복 전달 가능성을 허용한다.
- [ ] 동일 Match 상태를 반복 관찰하거나 scheduler 요청을 재시도해도 subscription별 START intent가 1회를 넘지 않는다.
- [ ] END intent를 생성하거나 발송하지 않는다.
- [ ] network/parsing failure와 upstream missing을 경기 시작으로 오인하지 않는다.
- [ ] postponed, cancelled, time-changed, missing 상태가 internal contract에서 terminal completed와 구분된다.
- [ ] terminal 상태 또는 활성 Target이 없는 Match는 추적에서 제거된다.
- [ ] push provider의 절대적 exactly-once가 아니라 서버 idempotency에 의한 intent uniqueness임을 구현 테스트가 반영한다.
- [ ] delivery intent는 committed call marker 뒤에만 fake provider를 호출하며 동시 invocation이 같은 intent를 중복 claim하지 않는다.
- [ ] retryable provider 결과는 application attempt, backoff와 safe hint를 영속화하고 timeout·취소·ambiguous 결과는 자동 재전송하지 않는 `UNKNOWN`으로 격리한다.
- [ ] provider가 invalid Target을 증명하면 해당 Target만 비활성화하고 registration token을 논리적으로 제거한다.
- [ ] Firestore Emulator, fake App Check/FCM, packaged `/health` 검증은 credential 없이 GREEN이다.
- [ ] App·실제 App Check/FCM·production Firestore·Cloud Run smoke는 Stage 2에서 증명한다.
