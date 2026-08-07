# UI Layer

## Responsibility

UI Layer는 `commonMain/ui` 아래에 둔다.

UI Layer는 화면 렌더링, 사용자 이벤트 전달, navigation callback 연결, ViewModel state 수집, UI behavior 처리를 담당한다. 비즈니스 규칙, remote/local model 처리, repository 구현은 UI Layer에 두지 않는다.

## `App.kt`

`App.kt`는 Compose 앱의 공통 진입점이다.

- 필요한 runtime dependency는 플랫폼 owner가 준비해 전달한다.
- 공통 Theme를 적용한다.
- `App.kt`는 `AppGraph`의 `metroViewModelFactory`를 `LocalMetroViewModelFactory`에 제공하고 `AppNavigation()`을 연결한다. Graph와 factory는 `NavigationContent`나 feature Screen parameter로 전달하지 않는다.
- 최상위 navigation host를 연결한다.
- 전역 scaffold나 app-level composition local이 필요하면 이 레벨에서 다룬다.
- 개별 feature의 세부 UI나 비즈니스 로직을 직접 넣지 않는다.
- graph를 composable 본문이나 recomposition 경로에서 생성하지 않는다.

Graph와 navigation 상태의 기본 경계, preview/test seam은 `app-runtime.md`를 따른다.

## `ui/theme`

Theme 관련 코드는 `ui/theme` 아래에 둔다.

예상 파일:

- `Theme.kt`
- `Colors.kt`
- `Typography.kt`
- `Dimensions.kt`

Theme는 [`../../DESIGN.md`](../../DESIGN.md), Stitch 결과물, 화면 기획 문서를 기준으로 갱신한다.

## `ui/component`

여러 feature에서 실제로 재사용되는 component만 `ui/component`로 올린다.

한 feature 안에서만 쓰는 component는 먼저 해당 feature의 `components/` 아래에 둔다. 재사용 가능성이 있다는 이유만으로 공통 component로 올리지 않는다.

## Navigation

Navigation 관련 코드는 `commonMain/ui/navigation` 아래에 둔다.

Android와 iOS는 가능한 동일한 화면 흐름을 가진다. Compose Multiplatform Navigation 3 의존성과 공통 runtime은 구현되어 있으며 현재 정책은 `app-runtime.md`를 따른다.

현재 공통 runtime 파일:

- `AppNavKey.kt`
- `AppNavigation.kt`
- `AppNavigationState.kt`

### `AppNavKey.kt`

`AppNavKey.kt`는 앱에서 사용하는 screen key를 모아두는 파일이다.

- key는 navigation 구현에 맞는 `NavKey` 형태로 둔다.
- 저장·복원이 필요한 key에는 안정적인 식별자만 넣고 직렬화 가능하게 만든다.
- route string을 화면 곳곳에 흩뿌리지 않는다.

### `AppNavigation.kt`

`AppNavigation.kt`는 앱 navigation 상태와 entry mapping을 정의한다.

- Screen callback을 기준으로 화면 이동을 처리한다.
- 현재 단일 root와 transient overlay back stack을 관리한다.
- 직렬화 가능한 key와 `SavedStateConfiguration`으로 back stack을 저장·복원한다.
- saveable-state와 ViewModelStore entry decorator를 사용하고 MyPage ViewModel을 entry scope에 둔다.
- ViewModel이 `NavBackStack` 또는 동등한 navigation state를 직접 다루지 않게 한다.

전체 runtime 구성, state restoration, deep link와 product-flow의 결정 경계는 `app-runtime.md`에서 관리한다. Deep link와 독립 multi-back-stack 등 미구현 범위는 후속 기능에서 당시 호환 API를 기준으로 결정한다.

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
    MainContentState.kt # optional
    MainViewModel.kt
```

각 파일의 책임은 다음과 같다.

| File | Responsibility |
| --- | --- |
| `*Screen.kt` | ViewModel state 수집, event callback 연결, navigation callback 전달 |
| `*Content.kt` | stateless UI rendering |
| `*UiState.kt` | 한 화면의 전체 UI 상태를 표현하는 single state container |
| `*ContentState.kt` | 주요 콘텐츠 상태가 복잡할 때만 사용하는 optional sealed state |
| `*ViewModel.kt` | 화면 상태 관리, repository/usecase 호출, UI event 처리 |
| `components/*` | 해당 feature 내부에서만 쓰는 하위 composable |

Screen은 ViewModel과 UI를 연결한다. Content는 가능하면 순수하게 `uiState`와 callback만 받아 화면을 그린다.

예시:

```kotlin
@Composable
fun MainScreen(
    viewModel: MainViewModel,
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

DI는 Metro DI와 MetroX ViewModel integration을 사용한다. Platform owner가 생성한 `AppGraph`는 `ViewModelGraph`를 확장하고, 공통 `App`이 graph의 `MetroViewModelFactory`를 `LocalMetroViewModelFactory`에 한 번 제공한다. 각 ViewModel은 `@ViewModelKey`와 `@ContributesIntoMap(AppScope::class)`로 provider map에 기여하며 Screen은 `metroViewModel()`로 현재 Navigation entry의 `ViewModelStoreOwner`에 속한 instance를 얻는다. `NavigationContent`는 graph, factory 또는 owner를 parameter로 전달하지 않는다. Feature composable은 app graph를 생성하거나 service locator처럼 조회하지 않으며, runtime parameter가 필요한 ViewModel의 assisted creation은 해당 기능 요구가 생길 때 MetroX 계약으로 추가한다.

## UI State Rules

- 화면 상태는 한 화면당 하나의 `UiState` data class로 표현하며, `UiState`는 화면 전체의 render snapshot이다.
- 기본적으로는 `isLoading`, content, generic error처럼 필요한 단순 field만 사용한다.
- loading, empty, error, content가 배타적이면서 각 상태별 data/표시 규칙이 많아 단순 field만으로 읽기 어렵거나 잘못된 조합이 생길 때만 feature-local sealed `ContentState`를 도입한다.
- `ContentState`는 주요 콘텐츠 영역의 optional 하위 상태다. 전체 화면 snapshot인 `UiState`를 대체하거나 `UiState`를 다시 포함하지 않는다.
- Domain Model은 presentation 변환이 필요하지 않으면 `UiState`의 content로 직접 사용할 수 있다.
- 날짜/시간 표시, 상태 label·icon, 화면용 그룹화, 선택·확장 상태가 필요할 때만 UI Layer에 UiModel을 만든다. UiModel은 Domain Model을 포함할 수 있지만 Domain Layer로 전달하지 않는다.
- ViewModel은 repository의 `AppResult`를 success 또는 generic error `UiState`로 변환한다. UI는 raw exception, HTTP code, Data Layer failure type을 해석하지 않는다.
- UI event는 explicit ViewModel function callback으로 전달한다. 초기 구조에는 `UiAction`, `Effect`, reducer, Channel/SharedFlow 기반 one-off event stream을 도입하지 않는다.
- 재시도는 해당 화면 요구가 있을 때만 명시적인 UI event로 추가한다. 자동 재시도와 failure type별 화면 분기는 초기 규칙에 포함하지 않는다.
- navigation event는 ViewModel이 직접 실행하지 않고 Screen callback 또는 `AppNavigation`이 처리한다.
- 초기에는 사용자의 직접 입력으로 발생하는 navigation을 Screen callback으로 처리한다. 비동기 작업 성공 뒤 자동 navigation이 필요한 기능은 해당 기능에서 state 기반 계약을 별도로 설계한다.
- Toast·Snackbar는 UI behavior다. 공통 message 구현은 지금 정의하지 않으며, 실제 화면 요구가 생길 때 Compose UI 방식과 필요한 platform 위치를 함께 결정한다.
