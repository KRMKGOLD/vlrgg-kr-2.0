# 서버 컨테이너 배포 경로

기록일: 2026-09-06. Issue #52의 provider는 아직 확정하지 않았다. 이 문서는 Railway, Render 또는 image deploy를 선택했을 때 사용할 단일 portable Docker 경로만 정의하며, Cloud Run buildpack용 `project.toml`이나 provider 설정 파일을 함께 추가하지 않는다.

## 이미지 계약

루트 `Dockerfile`은 Java 21 build stage에서 기존 root Gradle wrapper의 `:server:installDist`만 실행하고, runtime stage에는 생성된 `server` distribution만 복사한다. runtime은 `app` non-root 사용자로 `/app/bin/server`를 PID 1로 실행하며, `PORT`는 기본값 `8080`을 제공하되 provider가 환경 변수로 덮어쓴다.

초기 runtime memory 후보는 768 MiB container 검증을 위한 `JAVA_OPTS=-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError`이다. 이 값은 Java heap만 제한할 뿐 native memory, metaspace, thread stack, JIT/code cache 또는 Linux cgroup RSS를 제한하거나 보장하지 않는다.

## 빌드 컨텍스트 경계

`.dockerignore`는 deny-by-default allowlist다. root wrapper/Gradle descriptor, version catalog와 Gradle daemon JVM toolchain descriptor, root settings가 configure하는 `app/androidApp`·`app/shared`의 build descriptor, `core`의 common/JVM source, `server`의 main source만 builder로 보낸다. `server/src/main/**` 전체를 허용하므로 integration 시점의 새 보호 구현 파일도 숨기지 않는다.

server와 app의 test source, iOS source, 모든 build/out output, IDE/VCS/Gradle state, env/local properties, Firebase/service-account files, PEM/key/certificate/signing files와 logs는 context에 포함되지 않는다. 파일명 denylist는 임의의 secret을 탐지한다는 보장이 아니며, 새 source 경로 또는 Gradle configuration input이 생기면 allowlist를 넓히기 전에 해당 파일이 packaging input인지와 secret/output 배제 패턴을 검토한다.

## 검증 순서와 미해결 gate

Docker, Pack, Railway CLI는 현재 환경에 없으므로 image build나 provider 배포 성공을 주장하지 않는다. Docker가 있는 Linux-compatible builder에서 다음을 실행해 image/health baseline을 남긴다.

```bash
docker build --tag vlrgg-server:local .
docker run --rm --name vlrgg-server \
  --env PORT=18080 \
  --publish 18080:18080 \
  vlrgg-server:local
curl --fail --silent http://127.0.0.1:18080/health
```

이 명령은 image build와 local health 확인만 한다. provider 선택 후에도 Linux image architecture, selected provider의 health check (`/health`), injected `PORT`, startup, SIGTERM drain, protected-route load, cgroup RSS/GC 및 비용을 실측해야 한다.

그 전에 root multi-project configuration이 Android SDK 또는 `local.properties` 없이 `:server:installDist`를 수행하는지 별도 `/private/tmp` allowlisted snapshot에서 증명한다. 해당 snapshot은 tracked-file export가 아니라 working tree의 allowlisted `server/src/main/**`을 복사해야 새 보호 구현도 검증한다. 실패하면 Gradle 모듈을 수정하지 않고 정확한 failure와 최소 repair를 기록한다.

이 prerequisite는 2026-09-06에 snapshot commit `ec3a1bc`, SDK 환경 변수 unset, snapshot-local Gradle cache 및 명시적 `--project-dir`로 확인했다. 포함된 Gradle daemon toolchain descriptor가 isolated Amazon Corretto 21 daemon을 선택했고, `:server:installDist`는 14초에 성공하여 `server/build/install/server/bin/server`를 만들었다. 재현 명령과 전체 경계는 `.omx/evidence/issue52/packaging-implementation/isolated-install-dist.md`에 기록한다.

현재 root `gradle.properties`의 4 GiB Gradle/3 GiB Kotlin daemon JVM 값은 container build의 memory contract가 아니다. 저비용 builder에 적용할 build JVM cap은 먼저 isolated `installDist` compile에서 적합성을 검증한 다음 별도 evidence로 확정한다.

## 공개 배포와 앱 설치 진행 순서

1. `server-deployment-costs.md`의 동일 트래픽 가정을 기준으로 provider와 계정에서 실제 제공하는 리소스·요금 제한을 확정한다. 월 목표 5만원, 최대 허용 10만원은 예산 기준이며 애플리케이션의 rate limit이 청구 상한을 보장하지 않는다.
2. Linux builder에서 위 이미지를 빌드하고 768 MiB 후보 한도로 정상·과부하 상황의 cgroup 메모리와 종료/복구를 확인한다. macOS의 JUnit·클라이언트 포함 프로세스 RSS를 운영 서버 RSS로 사용하지 않는다.
3. 계정의 예산 알림과 비상 정지/복구를 합성 이벤트로 검증한다. 공개 접근 차단, 실행 중인 모든 인스턴스 종료, 재배포에 의한 무단 복구 방지 및 잔여 비용을 확인한 뒤 공개한다. 평상시에는 warm 인스턴스 1개를 유지하고 과부하는 오류로 처리한다.
4. HTTPS endpoint를 배포하고 `/health`, 정상 조회, 안전한 오류 응답, docs/notification 비활성화, SIGTERM, rollback을 확인한다. 먼저 별도 smoke 대상으로 확인한 뒤 사용자 트래픽을 연결한다.
5. Android Release의 `API_BASE_URL` Gradle 입력과 iOS Release의 `API_BASE_URL`/`TEAM_ID`를 외부 주입하고 서명한다. Android 입력은 현재 BuildConfig 표현식 계약에 맞는 따옴표 포함 문자열을 사용한다. 서명 키와 로컬 설정은 저장소에 넣지 않는다.
6. 실제 Android와 iPhone에 새로 설치하고 외부망에서 목록·상세·탭, 과부하 안내→수동 재시도, 비상 정지 오류→복구를 확인한다. simulator 테스트나 debug APK 빌드만으로 실제 설치 완료로 판정하지 않는다.

계정/provider, Linux 컨테이너 실행 환경, 공개 endpoint, Android 서명 및 iOS provisioning/배포 채널이 준비되지 않은 현재 상태에서는 코드 PR과 실제 release 완료를 구분한다. 별도 SDK, 로그인, 앱 진위 검증 또는 앱에 내장하는 server key는 이 배포의 접근 제어 전제로 추가하지 않는다.

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
