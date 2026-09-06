# #52 배포 비용 검토

검토일: 2026-09-06. 결제 계정 견적이나 실제 청구액이 아닌 후보 비교다. **provider는 아직 확정하지 않았다.** 서울 리전은 필수 요구사항이 아니며 싱가포르 후보도 비용과 응답 지연으로 비교한다.

## 같은 조건으로 비교하기

평소 최소 서버 한 개를 유지하고 일반 조회 DB·알림·별도 WAF는 사용하지 않는다. 계산은 30일, USD 1 = 1,450원, 세금 10%를 가정한다. 환율과 세금은 실제 결제 시 확인해야 한다.

정상 시나리오는 평균 0.2회/초, 요청 1초, 응답 50KiB다. 이는 사용자 트래픽 예측이 아니라 비교용 입력이다. 월 요청은 518,400건, 응답은 24.72GiB(26.54 decimal GB)가 된다.

| 후보 | 비용 구조 | 정상 시나리오 예시 | 미확인 조건 |
| --- | --- | --- | --- |
| Cloud Run 서울, request-based, 1vCPU/512MiB, min 1 | 활성·유휴 시간과 메모리, 요청, 전송, 부대비용 | compute 무료 credit을 보수적으로 제외하면 약 47,700원 | 실제 활성 시간·응답 크기·계정 무료량·container 메모리 |
| Railway Hobby, warm JVM | `max(5, 20×C + 10×M + 0.05×E)` USD | 평균 CPU 0.1개, RAM 0.5GB, 전송 26.54GB 가정 시 약 13,300원 | 실제 C/M, 리전 지연, 선택 구성의 추가 비용 |
| Render Starter, 0.5CPU/512MB | 공개된 compute 월 7 USD와 전송 등 | compute만 약 11,200원 | 512MB 적합성, 현재 workspace 전송 포함량·초과 비용 |
| Render Standard, 1CPU/2GB | 공개된 compute 월 25 USD와 전송 등 | compute만 약 39,900원 | 전송·기타 부대비용 |

Railway의 C는 예약 CPU가 아니라 평균 **실제 사용 vCPU**, M은 평균 사용 RAM(GB), E는 전송량(GB)다. 위 0.1/0.5는 실측값이 아니다. Render 공식 비교 글과 workspace 전송 문서의 포함량이 달라 결제 화면 확인 전 compute 가격만으로 총액을 확정하지 않는다.

근거: [Cloud Run 가격](https://cloud.google.com/run/pricing), [Railway 요금](https://docs.railway.com/pricing/plans), [Railway 비용 제어](https://docs.railway.com/pricing/cost-control), [Render compute](https://render.com/docs/compute-plans), [Render 공개 비교 가격](https://render.com/articles/render-vs-railway), [Render 전송 비용](https://render.com/docs/outbound-bandwidth).

## Cloud Run 계산과 잔여 위험

서울 Tier 2 계산은 활성 CPU 0.0000288 USD/vCPU초, 유휴 CPU 0.000003 USD/vCPU초, RAM 0.000003 USD/GiB초를 사용한다. 512MiB·한 달 대기 시 compute는 무료 credit 전 11.664 USD다. 위 정상 시나리오는 활성 518,400초·유휴 2,073,600초로 compute 25.0387 USD가 된다.

동시 요청의 처리 시간은 한 인스턴스에서 겹치는 구간의 합집합으로 계산한다. 32개 동시 요청이 각각 1초 걸렸다고 32초의 인스턴스 사용으로 곱하지 않는다. 반대로 빠른 429/503 응답은 계속 들어올 수 있으므로 concurrency 32가 초당 요청 수 32를 뜻하지 않는다. 진행 중 fetch 공유도 응답 전송량을 없애지 않는다.

한국 대상 전송을 0.19 USD/GiB로 가정하면 정상 응답 전송 4.6966 USD, 작은 upstream 요청 전송 약 0.0939 USD가 추가된다. Artifact Registry 1GiB 보관 중 무료 0.5GiB를 제외한 약 0.05 USD도 포함했다. build 60분·log 1GiB·기본 시스템 metrics는 해당 무료량 내를 가정했다. compute 무료 credit은 계정 적용을 확인하기 전 0으로 계산하고, 월 200만 건의 요청 무료량은 남아 있다고 가정했다. 무료량을 이미 사용했으면 다시 계산한다.

근거: [Cloud Run 과금](https://cloud.google.com/run/pricing), [네트워크 가격](https://cloud.google.com/vpc/pricing), [Artifact Registry](https://cloud.google.com/artifact-registry/pricing), [Cloud Build](https://cloud.google.com/build), [관측 비용](https://cloud.google.com/products/observability).

예를 들어 정상 월에 100회/초 유입이 한 시간 추가되고 모든 응답이 50KiB라는 **명시적 시나리오**는 기존 정상 시간 대비 약 5,300원 이상의 알려진 비용을 추가한다. 실제 성공/거절 응답 크기, 플랫폼 로그, 무료량과 추가 인스턴스에 따라 달라진다. 이는 임의 공격에 대한 상한이 아니다.

## 현재 실측과 결정 순서

macOS ARM64·Java 21에서 packaged 서버의 첫 health 응답은 약 1.2~1.4초, 20초 유휴 RSS는 약 140MiB였다. 별도 synthetic proxy 테스트는 평균 0.2회/초와 burst 4회를 재현했다. 이 테스트에는 production CIO·Jsoup·실제 DTO가 포함되지 않으므로 CPU/RAM을 그대로 Railway 견적이나 container 한도에 대입하지 않는다. `-Xmx512m` 역시 전체 RSS 512MiB 제한이 아니다.

1. 보호 구현 후 실제 route와 fixture transport로 CPU·RSS·성공/거절 응답 크기를 측정한다.
2. 계정 무료량을 제외한 보수적 비용과 정상·포화 비용을 다시 계산한다. 월 5만 원 목표와 비상 대응 여유를 평가한다.
3. 낮은 실사용 CPU/RAM이라면 Railway를 우선 비교하고, Render 전송 조건을 확인한다. Cloud Run을 기존 설계였다는 이유만으로 확정하지 않는다.
4. 선택한 환경에서 작은 비공개 배포로 지연·container 메모리·중단·복구를 검증한 뒤 공개한다.

현재 환경에는 GCP 결제·실제 provider invoice·지역 배포·물리 기기 설치 증거가 없다. 코드 구현은 계속하되 공개 배포 완료로 보고하지 않는다.

## 예산 중단

5만 원 경고·8만 원 중단은 잠정값이다. 관측·집행 지연 동안의 비용과 진행 작업·저장·log·세금을 고려하여 10만 원까지의 여유가 충분한지 검증해야 한다. Cloud Billing spend cap은 Preview이며 지연과 잔여 비용이 존재한다. Railway usage limit 역시 실제 계정에서 중단 범위와 결과를 검증해야 한다.

Cloud Run을 선택한다면 공개 호출 차단 → 모든 service/revision minimum 0 → drain → default/tagged URL 거절을 확인한다. minimum 0만 설정하면 외부 요청이 다시 기동할 수 있다. 자동 배포가 중단 설정을 되돌리지 않게 하고 원인·비용 검토 후 수동 복구한다. 자세한 보호 경계는 [공개 API 보호 계약](server-public-api-protection.md)을 따른다.

근거: [Cloud Billing spend cap](https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps), [Cloud Run minimum instances](https://docs.cloud.google.com/run/docs/configuring/min-instances), [Railway usage limits](https://docs.railway.com/pricing/cost-control).
