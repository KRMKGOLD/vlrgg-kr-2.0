# server Agent Rules

## Source of Truth

- 상세 서버 설계는 `../docs/architecture/server-arch.md`를 따른다.
- 앱/서버 API 경계는 `../docs/app-arch/app-arch.md`도 함께 확인한다.
- 루트 운영 규칙은 `../AGENTS.md`를 함께 따른다.
- 이 파일에는 `server`에서 바로 적용할 짧은 실행 규칙만 둔다.

## Rules

- `Application.kt`는 얇게 유지하고 plugin, routing, feature 설정은 별도 함수나 패키지로 분리한다.
- Route handler에는 HTML scraping/parsing 구현 세부사항을 넣지 않는다.
- Feature 코드는 route, service, scraper, parser, mapper, response DTO 책임을 분리한다.
- VLR.GG HTML selector와 Jsoup traversal은 parser 경계 안에 모은다.
- `SourceModel`을 route response로 직접 반환하지 않는다.
- 모든 실패 response는 HTTP status, `ApiErrorCode`, 안전한 message를 가진 공통 envelope로 반환한다.
- upstream 통신 실패는 public `UPSTREAM_NETWORK_FAILURE`, DOM 해석 실패는 public `SOURCE_PARSING_FAILURE`로 구분한다. 원본 HTML, Jsoup `Document`, URL, 내부 parser error는 client에 그대로 노출하지 않는다.
- feature별 endpoint, request parameter, 성공 response contract는 해당 기능의 기획·UI·데이터 요구에서 정하며 아키텍처 예시로 미리 고정하지 않는다.
- 이전 scraping 결과를 failure fallback으로 반환하지 않는다. 동시 중복 fetch를 공유하는 구현은 필요할 때만 추가한다.
- console request/failure logging을 기본으로 하고, Discord notification은 실제 운영 요구가 생길 때만 best-effort로 추가한다.
- 서버 변경 후 가능한 경우 `./gradlew :server:test`로 확인한다.
