# VLR.GG Mobile v3 — Scoreline 디자인 기록

> **Status:** 2026-11-05에 작성한 전면 리뉴얼 제안이다. 1차 범위는 토큰, 재사용 컴포넌트, 기본 12화면이다. 채택 전까지 현재 앱의 디자인 권한은 루트 [DESIGN.md](../../DESIGN.md)에 있다.

- **편집 원본:** [`pen/vlrgg-mobile-v3.pen`](pen/vlrgg-mobile-v3.pen). Pencil에서 열어 편집하고, 앱에서 저장해야 디스크에 반영된다.
- **이전 시안:** [`pen/vlrgg-mobile-v2.pen`](pen/vlrgg-mobile-v2.pen)과 [v2 기록](v2-redesign.md)·[v2.1 제안](v2.1-design-system.md)은 보관 자료다. v3는 v2를 이어받지 않았다.
- **제품 계약:** 정보 구조, 화면 순서, 필드, 문구, 상태 규칙은 [`docs/feature/`](../feature/README.md)를 그대로 따른다. v3는 시각 언어만 새로 정한다.

## 방향

방송 스코어 바에서 가져온 정보 디자인이다. 숫자와 팀이 먼저 읽히고 나머지는 물러난다.

1. **숫자가 먼저 읽힌다.** 점수·시각·약어는 Saira Condensed, 문장과 이름은 Noto Sans KR로 역할을 나눈다.
2. **빨강은 하나의 신호다.** LIVE, 하단 탭 선택, 승자 표시, 주요 버튼에만 쓴다. 예정·종료를 색 chip으로 나누지 않고 텍스트로 표시한다.
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
| `line` / `line-strong` | `#E4E6EA` / `#C9CED6` | 구분선 / 보조 버튼 테두리 | 비텍스트 |
| `ink` | `#111418` | 제목·본문 | surface 18.47, canvas 16.49 |
| `ink-2` | `#3D434C` | 보조 본문 | surface 9.97 |
| `muted` | `#646B78` | 메타·패자 점수 | surface 5.36, canvas 4.79, sunken 4.96 |
| `accent` | `#CF1F33` | 신호·선택·주요 버튼 | surface 5.39, canvas 4.81, 흰 글자 5.39 |
| `accent-pressed` / `accent-soft` | `#A8172A` / `#FDECEE` | 누름 / 선택 배경 | — |
| `live` | `#FF3B4B` | LIVE 점 | panel 5.03 |
| `panel` / `panel-2` | `#15191E` / `#232930` | 상세 hero / hero 안 chip | — |
| `on-panel` / `on-panel-muted` | `#FFFFFF` / `#A4ABB6` | hero 글자 / hero 보조·패자 | panel 17.65 / 7.63 |
| `warn` / `warn-soft` | `#9A5800` / `#FFF4E0` | 연기 상태 | 5.11 |
| `focus` | `#2F6FED` | 포커스 링 | 비텍스트 |
| `disabled` | `#9AA1AC` | 비활성 아이콘·이미지 자리 | 비텍스트 |

### 서체

- **Noto Sans KR:** 한국어 UI, 팀·선수·대회 이름, 기사 제목과 본문. Android 시스템 한글 서체와 같은 계열이라, `.pen`에서는 시스템 서체를 대신 표현하는 용도로 쓴다.
- **Saira Condensed:** 점수, 맵 점수, 통계 수치, 팀 tag placeholder, `LIVE`, `MAP 1` 같은 약어. 좁은 폭에서 큰 숫자를 담기 위해 고른 Google Fonts 서체(OFL)다.
- 크기: display 28, title 24, section 17, body 15, body-sm 14, meta 13, label 12. 최소 글자 크기는 12다.

### 형태·간격·아이콘

- 모서리는 2dp(`radius`)다. 앱 아이콘(12dp)과 선수 사진 slot(원형)만 예외다.
- 간격은 4·8·12·16·20·24·32, 화면 좌우 여백은 16dp, 섹션 사이는 8dp다. 터치 영역은 최소 48dp다.
- 아이콘은 phosphor(MIT)다. 기본은 regular이고, 선택·ON 상태는 `-fill` 짝을 쓴다(하단 탭, 즐겨찾기 별). 경기 탭 아이콘은 Riot 자산이 아닌 `crosshair`다.

## 컴포넌트

보드 `01 · Components`에 재사용 컴포넌트 24개가 있다. 화면의 반복 요소는 모두 인스턴스이며, 원본을 고치면 12화면에 반영된다.

| 묶음 | 컴포넌트 | 규칙 |
| --- | --- | --- |
| 내비게이션 | Status bar, Root header, Detail header, Tabs, Bottom navigation | 루트는 제목 24/900과 검색 48dp. 상세 헤더는 Back과 기능 action만 둔다. 탭 선택은 ink 2dp 밑줄, 하단 탭 선택은 빨간 2dp 막대와 fill 아이콘 |
| 목록 | Section header, Match row, News row, Identity row, Link row, Search result row, Team match row, Favorite tile | Match row는 72dp 시간 열과 팀 열을 세로선으로 나눈다. 승자는 ink 700, 패자는 muted 500 |
| 상세 | Scoreboard, Entity hero, Map row, Head to head row, Stat header, Stat row | Scoreboard는 로고 위·이름 아래 대칭 구조이며 점수는 중앙. Map row는 hero와 같은 좌우 위치에 점수를 두고 승자 아래에 빨간 막대를 둔다 |
| 컨트롤 | Logo slot, Status chip, Primary button, Secondary button, Search field | Logo slot은 이미지가 없으면 팀 tag나 선수 이니셜, 대회는 trophy 아이콘을 표시한다 |

## 화면

| 프레임 | 화면 | 반영한 계약 |
| --- | --- | --- |
| `10` | 뉴스 목록 | 이미지 없는 구분선 행, 첫 기사는 큰 제목. 게시 시각 `2026년 9월 19일 오후 2:40` 형식 |
| `11` | 경기 · 예정/라이브 | 날짜 헤더 `9월 22일 (화)`와 `한국 시간`, 예정은 `오후 8:00`·`1시간 32분 후`, LIVE는 상태만 표시. 목록에는 팀 로고가 없다 |
| `12` | 마이페이지 | 팀은 2열 타일, 선수는 원형 사진 행. 저장 순서대로 표시하고 별로 해제. 기기 저장 안내 |
| `13` | 대회 목록 | 진행 중·예정·종료/중단 세 그룹 고정. 기간은 `dateLabel` 원문 |
| `14` | 정보 | 앱 이름·버전·소개, 링크 4행(외부 `↗`, 라이선스 `>`), 테마 안내, 출처·비공식·상표 고지 원문 |
| `20` | 검색 결과 | 헤더 안 검색창(포커스 2dp 테두리), 이미지 없는 행과 텍스트 타입 라벨 |
| `21` | 경기 상세 · 종료 | Event 링크, 대칭 hero, `2026년 9월 21일 (월) 오후 6:00 · Bo3`, 경기 설명 원문, 맵, 상대 전적. 알림 벨 없음(Stage 2) |
| `22` | 뉴스 상세 | 제목·작성자·게시 시각, 문단·링크·이미지와 캡션·소제목·목록 블록. 링크는 색과 밑줄을 함께 쓴다 |
| `23` | 대회 상세 · 경기 탭 | hero와 상태 chip, 경기/뉴스/통계 탭. 응답에 날짜 그룹이 없어 날짜 헤더 없이 표시하고, 문맥은 `event.series` 원문 |
| `24` | 팀 상세 | 즐겨찾기 별, 예정·최근 경기(`statusText`·`scheduledAtText` 원문), 선수, 스태프(이동 없음), 뉴스 |
| `25` | 선수 상세 | 현재 팀, 요원별 기록(고정 열·Rating 내림차순·결측 마지막·오른쪽 흐림), 최근 경기 `승리`/`패배`/`결과 미정` |
| `26` | 시리즈 상세 | 예정 대회·완료 대회 |

- 뉴스 목록의 기사 제목과 작성자는 v2 당시 API 캡처에서 가져왔다. 대진, 점수, 통계, 기사 본문, 팀 뉴스 제목은 레이아웃 확인용 샘플이며 실제 기록이 아니다.
- 기사 본문은 원문 언어 그대로 표시한다는 계약을 보이기 위한 영문 샘플이다.

## Compose 적용 시 참고

채택하면 기존 theme surface를 확장해 적용한다. 아래는 현재 `VlrColors`와의 대응이며 아직 구현하지 않았다.

| v3 토큰 | 현재 `VlrColors` | 비고 |
| --- | --- | --- |
| `surface`, `canvas` | `surface`, `surfaceSubtle` | 캔버스가 앱 배경이 된다 |
| `ink`, `muted`, `line` | `textPrimary`, `textSecondary`, `outline` | `ink-2`, `line-strong`, `surface-sunken`은 신규 |
| `accent`, `accent-pressed`, `accent-soft` | `actionPrimary`, `actionPrimaryPressed`, `surfaceSelected` | |
| `live`, `focus` | `accentLive`, `focusOutline` | |
| `panel`, `on-panel` | `inverseSurface`, `inverseOnSurface` | `panel-2`, `on-panel-muted`는 신규 |
| `warn`, `warn-soft` | `statusPostponed`, `statusPostponedContainer` | 예정·종료 상태 색은 v3에서 텍스트로 대체 |

- Saira Condensed는 숫자용 Latin 서체만 번들하면 된다. 한글은 시스템 서체를 쓴다.
- phosphor 아이콘은 사용하는 글리프만 vector asset으로 가져온다.

## 검증

- Pencil 렌더로 방향 보드, 컴포넌트 보드, 12화면을 확인했다. 겹침, 잘림, 줄바꿈 이상(`결과 미정` 줄바꿈, LIVE 행 높이)을 찾아 수정했다.
- 위 대비 값은 WCAG 상대 휘도 공식으로 계산했다. 본문 텍스트 조합은 모두 4.5:1 이상이다.
- 현재 Pencil(`.pen` 2.21 형식)은 텍스트 `underline` 속성을 저장하지 않는다. 그래서 본문 링크 밑줄은 링크 텍스트를 감싼 프레임의 아래 1px 테두리로 표현했다. 구현에서는 텍스트 밑줄을 쓴다.
- 디자인·문서 작업이라 앱 코드를 변경하지 않았고 Gradle 빌드·테스트는 실행하지 않았다. 캔버스 확인은 실기기 접근성 검증을 대신하지 않는다.

## 다음 단계

- 방향을 승인받은 뒤 상태 화면을 확장한다: 로딩, 빈 목록, 초기·추가 로드 오류, 경기 상세 예정·LIVE·연기·취소·확인 불가, 대회 상세 뉴스·통계 탭, 즐겨찾기 실패 복구, 오픈소스 라이선스, About 링크 실패 Snackbar.
- 411dp 폭, 글꼴 130%, 긴 팀·대회 이름 QA 프레임을 추가한다.
- 채택하면 루트 `DESIGN.md`를 v3 기준으로 갱신하고, Compose theme·컴포넌트 적용은 별도 작업으로 진행한다.
