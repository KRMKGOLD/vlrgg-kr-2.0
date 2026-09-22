# 앱 내부 배포 절차 (#112 / #117)

Google Play 개발자 계정은 등록됐지만 앱은 아직 생성하지 않았고, Apple 개발자 계정은 없는 상태에서 Android 내부 테스트와 iOS TestFlight 배포 절차를 준비했다. 계정 등록, 앱 소유권·서명 확인, GitHub environment 연결, 실제 업로드·설치·실기기 검증은 [#117](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/117)에서 진행한다. 정식 스토어 공개 출시는 포함하지 않는다.

2026-09-22부터 **Android Google Play 내부 테스트를 먼저 진행한다.** iOS는 Apple 개발자 계정이 없어 서명·TestFlight 배포를 보류한다. 기존 iOS SDK·빌드 CI는 유지하며, Apple 계정은 Android 배포의 선행 조건이 아니다.

## 준비 현황 (2026-09-22)

| 항목 | 확인된 상태 | 다음 완료 조건 |
| --- | --- | --- |
| 배포 코드·Crashlytics | #112·#119·#121 완료, 수동 Android workflow 있음 | 배포할 정확한 main SHA의 CI 성공 |
| Play Console 계정·앱 | 사용자 확인: Google Play 개발자 계정 등록 완료, 앱 미생성 | 운영 소유자·Console 잔여 검증 확인 후 앱 생성 |
| Android 서명·Play API 인증 | `android-internal`에는 Firebase 설정 secret만 있음. 저장소 공통 secret 없음 | 아래 Android signing/API secrets 연결 |
| 환경 보호·배포 허용 | main 제한 있음, required reviewer 없음, 배포 허용 variable 미설정 | 실제 운영자 기준 승인 정책 확정 후 마지막에 허용 |
| 첫 AAB·테스터 설치 | 미실행 | 최초 수동 등록 → 내부 테스트 설치 → 후속 Actions 배포 검증 |
| iOS | 사용자 확인: Apple 개발자 계정 없음 | 계정 준비 후 별도 진행, 배포 허용은 계속 OFF |

절차 준비와 실제 배포 완료를 구분한다. 계정 확인·키 생성·권한 부여·secret 등록·업로드는 각 단계의 대상과 입력이 확인된 뒤 수행한다.

## 배포 경로와 소스

두 workflow는 `workflow_dispatch`로 `main`에서만 실행한다.

| 플랫폼 | Workflow | 실행 lane | 대상 |
| --- | --- | --- | --- |
| Android | `.github/workflows/deploy-app-android.yml` | `bundle exec fastlane android internal` | Google Play `internal` |
| iOS | `.github/workflows/deploy-app-ios.yml` | `bundle exec fastlane ios internal` | TestFlight 내부 테스트 |

workflow는 시작 시 `github.sha`를 `SOURCE_SHA`로 고정하고 그 commit을 checkout한다. 같은 SHA의 성공한 `main` push `CI`가 있어야 배포 단계로 넘어간다. lane도 GitHub Actions 수동 실행 여부, `main` ref, `GITHUB_SHA`·`SOURCE_SHA`·실제 `HEAD`와 작업 디렉터리를 검사하고, staged·수정·미추적 소스가 있으면 거절한다. 로컬에서 lane만 직접 실행하는 방식은 지원하지 않는다.

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

CI는 위 도구 확인과 `app/androidApp/scripts/test-release-config.sh`, `app/iosApp/Scripts/test_release_config.sh`를 실행한다. iOS job은 공유 simulator 테스트 뒤 무서명 Release simulator 앱도 빌드해 실제 앱 링크와 처리된 Info.plist 검증 단계를 확인한다. iOS 검사는 macOS/Xcode가 필요하다.

## #117에서 준비할 environment

| Environment | 배포 허용 variable | 필요한 secrets |
| --- | --- | --- |
| `android-internal` | `ANDROID_INTERNAL_DEPLOY_ENABLED=true` | `API_BASE_URL`, `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `ANDROID_PLAY_SERVICE_ACCOUNT_JSON` |
| `ios-testflight` | `IOS_TESTFLIGHT_DEPLOY_ENABLED=true` | `API_BASE_URL`, `IOS_TEAM_ID`, `IOS_CERTIFICATE_BASE64`, `IOS_CERTIFICATE_PASSWORD`, `IOS_PROVISION_PROFILE_BASE64`, `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_KEY_CONTENT_BASE64` |

#121 준비 과정에서 두 environment를 생성하고 `main` branch 제한과 플랫폼별 Firebase 설정 secret만 등록했다. Android는 `FIREBASE_ANDROID_CONFIG_BASE64`, iOS는 `FIREBASE_IOS_CONFIG_BASE64`를 임시 파일 wrapper로 주입하고 빌드 종료 시 삭제한다. 상세 수명과 검증은 [Crashlytics 연결](app-crashlytics.md)을 따른다. 스토어 인증·서명과 위 표의 배포 secrets, 운영 인원에 맞는 승인 정책은 #117에서 확정·검증한다. 배포 허용 variable은 모든 선행 조건을 확인한 뒤 마지막에 켠다. 값이 정확히 `true`가 아니면 배포 단계가 중단된다.

## Android 우선 실행 순서

### 1. 계정 소유자·가입 상태 확인

[Play Console](https://play.google.com/console)에 실제 배포 운영 계정으로 로그인한다. 개인/조직 유형은 실제 앱 소유 주체에 맞게 정하고, 계정 소유자가 가입·약관·결제·MFA와 콘솔이 요구하는 본인·연락처·Android 기기 확인을 완료한다. 조직 계정은 조직 정보와 D-U-N-S 등 해당 유형의 요건을 확인한다. 기존 Firebase 프로젝트의 Owner 권한만으로 Play 개발자 등록이 완료된 것은 아니다. [공식 가입 절차](https://support.google.com/googleplay/android-developer/answer/6112435), [계정 유형별 준비 사항](https://support.google.com/googleplay/android-developer/answer/13628312).

2023-11-13 이후 생성된 개인 계정의 **12명·연속 14일 비공개 테스트 요건은 production 접근 조건**이다. 이번 `internal` 트랙의 선행 조건으로 잡지 않으며, 내부 테스트를 그 비공개 테스트 실적으로 대신하지 않는다. [테스트 트랙별 조건](https://support.google.com/googleplay/android-developer/answer/14151465).

완료 기준: 올바른 소유 계정으로 Console에 접근하고, 계정 검증에서 남은 항목을 확인했다. 계정 주소나 신원 서류는 공개 이슈에 남기지 않는다.

### 2. 기존 앱·패키지·업로드 이력 확인

앱이 있으면 기존 항목을 재사용한다. 없으면 앱 이름·기본 언어·앱/게임·무료/유료·연락처 등 Console의 앱 생성 항목을 소유자가 확정한다. 첫 AAB의 패키지는 `kr.co.cotton.vlrgg_mobile`이어야 하며, 다른 패키지로 임시 등록하지 않는다. 이미 업로드한 앱은 Play App Signing 및 업로드 인증서와 기존 versionCode를 먼저 확인한다. [앱 생성·설정](https://support.google.com/googleplay/android-developer/answer/9859152).

완료 기준: 대상 앱과 기존 업로드 유무를 확인했다. 이미 초기 빌드가 등록돼 있으면 4단계의 최초 등록을 반복하지 않는다.

### 3. 업로드 키 준비와 보관

기존 앱은 현재 등록된 업로드 인증서와 맞는 키를 사용한다. 새 앱은 Android Studio의 **Generate Signed Bundle / APK → Android App Bundle → Create new** 등 공식 도구로 전용 업로드 keystore를 저장소 밖에 만든다. 로컬 Debug 키는 배포 키로 사용하지 않는다.

Google이 관리하는 **앱 서명 키**와 CI가 AAB에 서명할 **업로드 키**를 구분한다. 새 앱은 Play App Signing에서 Google 생성 앱 서명 키를 사용하는 것을 기본으로 하고, 다른 스토어와 서명 공유가 필요하면 첫 등록 전에 별도로 결정한다. [서명 공식 문서](https://developer.android.com/studio/publish/app-signing).

업로드 keystore·alias·비밀번호는 복구 가능한 암호화 보관소에 보관하고 복구 담당자를 정한다. GitHub Environment에도 필요한 값을 등록하되, 빌드에 쓴 로컬 작업 복사본과 base64 임시 파일은 사용 후 삭제한다. 복구 수단이 확인되지 않은 유일한 키까지 삭제하지 않는다. 키의 바이트·비밀번호·개인 정보가 포함된 인증서 출력은 공개하지 않는다.

완료 기준: keystore를 다시 열 수 있고 alias·인증서가 대상 앱과 일치하며, 안전한 복구 수단과 `android-internal` signing secrets가 준비됐다.

### 4. 최초 AAB는 Console에 수동 등록

Fastlane `supply`는 **앱의 수동 초기 설정과 최소 한 번의 빌드 업로드**를 전제로 한다. 기존 lane도 업로드 전에 `google_play_track_version_codes`로 조회하므로, 아직 빌드가 없는 앱을 첫 Actions 실행으로 초기화하려 하지 않는다. [Fastlane 선행 조건](https://docs.fastlane.tools/actions/supply/).

최초 등록용 AAB만 신뢰하는 로컬 환경의 깨끗한 전용 checkout에서 만든다. GitHub Actions의 공개 artifact나 Release에는 올리지 않는다. 이 단계는 Gradle 빌드와 Console 수동 업로드이며, Actions 전용 Fastlane lane을 로컬에서 실행하는 예외가 아니다.

빌드 전 `SOURCE_SHA`를 성공한 main CI의 전체 SHA로 고정하고 그 checkout으로 이동한다. Console에서 미사용 `APP_VERSION`·`APP_BUILD_NUMBER`를 정한다. Java 21과 Android SDK, 해당 저장소 Actions 실행 조회 권한(`actions: read`)으로 인증한 GitHub CLI `gh`, Python 3, Ruby `3.3.7`을 준비하고 `API_BASE_URL`, `APP_VERSION`, `APP_BUILD_NUMBER`, `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `FIREBASE_ANDROID_CONFIG_BASE64`를 포함한 빌드 입력은 비공개 환경 변수로 export한다. 아래 예시는 원본 파일을 직접 사용하지 않고 메모리의 base64 입력에서 일회용 키·설정을 만든다. 보관소에서 입력을 준비할 때 다운로드한 작업 복사본이 있으면 입력 확인 후 삭제하고, 전용 비공개 셸은 작업 후 종료한다.

```bash
(
set -euo pipefail
test "$(git rev-parse HEAD)" = "$SOURCE_SHA"
test -z "$(git status --porcelain --untracked-files=all)"
gh api --method GET repos/KRMKGOLD/vlrgg-kr-2.0/actions/workflows/ci.yml/runs \
  -f branch=main -f event=push -f head_sha="$SOURCE_SHA" -f status=success \
  | ruby scripts/app-release/release_contract.rb verify-ci "$SOURCE_SHA"
test ! -e app/androidApp/build
test -z "${FIREBASE_ANDROID_CONFIG_SOURCE:-}"
umask 077
bootstrap_dir=$(mktemp -d "${TMPDIR:-/tmp}/vlrgg-android-bootstrap.XXXXXX")
bundle_ready=false
cleanup_bootstrap() {
  rm -rf app/androidApp/build "$bootstrap_dir/signing"
  if [ "$bundle_ready" != true ]; then rm -rf "$bootstrap_dir"; fi
}
trap cleanup_bootstrap EXIT
trap 'exit 130' INT
trap 'exit 143' HUP TERM
mkdir "$bootstrap_dir/signing"
export ANDROID_KEYSTORE_PATH="$bootstrap_dir/signing/upload.keystore"
ruby scripts/app-release/release_contract.rb write-base64-secret \
  ANDROID_KEYSTORE_BASE64 "$ANDROID_KEYSTORE_PATH"
python3 scripts/firebase/with_config.py android -- \
  ./gradlew --no-daemon --no-configuration-cache --no-build-cache :app:androidApp:bundleRelease
install -m 600 app/androidApp/build/outputs/bundle/release/androidApp-release.aab \
  "$bootstrap_dir/first.aab"
bundle_ready=true
printf 'Console에 등록할 비공개 AAB: %s\n' "$bootstrap_dir/first.aab"
)
```

성공하면 출력된 저장소 밖 경로의 `first.aab`만 Console 등록까지 보관한다. wrapper는 주입한 Firebase 파일을 삭제하고, EXIT trap은 성공·실패·처리 가능한 종료 신호에서 일회용 keystore와 이 실행의 Android build 출력을 정리한다. 실패 시 AAB 임시 디렉터리도 삭제한다. 기존 build 디렉터리가 있으면 자동 삭제하지 않고 중단하므로, 이 절차에는 새 전용 checkout을 사용한다. SIGKILL·전원 차단 시에는 자동 정리가 실행되지 않으므로 남은 전용 임시 디렉터리와 build 출력을 확인해 정리한다.

Console의 **Testing → Internal testing**에서 이 AAB를 업로드하고 Play App Signing 설정과 해당 트랙이 요구하는 항목을 완료한다. 앱 콘텐츠·개인정보 관련 질문에는 실제 구현과 Crashlytics 사용에 맞게 답한다. 내부 테스트에 필요하지 않은 production 공개 준비를 이 단계의 선행 조건으로 추가하지 않는다.

완료 기준: 패키지·versionCode·업로드 인증서가 일치하고 internal 릴리스가 처리됐다. Console 접수 및 테스터 설치를 확인한 뒤 출력됐던 AAB와 그 전용 임시 디렉터리도 삭제한다. 공개 기록에는 SHA·버전·성공 여부만 남긴다.

### 5. Play API와 GitHub Environment 연결

기존 GCP 프로젝트에서 Google Play Developer API를 활성화하고, CI용 서비스 계정을 Play Console의 Users and permissions에 추가한다. **이 앱에 한정된 정보 조회와 테스트 트랙 릴리스 권한**을 부여한다. production 릴리스·재무·전체 관리자 권한은 부여하지 않는다. GCP 프로젝트의 Owner/Editor를 CI 서비스 계정에 줄 필요는 없다. 테스터 명단은 운영자가 Console에서 관리하고, CI에 테스터 관리까지 필요할 때만 해당 권한을 추가한다. [API 설정](https://developers.google.com/android-publisher/getting_started), [Play 권한](https://support.google.com/googleplay/android-developer/answer/9844686).

현재 lane은 `ANDROID_PLAY_SERVICE_ACCOUNT_JSON`의 JSON 원문을 메모리로 읽는다. 이 계약에 맞춰 키를 직접 GitHub Environment secret으로 전달한다. 키 파일은 저장소 밖에서 다루며 등록 확인 후 로컬 다운로드를 삭제한다. 키 생성이 조직 정책상 불가능하면 우회하지 않고, 그때 WIF/ADC 인증으로 lane 변경을 별도 검토한다. Firebase 앱 설정과 Play API 서비스 계정 키는 서로 다른 입력이다.

`android-internal`의 위 secrets 표와 `FIREBASE_ANDROID_CONFIG_BASE64`를 모두 확인한다. `gh secret list --env android-internal`은 **이름의 존재만** 증명하며 값·서명 호환성·Play 권한을 검증하지 않는다. 필요한 경우 공개 로그가 없는 환경에서 Fastlane의 `validate_play_store_json_key`와 `google_play_track_version_codes`로 인증과 대상 internal 트랙 조회를 확인한다. 조회 실패는 고친 뒤 진행한다.

완료 기준: 입력이 모두 준비되고 앱 범위 API 조회가 성공했다. 운영자에 맞는 environment 승인 정책을 정했으며, 배포 허용 variable은 아직 OFF다.

### 6. 후속 내부 빌드를 Actions로 배포

최초 수동 빌드보다 큰 미사용 versionCode를 Console에서 확인한다. 앱이 초안 상태인 경우 현재 lane의 `release_status: completed`를 사용할 수 있도록 최초 내부 릴리스의 처리를 먼저 마친다. 준비가 끝나면 마지막으로 `ANDROID_INTERNAL_DEPLOY_ENABLED=true`를 설정하고 **Deploy Android internal**을 `main`에서 수동 실행한다. workflow가 고정한 SHA는 승인 대기 중에도 유지된다.

완료 기준: 정확한 SHA의 성공한 main CI → Actions 실행 → 새 versionCode의 Play 접수·처리가 연결된다. Fastlane exit 0만으로 테스터 설치 성공을 대신하지 않는다. 재실행 판단은 아래 실패 절차를 따른다.

### 7. Play 경유 실기기 설치와 종료 증거

운영자가 내부 테스터 명단과 참여 링크를 설정한다. 지정 테스터가 해당 Google 계정으로 참여한 뒤 **Google Play에서 물리 Android 기기에 설치**한다. 로컬 APK 설치나 emulator 실행으로 대체하지 않는다. 이후 자동 배포한 다음 버전으로 업데이트도 확인한다.

외부망에서 뉴스·경기 목록과 연결된 상세의 HTTPS 조회·표시·기본 이동을 확인한다. Crashlytics는 배포 버전 조회와 수집 정책을 확인하고, 실제 오류가 있으면 해당 버전의 스택을 확인한다. Release에는 테스트 충돌 트리거를 추가하지 않는다. #121의 명시적 Debug 충돌·ANR 실수신 증거와 이번 스토어 설치 증거는 구분한다.

완료 기준: SHA·CI·배포 실행·version/build·internal 접수·테스터 설치/업데이트·기기/OS·조회 결과·작업 파일 정리를 #117에 기록했다. 테스터 주소·계정 키·원본 로그는 공개하지 않는다.

## iOS 재개 조건

Apple Developer Program/App Store Connect 계정, 앱 소유권, 배포 인증서·프로비저닝, API key와 테스터가 준비된 뒤 기존 iOS workflow를 검증한다. 그 전에는 `IOS_TESTFLIGHT_DEPLOY_ENABLED`를 켜지 않는다. Android 내부 배포 완료와 iOS 미실행을 각각 기록하며, 이번 Android 준비 PR만으로 어느 플랫폼의 스토어 배포도 완료 처리하지 않는다.

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
