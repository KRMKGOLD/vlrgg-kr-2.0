# ADR-0003: Root별 저장 Navigation stack

- 상태: 승인됨
- 날짜: 2026-08-23
- 범위: [Issue #37](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/37) N1
- 관련 문서: [Runtime](../app-runtime.md), [Feature navigation](../../feature/README.md)

## 배경

하단 root가 하나의 stack을 공유하면 root 전환 때 overlay 경로, entry ViewModel과 저장 가능한 UI 상태가 사라진다. 탭을 오가더라도 로드한 데이터, 선택 탭, scroll과 `rememberSaveable` 상태를 유지해야 한다.

## 결정

News, Matches, MyPage, Events, About는 각각 독립 `rememberNavBackStack`과 decorator state를 가진다. 선택 root만 `NavDisplay`에 제공하지만 모든 root의 decorator 호출은 composition에 유지한다.

- 선택 root는 `rememberSaveable`의 단일 state로 관리한다.
- 복원 map은 정확한 root 집합, 각 stack의 올바른 첫 root, 앱 key만 허용한다.
- 같은 overlay key가 여러 root에서 state를 공유하지 않도록 content key에 owning root와 entry instance를 포함한다.
- Root 전환은 양쪽 stack을 보존한다. 같은 root 재선택은 해당 overlay만 root까지 pop한다.
- Back은 선택 root의 마지막 overlay만 pop하며 root entry에서는 stack을 변경하지 않는다.

## 결과

Root 왕복 뒤 Navigation entry, MetroX ViewModel과 saveable UI 상태가 유지된다. 모든 stack과 선택 root는 `SavedStateConfiguration`으로 process 복원이 가능하며 key에는 안정적인 식별자만 둔다. Search/detail overlay는 진입한 root로 돌아간다.

다섯 decorator graph를 계속 유지하는 비용은 의도된 다중 back stack 동작이다.

## 범위와 검증

Deep link, adaptive scene와 인증 navigation은 이 결정의 범위가 아니다.

State/serialization test는 root 전환·재선택·Back·잘못된 복원 거부를 검증한다. Runtime UI test는 root별 ViewModel, 탭, scroll과 `rememberSaveable` 격리, detail pop의 entry 정리와 같은 Search key의 root 간 분리를 검증한다.

## 참고

- [Navigation 3 multiple back stacks](https://developer.android.com/guide/navigation/navigation-3/save-state)
