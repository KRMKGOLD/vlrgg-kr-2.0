# CI/CD and Cloud Run delivery direction

- Status: Stage 1.1 CI planned; Stage 2 live deployment deferred
- Last reviewed: 2026-07-31
- Related: [Server architecture](architecture/server-arch.md), [Stage 1.1 Match notification](architecture/server-fcm-stage1.md)

## Goal and stage boundary

작은 사이드 프로젝트에 맞춰 PR에서는 credential-free 검증만 수행하고, `main` 병합 후 서버 영향 변경만 Cloud Run으로 배포하는 구조를 목표로 한다. 이 문서 변경 시점에는 `.github/workflows/`가 없으며 CI/CD는 구현되지 않았다.

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

현재 Cloud Run 배포 전 수정이 필요한 항목은 다음과 같다.

- listener는 `0.0.0.0`을 기본으로 사용하지만 port는 Cloud Run `PORT`가 아니라 `VLRGG_SERVER_PORT`만 읽는다.
- Stage 1 알림 runtime은 local H2 file, process-owned loop와 enabled 시 ADC/Firebase lifecycle에 의존한다.
- repository root source build의 `:server:installDist` entrypoint를 buildpack에 알려 주는 설정이 없다.
- `.github/workflows/`와 source-deploy용 ignore/config 파일이 없다.

Stage 1.1 구현은 첫 두 항목을 offline-safe runtime으로 교체하고 `PORT`/packaged health를 검증한다. buildpack 및 실제 Cloud Run 동작은 Stage 2에서 검증한다.

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

## Planned `ci.yml`

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

iOS 검증은 macOS runner 비용 때문에 `app/shared/**`, `app/iosApp/**`, `core/**`, Gradle 설정 변경에 한해 별도 job 또는 `main`/수동 workflow로 운영한다. 최소 task는 `:app:shared:compileKotlinIosSimulatorArm64`; simulator test를 gate로 선택하면 `:app:shared:iosSimulatorArm64Test`를 사용한다.

Stage 1.1 문서 PR은 workflow 파일을 추가하지 않는다. 구현 PR에서 실제 `firestoreEmulatorTest` task와 emulator bootstrap이 생긴 뒤 CI를 함께 연결한다.

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
```

현재 `server`의 직접 공용 모듈 의존성은 `core`이므로 `app/**`는 server deploy path에 포함하지 않는다. 향후 server 의존성이 바뀌면 path도 함께 갱신한다.

Step 순서:

1. checkout
2. JDK와 Gradle cache 설정
3. final `:server:test :server:firestoreEmulatorTest :server:build :server:installDist`
4. GitHub OIDC로 GCP 인증
5. 기존 serving revision 기록
6. commit SHA가 포함된 새 revision을 no-traffic로 배포
7. `/health`, App Check/Target auth, production Firestore, disposable FCM smoke
8. 성공한 revision으로 traffic 전환
9. 실패하면 workflow 실패 및 기존 serving revision 유지/복원

동시 배포는 `concurrency.group`을 Cloud Run service 단위로 고정하고 `cancel-in-progress: false`로 직렬화한다.

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

- JDK 21 환경에서 root Gradle wrapper가 실행됨
- `:server:installDist`가 생성하는 정확한 entrypoint를 buildpack이 실행함
- server가 `0.0.0.0`에 bind하고 Cloud Run `PORT`를 사용함
- `/health`가 external credential과 무관하게 응답함
- repository upload manifest가 secret/local DB/build output을 제외함

필요하면 `project.toml`, `Procfile`, `.gcloudignore`로 build/run command를 명시한다. 이 방법을 검증한 뒤에도 buildpack이 멀티모듈 entrypoint를 안정적으로 실행하지 못한다는 build log가 있을 때만 shadowJar 또는 Dockerfile ADR을 작성한다. Dockerfile은 기본 선택이 아니다.

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

Cloud Run revision을 rollback 단위로 사용한다. 새 revision을 no-traffic로 배포하고 smoke 성공 후 traffic을 전환하면 health 실패가 기존 serving revision을 훼손하지 않는다. traffic 전환 후 문제가 생기면 기록한 이전 revision으로 수동 복원한다.

초기에는 별도 복잡한 blue-green 시스템을 만들지 않는다. commit SHA를 revision suffix/label에 연결하고, 자동 deploy workflow와 별도로 `workflow_dispatch` rollback 입력을 추가할지는 첫 운영 배포 후 결정한다.

## Stage evidence matrix

| Evidence | Stage 1.1 | Stage 2 |
| --- | --- | --- |
| Server unit/build/installDist | GREEN required | final rerun |
| Firestore SDK + Emulator | GREEN required | production smoke |
| Fake App Check/FCM | GREEN required | replaced by real adapters |
| App Android/iOS Firebase integration | NOT RUN — Stage 2 | required |
| Real App Check/FCM | NOT RUN — Stage 2 | required |
| Production Firestore/IAM/index | NOT RUN — Stage 2 | required |
| Cloud Run/Scheduler/WIF/CD | NOT RUN — Stage 2 | required |
| Live health/traffic/rollback | NOT RUN — Stage 2 | required |
