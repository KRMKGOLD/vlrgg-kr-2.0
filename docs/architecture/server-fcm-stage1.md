# Server FCM Match Notification — Stage 1.1 offline contract

- Status: Stage 1.1 offline server implementation GREEN
- Last reviewed: 2026-07-31
- Scope: `server` only, credential-free and offline-verifiable
- Related: [ADR-0001](adr/0001-match-notification-stage1-storage-and-provider-boundary.md), [ADR-0002](adr/0002-match-notification-stage1-1-offline-firestore-boundary.md), [Matches](../feature/matches/README.md), [CI/CD](../ci-cd.md)

## Document role

이 문서는 Match 알림 Stage 1.1의 구현 계약과 종료 조건을 정의한다. 현재 `main`에 존재하는 Stage 1 구현 사실과 Stage 1.1 목표를 구분한다.

- 현재 Stage 1: H2/Flyway, registration-value 기반 loopback API, process-owned fixed-delay tracking/delivery loop, START/END intent, Firebase Admin adapter가 구현되어 있다.
- Stage 1.1 목표: Firestore SDK + Emulator, 익명 Target 권한, START-only intent, request-bound scheduler use case, test-only fake App Check/FCM으로 교체한다.
- 이 문서가 갱신된 것만으로 Stage 1.1 구현이 완료된 것은 아니다. 코드와 credential-free 테스트가 모두 GREEN이 된 뒤에만 구현 완료로 표시한다.

Stage 1.1의 종료 문구는 다음과 같다.

> All contracts, offline implementation, and offline tests are GREEN. Real FCM and Cloud Run smoke tests remain NOT RUN — Stage 2.

2026-07-31 fresh evidence: `:server:test`, foreground Firestore Emulator `:server:firestoreEmulatorTest`, `:server:build`, `:server:installDist`, and the generated-launcher `/health` plus notification-route fail-closed smoke are GREEN. The PR-level `origin/main...HEAD -- app` check is empty; it is evidence for this Stage 1.1 branch rather than a permanent CI restriction on future app work.

`firestore.indexes.json` snapshots the current collection-group fan-out, due-Match, and delivery compound query shapes for review and later activation. Emulator coverage does not claim production index activation, which remains `NOT RUN — Stage 2`.

## Product boundary

- 로그인과 사용자 계정을 만들지 않는다.
- 앱 설치 단위의 익명 Target을 서버가 구분한다.
- Target ID나 secret이 앱 삭제·재설치 등으로 유실되어 새 Target이 생성되는 것은 허용한다. 이전 Target을 물리 기기나 사용자 기준으로 복원·병합하지 않는다.
- canonical delivery address는 opaque FCM registration token이다. FID, Android device ID, iOS identifier를 전송 주소나 권한 증명으로 사용하지 않는다.
- 한 Target은 자신이 선택한 Match만 구독한다. Topic은 공용 공지 요구가 생기기 전까지 사용하지 않는다.
- MVP 사용자 알림은 Match `START` 한 종류다. `END`, Team/Player 알림, 알림함과 사용자별 이력은 제외한다.
- 데이터베이스는 콘텐츠 cache가 아니라 Target, 구독, 한 번만 발송하려는 intent와 scheduler checkpoint를 보존하는 데만 사용한다.

## Stage boundary

### Stage 1.1에 포함

- production-facing `AppCheckVerifier`와 `NotificationProvider` interface
- test source 또는 test-only factory에서만 생성되는 deterministic fake App Check/FCM
- Google Cloud Firestore SDK 기반 `FirestoreNotificationStore`
- explicit Emulator host와 synthetic project ID를 사용하는 `EmulatorFirestoreClientFactory`
- 익명 Target 생성, Target Secret, token refresh, Match subscribe/unsubscribe, Target revoke, current-target global OFF 계약
- active unique Match의 10분 schedule slot을 처리하는 bounded `NotificationSchedulerUseCase`
- persistent START fan-out cursor, delivery claim/call marker/result state, retry·crash·concurrency 계약
- `/health`, `PORT`, `0.0.0.0`, `installDist`의 credential-free local runtime 검증
- 문서와 PR CI의 offline GREEN evidence 및 live `NOT RUN — Stage 2` ledger

### Stage 2로 이동

- 모든 `app/**` 구현과 Android/iOS Firebase SDK 연동
- 실제 Firebase App Check token 검증과 Firebase App ID allowlist
- 실제 FCM registration token 획득·갱신·발송·기기 표시
- production Firestore client factory, ADC, IAM, index activation과 live smoke
- public Scheduler route, Google OIDC 검증, Cloud Scheduler resource
- GCP API/Service Account/IAM, Cloud Run, WIF, GitHub Actions CD, traffic 전환과 rollback
- 실제 health/auth/Firestore/FCM/Cloud Run smoke와 비용·운영 관측

Stage 1.1에서는 위 항목을 skip-success로 만들지 않는다. 결과 표기는 정확히 `NOT RUN — Stage 2`다.

## Architecture

```text
Test client
  -> Ktor Target routes
     -> fake AppCheckVerifier
     -> Target Secret authorization
     -> Target use cases
        -> FirestoreNotificationStore
           -> Firestore Emulator

Test scheduler harness
  -> NotificationSchedulerUseCase(scheduleSlot, requestOwnerId)
     -> unique Match poll
     -> persistent START fan-out resume
     -> due delivery claim
     -> fake NotificationProvider
     -> durable result/failure state
```

일반 `main`/local/packaged runtime에는 fake App Check/FCM 생성 경로와 public scheduler route가 없다. Stage 1.1의 packaged smoke에서는 알림 route가 disabled/fail-closed인 상태로 `/health`만 검증한다.

## Anonymous Target authority

App Check와 Target Secret은 서로 다른 증명이다.

- `X-Firebase-AppCheck`: 요청이 허용된 Firebase App에서 왔다는 attestation evidence다.
- `Authorization: Target <targetId>.<secret>`: 특정 익명 Target의 상태를 읽거나 변경할 권한이다.
- registration token, FID, Target ID만으로는 권한을 증명하지 않는다.
- Target ID는 서버가 생성한 UUID v4이며 lowercase hyphenated `UUID.toString()` 표현만 canonical form으로 허용한다. path와 auth header의 다른 대소문자·축약 표현은 같은 Target으로 정규화하지 않고 `INVALID_REQUEST`로 거부한다.
- Target Secret은 32-byte CSPRNG으로 생성하고 원문은 Target 생성 응답에서 한 번만 반환한다. 서버에는 검증용 hash만 저장한다.
- `AppCheckVerifier` 입력은 `AppCheckEvidence(rawToken)`, 성공 출력은 `VerifiedApp(firebaseAppId)`다. Stage 1.1 fake는 test allowlist만 통과시키며 실제 token cryptography를 증명하지 않는다.

## Target HTTP contract

Base path는 `/api/v1/notification-targets`다.

| Method/path | Authority | Request | Success |
| --- | --- | --- | --- |
| `POST /` | `X-Firebase-AppCheck` | `RegisterTargetRequest(registrationToken)` | `201 RegisterTargetResponse(targetId,targetSecret,revision)` |
| `GET /{targetId}` | App Check + Target auth | none | `200 TargetStateResponse`; registration token과 secret은 제외 |
| `PUT /{targetId}/registration-token` | same | `RefreshRegistrationTokenRequest(registrationToken,expectedRevision)` | `200 TargetMutationResponse` |
| `PUT /{targetId}/match-subscriptions/{matchId}` | same | `SetMatchSubscriptionRequest(enabled,expectedRevision)` | `200 TargetMutationResponse` |
| `PUT /{targetId}/match-subscriptions` | same | `SetAllMatchSubscriptionsRequest(enabled=false,expectedRevision)` | `200`, 모든 current-target Match 구독 비활성화 |
| `POST /{targetId}/revoke` | same | `RevokeTargetRequest(expectedRevision)` | `200`, Target 비활성화 |

global OFF endpoint의 `enabled=true`는 `400 INVALID_REQUEST`다. 전체 ON은 지원하지 않으며 각 Match를 명시적으로 구독해야 한다.

| HTTP | Stable code | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | path/body/header 형식 또는 false-only 규칙 위반 |
| 401 | `APP_ATTESTATION_FAILED` | App Check evidence 누락·실패·허용되지 않은 app |
| 401 | `TARGET_AUTHENTICATION_FAILED` | Target auth 누락·불일치; Target 존재 여부를 누설하지 않음 |
| 404 | `NOT_FOUND` | 인증된 Target의 Match 또는 자원이 없음 |
| 409 | `REVISION_CONFLICT` | 같은 revision의 다른 operation 또는 stale mutation |
| 409 | `REVISION_EXHAUSTED` | accepted revision이 `Long.MAX_VALUE` |
| 409 | `SUBSCRIPTION_LIMIT` | Target별 활성 Match 상한 100 초과 |
| 409 | `ACTIVE_MATCH_CAPACITY_EXCEEDED` | 전체 active unique Match 상한 100 초과 |
| 413 | `REQUEST_TOO_LARGE` | bounded body/token limit 초과 |
| 500 | `INTERNAL_ERROR` | 안전하게 분류한 내부 실패 |

같은 revision과 같은 canonical operation은 idempotent replay로 성공한다. 같은 revision의 다른 operation, stale revision, revision overflow는 상태를 바꾸지 않는다.

## Firestore model and transaction invariants

논리 컬렉션은 다음 책임을 가진다. 실제 field 이름과 document layout은 구현 테스트와 함께 고정한다.

```text
notificationTargets/{targetId}
  registrationToken, secretHash, revision, operationHash, sendable
  subscriptions/{matchId}
    enabled, enabledAt (epoch millis), revision, updatedAt

notificationControl/capacity
  activeUniqueMatchCount

trackedMatches/{matchId}
  enabledTargetCount, terminal, nextCheckAt (epoch millis), startLatchedAt (epoch millis), lastObservation, fanoutCursor

deliveryIntents/{intentId}
  targetId, matchId, event=START, state, claim, dueAt (epoch millis), leaseUntil (epoch millis), timestamps

notificationControl/pollLease
  ownerId, scheduleSlot, leaseUntil (epoch millis)
```

필수 불변식:

- Target별 활성 Match는 최대 100개다.
- 전체 active unique Match도 최대 100개다. 여러 Target이 같은 Match를 구독해도 하나로 계산한다.
- subscribe/unsubscribe/global OFF는 Target revision, subscription, tracked count, global capacity를 한 transaction에서 일치시킨다.
- global OFF는 최대 100개 subscription에 bounded된 하나의 transaction이며 replay나 crash retry로 count를 두 번 감소시키지 않는다.
- 한 `(targetId, matchId, START)`에는 deterministic intent 하나만 존재한다. `intentId`는 `lowercaseHex(SHA-256(lp("vlrgg-match-start-intent-v1") || lp(canonicalTargetId) || lp(canonicalMatchId) || lp("START")))`로 고정한다. `lp(value)`는 UTF-8 byte length를 4-byte unsigned big-endian으로 붙인 length-prefixed encoding이고, `canonicalTargetId`는 위 lowercase hyphenated UUID, `canonicalMatchId`는 부호와 leading zero가 없는 10진수다.
- 이 `intentId`를 `deliveryIntents` document ID, fan-out create-if-absent, replay 조회, claim과 provider command에 동일하게 사용한다. 기존 document의 natural key가 요청 tuple과 다르면 hash collision 또는 저장소 손상으로 보고 fail-closed하며 새 intent를 만들거나 발송하지 않는다.
- terminal/revoked/unsendable Target과 Match는 claim/query 대상에서 제외한다.
- query, ordering, lease, retry due, fan-out eligibility에 쓰는 시간 field(`nextCheckAt`, `startLatchedAt`, `enabledAt`, `leaseUntil`, `dueAt`)은 signed UTC epoch milliseconds로 저장한다. 사람이 읽는 audit timestamp(`createdAt`, `updatedAt` 등)는 ISO-8601 문자열로 남길 수 있다.
- Emulator가 증명하는 범위는 transaction/query/document mapping이다. production IAM, index readiness, quota와 retry 차이는 Stage 2에서 검증한다.

## START observation and request-bound scheduling

- 10분은 desired schedule 간격이다. Stage 1.1 use case 자체가 timer를 소유하지 않는다.
- caller는 canonical `scheduleSlot`과 내부 `requestOwnerId`만 전달한다.
- deadline 500초, active Match 100, fan-out batch 100, delivery batch 500, fan-out batch start reserve 10초, lease 550초, clock skew 5초는 immutable server policy다.
- poll lease는 Firestore transaction/CAS로 한 owner만 획득한다.
- 처음 active unique Match가 될 때 `nextCheckAt` epoch milliseconds를 store clock의 현재 시각으로 설정한다. due query는 `enabledTargetCount > 0`, `terminal = false`, `nextCheckAt <= store clock`를 Firestore에서 제한하고 `activeMatchLimit`까지만 반환한다.
- 관찰 시도(정상 status, status 없음, upstream 실패)는 다음 due를 store clock 기준 10분 뒤로 전진시킨다. terminal 관찰은 `nextCheckAt`을 지우고 terminal 상태를 유지한다.
- 최초 정상 관찰은 baseline이다. `UPCOMING` 또는 `POSTPONED`에서 `LIVE`로 전환할 때만 START intent를 만든다.
- 최초 관찰이 이미 LIVE/terminal이거나 repeat, time change, cancelled, missing, network/parsing failure이면 START intent를 만들지 않는다.
- fan-out은 persistent cursor로 이어서 처리한다. 각 batch 직전 injectable clock의 `now + 10초 <= requestDeadline`일 때만 새 batch를 시작한다. 조건을 만족하지 않으면 현재 cursor를 checkpoint하고 새 write를 시작하지 않은 채 다음 Scheduler 요청에서 resume한다.
- Stage 2의 OIDC Scheduler adapter만 이 use case 앞에 붙는다. OIDC/JWKS/IAM은 use case 내부 책임이 아니다.

## Delivery safety and retry

상태 전이는 다음 원칙을 유지한다.

```text
PENDING | RETRY_WAIT -> CLAIMED_NOT_STARTED -> CALL_STARTED
CALL_STARTED -> ACCEPTED | INVALID_TARGET | RETRY_WAIT | TERMINAL_FAILURE | UNKNOWN
```

- provider 호출 전 committed `CALL_STARTED` marker를 기록한다.
- `CALL_STARTED` 이후 timeout, cancellation, exception 또는 불명확한 결과는 `UNKNOWN` terminal이며 자동 재발송하지 않는다.
- provider command는 `targetId`, `matchId`, `START`, opaque `registrationToken`, deterministic `intentId`, 안전한 payload만 포함한다.
- provider result는 `Accepted`, `InvalidTarget`, `Retryable(kind,hint)`, `NonRetryable`, `Unknown`만 허용한다.
- retry kind는 `RATE_LIMITED`, `UNAVAILABLE`뿐이다. raw status/header/SDK exception은 provider 경계 밖으로 나오지 않는다.
- 최대 application attempt는 5회다. 30초 exponential backoff를 2배씩 증가시켜 1시간으로 cap하고, `vlrgg-retry-jitter-v1` 기반 deterministic 0~5초 jitter를 더한 뒤 다시 cap한다.
- provider hint ceiling은 24시간이다. `RATE_LIMITED`에 hint가 없으면 최소 60초, `UNAVAILABLE`에 hint가 없으면 일반 backoff를 사용한다.
- unsafe/overflow hint와 attempt exhaustion은 terminal이다.
- Stage 2 adapter에서만 429를 `RATE_LIMITED`, 503/`UNAVAILABLE`/`INTERNAL`을 `UNAVAILABLE`로 map한다.

FCM provider acceptance는 실제 기기 표시를 보장하지 않는다. 이 계약의 “한 번”은 서버가 동일 intent를 의도적으로 다시 발송하지 않는다는 의미다.

## Configuration, redaction, and local runtime

- 서버는 Cloud Run 호환을 위해 `0.0.0.0`에 bind하고 `PORT`를 읽어야 한다. 현재 환경 변수 이름과 이전 listener 설정의 migration은 구현 단계에서 테스트로 고정한다.
- `/health`는 외부 credential 없이 server process 상태를 확인할 수 있어야 한다.
- `application.mainClass`는 `kr.co.cotton.vlrgg_mobile.ApplicationKt`이며 현재 확인된 packaging task는 `:server:installDist`다.
- local/main/packaged runtime은 emulator host나 credential을 암묵적으로 탐색하지 않는다. offline integration test만 explicit emulator factory를 생성한다.
- secret, registration token, App Check evidence, provider message ID, raw exception/status/header, intent/claim identifiers를 response, URL, log, metric label에 기록하지 않는다.
- 허용 관측은 bounded category, state transition count, backlog count, scheduler result 정도다.

## Verification and completion gate

Stage 1.1 구현 PR은 최소 다음을 fresh GREEN으로 증명해야 한다.

1. `:server:test`
2. 전용 JUnit4 Category 기반 `:server:firestoreEmulatorTest`
3. `:server:build`
4. `:server:installDist`
5. Target authority/security/revision/global-OFF Ktor + Emulator tests
6. Firestore concurrency, capacity, lease, crash/fan-out-resume tests
7. provider command/result, retry, UNKNOWN, redaction tests
8. packaged notification-disabled `/health` smoke
9. `origin/main...HEAD -- app` 변경 없음
10. live matrix의 모든 항목이 정확히 `NOT RUN — Stage 2`

구현 중 TDD RED는 임시 로컬 상태일 수 있지만 commit/PR terminal에는 의도적인 실패 테스트를 남기지 않는다. Emulator category는 default `test`에서 제외하고 전용 task와 CI에서 명시적으로 실행한다. 신규 JUnit5 전환은 하지 않는다.

## Implementation order

1. 문서와 ADR 정합화
2. repository/domain/provider 계약 추출과 기존 동작 parity test
3. Firestore SDK + Emulator adapter GREEN
4. composition 전환
5. H2/Flyway/Hikari, Firebase lifecycle, fixed-delay loops 제거
6. 익명 Target authority와 START-only HTTP 계약 구현
7. persistent tracking/fan-out/delivery와 request-bound scheduler 구현
8. credential-free CI와 전체 offline evidence 수집
9. 별도 Stage 2에서 App·실제 Firebase/GCP·Cloud Run smoke 수행
