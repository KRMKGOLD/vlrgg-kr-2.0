# VLR.GG Mobile 2.0

VLR.GG의 Valorant e-sports 정보를 Android와 iOS에서 탐색하는 Compose Multiplatform 포트폴리오 프로젝트입니다. Ktor 서버가 HTML을 앱용 JSON으로 가공하고, 앱은 UI·상태·데이터 로직을 공유합니다.

[![CI](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/workflows/ci.yml/badge.svg)](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/workflows/ci.yml)

[기능 기획과 구현 범위](docs/feature/README.md) · [문서 지도](docs/README.md) · [Stitch 디자인](https://stitch.withgoogle.com/projects/8765150675340843101)

## 현재 범위

- News, Matches, Events, Search, Team·Player·Series Detail, Team·Player 즐겨찾기와 MyPage, About의 앱 화면과 필요한 서버 API가 연결되어 있습니다.
- Android/iOS 실기기·접근성 검증과 E2E 후속 작업은 [#49](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/49), [#62](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/62)에서 관리합니다. 구현 완료와 MVP 검증 완료는 구분합니다.
- 경기 알림은 [MVP 이후 Stage 2](docs/feature/README.md#mvp-이후-stage-2-경기-알림)입니다. 서버의 offline 기반 구현은 앱의 실제 푸시 연동 완료를 의미하지 않습니다.
- 조회 서버의 Cloud Run 배포와 Android 내부 배포·Play 업데이트 검증은 완료됐습니다. iOS TestFlight는 계정 준비 전 [#139](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/139)에서 보류하며, 상세 상태와 절차는 [운영 문서](docs/README.md)를 따릅니다. 공개 서비스 운영이나 스토어 정식 출시 완료를 뜻하지 않습니다.

## 구조

| 위치 | 역할 |
| --- | --- |
| `app/shared` | 공통 Compose UI, Navigation 3, Metro DI, ViewModel·Domain·Data |
| `app/androidApp`, `app/iosApp` | 플랫폼 진입점과 OS 통합 |
| `server` | Ktor/Netty API, Jsoup parser, 요청 시점 scraping |
| `core` | 앱·서버가 공유하는 순수 Kotlin 코드 |

앱은 `StateFlow<UiState>`와 callback으로 상태를 관리하며 root별 navigation stack을 복원합니다. 서버는 HTML 의존성을 parser에 격리하고 안전한 응답만 노출합니다. 상세 계약은 [앱 아키텍처](docs/app-arch/app-arch.md)와 [서버 아키텍처](docs/architecture/server-arch.md), 정확한 의존성 버전은 [version catalog](gradle/libs.versions.toml)를 따릅니다.

## 로컬 실행

JDK 21, Android Studio, Android SDK 36이 필요하며 iOS 실행에는 Xcode가 필요합니다.

```bash
git clone https://github.com/KRMKGOLD/vlrgg-kr-2.0.git
cd vlrgg-kr-2.0
./gradlew :server:run
```

서버 기본 주소는 `http://localhost:8080`입니다. Android Studio에서 `app/androidApp`을 실행하거나 다음 명령으로 빌드합니다.

```bash
./gradlew :app:androidApp:assembleDebug
```

iOS는 Xcode에서 `app/iosApp/iosApp.xcodeproj`를 엽니다. 기본 Debug API 주소는 Android Emulator에서 `http://10.0.2.2:8080`, iOS Simulator에서 `http://127.0.0.1:8080`입니다. 실기기는 접근 가능한 개발 서버 주소가 필요합니다. Release URL·서명·Firebase 설정은 [앱 배포](docs/app-release.md)와 [Crashlytics](docs/app-crashlytics.md)를 따릅니다.

## 검증과 작업 기준

변경한 모듈에 맞는 검증을 실행합니다.

```bash
./gradlew :server:test
./gradlew :app:shared:testAndroidHostTest
./gradlew :app:shared:iosSimulatorArm64Test
./gradlew :app:androidApp:assembleDebug
```

CI gate와 opt-in 로컬 benchmark는 [CI/CD](docs/ci-cd.md), 작업 규칙은 [AGENTS.md](AGENTS.md), 시각·접근성 기준은 [DESIGN.md](DESIGN.md)에서 관리합니다.

## 프로젝트 운영 범위

- Riot Games 또는 VLR.GG의 공식 앱이 아닌 개인 학습·포트폴리오 프로젝트입니다.
- 조회 서버는 개발·검증용 Cloud Run에 배포되어 공개 호출을 허용합니다. 실제 서버 주소·운영 식별자는 저장소에 공개하지 않습니다.
- 앱 배포는 내부 검증 범위이며 공개 제품·데이터 서비스를 운영할 계획은 없습니다.
- 개인 프로젝트라는 사실은 upstream 데이터 사용 허가를 의미하지 않습니다. scraping 제한과 공개 범위 변경 시 검토 사항은 [데이터 사용 경계](docs/feature/README.md#외부-제약)를 따릅니다.
- 원본 HTML, 내부 예외, selector, token·secret은 API 응답에 노출하지 않으며 secret·운영 로그는 커밋하지 않습니다.
