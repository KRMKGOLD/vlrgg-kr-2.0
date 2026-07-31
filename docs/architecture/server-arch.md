# Server Architecture

## Purpose

`server`는 Ktor 3 기반의 VLR.GG 전용 backend다. 서버의 주된 기능은 외부 HTML을 scraping하고 해석해 앱이 사용할 수 있는 안정적인 JSON API response로 가공하는 것이다.

Compose Multiplatform 앱은 VLR.GG HTML 구조를 알지 않는다. CSS selector, Jsoup `Document`·`Element`, 원본 HTML, scraping 보정은 서버 경계 안에 머문다. 앱은 app-facing API contract만 사용한다.

첫 단계의 서버는 개인 앱을 위한 작은 개발 서버다. 일반 콘텐츠 조회에는 데이터베이스, 주기 갱신 job, durable cache를 전제하지 않는다. Match 알림만 익명 Target 구독·delivery 상태를 위한 좁은 Firestore 영속 저장과 request-bound scheduler를 feature-specific 예외로 허용한다. 상세 persistence, provider, retry, authority, 검증과 Stage 2 gate는 [Stage 1.1 technical contract](server-fcm-stage1.md)가 소유한다. 이 문서는 개별 feature 구현이나 API 명세가 아니라 Ktor 서버의 아키텍처와 개발 방향을 정한다.

## Current State and Direction

현재 `server`는 JSON serialization, request/failure logging, 공통 error envelope, Ktor CIO 기반 HTML transport와 `/health`를 공통 기반으로 제공한다. 이 기반 위에서 News, Matches, Events, Search, Team Detail, Player Detail, Series Detail의 app-facing API와 런타임 OpenAPI/Swagger 개발 문서를 구현했다. Match 알림 Stage 1 코드에는 default-disabled loopback 구독 API, H2/Flyway desired state, process-owned observation/delivery loop, START/END intent, named Firebase Admin adapter와 claim/retry lifecycle이 존재한다.

Stage 1.1은 이 알림 runtime을 Firestore SDK + Emulator, 익명 Target ID/Secret, START-only intent와 request-bound scheduler use case로 교체했고 offline server evidence가 GREEN이다. App, 실제 App Check/FCM, production Firestore, Cloud Run/Scheduler/WIF/CD smoke는 `NOT RUN — Stage 2`다. 현재 구현과 경계의 상세는 [Stage 1.1 contract](server-fcm-stage1.md)와 [ADR-0002](adr/0002-match-notification-stage1-1-offline-firestore-boundary.md)를 따른다.

서버 기능은 단일 `:server` Gradle module 안에서 feature-based modular structure로 개발한다.

- feature는 route부터 app-facing response까지 한 흐름을 소유한다.
- 공통 Ktor plugin, public HTTP contract, scraping utility는 feature 밖의 좁은 공통 영역에 둔다.
- 실제 기능이 없는 빈 package·file을 미리 만들지 않는다.
- feature가 충분히 커질 때만 별도 Gradle module 분리를 검토한다.

Ktor는 단일한 프로젝트 구조를 강제하지 않는다. 단일 Gradle module, 논리적 feature 경계, `Application` extension module과 명시적 dependency 전달은 현재의 작은 서버에 맞춰 이 저장소가 선택한 Ktor 지원 방식이다. `plugins/`, `routing/` 같은 디렉터리와 아래 파일명은 Ktor의 필수 구조가 아닌 저장소 관례다. [Ktor application structure](https://ktor.io/docs/server-application-structure.html), [Ktor routing organization](https://ktor.io/docs/server-routing-organization.html), [Ktor modules](https://ktor.io/docs/server-modules.html)

## Target Package Shape

`server/src/main/kotlin/kr/co/cotton/vlrgg_mobile` 아래에서 다음과 같은 형태를 사용할 수 있다. 이 트리는 책임 경계를 설명하는 비구속적 예시이며, feature마다 동일한 파일을 모두 만들도록 요구하지 않는다.

```text
server/
  Application.kt
  config/
    ServerConfig.kt
  plugins/
    Serialization.kt
    ErrorHandling.kt
    Monitoring.kt
  routing/
    Routing.kt
  common/
    http/
      ApiErrorCode.kt
      ApiErrorResponse.kt
    scraping/
    time/
    notification/              # Discord notification을 실제 도입할 때만
  feature/
    <feature>/
      <Feature>Module.kt
      <Feature>Routes.kt
      <Feature>Service.kt
      <Feature>Scraper.kt
      <Feature>Parser.kt
      <Feature>Mapper.kt
      <Feature>Response.kt
      <Feature>SourceModel.kt
```

작은 feature는 책임이 명확하다면 파일을 합칠 수 있다. 한 feature의 파일 수가 늘어 가독성이 떨어질 때만 `route`, `service`, `scraper`, `model`, `mapper` 같은 feature 내부 package로 나눈다. endpoint path, request parameter, pagination, 성공 JSON field와 feature별 DTO는 이 예시가 정하지 않으며 이후 기능 기획에서 정의한다.

## Application Entry and Dependency Composition

`Application.kt`는 얇게 유지한다.

```kotlin
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureErrorHandling()
    configureRouting()
}
```

담당 책임은 서버 시작, config 로드, plugin 설치, feature module 연결뿐이다. feature route, HTML parsing, response mapping을 직접 넣지 않는다.

초기 서버는 feature/module 함수 parameter 또는 명시적인 composition으로 dependency를 전달한다. route handler 안에서 scraper, service, HTTP client를 생성하지 않는다. manual wiring이 읽기 어렵거나 test setup을 해치기 전에는 별도 DI framework를 도입하지 않는다. 그 시점이 오면 Ktor built-in DI를 포함해 다시 검토한다.

## Feature Boundary

| Non-binding example | Responsibility |
| --- | --- |
| `*Module.kt` | feature dependency 조립과 Ktor module 연결 |
| `*Routes.kt` | HTTP input 검증, service 호출, status/response 반환 |
| `*Service.kt` | use-case 수준 orchestration과 feature 정책 |
| `*Scraper.kt` | upstream VLR.GG content 요청 |
| `*Parser.kt` | raw HTML을 내부 source model로 해석 |
| `*Mapper.kt` | source model을 app-facing response로 변환 |
| `*Response.kt` | 앱에 공개하는 JSON response model |
| `*SourceModel.kt` | VLR.GG 원본 구조를 표현하는 server-internal model |

Route handler는 request를 service 호출로 바꾸고 response를 반환하는 곳이다. 작은 feature가 파일을 합치더라도 표의 책임 경계는 유지한다. Jsoup traversal, CSS selector, raw HTML 보정은 route와 service 밖의 parser 경계에 둔다.

## Scraping, Freshness, and Upstream Policy

VLR.GG HTML은 외부의 불안정한 source contract다. DOM 구조와 텍스트 형식이 바뀔 수 있다고 가정한다. Scraping은 서버의 주된 기능이며, Jsoup은 scraping subsystem에서 DOM을 해석하는 필수 parser다.

- 앱 요청 시점에 VLR.GG를 조회한다. 최신성이 우선이므로 주기 갱신이나 cache-first 응답을 기본 구조로 두지 않는다.
- 일반 콘텐츠 응답에는 database, durable cache, stale-on-error fallback을 첫 단계에 도입하지 않는다. Match 알림의 Firestore는 콘텐츠 cache가 아니라 익명 Target, 구독과 delivery state를 보존하는 feature-specific 예외다.
- 같은 canonical upstream resource를 동시에 요청했을 때만 하나의 진행 중 fetch를 공유할 수 있다. 이는 중복 upstream 요청을 줄이기 위한 동시성 보호이며, 이전 성공 데이터를 반환하는 cache가 아니다.
- upstream network failure 또는 parsing failure가 발생하면 이전 데이터를 반환하지 않고 실패 응답을 반환한다.
- 공통 HTML transport는 Ktor CIO client를 사용한다. transport는 명시적인 `User-Agent`, connect 5초·request/socket 10초의 bounded timeout과 최대 1 MiB response body 기본값을 가지며, manual composition에서 설정을 바꿀 수 있다.
- transport는 생성한 `HttpClient`를 소유한다. composition root는 `Application.createUpstreamHtmlTransport()`로 application마다 한 번만 만들고 application stopping lifecycle에 cleanup을 연결한 뒤, close 할 수 없는 transport contract만 feature에 전달한다.
- `get()` 한 번은 upstream 요청 한 번만 수행한다. redirect follow를 비활성화하므로 모든 3xx와 다른 non-success status는 추가 요청 없이 `UPSTREAM_NETWORK_FAILURE`로 매핑하고, retry plugin은 설치하지 않는다. bounded retry가 필요해지면 기능 요구와 upstream 동작을 근거로 별도로 도입한다.
- transport 경계는 HTTPS의 `www.vlr.gg`와 `vlr.gg`만 direct target으로 허용한다. 두 host는 VLR.GG의 허용된 명시 요청 대상이지만 서로 간 redirect도 follow하지 않으며, HTTP·비표준 port·user-info·그 밖의 host는 요청 전에 실패한다.
- upstream response는 streaming으로 읽고, non-success status·선언된 body size 초과·읽는 중의 body size 초과처럼 끝까지 소비하지 못한 경우 response raw channel을 취소한다. 이로써 공유 CIO client connection이 미소비 body에 점유되지 않는다.
- upstream URL은 public response에 넣지 않는다. server log용 canonical URL은 항상 primary origin `https://www.vlr.gg/`로 제한해 request-derived path, query, fragment, user-info를 남기지 않는다. 허용되지 않은 target도 같은 안전한 origin만 기록한다.

Scraper, Parser, Mapper의 경계는 다음과 같다.

- Scraper: 구현 시 선택한 transport로 원본 content를 가져온다.
- Parser: Jsoup DOM parsing으로 원본 content를 `SourceModel`로 해석한다.
- Mapper: `SourceModel`을 app-facing `Response`로 바꾼다.

즉, 기본 책임 흐름은 `Route -> Service -> Scraper -> Parser -> SourceModel -> Mapper -> Response`다. DOM parsing 기술은 Jsoup으로 고정한다.

공통 upstream HTTP transport는 Ktor CIO로 정했다. feature별 request 조립과 content 획득 API는 구현 요구에 따라 정하되, feature는 manual wiring으로 전달받은 transport를 사용한다.

Jsoup `Document`와 `Element`, CSS selector, raw HTML, parsing 보정은 parser 내부에서만 사용한다. `SourceModel`, raw HTML, Jsoup type을 route response로 반환하거나 scraping 경계 밖으로 노출하지 않는다. 중요한 페이지는 최소 HTML fixture를 사용해 parser 가정을 테스트한다.

## Match Notification: Stage 1.1 offline gate

Match 알림은 일반 request-time scraping/no-database 정책의 좁은 예외다. 로그인 없이 앱 설치 단위의 익명 Target을 만들고, 그 Target이 선택한 Match의 START 알림만 관리한다. App Check는 앱 진위를, one-time Target Secret은 Target 권한을 증명한다. FCM registration token은 전달 주소이며 FID나 물리 기기 identity가 아니다.

- 한 Target은 활성 Match를 최대 100개 구독한다.
- 전체 active unique Match도 최대 100개이며 같은 Match의 upstream 확인은 Target 수와 무관하게 한 번이다.
- 10분은 desired schedule 간격이고 실제 작업은 bounded `NotificationSchedulerUseCase` 호출로 수행한다.
- Match `UPCOMING`/`POSTPONED -> LIVE` 전환만 START intent를 생성한다. END 알림은 MVP에서 제외한다.
- subscription별 START intent는 하나이며 committed call marker 이후 결과가 불명확하면 `UNKNOWN`으로 격리하고 자동 재발송하지 않는다.
- Target, subscription, capacity, lease, fan-out cursor와 delivery intent는 Firestore에 영속화한다.
- 일반 scraping response와 이전 Match 결과를 Firestore cache나 failure fallback으로 저장하지 않는다.

Stage 1.1은 Firestore SDK의 transaction/query/document mapping을 Emulator에서 실제로 검증하고 App Check/FCM 외부 경계는 test-only fake로 검증한다. 일반 local/main/packaged runtime에는 fake provider나 public scheduler route가 없고 알림 API는 disabled/fail-closed다.

Stage 2는 Android/iOS Target client, 실제 App Check/FCM, production Firestore/IAM/index, OIDC Scheduler route, Cloud Run과 CD를 소유한다. 앱 삭제·재설치로 Target credential을 잃으면 새 Target을 만들며 이전 Target을 자동 복원·병합하지 않는다.

구체적인 Target API, Firestore transaction, provider command/result, retry, scheduler policy와 offline/live completion gate는 [Stage 1.1 technical contract](server-fcm-stage1.md)가 소유한다. 제품 흐름은 [Matches 기능 문서](../feature/matches/README.md), 배포 방향은 [CI/CD 문서](../ci-cd.md)를 따른다.

## Public API Error Contract

모든 실패 응답은 동일한 JSON envelope를 사용한다. HTTP status는 protocol-level 의미를, `code`는 안정적인 machine-readable 의미를 가진다.

```kotlin
@Serializable
data class ApiErrorResponse(
    val code: ApiErrorCode,
    val message: String,
)
```

`ApiErrorCode`는 `common/http`의 public server API contract다. 첫 구현은 최소한 다음 범주를 다룬다.

| Code | HTTP status | Meaning |
| --- | --- | --- |
| `INVALID_REQUEST` | `400 Bad Request` | path/query/body input이 유효하지 않음 |
| `NOT_FOUND` | `404 Not Found` | 요청한 route가 존재하지 않음 |
| `UPSTREAM_NETWORK_FAILURE` | `502 Bad Gateway` | VLR.GG 요청·연결·timeout 등 upstream 통신 실패 |
| `SOURCE_PARSING_FAILURE` | `502 Bad Gateway` | 응답은 받았지만 필요한 VLR.GG 구조를 해석할 수 없음 |
| `INTERNAL_ERROR` | `500 Internal Server Error` | 위 범주 밖의 처리 실패 |

규칙:

- network failure와 parsing failure는 server 내부와 public `code`에서 모두 구분한다.
- `message`는 개발 중 원인을 파악할 수 있는 안전한 요약이다. 예외 메시지, stack trace, raw HTML, selector, canonical upstream URL을 그대로 넣지 않는다.
- parsing failure는 canonical upstream URL과 `Exception` cause를 server 내부에 반드시 보존한다. request cancellation과 JVM `Error`는 public error envelope로 변환하지 않고 전파한다.
- 내부 failure는 sealed type 또는 focused exception으로 구현할 수 있다. 어느 방식이든 원인과 URL을 내부에서 보존하고 `ErrorHandling` 경계에서 `ApiErrorResponse`로 매핑한다.
- 앱 Data Layer를 구현할 때 모든 non-success response는 generic `AppResult.Failure`로 변환한다. UI는 `ApiErrorCode`를 해석하지 않는다. 오류별 UI 요구가 생기면 앱 Data·Domain·UI 문서를 함께 갱신한다.
- API error envelope는 `StatusPages` 등 공통 error handling plugin에서 일관되게 반환한다. Ktor는 예외 처리를 위한 `StatusPages` plugin을 제공한다. [Ktor StatusPages](https://ktor.io/docs/server-status-pages.html)

## OpenAPI and Swagger Development Documentation

현재 작은 개발 서버는 Ktor runtime routing metadata를 사용해 `/openapi.json`과 `/swagger`를 제공한다. 각 public `/api/v1` GET route는 실제 response DTO와 `ApiErrorResponse` schema를 재사용해 문서화하며, `/health`, validation-only guard route, 문서 route 자체는 spec에서 제외한다. 이 문서화 계층은 Route -> Service -> Scraper -> Parser -> SourceModel -> Mapper -> Response의 feature 흐름이나 scraping 동작을 바꾸지 않는다.

문서는 app-facing path, request validation, response DTO, stable error code만 노출한다. raw HTML, selector, Jsoup type, upstream URL, exception, SourceModel과 같은 server-internal detail은 OpenAPI description이나 schema에 넣지 않는다.

문서 route는 `VLRGG_ENABLE_API_DOCUMENTATION` environment variable이 정확히 `true`일 때만 등록되며, 기본값은 비활성이다. local 개발에서 문서를 보려면 `VLRGG_ENABLE_API_DOCUMENTATION=true ./gradlew :server:run`으로 실행한다. 향후 public deployment에서 노출을 검토할 때는 API surface와 구현 세부사항을 탐색할 수 있는 위험을 고려해 접근 정책을 먼저 결정해야 하며, Swagger UI가 Ktor 기본 설정의 외부 asset을 사용하므로 asset self-hosting 또는 version pinning도 함께 결정할 때까지는 문서 노출을 유지하지 않는다.

## Plugins, Logging, and Notification

`plugins`에는 모든 feature에 공통인 Ktor 설정만 둔다.

- `Serialization.kt`: JSON content negotiation
- `ErrorHandling.kt`: exception/failure를 public error envelope로 매핑
- `Monitoring.kt`: request logging과 failure logging
- CORS, authentication 등은 실제 client requirement가 생길 때만 추가

첫 단계의 관측은 별도 log platform 없이 콘솔 로그를 사용한다. 현재 `logback.xml`의 console appender 위에 Ktor `CallLogging`과 공통 `StatusPages` failure logging을 적용했다.

- `CallLogging`은 request를 INFO 수준으로 기록한다.
- 공통 failure logging은 `ApiErrorCode`, method, request path, 제한된 canonical upstream URL과 cause class만 기록한다.
- secret, FCM registration token, App Check evidence, client credential, raw HTML은 로그에 기록하지 않는다.

현재 Stage 1 logger의 identifier redaction gap은 Stage 1.1 전환에서 반드시 제거한다. Stage 1.1은 secret, registration token, App Check evidence, provider message ID, intent/claim identifier와 raw provider 오류를 기록하지 않고 bounded category/state/backlog만 관측한다. 이 검증이 GREEN이 되기 전에는 Stage 1.1을 구현 완료로 표시하지 않는다.

Discord notification은 선택적인 운영 확장이다. 실제 도입할 때 webhook secret을 environment variable로 주입하고, failure notifier는 best effort로 동작시킨다. Discord 전송 실패는 원래 API failure response를 바꾸거나 새로운 failure를 만들지 않는다. 반복 failure의 notification 제어가 필요해지는 시점에만 throttling을 추가한다.

## Configuration and Deployment

현재 구현은 Kotlin/JVM 기반 Ktor 3와 Netty를 사용하며 local 실행이 기준이다. 목표 production provider는 비용 최소화를 위한 Cloud Run이고 source deploy를 Dockerfile보다 먼저 검증한다. repository root가 `server`와 직접 의존 모듈 `core`, root Gradle 설정을 함께 제공해야 한다.

Stage 1.1은 `0.0.0.0`, `PORT`, `/health`, `:server:installDist`의 credential-free packaged smoke까지만 소유한다. Cloud Run source buildpack entrypoint, public host/base URL, no-traffic smoke, traffic 전환과 rollback은 Stage 2다. 자세한 gate는 [CI/CD 문서](../ci-cd.md)를 따른다.

server config와 secret은 source code에 넣지 않는다. `ServerConfig` 또는 동등한 config boundary를 실제 도입할 때 사용하고, Discord webhook 같은 secret은 environment variable로만 전달한다.

Stage 1.1과 후속 Stage 2는 다음 public-safe 경계를 지킨다.

- Firebase Admin credential과 FCM send 권한은 앱이 아니라 trusted server boundary에만 둔다.
- credential은 외부 설정으로 주입하고 source code, repository, public response, log에 넣지 않는다.
- 실제 Firebase project identifier, service-account JSON, registration token을 예시나 placeholder 대신 커밋하지 않는다.
- Stage 1.1은 production ADC/FirebaseApp을 활성화하지 않는다. Stage 2에서 당시 공식 Admin SDK를 재검증하고 runtime Service Account로 production adapter를 구성한다.
- 등록 방식과 invalid-target 처리는 Firebase의 [registration 관리](https://firebase.google.com/docs/cloud-messaging/manage-tokens?hl=en), [error code](https://firebase.google.com/docs/cloud-messaging/error-codes), [trusted server 환경](https://firebase.google.com/docs/cloud-messaging/server-environment), [Admin SDK 설정](https://firebase.google.com/docs/admin/setup) 문서를 Stage 2 live smoke와 dependency 추가 시 다시 확인한다.

## Testing Expectations

- parser test: HTML fixture를 기반으로 selector와 source-structure 가정을 검증
- mapper test: `SourceModel`에서 response DTO로 변환되는 규칙을 검증
- service test: request-time scraping, concurrent fetch coalescing을 구현한 경우 그 정책과 stale fallback 부재를 검증
- notification test: Target Secret authority, token refresh, 같은 Target/Match의 set/remove와 global OFF 수렴, Firestore transaction/capacity, 10분 schedule slot, Match 단위 중복 제거, subscription별 START intent, retry/UNKNOWN과 provider-invalid Target 정리를 검증
- error handling test: 각 failure가 올바른 HTTP status, `ApiErrorCode`, 안전한 `message` envelope로 변환되는지 검증
- route test: 기능 기획에서 정한 request validation과 success/error response contract를 Ktor `testApplication {}`으로 검증. [Ktor server testing](https://ktor.io/docs/server-testing.html)

서버 변경의 기본 검증 명령은 `./gradlew :server:test`다. Stage 1.1 persistence 구현 이후에는 명시적 Emulator 환경에서 `./gradlew :server:firestoreEmulatorTest`도 필수다.

## Document Placement and Change Rules

이 문서는 `docs/architecture/server-arch.md`에 둔다. 앱 계층 문서인 `docs/app-arch/`와 구분해, 프로젝트 전체의 server/API/upstream 경계를 관리한다.

- `Application.kt`를 얇게 유지한다.
- route handler에 scraping/parsing 세부사항을 넣지 않는다.
- public response model과 server-internal source model을 섞지 않는다.
- error contract, freshness policy, notification, DI, deployment requirement가 바뀌면 이 문서를 갱신한다.
- 기능 없는 빈 scaffolding과 구현되지 않은 infrastructure를 먼저 만들지 않는다.
