# 서버 컨테이너 배포와 운영

일반 조회 서버는 서울 `asia-northeast3`의 Cloud Run에 배포돼 있다. [#111](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/111)에서 배포·rollback·비용 중단과 복구를 확인했다. 2026-09-29 KST 기준 [#122](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/122)의 private 오류·가용성·native OOM 알림, 실제 이메일 수신과 독립 복원을 검증했고, 정상 production 배포와 영구 Monitoring 자원 4개의 적용·정상 상태 확인을 마쳤다. 앱 배포와 제품 경기 알림 Stage 2는 별도 범위다.

## Image and runtime contract

- root `Dockerfile`은 Java 21 build stage에서 `:server:installDist`를 만들고 runtime에는 server distribution만 복사한다.
- runtime은 non-root `app` 사용자로 launcher를 PID 1로 실행하며 provider `PORT`와 `0.0.0.0`을 사용한다.
- Cloud Run은 CPU 1, memory 768 MiB, timeout 30초, concurrency 32, CPU throttling과 production startup CPU boost를 사용한다. `JAVA_OPTS=-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError`는 Java heap만 제한하며 전체 RSS 보장이 아니다.
- `.dockerignore`는 allowlist다. build에 필요한 root Gradle 설정, `core`, `server` source만 포함하고 VCS/IDE/Gradle state, build output, test/iOS source, local config, Firebase/service-account/signing files와 logs를 제외한다.
- 파일명 denylist는 임의 secret 탐지를 보장하지 않는다. build input을 늘릴 때 packaging 필요성과 secret/output 배제를 함께 검토한다.

## Deployment and rollback

`.github/workflows/deploy-server.yml`은 exact `main` SHA의 최신 CI run/attempt에서 Linux `server` job 하나가 완료·성공한 수동 배포만 허용한다. 전체 job inventory와 `run_id`, `run_attempt`, `head_sha`를 확인하고 같은 run을 다시 읽어 attempt 변경을 거부한다. main push와 main 수동 CI 중 최신 run을 사용하며 Android/macOS `ios` job을 기다리지 않는다. server CI와 deploy job은 [ref별 플랫폼 잠금](../ci-cd.md#platform-ci-and-deployment-concurrency)을 공유해 CI 증명 조회부터 배포·복원·cleanup이 끝날 때까지 같은 플랫폼 CI의 실행을 막는다. 기존 `cloud-run-query-production` workflow mutex도 유지한다. 대상이 skip됐으면 [명시적 CI 검증](../ci-cd.md#skipped-platform-validation-before-deployment)을 같은 main SHA에서 실행한다. skip이나 이전 성공은 배포 증거가 아니다. GitHub OIDC와 GCP WIF로 deploy Service Account를 impersonate하며 장기 key를 저장하지 않는다.

Deploy identity는 project의 Cloud Run developer/invoker, Artifact Registry repository writer와 runtime Service Account user 권한만 사용한다. WIF provider는 immutable repository/owner ID, `main`, 지정 workflow와 `production` environment로 제한한다. Runtime identity에는 일반 조회에 필요하지 않은 DB·Firebase 권한을 주지 않는다.

1. frozen source의 image를 cloud 인증 전에 GitHub runner에서 빌드한다. 그 뒤 WIF 인증을 수행해 Artifact Registry에 push하고 immutable digest를 기록한다.
2. 같은 digest를 private `vlrgg-query-check`에 먼저 배포한다. expected revision의 Ready, image, digest, 100% traffic, Invoker IAM check와 public invoker 부재를 확인한다.
3. validation service URL을 audience로 한 ID token으로 unauthenticated rejection, `/health`, 대표 query, 안전한 400과 docs/notification 404를 검사한다.
4. 첫 production은 private 기본 traffic으로 만들고 후속 production은 tag 없는 no-traffic revision으로 만든다. production URL용 별도 ID token smoke 뒤 명시적으로 100% 승격한다.
5. 승격 또는 smoke가 실패하면 workflow 시작 때 기록한 serving revision으로 rollback한다. partial traffic이나 불명확한 revision을 정상 상태로 간주하지 않는다.

Production은 public, service min/max `0/1`, revision min/max `0/1`이고 validation은 private, service min/max `0/1`이다. 양 service의 Invoker IAM check와 기존 identity/condition을 보존한다. 실제 URL, host, digest, revision, JWT와 raw provider output은 repository·Issue·Actions summary에 남기지 않는다. stable URL은 repository 밖 보호 파일로만 인계한다.

## Idle cost and scale to zero

2026-10 비용 점검에서 production service minimum 1이 요청이 없어도 Seoul Tier 2 idle min instance 요금을 계속 만든다는 것을 확인했다. 실제 사용자가 없는 단계의 고정비를 없애기 위해 production service minimum을 0으로 둔다. CPU throttling(request-based billing)을 유지하므로 minimum이 아닌 idle instance는 과금하지 않고, 요청 처리 시간만 free tier와 이후 사용량으로 과금한다. [Cloud Run pricing](https://cloud.google.com/run/pricing)

- 요청이 없는 기간 뒤 첫 요청은 JVM cold start를 포함할 수 있다. startup CPU boost로 시작 시간을 줄이지만 시작 지연 상한을 보장하지 않는다.
- 운영 uptime check의 주기 요청이 instance를 유지할 수 있지만 provider가 idle instance 유지를 보장하지 않으므로 warm 상태를 계약으로 사용하지 않는다.
- Stage 2 알림은 Cloud Scheduler 요청 기반 설계라 상시 instance를 전제하지 않는다. 실사용자 latency 요구가 생기면 minimum 재상향을 비용과 함께 다시 결정한다.
- service minimum 변경은 다음 production deploy부터 적용된다. 수동 변경은 workflow 계약과 어긋나므로 배포로 반영한다.

## Cost stop and recovery

월 10만 원 Budget과 1·3·5·8·10만 원 알림 resource를 사용한다. **1만 원에서 비용을 점검하고, 3만 원에서 추세·원인을 점검하며, 5만 원 도달 또는 더 이른 초과 예상 시 수동 공개 차단·중단한다.** 8·10만 원은 중단 실패·지연의 후속 경고다.

Budget과 Spend cap은 hard cap이 아니다. 보고·수신·집행 지연, 진행 중 요청, 저장·로그와 늦은 청구가 남을 수 있으며 service minimum 0도 접근 차단이나 비용 0을 뜻하지 않는다. 실제 invoice, Budget 알림 수신과 Spend cap 활성화는 확인하지 않았다. #122의 Monitoring 장애 알림 수신은 Budget 수신 검증과 별개다.

비용 중단 순서:

1. repository `CLOUD_RUN_DEPLOY_ENABLED=false`와 production environment의 동명 variable 부재 또는 false를 확인한다.
2. 대기·실행 중인 deploy/observability validation을 취소하거나 종료까지 기다린다. 남은 private validation journal은 `observability-restore`로 먼저 복원한다.
3. production public invoker를 제거하고 production·validation service minimum을 0으로 맞춘다. 관련 immutable revision의 minimum도 전수 확인한다.
4. production과 project IAM의 `allUsers`·`allAuthenticatedUsers` 부재, default/tag URL의 실제 unauthenticated 거절과 drain을 상한 내 재확인한다. 기존 운영 identity는 보존하고 누락 표본은 unknown으로 둔다.

복구는 기록한 production revision/digest 100%, production min/max `0/1`, validation private/minimum 0, production public invoker, 외부 `/health`·대표 query·안전한 400·docs/notification 404 순으로 확인한다. IAM·traffic·resource를 다시 읽은 뒤 enable variable을 마지막에 true로 되돌린다.

## #122 observability live runbook

2026-09-29 KST 확인 기준이다. 각 단계는 아래 실행과 보호된 원본 증거에 따로 귀속한다. Actions 성공만으로 실제 수신·복원을 대신하지 않으며, 공개 기록에는 workflow 링크와 검증 결과만 남긴다.

| 단계 | 확인한 결과 |
| --- | --- |
| O3~O6 | INTERNAL·parsing 오류 그룹과 실제 신규/동일 그룹 재발 이메일, 안전한 frame·revision, request trace 연결, category별 sampling·억제 수 확인 |
| O7 | native 5xx 5분 창 2건에서는 OPEN 없음, 3건에서 OPEN, 복구 후 동일 incident CLOSED와 실제 두 이메일 확인 |
| O8 | 서로 다른 3개 checker의 정상→장애→정상 값, 동일 incident OPEN/CLOSED와 실제 두 이메일, 독립 복원·정리 확인 |
| log-delivery | [run 36410338960](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/36410338960/attempts/1), attempt 1: 고정 canary→exact-policy OPEN→실제 이메일→독립 복원. O9와 별도 증거 |
| OOM discovery D1 | [run 36430026199](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/36430026199/attempts/1), attempt 1: 파일 쓰기 discovery는 `FAIL`, 실제 native OOM은 입증하지 못함. 실패 원문을 확보하지 못해 원인은 미확정이며 독립 복원·소유 자원 정리는 확인 |
| OOM discovery D2 | [run 36446508402](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/36446508402/attempts/1), attempt 1: 실제 native 768 MiB 메모리 한도 초과와 독립 복원 확인. 조기 cap guard로 workflow는 `FAIL`이며 이 결과를 성공으로 바꾸지 않음 |
| O9 OOM 알림 | [run 36464164330](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/36464164330/attempts/1), attempt 1: 정책 생성·300초 대기 후 단 한 번의 OOM 요청, 같은 revision의 fresh native OOM→exact-policy OPEN→승인 수신함의 실제 이메일→독립 복원·소유 image/revision/policy 삭제 확인. 독립 감사 `PASS` |
| Production | [run 36467015465](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/36467015465/attempts/1), attempt 1: 검증된 `main`의 정상 이미지 배포, Ready·100% traffic·IAM·공개 health/query·native 정상 요청 로그 확인. 영구 자원 4개 read-back, 생성 이후 3개 지역 정상/HTTP 200, 열린 소유 incident 없음 확인 |

O9와 production은 [main CI 36462345096](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/36462345096/attempts/1), attempt 1의 exact SHA에 연결된다. 실제 project·revision·digest·정책 ID·수신자·원본 로그·메일은 저장소 밖 보호 기록에 보존한다. 이전 exit42와 파일 쓰기 discovery 실패도 복원 결과와 함께 보존한다. 누적 OOM 요청은 discovery 2회와 알림 검증 1회로 총 3회이며 추가 장애 주입은 수행하지 않는다. 이 결과는 검증한 768 MiB native OOM 문구 계열만 입증하며 모든 crash·JVM OOME·종료 코드 탐지를 뜻하지 않는다.

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
| uptime | `/health`의 200과 `status=ok`, 5분 주기·10초 timeout·3 region; 300초 정렬 후 2개 이상 checker 실패가 600초 지속되면 조건 충족. Private에서 장애·복구·receipt, production에서 정상 상태 확인 |
| native OOM | 실제 검증한 768 MiB 메모리 한도 초과 system ERROR 문구 계열만 사용; 정상 exit/SIGTERM과 일반 JVM OOME는 탐지 증거에 포함하지 않음 |
| Error Reporting | 안전한 `INTERNAL_ERROR`·`SOURCE_PARSING_FAILURE`; representative event의 revision/frame과 실제 receipt 확인 |

이메일 채널을 기본으로 시작한다. Monitoring과 Error Reporting 연결을 각각 저장하며 채널 생성만으로 receipt를 통과시키지 않는다. empty/multiple/NaN/missing data를 정상으로 취급하지 않고 provider의 incident close 방식과 지연을 기록한다.

### Private validation and recovery

workflow operation은 `deploy`, `observability-validate`, `observability-restore`만 허용한다. 모두 exact SHA의 최신 main CI run/attempt에서 Linux `server` 성공, protected environment와 WIF를 사용한다. deploy/validate는 enable=true가 필요하지만 restore는 비용 중단 중에도 실행할 수 있도록 enable 검사에서 제외한다. `validation_scope`는 validate에서만 `all`, uptime·종료용 `o8-o9`, 종료 전용 `o9`, 장애 없는 알림 전달 진단용 `log-delivery`, 컨테이너 OOM 로그 수집용 `o9-oom-discovery`, 검토된 native OOM 알림 검증용 `o9-oom`을 허용한다. 축소 범위도 새 private revision·journal·preflight·복원을 사용하며 생략한 단계는 해당 실행의 PASS로 기록하지 않는다.

- validate는 test-only overlay를 고정 private service에 배포하며 production token, traffic, IAM과 policy를 변경하지 않는다.
- 8 KiB 이하 annotation journal에 immutable baseline revision, allowlist로 투영한 template와 hash, traffic/IAM, run-owned resource와 pending mutation을 기록한다. etag CAS와 read-back이 성공하기 전에는 IAM, policy, image와 fault를 변경하지 않는다. active/unknown journal 또는 조회 실패가 있으면 새 deploy를 첫 mutation 전에 중단한다.
- provider access를 cloud 인증 직후와 overlay 배포 후 다시 확인한다. 각 fault·mutation 직전 target guard도 반복한다.
- `log-delivery`는 현재 private revision의 stdout 고정 문구 `OBSERVABILITY_LOG_DELIVERY_CANARY`에만 맞는 log policy를 생성·read-back하고, 진단용 300초 대기 후 canary를 한 번 출력한다. 생성 이후의 exact-policy OPEN과 승인된 수신함의 실제 이메일을 별도로 확인한다. `LOG_DELIVERY` 결과는 종료 로그·O9 증거로 사용하지 않는다. 기존 run-owned log policy·journal·cleanup을 재사용하며, 복원·실제 수신 확인 후 별도 O9 실행으로 진행한다. [공식 로그 알림 검증 순서](https://cloud.google.com/logging/docs/alerting/log-based-alerts)
- live driver는 90분 deadline을 사용하고 workflow attempt 120분을 넘기지 않는다. 마지막 30분에는 새 fault나 mutation을 시작하지 않는다. O8 장애 주입 직전에는 두 fault poll, provider 처리 여유와 O9 진입 시간을 포함해 최소 2,940초가 남았는지 다시 확인하고 부족하면 복원한다.
- O9의 12시간 정상 이력과 종료 후 signature 조회는 같은 bounded system-log pagination을 사용한다. 분 단위 helper에 720을 전달하며 이전 43,200은 30일을 조회하던 단위 오류였다. 빈 page에 token이 있어도 계속 읽되 page·누적 entry·scan time·token 반복과 응답 형식을 제한하고 매 조회 전 fault cutoff를 확인한다. 완전한 목록을 얻지 못하면 종료 장애를 주입하지 않는다. [Cloud Logging entries.list](https://docs.cloud.google.com/logging/docs/reference/v2/rest/v2/entries/list)
- 기존 exit42 O9 경로는 정책 생성·read-back 후 300초를 둔 뒤 두 번째 종료를 보낸다. 대기 직전에는 대기 300초와 기존 O9 최소 잔여 시간 300초를 합쳐 검사하고, 대기 후 잔여 시간을 다시 확인한다. 최대 종료 2회와 기존 fault cutoff·복원 여유를 유지하며 부족하면 추가 종료 없이 복원한다. O8의 잔여 시간 검사는 이 추가 대기를 포함한 전체 O9 완료를 보장하지 않는다.
- 종료 fixture는 응답 후 비동기 작업 대신 활성 요청 안에서 `Runtime.halt(42)`를 호출한다. HTTP 500/502/503 또는 제한된 empty/reset 응답은 요청 관측값일 뿐 종료 증거가 아니다. DNS·연결·TLS·인증·redirect·timeout 오류나 이전 202 응답은 거부한다. 두 번째 종료 후에는 같은 revision·signature, 장애 이후 timestamp, 첫 로그와 다른 `insertId`를 가진 실제 system log와 fresh OPEN을 모두 요구한다. 로컬 subprocess의 exit 42 확인은 Cloud Run 로그 제공을 보장하지 않는다.
- `o9-oom-discovery`와 `o9-oom`은 고정 비공개 검증 서비스에서만 OOM fixture를 켠다. 로컬 모드에서는 OOM opt-in이 있어도 경로가 열리지 않는다. OOM scope는 OOM만, 기존 종료 scope는 EXIT만 켜고 `log-delivery`는 둘 다 끄며 이전 flag를 남기지 않는다.
- OOM fixture는 16 MiB direct buffer를 최대 64개 할당해 참조를 유지하고, 각 4 KiB 페이지에 서로 다른 64비트 값을 기록한다. 할당 전마다 단조 시계 기준 10초 예산을 확인한다. validation 이미지의 direct-memory 한도는 1,536 MiB이며 heap은 기존 384 MiB다. 크기·시간 한도나 할당 오류로 살아남으면 실패 코드·할당량·경과 시간을 제한된 JSON으로 남긴다. 경과 시간은 25,000 ms에서 포화되며 그 값은 25초 이상을 뜻한다. 진행 중인 할당의 강제 중단을 보장하지 않으므로 요청·workflow cutoff와 복원이 바깥 한도다. JVM OOME나 HTTP 5xx만으로 container OOM을 판정하지 않는다. [Java direct buffer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html), [컨테이너 메모리 초과 진단](https://cloud.google.com/run/docs/troubleshooting#container-instances-exceed-memory-limits).
- 검증 이미지의 실행 전 filesystem export에서 OS·JRE·앱 파일과 image config를 해시한다. 앱 JAR은 ZIP 시간·정렬 대신 entry 내용으로 비교하며 Docker가 만드는 네 파일(`/.dockerenv`, `/etc/hostname`, `/etc/hosts`, `/etc/resolv.conf`)만 제외한다. Docker가 컨테이너 생성 시 만드는 `/dev/pts`·`/dev/shm` 디렉터리와 내용이 없는 일반 파일 `/dev/console`은 권한·소유자를 포함해 해시한다. 그 외 `/proc`, `/sys`, `/dev` 하위 항목과 실제 장치 파일은 거부한다. 고정 해시만 workflow 로그에 남기고 실제 배포 digest에 연결한다. `o9-oom`은 discovery에서 고정한 applicability 해시와 새 빌드가 같은지 클라우드 인증 전에 확인하고, 불일치하면 push·journal·cloud mutation 전에 중단한다.
- Discovery는 실제 revision의 메모리·CPU·동시성·timeout·scaling·image·volume/mount·비공개 경계를 확인한 뒤 인증된 OOM POST를 한 번만 보낸다. 현재 run의 system log 원본을 제한된 조회로 보존하고 복원하며, payload 문구나 `textPayload` 형식을 미리 가정하지 않는다. 정책·두 번째 fault를 만들지 않고 O9 PASS도 기록하지 않는다. provider 원본에서 메모리 한도 종료를 확인해야 discovery 증거가 성립한다.
- `o9-oom`은 클라우드 인증 전에 D2와 같은 runtime applicability를 확인하고, 고정 contract의 system log·ERROR severity·768 MiB 문구 family와 현재 revision selector로 정책을 생성한다. exact read-back과 기존 OPEN 부재, 300초 진단 대기 뒤 OOM POST를 한 번만 보내며, full byte-cap 응답은 native polling으로만 진행할 수 있다. 현재 revision의 fresh exact native OOM이 없으면 실패한다. 이어서 fresh exact-policy OPEN과 health를 요구하고, 실제 이메일·독립 복원 전에는 receipt를 `PENDING`으로 둔다. 이 검증이 성공해도 확인한 OOM 계열만 입증한다.
- raw provider body는 `RUNNER_TEMP`의 0600 파일에만 두고 summary는 receipt 확인 전 `RECEIPT PENDING`으로 남긴다. receiver-side 증거 없이는 PASS로 올리지 않는다.
- failure에서도 immutable baseline revision의 존재·Ready 확인 → 그 revision으로 traffic 100% 복원 → allowlist template/hash 복원 → private IAM·health/query·scaling read-back → owned-resource cleanup → journal clear 순서를 `always()` 경로로 실행한다. 전체 service export를 덮어쓰지 않는다.
- serving 또는 tagged revision은 삭제하지 않는다. fault revision/image/policy/check는 journal의 exact name·digest·ownership label과 다른 참조 부재가 확인된 경우에만 정리한다.
- ownership이나 restore 상태가 불명확하면 resource와 journal을 유지하고 secret 없는 blocker를 남긴다. 정상 상태와 cleanup read-back이 끝난 뒤 journal을 마지막 etag CAS로 지운다. restore는 build/test/image push를 건너뛴다.
- 실제 private exit signature가 ambiguous하거나 정상 종료와 충돌하면 후속 exit를 보내지 않는다. production service에는 validation main, jar와 `__observability/*` route가 없어야 한다.
- policy helper는 journal 소유 private service와 run-owned policy/check만 변경한다. render는 cloud mutation 없이 수행하고 기존 production/shared policy나 channel을 이름만으로 갱신·삭제하지 않는다.

이전 exit42 검증에서 알림이 발생하지 않은 원인은 확정하지 못했다. enabled·valid 정책의 정확한 filter로 종료 로그를 조회했고, 로그 bucket 라우팅·결제 활성 상태·제외 규칙 부재·정책 생성 API 성공을 확인했다. 300초 대기는 전파 지연 가설을 확인하기 위한 진단이며 공급자의 활성화 보장이 아니다. 후속 log-delivery와 native OOM의 성공으로 이전 실패 원인을 확정하지 않는다. 조회 기간 단위 오류도 incident 미발생 원인으로 확인된 것은 아니다. 내부 notification rule에는 직접 조회하는 준비 상태 API가 없고 공식 LogMatch 예제도 `notificationPrompts`를 생략하므로 field나 filter를 추측으로 변경하지 않는다. [로그 기반 알림](https://cloud.google.com/logging/docs/alerting/log-based-alerts), [정책 변경 전파](https://cloud.google.com/monitoring/alerts/troubleshooting-alerts).

요청 기반 CPU 할당에서 응답 후 작업은 실행이 지연될 수 있어 fixture의 응답 이후 작업을 제거했지만, 이전 exit42 로그 부재의 원인으로 확정하지 않았다. CPU 설정은 유지한다. 확인한 Cloud Run 문서에는 모든 비정상 종료의 exit-code 로그 제공 보장이 없어 exit42 신호 부재 뒤 실제 native OOM을 별도로 검증했다. 파일 크기가 상주 메모리와 같다는 가정을 버리고 direct buffer discovery로 실제 신호를 확보했다. 실패한 실행과 복원 기록을 보존하며, 이미 사용한 누적 OOM 요청 3회 한도를 초기화하거나 추가 discovery를 실행하지 않는다. 지원 문의는 이번 검증의 선행 조건이 아니었다. [CPU 할당](https://cloud.google.com/run/docs/configuring/billing-settings#cpu_allocation_impact), [system logs](https://cloud.google.com/run/docs/logging#system_logs).

### Production permanent policies

Private O7~O9의 provider 결과·실제 수신·독립 복원을 확인한 뒤 정상 production 이미지를 배포했다. Ready, 단일 revision 100% traffic, immutable digest, template, IAM과 공개 smoke를 먼저 고정했으며 production에 장애를 주입하지 않았다.

기존 정책·check의 전체 목록과 승인 채널을 확인한 뒤 `managed_by=vlrgg-server-observability`, `service=vlrgg-query`, `resource_kind` 소유 label로 다음 자원을 순차 생성하고 exact ID·body hash·read-back과 중복 부재를 보호 기록에 남겼다.

- revision 전체를 합산하는 production 5xx 정책
- 현재 정상 배포를 대상으로 한 uptime check
- 생성된 check의 exact ID를 참조하는 uptime 알림 정책
- 검증한 768 MiB native OOM 문구 계열을 production service 전체에 적용하는 log 정책

최종 검증에서 네 자원의 설정, 생성 이후 서로 다른 3개 지역의 정상 값과 HTTP 200, 열린 소유 incident 없음, 마지막 정책 적용 뒤 native HTTP 200 로그와 기존 traffic/template/IAM 보존을 확인했다. 최초 조회는 한 지역만 보였으나 이후 세 지역의 실제 지표가 모인 뒤 통과시켰다. 실제 read-back은 revision/configuration label이 빈 service-level check였다. 이 uptime 증거는 생성한 check와 현재 정상 배포에 한정한다. 다음 배포 때 check 대상과 새 serving revision의 정상 지표를 다시 확인하고, 이전 revision에 고정돼 있으면 새 check→새 check-ID 정책 검증→기존 소유 정책 비활성화·삭제→기존 check 삭제 순으로 교체한다. 현재 증거를 향후 revision의 자동 감시 보장으로 사용하지 않는다. 메모리 한도나 provider 문구가 바뀌면 OOM contract도 다시 검토한다.

5xx·uptime은 OPEN/CLOSED 통지와 열린 incident의 1시간 재통지 간격을 사용한다. Uptime과 OOM log 정책은 데이터가 없으면 30분 뒤 auto-close되며 이를 정상 복구 증거로 대체하지 않는다. OOM log 정책의 알림은 최대 5분에 한 번이고 실제 수신 증거는 OPEN이다. 이 제한은 5분마다 반복 발송한다는 보장이 아니다. Error Reporting은 새 오류·해결 후 재발 알림이며 열린 그룹의 매 요청마다 메일을 보내는 계약이 아니다. [Monitoring 정책 API](https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.alertPolicies), [Error Reporting 알림](https://cloud.google.com/error-reporting/docs/notifications).

변경을 되돌릴 때는 보호 기록의 exact ID와 label/body hash로 이번 소유 자원을 확인하고 정책부터 비활성화·read-back한 뒤 삭제한다. uptime 정책을 check보다 먼저 제거하며, 소유권이 불명확하면 삭제하지 않는다. 기존 공유 channel, Logging routing/bucket, IAM과 serving revision은 보존한다. 정책 변경의 결과가 불명확하면 실제 목록과 ID를 조회해 조정하고 POST를 맹목적으로 재시도하지 않는다.

### Investigating an error

1. Error Reporting에서 production service의 오류 그룹을 열고 대표 이벤트의 `error_code`, 안전한 frame, `serviceContext.version`과 발생 시각을 확인한다. Logs Explorer에서는 아래 필터로 시작해 해당 revision·시간으로 좁힌다.
2. 유효한 `logging.googleapis.com/trace`가 있으면 같은 trace의 request log에서 HTTP status를 확인한다. 없는 trace를 추측해서 연결하지 않는다. Production revision 이름은 `vlrgg-query-r<run_id>-<attempt>`이므로 `serviceContext.version`에서 Actions run/attempt를 찾을 수 있다. 해당 실행과 보호된 run/SHA/digest 대응표로 배포 소스를 확인한다.
3. 수정·배포와 정상 상태를 확인한 뒤 해당 그룹을 해결 상태로 표시한다. 이후 같은 그룹의 재발과 실제 수신을 별도로 확인하며 과거 메일을 새 실행 증거로 재사용하지 않는다.
4. Monitoring에서 채널을 관리하고, 각 정책과 Error Reporting의 별도 알림 설정에 그 채널이 선택돼 있는지 확인한다. 수신자 변경은 보호 설정에서 수행하고 승인된 수신함의 실제 전달을 확인한다. 채널 생성이나 test 메시지만으로 모든 정책의 전달 성공을 판정하지 않는다.

```text
resource.type="cloud_run_revision"
resource.labels.service_name="vlrgg-query"
severity=ERROR
```

안전한 이벤트는 총 frame 32개, cause 깊이 3, frame 후보 검사 128개, 개행 포함 UTF-8 16 KiB와 category별 프로세스당 4건/60초로 제한한다. 원본 exception 문구·HTML·query·token을 추가로 출력하지 않는다. Error Reporting occurrence는 수집된 sample 수이며 실제 실패 요청 수가 아니다. native 요청 지표와 emitted/suppressed 요약을 함께 보고, 프로세스 재시작과 무요청 시 요약 flush 한계를 고려한다.

재현 가능한 로컬 검증:

```sh
./gradlew :server:test :server:build :server:installDist :server:observabilityValidationJar
bash .github/scripts/smoke-observability.sh
bash .github/scripts/test-observability-operations.sh
bash .github/scripts/test-query-production-deploy.sh
```
