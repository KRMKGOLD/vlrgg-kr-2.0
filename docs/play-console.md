# Play Console 입력 초안 (#164)

2026-10-19 기준 [#164](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/164)의 Play Console 앱 콘텐츠 선언, 스토어 등록정보, 비공개 테스트 입력값 초안이다. Console 입력과 테스터 모집은 소유자가 한다. 이 문서는 입력값과 근거만 담으며, Console 원본 응답·테스터 신원·배포 증거는 [앱 배포](app-release.md)의 공개 저장소 규칙에 따라 여기에 남기지 않는다.

- 대상 패키지: `kr.co.cotton.vlrgg_mobile`([app-release.md:46](app-release.md))
- 스토어 문구와 이미지 원본: [`fastlane/metadata/android/ko-KR/`](../fastlane/metadata/android/ko-KR/). 현재 lane은 `skip_upload_metadata`·`skip_upload_images`가 `true`라([Fastfile:56-60](../fastlane/Fastfile)) 자동 업로드에 쓰이지 않으며, Console에 복사해 붙이는 원본이다.
- 그래픽 이미지(`images/featureGraphic.png`)도 같은 경로에 있다. 1024×500 규격이다.
- 근거 표기의 `파일:줄`은 이 문서 작성 시점의 `main`(52bf0b0) 기준이다. merged manifest와 의존성은 같은 커밋을 로컬에서 빌드해 확인했다.
- 이 문서의 선언 근거와 스크린샷은 출시 후보가 아니라 52bf0b0 기준이다. Console에 입력하기 전에 실제 출시 후보 SHA로 merged manifest·의존성·데이터 전송 경로를 다시 확인한다. 스크린샷은 비공개 테스트에 임시본 3장을 쓰고, 프로덕션 신청 전에 재촬영을 다시 판단한다(5절 "스크린샷 상태", 7절 12·13).
- `확인 필요` 표시는 공식 자료로 확인하지 못한 항목이다. Console 화면 문구가 다르면 화면을 따른다.

## 1. 확인한 앱 사실

Console 답변은 아래 사실을 기준으로 한다. 앱 코드나 의존성이 바뀌면 이 표부터 다시 확인한다.

2026-10-26 `main` 7ed3dc1(#169 포함)에서 merged manifest(debug·release)와 `releaseRuntimeClasspath`를 다시 확인했다. 권한은 아래 "권한" 절의 3개 그대로이고 release에 평문 HTTP 허용이 없다. 광고·분석·결제 의존성도 없다. #169로 AboutLibraries(`com.mikepenz:aboutlibraries-core`, `aboutlibraries-compose-m3`)가 추가됐지만, 앱에 묶인 라이선스 데이터(`R.raw.aboutlibraries`)만 읽고 기기 밖으로 데이터를 보내지 않는다. 문의 링크의 `mailto:`는 사용자가 누를 때 메일 앱을 여는 것뿐이다. 그래서 2·3절 답은 바뀌지 않는다. 출시 후보 SHA가 이 커밋과 다르면 다시 확인한다.

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
| 검색어 전송 | 입력 중에는 보내지 않는다. 사용자가 유효한 검색어를 명시적으로 제출할 때만 조회 서버로 보낸다. 검색을 쓰지 않아도 다른 기능을 모두 쓸 수 있다. | [SearchViewModel.kt:33-45](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/search/SearchViewModel.kt), [SearchViewModel.kt:76-87](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/search/SearchViewModel.kt) |
| 외부 이미지 | 팀 로고·선수 사진·기사 이미지를 기기가 VLR.GG가 게시한 이미지 주소에서 직접 불러온다. 이때 해당 호스트가 기기 IP 주소 등을 받을 수 있다. | [privacy-policy.md:50](privacy-policy.md), [TeamDetailContent.kt:368](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/team/detail/TeamDetailContent.kt), [NewsDetailContent.kt:243](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/news/detail/NewsDetailContent.kt) |
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
  - 근거: 앱은 공개된 e스포츠 경기 결과·대회·팀·선수 정보와 기사를 읽기 전용으로 보여 준다. 게임이 아니고, 사용자 콘텐츠를 공유하는 소셜 앱이나 상점·스트리밍 서비스도 아니다. ESRB는 뉴스·교육 목적의 비게임 앱이 같은 내용의 다른 앱보다 낮은 등급을 받을 수 있다고 안내한다. 이는 등급 결과에 대한 설명이며, 질문에 콘텐츠가 없다고 답할 근거는 아니다([ESRB 비게임 앱 기준](https://www.esrb.org/esrb-ratings-criteria-for-non%E2%80%91game-apps/)).
  - 카테고리 이름은 Console 질문지 안에서만 확인할 수 있다(`확인 필요`). 절차는 [콘텐츠 등급](https://support.google.com/googleplay/android-developer/answer/9859655)을 따른다.
- 질문별 답변. 질문 문구는 카테고리에 따라 달라지므로 주제별로 적는다.

| 주제 | 답변 | 근거 |
| --- | --- | --- |
| 폭력(게임·비사실적 폭력 묘사 포함) | 게임·비사실적 폭력: 예. 사실적·잔인한 폭력: 아니요(2026-10-26 소유자 결정) | 앱이 만든 화면은 경기 점수·통계·텍스트·팀 로고·선수 사진이다. 기사 이미지는 VLR.GG 원문을 그대로 보여 준다. 아래 "폭력 문항 결정" 참고 |
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

- 폭력 문항 결정(2026-10-26 소유자 결정)
  - 기사 본문 이미지는 서버 parser가 VLR.GG 원문에서 그대로 가져오고 앱이 선별 없이 표시한다([NewsParser.kt:139-175](../server/src/main/kotlin/kr/co/cotton/vlrgg_mobile/feature/news/NewsParser.kt), [NewsDetailContent.kt:242-256](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/news/detail/NewsDetailContent.kt)). 발로란트 경기 화면처럼 무기·전투가 보이는 이미지가 들어갈 수 있다.
  - 게임 화면·비사실적·혈흔 없는 폭력 묘사를 묻는 문항은 **예**로 답한다. 타깃 연령이 16세 이상(아래 "타깃층 및 콘텐츠")이라 등급이 올라가도 타깃층과 충돌하지 않고, 기사 이미지에 게임 속 교전 장면이 들어갈 수 있어 "아니요"는 부정확한 답이 될 수 있다. 원작 게임 VALORANT도 국내 15세 이용가다.
  - 사실적·잔인한 폭력, 혈흔·신체 훼손을 묻는 문항은 **아니요**로 답한다. 경기 기사 이미지는 게임 화면과 선수 사진이 대부분이라 해당하지 않는다.
  - Console 질문지의 실제 문항 문구는 확인하지 못했다(`확인 필요`). 입력 시 위 원칙으로 문항별로 사실대로 답한다. Play는 질문지에 정확하게 답하도록 요구한다([콘텐츠 등급](https://support.google.com/googleplay/android-developer/answer/9859655)).
  - 기사 이미지는 VLR.GG 원문에 따라 계속 바뀐다. 제출 뒤에도 기사 이미지의 성격이 달라지면 질문지를 다시 평가하고 제출한다.

### 타깃층 및 콘텐츠

- 타깃 연령: **16~17세, 18세 이상**. 15세 이하 그룹은 선택하지 않는다.
- 아동에게 의도치 않게 어필하는지: **아니요**. 스토어 문구와 스크린샷에 아동 대상 캐릭터·애니메이션이 없다.
- 근거: [타깃층 및 콘텐츠](https://support.google.com/googleplay/android-developer/answer/9867159). 아동이 포함된 연령 그룹을 하나라도 고르면 가족 정책을 따라야 하고, 21세 미만 그룹은 현지 법상 아동에 해당하는지 따져야 한다. 13~15·16~17세는 일부 지역에서 아동을 포함할 수 있다고 안내한다. 배포 국가는 대한민국(6절 "국가·지역")이고 「개인정보 보호법」은 만 14세 미만을 아동으로 보므로, 13~15세 그룹에는 국내 기준 아동(13세)이 들어간다. 그래서 13~15세를 빼고 16세 이상만 고르며, 국내 기준 아동이 포함된 그룹이 없으므로 가족 정책은 적용되지 않는다. 앱은 연령을 수집하지 않고 처리 근거도 동의가 아니므로(처리방침 1절, 「개인정보 보호법」 제15조 제1항 제4호) 연령 확인·법정대리인 동의 절차는 두지 않는다. 배포 국가를 늘리거나, 타깃층에 15세 이하를 넣거나, 연령·계정 기능을 추가하면 이 판단을 다시 한다. 개인정보처리방침도 만 14세 미만 아동을 대상으로 개인정보를 따로 수집하지 않는다고 적는다([privacy-policy.md:131-133](privacy-policy.md)).
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

### 데이터 전송 경로

Play는 앱과 앱에 포함된 라이브러리가 기기 밖으로 보내는 데이터를, 받는 곳이 개발자 서버든 제3자 서버든 수집으로 본다([데이터 보안 섹션 안내](https://support.google.com/googleplay/android-developer/answer/10787469)). 그래서 개발자가 직접 받지 않는 경로도 아래 목록에 넣는다.

| 경로 | 데이터 | 방침 근거 |
| --- | --- | --- |
| 앱의 Firebase Crashlytics | 비정상 종료·ANR 기록(stack trace), 기기·OS·앱 상태, Crashlytics 설치 UUID, Firebase 설치 ID | [privacy-policy.md:13](privacy-policy.md) |
| 앱의 Firebase Sessions(Crashlytics 구성 요소) | 세션 ID·순번·시작 시각, 앱·OS·기기 모델, Firebase 설치 ID, SDK 버전 | [privacy-policy.md:14](privacy-policy.md) |
| 조회 서버(Cloud Run 요청 로그) | IP 주소, User-Agent, 요청 주소(팀·선수·경기 식별 번호와 검색어 포함), 요청 시각·크기·상태·처리 시간. 30일 보관 | [privacy-policy.md:15](privacy-policy.md), [privacy-policy.md:67](privacy-policy.md) |
| 외부 이미지 호스트(VLR.GG가 게시한 이미지 주소, 예: `www.vlr.gg`, `owcdn.net`) | 팀 로고·선수 사진·기사 이미지를 불러올 때 기기가 직접 보내는 요청. 호스트는 기기 IP 주소와 요청한 이미지 주소 등 일반적인 인터넷 요청 정보를 받을 수 있다. 호스트의 보존 기간과 IP 사용 목적은 확인하지 못했다. | [privacy-policy.md:50](privacy-policy.md), 1절 "외부 이미지" 행 |

외부 이미지 호스트 경로의 분류:

- 데이터 유형: 기기 IP 주소는 서버 로그 IP와 같은 기준으로 "기기 또는 기타 ID"에 포함한다. 이 유형은 Firebase 설치 ID 때문에 이미 신고한다. 요청한 이미지 주소로 어떤 팀·선수·기사를 봤는지 추정될 수 있으므로 "앱 상호작용" 신고 범위에도 포함해 본다.
- 공유 예외 해당 여부: 이 호스트는 개발자와 계약해 개발자를 대신해 처리하는 서비스 제공업체가 아니다. 그래서 서비스 제공업체 예외는 적용되지 않는다. 이미지는 화면을 열면 자동으로 불러오고 사용자는 보통 제3자 호스트에서 온다는 것을 알지 못하므로, "사용자 시작 전송" 예외의 근거도 약하다.
- 결론(2026-10-26 소유자 결정): 이 경로를 **공유**로 신고한다. Play는 앱에 포함된 라이브러리가 제3자에게 직접 보내는 데이터도 공유로 본다([데이터 보안 섹션 안내](https://support.google.com/googleplay/android-developer/answer/10787469)). 이미지 로딩 라이브러리가 이 호스트로 직접 요청을 보낸다(아래 "판단이 갈리는 항목" 5).

수집으로 보지 않는 것:

- 즐겨찾기는 기기 안에서만 처리한다. 기기 밖으로 보내지 않는 데이터는 수집이 아니다.
- 검색어는 조회 서버가 VLR.GG 검색에 전달하지만 IP·기기 정보는 넘기지 않는다([privacy-policy.md:23](privacy-policy.md)). 이 전달은 사용자가 요청한 검색을 처리하는 과정이며, 방침도 제3자 제공으로 보지 않는다.

### 공통 질문

| 질문 | 답변 | 근거 |
| --- | --- | --- |
| 필수 사용자 데이터 유형을 수집하거나 공유하나요? | 예(수집함, 외부 이미지 호스트 경로는 공유함) | 위 데이터 전송 경로 표 |
| 수집한 모든 사용자 데이터가 전송 중 암호화되나요? | 예 | 1절 "전송 암호화" 행. Firebase는 HTTPS로 전송하고, Release 앱은 HTTPS 서버 주소만 허용한다. Release manifest는 평문 HTTP를 허용하지 않으므로([AndroidManifest.xml](../app/androidApp/src/main/AndroidManifest.xml)에 `usesCleartextTraffic` 없음, Android 9 이상 기본값) 외부 이미지 요청도 HTTPS로만 성공한다([네트워크 보안 구성](https://developer.android.com/privacy-and-security/security-config#CleartextTrafficOptIn)). |
| 사용자가 데이터 삭제를 요청할 방법을 제공하나요? | 예(공개 이메일로 요청 접수, 2026-10-26 소유자 결정) | 개인정보처리방침이 문의처 이메일로 삭제 요청을 받는다([privacy-policy.md:113-116](privacy-policy.md)). 계정이 없어 특정 이용자의 데이터를 찾지 못할 수 있다. 아래 "판단이 갈리는 항목" 4 참고 |
| 공유 | 예(외부 이미지 호스트 경로, 2026-10-26 소유자 결정) | Google(Firebase, Google Cloud)은 개발자를 대신해 처리하는 서비스 제공업체이며, 서비스 제공업체 전송은 공유가 아니다([privacy-policy.md:72-85](privacy-policy.md)). 외부 이미지 호스트 경로는 서비스 제공업체가 아니고 예외 근거도 약해 공유로 신고한다(아래 판단 항목 5). |
| 선택 가능 여부 | 앱 내 검색 기록만 선택, 나머지는 필수 | Crashlytics·Sessions 자동 수집은 앱 안에서 끌 수 없고, 서버 요청 로그와 이미지 요청은 조회마다 생긴다([privacy-policy.md:103](privacy-policy.md)). 검색어는 사용자가 검색을 명시적으로 제출할 때만 보내며, 검색 없이도 앱을 쓸 수 있다(1절 "검색어 전송" 행). Play는 사용자가 수집을 통제하고 그 데이터 없이 앱을 쓸 수 있으면 선택으로 본다([데이터 보안 섹션 안내](https://support.google.com/googleplay/android-developer/answer/10787469)). |
| 계정 삭제 URL | 해당 없음 | 계정을 만들 수 없는 앱은 계정 삭제 요구가 없다([계정 삭제 요구사항](https://support.google.com/googleplay/android-developer/answer/13327111)). |

### 데이터 유형별 답변

| Console 데이터 유형 | 수집 | 공유 | 처리 | 선택 | 목적 | 출처 |
| --- | --- | --- | --- | --- | --- | --- |
| 앱 정보 및 성능 › 비정상 종료 로그 | 예 | 아니요 | 일시적 아님 | 필수 | 분석, 앱 기능 | Crashlytics stack trace·ANR |
| 앱 정보 및 성능 › 진단 | 예 | 아니요 | 일시적 아님 | 필수 | 분석, 앱 기능 | Crashlytics 기기·OS·앱 상태, Sessions 앱·기기 정보 |
| 앱 정보 및 성능 › 기타 앱 성능 데이터 | 예 | 아니요 | 일시적 아님 | 필수 | 분석 | Sessions 세션 시작·순번(crash-free 비율 산출) |
| 기기 또는 기타 ID | 예 | 예(외부 이미지 호스트의 IP 주소) | 일시적 아님 | 필수 | 분석, 앱 기능 | Crashlytics 설치 UUID, Firebase 설치 ID, 세션 ID. 서버 로그와 외부 이미지 요청의 IP 주소(아래 판단 항목 1·5) |
| 앱 활동 › 앱 내 검색 기록 | 예(2026-10-26 소유자 결정) | 아니요 | 일시적 아님 | 선택 | 앱 기능 | 사용자가 제출한 검색어. 서버 로그의 요청 주소에 남는다. |
| 앱 활동 › 앱 상호작용 | 예(2026-10-26 소유자 결정) | 예(외부 이미지 호스트의 이미지 주소) | 일시적 아님 | 필수 | 앱 기능, 분석 | 서버 로그의 요청 주소(조회한 팀·선수·경기 식별 번호), Sessions 포그라운드 시각, 외부 이미지 요청의 이미지 주소 |
| 위치 › 대략적인 위치 | 아니요(2026-10-26 소유자 결정) | - | - | - | - | 아래 판단 항목 1 |

공유 칸이 "예"인 유형은 Console이 공유 목적을 따로 묻는다. 두 유형 모두 공유 목적은 **앱 기능**만 고른다. 이미지를 표시하려고 생기는 요청이다.

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
   - 소유자 결정(2026-10-26): IP 주소는 "기기 또는 기타 ID"에 포함하고, 대략적인 위치는 "아니요"로 답한다. 위치 분석이나 국가별 집계를 도입하면 "대략적인 위치"를 추가한다.
2. 요청 주소의 검색어
   - "앱 내 검색 기록"은 사용자가 앱에서 검색한 내용이다. 검색어가 요청 주소에 담겨 30일 보관되므로, 요청 처리 중 메모리에서만 쓰고 버리는 일시적 처리에 해당하지 않는다.
   - 소유자 결정(2026-10-26): "수집: 예, 공유: 아니요, 선택, 목적: 앱 기능"으로 신고한다. 검색어는 사용자가 검색을 제출할 때만 보내므로 선택으로 표시한다. 제출된 뒤에는 요청 로그에 남으므로 일시적 처리가 아니다. 개인정보처리방침도 검색어를 접속 기록 항목으로 적는다([privacy-policy.md:15](privacy-policy.md)).
   - 신고하지 않으려면 서버 로그에서 검색어를 남기지 않는 변경이 먼저 필요하다. 이는 운영 GCP 변경이라 이 작업 범위 밖이다.
3. 요청 주소의 팀·선수·경기 식별 번호
   - 어떤 화면을 조회했는지 30일 동안 남으므로 "앱 상호작용(페이지 조회 등)"으로 신고한다. 소유자 결정(2026-10-26): 수집 예, 필수, 목적 앱 기능·분석. 외부 이미지 요청의 이미지 주소 때문에 공유 예(판단 항목 5).
   - 개발자는 이 기록을 사용 행태 분석에 쓰지 않지만, 보관한다는 사실 때문에 넓게 신고한다.
4. 삭제 요청
   - 개인정보처리방침은 문의처 이메일로 삭제 요청을 받는다([privacy-policy.md:113-116](privacy-policy.md)). Play의 이 질문은 삭제 요청을 받는 방법이 있는지를 묻는다([데이터 보안 섹션 안내](https://support.google.com/googleplay/android-developer/answer/10787469)). 그래서 처리방침과 같게 "예"로 답한다.
   - 한계: 계정이 없어 특정 이용자의 오류 보고·세션 데이터나 접속 기록을 찾지 못할 수 있다. 이 경우 처리방침대로 그 사유를 알린다([privacy-policy.md:116](privacy-policy.md)). 요청을 처리하지 못하는 경우가 있다고 요청 경로가 없어지지는 않는다.
   - 자동 삭제만으로는 답하지 않는다. 서버 로그는 30일, Crashlytics 오류 기록은 90일 뒤 삭제가 시작되지만([privacy-policy.md:64-67](privacy-policy.md)), Firebase 설치 ID와 오류 없이 끝난 세션 기록은 개발자가 삭제를 요청하기 전까지 남을 수 있다([privacy-policy.md:65-66](privacy-policy.md)).
   - 소유자 결정(2026-10-26): "예"로 답하고, 소유자가 문의처 메일함을 확인해 요청에 답한다. 이메일 요청 경로를 실제로 운영하지 않기로 하면 데이터 보안 답보다 처리방침을 먼저 고친다.
5. 외부 이미지 호스트 전송
   - 기기가 이미지 호스트에 직접 보내는 요청도 Play 정의상 수집이다. "개발자가 받지 않는다"는 이유로 신고 범위에서 빼지 않는다.
   - 소유자 결정(2026-10-26): **공유로 신고한다.** "기기 또는 기타 ID"(IP 주소)와 "앱 상호작용"(이미지 주소)의 공유 칸을 "예", 공유 목적을 "앱 기능"으로 답한다. 서비스 제공업체 예외는 해당하지 않고, 이미지는 화면을 열면 자동으로 불러오므로 사용자 시작 전송 예외의 근거도 약하다. Play는 앱에 포함된 라이브러리가 제3자에게 직접 보내는 데이터도 공유로 본다([데이터 보안 섹션 안내](https://support.google.com/googleplay/android-developer/answer/10787469)).
   - 개인정보처리방침 3항의 "제3자에게 제공하지 않습니다"는 「개인정보 보호법」상 개발자의 제공을 말한다. 이 경로는 개발자가 넘기는 것이 아니라 기기가 직접 요청하는 것이며 처리방침 "이미지 표시" 항목에 따로 적혀 있다([privacy-policy.md:50](privacy-policy.md)). Play의 공유 정의와 범위가 달라 처리방침은 고치지 않는다.
   - 이미지를 조회 서버를 거쳐 받도록 바꾸면 이 공유를 없앨 수 있지만, 서버 트래픽·비용이 늘어 이번 출시 범위에서 제외한다.
   - 호스트의 보존 기간과 IP 사용 목적은 확인하지 못했다. 이 문서와 Console 답에 추정해 쓰지 않는다.

### 스토어에 보일 요약(결정 기준)

- 수집 데이터: 앱 정보 및 성능, 기기 또는 기타 ID, 앱 활동
- 공유 데이터: 기기 또는 기타 ID, 앱 활동(외부 이미지 호스트 경로)
- 전송 중 데이터 암호화
- 데이터 삭제 요청 가능(공개 이메일 접수, 계정이 없어 식별이 제한될 수 있음)

## 4. 스토어 설정

| 항목 | 권장 입력 | 근거 |
| --- | --- | --- |
| 앱 또는 게임 | 앱 | 게임이 아니다. |
| 카테고리 | 스포츠 | Play는 스포츠 카테고리를 "스포츠 뉴스와 해설, 점수 추적, 판타지 팀 관리, 경기 중계"로 설명한다([카테고리 및 태그](https://support.google.com/googleplay/android-developer/answer/9859673)). 앱의 핵심이 e스포츠 경기 결과·대회·통계 추적이다. "뉴스 및 잡지"는 뉴스 앱 정책 대상이 되므로 고르지 않는다. |
| 태그 | `스포츠` 하나(2026-10-26 소유자 결정) | Console 태그 목록에 e스포츠·경기 결과 태그가 없다. 스포츠 아래 태그는 경마·야구·테니스처럼 특정 종목이라 고르지 않는다. `뉴스/잡지`·`뉴스 통합 서비스`는 2절 "뉴스 앱"의 해당 없음 결정과 맞지 않아 고르지 않는다. Play는 태그가 등록정보나 첫 화면만 봐도 분명히 관련 있어 보여야 한다고 요구한다([카테고리 및 태그](https://support.google.com/googleplay/android-developer/answer/9859673)). |
| 연락처 이메일 | `teamcotton124@gmail.com` | 필수 항목([스토어 등록정보](https://support.google.com/googleplay/android-developer/answer/9859152)). 개인정보처리방침 문의처와 같다([privacy-policy.md:144](privacy-policy.md)). |
| 전화번호 | 비움 | 공개 연락처는 이메일 하나만 쓴다. |
| 웹사이트 | `https://github.com/KRMKGOLD/vlrgg-kr-2.0`(2026-10-26 소유자 결정) | 선택 항목이지만 권장된다([스토어 등록정보](https://support.google.com/googleplay/android-developer/answer/9859152)). About의 "소스 코드" 링크와 같다([AboutUiState.kt:3](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/about/AboutUiState.kt)). 소유자는 개발 저장소가 스토어에서 바로 보이는 점을 받아들였다. 저장소를 비공개로 바꾸면 이 링크도 끊긴다. |
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
| `VLR.GG Mobile (Unofficial)` | 26 | **결정(2026-10-26). 파일에 넣은 값.** About의 앱 이름([AboutScreen.kt:197](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/about/AboutScreen.kt))과 #166의 Play 제목 결정([design/app-icon/README.md](design/app-icon/README.md))이 같다. 런처 이름 `VLR.GG Mobile`과도 이어진다. |
| `VLR.GG Mobile 비공식 앱` | 19 | 한국어 사용자에게 비공식임이 더 바로 읽힌다. About·상표 고지의 이름과 달라진다. |
| `발로란트 e스포츠 정보 (비공식)` | 18 | 제3자 사이트 이름을 제목에서 뺀다. 대신 `VALORANT` 상표 이름이 들어가고, About의 앱 이름과 달라진다. |

제목의 `VLR.GG`는 데이터 출처 사이트 이름이다. 제목·설명 첫 문장·About에서 비공식임과 제휴·승인이 없음을 밝히지만, 소유자가 2026-10-26 이 제목으로 확정했다. 사칭·지식재산권 심사에서 반려되면 그때 세 번째 후보처럼 사이트 이름을 뺀 제목을 다시 정한다.

### 간단한 설명·자세한 설명

- 간단한 설명은 이모지·특수 기호·줄바꿈 없이 쉼표로 나열했다([스토어 등록정보](https://support.google.com/googleplay/android-developer/answer/9859152)).
- 자세한 설명은 주요 기능, 데이터 출처(VLR.GG), 비공식 앱 고지, Riot Games 상표 고지, 개인정보처리방침 링크, 문의 이메일을 담는다. 비공식·상표 고지는 About 문구([AboutScreen.kt:330-337](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/about/AboutScreen.kt))를 그대로 썼다.
- [메타데이터 정책](https://support.google.com/googleplay/android-developer/answer/9898842)에 맞춰 "공식"을 암시하는 표현, 순위·최고·인기 표현, 가격·할인 표현, 키워드 나열, 출처 없는 추천사, 이모지, 대문자 강조를 쓰지 않았다. 제목의 `VLR.GG`·`(Unofficial)` 대문자는 고유 이름 표기다.

### 그래픽 자산

| 자산 | 파일 | 규격 확인(`sips`) | 근거 |
| --- | --- | --- | --- |
| 앱 아이콘 | `images/icon.png` | 512×512, RGBA 32-bit PNG, 11,381 bytes | #166의 [`play-icon-512.png`](design/app-icon/play-icon-512.png)를 그대로 복사했다. Play 규격은 512×512, 32-bit PNG(알파 포함), 1,024KB 이하다([그래픽 자산](https://support.google.com/googleplay/android-developer/answer/9866151)). |
| 그래픽 이미지 | `images/featureGraphic.png` | 1024×500, RGB 24-bit PNG, 알파 없음, 371,266 bytes | 완료. GPT image 2.5(`gpt-image-2.5-sunburst`)로 생성했다(a1f6893). Riot Games·VALORANT·팀 로고와 게임 화면 없이 앱 이름과 추상적인 경기 목록만 담았다. Play 규격은 1024×500, JPEG 또는 24-bit PNG(알파 없음)다([그래픽 자산](https://support.google.com/googleplay/android-developer/answer/9866151)). |
| 휴대전화 스크린샷 | `images/phoneScreenshots/1~6_*.png` | 6장 모두 1080×1920, RGB 24-bit PNG, 알파 없음 | **임시본.** 비공개 테스트에는 팀 로고가 없는 1·3·4번만 올린다(아래 "스크린샷 상태"). Play 규격은 JPEG 또는 24-bit PNG(알파 없음), 각 변 320~3,840px, 긴 변이 짧은 변의 2배 이하, 2~8장이다. 1080×1920(9:16) 4장 이상이면 추천 노출 조건도 맞는다([그래픽 자산](https://support.google.com/googleplay/android-developer/answer/9866151)). |

### 스크린샷 상태

현재 스크린샷 6장은 규격 확인과 화면 구성 검토를 위한 **임시본**이다. 소유자는 2026-10-26 다음과 같이 정했다.

- 비공개 테스트: 재촬영하지 않고 임시본 중 팀 로고가 없는 `1_matches.png`, `3_news.png`, `4_event_stats.png` 3장만 올린다. 로고가 보이는 2·5·6번은 올리지 않는다(아래 "팀 로고 권리 확인"). 1번의 날짜·시간 표시 문제는 비공개 테스트에서는 그대로 둔다.
- 프로덕션: 신청 전에 아래 재촬영 조건으로 다시 판단한다. 4장 미만이면 Play 추천 노출 조건(1080×1920 4장 이상)은 채우지 못한다.
- AI로 화면 안 내용(로고 등)을 지우거나 바꾼 이미지는 실제 앱 화면과 달라지므로 쓰지 않는다. 꾸밈은 프레임·배경·설명 문구 같은 화면 바깥에만 한다. Play는 스크린샷이 실제 앱 사용 경험을 보여 주도록 요구한다([그래픽 자산](https://support.google.com/googleplay/android-developer/answer/9866151)).

| 파일 | 화면 | 제출 전 문제 |
| --- | --- | --- |
| `1_matches.png` | 경기 목록(결과 탭) | 날짜 머리글이 영어(`Sun, October 18, 2026 Yesterday`)이고, 완료 경기의 상대 시간(`1d 1h`)이 무엇을 뜻하는지 분명하지 않다. |
| `2_match_detail.png` | 경기 상세(맵별 점수, 맵 선택·제외 순서) | ISO 원문 `2026-10-18T02:00:00Z`가 그대로 보인다. 이 시각은 KST 11:00인데 같은 화면의 `Sunday, October 18 3:00 PM KST`와 4시간 어긋난다([MatchDetailContent.kt:277](../app/shared/src/commonMain/kotlin/kr/co/cotton/vlrgg_mobile/ui/feature/matches/detail/MatchDetailContent.kt), [matches/README.md:134](feature/matches/README.md)). 실제 팀 로고가 보인다. |
| `3_news.png` | 뉴스 목록 | - |
| `4_event_stats.png` | 대회 상세의 선수 통계 탭 | - |
| `5_team_detail.png` | 팀 상세(예정·최근 경기) | 최근 경기 시각 표시가 재촬영 조건의 영향을 받는다. 실제 팀 로고가 보인다. |
| `6_mypage.png` | 마이페이지 즐겨찾기 팀 | 실제 팀 로고가 보인다. |

재촬영 조건(프로덕션 신청 전 판단):

1. 앱의 날짜·시간 표시 수정(별도 작업)을 끝낸다.
2. 경기 시각을 하나의 현지화된 시각으로만 표시한다. ISO 원문과 KST 표시가 4시간 어긋나는 문제를 없앤다.
3. 완료 경기의 상대 시간 표시를 사용자가 바로 이해할 수 있게 고친다.
4. 위 수정이 들어간 실제 출시 후보 SHA로 화면을 다시 찍고, 출시 후보의 화면이 다시 바뀌면 다시 찍는다.
5. 데이터 보안·권한 선언의 근거(merged manifest, 의존성, 데이터 전송 경로)와 스크린샷 검토도 같은 출시 후보 SHA 기준으로 다시 한다.
6. 아래 "팀 로고 권리 확인" 게이트를 통과한다.

### 팀 로고 권리 확인(제출 전 게이트)

- 스크린샷 2, 5, 6에 실제 팀 로고(LOUD, Team Vitality 등)가 보인다. 스크린샷 1, 2, 4, 5에는 대회 이름도 나온다.
- Play 지식재산권 정책은 무단 사용 예로 "대학 및 프로 스포츠팀 로고"를 들고, 개발자에게 권리 증빙을 요구할 수 있다고 적는다([지식재산권](https://support.google.com/googleplay/android-developer/answer/9888072)). 비공식 고지나 공개된 출처라는 사실은 사용 허락이 아니다.
- 제출 전에 다음 중 하나를 한다.
  - 남겨 둘 로고마다 사용 권리 근거를 확인해 기록한다.
  - 권리가 확인된 이미지만 보이거나 로고 이미지가 없는 실제 앱 화면만 쓴다.
- 소유자 결정(2026-10-26): 로고마다 권리를 확보하는 것은 현실적이지 않아, 로고 이미지가 없는 화면만 쓴다. 비공개 테스트에는 1·3·4번만 올린다. 심사에서 반려되면 비공개 테스트 시작이 늦어져 14일 기간도 밀린다.
- 이 게이트는 소유자가 이미 수용한 `VLR.GG` 이름 사용·scraping 리스크와 별개로 판단한다.

### 임시본 촬영 기록(2026-10-19)

- `main` 52bf0b0의 debug build를 Android 에뮬레이터(API 37)에 설치하고 로컬 서버(`./gradlew :server:run`, debug 기본 주소 `http://10.0.2.2:8080`)의 실제 VLR.GG 데이터로 찍었다([app-runtime.md:19](app-arch/app-runtime.md)). 화면에 서버 주소·디버그 표시는 나오지 않는다.
- 화면 크기는 `adb shell wm size 1080x1920`(density 420 유지)로 바꾸고 촬영 후 `wm size reset`, `wm density reset`으로 되돌렸다.
- 라이트 테마로 찍었다. 앱은 현재 라이트 테마만 지원한다.
- 상태 표시줄은 SystemUI demo mode(12:00, Wi-Fi, 배터리 가득 참, 알림 아이콘 없음)로 정리하고 촬영 후 껐다. 남아 있던 시스템 알림 2개는 1시간 snooze했다.
- 원본 캡처의 알파 채널은 macOS CoreGraphics로 흰 배경 위에 합성해 제거했다. 화면 내용은 바꾸지 않았다.
- 선수 상세 화면은 실제 인물 사진과 실명이 크게 나와 스크린샷에서 뺐다. 팀 상세에는 실제 팀 로고가 나온다(위 "팀 로고 권리 확인").
- About 화면은 찍지 않았다. 미병합 PR #169(오픈소스 라이선스 행)가 반영되면 About 화면이 바뀌기 때문이다.

## 6. 비공개 테스트 트랙

### 국가·지역

- 결정(2026-10-26 소유자): **대한민국만 선택**. 해외 테스터는 받지 않는다.
- 근거:
  - 앱 화면과 등록정보가 한국어뿐이다.
  - 테스터의 Play 국가가 트랙 국가에 포함돼야 설치할 수 있다. 테스트 트랙은 기본적으로 프로덕션 국가 설정을 따르며, "국가/지역 동기화 해제" 후 따로 고를 수 있다([테스트 트랙 국가 설정](https://support.google.com/googleplay/android-developer/answer/7550024)).
  - 개인정보처리방침이 「개인정보 보호법」을 기준으로 쓰였다. 다른 나라 이용자를 받으면 그 나라의 고지 요건을 따로 검토해야 한다.
- 나중에 해외 테스터를 받기로 하면 그 국가를 추가한다. 추가 비공개 트랙은 국가 타깃팅을 지원하지 않으므로 기본 비공개 테스트 트랙을 쓴다([트랙 관리](https://support.google.com/googleplay/android-developer/answer/9845334)).

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

- 결정(2026-10-26 소유자): 비공개 테스트에 올릴 버전은 **versionName `0.2.0`, versionCode `3`**
- 근거:
  - 마지막 internal 기록은 `0.1.0(2)`다([app-release.md:3](app-release.md)).
  - versionCode는 이전에 올린 값보다 커야 하고, 한 번 쓴 값은 다시 쓸 수 없다([버전 관리](https://developer.android.com/studio/publish/versioning)).
  - 이후 브랜딩(#166), About 고지·링크(#165), 개인정보처리방침(#167·#168)이 기능 단위로 추가돼 minor 버전을 올린다.
- 입력 방법: Actions의 `APP_VERSION=0.2.0`, `APP_BUILD_NUMBER=3`. Release build는 이 값을 versionName·versionCode로 쓴다([androidApp/build.gradle.kts:219-223](../app/androidApp/build.gradle.kts)).
- 주의: Fastlane lane은 internal track의 versionCode만 조회한다([Fastfile:39-45](../fastlane/Fastfile), [app-release.md:79](app-release.md)). 실행 전 Console의 App bundle 탐색기에서 3 이상이 이미 쓰였는지 확인한다. 쓰였다면 그보다 큰 값을 쓴다.
- 흐름: Actions로 internal에 올린 뒤 같은 빌드를 비공개 테스트로 승격하고 검토를 제출한다([#164](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/164) 과정 3).

## 7. 소유자 결정 목록

| # | 항목 | 결정 | 관련 절 |
| --- | --- | --- | --- |
| 1 | 제목 | **결정(2026-10-26)**: `VLR.GG Mobile (Unofficial)`. 사칭·지식재산권 심사에서 반려되면 사이트 이름을 뺀 대안 제목을 그때 정한다. | 5 |
| 2 | 웹사이트에 저장소 URL 사용 | **결정(2026-10-26)**: 사용. `https://github.com/KRMKGOLD/vlrgg-kr-2.0`을 입력한다. | 4 |
| 3 | 태그 | **결정(2026-10-26)**: `스포츠` 하나만 고른다. | 4 |
| 4 | 서버 로그 IP 주소 | **결정(2026-10-26)**: "기기 또는 기타 ID"에 포함, 대략적인 위치는 "아니요" | 3 |
| 5 | 검색어를 "앱 내 검색 기록"으로 신고 | **결정(2026-10-26)**: 신고. 수집 예, 공유 아니요, 선택, 목적 앱 기능 | 3 |
| 6 | 요청 주소의 조회 대상을 "앱 상호작용"으로 신고 | **결정(2026-10-26)**: 신고. 수집 예, 필수, 목적 앱 기능·분석. 공유는 11번 결정에 따라 예. | 3 |
| 7 | 데이터 삭제 요청 방법 제공 | **결정(2026-10-26)**: "예". 처리방침의 공개 이메일 요청 경로와 맞추고, 소유자가 메일함을 확인해 답한다. 계정이 없어 식별이 제한될 수 있다. 요청 경로를 운영하지 않기로 하면 처리방침을 먼저 고친다. | 3 |
| 8 | IARC 폭력 질문(기사 이미지의 게임 화면) | **결정(2026-10-26)**: 게임·비사실적 폭력 문항은 "예", 사실적·잔인한 폭력 문항은 "아니요". 타깃이 16세 이상이라 등급 상승과 충돌하지 않는다. 기사 이미지 성격이 바뀌면 다시 평가한다. | 2 |
| 9 | 비공개 테스트 국가 | **결정(2026-10-26)**: 대한민국만. 해외 테스터는 받지 않는다. | 6 |
| 10 | 다음 버전 | **결정(2026-10-26)**: 비공개 테스트는 `0.2.0(3)`. 배포 전에 Console App bundle 탐색기에서 3 이상 미사용을 확인한다. 테스트 중 업데이트 번호는 그때 정한다. | 6 |
| 11 | 외부 이미지 호스트 전송의 공유 여부 | **결정(2026-10-26)**: 공유로 신고. "기기 또는 기타 ID"·"앱 상호작용"의 공유 칸 예, 공유 목적 앱 기능. 처리방침은 고치지 않는다. | 3 |
| 12 | 팀 로고 권리 확인 | **결정(2026-10-26)**: 로고 권리는 확보하지 않고 로고 이미지가 없는 화면만 쓴다. 비공개 테스트에는 `1_matches`·`3_news`·`4_event_stats` 3장만 올린다. | 5 |
| 13 | 스크린샷 재촬영 | **결정(2026-10-26)**: 지금은 하지 않는다. 비공개 테스트에는 임시본을 쓰고, 프로덕션 신청 전에 5절 재촬영 조건으로 다시 판단한다. 선언 근거는 Console 입력 전에 출시 후보 SHA로 다시 확인한다. | 5 |

## 8. 남은 리스크

- `VLR.GG` 이름 사용: 제목·설명의 `VLR.GG`는 제3자 사이트 이름이다. 비공식 고지와 제휴 없음 문구를 넣었고 소유자가 이 리스크를 수용했지만, Play가 사칭 심사에서 문제 삼을 수 있다.
- 그래픽 이미지: 이미지 안의 제목이 `VLR.GG Mobile`이라 비공식 표기가 없다. 스토어 제목과 설명에는 비공식 표기가 있다.
- 팀 로고: 현재 스크린샷 2, 5, 6의 실제 팀 로고는 권리 근거를 확인하지 않았다. 그래서 이 3장은 올리지 않는다(5절 "팀 로고 권리 확인"). 앱 화면 안의 로고 표시는 이미 수용한 데이터 출처 리스크에 포함된다.
- 스크린샷: 비공개 테스트에 올리는 1·3·4번은 임시본이며, 1번에 날짜·시간 표시 문제가 있다. 프로덕션 신청 전에 5절 재촬영 조건으로 다시 판단한다.
- 데이터 보안의 공유 답: 외부 이미지 호스트 경로를 공유로 신고해 스토어에 "제3자와 데이터 공유"가 표시된다. 호스트의 보존 기간과 IP 사용 목적은 확인하지 못했다.
- IARC: 기사 이미지가 VLR.GG 원문에 따라 바뀌므로, 제출한 등급 답을 나중에 다시 평가해야 할 수 있다.
- 데이터 출처 이용 조건: VLR.GG 이용약관은 자동 scraping을 제한한다([feature/README.md](feature/README.md)의 "외부 제약"). 스토어 공개 범위가 넓어질수록 이 리스크도 커진다.
- 개인정보처리방침 URL이 GitHub 저장소에 의존한다. 저장소를 비공개로 바꾸면 URL이 끊긴다.
- Console 화면 문구(IARC 카테고리명, 광고 ID 질문)는 공식 도움말로 확인하지 못했다. 입력 시 화면 문구를 기준으로 이 문서와 다르면 고친다.
