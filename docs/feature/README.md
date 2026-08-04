# Feature Guide

## 문서 상태

- Status: Active
- Last reviewed: 2026-08-03
- Product scope: VLR.GG Mobile Tracker 1차 MVP
- Design source: [`../../DESIGN.md`](../../DESIGN.md)
- App architecture: [`../app-arch/app-arch.md`](../app-arch/app-arch.md)
- Server architecture: [`../architecture/server-arch.md`](../architecture/server-arch.md)

## 제품 목표

VLR.GG Mobile Tracker는 VLR.GG의 뉴스, 경기, 이벤트, 시리즈, 팀, 선수 정보를 모바일에서 빠르게 탐색할 수 있도록 재구성하는 개인용 포트폴리오 앱이다.

웹사이트를 그대로 복제하지 않는다. 사용자가 News, Match, Event, Series, Team, Player 사이를 자연스럽게 이동하고, 관심 있는 Team·Player를 MyPage에서 다시 찾으며 경기 시작 알림을 관리할 수 있는 연결형 탐색 경험을 제공한다.

앱과 서버는 다음 책임을 가진다.

- Ktor 서버는 VLR.GG HTML을 요청하고 Jsoup으로 해석해 app-facing response로 가공한다.
- Compose Multiplatform 앱은 서버 API를 통해 데이터를 받고 Android와 iOS에 공통 UI를 제공한다.
- Team·Player 즐겨찾기는 기기 로컬에 저장한다. Team 즐겨찾기가 주 개인화이고 Player 즐겨찾기는 보조다.
- 사용자에게 Match 즐겨찾기 기능/그룹을 제공하지 않는다. Match 벨은 `Upcoming`/`Postponed` Match의 서버 알림 구독만 제어하며 로컬 즐겨찾기를 만들지 않는다.

## 1차 MVP 범위

Phase 1부터 Phase 5까지를 모두 완료해야 1차 MVP가 완성된다.

| Phase | Feature slice | 문서 |
| --- | --- | --- |
| 1 | News List, News Detail, 본문 Team·Player 링크 | [`news/README.md`](news/README.md) |
| 2 | Upcoming/Live Matches, Results | [`matches/README.md`](matches/README.md) |
| 3 | Event List, Event Detail의 Matches·News·기본 Stats | [`events/README.md`](events/README.md) |
| 4 | Search, Team Detail, Player Detail | [`search/README.md`](search/README.md), [`teams/README.md`](teams/README.md), [`players/README.md`](players/README.md) |
| 5 | Match Detail Basic, Series Detail | [`matches/README.md`](matches/README.md), [`series/README.md`](series/README.md) |
| Cross-feature | MyPage, Team·Player 즐겨찾기, Match 알림, About | [`my-page/README.md`](my-page/README.md), [`about/README.md`](about/README.md) |

## 구현 상태 (2026-08-03)

아래 상태는 원격 `main`의 `6d442cd` 기준 구현 범위를 기록한 것이며, 1차 MVP의 제품 범위나 수용 기준을 변경하지 않는다. 서버 공통 기반에는 공개 콘텐츠 route의 런타임 OpenAPI/Swagger 문서가 구현되어 있다.

앱 공통 기반은 feature 구현과 분리해서 판단한다. `DESIGN.md` Step 1의 Light theme, Typography, `VlrButton`, `VlrIconButton`, `VlrSearchField`, `StatusChip`은 `app/shared`에 구현되어 있다. 실제 feature 화면, Navigation 3, Metro DI, remote DTO/Domain/Data 계층, 로컬 즐겨찾기, Match 알림 App 연동은 미구현이며, physical Android/iOS의 시각·접근성 검증도 완료되지 않았다.

| Feature slice | Backend | App |
| --- | --- | --- |
| News | 구현 완료 — 목록·상세 API와 구조화된 본문 parsing | Feature 미구현 — 공통 디자인 시스템 Step 1만 사용 가능 |
| Matches | 콘텐츠 조회 구현 완료 — Upcoming/Results 목록과 Match Detail API | Feature 미구현 — 목록·상세·내비게이션 없음 |
| Events | 구현 완료 — 목록, 상세, Matches, News, Stats API | Feature 미구현 |
| Search | 구현 완료 — Series/Event/Team/Player 결과 API | Feature 미구현 |
| Team Detail | 구현 완료 — Team overview·news를 합친 상세 API | Feature 미구현 |
| Player Detail | 구현 완료 — 기본 정보, 현재 팀, Agent Stats, 최근 경기 API | Feature 미구현 |
| Series Detail | 구현 완료 — Upcoming/Completed Event 그룹 API | Feature 미구현 |
| MyPage, Team·Player 즐겨찾기, About | Backend 기능 없음 | Feature 미구현 |
| Match 알림 | Stage 1.1 server offline GREEN — Firestore Emulator, 익명 Target 권한, START-only, request-bound scheduler | Feature 미구현 — App·실제 Firebase 연동은 `NOT RUN — Stage 2` |

## MVP 제외 범위

- Team·Player 알림 구독
- 알림함과 알림 이력
- 로그인, 사용자 계정, 기기 간 즐겨찾기 동기화
- 공개 서비스 규모의 scraping·polling 최적화
- Push 기반 Team·Player 지속 추적
- 스포일러 숨김
- 포럼, 댓글, Pick’em
- AI 요약
- 고급 검색 필터
- 외부 링크 preview
- Match Detail의 맵별·선수별 고급 통계
- Event 브라켓과 Agent 고급 통계
- Team Transactions 전체 탭과 Ranking History
- Player Recent Matches 전체 페이징
- Dark Mode

## 정보 구조

### Bottom navigation

Bottom navigation은 다음 순서로 고정한다.

1. News
2. Matches
3. MyPage
4. Events
5. About

시각적 순서와 관계없이 앱의 기본 진입 destination은 `MyPage`다. 각 탭은 독립 back stack 확장을 고려하되, 구체적인 다중 back stack 정책은 Navigation 구현 문서에서 정한다.

### Shared Top App Bar와 Search

- 모든 최상위 탭은 title과 Search action을 가진 공통 Top App Bar를 사용한다.
- Detail Top App Bar는 Back과 선택적 title 및 기능별 action만 사용한다. News/Event/Series는 Back-only, Match는 Upcoming/Postponed에만 bell, Team/Player는 star를 사용한다.
- Search는 Bottom navigation item이 아니다.
- Search action을 누르면 현재 화면 위에 별도 Search Screen을 push한다.
- Back을 누르면 직전 탭과 화면 상태로 돌아간다.
- Search 결과는 Series, Event, Team, Player Detail로 이동한다.
- Search는 full-screen push이며 bottom navigation을 표시하지 않는다. 입력은 최대 30자, explicit submit-only이고 blank/symbol-only query는 제출할 수 없다.

### 공통 목록·상태 계약

- News는 thumbnail/card 없는 divider full-row이며 전체 row가 News Detail로 이동한다. Search 결과도 이미지 없는 divider row와 text type label을 사용한다.
- Paginated News/Matches 목록은 initial loading/empty/initial error/pagination error를 구분한다. initial error는 전체 화면 Retry, pagination error는 하단 Retry를 제공한다.
- Paginated News/Matches의 Pull-to-refresh는 기존 데이터를 초기화한 뒤 `page=1`만 요청하고, pagination은 footer spinner·중복 요청 방지·중복 삽입 방지를 보장한다. Events는 기존 `All` 첫 페이지 single-page 계약을 유지하므로 pagination이나 `page=1` 규칙은 적용하지 않지만, Pull-to-refresh에서는 `GET /api/v1/events`의 단일 전체 응답을 다시 불러온다.
- Match Card는 시각 anatomy를 공유하지만 navigation capability는 context별로 분리한다. Matches Root만 stable Team/Event ID가 있을 때 nested target을 제공하고, Event/Team/Player/MyPage Next Matches는 card 전체를 Match Detail로 연결한다.

### 주요 route

아래 경로는 제품 수준의 destination 식별자다. 실제 Navigation 3 key와 deep-link 문자열은 구현 시 앱 아키텍처에 맞게 정의한다.

```text
/news
/news/{newsId}

/matches
/matches/{matchId}

/events
/events/{eventId}

/search

/teams/{teamId}
/players/{playerId}
/series/{seriesId}

/my
/about
```

VLR.GG upstream URL과 앱 route를 혼용하지 않는다. 예를 들어 upstream Match URL은 일반적으로 `/{matchId}/{slug}` 형태지만 앱 destination은 `/matches/{matchId}`로 표현한다.

## Feature 연결 규칙

```text
News Detail ─────────────→ Team Detail
     │                    → Player Detail
     │
Matches ────────────────→ Match Detail
     │                    → Event Detail
     │
Events ─────────────────→ Event Detail
     │                    → Match Detail
     │                    → News Detail
     │
Search ─────────────────→ Series / Event / Team / Player Detail

MyPage ─────────────────→ Favorite Team / Player Detail
     └──────────────────→ planned Next Matches → Match Detail
```

- Event는 Events 탭, Matches의 event context, Search 결과에서 접근할 수 있다.
- Team Detail과 Player Detail은 MVP에서 Event로 직접 이동하는 요소를 제공하지 않는다.
- News Detail의 내부 routing은 1차 MVP에서 Team과 Player 링크만 지원한다.
- Event·Match 내부 링크와 외부 링크 처리는 후속 범위로 남긴다.

## 즐겨찾기 모델

`관심 팀`, `관심 선수`, `관심 유저`는 별도 기능이 아니다. 제품 문서와 UI에서는 `즐겨찾기`를 canonical term으로 사용한다.

### Team·Player

- 기기 로컬 저장소에 저장한다.
- MyPage에서 Team을 주 섹션, Player를 compact 보조 섹션으로 표시한다.
- 알림 기능을 제공하지 않는다.
- 서버 사용자 DB나 계정을 요구하지 않는다.

### Match 알림

- Match Detail의 벨은 `Upcoming`/`Postponed`에서만 노출되는 서버 알림 구독 action이다.
- 벨 ON/OFF는 로컬 즐겨찾기나 MyPage의 Match 즐겨찾기 그룹을 생성·삭제하지 않는다.
- `Live`, `Completed`, `Cancelled`, `Unavailable`, FFW Match에는 벨을 노출하지 않는다.

## Match 알림 공통 계약

- 사용자가 Match Detail에서 `Upcoming` 또는 `Postponed` 경기에만 알림을 직접 설정한다. `Live`, `Completed`, `Cancelled`, `Unavailable`, FFW에는 벨을 노출하지 않는다.
- 이 action은 서버 notification subscription만 변경하며 로컬 즐겨찾기를 생성·삭제하지 않는다.
- Match 알림의 전송 provider는 FCM이다. opaque FCM registration token은 한 익명 Target의 전달 주소이지 사용자 인증, Target 권한, FID나 물리 기기 ID가 아니다.
- 앱 설치 단위의 Target은 서버가 발급한 Target ID/Secret으로 구분한다. 앱 삭제·재설치로 자격을 잃어 새 Target이 생기는 것은 허용하며 이전 Target을 자동 복원·병합하지 않는다.
- 같은 Target의 token refresh는 전달 주소만 교체하고 Match 설정을 보존한다. 서로 다른 Target의 독립 구독과 일시적 중복 전달은 MVP에서 허용한다.
- 같은 Target/Match의 설정과 해제는 각각 alarm ON/OFF로 수렴하며 target-scoped revision으로 늦은 요청이 최신 의도를 되돌리지 않게 한다.
- MyPage의 전역 OFF는 Team/Player favorite를 보존하고 current Target의 Match 구독만 하나의 bounded transaction으로 비활성화한다. 전역 ON은 앱 알림 설정 및 권한 활성화 흐름일 뿐 기존 Match 구독을 일괄 복원하지 않으며, 각 Match는 다시 명시적으로 선택한다.
- 서버는 외부 Scheduler가 전달한 10분 schedule slot마다 활성 구독의 고유 Match ID를 확인한다.
- 서버는 subscription별 경기 START intent를 한 번으로 관리한다. END 알림은 MVP에서 제외한다. 이는 FCM transport나 기기 표시의 exactly-once 보장이 아니다.
- 완료된 경기의 추적과 구독 작업을 종료한다.
- 경기 취소·연기·시간 변경·upstream 누락은 내부 상태로 구분한다.

Target/subscription의 상세 의미는 [Matches 추적 계약](matches/README.md#10분-match-추적-및-알림-contract), current-target OFF는 [Matches 전역 알림 계약](matches/README.md#전역-알림-off), token 동기화·authority·provider-invalid Target 정리와 Stage 1.1/2 경계는 [서버 아키텍처 문서](../architecture/server-arch.md#match-notification-stage-11-offline-gate)가 소유한다.

알림 권한과 앱 설정은 다음 흐름을 따른다.

1. 앱 최초 실행 시 플랫폼이 허용하는 경우 알림 권한을 요청한다.
2. MyPage에서 앱 전역 Match 알림 ON/OFF를 제공한다. ON은 앱 알림 설정 및 권한 활성화 흐름이며 기존 Match 구독을 일괄 복원하지 않고, 각 Match는 다시 명시적으로 선택한다. OFF만 Team/Player favorite를 보존한 채 current Target의 Match 구독을 일괄 비활성화한다.
3. 전역 알림 또는 시스템 권한이 꺼진 상태에서 Match 알림을 요청하면 활성화 안내 dialog를 표시한다.
4. 사용자가 활성화하면 시스템 권한 상태를 확인하고 가능한 경우 권한을 요청한다.
5. 앱 내부에서 다시 요청할 수 없는 상태라면 시스템 앱 설정으로 이동할 수 있는 action을 제공한다.

## 공통 화면 상태

각 feature 문서는 다음 상태 중 해당하는 상태의 구체적인 UI와 재시도 동작을 정의한다.

- Initial loading
- Refreshing
- Loading next page
- Empty
- Filtered empty
- Populated
- Partial data
- Generic error
- Stale data
- Disabled action

서버의 network/parsing failure code는 App Data Layer에서 generic `AppResult.Failure`로 변환한다. 초기 UI는 내부 exception, HTTP status, selector, upstream URL을 직접 해석하거나 노출하지 않는다.

## 데이터와 문서 경계

- Feature 문서는 사용자 경험, 노출 데이터, 상호작용, 상태, 수용 기준을 소유한다.
- Upstream URL과 parser 주의사항은 같은 feature 문서 안에서 별도 section으로 관리한다.
- CSS selector와 HTML traversal 상세는 구현과 parser test가 소유하며 기획 문서에 고정하지 않는다.
- Endpoint path와 성공 JSON field는 feature 구현 계획에서 app/server 요구를 기준으로 확정한다.
- Server Parser 결과는 `SourceModel`, 공개 API는 `Response`, 앱 비즈니스 경계는 Domain Model로 구분한다.

## 공통 완료 기준

- Phase 1~5의 모든 feature 문서에 MVP 포함·제외 범위가 모순 없이 기록되어 있다.
- 모든 화면은 진입/이탈 경로와 loading·empty·error 상태를 정의한다.
- 목록 화면은 pagination 또는 MVP의 명시적인 단일-page 정책을 정의한다.
- Detail 화면은 누락 가능한 데이터의 숨김/대체 표시 정책을 feature 수준에서 정의한다.
- Team·Player 즐겨찾기와 Match 서버 알림 구독의 차이가 모든 관련 문서에서 동일하다.
- Navigation 설명은 Bottom navigation, Search push, Back 복귀 계약과 일치한다.
- UI 결정은 루트 `DESIGN.md`의 Light theme, 접근성, color/token 규칙을 따른다.
- Parser 구현 전 대표 HTML fixture와 필수 parsing assertion이 feature 문서 또는 테스트 계획에 연결된다.

## 알려진 외부 제약

VLR.GG의 현재 이용약관은 자동 scraping과 체계적 data extraction을 제한한다. 이 프로젝트는 개인 사용과 포트폴리오 범위를 전제로 하지만, 이는 upstream permission이 확보되었다는 의미가 아니다. 공개 배포 또는 운영 범위가 바뀌면 데이터 사용 정책과 허용된 획득 방법을 다시 검토한다.
