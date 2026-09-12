# 앱 내부 배포 절차 (#112)

개발자 계정이 없는 상태에서 Android 내부 테스트와 iOS TestFlight 배포 절차를 준비했다. 계정 등록, 앱 소유권·서명 확인, GitHub environment 연결, 실제 업로드·설치·실기기 검증은 [#117](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/117)에서 진행한다. 정식 스토어 공개 출시는 포함하지 않는다.

## 배포 경로와 소스

두 workflow는 `workflow_dispatch`로 `main`에서만 실행한다.

| 플랫폼 | Workflow | 실행 lane | 대상 |
| --- | --- | --- | --- |
| Android | `.github/workflows/deploy-app-android.yml` | `bundle exec fastlane android internal` | Google Play `internal` |
| iOS | `.github/workflows/deploy-app-ios.yml` | `bundle exec fastlane ios internal` | TestFlight 내부 테스트 |

workflow는 시작 시 `github.sha`를 `SOURCE_SHA`로 고정하고 그 commit을 checkout한다. 같은 SHA의 성공한 `main` push `CI`가 있어야 배포 단계로 넘어간다. lane도 GitHub Actions 수동 실행 여부, `main` ref, `GITHUB_SHA`·`SOURCE_SHA`·실제 `HEAD`와 작업 디렉터리를 검사한다. 로컬에서 lane만 직접 실행하는 방식은 지원하지 않는다.

플랫폼별 concurrency group으로 같은 배포 workflow의 동시 실행을 막는다. 진행 중 실행은 자동 취소하지 않으며, Console이나 다른 도구의 업로드까지 잠그지는 않는다. token 권한은 `actions: read`, `contents: read`이고 checkout 인증정보는 보존하지 않는다. 배포 인증정보는 플랫폼별 environment에서만 읽는다.

## 입력과 도구

운영 `API_BASE_URL`은 따옴표를 미리 붙이지 않은 HTTPS origin이다. Android는 Java 문자열 리터럴로 변환해 `BuildConfig`에 넣고, iOS는 임시 xcconfig를 거쳐 처리된 `Info.plist`까지 확인한다. 두 플랫폼 모두 빈 값·비 HTTPS·사용자 정보·query·fragment·`/` 이외 경로·잘못된 port를 거절하며, iOS는 xcconfig 확장 문자도 거절한다. Debug 로컬 HTTP 기본값은 유지한다.

| 입력 | 계약 |
| --- | --- |
| `APP_VERSION` | 수동 실행 입력. 점으로 구분한 숫자 1~3개, 예: `1.2.3` |
| `APP_BUILD_NUMBER` | 수동 실행 입력. `1`~`2100000000` 범위 정수. 기존 스토어 접수 이력을 먼저 확인한다. |
| `API_BASE_URL` | 각 environment secret. 문서·소스·공개 로그에는 실제 값을 기록하지 않는다. |
| `SOURCE_SHA` | workflow가 정하는 전체 commit SHA. 사람이 입력하지 않는다. |

Ruby `3.3.7`, Bundler `2.4.22`, Fastlane `2.239.0`을 `.ruby-version`과 `Gemfile.lock`으로 고정한다. Java는 21을 사용하고, iOS는 `macos-26` runner의 Xcode `26.6`/build `17F113`을 선택·검사한다. 해당 hosted image에서 이 Xcode가 제거되면 검사에 실패하므로, 지원 버전을 확인하고 CI와 배포 workflow를 함께 갱신해야 한다.

Ruby `3.3.7`이 준비된 환경에서 인증정보 없이 확인하는 명령은 다음과 같다. 배포 lane을 호출하지 않는다.

```bash
gem install bundler -v 2.4.22 --no-document
bundle _2.4.22_ install
bundle _2.4.22_ exec fastlane lanes
ruby scripts/app-release/release_contract_test.rb
```

CI는 위 도구 확인과 `app/androidApp/scripts/test-release-config.sh`, `app/iosApp/Scripts/test_release_config.sh`를 실행한다. iOS 검사는 macOS/Xcode가 필요하다.

## #117에서 준비할 environment

| Environment | 배포 허용 variable | 필요한 secrets |
| --- | --- | --- |
| `android-internal` | `ANDROID_INTERNAL_DEPLOY_ENABLED=true` | `API_BASE_URL`, `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `ANDROID_PLAY_SERVICE_ACCOUNT_JSON` |
| `ios-testflight` | `IOS_TESTFLIGHT_DEPLOY_ENABLED=true` | `API_BASE_URL`, `IOS_TEAM_ID`, `IOS_CERTIFICATE_BASE64`, `IOS_CERTIFICATE_PASSWORD`, `IOS_PROVISION_PROFILE_BASE64`, `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_KEY_CONTENT_BASE64` |

두 environment는 아직 연결하지 않았다. 먼저 배포 가능 branch를 `main`으로 제한하고 실제 운영 인원에 맞는 승인 규칙을 정한다. 1인 운영에서 본인 승인 금지를 켜면 별도 승인자가 필요하다. 본인 승인을 허용하는 승인자 규칙 또는 별도 승인자 없는 운영 중 어떤 정책을 사용할지는 #117에서 확정·검증한다. 배포 허용 variable은 모든 선행 조건을 확인한 뒤 마지막에 켠다. 값이 정확히 `true`가 아니면 배포 단계가 중단된다.

## 첫 배포 순서

1. Google Play·Apple 개발자 계정을 등록하고 약관·결제·MFA를 완료한다. 기존 Android `kr.co.cotton.vlrgg_mobile`과 iOS `kr.co.cotton.vlrggmobile`의 소유권 및 앱 등록을 확인한다. Play Console의 초기 앱·패키지 등록 등 API 사용 전 절차도 확인한다. 식별자를 임의로 통합하거나 교체하지 않는다.
2. Android 업로드 키·Play App Signing, iOS 배포 인증서·프로비저닝·team·API key가 해당 앱과 호환되는지 확인한다. 위 environment에 인증정보를 연결하고 최소 권한, `main` 제한, 승인 및 내부 테스터·그룹을 점검한다.
3. Play Console과 App Store Connect에서 다음 사용 가능한 version/build를 정한다. 최종 PR 리뷰·CI·일반 병합과 병합된 정확한 `main` SHA의 CI 성공을 확인한다.
4. Actions에서 해당 workflow의 `main`과 확인한 `APP_VERSION`·`APP_BUILD_NUMBER`을 선택해 수동 실행한다. workflow가 고정한 SHA는 승인 대기 중에도 그대로 사용한다.
5. 각 스토어에서 접수·처리 상태와 정확한 version/build를 확인한다. Fastlane exit 0만으로 테스터 설치 가능까지 확인한 것으로 보지 않는다.
6. 지정 테스터가 내부 채널에서 설치한 Android 물리 기기와 iPhone으로 기존 대표 조회 화면의 HTTPS 응답·표시·기본 이동 및 기존 오류·재시도 경로를 검증한다. 결과와 SHA·CI·배포 실행·version/build 관계를 #117에 남긴다.

## 실패·재실행·정리

Android 자동 조회는 현재 `internal` track의 versionCode에서 같은 번호만 거절한다. iOS는 요청한 마케팅 버전의 최신 TestFlight build 이상 여부를 검사한다. 두 조회 모두 다른 track이나 모든 처리 중 접수를 증명하지 못한다. 조회 오류는 배포 실패이며 자동 재업로드하지 않는다.

응답 유실·timeout·처리 중단이 발생하면 새 실행 전에 Console에서 해당 version/build의 접수·처리 상태와 원래 소스를 확인한다. 자동 조회에 없다는 이유로 미접수라고 단정하거나 번호만 바꿔 중복 업로드하지 않는다. 확인되지 않은 접수는 미확인 상태로 남긴다.

Android 키는 새로 만든 전용 임시 디렉터리에 `0600`으로 생성한다. iOS는 기존 프로비저닝 파일을 덮어쓰지 않고 입력 파일에서 UUID를 구해 archive/export에 사용한다. 이 실행의 임시 keychain·profile만 추적해 정리하며 기존 사용자 인증키 디렉터리는 삭제하지 않는다. App Store Connect 키의 임시 저장은 고정 Fastlane의 정리에 맡긴다.

lane의 `ensure`와 workflow의 `always()` 단계에서 임시 서명자료와 AAB/IPA·archive·관련 빌드 출력을 정리한다. keychain 삭제 실패나 profile 변경을 발견하면 실패를 보고하고 복구용 marker를 보존한다. marker를 근거로 이 실행이 만든 자원인지 확인한 뒤 정리하며, 사용자의 keychain·인증서를 통째로 삭제하지 않는다.

공개 저장소의 로그·summary·artifact는 비공개 저장소가 아니다. 실제 URL·인증정보·서명자료·원본 빌드 로그·AAB/IPA를 공개 artifact나 GitHub Release에 올리지 않는다. API 주소는 앱 binary에서 추출할 수 있으므로 앱 인증 수단으로 사용하지 않는다.

## 확인한 범위

Android Release 입력 검사, 공유 host 테스트, Debug 빌드·lint와 iOS 입력 검사, 공유 simulator 테스트·컴파일, 무서명 Release simulator 빌드는 통과했다. Ruby 계약 검사와 Ruby `3.3.7`·Bundler `2.4.22`·Fastlane `2.239.0`의 lane 로딩도 통과했으며 CI에서 다시 실행한다.

이 결과는 실제 서명 호환성, 기기용 archive/export, 스토어 인증·접수·처리, 테스터 접근 또는 실기기 설치를 증명하지 않는다. 해당 검증과 개발자 계정 추가는 #117의 미실행 작업이다.

## 공식 참고

- [GitHub 수동 실행](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow), [environment 보호](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments)
- [Fastlane 설치](https://docs.fastlane.tools/getting-started/ios/setup/), [Play 업로드](https://docs.fastlane.tools/actions/upload_to_play_store/), [TestFlight 업로드](https://docs.fastlane.tools/actions/upload_to_testflight/)
- [Play 내부 테스트](https://support.google.com/googleplay/android-developer/answer/9845334), [Android 서명](https://developer.android.com/studio/publish/app-signing), [Apple 빌드 업로드](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds), [TestFlight 내부 테스터](https://developer.apple.com/help/app-store-connect/test-a-beta-version/add-internal-testers)
