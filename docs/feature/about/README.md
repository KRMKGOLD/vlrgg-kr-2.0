# About

About은 앱의 목적, 실제 build version, 공개 source, 테마 범위와 데이터 출처를 안내하는 다섯 번째 root다. 별도 API나 scraping은 사용하지 않는다. 현재 앱 구현과 자동화 검증은 완료됐지만 Android/iOS 실제 external-open과 실기기 접근성 검증은 남아 있다.

## MVP 범위

- 앱 이름·소개와 실제 build version
- Source Code 외부 링크: `https://github.com/KRMKGOLD/vlrgg-kr-2.0`
- 현재 지원 테마 `Light`와 Dark Mode 후속 안내
- VLR.GG 데이터 출처와 비공식 개인 프로젝트 고지
- 공통 Top App Bar Search와 root 상태 복원

Dark Mode 선택, 계정·프로필, 피드백·문의, 원격 About 설정은 제외한다. 개인정보 처리방침, 오픈소스 라이선스, 법적 문서 전문은 선택한 배포 채널이 요구할 때 범위를 갱신한다.

## 화면과 이동

화면은 App identity, Project links, Appearance, Attribution 순서다. 과도한 카드나 마케팅형 hero를 사용하지 않고 외부 이동과 고지를 구분한다.

- Bottom navigation의 `About`에서 진입한다.
- Search는 About 위에 push되고 Back 시 기존 About 상태로 돌아온다.
- Source Code는 목적지와 외부 이동임을 접근 가능한 label로 알린 뒤 브라우저나 대응 앱으로 연다.
- 다른 bottom item은 해당 root로 이동한다.

## 표시와 오류 계약

- version은 build metadata를 사용한다. null·blank·unavailable이면 version chip과 대체 문구를 모두 생략하고 나머지 identity는 유지한다.
- Light는 유일한 현재 테마다. Dark Mode를 선택 가능한 disabled control처럼 표현하지 않는다.
- 외부 링크 실행이 실패하면 화면과 Source Code row를 유지하고, 정확히 `소스 코드를 열 수 없습니다.`만 담은 action 없는 짧은 Snackbar를 표시한다.
- Snackbar timeout은 text-only 안내의 접근성 권장값을 적용한다. 사용자는 Source Code row를 다시 눌러 재시도한다.
- About을 떠난 뒤 늦게 도착한 platform callback과 화면 복귀 시 만료된 Snackbar는 무시한다.
- 원격 조회가 없으므로 Loading, Empty, Stale 화면이나 version placeholder를 만들지 않는다.

## 고지 경계

VLR.GG를 데이터 출처로 쓰지만 공식 앱·제휴·허가를 뜻하지 않는다. 현재 scraping permission은 확보되지 않았고 공개 서비스로 제공하지 않는다. 배포 범위가 바뀌면 필요한 데이터 사용·개인정보·라이선스 고지를 다시 검토한다.

## 수용 기준

- [x] 다섯 번째 root에서 소개, 가능한 경우의 build version, Source Code, Light theme, attribution을 표시한다.
- [x] version 누락과 external-open 실패가 나머지 정보나 화면 사용을 막지 않는다.
- [x] 오류 Snackbar의 문구·무동작·timeout과 늦은 callback 무시를 자동 검증한다.
- [x] Source Code의 목적과 외부 이동을 screen reader가 식별한다.
- [ ] Android/iOS 실기기에서 실제 external-open과 접근성을 확인한다.
