# App Runtime Composition

이 문서는 Android/iOS가 공유하는 composition, lifecycle, configuration과 Navigation 3 정책을 기록한다. 화면 요구사항은 [`../feature/`](../feature/)가 소유한다. `runtime`은 별도 wrapper 타입이 아니라 이 정책을 뜻한다.

## Composition과 lifetime

1. Android `Application`과 iOS SwiftUI의 `@StateObject` owner가 Compose recomposition 밖에서 `AppGraph`를 한 번 만든다.
2. 플랫폼 host가 `App(graph)`에 graph를 전달한다. 공통 `App`은 `LocalMetroViewModelFactory`, theme와 `AppNavigation`을 연결한다.
3. `AppGraph`는 `ViewModelGraph`를 확장하며 graph당 Ktor client 하나를 제공한다. Activity recreation과 iOS scene 전환은 graph/client를 교체하지 않는다.
4. OS process 종료가 자원 수명의 끝이다. 별도 `AppRuntime` 또는 production `shutdown()` API는 두지 않는다.

MetroX의 keyed map binding이 ViewModel provider를 모은다. Factory는 app scope지만 ViewModel instance는 Navigation 3 entry의 `ViewModelStoreOwner`가 소유한다. Feature는 graph, factory 또는 service locator를 전달받지 않는다. Runtime ID가 필요한 ViewModel은 해당 feature의 assisted factory를 사용한다.

이 선택의 이유와 재검토 조건은 [ADR-0001](adr/0001-thin-app-runtime-kernel.md)에 남긴다.

## API configuration

- `commonMain`은 platform configuration을 직접 읽지 않고 `apiBaseUrl`을 graph input으로 받는다.
- Android Debug 기본값은 emulator용 `http://10.0.2.2:8080`, iOS Debug 기본값은 simulator용 `http://127.0.0.1:8080`이다.
- Release는 저장소 밖에서 raw HTTPS origin을 주입한다. 빈 값, HTTP, userinfo/query/fragment, root 이외 path, 잘못된 port와 version/build number는 build-time validation에서 거부한다.
- Android는 generated `BuildConfig`, iOS는 private xcconfig에서 확장한 `Info.plist`를 통해 값을 전달한다.
- Endpoint는 binary에서 추출할 수 있으므로 secret이 아니다. 비커밋은 구성 위생이며 API 남용 방어는 [공개 API 보호 문서](../architecture/server-public-api-protection.md)가 소유한다.

## Navigation 3 정책

- root 순서는 News, Matches, MyPage, Events, About이며 기본 root는 MyPage다.
- 각 root는 독립 `rememberNavBackStack`, `SavedStateConfiguration`, saveable-state와 ViewModelStore decorator를 가진다. 선택하지 않은 root도 composition에 남겨 ViewModel, 데이터, 탭, scroll과 `rememberSaveable` 상태를 보존한다.
- Search와 News/Match/Event/Team/Player/Series Detail은 진입한 root stack의 overlay다. Root를 바꿔도 원래 stack에 남는다.
- 선택 root는 `rememberSaveable`의 단일 state로 관리한다. 복원 map은 정확한 root 집합과 올바른 root 첫 entry만 허용한다.
- 같은 destination을 여러 root에서 열어도 state가 섞이지 않도록 entry content key에 owning root와 entry instance를 포함한 저장 가능한 `String`을 쓴다.
- 현재 root를 다시 선택하면 그 root의 overlay만 root까지 pop한다. Back은 선택 root의 마지막 overlay만 pop하고 root entry에서는 stack을 바꾸지 않는다.
- Root 전환은 stack/decorator 바깥의 keyed host에서 200ms fade를 사용한다. Android와 iOS가 같은 상태 전이와 보존 동작을 유지해야 한다.
- Screen은 callback으로 이동 의도를 전달하고 ViewModel은 back stack을 직접 다루지 않는다.

구현 이유, 복원 결과와 범위 제외는 [ADR-0003](adr/0003-root-specific-saved-navigation-stacks.md)이 소유한다.

## 실패 경계

Remote data source는 DTO를 반환하고 repository가 non-cancellation 예외를 `AppResult`로 변환한다. Cancellation은 전파하며 raw exception, HTTP code, server message와 parser 세부사항은 UI에 노출하지 않는다. 상세 계약은 [`domain-layer.md`](domain-layer.md)와 [`data-layer.md`](data-layer.md)를 따른다.

## 검증 기준

- Navigation serialization/state test는 root별 push/pop, reselection, 잘못된 복원 거부와 entry key 분리를 고정한다.
- Runtime UI test는 root 왕복 뒤 ViewModel, loaded data, 선택 탭, scroll과 `rememberSaveable` 보존 및 pop 시 entry 정리를 검증한다.
- Graph/network test는 graph 내부 client singleton, graph 간 독립성, provider lookup과 entry별 ViewModel scope를 검증한다.
- Repository test는 success/failure/Busy 변환과 cancellation 전파를 검증한다.

이 근거는 runtime 회귀 검증이며 실제 기기의 시각·접근성 검증을 대신하지 않는다. 변경 시 shared Android host test, iOS simulator test, Android assemble과 iOS target compile 중 영향 범위에 맞는 가장 좁은 검사를 실행한다.

## 재검토 조건

- 로그인 계정이나 server environment 전환으로 실행 중 graph/client 교체가 필요할 때
- multi-window가 독립 runtime을 요구할 때
- deep link, adaptive scene 또는 인증 navigation을 도입할 때
- root 보존 또는 ViewModel scope 계약을 바꿀 때
