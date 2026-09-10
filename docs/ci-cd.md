# CI/CD delivery direction — Cloud Run query server

- Status: Stage 1.1 credential-free CI implemented; Cloud Run deployment workflow prepared but disabled; IAM/WIF bootstrap and live deployment not run; notification production deployment deferred
- Last reviewed: 2026-09-10
- Related: [Server architecture](architecture/server-arch.md), [Stage 1.1 Match notification](architecture/server-fcm-stage1.md)

## Goal and stage boundary

작은 사이드 프로젝트에 맞춰 PR에서는 credential-free 검증만 수행하고, 검증된 `main` commit을 Cloud Run에 수동 배포한다. `.github/workflows/ci.yml`은 완료된 Stage 1.1 offline gate이고, `.github/workflows/deploy-server.yml`은 기존 루트 `Dockerfile`을 GitHub Linux runner에서 빌드해 Artifact Registry와 Cloud Run으로 전달한다. Cloud Build와 source deploy용 buildpack은 사용하지 않는다.

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

현재 코드 준비 상태와 외부 실행 경계는 다음과 같다.

- listener는 `0.0.0.0`과 platform `PORT`를 지원하며 legacy `VLRGG_SERVER_PORT` fallback 및 packaged `/health` smoke가 검증됐다.
- Stage 1.1 알림 runtime은 Firestore 기반 request-bound 계약으로 교체됐고, 일반 runtime의 production provider·알림 route는 Stage 2까지 disabled/fail-closed다.
- 루트 `Dockerfile`은 `:server:installDist` 결과를 non-root Java 21 runtime으로 패키징하고 `PORT`를 지원한다.
- deploy workflow는 명시적 enable 변수와 production environment로 보호한다. 실제 배포는 결제 연결과 IAM/WIF 설정 후 원격 실행 증거로 확인한다.

남은 작업은 GCP bootstrap, workflow 변수 연결, 비공개 첫 배포, 공개 전환과 비용 중단·복구 검증이다. 사용자가 서버 개발과 부하 테스트를 완료로 판단했으므로 이를 다시 선행 조건으로 요구하지 않는다.

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
     -> manual GitHub Actions CD for the exact main SHA
        -> confirm the same SHA passed main CI
        -> GitHub OIDC -> GCP WIF -> deploy Service Account
        -> Docker build -> Artifact Registry image digest
        -> private first revision / no-traffic candidate revision
        -> read-only query release gates
        -> controlled Cloud Run traffic promotion or rollback
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

1. checkout
2. JDK setup and Gradle dependency/build cache
3. `:app:shared:testAndroidHostTest`
4. `:app:androidApp:testDebugUnitTest :app:androidApp:lintDebug`
5. `:server:test :server:build`
6. Stage 1.1 이후 Firestore Emulator launch/readiness, explicit environment, `:server:firestoreEmulatorTest`, always cleanup
7. macOS runner에서 `:app:shared:iosSimulatorArm64Test :app:shared:compileKotlinIosSimulatorArm64`

macOS iOS job은 Android/server Linux job과 별도로 모든 `pull_request` 및 `main` push에서 실행한다. 따라서 iOS simulator test와 Kotlin/Native iOS compilation은 PR 병합 전과 `main` 반영 후 모두 검증되며, macOS runner 사용 시간은 이 전체 CI trigger 범위에 따라 발생한다.

`ci.yml`은 Node 22, Java 21, pinned `firebase-tools@15.25.1`의 foreground `emulators:exec`로 Firestore를 시작·ready 확인·`:server:test :server:firestoreEmulatorTest :server:build :server:installDist` 실행·cleanup한다. Linux job의 KMP Android host, Android unit/lint, packaged `/health`와 notification-route fail-closed smoke와 macOS job의 iOS simulator test/compile 모두 credential 없이 실행한다. Patch whitespace 검사는 PR에서는 base SHA와 head SHA의 범위, `main` push에서는 event before와 head SHA의 범위를 검사하며, `app/**` zero-touch는 이 Stage 1.1 branch evidence이지 향후 app PR을 막는 permanent CI rule이 아니다.

## Prepared `deploy-server.yml`

배포 workflow는 `workflow_dispatch` 전용이며 `main`에서만 실행한다. 같은 SHA의 `main` push CI가 성공했는지 확인한 뒤에만 cloud write를 시작한다. GitHub `production` environment와 repository variable `CLOUD_RUN_DEPLOY_ENABLED=true`가 모두 준비돼야 하며, 변수 누락 또는 다른 값은 모든 cloud write를 차단한다. 중단 상태에서는 이 변수를 먼저 `false`로 바꿔 자동 또는 실수로 재개되지 않게 한다.

고정 배포 대상은 서울 `asia-northeast3`, Cloud Run service `vlrgg-query`, Artifact Registry repository `vlrgg-server`다. 다음 값은 GitHub `production` environment secrets로 연결하고 private key JSON은 저장하지 않는다. 이 값들은 식별자지만 공개 Actions 로그에서 그대로 출력되지 않도록 secrets의 마스킹을 사용한다.

```text
GCP_PROJECT_ID
GCP_WIF_PROVIDER
GCP_DEPLOY_SERVICE_ACCOUNT
GCP_RUNTIME_SERVICE_ACCOUNT
```

GitHub Actions는 루트 `Dockerfile`을 Linux에서 빌드하고 commit SHA로 tag한 이미지를 Artifact Registry에 push한다. push 뒤 digest를 얻어 Cloud Run에 digest로 배포한다. 최초 service는 private이며 stable service URL에서 인증 smoke를 수행한다. 기존 service가 있으면 `candidate` tag와 `--no-traffic`으로 새 revision을 검증하고, 성공한 revision만 traffic 100%로 승격한다. 승격 뒤 smoke 실패 시 workflow 시작 때 기록한 이전 serving revision으로 rollback한다.

공개 service의 candidate URL에도 같은 service IAM 정책이 적용되므로 비공개 URL로 간주하지 않는다. workflow 종료 시 candidate tag를 제거한다. 첫 배포 실패 시 service를 private 상태로 두고 minimum을 0으로 내려 불필요한 warm 비용을 줄인다. 실패한 첫 배포에는 rollback 대상이 없다.

공개 저장소의 Draft PR도 소스와 변경 내역이 공개되고 Actions 로그·요약도 외부에서 볼 수 있다. 배포 URL과 이미지 경로를 job summary에 기록하지 않는다. 생성된 service/candidate URL과 host를 후속 step 전에 마스킹한다. Cloud Run 변경 명령 출력은 runner 임시 파일로 받고, 실패 시 Cloud Run 주소와 이미지 경로를 치환한 진단 로그만 출력한다. 원본 로그는 artifact로 올리지 않는다. 실제 운영 URL은 권한이 있는 Google Cloud Console에서 확인한다. 이 조치는 불필요한 메타데이터 공개를 줄이며, 공개 API 주소 자체를 비밀이나 접근 제어 수단으로 만들지는 않는다.

배포 검증은 `.github/scripts/smoke-query-server.sh`의 `curl`·`jq`로 수행한다. Python 파일은 필요하지 않다. 같은 script의 `--local` 경로를 credential-free CI의 packaged smoke에서 실행해 health·안전한 400·문서/알림 404를 확인한다. 로컬 경로는 토큰 입력을 거절하고 실제 upstream 조회를 하지 않는다. 배포 경로는 HTTPS Cloud Run URL만 허용하고 redirect를 따라가지 않으며 토큰은 curl 인자 대신 stdin header로 전달한다.

초기 runtime은 request-based billing, service-level min/max `1/1`, revision-level min/max `0/1`, CPU 1, memory 768 MiB, timeout 30초, concurrency 32, CPU throttling 사용이다. CPU·memory는 최초 원격 기동과 기본 지표를 확인한 뒤 조정할 후보값이다. API documentation은 계속 disabled다.

### #52 read-only query deployment path

이 경로는 #52의 일반 조회 공개에만 적용한다. App Check, Target, production Firestore, FCM은 이 경로의 선행 조건이 아니며 기존 credential-free Firestore Emulator CI를 변경하지 않는다.

Deploy identity는 GitHub OIDC와 GCP Workload Identity Federation으로 deploy Service Account를 impersonate한다. 장기 Service Account key는 만들거나 GitHub에 저장하지 않는다.

1. enable 변수와 production environment의 main branch 제한 확인
2. exact `main` SHA와 같은 SHA의 성공한 CI push run 확인
3. 검증된 SHA checkout과 Docker build
4. WIF 인증, Artifact Registry push와 image digest 기록
5. private first revision 또는 no-traffic candidate revision 배포
6. WIF로 다시 발급한 Cloud Run ID token으로 `/health` 200, notification route 404와 대표 query를 확인
7. candidate 성공 시 traffic 승격, 승격 후 smoke 실패 시 이전 revision rollback

동시 배포는 service 단위 `concurrency.group`과 `cancel-in-progress: false`로 직렬화한다. workflow는 기존 CI 전체나 부하 테스트를 다시 실행하지 않는다.

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

루트 `Dockerfile`과 `.dockerignore`를 단일 packaging 경로로 사용한다. repository root가 build context이며 `server`가 의존하는 `core`, root Gradle wrapper와 version catalog를 포함한다. Cloud Build API, buildpack, `project.toml`, `Procfile`, shadow JAR는 추가하지 않는다.

성공 조건:

- 필요한 Gradle wrapper, root settings/version catalog, `server`, `core`, 선택한 packaging config가 입력에 포함됨
- `.git`, `.gradle`, 모든 `**/build`, IDE 설정, `.env*`, Service Account JSON, Firebase platform config, token/secret 파일, local-only config, runtime log와 local DB 파일은 입력에서 제외됨
- `:server:installDist` 또는 선택한 동등 artifact가 실제 launcher를 생성하고, server가 `0.0.0.0`과 platform `PORT`로 기동함
- 애플리케이션 `/health`는 별도 앱 secret 없이 응답하며 private Cloud Run에서는 platform ID token으로 호출됨
- GitHub Linux builder의 build/push log, image digest와 Cloud Run revision의 검증 결과가 있어야 하며, 문서와 workflow 존재만으로 배포 성공을 주장하지 않음

## GCP bootstrap runbook

Google 계정 로그인, 운영 프로젝트와 결제 계정 연결을 준비한 뒤 아래 설정을 진행한다. placeholder는 실제 값으로 바꾸고, 실행 결과는 secret 없이 운영 증거에 기록한다. 아래 명령은 설정 절차이며 실행 완료의 증거가 아니다.

```bash
GCP_PROJECT_ID="your-project-id"
GCP_PROJECT_NUMBER="your-project-number"
GITHUB_REPOSITORY="KRMKGOLD/vlrgg-kr-2.0"
GITHUB_REPOSITORY_ID="$(gh api "repos/$GITHUB_REPOSITORY" --jq '.id')"
GITHUB_OWNER_ID="$(gh api "repos/$GITHUB_REPOSITORY" --jq '.owner.id')"
GCP_REGION="asia-northeast3"
GCP_ARTIFACT_REPOSITORY="vlrgg-server"
GCP_WIF_POOL_ID="github"
GCP_WIF_PROVIDER_ID="vlrgg-main"
GCP_RUNTIME_SERVICE_ACCOUNT="vlrgg-query-runtime@${GCP_PROJECT_ID}.iam.gserviceaccount.com"
GCP_DEPLOY_SERVICE_ACCOUNT="vlrgg-query-deploy@${GCP_PROJECT_ID}.iam.gserviceaccount.com"

gcloud config set project "$GCP_PROJECT_ID"
gcloud services enable run.googleapis.com artifactregistry.googleapis.com iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com
gcloud artifacts repositories create "$GCP_ARTIFACT_REPOSITORY" --repository-format=docker --location="$GCP_REGION"
gcloud iam service-accounts create vlrgg-query-runtime
gcloud iam service-accounts create vlrgg-query-deploy
gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
  --member="serviceAccount:$GCP_DEPLOY_SERVICE_ACCOUNT" --role=roles/run.developer
gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
  --member="serviceAccount:$GCP_DEPLOY_SERVICE_ACCOUNT" --role=roles/run.invoker
gcloud artifacts repositories add-iam-policy-binding "$GCP_ARTIFACT_REPOSITORY" \
  --location="$GCP_REGION" --member="serviceAccount:$GCP_DEPLOY_SERVICE_ACCOUNT" \
  --role=roles/artifactregistry.writer
gcloud iam service-accounts add-iam-policy-binding "$GCP_RUNTIME_SERVICE_ACCOUNT" \
  --member="serviceAccount:$GCP_DEPLOY_SERVICE_ACCOUNT" --role=roles/iam.serviceAccountUser

gcloud iam workload-identity-pools create "$GCP_WIF_POOL_ID" \
  --project="$GCP_PROJECT_ID" --location=global --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc "$GCP_WIF_PROVIDER_ID" \
  --project="$GCP_PROJECT_ID" --location=global \
  --workload-identity-pool="$GCP_WIF_POOL_ID" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id" \
  --attribute-condition="assertion.repository_id == '$GITHUB_REPOSITORY_ID' && assertion.repository_owner_id == '$GITHUB_OWNER_ID' && assertion.ref == 'refs/heads/main' && assertion.workflow_ref == '$GITHUB_REPOSITORY/.github/workflows/deploy-server.yml@refs/heads/main' && assertion.environment == 'production'"
gcloud iam service-accounts add-iam-policy-binding "$GCP_DEPLOY_SERVICE_ACCOUNT" \
  --member="principalSet://iam.googleapis.com/projects/$GCP_PROJECT_NUMBER/locations/global/workloadIdentityPools/$GCP_WIF_POOL_ID/attribute.repository_id/$GITHUB_REPOSITORY_ID" \
  --role=roles/iam.workloadIdentityUser
gcloud iam workload-identity-pools providers describe "$GCP_WIF_PROVIDER_ID" \
  --project="$GCP_PROJECT_ID" --location=global \
  --workload-identity-pool="$GCP_WIF_POOL_ID" --format='value(name)'
```

deploy Service Account에는 project의 `roles/run.developer`와 첫 private service 생성 직후 자동 smoke에 필요한 `roles/run.invoker`, Artifact Registry repository의 `roles/artifactregistry.writer`, runtime Service Account의 `roles/iam.serviceAccountUser`를 부여한다. runtime Service Account에는 조회 배포에서 DB·Firebase 권한을 주지 않는다. bootstrap 인간 계정이 이 binding을 설정할 권한은 별도로 보유해야 한다.

WIF provider는 GitHub issuer를 사용하고 변하지 않는 repository/owner ID, `main` ref, 지정 deploy workflow와 `production` environment로 신뢰 범위를 제한한다. 이 principalSet에 deploy Service Account의 `roles/iam.workloadIdentityUser`만 부여한다. action이 WIF를 통해 ID token을 발급하므로 self `roles/iam.serviceAccountTokenCreator`는 필요하지 않다. [Google WIF 가이드](https://cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines), [auth action](https://github.com/google-github-actions/auth)

설정 후 GitHub `production` environment의 허용 branch를 `main`으로 제한하고 위 secrets를 등록한다. `CLOUD_RUN_DEPLOY_ENABLED`는 repository variable 한 곳에서만 관리하고 environment에 동명 변수를 만들지 않는다. 비용 통제 설정을 확인한 뒤 첫 실행 직전에만 `true`로 바꾼다. 별도의 필수 승인자를 추가하지 않는다.

### Public access and cost stop

비공개 smoke가 통과한 첫 service를 공개할 때만 bootstrap 인간 계정으로 다음 IAM binding을 추가한다.

```bash
gcloud run services add-iam-policy-binding vlrgg-query \
  --project="$GCP_PROJECT_ID" \
  --region=asia-northeast3 \
  --member=allUsers \
  --role=roles/run.invoker
```

비용 중단은 `CLOUD_RUN_DEPLOY_ENABLED=false` 설정 후 대기·실행 중인 배포를 취소하고 종료를 확인하는 것부터 시작한다. workflow가 시작할 때 읽은 변수는 실행 도중 갱신되지 않으므로 변수 변경만으로 진행 중인 배포가 멈추지는 않는다. 다음으로 `allUsers` invoker 제거, service minimum 0, 진행 요청 drain과 default/tagged URL의 공개 거절을 확인한다. 이 workflow가 만든 revision의 minimum은 이미 0이다.

```bash
gh variable set CLOUD_RUN_DEPLOY_ENABLED --repo KRMKGOLD/vlrgg-kr-2.0 --body false
# GitHub Actions에서 대기/실행 중인 Deploy query server run을 취소하고 종료를 확인한다.
gcloud run services remove-iam-policy-binding vlrgg-query \
  --project="$GCP_PROJECT_ID" --region=asia-northeast3 \
  --member=allUsers --role=roles/run.invoker
gcloud run services update vlrgg-query \
  --project="$GCP_PROJECT_ID" --region=asia-northeast3 --min=0
```

Artifact Registry image와 로그 비용은 계속 발생할 수 있다. 복구는 비용 원인을 확인한 뒤 `gcloud run services update vlrgg-query --project="$GCP_PROJECT_ID" --region=asia-northeast3 --min=1`, 위 public invoker 추가, 외부망 smoke 순으로 수행하고 enable 변수는 마지막에 되돌린다. Billing budget alert는 지출을 중단하지 않으며 Cloud Run spend cap은 Preview이고 집행 지연·잔여 비용이 있어 고정 청구 상한으로 보지 않는다.

Cloud Run revision이 rollback 단위다. candidate 검증 전에는 기존 serving revision을 유지하고, 승격 후 실패하면 기록한 revision으로 traffic을 복원한다. 첫 배포에는 이전 revision이 없으므로 후속 revision에서 rollback을 한 번 검증해야 완료 증거가 된다.

## Stage evidence matrix

| Evidence | Stage 1.1 | #52 query deployment | Future notification deployment |
| --- | --- | --- | --- |
| Server unit/build/installDist | GREEN — 2026-09-10 | existing CI success for exact SHA required | final rerun required |
| Firestore SDK + Emulator | GREEN — 2026-07-31 | credential-free Emulator CI retained | production smoke required |
| Fake App Check/FCM | GREEN — 2026-07-31 | retained CI; real adapters not required | replaced by real adapters |
| App Android/iOS Firebase integration | NOT RUN — Stage 2 | not required | required |
| Real App Check/FCM | NOT RUN — Stage 2 | not required | required |
| Production Firestore/IAM/index | NOT RUN — Stage 2 | not required | required |
| Cloud Run identity/CD | NOT RUN — IAM/WIF and deployment pending | required | notification deployment gate required |
| Live health/query protection/cost-stop/rollback | NOT RUN — Stage 2 | required | notification gate requirements apply separately |
