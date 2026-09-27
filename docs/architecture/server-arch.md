# Server Architecture

## Purpose and structure

`server`는 Ktor 3·Netty 기반의 VLR.GG 전용 backend다. VLR.GG HTML을 요청하고 Jsoup으로 해석해 앱이 사용하는 JSON API로 변환한다. 앱에는 CSS selector, Jsoup type, 원본 HTML과 scraping 보정을 노출하지 않는다.

서버는 단일 `:server` Gradle module 안에서 feature 단위로 구성한다. 각 feature는 route, service, scraper, parser, mapper와 app-facing response의 흐름을 소유한다. 공통 HTTP contract와 scraping transport만 feature 밖에 둔다. `Application.kt`는 plugin과 feature dependency를 조립하며 route handler 안에서 client나 scraper를 만들지 않는다. 별도 DI framework나 Gradle module은 실제 복잡성이 필요할 때만 추가한다.

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
- parsing failure의 제한된 canonical origin과 cause는 서버 내부에 보존한다. cancellation과 JVM `Error`는 일반 실패로 바꾸지 않는다.
- 앱 Data Layer는 인식된 429/`RATE_LIMITED`와 503/`SERVER_BUSY`만 `AppResult.Busy(retryDelay)`로 변환한다. 그 밖의 실패는 generic failure이며 UI가 HTTP status나 server code를 직접 해석하지 않는다. 자세한 보호·재시도 계약은 [공개 API 보호 계약](server-public-api-protection.md)을 따른다.

OpenAPI와 Swagger route는 `VLRGG_ENABLE_API_DOCUMENTATION=true`인 local 개발에서만 켠다. 문서에는 public path, request validation, response DTO와 stable error만 포함하며 production 공개 전에는 접근 정책과 asset pinning을 별도로 정한다.

## Logging and observability

공개 조회는 요청마다 raw 정보를 기록하지 않고 고정 route/status/error/latency bucket과 bounded failure sample만 남긴다.

- `INTERNAL_ERROR`와 `SOURCE_PARSING_FAILURE`만 Error Reporting용 ERROR event다.
- 로그에 request path/query/IP/header, token, exception message, raw HTML, selector와 임의 stack 문자열을 넣지 않는다.
- trace는 신뢰된 project 환경과 형식이 모두 유효한 단일 `X-Cloud-Trace-Context`에서만 연결한다.
- failure event와 stack은 고정 크기·깊이·빈도 제한을 적용한다. 출력 sample 수를 전체 실패 수로 해석하지 않는다.
- 운영 정책, 장애 주입, 권한과 복원 절차는 [서버 컨테이너 배포 경로](server-container-deployment.md)의 #122 runbook이 소유한다. 제품 경기 알림과 Crashlytics는 이 운영 관측과 별개다.

## Match Notification: Stage 1.1 offline gate

제품 경기 알림은 1차 MVP에서 제외하고 MVP 이후 Stage 2에서 진행한다. 현재 Stage 1.1은 credential-free offline 서버 기반만 구현했다.

- 앱 설치 단위의 익명 Target, one-time Target Secret, FCM registration token 전달 주소를 구분한다. token, FID와 물리 기기 ID는 권한 증명이 아니다.
- 한 Target과 전체 active unique Match는 각각 최대 100개다.
- `UPCOMING`/`POSTPONED -> LIVE`만 START intent를 만든다. END 알림은 현재 Stage 2 범위에 포함하지 않는다.
- subscription별 START intent는 하나이며 provider call 결과가 불명확하면 `UNKNOWN`으로 격리하고 자동 재발송하지 않는다.
- Target, subscription, capacity, lease, fan-out cursor와 delivery intent만 Firestore에 저장한다. 일반 scraping response와 이전 Match 결과는 저장하지 않는다.
- Firestore SDK transaction/query/document mapping은 Emulator에서 검증했다. 일반 local/main/packaged runtime에는 fake provider와 public scheduler route가 없고 알림 API는 disabled/fail-closed다.

Stage 2는 Android/iOS Target client, 실제 App Check/FCM, production Firestore/IAM/index, OIDC Scheduler route와 notification deployment를 소유한다. precise Target API, transaction, retry와 검증 gate는 [Stage 1.1 contract](server-fcm-stage1.md), 선택 이유는 [ADR-0002](adr/0002-match-notification-stage1-1-offline-firestore-boundary.md), 제품 범위는 [Feature Guide](../feature/README.md), 실행 범위는 [Epic #76](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/76)을 따른다.

## Configuration and deployment

config와 secret은 source code에 넣지 않는다. Firebase Admin credential과 FCM send 권한은 trusted server에만 두고 실제 project ID, service-account JSON, registration token을 문서·response·log에 기록하지 않는다.

일반 조회 서버는 서울 Cloud Run에 배포돼 있다. root `Dockerfile`은 repository root의 Gradle 설정, `core`, `server`를 사용해 `:server:installDist`를 만들며 runtime은 `0.0.0.0`, provider `PORT`, `/health`를 지원한다. exact SHA 검증, private validation, promotion, rollback과 비용 중단은 [배포 runbook](server-container-deployment.md)이 소유한다. 알림 production gate는 일반 조회 배포의 선행 조건이 아니다.

## Verification

- 기본 서버 변경: `./gradlew :server:test`
- packaging 또는 module 경계 변경: `./gradlew :server:build :server:installDist`
- Stage 1.1 persistence 변경: 명시적 Emulator 환경에서 `./gradlew :server:firestoreEmulatorTest`
- parser 변경: 최소 HTML fixture 기반 parser test
- route/error 변경: Ktor `testApplication` 기반 status·envelope test

변경 시 request-time freshness, stale fallback 부재, public error/redaction, cancellation 전파와 해당 feature의 route/service/parser 경계를 함께 검증한다.
