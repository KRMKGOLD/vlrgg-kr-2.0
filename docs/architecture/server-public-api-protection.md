# 공개 조회 API 보호 계약 (#52)

Android/iOS 앱이 사용하는 공개 조회 API의 비싼 작업과 비용을 제한한다. 로그인·앱 진위 검증·일반 조회 DB·stale fallback은 도입하지 않는다. 성공 응답의 짧은 TTL 메모리 캐시는 [서버 구조의 응답 캐시](server-arch.md#response-cache) 정책을 따른다. 앱에 포함된 고정 key, XFF/Forwarded, 앱 header와 peer IP는 검증된 사용자 신원이 아니다.

## Server limits

| Boundary | Default | Exceeded |
| --- | ---: | --- |
| 전체 요청 token bucket | process당 50/s, burst 100 | 429 `RATE_LIMITED` |
| upstream 작업 token bucket | process당 10/s, burst 20 | 429 `RATE_LIMITED` |
| active upstream 작업 | 8, queue 없음 | 503 `SERVER_BUSY` |
| new upstream fetch | 10/s, burst 20, active 8 | 503 `SERVER_BUSY` |
| in-flight canonical keys | 8 | 추가 key 즉시 거절 |
| upstream / whole request / Cloud Run | 10s / 15s / 30s | 안전한 오류와 cancellation |
| request target / headers / body | 4 KiB / 16 KiB / 1 KiB | 400·413·431 또는 platform rejection |
| upstream HTML / success JSON | 4 MiB / 2 MiB | 안전한 502 |

한도는 환경 변수로 조정한다. 값이 없으면 위 기본값을 쓴다.

| Boundary | 환경 변수 |
| --- | --- |
| 전체 요청 token bucket | `VLRGG_REQUEST_RATE_PER_SECOND`, `VLRGG_REQUEST_RATE_BURST` |
| upstream 작업 token bucket | `VLRGG_API_RATE_PER_SECOND`, `VLRGG_API_RATE_BURST` |
| active upstream 작업 | `VLRGG_API_MAX_CONCURRENCY` |
| new upstream fetch | `VLRGG_UPSTREAM_RATE_PER_SECOND`, `VLRGG_UPSTREAM_RATE_BURST`, `VLRGG_UPSTREAM_MAX_CONCURRENCY` |
| in-flight canonical keys | `VLRGG_UPSTREAM_MAX_IN_FLIGHT_KEYS` |
| whole request / request target / headers / body | `VLRGG_REQUEST_TIMEOUT_MILLIS`, `VLRGG_MAX_REQUEST_TARGET_BYTES`, `VLRGG_MAX_REQUEST_HEADER_BYTES`, `VLRGG_MAX_REQUEST_BODY_BYTES` |

`VLRGG_API_*`는 이름이 바뀌기 전부터 쓰던 이름이라 그대로 두며 upstream 작업 한도를 뜻한다. `public_api_summary`의 `active_api`도 진행 중인 upstream 작업 수다.

new upstream fetch를 2/s·burst 4·active 4에서 10/s·burst 20·active 8로, in-flight canonical keys를 4에서 8로 올렸다(사용자 결정, 2026-11-26). 서로 다른 페이지가 한꺼번에 열리는 순간 부하를 받기 위해서다. fetch 하나가 0.5~1초 걸리므로 active와 key 수가 4이면 초당 10건에 닿지 못하고 서로 다른 다섯 번째 페이지가 바로 거절된다. 주의점은 두 가지다.

- 메모리: 아래 "Heap 예산" 절의 계산과 측정을 따른다. 현실적인 큰 페이지(1.2 MB)는 heap 160 MiB에서도 OOM이 나지 않아 여유가 크다. 4 MiB급 페이지 8개가 동시에 열리는 최악의 경우는 `-Xmx384m`에서 통과하지만 OOM 경계가 288~320 MiB 사이여서 여유가 64~96 MiB다.
- VLR.GG 부담: 지속 refill 한도는 초당 10건이고 순간 burst는 20건이며, 평소 요청 수는 응답 캐시가 줄인다.

### Heap 예산

운영 heap은 `-Xmx384m`이고 Cloud Run 요청 동시성은 32다. 동시에 살아 있을 수 있는 heap을 네 부분으로 나눈다. 같은 배열을 여러 곳이 참조하면 한 번만 센다.

| 부분 | 상한 계산 | 비고 |
| --- | --- | --- |
| 캐시 저장량 | key+value ≤ 32 MiB, 항목당 Caffeine bookkeeping 수백 byte | 저장할 때마다 정리를 마쳐 쓰기가 없을 때 상한 안이다 |
| 진행 중 불러오기 (upstream 작업 최대 8) | 수신: 8 × (수신 버퍼 + String, 페이지 최대 4 MiB). DOM: 8 × 페이지 크기의 수 배 | 4.4 MiB 페이지 8건 파싱 + 4건 수신이 약 295 MiB였다(기존 측정). 같은 응답 key는 불러오기 하나를 공유한다 |
| 전송 중 body | (32 − 8) × 성공 JSON ≤ 2 MiB = 최대 48 MiB | 불러오기 permit은 완료 때 반환되지만 응답 배열은 전송이 끝날 때까지 산다. hit의 배열은 캐시와 같은 배열이라 중복이 아니다 |
| 직렬화 복사본 | 8 × (2 MiB 버퍼 + 최대 2 MiB 정확한 크기 복사) = 최대 32 MiB | 불러오기마다 잠깐 존재한다 |

DOM과 HTML 수신이 대부분을 차지한다. 캐시·전송·직렬화의 상한 합(32 + 48 + 32 MiB)은 기존 측정 위에 더해지는 최대값이며, 모든 부분이 동시에 최대가 된다는 뜻은 아니다.

2026-11-26 로컬 측정(opt-in `ResponseCacheMemoryBenchmarkTest`, Corretto 21, `-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError`)은 다음 순서로 했다.

- 실제 `module`을 loopback Netty로 띄우고 fixture transport를 쓴다. fixture는 fetch마다 페이지 복사본과 String을 새로 만들고, 외부 호출은 없다.
- 결과 목록의 서로 다른 페이지로 캐시를 상한의 1.2배까지 채운다.
- 서로 다른 페이지 24개를 읽지 않고 붙잡아 두는 느린 소비자를 띄운다(수신 버퍼 1 KiB).
- 그동안 서로 다른 페이지 8개의 동시 miss를 3회 반복한다.
- 동시성 한도는 운영 기본값(작업 8, fetch 8, key 8)이고, token 속도만 대기 없이 돌도록 올렸다.

| 시나리오 | 결과 (3회) | OOM 경계 (`-Xmx`를 낮춰 1회씩) |
| --- | --- | --- |
| (a) 1.2 MB 페이지(JSON 약 240 KB), 캐시 32 MiB | OOM 없음. raw heap peak 91.7~98.1%, young/full GC 직후 survivor+old 최대 74~99.9% | 160 MiB에서도 OOM 없음 |
| (a) 같은 조건, 캐시 끔 | OOM 없음. raw peak 57.5~72.4% | 160 MiB에서도 OOM 없음 |
| (b) 4 MiB급 페이지(JSON 약 820 KB), 캐시 32 MiB | OOM 없음. raw peak 98.6~99.1%, window 동안 GC 99~102회 | 288 MiB OOM, 320 MiB 통과 |
| (b) 같은 조건, 캐시 끔 | OOM 없음. raw peak 98.6~98.9% | 256 MiB OOM, 288 MiB 통과 |

읽는 법은 다음과 같다.

- raw peak와 GC 직후 값은 아직 회수하지 않은 old gen 쓰레기를 포함한다. G1은 여유 heap을 채운 뒤 회수하므로 85~99%가 곧 위험을 뜻하지 않는다. 실제 여유는 OOM 경계로 판단한다.
- (a)는 384 MiB의 절반 이하에서도 통과한다.
- (b)는 384 MiB에서 통과하지만 경계가 288~320 MiB다. 캐시 32 MiB가 경계를 약 32 MiB 올린다.
- 같은 측정에서 캐시 16 MiB나 fetch 동시 수 4로 줄여도 (b)의 raw peak는 98% 이상으로 거의 변하지 않았다. fetch 상한은 동시 파싱 상한이 아니기 때문이다.
- 운영 transport는 수신 중 버퍼가 커지면서 복사가 더 생겨 fixture보다 수신 비용이 크다.
- 실제 VLR.GG 페이지와 운영 heap에서는 측정하지 않았다.

이 측정으로 캐시 상한 32 MiB와 VLR.GG 동시 요청 8을 유지한다(2026-11-26 결정). 판단 기준은 GC 전 점유율이 아니라 OOM 경계다. 최악 (b)에서도 384 MiB heap에 최소 64 MiB(약 17%) 여유가 있다. (b)는 서로 다른 4 MiB급 페이지 8개가 동시에 열리는 경우이고 현재 가장 큰 실제 페이지는 1.16 MB다. 캐시 16 MiB와 fetch 동시 수 4에서는 raw peak만 쟀고 OOM 경계는 측정하지 않았다. 이 값들을 바꿀 때는 경계를 다시 측정한다.

다음 경우에는 [실행 방법](../ci-cd.md#response-cache-memory-benchmark)대로 다시 측정한다.

- 크기가 upstream HTML 제한(4 MiB)에 가까운 실제 페이지가 생길 때
- heap(`-Xmx`), 캐시 상한(`VLRGG_RESPONSE_CACHE_MAX_BYTES`), upstream 작업·fetch 동시 수, Cloud Run 요청 동시성 중 하나를 바꿀 때
- 운영에서 OOM이나 GC 지연이 보일 때

upstream HTML 제한을 4 MiB로 둔 것은 경기 수가 많은 대회의 경기 목록 페이지가 경기당 약 2.2 KB라서 4 MiB면 약 1,800경기까지 받을 수 있기 때문이다. fetch 상한(active 8)은 동시 파싱 상한이 아니다. fetch가 끝난 뒤 active upstream 작업(최대 8)마다 DOM을 따로 만든다. 같은 응답 key의 동시 miss는 불러오기 하나를 공유하므로 같은 페이지를 요청 수만큼 파싱하지 않는다. 로컬 재현에서 4.4 MiB 페이지 8건 동시 파싱과 4건 수신은 heap 약 295 MiB를 썼다(`-Xmx384m` 통과, `-Xmx256m` OOM). 2026-11 현재 가장 큰 실제 페이지는 1.16 MB이며, 제한에 가까운 페이지가 생기거나 OOM·GC 지연이 보이면 운영 heap에서 다시 측정한다.

upstream 작업은 VLR.GG 조회와 파싱이 필요한 처리다. 응답 캐시가 miss여서 불러오기를 실행하는 요청과 캐시하지 않는 Search가 해당한다. cache hit과 진행 중 불러오기를 기다리는 요청은 upstream 작업 한도를 쓰지 않고 전체 요청 한도만 쓴다. 전체 요청 한도를 느슨하게 둔 것은 hit의 비용이 작기 때문이다. DAU 1,000 가정의 순간 몰림(초당 약 30건)을 받도록 잡았고, 지속적인 남용의 전송 비용을 제한하려고 무제한으로 두지는 않는다. 값은 환경 변수로 조정한다.

잘못된 config는 server start에서 거절한다. 전체 deadline은 느린 body 수신보다 먼저 시작하고 request size → 전체 요청 token → 응답 캐시 조회 → upstream 작업 token/concurrency → upstream admission 순서로 자원을 얻는다. 실패·취소 시 이미 획득한 upstream 작업 concurrency semaphore와 upstream fetch admission permit은 반환하지만, 소비한 token-bucket token은 환불하지 않는다. unknown route/method와 검증에 실패한 요청은 전체 요청 한도만 쓰며 upstream을 호출하지 않는다.

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
