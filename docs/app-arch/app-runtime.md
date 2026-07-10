# App Runtime Composition

## 목적과 적용 상태

이 문서는 Android와 iOS가 공유하는 앱 런타임의 소유권 계약을 정의한다. 대상은 Compose root, Metro app graph, Navigation 3 back stack의 생성 위치와 수명이다.

현재 저장소의 앱 코드는 최소 Compose Multiplatform 템플릿에 가깝고, 아래 계약의 실제 Metro graph와 Navigation 3 구현은 아직 존재하지 않을 수 있다. 따라서 이 문서는 **현재 구현 설명**이 아니라 이후 앱 기능이 붙을 때 지켜야 할 **목표 런타임 계약**이다. Gradle 의존성, 실제 graph annotation/binding, 화면 key, 딥링크는 각각의 구현 작업에서 추가한다.

## 확정 런타임 흐름

```text
Android Application 또는 iOS root host
  ├─ 플랫폼 입력 준비
  ├─ AppGraph를 앱 수명당 한 번 생성
  └─ Compose root에 App(graph) 전달
       └─ LocalMetroViewModelFactory에 graph.metroViewModelFactory 제공
            └─ AppNavHost
                 ├─ 단일 NavBackStack<NavKey> 소유
                 ├─ NavDisplay/entryProvider 구성
                 └─ Screen callback으로 push/pop 처리
```

플랫폼 owner가 composition root다. `AppGraph`를 composable 본문이나 recomposition 경로에서 생성하지 않는다. 공통 `App(graph)`는 이미 생성된 graph를 받아 Compose tree에 연결하고, `AppNavHost`는 앱의 유일한 최상위 back stack을 소유한다.

## 소유권과 수명

| 객체 | 생성 owner | 목표 수명 | 금지 사항 |
| --- | --- | --- | --- |
| `AppGraph` | Android `Application`/iOS root host | 해당 앱 root가 살아 있는 동안 한 인스턴스 | `App()` 또는 feature composable에서 생성 |
| Metro ViewModel factory 제공 | 공통 `App(graph)` | 해당 Compose tree | feature마다 별도 root factory 제공 |
| `NavBackStack<NavKey>` | `AppNavHost` | root composition과 saveable state 수명 | ViewModel이나 개별 Screen이 최상위 stack 소유 |
| destination key | `AppNavKey` hierarchy | back stack entry의 저장 가능 상태 | route 문자열을 화면마다 별도 관리 |

Android의 configuration change나 Compose recomposition은 graph를 다시 만드는 이유가 아니다. Android process death와 iOS root host의 완전한 재생성처럼 플랫폼 앱 수명 owner 자체가 사라진 뒤에는 새 graph를 만든다. Graph 내부 객체를 디스크에 직렬화하지 않으며, 저장 가능한 navigation key/state만 복원한다.

## 공통 앱과 플랫폼 입력

목표 공통 진입점은 다음 책임만 가진다.

```kotlin
@Composable
fun App(graph: AppGraph) {
    AppTheme {
        CompositionLocalProvider(
            LocalMetroViewModelFactory provides graph.metroViewModelFactory,
        ) {
            AppNavHost()
        }
    }
}
```

위 코드는 목표 계약을 보여 주는 예시다. 중요한 불변식은 `AppGraph : ViewModelGraph`인 root graph를 플랫폼 owner가 만들고, graph가 노출하는 `metroViewModelFactory`를 공통 root의 `LocalMetroViewModelFactory`에 한 번 제공한다는 것이다.

플랫폼에서 공통 graph 생성에 넘길 수 있는 입력은 다음으로 제한한다.

- Android `Context`, iOS storage directory처럼 공통 코드가 직접 만들 수 없는 platform service/factory
- platform-specific logging, clock, dispatcher처럼 graph binding에 실제로 필요한 좁은 계약
- 테스트가 교체할 수 있는 명시적 runtime dependency

Activity, UIViewController/SwiftUI view, permission launcher 같은 UI host 자체를 공통 graph에 넣지 않는다. 플랫폼 입력이 아직 필요하지 않다면 불필요한 wrapper나 `expect`/`actual`을 미리 만들지 않는다.

## Metro 목표 계약

- `commonMain/di/AppGraph.kt`의 root graph는 Metro `ViewModelGraph`를 확장한다.
- app/data binding은 root graph에서 도달 가능하게 구성하되, 구현체는 constructor injection을 우선한다.
- `AppGraph` 인스턴스는 플랫폼 앱 수명 owner가 한 번 생성하고 `App(graph)`로 전달한다.
- 공통 root는 `graph.metroViewModelFactory`를 `LocalMetroViewModelFactory`에 제공하여 하위 Screen의 `metroViewModel()`이 동일 root graph를 사용하게 한다.
- preview와 테스트는 production graph 생성 함수를 숨겨 호출하지 않고, 명시적으로 대체 graph 또는 필요한 factory를 주입한다.
- 실제 Metro annotation, scope 이름, platform binding topology, dependency 추가는 첫 DI 구현 작업의 범위다.

Metro graph의 구체적인 annotation과 생성 API는 사용 버전의 [Dependency graphs](https://zacsweers.github.io/metro/latest/dependency-graphs/), [`ViewModelGraph` API](https://zacsweers.github.io/metro/latest/api/metrox-viewmodel/dev.zacsweers.metrox.viewmodel/-view-model-graph/index.html), [`LocalMetroViewModelFactory` API](https://zacsweers.github.io/metro/latest/api/metrox-viewmodel-compose/dev.zacsweers.metrox.viewmodel/-local-metro-view-model-factory.html)를 따른다.

## Navigation 3 목표 계약

### Key와 단일 back stack

모든 최상위 destination key는 직렬화 가능한 하나의 hierarchy로 관리한다.

```kotlin
@Serializable
sealed interface AppNavKey : NavKey {
    @Serializable
    data object Main : AppNavKey

    @Serializable
    data class MatchDetail(val matchId: String) : AppNavKey
}
```

`AppNavHost`가 root back stack을 생성하고 `NavDisplay`에 전달한다. `rememberNavBackStack`은 type argument를 쓰는 API가 아니다. KMP 공통 코드는 `SavedStateConfiguration`을 첫 argument로, 초기 key를 그 뒤의 vararg로 전달한다.

```kotlin
@Composable
fun AppNavHost() {
    val backStack = rememberNavBackStack(
        appNavSavedStateConfiguration,
        AppNavKey.Main,
    )

    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<AppNavKey.Main> {
                MainScreen(
                    onMatchClick = { matchId ->
                        backStack.add(AppNavKey.MatchDetail(matchId))
                    },
                )
            }
            entry<AppNavKey.MatchDetail> { key ->
                MatchDetailScreen(
                    matchId = key.matchId,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
```

ViewModel은 key나 stack을 직접 push/pop하지 않는다. Screen이 사용자 이벤트를 명시적 callback으로 올리고 `AppNavHost`가 stack mutation을 수행한다.

### NavEntry-scoped ViewModel

Root `LocalMetroViewModelFactory`는 ViewModel 생성 방법을 제공하지만 ViewModel 수명 자체를 destination에 묶지는 않는다. `AppNavHost`는 각 Navigation 3 entry에 별도 `ViewModelStoreOwner`가 생기도록 `NavDisplay.entryDecorators`를 다음 순서로 설치한다.

1. `rememberSaveableStateHolderNavEntryDecorator()`
2. `rememberViewModelStoreNavEntryDecorator()`

이 순서를 통해 entry 내부의 `metroViewModel()`은 root Metro factory를 사용하면서도 해당 `NavEntry`의 ViewModel store에 저장된다. entry가 back stack에서 제거되면 그 entry에 scope된 ViewModel도 정리되어야 한다. decorator를 생략해 ViewModel이 Activity나 root host 전체에 의도치 않게 scope되도록 하지 않는다.

Compose Multiplatform의 `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` dependency는 실제 Navigation 3 구현 작업에서 Navigation 3 dependency와 함께 도입한다. 정확한 version은 그 작업 시점의 Compose Multiplatform 호환표를 기준으로 결정한다.

### KMP state restoration

Navigation 3 back stack 저장은 Kotlin serialization을 사용한다. 모든 key에 `@Serializable`을 붙이는 것만으로 끝나지 않는다. non-JVM target에서 polymorphic `NavKey`를 복원하려면 single-module sealed `AppNavKey` hierarchy를 등록한 `SerializersModule`과 그 module을 사용하는 `SavedStateConfiguration`을 root navigation 저장 경계에 전달해야 한다.

```kotlin
val appNavSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclassesOfSealed<AppNavKey>()
    }
}

val appNavSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = appNavSerializersModule
}
```

KMP 공통 호출은 `rememberNavBackStack(appNavSavedStateConfiguration, AppNavKey.Main)` 형태로 configuration을 반드시 전달한다. configuration 없는 overload는 Android의 reflection 기반 편의 API이므로 commonMain 계약으로 사용하지 않는다. 새 key는 single-module sealed `AppNavKey` hierarchy 안에 `@Serializable`로 선언하고 Android/iOS 복원 테스트를 함께 갱신한다. key에는 복원에 필요한 안정적인 식별자만 넣고 repository 객체, DTO, platform handle, 큰 화면 snapshot을 넣지 않는다.

Navigation 3의 기본 back stack/저장 방식은 [Save and restore state](https://developer.android.com/guide/navigation/navigation-3/save-state)와 [`rememberNavBackStack` API](https://developer.android.com/reference/kotlin/androidx/navigation3/runtime/rememberNavBackStack.composable)를, multiplatform 설정 객체는 [`SavedStateConfiguration` API](https://developer.android.com/reference/kotlin/androidx/savedstate/serialization/SavedStateConfiguration)를 기준으로 한다.

## Preview와 테스트 seam

- Content composable preview는 graph나 navigation host 없이 `UiState`와 callback을 직접 전달한다.
- Screen/AppNavHost 테스트가 필요하면 테스트 graph 또는 factory와 초기 key/back stack을 명시적으로 주입할 수 있는 좁은 seam을 둔다.
- production 전역 singleton/service locator를 preview에서 참조하지 않는다.
- navigation 복원 테스트는 최소한 초기 key, parameterized key, 여러 entry의 순서, pop 이후 상태를 검증한다.
- Android host와 iOS simulator에서 같은 sealed-hierarchy serializer configuration이 적용되는지 확인한다.

구체적인 overload나 test helper는 실제 navigation 구현과 함께 추가한다. 테스트 가능성을 이유로 두 번째 production back stack owner를 만들지는 않는다.

## 재생성 정책

| 사건 | Graph | Back stack |
| --- | --- | --- |
| recomposition | 기존 인스턴스 유지 | `rememberNavBackStack` 상태 유지 |
| Android configuration change | 플랫폼 앱 수명 owner가 가진 기존 graph 재사용 | saveable state로 유지/복원 |
| Android process death | 새 graph 생성 | configured serializer와 saved state로 key stack 복원 |
| iOS root host 유지 중 Compose 갱신 | 기존 graph 재사용 | 기존 stack 유지 |
| iOS root host 완전 재생성 | 새 graph 생성 | host가 보존한 SavedState가 있을 때만 복원 |

Navigation 복원과 data 복구는 다른 문제다. key가 복원된 뒤 화면 데이터는 ViewModel/repository가 식별자를 사용해 다시 로드한다.

## 비목표와 결정 경계

이 문서에서 결정하지 않는다.

- 실제 Gradle dependency version과 build file 변경. 단, Navigation 3 구현 시 CMP `lifecycle-viewmodel-navigation3`를 함께 도입한다는 계약은 확정이다.
- Android `Application`/Activity, iOS SwiftUI/UIKit의 구체적인 파일 topology
- 전체 destination 목록과 제품 화면 흐름
- deep link URI parsing, 인증 gate, multiple back stacks, dialog/list-detail scene
- DataStore/Room/network binding과 platform factory의 실제 구현
- feature ViewModel, repository, 화면 기능 구현

위 항목은 해당 기능 작업의 요구사항과 현재 코드 구조를 확인해 별도 문서나 ADR로 결정한다. 다만 어떤 topology를 선택해도 `플랫폼 owner가 graph를 한 번 생성해 App(graph)에 전달`, `AppNavHost가 root stack을 단독 소유`, `non-JVM serializer configuration을 명시`, `NavEntry별 ViewModelStoreOwner를 제공`하는 계약은 유지한다. 이 불변식을 바꾸려면 이 문서와 관련 layer 문서를 같은 PR에서 갱신한다.

## 완료 조건

첫 runtime 구현은 다음을 모두 만족할 때 완료로 본다.

- Android와 iOS root host가 각각 앱 수명당 `AppGraph`를 한 번 만들고 공통 `App(graph)`에 전달한다.
- graph 생성이 composable/recomposition 경로 밖에 있다.
- `AppGraph : ViewModelGraph`이며 root `LocalMetroViewModelFactory` 제공이 한 곳에 있다.
- `AppNavHost`만 root back stack을 소유하고 Screen/ViewModel은 callback 계약을 지킨다.
- `NavDisplay.entryDecorators`에 `rememberSaveableStateHolderNavEntryDecorator()`와 `rememberViewModelStoreNavEntryDecorator()`를 이 순서로 설치하고, entry 제거 시 해당 Metro ViewModel이 정리되는지 검증한다.
- 실제 Navigation 3 구현에서 CMP `lifecycle-viewmodel-navigation3` dependency를 함께 도입한다.
- 모든 key가 sealed `AppNavKey` hierarchy 아래 `@Serializable`이며, KMP 초기화는 `rememberNavBackStack(appNavSavedStateConfiguration, AppNavKey.Main)` 형태를 사용한다.
- non-JVM용 `SerializersModule`이 `subclassesOfSealed<AppNavKey>()`를 구성하고, 이를 담은 `SavedStateConfiguration`으로 Android 및 iOS 복원을 검증한다.
- preview/test는 production global을 우회하지 않고 명시적 graph/factory/state seam을 사용한다.
- 실제 구현과 문서가 함께 검증되고, 이 문서의 비목표가 무단으로 포함되지 않는다.

## 공식 참고

- [Compose Multiplatform ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)
- [Compose Multiplatform Navigation 3](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Navigation 3 NavEntry decorators](https://developer.android.com/guide/navigation/navigation-3/naventrydecorators)
- [Navigation 3 save and restore state](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Metro `ViewModelGraph` API](https://zacsweers.github.io/metro/latest/api/metrox-viewmodel/dev.zacsweers.metrox.viewmodel/-view-model-graph/index.html)
- [Metro `LocalMetroViewModelFactory` API](https://zacsweers.github.io/metro/latest/api/metrox-viewmodel-compose/dev.zacsweers.metrox.viewmodel/-local-metro-view-model-factory.html)
