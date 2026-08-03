# app/shared Agent Rules

## Source of Truth

- 상세 앱 설계는 `../../docs/app-arch/app-arch.md`를 따른다.
- 앱 root, Metro graph, Navigation 3의 기본 runtime 원칙은 `../../docs/app-arch/app-runtime.md`를 따른다.
- UI, Domain, Data 세부 규칙은 `../../docs/app-arch/`의 layer 문서를 따른다.
- 루트 운영 규칙은 `../../AGENTS.md`를 함께 따른다.
- 이 파일에는 `app/shared`에서 바로 적용할 짧은 실행 규칙만 둔다.

## Rules

- Compose UI, ViewModel, UiState, navigation, domain/data 경계는 가능한 `commonMain`에 둔다.
- 공통 `App`은 플랫폼 runtime owner가 준비한 graph를 받으며, composable에서 app graph를 생성하지 않는다. Metro ViewModel factory는 실제 DI 구성에 맞는 상위 경계에서 제공한다.
- navigation owner가 필요한 navigation state를 관리한다. 저장·복원이 필요한 key에는 안정적인 식별자만 넣고 직렬화 가능하게 만든다.
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
- KMP 의존성 버전은 Compose Multiplatform 릴리스의 Coordinator/컴포넌트 표를 최우선 호환성 기준으로 삼고, 표에 지정된 alpha/beta 버전도 허용한다. 현재 CMP `1.11.1` 정렬은 Material3 `1.11.0-alpha07`, Lifecycle `2.11.0-beta01`, Navigation 3 `1.1.1`을 따른다.
- Coordinator 비대상 라이브러리는 필요한 기능이 prerelease에만 제공되는 경우가 아니면 stable 버전을 사용한다.
- Coordinator 표의 좌표가 실제 저장소에 게시되지 않은 경우에만 공식 component setup 문서와 Maven metadata로 확인한 가장 가까운 게시 호환 버전을 사용하고, 표 좌표·대체 버전·확인 근거를 기록한다.
