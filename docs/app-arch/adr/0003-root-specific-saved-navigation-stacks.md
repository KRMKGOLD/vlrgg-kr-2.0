# ADR-0003: Root별 저장 Navigation stack

- 상태: 승인됨
- 날짜: 2026-08-23
- 결정 범위: GitHub Issue #37 N1
- 관련 문서: [App Runtime Composition](../app-runtime.md), [UI Layer](../ui-layer.md), [Feature navigation](../../feature/README.md)

## 배경

기존에는 하단 navigation의 다섯 root가 하나의 stack을 공유했다. root를 전환하면서 이 stack을 교체하면, 전환을 시작한 root의 overlay 경로가 사라지고 entry 범위 ViewModel 및 저장 가능한 UI 상태도 폐기됐다. 이 앱은 deep link, adaptive scene, 인증 navigation을 도입하지 않으면서도 탭 전환 시 각 root의 로드된 페이지, 선택한 탭, 스크롤 위치, `rememberSaveable` 값과 entry ViewModel을 유지해야 한다.

## 결정

News, Matches, MyPage, Events, About는 각각 자기 root key로 초기화한 `rememberNavBackStack`을 소유한다. root 목록을 순회해 stack/decorator map을 구성하되, root별 호출과 composition 유지 계약은 그대로 둔다. 선택한 root는 `rememberSaveable`의 단일 mutable state로 보존하고 `AppNavigationState`가 그 state를 직접 갱신한다. 따라서 Compose runtime과 정책 객체 사이에 별도의 selected-root 값이나 수동 동기화는 없다. 복원한 map에 root가 누락되거나 추가된 경우, 첫 root가 일치하지 않는 경우, overlay에 root key가 포함된 경우, 앱 key가 아닌 값이 포함된 경우는 거부한다.

각 root는 독립적인 `rememberDecoratedNavEntries` 호출과 자체 saveable-state 및 ViewModelStore decorator를 가진다. 다섯 호출은 모두 composition에 유지하되, 선택한 root의 decorated entry만 `NavDisplay`에 제공한다. `Search`처럼 같은 overlay key가 root 사이에서 decorator 상태를 공유하지 않도록 entry content key에는 소유 root를 포함한다.

다른 root를 선택해도 이전 root와 새 root의 stack은 모두 보존한다. 현재 root를 다시 선택하면 그 root의 overlay만 root entry까지 pop하여 기존 하단 navigation의 기대 동작을 유지한다. 시스템 Back은 선택한 root의 마지막 overlay entry만 pop하며, root entry에 도달한 root stack은 변경하지 않는다.

## 결과

- root 전환은 해당 root stack에 남아 있는 Navigation 3 entry의 수명과 상태를 보존한다. 여기에는 MetroX entry 범위 ViewModel과 `rememberSaveable` 상태가 포함된다.
- 각 root stack과 선택한 root는 기존 다형 `SavedStateConfiguration`으로 프로세스 복원이 가능하다. route string이나 안정적이지 않은 표시 데이터는 key에 넣지 않는다.
- root에서 시작한 Search/detail overlay는 항상 그 root를 통해 돌아간다.
- 다섯 decorator graph는 의도적으로 유지한다. 이는 Navigation 3의 다중 back stack 패턴이며 root별 decorator 상태 보존에 필요하다.

## 범위 제외

- Deep link 연결 또는 route string 파싱
- Adaptive scene/list-detail 동작
- 인증 navigation
- cache, notification, Match UI/Card 변경 또는 신규 dependency

## 검증

`AppNavigationStateTest`는 root 전환, root 재선택, 선택한 stack에만 적용되는 Back, 동일 stack identity와 잘못된 복원 map 거부를 고정한다. `AppNavKeySerializationTest`는 모든 root stack과 선택한 root의 round-trip을 검증한다. iOS의 `AppNavigationRuntimeUiTest`는 production runtime seam을 통해 fixture entry content를 주입하고, 실제 entry의 `LocalViewModelStoreOwner`/`ViewModelProvider` 수명, root 왕복 시 ViewModel·페이지/탭·`rememberSaveable`·lazy list 위치 보존, pop 시 detail에만 적용되는 `onCleared`를 검증한다. 또한 두 root에 같은 `Search` key를 push해 saveable state/ViewModel이 root별로 분리되고, 한 root의 pop이 다른 root entry를 clear하지 않는지 검증한다.

## 참고 자료

- [Navigation 3 multiple back stacks](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Navigation 3 runtime `rememberDecoratedNavEntries`](https://developer.android.com/reference/kotlin/androidx/navigation3/runtime/package-summary#rememberDecoratedNavEntries(kotlin.collections.List,kotlin.collections.List,kotlin.Function1))
