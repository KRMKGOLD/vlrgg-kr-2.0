# ADR-0002: Stage 1.1 offline Firestore and anonymous Target boundary

- Status: Accepted
- Date: 2026-07-31
- Decision scope: Match notification Stage 1.1 server and offline verification
- Supersedes: [ADR-0001](0001-match-notification-stage1-storage-and-provider-boundary.md) for persistence, target authority, event scope, provider lifecycle and scheduling
- Related: [Stage 1.1 contract](../server-fcm-stage1.md), [Matches](../../feature/matches/README.md), [CI/CD](../../ci-cd.md)

## Context

2026-07-31 implementation evidence is GREEN for the offline Firestore Emulator, server tests, build, install distribution, packaged health smoke.

Stage 1 proved that durable subscription and delivery state can prevent the server from intentionally sending the same logical notification repeatedly. Its file-backed H2 store, registration-value identity, process-owned loops and active Firebase Admin lifecycle do not fit the newly selected runtime direction:

- 로그인 없이 앱 설치 단위의 Target과 그 Target의 Match별 설정 상태가 필요하다.
- 앱 재설치로 Target ID를 잃고 새 Target이 생기는 것은 허용한다.
- Match START 알림은 서버 재시작·재시도에도 한 logical intent로 유지해야 한다.
- Cloud Run은 scale-to-zero와 여러 process/revision 가능성이 있어 process memory, local file, singleton loop를 영속 상태나 scheduler ownership으로 사용할 수 없다.
- Stage 1.1은 실제 Firebase/GCP credential 없이 구현과 테스트를 완료해야 하지만, persistence concurrency를 단순 fake로 대신해서는 안 된다.
- 작은 사이드 프로젝트이므로 별도 PostgreSQL/Cloud SQL 운영을 피하고 Firebase/GCP 연계 비용과 운영 복잡도를 낮추는 선택이 필요하다.

## Decision

### Anonymous Target

서버는 계정이나 사람을 식별하지 않고 설치 단위의 익명 Target을 식별한다. Target 생성 시 서버가 `targetId`와 one-time `targetSecret`을 발급한다. App Check는 허용된 앱을, Target Secret은 해당 Target의 변경 권한을 각각 증명한다.

FCM registration token은 전달 주소일 뿐 Target ID나 권한 증명이 아니다. token refresh는 같은 Target의 주소만 교체한다. FID와 platform device identifier는 send address, Target merge key, authentication에 사용하지 않는다. 앱 삭제·재설치로 Target 자격을 잃으면 새 Target을 만들며 이전 Target의 자동 복원·병합은 하지 않는다.

### Firestore

Target, Match subscription, unique-Match count, poll lease, fan-out cursor와 delivery intent를 Firestore에 저장한다. Stage 1.1은 Google Cloud Firestore SDK의 실제 transaction/query/document mapping을 Firestore Emulator에서 검증한다.

Emulator client factory는 explicit host와 synthetic project ID를 요구하며 credential/ADC 접근을 금지한다. production client factory, ADC, IAM, index activation, quota와 live transaction behavior는 Stage 2가 소유한다. 따라서 “same code path” 주장은 repository mapping에만 적용되고 client construction이나 production readiness에는 적용되지 않는다.

### External provider boundary

Stage 1.1은 production-facing `AppCheckVerifier`와 `NotificationProvider` 계약을 구현하되 Firebase Admin production adapter를 활성화하지 않는다. deterministic fake는 test source 또는 test-only injected factory에만 존재한다. 일반 local/main/packaged runtime은 fake를 만들지 않고 알림 route를 disabled/fail-closed로 유지한다.

실제 Firebase Admin App Check/FCM adapter, real token, allowed Firebase App ID, SDK error mapping과 device-display smoke는 Stage 2에서 당시 공식 API를 재검증한 후 구현한다.

### START-only and request-bound Scheduler

MVP event는 `START`만 지원한다. `END`는 제거한다. scheduler는 process-owned background loop가 아니라 `NotificationSchedulerUseCase(scheduleSlot, requestOwnerId)` 한 번의 bounded 요청이다. Firestore lease가 동일 slot의 단일 owner를 정하고 persistent fan-out cursor와 delivery state가 요청 종료·crash 후 재개를 보장한다.

10분은 외부 Scheduler가 요청할 desired 간격이다. Stage 1.1은 active unique Match를 처음 만들 때 즉시 due로 기록하고, 각 observation attempt 뒤 store clock 기준 10분 후 `nextCheckAt`으로 전진시킨다. query는 due, non-terminal, enabled Match만 `activeMatchLimit`까지 읽으므로 scheduler가 non-due 작업을 스캔하지 않는다. Stage 1.1은 test harness로 같은 use case를 검증하며 public Scheduler route를 등록하지 않는다. Google OIDC route와 Cloud Scheduler resource는 Stage 2가 소유한다.

### Delivery ambiguity

provider 호출 전 committed `CALL_STARTED` marker를 기록한다. marker 이후 결과가 불확실하면 `UNKNOWN` terminal로 남기고 자동 재전송하지 않는다. 이는 가능한 유실보다 중복 사용자 알림을 피하는 Stage 1의 safety decision을 유지한다. FCM acceptance나 서버 intent uniqueness를 실제 기기 표시 exactly-once와 동일시하지 않는다.

## Alternatives considered

### 모든 저장소와 provider를 in-memory fake로 구현

외부 의존성은 가장 적지만 Firestore transaction retry, canonical document creation, query cursor와 concurrent capacity invariant를 증명하지 못한다. Stage 2에서 persistence adapter 전체가 새 구현이 되므로 선택하지 않았다.

### Firestore Emulator와 production App Check/FCM adapter를 함께 구현

Stage 2 작업량은 줄 수 있으나 실제 project/token/network 없이 production readiness를 증명하지 못하고 Stage 1.1 dependency와 lifecycle만 늘린다. provider interface와 fake까지만 Stage 1.1에 둔다.

### PostgreSQL 또는 Cloud SQL

범용 relational transaction에는 적합하지만 이 프로젝트의 익명 Target/FCM 상태를 위해 별도 DB instance, schema migration과 운영 비용을 추가한다. 현재 작은 규모와 Firebase 연계를 고려하면 Firestore가 더 단순하다. 향후 query/transaction/cost 요구가 바뀌면 별도 ADR로 재검토한다.

### FCM Topic-only

공용 공지에는 적합하지만 누가 어떤 Match를 선택했는지, current-target 설정 상태, 1회 intent를 서버가 관리할 수 없다. 현재 요구는 Target별 Match 선택이므로 사용하지 않는다.

## Consequences

- 로그인 없이 Target별 Match 설정과 token rotation을 표현할 수 있다.
- Firestore transaction과 persistent cursor가 Cloud Run scale-to-zero에 맞는 영속 경계를 제공한다.
- H2/Flyway/Hikari와 process-owned notification loops는 Stage 1.1 runtime에서 제거된다.
- Firebase Admin production lifecycle도 Stage 1.1 active runtime에서 제거되고 Stage 2 adapter로 다시 도입된다.
- Firestore는 Match 알림 state에만 사용한다. 일반 scraping response cache나 다른 feature의 사용자 DB로 확장하지 않는다.
- Emulator GREEN은 production IAM/index/quota/latency를 증명하지 않는다.
- 재설치 후 이전 Target/설정 복원을 보장하지 않고 orphan Target이나 잠시 중복된 전달이 남을 수 있다.
- 실제 App, Firebase, Cloud Run 동작은 Stage 2 smoke 이전까지 `NOT RUN — Stage 2`다.

## Completion criteria

이 ADR의 구현 완료 표시는 다음 조건이 모두 충족된 뒤에만 가능하다.

- Firestore SDK + Emulator repository/concurrency/crash suites GREEN
- 익명 Target authority와 exact HTTP/security/revision/global-OFF suites GREEN
- START-only scheduler/fan-out/delivery/retry/UNKNOWN suites GREEN
- packaged notification-disabled `/health` smoke GREEN
- `app/**` 변경 없음
- 실제 App/Firebase/GCP/Cloud Run 행은 정확히 `NOT RUN — Stage 2`

2026-07-31에 위 offline criteria는 credential-free server test/build/installDist, foreground Firestore Emulator suite, generated launcher smoke, and no-`app/**` branch diff evidence로 GREEN이다. 실제 App/Firebase/GCP/Cloud Run 검증은 이 결과에 포함되지 않으며 `NOT RUN — Stage 2`로 남는다.
