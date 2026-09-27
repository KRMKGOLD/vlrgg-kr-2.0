# 서버 컨테이너 배포와 운영

일반 조회 서버는 서울 `asia-northeast3`의 Cloud Run에 배포돼 있다. [#111](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/111)에서 exact `main` SHA, private validation, production 승격, 실제 rollback, 비용 중단 실패 후 drain·복구와 공개 조회를 확인했다. #122는 private O3~O7 provider 전이, 오류 재발과 O7 OPEN/CLOSED 실제 수신, 실패 실행의 독립 복원을 확인했다. O8은 3개 지역의 정상→장애→정상 지표와 OPEN 수신을 확인했지만 CLOSED 확인 전에 대기가 끝났으며, O8/O9 완료와 production 영구 정책·정상 배포는 아직 남아 있다. 앱 배포와 제품 경기 알림 Stage 2는 별도 범위다.

## Image and runtime contract

- root `Dockerfile`은 Java 21 build stage에서 `:server:installDist`를 만들고 runtime에는 server distribution만 복사한다.
- runtime은 non-root `app` 사용자로 launcher를 PID 1로 실행하며 provider `PORT`와 `0.0.0.0`을 사용한다.
- Cloud Run은 CPU 1, memory 768 MiB, timeout 30초, concurrency 32, CPU throttling을 사용한다. `JAVA_OPTS=-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError`는 Java heap만 제한하며 전체 RSS 보장이 아니다.
- `.dockerignore`는 allowlist다. build에 필요한 root Gradle 설정, `core`, `server` source만 포함하고 VCS/IDE/Gradle state, build output, test/iOS source, local config, Firebase/service-account/signing files와 logs를 제외한다.
- 파일명 denylist는 임의 secret 탐지를 보장하지 않는다. build input을 늘릴 때 packaging 필요성과 secret/output 배제를 함께 검토한다.

## Deployment and rollback

`.github/workflows/deploy-server.yml`은 exact `main` SHA의 성공한 CI를 확인한 수동 배포만 허용한다. GitHub OIDC와 GCP WIF로 deploy Service Account를 impersonate하며 장기 key를 저장하지 않는다.

Deploy identity는 project의 Cloud Run developer/invoker, Artifact Registry repository writer와 runtime Service Account user 권한만 사용한다. WIF provider는 immutable repository/owner ID, `main`, 지정 workflow와 `production` environment로 제한한다. Runtime identity에는 일반 조회에 필요하지 않은 DB·Firebase 권한을 주지 않는다.

1. frozen source의 image를 cloud 인증 전에 GitHub runner에서 빌드한다. 그 뒤 WIF 인증을 수행해 Artifact Registry에 push하고 immutable digest를 기록한다.
2. 같은 digest를 private `vlrgg-query-check`에 먼저 배포한다. expected revision의 Ready, image, digest, 100% traffic, Invoker IAM check와 public invoker 부재를 확인한다.
3. validation service URL을 audience로 한 ID token으로 unauthenticated rejection, `/health`, 대표 query, 안전한 400과 docs/notification 404를 검사한다.
4. 첫 production은 private 기본 traffic으로 만들고 후속 production은 tag 없는 no-traffic revision으로 만든다. production URL용 별도 ID token smoke 뒤 명시적으로 100% 승격한다.
5. 승격 또는 smoke가 실패하면 workflow 시작 때 기록한 serving revision으로 rollback한다. partial traffic이나 불명확한 revision을 정상 상태로 간주하지 않는다.

Production은 public, service min/max `1/1`, revision min/max `0/1`이고 validation은 private, service min/max `0/1`이다. 양 service의 Invoker IAM check와 기존 identity/condition을 보존한다. 실제 URL, host, digest, revision, JWT와 raw provider output은 repository·Issue·Actions summary에 남기지 않는다. stable URL은 repository 밖 보호 파일로만 인계한다.

## Cost stop and recovery

월 10만 원 Budget과 1·3·5·8·10만 원 알림 resource를 사용한다. **1만 원에서 비용을 점검하고, 3만 원에서 추세·원인을 점검하며, 5만 원 도달 또는 더 이른 초과 예상 시 수동 공개 차단·중단한다.** 8·10만 원은 중단 실패·지연의 후속 경고다.

Budget과 Spend cap은 hard cap이 아니다. 보고·수신·집행 지연, 진행 중 요청, 저장·로그와 늦은 청구가 남을 수 있으며 service minimum 0도 접근 차단이나 비용 0을 뜻하지 않는다. 실제 invoice, Budget/Monitoring 알림 수신과 Spend cap 활성화는 확인하지 않았다.

비용 중단 순서:

1. repository `CLOUD_RUN_DEPLOY_ENABLED=false`와 production environment의 동명 variable 부재 또는 false를 확인한다.
2. 대기·실행 중인 deploy/observability validation을 취소하거나 종료까지 기다린다. 남은 private validation journal은 `observability-restore`로 먼저 복원한다.
3. production public invoker를 제거하고 production·validation service minimum을 0으로 맞춘다. 관련 immutable revision의 minimum도 전수 확인한다.
4. production과 project IAM의 `allUsers`·`allAuthenticatedUsers` 부재, default/tag URL의 실제 unauthenticated 거절과 drain을 상한 내 재확인한다. 기존 운영 identity는 보존하고 누락 표본은 unknown으로 둔다.

복구는 기록한 production revision/digest 100%, production min/max `1/1`, validation private/minimum 0, production public invoker, 외부 `/health`·대표 query·안전한 400·docs/notification 404 순으로 확인한다. IAM·traffic·resource를 다시 읽은 뒤 enable variable을 마지막에 true로 되돌린다.

## #122 observability live runbook

2026-09-27 확인 기준, 로컬 server tests, validation jar, stdout smoke와 workflow stub은 통과했다. Private O3~O7의 grouping·trace·sampling·incident와 실제 수신, 실패 실행의 baseline template·traffic·IAM 복원 및 소유 자원 정리도 확인했다. O8은 OPEN 수신 뒤 driver 대기가 먼저 끝나 전체 PASS가 아니며 O9와 OOM은 `NOT RUN`이다.

전체 live 완료와 production 적용을 완료로 표시하지 않는다. policy·장애·grouping·trace·sampling·receipt·복원·정리의 개별 결과를 확인하고, 미실행은 `NOT RUN`, 실패는 `FAIL`, 실행 중은 `IN PROGRESS`, 증거 미확인은 `UNKNOWN`으로 구분한다. endpoint status나 workflow 착수만으로 [#122](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/122)를 종료하지 않는다.

### Authority and preflight

- exact `main` SHA/CI/run attempt와 production·validation의 serving revision, digest, traffic, template, IAM을 보호 기록에 고정한다.
- Logging retention/sink/exclusion, Error Reporting, alert/uptime/channel, Monitoring service agent와 현재 권한을 mutation 전에 읽는다. 이름만으로 소유권을 추정하지 않는다.
- 권한은 inventory, private validation 변경, 필요한 최소 invoker, 이번 run 소유 policy/check/revision/image의 생성·조회·비활성화·삭제로 제한한다. 기존 shared channel/policy/image/baseline revision을 보존하며 private 검증 중에는 production IAM/policy를 변경하지 않는다.
- incident, Error Reporting group와 channel 조회가 실패하면 journal·image·revision을 만들기 전에 중단한다. 권한·API·receiver가 불명확해도 fault 전에 `NOT RUN`으로 종료한다.
- 실제 URL, receiver, project/revision/digest, credential과 raw log는 공개 기록에 남기지 않는다.

Protected environment에는 channel resource-name 배열 `GCP_OBSERVABILITY_NOTIFICATION_CHANNELS_JSON`, receiver 확인 `GCP_OBSERVABILITY_CONFIRMED_RECEIVERS=true`, Error Reporting 등록 확인 `GCP_ERROR_REPORTING_ENROLLED=true`를 둔다. Monitoring service agent가 project IAM의 unconditional `roles/monitoring.notificationServiceAgent` member인지 확인하며 별도 identity secret이나 service-account key를 만들지 않는다.

Observability custom role은 다음 23개 권한으로 고정한다: `serviceusage.services.use`, `resourcemanager.projects.get`, `resourcemanager.projects.getIamPolicy`, `logging.logEntries.list`, `logging.notificationRules.create`, `errorreporting.groups.list`, `errorreporting.errorEvents.list`, `errorreporting.groupMetadata.get`, `errorreporting.groupMetadata.update`, `errorreporting.applications.list`, `monitoring.metricDescriptors.get`, `monitoring.timeSeries.list`, `monitoring.notificationChannels.get`, `monitoring.alerts.list`, `monitoring.alertPolicies.get`, `monitoring.alertPolicies.list`, `monitoring.uptimeCheckConfigs.get`, `monitoring.uptimeCheckConfigs.list`, `monitoring.alertPolicies.create`, `monitoring.alertPolicies.update`, `monitoring.alertPolicies.delete`, `monitoring.uptimeCheckConfigs.create`, `monitoring.uptimeCheckConfigs.delete`. Cloud Run은 validation service에 한정한 `roles/run.admin`과 필요한 invoker/token binding을 사용한다. Artifact cleanup role은 기존 repository의 `artifactregistry.tags.delete`와 `artifactregistry.versions.delete`만 허용하며 project-wide Repository Admin은 부여하지 않는다.

### Initial signals

| Signal | Initial policy and evidence |
| --- | --- |
| 5xx | exact project/location/service의 `request_count` 5분 증가량 `>=3`; 같은 window 2건은 미충족, 3건은 OPENED, fresh 2xx와 0 뒤 CLOSED·receipt 확인 |
| uptime | production `/health`, 200과 `status=ok`, 5분 주기·10초 timeout·3 region; 서로 다른 checker 정상값 뒤 장애·복구·receipt 확인 |
| abnormal exit | 실제 확인한 Cloud Run system log signature만 사용; 정상 exit/SIGTERM 제외, OOM 증거가 없으면 matcher를 넓히지 않고 `NOT RUN` |
| Error Reporting | 안전한 `INTERNAL_ERROR`·`SOURCE_PARSING_FAILURE`; representative event의 revision/frame과 실제 receipt 확인 |

이메일 채널을 기본으로 시작한다. Monitoring과 Error Reporting 연결을 각각 저장하며 채널 생성만으로 receipt를 통과시키지 않는다. empty/multiple/NaN/missing data를 정상으로 취급하지 않고 provider의 incident close 방식과 지연을 기록한다.

### Private validation and recovery

workflow operation은 `deploy`, `observability-validate`, `observability-restore`만 허용한다. 모두 exact SHA CI, protected environment와 WIF를 사용한다. deploy/validate는 enable=true가 필요하지만 restore는 비용 중단 중에도 실행할 수 있도록 enable 검사에서 제외한다. `validation_scope`는 validate에서만 `all` 또는 `o8-o9`를 허용한다. `o8-o9`도 새 private revision·journal·preflight·복원을 사용하며 생략한 O3~O7을 해당 실행의 PASS로 기록하지 않는다.

- validate는 test-only overlay를 고정 private service에 배포하며 production token, traffic, IAM과 policy를 변경하지 않는다.
- 8 KiB 이하 annotation journal에 immutable baseline revision, allowlist로 투영한 template와 hash, traffic/IAM, run-owned resource와 pending mutation을 기록한다. etag CAS와 read-back이 성공하기 전에는 IAM, policy, image와 fault를 변경하지 않는다. active/unknown journal 또는 조회 실패가 있으면 새 deploy를 첫 mutation 전에 중단한다.
- provider access를 cloud 인증 직후와 overlay 배포 후 다시 확인한다. 각 fault·mutation 직전 target guard도 반복한다.
- live driver는 90분 deadline을 사용하고 workflow attempt 120분을 넘기지 않는다. 마지막 30분에는 새 fault나 mutation을 시작하지 않는다. O8 장애 주입 직전에는 두 fault poll, provider 처리 여유와 O9 진입 시간을 포함해 최소 2,940초가 남았는지 다시 확인하고 부족하면 복원한다.
- raw provider body는 `RUNNER_TEMP`의 0600 파일에만 두고 summary는 receipt 확인 전 `RECEIPT PENDING`으로 남긴다. receiver-side 증거 없이는 PASS로 올리지 않는다.
- failure에서도 immutable baseline revision의 존재·Ready 확인 → 그 revision으로 traffic 100% 복원 → allowlist template/hash 복원 → private IAM·health/query·scaling read-back → owned-resource cleanup → journal clear 순서를 `always()` 경로로 실행한다. 전체 service export를 덮어쓰지 않는다.
- serving 또는 tagged revision은 삭제하지 않는다. fault revision/image/policy/check는 journal의 exact name·digest·ownership label과 다른 참조 부재가 확인된 경우에만 정리한다.
- ownership이나 restore 상태가 불명확하면 resource와 journal을 유지하고 secret 없는 blocker를 남긴다. 정상 상태와 cleanup read-back이 끝난 뒤 journal을 마지막 etag CAS로 지운다. restore는 build/test/image push를 건너뛴다.
- 실제 private exit signature가 ambiguous하거나 정상 종료와 충돌하면 후속 exit를 보내지 않는다. production service에는 validation main, jar와 `__observability/*` route가 없어야 한다.
- policy helper는 journal 소유 private service와 run-owned policy/check만 변경한다. render는 cloud mutation 없이 수행하고 기존 production/shared policy나 channel을 이름만으로 갱신·삭제하지 않는다.

### Production permanent policies

Private O7~O9의 provider 결과·실제 수신·독립 복원을 모두 확인한 뒤에만 production 정책을 적용한다. 정상 이미지를 배포해 Ready, 단일 revision 100% traffic, immutable digest, template, IAM과 공개 smoke를 먼저 고정하며 production에 장애를 주입하지 않는다.

기존 alert policy·uptime check를 끝 페이지까지 조회해 중복과 소유권을 확인하고, 소유 label이 있는 5xx policy, uptime check와 그 exact ID를 쓰는 uptime policy, 검증된 system-log signature의 log policy만 만든다. 적용 실패 시 이번 작업이 만든 exact 자원만 비활성화·삭제하고 기존 channel, Logging routing, IAM과 serving revision은 보존한다. OOM을 실행하지 않았다면 OOM 감지를 검증했다고 기록하지 않는다.

재현 가능한 로컬 검증:

```sh
./gradlew :server:test :server:build :server:installDist :server:observabilityValidationJar
bash .github/scripts/smoke-observability.sh
bash .github/scripts/test-observability-operations.sh
bash .github/scripts/test-query-production-deploy.sh
```
