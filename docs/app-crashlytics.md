# 앱 Crashlytics 계약 (#121)

Android/iOS 플랫폼 SDK로 자동 fatal crash와 Android ANR을 수집한다. FCM, Analytics, custom nonfatal, user ID, Kotlin/Native exception hook과 store 배포는 이 범위에 포함하지 않는다.

Release·내부 배포는 수집 ON, 일반 Debug는 OFF다. 명시적 검증 build에서만 Debug 수집을 허용한다. SDK의 이전 persisted setting이 일반 Debug를 켜지 못하게 초기화를 통제하고, 수집 대상 build는 공개 API로 ON을 다시 적용한다.

## Privacy basis and minimization

[개인정보처리방침](privacy-policy.md)은 오류 보고와 세션 측정을 「개인정보 보호법」 제15조 제1항 제4호(계약 이행)로 처리한다. 2026-10-17 검토에서 정한 필요성과 수집 범위는 다음과 같다.

- 오류 보고: 자동 fatal crash와 Android ANR만 수집한다. stack trace와 기기·OS 정보가 없으면 실제 이용 환경의 crash를 재현하고 고칠 수 없다. custom nonfatal·key·log, user ID, Analytics는 쓰지 않는다.
- 세션 측정: Crashlytics SDK가 crash-free 사용자·세션 비율을 계산하려고 함께 보내는 Firebase Sessions 데이터다. 이 비율이 없으면 crash가 영향을 준 범위로 수정 우선순위를 정하거나 수정 효과를 확인할 수 없어 유지한다. Firebase가 보유 기간을 따로 공개하지 않는 한계와 Firebase installation ID 삭제 기준은 처리방침에 적었다.
- 더 적은 처리: Debug 전용 수집이나 수집 OFF로는 실제 사용자 crash를 볼 수 없다. 앱 안에서 수집을 끄는 설정은 아직 없다. 설정을 추가하면 처리방침의 거부 방법을 함께 갱신한다.
- 재검토 조건: custom nonfatal·key·log, Analytics, user ID나 계정 기능을 추가하면 이 근거와 처리방침을 다시 검토한다.

## Configuration lifetime

Firebase 설정 원문은 Git에 넣지 않는다. `scripts/firebase/with_config.py`가 environment secret 또는 repository 밖 source를 검증해 0700 temp directory의 0600 file로 전달한다. 자식 process가 끝난 뒤 성공·실패·처리 가능한 signal에서 주입 파일을 삭제한다. ignore는 cleanup의 대체 수단이 아니다.

SIGKILL이나 전원 차단처럼 cleanup code가 실행되지 않으면 runner temp를 별도로 정리한다. SDK 실행에 필요한 Firebase identifier가 최종 app resource/binary에 포함되는 것은 정상이며, CI가 끝난 뒤 archive/build output은 release cleanup이 삭제한다. long-lived service-account credential은 입력으로 허용하지 않는다.

## Android

Firebase version은 version catalog가 소유한다. platform `Application`에서만 초기화하며 shared graph에 Crashlytics interface를 추가하지 않는다. `FirebaseInitProvider`를 제거해 일반 Debug 자동 초기화를 막는다.

`android-internal`의 `FIREBASE_ANDROID_CONFIG_BASE64`는 배포 단계에만 전달한다. wrapper는 child에 base64를 넘기지 않고 `FIREBASE_ANDROID_CONFIG_FILE` temp path만 제공한다. 실제 설정 build는 Gradle configuration/build cache를 사용하지 않는다.

Release 또는 수집 ON Debug는 설정이 없으면 실패한다. 일반 Debug와 PR CI는 설정 없이 빌드되고 SDK를 초기화하지 않는다. validation Activity는 debug source에만 있고 launcher/navigation에 연결하지 않으며 수집 ON build와 adb shell에서만 crash·ANR·collection change를 허용한다.

현재 Release는 minify를 사용하지 않아 mapping upload가 없다. R8을 켜면 plugin mapping upload와 실제 console stack을 함께 검증한다.

```sh
python3 -B scripts/firebase/test_with_config.py
sh app/androidApp/scripts/test-crashlytics-config.sh
sh app/androidApp/scripts/test-release-config.sh
./gradlew :app:androidApp:assembleDebug :app:androidApp:lintDebug :app:shared:testAndroidHostTest
```

## iOS

Firebase Apple SDK version과 package resolution은 Xcode project와 `Package.resolved`가 소유한다. built Info.plist의 collection flag가 켜진 경우에만 `FirebaseApp.configure()`와 collection API를 호출하며 일반 Debug는 초기화하지 않는다.

`ios-testflight`의 `FIREBASE_IOS_CONFIG_BASE64`를 같은 wrapper로 주입한다. Xcode prepare 단계는 매 build마다 이전 설정을 지우고 현재 설정과 collection flag를 app bundle에 복사한다. Release와 수집 ON Debug는 설정이 없으면 실패한다. credential-free unsigned Release simulator check만 `FIREBASE_ALLOW_UNCONFIGURED=YES`를 허용한다.

Debug·Release 모두 dSYM을 만들고 수집 대상 build는 공식 `upload-symbols`를 동기 실행한다. 업로드 실패는 build 실패다. uploader exit 0만으로 server-side symbol processing을 완료로 보지 않고 crash binary UUID와 dSYM UUID, console의 source file·line을 확인한다.

```sh
sh app/iosApp/Scripts/test_firebase_crashlytics.sh
sh app/iosApp/Scripts/test_release_config.sh
./gradlew :app:shared:iosSimulatorArm64Test
```

## Live evidence and limits

2026-09-21 emulator/simulator 검증에서 다음을 Firebase console의 해당 app/version event와 stack으로 확인했다.

- Android fatal과 Android 11+ ANR 수신
- iOS fatal 수신과 app source file·line symbolication
- 양 플랫폼 일반 Debug 기본 OFF와 명시적 Debug ON
- 수집 OFF persisted setting 뒤 Release/검증 build의 ON 복구
- Android Release artifact와 iOS Release binary에 test trigger가 없음

의도적으로 만든 소수 test session의 crash-free 비율은 운영 안정성 지표가 아니다. console 처리와 aggregate metric은 지연될 수 있다. local transport success message만으로 실수신을 통과시키지 않는다. 원본 logs, console response와 app config는 공개 저장소에 첨부하지 않는다.

Store signing/deployment과 실제 사용자 release 안정성은 [앱 내부 배포](app-release.md)와 이후 운영 관측이 소유한다.
