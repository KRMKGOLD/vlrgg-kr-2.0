# VLR.GG Mobile v3 — Scoreline 디자인 기록

> **Status:** 2026-11-05에 작성한 전면 리뉴얼 제안이다. 토큰, 재사용 컴포넌트, 화면 12계열의 기본·상태 화면 82개, QA 화면 5개를 포함한다. 채택 전까지 현재 앱의 디자인 권한은 루트 [DESIGN.md](../../DESIGN.md)에 있다.

- **편집 원본:** [`pen/vlrgg-mobile-v3.pen`](pen/vlrgg-mobile-v3.pen). Pencil에서 열어 편집하고, 앱에서 저장해야 디스크에 반영된다.
- **이전 시안:** [`pen/vlrgg-mobile-v2.pen`](pen/vlrgg-mobile-v2.pen)과 [v2 기록](v2-redesign.md)·[v2.1 제안](v2.1-design-system.md)은 보관 자료다. v3는 v2의 시각 언어를 이어받지 않았고, 상태 case 목록만 v2.1에서 가져왔다.
- **제품 계약:** 정보 구조, 화면 순서, 필드, 문구, 상태 규칙은 [`docs/feature/`](../feature/README.md)를 따른다. v3는 시각 언어만 새로 정한다.

## 방향

방송 스코어 바에서 가져온 정보 디자인이다. 숫자와 팀이 먼저 읽히고 나머지는 물러난다.

1. **숫자가 먼저 읽힌다.** 점수·시각·약어는 Saira Condensed, 문장과 이름은 Noto Sans KR로 역할을 나눈다.
2. **빨강은 하나의 신호다.** LIVE, 하단 탭 선택, 승자 표시, 주요 버튼, 재시도에만 쓴다. 예정·종료를 색 chip으로 나누지 않고 텍스트로 표시한다.
3. **카드 대신 전폭 섹션을 쓴다.** 흰 섹션과 8dp 캔버스 간격으로 리듬을 만들고, 행은 구분선으로 나눈다. 카드 형태는 마이페이지 팀 타일에만 쓴다.
4. **상세는 어두운 hero로 시작한다.** 상태바·상세 헤더·hero를 한 띠로 묶는다. 로고 slot은 흰 바탕이라 어두운 로고도 보인다.
5. **없는 정보는 만들지 않는다.** 원문 문자열은 그대로 두고, 결측은 `—`, 이미지가 없으면 텍스트 placeholder를 쓴다.

## 토큰

모든 색·서체·크기·간격은 `.pen` 변수로 참조한다. 이름은 버전 접두사 없이 역할로 정했다.

### 색상

| 토큰 | 값 | 역할 | 대비 |
| --- | --- | --- | --- |
| `canvas` | `#F1F2F4` | 앱 배경, 섹션 사이 간격 | — |
| `surface` | `#FFFFFF` | 섹션·행 표면 | — |
| `surface-sunken` | `#F5F6F8` | 로고 slot, 표 머리, 입력창 | — |
| `skeleton` | `#E8EAED` | 로딩 자리 | 비텍스트 |
| `line` / `line-strong` | `#E4E6EA` / `#C9CED6` | 구분선 / 보조 버튼·비활성 입력 테두리 | 비텍스트 |
| `ink` | `#111418` | 제목·본문 | surface 18.47, canvas 16.49 |
| `ink-2` | `#3D434C` | 보조 본문 | surface 9.97 |
| `muted` | `#646B78` | 메타·패자 점수 | surface 5.36, canvas 4.79, sunken 4.96 |
| `accent` | `#CF1F33` | 신호·선택·주요 버튼·재시도 | surface 5.39, canvas 4.81, 흰 글자 5.39 |
| `accent-pressed` / `accent-soft` | `#A8172A` / `#FDECEE` | 누름 / 선택 배경 | — |
| `live` | `#FF3B4B` | LIVE 점 | panel 5.03 |
| `panel` / `panel-2` | `#15191E` / `#232930` | 상세 hero·Snackbar / hero 안 chip·스켈레톤 | — |
| `on-panel` / `on-panel-muted` | `#FFFFFF` / `#A4ABB6` | hero 글자 / hero 보조·패자 | panel 17.65 / 7.63 |
| `on-panel-accent` | `#FF7A86` | Snackbar 액션, 어두운 헤더의 즐겨찾기 ON | panel 7.04 |
| `warn` / `warn-soft` | `#9A5800` / `#FFF4E0` | 연기됨 | 5.11 |
| `scrim` | `#0B0D1066` | 모달 뒤 배경 | — |
| `focus` | `#2F6FED` | 포커스 링 | 비텍스트 |
| `disabled` | `#9AA1AC` | 상태 아이콘·이미지 자리 | 비텍스트 |

### 서체

- **Noto Sans KR:** 한국어 UI, 팀·선수·대회 이름, 기사 제목과 본문. Android 시스템 한글 서체와 같은 계열이라, `.pen`에서는 시스템 서체를 대신 표현하는 용도로 쓴다.
- **Saira Condensed:** 점수, 맵 점수, 통계 수치, 팀 tag placeholder, `LIVE`, `MAP 1` 같은 약어. 좁은 폭에서 큰 숫자를 담기 위해 고른 Google Fonts 서체(OFL)다.
- 크기: display 28, title 24, section 17, body 15, body-sm 14, meta 13, label 12. 최소 글자 크기는 12다.

### 형태·간격·아이콘

- 모서리는 2dp(`radius`)다. 버튼은 10dp(`radius-button`)로 둥글게 하고, Snackbar·다이얼로그(4dp), 앱 아이콘(12dp), 선수 사진 slot(원형)도 예외로 둔다.
- 간격은 4·8·12·16·20·24·32, 화면 좌우 여백은 16dp, 섹션 사이는 8dp다. 터치 영역은 최소 48dp다.
- 아이콘은 [Google Material Symbols Outlined](https://fonts.google.com/icons) 원본을 굵기 400으로 쓴다. 패스 재작성, 라이브러리 혼용, 자체 제작 아이콘은 쓰지 않는다.
- 하단 탭은 `newspaper`, `scoreboard`, `person`, `trophy`, `info`다. 선택 상태는 ink 아이콘, 빨간 2dp 막대, 굵은 라벨로 표시한다.
- Pencil의 Material Symbols 라이브러리는 FILL 축을 지원하지 않는다. 그래서 즐겨찾기 ON 별만 Google Fonts가 배포하는 공식 `star` FILL 1 SVG를 수정 없이 컴포넌트(`C / Icon star · Material Symbols Outlined FILL 1`)로 넣었다.
- 외부 링크는 `arrow_outward`(↗), 앱 안 이동은 `chevron_right`다.

## 컴포넌트

보드 `01 · Components`에 재사용 컴포넌트 33개가 있다. 화면의 반복 요소는 모두 인스턴스이며, 원본을 고치면 모든 화면에 반영된다.

| 묶음 | 컴포넌트 | 규칙 |
| --- | --- | --- |
| 내비게이션 | Status bar, Root header, Detail header, Tabs, Bottom navigation | 루트는 제목 24/900과 검색 48dp. 상세 헤더는 Back과 기능 action만 둔다. 탭 선택은 ink 2dp 밑줄, 하단 탭 선택은 빨간 2dp 막대, ink 아이콘, 굵은 라벨 |
| 목록 | Section header, Match row, News row, Identity row, Link row, Search result row, Team match row, Favorite tile | Match row는 72dp 시간 열과 팀 열을 세로선으로 나눈다. 승자는 ink 700, 패자는 muted 500. 긴 팀 이름은 줄바꿈된다 |
| 상세 | Scoreboard, Entity hero, Map row, Head to head row, Stat header, Stat row | Scoreboard는 로고 위·이름 아래 대칭 구조이며 점수는 중앙. Map row는 hero와 같은 좌우 위치에 점수를 두고 승자 아래에 빨간 막대를 둔다 |
| 컨트롤 | Logo slot, Status chip, Primary button, Secondary button, Search field, Icon star(FILL 1) | Logo slot은 이미지가 없으면 팀 tag나 선수 이니셜, 대회는 trophy 아이콘을 표시한다. 버튼은 높이 48dp, 좌우 여백 24dp, 모서리 10dp다 |
| 상태 | Skeleton row, Skeleton match row, Skeleton hero, State panel, Inline retry, Section message, Snackbar, Error dialog | 아래 상태 규칙을 따른다 |

## 상태 규칙

| 상황 | 표현 | 예 |
| --- | --- | --- |
| 최초 로딩 | 실제 레이아웃과 같은 자리의 스켈레톤. 상세는 어두운 hero 스켈레톤 | 뉴스·경기·대회 목록, 모든 상세 |
| 최초 오류 | State panel: 아이콘, 제목, 안내, `재시도` 버튼 | `뉴스를 불러오지 못했습니다.` |
| 정상 빈 목록 | State panel: 기능 아이콘과 제목만. 버튼 없음 | `표시할 뉴스가 없어요.` |
| 추가 로드 오류 | 기존 목록 끝의 Inline retry | `경기를 더 불러오지 못했습니다.` |
| 섹션 빈 상태 | 섹션 제목을 유지하고 Section message | `예정된 경기가 없습니다` |
| 섹션·탭 오류 | 성공한 다른 콘텐츠를 유지하고 해당 섹션만 Inline retry 또는 탭 영역 State panel | 마이페이지 팀 섹션, 대회 상세 탭 |
| 즐겨찾기 실패 | 별·목록을 이전 상태로 되돌리고 `재시도` Snackbar | `즐겨찾기 해제에 실패했습니다.` |
| 팀·선수 전체 조회 실패 | 스켈레톤 위 Error dialog: `뒤로가기`, `재시도` | `정보를 불러오지 못했습니다` |
| About 링크 실패 | 액션 없는 Snackbar. 같은 행을 다시 눌러 재시도 | `소스 코드를 열 수 없습니다.` |

- 상태 문구는 현재 앱 구현(`app/shared/.../ui/feature/**`)의 문구를 그대로 썼다. v2.1 시안 문구와 다른 곳이 있으면 앱 문구를 따랐다.
- State panel은 아이콘과 문구 사이 16dp, 제목과 안내 사이 6dp, 문구와 버튼 사이 24dp다. 오류 다이얼로그는 본문과 버튼 사이 28dp이며 본문은 어절 단위로 줄을 나눈다.
- 재시도 버튼은 앱과 같이 `재시도`다. 검색 화면만 앱 구현대로 `다시 시도`다.
- 경기 상세 상태 라벨은 앱과 같이 `예정`, `완료됨`, `연기됨`, `취소됨`, `정보 없음`이다. 맵·상대 전적 섹션은 빈 목록(`맵 정보가 없습니다`)과 제공 불가(`이 경기의 맵 정보는 제공되지 않습니다`)를 구분한다.

## 캔버스 구성

방향 보드(`00`)와 컴포넌트 보드(`01`) 아래로 화면 계열마다 한 줄을 쓴다. 각 줄은 제목 라벨, 맨 왼쪽의 기본 화면(`-1`), 오른쪽으로 이어지는 상태 화면 순서다. 프레임 이름은 `{줄}-{순번} · {화면} / {case}`다.

| 줄 | 화면 | 기본(`-1`) | 상태 case | 수 |
| --- | --- | --- | --- | ---: |
| `10` | 뉴스 목록 | 정상 | 로딩, 빈 목록, 초기 오류, 추가 로드 오류 | 5 |
| `11` | 뉴스 상세 | 정상 | 로딩, 본문 없음, 오류, 선택 블록 누락 | 5 |
| `20` | 경기 목록 | 예정·라이브 | 결과, 로딩, 빈 목록, 결과 오류, 추가 로드 오류 | 6 |
| `21` | 경기 상세 | 종료 | 예정, LIVE, 연기, 취소, 로딩, 오류, 부분 정보, 정보 없음 | 9 |
| `30` | 마이페이지 | 정상 | 전체 로딩, 모두 없음, 팀 없음, 선수 없음, 팀 오류, 선수 오류, 전체 오류, 제거 중, 제거 실패, 팀 로딩·선수 표시 | 11 |
| `40` | 대회 목록 | 정상 | 로딩, 빈 목록, 오류, 예정 그룹 없음 | 5 |
| `41` | 대회 상세 | 경기 탭 | 최초 로딩, 최초 오류, 경기 로딩·없음·오류, 뉴스 정상·로딩·없음·오류, 통계 정상·로딩·없음·오류 | 14 |
| `42` | 시리즈 상세 | 전체 | 로딩, 대회 없음, 오류, 예정만, 완료만 | 6 |
| `50` | 팀 상세 | 즐겨찾기 OFF | 즐겨찾기 ON, 로딩, 정보가 적은 경우, 오류 다이얼로그, 추가 실패, 해제 실패 | 7 |
| `51` | 선수 상세 | 즐겨찾기 OFF | 즐겨찾기 ON, 로딩, 정보가 적은 경우, 오류 다이얼로그, 추가 실패, 해제 실패 | 7 |
| `60` | 검색 | 검색 결과 | 처음 진입, 로딩, 결과 없음, 오류 | 5 |
| `70` | 정보 | 정상 | 소스 코드 열기 실패 | 2 |
| `90` | QA | — | 411dp 경기, 411dp 마이페이지, 411dp 대회 통계, 글꼴 130% 뉴스, 긴 팀·대회 이름 | 5 |
| | **합계** | | | **87** |

제품 화면 82개는 v2.1의 case 목록과 같다. 기본 화면이 반영한 주요 계약은 다음과 같다.

- 뉴스 목록: 이미지 없는 구분선 행, 첫 기사는 큰 제목. 게시 시각은 `2026년 9월 19일 오후 2:40` 형식이다.
- 경기 목록: 날짜 헤더 `9월 22일 (화)`와 `한국 시간`을 쓴다. 시간 열에는 예정이면 `오후 8:00`·`1시간 32분 후`를, LIVE와 종료면 상태만 표시한다. 목록에는 팀 로고가 없다.
- 경기 상세: Event 링크, 대칭 hero, `2026년 9월 21일 (월) 오후 6:00 · Bo3`, 경기 설명 원문, 맵, 상대 전적을 둔다. 예정 경기는 `2일 2시간 후 · Bo3`을 추가하고, 알림 벨은 두지 않는다(Stage 2).
- 대회 상세: 응답에 날짜 그룹이 없어 날짜 헤더 없이 표시하고, 문맥은 `event.series` 원문이다. 통계는 `Rounds`, `Rating`, `ACS`, `K-D`, `ADR`, `KAST` 순서이며 선수 셀만 고정한다.
- 팀·선수 상세: 팀 경기는 `statusText`·`scheduledAtText` 원문을 쓰고, 스태프 행은 이동하지 않는다. 선수 요원 기록은 Rating 내림차순, 결측 마지막 규칙을 따르고, 최근 경기는 `승리`/`패배`/`결과 미정`으로 표시한다.
- 정보: 앱 이름·버전·소개, 링크 4행(외부 `↗`, 라이선스 `>`), 테마 안내, 출처·비공식·상표 고지를 계약 원문 그대로 담는다.
- 뉴스 목록의 기사 제목·작성자와 경기 결과 탭의 대진은 v2 당시 API 캡처다. 그 밖의 대진·점수·통계·기사 본문·팀 뉴스 제목은 레이아웃 확인용 샘플이며 실제 기록이 아니다.

### v2.1 case와 달라진 점

- `부분 오류`·`확인 불가`는 앱 표기에 맞춰 `부분 정보`·`정보 없음`으로 이름을 바꿨다. 부분 정보는 재시도 대신 섹션의 제공 불가 안내로 표현한다.
- 마이페이지 빈 상태의 `팀과 선수 찾기` 버튼은 현재 앱에 없어 넣지 않았다. 제거 중은 계약대로 대상만 숨긴다.
- About 링크 실패 Snackbar는 계약대로 액션이 없다.

## Compose 적용 시 참고

채택하면 기존 theme surface를 확장해 적용한다. 아래는 현재 `VlrColors`와의 대응이며 아직 구현하지 않았다.

| v3 토큰 | 현재 `VlrColors` | 비고 |
| --- | --- | --- |
| `surface`, `canvas` | `surface`, `surfaceSubtle` | 캔버스가 앱 배경이 된다 |
| `ink`, `muted`, `line`, `skeleton` | `textPrimary`, `textSecondary`, `outline`, `skeleton` | `ink-2`, `line-strong`, `surface-sunken`은 신규 |
| `accent`, `accent-pressed`, `accent-soft` | `actionPrimary`, `actionPrimaryPressed`, `surfaceSelected` | |
| `live`, `focus`, `scrim` | `accentLive`, `focusOutline`, `scrim` | |
| `panel`, `on-panel` | `inverseSurface`, `inverseOnSurface` | `panel-2`, `on-panel-muted`, `on-panel-accent`는 신규 |
| `warn`, `warn-soft` | `statusPostponed`, `statusPostponedContainer` | 예정·종료 상태 색은 v3에서 텍스트로 대체 |

- Saira Condensed는 숫자용 Latin 서체만 번들하면 된다. 한글은 시스템 서체를 쓴다.
- 아이콘은 Material Symbols Outlined 400(즐겨찾기 ON은 FILL 1)에서 사용하는 글리프만 vector asset으로 가져온다.

## 검증

- Pencil 렌더로 방향 보드, 컴포넌트 보드, 87개 화면을 확인했다. 겹침, 잘림, 줄바꿈 이상을 찾아 고쳤다. LIVE 행 높이, `결과 미정` 줄바꿈, 긴 팀 이름이 겹치던 경기 행 높이가 수정 대상이었다.
- 최상위 프레임 102개(보드 2, 줄 라벨 13, 화면 87)의 bounds를 비교해 서로 겹치는 프레임이 0개임을 확인했다.
- 위 대비 값은 WCAG 상대 휘도 공식으로 계산했다. 본문 텍스트 조합은 모두 4.5:1 이상이다.
- 현재 Pencil(`.pen` 2.21 형식)은 텍스트 `underline` 속성을 저장하지 않는다. 그래서 본문 링크 밑줄은 링크 텍스트를 감싼 프레임의 아래 1px 테두리로 표현했다. 구현에서는 텍스트 밑줄을 쓴다.
- 디자인·문서 작업이라 앱 코드를 변경하지 않았고 Gradle 빌드·테스트는 실행하지 않았다. 캔버스 확인은 실기기 접근성 검증을 대신하지 않는다.

## 다음 단계

- v2.1에 없던 현재 계약 화면을 추가로 그릴지 결정한다: About의 개인정보처리방침·메일 앱 열기 실패 Snackbar, 오픈소스 라이선스 화면(로딩·읽기 실패).
- 채택하면 루트 `DESIGN.md`를 v3 기준으로 갱신하고, Compose theme·컴포넌트 적용은 별도 작업으로 진행한다.
