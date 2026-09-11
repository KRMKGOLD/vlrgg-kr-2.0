# 서버 컨테이너 배포 경로

기록일: 2026-09-06, 갱신일: 2026-09-11. Issue #52의 보호 구현을 적용한 조회 서버는 서울 `asia-northeast3`의 Cloud Run에 기존 Docker image로 배포한다. GitHub Actions Linux runner가 이미지를 빌드해 Artifact Registry `vlrgg-server`에 push하며 Cloud Build·buildpack·`project.toml`은 사용하지 않는다. [#111](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/111)의 private validation 후속 배포, 실제 rollback, 비용 중단 실패 후 drain·정상 복구, 기본 `run.app` HTTPS 공개 조회와 G 독립 검증은 PASS다. 실제 청구액·알림 수신·Spend cap 활성화는 미확인이며 #112 앱 배포는 별도다. 현재 이슈 상태는 #111에서 추적한다.

## 이미지 계약

루트 `Dockerfile`은 Java 21 build stage에서 기존 root Gradle wrapper의 `:server:installDist`만 실행하고, runtime stage에는 생성된 `server` distribution만 복사한다. runtime은 `app` non-root 사용자로 `/app/bin/server`를 PID 1로 실행하며, `PORT`는 기본값 `8080`을 제공하되 provider가 환경 변수로 덮어쓴다.

초기 runtime은 768 MiB container와 `JAVA_OPTS=-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError`를 사용한다. 이 값은 Java heap만 제한할 뿐 native memory, metaspace, thread stack, JIT/code cache 또는 Linux cgroup RSS를 제한하거나 보장하지 않는다.

## 빌드 컨텍스트 경계

`.dockerignore`는 deny-by-default allowlist다. root wrapper/Gradle descriptor, version catalog와 Gradle daemon JVM toolchain descriptor, root settings가 configure하는 `app/androidApp`·`app/shared`의 build descriptor, `core`의 common/JVM source, `server`의 main source만 builder로 보낸다. `server/src/main/**` 전체를 허용하므로 integration 시점의 새 보호 구현 파일도 숨기지 않는다.

server와 app의 test source, iOS source, 모든 build/out output, IDE/VCS/Gradle state, env/local properties, Firebase/service-account files, PEM/key/certificate/signing files와 logs는 context에 포함되지 않는다. 파일명 denylist는 임의의 secret을 탐지한다는 보장이 아니며, 새 source 경로 또는 Gradle configuration input이 생기면 allowlist를 넓히기 전에 해당 파일이 packaging input인지와 secret/output 배제 패턴을 검토한다.

## 빌드와 Cloud Run runtime 계약

로컬 환경에는 Docker가 없으므로 local image build를 배포 gate로 추가하지 않는다. 기존 CI를 성공한 정확한 `main` SHA만 `.github/workflows/deploy-server.yml`의 수동 실행 대상으로 삼고, GitHub Linux runner에서 image build/push를 확인한다. 원격 실행 로그와 검증 결과를 실제 배포 근거로 사용한다.

초기 Cloud Run 설정은 CPU 1, memory 768 MiB, timeout 30초, concurrency 32, CPU throttling, service-level min/max `1/1`, revision-level min/max `0/1`이다. `JAVA_OPTS`는 위 이미지 계약의 값을 유지한다. 이 설정으로 첫 원격 기동과 대표 조회가 통과했으며, [비용 검토](server-deployment-costs.md)의 과거 512 MiB 계산을 768 MiB 예측으로 읽지 않는다.

같은 image digest를 service-level minimum 0인 고정 private 검증 service `vlrgg-query-check`에 먼저 배포한다. 양 service의 이번 run ID/attempt로 정한 expected revision을 직접 조회하여 Ready·spec image·resolved digest를 확인하고 명시적으로 100% 승격한다. 검증 service에 `allUsers`/`allAuthenticatedUsers` invoker가 있거나 Invoker IAM check가 꺼져 있으면 production 배포 전에 실패한다. 검증 service의 base URL과 그 URL을 audience로 발급한 ID token으로 smoke가 끝난 뒤에만 production `vlrgg-query`에 배포한다. Cloud Run은 새 service 생성에 `--no-traffic`을 지원하지 않으므로 첫 production은 private 기본 traffic으로 만들고 무인증 거절을 확인한다. 후속 production만 tag 없이 `--no-traffic`으로 만들고 승격 전 새 revision의 양수 traffic 부재를 확인한다. production base URL용 별도 ID token으로 다시 smoke하며 실패하면 시작 때 기록한 serving revision으로 복구한다. 배포 환경에서는 injected `PORT`, `/health`, 정상 조회, 안전한 오류, docs/notification 404, 기동 로그와 OOM 여부만 확인하며 완료된 부하 테스트를 다시 선행 조건으로 두지 않는다.

root multi-project configuration이 Android SDK 또는 `local.properties` 없이 `:server:installDist`를 수행하는지 별도 `/private/tmp` allowlisted snapshot에서 확인한 기록은 아래와 같다.

이 prerequisite는 2026-09-06에 snapshot commit `ec3a1bc`, SDK 환경 변수 unset, snapshot-local Gradle cache 및 명시적 `--project-dir`로 확인했다. 포함된 Gradle daemon toolchain descriptor가 isolated Amazon Corretto 21 daemon을 선택했고, `:server:installDist`는 14초에 성공하여 `server/build/install/server/bin/server`를 만들었다. 재현 명령과 전체 경계는 `.omx/evidence/issue52/packaging-implementation/isolated-install-dist.md`에 기록한다.

현재 root `gradle.properties`의 4 GiB Gradle/3 GiB Kotlin daemon JVM 값은 container runtime memory contract가 아니다. GitHub builder의 build memory와 Cloud Run runtime 768 MiB를 같은 한도로 취급하지 않는다.

## 첫 비공개 배포 이력 — 2026-09-10

`main` commit `24cc1dc05c3339f0cdc1a1ab894c9d4c7ba62006`의 [CI](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34466760913)와 [첫 배포](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34467788354)가 통과했다. 기존 Dockerfile 빌드, WIF 인증, image digest 배포와 비공개 IAM을 확인했다. 무인증 요청은 거절되고, 인증 후 health·경기·뉴스는 200, 잘못된 page는 400, 문서·알림 경로는 404였다.

실제 service의 Ready, runtime identity와 위 자원 설정을 확인했고 기동 이후 ERROR 이상 로그는 없었다. 공개 Actions 로그의 Gitleaks 검출은 0건이며 실제 운영 주소와 GCP 식별자가 마스킹됐는지도 별도로 확인했다. 주소·운영 metadata 원본은 저장소에 넣지 않는다.

이 실행은 별도 검증 service를 도입하기 전의 첫 private 배포 이력이다. #110의 APPROVED·병합과 main CI, PR113·114도 선행 변경으로 구분하며 최종 운영 결과는 다음 절을 따른다.

## 후속 배포와 최종 운영 결과 — 2026-09-11 UTC

[PR115](https://github.com/KRMKGOLD/vlrgg-kr-2.0/pull/115)는 최종 head `bd507a95bd87a75c60445c4246a3caa2d412389d`에서 APPROVED 상태 갱신 후 main `74a565ab959b1d5499405979a582aa5789625f4d`로 병합됐다. 최종 두 파일 전체 CodeRabbit CLI 검토는 병합 후·배포 전 17:12:03Z에 findings 0/exit 0으로 완료했다([검토 출처](https://github.com/KRMKGOLD/vlrgg-kr-2.0/pull/115#issuecomment-5638012086)). 같은 main SHA의 [push CI 34625097062](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34625097062)와 [수동 deploy 34627000600](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34627000600)는 success다. G의 현재 상태 53/53·이력 49/49 독립 검증도 PASS다.

| UTC | 확인 결과 |
| --- | --- |
| 17:19:09–17:23:45 | 같은 immutable digest의 private validation/production 배포, expected revision 직접 Ready·digest 확인과 100% 승격·인증 smoke PASS |
| 17:24:30 / 17:24:49 | 실제 이전 정상 revision 100% rollback / 새 revision 100% 복구와 각각 인증 smoke PASS |
| 17:29:20.374 | production short public smoke PASS |
| 17:29:21.237–17:29:28.352 | enable=false·active deploy 0 확인, public invoker 제거 exit 0 뒤 health 200으로 최초 중단 시도 실패 |
| 17:29:32.871–17:29:50.373 | fail-safe로 양 service min0·이전 정상 production 100%, 양 default URL 403과 인증 smoke 확인 |
| 17:46:49.517 | 양수 baseline 세 revision의 개별 active+idle 1→0 표본 조회 완료; 마지막 인증 호출 종료부터 약 1,019초 |
| 17:50:41.853 / 17:50:49.416 | 새 revision 100%·production min1·public 복구 후 full smoke PASS / enable=true 마지막 write |
| 17:58:49–17:58:55 / 18:00:34–18:00:37 | G의 새 설정 조회 / 새 무인증 smoke PASS |

IAM 제거 직후 200은 전파 지연과 일치하는 관측이며 내부 원인을 확정하지 않았다. 첫 실패 기록을 보존하고 같은 차단 구간에서 실제 403과 drain을 확인한 뒤 별도 복구했다. 이전 production은 17:31Z 1→17:46Z 0, 새 production은 17:29Z 1→17:36Z 0, 새 validation은 17:29Z 1→17:46Z 0이었다. 기존 validation 하나는 baseline 0 이후 표본이 없고 실패 revision 둘은 미관측이다. 누락은 unknown이며 서비스 전체 동시각 0·비용 0을 증명하지 않는다. Active deploy와 tag URL은 모두 0이어서 실제 run 취소·tag URL 거절 동작은 실증하지 않았다.

최종 양 service는 CPU 1/768 MiB, concurrency 32, timeout 30초, CPU throttling, 같은 digest·의도한 revision 단독 100%·tag 없음이다. Production service min/max `1/1`, validation `0/1`, 관련 revision 전부 `0/1`이다. 양 Invoker IAM check는 켜져 있고 production public invoker 외 기존 identity/condition을 보존했으며 validation은 private다. Repository enable=true, environment 동명 override 없음, active deploy 0, 수동 workflow 유지다.

최종 무인증 `/health`는 200/`status=ok`, `/api/v1/matches/upcoming`·`/api/v1/news`는 200/예상 JSON, `page=0`은 안전한 `INVALID_REQUEST` 400, `/openapi.json`·`/swagger`·`/api/v1/notification-targets`는 404다. Validation `/health`는 403이다. 로컬 인증 드릴은 bare 사용자 ID token의 개발자 예외 경로였고, Actions WIF는 각 service URL별 audience를 사용했다.

원본 digest/revision/IAM·운영 로그와 F/G 보고서는 Git ignored 보호 경로 `.omx/evidence/issue111/20260911T164328Z/`의 `F/`, `G/`에 보존한다(디렉터리 0700/파일 0600). 공개 문서에는 실제 URL/host를 넣지 않는다. 기존 stable URL과 동일한 주소를 repository 외부 `~/.config/vlrgg-mobile/release-api-url`(0700/0600)로 전달했다. G의 실제 로그·diff 등 URL/host/JWT·Gitleaks 검출은 0건이며, 렌더링된 Actions summary는 취득하지 못해 정확한 배포 summary writer와 성공 step을 대신 확인·검사했다.

월 10만 원 Budget과 1·3·5·8·10만 원 알림 resource는 변경하지 않았다. **1만 원 점검·3만 원 추세 점검·5만 원 도달 또는 더 이른 초과 예상 시 수동 중단**, 8·10만 원 후속 경고로 대응한다. 실제 청구액·알림 수신·Spend cap 활성화는 미확인이고 확인된 자동 상한은 없다. 현재 768 MiB Catalog compute와 과거 전체 계획 가정, 잔여 비용은 [비용 검토](server-deployment-costs.md)에 구분한다.

## 공개 배포와 앱 설치 진행 순서

위 절은 실제 실행 결과이며 아래는 재실행 시 유지할 절차다. 실행 전 `CLOUD_RUN_DEPLOY_ENABLED`의 repository 값과 production environment 동명 값 부재를 모두 확인한다. environment 변수가 있으면 우선하므로 repository의 `true`만 보고 실행하지 않는다.

1. GCP 프로젝트·결제, Artifact Registry, runtime/deploy Service Account와 GitHub WIF를 준비한다. runtime Service Account에는 조회 서버에 필요 없는 DB·Firebase 권한을 주지 않는다.
2. GitHub `production` environment의 운영 식별자 secrets와 repository의 enable 변수를 등록한다. `CLOUD_RUN_DEPLOY_ENABLED=true` 전에는 workflow가 cloud write를 하지 않아야 한다.
3. 수동 workflow로 private 검증 service에서 authenticated `/health`, 대표 조회, 안전한 400, docs/notification 404와 무인증 거절을 확인한 뒤 production 첫 revision을 private 기본 traffic으로 배포한다.
4. 후속 production revision은 tag 없이 no-traffic 배포하고, traffic 승격과 이전 revision rollback을 확인한다. 첫 revision만으로 rollback 검증 완료를 주장하지 않는다.
5. 비용 중단은 enable=false → 대기/진행 deploy가 있으면 취소·종료 → production public invoker 제거 → 양 service min0 → drain·default/존재 tag URL 거절 확인 순서다. 기존 immutable revision의 minimum 0을 먼저 전수 조회하며 `--min-instances=0`으로 기존 revision까지 바뀐다고 보지 않는다. 양수 minimum이 있으면 traffic/tag와 실제 인스턴스를 확인해 절차를 조정한다. IAM readback 외 실제 403을 상한 내 재확인하고 누락 지표는 unknown으로 둔다. Min0은 비용 0을 뜻하지 않으며 image/log·늦은 청구가 남는다.
6. 정상 복구는 기록한 production revision/digest 100%, production service min/max `1/1`·revision min/max `0/1`, validation service private/minimum 0, production public invoker, 외부 smoke 순서다. IAM·traffic·digest·자원을 재확인한 뒤에만 enable을 마지막으로 복구한다. stable URL 원문은 repository 밖의 보호 파일 `~/.config/vlrgg-mobile/release-api-url`(0700/0600)로만 #112에 전달한다.

workflow 준비와 실제 release 완료를 구분한다. #111은 후속 배포·rollback·비용 중단/복구와 원격 공개 endpoint 검증까지 소유한다. [#112](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/112)는 URL 주입·앱 서명·Fastlane/Actions·Android 내부 테스트/TestFlight와 설치 앱 조회 검증을 소유한다. 정식 스토어 공개 출시와 Stage 2의 FCM·Firestore·App Check·Scheduler는 이번 완료 조건에 포함하지 않는다. 별도 SDK, 로그인, 앱 진위 검증 또는 앱에 내장하는 server key는 공개 조회의 접근 제어 전제로 추가하지 않는다.

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
