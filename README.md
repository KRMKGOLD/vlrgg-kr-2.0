<!-- markdownlint-disable MD013 -->

# VLR.GG Mobile 2.0

VLR.GG의 Valorant e-sports 정보를 Android와 iOS에서 탐색할 수 있도록 재구성한 Compose Multiplatform 포트폴리오 프로젝트입니다.

[![CI](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/workflows/ci.yml/badge.svg)](https://github.com/KRMKGOLD/vlrgg-kr-2.0/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.11.1-4285F4?logo=jetpackcompose&logoColor=white)
![Ktor](https://img.shields.io/badge/Ktor-3.5.0-087CFA?logo=ktor&logoColor=white)

[Stitch에서 디자인 보기](https://stitch.withgoogle.com/projects/8765150675340843101) · [제품 및 설계 문서](docs/README.md) · [기능 구현 현황](docs/feature/README.md)

## 프로젝트 소개

웹 화면을 그대로 옮기지 않고, VLR.GG의 뉴스·경기·이벤트·팀·선수 정보를 모바일 탐색 흐름에 맞게 다시 구성했습니다. Ktor 서버는 HTML을 앱에서 사용하기 쉬운 JSON으로 가공하고, Compose Multiplatform 앱은 UI와 상태 관리 로직을 Android와 iOS에서 공유합니다.

이 프로젝트에서 중점적으로 다룬 문제는 다음과 같습니다.

- Android와 iOS 사이에서 UI, 상태, navigation, data 코드를 얼마나 공유할 것인가
- 외부 HTML 구조의 변화를 앱 모델에 전파하지 않도록 어디에서 격리할 것인가
- 화면 수가 늘어날 때 ViewModel과 navigation lifecycle을 어떻게 일관되게 관리할 것인가
- loading, refresh, pagination, error를 하나의 예측 가능한 상태 흐름으로 어떻게 표현할 것인가

## 주요 구현

- **Compose Multiplatform shared-first 구조**: 대부분의 UI, ViewModel, Domain, Data 코드를 `commonMain`에 배치했습니다.
- **Metro 기반 compile-time DI**: 플랫폼이 `AppGraph`의 생명주기를 소유하고, ViewModel은 keyed multibinding으로 기능별 등록이 가능합니다.
- **Navigation 3 상태 관리**: 직렬화 가능한 destination, back stack 복원, entry별 saveable state와 ViewModel scope를 구현했습니다.
- **UDF 기반 News vertical slice**: 서버 scraping부터 DTO, Domain Model, Repository, `StateFlow<UiState>`, Compose 목록 UI까지 연결했습니다.
- **안전한 scraping 경계**: Jsoup selector와 DOM traversal을 서버 Parser에 가두고, 원본 HTML이나 내부 오류가 앱에 노출되지 않게 했습니다.
- **fixture 중심 회귀 테스트**: 외부 HTML 변화에 민감한 Parser와 Route 계약을 fixture와 Ktor Test Host로 검증합니다.

## 현재 구현 범위

- [x] Android/iOS 공통 runtime, Navigation 3, Metro DI
- [x] Light theme와 공통 UI component
- [x] News 목록의 Server → Data → Domain → ViewModel → Compose UI 연결
- [x] News, Matches, Events, Search, Team, Player, Series 서버 API
- [ ] News Detail 및 나머지 기능의 앱 화면 연결
- [ ] 실제 Android/iOS 기기의 시각·접근성 검증

상세 상태와 기능별 수용 기준은 [Feature Guide](docs/feature/README.md)에서 관리합니다.

## 시스템 구조

GitHub README에서 환경에 관계없이 보이도록 구조를 텍스트로 표현했습니다.

```text
┌──────────────────┐               ┌──────────────────┐
│ Android App      │               │ iOS App          │
│ Application/Host │               │ SwiftUI App/Host │
└────────┬─────────┘               └────────┬─────────┘
         └──────────────┬───────────────────┘
                        ▼
              ┌─────────────────────┐
              │ app/shared          │
              │ Compose UI          │
              │ Navigation 3        │
              │ ViewModel / Domain  │
              │ Data / Ktor Client  │
              └──────────┬──────────┘
                         │ JSON API
                         ▼
              ┌─────────────────────┐
              │ server              │
              │ Ktor 3 / Netty      │
              │ Jsoup Parser        │
              └──────────┬──────────┘
                         │ request-time fetch
                         ▼
                    VLR.GG HTML
```

### 앱 아키텍처

```text
Platform Host
  └─ AppGraph (Metro, application lifetime)
       └─ App
            ├─ VlrTheme
            └─ Navigation 3
                 └─ Screen
                      └─ ViewModel: StateFlow<UiState>
                           └─ Domain Repository: AppResult<T>
                                └─ Repository Implementation
                                     └─ RemoteDataSource
                                          └─ app-scoped Ktor Client
```

#### 플랫폼과 공통 코드의 경계

Android `Application`과 iOS SwiftUI `App`이 각각 `AppGraph`를 한 번 생성해 공통 `App(graph)`에 전달합니다. Compose 재구성이나 화면 이동은 graph와 Ktor client의 생명주기를 바꾸지 않습니다. 플랫폼 모듈에는 진입점과 설정만 남기고, 제품 로직은 `app/shared`에 모았습니다.

#### 화면 상태와 이벤트

Screen은 ViewModel을 연결하고 실제 UI는 Content composable로 분리합니다. ViewModel은 하나의 `UiState`를 `StateFlow`로 노출하며, navigation은 callback으로 요청합니다. 이 구조를 News 목록에 적용해 초기 로딩, 새로고침, 다음 페이지 요청, 오류 복구, 중복 요청과 중복 기사 삽입 방지를 검증했습니다.

#### 계층별 모델 분리

서버 응답 DTO는 Data 계층에 머물고 Mapper가 Domain Model로 변환합니다. UI는 Domain Model을 사용하되 표시 전용 값은 UI 계층에서 관리합니다. Repository는 일반 예외를 `AppResult.Failure`로 바꾸고 coroutine 취소는 그대로 전파합니다.

#### Navigation과 ViewModel scope

Navigation key에는 화면 복원에 필요한 안정적인 식별자만 저장합니다. root 화면과 그 위에 쌓이는 Search/Detail overlay를 하나의 back stack으로 관리하며, 각 entry가 saveable state와 ViewModelStore를 소유합니다. ViewModel이 back stack을 직접 변경하지 않아 화면 상태와 이동 책임이 섞이지 않습니다.

관련 문서: [App Architecture](docs/app-arch/app-arch.md), [Runtime](docs/app-arch/app-runtime.md), [UI](docs/app-arch/ui-layer.md), [Domain](docs/app-arch/domain-layer.md), [Data](docs/app-arch/data-layer.md)

### 서버 아키텍처

서버는 Ktor의 일반적인 `Application`·plugin·routing 구성을 따르고, 기능 내부를 Route, Service, Scraper, Parser, Mapper로 나눴습니다. Ktor가 특정 디렉터리 구조를 강제하지 않으므로 아래 구조는 각 기능의 HTML 의존성과 API 계약을 한곳에 모으기 위한 프로젝트 규칙입니다.

```text
server/src/main/kotlin/.../
├── Application.kt          # Netty 시작, plugin과 dependency 구성
├── plugins/                # Serialization, logging, error handling
├── routing/                # 공통 routing과 OpenAPI 설정
├── common/
│   ├── http/               # 공통 오류 응답
│   └── scraping/           # upstream transport와 URL 검증
└── feature/
    ├── news/
    ├── matches/
    ├── events/
    ├── search/
    ├── teams/
    ├── player/
    └── series/
```

```text
Route → Service → Scraper → Parser → SourceModel → Mapper → Response
```

공통 Ktor CIO client는 application lifetime 동안 재사용합니다. timeout, 응답 크기, redirect, 허용 host를 transport에서 제한하고, network failure와 parsing failure는 안전한 공통 응답으로 변환합니다. VLR.GG의 selector, 원본 HTML, upstream URL과 내부 예외는 client에 전달하지 않습니다.

관련 문서: [Server Architecture](docs/architecture/server-arch.md)

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Client | Kotlin Multiplatform, Compose Multiplatform, Material 3, Navigation 3, Metro, Coroutines/StateFlow, Ktor Client |
| Server | Kotlin/JVM, Ktor 3, Netty, Jsoup, Kotlinx Serialization |
| Test | kotlin-test, JUnit, Turbine, Mokkery, Ktor Test Host, HTML fixture |
| Design & CI | Google Stitch, `DESIGN.md`, GitHub Actions |

정확한 버전은 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)에서 관리합니다.

## 프로젝트 구조

```text
.
├── app/
│   ├── shared/       # 공통 UI, Navigation, ViewModel, Domain, Data
│   ├── androidApp/   # Android 진입점
│   └── iosApp/       # iOS 진입점
├── server/           # Ktor API와 scraping
├── core/             # 앱·서버가 공유하는 순수 Kotlin 코드
├── docs/             # 제품 요구사항, 아키텍처, ADR
└── DESIGN.md         # Stitch 기반 디자인·접근성 계약
```

## 로컬 실행

JDK 21, Android Studio, Android SDK 36이 필요하며 iOS 실행에는 Xcode가 필요합니다.

### 1. 서버 실행

```bash
git clone https://github.com/KRMKGOLD/vlrgg-kr-2.0.git
cd vlrgg-kr-2.0
./gradlew :server:run
```

서버는 로컬 개발용 `http://localhost:8080`에서 실행합니다. 공개 배포된 backend endpoint는 제공하지 않습니다.

### 2. 앱 실행

Android는 Android Studio에서 `app/androidApp`을 실행하고, iOS는 Xcode에서 `app/iosApp/iosApp.xcodeproj`를 엽니다. 각 플랫폼의 Debug 설정은 로컬 서버를 바라보도록 구성되어 있습니다.

```bash
./gradlew :app:androidApp:assembleDebug
```

## 검증

```bash
./gradlew :server:test
./gradlew :app:shared:testAndroidHostTest
./gradlew :app:shared:iosSimulatorArm64Test
./gradlew :app:androidApp:assembleDebug
```

GitHub Actions는 앱 테스트·lint, 서버 테스트·build, packaged health smoke를 PR과 `main` push에서 검증합니다.

## 디자인과 문서

| 문서 | 내용 |
| --- | --- |
| [Stitch 프로젝트](https://stitch.withgoogle.com/projects/8765150675340843101) | 모바일 화면의 시각적 기준과 handoff |
| [DESIGN.md](DESIGN.md) | 색상, typography, component, layout, 접근성 계약 |
| [Feature Guide](docs/feature/README.md) | 제품 범위와 기능별 구현 상태 |
| [Architecture Docs](docs/README.md) | 앱·서버 아키텍처와 ADR 안내 |

## 프로젝트 운영 범위

- 이 프로젝트는 Riot Games 또는 VLR.GG의 공식 앱이 아닙니다.
- 개인 학습과 포트폴리오 제출을 위해 소스 코드를 공유합니다.
- PRD에 정의된 기능을 구현한 뒤에도 공개 서비스로 운영할 계획이 없으며, 실배포 서버 주소도 제공하지 않습니다.
- VLR.GG의 이용약관은 자동 scraping과 체계적인 데이터 추출을 제한합니다. 본 저장소는 공개 데이터 서비스를 제공하지 않으며, 로컬 개발과 구조 검증 범위에서만 upstream 데이터를 다룹니다.
- 원본 HTML, 내부 예외, selector, token이나 secret은 API 응답과 저장소에 포함하지 않습니다.
