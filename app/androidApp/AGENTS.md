# app/androidApp Agent Rules

## Source of Truth

- 상세 앱 설계는 `../../docs/app-arch/app-arch.md`를 따른다.
- 앱 runtime composition 계약은 `../../docs/app-arch/app-runtime.md`를 따른다.
- 루트 운영 규칙은 `../../AGENTS.md`를 함께 따른다.
- 이 파일에는 Android 엔트리 모듈에서 바로 적용할 짧은 실행 규칙만 둔다.

## Rules

- `app/androidApp`은 Android 앱 엔트리 포인트와 Android-only integration만 담당한다.
- Android runtime owner는 필요한 `AppGraph`를 Compose recomposition 경로 밖에서 준비해 공통 `App`에 전달한다.
- 비즈니스 로직, 공통 UI, ViewModel, repository 구현은 기본적으로 `app/shared`에 둔다.
- Android Context, Activity, permission, intent, platform API가 필요한 코드만 이 모듈에 둔다.
- 플랫폼별 DI binding이 필요할 때만 이 모듈에서 구현하고, 공통 계약은 `app/shared`에 둔다.
- Android-only dependency를 추가할 때는 shared/commonMain에 새 의존성이 새지 않게 한다.
- Android 변경 후 가능한 경우 `./gradlew :app:androidApp:assembleDebug`로 확인한다.
