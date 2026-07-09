# server Agent Rules

## Source of Truth

- 상세 서버 설계는 `../docs/architecture/server-architecture.md`를 따른다.
- 앱/서버 API 경계는 `../docs/architecture/app-architecture.md`도 함께 확인한다.
- 루트 운영 규칙은 `../AGENTS.md`를 함께 따른다.
- 이 파일에는 `server`에서 바로 적용할 짧은 실행 규칙만 둔다.

## Rules

- `Application.kt`는 얇게 유지하고 plugin, routing, feature 설정은 별도 함수나 패키지로 분리한다.
- Route handler에는 HTML scraping/parsing 구현 세부사항을 넣지 않는다.
- Feature 코드는 route, service, scraper, parser, mapper, response DTO 책임을 분리한다.
- VLR.GG HTML selector와 Jsoup traversal은 parser 경계 안에 모은다.
- `SourceModel`을 route response로 직접 반환하지 않는다.
- 원본 HTML, Jsoup `Document`, 내부 parser error를 client에 그대로 노출하지 않는다.
- 서버 변경 후 가능한 경우 `./gradlew :server:test`로 확인한다.
