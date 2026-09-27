# 공개 조회 API 보호 계약 (#52)

Android/iOS 앱이 사용하는 공개 조회 API의 비싼 작업과 비용을 제한한다. 로그인·앱 진위 검증·일반 조회 DB·완료 응답 cache·stale fallback·Redis는 도입하지 않는다. 앱에 포함된 고정 key, XFF/Forwarded, 앱 header와 peer IP는 검증된 사용자 신원이 아니다. FCM production wiring은 별도 Stage 2다.

## Server limits

| Boundary | Default | Exceeded |
| --- | ---: | --- |
| API token bucket | process당 10/s, burst 20 | 429 `RATE_LIMITED` |
| active API requests | 8, queue 없음 | 503 `SERVER_BUSY` |
| new upstream fetch | 2/s, burst 4, active 4 | 503 `SERVER_BUSY` |
| in-flight canonical keys | 4 | 추가 key 즉시 거절 |
| upstream / whole request / Cloud Run | 10s / 15s / 30s | 안전한 오류와 cancellation |
| request target / headers / body | 4 KiB / 16 KiB / 1 KiB | 400·413·431 또는 platform rejection |
| upstream HTML / success JSON | 1 MiB / 2 MiB | 안전한 502 |

잘못된 config는 server start에서 거절한다. 전체 deadline은 느린 body 수신보다 먼저 시작하고 request size → API token/concurrency → upstream admission 순서로 자원을 얻는다. 실패·취소 시 이미 획득한 API concurrency semaphore와 upstream fetch admission permit은 반환하지만, 소비한 token-bucket token은 환불하지 않는다. unknown route/method도 저비용 request limit 대상이며 upstream을 호출하지 않는다.

과부하 response는 공통 `{code, message}`와 정수 `Retry-After`를 사용한다. exception, HTML, selector와 원본 URL은 반환하지 않는다. success JSON은 header 전송 전에 capped buffer로 직렬화한다. `/health`는 constant liveness response이며 API quota, upstream과 request log에서 분리한다.

동일 canonical URL의 진행 중 immutable HTML만 공유한다. caller별 parser는 독립적이며 Jsoup `Document`를 공유하지 않는다. 한 waiter의 cancellation은 나머지를 취소하지 않고 마지막 waiter가 떠나면 fetch를 취소한다. 완료 결과는 즉시 제거한다. 대부분의 API는 upstream fetch 1회이며 Team detail만 overview/news 2회를 병렬 수행하므로 admission 검증은 HTTP call 수가 아니라 fetch 비용을 기준으로 한다.

프로세스별 한도는 분산 전역 한도가 아니고 한 caller가 다른 caller를 거절시킬 수 있다. Cloud Run instance limit과 비용 중단도 절대적인 공격 비용 상한을 보장하지 않는다.

## App retry contract

조회 helper는 status를 먼저 검사하고 error body를 최대 8 KiB만 읽은 뒤 나머지를 취소한다.

- 429/`RATE_LIMITED`와 503/`SERVER_BUSY`만 `AppResult.Busy(retryDelay)`다.
- `Retry-After`의 정수 1~60초만 사용하고 없거나 잘못되면 2초를 사용한다.
- platform HTML error와 unknown code는 generic failure로 처리하며 coroutine cancellation은 전파한다.
- raw HTTP status와 server message는 Domain/UI로 전달하지 않는다.

화면은 실패한 작업 identity와 monotonic deadline을 보존하고 대기 중 action을 막은 뒤 사용자가 한 번 재시도하게 한다. 기존 retry/refresh/pagination도 같은 중복 방지를 사용한다. query·tab·screen이 바뀌면 이전 작업을 재시도하지 않으며 가능한 경우 정상 content와 위치를 유지한다. local favorite failure UX는 그대로 둔다.

## Operations and verification

Cloud Run 자원, private validation, promotion, rollback, 비용 중단과 복구는 [서버 배포 runbook](server-container-deployment.md)을 따른다. minimum 0은 접근 차단이나 비용 0을 뜻하지 않는다.

구현 검증은 성공·취소·오류 envelope, request/body/header limit, rate/concurrency/fetch admission, canonical in-flight sharing, bounded serialization, Busy mapping과 화면별 수동 재시도를 포함한다. opt-in synthetic benchmark는 [CI/CD 문서](../ci-cd.md#g0-synthetic-benchmark)에 기록한다.
