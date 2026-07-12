# Matches

## 문서 역할

이 문서는 Upcoming/Live, Results, Match Detail, Match 즐겨찾기와 시작·종료 알림의 제품 요구사항을 정의한다. 공통 시각 언어와 상태 표현은 루트 [`DESIGN.md`](../../../DESIGN.md), 전체 내비게이션과 즐겨찾기 관계는 상위 [`docs/feature/README.md`](../README.md)를 따른다.

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
- 익명 설치/푸시 대상을 이용한 서버 알림 구독
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

푸시 토큰, 익명 installation 식별자, scheduler 내부 delivery marker는 UI 모델로 노출하지 않는다.

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

서버 subscription 생성에 실패했는데 알림이 설정된 것처럼 표시하거나, 해제 실패를 숨기고 로컬 favorite만 제거해서는 안 된다. 실패 시 일관성 복구 정책은 구현 전에 아래 열린 결정에서 확정한다.

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

앱 최초 실행에서도 platform이 요청을 허용하는 상태라면 알림 권한을 요청한다. 권한 거부 자체가 News/Matches 등 비알림 기능 사용을 막아서는 안 된다.

### Match 알림 해제

- Match favorite 해제는 local favorite 제거와 해당 Match의 server subscription 취소를 함께 요청한다.
- MyPage와 Match Detail 어디에서 해제해도 같은 결과가 되어야 한다.
- Team/Player favorite에는 이 흐름을 적용하지 않는다.

### 전역 알림 OFF

- MyPage의 전역 OFF는 기존 Match favorite를 Team/Player favorite로 변환하거나 삭제하지 않는다.
- OFF 상태에서는 사용자에게 알림을 전달하지 않는다.
- OFF 상태에서 새 Match 알림을 요청하면 activation-required dialog를 표시한다.

## 10분 Match 추적 및 알림 contract

### 구독

- 서버는 계정 없이 전송할 수 있도록 Match ID와 최소한의 익명 installation/push-target 정보를 저장한다.
- 같은 Match를 여러 기기가 구독해도 upstream 상태 확인은 고유 Match ID 기준으로 중복 제거한다.
- Match favorite를 제거하면 해당 설치의 구독을 취소한다. 다른 설치의 같은 Match 구독에는 영향을 주지 않는다.

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
- 알림 기능에 한해 anonymous subscription persistence, 10분 scheduler, 상태 비교, idempotent start/end delivery를 소유한다.
- network/parsing failure를 안전한 공통 error envelope로 반환하고 실패를 terminal Match 상태로 오인하지 않는다.

### 앱

- remote DTO를 app Domain Model로 매핑하고 목록/상세 UiState를 관리한다.
- local Match favorite와 전역 알림 설정을 저장한다.
- platform permission 확인/요청과 system settings 이동 bridge를 제공한다.
- 서버 구독 생성/해제 결과를 반영해 로컬과 서버 상태가 일치하도록 조정한다.
- push credential이나 raw server failure를 UI에 노출하지 않는다.

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
- [ ] BO1/BO3/BO5와 FFW fixture에서 상태별 선택성이 parsing failure와 구분된다.
- [ ] loading, empty, partial, error, stale/unavailable 표현이 정상 populated 상태와 구분된다.

### 즐겨찾기, 권한, 전역 설정

- [ ] 활성 권한/전역 ON 상태에서 Match 알림을 설정하면 local Match favorite와 server subscription이 모두 생성된다.
- [ ] Match favorite는 MyPage에 표시되고 MyPage와 Match Detail 모두에서 같은 상세 화면으로 이동한다.
- [ ] Match favorite 해제는 해당 설치의 server subscription도 취소한다.
- [ ] Team/Player favorite는 Match 알림 subscription을 만들지 않는다.
- [ ] 최초 앱 실행에서 platform이 허용하면 알림 권한을 요청하며, 거부해도 비알림 기능을 사용할 수 있다.
- [ ] 전역 OFF 또는 권한 비활성 상태에서 Match 알림을 누르면 activation-required dialog가 표시된다.
- [ ] dialog 활성화 흐름은 permission 성공 뒤에만 전역 설정을 ON으로 바꾸고 구독을 생성한다.
- [ ] 앱 내 재요청이 불가능하면 명확한 안내와 system settings 이동 action을 제공한다.
- [ ] 전역 알림 OFF는 기존 Match favorite를 삭제하지 않지만 사용자 알림 전달을 중단한다.

### 서버 추적과 전달

- [ ] scheduler가 활성 구독을 기기별이 아닌 고유 Match ID별로 10분마다 확인한다.
- [ ] 동일 Match 상태를 반복 관찰하거나 job을 재시도해도 subscription별 시작 알림 intent가 1회를 넘지 않는다.
- [ ] 동일 조건에서 종료 알림 intent가 1회를 넘지 않는다.
- [ ] network/parsing failure와 upstream missing을 경기 시작·종료로 오인하지 않는다.
- [ ] postponed, cancelled, time-changed, missing 상태가 internal contract에서 terminal completed와 구분된다.
- [ ] terminal 상태와 알림 의무가 끝난 Match는 polling에서 제거된다.
- [ ] push provider의 절대적 exactly-once 보장이 아니라 서버 idempotency에 의한 exactly-once intent임을 구현 테스트가 반영한다.

## 열린 결정

다음 결정은 알림 구현 전에 확정해야 한다.

1. local favorite 저장과 server subscription 생성/해제 중 한쪽만 성공했을 때의 보상·재시도 정책
2. MyPage 전역 알림을 OFF로 바꿀 때 서버 subscription을 유지하되 전달만 억제할지, 서버에 pause 상태를 동기화할지의 구체 contract

위 결정은 MVP 범위 자체를 바꾸지 않으며, API 및 persistence 설계 작업에서 확정한다.
