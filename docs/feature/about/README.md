# About 기능 기획

## 구현 상태 (2026-08-31)

- **Backend: 해당 없음.** About은 정적 앱 정보와 외부 Source Code 링크만 사용하며 별도 API/scraping이 없다.
- **App: A1 구현 및 자동 검증 완료.** About은 다섯 번째 root destination으로 렌더링되고, Android/iOS runtime owner가 실제 build metadata와 external-open/copy 경계를 제공한다. Android host test, iOS simulator test, Android Debug build, iOS simulator Xcode build를 통과했다.
- **실기기 검증은 미완료.** Android/iOS의 실제 external-open, clipboard, screen reader 동작은 이 문서의 자동 검증 완료 범위에 포함하지 않는다.

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
| Populated | 빌드 버전과 확정된 Source Code 링크를 포함한 모든 정보 영역을 표시한다. |
| Source Link Error | 외부 앱에서 Source Code 링크를 실행할 수 없으면 모든 정보를 유지하고 Snackbar에 `링크 복사` action을 제공한다. |
| Version unavailable | 버전 조회가 실패하면 해당 값만 안전한 unavailable copy로 대체하며 전체 화면 오류로 만들지 않는다. |
| Static availability | About은 원격 조회가 없으므로 독립 Loading/Empty/Stale screen을 만들지 않는다. platform 버전 정보 준비가 비동기라면 해당 값만 안정적인 placeholder로 표시한다. |

## Stitch 오류 상태 적용 판정

`About Source Link Error` Stitch 시안은 inverse-surface Snackbar와 compact geometry를 기준으로 사용했다. 다만 시안의 `재시도` action은 GitHub #47, RALPLAN A1, 이 문서의 recovery 계약과 충돌하므로, 동작과 label은 `링크 복사`를 우선 적용했다. canonical Stitch 원본은 수정하지 않는다.

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
- 실패한 외부 이동을 raw platform 오류 없이 Snackbar로 안내하고 `링크 복사` recovery를 제공한다.
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
- [x] About은 Feedback 액션을 표시하지 않는다.
- [x] 외부 이동을 처리할 앱이 없거나 실행이 실패해도 모든 콘텐츠를 유지하고 Snackbar와 `링크 복사` recovery를 표시한다.
- [x] Theme 영역은 Light만 현재 지원됨을 표시하고 Dark Mode가 MVP에 포함된 것처럼 동작하지 않는다.
- [x] VLR.GG 데이터 출처와 비공식·개인 프로젝트 문맥을 명확히 표시한다.
- [x] About은 별도 server API를 호출하지 않는다.
- [x] Top App Bar에서 Search를 열고 Back하면 About으로 복귀한다.
- [ ] screen reader가 외부 링크의 목적과 외부 이동임을 식별할 수 있다.

## 후속 배포 검토

- 법적 고지, 개인정보 처리방침, 오픈소스 라이선스 목록이 필요한 배포 채널을 선택하면 About 또는 별도 화면의 범위를 갱신한다.
