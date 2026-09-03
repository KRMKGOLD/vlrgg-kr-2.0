# About 기능 기획

## 구현 상태 (2026-09-03)

- **Backend: 해당 없음.** About은 정적 앱 정보와 외부 Source Code 링크만 사용하며 별도 API/scraping이 없다.
- **App: A1 기본 구현과 #47 refinement의 자동 검증이 완료되었다.** About은 다섯 번째 root destination이며 Android/iOS runtime owner가 build metadata와 external-open 경계를 제공한다. metadata가 없으면 version UI를 생략하고, source-link 실패는 action 없는 짧은 Snackbar로 알린다. 이 시간은 text-only 안내에 맞춰 accessibility 권장 timeout으로 조정되며, About을 벗어난 뒤 늦게 도착한 platform callback은 무시한다.
- **실기기 검증은 미완료.** Android/iOS의 실제 external-open과 screen reader 동작은 이 문서의 자동 검증 완료 범위에 포함하지 않는다.

## 목적과 사용자 가치

About은 앱의 목적과 현재 버전, 코드 공개 위치, 테마 지원 범위, 데이터 출처를 투명하게 안내한다. 사용자는 이 앱이 VLR.GG의 공식 앱이 아닌 개인 포트폴리오 프로젝트임을 이해하고 공개된 source code를 확인할 수 있다.

## 1차 MVP 범위

- 앱 소개
- 앱 버전
- source code 외부 링크
- Theme 정보/설정 영역
  - 현재 선택 가능한 Theme은 Light 하나
  - Dark Mode가 추후 계획임을 안내
- VLR.GG 데이터 출처 표기
- 비공식·개인 프로젝트 문맥 고지

## 명시적 제외 범위

- Dark Mode 선택 및 적용
- 계정, 로그인, 프로필 설정
- 피드백 액션과 앱 내 문의 기능
- 개인정보·오픈소스 라이선스·법적 문서 전문 화면은 실제 배포 요구가 생기기 전까지 별도 범위로 둔다.
- 원격 설정으로 About 콘텐츠를 변경하는 기능

## 진입과 이탈 경로

### 진입

- Bottom Navigation의 다섯 번째 `About` 항목

### 이탈

- Top App Bar의 Search → 별도 Search Screen
- source code → 외부 브라우저 또는 대응 앱
- 다른 Bottom Navigation 항목 → 해당 root destination
- Search에서 Back → 이전 About 상태

외부 링크를 열 수 있는 앱이 없거나 실행에 실패하면 About 화면에 남아 안전한 안내를 제공한다.

## 화면과 콘텐츠 계층

1. Shared Top App Bar
   - About 제목
   - Search
2. App identity
   - 앱 이름
   - 간략한 소개
   - 버전
3. Project links
   - Source Code
4. Appearance
   - 현재 Theme: Light
   - Dark Mode 추후 지원 안내
5. Attribution
   - VLR.GG를 데이터 출처로 사용한다는 설명
   - VLR.GG 공식 앱이 아닌 개인 프로젝트라는 고지

정보성 화면이므로 과도한 카드 중첩이나 마케팅형 hero를 피하고, 외부 이동 액션과 고지를 명확히 구분한다.

## 표시 데이터

| 영역 | 표시 데이터 |
| --- | --- |
| App | 앱 이름, 소개 문구, 현재 버전 |
| Links | source repository URL |
| Theme | 현재 지원 Theme `Light`, Dark Mode deferred 안내 |
| Attribution | VLR.GG 출처, 비공식·개인 프로젝트 고지 |

버전은 빌드 설정의 실제 앱 버전을 표시하며 문서에 정적 버전 값을 중복 보관하지 않는다. Source Code는 `https://github.com/KRMKGOLD/vlrgg-kr-2.0`을 연다.

## 화면 상태

| 상태 | 동작 |
| --- | --- |
| Populated | 유효한 build version이 있을 때만 version chip과 확정된 Source Code 링크를 포함한 정보 영역을 표시한다. |
| Source Link Error | 외부 앱에서 Source Code 링크를 실행할 수 없으면 모든 정보를 유지하고 정확히 `소스 코드를 열 수 없습니다.`만 보이는 action 없는 짧은 Snackbar를 자동으로 닫는다. 표시 시간은 text-only 안내를 기준으로 accessibility 권장 timeout을 적용한다. Source Code row는 남아 다음 사용자 탭으로 재시도할 수 있다. |
| Version unavailable | version이 null·blank·unavailable이면 unavailable copy와 빈 version chip을 모두 생략하며 App identity는 유지한다. |
| Static availability | About은 원격 조회가 없으므로 독립 Loading/Empty/Stale screen이나 version placeholder를 만들지 않는다. |

## Stitch 오류 상태 적용 판정

canonical `About — Source Link Error`는 inverse-surface compact Snackbar를 유지하면서 `소스 코드를 열 수 없습니다.`만 표시하도록 정렬한다. `재시도`, `링크 복사`, 닫기 및 그 밖의 action은 없으며, canonical `About — Populated`의 정보 구조는 변경하지 않는다.

## 사용자 인터랙션

- Source Code를 누르면 외부 브라우저 또는 설치된 대응 앱으로 repository를 연다.
- 외부 이동 전 목적지를 사용자가 이해할 수 있는 label과 icon으로 표시한다.
- Light Theme은 유일한 선택지로 표시하며 Dark Mode를 선택 가능한 disabled control처럼 오인시키지 않는다.
- Search는 현재 화면 위에 push되고 Back 시 About으로 복귀한다.

## 앱·서버 책임 경계

### 앱

- 앱 소개와 attribution 문구를 제품 리소스로 제공한다.
- 실제 build version을 platform/shared boundary를 통해 표시한다.
- Source Code 외부 이동을 platform 방식으로 처리한다.
- 실패한 외부 이동을 raw platform 오류 없이 action 없는 짧은 Snackbar로 안내하고, text-only 안내의 accessibility 권장 timeout이 지난 뒤 사용자가 Source Code row를 다시 눌러 재시도하게 한다.
- Theme 영역은 Light-only MVP 계약을 따른다.

### 서버

- About을 위해 별도 API나 scraping을 제공하지 않는다.

## Upstream 및 외부 링크 메모

- About은 VLR.GG 콘텐츠를 scraping하지 않는다.
- source repository는 `https://github.com/KRMKGOLD/vlrgg-kr-2.0`을 사용한다.
- VLR.GG 출처 고지는 공식 제휴나 허가를 의미하지 않는다.
- 외부 정책과 배포 요구에 따라 필요한 추가 고지 문구는 출시 전 별도 검토한다.

## 테스트 가능한 수용 기준

- [x] About은 Bottom Navigation의 다섯 번째 root destination이다.
- [x] 앱 소개, 실제 build version, Source Code, Theme, attribution 영역을 표시한다.
- [ ] Source Code 액션은 `https://github.com/KRMKGOLD/vlrgg-kr-2.0`을 외부 앱으로 연다. (실기기 external-open 검증 필요)
- [x] About failure Snackbar는 정확한 오류 문구만 표시하고 `재시도`, `링크 복사`, 닫기 등 action을 표시하지 않으며, 짧은 base time을 text-only accessibility 권장 timeout으로 조정한 뒤 자동으로 사라진다.
- [x] 외부 이동을 처리할 앱이 없거나 실행이 실패해도 모든 콘텐츠와 Source Code row를 유지하고, About disposal 뒤 늦게 도착한 callback이나 화면 복귀 시 만료된 Snackbar를 다시 표시하지 않는다.
- [x] Theme 영역은 Light만 현재 지원됨을 표시하고 Dark Mode가 MVP에 포함된 것처럼 동작하지 않는다.
- [x] VLR.GG 데이터 출처와 비공식·개인 프로젝트 문맥을 명확히 표시한다.
- [x] About은 별도 server API를 호출하지 않는다.
- [x] Top App Bar에서 Search를 열고 Back하면 About으로 복귀한다.
- [ ] screen reader가 외부 링크의 목적과 외부 이동임을 식별할 수 있다.

## 후속 배포 검토

- 법적 고지, 개인정보 처리방침, 오픈소스 라이선스 목록이 필요한 배포 채널을 선택하면 About 또는 별도 화면의 범위를 갱신한다.
