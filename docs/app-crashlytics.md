# 앱 Crashlytics 연결 (#121)

## 범위와 구현 순서

Android/iOS 플랫폼 SDK로 자동 치명적 충돌과 Android ANR을 수집한다. Android 구현·검증·커밋 뒤 iOS 구현·검증·커밋을 분리한다. FCM, Analytics, custom nonfatal, user ID, Kotlin/Native 예외 훅과 스토어 배포는 포함하지 않는다.

Release·내부 배포는 수집 ON, 일반 Debug는 OFF다. 명시적 검증 빌드에서만 Debug 수집을 허용한다. SDK의 이전 영속 설정이 일반 Debug 수집을 활성화하지 않도록 초기화를 제어한다.

## 설정 파일 수명

Firebase 설정 원문은 Git 소스에 두지 않는다. 빌드 wrapper가 GitHub Environment secret 또는 저장소 밖 입력 파일을 읽고, 권한 0700 임시 디렉터리/0600 파일로 전달한다. 빌드 성공·실패·처리 가능한 종료 신호에서 자식 프로세스 종료 후 주입 파일을 삭제한다. ignore는 보조 장치이며 정리의 대체 수단이 아니다.

SDK 실행에 필요한 Firebase 식별자는 최종 앱 리소스/바이너리에 포함된다. 앱의 실행용 리소스까지 삭제하면 수집이 불가능하다. 배포 작업이 끝난 CI의 앱·archive 출력은 기존 배포 정리 단계가 삭제한다. SIGKILL·전원 차단처럼 정리 코드가 실행될 수 없는 종료는 별도의 runner 정리가 필요하다.

초기 준비 때 보관한 로컬 원본은 이번 연결 검증이 끝난 뒤 삭제한다. 재검증이 필요하면 Firebase에서 다시 내려받는다. GitHub의 플랫폼 Environment secrets는 유지한다.

## 현재 검증 범위

Firebase 프로젝트·양 플랫폼 앱 등록과 설정 일치, Environment secrets 등록을 확인했다. `android-internal`·`ios-testflight`는 `main` branch만 허용하며 스토어 배포 허용 변수는 아직 켜지 않았다.

Android 일반 Debug와 실제 설정을 주입한 Debug의 assemble/lint, shared host 테스트, Release 입력 검사, 수집 설정 전환 검사, 임시 파일 정리 테스트를 통과했다. iOS 구현·앱 빌드 검증은 진행 중이다. 실제 테스트 충돌/ANR 수신, crash-free 지표와 심볼화 확인은 별도 증거가 필요하다. 코드·빌드 통과를 콘솔 수신 성공으로 기록하지 않는다.

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
