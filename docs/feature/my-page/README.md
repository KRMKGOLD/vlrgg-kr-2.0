# MyPage

MyPage는 앱의 기본 root이며 기기에 저장한 Team·Player 즐겨찾기를 다시 찾고 해제하는 화면이다. 구현과 자동화 검증은 완료됐고 실기기 시각·접근성 확인은 남아 있다. 공통 즐겨찾기 계약은 [Feature Guide](../README.md#즐겨찾기-계약)를 따른다.

## MVP 범위

- Favorite Teams 주 섹션과 Favorite Players 보조 섹션
- repository 저장 순서를 유지한 독립 목록
- 저장된 nullable Team logo와 Player profile image, 안정적인 placeholder
- stable ID를 사용한 Team/Player Detail 이동
- optimistic 제거, 실패 rollback과 Retry Snackbar
- 두 섹션의 독립 loading·empty·content·error와 초기 full error
- Search, Detail, 다른 root 왕복 뒤 scroll·ViewModel·콘텐츠 상태 복원

Match 목록·즐겨찾기·알림·`Next Matches`, 로그인·프로필·동기화, Team/Player 알림, 폴더·태그·수동 정렬은 제외한다. 예정 섹션으로도 노출하지 않는다. 경기 알림 설정은 MVP 이후 [Stage 2](../README.md#mvp-이후-stage-2-경기-알림)다.

## 화면과 이동

Shared Top App Bar 아래 Favorite Teams, Favorite Players 순서로 표시하고 두 종류를 섞지 않는다. MyPage는 bottom navigation의 세 번째 item이다.

- Team/Player row는 저장된 ID로 대응 Detail을 연다.
- Search는 MyPage 위에 push된다.
- Back이나 root 왕복 뒤 독립 back stack과 entry state를 복원한다.
- 행 전체는 48dp 이상의 target이고 제거 icon은 별도의 접근 가능한 이름을 가진다.
- Phone inset 16dp, Top App Bar 56dp를 사용하고 360dp에서 긴 이름과 한국어 문자열은 안전하게 ellipsis 처리한다.

## 데이터

| 종류 | 저장 데이터 |
| --- | --- |
| Team | ID, 이름, tag, country, nullable logo `imageUrl` |
| Player | ID, handle, real name, country, nullable profile `imageUrl` |

같은 ID를 다시 저장하면 최신 image URL 또는 `null`로 수렴한다. image field가 없는 기존 DataStore JSON은 nullable default로 복원한다. Match 이미지는 저장하지 않으며 이미지 누락·blank·load failure를 행 전체 오류로 올리지 않는다.

## 상태와 경쟁 처리

Team과 Player 관찰은 독립적으로 시작하고 갱신한다.

| 상태 | 동작 |
| --- | --- |
| Loading / Empty / Content | 해당 섹션만 바꾸고 다른 섹션은 유지한다. |
| Section Error | 성공한 다른 섹션과 최신 성공 snapshot을 유지하고 해당 종류만 Retry한다. |
| Initial Full Error | 두 섹션 모두 첫 성공 snapshot 없이 실패하면 전체 Retry를 제공하되 bottom navigation은 유지한다. |
| Removal In Progress | 대상 행만 optimistic하게 숨기고 다른 action은 유지한다. |
| Removal Error | 최신 성공 snapshot과 저장 순서를 복원하고 대상이 아직 있을 때만 Retry Snackbar를 표시한다. |

- full Retry는 두 관찰 generation을 먼저 교체하고 section Retry는 해당 종류만 교체한다. 취소된 generation의 emission은 무시한다.
- 제거 성공 후 해당 종류의 generation을 교체하고 첫 새 snapshot을 authoritative state로 사용한다. 같은 ID가 다시 있으면 새 즐겨찾기로 즉시 표시한다.
- 제거 실패 시 최신 snapshot에서 이미 사라진 대상은 되살리지 않는다. 다른 favorite 종류에는 영향을 주지 않는다.
- 동시에 제거 mutation 하나만 실행하며 중복 제거·재시도 입력은 무시한다. `CancellationException`은 오류로 변환하지 않는다.

MyPage 목록과 persistence는 앱이 소유하며 전용 서버 API는 없다.

## 수용 기준

- [x] MyPage가 기본 destination이고 Team이 Player보다 먼저 표시된다.
- [x] 두 섹션의 상태·Retry와 저장 순서가 독립적이며 stale generation을 무시한다.
- [x] Detail/Search/root 왕복 뒤 scroll과 entry state를 복원한다.
- [x] 제거는 대상만 숨기고 실패 시 최신 snapshot 기준으로 rollback하며 이미 사라진 대상을 복원하지 않는다.
- [x] 360dp에서 inset, app bar, minimum target과 긴 문자열 배치를 자동 검증한다.
- [x] Team·Player 이외의 개인화 섹션을 표시하지 않는다.
- [ ] 실기기 screenshot·접근성 검증을 완료한다.
