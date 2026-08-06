# App Runtime Composition

## 목적과 적용 상태

이 문서는 Android와 iOS가 공유하는 앱 runtime의 composition, lifecycle, configuration, navigation 정책을 기록한다. feature 화면의 제품 요구사항은 `docs/feature/`가 소유한다.

현재 공통 앱에는 Metro DI와 Compose Multiplatform Navigation 3 기반 runtime이 구현되어 있다. MyPage의 entry-scoped ViewModel skeleton을 제외한 root/Search/detail content는 marker와 sample navigation button 단계이며 실제 feature UI와 data 연동이 구현되었다는 의미는 아니다.

Issue #33 H1-K0의 runtime kernel 방향은 [ADR-0001](adr/0001-thin-app-runtime-kernel.md)로 확정되었으며 아직 코드에는 반영되지 않았다. 아래에서 `현재`는 repository 구현 상태, `확정 계약`은 H1-K0 구현이 충족해야 할 target을 의미한다.

## 현재 Runtime composition

1. Android `MainActivity`와 iOS SwiftUI `iOSApp`이 Compose recomposition 경로 밖에서 `AppGraph`를 생성한다. 현재 Android는 Activity-owned lazy graph, iOS는 app-owned graph를 사용한다.
2. 플랫폼 host는 graph를 공통 `App(graph)`에 전달한다. 공통 `App`은 `VlrTheme` 안에서 `AppNavigation(graph)`을 연결하며 graph를 새로 만들지 않는다.
3. Metro `AppGraph`는 `AppViewModelFactory`를 제공한다. 현재 factory는 MyPage ViewModel만 handwritten `when`으로 생성하며, MyPage entry가 Navigation 3의 `ViewModelStoreOwner`로부터 entry-scoped ViewModel을 얻는다.
4. 아직 Ktor client, base URL configuration, runtime shutdown owner는 graph에 연결되어 있지 않다.

## H1-K0 확정 Runtime kernel 계약

### Composition과 lifecycle

- 공통 runtime은 immutable configuration을 Metro graph input으로 받아 graph와 app-scoped Ktor client를 만든다.
- Android `Application`이 process lifetime의 runtime owner다. `Activity`는 graph를 전달받을 뿐 생성하거나 종료하지 않으므로 configuration change와 Activity recreation에서 같은 runtime/client가 유지된다.
- iOS SwiftUI `App`은 reference-type runtime owner를 `@StateObject`로 소유한다. `WindowGroup`, root view, scene background/foreground 전환은 runtime lifetime 경계가 아니다.
- OS 종료 callback에 resource correctness를 의존하지 않는다. 테스트와 통제된 runtime 교체를 위해 owner가 idempotent `shutdown()`을 제공하며 client는 정확히 한 번 닫힌다.
- 같은 runtime의 remote dependency는 하나의 Ktor client instance를 공유한다. 별도 runtime은 독립 client를 가진다.

### API base URL configuration

- `commonMain`은 platform configuration API를 직접 읽지 않고 `apiBaseUrl`을 명시적 입력으로 받는다.
- Android는 AGP 9 generated `BuildConfig`와 Gradle property를 사용한다. 현재 emulator local 값은 `http://10.0.2.2:8080`이다.
- iOS는 xcconfig build setting을 `Info.plist`로 확장해 읽는다. 현재 simulator local 값은 `http://127.0.0.1:8080`이다.
- local cleartext HTTP는 debug/local 구성에만 허용한다. 배포 build는 HTTPS endpoint를 저장소 밖에서 주입하고 tracked source에 실제 host를 기본값으로 두지 않는다.
- endpoint는 배포 binary에서 추출할 수 있으므로 secret이 아니다. non-commit 정책은 구성 위생이며 DDoS·남용 방어는 [GitHub Issue #52](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/52)에서 다룬다.

### ViewModel provider registry

- MyPage 전용 `when`은 Metro keyed provider registry로 교체한다.
- known type은 생성하고 missing, duplicate, wrong-type binding은 명확히 실패한다.
- feature ViewModel 추가는 central type switch 수정이 아니라 binding 추가로 끝나야 한다.
- Navigation entry의 `ViewModelStoreOwner`가 scope를 계속 소유하며 registry는 navigation/back stack을 알지 못한다.

### Failure와 cancellation

- Repository boundary는 `AppResult.Success<T>`와 단일 `AppResult.Failure`를 유지한다.
- non-cancellation network/HTTP/serialization 실패는 repository implementation에서 failure로 변환한다.
- `CancellationException`은 failure로 삼키지 않고 전파한다.
- raw exception, HTTP code, server 내부 메시지와 parser 세부사항은 ViewModel/UI에 노출하지 않는다.

## Navigation 3 정책

- root key 순서는 News, Matches, MyPage, Events, About이며 기본 destination은 `MyPageRoot`다.
- Search와 News/Match/Event/Team/Player/Series detail은 현재 root 위에 push되는 transient overlay다.
- runtime은 `rememberNavBackStack`과 app 전용 `SavedStateConfiguration`을 사용한다. 모든 key는 직렬화 가능하며 detail key에는 복원에 필요한 안정적인 식별자만 둔다.
- root 선택은 기존 overlay를 모두 비우고 선택한 root로 교체한다. Back은 overlay가 있을 때 마지막 entry만 pop하며 root에서는 stack을 변경하지 않는다.
- `NavDisplay`는 entry provider, saveable-state entry decorator, ViewModelStore entry decorator를 연결한다.
- navigation owner가 back stack을 소유하고 `AppNavigationState`는 같은 stack에서 선택 root와 overlay를 파생해 상태 전이를 수행한다. 별도의 두 번째 navigation state를 유지하지 않는다.
- feature composable은 별도의 app graph나 전역 service locator를 만들지 않는다. Screen은 callback으로 navigation 의도를 전달하고 ViewModel은 back stack을 직접 조작하지 않는다.

## 제외 및 후속 결정

- 탭별 독립 multi-back-stack
- deep link와 제품 route 문자열 binding
- adaptive scene, 인증 흐름
- 실제 News/Matches/Events/Search/Team/Player/Series/About UI와 data loading
- pagination framework와 공통 cache/storage
- production API URL, hosting provider, server-side DDoS·남용 방어

Pagination은 여러 feature에서 사용될 가능성이 있지만 아직 100% 공통 계약이 아니므로 후속 이슈에서 실제 사용처를 기준으로 결정한다. 나머지 항목도 기능 요구사항과 당시 library API를 확인해 결정한다.

## 검증 계약

현재 구현의 회귀 근거:

- `AppNavKeySerializationTest`: root/Search/detail key 직렬화와 복원
- `AppNavigationStateTest`: root 선택, overlay push/pop, 복원된 stack 정책
- `DestinationDescriptorTest`: root 순서와 destination metadata
- `MyPageViewModelTest`: MyPage skeleton state
- `AppGraphAndroidHostTest`: 현재 Metro graph와 entry별 ViewModelStore scope

H1-K0 구현 시 추가할 검증:

- platform별 exact base URL 전달과 invalid/missing configuration 실패
- runtime 안의 singleton client와 runtime 사이의 client 독립성
- idempotent shutdown, close-once, shutdown 이후 접근 실패
- Android Activity recreation에서 Application-owned runtime 유지
- iOS scene background에서 runtime 미종료
- ViewModel registry known/missing/duplicate/wrong-type 경로
- repository failure mapping과 cancellation 전파
- shared Android host test와 iOS simulator compile/test

이 테스트들은 runtime 기반의 회귀 검증이다. 실제 feature 화면 동작이나 physical Android/iOS 시각·접근성을 검증한 것으로 해석하지 않는다.

## 관련 문서

- [ADR-0001: Thin App Runtime Kernel](adr/0001-thin-app-runtime-kernel.md): H1-K0의 확정 decision과 재검토 조건
- [App Architecture](app-arch.md): module 책임과 공통 package 방향
- [UI Layer](ui-layer.md): Screen callback, ViewModel, feature UI 규칙
- [Data Layer](data-layer.md): data binding과 repository 경계

## 공식 참고

- [Compose Multiplatform Navigation 3](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Navigation 3 overview](https://developer.android.com/guide/navigation/navigation-3)
- [Compose Multiplatform ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)
- [Metro dependency graph](https://zacsweers.github.io/metro/latest/dependency-graphs/)
- [Ktor client creation, reuse, and close](https://ktor.io/docs/client-create-and-configure.html)
