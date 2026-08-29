# GitHub #42 P1 Player Detail 구현 프롬프트

아래 프롬프트를 T1 Team Detail PR이 `main`에 병합된 뒤 다음 작업 세션에 그대로 전달한다.

---

당신은 GitHub #42의 남은 RALPLAN packet인 P1 Player Detail을 완수하는 구현 coordinator다.

- Coordinator: Codex Sol, reasoning effort High
- 구현 담당: 단일 Codex native subagent, model `gpt-5.6-terra`, reasoning effort High
- Repository: `KRMKGOLD/vlrgg-kr-2.0`
- Target issue: GitHub #42
- Active packet: `P1 | Player Detail sections/navigation. Favorites excluded.`
- 작업 방식: Stitch 산출물과 기존 spec을 기반으로 한 Spec Driven Development
- 작업 범위: 구현, 테스트, 필요한 feature 문서 갱신, 단위별 commit/push, CodeRabbit CLI review, PR 생성

Coordinator는 기준선 보존, 요구사항·diff 평가, 검증, Git/PR lifecycle을 직접 소유한다. Terra High 하위 에이전트 한 명에게 코드·테스트·문서 구현을 맡기되, 다른 subagent, Team, Ralph는 추가 실행하지 않는다. 구현 하위 에이전트에게 현재 저장소를 다른 작업자와 공유할 수 있으므로 타인의 변경을 되돌리지 말고 지정 범위만 수정하라고 명시한다.

Orca orchestration lifecycle preamble에 실제 `taskId`/`dispatchId`가 주입된 환경이면 그 계약을 따른다. 차단 질문은 `orchestration ask`로 전달하고, 종료 시 주입된 ID로 `worker_done`을 정확히 한 번 전송한다. preamble이나 live ID가 없으면 값을 추정하거나 가짜 lifecycle call을 만들지 않는다.

## 목표

GitHub #42의 P1 Player Detail UI vertical slice와 실제 Navigation 3 destination을 구현한다.

현재 `main`에는 다음 기반이 이미 있어야 한다.

- T1 Team Detail 실제 화면과 navigation
- `GET /api/v1/players/{playerId}`
- Player remote DTO와 remote data source
- Player mapper와 Domain Model
- Player Repository와 Metro binding
- mapper/repository/remote/graph 테스트

이를 재설계하거나 재구현하지 않는다. P1은 Player Detail 화면 상태, 섹션 UI, interaction, navigation destination만 완성한다.

Player/Team favorite는 #43 범위다. Team logo와 roster member image 서버 파싱은 #68 범위다. P1 완료만으로 #43 또는 #68을 완료 처리하지 않는다.

## 시작 전 필수 절차

1. 루트 `AGENTS.md`와 `app/shared/AGENTS.md`를 읽는다.
2. `.codex/skills/coordinate-kmp-issue/SKILL.md`를 완전히 읽고 명시적 실행 요청 계약을 따른다.
3. 저장소 밖 `mktemp -d`로 실행 기준선을 만든다.
4. 다음 정보를 기준선 파일로 보존한다.
   - `git status --short --branch`
   - `git diff`
   - `git diff --cached`
   - `git ls-files --others --exclude-standard`
5. #42와 blocked-by #41, #35의 실제 GitHub 상태를 확인한다. #41 또는 #35가 열려 있으면 구현하지 않고 coordinator에게 escalation한다.
6. T1 Team Detail PR이 `main`에 병합됐는지 확인한다. 아직 미병합이면 임의로 stacked PR을 만들지 말고 Orca `ask`로 base 전략을 확인한다.
7. T1 병합 뒤 최신 `origin/main`에서 `feature/issue-42-player-detail-p1` 브랜치를 생성한다.
8. 현재 HEAD와 `origin/main` 관계, 동일 head branch/PR 중복 여부를 확인한다.
9. 기존 사용자 변경을 수정·stage·삭제·되돌리지 않는다. baseline과 같은 hunk에서 충돌하면 escalation한다.
10. 관련 코드와 spec, Stitch screenshot/HTML을 읽기 전에는 production 구현을 시작하지 않는다.

`.omx`, canonical RALPLAN, `.gitignore`, GitHub issue 상태는 읽기 전용으로 취급한다.

## Source of Truth

다음을 교차 검증한다.

### GitHub / RALPLAN

- Issue: `https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/42`
- RALPLAN: `.omx/plans/kmp-app-development-direction-ralplan.md`
- Packet: `P1 | Player Detail sections/navigation. Favorites excluded.`
- Acceptance: `Overall error vs section empty is distinct; mapper and navigation tests pass.`
- Favorite follow-up: `https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/43`
- Team image server follow-up: `https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/68`

### Feature / architecture spec

- `docs/feature/players/README.md`
- `docs/feature/teams/README.md`
- `DESIGN.md`
- `docs/app-arch/ui-layer.md`
- `docs/app-arch/domain-layer.md`
- `docs/app-arch/data-layer.md`
- `docs/app-arch/app-runtime.md`

### 현재 구현

최소한 다음 파일과 인접 테스트를 읽는다.

- `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/domain/model/player/PlayerDetail.kt`
- `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/data/remote/model/player/PlayerDetailResponseDto.kt`
- `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/data/mapper/PlayerMapper.kt`
- `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/domain/repository/PlayerRepository.kt`
- `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/data/repository/PlayerRepositoryImpl.kt`
- `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/navigation/AppNavKey.kt`
- `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/navigation/NavigationContent.kt`
- Team/News/Event Detail의 assisted ViewModel·Screen·Content 패턴
- Team Detail의 feature-local match card와 section empty/error dialog 패턴
- 기존 Player mapper/repository/remote/graph 테스트
- Search, News, Event, Team의 Player navigation callback
- 기존 navigation runtime 테스트와 root overlay state 보존 테스트

문서와 코드가 충돌하면 현재 코드 구조를 먼저 확인하고 차이를 보고한다. 범위를 조용히 확장하지 않는다.

## Stitch 디자인 증거

Stitch canonical 프로젝트를 직접 조회하고 다음 화면의 screenshot과 HTML 산출물을 모두 확인한다.

Project:

- ID: `8765150675340843101`
- Title: `VLR.GG Mobile 2.0 — Canonical 88 Screens`

P1 검토 대상:

1. Populated / Favorite Off
   - `ad77b73d86b34916adaa9ae6c8ace598`
2. Loading
   - `a22d3782a851475bbda52b53122d9fa9`
3. Sparse
   - `1eb25c82c57145fa91f32f2e337728ea`
4. Error Dialog
   - `d370e90c5ee44ed096a40b3a010aeba1`

필요한 임시 다운로드는 저장소 밖 임시 디렉터리에만 둔다.

다음 화면은 #43 범위이므로 구현하지 않는다.

- Favorite On: `3273bfe39bed41f7a73655d90d91168e`
- Add Favorite Error: `b50de906fb1a488fa037a7b34483b577`
- Remove Favorite Error: `f49a4b1d6f204914ab22ffd527d8505f`

Stitch에 player face, team logo, agent icon, favorite star가 보여도 현재 API/RALPLAN 계약이 우선한다.

- top bar는 Back + `Player Profile` title만 제공한다.
- star, 빈 star 자리, disabled star, favorite action/Snackbar를 만들지 않는다.
- Player endpoint에 profile image URL이 없으므로 원격 face를 추정하지 않는다. handle에서 만든 안정적인 text placeholder를 사용한다.
- `currentTeam`에는 id/name만 있으므로 logo나 league/event 이름을 만들지 않는다.
- Agent stats에는 icon URL이 없으므로 agent icon을 추정하거나 새 image loader를 추가하지 않는다.

## Spec Driven Development

Production 코드보다 실행 가능한 spec 테스트를 먼저 작성한다.

### 1단계: 상태·상호작용 matrix 확정

구현 전 작업 기록에 다음 matrix를 고정한다.

#### Loading

- 56dp top bar와 Back은 안정적으로 유지된다.
- Player header의 최종 geometry를 반영한 skeleton을 표시한다.
- Current Team, Agent Stats, Recent Matches의 최종 geometry를 반영한 skeleton을 표시한다.
- 로딩 중 sample Player, 임의의 team/stats/match를 노출하지 않는다.
- detail 화면에서 bottom navigation을 표시하지 않는다.

#### Populated

순서를 고정한다.

1. Player header
2. 현재 소속 팀
3. 에이전트 통계
4. 최근 경기

표시 데이터:

- Header: `handle`, nullable `realName`, `aliases`, nullable `countryName`/`countryCode`, 안정적인 text placeholder
- Current Team: 정확한 `id`, `name`
- Agent Stats: `agentName`, `mapsPlayed`, `pickRatePercent`, `rating`, `averageCombatScore`, `killDeathRatio`, `kastPercent`, `averageDamagePerRound`
- Recent Match: `id`, `eventName`, nullable `eventStage`, `teamA`/`teamB` name/tag, nullable scores, `outcome`, nullable `playedOn`

Agent metric column 순서는 다음으로 고정한다.

1. Agent identity
2. Maps
3. Pick Rate
4. Rating
5. ACS
6. K/D
7. KAST
8. ADR

Agent identity column은 고정하고 metrics는 수평 스크롤 가능하게 한다. nullable metric은 `0`이나 가짜 값이 아니라 `—`를 표시한다. percentage 값은 값이 있을 때만 `%`를 붙인다.

Recent Matches는 server source 순서대로 표시하고 검색, 더보기, pagination, infinite scroll을 제공하지 않는다. 최대 5개 보장은 app DTO/Domain/mapper가 자체 cap을 강제하는 계약이 아니라 server `PlayerDetailParser`의 distinct 후 `take(5)`와 `PlayerDetailMapper`의 defensive `take(5)`가 public response에서 보장한다. app remote DTO/mapper/domain은 그 response를 순서대로 보존하며, UI에서 별도 fake cap·데이터 변환·fake timestamp를 만들지 않는다.

#### Sparse / Section Empty

Atomic response이므로 generic Partial 상태, section별 request/loading/error 상태를 만들지 않는다. section empty는 `Content(PlayerDetail)` 내부의 nullable/list 값에서 파생한다.

다음을 독립적으로 검증한다.

- Current Team만 empty
- Agent Stats만 empty
- Recent Matches만 empty
- 둘 이상의 optional section이 empty지만 다른 성공 section은 계속 표시
- 모든 optional section이 empty지만 Player header는 정상 표시
- Agent row 자체는 있으나 일부 metric만 null

Stitch 문구를 사용한다.

- `소속 팀 정보가 없습니다`
- `에이전트 통계 정보가 없습니다`
- `최근 경기 기록이 없습니다`

#### Error

- Player Detail 전체 요청 실패는 section empty와 다른 전체 Error다.
- Retry와 Back만 제공하는 modal dialog를 표시한다.
- 문구는 Stitch 기준으로 고정한다.
  - title: `정보를 불러오지 못했습니다`
  - body: `네트워크 상태를 확인하고 다시 시도해 주세요.`
  - action: `재시도`, `뒤로가기`
- dialog는 focus를 가두고 dismiss 후 적절한 focus 복원이 가능해야 한다.
- raw exception, HTTP status, server message, URL, selector, parser 정보를 노출하지 않는다.
- stale/sample/populated 데이터를 dialog 아래에 임의로 만들지 않는다.
- Retry는 Loading으로 전환한 뒤 같은 `playerId`를 다시 요청한다.
- Error가 아닐 때 중복 Retry를 허용하지 않는다.

### 2단계: 테스트 선작성

최소 다음 테스트를 먼저 실패시키고 이후 구현으로 통과시킨다.

#### Common ViewModel tests

- 초기 상태는 Loading이며 `playerId`로 정확히 한 번 요청한다.
- Success는 전체 `PlayerDetail`을 Content로 보존한다.
- nullable currentTeam 또는 empty list가 전체 Error로 변환되지 않는다.
- Failure는 Error가 된다.
- Retry는 동일 `playerId`를 사용한다.
- Error가 아닌 상태에서 retry가 중복 요청을 만들지 않는다.
- UiState/ViewModel이 raw exception을 보존하거나 노출하지 않는다.

#### Compose UI tests

기존 프로젝트 패턴에 따라 `iosTest`에 작성한다.

- Loading skeleton과 sample data 미노출
- Populated section 순서와 주요 데이터
- Current Team empty
- Agent Stats empty
- Recent Matches empty
- 여러 section/all optional section empty
- nullable metric의 `—` marker와 numeric zero의 구분
- Error dialog와 Retry/Back
- Current Team click이 정확한 `teamId`를 전달
- Recent Match click이 정확한 `matchId`를 전달
- Event 이름/단계에는 Event Detail target이 없음
- player face/team logo/agent icon의 추정 remote image가 없음
- favorite star/action/Snackbar가 없음
- 주요 interactive target이 최소 48dp이며 접근 가능한 label을 가짐
- 긴 한국어와 공식 Player/Team/Event 이름이 레이아웃을 파괴하지 않음

#### Navigation runtime tests

- Search Player 결과 → 실제 `PlayerDetailScreen`
- News Player link → 실제 `PlayerDetailScreen`
- Team Player row → 실제 `PlayerDetailScreen`
- 기존 Event Player row도 동일 `PlayerDetail(playerId)` key를 실제 화면으로 해석
- Player Current Team → `TeamDetail(teamId)`
- Player Recent Match → `MatchDetail(matchId)`
- Back 후 진입 전 화면 state 유지
- Player Detail에서 Team/Match를 열었다가 Back했을 때 loaded Player 데이터와 scroll 유지
- Team Detail에서 Player를 열었다가 Back했을 때 loaded Team 데이터와 scroll 유지
- 다른 root로 전환 후 돌아와도 owning root의 Player overlay state 유지
- Player destination marker가 더 이상 표시되지 않음
- MatchDetail/SeriesDetail marker는 후속 packet까지 유지

기존 Player mapper/repository/remote/graph 테스트는 중복 작성하지 않고 회귀 검증으로 실행한다.

## 구현 계약

### UI architecture

권장 package:

`app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/player/detail`

기본 파일:

- `PlayerDetailUiState.kt`
- `PlayerDetailViewModel.kt`
- `PlayerDetailScreen.kt`
- `PlayerDetailContent.kt`

필요한 경우에만 feature-local component를 분리한다.

- `components/PlayerAgentStatsTable.kt`
- `components/PlayerRecentMatchCard.kt`

작은 subcomposable을 과도하게 파일로 분리하거나 generic entity/detail framework를 만들지 않는다.

`PlayerDetailUiState`는 화면 전체 snapshot이다. Atomic 단일 요청에 맞춰 다음 content state를 사용한다.

- Loading
- Content(`PlayerDetail`)
- Error

Section empty는 Content 내부 nullable/list에서 파생한다. generic Partial이나 section별 request state를 만들지 않는다.

`PlayerDetailViewModel`은 다음 패턴을 따른다.

- `PlayerRepository` constructor dependency
- `playerId` assisted dependency
- `@AssistedInject`
- `@AssistedFactory`
- `@ManualViewModelAssistedFactoryKey`
- `@ContributesIntoMap(AppScope::class)`
- `StateFlow<PlayerDetailUiState>`
- init에서 최초 load
- retry는 explicit function

`PlayerDetailScreen`은 다음만 담당한다.

- assisted `playerId`로 ViewModel 획득
- lifecycle-aware state collect
- `LazyListState` 소유
- navigation callback을 Content에 전달
- `viewModel::retry`를 `PlayerDetailContent`의 명시적 `onRetry` callback으로 전달

ViewModel은 navigation stack, callback, Compose scroll state를 소유하지 않는다.

`PlayerDetailContent`는 stateless rendering과 callback 전달을 담당하며, Error dialog의 Retry에서 `onRetry`를 호출한다.

### Existing components

계약이 맞는 기존 컴포넌트와 token을 재사용한다.

- `VlrTheme`
- `VlrDimensions`
- `VlrTypography`
- `VlrIconButton`
- `VlrButton`
- `StatusChip`
- 기존 arrow/error/match/person/team resources

새 dependency, 새 image loader, Android-only/iOS-only UI를 추가하지 않는다.

### Current Team / Stats / Recent Match 경계

- Current Team row 전체만 `TeamDetail(currentTeam.id)` target이다.
- logo/event/league/tag를 추정하지 않는다.
- Agent Stats는 typed Domain numeric field를 그대로 표시하고 nullable 값을 `—`로 처리한다.
- stats 문자열을 Domain Model에 추가하지 않는다.
- existing `MatchCard`에 맞추려고 fake `MatchSummary`를 만들지 않는다.
- `PlayerRecentMatch`를 그대로 받는 feature-local card를 사용한다.
- score가 null이면 임의의 `0`을 만들지 않는다.
- `playedOn`은 서버 문자열을 그대로 안전하게 표시하며 임의 date parsing을 하지 않는다.
- match card 전체 surface만 Match Detail target이다.
- Event 이름/단계는 표시 전용이며 Event Detail nested target을 만들지 않는다.

## Navigation 변경

`NavigationContent.kt`의 marker branch에서 Player를 분리한다.

변경 전 예상:

- `MatchDetail`, `PlayerDetail`, `SeriesDetail` → `PushedContent`

변경 후:

- `is PlayerDetail` → 실제 `PlayerDetailScreen`
- `MatchDetail`, `SeriesDetail` → 기존 marker 유지

PlayerDetailScreen callbacks:

- `onBack`
- `onTeamClick(teamId)`
- `onMatchClick(matchId)`

Search, News, Event, Team이 이미 생성하는 `PlayerDetail(playerId)` key 계약을 유지한다. `AppNavKey`나 serializer 구조를 변경하지 않는다.

## 문서 갱신

모든 구현과 fresh verification이 통과한 뒤에만 `docs/feature/players/README.md`를 갱신한다.

- App UI의 P1 Player Detail 상태를 구현 완료로 표시
- Player sections/navigation acceptance만 체크
- Favorite #43 예정 상태 유지
- Team image server follow-up #68 상태를 별도 유지
- 실제 양 플랫폼 screenshot/실기기 접근성 검증을 수행하지 않았다면 완료로 주장하지 않음
- DESIGN.md나 RALPLAN은 계약 변경이 없는 한 수정하지 않음

## 명시적 제외 범위

다음을 구현하거나 변경하지 않는다.

- Player favorite star와 persistence
- Add/Remove favorite mutation과 Retry Snackbar
- MyPage favorite 연동
- Player notification
- Player face API/파싱/이미지 로딩
- Team logo API/파싱/이미지 로딩 (#68)
- Match Detail 실제 화면
- Event Detail 직접 이동
- 서버 endpoint/parser/response 변경
- DTO/Domain/Repository 재설계
- fake `MatchSummary`
- stale cache/fallback
- refresh/pagination
- raw error category UI
- generic entity/detail framework
- 새 dependency

## 중단 및 coordinator 질문 조건

다음 중 하나가 필요하면 임의로 구현하지 말고 Orca `ask`를 사용한다.

- Player face 또는 Team/Agent logo URL
- 새로운 metric, score, date/time parsing 계약
- Current Team/Recent Match 외의 새로운 navigation
- Player API/Domain 계약 변경
- favorite UI를 P1에 포함
- stale content/cache
- 새로운 error category
- DESIGN/spec/RALPLAN 간 해결되지 않는 제품 충돌
- T1 PR이 미병합인 상태의 stacked base 결정
- baseline 사용자 변경과 같은 hunk 충돌
- 필수 플랫폼 테스트를 실행할 수 없는 환경 문제

## Commit / push / review / PR 계약

각 단위가 테스트를 통과한 뒤 하나의 목적만 가진 한글 Conventional Commit으로 커밋하고 즉시 같은 branch를 push한다. 실패 테스트만 남는 커밋은 만들지 않는다.

권장 단위:

1. `feat: 선수 상세 상태와 뷰모델 구현`
2. `feat: 선수 상세 화면과 섹션 UI 구현`
3. `feat: 선수 상세 내비게이션 연결`
4. `docs: 선수 상세 P1 구현 상태 반영`

각 commit 전에 baseline 사용자 변경이 섞이지 않았는지 path 단위로 확인하고 명시적으로 stage한다.

코드·테스트·문서 커밋과 push가 끝나면 다음 CodeRabbit CLI review를 실행한다.

```bash
coderabbit review --committed --base main --agent
```

- 각 finding의 파일/라인, severity, 근거를 직접 재검증한다.
- 유효한 finding은 focused test와 함께 수정하고 별도 단위 commit/push한다.
- 무효 finding은 왜 적용하지 않았는지 작업 기록에 남긴다.
- 수정 뒤 CodeRabbit review를 다시 실행해 남은 finding을 확인한다.

모든 필수 검증이 통과한 뒤 `main` base PR을 생성한다.

- 권장 title: `feat: Player Detail P1 UI와 내비게이션 구현`
- body에는 구현 상태, 정확한 검증 명령/결과, Stitch 비교, CodeRabbit finding/수정, 제외 범위 #43/#68을 포함한다.
- T1이 이미 병합됐고 P1이 #42의 마지막 남은 packet임을 확인한 경우에만 `Closes #42`를 사용한다.
- #42에 미완료 acceptance가 남아 있으면 `Refs #42`를 사용하고 issue를 닫지 않는다.
- PR 생성 뒤 base/head/diff/body/check 상태를 재조회한다.

## 검증 순서

가장 좁은 테스트부터 실행하고 실패하면 수정 후 다시 실행한다.

1. Player ViewModel focused test
2. Player Content UI focused test
3. Player navigation runtime focused test
4. 기존 Player mapper/repository/remote/graph regression
5. 전체 shared Android host test
6. Android common compile
7. iOS simulator target compile
8. iOS simulator tests
9. Android app assemble
10. diff whitespace check

최종 필수 명령:

```bash
./gradlew :app:shared:testAndroidHostTest
./gradlew :app:shared:compileAndroidMain
./gradlew :app:shared:compileKotlinIosSimulatorArm64
./gradlew :app:shared:iosSimulatorArm64Test
./gradlew :app:androidApp:assembleDebug
git diff --check
```

명령을 실행하지 못하면 숨기지 말고 정확한 이유와 next-best evidence를 보고한다.

## Stitch 시각 검증

최소 360dp compact baseline에서 Loading, Populated, Sparse, Error Dialog를 Stitch와 비교한다.

확인 항목:

- 56dp app bar와 safe area
- detail 화면의 bottom navigation 미표시
- 16dp horizontal inset
- flat white surface와 low-emphasis outline
- section 순서와 divider rhythm
- skeleton geometry 안정성
- 고정 Agent identity + 수평 metric table
- 48dp interactive targets
- 긴 한국어와 공식 Player/Team/Event 이름의 안전한 배치
- section empty와 overall error의 시각적 차이
- star와 추정 remote image가 의도적으로 없음

실제 양 플랫폼 screenshot/실기기 접근성 검증을 하지 않았다면 실행 가능한 UI 테스트와 simulator/preview 비교만 보고하고 완료를 허위로 주장하지 않는다.

## 완료 조건

다음을 모두 만족해야 succeeded로 보고한다.

- PlayerDetail marker가 실제 화면으로 교체됨
- Loading/Populated/Sparse/Error가 spec대로 동작
- Header/Current Team/Agent Stats/Recent Matches가 순서대로 구분되어 표시됨
- section empty가 다른 성공 section을 숨기지 않음
- missing metric과 실제 zero가 구분됨
- overall error와 section empty가 구분됨
- Search/News/Team/Event의 기존 Player key가 실제 화면에 도달
- Current Team/Recent Match callback ID가 정확함
- Event/favorite/image navigation 또는 추정 데이터가 없음
- Back 후 initiating state와 Player loaded/scroll state 유지
- 다른 root 전환 후 owning overlay state 유지
- 기존 server/data tests 유지
- Android/iOS compile과 관련 테스트 통과
- feature spec 상태 갱신
- CodeRabbit review finding을 평가하고 유효 항목을 수정
- 단위별 commit/push와 PR 생성 완료
- 사용자 기존 변경을 훼손하지 않음
- 범위 밖 변경이 없음

## 최종 보고 형식

Orca `worker_done` body와 최종 응답에 다음을 포함한다.

1. Outcome: `succeeded` 또는 `failed`
2. 구현한 spec/상태
3. 변경 파일 목록과 각 책임
4. 먼저 작성한 테스트와 검증한 acceptance
5. 실행한 정확한 명령과 결과
6. Stitch 상태별 비교 결과
7. CodeRabbit finding과 적용/미적용 판단 및 수정 내용
8. commit hash/title과 push 결과, PR URL/base/head
9. 실행하지 못한 검증과 이유
10. 남은 위험 또는 #43/#68/Match Detail 후속 경계
11. 범위 밖 변경이 없었는지
12. 문서 갱신 내용

성공 또는 실패를 prose에만 숨기지 말고 Orca lifecycle outcome에도 정확히 반영한다.
