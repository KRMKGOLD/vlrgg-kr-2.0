# app/shared Agent Rules

## Source of Truth

- 상세 앱 설계는 `../../docs/architecture/app-architecture.md`를 따른다.
- 루트 운영 규칙은 `../../AGENTS.md`를 함께 따른다.
- 이 파일에는 `app/shared`에서 바로 적용할 짧은 실행 규칙만 둔다.

## Rules

- Compose UI, ViewModel, UiState, navigation, domain/data 경계는 가능한 `commonMain`에 둔다.
- 새 화면은 `src/commonMain/kotlin/.../ui/feature/{feature}` 아래에 vertical slice로 추가한다.
- Screen은 ViewModel state를 연결하고, Content composable은 stateless rendering을 담당하게 한다.
- ViewModel은 navigation back stack, NavController, NavBackStack을 직접 제어하지 않는다.
- Domain Layer는 app-facing model과 repository contract 경계로 유지하고, UseCase는 app architecture 문서의 생성 조건을 만족할 때만 추가한다.
- RemoteResponse, DTO, Entity를 ViewModel이나 UiState에 직접 노출하지 않는다.
- 공통 component는 실제 재사용이 생긴 뒤 `ui/component`로 이동한다.
- 새 dependency가 필요하면 먼저 `gradle/libs.versions.toml`과 architecture 문서를 확인한다.
