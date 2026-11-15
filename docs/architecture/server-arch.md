# Server Architecture

## Purpose and structure

`server`는 Ktor 3·Netty 기반의 VLR.GG 전용 backend다. VLR.GG HTML을 요청하고 Jsoup으로 해석해 앱이 사용하는 JSON API로 변환한다. 앱에는 CSS selector, Jsoup type, 원본 HTML과 scraping 보정을 노출하지 않는다.

서버는 단일 `:server` Gradle module 안에서 feature 단위로 구성한다. 각 feature는 route, service, scraper, parser, mapper와 app-facing response의 흐름을 소유한다. 공통 HTTP contract, scraping transport와 feature에 독립적인 parser 헬퍼만 feature 밖에 둔다. `Application.kt`는 plugin과 feature dependency를 조립하며 route handler 안에서 client나 scraper를 만들지 않는다. 별도 DI framework나 Gradle module은 실제 복잡성이 필요할 때만 추가한다.

## Scraping and freshness

- 앱 요청 시점에 VLR.GG를 조회한다. 일반 콘텐츠에 database, durable cache, 주기 갱신이나 stale-on-error fallback을 사용하지 않는다.
- 같은 canonical upstream resource의 동시 요청은 진행 중 fetch 하나를 공유할 수 있지만 이전 성공 응답을 반환하는 cache로 사용하지 않는다.
- upstream network 또는 parsing 실패 시 이전 데이터를 대신 반환하지 않고 안전한 실패 응답을 반환한다.
- 공통 transport는 Ktor CIO를 사용하고 HTTPS의 `www.vlr.gg`와 `vlr.gg`만 직접 요청한다. redirect를 따르지 않으며 HTTP, 비표준 port, user-info와 다른 host는 요청 전에 거절한다.
- transport는 명시적 `User-Agent`, bounded timeout과 response size limit을 적용한다. 미소비 response channel은 취소하고 retry는 기본으로 설치하지 않는다.
- canonical upstream URL은 로그에서도 `https://www.vlr.gg/` origin으로 제한한다. request path, query, fragment, raw HTML과 selector를 기록하지 않는다.
- Scraper는 content 획득, Parser는 Jsoup DOM 해석, Mapper는 app-facing response 변환을 담당한다. 중요한 DOM 가정은 최소 HTML fixture로 검증한다.

## Public API and failure boundary

모든 실패는 HTTP status, 안정적인 `ApiErrorCode`, 안전한 `message`를 가진 공통 envelope로 반환한다. 현재 code는 `INVALID_REQUEST`, `NOT_FOUND`, `UPSTREAM_NETWORK_FAILURE`, `SOURCE_PARSING_FAILURE`, `RESPONSE_TOO_LARGE`, `INTERNAL_ERROR`, `RATE_LIMITED`, `SERVER_BUSY`, `REQUEST_TOO_LARGE`, `REQUEST_TIMEOUT`이다.

- network와 parsing 실패를 구분한다.
- exception message, stack trace, raw HTML, selector와 upstream URL을 public response에 넣지 않는다.
- parsing failure의 cause는 서버 내부에 보존하고, 로그에는 고정된 canonical origin만 기록한다. cancellation과 JVM `Error`는 일반 실패로 바꾸지 않는다.
- 앱 Data Layer는 인식된 429/`RATE_LIMITED`와 503/`SERVER_BUSY`만 `AppResult.Busy(retryDelay)`로 변환한다. 그 밖의 실패는 generic failure이며 UI가 HTTP status나 server code를 직접 해석하지 않는다. 자세한 보호·재시도 계약은 [공개 API 보호 계약](server-public-api-protection.md)을 따른다.

OpenAPI와 Swagger route는 `VLRGG_ENABLE_API_DOCUMENTATION=true`인 local 개발에서만 켠다. 문서에는 public path, request validation, response DTO와 stable error만 포함하며 production 공개 전에는 접근 정책과 asset pinning을 별도로 정한다.

## Logging and observability

공개 조회는 요청마다 raw 정보를 기록하지 않고 고정 route/status/error/latency bucket과 bounded failure sample만 남긴다.

- `INTERNAL_ERROR`와 `SOURCE_PARSING_FAILURE`만 Error Reporting용 ERROR event다.
- 로그에 request path/query/IP/header, token, exception message, raw HTML과 selector를 넣지 않는다. ERROR event의 stack에는 예외 종류와 코드 위치만 남긴다.
- trace는 project 환경 값이 있고 형식이 유효한 단일 `X-Cloud-Trace-Context`에서만 연결한다.
- failure event의 stack은 frame 32개와 cause 깊이 3으로 제한하고, event 수는 category별로 프로세스당 60초에 4건으로 제한한다. 출력 sample 수를 전체 실패 수로 해석하지 않는다.
- 운영 정책, 장애 주입, 권한과 복원 절차는 [서버 컨테이너 배포 경로](server-container-deployment.md)의 #122 runbook이 소유한다. Crashlytics는 이 운영 관측과 별개다.

## Configuration and deployment

config와 secret은 source code에 넣지 않는다.

일반 조회 서버는 서울 Cloud Run에 배포돼 있다. root `Dockerfile`은 repository root의 Gradle 설정과 `server`를 사용해 `:server:installDist`를 만들며 runtime은 `0.0.0.0`, provider `PORT`, `/health`를 지원한다. exact SHA 검증, private validation, promotion, rollback과 비용 중단은 [배포 runbook](server-container-deployment.md)이 소유한다.

## Verification

- 기본 서버 변경: `./gradlew :server:test`
- packaging 또는 module 경계 변경: `./gradlew :server:build :server:installDist`
- parser 변경: 최소 HTML fixture 기반 parser test
- route/error 변경: Ktor `testApplication` 기반 status·envelope test

변경 시 request-time freshness, stale fallback 부재, public error/redaction, cancellation 전파와 해당 feature의 route/service/parser 경계를 함께 검증한다.
