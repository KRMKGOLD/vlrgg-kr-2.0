# App Runtime Composition

## 목적과 적용 상태

이 문서는 Android와 iOS가 공유하는 앱 runtime의 현재 composition과 navigation 정책을 기록한다. 기준은 원격 `main`의 `6cda972`이며, feature 화면의 제품 요구사항은 `docs/feature/`가 소유한다.

현재 공통 앱에는 Metro DI와 Compose Multiplatform Navigation 3 기반 runtime이 구현되어 있다. 다만 MyPage의 entry-scoped ViewModel skeleton을 제외한 root/Search/detail content는 marker와 sample navigation button 단계이며, 실제 feature UI와 data 연동이 구현되었다는 의미는 아니다.

## Runtime composition

1. Android `MainActivity`와 iOS SwiftUI `iOSApp`이 Compose recomposition 경로 밖에서 `AppGraph`를 한 번 생성한다. Android는 Activity-owned lazy graph, iOS는 app-owned graph를 사용한다.
2. 플랫폼 host는 graph를 공통 `App(graph)`에 전달한다. 공통 `App`은 `VlrTheme` 안에서 `AppNavigation(graph)`를 연결하며 graph를 새로 만들지 않는다.
3. Metro `AppGraph`는 `AppViewModelFactory`를 제공한다. 현재 factory는 MyPage ViewModel만 생성하며, MyPage entry가 Navigation 3의 `ViewModelStoreOwner`로부터 entry-scoped ViewModel을 얻는다.
4. feature composable은 별도의 app graph나 전역 service locator를 만들지 않는다. Screen은 callback으로 navigation 의도를 전달하고 ViewModel은 back stack을 직접 조작하지 않는다.

## Navigation 3 정책

- root key 순서는 News, Matches, MyPage, Events, About이며 기본 destination은 `MyPageRoot`다.
- Search와 News/Match/Event/Team/Player/Series detail은 현재 root 위에 push되는 transient overlay다.
- runtime은 `rememberNavBackStack`과 app 전용 `SavedStateConfiguration`을 사용한다. 모든 key는 직렬화 가능하며 detail key에는 복원에 필요한 안정적인 식별자만 둔다.
- root 선택은 기존 overlay를 모두 비우고 선택한 root로 교체한다. Back은 overlay가 있을 때 마지막 entry만 pop하며 root에서는 stack을 변경하지 않는다.
- `NavDisplay`는 entry provider, saveable-state entry decorator, ViewModelStore entry decorator를 연결한다.
- navigation owner가 back stack을 소유하고 `AppNavigationState`는 같은 stack에서 선택 root와 overlay를 파생해 상태 전이를 수행한다. 별도의 두 번째 navigation state를 유지하지 않는다.

## 아직 확정하거나 구현하지 않은 항목

- 탭별 독립 multi-back-stack
- deep link와 제품 route 문자열 binding
- adaptive scene, 인증 흐름
- MyPage 외 feature ViewModel binding과 app/feature 공유 state scope
- 실제 News/Matches/Events/Search/Team/Player/Series/About UI와 data loading

이 항목은 후속 기능 요구사항과 당시 라이브러리 API를 확인해 결정한다. Navigation 구조를 변경할 때는 현재 root/overlay 동작과 저장·복원 영향을 함께 검토한다.

## 현재 검증 근거

- `AppNavKeySerializationTest`: root/Search/detail key 직렬화와 복원
- `AppNavigationStateTest`: root 선택, overlay push/pop, 복원된 stack 정책
- `DestinationDescriptorTest`: root 순서와 destination metadata
- `MyPageViewModelTest`: MyPage skeleton state
- `AppGraphAndroidHostTest`: Metro graph와 entry별 ViewModelStore scope

이 테스트는 runtime 기반의 회귀 검증이다. 실제 feature 화면의 동작이나 physical Android/iOS 시각·접근성을 검증한 것으로 해석하지 않는다.

## 관련 문서

- [App Architecture](app-arch.md): module 책임과 공통 package 방향
- [UI Layer](ui-layer.md): Screen callback, ViewModel, feature UI 규칙
- [Data Layer](data-layer.md): data binding과 repository 경계

## 공식 참고

- [Compose Multiplatform Navigation 3](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Navigation 3 overview](https://developer.android.com/guide/navigation/navigation-3)
- [Compose Multiplatform ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)
- [Metro documentation](https://zacsweers.github.io/metro/latest/)
