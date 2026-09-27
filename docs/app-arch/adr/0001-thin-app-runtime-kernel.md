# ADR-0001: Thin App Runtime Kernel

- Status: Accepted
- Date: 2026-08-06
- Amended: 2026-08-07 — 별도 runtime wrapper와 custom ViewModel registry를 제거하고 platform-owned graph와 MetroX를 채택
- Scope: [Issue #33](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/33) H1-K0
- Related: [Runtime](../app-runtime.md), [Data Layer](../data-layer.md)

## Context

Android/iOS가 플랫폼 설정으로 하나의 Ktor client를 만들고 application lifetime 동안 공유할 최소 runtime이 필요했다. 개발용 local URL은 필요하지만 배포 endpoint를 tracked source에 고정할 이유는 없다. Pagination, cache와 범용 UI/repository framework는 사용처가 증명되지 않았다.

## Decision

### Platform configuration

공통 graph는 `apiBaseUrl`을 포함한 immutable configuration을 입력으로 받는다. Android는 generated `BuildConfig`, iOS는 xcconfig와 `Info.plist`를 통해 값을 전달하며 `commonMain`은 플랫폼 설정 API를 읽지 않는다. Debug local 기본값은 Android emulator `http://10.0.2.2:8080`, iOS simulator `http://127.0.0.1:8080`이다. Release endpoint는 외부에서 주입한 HTTPS origin만 허용한다.

Endpoint는 binary에서 추출할 수 있으므로 secret이 아니다. 저장소 밖 주입은 구성 위생이며 server-side 남용 방어를 대신하지 않는다.

### Graph와 client 수명

Android `Application`과 iOS SwiftUI의 reference-type `@StateObject` owner가 `AppGraph`를 한 번 만든다. Graph의 Metro app scope는 Ktor client 하나를 제공하고 모든 remote source가 이를 공유한다. Activity recreation과 scene background/foreground는 수명 경계가 아니다.

OS process 종료에 자원 회수를 맡기며 별도 `AppRuntime` wrapper와 production `shutdown()`을 두지 않는다. 테스트가 직접 만든 client만 테스트가 정리한다.

### ViewModel 생성

`AppGraph`는 MetroX `ViewModelGraph`를 확장하고 ViewModel은 keyed map multibinding에 기여한다. 공통 `App`이 factory를 한 번 제공하며 실제 instance scope는 Navigation 3 entry의 `ViewModelStoreOwner`가 소유한다. Central `when`, custom registry와 수동 provider map은 만들지 않는다.

### Repository 실패

Repository는 non-cancellation 실패를 `AppResult.Failure`로 변환하고 `CancellationException`은 전파한다. Raw exception, HTTP status, server message, upstream URL과 parser 세부사항은 공개 contract와 UI에 노출하지 않는다. 이후 추가된 `Busy` 경계는 [Domain](../domain-layer.md)과 [Data](../data-layer.md) 문서가 소유한다.

## 결과

- 플랫폼 차이는 configuration 획득과 engine 생성에만 남고 graph/client는 화면마다 재생성되지 않는다.
- ViewModel 추가는 map contribution으로 끝나며 중복 key/type 오류는 Metro compilation에서 잡힌다.
- 실행 중 graph 교체, 명시적 client 종료, 공통 paging/cache abstraction은 지원하지 않는다.

## 검증

Platform configuration 전달과 invalid 값 거부, graph별 client 수명, MetroX provider lookup과 entry별 ViewModel scope, repository cancellation 전파를 자동 검증한다. 플랫폼 lifecycle을 UI test로 재현할 수 없으면 graph owner test와 entry compile check로 나누고 검증 공백을 기록한다.

## 재검토 조건

- 로그인 계정·server environment 전환 또는 multi-window로 실행 중 graph 교체가 필요할 때
- 여러 feature가 동일한 paging/cache 정책을 실제로 공유할 때
- 현재 `AppResult`로 표현할 수 없는 복구 동작이 생길 때

## 참고

- [Ktor client lifecycle](https://ktor.io/docs/client-create-and-configure.html)
- [Metro dependency graph](https://zacsweers.github.io/metro/latest/dependency-graphs/)
- [MetroX ViewModel](https://zacsweers.github.io/metro/latest/metrox-viewmodel/)
