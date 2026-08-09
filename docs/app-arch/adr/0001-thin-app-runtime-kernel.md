# ADR-0001: Thin App Runtime Kernel

- Status: Accepted
- Date: 2026-08-06
- Amended: 2026-08-06 — 별도 `AppRuntime`과 명시적 `shutdown()` 계약을 제거하고 platform-owned `AppGraph` 수명으로 단순화
- Amended: 2026-08-07 — custom ViewModel registry 대신 MetroX의 keyed map multibinding과 Compose integration을 채택
- Decision scope: [GitHub Issue #33](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/33)의 H1-K0 app runtime foundation
- Related: [App Runtime Composition](../app-runtime.md), [App Architecture](../app-arch.md), [Data Layer](../data-layer.md)

## Context

현재 앱은 Metro graph와 Navigation 3 runtime을 갖고 있다. 서버 API를 사용하는 feature를 추가하기 전에 Android/iOS가 platform에서 얻은 설정으로 하나의 Ktor client를 구성하고 application-owned graph 수명 동안 재사용하는 얇은 기반이 필요하다.

운영 API host는 아직 확정되지 않았다. 개발 중에는 로컬 서버에 연결해야 하지만 공개 저장소에 향후 배포 endpoint를 고정해 두지 않는다. 다만 앱에 포함된 endpoint는 APK/IPA에서 추출할 수 있으므로 비밀로 간주할 수 없다. 저장소 비공개 주입은 구성 관리와 불필요한 자동 수집 노출을 줄이는 수단이며, DDoS·남용 방어를 대신하지 않는다.

이 결정은 이후 feature를 구현할 수 있는 최소 runtime kernel만 만든다. 아직 반복 사용이 증명되지 않은 pagination, cache, 범용 repository/UI framework는 포함하지 않는다.

## Decision

### 1. Platform configuration is an explicit graph input

공통 graph 생성 경계는 `apiBaseUrl`을 포함한 immutable configuration을 입력으로 받는다. `commonMain`은 Android `BuildConfig`나 iOS `Bundle`을 직접 읽지 않는다.

- Android는 AGP 9의 generated `BuildConfig` field에 Gradle property를 주입하고 platform entry에서 공통 configuration으로 변환한다.
- iOS는 xcconfig build setting을 `Info.plist` key로 확장하고 Swift platform entry에서 읽어 공통 configuration으로 변환한다.
- 현재 local 기본값은 Android emulator에서 `http://10.0.2.2:8080`, iOS simulator에서 `http://127.0.0.1:8080`이다. 물리 기기 주소는 이 결정의 기본값에 포함하지 않는다.
- local HTTP 허용은 debug/local 구성으로 제한한다. 배포 구성은 HTTPS endpoint를 외부 build configuration에서 주입해야 하며 실제 endpoint를 tracked source에 기본값으로 두지 않는다.
- 값이 없거나 유효한 absolute HTTP(S) URL이 아니면 client 생성 전에 명확히 실패한다. 문자열 뒤의 `/` 정규화는 한 곳에서 수행한다.

### 2. One application-owned graph provides one Ktor client

platform이 다루는 composition boundary는 application lifetime에 한 번 생성하는 `AppGraph`다. graph 생성 시 configuration을 input으로 전달하고 Metro `AppScope`의 `@SingleIn` binding으로 Ktor `HttpClient`를 하나만 제공한다. 같은 graph의 모든 remote data source는 동일한 client instance를 주입받는다.

- Android의 process-level `Application`이 graph를 lazy하게 한 번 소유한다. `Activity`는 graph를 조회해 Compose에 전달할 뿐 생성하거나 교체하지 않는다.
- iOS의 SwiftUI `App`이 reference-type graph owner를 `@StateObject`로 소유한다. `WindowGroup`, root view 또는 scene background 전환은 graph를 생성하거나 교체하지 않는다.
- Android configuration change, Activity recreation, iOS scene background/foreground는 client lifetime 경계가 아니다.
- OS가 process를 종료하면 graph와 client 자원도 함께 회수되며, platform 종료 callback 호출을 correctness 조건으로 두지 않는다.
- 이번 범위에는 별도 `AppRuntime` wrapper와 production `shutdown()` API를 두지 않는다. 테스트에서 직접 생성한 client가 있다면 해당 테스트가 정리 책임을 가진다.
- 로그인 계정 또는 server environment 전환처럼 process 실행 중 graph 교체가 실제 제품 요구가 되면 기존 client 정리와 새 graph 생성 계약을 새 ADR로 결정한다.

이 문서의 `runtime`은 앱 composition과 lifetime 정책을 가리키는 용어이며 `AppRuntime`이라는 별도 타입 도입을 의미하지 않는다.

### 3. ViewModel creation uses MetroX keyed multibinding

MyPage 전용 `when` factory는 H1-K1에서 MetroX의 공식 ViewModel integration으로 교체한다. `AppGraph`는 `ViewModelGraph`를 확장하고, app-scoped `AppViewModelFactory`는 `MetroViewModelFactory`를 상속해 `Map<KClass<out ViewModel>, () -> ViewModel>` provider map을 받는다. 각 feature ViewModel은 concrete class에 `@ViewModelKey`와 `@ContributesIntoMap(AppScope::class)`를 선언해 provider map에 직접 기여한다.

- 등록된 ViewModel type은 새 instance를 생성할 수 있다.
- 미등록 type 요청은 `MetroViewModelFactory`가 요청 `KClass`를 포함한 명확한 오류로 실패한다.
- 같은 map key의 중복 binding은 runtime registry 검사가 아니라 Metro graph compilation에서 실패한다.
- 표준 ViewModel contribution은 concrete class의 implicit `@ViewModelKey`를 사용한다. app code에서 key와 provider를 따로 조합하는 수동 map provider는 만들지 않아 key/provider type 불일치를 구조적으로 피한다.
- feature ViewModel을 추가할 때 central `when`, 별도 registry class 또는 `Set<Entry>` 변환을 추가하지 않는다.

공통 `App`은 `LocalMetroViewModelFactory`에 graph factory를 한 번 제공한다. feature Screen의 `metroViewModel()`은 Navigation 3 entry decorator가 제공하는 `LocalViewModelStoreOwner`를 사용하므로 실제 ViewModel scope는 계속 navigation entry가 소유한다. Factory와 provider map은 navigation이나 back stack을 소유하지 않는다.

### 4. Repository failure remains deliberately small

공통 Domain 경계는 `AppResult.Success<T>`와 단일 `AppResult.Failure`를 사용한다. Repository implementation은 network, HTTP, serialization 등 non-cancellation 실패를 이 failure로 변환한다. coroutine `CancellationException`은 변환하지 않고 다시 전파한다.

raw exception, HTTP status, server 내부 메시지, upstream URL이나 parser 세부사항은 repository public contract, ViewModel, UI에 노출하지 않는다. 오류 category, 자동 retry, 오류별 UI 분기는 실제 feature 요구가 생길 때 별도 결정한다.

## Consequences

- Android/iOS 모두 같은 공통 client 구성 경계를 사용하고 platform 차이는 값 획득에만 남는다.
- Ktor client와 connection pool을 화면마다 생성하지 않아 resource lifetime이 예측 가능하다.
- 별도 lifecycle wrapper 없이 platform-owned graph와 Metro scope만으로 현재 단일-client 요구를 충족한다.
- process 실행 중 graph 교체와 client의 명시적 종료는 현재 지원하지 않으며 실제 요구가 생기면 별도 결정이 필요하다.
- 배포 endpoint는 저장소 밖에서 주입할 수 있지만 앱 binary에서 숨겨지는 secret은 아니다.
- ViewModel 추가가 central type switch나 custom registry의 수정으로 이어지지 않는다.
- duplicate key와 standard contribution type 오류는 Metro compilation에서 검출하므로 이를 재현하는 app runtime registry test는 두지 않는다.
- 작은 failure contract는 초기 feature를 단순하게 유지하지만, 세분화가 필요해지면 Domain·Data·UI 전체 경계를 다시 검토해야 한다.

## Non-goals and deferred work

- News, Matches 등 실제 feature API/화면 구현
- pagination framework 또는 공통 paging abstraction
- cache, Preferences DataStore, Room schema
- 범용 repository, datasource, UI state framework
- process 실행 중 graph/client 교체와 별도 `AppRuntime` lifecycle abstraction
- production API URL이나 hosting provider 추정
- endpoint 은닉을 보안 경계로 취급하는 것
- server-side rate limiting, WAF/CDN, quota, authentication, monitoring 등 DDoS·남용 방어 구현

Pagination은 여러 feature에서 사용될 가능성이 있지만 현재 100% 공통 계약이 아니므로 실제 두 번째 사용처의 요구가 확인된 후 후속 이슈에서 정의한다. 서버 공개 방어는 [GitHub Issue #52](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/52)가 소유한다.

## Verification contract

H1-K0 구현은 최소한 다음을 자동 검증한다.

- Android/iOS platform configuration의 정확한 base URL이 graph/client 생성 지점에 도달한다.
- 같은 graph에서 client를 반복 resolve하면 동일 instance이고, 별도 graph는 독립 instance를 가진다.
- Android Activity recreation/configuration change가 Application-owned graph/client를 교체하지 않는다.
- iOS scene background 전환이 app-owned graph/client를 교체하지 않는다.
- MetroX factory가 등록된 ViewModel과 미등록 ViewModel 요청을 계약대로 처리한다.
- duplicate key와 standard contribution type 계약은 Android/iOS Metro graph compilation으로 검증한다.
- 서로 다른 Navigation entry `ViewModelStoreOwner`가 ViewModel instance scope를 독립적으로 소유한다.
- Repository가 non-cancellation 실패를 `AppResult.Failure`로 변환하고 cancellation은 전파한다.
- shared Android host test와 iOS simulator compilation/test가 통과한다.

platform lifecycle을 실제 instrumentation/UI test로 안정적으로 재현하기 어렵다면 graph owner를 직접 검증하는 focused test와 platform entry compile check로 나누되, 검증 공백을 숨기지 않는다.

## Revisit triggers

다음 조건이 생기면 이 결정을 새 ADR로 대체하거나 확장한다.

- 로그인 계정 또는 server environment 전환으로 process 실행 중 graph/client 교체가 제품 기능이 됨
- multi-window가 독립 runtime을 요구함
- 두 개 이상의 feature가 동일한 pagination/cache 요구를 실제로 공유함
- UI가 단일 failure로 표현할 수 없는 복구 동작을 요구함
- deployment provider와 public API protection 정책이 확정됨

## References

- [Ktor client creation, reuse, and close](https://ktor.io/docs/client-create-and-configure.html)
- [Metro dependency graph scopes](https://zacsweers.github.io/metro/latest/dependency-graphs/)
- [MetroX ViewModel](https://zacsweers.github.io/metro/latest/metrox-viewmodel/)
- [MetroX ViewModel Compose](https://zacsweers.github.io/metro/latest/metrox-viewmodel-compose/)
- [Android runtime configuration changes](https://developer.android.com/guide/topics/resources/runtime-changes.html)
- [Android Application lifecycle reference](https://developer.android.com/reference/android/app/Application.html)
- [SwiftUI StateObject](https://developer.apple.com/documentation/swiftui/stateobject)
- [SwiftUI ScenePhase](https://developer.apple.com/documentation/swiftui/scenephase)
- [AGP BuildConfigField API](https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/BuildConfigField)
- [Apple property list configuration](https://developer.apple.com/documentation/bundleresources/managing-your-app-s-information-property-list)
- [Android emulator networking](https://developer.android.com/studio/run/emulator-networking-address)
- [OWASP: hardcoded API keys are extractable](https://mas.owasp.org/MASWE-0005/)
