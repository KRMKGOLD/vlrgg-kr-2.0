# MyPage 기능 기획

## 목적과 사용자 가치

MyPage는 사용자가 저장한 Team, Player, Match를 한곳에서 다시 찾고 Match 알림의 전역 상태를 관리하는 개인화 허브다. 앱의 기본 진입 화면으로서 즐겨찾기 현황과 알림 사용 가능 여부를 즉시 이해하게 한다.

## 1차 MVP 범위

- 앱 실행 후 Bottom Navigation의 기본 destination
- 즐겨찾기 Team, Player, Match를 서로 구분된 그룹으로 표시
- 각 즐겨찾기 항목의 Detail 이동
- 즐겨찾기 해제
- 앱 전역 Match 알림 ON/OFF 토글
- 알림 권한 상태와 설정 안내
- 각 즐겨찾기 그룹의 독립적인 empty state

Match 알림을 설정하면 Match 즐겨찾기와 서버 notification subscription이 함께 생성된다. MyPage에서 Match 즐겨찾기를 제거하면 해당 subscription도 함께 취소된다.

## 명시적 제외 범위

- 로그인, 사용자 계정, 프로필 편집
- 기기 간 즐겨찾기 동기화
- Team 또는 Player 알림
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
- Match 즐겨찾기 선택 → Match Detail
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
3. Favorite Matches
   - Match 상태와 시작 시각
   - 알림 구독 상태를 이해할 수 있는 표시
4. Favorite Teams
5. Favorite Players

세 그룹은 같은 목록에 섞지 않는다. 각 그룹은 독립적으로 populated 또는 empty 상태를 가질 수 있으며, Match와 알림의 결합 관계는 Team/Player 즐겨찾기와 시각적으로 혼동되지 않아야 한다.

## 표시 데이터

| 영역 | 표시 데이터 |
| --- | --- |
| Notification | 앱 전역 알림 설정, system permission 상태, 필요 시 설정 이동 안내 |
| Favorite Match | Match ID, 팀, 예정 시각/상태, 스코어 등 로컬에 저장된 식별·요약 정보, subscription 상태 |
| Favorite Team | Team ID, 이름, 태그, 로고 등 저장된 식별·요약 정보 |
| Favorite Player | Player ID, handle, 현재 팀/이미지 등 저장된 식별·요약 정보 |

로컬에는 Detail로 다시 조회할 안정적인 ID가 반드시 있어야 한다. 목록에 보관할 snapshot의 정확한 범위와 갱신 정책은 구현 계약에서 확정한다.

## 화면 상태

| 상태 | 동작 |
| --- | --- |
| Loading | 로컬 즐겨찾기와 알림 설정을 읽는 동안 각 그룹의 안정적인 skeleton 또는 loading 상태를 표시한다. |
| Populated | Match, Team, Player 그룹을 분리해 표시하고 각 항목의 현재 동작을 제공한다. |
| Empty | 전체 즐겨찾기가 없으면 개인화 시작 방법을 안내하며, 각 그룹에도 독립적인 빈 상태를 제공한다. |
| Partial | 로컬 즐겨찾기는 표시할 수 있지만 권한 확인 또는 Match subscription 동기화가 실패한 경우 성공한 로컬 콘텐츠를 유지하고 실패 범위를 명시한다. |
| Error | 로컬 persistence 자체를 읽지 못하면 일반화된 오류와 재시도 동작을 표시한다. |
| Stale | 저장된 Match 요약이 최신 상태가 아닐 수 있으면 마지막 갱신 또는 재확인 필요 상태를 명시한다. |

system permission이 거부된 상태는 콘텐츠 오류가 아니라 알림 사용 불가 상태로 표현한다.

## 사용자 인터랙션

### 즐겨찾기

- Team 또는 Player를 제거하면 로컬 즐겨찾기만 삭제한다.
- Match를 제거하면 로컬 즐겨찾기를 삭제하고 해당 서버 notification subscription 취소를 요청한다.
- 즐겨찾기 항목을 누르면 대응하는 Detail로 이동한다.
- subscription 취소가 실패하면 Match가 제거된 것처럼 확정 표시하지 않고 재시도 가능한 동기화 상태를 제공해야 한다.

### 전역 알림 토글

- ON 전환 시 system permission을 확인한다.
- permission 요청이 가능한 상태라면 platform permission을 요청하고, 성공한 뒤에만 앱 전역 설정을 ON으로 확정한다.
- 인앱 요청이 더 이상 허용되지 않는 상태라면 설명과 함께 앱의 system settings로 이동하는 동작을 제공한다.
- OFF 전환 시 Match 알림 전달을 비활성화한다.
- OFF 전환만으로 Team/Player 즐겨찾기나 Match 즐겨찾기를 삭제하지 않는다.
- OFF 상태에서 Match Detail이 알림을 요청하면 활성화 필요 dialog를 띄우며, 사용자가 활성화를 선택하면 같은 permission 확인 흐름을 수행한다.

### 앱 최초 실행

- platform 상태가 permission 요청을 허용할 때 알림 동의를 요청한다.
- 사용자가 거부해도 앱과 즐겨찾기 기능은 계속 사용할 수 있다.

## 앱·서버 책임 경계

### 앱

- Team, Player, Match 즐겨찾기를 기기 로컬에 저장하고 그룹별로 조회한다.
- 앱 전역 notification 설정과 system permission 상태를 구분해 관리한다.
- platform notification permission 요청과 system settings 이동을 platform 경계로 제공한다.
- Match 즐겨찾기 추가·제거와 server subscription 생성·취소를 일관된 사용자 동작으로 연결한다.
- local persistence나 subscription 실패를 raw exception 없이 UI state로 변환한다.

### 서버

- 계정 없이 익명 installation/push target과 Match subscription을 저장한다.
- 활성 subscription의 고유 Match ID를 10분마다 확인한다.
- 시작 알림 1회와 종료 알림 1회를 중복 없이 전달하도록 idempotency를 보장한다.
- Match가 terminal state에 도달하고 알림 의무가 끝나면 tracking을 중단한다.
- 앱의 unsubscribe 요청을 반영해 더 이상 해당 target으로 알림을 보내지 않는다.
- Team/Player 즐겨찾기 데이터를 저장하거나 추적하지 않는다.

Notification subscription persistence와 scheduler는 일반 조회의 request-time scraping 원칙에 대한 기능 한정 예외다.

## Upstream 및 구현 메모

- MyPage 자체에는 upstream VLR.GG URL이 없다.
- Match tracking의 upstream URL과 상태 판별 규칙은 Matches/Match Detail 기능의 server contract에서 소유한다.
- push provider, endpoint path, DTO field, 로컬 persistence 기술은 이 제품 문서에서 확정하지 않는다.

## 테스트 가능한 수용 기준

- [ ] 앱의 초기 destination은 Bottom Navigation에서 세 번째인 MyPage다.
- [ ] MyPage Top App Bar에서 Search를 열고 Back하면 이전 MyPage 상태가 보존된다.
- [ ] Team, Player, Match 즐겨찾기가 서로 구분된 그룹에 표시된다.
- [ ] 각 그룹이 비어 있을 때 다른 그룹과 독립적인 empty state를 표시한다.
- [ ] 각 즐겨찾기 항목은 대응하는 Detail로 이동한다.
- [ ] Team/Player 즐겨찾기 제거는 로컬 데이터만 변경하며 notification subscription에는 영향을 주지 않는다.
- [ ] Match 즐겨찾기 제거는 대응하는 서버 subscription을 취소한다.
- [ ] 전역 알림 OFF는 어떤 즐겨찾기도 삭제하지 않으면서 알림 전달을 비활성화한다.
- [ ] system permission이 없는 상태에서 ON을 선택하면 허용 가능한 경우 permission을 요청하고 성공 후에만 ON이 된다.
- [ ] 인앱 permission 요청이 불가능하면 system settings 이동 안내를 제공한다.
- [ ] 최초 permission 요청을 거부해도 MyPage와 즐겨찾기 탐색은 정상 동작한다.
- [ ] subscription 동기화 실패가 Team/Player 목록이나 성공한 로컬 콘텐츠를 숨기지 않는다.
- [ ] 서버 scheduler 재시도에도 Match 시작·종료 알림은 각각 최대 1회만 발송된다.

## 열린 결정

- 전역 알림 OFF를 서버에 전달하는 방식과 이미 존재하는 Match subscription의 보존/재활성화 계약은 notification API 설계 시 확정한다. 사용자 관점에서는 즐겨찾기를 유지하고 전달만 중단해야 한다.
- Match 즐겨찾기 제거 중 unsubscribe 실패가 발생했을 때의 로컬 commit/rollback 및 재시도 정책은 데이터 일관성 설계에서 확정한다.
- 로컬 목록 snapshot의 보관 필드와 최신 정보 재조회 시점은 각 Detail repository 계약과 함께 확정한다.
