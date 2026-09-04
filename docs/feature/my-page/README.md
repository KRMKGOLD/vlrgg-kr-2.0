# MyPage 기능 기획

- Status: Implemented (#44, #70 image persistence)
- Last reviewed: 2026-09-04

## 목적과 사용자 가치

MyPage는 기기에 저장한 Favorite Team과 Favorite Player를 다시 찾는 로컬 개인화 화면이다. 앱의 기본 root로 진입하며 Team과 Player를 독립된 섹션으로 보여 주고, 각 항목의 Detail 이동과 즐겨찾기 해제를 제공한다.

## 구현 범위

- 앱 실행 후 기본 destination인 MyPage root
- 56dp Top App Bar의 제목과 Search 진입
- Favorite Teams 주 섹션
- Favorite Players 보조 섹션
- 저장 순서를 유지하는 Team/Player 목록
- 저장된 Team logo와 Player profile 이미지 표시 및 안정적인 placeholder fallback
- Team Detail과 Player Detail 이동
- optimistic 즐겨찾기 해제, 실패 rollback, actionable Retry Snackbar
- Team/Player별 loading, empty, content, section error와 초기 full error
- Search 및 다른 root 왕복 후 MyPage scroll·ViewModel·콘텐츠 상태 복원

## 명시적 제외 범위

다음 항목은 #44와 현재 MyPage 제품 범위에 포함하지 않는다.

- Notification 설정, 권한, 알림 이력, alarm ON/OFF
- Match 목록, Match 즐겨찾기, Match 알림, `Next Matches`
- 로그인, 계정, 프로필 편집, 계정 기반 동기화
- Team 또는 Player 알림
- 즐겨찾기 폴더, 태그, 수동 정렬

제외 항목을 MyPage의 예정 섹션이나 후속 계약으로 유지하지 않는다. 별도 기능이 필요해지면 해당 기능 문서와 Issue에서 새 범위를 정의한다.

## 진입과 이탈 경로

### 진입

- 앱의 초기/default destination
- Bottom Navigation의 세 번째 `My Page` 항목
- 다른 root 또는 overlay에서 이전 MyPage root로 복귀

### 이탈

- Favorite Team 선택 → `TeamDetail(teamId)`
- Favorite Player 선택 → `PlayerDetail(playerId)`
- Top App Bar Search 선택 → `Search`
- 다른 Bottom Navigation 항목 선택 → 해당 root destination

Detail 또는 Search에서 Back하면 기존 MyPage scroll, 섹션 상태, ViewModel instance를 보존한 채 복귀한다. 다른 root를 왕복해도 MyPage의 독립 back stack과 entry state를 유지한다.

## 화면과 콘텐츠 계층

1. Shared Top App Bar
   - `MyPage` 제목
   - Search
2. Favorite Teams
3. Favorite Players

Team과 Player는 하나의 목록으로 섞지 않으며 Team 섹션이 항상 먼저 온다. Phone horizontal inset은 16dp, Top App Bar 높이는 56dp, 모든 행과 icon action의 reachable target은 최소 48dp다. 360dp compact width에서도 긴 Team/Player 이름과 한국어 문자열은 행 경계를 넘지 않고 ellipsis로 안전하게 제한한다.

## 표시 데이터

| 영역 | 표시 데이터 |
| --- | --- |
| Favorite Team | 저장된 Team ID, 이름, tag, country, nullable logo `imageUrl` |
| Favorite Player | 저장된 Player ID, handle, real name, country, nullable profile `imageUrl` |

목록은 repository가 제공한 저장 순서를 그대로 사용한다. Detail navigation에는 저장된 stable ID를 전달하며 UI에서 임의 ID를 만들거나 정렬하지 않는다.

#70부터 동일 ID를 다시 저장하면 Team은 최신 logo URL 또는 `null`, Player는 최신 profile URL 또는 `null`로 수렴한다. nullable default를 사용하므로 image field가 없는 기존 DataStore JSON도 `null`로 복원한다. Match 이미지는 저장하지 않으며 이미지 null·blank·load failure는 행 전체 오류로 승격하지 않는다.

## 화면 상태

Team과 Player 관찰은 독립적으로 시작하고 갱신한다.

| 상태 | 동작 |
| --- | --- |
| Loading | 해당 섹션에 로컬 즐겨찾기를 불러오는 진행 상태를 표시한다. 다른 섹션은 자기 상태를 유지한다. |
| Content | 저장 순서대로 favorite 행을 표시한다. |
| Empty | 해당 종류의 즐겨찾기가 없다는 section message를 표시한다. |
| Section Error | 성공한 다른 섹션을 유지하고 실패한 섹션에만 Retry를 제공한다. |
| Initial Full Error | 두 섹션 모두 첫 성공 snapshot 없이 실패한 경우 content 영역 전체에 Retry를 제공한다. Bottom Navigation은 유지한다. |
| Favorite Removal In Progress | 제거할 항목만 optimistic하게 숨긴다. 다른 섹션과 화면 action은 유지한다. |
| Favorite Removal Error | 최신 성공 repository snapshot을 저장 순서 그대로 표시하고, 제거 대상이 그 snapshot에 남아 있을 때만 Retry Snackbar를 표시한다. |

한 섹션이 한 번이라도 성공 snapshot을 받은 뒤 발생한 관찰 실패는 full error로 승격하지 않는다. 전체 Retry는 두 관찰 generation을 함께 교체하고, section Retry는 실패한 종류의 generation만 교체한다. 취소된 이전 generation의 emission은 현재 state에 반영하지 않는다.

## 사용자 인터랙션

### Detail 이동

- Team 행은 저장된 Team ID로 Team Detail을 연다.
- Player 행은 저장된 Player ID로 Player Detail을 연다.
- 행 전체가 하나의 48dp 이상 button target이며 제거 icon은 별도의 접근 가능한 이름을 가진다.

### 즐겨찾기 해제

- 제거 요청 직후 대상 행을 optimistic하게 숨긴다.
- mutation 성공 시 현재 숨김 상태를 유지한 채 해당 favorite 종류의 관찰 generation만 교체한다. 이전 generation의 queued snapshot은 폐기하고, 새 generation의 첫 snapshot을 authoritative state로 적용한다. 같은 ID가 다시 포함되어 있으면 새 즐겨찾기로 간주해 즉시 표시한다.
- mutation 실패 시 최신 성공 repository snapshot을 저장 순서 그대로 표시한다. 대상이 그 snapshot에 남아 있을 때만 `재시도` Snackbar를 제공하며, 이미 사라진 대상은 되살리지 않는다.
- optimistic 제거 중 같은 섹션이 Error 또는 Loading으로 바뀌어도 최신 성공 snapshot을 유지해 rollback에 사용한다. 이 rollback은 다른 종류의 favorite state를 변경하지 않는다.
- 동시에 하나의 제거 mutation만 실행하며 중복 제거/재시도 입력은 무시한다.
- `CancellationException`은 오류로 변환하지 않는다.

## 앱·서버 책임 경계

### 앱

- Team/Player favorite를 기기 로컬 persistence에 저장한다.
- 각 favorite 종류를 독립 Flow로 관찰하고 Domain Model을 UiState에 반영한다.
- loading/content/empty/error, optimistic removal, rollback, retry를 관리한다.
- raw exception을 UiState에 노출하지 않는다.

### 서버

- MyPage 목록을 위한 API나 persistence를 제공하지 않는다.
- Team/Player Detail 데이터는 각 기능의 기존 server contract가 소유한다.

MyPage 자체에는 upstream VLR.GG URL이 없다.

## 검증과 한계

- common ViewModel 테스트는 독립 관찰, 양쪽 generation을 먼저 교체하는 full Retry, cancellation, 저장 순서, optimistic 제거, 최신 관찰 snapshot 기준 실패 rollback race를 검증한다.
- iOS Compose 테스트는 loading, empty, populated, section/full error, removal error, 섹션 순서, navigation round trip을 검증한다.
- 360dp virtual display에서 16dp inset, 56dp Top App Bar, 48dp minimum target, 긴 Team/Player/한국어 문자열의 안전한 layout을 검증한다.
- 현재 저장소에는 golden image 또는 real-device screenshot 비교 infrastructure가 없다. 따라서 자동화 결과는 Compose semantics/geometry 증거이며 pixel-perfect 또는 실기기 시각 검증으로 주장하지 않는다.

## 테스트 가능한 수용 기준

- [x] 앱의 초기 destination은 MyPage다.
- [x] MyPage Top App Bar에서 Search를 열고 Back하면 이전 MyPage 상태가 보존된다.
- [x] Favorite Teams가 Favorite Players보다 먼저 표시된다.
- [x] Team/Player는 저장된 순서와 ID를 유지하고 각각 대응하는 Detail로 이동한다.
- [x] Team/Player loading, empty, content, section error는 서로 독립적이다.
- [x] 두 첫 snapshot이 모두 실패하면 full error와 Retry를 표시한다.
- [x] section Retry와 full Retry는 stale generation emission을 무시한다.
- [x] 즐겨찾기 제거는 대상 행만 optimistic하게 숨긴다.
- [x] 제거 실패는 대상과 sibling의 저장 순서를 복원하고 Retry Snackbar를 제공한다.
- [x] 제거 중 최신 관찰 snapshot이 바뀌거나 관찰 실패가 이어져도 최신 성공 snapshot의 항목과 순서를 기준으로 rollback하며, 이미 사라진 대상은 되살리지 않는다.
- [x] Search, Team/Player Detail, 다른 root 왕복 후 MyPage scroll과 entry state를 보존한다.
- [x] 360dp compact width에서 16dp inset, 56dp Top App Bar, 48dp minimum target을 유지한다.
- [x] 현재 MyPage에는 Team/Player favorite 이외의 개인화 섹션이 없다.
