# 조회 서버 배포 비용 검토 (#111)

검토일: 2026-09-07, 결정 갱신일: 2026-09-11. 조회 서버는 서울 `asia-northeast3` Cloud Run CPU 1/768 MiB로 운영한다. [PR115](https://github.com/KRMKGOLD/vlrgg-kr-2.0/pull/115) main `74a565ab959b1d5499405979a582aa5789625f4d`의 [CI 34625097062](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34625097062)·[deploy 34627000600](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/34627000600)는 success다. 실제 rollback·비용 중단 실패 후 drain/정상 공개 복구와 G 독립 검증은 PASS이며 [운영 결과](server-container-deployment.md)에 시각·최종 설정·한계를 기록했다. 아래 Catalog compute, 과거 전체 계획 가정, 실제 청구액을 구분한다. 관측 청구액·알림 수신·Spend cap 활성화는 미확인이다.

## 현재 768 MiB Catalog compute — 2026-09-11

B2가 Cloud Billing Catalog API에서 서울이 포함된 request-based Tier 2 SKU 네 개를 조회했다. 가격 entry는 `2026-09-11T07:00:00Z`부터 유효하며, KRW 변환 정보 약 1,383.28원/USD는 invoice 환율 보장이 아니다.

| 항목 | 현재 Catalog USD | 현재 Catalog KRW |
| --- | ---: | ---: |
| 활성 CPU | 0.000033600/vCPU초 | 0.046478207/vCPU초 |
| min instance 유휴 CPU | 0.000003500/vCPU초 | 0.004841479/vCPU초 |
| 활성·유휴 memory | 0.000003500/GiB초 | 0.004841479/GiB초 |

Production 1 vCPU/0.75 GiB, 30일 2,592,000초, min 1 가정의 compute만 계산했다. 기존 workload 입력은 active 518,400초, 나머지 idle이며 실제 월 사용량 예측이 아니다.

| 30일 가정 | 현재 KRW compute |
| --- | ---: |
| 한 instance가 계속 유휴 | 약 21,960.95원 |
| 기존 workload 입력 | 약 43,545.43원 |
| 한 instance가 계속 활성 | 약 129,883.35원 |

할인·무료 compute credit·세금·요청·전송·저장·log와 별도 validation 사용 비용을 반영하지 않은 계산이다. Validation min0도 배포·호출 중 비용은 발생한다. 과거 768 MiB 전체 계획 약 57,900원(세금 포함)은 당시 환율·전송·Artifact Registry 등 다른 SKU 가정을 섞은 시나리오이며 현재 Catalog compute와 합쳐 최신 총액이나 invoice로 제시하지 않는다. 실제 빌드는 GitHub Actions runner에서 수행하므로 Cloud Build 요금을 현재 배포에 적용하지 않는다.

B2/report.md와 budget-headroom-decision.md는 Git ignored 보호 경로 `.omx/evidence/issue111/20260911T121017Z/`에 보존한다. 실제 운영·독립 검증 원본은 `.omx/evidence/issue111/20260911T164328Z/F/`, `G/`에 있다. 공개 문서에 원본 digest/revision/IAM·URL/host·로그를 옮기지 않으며 실제 stable URL의 인계 경로는 repository 외부 `~/.config/vlrgg-mobile/release-api-url`(디렉터리 0700/파일 0600)다.

## 과거 후보 비교 — 2026-09-07

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

## 과거 512 MiB 계산과 잔여 위험

이 절의 1vCPU/512MiB 계산은 2026-09-07 당시 비교 기록이다. 실제 운영은 CPU 1, memory 768 MiB이므로 아래 약 54,300원을 현재 구성의 예측값으로 사용하지 않는다. 현재 Catalog compute는 앞 절에 기록했으며 계정 무료량과 전체 청구액은 미확인이다.

2026-09-07 기준 서울(`asia-northeast3`)은 Cloud Run Tier 2다. 공식 [Cloud Run 가격](https://cloud.google.com/run/pricing)에서 **Services (Requests-based billing)**의 지역 선택기를 `Seoul (asia-northeast3)`로 바꾸고, CUD 열이 아닌 `Default (USD)` 열을 사용한다. 이 조합의 활성 CPU는 0.0000336 USD/vCPU초, min instance 유휴 CPU는 0.0000035 USD/vCPU초, RAM(활성·유휴 모두)은 0.0000035 USD/GiB초다. 기본으로 렌더링되는 Iowa 표(0.000024/0.0000025)나 CUD 가격을 서울 온디맨드 견적에 사용하지 않는다.

아래 계산은 부동소수점이 아닌 Decimal 산술이며, 표시할 때만 반올림한다. `M = 30 × 24 × 60 × 60 = 2,592,000초`, `A = 518,400초`, `I = M - A = 2,073,600초`, `RAM = 0.5GiB`로 둔다. min 1이 한 달 내내 유휴인 기준선은 `M × 0.0000035 + M × 0.5 × 0.0000035 = 13.608 USD`다. 정상 시나리오 compute는 `A × 0.0000336 + I × 0.0000035 + M × 0.5 × 0.0000035 = 17.418240 + 7.257600 + 4.536000 = 29.211840 USD`다.

동시 요청의 처리 시간은 한 인스턴스에서 겹치는 구간의 합집합으로 계산한다. 32개 동시 요청이 각각 1초 걸렸다고 32초의 인스턴스 사용으로 곱하지 않는다. 반대로 빠른 429/503 응답은 계속 들어올 수 있으므로 concurrency 32가 초당 요청 수 32를 뜻하지 않는다. 진행 중 fetch 공유도 응답 전송량을 없애지 않는다.

한국 대상 전송을 기존 가정대로 0.19 USD/GiB로 두면 정상 응답 전송은 `(518,400 × 51,200 / 1,073,741,824) × 0.19 = 4.6966552734375 USD`다. 작은 upstream 요청 전송도 기존의 2% 가정을 유지하여 `4.6966552734375 × 0.02 = 0.09393310546875 USD`로 계산한다. Artifact Registry 1GiB 보관 중 무료 0.5GiB를 제외한 약 0.05 USD를 더하면 정상 총액은 `29.211840 + 4.6966552734375 + 0.09393310546875 + 0.050000 = 34.05242837890625 USD`, `34.05242837890625 × 1,450 × 1.10 = 54,313.62326435546875원`(약 54,300원)이다. 당시 build 60분·log 1GiB·기본 시스템 metrics는 해당 무료량 내를 가정했으나 실제 배포의 build는 GitHub runner이므로 Cloud Build 과금 근거로 사용하지 않는다. compute 무료 credit은 계정 적용을 확인하기 전 0으로 계산하고, 월 200만 건의 요청 무료량은 남아 있다고 가정했다. 무료량을 이미 사용했으면 다시 계산한다.

근거: [Cloud Run 과금](https://cloud.google.com/run/pricing), [네트워크 가격](https://cloud.google.com/vpc/pricing), [Artifact Registry](https://cloud.google.com/artifact-registry/pricing), [Cloud Build](https://cloud.google.com/build), [관측 비용](https://cloud.google.com/products/observability).

예를 들어 정상 월의 한 시간 동안 총 유입이 0.2회/초에서 **100회/초로 대체**되고 모든 응답이 50KiB라는 명시적 포화 시나리오를 둔다. 확정된 단일 warm server·overload rejection 계약에 따라 1vCPU/512MiB 인스턴스 **한 개**가 3,600초 활성이라고 계산한다. 이 한 시간의 정상 기준 compute는 `720 × 0.0000336 + 2,880 × 0.0000035 + 3,600 × 0.5 × 0.0000035 = 0.040572 USD`, 100회/초 compute는 `3,600 × 0.0000336 + 3,600 × 0.5 × 0.0000035 = 0.127260 USD`이므로 증분은 `0.086688 USD`다. 0.2회/초 정상분 720건을 대체한 추가 359,280건의 전송은 `(359,280 × 51,200 / 1,073,741,824) × 0.19 = 3.25504302978515625 USD`, 기존 2% upstream 가정은 `0.065100860595703125 USD`다. 따라서 이 명시적 시나리오의 증분은 `0.086688 + 3.25504302978515625 + 0.065100860595703125 = 3.406831890380859375 USD`, 즉 `× 1,450 × 1.10 = 5,433.896865157470703125원`(약 5,434원)이다. 누적 요청 877,680건은 기존 월 200만 건 request 무료량 가정 안에 있다. 별도 추가 인스턴스가 발생한다면 이 수치에 넣지 않은 잔여 위험이며, 실제 성공/거절 응답 크기, 플랫폼 로그, 무료량에 따라 달라진다. 이는 임의 공격에 대한 상한이 아니다.

## 로컬 측정 이력과 실제 운영 관측

macOS ARM64·Java 21에서 packaged 서버의 첫 health 응답은 약 1.2~1.4초, 20초 유휴 RSS는 약 140MiB였다. 별도 synthetic proxy 테스트는 평균 0.2회/초와 burst 4회를 재현했다. 이 테스트에는 production CIO·Jsoup·실제 DTO가 포함되지 않으므로 CPU/RAM을 그대로 Railway 견적이나 container 한도에 대입하지 않는다. `-Xmx512m` 역시 전체 RSS 512MiB 제한이 아니다.

사용자가 서버 개발과 부하 테스트를 완료로 판단했으므로 기존 측정을 다시 배포 선행 조건으로 두지 않는다.

F가 새 private 배포의 17:24Z CPU mean production 13.62%/validation 11.86%, memory 32.38%/32.47%와 17:25Z 양 새 revision active 1/idle 0을 관측했다. 조회창에 5xx series는 없었으나 오류 0이나 장기 공개 사용량을 보장하지 않는다. G는 17:58:49–17:58:55Z 최종 CPU 1/768 MiB·concurrency 32·timeout 30초·CPU throttling, production service min/max `1/1`·validation `0/1`·모든 revision `0/1`을 확인했다. 동일 digest·각 의도한 revision 100%·tag 없음, production public/validation private, enable=true·동명 environment override 없음·active deploy 0·수동 workflow 유지다. 18:00:34–18:00:37Z 공개 health·경기·뉴스 200, 안전한 400, docs/notification 404와 validation 403도 PASS다.

실제 중단에서는 IAM 제거 직후 health 200으로 첫 시도가 실패했고 같은 차단 구간에서 양 default 403·min0을 확인했다. 양수 baseline 세 revision의 개별 active+idle 1→0을 약 1,019초 뒤 확인한 후 새 revision 100%·production min1·public을 복구했다. 미관측 revision 둘과 baseline 0 이후 표본 없는 하나는 unknown이며 서비스 전체 동시각 0·비용 0을 증명하지 않는다. Active deploy와 tag URL이 없어 실제 취소·tag 거절은 실증하지 않았다. 최종 public smoke와 enable 마지막 복구는 17:50:41.853Z/17:50:49.416Z다.

이 관측은 invoice가 아니다. B2가 조회한 project 안에는 Billing export dataset/table이 없었고 Catalog·Budget API는 실제 accrued cost를 제공하지 않았다. 다른 project의 export나 Console 보고 존재 가능성은 배제하지 않는다. 기존 첫 private 배포와 #110은 역사 기록으로 유지하며 현재 실행은 [서버 배포 문서](server-container-deployment.md)를 따른다. #112는 앱 release process만, 실제 #117 설치 앱 검증은 별도다.

## 예산 중단

G가 project-scoped 월 100,000 KRW Budget과 CURRENT_SPEND 10/30/50/80/100% threshold resource를 새로 확인했으며 금액·알림 resource는 변경하지 않았다. 별도 Pub/Sub·Monitoring notification channel 지정은 없고 기본 IAM 수신자는 비활성화되지 않았으나 실제 발송·수신은 미확인이다. 관측 청구액·Spend cap 활성화도 미확인이고 확인된 자동 상한은 없다. 일반 Budget alert는 지출을 중단하지 않는다.

재검토한 기준은 **1만 원 비용 점검·3만 원 추세/원인 점검·5만 원 도달 또는 더 이른 초과 예상 시 수동 공개 차단/중단**, 8·10만 원은 중단 실패·지연의 후속 경고다. 기존 8만 원 중단 기준을 대체하며 월 목표 5만 원·최대 허용 10만 원은 hard cap 보장이 아니다. 앞당긴 5만 원 대응 여유도 보고·수신 지연, 사용량·전송·세금·잔여 비용으로 소진될 수 있다. 보고·알림의 보장된 최대 지연은 확인되지 않았다. Cloud Billing spend cap은 Preview이고 적용 지연·진행 요청·저장·log 등의 잔여 비용도 있어 최종 청구 상한으로 표현하지 않는다.

비용 중단은 repository `CLOUD_RUN_DEPLOY_ENABLED=false`와 production environment 동명 변수 부재 또는 false 확인 → 대기/진행 deploy가 있으면 취소·종료 확인 → production public invoker 제거 → 양 service min0 → drain·default/존재 tag URL 공개 거절 확인 순서다. 기존 immutable revision의 minimum 0을 먼저 전수 조회하며 `--min-instances=0`을 추가해도 기존 revision의 minimum은 바뀌지 않는다. 양수 minimum이 남으면 traffic/tag와 실제 인스턴스를 확인해 절차를 조정한다. IAM readback 외 실제 403을 상한 내 재확인하며 누락 표본은 unknown으로 둔다. Min0은 접근 차단·비용 0이 아니며 저장·log·늦은 청구가 남는다. 원인·비용을 확인한 뒤 기록한 production revision/digest 100%, production min/max `1/1`·revision min/max `0/1`, validation private/minimum 0, production public invoker와 외부 smoke를 복구하고 IAM·traffic·자원을 다시 확인한 다음 enable 변수를 **마지막**에 되돌린다. 자세한 보호 경계는 [공개 API 보호 계약](server-public-api-protection.md)을 따른다.

근거: [Cloud Billing budget](https://cloud.google.com/billing/docs/how-to/budgets), [Cloud Billing spend cap](https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps), [Cloud Run minimum instances](https://docs.cloud.google.com/run/docs/configuring/min-instances).
