# Play Console 입력 초안 (#164)

2026-10-19 기준 [#164](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/164)의 Play Console 앱 콘텐츠 선언, 스토어 등록정보, 비공개 테스트 입력값 초안이다. Console 입력과 테스터 모집은 소유자가 한다. 이 문서는 입력값과 근거만 담으며, Console 원본 응답·테스터 신원·배포 증거는 [앱 배포](app-release.md)의 공개 저장소 규칙에 따라 여기에 남기지 않는다.

- 대상 패키지: `kr.co.cotton.vlrgg_mobile`([app-release.md:46](app-release.md))
- 스토어 문구와 이미지 원본: [`fastlane/metadata/android/ko-KR/`](../fastlane/metadata/android/ko-KR/). 현재 lane은 `skip_upload_metadata`·`skip_upload_images`가 `true`라([Fastfile:56-60](../fastlane/Fastfile)) 자동 업로드에 쓰이지 않으며, Console에 복사해 붙이는 원본이다.
- 그래픽 이미지(`images/featureGraphic.png`, 1024×500)는 별도 작업에서 추가한다.
- 근거 표기의 `파일:줄`은 이 문서 작성 시점의 `main`(52bf0b0) 기준이다. merged manifest와 의존성은 같은 커밋을 로컬에서 빌드해 확인했다.
- `확인 필요` 표시는 공식 자료로 확인하지 못한 항목이다. Console 화면 문구가 다르면 화면을 따른다.

## 1. 확인한 앱 사실

Console 답변은 아래 사실을 기준으로 한다. 앱 코드나 의존성이 바뀌면 이 표부터 다시 확인한다.

| 항목 | 확인 결과 | 근거 |
| --- | --- | --- |
| 계정·로그인 | 없음. 모든 화면이 로그인 없이 열린다. | [feature/README.md](feature/README.md)의 MVP 제외 범위(로그인·계정), [privacy-policy.md:9](privacy-policy.md) |
| 광고 SDK | 없음. `releaseRuntimeClasspath`에 `play-services-ads`, `firebase-analytics`, `play-services-measurement`, 기타 광고 네트워크가 없다. Firebase는 `firebase-crashlytics`(BoM 34.19.0)와 그 전이 의존성(`firebase-sessions`, `firebase-installations`, datatransport, 인터페이스 모듈 `firebase-measurement-connector`)뿐이다. | [libs.versions.toml](../gradle/libs.versions.toml)의 `firebaseBom`·`firebase-crashlytics`, [androidApp/build.gradle.kts:97-98](../app/androidApp/build.gradle.kts), `./gradlew :app:androidApp:dependencies --configuration releaseRuntimeClasspath` |
| 광고 ID 권한 | merged manifest(debug·release)에 `com.google.android.gms.permission.AD_ID`가 없다. | 아래 "권한" 절 |
| 권한 | `INTERNET`, `ACCESS_NETWORK_STATE`, 앱 전용 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`만 있다. | 아래 "권한" 절 |
| 사용자 생성 콘텐츠·사용자 간 상호작용 | 없음. 댓글·포럼·Pick'em·채팅·업로드·공유 기능이 없다. | [feature/README.md](feature/README.md)의 MVP 제외 범위(포럼·댓글·Pick'em), [news/README.md](feature/news/README.md)의 제외 범위(댓글·포럼) |
| 위치 | 수집·공유하지 않는다. 코드의 `location`은 대회 개최지 문자열이다. | [EventDetail.kt:8](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/domain/model/events/EventDetail.kt), [privacy-policy.md:54](privacy-policy.md) |
| 디지털 구매·결제 | 없음. Billing 라이브러리와 결제 화면이 없다. | `releaseRuntimeClasspath`(billing 없음) |
| 도박·베팅 | 없음. 배당·베팅·Pick'em 화면이 없다. | [feature/README.md](feature/README.md)의 MVP 제외 범위 |
| 외부 링크 | About의 고정 링크 3개(소스 코드, 개인정보처리방침, 문의)를 기본 브라우저로 연다. 앱 안 웹 브라우저(WebView)는 없다. | [AboutUiState.kt:3-12](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/about/AboutUiState.kt), [AndroidAboutPlatform.kt:14-17](../app/androidApp/src/main/kotlin/kr/co/cotton/vlrgg_mobile/AndroidAboutPlatform.kt) |
| 전송 암호화 | Release 서버 주소는 HTTPS만 허용한다. 평문 HTTP는 debug manifest에서만 허용한다. Firebase는 HTTPS로 전송한다. | [androidApp/build.gradle.kts:43](../app/androidApp/build.gradle.kts), [debug/AndroidManifest.xml:4](../app/androidApp/src/debug/AndroidManifest.xml), [privacy-policy.md:122](privacy-policy.md), [Firebase Play 데이터 공개](https://firebase.google.com/docs/android/play-data-disclosure) |
| 오류 보고 수집 | Release는 Crashlytics 수집 ON, 일반 Debug는 OFF. 앱 안에 끄는 설정은 없다. | [androidApp/build.gradle.kts:165-175](../app/androidApp/build.gradle.kts), [VlrggApplication.kt:17-18](../app/androidApp/src/main/kotlin/kr/co/cotton/vlrgg_mobile/VlrggApplication.kt), [privacy-policy.md:103](privacy-policy.md) |
| 즐겨찾기 | 기기 안에만 저장하고 서버로 보내지 않는다. | [privacy-policy.md:39](privacy-policy.md) |
| 비공식·상표 고지 | About에 비공식 앱 고지와 Riot Games 상표 고지가 있다. | [AboutScreen.kt:330-337](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/about/AboutScreen.kt) |

## 2. 앱 콘텐츠

### 개인정보처리방침

- URL: `https://github.com/KRMKGOLD/vlrgg-kr-2.0/blob/main/docs/privacy-policy.md`
- 앱 About의 "개인정보처리방침" 행이 여는 URL과 같다([AboutUiState.kt:4](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/about/AboutUiState.kt)).
- 저장소가 공개 상태여야 열린다. 저장소를 비공개로 바꾸거나 파일 경로를 옮기면 Console URL과 앱 링크를 함께 바꿔야 한다.

### 앱 액세스

- 답변: **모든 기능을 특별한 액세스 권한 없이 사용할 수 있음**
- 근거: 로그인·회원가입·구독·지역 제한 기능이 없다(1절 표). 심사자에게 줄 계정 정보가 필요 없다.

### 광고

- 답변: **아니요, 앱에 광고가 없습니다**
- 근거: 1절 "광고 SDK" 행. 앱 화면에 자체 광고나 제휴 홍보도 없다.

### 광고 ID

- 답변: **아니요, 광고 ID를 사용하지 않음**
- 근거: merged manifest에 `AD_ID` 권한이 없다. Android 13 이상을 타깃팅하는 앱은 이 권한이 없으면 광고 ID 대신 0으로 채운 값을 받는다([광고 ID 정책](https://support.google.com/googleplay/android-developer/answer/6048248)). 현재 `targetSdk`는 36이다([libs.versions.toml](../gradle/libs.versions.toml)의 `android-targetSdk`).
- 주의: "사용함"으로 답하면 `AD_ID` 권한이 없는 release가 차단된다고 Console이 안내한다는 보고가 있다(Console 문구 `확인 필요`). 의존성을 추가할 때 이 권한이 merged manifest에 들어오지 않는지 다시 확인한다.

### 권한

로컬에서 `processDebugMainManifest`와 `processReleaseMainManifest`를 실행해 merged manifest를 확인했다. release는 서명·Firebase 설정 없이 manifest 단계만 실행했으며, 서명 설정과 `google-services`·Crashlytics Gradle plugin은 권한을 추가하지 않는다.

| 권한 | 추가한 곳 | 보호 수준 | 추가 선언 |
| --- | --- | --- | --- |
| `android.permission.INTERNET` | 앱 [AndroidManifest.xml:5](../app/androidApp/src/main/AndroidManifest.xml), `firebase-crashlytics` | normal | 필요 없음 |
| `android.permission.ACCESS_NETWORK_STATE` | `firebase-installations`, `transport-backend-cct` | normal | 필요 없음 |
| `kr.co.cotton.vlrgg_mobile.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` | signature(앱 전용) | 필요 없음 |

- Console 선언 양식이 필요한 권한(SMS·통화 기록, 백그라운드 위치, 모든 파일 접근, `QUERY_ALL_PACKAGES`, 접근성 API, 사진·동영상, `USE_EXACT_ALARM`, 전체 화면 인텐트, Health Connect, `REQUEST_INSTALL_PACKAGES` 등)은 없다([권한 및 민감한 정보에 액세스하는 API](https://support.google.com/googleplay/android-developer/answer/9888170)). 위 세 권한이 선언 대상이 아니라는 판단은 그 목록에 없다는 데서 추론했다.
- 포그라운드 서비스, 알림(`POST_NOTIFICATIONS`), 위치 권한도 없다.

### 콘텐츠 등급(IARC)

- 카테고리 권장: **참고, 뉴스 또는 교육(Reference, News, or Educational)**
  - 근거: 앱은 공개된 e스포츠 경기 결과·대회·팀·선수 정보와 기사를 읽기 전용으로 보여 준다. 게임이 아니고, 사용자 콘텐츠를 공유하는 소셜 앱이나 상점·스트리밍 서비스도 아니다. ESRB는 뉴스·교육 목적의 비게임 앱을 따로 판단한다고 안내한다([ESRB 비게임 앱 기준](https://www.esrb.org/esrb-ratings-criteria-for-non‑game-apps/)).
  - 카테고리 이름은 Console 질문지 안에서만 확인할 수 있다(`확인 필요`). 절차는 [콘텐츠 등급](https://support.google.com/googleplay/android-developer/answer/9859655)을 따른다.
- 질문별 답변. 질문 문구는 카테고리에 따라 달라지므로 주제별로 적는다.

| 주제 | 답변 | 근거 |
| --- | --- | --- |
| 폭력(사실적·만화·유혈 묘사 포함) | 아니요 | 앱 자체 콘텐츠는 경기 점수·통계·텍스트 기사·팀 로고·선수 사진이다. 아래 "판단이 갈리는 점" 참고 |
| 성적 콘텐츠·노출 | 아니요 | 같은 근거 |
| 비속어·저속한 표현 | 아니요 | 기사는 VLR.GG 편집 기사다. 사용자 댓글을 보여 주지 않는다. |
| 약물·술·담배 | 아니요 | 관련 콘텐츠가 없다. |
| 혐오 표현·차별 | 아니요 | 관련 콘텐츠가 없다. |
| 실제 도박, 모의 도박, 베팅 정보 | 아니요 | 1절 "도박·베팅" 행 |
| 사용자 간 상호작용·소통(채팅, 메시지) | 아니요 | 1절 "사용자 생성 콘텐츠" 행 |
| 사용자 생성 콘텐츠 공유 | 아니요 | 같은 근거 |
| 사용자 위치 공유 | 아니요 | 1절 "위치" 행 |
| 디지털 상품 구매 | 아니요 | 1절 "디지털 구매" 행 |
| 제한 없는 인터넷 접근(웹 브라우저·검색 엔진 기능) | 아니요 | 앱 안 브라우저가 없고 고정 외부 링크 3개만 기본 브라우저로 연다. 앱 검색은 VLR.GG의 시리즈·대회·팀·선수 결과로 제한된다([search/README.md](feature/search/README.md)). |
| 뉴스 콘텐츠 포함 여부(질문이 나오는 경우) | 예 | e스포츠 기사 목록과 본문을 보여 준다. 뉴스 앱 선언과는 별개의 질문이다. |

- 판단이 갈리는 점(소유자 확인): 기사 본문 이미지는 VLR.GG 원문에 따라 달라지며 발로란트 경기 화면이 들어갈 수 있다. 앱은 이미지를 선별하지 않는다. 권장안은 "폭력: 아니요"다. 앱의 목적이 경기 정보 제공이고 IARC가 뉴스·교육 앱을 낮게 평가하는 방향이기 때문이다. 기사 이미지에 무기·전투 장면이 반복적으로 나온다고 판단하면 해당 질문에 "예(사실적이지 않은 게임 화면)"로 답하는 것이 보수적이다. 등급은 게시 후에도 질문지를 다시 제출해 고칠 수 있다.

### 타깃층 및 콘텐츠

- 타깃 연령: **13~15세, 16~17세, 18세 이상**. 12세 이하 그룹은 선택하지 않는다.
- 아동에게 의도치 않게 어필하는지: **아니요**. 스토어 문구와 스크린샷에 아동 대상 캐릭터·애니메이션이 없다.
- 근거: [타깃층 및 콘텐츠](https://support.google.com/googleplay/android-developer/answer/9867159). 12세 이하 그룹을 넣으면 가족 정책이 적용된다. 13~15·16~17세는 일부 지역에서 아동으로 볼 수 있다고 안내한다. 개인정보처리방침도 만 14세 미만 아동을 대상으로 개인정보를 따로 수집하지 않는다고 적는다([privacy-policy.md:131-133](privacy-policy.md)).
- "미성년자 액세스 제한"은 18세 이상만 선택할 때 쓰는 옵션이므로 해당 없다.

### 뉴스 앱

- 답변: **해당 없음**. 소유자가 2026-10-19 결정했다.
- 앱 카테고리를 "뉴스 및 잡지"로 두지 않고 제목에 "뉴스"를 쓰지 않는다. 기준: [뉴스 앱 정책](https://support.google.com/googleplay/android-developer/answer/10523915), [뉴스 앱 선언](https://support.google.com/googleplay/android-developer/answer/16189314).

### 건강·정부·금융 기능

- 건강 앱: **해당 없음**. 건강·피트니스·의료 기능이 없다.
- 정부 앱: **아니요**. 정부 기관과 관련이 없다.
- 금융 기능: **해당 없음**. 결제·대출·투자·암호화폐 기능이 없다.

## 3. 데이터 보안

[개인정보처리방침](privacy-policy.md)과 같은 범위로 답한다. 정의는 [데이터 보안 섹션 안내](https://support.google.com/googleplay/android-developer/answer/10787469), Firebase 항목은 [Google Play 데이터 보안 섹션 준비(Firebase)](https://firebase.google.com/docs/android/play-data-disclosure)를 따른다. Firebase 문서는 응답 책임이 개발자에게 있다고 밝힌다.

### 수집 주체

| 주체 | 데이터 | 방침 근거 |
| --- | --- | --- |
| 앱의 Firebase Crashlytics | 비정상 종료·ANR 기록(stack trace), 기기·OS·앱 상태, Crashlytics 설치 UUID, Firebase 설치 ID | [privacy-policy.md:13](privacy-policy.md) |
| 앱의 Firebase Sessions(Crashlytics 구성 요소) | 세션 ID·순번·시작 시각, 앱·OS·기기 모델, Firebase 설치 ID, SDK 버전 | [privacy-policy.md:14](privacy-policy.md) |
| 조회 서버(Cloud Run 요청 로그) | IP 주소, User-Agent, 요청 주소(팀·선수·경기 식별 번호와 검색어 포함), 요청 시각·크기·상태·처리 시간. 30일 보관 | [privacy-policy.md:15](privacy-policy.md), [privacy-policy.md:67](privacy-policy.md) |

수집으로 보지 않는 것:

- 즐겨찾기는 기기 안에서만 처리한다. 기기 밖으로 보내지 않는 데이터는 수집이 아니다.
- 팀 로고·선수 사진은 기기가 VLR.GG 이미지 주소에서 직접 불러온다([privacy-policy.md:50](privacy-policy.md)). 앱이나 개발자가 사용자 데이터를 보내거나 받는 것이 아니라 일반 이미지 요청이므로 수집으로 신고하지 않는 것을 권장한다.
- 검색어는 조회 서버가 VLR.GG 검색에 전달하지만 IP·기기 정보는 넘기지 않는다([privacy-policy.md:23](privacy-policy.md)). 이 전달은 사용자가 요청한 검색을 처리하는 과정이며, 방침도 제3자 제공으로 보지 않는다.

### 공통 질문

| 질문 | 답변 | 근거 |
| --- | --- | --- |
| 필수 사용자 데이터 유형을 수집하거나 공유하나요? | 예(수집함) | 위 수집 주체 표 |
| 수집한 모든 사용자 데이터가 전송 중 암호화되나요? | 예 | 1절 "전송 암호화" 행. Firebase는 HTTPS로 전송하고, Release 앱은 HTTPS 서버 주소만 허용한다. |
| 사용자가 데이터 삭제를 요청할 방법을 제공하나요? | 아니요(권장, 소유자 결정 필요) | 아래 "삭제 요청" 참고 |
| 공유 | 없음 | Google(Firebase, Google Cloud)은 개발자를 대신해 처리하는 서비스 제공업체이며, 서비스 제공업체 전송은 공유가 아니다([privacy-policy.md:72-85](privacy-policy.md)). |
| 선택 가능 여부 | 모두 필수(사용자가 선택할 수 없음) | 앱 안에 오류 보고·세션 전송을 끄는 설정이 없고, 서버 요청 로그는 조회마다 자동 생성된다([privacy-policy.md:103](privacy-policy.md)). |
| 계정 삭제 URL | 해당 없음 | 계정을 만들 수 없는 앱은 계정 삭제 요구가 없다([계정 삭제 요구사항](https://support.google.com/googleplay/android-developer/answer/13327111)). |

### 데이터 유형별 답변

| Console 데이터 유형 | 수집 | 공유 | 처리 | 선택 | 목적 | 출처 |
| --- | --- | --- | --- | --- | --- | --- |
| 앱 정보 및 성능 › 비정상 종료 로그 | 예 | 아니요 | 일시적 아님 | 필수 | 분석, 앱 기능 | Crashlytics stack trace·ANR |
| 앱 정보 및 성능 › 진단 | 예 | 아니요 | 일시적 아님 | 필수 | 분석, 앱 기능 | Crashlytics 기기·OS·앱 상태, Sessions 앱·기기 정보 |
| 앱 정보 및 성능 › 기타 앱 성능 데이터 | 예 | 아니요 | 일시적 아님 | 필수 | 분석 | Sessions 세션 시작·순번(crash-free 비율 산출) |
| 기기 또는 기타 ID | 예 | 아니요 | 일시적 아님 | 필수 | 분석, 앱 기능 | Crashlytics 설치 UUID, Firebase 설치 ID, 세션 ID. 서버 로그의 IP 주소(아래 판단 항목) |
| 앱 활동 › 앱 내 검색 기록 | 예(권장, 소유자 결정 필요) | 아니요 | 일시적 아님 | 필수 | 앱 기능 | 서버 로그의 요청 주소에 담긴 검색어 |
| 앱 활동 › 앱 상호작용 | 예(권장, 소유자 결정 필요) | 아니요 | 일시적 아님 | 필수 | 앱 기능, 분석 | 서버 로그의 요청 주소(조회한 팀·선수·경기 식별 번호), Sessions 포그라운드 시각 |
| 위치 › 대략적인 위치 | 아니요(권장, 소유자 결정 필요) | - | - | - | - | 아래 판단 항목 |

목적 선택 이유:

- 분석: 오류 원인 파악, 영향받는 사용자 수와 crash-free 세션 비율 산출([privacy-policy.md:13-14](privacy-policy.md)). Play 정의상 앱이 어떻게 작동하는지 평가하는 데이터다.
- 앱 기능: 서버 로그는 조회 제공과 장애 대응([privacy-policy.md:15](privacy-policy.md)), 오류 보고는 정상 작동하는 앱 제공([privacy-policy.md:31](privacy-policy.md))에 쓴다.
- 광고·마케팅, 맞춤설정, 계정 관리, 개발자 커뮤니케이션은 해당 없다. 사기 방지·보안·규정 준수는 선택하지 않는다. 서버의 요청 제한은 이용자별로 구분하지 않는 서버 전체 기준이다([privacy-policy.md:21](privacy-policy.md)).

목록에 없는 유형(개인 정보, 금융 정보, 건강, 메시지, 사진·동영상, 오디오, 파일, 캘린더, 연락처, 웹 기록, 설치된 앱, 정확한 위치)은 수집하지 않는다([privacy-policy.md:52-58](privacy-policy.md)).

### 판단이 갈리는 항목

보수적으로 더 넓게 신고하는 쪽을 권장안으로 둔다. 누락은 정책 위반 사유가 되지만, 실제 처리 범위 안에서 넓게 신고하는 것은 위반이 아니다.

1. 서버 로그의 IP 주소
   - Play는 IP 주소 신고를 실제 사용 방식에 따라 정하라고 하고, IP로 위치를 추론하면 "대략적인 위치"로 신고하라고 안내한다([데이터 보안 섹션 안내](https://support.google.com/googleplay/android-developer/answer/10787469)).
   - 서버와 Cloud Run 요청 로그는 IP로 위치를 추론하지 않는다([privacy-policy.md:19-21](privacy-policy.md)). 그래서 대략적인 위치는 "아니요"를 권장한다.
   - IP 주소는 기기 단위로 연결될 수 있는 값이므로 "기기 또는 기타 ID"에 포함해 신고하는 것을 권장한다. Firebase 설치 ID 때문에 이 유형은 어차피 신고한다.
   - 소유자 결정: 위치 분석이나 국가별 집계를 도입하면 "대략적인 위치"를 추가한다.
2. 요청 주소의 검색어
   - "앱 내 검색 기록"은 사용자가 앱에서 검색한 내용이다. 검색어가 요청 주소에 담겨 30일 보관되므로, 요청 처리 중 메모리에서만 쓰고 버리는 일시적 처리에 해당하지 않는다.
   - 권장안은 "수집: 예, 목적: 앱 기능"이다. 개인정보처리방침도 검색어를 접속 기록 항목으로 적는다([privacy-policy.md:15](privacy-policy.md)).
   - 신고하지 않으려면 서버 로그에서 검색어를 남기지 않는 변경이 먼저 필요하다. 이는 운영 GCP 변경이라 이 작업 범위 밖이다.
3. 요청 주소의 팀·선수·경기 식별 번호
   - 어떤 화면을 조회했는지 30일 동안 남으므로 "앱 상호작용(페이지 조회 등)"으로 신고하는 것을 권장한다.
   - 개발자는 이 기록을 사용 행태 분석에 쓰지 않지만, 보관한다는 사실 때문에 넓게 신고한다.
4. 삭제 요청
   - 개인정보처리방침은 이메일로 삭제 요청을 받되, 계정이 없어 특정 이용자의 데이터를 찾지 못할 수 있다고 적는다([privacy-policy.md:113-116](privacy-policy.md)).
   - Console에서 "예"로 답하면 스토어에 "데이터 삭제 요청 가능"이 표시되어, 실제로 지키기 어려운 기대를 만든다. 그래서 "아니요"를 권장한다. 서버 로그는 30일, Crashlytics 오류 기록은 90일 뒤 자동 삭제가 시작된다([privacy-policy.md:64-67](privacy-policy.md)).
   - Firebase 설치 ID와 오류 없이 끝난 세션 기록은 개발자가 삭제를 요청하기 전까지 남을 수 있다([privacy-policy.md:65-66](privacy-policy.md)). 따라서 "90일 이내 자동 삭제"를 전제로 한 답은 하지 않는다.
   - 소유자 결정: 이메일 요청 경로를 근거로 "예"를 고를 수도 있다. 이 경우 Firebase 설치 ID를 특정할 방법이 없다는 한계를 감수해야 한다.

### 스토어에 보일 요약(권장안 기준)

- 수집 데이터: 앱 정보 및 성능, 기기 또는 기타 ID, 앱 활동
- 제3자와 공유하는 데이터 없음
- 전송 중 데이터 암호화
- 데이터 삭제 요청 불가(권장안 "아니요"인 경우)

## 4. 스토어 설정

| 항목 | 권장 입력 | 근거 |
| --- | --- | --- |
| 앱 또는 게임 | 앱 | 게임이 아니다. |
| 카테고리 | 스포츠 | Play는 스포츠 카테고리를 "스포츠 뉴스와 해설, 점수 추적, 판타지 팀 관리, 경기 중계"로 설명한다([카테고리 및 태그](https://support.google.com/googleplay/android-developer/answer/9859673)). 앱의 핵심이 e스포츠 경기 결과·대회·통계 추적이다. "뉴스 및 잡지"는 뉴스 앱 정책 대상이 되므로 고르지 않는다. |
| 태그 | 선택 사항. 소유자 결정 | Console이 제시하는 목록에서 고른다. |
| 연락처 이메일 | `teamcotton124@gmail.com` | 필수 항목([스토어 등록정보](https://support.google.com/googleplay/android-developer/answer/9859152)). 개인정보처리방침 문의처와 같다([privacy-policy.md:144](privacy-policy.md)). |
| 전화번호 | 비움 | 공개 연락처는 이메일 하나만 쓴다. |
| 웹사이트 | 소유자 결정. 권장: `https://github.com/KRMKGOLD/vlrgg-kr-2.0` | 선택 항목이지만 권장된다([스토어 등록정보](https://support.google.com/googleplay/android-developer/answer/9859152)). About의 "소스 코드" 링크와 같다([AboutUiState.kt:3](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/about/AboutUiState.kt)). 개발 저장소가 스토어에서 바로 보인다는 점을 받아들일 수 있는지 소유자가 정한다. |
| 기본 언어 | 한국어(ko-KR) | 앱 화면과 등록정보가 한국어다. Fastlane 경로도 `ko-KR`이다. |

## 5. 스토어 등록정보

원본 파일은 [`fastlane/metadata/android/ko-KR/`](../fastlane/metadata/android/ko-KR/)에 있다. 글자 수는 파일 끝 줄바꿈을 뺀 Unicode 문자 수이며, 아래 명령으로 셌다. Play는 전각·반각 문자를 같은 한 글자로 센다([스토어 등록정보](https://support.google.com/googleplay/android-developer/answer/9859152)).

```sh
ruby -e 'Dir["fastlane/metadata/android/ko-KR/*.txt"].sort.each { |f| puts "#{File.basename(f)}: #{File.read(f, encoding: "UTF-8").chomp.length}" }'
```

| 파일 | 제한 | 2026-10-19 글자 수 |
| --- | --- | --- |
| `title.txt` | 30 | 26 |
| `short_description.txt` | 80 | 55 |
| `full_description.txt` | 4,000 | 1,251 |

### 제목 후보

| 후보 | 글자 수 | 판단 |
| --- | --- | --- |
| `VLR.GG Mobile (Unofficial)` | 26 | **권장. 파일에 넣은 값.** About의 앱 이름([AboutScreen.kt:197](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/about/AboutScreen.kt))과 #166의 Play 제목 결정([design/app-icon/README.md](design/app-icon/README.md))이 같다. 런처 이름 `VLR.GG Mobile`과도 이어진다. |
| `VLR.GG Mobile 비공식 앱` | 18 | 한국어 사용자에게 비공식임이 더 바로 읽힌다. About·상표 고지의 이름과 달라진다. |
| `발로란트 e스포츠 정보 (비공식)` | 18 | 제3자 사이트 이름을 제목에서 뺀다. 대신 `VALORANT` 상표 이름이 들어가고, About의 앱 이름과 달라진다. |

제목의 `VLR.GG`는 데이터 출처 사이트 이름이다. 제목·설명 첫 문장·About에서 비공식임과 제휴·승인이 없음을 밝히지만, 사칭·지식재산권 심사에서 문제가 되면 세 번째 후보처럼 사이트 이름을 뺀 제목으로 바꾼다. 이 판단은 소유자 결정 항목이다.

### 간단한 설명·자세한 설명

- 간단한 설명은 이모지·특수 기호·줄바꿈 없이 쉼표로 나열했다([스토어 등록정보](https://support.google.com/googleplay/android-developer/answer/9859152)).
- 자세한 설명은 주요 기능, 데이터 출처(VLR.GG), 비공식 앱 고지, Riot Games 상표 고지, 개인정보처리방침 링크, 문의 이메일을 담는다. 비공식·상표 고지는 About 문구([AboutScreen.kt:330-337](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/about/AboutScreen.kt))를 그대로 썼다.
- [메타데이터 정책](https://support.google.com/googleplay/android-developer/answer/9898842)에 맞춰 "공식"을 암시하는 표현, 순위·최고·인기 표현, 가격·할인 표현, 키워드 나열, 출처 없는 추천사, 이모지, 대문자 강조를 쓰지 않았다. 제목의 `VLR.GG`·`(Unofficial)` 대문자는 고유 이름 표기다.

### 그래픽 자산

| 자산 | 파일 | 규격 확인(`sips`) | 근거 |
| --- | --- | --- | --- |
| 앱 아이콘 | `images/icon.png` | 512×512, RGBA 32-bit PNG, 11,381 bytes | #166의 [`play-icon-512.png`](design/app-icon/play-icon-512.png)를 그대로 복사했다. Play 규격은 512×512, 32-bit PNG(알파 포함), 1,024KB 이하다([그래픽 자산](https://support.google.com/googleplay/android-developer/answer/9866151)). |
| 그래픽 이미지 | `images/featureGraphic.png` | 별도 작업 | 1024×500, JPEG 또는 24-bit PNG(알파 없음) |
| 휴대전화 스크린샷 | `images/phoneScreenshots/1~6_*.png` | 6장 모두 1080×1920, RGB 24-bit PNG, 알파 없음 | Play 규격은 JPEG 또는 24-bit PNG(알파 없음), 각 변 320~3,840px, 긴 변이 짧은 변의 2배 이하, 2~8장이다. 1080×1920(9:16) 4장 이상이면 추천 노출 조건도 맞는다([그래픽 자산](https://support.google.com/googleplay/android-developer/answer/9866151)). |

스크린샷 순서와 화면:

| 파일 | 화면 |
| --- | --- |
| `1_matches.png` | 경기 목록(결과 탭) |
| `2_match_detail.png` | 경기 상세(맵별 점수, 맵 선택·제외 순서) |
| `3_news.png` | 뉴스 목록 |
| `4_event_stats.png` | 대회 상세의 선수 통계 탭 |
| `5_team_detail.png` | 팀 상세(예정·최근 경기) |
| `6_mypage.png` | 마이페이지 즐겨찾기 팀 |

촬영 조건(2026-10-19):

- `main` 52bf0b0의 debug build를 Android 에뮬레이터(API 37)에 설치하고 로컬 서버(`./gradlew :server:run`, debug 기본 주소 `http://10.0.2.2:8080`)의 실제 VLR.GG 데이터로 찍었다([app-runtime.md:19](app-arch/app-runtime.md)). 화면에 서버 주소·디버그 표시는 나오지 않는다.
- 화면 크기는 `adb shell wm size 1080x1920`(density 420 유지)로 바꾸고 촬영 후 `wm size reset`, `wm density reset`으로 되돌렸다.
- 라이트 테마로 찍었다. 앱은 현재 라이트 테마만 지원한다.
- 상태 표시줄은 SystemUI demo mode(12:00, Wi-Fi, 배터리 가득 참, 알림 아이콘 없음)로 정리하고 촬영 후 껐다. 남아 있던 시스템 알림 2개는 1시간 snooze했다.
- 원본 캡처의 알파 채널은 macOS CoreGraphics로 흰 배경 위에 합성해 제거했다. 화면 내용은 바꾸지 않았다.
- 선수 상세 화면은 실제 인물 사진과 실명이 크게 나와 스크린샷에서 뺐다. 팀 상세는 로고만 나온다.
- About 화면은 찍지 않았다. 미병합 PR #169(오픈소스 라이선스 행)가 반영되면 About 화면이 바뀌기 때문이다.

## 6. 비공개 테스트 트랙

### 국가·지역

- 권장: **대한민국만 선택**
- 근거:
  - 앱 화면과 등록정보가 한국어뿐이다.
  - 테스터의 Play 국가가 트랙 국가에 포함돼야 설치할 수 있다. 테스트 트랙은 기본적으로 프로덕션 국가 설정을 따르며, "국가/지역 동기화 해제" 후 따로 고를 수 있다([테스트 트랙 국가 설정](https://support.google.com/googleplay/android-developer/answer/7550024)).
  - 개인정보처리방침이 「개인정보 보호법」을 기준으로 쓰였다. 다른 나라 이용자를 받으면 그 나라의 고지 요건을 따로 검토해야 한다.
- 소유자 결정: 해외에 있는 테스터가 있으면 그 국가를 추가한다. 추가 비공개 트랙은 국가 타깃팅을 지원하지 않으므로 기본 비공개 테스트 트랙을 쓴다([트랙 관리](https://support.google.com/googleplay/android-developer/answer/9845334)).

### 테스터

- 테스터 목록(이메일 목록 또는 Google 그룹)은 소유자가 준비한다. 테스터 신원은 저장소에 쓰지 않는다.
- 2023-11-13 이후 만든 개인 개발자 계정은 프로덕션 신청 전에 테스터 **12명 이상**이 **14일 연속** 비공개 테스트에 참여해야 한다. 중간에 나갔다 다시 들어오면 기간이 다시 시작된다([테스트 요구사항](https://support.google.com/googleplay/android-developer/answer/14151465)). 프로덕션 신청은 #164 범위 밖이다.

### 출시 노트 초안

`<ko-KR>` 태그 안에 넣는다. 500자 이하([출시 노트](https://support.google.com/googleplay/android-developer/answer/9859348)). 아래 태그 안 본문은 줄바꿈 포함 253자다.

```text
<ko-KR>
첫 비공개 테스트 버전입니다.
- 경기 일정과 결과, 경기 상세(맵별 점수)를 볼 수 있습니다.
- VLR.GG 뉴스 목록과 본문을 읽을 수 있습니다.
- 대회별 경기, 뉴스, 선수 통계를 확인할 수 있습니다.
- 팀·선수 상세와 검색, 기기 안에 저장되는 즐겨찾기를 지원합니다.
- 앱 이름과 아이콘을 바꾸고 About에 비공식 앱 고지와 개인정보처리방침 링크를 추가했습니다.
문제나 의견은 teamcotton124@gmail.com으로 보내 주세요.
</ko-KR>
```

### 다음 버전

- 권장: **versionName `0.2.0`, versionCode `3`**
- 근거:
  - 마지막 internal 기록은 `0.1.0(2)`다([app-release.md:3](app-release.md)).
  - versionCode는 이전에 올린 값보다 커야 하고, 한 번 쓴 값은 다시 쓸 수 없다([버전 관리](https://developer.android.com/studio/publish/versioning)).
  - 이후 브랜딩(#166), About 고지·링크(#165), 개인정보처리방침(#167·#168)이 기능 단위로 추가돼 minor 버전을 올린다.
- 입력 방법: Actions의 `APP_VERSION=0.2.0`, `APP_BUILD_NUMBER=3`. Release build는 이 값을 versionName·versionCode로 쓴다([androidApp/build.gradle.kts:219-223](../app/androidApp/build.gradle.kts)).
- 주의: Fastlane lane은 internal track의 versionCode만 조회한다([Fastfile:39-45](../fastlane/Fastfile), [app-release.md:79](app-release.md)). 실행 전 Console의 App bundle 탐색기에서 3 이상이 이미 쓰였는지 확인한다. 쓰였다면 그보다 큰 값을 쓴다.
- 흐름: Actions로 internal에 올린 뒤 같은 빌드를 비공개 테스트로 승격하고 검토를 제출한다([#164](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/164) 과정 3).

## 7. 소유자 결정 필요 목록

| # | 항목 | 권장안 | 관련 절 |
| --- | --- | --- | --- |
| 1 | 제목 | `VLR.GG Mobile (Unofficial)`. 사칭 심사 대응 시 사이트 이름을 뺀 후보로 변경 | 5 |
| 2 | 웹사이트에 저장소 URL 사용 | 사용 | 4 |
| 3 | 태그 | 선택 사항 | 4 |
| 4 | 서버 로그 IP 주소 | "기기 또는 기타 ID"에 포함, 대략적인 위치는 "아니요" | 3 |
| 5 | 검색어를 "앱 내 검색 기록"으로 신고 | 신고 | 3 |
| 6 | 요청 주소의 조회 대상을 "앱 상호작용"으로 신고 | 신고 | 3 |
| 7 | 데이터 삭제 요청 방법 제공 | "아니요" | 3 |
| 8 | IARC 폭력 질문(기사 이미지의 게임 화면) | "아니요" | 2 |
| 9 | 비공개 테스트 국가 | 대한민국만 | 6 |
| 10 | 다음 버전 | `0.2.0(3)`, Console 사용 이력 확인 후 | 6 |

## 8. 남은 리스크

- 지식재산권·사칭 심사: 제목·설명의 `VLR.GG`, 스크린샷의 팀 로고와 대회 로고는 제3자 상표다. 비공식 고지와 제휴 없음 문구를 넣었지만, Play가 권리 증빙을 요구하거나 거절할 수 있다.
- 데이터 출처 이용 조건: VLR.GG 이용약관은 자동 scraping을 제한한다([feature/README.md](feature/README.md)의 "외부 제약"). 스토어 공개 범위가 넓어질수록 이 리스크도 커진다.
- 개인정보처리방침 URL이 GitHub 저장소에 의존한다. 저장소를 비공개로 바꾸면 URL이 끊긴다.
- Console 화면 문구(IARC 카테고리명, 광고 ID 질문)는 공식 도움말로 확인하지 못했다. 입력 시 화면 문구를 기준으로 이 문서와 다르면 고친다.
