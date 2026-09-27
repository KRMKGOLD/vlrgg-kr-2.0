# Feature Guide

이 문서는 제품 범위와 여러 기능에 공통으로 적용되는 계약을 정의한다. 기능별 데이터·상태·API·수용 기준은 각 기능 문서가 소유한다. 시각 규칙은 [`DESIGN.md`](../../DESIGN.md), 앱과 서버 구조는 각각 [`app-arch.md`](../app-arch/app-arch.md), [`server-arch.md`](../architecture/server-arch.md)를 따른다.

## 제품과 MVP 범위

VLR.GG Mobile Tracker는 VLR.GG의 News, Match, Event, Series, Team, Player를 모바일에서 연결해 탐색하고, Team·Player 즐겨찾기를 기기에 보관하는 비공식 개인 포트폴리오 앱이다. 서버는 VLR.GG HTML을 요청 시점에 수집·해석해 app-facing response로 만들고, 앱은 이를 Android와 iOS의 공통 UI로 표시한다.

1차 MVP는 Phase 1~5, Cross-feature 기능과 Android/iOS 품질 확인을 포함한다.

| Phase | 기능 | 문서 |
| --- | --- | --- |
| 1 | News List/Detail, 본문 Team·Player 링크 | [`news`](news/README.md) |
| 2 | Upcoming/Live Matches, Results | [`matches`](matches/README.md) |
| 3 | Event List, Event Detail의 Matches·News·Stats | [`events`](events/README.md) |
| 4 | Search, Team Detail, Player Detail | [`search`](search/README.md), [`teams`](teams/README.md), [`players`](players/README.md) |
| 5 | Match Detail Basic, Series Detail | [`matches`](matches/README.md), [`series`](series/README.md) |
| Cross-feature | MyPage, Team·Player 즐겨찾기, About | [`my-page`](my-page/README.md), [`about`](about/README.md) |

현재 MVP slice는 서버와 앱에 구현되어 있다. 자동화 검증 완료와 실기기 검증 완료를 혼동하지 않는다. Android/iOS 실기기 시각·접근성 확인은 남아 있으며, QA #49와 E2E #62를 완료한 것으로 표시하지 않는다.

## MVP 이후 Stage 2: 경기 알림

2026-09-27 결정에 따라 경기 알림 전체는 MVP 이후 Stage 2다. Match Detail 벨, 권한·수신·탭 이동, MyPage 전역 OFF, App Check/FCM/Firestore/Scheduler 실환경 연동과 실기기 검증은 [Epic #76](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/76), #77–#91, #103과 [Stage 2 마일스톤](https://github.com/KRMKGOLD/vlrgg-kr-2.0/milestone/2)에서 관리한다.

- 서버 Stage 1.1의 Target 인증, Firestore Emulator, START-only delivery, request-bound scheduler는 offline 검증이 완료된 기반이다. 앱 연결이나 실제 push 전달 완료를 뜻하지 않는다.
- 현재 서버는 registration token을 전달 주소로 사용한다. FID 전환은 #77의 계획이며 구현 사실로 쓰지 않는다.
- 현재 MVP에는 알림 벨·권한 요청·설정 UI·Match 즐겨찾기를 추가하지 않는다.
- 일반 조회 서버 배포, Crashlytics, 서버 운영 장애 알림 #122는 제품 경기 알림과 별개다.

상세한 Stage 2 제품 계약은 [`matches`](matches/README.md#mvp-이후-stage-2-범위-match-알림), 서버 구현 경계는 [`server-fcm-stage1.md`](../architecture/server-fcm-stage1.md)가 소유한다.

## 공통 탐색 계약

Bottom navigation은 `News`, `Matches`, `MyPage`, `Events`, `About` 다섯 root이며 기본 진입점은 `MyPage`다. 각 root는 독립 back stack과 화면 상태를 유지한다. 다른 root에서 돌아오면 기존 overlay와 entry state를 복원하고, 현재 root를 다시 선택하면 그 root의 overlay를 비운다.

- 모든 root는 title과 Search action이 있는 공통 Top App Bar를 사용한다.
- Search는 bottom item이 아닌 full-screen overlay다. 입력은 최대 30자, 명시적 제출만 허용하고 blank/symbol-only query는 요청하지 않는다.
- Detail은 Back과 기능별 action만 제공한다. MVP에서 Team·Player는 favorite star, 나머지는 Back-only다.
- ViewModel은 navigation stack을 직접 조작하지 않고 Screen callback으로 이동을 요청한다.
- Back은 직전 화면의 로드 데이터, 선택·필터·스크롤 등 해당 기능이 보존하기로 한 상태를 복원한다.

주요 destination은 `/news`, `/matches`, `/events`, `/search`, `/teams/{id}`, `/players/{id}`, `/series/{id}`, `/my`, `/about`이다. 이는 제품 식별자이며 외부 VLR.GG URL이나 아직 없는 deep link 구현을 뜻하지 않는다.

## 공통 상태와 오류 계약

- Loading, 정상 Empty, Error를 구분한다. 선택적 데이터 누락은 기능 문서가 정한 section/row 단위로 처리하고 필수 구조 손상을 Empty로 숨기지 않는다.
- 초기 실패는 전체 Retry, pagination 실패는 기존 목록을 유지한 footer Retry, 독립 탭·섹션 실패는 성공한 다른 콘텐츠를 유지한 local Retry를 사용한다.
- News/Matches refresh는 기존 목록을 비우고 첫 페이지만 다시 요청한다. 진행 중 pagination을 취소하거나 이전 generation 결과를 무시해 stale 삽입을 막는다. 중복 요청과 중복 항목을 허용하지 않는다.
- Events는 단일 `All` 첫 페이지 전체 응답을 다시 불러오며 pagination 규칙을 적용하지 않는다.
- 서버는 일반 조회 실패 때 이전 성공 결과를 반환하지 않는다. 앱이 향후 stale 데이터를 유지한다면 마지막 확인 시각과 갱신 실패를 명시한다.
- UI는 raw exception, HTTP status, selector, upstream URL, credential을 노출하지 않는다. 색상만으로 상태나 링크를 구분하지 않고 interactive target과 접근 가능한 이름을 제공한다.
- `CancellationException`은 실패로 변환하지 않는다.

## 즐겨찾기 계약

Team·Player 즐겨찾기는 계정이나 서버 사용자 DB 없이 기기 로컬에 저장하며 MyPage에서 Team, Player 독립 섹션으로 저장 순서대로 표시한다. Match·News·Event·Series 즐겨찾기는 없다.

- Detail star는 optimistic하게 갱신한다. Add 실패는 OFF, Remove 실패는 ON으로 rollback하고 Retry Snackbar를 제공한다.
- MyPage 제거는 대상만 숨기고 실패 시 최신 성공 snapshot과 저장 순서로 복원한다. stale generation emission과 이미 사라진 대상을 되살리지 않는다.
- 즐겨찾기 mutation은 전체 화면을 막지 않으며 Team·Player 알림 permission이나 서버 subscription을 만들지 않는다.
- nullable image는 최신 URL 또는 `null`로 저장한다. 누락·blank·load failure는 안정적인 placeholder를 사용하며 행 전체 오류로 올리지 않는다.
- MyPage에는 Match 목록, Match 즐겨찾기, 알림 설정, `Next Matches`를 노출하지 않는다.

## 데이터와 서버 경계

- 서버는 `Scraper → Parser → SourceModel → Mapper → Response`, 앱은 remote DTO → Domain Model → UiState 경계를 지킨다.
- ID는 기능별 API가 정의한 canonical String을 그대로 navigation에 사용하며 UI에서 합성하지 않는다.
- nullable/empty는 source가 실제로 제공하지 않는 값에만 사용한다. parser drift와 필수 값 누락은 `SOURCE_PARSING_FAILURE`, upstream 통신 실패는 `UPSTREAM_NETWORK_FAILURE`다.
- public response는 안전한 status/code/message만 제공하고 raw HTML, selector, canonical upstream URL, 내부 예외를 노출하지 않는다.
- 기능 문서는 UX, 필드 의미, 상태·이동·제외·수용 기준을 소유한다. CSS selector와 세부 traversal은 parser와 테스트가 소유한다.

## MVP 제외 범위

- 경기 알림 전체와 Team·Player 알림, 알림함·이력
- 로그인·계정·기기 간 즐겨찾기 동기화
- 공개 서비스 규모의 scraping·polling 최적화
- 스포일러 숨김, 포럼·댓글·Pick'em, AI 요약
- 고급 검색 필터와 외부 링크 preview
- Match 맵별·선수별 고급 통계, Event 브래킷·Agent 고급 통계
- Team Transactions·Ranking History, Player Recent Matches 전체 페이징
- Dark Mode

## 외부 제약

VLR.GG의 현재 이용약관은 자동 scraping과 체계적 data extraction을 제한한다. 개인 사용과 포트폴리오 범위도 upstream permission이 확보되었다는 뜻은 아니다. 공개 서비스나 운영 범위가 바뀌면 데이터 사용 정책과 허용된 획득 방법을 다시 검토한다.
