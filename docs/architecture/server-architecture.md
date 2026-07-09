# Server Architecture

## 목적

`server` 모듈은 Ktor 3 기반 서버다. 이 서버의 역할은 VLR.GG 데이터를 수집하고, HTML 구조에 강하게 묶인 불안정한 원본 데이터를 앱에서 쓰기 좋은 안정적인 API 응답으로 바꾸는 것이다.

Compose Multiplatform 앱은 VLR.GG HTML 구조를 직접 알지 않아야 한다. HTML 스크래핑, 파싱, 원본 데이터 보정은 서버가 맡고, 앱은 서버가 제공하는 app-facing API 응답만 사용한다.

서버의 HTML parsing은 Jsoup을 사용한다. Jsoup 의존성이 아직 Gradle에 없다면 첫 scraper/parser 구현 작업에서 `gradle/libs.versions.toml`과 `server/build.gradle.kts`에 반영한다.

서버는 앞으로 경기, 대회, 팀, 선수, 랭킹, 뉴스 같은 Valorant e-sports 도메인을 중심으로 커질 가능성이 있다. 그래서 Ktor 라우팅 코드, 스크래핑/파싱 코드, 서비스 로직, 응답 DTO는 서로 독립적으로 테스트하고 교체할 수 있을 만큼 분리한다.

## Ktor 공식 기준

Ktor는 특정 프로젝트 구조를 강제하지 않는다. 대신 공식 문서에서 프로젝트 규모와 도메인 복잡도에 따라 여러 구조를 선택할 수 있다고 설명한다.

대표적인 구조는 다음과 같다.

- layered structure: config, plugins, routes, service, repository, domain, dto처럼 책임별로 나누는 방식
- modular architecture: Ktor `Application` module 단위로 route, plugin, service 설정을 묶는 방식
- feature-based modules: match, team, player처럼 기능 단위로 route, service, dto, domain logic을 묶는 방식
- domain-driven approach: 복잡한 도메인 규칙을 Ktor transport와 분리해 domain 중심으로 구성하는 방식

이 프로젝트에서는 현재의 단일 Gradle `:server` 모듈 안에서 **feature-based modular structure**를 사용한다.

이 선택의 의미는 다음과 같다.

- Ktor application module은 route, plugin, service, infrastructure 연결 지점을 구성한다.
- feature package는 관련 route, service, scraper/parser, DTO, mapper를 함께 가진다.
- 재사용되는 비즈니스 개념이 생기면 Ktor에 의존하지 않는 모델로 분리한다.
- dependency는 module boundary에서 넘기거나 해결해서 테스트하기 쉽게 만든다.

이 방식은 현재 `:server` 모듈을 단순하게 유지하면서도, 나중에 scraping/API 복잡도가 커졌을 때 feature를 별도 모듈로 분리할 여지를 남긴다.

참고 문서:

- Ktor Application structure: https://ktor.io/docs/server-application-structure.html
- Ktor Modules: https://ktor.io/docs/server-modules.html
- Ktor Dependency injection: https://ktor.io/docs/server-dependency-injection.html
- Ktor Testing: https://ktor.io/docs/server-testing.html

## 현재 모듈 상태

현재 서버 진입점은 다음 파일이다.

```text
server/src/main/kotlin/kr/co/cotton/vlrgg_mobile/Application.kt
```

현재 서버 테스트 진입점은 다음 파일이다.

```text
server/src/test/kotlin/kr/co/cotton/vlrgg_mobile/ApplicationTest.kt
```

현재 구현은 최소 Ktor 템플릿에 가깝다. 새 서버 기능을 추가할 때는 아래 목표 구조로 점진적으로 발전시킨다. 단, 실제 기능이 없는 빈 패키지를 먼저 대량으로 만들지는 않는다.

## 목표 패키지 구조

`server/src/main/kotlin/kr/co/cotton/vlrgg_mobile` 아래에서 다음 구조를 목표로 한다.

```text
server/
  Application.kt
  config/
    ServerConfig.kt
  plugins/
    Serialization.kt
    Monitoring.kt
    ErrorHandling.kt
  routing/
    Routing.kt
  feature/
    match/
      MatchModule.kt
      MatchRoutes.kt
      MatchService.kt
      MatchScraper.kt
      MatchParser.kt
      MatchMapper.kt
      MatchResponse.kt
      MatchSourceModel.kt
    event/
    team/
    player/
  common/
    http/
    scraping/
    time/
```

이 구조는 방향성이다. 작은 기능이라면 파일이나 패키지 이름을 더 짧게 가져가도 된다. 중요한 기준은 “폴더를 많이 만드는 것”이 아니라 “책임을 섞지 않는 것”이다.

## Application Entry Point

`Application.kt`는 얇게 유지한다.

담당 책임은 다음 정도로 제한한다.

- 서버 설정 생성 또는 로드
- `embeddedServer`를 사용할 때 Netty engine 시작
- 메인 Ktor `Application.module` 호출
- plugin, dependency, route 설정을 별도 함수로 위임

`Application.kt`에 feature route, HTML 파싱, response 가공 로직을 직접 넣지 않는다.

권장 방향:

```kotlin
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureErrorHandling()
    configureRouting()
}
```

## Plugins

Ktor plugin 설정은 `plugins` 패키지 아래에 둔다.

예상되는 plugin 그룹은 다음과 같다.

- JSON 응답을 위한 serialization/content negotiation
- 공통 예외 처리를 위한 status pages 또는 error handling
- call logging 또는 monitoring
- 실제 앱/웹 클라이언트 요구가 생겼을 때만 CORS

모든 route에 적용되는 동작은 전역 plugin으로 설치한다. 특정 feature에만 다른 설정이 필요할 때만 route scope에 plugin을 설치한다.

## Routing

Route는 feature 기준으로 묶는다.

- `routing/Routing.kt`는 feature route 그룹을 연결한다.
- feature route 파일은 HTTP 요청을 service 호출로 변환한다.
- route handler는 입력값 검증, service 호출, response DTO 반환까지만 담당한다.
- route handler 안에 HTML 파싱 로직을 넣지 않는다.

권장 방향:

```kotlin
fun Application.configureRouting(matchService: MatchService) {
    routing {
        route("/matches") {
            matchRoutes(matchService)
        }
    }
}
```

## Feature Boundary

각 feature는 route에서 app-facing response까지 이어지는 한 흐름을 가진다.

대표적인 파일 책임은 다음과 같다.

| File | Responsibility |
| --- | --- |
| `*Module.kt` | feature 수준 dependency 조립 또는 Ktor module 함수 |
| `*Routes.kt` | HTTP route 정의와 request/response 처리 |
| `*Service.kt` | use-case 수준의 orchestration과 정책 처리 |
| `*Scraper.kt` | 원본 HTML 또는 원본 문서 가져오기 |
| `*Parser.kt` | 원본 HTML을 source model로 변환 |
| `*Mapper.kt` | source model을 app-facing response DTO로 변환 |
| `*Response.kt` | 앱에 공개되는 API 응답 모델 |
| `*SourceModel.kt` | 스크래핑 원본을 표현하는 내부 모델 |

feature가 커지면 `route`, `service`, `scraper`, `dto`, `mapper` 같은 내부 패키지로 나눌 수 있다.

## Scraping and Parsing

VLR.GG HTML은 외부에 있는 불안정한 source contract다. 페이지 클래스명, DOM 구조, 텍스트 포맷이 언제든 바뀔 수 있다고 가정한다.

따라서 다음 규칙을 따른다.

- CSS selector와 HTML traversal 로직은 parser class 안에 모은다.
- Jsoup `Document`, `Element`는 parser 내부 경계에만 둔다.
- Service, Route, Response DTO는 Jsoup 타입을 알면 안 된다.
- raw HTML이나 Jsoup `Document`, `Element`를 route 또는 response DTO 밖으로 흘리지 않는다.
- 중요한 페이지는 최소 HTML fixture를 저장해 parser test를 작성하는 것을 우선한다.
- 누락된 element, 변경된 class name, 일부 데이터 부재는 예외적인 상황이 아니라 예상 가능한 실패 모드로 다룬다.
- 원본 HTML이나 내부 parser error를 client에 그대로 노출하지 않는다.

역할을 구분하면 다음과 같다.

- Scraper: 원본 content를 가져온다.
- Parser: 원본 content를 해석해 source model로 바꾼다.
- Mapper: source model을 앱이 사용할 response로 바꾼다.

## DTO and Model Rules

모델의 역할을 명확히 분리한다.

- `SourceModel`: VLR.GG에서 긁어온 데이터를 서버 내부에서 표현하는 모델
- `Response`: 앱에 반환하는 서버 API 계약
- `Domain` 또는 service model: 여러 source model을 가로지르는 정책이 있을 때만 사용하는 내부 비즈니스 개념

`SourceModel`을 route에서 그대로 반환하지 않는다. Ktor `ApplicationCall`, Jsoup `Element`, Jsoup `Document`를 service/domain model 안에 넣지 않는다.

## Dependency Management

작거나 중간 규모의 feature는 module 함수나 route 함수의 parameter로 dependency를 넘기는 방식을 우선한다. 이 방식은 Ktor module 구조와 잘 맞고 테스트가 단순하다.

dependency graph가 복잡해지면 외부 DI framework를 추가하기 전에 Ktor built-in DI plugin을 먼저 검토한다.

dependency 생성은 route handler 내부에 숨기지 않는다. module boundary나 feature module 근처에서 보이게 구성한다.

## Error Handling

다음 상황은 공통 error handling 전략으로 다룬다.

- upstream 요청 실패
- VLR.GG HTML 구조 변경
- parser가 필수 field를 찾지 못한 경우
- client parameter가 잘못된 경우
- upstream 결과는 정상이나 데이터가 비어 있는 경우

예외를 그대로 노출하기보다 안정적인 app-facing error response를 반환한다. 다만 parser drift를 디버깅할 수 있도록 서버 로그에는 필요한 context를 남긴다.

## Testing Expectations

Route와 module 테스트는 Ktor의 `testApplication {}`을 사용한다.

권장 테스트 레벨은 다음과 같다.

- parser test: HTML fixture를 기반으로 source structure 가정을 검증
- mapper test: source model에서 response DTO로 변환되는 규칙 검증
- service test: orchestration, fallback, error policy 검증
- route test: status code와 response body contract 검증

서버 변경의 기본 검증 명령은 `./gradlew :server:test`다.

## Placement Decision

이 파일은 `docs/architecture` 아래에 둔다. 이유는 이 문서가 단순히 `server` 디렉터리 안에서 코드를 어떻게 쓸지 알려주는 규칙을 넘어, 프로젝트 전체에서 서버가 어떤 책임을 가지는지와 앱/서버 경계를 정의하기 때문이다.

서버 모듈에서 바로 적용할 짧은 실행 규칙은 `server/AGENTS.md`에 둔다. 긴 설계 배경, 목표 구조, Ktor/Jsoup 경계 설명은 이 문서에서 관리한다.

## Change Rules

- `Application.kt`는 얇게 유지한다.
- route handler에 scraping/parsing 구현 세부사항을 넣지 않는다.
- 실제 기능 작업 없이 빈 package를 대량으로 만들지 않는다.
- dependency를 추가할 때는 `gradle/libs.versions.toml`을 갱신하고 해당 dependency가 어느 모듈 책임인지 설명한다.
- 서버 모듈 경계, DI 접근, plugin 전략, response contract 정책이 바뀌면 이 문서를 갱신한다.
