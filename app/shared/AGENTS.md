# app/shared Agent Rules

## Source of Truth

- 상세 앱 설계는 `../../docs/app-arch/app-arch.md`를 따른다.
- 앱 root, Metro graph 수명, Navigation 3 복원 계약은 `../../docs/app-arch/app-runtime.md`를 따른다.
- UI, Domain, Data 세부 규칙은 `../../docs/app-arch/`의 layer 문서를 따른다.
- 루트 운영 규칙은 `../../AGENTS.md`를 함께 따른다.
- 이 파일에는 `app/shared`에서 바로 적용할 짧은 실행 규칙만 둔다.

## Rules

- Compose UI, ViewModel, UiState, navigation, domain/data 경계는 가능한 `commonMain`에 둔다.
- 공통 `App(graph)`는 플랫폼 앱 수명 owner가 생성한 `AppGraph`를 받고 root Metro ViewModel factory를 제공한다. composable에서 graph를 생성하지 않는다.
- `AppNavHost`만 root back stack을 소유한다. 새 key는 sealed `AppNavKey` hierarchy에 `@Serializable`로 추가하고 Android/iOS 복원 검증을 함께 갱신한다.
- 새 화면은 `src/commonMain/kotlin/.../ui/feature/{feature}` 아래에 vertical slice로 추가한다.
- Screen은 ViewModel state를 연결하고, Content composable은 stateless rendering을 담당하게 한다.
- ViewModel은 navigation back stack, NavController, NavBackStack을 직접 제어하지 않는다.
- UI event는 explicit ViewModel function callback으로 전달하고, navigation은 Screen callback으로 navigation owner에 위임한다. `UiAction`, `Effect`, one-off event stream은 초기 구조에 두지 않는다.
- UiState는 화면 전체 snapshot이며, sealed ContentState는 주요 콘텐츠 상태가 복잡할 때만 선택적으로 사용한다.
- Domain Layer는 app-facing business model, repository contract, 공통 `AppResult` 경계로 유지하고, UseCase는 app architecture 문서의 생성 조건을 만족할 때만 추가한다.
- Domain Model은 필요한 경우 UiState에서 직접 사용할 수 있다. UiModel은 UI Layer에만 두며 Domain Model을 포함할 수 있지만 Domain Layer로 전달하지 않는다.
- RemoteResponse, DTO, Entity, raw exception을 ViewModel이나 UiState에 직접 노출하지 않는다.
- 공통 component는 실제 재사용이 생긴 뒤 `ui/component`로 이동한다.
- 새 dependency가 필요하면 먼저 `gradle/libs.versions.toml`과 architecture 문서를 확인한다.
