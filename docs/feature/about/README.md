# About

About은 앱의 목적, 실제 build version, 공개 source·개인정보처리방침·문의 링크, 테마 범위, 데이터 출처와 비공식·상표 고지를 안내하는 다섯 번째 root다. 별도 API나 scraping은 사용하지 않는다. 스토어 공개 배포를 기준으로 범위를 정한다. 오픈소스 라이선스 고지와 Android/iOS 실기기 external-open·접근성 검증은 남아 있다.

## MVP 범위

- 앱 이름 `VLR.GG Mobile (Unofficial)`, 과장 없는 소개 문구, 실제 build version
- 외부 링크 세 개. 표시 순서는 아래와 같다.
  - 소스 코드: `https://github.com/KRMKGOLD/vlrgg-kr-2.0`
  - 개인정보처리방침: `https://github.com/KRMKGOLD/vlrgg-kr-2.0/blob/main/docs/privacy-policy.md`
  - 문의: `https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues`
- 현재 지원 테마 라이트와 다크 모드 후속 안내
- 데이터 출처 VLR.GG, VLR.GG·Riot Games 비제휴·비공식 앱 고지, Riot Games 상표 고지
- 공통 Top App Bar Search와 root 상태 복원

Dark Mode 선택, 계정·프로필, 앱 내 문의 양식, 원격 About 설정은 제외한다. 문의는 GitHub Issues 외부 링크로만 받는다. 오픈소스 라이선스 고지는 스토어 배포에 필요하지만 표시 방식(라이브러리 자동 생성 또는 수동 목록)과 새 dependency 결정이 남아 있어 이번 범위에서 구현하지 않는다.

## 화면과 이동

화면은 App identity, Project links(소스 코드 → 개인정보처리방침 → 문의), Appearance, Attribution(데이터 출처 → 비공식 앱 고지 → 상표 고지) 순서다. 과도한 카드나 마케팅형 hero를 사용하지 않고 외부 이동과 고지를 구분한다. Top App Bar 제목은 다른 root와 같이 `About`을 유지하고, 화면 안의 라벨은 한국어로 표시한다.

- Bottom navigation의 `About`에서 진입한다.
- Search는 About 위에 push되고 Back 시 기존 About 상태로 돌아온다.
- 각 링크 row는 목적지와 외부 이동임을 접근 가능한 label(`소스 코드 외부 링크 열기`, `개인정보처리방침 외부 링크 열기`, `문의 외부 링크 열기`)로 알린다. 같은 문구를 click label과 content description에 쓰고, row는 button role과 최소 48dp 높이를 가진다. 누르면 브라우저나 대응 앱으로 연다.
- 다른 bottom item은 해당 root로 이동한다.

## 표시 문구

- 소개: `VLR.GG에 공개된 발로란트 e스포츠 경기·대회·팀·선수 정보를 모바일에서 보기 편하게 정리한 비공식 앱입니다.`
- 데이터 출처: VLR.GG: `경기·대회·팀·선수 정보는 VLR.GG에 공개된 데이터를 가져와 정리합니다.`
- 비공식 앱 고지: `이 앱은 개인이 만든 비공식 앱입니다. VLR.GG 및 Riot Games와 제휴하지 않았으며, VLR.GG 또는 Riot Games의 승인이나 후원을 받지 않았습니다.`
- 상표 고지: `VLR.GG Mobile (Unofficial)은 Riot Games의 보증을 받지 않았으며 Riot Games 또는 Riot Games 자산의 제작·관리에 공식적으로 관여하는 누구의 견해나 의견도 반영하지 않습니다. Riot Games, VALORANT(발로란트) 및 모든 관련 자산은 Riot Games, Inc.의 상표 또는 등록 상표입니다.`

## 표시와 오류 계약

- version은 build metadata를 사용한다. null·blank·unavailable이면 version chip과 대체 문구를 모두 생략하고 나머지 identity는 유지한다.
- 라이트는 유일한 현재 테마다. Dark Mode를 선택 가능한 disabled control처럼 표현하지 않는다.
- 외부 링크 실행이 실패하면 화면과 모든 링크 row를 유지하고, 실패한 링크의 문구 하나만 담은 action 없는 짧은 Snackbar를 표시한다.

  | 링크 | 실패 문구 |
  | --- | --- |
  | 소스 코드 | `소스 코드를 열 수 없습니다.` |
  | 개인정보처리방침 | `개인정보처리방침을 열 수 없습니다.` |
  | 문의 | `문의 페이지를 열 수 없습니다.` |

- Snackbar timeout은 text-only 안내의 접근성 권장값을 적용한다. 사용자는 같은 row를 다시 눌러 재시도한다. 다른 링크 열기에 성공하면 남아 있던 Snackbar를 닫는다.
- About을 떠난 뒤 늦게 도착한 platform callback, 더 최근 링크 시도에 밀린 이전 callback, 화면 복귀 시 만료된 Snackbar는 무시한다.
- 원격 조회가 없으므로 Loading, Empty, Stale 화면이나 version placeholder를 만들지 않는다.

## 고지 경계

Google Play 프로덕션 공개를 목표로 하는 비공식 앱을 전제로 한다(2026-10-13 소유자 결정). VLR.GG에 데이터 사용 허가를 요청하지 않았고 받지 않았다. VLR.GG를 데이터 출처로 쓰지만 공식 앱·제휴·승인·후원을 뜻하지 않으며, 화면에 VLR.GG·Riot Games 비제휴와 Riot Games 상표 고지를 항상 표시한다. 고지 문구는 허가를 받은 것처럼 읽히지 않아야 한다. 스토어 검토 반려나 신고 때 제목·아이콘의 VLR.GG 표기를 빼더라도 데이터 출처와 비제휴 고지는 유지한다. 근거와 리스크는 [외부 제약](../README.md#외부-제약)을 따른다. 개인정보 처리 내용은 앱 안에 전문을 두지 않고 저장소의 `docs/privacy-policy.md`를 외부 링크로 연다. 오픈소스 라이선스 고지는 배포 전 별도로 완료해야 한다. 데이터 사용 범위나 수집 항목이 바뀌면 고지 문구와 개인정보처리방침을 함께 다시 검토한다.

## 수용 기준

- [x] 다섯 번째 root에서 앱 이름 `VLR.GG Mobile (Unofficial)`, 소개, 가능한 경우의 build version, 소스 코드·개인정보처리방침·문의 링크, 라이트 테마, 데이터 출처·비공식 앱·상표 고지를 정해진 순서로 표시한다.
- [x] 각 링크가 정확한 URL로 열리고, version 누락과 external-open 실패가 나머지 정보나 화면 사용을 막지 않는다.
- [x] 링크별 오류 Snackbar의 문구·무동작·timeout과 늦은 callback 무시를 자동 검증한다.
- [x] 각 링크의 목적과 외부 이동을 screen reader가 식별한다(click label·content description·button role·48dp).
- [ ] 오픈소스 라이선스 고지를 표시한다.
- [ ] Android/iOS 실기기에서 실제 external-open과 접근성을 확인한다.
