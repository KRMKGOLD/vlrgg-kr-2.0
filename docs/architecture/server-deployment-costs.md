# 조회 서버 배포 비용 검토 (#111)

검토일: 2026-09-07, 결정 갱신일: 2026-09-10. 아래 표와 계산은 결제 계정 견적이나 실제 청구액이 아닌 당시 후보 비교다. 조회 서버 provider는 후속 FCM·Firestore 운영 경로까지 고려해 서울 `asia-northeast3`의 Cloud Run으로 선택했다. GCP 계정·프로젝트·결제와 첫 비공개 배포는 완료했으며 실제 청구액과 현재 구성의 운영 비용 확인은 [#111](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/111)에 남아 있다.

## 같은 조건으로 비교하기

평소 최소 서버 한 개를 유지하고 일반 조회 DB·알림·별도 WAF는 사용하지 않는다. 계산은 30일, USD 1 = 1,450원, 세금 10%를 가정한다. 환율과 세금은 실제 결제 시 확인해야 한다.

정상 시나리오는 평균 0.2회/초, 요청 1초, 응답 50KiB다. 이는 사용자 트래픽 예측이 아니라 비교용 입력이다. 월 요청은 518,400건, 응답은 24.72GiB(26.54 decimal GB)가 된다.

| 후보 | 비용 구조 | 정상 시나리오 예시 | 미확인 조건 |
| --- | --- | --- | --- |
| Cloud Run 서울, request-based, 1vCPU/512MiB, min 1 | 활성·유휴 시간과 메모리, 요청, 전송, 부대비용 | compute 무료 credit을 보수적으로 제외하면 약 54,300원 | 실제 활성 시간·응답 크기·계정 무료량·container 메모리 |
| Railway Hobby, warm JVM | `max(5, 20×C + 10×M + 0.05×E)` USD | 평균 CPU 0.1개, RAM 0.5GB, 전송 26.54GB 가정 시 약 13,300원 | 실제 C/M, 리전 지연, 선택 구성의 추가 비용 |
| Render Starter, 0.5CPU/512MB | 공개된 compute 월 7 USD와 전송 등 | compute만 약 11,200원 | 512MB 적합성, 현재 workspace 전송 포함량·초과 비용 |
| Render Standard, 1CPU/2GB | 공개된 compute 월 25 USD와 전송 등 | compute만 약 39,900원 | 전송·기타 부대비용 |

Railway의 C는 예약 CPU가 아니라 평균 **실제 사용 vCPU**, M은 평균 사용 RAM(GB), E는 전송량(GB)다. 위 0.1/0.5는 실측값이 아니다. Render 공식 비교 글과 workspace 전송 문서의 포함량이 달라 결제 화면 확인 전 compute 가격만으로 총액을 확정하지 않는다.

근거: [Cloud Run 가격](https://cloud.google.com/run/pricing), [Railway 요금](https://docs.railway.com/pricing/plans), [Railway 비용 제어](https://docs.railway.com/pricing/cost-control), [Render compute](https://render.com/docs/compute-plans), [Render 공개 비교 가격](https://render.com/articles/render-vs-railway), [Render 전송 비용](https://render.com/docs/outbound-bandwidth).

## Cloud Run 계산과 잔여 위험

이 절의 1vCPU/512MiB 계산은 2026-09-07 당시 비교 기록이다. 첫 비공개 배포는 CPU 1, memory 768 MiB로 기동·조회에 성공했으므로 아래 약 54,300원을 그 구성의 예측값으로 사용하지 않는다. 계정 요금·무료량과 원격 지표를 확인한 뒤 768 MiB 운영 service와 minimum 0 검증 service의 비용을 반영해 견적을 갱신한다.

2026-09-07 기준 서울(`asia-northeast3`)은 Cloud Run Tier 2다. 공식 [Cloud Run 가격](https://cloud.google.com/run/pricing)에서 **Services (Requests-based billing)**의 지역 선택기를 `Seoul (asia-northeast3)`로 바꾸고, CUD 열이 아닌 `Default (USD)` 열을 사용한다. 이 조합의 활성 CPU는 0.0000336 USD/vCPU초, min instance 유휴 CPU는 0.0000035 USD/vCPU초, RAM(활성·유휴 모두)은 0.0000035 USD/GiB초다. 기본으로 렌더링되는 Iowa 표(0.000024/0.0000025)나 CUD 가격을 서울 온디맨드 견적에 사용하지 않는다.

아래 계산은 부동소수점이 아닌 Decimal 산술이며, 표시할 때만 반올림한다. `M = 30 × 24 × 60 × 60 = 2,592,000초`, `A = 518,400초`, `I = M - A = 2,073,600초`, `RAM = 0.5GiB`로 둔다. min 1이 한 달 내내 유휴인 기준선은 `M × 0.0000035 + M × 0.5 × 0.0000035 = 13.608 USD`다. 정상 시나리오 compute는 `A × 0.0000336 + I × 0.0000035 + M × 0.5 × 0.0000035 = 17.418240 + 7.257600 + 4.536000 = 29.211840 USD`다.

동시 요청의 처리 시간은 한 인스턴스에서 겹치는 구간의 합집합으로 계산한다. 32개 동시 요청이 각각 1초 걸렸다고 32초의 인스턴스 사용으로 곱하지 않는다. 반대로 빠른 429/503 응답은 계속 들어올 수 있으므로 concurrency 32가 초당 요청 수 32를 뜻하지 않는다. 진행 중 fetch 공유도 응답 전송량을 없애지 않는다.

한국 대상 전송을 기존 가정대로 0.19 USD/GiB로 두면 정상 응답 전송은 `(518,400 × 51,200 / 1,073,741,824) × 0.19 = 4.6966552734375 USD`다. 작은 upstream 요청 전송도 기존의 2% 가정을 유지하여 `4.6966552734375 × 0.02 = 0.09393310546875 USD`로 계산한다. Artifact Registry 1GiB 보관 중 무료 0.5GiB를 제외한 약 0.05 USD를 더하면 정상 총액은 `29.211840 + 4.6966552734375 + 0.09393310546875 + 0.050000 = 34.05242837890625 USD`, `34.05242837890625 × 1,450 × 1.10 = 54,313.62326435546875원`(약 54,300원)이다. build 60분·log 1GiB·기본 시스템 metrics는 해당 무료량 내를 가정했다. compute 무료 credit은 계정 적용을 확인하기 전 0으로 계산하고, 월 200만 건의 요청 무료량은 남아 있다고 가정했다. 무료량을 이미 사용했으면 다시 계산한다.

근거: [Cloud Run 과금](https://cloud.google.com/run/pricing), [네트워크 가격](https://cloud.google.com/vpc/pricing), [Artifact Registry](https://cloud.google.com/artifact-registry/pricing), [Cloud Build](https://cloud.google.com/build), [관측 비용](https://cloud.google.com/products/observability).

예를 들어 정상 월의 한 시간 동안 총 유입이 0.2회/초에서 **100회/초로 대체**되고 모든 응답이 50KiB라는 명시적 포화 시나리오를 둔다. 확정된 단일 warm server·overload rejection 계약에 따라 1vCPU/512MiB 인스턴스 **한 개**가 3,600초 활성이라고 계산한다. 이 한 시간의 정상 기준 compute는 `720 × 0.0000336 + 2,880 × 0.0000035 + 3,600 × 0.5 × 0.0000035 = 0.040572 USD`, 100회/초 compute는 `3,600 × 0.0000336 + 3,600 × 0.5 × 0.0000035 = 0.127260 USD`이므로 증분은 `0.086688 USD`다. 0.2회/초 정상분 720건을 대체한 추가 359,280건의 전송은 `(359,280 × 51,200 / 1,073,741,824) × 0.19 = 3.25504302978515625 USD`, 기존 2% upstream 가정은 `0.065100860595703125 USD`다. 따라서 이 명시적 시나리오의 증분은 `0.086688 + 3.25504302978515625 + 0.065100860595703125 = 3.406831890380859375 USD`, 즉 `× 1,450 × 1.10 = 5,433.896865157470703125원`(약 5,434원)이다. 누적 요청 877,680건은 기존 월 200만 건 request 무료량 가정 안에 있다. 별도 추가 인스턴스가 발생한다면 이 수치에 넣지 않은 잔여 위험이며, 실제 성공/거절 응답 크기, 플랫폼 로그, 무료량에 따라 달라진다. 이는 임의 공격에 대한 상한이 아니다.

## 현재 실측과 실행 순서

macOS ARM64·Java 21에서 packaged 서버의 첫 health 응답은 약 1.2~1.4초, 20초 유휴 RSS는 약 140MiB였다. 별도 synthetic proxy 테스트는 평균 0.2회/초와 burst 4회를 재현했다. 이 테스트에는 production CIO·Jsoup·실제 DTO가 포함되지 않으므로 CPU/RAM을 그대로 Railway 견적이나 container 한도에 대입하지 않는다. `-Xmx512m` 역시 전체 RSS 512MiB 제한이 아니다.

사용자가 서버 개발과 부하 테스트를 완료로 판단했으므로 기존 측정을 다시 배포 선행 조건으로 두지 않는다.

1. 연결된 GCP 프로젝트·결제의 서울 Cloud Run·Artifact Registry 계정 요금과 무료량을 확인한다.
2. 기동한 CPU 1, memory 768 MiB, service min/max `1/1` private revision의 idle/대표 조회 CPU·memory와 전송량을 확인한다.
3. 확인한 값으로 월 5만 원 목표와 최대 10만 원 대응 여유를 다시 계산한다. 필요하면 memory만 실제 기동 여유 안에서 조정한다.
4. 예산 알림·Spend cap과 수동 비용 중단·복구를 검증한 뒤 공개한다.

첫 비공개 배포 증거는 [서버 배포 문서](server-container-deployment.md)에 있다. 실제 provider invoice, 운영 비용 산정과 비용 중단·복구는 #111에서 확인하며 공개 배포 완료로 보고하지 않는다. 물리 기기 설치는 #112에서 별도로 검증한다.

## 예산 중단

월 지출 알림은 1만·3만·5만·8만·10만 원으로 설정됐다. 8만 원 중단은 잠정 대응 기준이며 Spend cap 자동 중단의 활성화 증거는 없다. 관측·집행 지연 동안의 비용과 진행 작업·저장·log·세금을 고려하여 10만 원까지의 여유가 충분한지 #111에서 검증해야 한다. 일반 budget alert는 지출을 중단하지 않는다. Cloud Billing spend cap은 Preview이며 적용 지연과 진행 요청·Artifact Registry·log 등 잔여 비용이 존재하므로 고정된 최종 청구 상한으로 표현하지 않는다.

비용 중단은 GitHub `CLOUD_RUN_DEPLOY_ENABLED=false` → 진행 중인 배포 취소·종료 확인 → public invoker 제거 → 모든 service/revision minimum 0 → drain → default/tagged URL 공개 거절 확인 순서로 수행한다. minimum 0만 설정하면 외부 요청이 다시 기동할 수 있다. 원인·비용을 확인한 뒤 minimum 1과 public invoker를 복구하고 smoke를 통과한 다음 enable 변수를 마지막에 되돌린다. 자세한 보호 경계는 [공개 API 보호 계약](server-public-api-protection.md)을 따른다.

근거: [Cloud Billing budget](https://cloud.google.com/billing/docs/how-to/budgets), [Cloud Billing spend cap](https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps), [Cloud Run minimum instances](https://docs.cloud.google.com/run/docs/configuring/min-instances).
