# CI/CD delivery direction (provider selection pending)

- Status: Stage 1.1 credential-free CI implemented; #52 public read deployment preparation in progress; notification production deployment deferred
- Last reviewed: 2026-09-06
- Related: [Server architecture](architecture/server-arch.md), [Stage 1.1 Match notification](architecture/server-fcm-stage1.md)

## Goal and stage boundary

작은 사이드 프로젝트에 맞춰 PR에서는 credential-free 검증만 수행하고, `main` 병합 후 서버 영향 변경만 **선정된 provider**로 배포하는 구조를 목표로 한다. `.github/workflows/ci.yml`은 완료된 Stage 1.1 offline gate를 구현하며 deploy workflow는 없다. provider, packaging 방식, public host와 base URL은 아직 확정하지 않았다.

Stage 1.1은 실제 Firebase App/production provider를 연결하지 않는다. Stage 1.1 구현 PR은 Firestore Emulator와 fake provider를 포함한 offline GREEN까지만 소유한다. 실제 App Check, FCM, production Firestore와 원격 배포 health/rollback은 Stage 2에서 수행한다.

#52는 Stage 2 중 **일반 조회 서버와 설치 앱 배포**를 먼저 진행한다. [공개 API 보호 계약](architecture/server-public-api-protection.md)에 따라 로그인·앱 진위 검증·FCM·production Firestore 없이 조회 서버를 준비한다. 아래 알림 관련 App Check/Target/Firestore/FCM smoke는 알림 기능을 production에 연결할 때의 별도 gate이며 #52 조회 배포의 선행 조건이 아니다. 기존 Firestore Emulator CI는 유지한다.

#52 공개 전에는 일반 조회·과부하 보호·health 200·notification 404, 비용 중단과 rollback을 검증한다. 이후 Android/iOS 서명 앱의 실제 설치와 외부망 조회·수동 재시도 증거를 수집한다. 계정·배포·기기 증거 없이 이 gate를 통과한 것으로 기록하지 않는다.

## Verified repository structure

```text
app/shared       Compose Multiplatform 공통 코드와 Android host/iOS simulator tests
app/androidApp   Android application과 Android Lint/unit-test tasks
app/iosApp       Xcode iOS entry point; Gradle subproject는 아님
core             server와 app/shared가 함께 의존하는 순수 Kotlin 모듈
server           Ktor 3 Netty application
```

`server`는 `core`에 직접 의존한다. 현재 server plugin은 Kotlin JVM, Kotlin Serialization, Ktor plugin이고 `application.mainClass`는 `kr.co.cotton.vlrgg_mobile.ApplicationKt`다. 확인된 server task는 `:server:test`, `:server:build`, `:server:installDist`, `:server:run`이다.

현재 Stage 1.1 구현과 Stage 2 provider 선택·배포 전 남은 항목은 다음과 같다.

- listener는 `0.0.0.0`과 platform `PORT`를 지원하며 legacy `VLRGG_SERVER_PORT` fallback 및 packaged `/health` smoke가 검증됐다.
- Stage 1.1 알림 runtime은 Firestore 기반 request-bound 계약으로 교체됐고, 일반 runtime의 production provider·알림 route는 Stage 2까지 disabled/fail-closed다.
- 선택 provider의 artifact/source packaging 방식과 `:server:installDist` entrypoint 계약이 아직 없다.
- `.github/workflows/ci.yml`은 존재하지만 provider 선택 뒤 추가할 deploy workflow와 packaging config는 아직 없다.

남은 packaging entrypoint, deploy workflow 및 실제 provider 동작은 Stage 2에서 검증한다.

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
        -> selected provider's supported deployment identity
        -> selected packaging / isolated candidate release when supported
        -> read-only query release gates
        -> controlled promotion or rollback
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

Stage 2에서만 추가한다. Trigger는 `push` to `main`과 server 영향 path 조건이며, provider를 선택하고 최소 권한·비용·rollback 계약을 검증한 뒤에만 구현한다.

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
<selected-provider packaging/config files>
```

현재 `server`의 직접 공용 모듈 의존성은 `core`이므로 `app/**`와 다른 workflow 전체는 server deploy path에 포함하지 않는다. 선택한 packaging 파일만 path filter에 포함하며, provider가 source deploy를 쓰지 않으면 source-deploy config를 추가하지 않는다. 향후 server 의존성이 바뀌면 path도 함께 갱신한다.

### #52 read-only query deployment path

이 경로는 #52의 일반 조회 공개에만 적용한다. App Check, Target, production Firestore, FCM은 이 경로의 선행 조건이 아니며 기존 credential-free Firestore Emulator CI를 변경하지 않는다.

Deploy identity는 provider가 지원할 때 short-lived identity를 우선한다. 지원하지 않는 provider에서는 그 provider가 지원하는 최소 권한의 narrowly scoped credential을 사용하며, 모든 provider에 Workload Identity Federation이 있다고 가정하지 않는다.

1. checkout
2. JDK와 Gradle cache 설정
3. final `:server:test :server:firestoreEmulatorTest :server:build :server:installDist`
4. 선택 provider가 지원하면 short-lived, least-privilege deployment identity로, 지원하지 않으면 그 provider가 지원하는 최소 권한의 narrowly scoped credential로 candidate artifact 또는 release를 준비하고 현재 serving release를 기록
5. provider가 isolated candidate endpoint를 지원하면 그 endpoint에서, 지원하지 않으면 provider가 보장하는 가장 작은 비공개 범위에서 `/health` 200과 notification route 404를 확인
6. [공개 API 보호 계약](architecture/server-public-api-protection.md)의 query protection을 원격 환경에서 확인한다. 여기에는 request/upstream admission의 거절 응답, 안전한 오류 경계, upstream fetch 비용 제한이 포함된다.
7. provider별 공개 호출 차단·진행 작업 drain·재기동 방지·저장/build/log 비용을 포함한 cost-stop 절차를 실제로 실행하고 복구 전후 상태를 기록
8. promotion 전 실패는 candidate를 공개하지 않고 workflow를 실패 처리한다. promotion 후 health 또는 query protection 검증이 실패하면 기록한 이전 serving release로 rollback하고 workflow를 실패 처리한다.
9. 서명된 Android와 iOS 앱을 실제 기기에 fresh install한 뒤 외부망 조회와 Busy 수동 재시도까지 확인한다. 계정·원격 환경·기기 증거가 없으면 이 gate를 GREEN으로 기록하지 않는다.

동시 배포는 선택 provider의 application/service 단위 `concurrency.group`으로 고정하고 `cancel-in-progress: false`로 직렬화한다. candidate endpoint, promotion, traffic 같은 용어와 구현은 provider가 지원하는 배포 모델에 맞춰 선택 후 확정한다.

### Future notification release gate

Match notification을 production에 연결하는 별도 release/manual gate다. #52 read-only query deployment path의 통과 조건도, 일반 조회 release의 promotion 조건도 아니다.

- Stage 2 ADR이 확정한 just-in-time credential source가 short-lived App Check evidence와 non-production disposable registration value를 제공한다. source가 구현되기 전에는 이 gate를 GREEN으로 간주하지 않는다.
- disposable Target을 등록하고 Target ID와 one-time Target Secret은 실행 메모리의 masked value로만 보관한다. 같은 Target auth로 read, expected revision을 이용한 registration value 교체, 재조회를 수행해 production Firestore create/read/update를 확인하되 실제 경기 구독이나 발송 대상에는 포함하지 않는다.
- raw App Check token, smoke credential, Target Secret, registration value는 GitHub repository/environment secret·variable, artifact, cache, step output 또는 log에 저장하지 않는다. 선택 provider의 secret store 또는 test-client broker를 쓰더라도 short-lived 값은 job memory에서만 사용하고 권한은 필요한 단일 read path로 제한한다.
- 성공·실패와 무관한 cleanup에서 Target을 revoke한다. revoke가 실패하면 token/secret이 아닌 Target ID와 selected-provider release 식별자만 남겨 gate를 실패시키고 제한된 운영 로그로 수동 cleanup을 추적한다. revoked Target이 sendable query에서 제외되는 불변식은 production repository live integration test가 별도로 증명한다.
- 실제 FCM device-display smoke는 자동 배포 gate와 분리한 release/manual gate다. fresh FCM registration token과 App Check evidence로 disposable Target과 START 구독을 만들고 한 건의 수신을 확인한 뒤 즉시 revoke한다. 기기의 registration token은 테스트 앱의 platform secure storage와 해당 실행의 masked memory 밖에 보존하지 않으며, 이 수신 검증 전에는 “실제 FCM GREEN”으로 기록하지 않는다.

## Branch protection direction

CI workflow가 안정된 뒤 `main` Ruleset에 다음을 적용한다.

- 직접 push 제한과 PR 병합 요구
- KMP/Android/server CI를 required checks로 지정
- stale base에서 통과한 결과를 막기 위해 최신 branch 상태 요구
- 사이드 프로젝트 초기 review 수는 0 또는 1로 시작하되 CI 우회는 허용하지 않음
- deploy workflow는 PR에서 실행하지 않고 `main` 병합 commit에서만 실행

문서 PR이나 workflow 이름이 아직 없는 상태에서 존재하지 않는 required-check 이름을 미리 등록하지 않는다.

## Release packaging and runtime gates

선택 provider가 source deploy, container image, prebuilt artifact 중 무엇을 받는지 확정하기 전에는 packaging 방식을 기본값으로 정하지 않는다. 어떤 방식을 고르더라도 repository root를 build context로 써야 하는지와 artifact의 실제 entrypoint를 검증한다. `server`가 `core`와 root Gradle/version catalog/wrapper에 의존하므로 `server/`만 context로 써서는 독립 빌드가 되지 않을 수 있다.

선택한 packaging 방식의 성공 조건:

- 필요한 Gradle wrapper, root settings/version catalog, `server`, `core`, 선택한 packaging config가 입력에 포함됨
- `.git`, `.gradle`, 모든 `**/build`, IDE 설정, `.env*`, Service Account JSON, Firebase platform config, token/secret 파일, local-only config, runtime log와 local DB 파일은 입력에서 제외됨
- `:server:installDist` 또는 선택한 동등 artifact가 실제 launcher를 생성하고, server가 `0.0.0.0`과 platform `PORT`로 기동함
- `/health`는 external credential과 무관하게 응답함
- build/packaging 로그와 isolated candidate 또는 controlled release의 검증 결과가 있어야 하며, 문서의 후보 절차만으로 배포 성공을 주장하지 않음

### Conditional example: Cloud Run source deploy

**Cloud Run을 provider로 선택한 경우에만** Dockerfile 없이 repository root를 source로 전달하는 방식을 검증 후보로 삼는다. 아래 명령과 GCP-specific config는 선택·실행·검증 전에는 배포 구현이나 배포 증거가 아니다.

```bash
gcloud run deploy "$SERVICE_NAME" \
  --source . \
  --region asia-northeast3
```

- Cloud Run source deploy를 선택하면 repository root의 `project.toml`에 `GOOGLE_RUNTIME_VERSION=21`, `GOOGLE_GRADLE_BUILD_ARGS=clean :server:installDist --no-daemon`, `GOOGLE_ENTRYPOINT=./server/build/install/server/bin/server`를 고정하고 source build log에서 세 값의 적용을 확인한다.
- Buildpacks가 root Gradle wrapper에 위 build args를 전달하고 `:server:installDist` launcher를 실행하는지 확인한다. `.gcloudignore`와 `gcloud meta list-files-for-upload`으로 위 일반 입력·제외 계약도 검증한다.
- `GOOGLE_ENTRYPOINT`가 실제 build 환경에서 적용되지 않을 때만 같은 launcher를 지정한 root `Procfile`을 대안으로 검증한다. 그 뒤에도 buildpack이 멀티모듈 entrypoint를 안정적으로 실행하지 못한다는 build log가 있을 때만 shadowJar 또는 Dockerfile ADR을 작성한다.

### Conditional example: GCP OIDC and Workload Identity Federation

**Cloud Run/GCP를 선택한 경우에만** 장기 Service Account JSON key를 GitHub Secret에 저장하지 않고 다음 인증 경로를 검증한다.

```text
GitHub Actions OIDC
  -> Workload Identity Pool/Provider
  -> repository/ref attribute condition
  -> deploy-only Service Account impersonation
```

Cloud Run source deploy를 선택한 #52 bootstrap에서는 다음 API만 배포 필요 항목으로 검토·활성화한다.

- `run.googleapis.com`
- `cloudbuild.googleapis.com`
- `artifactregistry.googleapis.com`
- `iamcredentials.googleapis.com`
- `sts.googleapis.com`

다음은 future notification deployment 전용이며 #52 조회 배포에는 필요하지 않다.

- `firestore.googleapis.com`
- `cloudscheduler.googleapis.com`
- FCM에 필요한 Firebase/Google API는 notification deployment 시점에 당시 공식 Admin SDK 문서로 재확인

GitHub repository와 `main` ref 또는 protected environment를 provider attribute condition으로 제한한다. GitHub에는 provider resource name, deploy Service Account email, GCP project ID, region, Cloud Run service name을 repository/environment variable로 두며 JSON private key는 두지 않는다.

정확한 IAM role은 선택 뒤 positive/negative test와 함께 확정한다. 넓은 Owner/Editor는 사용하지 않고 deploy SA, Cloud Build execution, runtime SA, Scheduler invocation 역할을 분리한다.

### Conditional example: Cloud Run revisions, tags, and traffic

**Cloud Run을 선택한 경우에만** commit SHA를 포함한 새 revision을 `--no-traffic`과 고유 tag로 배포하고, Cloud Run이 반환한 tagged revision URL에서 smoke한다. 기본 service URL로 대체하지 않는다. private service이면 deploy SA Cloud Run ID token을 `X-Serverless-Authorization`에 넣고 `aud`는 tag URL이 아닌 base service URL로 고정하며, app의 `Authorization: Target ...` header를 덮어쓰지 않는다.

Cloud Run candidate smoke는 read-only query deployment path의 `/health` 200, notification 404, query protection만 수행한다. 성공하면 traffic을 전환하고, traffic 전 실패는 기존 serving revision을 유지한다. 전환 후 검증 실패는 기록한 이전 revision으로 자동 복원하며, 자동 복원까지 실패한 경우에만 이전 revision 이름과 수동 복원 절차를 안전하게 출력한다. commit SHA tag, Cloud Run source packaging, tagged revision URL과 traffic switch는 provider 선택 전에는 구현·검증된 것으로 기록하지 않는다.

## Provider cost, safety, and rollback gates

Cloud Run·Railway·Render 등 후보 비교와 실측 후 provider를 확정한다. 평상시 최소 한 개 대기는 사용자 요구지만, 구체 instance/CPU/memory/region/billing 설정은 선택된 provider에서 실제 JVM/Ktor 기동과 API latency·비용을 측정한 뒤 정한다. local filesystem/in-memory state는 영속 저장소로 사용하지 않으며, #52 일반 조회에는 DB를 추가하지 않는다.

cost-stop은 provider의 billing/account control에만 의존하지 않는다. 공개 호출 차단, provider가 지원하는 compute/service disable 또는 scale-down, 진행 작업 drain, public endpoint 재기동/수신 거절, build·artifact·storage·log의 잔여 비용 확인을 하나의 절차로 검증한다. 자동 배포가 중단 상태를 되돌리지 않게 하고 원인과 비용 검토 후에만 수동 복구한다.

rollback 단위와 promotion 방식은 provider 선택 후 정한다. candidate 검증 중 실패하면 기존 serving release를 유지하고, promotion 뒤 read-only query gate가 실패하면 기록한 이전 serving release로 rollback한다. rollback 자동화가 실패하면 release 식별자와 안전한 수동 복원 절차만 남기며, 첫 운영 배포 뒤 반복 필요성을 보고 별도 `workflow_dispatch` rollback을 검토한다.

Cloud Run 선택 시에만 revision을 rollback 단위로 쓰고 `--no-traffic`, tag, traffic split을 위 conditional example대로 적용한다. 다른 provider의 release/version/instance 구조를 Cloud Run revision으로 가정하지 않는다.

## Stage evidence matrix

| Evidence | Stage 1.1 | #52 query deployment | Future notification deployment |
| --- | --- | --- | --- |
| Server unit/build/installDist | GREEN — 2026-07-31 | final rerun required | final rerun required |
| Firestore SDK + Emulator | GREEN — 2026-07-31 | credential-free Emulator CI retained | production smoke required |
| Fake App Check/FCM | GREEN — 2026-07-31 | retained CI; real adapters not required | replaced by real adapters |
| App Android/iOS Firebase integration | NOT RUN — Stage 2 | not required | required |
| Real App Check/FCM | NOT RUN — Stage 2 | not required | required |
| Production Firestore/IAM/index | NOT RUN — Stage 2 | not required | required |
| Selected provider/identity/CD | NOT RUN — Stage 2 | required after provider decision | notification deployment gate required |
| Live health/query protection/cost-stop/rollback | NOT RUN — Stage 2 | required | notification gate requirements apply separately |
