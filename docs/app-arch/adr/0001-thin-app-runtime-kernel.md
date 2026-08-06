# ADR-0001: Thin App Runtime Kernel

- Status: Accepted
- Date: 2026-08-06
- Decision scope: [GitHub Issue #33](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/33)의 H1-K0 app runtime foundation
- Related: [App Runtime Composition](../app-runtime.md), [App Architecture](../app-arch.md), [Data Layer](../data-layer.md)

## Context

현재 앱은 Metro graph와 Navigation 3 runtime을 갖고 있지만 Android에서는 `MainActivity`가 graph를 소유하고, 공통 graph는 ViewModel factory만 제공한다. 서버 API를 사용하는 feature를 추가하기 전에 Android/iOS가 동일한 설정으로 하나의 Ktor client를 구성하고, runtime lifetime 동안 재사용하며, 테스트에서 명시적으로 종료할 수 있는 얇은 기반이 필요하다.

운영 API host는 아직 확정되지 않았다. 개발 중에는 로컬 서버에 연결해야 하지만 공개 저장소에 향후 배포 endpoint를 고정해 두지 않는다. 다만 앱에 포함된 endpoint는 APK/IPA에서 추출할 수 있으므로 비밀로 간주할 수 없다. 저장소 비공개 주입은 구성 관리와 불필요한 자동 수집 노출을 줄이는 수단이며, DDoS·남용 방어를 대신하지 않는다.

이 결정은 이후 feature를 구현할 수 있는 최소 runtime kernel만 만든다. 아직 반복 사용이 증명되지 않은 pagination, cache, 범용 repository/UI framework는 포함하지 않는다.

## Decision

### 1. Platform configuration is an explicit graph input

공통 runtime은 `apiBaseUrl`을 포함한 immutable configuration을 입력으로 받아 Metro graph를 만든다. `commonMain`은 Android `BuildConfig`나 iOS `Bundle`을 직접 읽지 않는다.

- Android는 AGP 9의 generated `BuildConfig` field에 Gradle property를 주입하고 platform entry에서 공통 configuration으로 변환한다.
- iOS는 xcconfig build setting을 `Info.plist` key로 확장하고 Swift platform entry에서 읽어 공통 configuration으로 변환한다.
- 현재 local 기본값은 Android emulator에서 `http://10.0.2.2:8080`, iOS simulator에서 `http://127.0.0.1:8080`이다. 물리 기기 주소는 이 결정의 기본값에 포함하지 않는다.
- local HTTP 허용은 debug/local 구성으로 제한한다. 배포 구성은 HTTPS endpoint를 외부 build configuration에서 주입해야 하며 실제 endpoint를 tracked source에 기본값으로 두지 않는다.
- 값이 없거나 유효한 absolute HTTP(S) URL이 아니면 client 생성 전에 명확히 실패한다. 문자열 뒤의 `/` 정규화는 한 곳에서 수행한다.

### 2. One runtime owns one Metro graph and one Ktor client

platform이 다루는 public composition boundary는 graph와 종료 책임을 함께 가진 하나의 app runtime owner다. runtime 생성 시 configuration을 graph input으로 전달하고 Metro app scope에서 Ktor `HttpClient`를 한 번 생성한다. 같은 runtime/graph의 모든 remote data source는 동일한 client instance를 주입받는다.

- Android의 process-level `Application`이 runtime을 lazy하게 한 번 소유한다. `Activity`는 graph를 조회해 Compose에 전달할 뿐 생성하거나 종료하지 않는다.
- iOS의 SwiftUI `App`이 reference-type runtime owner를 `@StateObject`로 소유한다. `WindowGroup`, root view 또는 scene background 전환은 runtime을 생성하거나 종료하지 않는다.
- Android configuration change, Activity recreation, iOS scene background/foreground는 client lifetime 경계가 아니다.
- OS process termination callback에 정상 종료 correctness를 의존하지 않는다. 새 process는 새 runtime을 만든다.
- owner는 테스트와 통제된 교체를 위한 명시적이고 idempotent한 `shutdown()`을 제공한다. 첫 호출만 client를 닫고 이후 호출은 no-op이다. 종료 후 새로운 graph/client 접근은 명확히 실패한다.
- 향후 계정 또는 server environment별 graph 교체가 필요하면 기존 runtime을 명시적으로 종료한 뒤 새 owner를 만든다. Activity/scene lifecycle에 이 책임을 연결하지 않는다.

구체적인 wrapper/class 이름은 구현에서 현재 package와 Swift interop에 맞춰 정할 수 있지만 위 소유권과 lifetime은 변경할 수 없는 계약이다.

### 3. ViewModel creation uses a keyed registry

현재 MyPage 전용 `when` factory는 H1-K0에서 Metro가 구성한 keyed provider registry로 교체한다. registry의 내부 collection이나 annotation 형태는 Metro API에 맞추되 다음 동작을 보장한다.

- 등록된 ViewModel type은 새 instance를 생성할 수 있다.
- 미등록 type 요청은 요청 type을 식별할 수 있는 명확한 오류로 실패한다.
- 같은 key의 중복 binding은 graph 생성 또는 registry 초기화 시 실패한다.
- provider 결과가 요청 type과 다르면 잘못된 instance를 반환하지 않고 실패한다.
- H1-K1에서 feature ViewModel을 추가할 때 central `when` 분기를 수정하지 않고 binding을 추가한다.

Navigation 3 entry의 `ViewModelStoreOwner`가 ViewModel scope를 계속 소유한다. registry는 instance lookup/creation만 담당하며 navigation이나 back stack을 소유하지 않는다.

### 4. Repository failure remains deliberately small

공통 Domain 경계는 `AppResult.Success<T>`와 단일 `AppResult.Failure`를 사용한다. Repository implementation은 network, HTTP, serialization 등 non-cancellation 실패를 이 failure로 변환한다. coroutine `CancellationException`은 변환하지 않고 다시 전파한다.

raw exception, HTTP status, server 내부 메시지, upstream URL이나 parser 세부사항은 repository public contract, ViewModel, UI에 노출하지 않는다. 오류 category, 자동 retry, 오류별 UI 분기는 실제 feature 요구가 생길 때 별도 결정한다.

## Consequences

- Android/iOS 모두 같은 공통 client 구성 경계를 사용하고 platform 차이는 값 획득에만 남는다.
- Ktor client와 connection pool을 화면마다 생성하지 않아 resource lifetime이 예측 가능하다.
- 명시적 shutdown seam으로 OS 종료 callback 없이도 close 동작을 단위 테스트할 수 있다.
- 배포 endpoint는 저장소 밖에서 주입할 수 있지만 앱 binary에서 숨겨지는 secret은 아니다.
- ViewModel 추가가 central type switch의 수정으로 이어지지 않는다.
- 작은 failure contract는 초기 feature를 단순하게 유지하지만, 세분화가 필요해지면 Domain·Data·UI 전체 경계를 다시 검토해야 한다.

## Non-goals and deferred work

- News, Matches 등 실제 feature API/화면 구현
- pagination framework 또는 공통 paging abstraction
- cache, Preferences DataStore, Room schema
- 범용 repository, datasource, UI state framework
- production API URL이나 hosting provider 추정
- endpoint 은닉을 보안 경계로 취급하는 것
- server-side rate limiting, WAF/CDN, quota, authentication, monitoring 등 DDoS·남용 방어 구현

Pagination은 여러 feature에서 사용될 가능성이 있지만 현재 100% 공통 계약이 아니므로 실제 두 번째 사용처의 요구가 확인된 후 후속 이슈에서 정의한다. 서버 공개 방어는 [GitHub Issue #52](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/52)가 소유한다.

## Verification contract

H1-K0 구현은 최소한 다음을 자동 검증한다.

- Android/iOS platform configuration의 정확한 base URL이 graph/client 생성 지점에 도달한다.
- 같은 runtime에서 client를 반복 resolve하면 동일 instance이고, 별도 runtime은 독립 instance를 가진다.
- `shutdown()` 반복 호출이 client를 정확히 한 번만 닫으며 종료 후 접근이 안전하게 실패한다.
- Android Activity recreation/configuration change가 Application-owned runtime/client를 교체하지 않는다.
- iOS scene background 전환이 runtime/client를 종료하지 않는다.
- ViewModel registry의 known, missing, duplicate, wrong-type 경로가 각각 계약대로 동작한다.
- Repository가 non-cancellation 실패를 `AppResult.Failure`로 변환하고 cancellation은 전파한다.
- shared Android host test와 iOS simulator compilation/test가 통과한다.

platform lifecycle을 실제 instrumentation/UI test로 안정적으로 재현하기 어렵다면 owner를 직접 생성하는 focused test와 platform entry compile check로 나누되, 검증 공백을 숨기지 않는다.

## Revisit triggers

다음 조건이 생기면 이 결정을 새 ADR로 대체하거나 확장한다.

- 로그인 계정 또는 server environment 전환으로 runtime 교체가 제품 기능이 됨
- multi-window가 독립 runtime을 요구함
- 두 개 이상의 feature가 동일한 pagination/cache 요구를 실제로 공유함
- UI가 단일 failure로 표현할 수 없는 복구 동작을 요구함
- deployment provider와 public API protection 정책이 확정됨

## References

- [Ktor client creation, reuse, and close](https://ktor.io/docs/client-create-and-configure.html)
- [Metro dependency graph scopes](https://zacsweers.github.io/metro/latest/dependency-graphs/)
- [Android runtime configuration changes](https://developer.android.com/guide/topics/resources/runtime-changes.html)
- [Android Application lifecycle reference](https://developer.android.com/reference/android/app/Application.html)
- [SwiftUI StateObject](https://developer.apple.com/documentation/swiftui/stateobject)
- [SwiftUI ScenePhase](https://developer.apple.com/documentation/swiftui/scenephase)
- [AGP BuildConfigField API](https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/BuildConfigField)
- [Apple property list configuration](https://developer.apple.com/documentation/bundleresources/managing-your-app-s-information-property-list)
- [Android emulator networking](https://developer.android.com/studio/run/emulator-networking-address)
- [OWASP: hardcoded API keys are extractable](https://mas.owasp.org/MASWE-0005/)
