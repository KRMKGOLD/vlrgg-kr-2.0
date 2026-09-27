# UI Layer

UI 코드는 `app/shared/src/commonMain/.../ui`에 둔다. UI는 화면 렌더링, 상태 수집, 사용자 event와 navigation callback 전달을 담당하고 repository 구현이나 transport/storage model을 알지 않는다.

## App과 navigation

- 플랫폼 owner가 만든 `AppGraph`를 공통 `App(graph)`에 전달한다. `App`은 `LocalMetroViewModelFactory`, theme와 `AppNavigation`을 연결하며 graph를 recomposition 중 만들거나 feature parameter로 전달하지 않는다.
- Navigation 3 key에는 복원에 필요한 안정적인 식별자만 넣고 직렬화 가능하게 만든다.
- News, Matches, MyPage, Events, About는 root별 stack과 decorator state를 유지한다. 같은 root를 다시 고르면 그 root의 overlay만 pop한다.
- Screen은 이동 의도를 callback으로 올린다. ViewModel은 back stack을 직접 조작하지 않는다.

수명, 복원과 root별 stack 계약은 [`app-runtime.md`](app-runtime.md)를 따른다.

## Screen, Content, ViewModel

| 구성 | 책임 |
| --- | --- |
| `*Screen` | ViewModel 상태 수집, event/navigation callback 연결 |
| `*Content` | `UiState`와 callback으로 그리는 UI |
| `*UiState` | 한 화면의 전체 render snapshot |
| `*ContentState` | 주요 콘텐츠 상태가 복잡할 때만 쓰는 선택적 하위 상태 |
| `*ViewModel` | repository/use case 호출과 상태 변경 |

ViewModel은 MetroX map binding에 기여하고 Screen은 `metroViewModel()`로 현재 navigation entry의 instance를 얻는다. Runtime parameter가 필요한 ViewModel만 해당 feature에서 assisted creation을 사용한다.

## 상태와 event

- 화면마다 하나의 `UiState` data class를 `StateFlow`로 노출한다.
- 단순한 loading/content/error field로 충분하면 sealed state를 만들지 않는다. 배타적 상태와 표시 규칙이 복잡해 잘못된 조합이 생길 때만 feature-local `ContentState`를 둔다. `ContentState`는 `UiState`를 대체하거나 다시 포함하지 않는다.
- Domain Model은 그대로 표시할 수 있으면 직접 사용한다. 형식화, label/icon, 화면별 그룹화·선택 상태가 필요할 때만 UI 전용 model을 둔다.
- 사용자 event는 명시적인 ViewModel 함수 callback으로 전달한다. 공통 `UiAction`, reducer, Effect 또는 one-off stream은 실제 요구 전에는 만들지 않는다.
- `AppResult.Failure`와 `Busy`는 화면 상태로 변환한다. raw exception, HTTP code와 data 내부 오류를 UI가 해석하지 않는다.
- 재시도는 사용자가 누르는 명시적 event로 제공하며 자동 재시도하지 않는다. 비동기 완료 뒤 이동이 필요하면 해당 기능에서 state 기반 계약을 정한다.

## Component와 시각 규칙

feature 하나에서만 쓰는 composable은 해당 feature에 둔다. 두 feature 이상에서 같은 계약으로 재사용할 때 `ui/component`로 옮긴다. Theme와 접근성·시각 결정은 [`../../DESIGN.md`](../../DESIGN.md), 기능 문서와 Stitch 결과물을 따른다.

상태 전이는 ViewModel test로, callback과 주요 상태 렌더링은 가장 좁은 UI test로 검증한다. 플랫폼별 실제 접근성과 시각 동작은 simulator/device 확인이 필요한 별도 근거로 취급한다.
