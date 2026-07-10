# app/iosApp Agent Rules

## Source of Truth

- 상세 앱 설계는 `../../docs/app-arch/app-arch.md`를 따른다.
- 앱 runtime composition 계약은 `../../docs/app-arch/app-runtime.md`를 따른다.
- 루트 운영 규칙은 `../../AGENTS.md`를 함께 따른다.
- 이 파일에는 iOS 엔트리 모듈에서 바로 적용할 짧은 실행 규칙만 둔다.

## Rules

- `app/iosApp`은 iOS 앱 엔트리 포인트, SwiftUI host, iOS-only integration만 담당한다.
- iOS root host는 `AppGraph`를 한 번 생성해 공통 `App(graph)`에 전달하고 host 수명 동안 유지한다.
- 비즈니스 로직, 공통 UI, ViewModel, repository 구현은 기본적으로 `app/shared`에 둔다.
- iOS API, SwiftUI bridge, platform-specific lifecycle 처리가 필요한 경우에만 이 모듈을 수정한다.
- 공통 Compose 화면 흐름은 `app/shared`의 navigation 경계를 따른다.
- iOS-only 설정을 바꿀 때는 Xcode project와 `app/shared` framework 연결 영향을 함께 확인한다.
- iOS 변경 후 가능한 경우 Xcode 실행 또는 `./gradlew :app:shared:iosSimulatorArm64Test`로 관련 영향을 확인한다.
