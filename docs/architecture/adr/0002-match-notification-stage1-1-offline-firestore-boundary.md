# ADR-0002: Stage 1.1 offline Firestore and anonymous Target boundary

- Status: Accepted
- Date: 2026-07-31
- Scope: Match notification Stage 1.1 server and offline verification
- Supersedes: [ADR-0001](0001-match-notification-stage1-storage-and-provider-boundary.md)
- Normative contract: [Stage 1.1](../server-fcm-stage1.md)

경기 알림 제품 구현은 1차 MVP에서 제외하고 MVP 이후 Stage 2로 이관했다. 이 ADR의 offline 기술 결정과 완료 증거는 유지하며 제품 범위는 [Feature Guide](../../feature/README.md#mvp-이후-stage-2-경기-알림)를 따른다.

## Context

Stage 1의 local H2, provider-value identity와 process-owned loop는 Cloud Run의 여러 process/revision과 scale-to-zero에 맞지 않았다. 로그인 없이 설치 단위 설정이 필요했고, 별도 PostgreSQL 운영 없이 실제 persistence concurrency를 credential-free 환경에서 검증해야 했다.

## Decision

### Anonymous Target authority

서버가 install-scoped `targetId`와 one-time `targetSecret`을 발급한다. App Check는 허용된 앱을, Target Secret은 특정 Target 변경 권한을 증명한다. FCM registration token은 전달 주소일 뿐 identity나 authority가 아니다. FID와 device identifier를 merge key나 인증에 사용하지 않는다. 앱 재설치로 credential을 잃으면 새 Target을 만들며 이전 Target을 복원·병합하지 않는다.

### Firestore persistence

Target, subscription, unique-Match capacity, poll lease, fan-out cursor와 delivery intent를 Firestore에 저장한다. Stage 1.1은 Firestore SDK transaction/query/document mapping을 Emulator에서 검증한다. production client, ADC, IAM, index, quota와 live behavior는 Stage 2가 소유한다.

### Offline external boundaries

Stage 1.1은 `AppCheckVerifier`와 `NotificationProvider` contract를 두되 deterministic fake는 test source/factory에서만 만든다. local/main/packaged runtime에는 fake나 production Firebase adapter가 없고 notification route를 fail-closed로 유지한다. 실제 App Check/FCM adapter와 device-display smoke는 Stage 2에서 당시 공식 API를 다시 확인한 뒤 구현한다.

### START-only request-bound scheduling

Stage 1.1은 START만 지원한다. process timer 대신 bounded `NotificationSchedulerUseCase(scheduleSlot, requestOwnerId)`를 사용하고 Firestore lease, persistent cursor와 delivery state로 crash 후 재개한다. 외부 Scheduler의 desired interval은 10분이며 public OIDC route와 Cloud Scheduler resource는 Stage 2가 소유한다.

Provider call 전 committed `CALL_STARTED`를 기록한다. 이후 결과가 불명확하면 `UNKNOWN` terminal로 남기고 자동 재발송하지 않는다. server intent uniqueness나 FCM acceptance는 기기 표시 exactly-once를 뜻하지 않는다.

## Alternatives

- in-memory fake만 사용하면 Firestore transaction retry, query cursor와 concurrent capacity를 증명하지 못한다.
- production Firebase adapter까지 함께 만들면 credential 없는 Stage 1.1에서 readiness를 증명하지 못한 채 lifecycle만 늘어난다.
- PostgreSQL/Cloud SQL은 이 작은 익명 notification state에 별도 instance와 migration 운영을 추가한다.
- FCM Topic은 Target별 Match 설정과 1회 intent를 서버가 관리할 수 없다.

## Consequences

- Firestore는 Match notification state에만 사용하며 일반 scraping cache나 사용자 DB로 확장하지 않는다.
- H2/Flyway/Hikari, process-owned notification loop와 active Firebase Admin lifecycle은 Stage 1.1 runtime에서 제거한다.
- Emulator GREEN은 production IAM/index/quota/latency를 증명하지 않는다.
- orphan Target과 재설치 시 일시적 중복 전달은 남을 수 있다.
- offline repository/concurrency/security/scheduler/delivery/build/package evidence는 GREEN이다. 실제 App/Firebase/GCP notification runtime은 `NOT RUN — Stage 2`다.
