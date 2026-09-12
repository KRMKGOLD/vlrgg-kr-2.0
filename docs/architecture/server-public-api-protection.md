# 공개 조회 API 보호 계약 (#52)

상태(2026-09-12): #52 보호 구현·부하 검증과 #110 APPROVED·병합은 선행 이력이며 #52는 잔여 배포 항목 이관 후 종료했다. [#111](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/111)은 PR115 main `74a565ab959b1d5499405979a582aa5789625f4d`의 [CI 34625097062](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34625097062)·[deploy 34627000600](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34627000600) success와 실제 rollback·비용 중단 실패 후 복구·public smoke 및 G 독립 검증 PASS를 확인했다. 실행 시각·검토 출처·최종 설정은 [운영 결과](server-container-deployment.md)에 기록한다. [#112](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/112)는 credential-free 앱 release process 구현만, 실제 계정·서명·업로드·기기 검증은 [#117](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/117)이 소유한다. 아래 한도는 보호 계약이지 월 비용 실측값이 아니다.

## 범위

Android와 iOS에 실제 설치한 앱에서 기존 조회 API를 사용한다. 로그인과 앱 진위 검증은 도입하지 않으며 외부 API 호출 자체는 허용한다. 서버의 비싼 작업과 비용을 제한하고, 과부하에는 오류와 수동 재시도를 제공한다. FCM·알림 production wiring은 별도 작업이다.

기존 Ktor/JVM·Jsoup을 유지한다. 일반 조회용 DB, 완료 응답 캐시, stale fallback, Redis는 추가하지 않는다. 고정 앱 키는 앱 설치 파일에서 추출될 수 있으므로 호출자 신원이나 주요 보호 수단으로 사용하지 않는다.

평소 서버 한 개를 대기시킨다. 월 5만 원을 목표로 하고 10만 원을 최대 허용 예산으로 삼는다. 과부하 때 자동 증설보다 빠른 거절을 우선하며, 예산 초과 예상 시 서비스 중단은 허용한다. 요금 알림이나 인스턴스 설정만으로 모든 공격에 대한 절대 지출 상한을 보장하지 않는다.

## 서버 계약

| 경계 | 초기 기준 | 초과 동작 |
| --- | --- | --- |
| API 요청 token bucket | 프로세스당 10회/초, burst 20 | 429 `RATE_LIMITED` |
| 처리 중 API | 8개, 내부 대기열 없음 | 503 `SERVER_BUSY` |
| 새 upstream fetch | 2회/초, burst 4, 동시 4개 | 503 `SERVER_BUSY` |
| 진행 중 canonical key | 최대 4개 | 추가 key는 즉시 거절 |
| 요청 시간 | upstream 10초 < 서버 전체 15초; 실제 Cloud Run timeout 30초, 앱 30초 | 안전한 오류와 작업 취소 |
| 요청 target / headers / read body | 4KiB / 16KiB / 1KiB | 앱 경계에서 400·413·431; 플랫폼/엔진 응답은 별도 |
| upstream HTML / 성공 JSON | 1MiB / 2MiB | 안전한 502 (`RESPONSE_TOO_LARGE`는 성공 JSON 제한 초과) |

과부하 응답은 기존 `{code, message}` 형식을 사용하며 `Retry-After`에 정수 초를 보낸다. 서버 내부 예외, HTML, selector, 원본 URL을 응답에 넣지 않는다. 잘못된 한도 설정은 서버 시작 시 거절한다.

전체 요청 deadline은 느린 body 수신보다 먼저 시작한다. 요청 크기를 확인한 다음 API token과 동시 처리 자원을 얻고, 실제 fetch 전에 별도의 upstream 한도를 적용한다. 실패·취소 시 모든 자원을 반환한다. 알 수 없는 경로와 method도 저비용 요청 제한 대상이며 스크래핑하지 않는다.

성공 JSON은 응답 header를 보내기 전에 제한된 출력 버퍼로 직렬화한다. 무제한 문자열을 만든 뒤 길이만 검사하지 않는다. 최대 8개 직렬화 버퍼 합계 16MiB 외에 HTML·DTO·parser·네트워크·JVM 메모리를 함께 측정한다.

`/health`는 상수 크기의 liveness 응답이다. API quota, scrape admission, upstream 상태 및 요청별 로그에서 독립시켜 API 포화가 재시작을 유발하지 않게 한다. 공개 health 요청 자체의 CPU·전송 비용은 남는다.

## 실제 fetch 비용

| API 종류 | 호출당 upstream fetch | 구현 근거 |
| --- | --- | --- |
| matches 목록·상세 | 1 | `VlrMatchesScraper` |
| news 목록·상세 | 1 | `NewsScraper` |
| events 목록·상세·각 탭 | 각 호출 1 | `EventsScraper` |
| search / player / series | 1 | 각 feature scraper |
| team 상세 | 2 | `TeamDetailScraper`의 overview·news 병렬 조회 |
| health / 유효하지 않은 요청 / 미연결 notification | 0 | route 검증 및 production 구성 |

정상 burst 검증은 호출 수가 아니라 새 fetch 비용 합계 4 이하를 사용한다. 예를 들어 단일 fetch API 4개와 team 상세 2개를 각각 검증한다. team 상세 3개를 동시에 호출하면 일부 거절이 가능하다.

동일 canonical URL의 **진행 중인** immutable HTML만 공유한다. 호출자별 parser는 독립적이며 Jsoup Document를 공유하지 않는다. 한 waiter의 취소는 나머지를 취소하지 않고, 마지막 waiter가 떠나면 fetch를 취소한다. 완료 결과는 즉시 제거하여 다음 요청에서 새로 조회한다. application 종료 시 공유 작업 scope도 종료한다.

## 앱 계약

조회 API 전용 helper가 기본 HTTP 오류 validator를 호출별로 해제하고 status를 먼저 검사한다. 오류 body는 8KiB 이내에서 읽고 남은 body를 취소한다. 공통 HTTP client의 다른 소비자 설정은 유지한다.

429/`RATE_LIMITED`, 503/`SERVER_BUSY` 조합만 Domain의 `Busy(retryDelay)`로 변환한다. `Retry-After`는 정수 1~60초만 사용하고 누락·잘못된 값은 2초로 처리한다. 플랫폼 HTML 오류나 알 수 없는 코드는 기존 `Failure`로 처리하며 coroutine 취소는 전파한다. 원본 HTTP code·서버 message는 Domain/UI에 전달하지 않는다.

`onFailure`는 일반 실패만, `onBusy`는 Busy만 처리한다. 새 결과 타입의 모든 소비자를 점검하며 즐겨찾기 같은 local 실패 UX는 유지한다.

각 화면 UiState에 실패한 작업 identity, monotonic 재시도 deadline, 다이얼로그 상태를 둔다. 대기 중 버튼은 비활성화하고 이후 사용자가 한 번 재시도한다. 닫기 후 기존 retry·refresh·pagination 경로도 같은 대기 시간과 중복 방지 검사를 거친다. query·탭·화면이 바뀌면 이전 작업을 재시도하지 않는다. 정상 콘텐츠와 페이지 위치를 가능한 한 유지한다.

대상은 matches 목록·상세, news 목록·상세, events 목록·상세 탭, search, team·player·series 상세다. MyPage는 실제 remote 호출 유무와 sealed 결과 소비를 별도로 확인한다.

## 배포 판단과 운영 경계

조회 서버의 최종 구성은 서울 `asia-northeast3` Cloud Run request-based billing, CPU 1, memory 768 MiB, concurrency 32, timeout 30초, CPU throttling이다. Production service min/max `1/1`, validation `0/1`, 관련 revision 전부 `0/1`이며 동일 digest의 검증된 revision 단독 100%·tag 없음이다. Production은 public, validation은 private이고 양 Invoker IAM check와 기존 identity 권한을 유지한다. G가 17:58:49–17:58:55Z 설정, 18:00:34–18:00:37Z 공개 health·경기·뉴스 200/안전한 400/docs·notification 404 및 validation 403을 확인했다. Repository enable=true, environment 동명 override 없음, active deploy 0이며 배포는 수동이다. 현재 Catalog compute와 실제 invoice는 [비용 검토](server-deployment-costs.md)에서 구분하며 512 MiB 후보 계산은 과거 기록이다.

직접 공개 endpoint의 XFF·Forwarded·앱 header·peer IP를 검증된 사용자 신원으로 사용하지 않는다. 프로세스별 한도는 분산 전역 한도가 아니며 한 사용자가 다른 사용자의 요청까지 거절되게 만들 수 있다. 플랫폼의 인스턴스 한도와 별도로 이 잔여 위험을 기록한다.

비용 중단은 repository enable=false와 production environment 동명 변수 부재 또는 false 확인 → 대기/진행 deploy가 있으면 취소·종료 확인 → production `allUsers` invoker 제거 → 양 service min0 → drain·default/존재 tag URL 무인증 거절 확인 순서다. 기존 immutable revision의 minimum 0을 먼저 전수 조회하며 template의 `--min-instances=0`만으로 기존 revision을 바꿀 수 없다. 양수 minimum이 남으면 traffic/tag와 실제 인스턴스를 확인해 절차를 조정한다. Environment 값이 true이거나 조회가 실패하면 먼저 해당 scope를 바로잡는다. Production service와 상속되는 project IAM에서 `allAuthenticatedUsers` invoker 부재를 중단 전후 확인하고, 중단 후 `allUsers` 호출 권한도 없어야 한다. 무인증 403만으로 인증된 외부 사용자의 권한까지 차단됐다고 판단하지 않으며 기존 운영 identity 권한은 보존한다. Validation은 끝까지 private/min0이다. IAM readback 외 상한을 둔 실제 403 확인이 필요하고, 표본 누락은 unknown이다. 최소값 0만으로 접근 차단·비용 0을 뜻하지 않으며 저장·log·늦은 청구가 남는다. 정상 revision 100%·production min1·public·외부 smoke·IAM/자원 확인 후 enable을 마지막에 복구한다.

이번 중단 첫 시도는 IAM 제거 직후 health 200으로 실패했다. 같은 차단 구간에서 양 default 403·min0을 확인하고 양수 baseline 세 revision의 개별 active+idle 1→0을 약 1,019초 뒤 확인한 후 정상 공개 복구했다. 미관측 revision 둘과 baseline 0 이후 표본 없는 하나를 0으로 합산하지 않았으며 서비스 전체 동시각 0·비용 0은 증명하지 않았다. Active deploy와 tag URL이 없어 실제 취소·tag 거절 대상도 없었다.

비용 대응은 **1만 원 점검·3만 원 추세 점검·5만 원 도달 또는 더 이른 초과 예상 시 수동 차단/중단**, 8·10만 원은 후속 경고로 앞당겼다. 월 10만 원 Budget과 10/30/50/80/100% 알림 resource는 변경하지 않았다. 실제 알림 수신·관측 청구액·Spend cap 활성화는 미확인이며 확인된 자동 상한은 없다. 보고·수신 지연과 전송·잔여 비용으로 5만 원 대응 여유도 소진될 수 있어 최대 10만 원을 보장하지 않는다.

원본 digest/revision/IAM·로그는 Git ignored 보호 경로 `.omx/evidence/issue111/20260911T164328Z/F/`, `G/`에 보존한다. 실제 stable URL은 공개 문서에 쓰지 않고 repository 외부 `~/.config/vlrgg-mobile/release-api-url`(디렉터리 0700/파일 0600)로만 인계한다. G의 공개 로그·diff 등 누출 검출은 0건이며 렌더링된 Actions summary를 직접 취득한 것은 아니다. 정확한 배포 summary writer와 성공 step을 확인·검사한 한계는 운영 결과에 남긴다.

관측 값은 고정 route·status class·거절 사유·활성 작업 수에 한정한다. 집계 summary는 `api`/`other`, `0xx`~`5xx`, stable error code, 고정 latency bucket을 name-to-count 형태로 출력한다. upstream failure counter는 실제 network·parsing failure만 포함하고 local 성공 JSON 제한의 `RESPONSE_TOO_LARGE`는 포함하지 않는다. 요청마다 달라지는 path·query·IP·token·HTML을 label로 쓰지 않으며 공격 요청 수에 비례하는 로그를 피한다.

## 완료 증거와 이슈별 책임

보호 계약은 유지하며 구현 완료와 실제 배포 완료를 각각 추적한다. #52 종료 전에는 다음을 확인한다.

1. 현재 성공·취소·안전 오류 회귀 및 한도·경합·직렬화 테스트.
2. 모든 대상 화면의 Busy mapping·대기 시간·단일 수동 재시도 검증.
3. packaged smoke와 배포 workflow 코드·정적/모의 검증, #110의 최종 커밋에 대한 실제 리뷰·지적 처리·CI 및 병합.
4. 잔여 배포 항목이 #111·#112의 완료 조건에 연결되고 관련 문서가 같은 범위를 반영함.

| 후속 이슈 | 이관한 실제 배포 완료 증거 |
| --- | --- |
| #111 Server | provider 비용·자원 실측, least-privilege 구성, 비공개 검증 service를 통한 후속 배포, 원격 health/query·notification 404, rollback, 비용 중단·복구, 기본 `run.app` HTTPS 공개 조회 |
| #112 App process | Release URL 주입·Fastlane/Actions·credential-free 검증과 future input runbook |
| #117 App release | 계정·앱 record·environment/secrets/signing/auth 연결, Android internal/TestFlight upload·receipt·tester·physical-device 조회 검증 |

#52 종료만으로 Stage 1 배포 완료를 선언하지 않는다. 계정·배포·서명·기기 증거가 없으면 #117은 미완료로 남긴다. #49·#62·#74와 Stage 2는 보류하며 향후 #117 설치 smoke도 전체 접근성/E2E 완료를 뜻하지 않는다. 기존 상세 실행 기록은 `.omx/plans/issue52-native-goal-execution.md`에 보존하고, 이후 서버 상태는 #111, 앱 process는 #112, 실제 앱 release는 #117에서 추적한다.
