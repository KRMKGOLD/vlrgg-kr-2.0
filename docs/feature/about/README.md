# About

About은 앱의 목적, 실제 build version, 공개 source·개인정보처리방침 링크, 메일 문의, 오픈소스 라이선스, 테마 범위, 데이터 출처와 비공식·상표 고지를 안내하는 다섯 번째 root다. 별도 API나 scraping은 사용하지 않는다. 스토어 공개 배포를 기준으로 범위를 정한다. Android/iOS 실기기 external-open·접근성 검증은 남아 있다.

## MVP 범위

- 앱 이름 `VLR.GG Mobile (Unofficial)`, 과장 없는 소개 문구, 실제 build version
- 프로젝트 링크 네 행. 표시 순서는 아래와 같다.
  - 소스 코드(외부): `https://github.com/KRMKGOLD/vlrgg-kr-2.0`
  - 개인정보처리방침(외부): `https://github.com/KRMKGOLD/vlrgg-kr-2.0/blob/main/docs/privacy-policy.md`
  - 문의(외부 메일 앱): `mailto:teamcotton124@gmail.com`. 보조 문구는 `teamcotton124@gmail.com`이며 공개를 승인받은 주소다.
  - 오픈소스 라이선스(앱 내부 화면)
- 현재 지원 테마 라이트와 다크 모드 후속 안내
- 데이터 출처 VLR.GG, VLR.GG·Riot Games 비제휴 고지, Riot Games 상표 고지
- 공통 Top App Bar Search와 root 상태 복원

Dark Mode 선택, 계정·프로필, 앱 내 문의 양식, 원격 About 설정은 제외한다. 문의는 메일 앱(`mailto:`)으로만 받는다.

## 화면과 이동

화면은 App identity, Project links(소스 코드 → 개인정보처리방침 → 문의 → 오픈소스 라이선스), Appearance, Attribution(데이터 출처 → 비공식 앱 고지 → 상표 고지) 순서다. 과도한 카드나 마케팅형 hero를 사용하지 않고 외부 이동과 고지를 구분한다. Top App Bar 제목은 다른 root와 같이 `About`을 유지하고, 화면 안의 라벨은 한국어로 표시한다.

- Bottom navigation의 `About`에서 진입한다.
- Search는 About 위에 push되고 Back 시 기존 About 상태로 돌아온다.
- 각 row는 목적지와 이동 방식을 접근 가능한 label(`소스 코드 외부 링크 열기`, `개인정보처리방침 외부 링크 열기`, `메일 앱에서 문의 메일 쓰기`, `오픈소스 라이선스 화면 열기`)로 알린다. 같은 문구를 click label과 content description에 쓰고, row는 button role과 최소 48dp 높이를 가진다.
- 앱 밖으로 나가는 세 row는 `↗`, 앱 안 화면으로 가는 오픈소스 라이선스 row는 chevron(`>`)을 trailing에 둬 구분한다. 외부 row는 브라우저나 메일 앱으로 연다. Android는 `mailto:`를 `ACTION_SENDTO`로 보내 메일 앱만 대상으로 하고, `startActivity` 실패를 실패로 처리하므로 package visibility 선언이 필요 없다. iOS는 `canOpenURL` 사전 검사 없이 `UIApplication.open`의 완료 결과로 성공 여부를 판단한다(`LSApplicationQueriesSchemes`에 없는 `mailto:`는 `canOpenURL`이 항상 false).
- 오픈소스 라이선스 row는 Navigation 3 `OpenSourceLicenses` key를 About stack에 push한다. Back은 About으로 돌아오며 About 상태를 유지한다.
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
  | 문의 | `메일 앱을 열 수 없습니다.` |

- Snackbar timeout은 text-only 안내의 접근성 권장값을 적용한다. 사용자는 같은 row를 다시 눌러 재시도한다. 다른 링크 열기에 성공하면 남아 있던 Snackbar를 닫는다.
- About을 떠난 뒤 늦게 도착한 platform callback, 더 최근 링크 시도에 밀린 이전 callback, 화면 복귀 시 만료된 Snackbar는 무시한다.
- 원격 조회가 없으므로 Loading, Empty, Stale 화면이나 version placeholder를 만들지 않는다.

## 오픈소스 라이선스

- 화면: Top App Bar는 Back과 제목 `오픈소스 라이선스`. 본문은 AboutLibraries의 기본 Compose UI(`aboutlibraries-compose-m3`의 `LibrariesContainer`, 기본 설정)를 그대로 쓴다. 목록 row는 라이브러리 이름·버전·작성자·라이선스 badge를 보이고, row를 누르면 라이브러리 기본 license dialog에 전문을 띄운다(`확인` 또는 Back으로 닫음). 이 화면은 DESIGN.md 컴포넌트 계약 대상이 아니다.
- 상태: `OpenSourceLicensesViewModel`이 build 시 생성된 JSON을 한 번 읽어 `Libs`로 parse해 단일 `OpenSourceLicensesUiState`에 둔다. 읽기·parse 실패나 빈 목록은 `오픈소스 라이선스 정보를 불러올 수 없습니다.`만 표시한다(재시도 없음: 번들 파일이라 재시도가 의미 없다).
- 데이터 생성(AboutLibraries Gradle plugin):
  - Android: `:app:androidApp`에 `com.mikepenz.aboutlibraries.plugin.android`를 적용해 variant마다 runtime classpath로 `res/raw/aboutlibraries.json`을 생성한다. Firebase Crashlytics, activity-compose, splashscreen 같은 앱 모듈 의존성과 `:app:shared` 의존성이 모두 들어간다. Release resource는 release runtime classpath 기준이다(debug에는 `debugImplementation` ui-tooling만 추가된다). platform owner(`AndroidAboutPlatform.readLicenseDefinitions`)가 이 raw resource를 읽어 `App`에 전달한다.
  - iOS: `:app:shared`의 `exportLibraryDefinitions`가 iosArm64 Kotlin/Native graph(`collect.all` + `filterVariants = iosArm64`; K/N에는 `*Classpath` configuration이 없다)로 JSON을 만들고 iosMain compose resource(`files/aboutlibraries.json`)로 번들한다. `:app:shared`의 Android variant 생성 task는 끈다. AboutLibraries가 attribute 없는 detached configuration으로 POM을 찾을 때 K/N publication의 `kotlin-api`/`kotlin-metadata` variant가 모호해지므로 `:app:shared`에 `kotlin-api` 우선 disambiguation rule을 둔다.
  - SPM으로 들어오는 Firebase Apple SDK는 Gradle이 수집하지 못하므로 `app/shared/aboutlibraries/libraries/`에 수동 정의한다: firebase-ios-sdk 12.19.2, GoogleDataTransport 10.1.1, GoogleUtilities 8.1.3, nanopb 2.30910.1(Zlib), Promises 2.4.1(Apache-2.0). firebase-ios-sdk 행은 Crashlytics가 `third_party/libunwind/dwarf.h`를 포함하므로 12.19.2 `Crashlytics/LICENSE` 전문(Apache License 2.0 본문 뒤 Hewlett-Packard libunwind MIT 고지까지)을 쓴다. **Firebase SPM 버전을 바꾸면 `app/iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`, 이 파일들, 해당 버전의 `Crashlytics/LICENSE` 정의를 함께 갱신한다.**
  - 번들된 제3자 asset: `composeResources/drawable`에는 Google Material Symbols(Apache-2.0) 아이콘만 두고, Android·iOS 양쪽 config에 `Material Symbols` 항목을 둔다(쓰지 않던 Compose Multiplatform 템플릿 로고는 삭제했다). 런처·스플래시 아이콘은 자체 제작(`docs/design/app-icon/`)이고 번들 폰트는 없다.
- 라이선스 전문(로컬 번들):
  - 두 모듈 모두 `offlineMode = true`라 plugin은 build 중 SPDX 전문을 내려받지 않는다. 전문은 각 모듈의 `aboutlibraries/licenses/*.json`(`hash`가 생성 JSON의 license key와 같으면 plugin이 병합한다)에서만 온다. 정의에는 받은 원문을 그대로 넣고 요약·수정하지 않는다. 생성 JSON에 쓰이는 license만 둔다(이 폴더의 정의는 쓰이지 않아도 출력에 들어간다).

    | 모듈 | 정의 파일 | 원문 출처(2026-10-17 확인) |
    | --- | --- | --- |
    | androidApp, shared | `Apache-2.0.json` | https://spdx.org/licenses/Apache-2.0.txt |
    | androidApp | `BSD-3-Clause.json` | https://spdx.org/licenses/BSD-3-Clause.txt |
    | androidApp | `MIT.json` | https://spdx.org/licenses/MIT.txt |
    | androidApp | `ASDKL.json` | https://developer.android.com/studio/terms (본문 `sdk-terms` 블록, 개정일 April 28, 2026) |
    | shared | `Zlib.json` | https://spdx.org/licenses/Zlib.txt |
    | shared | `firebase-ios-sdk-12.19.2-crashlytics.json` | https://github.com/firebase/firebase-ios-sdk/blob/12.19.2/Crashlytics/LICENSE |

  - `ASDKL`은 play-services-basement 18.9.0, play-services-tasks 18.4.0, firebase-measurement-connector 20.0.1의 POM이 가리키는 Android Software Development Kit License(`https://developer.android.com/studio/terms.html`)다. 페이지의 약관 본문에서 HTML 태그와 연속 빈 줄만 걷어낸 텍스트로 넣어 이 세 행도 전문 dialog가 열린다.
  - 누락 차단: Android는 variant마다 `verifyLibraryDefinitions<Variant>`가 생성된 `res/raw/aboutlibraries.json`을, iOS는 `verifyIosLibraryDefinitions`가 `files/aboutlibraries.json`을 읽는다. license가 없는 library나 전문이 빈 license가 하나라도 있으면 실패한다. 각각 `merge<Variant>Resources`(release 포함), `copyNonXmlValueResourcesForIosMain`(iOS framework·test 공통 경로) 전에 실행된다. 새 dependency가 다른 license를 가져오면 build가 멈추므로 원문을 받아 정의를 추가한다.
- 버전 고정: Gradle plugin은 15.1.1, 런타임(`aboutlibraries-core`, `aboutlibraries-compose-m3`)은 14.2.1이다.
  - 런타임 15.x AAR은 minCompileSdk 37과 androidx.core 1.19(AGP 9.1+)를 요구해 현재 compileSdk 36 / AGP 9.0.1에서 빌드되지 않는다. 14.2.1은 minCompileSdk 36이며 Compose 1.10.3을 요구하지만 CMP 1.11.1로 올라가 해석된다. transitive로 `com.github.skydoves:compose-stability-runtime` 0.7.1과 `androidx.core:core-ktx` 1.18.0이 추가된다.
  - plugin 14.2.1은 KMP artifact의 version을 잘못 기록하고(예: 실제 1.11.0인 kotlinx-coroutines-core-jvm을 1.9.0으로) `-jvm`·`-iosarm64` artifact를 병합하지 않아 15.1.1을 쓴다. 15.1.1이 생성한 JSON을 14.2.1이 parse하는지는 iOS test와 Android device test로 검증한다.
  - 해제 조건: AGP 9.1 이상과 compileSdk 37로 올리면 plugin과 런타임을 15.x로 통일한다.

## 고지 경계

스토어 공개 배포를 전제로 한다. VLR.GG를 데이터 출처로 쓰지만 공식 앱·제휴·승인·후원을 뜻하지 않으며, 화면에 VLR.GG·Riot Games 비제휴와 Riot Games 상표 고지를 항상 표시한다. 개인정보 처리 내용은 앱 안에 전문을 두지 않고 저장소의 `docs/privacy-policy.md`를 외부 링크로 연다. 앱에 포함된 오픈소스 라이선스는 앱 안 화면에 표시한다. 데이터 사용 범위나 수집 항목이 바뀌면 고지 문구와 개인정보처리방침을 함께 다시 검토한다.

## 수용 기준

- [x] 다섯 번째 root에서 앱 이름 `VLR.GG Mobile (Unofficial)`, 소개, 가능한 경우의 build version, 소스 코드·개인정보처리방침·메일 문의·오픈소스 라이선스 row, 라이트 테마, 데이터 출처·비공식 앱·상표 고지를 정해진 순서로 표시한다.
- [x] 각 링크가 정확한 URL(`mailto:` 포함)로 열리고, version 누락과 external-open 실패가 나머지 정보나 화면 사용을 막지 않는다.
- [x] 링크별 오류 Snackbar의 문구·무동작·timeout과 늦은 callback 무시를 자동 검증한다.
- [x] 각 row의 목적과 외부·내부 이동을 screen reader가 식별하고(click label·content description·button role·48dp), 외부 `↗`와 내부 chevron으로 시각적으로 구분한다.
- [x] About → 오픈소스 라이선스 → 목록 → 전문 dialog → Back → About 흐름이 동작한다.
- [x] Android 라이선스 데이터는 앱 모듈 release runtime classpath(Firebase Crashlytics 포함)로, iOS 데이터는 shared K/N graph와 Firebase Apple SDK 수동 항목으로 build 때 생성되고 런타임이 parse한다.
- [x] 모든 행의 라이선스 전문이 네트워크 없이 저장소의 정의로 채워지고(ASDKL, iOS Firebase의 libunwind 고지 포함), 전문이 빈 항목이 있으면 Android 각 variant와 iOS build가 실패한다.
- [ ] iOS 배포 전 Firebase Apple SDK 수동 항목의 버전이 `Package.resolved`와 일치하는지 확인한다.
- [ ] Android/iOS 실기기에서 실제 external-open과 접근성을 확인한다.
