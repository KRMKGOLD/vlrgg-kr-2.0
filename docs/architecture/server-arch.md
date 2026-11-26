# Server Architecture

## Purpose and structure

`server`는 Ktor 3·Netty 기반의 VLR.GG 전용 backend다. VLR.GG HTML을 요청하고 Jsoup으로 해석해 앱이 사용하는 JSON API로 변환한다. 앱에는 CSS selector, Jsoup type, 원본 HTML과 scraping 보정을 노출하지 않는다.

서버는 단일 `:server` Gradle module 안에서 feature 단위로 구성한다. 각 feature는 route, service, scraper, parser, mapper와 app-facing response의 흐름을 소유한다. 공통 HTTP contract, scraping transport와 feature에 독립적인 parser 헬퍼만 feature 밖에 둔다. `Application.kt`는 plugin과 feature dependency를 조립하며 route handler 안에서 client나 scraper를 만들지 않는다. 별도 DI framework나 Gradle module은 실제 복잡성이 필요할 때만 추가한다.

## Scraping and freshness

- 앱 요청 시점에 VLR.GG를 조회하며, 성공 응답은 TTL 5분 이하의 [Response cache](#response-cache)를 거칠 수 있다. 일반 콘텐츠에 database, 주기 갱신(cron 선갱신)이나 stale-on-error fallback을 사용하지 않는다.
- 같은 canonical upstream resource의 동시 요청은 진행 중 fetch 하나를 공유할 수 있다. 이 공유는 Response cache와 별개이며 만료된 응답을 반환하는 데 쓰지 않는다.
- upstream network 또는 parsing 실패 시 이전 데이터를 대신 반환하지 않고 안전한 실패 응답을 반환한다.
- 공통 transport는 Ktor CIO를 사용하고 HTTPS의 `www.vlr.gg`와 `vlr.gg`만 직접 요청한다. redirect를 따르지 않으며 HTTP, 비표준 port, user-info와 다른 host는 요청 전에 거절한다.
- transport는 명시적 `User-Agent`, bounded timeout과 response size limit을 적용한다. 미소비 response channel은 취소하고 retry는 기본으로 설치하지 않는다.
- canonical upstream URL은 로그에서도 `https://www.vlr.gg/` origin으로 제한한다. request path, query, fragment, raw HTML과 selector를 기록하지 않는다.
- Scraper는 content 획득, Parser는 Jsoup DOM 해석, Mapper는 app-facing response 변환을 담당한다. 중요한 DOM 가정은 최소 HTML fixture로 검증한다.

## Response cache

2026-11-24 정책을 확정했고 아직 구현 전이다. 구현 PR은 이 절의 상태와 기능 문서의 "cache 없음" 문구를 함께 갱신한다.

사용자 요청 수에 비례해 VLR.GG 요청이 늘지 않도록 성공 응답을 짧게 공유 캐시한다. 사용자가 적은 단계라 최신성을 우선해 TTL 상한은 5분으로 둔다.

### Store

- Upstash Redis(서버리스, GCP `asia-northeast1` 도쿄)를 쓴다. 요청량만큼 과금되고 상시 고정비가 없어 Cloud Run minimum 0 운영과 맞는다. Memorystore와 ElastiCache는 상시 과금이라 쓰지 않는다.
- 서울 Cloud Run에서 TLS로 접속하고 연결을 재사용한다. 서울–도쿄 왕복 지연(약 40ms 추정)은 수용한다.
- 캐시는 운영 서비스에서만 쓴다. 로컬과 배포 검증 서비스(`vlrgg-query-check`) 같은 개발·검증 환경은 캐시를 쓰지 않고 Upstash database도 두지 않는다. 캐시 설정이 없으면 끔이 기본값이다.

### Rules

- key는 `{배포 SHA}:{고정 route 식별자}:{요청을 구분하는 식별자}`다. 식별자는 검증·정규화를 거친 resource ID(match, event, team, player, series, News의 articleId와 slug)와 page다. 응답 본문에 없는 값(예: Event News·Stats의 eventId)도 요청을 구분하는 데 필요하면 넣는다. 배포마다 key 공간이 바뀌어 이전 응답 형식이 나가지 않으므로 수동 purge를 두지 않는다.
- value는 최종 성공 응답 JSON 그대로다. 예외로 `SOURCE_PARSING_FAILURE`는 status와 code만 60초 저장한다. `UPSTREAM_NETWORK_FAILURE`와 `NOT_FOUND`를 포함한 그 밖의 실패는 캐시하지 않는다. 압축은 실제 크기를 잰 뒤 필요할 때만 한다.
- key와 value 어디에도 이용자 식별 정보(IP, User-Agent 등), 검색어, 원본 HTML, upstream URL을 넣지 않는다.
- TTL이 지난 뒤 조회가 실패하면 이전 응답 없이 실패 envelope를 반환한다.
- Upstash 조회 timeout은 200ms이며 오류와 timeout은 cache miss로 처리한다. 저장 실패는 응답에 영향을 주지 않고 WARN만 남기며 Error Reporting용 ERROR event가 아니다. 연속 실패 시 호출을 건너뛰는 장치는 실제 장애를 겪은 뒤 판단한다.
- 만료 시점의 요청 몰림은 인스턴스 안의 진행 중 fetch 공유로만 막는다. 인스턴스 사이 분산 lock과 만료 전 미리 갱신은 두지 않는다.

### TTL

| 응답 | TTL |
| --- | --- |
| `GET /api/v1/matches/upcoming` 1페이지 | 30초 |
| `GET /api/v1/matches/upcoming` 2페이지 이후 | 5분 |
| `GET /api/v1/matches/results` 1페이지 | 2분 |
| `GET /api/v1/matches/results` 2페이지 이후 | 5분 |
| `GET /api/v1/matches/{matchId}` | LIVE 30초, 그 외 5분 |
| `GET /api/v1/events/{eventId}/matches` | LIVE 경기가 있으면 30초, 그 외 5분 |
| `GET /api/v1/teams/{teamId}` | LIVE 경기가 있으면 30초, 그 외 5분 |
| Events 목록·상세·News·Stats, News 목록·상세, Player, Series | 5분 |
| `GET /api/v1/search` | 캐시하지 않음 |

LIVE 여부는 응답에 담긴 경기 상태로 판단한다. Matches와 Event Matches는 `status`가 `live`인 경기다. Team 응답에는 상태 값이 없으므로 `upcomingMatches`와 `recentMatches`의 `statusText`가 `LIVE`(앞뒤 공백 제거, 대소문자 무시)인 경기를 LIVE로 본다. 값이 없거나 다른 문구인 경기는 LIVE로 보지 않는다. 2026-11-24에 진행 중 경기가 있는 Team 페이지에서 이 원문을 확인했으며, 구현 PR은 LIVE fixture로 검증한다.

Search는 캐시하지 않는다. 검색어는 이용자가 입력한 값이라 Upstash에 저장하지 않으며, [개인정보처리방침](../privacy-policy.md)의 "검색어를 따로 저장하지 않는다"는 설명을 그대로 유지한다.

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

[Response cache](#response-cache) 환경 변수는 다음과 같다. 로컬과 검증 서비스에는 넣지 않으며, 값이 없으면 캐시는 꺼진다.

| 이름 | 값(placeholder) | 설명 |
| --- | --- | --- |
| `VLRGG_RESPONSE_CACHE_ENABLED` | `true` | 정확히 `true`일 때만 켠다. 없거나 다른 값이면 끈다 |
| `VLRGG_UPSTASH_REDIS_REST_URL` | `https://<database>.upstash.io` | HTTPS, 기본 port, user-info·path·query 없음. secret으로 주입한다 |
| `VLRGG_UPSTASH_REDIS_REST_TOKEN` | `<upstash-rest-token>` | secret으로 주입한다 |
| `VLRGG_DEPLOY_SHA` | `<git-commit-sha>` | key 공간을 배포마다 나눈다. `[A-Za-z0-9._-]{1,64}` |

캐시가 켜졌는데 나머지 셋 중 하나라도 없거나 형식이 틀리면 서버가 시작하지 않는다. 시작 실패 메시지에는 변수 이름만 남고 URL과 token 값은 남지 않는다.

일반 조회 서버는 서울 Cloud Run에 배포돼 있다. root `Dockerfile`은 repository root의 Gradle 설정과 `server`를 사용해 `:server:installDist`를 만들며 runtime은 `0.0.0.0`, provider `PORT`, `/health`를 지원한다. exact SHA 검증, private validation, promotion, rollback과 비용 중단은 [배포 runbook](server-container-deployment.md)이 소유한다.

## Verification

- 기본 서버 변경: `./gradlew :server:test`
- packaging 또는 module 경계 변경: `./gradlew :server:build :server:installDist`
- parser 변경: 최소 HTML fixture 기반 parser test
- route/error 변경: Ktor `testApplication` 기반 status·envelope test

변경 시 Response cache TTL 상한, stale fallback 부재, public error/redaction, cancellation 전파와 해당 feature의 route/service/parser 경계를 함께 검증한다.
