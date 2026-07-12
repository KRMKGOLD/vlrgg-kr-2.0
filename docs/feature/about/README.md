# About 기능 기획

## 목적과 사용자 가치

About은 앱의 목적과 현재 버전, 코드 공개 위치, 피드백 경로, 테마 지원 범위, 데이터 출처를 투명하게 안내한다. 사용자는 이 앱이 VLR.GG의 공식 앱이 아닌 개인 포트폴리오 프로젝트임을 이해하고 필요한 외부 채널로 이동할 수 있다.

## 1차 MVP 범위

- 앱 소개
- 앱 버전
- source code 외부 링크
- 피드백 액션
  - 이메일 전송 또는 GitHub Issues
- Theme 정보/설정 영역
  - 현재 선택 가능한 Theme은 Light 하나
  - Dark Mode가 추후 계획임을 안내
- VLR.GG 데이터 출처 표기
- 비공식·개인 프로젝트 문맥 고지

## 명시적 제외 범위

- Dark Mode 선택 및 적용
- 계정, 로그인, 프로필 설정
- 앱 내 GitHub Issue 작성 폼
- 개인정보·오픈소스 라이선스·법적 문서 전문 화면은 실제 배포 요구가 생기기 전까지 별도 범위로 둔다.
- 원격 설정으로 About 콘텐츠를 변경하는 기능

## 진입과 이탈 경로

### 진입

- Bottom Navigation의 다섯 번째 `About` 항목

### 이탈

- Top App Bar의 Search → 별도 Search Screen
- source code → 외부 브라우저 또는 대응 앱
- feedback → 이메일 앱 또는 GitHub Issues
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
   - Feedback
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
| Links | source repository URL, feedback email 또는 GitHub Issues URL |
| Theme | 현재 지원 Theme `Light`, Dark Mode deferred 안내 |
| Attribution | VLR.GG 출처, 비공식·개인 프로젝트 고지 |

버전은 빌드 설정의 실제 앱 버전을 표시하며 문서에 정적 버전 값을 중복 보관하지 않는다. 외부 URL과 이메일 주소는 배포 구성에서 관리한다.

## 화면 상태

| 상태 | 동작 |
| --- | --- |
| Populated | 빌드 버전과 구성된 링크를 포함한 모든 정보 영역을 표시한다. |
| Partial | 링크 하나가 구성되지 않았거나 실행할 수 없으면 나머지 정보를 유지하고 해당 액션만 unavailable로 표시한다. |
| Error | 버전 등 필수 로컬 정보를 읽지 못하면 안전한 대체 표기와 일반화된 안내를 사용한다. |
| Empty | 제품상 정상적인 전체 empty 상태는 없다. 필수 소개·출처·Theme 정보는 앱에 포함된다. |
| Loading | 원격 조회가 없으므로 일반적인 콘텐츠 loading은 필요하지 않다. platform 버전 정보 준비가 비동기라면 해당 값만 안정적인 placeholder로 표시한다. |
| Stale | 정적/빌드 정보 화면이므로 stale 상태를 사용하지 않는다. |

## 사용자 인터랙션

- Source Code를 누르면 외부 브라우저 또는 설치된 대응 앱으로 repository를 연다.
- Feedback을 누르면 이메일 작성 화면 또는 GitHub Issues를 연다.
- 외부 이동 전 목적지를 사용자가 이해할 수 있는 label과 icon으로 표시한다.
- Light Theme은 유일한 선택지로 표시하며 Dark Mode를 선택 가능한 disabled control처럼 오인시키지 않는다.
- Search는 현재 화면 위에 push되고 Back 시 About으로 복귀한다.

## 앱·서버 책임 경계

### 앱

- 앱 소개와 attribution 문구를 제품 리소스로 제공한다.
- 실제 build version을 platform/shared boundary를 통해 표시한다.
- source 및 feedback external intent를 platform 방식으로 연다.
- 실패한 외부 이동을 raw platform 오류 없이 사용자에게 안내한다.
- Theme 영역은 Light-only MVP 계약을 따른다.

### 서버

- About을 위해 별도 API나 scraping을 제공하지 않는다.

## Upstream 및 외부 링크 메모

- About은 VLR.GG 콘텐츠를 scraping하지 않는다.
- source repository, GitHub Issues, feedback email의 실제 값은 배포 전 구성에서 확정한다.
- VLR.GG 출처 고지는 공식 제휴나 허가를 의미하지 않는다.
- 외부 정책과 배포 요구에 따라 필요한 추가 고지 문구는 출시 전 별도 검토한다.

## 테스트 가능한 수용 기준

- [ ] About은 Bottom Navigation의 다섯 번째 root destination이다.
- [ ] 앱 소개, 실제 build version, Source Code, Feedback, Theme, attribution 영역을 표시한다.
- [ ] Source Code 액션은 구성된 repository URL을 외부 앱으로 연다.
- [ ] Feedback 액션은 구성에 따라 이메일 작성 화면 또는 GitHub Issues를 연다.
- [ ] 외부 이동을 처리할 앱이 없거나 실행이 실패해도 앱이 종료되지 않고 안내를 표시한다.
- [ ] Theme 영역은 Light만 현재 지원됨을 표시하고 Dark Mode가 MVP에 포함된 것처럼 동작하지 않는다.
- [ ] VLR.GG 데이터 출처와 비공식·개인 프로젝트 문맥을 명확히 표시한다.
- [ ] About은 별도 server API를 호출하지 않는다.
- [ ] Top App Bar에서 Search를 열고 Back하면 About으로 복귀한다.
- [ ] screen reader가 외부 링크의 목적과 외부 이동임을 식별할 수 있다.

## 열린 결정

- Source repository의 최종 URL과 Feedback 기본 경로(이메일 또는 GitHub Issues)는 배포 구성을 확정할 때 결정한다.
- 법적 고지, 개인정보 처리방침, 오픈소스 라이선스 목록이 필요한 배포 채널을 선택하면 About 또는 별도 화면의 범위를 갱신한다.
