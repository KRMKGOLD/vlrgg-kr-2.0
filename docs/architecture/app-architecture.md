# App Architecture

## 목적

VLR.GG Mobile 2.0 앱은 vlr.gg의 Valorant e-sports 정보를 Android와 iOS에서 동일한 사용자 경험으로 제공하는 Compose Multiplatform 클라이언트다.

앱은 `app/shared`를 중심으로 작성한다. Android와 iOS 엔트리 포인트는 가능한 얇게 유지하고, 화면, 상태, ViewModel, repository 계약, app-facing model, API client 로직은 `commonMain`에서 공유한다.

서버는 VLR.GG HTML을 스크래핑하고 앱에서 사용하기 좋은 형태로 가공한다. 앱은 서버가 제공하는 API 응답을 기반으로 대회, 경기, 팀, 선수 정보를 노출한다.

## Source of Truth

- 루트 운영 규칙은 `AGENTS.md`를 따른다.
- 앱 아키텍처의 상세 기준은 이 문서를 따른다.
- 화면 요구사항과 사용자 흐름은 `docs/`의 기획 문서, Stitch 결과물, ralplan 결과물을 따른다.
- Stitch 기반 `DESIGN.md`가 수립되면 UI, theme, component, visual decision의 기준으로 함께 확인한다.
- 기능 작업을 시작하기 전 `docs/`, Stitch `DESIGN.md`, ralplan 결과물에 해당 기능의 최신 기획/범위가 업데이트되어 있는지 확인한다.
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

Client architecture는 UI, Domain, Data의 세 경계를 가진다. Data Layer는 server API, local cache, mapper, repository implementation을 담당한다. UseCase는 필수 계층이 아니라 필요한 경우에만 생성한다.

서버가 같은 저장소 안에 있고, 스크래핑 데이터를 앱 친화적인 API로 가공해서 제공하기 때문에 앱의 Domain Layer는 상대적으로 작게 유지한다.

일반적인 의존 흐름은 다음과 같다.

```text
Screen (Composable)
  -> ViewModel (StateFlow)
    -> Repository interface 또는 UseCase
      -> RepositoryImpl
        -> RemoteDataSource / Ktor API Service
        -> LocalDataSource / DAO
```

이 흐름은 기준선이다. 작은 기능에서 모든 계층이 필요하지 않다면 억지로 만들지 않는다.

## Target Package Shape

앱 공통 코드는 `app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile` 아래에서 다음 구조를 목표로 한다.

```text
commonMain/
  ui/
    App.kt
    theme/
      Theme.kt
      Colors.kt
      Typography.kt
      Dimensions.kt
    component/
    navigation/
      AppNavKey.kt
      AppNavHost.kt
    feature/
      home/
        components/
          MainTabRow.kt
        MainScreen.kt
        MainContent.kt
        MainUiState.kt
        MainViewModel.kt
      match/
      team/
      player/
  domain/
    model/
      Team.kt
      Match.kt
      Player.kt
    repository/
      MatchRepository.kt
      TeamRepository.kt
    usecase/
  data/
    repository/
      MatchRepositoryImpl.kt
      TeamRepositoryImpl.kt
    mapper/
      MatchMapper.kt
      TeamMapper.kt
    remote/
    local/
    network/
```

이 구조는 목표 구조다. 실제 기능이 없는 빈 패키지를 먼저 대량으로 만들 필요는 없다. 기능을 추가할 때 필요한 경계부터 만든다.

## UI Layer

UI Layer는 `commonMain/ui` 아래에 둔다.

### `ui/App.kt`

`App.kt`는 Compose 앱의 공통 진입점이다.

- 공통 Theme를 적용한다.
- 최상위 navigation host를 연결한다.
- 전역 scaffold나 app-level composition local이 필요하면 이 레벨에서 다룬다.
- 개별 feature의 세부 UI나 비즈니스 로직을 직접 넣지 않는다.

### `ui/theme`

Theme 관련 코드는 `ui/theme` 아래에 둔다.

예상 파일:

- `Theme.kt`
- `Colors.kt`
- `Typography.kt`
- `Dimensions.kt`

디자인 시스템이 구체화되면 `DESIGN.md`, Stitch 결과물, 화면 기획 문서를 기준으로 theme를 갱신한다.

### `ui/component`

여러 feature에서 실제로 재사용되는 component만 `ui/component`로 올린다.

한 feature 안에서만 쓰는 component는 먼저 해당 feature의 `components/` 아래에 둔다. 재사용 가능성이 있다는 이유만으로 공통 component로 올리지 않는다.

## Navigation

Navigation 관련 코드는 `commonMain/ui/navigation` 아래에 둔다.

Android와 iOS는 가능한 동일한 화면 흐름을 가진다. Navigation은 Compose Multiplatform Navigation 3를 사용한다. 의존성이 아직 Gradle에 없다면 첫 navigation 구현 작업에서 `gradle/libs.versions.toml`과 `app/shared/build.gradle.kts`에 반영한다.

예상 파일:

- `AppNavKey.kt`
- `AppNavHost.kt`

### `AppNavKey.kt`

`AppNavKey.kt`는 앱에서 사용하는 screen key를 모아두는 파일이다.

- Navigation key는 `ui/navigation` 경계 안에서만 동작하게 한다.
- 앱링크와 딥링크 확장을 고려해 key를 설계한다.
- route string을 화면 곳곳에 흩뿌리지 않는다.

예시:

```kotlin
@Serializable
sealed interface AppNavKey : NavKey {
    @Serializable
    data object Main : AppNavKey

    @Serializable
    data class MatchDetail(
        val matchId: String,
    ) : AppNavKey
}
```

### `AppNavHost.kt`

`AppNavHost.kt`는 앱의 Navigation Graph를 정의한다.

- Screen callback을 기준으로 화면 이동을 처리한다.
- back stack을 생성하고 관리한다.
- saved state, deep link, state restoration이 필요하면 이 레벨에서 다룬다.
- ViewModel이 `NavBackStack`, `NavController` 또는 동등한 navigation state를 직접 다루지 않게 한다.

예시:

```kotlin
@Composable
fun AppNavHost() {
    val backStack = rememberNavBackStack<AppNavKey>(AppNavKey.Main)

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<AppNavKey.Main> {
                MainScreen(
                    onNavigateToMatchDetail = { matchId ->
                        backStack.add(AppNavKey.MatchDetail(matchId))
                    },
                )
            }

            entry<AppNavKey.MatchDetail> { key ->
                MatchDetailScreen(
                    matchId = key.matchId,
                    onBack = {
                        backStack.removeLastOrNull()
                    },
                )
            }
        },
    )
}
```

## Feature Package Rules

Feature는 화면 단위로 패키지를 나눈다.

기본 구성:

```text
feature/
  home/
    components/
      MainTabRow.kt
    MainScreen.kt
    MainContent.kt
    MainUiState.kt
    MainViewModel.kt
```

각 파일의 책임은 다음과 같다.

| File | Responsibility |
| --- | --- |
| `*Screen.kt` | ViewModel state 수집, event callback 연결, navigation callback 전달 |
| `*Content.kt` | stateless UI rendering |
| `*UiState.kt` | 한 화면의 전체 UI 상태를 표현하는 single state container |
| `*ViewModel.kt` | 화면 상태 관리, repository/usecase 호출, UI event 처리 |
| `components/*` | 해당 feature 내부에서만 쓰는 하위 composable |

Screen은 ViewModel과 UI를 연결한다. Content는 가능하면 순수하게 `uiState`와 callback만 받아 화면을 그린다.

예시:

```kotlin
@Composable
fun MainScreen(
    viewModel: MainViewModel = metroViewModel(),
    onNavigateToMatchDetail: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MainContent(
        uiState = uiState,
        onRefreshClick = viewModel::refresh,
        onFavoriteClick = viewModel::toggleFavorite,
        onMatchClick = { matchId ->
            onNavigateToMatchDetail(matchId)
        },
    )
}

class MainViewModel(
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun refresh() {
        // fetch data and update uiState
    }

    fun toggleFavorite(matchId: String) {
        // update favorite state
    }
}

data class MainUiState(
    val favoriteIds: List<String> = emptyList(),
)
```

DI는 Metro DI를 사용한다. 의존성이 아직 Gradle에 없다면 첫 DI 구성 작업에서 dependency를 반영하고, ViewModel 생성 및 platform binding 규칙을 함께 문서화한다.

## UI State Rules

- 화면 상태는 한 화면당 하나의 `UiState` data class로 표현한다.
- loading, error, empty, content 상태를 `UiState` 안에서 명확히 표현한다.
- UI event는 ViewModel 함수로 전달한다.
- navigation event는 ViewModel이 직접 실행하지 않고 Screen callback 또는 AppNavHost가 처리한다.
- Domain Model을 그대로 화면에 노출하기 어려우면 ViewModel 또는 mapper에서 UiModel로 변환한다.

## Domain Layer

Domain Layer는 `commonMain/domain` 아래에 둔다.

Domain Layer의 책임은 다음으로 제한한다.

- 앱에서 사용하는 순수 Domain Model 정의
- Repository Interface 정의
- 여러 ViewModel에서 재사용되는 비즈니스 로직 정의
- UI Layer와 Data Layer 사이의 의존성 경계 제공

Domain Layer는 다음을 알면 안 된다.

- Ktor client/server DTO
- Room, SQLDelight, DataStore 같은 local persistence 구현체
- Android Context
- iOS API
- Compose UI

## Domain Model

Domain Model은 앱 내부에서 사용하는 app-facing model이다.

예시:

```kotlin
data class Match(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val startTime: String,
    val status: MatchStatus,
)
```

규칙:

- Domain Model은 ViewModel의 `UiState` 안에 포함될 수 있다.
- 화면 표시를 위해 문자열 포맷팅이나 UI 상태가 필요하면 UiModel로 변환한다.
- Domain Model은 RemoteResponse, Request, DTO, Entity에 의존하지 않는다.
- Domain Model은 화면 출력 전용 문자열이나 Compose 상태를 포함하지 않는다.

## Repository Interface

Repository Interface는 `commonMain/domain/repository`에 둔다.

예시:

```kotlin
interface MatchRepository {
    suspend fun getUpcomingMatches(): List<Match>
    suspend fun getMatchDetail(matchId: String): Match
}
```

규칙:

- Repository Interface의 반환 타입은 Domain Model 또는 app-facing model이어야 한다.
- Repository Interface는 RemoteResponse, DTO, Entity를 노출하지 않는다.
- Repository Interface는 DataSource 구현체를 알면 안 된다.
- Repository 구현체는 `commonMain/data/repository` 아래에 둔다.

## UseCase

UseCase는 필수가 아니다. 단순히 Repository 메서드를 한 번 호출하는 UseCase는 만들지 않는다.

UseCase는 다음 조건 중 하나 이상을 만족할 때만 만든다.

- 여러 ViewModel에서 같은 로직을 재사용한다.
- 여러 Repository를 조합해야 한다.
- 정렬, 필터링, 상태 판단 등 앱 정책이 명확히 존재한다.
- RepositoryImpl에 비즈니스 로직이 과도하게 쌓이고 있다.
- 별도 단위 테스트가 필요한 도메인 규칙이 있다.

좋은 예:

```kotlin
class GetUpcomingMatchesUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(): List<Match> {
        return matchRepository.getUpcomingMatches()
            .filter { it.status != MatchStatus.Finished }
    }
}
```

나쁜 예:

```kotlin
class GetMatchDetailUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String): Match {
        return matchRepository.getMatchDetail(matchId)
    }
}
```

위 예시는 Repository 호출을 그대로 감싸기만 하므로 ViewModel에서 Repository를 직접 호출하는 편이 낫다.

## Data Layer

Data Layer는 `commonMain/data` 아래에 둔다.

책임:

- Domain Layer의 Repository Interface 구현
- Remote 통신
- Local storage/cache 접근
- Remote/Local model을 Domain 또는 app-facing model로 매핑

기본 구조:

```text
data/
  repository/
    MatchRepositoryImpl.kt
    UserRepositoryImpl.kt
  mapper/
    MatchMapper.kt
    UserMapper.kt
  remote/
  local/
  network/
```

규칙:

- `remote`는 Ktor client API 호출과 remote DTO를 담당한다.
- `local`은 local database, cache, preference 등을 담당한다.
- `network`는 client 생성, base URL, 공통 request 설정을 담당한다.
- `mapper`는 Remote/Local model을 Domain 또는 app-facing model로 변환한다.
- RepositoryImpl은 RemoteDataSource/LocalDataSource를 조합하고, ViewModel이 사용할 모델을 반환한다.
- RemoteResponse나 Entity를 ViewModel에 직접 노출하지 않는다.

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
- Navigation 3, Metro DI, Ktor Client, persistence dependency는 확정 방향에 맞게 추가하되, 기능 구현 중 암묵적으로 넣지 않는다.
- 새 dependency가 필요하면 `gradle/libs.versions.toml`을 갱신하고, 어느 모듈 책임인지 문서화한다.
- platform-only dependency는 platform source set 또는 platform module에 둔다.
- 확정 기술 스택이어도 Gradle에 아직 없다면 첫 사용 작업에서 dependency 반영을 함께 처리한다.

## Testing Expectations

- ViewModel과 domain policy는 가능하면 `commonTest`에서 테스트한다.
- Android host 동작은 `androidHostTest`를 사용할 수 있다.
- iOS-specific 동작은 iOS test task를 사용할 수 있다.
- Mapping code가 VLR.GG 응답 구조를 의미 있게 변환한다면 focused test를 작성한다.
- UI test는 화면 흐름이 안정화된 뒤 도입한다.

루트 `AGENTS.md`의 Gradle 명령을 기준으로 가장 좁은 테스트부터 실행한다. 모듈 경계를 건드렸다면 관련 Gradle task를 추가로 실행한다.

## Placement Decision

이 파일은 `docs/architecture` 아래에 둔다. 이유는 이 문서가 `app/shared`만의 규칙이 아니라 Android/iOS 엔트리, shared module, core module, server API 경계까지 함께 설명하기 때문이다.

현재 앱 모듈별 작업 규칙은 다음 파일에 짧게 유지한다.

- `app/shared/AGENTS.md`
- `app/androidApp/AGENTS.md`
- `app/iosApp/AGENTS.md`

모듈별 `AGENTS.md`에는 바로 실행할 짧은 규칙과 이 문서에 대한 참조만 둔다. 긴 설계 배경, 목표 구조, 예시는 이 문서에서 관리한다.

## Change Rules

- feature package는 screen, state, ViewModel, feature component를 가까이 둔다.
- shared component는 실제 재사용이 생긴 뒤 추출한다.
- 넓은 빈 scaffolding보다 하나의 vertical feature slice를 우선한다.
- dependency, navigation strategy, layer boundary, module responsibility가 바뀌면 이 문서를 갱신한다.
