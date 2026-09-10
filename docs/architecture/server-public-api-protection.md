# 공개 조회 API 보호와 설치 앱 배포 (#52)

상태: 구현 진행 중. 아래 수치는 초기 검증 기준이며 운영 실측값이나 배포 완료 증거가 아니다.

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
| 요청 시간 | upstream 10초 < 서버 전체 15초 < 플랫폼 20초 < 앱 30초 | 안전한 오류와 작업 취소 |
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

조회 서버는 후속 FCM·Firestore 운영 경로까지 고려해 서울 `asia-northeast3`의 Cloud Run에 배포한다. 초기 후보는 request-based billing, CPU 1, memory 768 MiB, service min/max `1/1`이며, 768 MiB 적합성과 비용은 private 첫 배포의 기본 지표로 확인한다. 기존 512 MiB 후보 비교는 과거 계산으로 유지하며 Cloud Run이 최저가라고 주장하지 않는다.

직접 공개 endpoint의 XFF·Forwarded·앱 header·peer IP를 검증된 사용자 신원으로 사용하지 않는다. 프로세스별 한도는 분산 전역 한도가 아니며 한 사용자가 다른 사용자의 요청까지 거절되게 만들 수 있다. 플랫폼의 인스턴스 한도와 별도로 이 잔여 위험을 기록한다.

비용 중단은 공개 호출 차단, service와 revision의 minimum 0, 진행 작업 drain, default/tagged URL 거절 확인까지 포함한다. minimum 0만 설정하면 공개 요청으로 재기동할 수 있다. build·저장·log 비용도 별도 확인한다. 재배포가 중단 상태를 자동 해제하지 않게 하고 원인과 비용 확인 후 수동 복구한다.

경고 5만 원·중단 8만 원은 잠정값이다. 최대 예상 소모율과 관측·집행 지연, 진행 작업, 부대요금·세금을 계산하여 남은 2만 원 안에 대응 여유가 있는지 확인한다. 지연 근거와 대응 여유가 불충분하면 trigger 또는 provider를 재검토하기 전 공개하지 않는다.

관측 값은 고정 route·status class·거절 사유·활성 작업 수에 한정한다. 집계 summary는 `api`/`other`, `0xx`~`5xx`, stable error code, 고정 latency bucket을 name-to-count 형태로 출력한다. upstream failure counter는 실제 network·parsing failure만 포함하고 local 성공 JSON 제한의 `RESPONSE_TOO_LARGE`는 포함하지 않는다. 요청마다 달라지는 path·query·IP·token·HTML을 label로 쓰지 않으며 공격 요청 수에 비례하는 로그를 피한다.

## 완료 증거

코드와 배포 완료를 구분한다. 다음 증거가 모두 있어야 #52를 완료한다.

1. 현재 성공·취소·안전 오류 회귀 및 한도·경합·직렬화 테스트.
2. 모든 대상 화면의 Busy mapping·대기 시간·단일 수동 재시도 검증.
3. provider 비용표, 자원 실측, least-privilege 배포 구성과 비상 중단·복구 검증.
4. packaged 및 원격 health 200·notification 404, 배포 전 smoke와 rollback.
5. 서명된 Android와 iOS 앱의 실제 기기 fresh install, 외부망 조회와 오류 복구.

계정·결제·서명·기기 증거가 없으면 해당 release gate는 미완료로 남긴다. FCM production smoke와 이번 일반 조회 release gate를 혼동하지 않는다. 상세 실행 상태와 테스트별 증거는 `.omx/plans/issue52-native-goal-execution.md`에서 관리한다.
