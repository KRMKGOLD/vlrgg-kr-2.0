# Server Architecture

## Purpose

`server`는 Ktor 3 기반의 VLR.GG 전용 backend다. 서버의 주된 기능은 외부 HTML을 scraping하고 해석해 앱이 사용할 수 있는 안정적인 JSON API response로 가공하는 것이다.

Compose Multiplatform 앱은 VLR.GG HTML 구조를 알지 않는다. CSS selector, Jsoup `Document`·`Element`, 원본 HTML, scraping 보정은 서버 경계 안에 머문다. 앱은 app-facing API contract만 사용한다.

첫 단계의 서버는 개인 앱을 위한 작은 개발 서버다. 일반 콘텐츠 조회에는 데이터베이스, 주기 갱신 job, durable cache를 전제하지 않는다. 1차 MVP에 기획된 Match 알림은 구현할 때만 사용자 구독 상태를 위한 좁은 영속 저장과 scheduler 예외가 필요하지만, 현재 서버에는 이 기능이 없다. 이 문서는 개별 feature 구현이나 API 명세가 아니라 Ktor 서버의 아키텍처와 개발 방향을 정한다. 새로운 dependency와 실제 Gradle 설정, endpoint와 성공 response contract는 해당 기능의 기획·UI·데이터 요구가 정해지는 구현 시점에 결정한다.

## Current State and Direction

원격 `main`의 `d220a69b59d4863c0ade0e09c77c1192c36bba95` 기준으로 현재 `server`는 JSON serialization, request/failure logging, 공통 error envelope, Ktor CIO 기반 HTML transport와 `/health`를 공통 기반으로 제공한다. 이 기반 위에서 News, Matches, Events, Search, Team Detail, Player Detail, Series Detail의 app-facing API를 구현했다. 일반 콘텐츠 조회를 위한 cache와 별도 DI framework, Match 알림의 구독 API·영속 저장·scheduler·delivery는 도입하지 않았다.

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
- 일반 콘텐츠 응답에는 database, durable cache, stale-on-error fallback을 첫 단계에 도입하지 않는다. 향후 Match 알림을 구현할 때의 구독 저장소는 콘텐츠 cache가 아니라 알림 delivery state를 보존하는 feature-specific 예외다.
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

## Match Notification: Planned Exception

Match 알림은 일반 request-time scraping 정책의 좁은 예외로 기획되어 있지만 아직 구현되지 않았다. 현재 서버에는 구독 endpoint, 익명 설치 식별, 영속 저장, polling scheduler, push/delivery provider 또는 delivery state가 없다. 구현 시 사용자가 특정 Match의 알림을 설정하면 앱은 Match 즐겨찾기를 로컬에 저장하고, 서버에는 익명 설치 단위의 알림 구독을 등록한다.

- Team과 Player 즐겨찾기는 서버 알림 구독을 만들지 않는다.
- 서버는 활성 구독의 고유 Match ID를 10분마다 확인한다.
- 같은 Match를 여러 설치가 구독해도 upstream 확인은 Match 단위로 통합한다.
- 경기 시작과 경기 종료 알림은 각각 한 번만 발송한다.
- scheduler 재시도와 서버 재시작에도 중복 발송되지 않도록 delivery state를 영속화하고 idempotent하게 처리한다.
- Match 즐겨찾기 해제는 해당 설치의 알림 구독을 제거한다.
- 모든 구독이 해제되거나 경기가 종료되고 필요한 알림이 발송되면 해당 Match 추적을 중단한다.
- 시간 변경, 연기, 취소, parsing/network failure는 서로 구분되는 내부 상태로 다룬다.
- 알림 구독·delivery state는 이전 scraping 결과를 API failure fallback으로 제공하는 cache가 아니다.

구체적인 scheduler library, database, push provider, 설치 식별자, endpoint와 payload는 Match feature 구현 계획에서 선택한다. secret과 push credential은 source code에 넣지 않는다.

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

## Plugins, Logging, and Notification

`plugins`에는 모든 feature에 공통인 Ktor 설정만 둔다.

- `Serialization.kt`: JSON content negotiation
- `ErrorHandling.kt`: exception/failure를 public error envelope로 매핑
- `Monitoring.kt`: request logging과 failure logging
- CORS, authentication 등은 실제 client requirement가 생길 때만 추가

첫 단계의 관측은 별도 log platform 없이 콘솔 로그를 사용한다. 현재 `logback.xml`의 console appender를 기반으로 다음을 구현 시 적용한다.

- Ktor request logging
- network/parsing failure의 `ApiErrorCode`, canonical upstream URL, cause를 서버 로그에 기록
- secret, token, client credential, raw HTML을 로그에 기록하지 않음

Discord notification은 선택적인 운영 확장이다. 실제 도입할 때 webhook secret을 environment variable로 주입하고, failure notifier는 best effort로 동작시킨다. Discord 전송 실패는 원래 API failure response를 바꾸거나 새로운 failure를 만들지 않는다. 반복 failure의 notification 제어가 필요해지는 시점에만 throttling을 추가한다.

## Configuration and Deployment

현재 구현은 Kotlin/JVM 기반 Ktor 3와 Netty를 사용하며, 지금은 local 실행을 개발 기준으로 삼는다. 이는 미래 production 배포 형태나 `localhost` base URL을 확정하는 결정이 아니다.

배포 packaging, provider, public host와 base URL, container/cloud resource, client 환경별 설정은 실제 배포 작업에서 선택한다. 현재 구현 사실을 유지하되 미래 환경이 JVM application이나 container여야 한다고 이 문서에서 미리 제한하지 않는다.

server config와 secret은 source code에 넣지 않는다. `ServerConfig` 또는 동등한 config boundary를 실제 도입할 때 사용하고, Discord webhook 같은 secret은 environment variable로만 전달한다.

## Testing Expectations

- parser test: HTML fixture를 기반으로 selector와 source-structure 가정을 검증
- mapper test: `SourceModel`에서 response DTO로 변환되는 규칙을 검증
- service test: request-time scraping, concurrent fetch coalescing을 구현한 경우 그 정책과 stale fallback 부재를 검증
- notification test: 10분 polling 대상 선정, Match 단위 중복 제거, 시작/종료 1회 발송, retry idempotency, 구독 해제와 terminal cleanup을 검증
- error handling test: 각 failure가 올바른 HTTP status, `ApiErrorCode`, 안전한 `message` envelope로 변환되는지 검증
- route test: 기능 기획에서 정한 request validation과 success/error response contract를 Ktor `testApplication {}`으로 검증. [Ktor server testing](https://ktor.io/docs/server-testing.html)

서버 변경의 기본 검증 명령은 `./gradlew :server:test`다.

## Document Placement and Change Rules

이 문서는 `docs/architecture/server-arch.md`에 둔다. 앱 계층 문서인 `docs/app-arch/`와 구분해, 프로젝트 전체의 server/API/upstream 경계를 관리한다.

- `Application.kt`를 얇게 유지한다.
- route handler에 scraping/parsing 세부사항을 넣지 않는다.
- public response model과 server-internal source model을 섞지 않는다.
- error contract, freshness policy, notification, DI, deployment requirement가 바뀌면 이 문서를 갱신한다.
- 기능 없는 빈 scaffolding과 구현되지 않은 infrastructure를 먼저 만들지 않는다.
