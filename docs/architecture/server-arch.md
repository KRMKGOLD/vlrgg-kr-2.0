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

2026-11-26 정책을 다시 확정했고 아직 구현 전이다. Upstash 공유 캐시로 구현했던 #216~#219는 #220으로 되돌렸다. 구현 PR은 이 절의 상태와 기능 문서의 "cache 없음" 문구를 함께 갱신한다.

사용자 요청 수에 비례해 VLR.GG 요청이 늘지 않도록 성공 응답을 짧게 캐시한다. 사용자가 적은 단계라 최신성을 우선해 TTL 상한은 5분으로 둔다.

### Store

- 서버 프로세스 안의 메모리 캐시이며 Caffeine을 쓴다. 외부 저장소, secret과 네트워크 호출이 없다.
- 공유 캐시(Upstash Redis)를 쓰지 않는 이유는 두 가지다. 운영 Cloud Run의 최대 instance가 1개라 instance 사이에 나눌 캐시가 없다. TTL이 5분 이하라 instance가 내려갈 때 잃는 캐시도 사실상 없다. instance를 2개 이상으로 늘리게 되면 instance마다 캐시가 따로이므로 그때 공유 캐시를 다시 검토한다.
- 총 용량은 저장한 값의 바이트 합으로 제한한다(기본 32 MiB). 넘으면 Caffeine이 항목을 제거한다. 디스크에는 쓰지 않는다.
- 캐시는 로컬, 배포 검증 서비스와 운영에서 같은 코드 경로로 기본 동작한다. 환경 변수로 끌 수 있다.
- Cloud Run은 요청이 없을 때 CPU를 주지 않는다. 만료와 용량 정리를 background thread에 맡기지 않고 요청 처리 중에 수행한다.

### Rules

- key는 `{고정 route 식별자}:{요청을 구분하는 식별자}`다. 식별자는 검증·정규화를 거친 resource ID(match, event, team, player, series, News의 articleId와 slug)와 page다. 응답 본문에 없는 값(예: Event News·Stats의 eventId)도 요청을 구분하는 데 필요하면 넣는다. 프로세스가 바뀌면 캐시가 비므로 배포 사이에 이전 응답 형식이 남지 않는다.
- value는 최종 성공 응답 JSON 그대로다. hit은 저장된 JSON을 그대로 응답한다. 예외로 `SOURCE_PARSING_FAILURE`는 status와 code만 60초 저장한다. `UPSTREAM_NETWORK_FAILURE`와 `NOT_FOUND`를 포함한 그 밖의 실패는 캐시하지 않는다.
- key와 value 어디에도 이용자 식별 정보(IP, User-Agent 등), 검색어, 원본 HTML, upstream URL을 넣지 않는다.
- TTL이 지난 뒤 조회가 실패하면 이전 응답 없이 실패 envelope를 반환한다. 만료 전 미리 갱신은 두지 않는다.
- 같은 key의 동시 miss는 불러오기 하나를 공유한다. 첫 요청이 upstream 조회와 파싱을 하는 동안 나머지 요청은 그 결과를 기다려 같은 응답을 받는다. 불러오기가 실패하면 기다리던 요청도 같은 실패를 받는다. 한 요청이 취소돼도 다른 요청이 기다리는 불러오기는 계속하고, 기다리는 요청이 하나도 없으면 취소한다.
- hit과 진행 중 불러오기를 기다리는 요청은 upstream과 parser를 거치지 않는다. 그래서 upstream 작업 한도를 쓰지 않고 전체 요청 한도만 쓴다. 한도는 [공개 API 보호 계약](server-public-api-protection.md)을 따른다.

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

Search는 캐시하지 않는다. 검색어는 사람마다 달라 적중이 드물다. 이용자가 입력한 검색어를 서버가 따로 보관하지 않는다는 [개인정보처리방침](../privacy-policy.md) 설명도 그대로 유지한다.

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

응답 캐시는 아래 환경 변수로 조정한다. 둘 다 없으면 켜진 상태에 용량 32 MiB다. secret은 없다.

| 환경 변수 | 기본값 | 허용 값 |
| --- | --- | --- |
| `VLRGG_RESPONSE_CACHE_ENABLED` | `true` | `true` 또는 `false`만. 대소문자·공백이 다른 값도 거절한다 |
| `VLRGG_RESPONSE_CACHE_MAX_BYTES` | `33554432` (32 MiB) | 양의 정수(byte). key와 value 바이트 합의 상한이다 |

허용 값이 아니면 server start를 거절한다. `false`이면 캐시를 만들지 않고 모든 요청이 그 자리에서 upstream을 조회하며, upstream 작업 한도는 그대로 적용된다.

일반 조회 서버는 서울 Cloud Run에 배포돼 있다. root `Dockerfile`은 repository root의 Gradle 설정과 `server`를 사용해 `:server:installDist`를 만들며 runtime은 `0.0.0.0`, provider `PORT`, `/health`를 지원한다. exact SHA 검증, private validation, promotion, rollback과 비용 중단은 [배포 runbook](server-container-deployment.md)이 소유한다.

## Verification

- 기본 서버 변경: `./gradlew :server:test`
- packaging 또는 module 경계 변경: `./gradlew :server:build :server:installDist`
- parser 변경: 최소 HTML fixture 기반 parser test
- route/error 변경: Ktor `testApplication` 기반 status·envelope test

변경 시 Response cache TTL 상한, stale fallback 부재, public error/redaction, cancellation 전파와 해당 feature의 route/service/parser 경계를 함께 검증한다.
