# CI/CD contracts

## Scope

PR과 `main` push는 credential-free CI를 실행한다. 서버와 앱 배포는 검증된 exact `main` SHA에서 수동으로 시작한다. 일반 조회 서버 배포는 완료됐고, 제품 경기 알림의 App Check/FCM/production Firestore/Scheduler는 MVP 이후 Stage 2다.

- 서버 runtime·rollback·비용 중단·#122 복원: [서버 배포 runbook](architecture/server-container-deployment.md)
- 앱 signing·store 배포: [앱 내부 배포](app-release.md)
- Crashlytics 설정·수집: [Crashlytics](app-crashlytics.md)
- 경기 알림 offline/live 경계: [Stage 1.1 contract](architecture/server-fcm-stage1.md)

## CI

`.github/workflows/ci.yml`은 모든 PR과 `main` push에서 실행한다. workflow-level path filter를 두지 않고 `changes`가 필요한 플랫폼을 선택한다.

| Job | Checks |
| --- | --- |
| Linux `changes` | CI 선택·배포 증명 회귀 테스트, 전체 변경 범위 기반 플랫폼 선택 |
| Linux `android` | release contracts, shared Android host tests, Android unit/lint, Firebase config lifecycle |
| Linux `server` | deployment script stubs, server unit/build/installDist/observability jar, Firestore Emulator suite, packaged observability smoke |
| macOS `ios` | iOS release/Firebase scripts, shared simulator tests·compile, unsigned Release simulator app |
| Linux `verify` | `always()`로 detector·선택된 job 성공과 의도한 skip 확인, changed-lines whitespace |

단일 경로 규칙은 `scripts/ci/ci_contract.rb`가 소유한다. `server/src/**`는 server, Android source/scripts는 android, iOS 디렉터리는 ios, shared source는 android+ios를 실행한다. core, 각 모듈의 Gradle build script, root/settings/Gradle wrapper·catalog·JDK 설정, 공통 CI/script와 알 수 없는 경로는 모두 실행한다. 서버 Docker build도 Android/shared Gradle 설정을 읽으므로 app 디렉터리 전체를 앱 전용으로 취급하지 않는다. 문서 Markdown만 바뀌면 세 build job을 생략할 수 있다.

PR은 base와 PR head의 merge-base부터 PR head까지, main push는 event의 `before`부터 `after`까지 비교한다. rename의 이전·이후 경로와 삭제를 포함하며 diff·identity를 확인할 수 없으면 모두 실행한다. PR 빌드는 기존처럼 GitHub merge checkout을 사용한다. `verify`는 detector 실패, 선택된 job의 실패·취소·예상하지 못한 skip을 거부한다. detector가 명시적으로 제외한 job만 skip을 인정하며 aggregate 성공 자체는 배포 증거가 아니다.

도구 버전, task 목록과 workflow step은 workflow/build source가 소유한다. 저장소에 ktlint나 Detekt가 추가되기 전에는 존재하지 않는 task를 gate로 만들지 않는다. Stage 1.1의 과거 `app/**` zero-touch 검사는 해당 구현 branch의 증거이며 향후 앱 PR을 막는 규칙이 아니다. 앱/iOS 최종 완료 판정은 최종 exact commit의 해당 플랫폼 CI 성공을 확인한다.

### Skipped platform validation before deployment

배포는 exact main SHA에 대한 최신 CI run의 최신 attempt에서 해당 `server`, `android`, `ios` job 하나가 실제 완료·성공해야 한다. push와 main `workflow_dispatch`를 함께 조회하고 가장 큰 `run_number`를 선택한다. 최신 실행에서 대상이 skip되면 이전 성공으로 대체하지 않는다. 다른 플랫폼만 수동 검증한 경우도 같으므로 여러 플랫폼 배포에는 `target=all`을 사용한다.

대상이 생략됐다면 현재 main의 exact SHA로 같은 CI workflow를 명시 실행한다.

```sh
source_sha=$(gh api repos/KRMKGOLD/vlrgg-kr-2.0/git/ref/heads/main --jq '.object.sha')
gh workflow run ci.yml --ref main -f target=server -f source_sha="$source_sha"
```

`target`은 `server`, `android`, `ios`, `all`이며 source SHA와 dispatch의 main SHA가 다르면 실패한다. 실행 완료 후 배포 source SHA와 CI run/attempt가 일치하는지 확인한다. 선택 검증은 production 작업을 하지 않으며 skipped job을 성공 증거로 바꾸지 않는다. 수동 검증은 새 patch가 없으므로 과거 전체 source의 whitespace를 재검사하지 않는다. PR/main push의 patch 검사는 유지한다.

### Platform CI and deployment concurrency

각 CI 플랫폼 job과 대응 deploy job은 `ci-deploy-${{ github.ref }}-android|server|ios` 그룹을 공유하고 `cancel-in-progress: false`를 사용한다. 배포의 최종 CI 증명 조회부터 인증·signing·배포·복원·cleanup까지 같은 job 잠금 안에서 수행하므로, 조회 직후 같은 플랫폼의 CI가 새로 실행되어 증명을 바꾸는 race를 막는다. server CI job이 완료되면 server 잠금이 풀리며 실행 중인 iOS나 최종 `verify`를 기다리지 않는다. main과 PR ref도 서로 다른 그룹이다.

기존 CI workflow concurrency와 플랫폼별 배포 workflow mutex(`cloud-run-query-production` 포함)는 별도 key로 유지한다. CI·배포 전체를 하나의 그룹으로 묶지 않는다. 앱의 초기 preflight는 플랫폼 잠금 밖에서 빠르게 실패할 수 있고, 최종 검증은 environment 승인 뒤 잠긴 deploy job 안에서 다시 수행한다.

새 run/job의 등록·대기 자체는 막지 않는다. 최종 검증 전에 더 최신 run/attempt가 생겼는데 해당 플랫폼 job이 queued·누락·실패라면 즉시 거부하고, 현재 배포가 잠근 job을 기다리거나 이전 성공으로 대체하지 않는다. 최종 검증 후 등록된 같은 플랫폼의 CI는 배포 job 종료까지 실행을 기다린다. 기본 concurrency queue는 running 하나와 pending 하나를 유지하며 새 요청이 기존 pending을 대체할 수 있다. `cancel-in-progress: false`는 이미 실행 중인 job을 보호한다. environment 승인과 잠금 획득 순서는 보장되지 않아 승인 대기가 플랫폼 CI를 지연시킬 수 있다. 잠금은 같은 그룹을 사용하는 repository Actions job에 적용되며 local/Console 작업을 직렬화하지 않는다. [GitHub job concurrency](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#jobsjob_idconcurrency), [deployment concurrency](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/control-deployments#using-concurrency).

### G0 synthetic benchmark

보호 로직의 regression 자료가 필요할 때만 opt-in benchmark를 실행한다.

```sh
G0_BENCH_REPORT_PATH=/tmp/vlrgg-g0-benchmark.txt \
  ./gradlew :server:test \
  --tests 'kr.co.cotton.vlrgg_mobile.benchmark.G0LocalFakeUpstreamBenchmarkTest' \
  --rerun-tasks
```

이 검사는 loopback fake upstream을 쓰는 synthetic 측정이며 production network, VLR.GG upstream, Cloud Run 성능을 포함하지 않는다. 기본 test에서는 skip되고 report는 커밋하지 않는다. 결과는 성능 보장이 아니라 같은 환경에서의 회귀 참고값이다.

### Protected route load benchmark

실제 Ktor/Netty와 Matches route/service/parser/mapper, 보호·직렬화 경로의 opt-in 부하 검사는 별도다.

```sh
PROTECTED_ROUTE_LOAD_REPORT_PATH=/tmp/vlrgg-protected-load.properties \
  ./gradlew :server:test --tests '*ProtectedRouteLoadBenchmarkTest' --rerun-tasks
```

1초 지연 fixture transport를 쓰며 외부 VLR.GG, production CIO/TLS/network, Linux cgroup, Cloud Run autoscaling과 provider 비용을 측정하지 않는다. 환경 변수가 없으면 기본 test에서 skip되고 report는 커밋하지 않는다. 결과는 process별 보호 회귀 자료이며 production 성능·memory·비용 보장이 아니다.

## Query server deployment

`.github/workflows/deploy-server.yml`은 `workflow_dispatch` 전용이다. exact main SHA의 최신 CI run/attempt에서 `server` job 하나가 완료·성공했는지 확인하고, protected `production` environment와 `CLOUD_RUN_DEPLOY_ENABLED=true`를 확인한 뒤 frozen source의 root Docker image를 credential-free로 먼저 만든다. 공통 CI verifier는 전체 run/job inventory, `run_id`, `run_attempt`, `head_sha`, workflow path와 main event를 확인하고 같은 run을 다시 읽어 조회 중 실행·attempt 변경도 거부한다. Android/iOS 상태는 서버 배포 gate가 아니다. 그 뒤 GitHub OIDC→GCP WIF 인증을 수행하고 cloud push와 mutation을 시작한다.

같은 digest를 private validation service에 먼저 배포하고 Ready/digest/IAM/authenticated smoke를 확인한 뒤 production에 승격한다. 후속 revision 실패 시 시작 때 기록한 serving revision으로 rollback한다. URL, JWT, credential, image path와 raw provider output은 public log·summary·artifact에 남기지 않는다. 상세 순서와 비용 중단은 [배포 runbook](architecture/server-container-deployment.md)을 따른다.

`operation`은 다음 세 값만 허용한다.

- `deploy`: 일반 조회 배포. active/unknown observability journal이 있으면 mutation 전에 중단한다.
- `observability-validate`: private validation overlay와 bounded live driver만 사용하고 production을 변경하지 않는다.
- `observability-restore`: enable 값과 무관하게 journal 기반 복원만 수행하며 build/push를 하지 않는다.

`validation_scope`는 validate에서만 `all`, uptime·종료용 `o8-o9`, 종료 전용 `o9`, 장애 없는 알림 전달 진단용 `log-delivery`, 비공개 컨테이너 OOM의 실제 system log를 한 번 수집하는 `o9-oom-discovery`를 허용한다. `LOG_DELIVERY`와 OOM discovery는 별도 결과이며 O9 성공으로 계산하지 않는다. Discovery에는 정책 생성이나 두 번째 fault가 없고, 실제 로그를 기반으로 후속 알림 검증 코드를 리뷰한 뒤 진행한다. 축소 범위도 새 private revision·journal·preflight와 `always()` 복원·정리를 사용하고, 생략한 단계는 해당 실행의 PASS로 표시하지 않는다. 상세 paging·fault cutoff 계약은 [배포 runbook](architecture/server-container-deployment.md#private-validation-and-recovery)이 소유한다.

## App deployment

`.github/workflows/deploy-app-android.yml`과 `deploy-app-ios.yml`은 `workflow_dispatch`와 `main` ref로 제한한다. checkout HEAD, workflow SHA와 frozen source SHA가 일치해야 한다. Android는 최신 exact-SHA main CI attempt의 `android`, iOS는 `ios` job 성공을 요구한다. 서버와 동일한 CI verifier를 사용하며 다른 플랫폼이나 aggregate 결과로 대체하지 않는다.

Android는 Play internal, iOS는 TestFlight로 배포한다. 플랫폼별 environment, WIF 또는 App Store Connect 인증, signing input, uncertain upload 처리와 cleanup은 [앱 내부 배포 계약](app-release.md)이 소유한다. 실제 URL, signing 자료, AAB/IPA와 raw Fastlane output은 artifact나 Release에 올리지 않는다.

## Notification Stage 2 gate

제품 경기 알림 gate는 일반 조회 release와 분리한다. 다음이 구현·검증되기 전에는 notification production을 GREEN으로 기록하지 않는다.

- short-lived App Check evidence와 disposable registration address를 제공하는 Stage 2 credential source
- production Firestore/IAM/index와 Target create/read/update/revoke smoke
- 실제 FCM provider error mapping과 fresh device-display smoke
- OIDC Scheduler route와 Cloud Scheduler resource

Target Secret, registration token과 App Check token은 repository/environment variable, artifact, cache, step output과 log에 저장하지 않는다. smoke Target은 성공·실패와 무관하게 revoke한다. cleanup 실패 시 secret이 아닌 Target ID와 release identifier로 제한된 운영 기록만 남기고 gate를 실패시킨다. 실제 기기 수신 전에는 FCM GREEN으로 기록하지 않는다.

## Release evidence

문서나 workflow 존재만으로 배포 성공을 주장하지 않는다. 각 release는 exact SHA/CI, immutable artifact, provider receipt, promotion 또는 rollback, smoke와 cleanup 증거를 연결한다.

| Area | Current evidence | Remaining gate |
| --- | --- | --- |
| Stage 1.1 notification server | Emulator contract/concurrency/security tests, build/installDist, notification-disabled packaged smoke GREEN | App·real Firebase/GCP/Cloud Run은 `NOT RUN — Stage 2` |
| Query server | private validation, production promotion, rollback, public smoke와 cost-stop recovery PASS | 실제 invoice·Budget/Monitoring receipt·Spend cap 미확인 |
| App release process | [#117](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/117): Android `0.1.0(2)` Actions 업로드·Play 업데이트·실기기 정상 동작·cleanup 확인 완료 | iOS account/signing/TestFlight는 [#139](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/139)에서 보류 |
| Crashlytics | Android fatal·ANR, iOS fatal·dSYM 실수신 확인 | store release 안정성은 별도 운영 관측 |
| #122 observability | private O3~O8 및 log-delivery의 실제 수신·독립 복원 확인; PR #152의 요청 수정 후에도 native exit42 로그가 없어 실패·복원 | O9 `FAIL`; OOM discovery·후속 실제 알림 검증과 production 적용 대기. OOM `NOT RUN`. [현재 검증 상태](architecture/server-container-deployment.md#122-observability-live-runbook) 참조 |

Branch protection의 required check는 항상 실행되는 `verify`를 권장한다. 이 문서와 workflow는 repository 설정을 변경하지 않으므로 GitHub에서 실제 보호 설정을 별도로 확인해야 한다. 선택적으로 skip되는 플랫폼 job을 개별 필수 check로 등록하는 대신 `verify`가 선택 결과를 검사하게 한다. direct push 제한·PR 요구·최신 branch 상태도 실제 repository 설정으로 적용한다.
