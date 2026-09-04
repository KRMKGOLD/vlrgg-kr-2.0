# CI/CD and Cloud Run delivery direction

- Status: Stage 1.1 credential-free CI implemented, including iOS simulator coverage; Stage 2 live deployment deferred
- Last reviewed: 2026-09-03
- Related: [Server architecture](architecture/server-arch.md), [Stage 1.1 Match notification](architecture/server-fcm-stage1.md)

## Goal and stage boundary

작은 사이드 프로젝트에 맞춰 PR에서는 credential-free 검증만 수행하고, `main` 병합 후 서버 영향 변경만 Cloud Run으로 배포하는 구조를 목표로 한다. `.github/workflows/ci.yml`은 완료된 Stage 1.1 offline gate를 구현하며 deploy workflow는 없다.

Stage 1.1은 실제 Firebase App/GCP project/Cloud Run을 연결하지 않는다. Stage 1.1 구현 PR은 Firestore Emulator와 fake provider를 포함한 offline GREEN까지만 소유한다. 실제 App Check, FCM, production Firestore, Cloud Run과 배포 health/rollback은 Stage 2에서 수행한다.

## Verified repository structure

```text
app/shared       Compose Multiplatform 공통 코드와 Android host/iOS simulator tests
app/androidApp   Android application과 Android Lint/unit-test tasks
app/iosApp       Xcode iOS entry point; Gradle subproject는 아님
core             server와 app/shared가 함께 의존하는 순수 Kotlin 모듈
server           Ktor 3 Netty application
```

`server`는 `core`에 직접 의존한다. 현재 server plugin은 Kotlin JVM, Kotlin Serialization, Ktor plugin이고 `application.mainClass`는 `kr.co.cotton.vlrgg_mobile.ApplicationKt`다. 확인된 server task는 `:server:test`, `:server:build`, `:server:installDist`, `:server:run`이다.

현재 Stage 1.1 구현과 Stage 2 Cloud Run 배포 전 남은 항목은 다음과 같다.

- listener는 `0.0.0.0`과 Cloud Run `PORT`를 지원하며 legacy `VLRGG_SERVER_PORT` fallback 및 packaged `/health` smoke가 검증됐다.
- Stage 1.1 알림 runtime은 Firestore 기반 request-bound 계약으로 교체됐고, 일반 runtime의 production provider·알림 route는 Stage 2까지 disabled/fail-closed다.
- repository root source build의 `:server:installDist` entrypoint를 buildpack에 알려 주는 설정이 없다.
- `.github/workflows/ci.yml`은 존재하지만 deploy workflow와 source-deploy용 ignore/config 파일은 아직 없다.

남은 buildpack entrypoint, deploy workflow 및 실제 Cloud Run 동작은 Stage 2에서 검증한다.

확인된 app task는 다음과 같다.

- KMP Android host: `:app:shared:testAndroidHostTest`
- KMP iOS simulator: `:app:shared:iosSimulatorArm64Test`
- iOS compile: `:app:shared:compileKotlinIosSimulatorArm64`
- Android unit test: `:app:androidApp:testDebugUnitTest`
- Android lint: `:app:androidApp:lintDebug`
- Android build: `:app:androidApp:assembleDebug`

현재 version catalog와 Gradle build에는 ktlint와 Detekt가 적용되어 있지 않으므로 존재하지 않는 task를 CI에 추가하지 않는다.

## Target architecture

```text
Developer
  -> Pull Request
     -> GitHub Actions CI
        -> KMP/Android checks
        -> server unit + Firestore Emulator + build
  -> main merge
     -> GitHub Actions CD (server-impacting paths only)
        -> final server checks
        -> GitHub OIDC -> GCP Workload Identity Federation
        -> Cloud Run source deploy
        -> no-traffic/live health and smoke
        -> traffic switch
  -> Mobile App
```

Match 알림의 별도 흐름은 다음과 같다.

```text
App Target
  -> selected Match subscription
  -> Ktor Target API
  -> Firestore

Cloud Scheduler
  -> OIDC Scheduler route
  -> NotificationSchedulerUseCase
  -> Match state observation
  -> START intent
  -> FCM registration token
  -> that Target only
```

FCM Topic은 공용 공지 요구가 생겼을 때 별도 흐름으로 추가할 수 있으나 현재 Match 알림에는 사용하지 않는다.

## Implemented `ci.yml`

Trigger:

- `pull_request`
- `push` to `main`

Jobs:

1. checkout and Gradle Wrapper validation
2. JDK setup and Gradle dependency/build cache
3. `:app:shared:testAndroidHostTest`
4. `:app:androidApp:testDebugUnitTest :app:androidApp:lintDebug`
5. `:server:test :server:build`
6. Stage 1.1 이후 Firestore Emulator launch/readiness, explicit environment, `:server:firestoreEmulatorTest`, always cleanup
7. macOS runner에서 `:app:shared:iosSimulatorArm64Test :app:shared:compileKotlinIosSimulatorArm64`

macOS iOS job은 Android/server Linux job과 별도로 모든 `pull_request` 및 `main` push에서 실행한다. 따라서 iOS simulator test와 Kotlin/Native iOS compilation은 PR 병합 전과 `main` 반영 후 모두 검증되며, macOS runner 사용 시간은 이 전체 CI trigger 범위에 따라 발생한다.

`ci.yml`은 Node 22, Java 21, pinned `firebase-tools@15.25.1`의 foreground `emulators:exec`로 Firestore를 시작·ready 확인·`:server:test :server:firestoreEmulatorTest :server:build :server:installDist` 실행·cleanup한다. Linux job의 KMP Android host, Android unit/lint, packaged `/health`와 notification-route fail-closed smoke와 macOS job의 iOS simulator test/compile 모두 credential 없이 실행한다. Patch whitespace 검사는 PR에서는 base SHA와 head SHA의 범위, `main` push에서는 event before와 head SHA의 범위를 검사하며, `app/**` zero-touch는 이 Stage 1.1 branch evidence이지 향후 app PR을 막는 permanent CI rule이 아니다.

## Planned `deploy-server.yml`

Stage 2에서만 추가한다. Trigger는 `push` to `main`과 server 영향 path 조건이다.

```text
server/**
core/**
gradle/**
gradle.properties
settings.gradle.kts
build.gradle.kts
gradlew
gradlew.bat
.github/workflows/deploy-server.yml
project.toml
Procfile
.gcloudignore
```

현재 `server`의 직접 공용 모듈 의존성은 `core`이므로 `app/**`와 다른 workflow 전체는 server deploy path에 포함하지 않는다. source deploy 구현 시 위 세 packaging 파일 중 실제 채택한 파일만 유지하되, 채택된 파일은 모두 path filter에 포함한다. 향후 server 의존성이 바뀌면 path도 함께 갱신한다.

Step 순서:

1. checkout
2. JDK와 Gradle cache 설정
3. final `:server:test :server:firestoreEmulatorTest :server:build :server:installDist`
4. GitHub OIDC로 GCP 인증
5. 기존 serving revision 기록
6. commit SHA가 포함된 새 revision을 `--no-traffic`과 고유 tag로 배포하고 tagged revision URL을 기록
7. 기본 service URL이 아니라 tagged revision URL에서 `/health`, Cloud Run invocation auth, App Check/Target auth와 production Firestore safe smoke
8. 성공한 revision으로 traffic 전환
9. traffic 전 실패면 workflow를 실패 처리하고 기존 serving revision의 traffic을 그대로 유지
10. traffic 전환 후 검증 실패면 기록한 이전 revision으로 traffic을 자동 복원하고 workflow를 실패 처리; 자동 복원도 실패하면 이전 revision 이름과 수동 복원 절차만 안전하게 출력

동시 배포는 `concurrency.group`을 Cloud Run service 단위로 고정하고 `cancel-in-progress: false`로 직렬화한다.

No-traffic smoke의 구체 계약은 다음과 같다.

- deploy tag는 commit SHA에서 파생하고 Cloud Run이 반환한 tagged revision URL만 smoke 대상으로 사용한다. default service URL로 대체하지 않는다.
- service가 private이면 deploy SA의 Cloud Run ID token을 `X-Serverless-Authorization`에 넣고, `aud`는 tag URL이 아니라 base service URL로 고정한다. 애플리케이션의 `Authorization: Target ...` header와 Cloud Run IAM header를 덮어쓰지 않는다. public access를 선택하면 이 IAM header 단계만 생략한다.
- `/health` 성공 뒤 Stage 2가 별도 ADR로 확정한 smoke credential source가 short-lived App Check evidence와 non-production disposable registration value를 just-in-time으로 제공한다. 허용 후보는 권한을 제한한 Secret Manager 또는 전용 test-client broker이며, 공급 경로가 구현되지 않은 상태에서 이 smoke를 GREEN으로 간주하거나 traffic을 자동 전환하지 않는다.
- disposable Target을 등록하고 응답의 Target ID와 one-time Target Secret을 job memory의 masked value로만 보관한다. 같은 Target auth로 Target read, expected revision을 사용한 registration value 교체, 재조회까지 수행해 production Firestore create/read/update를 확인하되 실제 경기 구독이나 발송 대상에는 포함하지 않는다.
- raw App Check token, smoke credential, Target Secret과 registration value는 GitHub repository/environment secret·variable, artifact, cache, step output 또는 log에 저장하지 않는다. source가 Secret Manager이면 WIF principal의 해당 secret version accessor 권한만 허용하고, broker이면 short-lived response만 job memory에서 사용한다.
- 성공·실패와 무관한 cleanup step에서 Target을 revoke하고 traffic tag를 제거한다. deploy gate는 authenticated revoke 성공까지만 관찰하고, revoked Target이 sendable query에서 제외되는 불변식은 production repository live integration test가 별도로 증명한다. cleanup 실패 시 token/secret이 아닌 Target ID와 revision만 남겨 workflow를 실패시키고 제한된 운영 로그로 수동 cleanup을 추적한다.

실제 FCM device-display smoke는 per-deploy 자동 gate와 분리해 traffic 전환 후 current serving service URL을 대상으로 수행하는 Stage 2 release/manual gate다. 테스트 앱 설치가 생성한 fresh FCM registration token과 App Check evidence로 disposable Target과 START 구독을 만들고 한 건의 수신을 확인한 뒤 즉시 revoke한다. 기기의 registration token은 테스트 앱의 platform secure storage와 해당 실행의 masked memory 밖에 보존하지 않으며, 이 수신 검증을 완료하기 전에는 “실제 FCM GREEN”으로 기록하지 않는다.

## Branch protection direction

CI workflow가 안정된 뒤 `main` Ruleset에 다음을 적용한다.

- 직접 push 제한과 PR 병합 요구
- KMP/Android/server CI를 required checks로 지정
- stale base에서 통과한 결과를 막기 위해 최신 branch 상태 요구
- 사이드 프로젝트 초기 review 수는 0 또는 1로 시작하되 CI 우회는 허용하지 않음
- deploy workflow는 PR에서 실행하지 않고 `main` 병합 commit에서만 실행

문서 PR이나 workflow 이름이 아직 없는 상태에서 존재하지 않는 required-check 이름을 미리 등록하지 않는다.

## Source deploy and runtime gates

Cloud Run은 Dockerfile 없이 repository root를 source로 전달하는 방식을 먼저 검증한다.

```bash
gcloud run deploy "$SERVICE_NAME" \
  --source . \
  --region asia-northeast3
```

repository root가 필요한 이유는 `server`가 `core`와 root Gradle/version catalog/wrapper에 의존하기 때문이다. `server/`만 source context로 전달하면 독립 빌드가 되지 않는다.

Source deploy 성공 조건:

- repository root의 `project.toml`에 `GOOGLE_RUNTIME_VERSION=21`, `GOOGLE_GRADLE_BUILD_ARGS=clean :server:installDist --no-daemon`, `GOOGLE_ENTRYPOINT=./server/build/install/server/bin/server`를 고정하고 source build log에서 세 값의 적용을 확인함
- Buildpacks가 root Gradle wrapper에 위 build args를 전달하고 `:server:installDist`가 생성한 launcher를 실행함
- server가 `0.0.0.0`에 bind하고 Cloud Run `PORT`를 사용함
- `/health`가 external credential과 무관하게 응답함
- `.gcloudignore`와 `gcloud meta list-files-for-upload` 결과가 Gradle wrapper, root settings/version catalog, `server`, `core`, 선택한 source deploy config는 포함하고 `.git`, `.gradle`, 모든 `**/build`, IDE 설정, `.env*`, Service Account JSON, Firebase platform config, token/secret 파일, local-only config, runtime log와 local DB 파일은 제외함

`project.toml`과 `.gcloudignore`는 source deploy의 필수 계약이다. `GOOGLE_ENTRYPOINT`가 실제 빌드 환경에서 적용되지 않을 때만 같은 launcher를 지정한 root `Procfile`을 대안으로 검증한다. 이 방법을 검증한 뒤에도 buildpack이 멀티모듈 entrypoint를 안정적으로 실행하지 못한다는 build log가 있을 때만 shadowJar 또는 Dockerfile ADR을 작성한다. Dockerfile은 기본 선택이 아니다.

## GCP authentication direction

장기 Service Account JSON key를 GitHub Secret에 저장하지 않는다.

```text
GitHub Actions OIDC
  -> Workload Identity Pool/Provider
  -> repository/ref attribute condition
  -> deploy-only Service Account impersonation
```

Stage 2 bootstrap에서 최소 다음 API를 검토·활성화한다.

- `run.googleapis.com`
- `cloudbuild.googleapis.com`
- `artifactregistry.googleapis.com`
- `iamcredentials.googleapis.com`
- `sts.googleapis.com`
- `firestore.googleapis.com`
- `cloudscheduler.googleapis.com`
- FCM에 필요한 Firebase/Google API는 당시 공식 Admin SDK 문서로 재확인

GitHub repository와 `main` ref 또는 protected environment를 provider attribute condition으로 제한한다. GitHub에는 provider resource name, deploy Service Account email, GCP project ID, region, Cloud Run service name을 repository/environment variable로 두며 JSON private key는 두지 않는다.

정확한 IAM role은 Stage 2에서 배포 방식과 runtime identity를 기준으로 positive/negative test와 함께 확정한다. 넓은 Owner/Editor는 사용하지 않고 deploy SA, Cloud Build execution, runtime SA, Scheduler invocation 역할을 분리한다.

## Cloud Run cost and safety defaults

- region: `asia-northeast3`
- min instances: `0`
- max instances: `1`
- memory: `512Mi`부터 검증
- billing: request-based
- local filesystem/in-memory state: 영속 저장소로 사용 금지
- database: Match 알림 state에 한정한 Firestore
- public access: app API와 Scheduler route의 권한 설계가 완료된 뒤 결정

JVM/Ktor cold start는 허용하되 `/health`와 실제 API latency를 Stage 2에서 측정한다. min instances를 비용 때문에 1로 올리지 않는다.

## Rollback

Cloud Run revision을 rollback 단위로 사용한다. 새 revision을 no-traffic로 배포해 tagged revision URL에서 smoke하는 동안에는 기존 serving revision이 100% traffic을 유지한다. 이 pre-traffic 단계가 실패하면 새 revision으로 전환하지 않고 workflow를 실패 처리한다.

smoke 성공 뒤 새 revision으로 traffic을 전환하고 post-switch health를 다시 확인한다. 이 검증이 실패하면 workflow가 배포 전에 기록한 이전 revision으로 traffic을 자동 복원한 뒤 실패한다. 자동 복원 명령도 실패한 경우에만 같은 이전 revision을 대상으로 수동 복원하며, 별도 `workflow_dispatch` rollback 입력은 첫 운영 배포 후 반복 필요성을 보고 추가한다.

초기에는 별도 복잡한 blue-green 시스템을 만들지 않고 commit SHA를 revision suffix/label에 연결한다.

## Stage evidence matrix

| Evidence | Stage 1.1 | Stage 2 |
| --- | --- | --- |
| Server unit/build/installDist | GREEN — 2026-07-31 | final rerun |
| Firestore SDK + Emulator | GREEN — 2026-07-31 | production smoke |
| Fake App Check/FCM | GREEN — 2026-07-31 | replaced by real adapters |
| App Android/iOS Firebase integration | NOT RUN — Stage 2 | required |
| Real App Check/FCM | NOT RUN — Stage 2 | required |
| Production Firestore/IAM/index | NOT RUN — Stage 2 | required |
| Cloud Run/Scheduler/WIF/CD | NOT RUN — Stage 2 | required |
| Live health/traffic/rollback | NOT RUN — Stage 2 | required |
