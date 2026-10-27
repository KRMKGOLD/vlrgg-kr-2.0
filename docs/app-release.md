# 앱 배포 계약

2026-09-27 기준 [#117](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/117)의 Android 내부 배포 검증은 완료됐다. `0.1.0(1)` 수동 설치 이후 `0.1.0(2)`의 [Actions 빌드·서명·Play internal 업로드](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/35855931943), 기존 앱의 Play 업데이트, 실기기 정상 동작과 실행 직후 crash·ANR 미발생을 확인했다. 동작·Crashlytics 확인은 #117에 기록된 사용자 확인이며 전체 접근성/E2E 검증 완료를 뜻하지 않는다.

iOS signing·TestFlight는 Apple Developer Program/App Store Connect 계정 준비 전 보류하며 [#139](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/139)에서 추적한다. 아래 내용은 후속 배포에도 유지할 절차이며, 완료된 Android 작업을 미완료로 다시 분류하지 않는다.

Android의 목표는 Google Play 프로덕션 공개다(2026-10-13 소유자 결정, [#160](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/160)). 자동화는 internal 업로드까지만 맡고, 비공개 테스트와 프로덕션 절차는 [Android 비공개 테스트와 프로덕션](#android-비공개-테스트와-프로덕션)에서 정한다. 이 문서의 나머지는 internal 배포와 공통 gate를 다룬다.

## Common gates

두 workflow는 manual-only이며 실행을 시작한 immutable `main` commit을 배포한다. checkout HEAD, workflow SHA와 frozen source SHA가 모두 일치해야 한다.

- `APP_VERSION`: 점으로 구분한 숫자 1~3개
- `APP_BUILD_NUMBER`: `1`~`2100000000`의 미사용 정수
- `API_BASE_URL`: credential, query, fragment와 `/` 이외 path가 없는 HTTPS origin

Android는 exact SHA의 최신 main CI run/attempt에서 `android`, iOS는 `ios` job 하나의 완료·성공을 요구한다. main push와 수동 CI 중 최신 실행을 선택하고 전체 run/job inventory·workflow·SHA·run·attempt 및 재조회 identity를 검사한다. 다른 플랫폼이나 aggregate 성공, skip, 이전 attempt 성공으로 대체하지 않는다. 대상이 skip됐다면 [명시적 CI 검증](ci-cd.md#skipped-platform-validation-before-deployment)을 같은 main SHA에서 실행한다. 초기 preflight 이후 environment 승인을 기다렸다면 배포 job이 인증·signing 정보를 사용하기 직전에 최신 CI를 다시 검사한다. deploy job은 [동일 플랫폼 CI와 실행 잠금](ci-cd.md#platform-ci-and-deployment-concurrency)을 공유하며 기존 플랫폼별 배포 workflow mutex도 유지한다. local/Console upload는 이 잠금에 포함되지 않는다.

공개 Actions log·summary·artifact는 비공개 경계가 아니다. URL 원문, signing credential, AAB/IPA와 raw Fastlane output을 올리지 않는다. URL은 앱 binary에서 추출 가능하므로 인증 수단으로 사용하지 않는다.

인증정보 없는 계약 검사:

```sh
bundle _2.4.22_ install
bundle _2.4.22_ exec fastlane lanes
ruby scripts/app-release/release_contract_test.rb
sh app/androidApp/scripts/test-release-config.sh
sh app/iosApp/Scripts/test_release_config.sh
```

도구와 Xcode 버전은 `.ruby-version`, `Gemfile.lock`과 workflow가 소유한다.

## GitHub environments

| Environment | Enable variable | Required secrets |
| --- | --- | --- |
| `android-internal` | `ANDROID_INTERNAL_DEPLOY_ENABLED=true` | `API_BASE_URL`, `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `FIREBASE_ANDROID_CONFIG_BASE64` |
| `ios-testflight` | 계정 준비 전 `IOS_TESTFLIGHT_DEPLOY_ENABLED`를 켜지 않음 | `API_BASE_URL`, `IOS_TEAM_ID`, `IOS_CERTIFICATE_BASE64`, `IOS_CERTIFICATE_PASSWORD`, `IOS_PROVISION_PROFILE_BASE64`, `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_KEY_CONTENT_BASE64`, `FIREBASE_IOS_CONFIG_BASE64` |

Android environment에는 `ANDROID_PLAY_WIF_PROVIDER`와 `ANDROID_PLAY_SERVICE_ACCOUNT` variables도 둔다. GitHub OIDC와 WIF로 Play 배포 Service Account를 impersonate하며 장기 service-account JSON key를 만들거나 저장하지 않는다. 권한은 대상 앱 조회와 testing release로 제한한다. Firebase config는 Play API credential과 별개다.

두 environment는 `main`만 허용한다. Android는 서버 인증과 별도 WIF pool/provider를 사용하고 immutable repository/owner ID, `main`, Android 배포 workflow, `workflow_dispatch`, `android-internal`로 제한한다. Play 배포 계정의 `roles/iam.workloadIdentityUser`는 해당 environment의 정확한 subject에만 부여한다. 임시 ADC `gha-creds-*.json`은 커밋하지 않으며 auth action의 종료 단계에서 삭제한다.

Firebase 설정은 [Crashlytics 설정 수명](app-crashlytics.md)에 따라 private temp file로 주입하고 종료 시 삭제한다. signing key·profile·App Store Connect credential도 environment 밖으로 노출하지 않는다.

## Android account and app gate

운영 소유자가 Play Console 가입, 약관, 결제, MFA와 요구되는 신원·연락처·기기 검증을 완료해야 한다. Firebase project Owner 권한은 Play 개발자 등록을 대신하지 않는다. 대상 package는 `kr.co.cotton.vlrgg_mobile`이며 기존 앱 record, Play App Signing, upload certificate와 versionCode 이력을 재사용한다.

2023-11-13 이후 개인 계정의 12명·14일 closed testing 요구는 production 접근 조건이며 internal track의 선행 조건으로 두지 않는다. 요건과 신청 절차는 [비공개 테스트 요건과 프로덕션 액세스](#비공개-테스트-요건과-프로덕션-액세스)를 따른다.

### Upload key and first AAB

앱 signing key와 upload key를 구분한다. CI가 사용하는 전용 upload keystore, alias와 password는 복구 가능하게 보관하고 GitHub environment에는 필요한 복사본만 둔다. 현재 원본 upload key와 첫 AAB는 gitignored local storage에 있으며 별도 recovery copy는 검증되지 않았다. workflow cleanup이 유일한 원본을 삭제해서는 안 된다.

Fastlane `supply`는 앱의 수동 초기 설정과 최소 한 번의 build upload를 전제로 하며 현재 lane도 upload 전에 internal track을 조회한다. 앱 record에 build가 없으면 최초 AAB를 Actions로 bootstrap하지 않는다.

최초 AAB는 다음 조건으로 수동 등록한다.

1. main CI `android`가 성공한 exact SHA의 깨끗한 전용 checkout을 사용한다.
2. Console에서 미사용 version/versionCode를 정하고 Actions와 같은 upload keystore·alias, release API URL과 Firebase config로 `:app:androidApp:bundleRelease`를 실행한다.
3. AAB signer의 SHA-256 fingerprint가 environment signing secret 원본의 선택한 alias certificate fingerprint와 같은지 확인한다. 다르면 upload하지 않는다.
4. AAB와 임시 keystore/config/build output은 repository와 공개 artifact 밖에 두고, Console 접수·Play App Signing·internal release 처리를 확인한다.

수동 bootstrap caller는 repository 밖 0700 private directory에 0600 signing copy를 만들고, `EXIT`/`finally`에서 이번 실행의 signing copy와 Android build output만 정리해야 한다. 성공한 첫 AAB는 cleanup 전에 별도 보호 위치로 복사한다. `with_config.py`는 자신이 만든 Firebase temp만 삭제하며 direct Gradle build는 lane/workflow cleanup을 실행하지 않는다. SIGKILL·전원 차단처럼 cleanup이 실행되지 않으면 이번 실행의 temp/build 잔여물을 직접 확인한다. 원본 upload key와 보호 위치의 첫 AAB는 자동 삭제하지 않는다.

첫 upload 뒤 Google Play Developer API를 활성화하고 Play Console에서 대상 앱의 조회·testing release 권한만 Service Account에 부여한다. track query가 성공하고 더 큰 미사용 versionCode를 확인한 뒤 Actions를 실행한다. Fastlane exit 0만으로 접수·tester 설치를 대신하지 않는다.

## Android completion evidence

배포마다 Play tester가 지정 계정으로 internal testing에 참여해 물리 Android 기기에서 설치·업데이트하고, 외부망에서 뉴스·경기 목록과 연결된 상세의 HTTPS 조회·표시·기본 이동을 확인한다. SHA, CI run, deployment run, version/build, Play receipt, tester install/update, 기기/OS, query 결과와 cleanup을 기록하되 tester identity와 raw logs는 공개하지 않는다. `0.1.0(2)`의 완료 근거와 사용자 확인 범위는 #117이 소유한다.

## Android 비공개 테스트와 프로덕션

2026-10-13 소유자 결정(2026-10-19 확인)에 따라 Android 앱을 Google Play 프로덕션에 공개한다. 개인 개발자 계정이므로 비공개 테스트를 먼저 거친다. 이 절을 쓴 2026-10-19 기준 완료 기록이 있는 것은 internal 배포(`0.1.0(2)`)뿐이다. 이 절은 절차와 경계를 정하며 Console 입력이나 신청을 수행했다는 기록이 아니다.

### 운영 방침

- 비공식(unofficial) 앱으로 공개한다. VLR.GG에 데이터 사용 허가를 요청하지 않았고 받지 않았다. 허가를 받은 것처럼 store listing, 신청서와 문서에 쓰지 않는다.
- VLR.GG 이용약관은 scraping과 재게시를 제한한다. Play [지식재산권 정책](https://support.google.com/googleplay/android-developer/answer/9888072)과 [사칭 정책](https://support.google.com/googleplay/android-developer/answer/9888374)상 "unofficial" 표기만으로 제3자 상표 사용이 허용되지 않을 수 있다.
- 검토에서 반려되거나 신고가 들어오면 앱 제목과 아이콘에서 VLR.GG 표기를 빼는 방안을 먼저 검토한다. 근거는 [외부 제약](feature/README.md#외부-제약)이 소유한다.
- Play 앱 콘텐츠의 "뉴스 앱" 선언은 해당 없음으로 한다(소유자 결정, 2026-10-19). Play는 앱이 스스로 News 앱으로 선언하거나 News and Magazine 카테고리에 있으면서 제목·아이콘·개발자명·설명에서 뉴스 앱으로 소개하는 경우를 뉴스 앱으로 본다([Play 앱 콘텐츠](https://support.google.com/googleplay/android-developer/answer/9859455)). 카테고리와 store listing 문구가 이 결정과 어긋나지 않게 한다.

### 트랙 흐름

1. internal: 현재 Actions와 Fastlane이 올리는 유일한 track이다. 설치·업데이트 증빙은 [Android completion evidence](#android-completion-evidence)를 따른다.
2. 비공개 테스트(closed testing): internal에서 검증한 build를 Play Console에서 승격한다. 승격 메뉴와 버튼 이름은 Console 화면에서 확인한다(확인 필요). 테스터 모집과 opt-in 안내는 소유자가 직접 한다. internal에 참여 중인 계정(#117 참가자 등)은 비공개 테스트 버전을 받을 수 없으므로 internal에서 먼저 opt-out한 뒤 비공개 테스트 opt-in 링크로 참여해야 한다([Play 테스트 설정](https://support.google.com/googleplay/android-developer/answer/9845334)). 14일 연속 참여는 비공개 테스트 opt-in을 기준으로 센다.
3. 프로덕션: 비공개 테스트 요건을 채우고 Console에서 프로덕션 액세스를 신청해 승인받은 뒤 출시한다. 같은 versionCode를 승격하거나 더 큰 미사용 versionCode로 새 release를 만든다. 기기는 자격이 있는 track 중 가장 높은 versionCode를 받는다([Play 테스트 설정](https://support.google.com/googleplay/android-developer/answer/9845334)).

자동 조회는 internal만 보므로([Uncertain upload and cleanup](#uncertain-upload-and-cleanup)) 비공개 테스트·프로덕션의 versionCode와 처리 상태는 Console에서 직접 확인한다.

### 비공개 테스트 요건과 프로덕션 액세스

[Play 고객센터](https://support.google.com/googleplay/android-developer/answer/14151465)는 2023-11-13 이후 만든 개인 계정에 다음을 요구한다. 2026-10-19에 확인한 내용이며, 신청 전에 다시 확인한다.

- 앱 설정을 마친 비공개 테스트를 운영한다.
- 신청 시점에 테스터 12명 이상이 opt-in 상태여야 하고, 직전 14일 동안 연속으로 유지해야 한다. 중간에 opt-out했다가 다시 참여하면 일수를 처음부터 센다.
- Console Dashboard의 "Apply for production"에서 신청한다. 질문은 세 부분이다: 비공개 테스트(테스터 모집·참여·피드백 요약), 앱(대상 사용자·가치·첫해 예상 설치 수), 프로덕션 준비 상태(테스트 후 바뀐 점과 준비됐다고 판단한 근거).
- 검토는 보통 7일 안에 끝나지만 더 걸릴 수 있다. 테스터 수나 참여가 부족하면 테스트를 계속해야 할 수 있다.

테스터 목록은 Console의 Closed testing > Testers에서 이메일 목록 또는 Google Group으로 관리하고 opt-in 링크를 공유한다. 테스터 신원과 이메일은 저장소·이슈·공개 log에 남기지 않는다. 앱이 Published 상태가 아니면 opt-in 링크가 보이지 않는다고 안내되므로 현재 앱 상태에서 링크가 나오는지 확인한다(확인 필요).

신청서 답변과 소유자의 모집·참여 기록은 이 문서가 아니라 소유자의 비공개 기록에 둔다. 신청과 Console 입력은 소유자가 직접 한다.

### Console 앱 콘텐츠 선언

Console의 앱 콘텐츠에서 다음 항목을 완료해야 한다. 실제 답변 초안은 [`play-console.md`](play-console.md)에서 관리하며 이 문서에 복사하지 않는다.

| 항목 | 이 문서의 기준 |
| --- | --- |
| 개인정보처리방침 | [`privacy-policy.md`](privacy-policy.md)의 공개 URL |
| 광고 | 광고 포함 여부 선언 |
| 앱 액세스 | 로그인·멤버십 등으로 제한된 기능 여부 |
| 콘텐츠 등급 | 설문 완료 |
| 타겟층과 콘텐츠 | 대상 연령대 선언 |
| 데이터 보안(Data safety) | 수집 데이터가 없어도 양식과 개인정보처리방침 링크가 필요하다([Play 안내](https://support.google.com/googleplay/android-developer/answer/10787469)) |
| 뉴스 앱 | 해당 없음(소유자 결정, 2026-10-19) |
| 기타 앱 유형별 선언 | 해당 여부 확인. 해당하면 `play-console.md`에 추가 |

항목 목록은 [Play 앱 콘텐츠 안내](https://support.google.com/googleplay/android-developer/answer/9859455)와 Console 화면을 기준으로 하며, 항목이 바뀌면 Console 화면이 우선한다. 선언은 store listing, About 고지와 [개인정보처리방침](privacy-policy.md)과 일치해야 한다.

### Play 배포 서비스 계정 권한

[GitHub environments](#github-environments)의 Play 배포 서비스 계정은 현재 대상 앱 조회와 testing release로 제한한다. 프로덕션 출시 권한은 부여하지 않았다. internal 업로드 workflow가 프로덕션에 release를 만들 수 없게 하려는 경계이며, 권한을 바꾸는 변경은 별도 이슈와 리뷰로 진행한다.

권한을 늘리기 전에 다음을 검토한다.

- 자동화가 프로덕션 release를 만들어야 하는지, Console 수동 출시로 충분한지
- 필요하면 `android-internal`과 분리한 environment, 필수 reviewer와 WIF `iam.workloadIdentityUser` subject 제한
- 부여할 Play Console 권한이 앱 단위로 한정되는지와 현재 권한 목록
- 실패·오배포 때 출시 중단과 복구 방법
- 공개 Actions log·summary·artifact에 노출되는 값과 서비스 계정 key를 만들지 않는 원칙의 유지

### Fastlane 범위

현재 Fastlane lane은 `android internal`뿐이며 internal track에만 AAB를 올린다. 비공개 테스트 승격과 프로덕션 신청·출시는 Console에서 한다. production lane과 store metadata·이미지 업로드는 이 범위 밖이다(현재 lane은 metadata·changelog·이미지 업로드를 건너뛴다). 필요해지면 별도 이슈에서 서비스 계정 권한 검토와 함께 결정한다.

## iOS 재개 조건

Apple Developer Program과 App Store Connect enrollment, `kr.co.cotton.vlrggmobile` 앱 소유권, distribution certificate/profile, App Store Connect API key와 internal tester가 준비된 뒤에만 iOS workflow를 켠다.

iOS lane은 temporary keychain/profile/config를 만들고 archive와 dSYM을 처리한다. 기존 profile을 덮어쓰지 않으며 marker에 기록된 이번 실행의 자원만 삭제한다. cleanup marker가 불일치하거나 credential 삭제가 실패하면 임의 정리를 하지 않고 실패로 남긴다. 기존 사용자 keychain이나 인증서 디렉터리를 통째로 삭제하지 않는다.

## Uncertain upload and cleanup

Android 자동 조회는 internal track의 versionCode, iOS는 요청한 marketing version의 최신 TestFlight build만 본다. 다른 track, 처리 중 upload와 응답 유실을 완전히 증명하지 못한다.

upload timeout, 응답 유실 또는 처리 상태 불명확 시 새 번호로 다시 올리지 않는다. Console에서 해당 version/build receipt, processing state와 source를 확인한 뒤에만 재실행한다. 조회 오류는 fail-closed하며 확인되지 않은 upload는 unknown으로 남긴다.

lane `ensure`와 workflow `always()`는 이번 실행의 temp signing 자료, config, AAB/IPA, archive와 derived data를 정리한다. marker가 없거나 ownership이 불명확하면 기존 credential을 삭제하지 않는다.
