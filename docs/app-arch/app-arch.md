# App Architecture

## 목적

VLR.GG Mobile 2.0 앱은 vlr.gg의 Valorant e-sports 정보를 Android와 iOS에서 동일한 사용자 경험으로 제공하는 Compose Multiplatform 클라이언트다.

앱은 `app/shared`를 중심으로 작성한다. Android와 iOS 엔트리 포인트는 가능한 얇게 유지하고, 화면, 상태, ViewModel, repository 계약, app-facing model, API client 로직은 `commonMain`에서 공유한다.

서버는 VLR.GG HTML을 스크래핑하고 앱에서 사용하기 좋은 형태로 가공한다. 앱은 서버가 제공하는 API 응답을 기반으로 대회, 경기, 팀, 선수 정보를 노출한다.

## Source of Truth

- 루트 운영 규칙은 `../../AGENTS.md`를 따른다.
- 앱 아키텍처의 전체 기준은 이 문서를 따른다.
- 앱 root, Metro graph, Navigation 3의 기본 runtime 원칙은 `app-runtime.md`를 따른다.
- UI 세부 규칙은 `ui-layer.md`를 따른다.
- Domain 세부 규칙은 `domain-layer.md`를 따른다.
- Data 세부 규칙은 `data-layer.md`를 따른다.
- 화면 요구사항과 사용자 흐름은 `docs/`의 기획 문서, Stitch 결과물, ralplan 결과물을 따른다.
- UI, theme, component, visual decision은 [`../../DESIGN.md`](../../DESIGN.md)를 기준으로 함께 확인한다.
- 기능 작업을 시작하기 전 `docs/`, 루트 `DESIGN.md`, Stitch 결과물, ralplan 결과물에 해당 기능의 최신 기획/범위가 업데이트되어 있는지 확인한다.
- 기능 문서가 아직 예정 상태라면 빈 문서를 미리 만들지 않고, 해당 기능 작업이 시작될 때 필요한 최소 범위만 `docs/`에 생성하거나 갱신한다.
- 현재 코드와 문서가 충돌하면 현재 코드 구조를 먼저 확인하고, 변경 의도에 맞게 문서를 함께 갱신한다.

## Module Placement

| Module | Responsibility |
| --- | --- |
| `app/shared` | Compose Multiplatform 공통 UI, ViewModel, state, navigation, domain/data 경계, API client |
| `app/androidApp` | Android 앱 엔트리 포인트, Android-only integration, platform-specific DI binding |
| `app/iosApp` | iOS 앱 엔트리 포인트, SwiftUI host, iOS-only integration |
| `core` | 앱과 서버가 함께 사용할 수 있는 순수 Kotlin utility, value object, framework-free shared concept |
| `server` | VLR.GG scraping, server-side processing, app-facing API response |

`app/shared`가 기본 구현 위치다. 동일 기능을 Android와 iOS에 각각 구현하기 전에 `commonMain`에서 해결 가능한지 먼저 판단한다.

`app/androidApp`과 `app/iosApp`에는 비즈니스 로직을 넣지 않는다. 플랫폼 API, 앱 엔트리, 플랫폼별 bridge, 플랫폼별 DI binding처럼 해당 OS에서만 필요한 코드만 둔다.

## Architecture Overview

기본 방향은 Android Recommended Architecture를 Compose Multiplatform 환경에 맞게 적용하는 것이다.

Client architecture는 UI, Domain, Data의 세 경계를 가진다. UI는 `StateFlow`로 화면 snapshot을 노출하고 explicit ViewModel function과 Screen callback으로 UDF를 구성한다. Domain Layer는 app-facing business model, repository contract, 공통 `AppResult`를 담당하고, Data Layer는 server API, local cache, mapper, repository implementation을 담당한다. UseCase는 필수 계층이 아니라 필요한 경우에만 생성한다.

레이어 의존성과 별도로 앱 runtime composition은 플랫폼 runtime owner에서 시작한다. 필요한 graph는 Compose recomposition 경로 밖에서 준비해 공통 `App`에 전달한다. navigation 상태의 형태와 Metro factory 제공 위치는 기능 요구사항에 맞춰 정하며, 기본 경계는 `app-runtime.md`를 따른다.

서버가 같은 저장소 안에 있고, 스크래핑 데이터를 앱 친화적인 API로 가공해서 제공하기 때문에 앱의 Domain Layer는 상대적으로 작게 유지한다.

일반적인 의존 흐름은 다음과 같다.

```text
Screen (Composable)
  -> ViewModel (StateFlow)
    -> Repository interface (`AppResult`) 또는 UseCase
      -> RepositoryImpl
        -> RemoteDataSource / Ktor Client
        -> LocalDataSource / DataStore or DAO
```

이 흐름은 기준선이다. 작은 기능에서 모든 계층이 필요하지 않다면 억지로 만들지 않는다.

## Target Package Shape

앱 공통 코드는 `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile` 아래에서 다음 구조를 목표로 한다.

```text
commonMain/
  di/
    AppGraph.kt             # target: app runtime DI graph
  ui/
    App.kt                  # target: shared app composition root
    theme/
      Theme.kt
      Colors.kt
      Typography.kt
      Dimensions.kt
    component/
    navigation/
      AppNavKey.kt
      AppNavHost.kt         # navigation state and entry mapping
    feature/
      home/
        components/
          MainTabRow.kt
        MainScreen.kt
        MainContent.kt
        MainUiState.kt
        MainContentState.kt # optional: complex primary content only
        MainViewModel.kt
      match/
      team/
      player/
  domain/
    AppResult.kt
    model/
      Team.kt
      Match.kt
      Player.kt
    repository/
      MatchRepository.kt
      TeamRepository.kt
    usecase/
  data/
    di/
      DataBindings.kt
    repository/
      MatchRepositoryImpl.kt
      TeamRepositoryImpl.kt
    mapper/
      MatchMapper.kt
      TeamMapper.kt
    remote/
      model/
      impl/
        RemoteMatchDataSourceImpl.kt
      RemoteMatchDataSource.kt
    local/
      impl/
        LocalMatchDataSourceImpl.kt
      LocalMatchDataSource.kt
      datastore/ # optional: Preferences storage feature only
      room/      # optional: relational/large local DB feature only
    network/
      HttpClientFactory.kt
      NetworkConfig.kt
```

`commonMain`에는 공통 contract, repository 정책, mapper, Ktor client 설정, Metro graph/binding을 둔다. `androidMain`과 `iosMain`에는 DataStore path와 Room builder처럼 platform API가 필요한 좁은 factory 구현만 둔다.

이 구조는 목표 구조다. 실제 기능이 없는 빈 패키지를 먼저 대량으로 만들 필요는 없다. DataStore와 Room branch는 저장 요구가 있는 기능에서만 생성한다. 세부 package 책임, model 규칙, DI binding 규칙은 `data-layer.md`를 따른다.

## Layer Documents

- `app-runtime.md`: platform composition, app graph, navigation state의 기본 runtime 원칙과 구현 시 결정 경계
- `ui-layer.md`: Compose UI, navigation, feature package, UiState/optional ContentState, direct callback UDF 규칙
- `domain-layer.md`: Domain model, `AppResult`, repository contract, UseCase 생성 조건
- `data-layer.md`: Remote/local data source, DTO/entity, repository implementation, mapper, error/cache/test 규칙

## Core Module Usage

`core`에는 앱과 서버가 공유해도 안전한 순수 Kotlin 코드를 둔다.

좋은 후보:

- 앱/서버가 함께 사용하는 value object
- 작은 validation utility
- 공통 enum-like concept
- UI나 server framework에 의존하지 않는 날짜/문자열 처리 primitive

피해야 할 것:

- Compose UI
- Android Context
- iOS API
- Ktor Application
- request/response DTO
- API contract
- transport-oriented model
- 특정 feature에서만 사용하는 코드

공통으로 보인다는 이유만으로 `core`에 올리지 않는다. 실제로 앱과 서버 양쪽에서 필요하고, framework 의존성이 없을 때만 `core`로 이동한다.

## Dependency Rules

- 기존 project dependency와 Kotlin Multiplatform 호환 library를 우선한다.
- Navigation 3와 Metro DI의 기본 runtime 원칙은 `app-runtime.md`, Kotlinx Serialization, Ktor Client, Preferences DataStore, Room의 data 책임은 `data-layer.md`에 기록한다.
- 실제 Gradle dependency/version 추가는 기능 구현 작업에서 `gradle/libs.versions.toml`과 관련 모듈 build file을 함께 갱신하며 수행한다.
- platform-only API와 factory 구현은 해당 platform source set에 둔다. 공통 contract와 정책은 `commonMain`에 유지한다.

## Testing Expectations

- ViewModel과 domain policy는 가능하면 `commonTest`에서 테스트한다.
- Data mapping code가 VLR.GG 응답 구조를 의미 있게 변환한다면 focused test를 작성한다.
- Android host 동작은 `androidHostTest`를 사용할 수 있다.
- iOS-specific 동작은 iOS test task를 사용할 수 있다.
- UI test는 화면 흐름이 안정화된 뒤 도입한다.

루트 `AGENTS.md`의 Gradle 명령을 기준으로 가장 좁은 테스트부터 실행한다. 모듈 경계를 건드렸다면 관련 Gradle task를 추가로 실행한다.

## Placement Decision

앱 아키텍처 문서는 `docs/app-arch` 아래에 둔다. 이유는 앱 아키텍처가 UI, Domain, Data 세부 규칙을 함께 다루고, Data Layer 강화처럼 특정 layer 문서가 커질 수 있기 때문이다.

긴 설계 배경과 목표 구조는 이 디렉터리의 문서에서 관리한다. 모듈별 `AGENTS.md`에는 바로 실행할 짧은 규칙과 이 문서에 대한 참조만 둔다.

현재 앱 모듈별 작업 규칙은 다음 파일에 짧게 유지한다.

- `app/shared/AGENTS.md`
- `app/androidApp/AGENTS.md`
- `app/iosApp/AGENTS.md`

## Change Rules

- feature package는 screen, state, ViewModel, feature component를 가까이 둔다.
- shared component는 실제 재사용이 생긴 뒤 추출한다.
- 넓은 빈 scaffolding보다 하나의 vertical feature slice를 우선한다.
- dependency, navigation strategy, layer boundary, module responsibility가 바뀌면 이 디렉터리의 관련 문서를 갱신한다.
