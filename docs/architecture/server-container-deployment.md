# 서버 컨테이너 배포 경로

기록일: 2026-09-06, 갱신일: 2026-09-10. Issue #52 조회 서버는 서울 `asia-northeast3`의 Cloud Run에 기존 Docker image로 배포한다. GitHub Actions Linux runner가 이미지를 빌드해 Artifact Registry `vlrgg-server`에 push하며 Cloud Build·buildpack·`project.toml`은 사용하지 않는다. 첫 비공개 배포와 대표 조회 검증을 완료했으며 공개 전환과 후속 배포 검증은 남아 있다.

## 이미지 계약

루트 `Dockerfile`은 Java 21 build stage에서 기존 root Gradle wrapper의 `:server:installDist`만 실행하고, runtime stage에는 생성된 `server` distribution만 복사한다. runtime은 `app` non-root 사용자로 `/app/bin/server`를 PID 1로 실행하며, `PORT`는 기본값 `8080`을 제공하되 provider가 환경 변수로 덮어쓴다.

초기 runtime은 768 MiB container와 `JAVA_OPTS=-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError`를 사용한다. 이 값은 Java heap만 제한할 뿐 native memory, metaspace, thread stack, JIT/code cache 또는 Linux cgroup RSS를 제한하거나 보장하지 않는다.

## 빌드 컨텍스트 경계

`.dockerignore`는 deny-by-default allowlist다. root wrapper/Gradle descriptor, version catalog와 Gradle daemon JVM toolchain descriptor, root settings가 configure하는 `app/androidApp`·`app/shared`의 build descriptor, `core`의 common/JVM source, `server`의 main source만 builder로 보낸다. `server/src/main/**` 전체를 허용하므로 integration 시점의 새 보호 구현 파일도 숨기지 않는다.

server와 app의 test source, iOS source, 모든 build/out output, IDE/VCS/Gradle state, env/local properties, Firebase/service-account files, PEM/key/certificate/signing files와 logs는 context에 포함되지 않는다. 파일명 denylist는 임의의 secret을 탐지한다는 보장이 아니며, 새 source 경로 또는 Gradle configuration input이 생기면 allowlist를 넓히기 전에 해당 파일이 packaging input인지와 secret/output 배제 패턴을 검토한다.

## 빌드와 Cloud Run runtime 계약

로컬 환경에는 Docker가 없으므로 local image build를 배포 gate로 추가하지 않는다. 기존 CI를 성공한 정확한 `main` SHA만 `.github/workflows/deploy-server.yml`의 수동 실행 대상으로 삼고, GitHub Linux runner에서 image build/push를 확인한다. 원격 실행 로그와 검증 결과를 실제 배포 근거로 사용한다.

초기 Cloud Run 설정은 CPU 1, memory 768 MiB, timeout 30초, concurrency 32, CPU throttling, service-level min/max `1/1`, revision-level min/max `0/1`이다. `JAVA_OPTS`는 위 이미지 계약의 값을 유지한다. 이 설정으로 첫 원격 기동과 대표 조회가 통과했으며, 아래 과거 512 MiB 비용 계산을 768 MiB 예측으로 읽지 않는다.

같은 image digest를 service-level minimum 0인 고정 private 검증 service `vlrgg-query-check`에 먼저 배포하고 새 revision을 100%로 명시한다. 검증 service에 `allUsers`/`allAuthenticatedUsers` invoker가 있거나 Invoker IAM check가 꺼져 있으면 production 배포 전에 실패한다. 검증 service의 base URL과 그 URL을 audience로 발급한 ID token으로 smoke가 끝난 뒤에만 production `vlrgg-query`에 배포한다. Cloud Run은 새 service 생성에 `--no-traffic`을 지원하지 않으므로 첫 production은 private 기본 traffic으로 만들고 무인증 거절을 확인한다. 후속 production만 tag 없이 `--no-traffic`으로 만들고 명시된 새 revision을 승격한다. production base URL용 별도 ID token으로 다시 smoke하며 실패하면 시작 때 기록한 serving revision으로 복구한다. 배포 환경에서는 injected `PORT`, `/health`, 정상 조회, 안전한 오류, docs/notification 404, 기동 로그와 OOM 여부만 확인하며 완료된 부하 테스트를 다시 선행 조건으로 두지 않는다.

root multi-project configuration이 Android SDK 또는 `local.properties` 없이 `:server:installDist`를 수행하는지 별도 `/private/tmp` allowlisted snapshot에서 확인한 기록은 아래와 같다.

이 prerequisite는 2026-09-06에 snapshot commit `ec3a1bc`, SDK 환경 변수 unset, snapshot-local Gradle cache 및 명시적 `--project-dir`로 확인했다. 포함된 Gradle daemon toolchain descriptor가 isolated Amazon Corretto 21 daemon을 선택했고, `:server:installDist`는 14초에 성공하여 `server/build/install/server/bin/server`를 만들었다. 재현 명령과 전체 경계는 `.omx/evidence/issue52/packaging-implementation/isolated-install-dist.md`에 기록한다.

현재 root `gradle.properties`의 4 GiB Gradle/3 GiB Kotlin daemon JVM 값은 container runtime memory contract가 아니다. GitHub builder의 build memory와 Cloud Run runtime 768 MiB를 같은 한도로 취급하지 않는다.

## 첫 비공개 배포 증거 — 2026-09-10

`main` commit `24cc1dc05c3339f0cdc1a1ab894c9d4c7ba62006`의 [CI](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34466760913)와 [첫 배포](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34467788354)가 통과했다. 기존 Dockerfile 빌드, WIF 인증, image digest 배포와 비공개 IAM을 확인했다. 무인증 요청은 거절되고, 인증 후 health·경기·뉴스는 200, 잘못된 page는 400, 문서·알림 경로는 404였다.

실제 service의 Ready, runtime identity와 위 자원 설정을 확인했고 기동 이후 ERROR 이상 로그는 없었다. 공개 Actions 로그의 Gitleaks 검출은 0건이며 실제 운영 주소와 GCP 식별자가 마스킹됐는지도 별도로 확인했다. 주소·운영 metadata 원본은 저장소에 넣지 않는다.

이 실행은 별도 검증 service를 도입하기 전의 첫 private 배포다. 비공개 검증 service를 통한 후속 배포, rollback, 공개 전환과 비용 중단·복구는 아직 원격 검증하지 않았다. 프로젝트의 월 지출 알림은 1만·3만·5만·8만·10만 원으로 설정됐으며, Spend cap 자동 중단의 활성화 증거는 없다.

## 공개 배포와 앱 설치 진행 순서

1. GCP 프로젝트·결제, Artifact Registry, runtime/deploy Service Account와 GitHub WIF를 준비한다. runtime Service Account에는 조회 서버에 필요 없는 DB·Firebase 권한을 주지 않는다.
2. GitHub `production` environment의 운영 식별자 secrets와 repository의 enable 변수를 등록한다. `CLOUD_RUN_DEPLOY_ENABLED=true` 전에는 workflow가 cloud write를 하지 않아야 한다.
3. 수동 workflow로 private 검증 service에서 authenticated `/health`, 대표 조회, 안전한 400, docs/notification 404와 무인증 거절을 확인한 뒤 production 첫 revision을 private 기본 traffic으로 배포한다.
4. 후속 production revision은 tag 없이 no-traffic 배포하고, traffic 승격과 이전 revision rollback을 확인한다. 첫 revision만으로 rollback 검증 완료를 주장하지 않는다.
5. 비용 중단을 연습한다. enable 변수를 `false`로 바꾸고 진행 중인 배포를 취소·종료한 뒤 public invoker 제거, service/revision minimum 0, drain, default/tagged URL 공개 거절과 잔여 image/log 비용을 확인한다. 복구 후 같은 stable URL을 다시 smoke한다.
6. 검증된 revision에 public invoker를 부여하고 외부망 조회를 확인한 뒤 stable URL을 Android/iOS `API_BASE_URL` 입력으로 전달한다.

workflow 준비와 실제 release 완료를 구분한다. 결제와 IAM/WIF 설정, 원격 revision·공개 endpoint 검증을 마쳐야 조회 서버 배포 완료다. 앱 서명, 기기 설치와 스토어 출시, FCM·Firestore·App Check·Scheduler는 후속 범위다. 별도 SDK, 로그인, 앱 진위 검증 또는 앱에 내장하는 server key는 공개 조회의 접근 제어 전제로 추가하지 않는다.

## 로컬 보호 경로 부하 결과 — 2026-09-07

Java 21.0.11 macOS의 단일 JUnit JVM에서 Netty, 실제 matches route/service/parser/mapper/protection/serializer와 1초 지연 fixture transport를 실행했다. 외부 VLR.GG 요청은 하지 않았다.

| 조건 | 관측 결과 |
| --- | --- |
| 정상 0.2요청/초 | 5건 모두 200, 완료된 요청별 fetch 1회 |
| 서로 다른 상세 ID 4건 동시 | 4건 모두 200, fetch 4회, 약 1.03초 |
| 100요청/초 × 60초 | 제출 6,000건, 생성기 누락·통신 실패·예상 밖 HTTP 상태 0건 |
| 과부하 응답 | 200: 121건, 429: 5,381건, 503: 498건 |
| 제한 응답 p95 | 약 1.54ms (로컬 목표 200ms 미만) |
| 과부하 upstream fetch | 121회, burst+refill 상한 124회 이내 |
| 샘플링한 동시 작업 최대 | API 4/상한 8, upstream 4/상한 4 |
| 종료 후 복구 | active API/upstream 0, 새 요청 200 및 새 fetch 1회 |
| 프로세스 RSS | idle 약 156.6 MiB, 정상 직후 약 174.5 MiB, 종료 시 약 240.8 MiB |
| 프로세스 CPU 사용률 | 정상 구간 약 1.64%, 과부하 구간 약 20.45% |

RSS/CPU에는 서버 외 JUnit·Java HTTP client·계측·fixture 처리가 포함된다. 종료 시 RSS는 최대 RSS가 아니며, production CIO·TLS·네트워크·Linux cgroup·두 독립 프로세스(I8)·provider 요금의 증거가 아니다. 따라서 이 측정만으로 512 MiB 운영 가능 여부나 월 청구액을 확정하지 않는다. 컨테이너 검증과 계정의 비용 정지 확인을 거쳐야 한다.

재현 시 report 경로는 저장소 밖 또는 ignored evidence의 절대 경로를 사용한다. 벤치마크는 환경 변수가 없으면 일반 테스트에서 건너뛴다.

```bash
PROTECTED_ROUTE_LOAD_REPORT_PATH=/tmp/vlrgg-protected-load.properties \
  ./gradlew :server:test --tests '*ProtectedRouteLoadBenchmarkTest' --rerun-tasks
```

전체 숫자는 당시 로컬 `.omx/evidence/issue52/protected-route-load/latest.properties`에 보관했으며 런타임 로그는 커밋하지 않는다.

### 두 독립 프로세스 확인 (I8)

같은 날 별도 Java 프로세스 두 개(PID 78253, 78254)에서 기존 JUnit 부하 테스트를 동시에 실행했다. 각 프로세스는 독립 Netty와 보호 상태를 생성하고 각각 다른 절대 report 파일을 사용했다. 두 JUnit 실행 모두 exit 0이었다.

각 프로세스는 6,000건 중 121건 200, 5,381건 429, 498건 503을 반환했고 통신 실패는 없었다. 각각 upstream fetch 121회, 관측 active API/upstream 최대 4/4, 종료 후 0/0 및 정상 200 복구를 확인했다. 제한 응답 p95는 각각 약 1.77ms, 1.78ms였다. 따라서 과부하 구간의 합산 fetch는 242회로, 단일 프로세스 상한 124회를 넘는다. 현재 제한은 프로세스별 제한이며 여러 인스턴스에 걸친 총량 제한이 아니다.

이 결과는 로컬 독립 프로세스 증거이며 provider autoscaling·공유 네트워크·Linux cgroup 검증을 대신하지 않는다. 최초 raw report의 `rss.sampling` 설명은 잘못 기재되어 별도로 정정했다. RSS는 idle/정상 직후/종료 시 세 지점만 측정했으며 250ms 간격의 샘플링 대상은 동시 작업 수다. 기존 raw report를 덮어쓰지 않는다.
