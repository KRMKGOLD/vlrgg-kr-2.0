# CI/CD contracts

## Scope

PR과 `main` push는 credential-free CI를 실행한다. 서버와 앱 배포는 검증된 exact `main` SHA에서 수동으로 시작한다. 일반 조회 서버 배포는 완료됐고, 제품 경기 알림의 App Check/FCM/production Firestore/Scheduler는 MVP 이후 Stage 2다.

- 서버 runtime·rollback·비용 중단·#122 복원: [서버 배포 runbook](architecture/server-container-deployment.md)
- 앱 signing·store 배포: [앱 내부 배포](app-release.md)
- Crashlytics 설정·수집: [Crashlytics](app-crashlytics.md)
- 경기 알림 offline/live 경계: [Stage 1.1 contract](architecture/server-fcm-stage1.md)

## CI

`.github/workflows/ci.yml`은 모든 PR과 `main` push에서 다음을 검사한다.

| Job | Checks |
| --- | --- |
| Linux `verify` | deployment script stubs, release contracts, shared Android host tests, Android unit/lint, Firebase config lifecycle, server unit/build/installDist/observability jar, Firestore Emulator suite, packaged observability smoke, changed-lines whitespace |
| macOS `ios` | iOS release/Firebase scripts, shared simulator tests·compile, unsigned Release simulator app |

도구 버전, task 목록과 workflow step은 workflow/build source가 소유한다. 저장소에 ktlint나 Detekt가 추가되기 전에는 존재하지 않는 task를 gate로 만들지 않는다. Stage 1.1의 과거 `app/**` zero-touch 검사는 해당 구현 branch의 증거이며 향후 앱 PR을 막는 규칙이 아니다.

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

`.github/workflows/deploy-server.yml`은 `workflow_dispatch` 전용이다. exact SHA의 성공한 main CI, protected `production` environment와 `CLOUD_RUN_DEPLOY_ENABLED=true`를 확인하고 frozen source를 checkout해 root Docker image를 credential-free로 먼저 만든다. 그 뒤 GitHub OIDC→GCP WIF 인증을 수행하고 cloud push와 mutation을 시작한다.

같은 digest를 private validation service에 먼저 배포하고 Ready/digest/IAM/authenticated smoke를 확인한 뒤 production에 승격한다. 후속 revision 실패 시 시작 때 기록한 serving revision으로 rollback한다. URL, JWT, credential, image path와 raw provider output은 public log·summary·artifact에 남기지 않는다. 상세 순서와 비용 중단은 [배포 runbook](architecture/server-container-deployment.md)을 따른다.

`operation`은 다음 세 값만 허용한다.

- `deploy`: 일반 조회 배포. active/unknown observability journal이 있으면 mutation 전에 중단한다.
- `observability-validate`: private validation overlay만 사용하고 production을 변경하지 않는다.
- `observability-restore`: enable 값과 무관하게 journal 기반 복원만 수행하며 build/push를 하지 않는다.

## App deployment

`.github/workflows/deploy-app-android.yml`과 `deploy-app-ios.yml`은 `workflow_dispatch`와 `main` ref로 제한한다. checkout HEAD, workflow SHA와 frozen source SHA가 일치해야 한다. Android는 해당 main CI attempt의 `verify` job 성공을, iOS는 전체 CI 성공을 요구한다.

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
| #122 observability | local code/workflow/stub checks GREEN; private live validation 실행 착수 | 실수신·incident close·복원·정리 등 전체 완료는 미확인. [현재 검증 상태](architecture/server-container-deployment.md#122-observability-live-runbook) 참조 |

Branch protection은 CI workflow가 제공하는 실제 check 이름만 사용한다. direct push 제한, PR 요구와 최신 branch 상태를 적용하되 존재하지 않는 check를 미리 등록하지 않는다.
