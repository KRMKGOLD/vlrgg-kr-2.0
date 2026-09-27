# App Architecture

VLR.GG Mobile 2.0은 Compose Multiplatform 클라이언트다. 화면, 상태, ViewModel, repository 계약과 API client는 `app/shared/src/commonMain`을 기본 구현 위치로 삼고, Android/iOS entry에는 플랫폼 API와 bridge만 둔다.

## 기준 문서

- 저장소 작업 규칙: [`../../AGENTS.md`](../../AGENTS.md)와 각 모듈의 `AGENTS.md`
- 앱 runtime, Metro graph, Navigation 3: [`app-runtime.md`](app-runtime.md)
- UI/Domain/Data 경계: [`ui-layer.md`](ui-layer.md), [`domain-layer.md`](domain-layer.md), [`data-layer.md`](data-layer.md)
- 화면 요구사항과 사용자 흐름: [`../feature/`](../feature/)
- 시각 규칙: [`../../DESIGN.md`](../../DESIGN.md)와 Stitch 결과물

코드와 문서가 다르면 현재 구현과 변경 의도를 확인하고 관련 문서를 함께 갱신한다. 아직 시작하지 않은 기능의 빈 문서나 package는 미리 만들지 않는다.

## 모듈 경계

| 모듈 | 책임 |
| --- | --- |
| `app/shared` | 공통 UI, 상태, navigation, domain/data, API client |
| `app/androidApp` | Android entry와 Android 전용 integration |
| `app/iosApp` | iOS entry, SwiftUI host와 iOS 전용 integration |
| `core` | 앱과 서버가 함께 쓰는 framework-free Kotlin 코드 |
| `server` | scraping, 서버 가공, 앱용 API 응답 |

동일 기능을 플랫폼별로 복제하기 전에 `commonMain`에서 해결할 수 있는지 먼저 확인한다. 플랫폼 entry에는 비즈니스 로직을 두지 않는다.

## 의존 방향

```text
Screen/Content -> ViewModel -> Repository 또는 의미 있는 UseCase
                               -> RepositoryImpl -> Remote/LocalDataSource
```

- UI는 `StateFlow`의 화면 snapshot과 명시적인 callback으로 UDF를 구성한다.
- Domain은 app-facing model, repository contract와 `AppResult`를 소유한다.
- Data는 DTO/storage model 변환, remote/local 접근과 repository 구현을 소유한다.
- UseCase는 여러 repository 조합이나 재사용할 정책이 있을 때만 만든다.
- 앱 graph는 플랫폼이 Compose recomposition 밖에서 만들며, Navigation 3 entry가 ViewModel instance scope를 소유한다.

## `core` 사용 기준

`core`에는 앱과 서버가 실제로 함께 사용하며 Compose, Android/iOS API, Ktor server와 transport contract에 의존하지 않는 value object·검증·작은 utility만 둔다. 한 기능에서만 쓰거나 request/response DTO인 코드는 원래 모듈에 둔다.

## 변경 원칙

- feature의 Screen, Content, state, ViewModel과 전용 component는 가까이 둔다.
- 공통 component와 abstraction은 두 번째 실제 사용처가 생긴 뒤 추출한다.
- dependency와 빈 scaffolding보다 하나의 동작하는 vertical slice를 우선한다.
- dependency, navigation, layer 또는 module 책임이 바뀌면 해당 문서와 가장 좁은 테스트를 함께 갱신한다.
