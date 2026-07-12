# App Runtime Composition

## 목적과 적용 상태

이 문서는 Android와 iOS가 공유하는 앱 runtime의 **짧은 기본 원칙**을 정의한다. Compose root, Metro DI, Navigation 3의 구체 API나 파일 구조를 미리 확정하는 구현 명세가 아니다.

현재 앱은 Compose Multiplatform 템플릿 단계다. 아래 원칙은 첫 DI 또는 navigation 기능을 구현할 때 적용하며, dependency version·platform host 구조·navigation 형태는 해당 기능의 요구사항과 당시 라이브러리 API를 확인해 결정한다.

## 기본 원칙

1. 플랫폼 runtime owner는 필요한 app graph를 Compose recomposition 경로 밖에서 준비하고 공통 `App`에 전달한다. graph를 `App()`이나 feature composable에서 새로 만들지 않는다.
2. 공통 `App`은 theme와 app-level composition을 연결한다. Metro ViewModel을 실제로 도입하는 경우에만 적절한 상위 경계에서 factory를 제공한다. feature가 별도의 app graph나 전역 service locator를 만들지 않는다.
3. navigation 상태는 navigation owner가 관리한다. Screen은 callback으로 navigation 의도를 전달하고, ViewModel은 back stack이나 controller를 직접 조작하지 않는다.
4. 저장·복원이 필요한 navigation key에는 안정적인 식별자만 넣고 직렬화 가능하게 만든다. serialization 방식과 `SavedStateConfiguration`은 실제 target과 사용 API에 맞춰 구현 작업에서 정한다.
5. destination별 ViewModel 수명이 필요한 화면은 Navigation 3의 entry scope를 사용한다. app 또는 feature 공유 상태의 수명은 기능 요구사항에 맞춰 별도로 정한다.
6. preview와 테스트는 production 전역 객체를 우회해 사용하지 않고, 필요한 state·callback·graph 또는 factory를 명시적으로 제공한다.

## 구현 시 결정할 항목

다음은 이 문서에서 고정하지 않는다.

- Android `Application`/Activity와 iOS SwiftUI/UIKit host의 구체 topology 및 graph 생성 API
- Metro annotation, scope, binding 구성과 platform binding 경계
- 단일 또는 복수 back stack, adaptive scene, deep link, 인증 흐름, 전체 destination 구조
- key hierarchy와 KMP state-restoration serializer 구성
- Navigation 3 decorator 조합, ViewModel scope, dependency version
- navigation 복원과 관련한 test 범위

기능 작업에서는 필요한 항목만 선택해 구현하고, 사용한 runtime 전략과 검증 방법을 관련 feature 또는 architecture 문서에 남긴다. Navigation 구조를 변경할 때는 기존 화면 흐름과 상태 복원 영향만 함께 검토한다.

## 관련 문서

- [App Architecture](app-arch.md): module 책임과 공통 package 방향
- [UI Layer](ui-layer.md): Screen callback, ViewModel, feature UI 규칙
- [Data Layer](data-layer.md): data binding과 repository 경계

## 공식 참고

- [Compose Multiplatform Navigation 3](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Navigation 3 overview](https://developer.android.com/guide/navigation/navigation-3)
- [Compose Multiplatform ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)
- [Metro documentation](https://zacsweers.github.io/metro/latest/)
