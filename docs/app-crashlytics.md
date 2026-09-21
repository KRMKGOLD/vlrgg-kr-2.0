# 앱 Crashlytics 연결 (#121)

## 범위와 구현 순서

Android/iOS 플랫폼 SDK로 자동 치명적 충돌과 Android ANR을 수집한다. Android 구현·검증·커밋 뒤 iOS 구현·검증·커밋을 분리한다. FCM, Analytics, custom nonfatal, user ID, Kotlin/Native 예외 훅과 스토어 배포는 포함하지 않는다.

Release·내부 배포는 수집 ON, 일반 Debug는 OFF다. 명시적 검증 빌드에서만 Debug 수집을 허용한다. SDK의 이전 영속 설정이 일반 Debug 수집을 활성화하지 않도록 초기화를 제어한다.

## 설정 파일 수명

Firebase 설정 원문은 Git 소스에 두지 않는다. 빌드 wrapper가 GitHub Environment secret 또는 저장소 밖 입력 파일을 읽고, 권한 0700 임시 디렉터리/0600 파일로 전달한다. 빌드 성공·실패·처리 가능한 종료 신호에서 자식 프로세스 종료 후 주입 파일을 삭제한다. ignore는 보조 장치이며 정리의 대체 수단이 아니다.

SDK 실행에 필요한 Firebase 식별자는 최종 앱 리소스/바이너리에 포함된다. 앱의 실행용 리소스까지 삭제하면 수집이 불가능하다. 배포 작업이 끝난 CI의 앱·archive 출력은 기존 배포 정리 단계가 삭제한다. SIGKILL·전원 차단처럼 정리 코드가 실행될 수 없는 종료는 별도의 runner 정리가 필요하다.

초기 준비 때 보관한 로컬 원본은 연결 검증 뒤 삭제했다. 재검증에서는 인증된 Firebase Management API 응답을 메모리에서 wrapper의 base64 환경 변수로 전달한다. GitHub의 플랫폼 Environment secrets는 유지한다.

## 현재 검증 범위

Firebase 프로젝트·양 플랫폼 앱 등록과 설정 일치, Environment secrets 등록을 확인했다. `android-internal`·`ios-testflight`는 `main` branch만 허용하며 스토어 배포 허용 변수는 아직 켜지 않았다.

Android 일반 Debug와 실제 설정을 주입한 Debug의 assemble/lint, shared host 테스트, Release 입력 검사, 수집 설정 전환 검사, 임시 파일 정리 테스트를 통과했다. iOS 일반 Debug·실제 설정을 주입한 Debug·설정 없는 미서명 Release simulator 빌드, shared iOS simulator 테스트와 설정·심볼 업로드 분기 검사를 통과했다. iOS 주입 빌드 뒤 같은 DerivedData에서 설정 없이 다시 빌드하면 이전 설정이 제거되는 것도 확인했다.

2026-09-21에는 Orca 에뮬레이터와 Firebase 콘솔로 Android fatal·ANR, iOS fatal과 기본 스택 심볼화를 확인했다. 구체적인 실행 조건과 결과는 아래 실수신 검증 기록을 따른다. 스토어 서명·배포와 Kotlin/Native 원래 예외 스택 보완은 이번 검증에 포함하지 않는다.

## Android

Firebase BoM `34.19.0`, Crashlytics Gradle plugin `3.0.8`, Google Services plugin `4.5.0`을 사용한다. 플랫폼 Application에서만 초기화하며 공통 AppGraph에는 수집 인터페이스를 추가하지 않는다. `FirebaseInitProvider`를 제거해 일반 Debug가 이전 SDK override 때문에 초기화되는 것을 차단한다. 수집 대상 빌드는 초기화 직후 공개 API로 수집을 켜서 이전 `false` override를 덮어쓴다.

`android-internal`의 `FIREBASE_ANDROID_CONFIG_BASE64`를 배포 단계에만 전달한다. wrapper는 child process에 base64를 전달하지 않고 `FIREBASE_ANDROID_CONFIG_FILE` 임시 경로만 제공한다. Google Services task는 그 경로를 직접 읽는다. 실제 설정 빌드는 Gradle configuration/build cache를 사용하지 않는다.

로컬 검증은 저장소 밖에 내려받은 설정을 사용한다. 입력 원본의 삭제는 호출자가 소유하며 wrapper는 자신이 만든 임시 파일만 삭제한다. 다음 예시는 Debug 수집 OFF다.

```sh
FIREBASE_ANDROID_CONFIG_SOURCE=/private/path/google-services.json \
  python3 scripts/firebase/with_config.py android -- \
  ./gradlew --no-daemon --no-configuration-cache --no-build-cache :app:androidApp:assembleDebug
```

연결 검증을 명시적으로 수행할 때만 `FIREBASE_CRASHLYTICS_DEBUG_ENABLED=YES`를 추가한다. Release 또는 수집을 켠 Debug는 설정이 없으면 빌드에 실패한다. 일반 Debug와 PR CI는 설정 없이 빌드되며 SDK를 초기화하지 않는다. 테스트 충돌 트리거는 일반 사용자 경로에 추가하지 않는다.

현재 Release의 `isMinifyEnabled=false`를 유지하므로 난독화 mapping 파일은 생성되지 않는다. 원본 JVM 이름을 사용하는 이 빌드의 mapping 검증은 해당 없음이다. 이후 R8을 켜면 적용된 Crashlytics plugin의 mapping 업로드를 실제 콘솔 스택과 함께 검증해야 한다. Android ANR은 Android 11 이상에서 재현·재실행·콘솔 수신을 별도 확인한다.

검증 명령:

```sh
python3 -B scripts/firebase/test_with_config.py
sh app/androidApp/scripts/test-crashlytics-config.sh
sh app/androidApp/scripts/test-release-config.sh
./gradlew :app:androidApp:assembleDebug :app:androidApp:lintDebug :app:shared:testAndroidHostTest
```

공식 근거: [Android 설정](https://firebase.google.com/docs/crashlytics/android/get-started), [수집 설정 우선순위](https://firebase.google.com/docs/reference/android/com/google/firebase/crashlytics/FirebaseCrashlytics).

## iOS

Swift Package Manager로 Firebase Apple SDK `12.19.2`의 FirebaseCore·FirebaseCrashlytics를 고정한다. `Package.resolved`도 추적한다. 앱 시작 시 빌드된 Info.plist의 수집 플래그가 켜져 있을 때만 `FirebaseApp.configure()`와 공개 수집 API를 호출한다. 일반 Debug는 초기화하지 않는다.

`ios-testflight`의 `FIREBASE_IOS_CONFIG_BASE64`를 같은 wrapper의 `ios` 인자로 주입한다. Xcode의 prepare 단계는 `FIREBASE_IOS_CONFIG_FILE`에서 앱 번들로 필요한 설정을 복사하고, 매 빌드마다 이전 설정을 지운 뒤 수집 플래그를 다시 지정한다. 임시 입력은 wrapper 종료 시 삭제하며 앱 번들·archive는 배포 lane의 정리 대상이다.

```sh
FIREBASE_IOS_CONFIG_SOURCE=/private/path/GoogleService-Info.plist \
  python3 scripts/firebase/with_config.py ios -- \
  xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp \
    -configuration Debug -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Debug 수집은 Android와 동일한 `FIREBASE_CRASHLYTICS_DEBUG_ENABLED=YES`로 명시적으로 켠다. Release와 수집을 켠 Debug는 설정이 없으면 실패한다. 일반 PR CI의 Release 컴파일 검사만 `FIREBASE_ALLOW_UNCONFIGURED=YES`를 사용하며, 미서명 simulator 조건을 함께 충족해야 한다. 기기·archive 배포에는 적용되지 않는다.

Debug·Release 모두 dSYM을 생성한다. 마지막 빌드 단계에서 수집 대상 빌드의 dSYM을 공식 `upload-symbols`로 동기 업로드하며 실패를 빌드 실패로 전달한다. 심볼 업로드 분기·실패 전파는 가짜 업로더로 검사했고, ad-hoc 서명한 simulator 검증 빌드에서는 실제 업로드와 콘솔의 소스 파일·줄 번호를 확인했다.

`CODE_SIGNING_ALLOWED=NO`는 컴파일 검사에만 사용한다. Firebase 수집을 실제 실행하는 simulator 빌드는 `CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- DEVELOPMENT_TEAM=`를 사용한다. Xcode가 생성하는 simulator용 기본 entitlement가 없으면 Firebase Installations의 Keychain 접근이 `-34018`로 실패해 세션 지표가 전송되지 않을 수 있다. 실제 Team ID나 배포 인증서는 simulator 검증에 필요하지 않으며, 이 옵션을 기기·스토어 배포에 적용하지 않는다.

검증 명령:

```sh
sh app/iosApp/Scripts/test_firebase_crashlytics.sh
sh app/iosApp/Scripts/test_release_config.sh
./gradlew :app:shared:iosSimulatorArm64Test
```

공식 근거: [Apple SDK 설정](https://firebase.google.com/docs/crashlytics/ios/get-started), [dSYM 업로드](https://firebase.google.com/docs/crashlytics/ios/get-deobfuscated-reports).

## 실수신 검증 기록 (2026-09-21, KST)

Orca 1.4.206, Android 에뮬레이터 API 37(Android 17), iPhone 17 Pro simulator iOS 26.5, Xcode 26.6에서 실행했다. 현재 gcloud 계정과 Firebase 콘솔 로그인 계정이 같고 해당 프로젝트의 Owner 권한과 앱 대시보드 접근을 확인했다. IAM·결제 설정은 변경하지 않았다.

| 항목 | 실제 확인 결과 |
| --- | --- |
| Android fatal | 15:19:18, `1.0 (1)`, `IllegalStateException`, `CrashlyticsValidationActivity.kt:25`를 콘솔 이벤트와 스택에서 확인 |
| Android ANR | 5,003ms input dispatch timeout → Orca에서 Close app → 정상 재실행. OS 종료 사유 `ANR`, 15:21:22 콘솔 이벤트와 main thread의 `CrashlyticsValidationActivity.kt:30` 확인 |
| iOS fatal·심볼 | 15:26:20, `1.0 (1)`, `EXC_BREAKPOINT`, `closure #1 in iOSApp.init()`과 `iOSApp.swift:59`를 콘솔에서 확인. 앱과 debug dylib의 dSYM 동기 업로드 성공 |
| Debug 기본 OFF | Android는 이전 수집 ON 값이 있어도 `firebaseInitialized=false`. iOS는 기본 Debug로 교체 후 검증 충돌 인자를 주어도 SDK 초기화·충돌 없이 실행됨 |
| 명시적 Debug ON | 공개 API로 수집 OFF를 저장한 뒤 재실행하면 양 플랫폼 모두 수집 ON으로 복구됨 |
| Android Release ON | 로컬 개발 키로 서명한 Release 설치 후 저장된 OFF가 ON으로 복구됨. APK DEX에 검증 Activity가 없는 것도 확인 |
| iOS Release ON | ad-hoc 서명한 Release 설치 후 SDK의 저장된 OFF가 ON으로 복구됨. Release 바이너리에 검증 인자가 없고 충돌 인자를 주어도 정상 실행됨 |
| 지표 | 앱별 crash-free users/sessions 카드와 버전별 이벤트 조회 확인. 관측값은 Android users 0%·sessions 66.67%, iOS users 0%·sessions 75%. ANR은 fatal 지표로 합산하지 않음 |

의도적으로 발생시킨 테스트 이벤트와 소수의 에뮬레이터 세션으로 만든 수치다. 운영 안정성 수치로 해석하지 않는다. 콘솔 이벤트 처리와 crash-free 집계는 비동기이므로 같은 시점에도 반영 시차가 있을 수 있다. 원본 로그·콘솔 응답·앱 설정은 공개 저장소에 첨부하지 않는다.

초기 미서명 iOS 테스트의 필수 dSYM 누락은 해당 충돌 바이너리와 남아 있는 object 파일로 `dsymutil`을 실행해 동일 UUID의 dSYM을 복구·재업로드했다. 콘솔의 업로드·처리 완료와 필수 경고 0개를 확인했다. 일부 simulator 시스템 라이브러리는 선택적 심볼 누락으로 남지만 앱의 소스 파일·줄 번호와 이벤트 처리는 확인됐다. 업로더의 제출 성공만으로 서버의 심볼 처리를 완료로 간주하지 않는다.

## 명시적 재현 절차

테스트는 실제 Firebase 프로젝트에 이벤트를 생성한다. Android 검증 Activity는 `src/debug`에만 있고 launcher나 앱 navigation에 연결되지 않는다. 수집 ON 빌드에서만 충돌·ANR·설정 변경을 허용한다.

```sh
# FIREBASE_CRASHLYTICS_DEBUG_ENABLED=YES로 주입·빌드한 Debug APK를 설치한 뒤 실행
adb shell am start -n kr.co.cotton.vlrgg_mobile/.CrashlyticsValidationActivity --es validation_action crash
# 충돌 후 정상 재실행하여 전송
adb shell am start -n kr.co.cotton.vlrgg_mobile/.MainActivity

# ANR: 검증 화면을 두 번 탭하고 시스템 ANR 대화상자에서 Close app, 이후 정상 재실행
adb shell am start -n kr.co.cotton.vlrgg_mobile/.CrashlyticsValidationActivity --es validation_action anr

# 저장된 OFF → 수집 대상 빌드 재실행 시 ON 복구 검사
adb shell am start -n kr.co.cotton.vlrgg_mobile/.CrashlyticsValidationActivity --es validation_action disable
adb shell am force-stop kr.co.cotton.vlrgg_mobile
adb shell am start -n kr.co.cotton.vlrgg_mobile/.MainActivity
```

iOS 검증 인자는 `#if DEBUG`와 빌드된 수집 ON 플래그를 모두 만족해야 동작한다. 디버거를 연결하지 않고, ad-hoc 서명한 simulator 앱을 설치한 뒤 실행한다. `<udid>`는 `xcrun simctl list devices` 또는 `orca emulator devices --json`으로 확인한다.

```sh
xcrun simctl launch --terminate-running-process <udid> kr.co.cotton.vlrggmobile -FIRDebugEnabled --firebase-crashlytics-test-crash
# 충돌 후 인자를 빼고 재실행
xcrun simctl launch <udid> kr.co.cotton.vlrggmobile -FIRDebugEnabled
# 저장된 OFF 검사; 다음 일반 실행에서 ON으로 복구
xcrun simctl launch --terminate-running-process <udid> kr.co.cotton.vlrggmobile -FIRDebugEnabled --firebase-crashlytics-disable-collection
```

Android의 `enqueued to DataTransport`나 iOS의 `Completed report submission`만으로 콘솔 검증을 끝내지 않는다. 해당 앱·버전의 이벤트·스택을 확인하고, iOS는 실제 충돌 바이너리의 UUID와 일치하는 dSYM이 처리됐는지 확인한 뒤 검증용 파일을 정리한다. 일반 Debug 검사는 이전 ON 값을 유지한 채 기본 Debug를 덮어 설치하고, Release 검사는 공개 API로 OFF를 저장한 뒤 Release를 덮어 설치해 확인한다.

공식 재현 기준: [Android 테스트 충돌](https://firebase.google.com/docs/crashlytics/android/test-implementation), [ANR 지원 조건](https://firebase.google.com/docs/crashlytics/troubleshooting), [Apple 테스트 충돌](https://firebase.google.com/docs/crashlytics/ios/test-implementation), [crash-free 지표](https://firebase.google.com/docs/crashlytics/crash-free-metrics).
