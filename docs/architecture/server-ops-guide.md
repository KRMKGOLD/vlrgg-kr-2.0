# 서버 운영 스크립트 안내

서버 배포와 운영 검증에 쓰는 workflow와 스크립트가 각각 무엇을 하는지 정리한다. 처음 보는 사람이나 에이전트가 어느 파일을 읽고 무엇을 함께 바꿔야 하는지 알 수 있게 하는 것이 목적이다.

설계 이유, 권한, 비용 중단 절차와 #122 검증 기록의 상세는 [서버 컨테이너 배포와 운영](server-container-deployment.md)이 소유한다. CI 규칙은 [CI/CD](../ci-cd.md)가 소유한다. 이 문서와 그 문서가 다르면 그 문서와 실제 스크립트가 기준이다.

## 한눈에 보기

`.github/workflows/deploy-server.yml`은 수동 실행 전용이고 operation 세 개를 가진다.

| operation | 하는 일 | 언제 쓰나 |
| --- | --- | --- |
| `deploy` | 서버 이미지를 만들어 비공개 확인 서비스에 먼저 올려 확인한 뒤 운영 서비스로 승격한다 | 서버를 배포할 때. 평소에 쓰는 것은 이것뿐이다 |
| `observability-validate` | 비공개 확인 서비스에 일부러 장애를 일으켜 알림이 실제로 오는지 증명한다 | 알림 정책, 메모리 한도, 실패 로그 형식을 바꿔 다시 증명해야 할 때만 |
| `observability-restore` | validate가 중간에 끝났을 때 확인 서비스를 원래 상태로 되돌린다 | validate 실패 뒤 복구 journal이 남았을 때 |

Cloud Run 서비스는 두 개다. `vlrgg-query`가 운영(공개)이고 `vlrgg-query-check`가 확인용(비공개)이다. 지역은 `asia-northeast3`이다.

운영 알림은 배포 스크립트와 따로 있다. 5xx 정책, uptime check와 그 정책, 메모리 초과 로그 정책 네 개와 Error Reporting 알림은 GCP에 이미 만들어져 있다. 저장소의 스크립트는 `managed_by=issue122-validation` label이 붙은 검증용 자원만 만들고 지우며, 운영 알림을 만들거나 바꾸지 않는다.

## 평소 배포 절차 (`deploy`)

실행 조건:

- main의 최신 커밋에서 CI `server` job이 성공해 있어야 한다. 서버와 무관한 변경이라 `server` job이 skip됐으면 [명시적 CI 검증](../ci-cd.md#skipped-platform-validation-before-deployment)을 먼저 실행한다.
- repository variable `CLOUD_RUN_DEPLOY_ENABLED`가 `true`여야 한다.
- `production` environment 승인이 필요하다.

실행은 Actions의 `Deploy query server`에서 branch `main`, operation `deploy`, validation_scope `all`로 한다.

| 순서 | 하는 일 | 쓰는 것 |
| --- | --- | --- |
| 1 | 입력 조합, secret 존재, 체크아웃한 커밋이 main의 최신인지 확인하고 그 커밋의 CI `server` 성공을 조회한다 | `scripts/ci/ci_contract.rb verify` |
| 2 | cloud 인증 전에 서버 이미지를 빌드한다 | 루트 `Dockerfile` |
| 3 | GitHub OIDC와 GCP Workload Identity Federation으로 인증한다 | 장기 key 없음 |
| 4 | 확인 서비스에 검증 복구 journal이 남아 있으면 중단한다 | `observability-service.sh guard` |
| 5 | 이미지를 push하고 digest를 얻는다. 이후 배포는 tag가 아니라 이 digest로 한다 | Artifact Registry `vlrgg-server` |
| 6 | 확인 서비스에 배포하고 Ready, digest, 비공개 IAM, traffic 100%를 확인한다 | revision `vlrgg-query-check-r<run_id>-<attempt>` |
| 7 | ID token으로 확인 서비스를 호출해 응답을 검사한다 | `smoke-query-server.sh` |
| 8 | 운영 서비스에 traffic 없는 새 revision을 만든다. 서비스가 처음 만들어지는 배포만 비공개 상태로 traffic을 받는다 | revision `vlrgg-query-r<run_id>-<attempt>` |
| 9 | 새 revision으로 traffic을 100% 옮긴다 | |
| 10 | 운영 URL을 같은 방법으로 검사한다 | `smoke-query-server.sh` |
| 11 | 9번이나 10번이 실패하면 시작할 때 기록한 이전 revision으로 traffic을 되돌린다 | |

`smoke-query-server.sh`가 보는 것은 다음과 같다. 비공개 서비스는 token 없이 401 또는 403, `/health` 200, 잘못된 page는 400 `INVALID_REQUEST`, `/openapi.json`과 `/swagger`는 404, 예정 경기 목록과 뉴스 목록은 200이다. 마지막 두 개는 실제 VLR.GG를 조회한다.

### 배포가 남기는 것

- Artifact Registry의 이미지 `query:<커밋 SHA>`.
- 확인 서비스와 운영 서비스에 revision 하나씩. 이름 끝의 `r<run_id>-<attempt>`로 Actions 실행을 찾을 수 있다. 실패 로그의 `serviceContext.version`에도 이 revision 이름이 들어간다.
- 운영 traffic은 새 revision 100%다. 이전 revision은 지워지지 않고 남는다.
- 서비스 설정: CPU 1, memory 768 MiB, concurrency 32, timeout 30초, 최소 0·최대 1 instance, CPU throttling. 운영에만 startup CPU boost를 준다. 환경변수는 `VLRGG_ENABLE_API_DOCUMENTATION=false`와 `GOOGLE_CLOUD_PROJECT`다.
- Actions step summary에 커밋 SHA와 통과 문구가 남는다.

URL, digest, token과 gcloud 출력은 로그와 summary에 남기지 않는다. URL은 mask하고 gcloud 출력은 runner의 임시 파일에만 쓴다. artifact는 올리지 않는다.

### 배포 뒤 확인

- 운영 uptime check가 새 revision에서도 정상 값을 내는지 본다. 이전 revision에 묶여 있으면 [교체 순서](server-container-deployment.md#production-permanent-policies)를 따른다.
- Error Reporting의 `asia-northeast3` region에 새 오류 그룹이 생겼는지 본다. 조사 방법은 [Investigating an error](server-container-deployment.md#investigating-an-error)에 있다.

### 되돌리기

승격이나 운영 검사가 실패하면 workflow가 이전 revision으로 되돌린다. 배포가 성공한 뒤 문제를 발견했다면 main에 되돌리는 커밋을 올리고 다시 `deploy`를 실행한다. workflow는 main의 최신 커밋만 배포하므로 과거 커밋을 골라 배포할 수 없다.

## 알림 검증 (`observability-validate`)

확인 서비스에 테스트 전용 서버를 올려 일부러 오류, 종료, 메모리 초과를 일으키고 로그와 알림, 이메일이 실제로 오는지 확인한다. 운영 서비스의 token, traffic, IAM, 정책은 건드리지 않는다.

- 테스트 전용 서버는 `server/src/test/.../observability/validation/ObservabilityValidationMain.kt`다. test source set에 있어 운영 배포본에는 들어가지 않고, `:server:observabilityValidationJar`로 따로 묶어 운영 이미지 위에 덧씌운다. `/__observability/*` 경로로 장애를 일으키며, 확인 서비스 이름과 opt-in 환경변수가 맞지 않으면 시작하지 않는다.
- `validation_scope`로 범위를 고른다. `all`은 오류 로그와 5xx 알림부터 종료 알림까지 전부, `o8-o9`는 uptime과 종료, `o9`는 종료만, `log-delivery`는 장애 없이 로그 알림 전달만, `o9-oom-discovery`와 `o9-oom`은 메모리 초과를 다룬다.
- 시작할 때 확인 서비스의 annotation `vlrgg-observability-validation`에 복구 journal을 쓴다. 원래 revision, 설정 hash, IAM, 이번 실행이 만든 자원이 들어간다. 끝나면 성공이든 실패든 원래 revision으로 traffic을 되돌리고, 설정과 IAM을 복원하고, 이번 실행이 만든 자원을 지우고, journal을 마지막에 지운다.
- journal이 남은 채 끝났으면 `observability-restore`를 실행한다. journal이 있는 동안 `deploy`는 4번 단계에서 멈춘다.
- 검증 중에는 확인 서비스 revision이 최소 instance 1로 떠 있어 비용이 든다.

2026-09-29에 전 범위를 한 번 증명했고 결과는 [#122 observability live runbook](server-container-deployment.md#122-observability-live-runbook)에 있다. 메모리 초과 요청은 누적 3회 한도를 다 썼으므로 근거 없이 다시 실행하지 않는다.

## 파일 지도

| 파일 | 줄 수 | 쓰이는 곳 | 역할 |
| --- | ---: | --- | --- |
| `.github/workflows/deploy-server.yml` | 681 | 전체 | operation 세 개의 단계 |
| `scripts/ci/ci_contract.rb` | 188 | deploy 1번, CI | 변경 경로로 실행할 CI job 선택, 배포 전 CI 성공 확인 |
| `.github/scripts/smoke-query-server.sh` | 65 | deploy, 복원 뒤 확인, CI | 서비스 응답 검사. `--local`은 CI에서 패키징된 서버를 검사한다 |
| `.github/scripts/observability-service.sh` | 372 | deploy는 `guard`만, 나머지는 validate·restore | 복구 journal 읽기·쓰기와 확인 서비스 복원 |
| `.github/scripts/observability-live.sh` | 1,175 | validate | 장애 주입과 로그·trace·알림 확인 |
| `.github/scripts/observability-policies.sh` | 582 | validate | 실행 소유 알림 정책과 uptime check 생성·조회·삭제 |
| `.github/scripts/observability-cleanup.sh` | 223 | validate, restore | 복원과 실행 소유 자원 정리 |
| `.github/scripts/observability-runtime-proof.py` | 299 | validate의 메모리 초과 범위 | 이미지 내용 hash 계산 |
| `.github/scripts/observability-oom.json` | 1 | validate의 `o9-oom` | 메모리 초과 로그 문구 계약 |
| `.github/scripts/observability-validation.Dockerfile` | 7 | validate | 테스트 전용 서버를 덧씌운 이미지 |

테스트는 모두 cloud 접근 없이 돈다. CI `server` job이 실행한다.

| 테스트 | 줄 수 | 확인하는 것 |
| --- | ---: | --- |
| `test-query-production-deploy.sh` | 307 | `deploy-server.yml`에서 6번과 8번 단계의 셸을 뽑아 가짜 gcloud로 실행한다 |
| `test-observability-operations.sh` | 2,968 | service·policies·live·cleanup 스크립트를 가짜 REST와 gcloud로 실행한다 |
| `test-observability-runtime-proof.py` | 330 | 이미지 hash 계산 |
| `test-observability-runtime-container.sh` | 67 | 실제 이미지를 두 번 빌드해 hash가 같은지 본다. Docker가 필요하다 |
| `smoke-observability.sh` | 169 | 패키징된 서버와 테스트 전용 서버를 로컬에서 띄워 격리, 시작 guard, 실패 로그 JSON, trace, health를 본다 |

로컬에서 돌리는 순서:

```sh
./gradlew :server:test :server:build :server:installDist :server:observabilityValidationJar
bash .github/scripts/smoke-observability.sh
bash .github/scripts/test-observability-operations.sh
bash .github/scripts/test-query-production-deploy.sh
python3 -B .github/scripts/test-observability-runtime-proof.py
```

## 함께 바꿔야 하는 것

스크립트가 서버 출력과 workflow 문구를 직접 검사하므로 한쪽만 바꾸면 검증이 깨진다.

| 바꾸는 것 | 같이 볼 곳 |
| --- | --- |
| 실패 로그 JSON의 필드나 severity (`server/.../plugins/FailureEvent.kt`) | `observability-live.sh`의 `run_o3_o6` jq 조건, `test-observability-operations.sh`의 로그 fixture, `smoke-observability.sh`의 이벤트 검사, [서버 구조](server-arch.md)의 Logging 절 |
| 요청 요약 로그 `public_api_summary` (`server/.../plugins/Monitoring.kt`) | `observability-live.sh`가 `requests=`와 `diagnostics_emitted`·`diagnostics_suppressed` 문자열을 검사한다 |
| `deploy-server.yml`의 `Deploy and inspect the private validation service`, `Deploy the verified image to production` 단계 이름이나 셸 | `test-query-production-deploy.sh`가 단계 이름으로 셸을 뽑는다 |
| 루트 `Dockerfile`의 runtime stage | `test-observability-runtime-container.sh`와 `smoke-observability.sh`가 `FROM`·`COPY` 줄과 JVM 옵션을 검사한다 |
| memory 768 MiB | `deploy-server.yml` 세 곳, `observability-oom.json`의 `memoryLimitMiB`와 그 파일의 hash, GCP의 운영 메모리 초과 로그 정책 |
| 공개 route 추가·삭제, 문서 route 노출 | `smoke-query-server.sh` |
| Cloud Run 설정 값 | `deploy-server.yml`의 확인 서비스와 운영 두 곳, `test-query-production-deploy.sh`의 기대값 |

## 줄일 수 있는 것

`deploy`가 실제로 쓰는 스크립트는 `ci_contract.rb verify`, `observability-service.sh guard`, `smoke-query-server.sh` 세 가지다. 나머지 `observability-*` 스크립트와 그 테스트, 테스트 전용 서버(합쳐 약 6,500줄)는 알림을 증명하는 장치이고 평소 배포에는 쓰이지 않는다. 운영 알림은 GCP에 따로 있으므로 이 장치를 지워도 알림은 계속 온다.

지우면 잃는 것은 알림을 자동으로 다시 증명하는 수단이다. 지운다면 다음을 함께 바꾼다.

- `deploy-server.yml`에서 validate·restore 단계와 `guard` 단계를 뺀다. 남는 것은 위 배포 절차의 1~3번과 5~11번이다.
- CI `server` job에서 `test-observability-operations.sh`, `test-observability-runtime-proof.py`, `test-observability-runtime-container.sh`와 `:server:observabilityValidationJar`를 뺀다. `smoke-observability.sh`는 테스트 전용 서버에 기대는 부분을 덜어 내거나 `smoke-query-server.sh --local`로 대체한다.
- `server/build.gradle.kts`의 `observabilityValidationJar` task와 `observability/validation` 테스트 소스를 뺀다.
- 이 문서의 알림 검증 절과 [서버 컨테이너 배포와 운영](server-container-deployment.md)의 검증 절차를 수동 확인 절차로 고친다.

배포 경로를 바꾸는 일이라 실제로 한 번 배포해 확인해야 끝난다. 2026-11-12 현재 이 정리는 하지 않았다.
