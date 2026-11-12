# Design System: VLR.GG Mobile v3 — Scoreline

> **코드 일부 적용:** 이 문서와 `.pen`은 v3 디자인 계약이다. 기반(토큰·서체·아이콘)과 chrome·컨트롤·피드백·목록·상세 공통 컴포넌트는 `feat/v3-design`에 적용했고, 화면별 배치는 [적용 순서](#11-적용-순서와-범위-밖)에 따른 후속 작업이다.

- **Status:** Active — v3 채택 (2026-11-05). 기반과 공통 컴포넌트는 `feat/v3-design`에 적용, 화면별 배치는 후속이다([구현 상태](#1-구현-상태) 참조).
- **Design source:** [`docs/design/pen/vlrgg-mobile-v3.pen`](docs/design/pen/vlrgg-mobile-v3.pen) (Pencil). 작업 기록과 캔버스 구성은 [v3 디자인 기록](docs/design/v3-redesign.md)에 있다.
- **Applies to:** `app/shared/src/commonMain`의 Compose Multiplatform UI. Android·iOS 모바일, 라이트 테마.
- **소유 범위:** 이 문서는 시각·컴포넌트·상호작용·접근성 계약을 소유한다. 데이터, 흐름, 화면 문구는 [기능 문서](docs/feature/README.md)가, 배치와 런타임 경계는 [앱 아키텍처](docs/app-arch/app-arch.md)가 소유한다. 기능 문서와 시각 규칙이 충돌하면 이 문서를 따른다.
- **이력:** v3 이전의 디자인 자료(2026-08~10 Stitch 기반 계약, v2·v2.1 Pencil 시안)는 v3로 대체되어 저장소에서 정리했다(2026-11-05). 필요하면 git 이력에서 확인한다.

## 1. 구현 상태

기반(토큰·서체·아이콘, 2026-11-11)과 chrome·컨트롤·피드백·목록·상세 공통 컴포넌트(2026-11-12)는 `feat/v3-design`에 적용했다. 화면별 섹션 순서·간격, 영어 섹션 제목, 한국어 날짜, 로딩 스피너는 화면 단계 작업이라 그 전까지 화면에 v2와 v3가 섞여 보인다.

- 공통 컴포넌트 적용 전에는 화면 하나만 부분적으로 v3 배치로 바꾸지 않는다. 시각 변경은 v3 적용 작업 안에서만 한다.
- 기능 문서의 동작은 v3에서도 바뀌지 않는다. 문구는 루트 제목·하단 탭 라벨만 한국어(`뉴스`·`경기`·`마이페이지`·`대회`·`정보`)로 바꾸며(2026-11-05 결정, 기능 문서 반영), 상태 문구는 현재 앱 구현 문구를 그대로 쓴다.
- 루트 제목·하단 탭 라벨과 대회 상세 탭 라벨(`경기`·`뉴스`·`통계`)은 한국어로 바꿨다(2026-11-12).
- 공통 컴포넌트(`ui/component`): `RootHeader`, `DetailHeader`(Light·Dark), `VlrBottomNavigation`, `VlrTabs`, `VlrButton`(Primary·Secondary·Text), `VlrIconButton`, `StatusChip`, `VlrSearchField`, `StatePanel`, `InlineRetry`, `SectionMessage`, `SectionHeader`, `SkeletonBlock`·`SkeletonRow`·`SkeletonMatchRow`·`SkeletonHero`, `VlrSnackbar`, `VlrErrorDialog`(`BusyRetryDialog`가 사용). 화면의 같은 역할 구현은 이 컴포넌트로 바꿨다.
- 목록·상세 컴포넌트(2026-11-12): `LogoSlot`, `MatchRow`(경기 목록·대회 경기 탭), `NewsRow`(뉴스 목록 첫 기사 `headline`·대회 뉴스·팀 뉴스), `IdentityRow`(대회 목록·시리즈 대회·팀 로스터·스태프·마이페이지 선수·선수 현재 팀), `TeamMatchRow`(팀 예정·최근 경기, 선수 최근 경기), v3 `VlrStatsTable`(선수 요원 기록·대회 통계), `DetailTopBand`·`EntityHero`(팀·선수·대회·시리즈 상세, 로딩은 `SkeletonHero`). `StatusChip`의 v2 `Loss`·`Partial` 변형은 없앴다. 검색 결과 행은 화면 단계다.
- 상단 inset: `RootHeader`·`DetailHeader`·검색 헤더가 상태바 영역을 직접 칠하고, 앱 runtime은 위쪽 inset을 화면에 넘긴다. 어두운 띠가 보이는 동안 `LightStatusBarIconsEffect`가 밝은 상태바 아이콘을 요청하고, 마지막 띠가 사라지면 루트의 어두운 아이콘으로 돌아온다(Android `WindowInsetsController`, iOS 앱 전체 status bar style — `UIViewControllerBasedStatusBarAppearance` 끔).
- 대회 목록·시리즈 행은 그룹·섹션 제목이 상태를 나타낸다. 섞인 상태만 따로 표시한다: 종료 그룹의 중단 대회는 메타 앞에 `중단`(시리즈 `일시 중지`), 시리즈 예정 섹션의 진행 중 대회는 `진행 중` 상태 chip. 대회 상세 hero·탭은 탭 내용과 한 스크롤로 묶여 hero부터 접히고, hero 메타 줄은 최대 3줄이다. 팀 경기 행 제목은 `vs 상대 팀`이다.
- 뉴스 목록·상세(2026-11-12): `.pen` `10`·`11`대로 적용했다. 목록 끝 추가 로딩은 스피너 대신 기사 행 스켈레톤 한 줄(진행 중 semantics)이고, 목록 게시일은 `publishedDate`가 있으면 `2026년 9월 19일`, 없으면 원문이다(서버 field는 미구현). 상세는 밝은 헤더 + `title` 제목 + `작성자 · 날짜 시각` + `article` 본문이며, 본문 이미지는 184dp `surface-sunken` 자리에 그리고 실패하면 `image` 아이콘을 둔다. 본문 없음은 기사 제목 없이 State panel만 둔다. 소제목·인용 블록은 서버 응답에 없어 그리지 않는다.
- 경기 목록·상세(2026-11-12): `.pen` `20`·`21`대로 적용했다. 목록 날짜 헤더는 `한국 시간` 메타를 두고 그룹 사이를 8dp `canvas`로 띄우며, 로딩은 경기 행 스켈레톤(추가 로딩은 한 줄 + 진행 중 semantics)이다. 상세는 Scoreboard hero·Map row·Head to head row를 경기 상세 화면 안에 두었다(다른 화면이 쓰지 않아 `ui/component`로 올리지 않음). 맵 범례는 응답에 팀 tag가 없어 팀 이름을 쓴다. 완료 경기 점수가 null로 오는 서버 `sp-hide` 문제가 남아 있어 그 경우 hero가 `VS`로 보인다.
- 마이페이지(2026-11-12): `.pen` `30`대로 적용했다. 섹션 제목은 `팀`·`선수`와 보이는 개수(제거 중에는 숨긴 대상을 뺀 수)이고, 흰 섹션 사이 8dp `canvas` 아래에 `즐겨찾기는 이 기기에만 저장됩니다.` 안내를 둔다. Favorite tile은 마이페이지만 써서 화면 안에 두었고, 같은 행의 두 타일은 높이를 맞추며 글꼴 확대 시 함께 늘어난다. 섹션 로딩은 스피너 대신 타일·행 스켈레톤(진행 중 semantics)이다. LogoSlot 48dp 자리 표시 글자는 16sp다.
- 한글 어절 줄바꿈: State panel·Error dialog·기사 제목·본문·캡션에 `withKoreanWordBreak()`(locale `ko` + `WordBreak.Phrase`)를 쓴다. Android 13(API 33) 이상에서만 적용되고, 그 아래 Android와 iOS(skiko가 `LineBreak`를 읽지 않음)는 기존 줄바꿈 그대로다.
- 하단 탭은 시스템 navigation inset을 아래에 더하고 `.pen`의 16dp Gesture area를 고정값으로 두지 않는다. 글꼴 확대로 `마이페이지`가 칸을 넘으면 `마이`/`페이지`로 줄바꿈한다.
- 다이얼로그 뒤 배경은 플랫폼 Dialog의 기본 scrim을 쓴다(`scrim` 토큰 값과 다를 수 있다).
- 타이포그래피: `VlrTypeScale`은 [5절](#5-타이포그래피) 역할을 camelCase로 쓴다(`bodySm`, `scoreXl`, `scoreL`, `scoreM`, `stat`, `tag`). 숫자 역할은 `VlrTheme` 안에서 번들 Saira Condensed로 만든다. 굵기 범위가 있는 역할은 가장 낮은 굵기가 기본이고 화면에서 `copy(fontWeight = …)`로 올린다. 이전 이름은 `pageTitle`→`title`, `sectionTitle`→`section`, `body`→`bodySm`, `bodyStrong`→`body`, `label`→`meta`, `labelSmall`→`label`이다.
- 형태·간격: `VlrDimensions`에 `Space5`(20dp), `CornerRadius`(2dp), `ButtonCornerRadius`(10dp), `OverlayCornerRadius`(4dp, Snackbar·다이얼로그), `HeaderHeight`(56dp)를 둔다. 이전 `DefaultCornerRadius`·`CardCornerRadius` 사용처는 `CornerRadius`로, 버튼은 `ButtonCornerRadius`로 옮겼다.
- 서체: Saira Condensed SemiBold·Bold 원본 TTF를 subset 없이 `composeResources/font/`에 번들한다. `Saira`가 OFL Reserved Font Name이라 subset한 수정본은 이름을 바꿔야 하기 때문이다(OFL FAQ 2.6).
- 아이콘: [7절](#7-아이콘) 글리프 24개를 `composeResources/drawable/ic_<glyph>.xml`로 가져왔다. FILL 1 별은 `ic_star_fill.xml`이다.

| v3 토큰 | `VlrColors` | 이전 이름 |
| --- | --- | --- |
| `canvas`, `surface`, `surface-sunken` | `canvas`, `surface`, `surfaceSunken` | `surfaceSubtle`, `surface`, — |
| `ink`, `ink-2`, `muted` | `ink`, `ink2`, `muted` | `textPrimary`·`textBrand`, —, `textSecondary` |
| `line`, `line-strong`, `skeleton` | `line`, `lineStrong`, `skeleton` | `outline`, —, `skeleton` |
| `accent`, `accent-pressed`, `accent-soft` | `accent`, `accentPressed`, `accentSoft` | `actionPrimary`, `actionPrimaryPressed`, `surfaceSelected` |
| `live`, `focus`, `scrim` | `live`, `focus`, `scrim` | `accentLive`, `focusOutline`, `scrim` |
| `panel`, `panel-2` | `panel`, `panel2` | `inverseSurface`, — |
| `on-panel`, `on-panel-muted`, `on-panel-accent` | `onPanel`, `onPanelMuted`, `onPanelAccent` | `inverseOnSurface`·`onActionPrimary`, —, `inversePrimary` |
| `warn`, `warn-soft` | `warn`, `warnSoft` | `statusPostponed`, `statusPostponedContainer` |
| `disabled` | `disabled` | — |
| — | `statusUpcoming*`, `statusCompleted*`, `statusCancelled` | v3에서 상태는 텍스트로 표시한다. 해당 화면 적용 단계에서 제거한다 |

## 2. 화면 계약

- **검토 프레임:** 기본 360×780dp. 긴 상세 화면은 전체 길이로 그리고, QA는 411×914dp·글꼴 130%·긴 이름으로 확인한다. 실제 Compose 레이아웃은 반응형이며 플랫폼 safe area를 따르고, 고정 chrome 사이의 콘텐츠만 스크롤한다.
- **루트:** 하단 탭 5개 `뉴스`, `경기`, `마이페이지`, `대회`, `정보`. 기본 진입은 마이페이지다. 모든 루트는 제목과 검색 action을 가진다. 상세와 검색은 하단 탭 없이 push한다.
- **상세 상단:** 경기·대회·시리즈·팀·선수 상세는 상태바·상세 헤더·hero를 하나의 어두운 `panel` 띠로 묶는다. 뉴스 상세와 검색은 밝은 헤더를 쓴다.
- **상세 action:** Back과 기능에 필요한 action만 둔다. 팀·선수는 즐겨찾기 별, 나머지는 Back만 둔다. 경기 알림 벨은 MVP 이후 Stage 2이며 v3 MVP 화면에 그리지 않는다.
- **검색:** 전체 화면 push, 명시적 제출만, 최대 30자. 빈 값이나 기호만 있는 검색어는 제출하지 않는다. 결과는 이미지 없는 구분선 행과 텍스트 타입 라벨을 쓰고 행 전체가 상세로 이동한다.
- **뉴스:** 썸네일·카드 없는 구분선 행이며 행 전체가 뉴스 상세를 연다. 첫 기사는 큰 제목으로 표시할 수 있다.
- **경기 행:** 시간·상태 → 두 팀과 점수 → 대회 문맥 순서다. 경기 루트는 안정 ID가 있을 때만 팀·대회 중첩 target을 둘 수 있다. 대회·팀·선수 화면의 경기 행은 경기 상세만 연다. 대회 상세도 경기 목록과 같은 날짜 헤더로 묶고, 문맥으로 `event.series`를 표시한다.
- **경기 상세:** 대회 링크 → 대칭 hero(로고 위·이름 아래, 점수·상태 중앙) → 일정·Bo → 경기 설명 → 맵 → 상대 전적 순서다. 서버 `pastMatches`는 표시하지 않는다.
- **마이페이지:** 팀 즐겨찾기는 2열 타일, 선수 즐겨찾기는 행으로 저장 순서대로 표시한다. 경기 즐겨찾기, 알림 설정, Next Matches는 두지 않는다.
- **대회 상세:** `경기`(기본)·`뉴스`·`통계` 탭이며 탭별 상태·스크롤을 보존한다. 통계는 선수 셀을 고정하고 `Rounds`·`Rating`·`ACS`·`K-D`·`ADR`·`KAST` 순서로 가로 스크롤하며, 선수 셀만 선수 상세로 이동한다.
- **팀·선수·시리즈:** 섹션 단위 빈 안내를 쓰고 generic Partial 화면을 만들지 않는다. 팀·선수 전체 조회 실패만 모달 오류로 표시한다.
- **정보:** 앱 정체성 → 프로젝트 링크 4행 → 화면 테마 → 출처와 고지 순서의 평평한 섹션이다. 외부 링크는 `↗`, 앱 안 이동은 chevron이다.

## 3. 원칙

1. **숫자가 먼저 읽힌다.** 점수·시각·약어는 Saira Condensed, 문장과 이름은 한글 시스템 서체로 역할을 나눈다.
2. **빨강은 하나의 신호다.** `accent`는 LIVE, `진행 중` chip, 하단 탭 선택, 승자 표시(`승리` 라벨 포함), 즐겨찾기 ON 별, 주요 버튼, 재시도, 본문 링크에만 쓴다. 어두운 `panel` 위의 빨강 글자·아이콘(Snackbar 액션, 즐겨찾기 ON 별)은 `on-panel-accent`를 쓰고, 채운 chip(`LIVE`, `진행 중`)은 `accent` 배경에 흰 글자를 그대로 쓴다. 일반 텍스트·배경·상태 구분에는 쓰지 않는다.
3. **카드 대신 전폭 섹션을 쓴다.** 흰 섹션 사이를 8dp 캔버스로 띄우고 행은 1dp 구분선으로 나눈다. 카드 형태는 마이페이지 팀 타일에만 쓴다. 그림자는 Snackbar와 다이얼로그에만 쓴다.
4. **상세는 어두운 hero로 시작한다.** 로고·사진은 흰 slot 위에 두어 원본 색을 지킨다.
5. **없는 정보는 만들지 않는다.** 이름·제목 원문을 바꾸지 않고(날짜·시각은 한국어로 포맷한다, [5절](#5-타이포그래피)), 결측은 `—`, 실제 0은 `0`, 이미지가 없으면 텍스트 placeholder를 쓴다. 장식용 그래픽, 그라디언트, 발광 효과를 쓰지 않는다. 표의 가로 스크롤 흐림만 예외다.

## 4. 색상 토큰

기능 코드에는 raw hex를 쓰지 않고 semantic token만 쓴다. 라이트 전용이며 다크 모드는 아직 설계 대상이 아니다. 대비는 WCAG 상대 휘도 기준 계산값이다.

| 토큰 | 값 | 역할 | 대비 |
| --- | --- | --- | --- |
| `canvas` | `#F1F2F4` | 앱 배경, 섹션 사이 간격 | — |
| `surface` | `#FFFFFF` | 섹션·행·헤더·하단 탭 표면 | — |
| `surface-sunken` | `#F5F6F8` | 로고 slot, 표 머리, 검색창, 버전 chip | — |
| `skeleton` | `#E8EAED` | 로딩 자리 | 비텍스트 |
| `line` | `#E4E6EA` | 1dp 구분선, 타일·slot 테두리 | 비텍스트 |
| `line-strong` | `#C9CED6` | 보조 버튼 테두리, 비활성 입력 테두리 | 비텍스트 |
| `ink` | `#111418` | 제목·본문·승자·선택 | surface 18.47, canvas 16.49 |
| `ink-2` | `#3D434C` | 보조 본문, 고지문 | surface 9.97 |
| `muted` | `#646B78` | 메타, 패자, 비선택 탭 | surface 5.36, canvas 4.79, sunken 4.96 |
| `accent` | `#CF1F33` | LIVE·`진행 중` chip·탭 선택·승자(`승리` 라벨)·즐겨찾기 ON 별·주요 버튼·재시도·본문 링크 | surface 5.39, canvas 4.81, 흰 글자 5.39 |
| `accent-pressed` | `#A8172A` | 주요 버튼 누름(기본 ripple로 부족할 때만, [8.4](#84-컨트롤)) | — |
| `accent-soft` | `#FDECEE` | 선택 배경이 필요할 때 | accent 4.72 |
| `live` | `#FF3B4B` | LIVE 점(장식 신호, 항상 `LIVE` 글자와 함께) | panel 5.03 |
| `panel` | `#15191E` | 상세 상단 띠, Snackbar | — |
| `panel-2` | `#232930` | panel 위 chip, hero 스켈레톤 | — |
| `on-panel` | `#FFFFFF` | panel 위 글자·아이콘, 승자 점수 | panel 17.65 |
| `on-panel-muted` | `#A4ABB6` | panel 위 보조 글자, 패자 점수 | panel 7.63, panel-2 6.34 |
| `on-panel-accent` | `#FF7A86` | Snackbar 액션, 어두운 헤더의 즐겨찾기 ON | panel 7.04 |
| `warn` / `warn-soft` | `#9A5800` / `#FFF4E0` | `연기됨` 라벨 | 5.11 |
| `focus` | `#2F6FED` | 키보드·접근성 포커스 링(플랫폼 기본 표시가 보이지 않을 때) | 비텍스트 |
| `disabled` | `#9AA1AC` | 상태 아이콘, 이미지 자리 아이콘 | 비텍스트 전용 |
| `scrim` | `#0B0D10` 40% | 모달 뒤 배경 | — |

## 5. 타이포그래피

- **한글·문장·이름:** 플랫폼 시스템 sans-serif를 쓴다(Android Noto Sans CJK, iOS Apple SD Gothic Neo). `.pen`은 Noto Sans KR로 대리 표현한다. 한글 서체는 번들하지 않는다.
- **숫자·약어:** Saira Condensed(Google Fonts, OFL)를 번들한다. 점수, 맵 점수, 통계 수치, 팀 tag placeholder, `LIVE`, `MAP 1`, `Bo3` 계열에만 쓴다. 오픈소스 라이선스 화면에 수동으로 등록한다.
- 공식 팀·선수·대회·맵·지역 이름과 기사 원제는 원문 그대로 둔다. UI 라벨은 한국어다.
- 날짜·시각은 모든 화면에서 한국어로 표시한다(2026-11-10 결정). 형식은 기능 문서의 [앱 표시 규칙](docs/feature/matches/README.md#앱-표시-규칙)을 따른다. 서버가 주는 구조화 값(`date`·`time`·instant와 2026-11-10 계약의 날짜 field)으로 포맷하고, 그 값이 없거나 파싱에 실패할 때만 원문을 대신 표시한다.
  - 기사 날짜: `2026년 9월 19일`(뉴스 목록·팀 뉴스·대회 뉴스). 시각까지는 뉴스 상세에서만 쓴다.
  - 경기 날짜·시각: `9월 24일 오후 6:00`(팀 경기), `7월 12일`(선수 최근 경기). 기기 날짜와 연도가 다를 때만 연도를 붙인다.
  - 대회 기간: `9월 12일 ~ 10월 4일`, 같은 달 `11월 20일 ~ 23일`, 하루 `12월 17일`, 끝 미정 `11월 1일 ~ 미정`.
  - 남은 시간: `18시간 5분 후`. 한글이 들어가므로 Saira가 아니라 시스템 서체를 쓴다.

| 역할 | 크기 / 줄 높이 | 굵기 | 서체 | 용도 |
| --- | --- | --- | --- | --- |
| `display` | 28 / 34 | 900 | 시스템 | 상세 hero 이름 |
| `title` | 24 / 31 | 900 | 시스템 | 루트 제목, 기사 제목 |
| `headline` | 20 / 27 | 800 | 시스템 | 뉴스 첫 기사 |
| `section` | 17 / 24 | 800 | 시스템 | 섹션 제목 |
| `body` | 15 / 22 | 600–700 | 시스템 | 팀·선수·대회 이름, 행 제목 |
| `article` | 16 / 27 | 400 | 시스템 | 기사 본문 |
| `body-sm` | 14 / 20 | 400–600 | 시스템 | 다이얼로그 본문, Snackbar, 소개 |
| `meta` | 13 / 19 | 400–700 | 시스템 | 시각, 메타, 안내 |
| `label` | 12 / 16 | 500–700 | 시스템 | 작성자·날짜, 하단 탭 라벨, 타입 라벨 |
| `score-xl` | 52 | 700 | Saira | 경기 상세 점수 |
| `score-l` | 26 | 700 | Saira | 맵 점수 |
| `score-m` | 20 / 22 | 700 | Saira | 경기 행 점수, 팀 경기 결과 |
| `stat` | 16 | 600 | Saira | 통계 수치 |
| `tag` | 12–14 | 600–700 | Saira | `LIVE`, `MAP 1`, 팀 tag, 표 머리 |

- 최소 글자 크기는 12다. 시스템 글꼴 크기 확대를 따르고, 행 높이를 고정하지 않는다. 긴 팀 이름과 대회 문맥은 줄바꿈하며 경기 정체성은 말줄임하지 않는다.
- 한글 문장은 가능한 플랫폼에서 어절 단위로 줄바꿈한다.

## 6. 형태·간격·깊이

| 항목 | 값 |
| --- | --- |
| 기본 모서리 `radius` | 2dp — chip, 로고 slot, 검색창, 타일, 스켈레톤 |
| 버튼 모서리 `radius-button` | 10dp — 주요·보조 버튼 |
| Snackbar·다이얼로그 | 4dp |
| 선수 사진 slot | 원형 |
| 간격 | 4 · 8 · 12 · 16 · 20 · 24 · 32dp |
| 화면 좌우 여백 | 16dp |
| 섹션 사이 | 8dp `canvas` |
| 최소 터치 영역 | 48dp |
| 헤더 높이 | 56dp |

- 표면은 평평하다. 1dp 구분선으로 나누고, 그림자는 Snackbar(y4·blur16)와 다이얼로그(y8·blur32)에만 쓴다.
- 로딩 스켈레톤은 최종 행의 자리와 크기를 지킨다. 헤더와 하단 탭은 움직이지 않는다.

## 7. 아이콘

- [Google Material Symbols Outlined](https://fonts.google.com/icons) 원본을 굵기 400으로 쓴다. 패스 재작성, 라이브러리 혼용, 자체 제작 아이콘을 쓰지 않는다. 사용하는 글리프만 vector asset으로 가져온다.
- 즐겨찾기 ON만 같은 사이트의 공식 `star` FILL 1을 쓴다. 다른 선택 상태는 선 아이콘에 색·막대·굵기를 함께 쓴다.
- 크기: 기본 24dp, 행 trailing 20dp, 보조 16–18dp, 상태 패널 40dp. 아이콘 버튼은 48dp 영역을 갖는다.

| 용도 | 아이콘 |
| --- | --- |
| 하단 탭 | `newspaper`, `scoreboard`, `person`, `trophy`, `info` |
| 헤더 | `search`, `arrow_back`, `star` / `star` FILL 1 |
| 이동 | `chevron_right`(앱 안), `arrow_outward`(외부) |
| 검색창 | `search`, `cancel`(지우기) |
| 정보 링크 | `code`, `privacy_tip`, `mail`, `description` |
| 상태 | `error`(오류), `do_not_disturb_on`(섹션 빈 상태), `progress_activity`(검색 중), `info`(안내) |
| 빈 목록 | 기능 아이콘: `newspaper`(뉴스), `scoreboard`(경기), `trophy`(대회·시리즈), `search`(검색), `description`(기사 본문 없음), `bar_chart`(통계 없음) |
| 표 | `arrow_downward`·`arrow_upward`(내림차순·오름차순 정렬), `swap_horiz`(가로 스크롤 안내) |
| 자리 표시 | `trophy`(대회·시리즈 로고 없음), `image`(기사 이미지 로딩 실패) |

## 8. 컴포넌트

`.pen`의 `01 · Components` 보드에 원본이 있다. 디자인 컴포넌트 수를 코드 abstraction 수로 옮기지 않으며, 두 feature 이상이 같은 계약을 쓸 때 `ui/component`로 올린다([UI 계층 규칙](docs/app-arch/ui-layer.md)).

### 8.1 앱 chrome

- **Root header:** 56dp, 좌우 16/4dp. 제목 `title`(letter spacing −0.5), 오른쪽 검색 48dp.
- **Detail header:** 56dp. Back 48dp, 선택적 제목(밝은 변형만, 16/700), 기능 action 48dp. 어두운 변형은 `panel` 배경과 `on-panel` 아이콘을 쓰고 제목을 두지 않는다.
- **Bottom navigation:** `surface`와 위쪽 1dp `line`, 항목 5개는 폭을 균등하게 나눈다. 항목은 위쪽 24×2dp 선택 막대, 24dp 아이콘, 12dp 라벨이다. 선택 항목은 `accent` 막대와 `ink` 아이콘, 700 라벨이고, 비선택 항목은 `muted`·500이다. 라벨은 항상 보이며 말줄임하지 않는다. 기본 크기에서는 모든 라벨이 한 줄이다. 글꼴 확대로 라벨이 칸(360dp에서 72dp)을 넘으면 의미 단위(예: `마이`/`페이지`)에서 최대 2줄로 줄바꿈하고 하단 탭 높이를 늘린다.
- **Tabs:** 48dp 높이, 균등 폭, 아래 1dp `line`. 선택은 `ink` 700 라벨과 2dp `ink` 밑줄, 비선택은 `muted` 500이다.

### 8.2 목록

- **Section header:** 위 20·아래 8·좌우 16dp. 제목 `section`, 선택적 개수(Saira 17/600 `muted`), 오른쪽 선택적 메타(`label`, 예: `한국 시간`, `전체 기간`, `최대 5경기`).
- **Match row:** 위아래 12·좌우 16dp, 아래 1dp 구분선. 왼쪽 72dp 시간 열과 오른쪽 팀 열 사이에 1dp 세로선을 둔다.
  - 시간 열: 예정은 `오후 8:00`(13/700) + `1시간 32분 후`(12/500 `muted`)를 쓴다. LIVE는 6dp `live` 점과 `LIVE`(Saira 14/700 `accent`)이고, 종료는 시각(13/700) + `종료`(12/500 `muted`)다. 경기 목록 결과 탭과 대회 상세가 같은 표기를 쓴다.
  - 팀 열: 팀 이름 `body` 700과 점수 `score-m`. 종료 경기의 승자는 `ink` 700, 패자는 `muted` 500이다. 예정 경기에는 점수를 두지 않는다. 대회 문맥은 12 `muted`다.
  - 목록에는 팀 로고가 없다(목록 응답에 이미지가 없음).
- **News row:** 위아래 14·좌우 16dp. 제목 15/600(줄 높이 1.45), 아래 `작성자 · 게시일` 12 `muted`. 목록의 게시일은 날짜만 `2026년 9월 19일`로 쓰고, 시각까지 쓰는 형식은 뉴스 상세에만 쓴다. 첫 기사는 `headline`과 위아래 20/18dp 여백을 쓴다.
- **Identity row:** 로고 slot 40dp, 제목 `body` 700, 메타 13 `muted`, trailing 48dp(chevron, 즐겨찾기 해제 별, 또는 없음). 이동하지 않는 행(스태프)은 trailing을 두지 않는다.
- **Link row:** 선두 아이콘 22dp `ink-2`, 제목 15/600, 보조 12 `muted`, trailing `arrow_outward` 또는 `chevron_right`. 최소 48dp이며 button role을 갖는다.
- **Search result row:** 타입 라벨 32dp 열(12/700 `muted`: `팀`·`선수`·`대회`·`시리즈`), 제목·메타, chevron. 이미지를 두지 않는다.
- **Team match row:** 팀·선수 상세용 행이다. 선택적 결과 열 60dp(`승리` `accent` 800, `패배`·`결과 미정` `muted`), 제목(줄바꿈 허용)과 대회 문맥, 오른쪽에 상태와 날짜·시각(12 `muted`)을 둔다. 상태는 점수면 Saira 20, 예정 경기의 남은 시간(`18시간 5분 후`)이면 시스템 서체 13/700이다.
- **Favorite tile:** 마이페이지 팀용 2열 타일, 간격 8dp. `surface`에 1dp `line` 테두리, 2dp 모서리. 로고 slot 48dp, 즐겨찾기 해제 별(FILL, `accent`, 48dp 영역), 이름 15/800, 메타 12 `muted`.

### 8.3 상세

- **Scoreboard:** 경기 상세 hero다. 대회 링크(13/600 `on-panel-muted` + chevron), 좌우 100dp 팀 열(로고 slot 56dp, 이름 14/700), 중앙 점수 `score-xl`(승자 `on-panel`, 패자 `on-panel-muted`)과 상태 chip, 아래 일정 줄로 구성한다.
  - 예정·연기·취소·정보 없음은 점수 대신 `VS`(Saira 36 `on-panel-muted`)를 둔다.
  - 일정은 `2026년 9월 21일 (월) 오후 6:00 · Bo3` 형식이다. 예정 경기는 `2일 2시간 후 · Bo3`을 한 줄 더 둔다.
- **Entity hero:** 팀·선수·대회·시리즈 상단이다. 로고 slot 72dp(선수는 원형), 이름 `display` `on-panel`, 메타 13 `on-panel-muted`. 대회는 상태 chip을 둘 수 있다.
- **Logo slot:** 이미지가 있으면 팀·대회 로고는 Fit, 선수 사진은 Crop으로 채운다. 없거나 실패하면 팀 tag 또는 이니셜(Saira 700 `muted`)을, 대회·시리즈는 `trophy`를 표시한다. 밝은 배경에서는 `surface-sunken` + 1dp `line`, 어두운 hero에서는 `surface`를 쓴다. 반복되는 로고는 낭독하지 않는다.
- **Map row:** 홈 점수 열 56dp, 중앙 `MAP n`(Saira 12/600 `muted`)과 맵 이름(15/700), 원정 점수 열 56dp. 승자 점수는 `ink`이고 아래에 16×2dp `accent` 막대를 둔다. 패자는 `muted`다. 목록 위에 팀 tag 범례를 둔다. 진행 중 맵은 승자 표시 없이 점수만 둔다.
- **Head to head row:** 팀 A(오른쪽 정렬) · 점수(Saira 18) · 팀 B, 행 전체가 경기 상세로 이동한다. 승자는 `ink` 700, 패자는 `muted` 500이다.
- **Stats table:** 선수 요원 기록과 대회 통계가 같은 표를 쓴다. 섹션 안 전폭으로 두고 외곽 테두리를 두지 않는다.
  - 머리 행 40dp(`surface-sunken`, Saira 13/700 `muted`), 본문 행 52dp(수치 `stat`, 오른쪽 정렬)다. 고정 정체성 열 120dp에는 오른쪽 1dp 구분선을 둔다.
  - 정렬된 열은 `ink` 라벨과 `arrow_downward`/`arrow_upward`로 방향을 표시한다. 머리는 48dp 이상의 target을 갖는다.
  - 오른쪽 끝 56dp 흐림과 `옆으로 밀어 지표 더 보기 · — 는 기록 없음` 안내로 가로 스크롤을 알린다. 결측 `—`는 `muted`다.

### 8.4 컨트롤

| 종류 | 시각 | 용도 |
| --- | --- | --- |
| Primary button | `accent` 배경, 흰 글자 15/700, 높이 48dp, 좌우 24dp, 모서리 10dp | 상태 패널의 `재시도`·`다시 시도` |
| Secondary button | `surface` 배경, 1dp `line-strong`, `ink` 글자, 같은 크기 | 주요 action이 이미 있을 때 |
| Text button | 투명, 14–15/700, 높이 48dp, 좌우 12dp | Inline retry, 다이얼로그, Snackbar |

| 상태 | 버튼 동작 |
| --- | --- |
| Pressed | 별도 시안 없이 Compose 기본 indication(ripple)을 쓴다 |
| Focus | 플랫폼 기본 포커스 표시를 쓴다. 보이지 않으면 2dp `focus` 링을 바깥에 둔다. 색 변화만으로 표시하지 않는다 |
| Disabled | 이유가 분명할 때만 흐리게(50% 불투명도) 표시한다. 아니면 활성 상태로 두고 근처에 이유를 쓴다 |
| Loading | 폭을 유지하고 반복 실행을 막으며 진행 표시와 busy semantics를 준다 |

- 버튼 상태는 대략적인 기준만 둔다(2026-11-10 결정). ripple 색이나 흐림 정도 같은 세부 표현은 실제 기기에서 써 보고 불편하면 그때 조정하고 이 표를 갱신한다.

- **Search field:** 높이 48dp, `surface-sunken`, 2dp 모서리, 선두 `search` 20dp `muted`. 지우기 `cancel`은 값이 있을 때만 48dp 영역으로 둔다(접근성 이름 `검색어 지우기`). 포커스는 2dp `ink` 테두리, 제출 후 비포커스는 1dp `line-strong`이다. placeholder는 `팀, 선수, 대회 검색…`이다.
- **Status chip:** 위아래 2·좌우 6dp, 2dp 모서리, 정보 전용이다.
  - `LIVE`: `accent` + 흰 Saira 13/700
  - `진행 중`: `accent` + 흰 12
  - `예정`·`완료됨`·`취소됨`·`정보 없음`: 어두운 배경에서 `panel-2` + `on-panel`/`on-panel-muted`
  - `연기됨`: `warn-soft` + `warn`
  - 버전 표시: `surface-sunken` + `ink-2`

### 8.5 피드백

- **Skeleton:** `skeleton` 막대로 최종 레이아웃 자리를 표시한다(목록 행, 경기 행, 어두운 hero는 `panel-2`).
- **State panel:** 위아래 56·좌우 32dp, 중앙 정렬. 아이콘 40dp `disabled`, 제목 16/700, 안내 13 `muted`(제목과 6dp)로 구성한다. 아이콘과 문구 사이 16dp, 문구와 버튼 사이 24dp다. 버튼은 재시도가 의미 있을 때만 둔다.
- **Inline retry:** `error` 18dp `muted`, 문구 13 `ink-2`, 오른쪽 `재시도` Text button(`accent`). 목록 끝이나 섹션 안에 둔다.
- **Section message:** `do_not_disturb_on` 16dp `disabled`와 13 `muted` 문구. 섹션 제목 아래에 둔다.
- **Snackbar:** `panel`, 4dp 모서리, 좌우 16dp 여백, 하단 탭 위 16dp. 문구 14 `on-panel`, 액션 14/700 `on-panel-accent`(48dp). 정보 링크 실패는 액션을 두지 않는다.
- **Error dialog:** `surface`, 4dp 모서리, 폭 296dp, 안쪽 24dp. 제목 18/800, 본문 14 `ink-2`, 본문과 버튼 사이 28dp. 오른쪽 정렬 `뒤로가기`(`ink-2`)·`재시도`(`accent`). 뒤에 `scrim`을 둔다. 포커스를 가두고, 닫히면 포커스를 되돌린다.

## 9. 상태 규칙

| 상황 | 표현 |
| --- | --- |
| 최초 로딩 | 레이아웃과 같은 자리의 스켈레톤. 경기·대회·시리즈·팀·선수 상세는 어두운 hero 스켈레톤, 뉴스 상세는 밝은 헤더와 본문 스켈레톤 |
| 최초 오류 | State panel + `재시도`(검색은 `다시 시도`) |
| 정상 빈 목록 | State panel(기능 아이콘과 제목만, 버튼 없음) |
| 추가 로드 오류 | 기존 목록을 유지하고 끝에 Inline retry |
| 섹션 빈 상태 | 섹션 제목 유지 + Section message |
| 섹션·탭 오류 | 성공한 콘텐츠를 유지하고 해당 섹션은 Inline retry, 탭은 탭 영역 State panel |
| 즐겨찾기 실패 | 별·목록을 이전 상태로 되돌리고 `재시도` Snackbar |
| 마이페이지 제거 중 | 대상만 숨긴다 |
| 팀·선수 전체 조회 실패 | 스켈레톤 위 Error dialog(`뒤로가기`·`재시도`) |
| 경기 상세 제공 불가 | 섹션별로 빈 목록(`맵 정보가 없습니다`)과 제공 불가(`이 경기의 맵 정보는 제공되지 않습니다`)를 구분한다 |
| 정보 링크 실패 | 액션 없는 Snackbar. 같은 행을 다시 눌러 재시도 |

- 상태 문구는 기능 문서와 앱 구현 문구를 따른다. 오류 원인(HTTP status, 예외, URL, selector)을 노출하지 않는다.
- 새로고침·진행 중 동작은 기능 문서 계약을 따른다. 예를 들어 뉴스·경기 새로고침은 기존 목록을 비우고 첫 페이지를 다시 요청하므로 최초 로딩 표현을 쓴다([공통 상태와 오류 계약](docs/feature/README.md#공통-상태와-오류-계약)).

## 10. 접근성과 상호작용

- 모든 상호작용 요소는 48dp 이상의 target, 접근 가능한 이름, 보이는 포커스 순서, safe area 배치를 갖는다.
- 상태를 색만으로 전달하지 않는다. 각 상태의 비색상 단서는 다음과 같다.
  - LIVE: `LIVE` 글자
  - 승자: 굵기와 막대
  - 하단 탭 선택: 막대와 굵기
  - 즐겨찾기 ON: 채운 별
  - 본문 링크: 밑줄
- 본문 텍스트는 4.5:1, 큰 텍스트는 3:1 이상이다(WCAG 2.2 AA 의도). `line`·`line-strong` 구분선과 테두리(1.25–1.58:1)는 장식이며, 컨트롤 식별을 테두리에만 맡기지 않는다. 식별에 필요한 포커스 링·선택 막대·아이콘은 3:1 이상 색을 쓴다. 실제 적합 여부는 구현 후 렌더 검증으로 확인한다.
- 표는 행 정체성과 지표 라벨을 함께 노출한다. 가로 스크롤해도 고정 열은 사라지지 않는다. 검색 포커스 순서는 Back → 입력 → 지우기 → 결과다.
- 숫자만 있는 점수는 `KIWOOM DRX 2, Paper Rex 1`처럼 묶은 설명을 준다. 반복 로고와 장식 이미지는 낭독하지 않는다.
- Snackbar 액션은 도달 가능하고 알림으로 읽힌다. 모달은 포커스를 가두고 명시적 action을 제공한다.
- 글꼴 크기 확대와 긴 이름에서 잘림 없이 행이 늘어난다. 모션 감소 설정을 따른다.

## 11. 적용 순서와 범위 밖

1. [x] 기반: `ui/theme` 토큰을 v3 값으로 교체하고 신규 토큰을 추가한다. Saira Condensed와 Material Symbols 글리프를 가져오고 공통 컴포넌트를 v3 계약으로 맞춘다. 토큰·서체·아이콘(2026-11-11)과 chrome·컨트롤·피드백·목록·상세 컴포넌트(2026-11-12)를 `feat/v3-design`에 적용했다.
2. [ ] 화면: 기능 묶음별로 v3를 적용한다. 상세 상단의 어두운 띠와 상태바 아이콘 색은 공통 컴포넌트 단계에서 처리했다. 뉴스(`10`·`11`), 경기(`20`·`21`), 마이페이지(`30`)를 적용했다(2026-11-12).
3. [ ] 검증: UI 테스트를 갱신하고 Android·iOS에서 주요 상태, 글꼴 확대, TalkBack·VoiceOver를 확인한다.
4. [ ] 정리: 코드에 남은 이전 디자인 흔적(`TeamDetailContentUiTest`의 Stitch marker 테스트 이름)을 v3 기준으로 바꾼다. 뉴스 상세 미리보기의 Stitch placeholder 이미지 URL은 v3 샘플로 바꿨다(2026-11-12).

범위 밖: 다크 모드, 태블릿 전용 레이아웃, 별도 애니메이션 시스템, 플랫폼별로 다른 시각, 경기 알림(Stage 2) 화면. 새 색, 새 상호작용 컴포넌트, 파괴적 action 표현은 이 문서와 `.pen`을 먼저 갱신한 뒤 도입한다.
