# App Runtime Composition

## 목적과 적용 상태

이 문서는 Android와 iOS가 공유하는 앱 runtime의 composition, lifecycle, configuration, navigation 정책을 기록한다. feature 화면의 제품 요구사항은 `docs/feature/`가 소유한다.

현재 공통 앱에는 Metro DI와 Compose Multiplatform Navigation 3 기반 runtime이 구현되어 있다. Issue #37 N1부터 News, Matches, MyPage, Events, About은 독립 saved back stack과 독립 entry decorator state를 가진다. News, Matches, Events root와 News/Event Detail은 실제 feature UI와 data에 연결되어 있고, Match Detail은 상태 보존 검증을 위한 placeholder다. 나머지 marker 화면은 feature 구현 완료를 의미하지 않는다.

이 문서에서 runtime은 앱 composition과 lifetime 정책을 의미하며 별도 `AppRuntime` wrapper 타입을 뜻하지 않는다.

Issue #33 H1-K0의 runtime kernel 방향은 [ADR-0001](adr/0001-thin-app-runtime-kernel.md)로 확정되었다. platform configuration, application-owned graph, MetroX ViewModel integration과 최소 `AppResult` failure/cancellation 경계가 반영되었다. News, Matches, Events의 feature repository와 remote data source는 구현되어 있으며, 나머지 feature의 data 연결은 후속 feature 구현 범위다.

## 현재 Runtime composition

1. Android `Application`과 iOS SwiftUI `iOSApp`의 reference-type owner가 Compose recomposition 경로 밖에서 `AppGraph`를 생성한다. Android는 process-owned lazy graph, iOS는 `@StateObject`가 유지하는 app-owned graph를 사용한다.
2. 플랫폼 host는 graph를 공통 `App(graph)`에 전달한다. 공통 `App`은 graph의 `metroViewModelFactory`를 `LocalMetroViewModelFactory`에 제공한 뒤 `VlrTheme`과 `AppNavigation()`을 연결하며 graph를 UI 하위 계층으로 전달하거나 새로 만들지 않는다.
3. Metro `AppGraph`는 `ViewModelGraph`를 확장하고 app-scoped `AppViewModelFactory`는 공식 `MetroViewModelFactory`를 구현한다. News, Events, MyPage의 ViewModel과 Matches의 assisted ViewModel factory, News/Event Detail의 manual assisted factory가 keyed map multibinding으로 provider를 기여하며 각 Screen의 ViewModel 조회가 Navigation 3 entry의 `ViewModelStoreOwner`에 instance를 보관한다. Matches와 Event Detail은 `CreationExtras`에서 만든 `SavedStateHandle`로 선택 탭을 복원하고, Event Detail은 같은 manual assisted creation에서 runtime `eventId`도 전달한다.
4. `NetworkConfig`와 app-scoped Ktor client provider는 graph에 연결되어 있고 News, Matches, Events repository가 이를 사용한다. 별도 `AppRuntime`이나 `shutdown()` API는 확정 계약에 포함하지 않는다.

## H1-K0 확정 Runtime kernel 계약

### Composition과 lifecycle

- immutable configuration을 Metro graph input으로 전달하고 `@SingleIn(AppScope::class)` binding으로 graph당 Ktor client 하나를 제공한다.
- Android `Application`이 process lifetime의 graph owner다. `Activity`는 graph를 전달받을 뿐 생성하거나 교체하지 않으므로 configuration change와 Activity recreation에서 같은 graph/client가 유지된다.
- iOS SwiftUI `App`은 reference-type graph owner를 `@StateObject`로 소유한다. `WindowGroup`, root view, scene background/foreground 전환은 graph/client lifetime 경계가 아니다.
- OS가 process를 종료하면 graph와 client 자원도 함께 회수된다. platform 종료 callback 호출을 correctness 조건으로 두지 않는다.
- 별도 `AppRuntime`과 production `shutdown()` API는 만들지 않는다. process 실행 중 graph 교체가 필요해지면 client 정리 정책과 함께 새 ADR로 결정한다.

### API base URL configuration

- `commonMain`은 platform configuration API를 직접 읽지 않고 `apiBaseUrl`을 명시적 입력으로 받는다.
- Android는 AGP 9 generated `BuildConfig`와 Gradle property를 사용한다. 현재 emulator local 값은 `http://10.0.2.2:8080`이다.
- iOS는 xcconfig build setting을 `Info.plist`로 확장해 읽는다. 현재 simulator local 값은 `http://127.0.0.1:8080`이다.
- local cleartext HTTP는 debug/local 구성에만 허용한다. 배포 build는 HTTPS endpoint를 저장소 밖에서 주입하고 tracked source에 실제 host를 기본값으로 두지 않는다.
- endpoint는 배포 binary에서 추출할 수 있으므로 secret이 아니다. non-commit 정책은 구성 위생이며 DDoS·남용 방어는 [GitHub Issue #52](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/52)에서 다룬다.

### 구성 주입

- Android Debug의 tracked 기본값은 `http://10.0.2.2:8080`이고 Release의 tracked 기본값은 빈 문자열이므로 공통 configuration 검증에서 실패한다. Gradle property와 환경 변수 provider seam은 존재하지만 외부 값을 generated Java 문자열 리터럴로 변환하는 지원은 후속 작업이며, 현재 검증된 주입 경로로 간주하지 않는다.
- iOS Debug는 `http://127.0.0.1:8080`을 기본으로 한다. 로컬 override는 ignored `Configuration/Config.local.xcconfig`에서 configuration별 `API_BASE_URL`을 설정하거나, 빌드 시 `API_BASE_URL=https://example.invalid`을 전달한다. Release에는 외부 HTTPS 값을 제공해야 한다.
- iOS 외부 주입 예: `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release API_BASE_URL=https://example.invalid build`.

### MetroX ViewModel provider map

- 별도 registry class나 `Set<Entry>` 변환을 만들지 않고 MetroX의 `Map<KClass<out ViewModel>, () -> ViewModel>` multibinding을 사용한다.
- 각 feature ViewModel은 concrete class에 `@ViewModelKey`와 `@ContributesIntoMap(AppScope::class)`를 선언한다. 새 ViewModel 추가는 contribution 추가로 끝나며 factory나 central branch를 수정하지 않는다.
- registered/missing lookup은 `MetroViewModelFactory`의 runtime 계약을 따른다. duplicate key와 standard contribution type 오류는 Metro graph compilation에서 실패한다.
- 공통 `App`만 `LocalMetroViewModelFactory`를 제공하며 feature와 navigation content는 graph나 factory를 parameter로 전달하지 않는다.
- `metroViewModel()`은 Navigation entry decorator의 `LocalViewModelStoreOwner`를 사용한다. 따라서 app-scoped factory와 별개로 ViewModel instance scope는 navigation entry가 소유한다.

### Failure와 cancellation

- Repository boundary는 `AppResult.Success<T>`와 단일 `AppResult.Failure`를 유지한다.
- `AppResult` contract는 `commonMain/domain/AppResult.kt`에 두고, 예외 변환은 `data/repository`의 internal repository boundary helper `wrapAsAppResult`로 제한한다. News, Matches, Events repository가 이 helper를 사용하며, remote data source는 DTO를 직접 반환하고 예외를 변환하지 않는다. `wrapAsAppResult`는 `CancellationException`을 다시 던지고, 그 외 `Exception`만 `AppResult.Failure`로 변환한다. 다른 feature의 repository/data source 구현은 각 feature 범위에서 추가한다.
- non-cancellation network/HTTP/serialization 실패는 repository implementation에서 failure로 변환한다.
- `CancellationException`은 failure로 삼키지 않고 전파한다.
- raw exception, HTTP code, server 내부 메시지와 parser 세부사항은 ViewModel/UI에 노출하지 않는다.

## Navigation 3 정책

- root key 순서는 News, Matches, MyPage, Events, About이며 기본 destination은 `MyPageRoot`다.
- Search와 News/Match/Event/Team/Player/Series detail은 진입한 root의 stack 위에 push되는 transient overlay다. root를 바꿔도 그 overlay는 원래 root에 남고, 해당 root로 돌아온 뒤 Back을 누르면 그 root의 이전 entry로 복귀한다.
- runtime은 root마다 별도 `rememberNavBackStack`과 app 전용 `SavedStateConfiguration`을 사용한다. root 목록을 순회해 stack/decorator map을 만들며, 선택 root는 `rememberSaveable`의 단일 mutable state로 보존한다. `AppNavigationState`는 그 state를 직접 갱신하므로 별도의 Compose selected-root state나 수동 동기화가 없다. 모든 key는 직렬화 가능하고 detail key에는 복원에 필요한 안정적인 식별자만 둔다.
- root마다 별도 `rememberDecoratedNavEntries`, `SaveableStateHolderNavEntryDecorator`, `ViewModelStoreNavEntryDecorator`를 생성한다. 선택 root의 entries만 `NavDisplay`에 전달하되 선택되지 않은 root의 stack과 decorator state는 composition 안에 남아 ViewModel, loaded page/selected tab, scroll과 `rememberSaveable` state가 유지된다.
- root 전환은 200 ms fade-in/fade-out으로 표시한다. transition은 root stack이나 decorator 소유권 바깥의 keyed display host에만 적용하므로, 전환 중 이전·새 root의 화면을 함께 표시해도 각각의 entry state는 그대로 유지된다.
- root 전환은 각 stack을 보존한다. 현재 root를 다시 선택하면 그 root의 overlay만 root entry까지 pop한다. Back은 선택 root에 overlay가 있을 때 마지막 entry만 pop하며 root에서는 stack을 변경하지 않는다.
- overlay가 두 root에서 같은 destination key를 가져도 decorator state가 공유되지 않도록 entry content key는 owning root와 entry instance ID를 포함한 안정적인 `String`을 사용한다. `String`은 Android `Bundle`에 저장할 수 있어 entry decorator state 복원에서 custom Kotlin object를 전달하지 않는다.
- navigation owner가 모든 root back stack과 selected root를 소유하고 `AppNavigationState`는 정확히 그 stack instances에서 현재 root와 overlay를 파생해 상태 전이를 수행한다. 복원 map은 정확히 다섯 root를 포함하고 각 stack은 자신이 소유한 root entry로 시작해야 한다.
- feature composable은 별도의 app graph나 전역 service locator를 만들지 않는다. Screen은 callback으로 navigation 의도를 전달하고 ViewModel은 back stack을 직접 조작하지 않는다.

## 제외 및 후속 결정

- deep link와 제품 route 문자열 binding
- adaptive scene, 인증 흐름
- 실제 Match Detail, Search/Team/Player/Series/About UI와 data loading
- pagination framework와 공통 cache/storage
- production API URL, hosting provider, server-side DDoS·남용 방어

Pagination은 여러 feature에서 사용될 가능성이 있지만 아직 100% 공통 계약이 아니므로 후속 이슈에서 실제 사용처를 기준으로 결정한다. 나머지 항목도 기능 요구사항과 당시 library API를 확인해 결정한다.

## 검증 계약

현재 구현의 회귀 근거:

- `AppNavKeySerializationTest`: root/Search/detail key, 모든 root stack과 selected root의 직렬화·복원, root·entry-instance별 content key 고유성
- `AppNavigationStateTest`: root별 overlay push/pop, root 전환/reselection, 동일 stack instance 유지, 잘못 복원된 map/stack 거부
- `AppNavigationRuntimeUiTest` (iOS): 실제 `AppNavigationRuntime` seam에 test entry content를 주입해 entry `LocalViewModelStoreOwner`와 test-local `ViewModelProvider.Factory`를 검증한다. root 왕복 후 loaded page/selected tab·동일 ViewModel instance·`rememberSaveable` counter·`LazyListState.firstVisibleItemIndex`가 유지되고, detail pop은 detail ViewModel만 clear하며 initiating root state를 보존한다. 두 root의 동일 `Search` key는 saveable state/ViewModel을 공유하지 않고, 한 root Search pop은 다른 root Search ViewModel을 clear하지 않는다.
- `SharedLogicAndroidHostTest`: Navigation entry content key가 Android `Bundle` 호환 `String`임을 검증한다.
- `DestinationDescriptorTest`: root 순서와 destination metadata
- `MyPageViewModelTest`: MyPage skeleton state
- `MatchesViewModelTest`: 두 feed의 독립 최초 로딩·새로고침·페이지네이션·중복 제거·취소와 선택 탭 `SavedStateHandle` 복원
- `MatchesContentUiTest` (iOS): Matches 상태별 rendering, card 필드와 click, refresh/pagination retry, 알림 UI 부재
- `EventsViewModelTest`: Events 최초 로딩·재시도·새로고침 중 콘텐츠 유지와 취소된 요청의 늦은 결과 무시
- `EventsContentUiTest` (iOS): Events 상태별 rendering, event click, 새로고침 중 콘텐츠·progress 표시
- `MatchesNavigationRuntimeUiTest` (iOS): 실제 Matches root의 두 feed·선택 탭·탭별 scroll·loaded data를 root/detail 왕복에서 보존하고 Match Detail key를 검증
- `EventDetailViewModelTest`: identity failure와 tab-local failure 분리, Matches 기본·lazy tab load·중복 요청 방지·독립 retry·selected tab `SavedStateHandle` 복원
- `EventDetailContentUiTest` (iOS): Matches/News/Stats 상태와 navigation callback, Stats metric 순서·null marker·Player identity target 검증
- `EventDetailNavigationRuntimeUiTest` (iOS): Event Detail selected tab·loaded data·scroll을 root/News Detail 왕복에서 보존하고 News Detail key를 검증
- `AppGraphAndroidHostTest`: MetroX known/missing provider lookup과 entry별 `ViewModelStoreOwner` scope
- `NetworkBindingTest`: graph 내부 client singleton, graph 간 client 독립성과 요청 base URL 적용
- `RepositoryResultTest`: success/failure 변환과 `CancellationException` 재전파

H1-K1 완료까지 추가할 검증:

- Android/iOS platform entry의 exact base URL 전달과 invalid/missing configuration 실패
- Android Activity recreation에서 Application-owned graph 유지
- iOS scene background에서 app-owned graph 유지

회귀 검증 시 `:app:shared:testAndroidHostTest`, `:app:shared:iosSimulatorArm64Test`, Android assemble과 iOS target compile을 함께 실행한다.

이 테스트들은 runtime 기반의 회귀 검증이다. 실제 feature 화면 동작이나 physical Android/iOS 시각·접근성을 검증한 것으로 해석하지 않는다.

## 관련 문서

- [ADR-0001: Thin App Runtime Kernel](adr/0001-thin-app-runtime-kernel.md): H1-K0의 확정 decision과 재검토 조건
- [ADR-0003: Root-specific saved Navigation stacks](adr/0003-root-specific-saved-navigation-stacks.md): Issue #37 N1의 multi-back-stack decision
- [App Architecture](app-arch.md): module 책임과 공통 package 방향
- [UI Layer](ui-layer.md): Screen callback, ViewModel, feature UI 규칙
- [Data Layer](data-layer.md): data binding과 repository 경계

## 공식 참고

- [Compose Multiplatform Navigation 3](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Navigation 3 overview](https://developer.android.com/guide/navigation/navigation-3)
- [Navigation 3 multiple back stacks](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Compose Multiplatform ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)
- [Metro dependency graph](https://zacsweers.github.io/metro/latest/dependency-graphs/)
- [MetroX ViewModel](https://zacsweers.github.io/metro/latest/metrox-viewmodel/)
- [MetroX ViewModel Compose](https://zacsweers.github.io/metro/latest/metrox-viewmodel-compose/)
- [Ktor client creation, reuse, and close](https://ktor.io/docs/client-create-and-configure.html)
