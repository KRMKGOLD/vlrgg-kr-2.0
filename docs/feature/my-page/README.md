# MyPage 기능 기획

- Status: Planned; App implementation is Stage 2
- Last reviewed: 2026-08-03

## 목적과 사용자 가치

MyPage는 사용자가 저장한 Team과 Player를 다시 찾고 Match 알림의 전역 상태를 관리하는 개인화 허브다. 앱의 기본 진입 화면으로서 Team favorite를 우선 노출하고 알림 사용 가능 여부를 즉시 이해하게 한다.

## 1차 MVP 범위

- 앱 실행 후 Bottom Navigation의 기본 destination
- Favorite Team을 주 섹션으로 표시
- Favorite Player를 compact 보조 섹션으로 표시
- Favorite Team에서 집계한 예정 Match의 `Next Matches` 섹션
- 각 즐겨찾기 항목의 Detail 이동
- 즐겨찾기 해제
- 앱 전역 Match 알림 ON/OFF 토글
- 알림 권한 상태와 설정 안내
- 각 favorite/Next Matches 섹션의 독립적인 empty state

Match 알림은 Match Detail의 벨에서만 설정하며 로컬 즐겨찾기를 만들지 않는다. 벨은 `Upcoming`/`Postponed` Match에서만 보이며 current anonymous Target의 서버 notification subscription을 제어한다. 이 App 흐름과 실제 Firebase SDK 연동은 Stage 2다.

## 명시적 제외 범위

- 로그인, 사용자 계정, 프로필 편집
- 기기 간 즐겨찾기 동기화
- Team 또는 Player 알림
- 사용자에게 Match 즐겨찾기 기능/그룹을 제공하지 않는다.
- 알림함, 알림 이력
- 즐겨찾기 폴더, 태그, 수동 정렬
- Dark Mode 선택

## 진입과 이탈 경로

### 진입

- 앱의 초기/default destination
- Bottom Navigation의 세 번째 `MyPage` 항목
- Detail 화면에서 Back으로 이전 MyPage 상태에 복귀

### 이탈

- Team 즐겨찾기 선택 → Team Detail
- Player 즐겨찾기 선택 → Player Detail
- `Next Matches`의 Match 선택 → Match Detail
- Top App Bar의 Search → 별도 Search Screen
- 다른 Bottom Navigation 항목 선택 → 해당 root destination

Search에서 Back하면 MyPage의 선택, 스크롤, 콘텐츠 상태를 보존한 채 복귀한다.

## 화면과 콘텐츠 계층

1. Shared Top App Bar
   - MyPage 제목
   - Search
2. Notification setting
   - 전역 알림 ON/OFF 토글
   - system permission이 차단된 경우 상태와 설정 이동 안내
3. Next Matches (planned API contract)
   - favorite Team IDs로 집계한 예정 Match
   - stable Match ID dedupe와 scheduled-time 정렬
4. Favorite Teams (primary personalization)
5. Favorite Players (compact secondary list)

각 섹션은 같은 목록에 섞지 않는다. Next Matches는 Team favorite IDs를 입력으로 한 예정 계약이며, Match card는 Match Detail만 연다. MyPage에는 Match 벨을 표시하지 않는다.

## 표시 데이터

| 영역 | 표시 데이터 |
| --- | --- |
| Notification | 앱 전역 알림 설정, system permission 상태, 필요 시 설정 이동 안내 |
| Favorite Team | Team ID, 이름, 태그, 로고 등 저장된 식별·요약 정보 |
| Favorite Player | Player ID, handle, 현재 팀/이미지 등 저장된 식별·요약 정보 |
| Next Matches | favorite Team IDs로 예정된 Match를 집계한 향후 서버 계약, stable Match ID, scheduled time, Match 요약 |

로컬에는 Detail로 다시 조회할 안정적인 ID가 반드시 있어야 한다. 목록에 보관할 snapshot의 정확한 범위와 갱신 정책은 구현 계약에서 확정한다.
Next Matches의 endpoint path와 DTO shape은 아직 정하지 않으며 이 문서에서 발명하지 않는다. 서버 구현이 없는 planned contract다.

## 화면 상태

| 상태 | 동작 |
| --- | --- |
| Initial Loading | 로컬 Team/Player 즐겨찾기와 알림 설정을 읽는 동안 안정적인 skeleton을 표시한다. |
| Populated | Next Matches, Favorite Teams, Favorite Players를 분리해 표시하고 각 항목의 현재 동작을 제공한다. |
| Empty | Team/Player 즐겨찾기와 Next Matches의 empty를 독립적으로 표시한다. Next Matches는 즐겨찾기 Team 없음과 Team은 있으나 예정 Match 없음으로 구분한다. |
| Next Matches loading | Team/Player 목록과 알림 영역을 유지하고 Next Matches section만 loading 처리한다. |
| Next Matches Error | Team/Player 목록을 유지하고 Next Matches section-level Retry를 제공한다. |
| Notification Sync Error | 마지막 confirmed toggle 상태를 유지하고 notification setting region에 inline Retry를 표시한다. |
| Notification Mutation In Progress | full-screen modal spinner를 표시하고 화면 입력을 일시적으로 block한다. |
| Favorite Removal Error | 제거하려던 Team/Player 항목을 유지하고 actionable Retry Snackbar를 표시한다. |
| Initial Local Persistence Error | bottom navigation을 유지하는 full-content Error Screen과 Retry를 표시한다. |
| Freshness annotation (not independent screen) | 서버가 이전 Match 결과를 failure fallback으로 반환하지 않는다. 앱이 기존 Next Matches를 유지한다면 마지막 갱신 또는 재확인 필요 상태를 명시한다. |

system permission이 거부된 상태는 콘텐츠 오류가 아니라 알림 사용 불가 상태로 표현한다.

## 사용자 인터랙션

### 즐겨찾기

- Team 또는 Player를 제거하면 로컬 즐겨찾기만 삭제한다. 제거 mutation이 실패하면 해당 항목을 유지하고 Retry Snackbar를 표시한다.
- Match를 제거하는 UI는 제공하지 않는다. Match 알림 해제는 Match Detail의 벨에서 서버 subscription만 OFF로 요청한다.
- 즐겨찾기 항목을 누르면 대응하는 Detail로 이동한다.
- Match subscription 해제 실패는 Match Detail에서 actionable Snackbar Retry로 표시하며 MyPage favorite 목록에는 영향을 주지 않는다.

### 전역 알림 토글

- ON 전환 시 system permission을 확인한다.
- permission 요청이 가능한 상태라면 platform permission을 요청하고, 성공한 뒤에만 앱 전역 설정을 ON으로 확정한다.
- 인앱 요청이 더 이상 허용되지 않는 상태라면 설명과 함께 앱의 system settings로 이동하는 동작을 제공한다.
- OFF 전환 시 current Target에 연결된 Match 알림을 false-only global-OFF endpoint로 비활성화한다.
- OFF 전환만으로 Team/Player 즐겨찾기를 삭제하지 않는다.
- 현재 target의 모든 Match subscription이 OFF로 확인될 때까지 전역 토글은 전환 중 상태를 유지한다. 일부만 성공하거나 응답이 불확실하면 OFF 완료로 표시하지 않고, 성공한 항목은 유지한 채 미확정 항목과 재시도·재동기화 동작을 보여준다.
- notification sync가 실패하면 last confirmed toggle 상태를 유지하고 notification setting region에 inline Retry를 표시한다.
- notification mutation 중에는 full-screen modal spinner로 화면 입력을 일시적으로 block한다.
- 전역 OFF 전환 중 개별 Match 알림을 ON으로 선택하면 OFF 재시도보다 이 최신 의도를 우선한다. 앱은 남은 OFF 재시도를 중단하고 system permission 확인과 앱의 전역 알림 설정 활성화 흐름 및 해당 Match의 개별 설정 상태를 표시하며, 오래된 OFF 응답으로 다시 OFF 완료를 표시하지 않는다. 서버의 false-only global-OFF endpoint에 전체 ON을 요청하지 않는다.
- 앱 삭제·재설치 등으로 자격을 잃은 이전 Target은 같은 물리 기기나 사용자로 추론하거나 해제하지 않는다. 이전 Target의 독립 구독은 남을 수 있으며, 이 current-target 의미는 [Matches 문서](../matches/README.md#전역-알림-off)를 따른다.
- OFF 상태에서 Match Detail이 알림을 요청하면 활성화 필요 dialog를 띄우며, 사용자가 활성화를 선택하면 같은 permission 확인 흐름을 수행한다.

### 앱 최초 실행

- platform 상태가 permission 요청을 허용할 때 알림 동의를 요청한다.
- 사용자가 거부해도 앱과 즐겨찾기 기능은 계속 사용할 수 있다.

## 앱·서버 책임 경계

### 앱

- Team, Player 즐겨찾기를 기기 로컬에 저장하고 그룹별로 조회한다.
- 앱 전역 notification 설정과 system permission 상태를 구분해 관리한다.
- platform notification permission 요청과 system settings 이동을 platform 경계로 제공한다.
- Match Detail의 notification subscription 생성·취소와 전역 알림 상태를 연결한다. 로컬 즐겨찾기는 저장하지 않는다.
- Target ID/Secret을 안전하게 보관하고 FCM registration token refresh를 같은 Target에 동기화한다. 자격을 잃으면 새 Target을 생성한다.
- local persistence나 subscription 실패를 raw exception 없이 UI state로 변환한다.

### 서버

- 계정 없이 서버가 발급한 익명 Target ID/Secret과 FCM registration token, Match subscription을 구분해 저장한다.
- App Check와 Target Secret을 모두 검증하고 current Target의 unsubscribe/global OFF를 revision ordering으로 alarm OFF에 수렴시킨다.
- Team/Player 즐겨찾기 데이터를 저장하거나 추적하지 않는다.

Notification subscription persistence와 scheduler는 일반 조회의 request-time scraping 원칙에 대한 기능 한정 예외다. 고유 Match schedule slot, START intent와 terminal cleanup의 상세 계약은 [Matches 문서](../matches/README.md#10분-match-추적-및-알림-contract)가 소유한다.

## Upstream 및 구현 메모

- MyPage 자체에는 upstream VLR.GG URL이 없다.
- Match tracking의 upstream URL과 상태 판별 규칙은 Matches/Match Detail 기능의 server contract에서 소유한다.

## 테스트 가능한 수용 기준

- [ ] 앱의 초기 destination은 Bottom Navigation에서 세 번째인 MyPage다.
- [ ] MyPage Top App Bar에서 Search를 열고 Back하면 이전 MyPage 상태가 보존된다.
- [ ] Favorite Team이 주 섹션, Favorite Player가 compact 보조 섹션으로 표시된다.
- [ ] Next Matches는 즐겨찾기 Team IDs로 예정 Match를 집계하고 stable Match ID로 dedupe하며 scheduled time 순으로 정렬한다.
- [ ] Next Matches는 즐겨찾기 Team 없음, 예정 Match 없음, section failure를 구분한다.
- [ ] Next Matches의 compact Match Card는 Match Detail만 연다.
- [ ] 각 Team/Player favorite 항목은 대응하는 Detail로 이동한다.
- [ ] Team/Player 즐겨찾기 제거는 로컬 데이터만 변경하며 notification subscription에는 영향을 주지 않는다.
- [ ] Match Detail의 벨 ON/OFF는 로컬 즐겨찾기 부작용 없이 current Target 서버 subscription만 변경한다.
- [ ] 전역 알림 OFF는 어떤 즐겨찾기도 삭제하지 않으면서 current Target만 비활성화한다.
- [ ] 전역 알림 OFF가 부분 성공하거나 응답이 불확실하면 완료로 표시하지 않고 미확정 subscription의 재시도·재동기화를 제공한다.
- [ ] 전역 OFF 전환 중 개별 Match ON을 선택하면 남은 OFF 재시도를 중단하고 최신 ON 상태를 표시하며, 지연된 OFF 응답이 이를 되돌리지 않는다.
- [ ] 전역 알림 OFF는 잃어버린 이전 Target을 같은 물리 기기나 사용자로 추론하거나 해제했다고 표현하지 않는다.
- [ ] system permission이 없는 상태에서 ON을 선택하면 허용 가능한 경우 permission을 요청하고 성공 후에만 ON이 된다.
- [ ] 인앱 permission 요청이 불가능하면 system settings 이동 안내를 제공한다.
- [ ] 최초 permission 요청을 거부해도 MyPage와 즐겨찾기 탐색은 정상 동작한다.
- [ ] Next Matches section failure가 Team/Player 목록이나 성공한 로컬 콘텐츠를 숨기지 않는다.
- [ ] Next Matches Error는 Team/Player 목록을 유지하고 section-level Retry를 제공한다.
- [ ] Notification Sync Error는 last confirmed toggle을 유지하고 notification region inline Retry를 제공한다.
- [ ] Notification Mutation In Progress는 full-screen modal spinner로 입력을 일시 block한다.
- [ ] Team/Player 즐겨찾기 제거 실패는 해당 항목을 유지하고 Retry Snackbar를 제공한다.
- [ ] Initial local persistence error는 bottom navigation을 유지하는 full-content Error Screen과 Retry를 제공한다.
- [ ] MyPage에는 Match 알림 벨이나 별도 Match 즐겨찾기 그룹이 없다.
