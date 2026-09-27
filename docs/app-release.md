# 앱 내부 배포 계약

2026-09-27 기준 [#117](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/117)의 Android 내부 배포 검증은 완료됐다. `0.1.0(1)` 수동 설치 이후 `0.1.0(2)`의 [Actions 빌드·서명·Play internal 업로드](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/runs/35855931943), 기존 앱의 Play 업데이트, 실기기 정상 동작과 실행 직후 crash·ANR 미발생을 확인했다. 동작·Crashlytics 확인은 #117에 기록된 사용자 확인이며 전체 접근성/E2E 검증 완료를 뜻하지 않는다.

iOS signing·TestFlight는 Apple Developer Program/App Store Connect 계정 준비 전 보류하며 [#139](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/139)에서 추적한다. 아래 내용은 후속 배포에도 유지할 절차이며, 완료된 Android 작업을 미완료로 다시 분류하지 않는다.

## Common gates

두 workflow는 manual-only이며 실행을 시작한 immutable `main` commit을 배포한다. checkout HEAD, workflow SHA와 frozen source SHA가 모두 일치해야 한다.

- `APP_VERSION`: 점으로 구분한 숫자 1~3개
- `APP_BUILD_NUMBER`: `1`~`2100000000`의 미사용 정수
- `API_BASE_URL`: credential, query, fragment와 `/` 이외 path가 없는 HTTPS origin

Android는 exact SHA의 최신 main CI attempt에서 `verify` job 성공을 요구한다. iOS는 같은 SHA의 전체 CI 성공을 요구한다. 플랫폼별 workflow concurrency는 repository workflow끼리만 직렬화하며 local/Console upload를 감지하지 못한다.

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

2023-11-13 이후 개인 계정의 12명·14일 closed testing 요구는 production 접근 조건이며 internal track의 선행 조건으로 두지 않는다.

### Upload key and first AAB

앱 signing key와 upload key를 구분한다. CI가 사용하는 전용 upload keystore, alias와 password는 복구 가능하게 보관하고 GitHub environment에는 필요한 복사본만 둔다. 현재 원본 upload key와 첫 AAB는 gitignored local storage에 있으며 별도 recovery copy는 검증되지 않았다. workflow cleanup이 유일한 원본을 삭제해서는 안 된다.

Fastlane `supply`는 앱의 수동 초기 설정과 최소 한 번의 build upload를 전제로 하며 현재 lane도 upload 전에 internal track을 조회한다. 앱 record에 build가 없으면 최초 AAB를 Actions로 bootstrap하지 않는다.

최초 AAB는 다음 조건으로 수동 등록한다.

1. main CI `verify`가 성공한 exact SHA의 깨끗한 전용 checkout을 사용한다.
2. Console에서 미사용 version/versionCode를 정하고 Actions와 같은 upload keystore·alias, release API URL과 Firebase config로 `:app:androidApp:bundleRelease`를 실행한다.
3. AAB signer의 SHA-256 fingerprint가 environment signing secret 원본의 선택한 alias certificate fingerprint와 같은지 확인한다. 다르면 upload하지 않는다.
4. AAB와 임시 keystore/config/build output은 repository와 공개 artifact 밖에 두고, Console 접수·Play App Signing·internal release 처리를 확인한다.

수동 bootstrap caller는 repository 밖 0700 private directory에 0600 signing copy를 만들고, `EXIT`/`finally`에서 이번 실행의 signing copy와 Android build output만 정리해야 한다. 성공한 첫 AAB는 cleanup 전에 별도 보호 위치로 복사한다. `with_config.py`는 자신이 만든 Firebase temp만 삭제하며 direct Gradle build는 lane/workflow cleanup을 실행하지 않는다. SIGKILL·전원 차단처럼 cleanup이 실행되지 않으면 이번 실행의 temp/build 잔여물을 직접 확인한다. 원본 upload key와 보호 위치의 첫 AAB는 자동 삭제하지 않는다.

첫 upload 뒤 Google Play Developer API를 활성화하고 Play Console에서 대상 앱의 조회·testing release 권한만 Service Account에 부여한다. track query가 성공하고 더 큰 미사용 versionCode를 확인한 뒤 Actions를 실행한다. Fastlane exit 0만으로 접수·tester 설치를 대신하지 않는다.

## Android completion evidence

배포마다 Play tester가 지정 계정으로 internal testing에 참여해 물리 Android 기기에서 설치·업데이트하고, 외부망에서 뉴스·경기 목록과 연결된 상세의 HTTPS 조회·표시·기본 이동을 확인한다. SHA, CI run, deployment run, version/build, Play receipt, tester install/update, 기기/OS, query 결과와 cleanup을 기록하되 tester identity와 raw logs는 공개하지 않는다. `0.1.0(2)`의 완료 근거와 사용자 확인 범위는 #117이 소유한다.

## iOS 재개 조건

Apple Developer Program과 App Store Connect enrollment, `kr.co.cotton.vlrggmobile` 앱 소유권, distribution certificate/profile, App Store Connect API key와 internal tester가 준비된 뒤에만 iOS workflow를 켠다.

iOS lane은 temporary keychain/profile/config를 만들고 archive와 dSYM을 처리한다. 기존 profile을 덮어쓰지 않으며 marker에 기록된 이번 실행의 자원만 삭제한다. cleanup marker가 불일치하거나 credential 삭제가 실패하면 임의 정리를 하지 않고 실패로 남긴다. 기존 사용자 keychain이나 인증서 디렉터리를 통째로 삭제하지 않는다.

## Uncertain upload and cleanup

Android 자동 조회는 internal track의 versionCode, iOS는 요청한 marketing version의 최신 TestFlight build만 본다. 다른 track, 처리 중 upload와 응답 유실을 완전히 증명하지 못한다.

upload timeout, 응답 유실 또는 처리 상태 불명확 시 새 번호로 다시 올리지 않는다. Console에서 해당 version/build receipt, processing state와 source를 확인한 뒤에만 재실행한다. 조회 오류는 fail-closed하며 확인되지 않은 upload는 unknown으로 남긴다.

lane `ensure`와 workflow `always()`는 이번 실행의 temp signing 자료, config, AAB/IPA, archive와 derived data를 정리한다. marker가 없거나 ownership이 불명확하면 기존 credential을 삭제하지 않는다.
