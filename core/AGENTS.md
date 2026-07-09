# core Agent Rules

## Source of Truth

- 앱/서버 경계는 `../docs/architecture/app-architecture.md`와 `../docs/architecture/server-architecture.md`를 함께 확인한다.
- 루트 운영 규칙은 `../AGENTS.md`를 함께 따른다.
- 이 파일에는 `core`에서 바로 적용할 짧은 실행 규칙만 둔다.

## Rules

- `core`에는 앱과 서버가 함께 써도 안전한 순수 Kotlin 코드만 둔다.
- Compose UI, Android Context, iOS API, Ktor Application 의존성을 넣지 않는다.
- request/response DTO, API contract, transport-oriented model은 넣지 않는다.
- 공통 value object, 작은 validation utility, framework-free formatter 같은 코드만 우선 배치한다.
- 특정 feature에서만 쓰이는 코드는 해당 feature 모듈에 둔다.
- 공통으로 보인다는 이유만으로 먼저 `core`로 올리지 않는다. 실제 양쪽 사용처가 생긴 뒤 이동한다.
- API 형태를 바꾸면 `app/shared`와 `server` 양쪽 영향을 확인한다.
