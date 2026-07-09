# VLR.GG Mobile 2.0 Agent Guide

## Project Mission

VLR.GG Mobile 2.0 프로젝트는 발로란트 이스포츠 정보들을 공유하는 vlr.gg 사이트의 모바일 앱 포팅 버전입니다.

Compose Multiplatform 을 기반으로 작성하며, 서버 코드는 Kotlin Ktor 라이브러리와 Jsoup 스크래핑 라이브러리를 이용하여 작성됩니다.

Agent는 각 모듈 내의 별도의 AGENTS.md 를 추가로 확인하여 작업을 진행할 수 있습니다.

AI Agent 및 Codex를 이용하여 Compose Multiplatform 프로젝트, 그리고 server 모듈에는 Ktor 서버를 구축합니다.
기획 문서는 /docs 또는 ralplan 결과물에 지속적으로 업데이트됩니다.
Ktor 서버는 기획에 맞게 vlr.gg 사이트를 스크래핑하고 데이터를 가공하여 App 노출에 용이한 구조를 반환합니다.
KMP Client는 가공된 데이터를 기반으로 대회 정보 및 팀, 선수 정보들을 노출합니다.

## Source of Truth

- 제품 기획과 화면 요구사항에 대해서는 docs/ 와 Stitch를 우선적으로 확인한다.
- 아키텍처 결정은 `docs/architecture/` 또는 ralplan 결과물을 따른다.
- 실제 구현 규칙은 루트 `AGENTS.md`와 각 모듈의 `AGENTS.md`를 함께 따른다.
- 코드와 문서가 충돌하면 현재 코드 구조를 먼저 확인하고, 필요한 경우 문서를 함께 갱신한다.

## Repository Structure

| **Module**      | **Purpose**                          |
|-----------------|--------------------------------------|
| app/shared      | Compose Multiplatform 공통 앱 코드        |
| app/androidApp  | Android 앱 엔트리 포인트                    |
| app/iosApp      | iOS 앱 엔트리 포인트                        |
| server          | Ktor 기반 서버 애플리케이션                    |
| core (optional) | 앱/서버가 공유할 수 있는 순수 Kotlin 유틸리티와 공통 코드 |
| docs            | 기획, 아키텍처, ADR, 작업 계획 문서              |

## Module Boundaries

- 대부분의 앱 UI, ViewModel, 상태 관리, data/domain 로직은 `app/shared`에 둔다.
- `androidApp`과 `iosApp`에는 플랫폼 엔트리 포인트와 플랫폼 특화 코드만 둔다.
- `server`는 scraping, API route, server-side DTO, service 로직을 담당한다.
- `core`는 특정 플랫폼, Compose UI, Ktor server framework에 강하게 의존하지 않는 공통 코드만 둔다.

## Architecture Principles

- shared-first 원칙을 따른다. Android/iOS에 중복 구현하기 전에 `commonMain` 구현 가능성을 먼저 검토한다.
- 앱은 UDF 기반으로 상태를 관리한다.
- ViewModel은 UI state를 `StateFlow`로 노출한다.
- Domain layer는 필요한 경우에만 둔다. 단순 Repository 호출만 감싸는 UseCase는 만들지 않는다.
- 서버 응답 DTO, local entity, UI model, domain model의 책임을 섞지 않는다.

## App Development Rules

- Compose UI는 `app/shared/src/commonMain`을 기본 위치로 한다.
- Screen은 ViewModel을 연결하고, 실제 UI는 Content composable로 분리한다.
- Navigation은 Screen callback을 통해 처리하고, ViewModel이 back stack을 직접 제어하지 않는다.
- 화면 상태는 단일 UiState data class로 표현한다.
- UI 표시용 문자열/포맷팅 상태는 Domain Model에 넣지 않는다.

## Server Development Rules

- 서버는 Ktor 기반으로 route, service, scraper/parser, response DTO 책임을 분리한다.
- vlr.gg HTML 구조에 의존하는 parsing 코드는 한 곳에 모아 변경 영향을 줄인다.
- 앱에 그대로 노출하기 어려운 원본 scraping 결과는 server에서 app-facing response로 가공한다.
- scraping 실패, HTML 구조 변경, 네트워크 오류를 명시적인 error response 또는 fallback으로 처리한다.

## Core Module Rules

- `core`에는 앱과 서버가 공유해도 안전한 순수 Kotlin 코드를 둔다.
- Compose UI, Android Context, iOS API, Ktor Application 같은 framework-specific 의존성은 피한다.
- 공통 enum, value object, formatter, validation, lightweight utility를 우선 배치한다.
- 특정 기능에만 쓰이는 코드는 무리하게 `core`로 올리지 않는다.

## Build, Test, and Verification Commands

- Android build: `./gradlew :app:androidApp:assembleDebug`
- Server run: `./gradlew :server:run`
- Server test: `./gradlew :server:test`
- Shared Android host test: `./gradlew :app:shared:testAndroidHostTest`
- Shared iOS test: `./gradlew :app:shared:iosSimulatorArm64Test`
- 변경 후 가능한 가장 좁은 테스트를 먼저 실행하고, 모듈 경계를 건드렸다면 관련 Gradle task를 추가로 실행한다.

## Documentation Workflow

- 큰 기능을 시작하기 전 `docs/` 또는 ralplan 결과물에 목표와 범위를 남긴다.
- 아키텍처 결정이 바뀌면 관련 문서를 함께 갱신한다.
- 긴 코드 예시와 설계 배경은 AGENTS.md가 아니라 `docs/`에 둔다.
- AGENTS.md에는 반복 작업에 필요한 규칙과 기준만 유지한다.

## Agent Workflow

- 작업 전 관련 모듈의 `AGENTS.md`가 있는지 확인한다.
- 구현 전 현재 코드 구조와 기존 패턴을 먼저 읽는다.
- 불필요한 새 abstraction이나 dependency를 추가하지 않는다.
- 변경 후 테스트/빌드/정적 검사를 실행하고, 실행하지 못한 경우 이유를 명시한다.
- 사용자가 만든 변경을 되돌리지 않는다.

## Commit and PR Rules

- Commit message는 한글로 작성한다.
- 형식: `{type}: {subject}`
- type은 `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `revert` 중 하나를 사용한다.
- 한 커밋에는 가능한 한 단일 작업만 포함한다.
- 한 기능 단위나 ralplan/goal slice가 완료되면 PR을 생성한다.

## Security and Configuration

- secrets, token, `.env`, local 설정, runtime log를 커밋하지 않는다.
- 외부 사이트 scraping 로직은 과도한 요청을 만들지 않도록 주의한다.
- 서버 응답에는 앱에 필요한 데이터만 포함하고, 불필요한 원본 HTML이나 내부 오류 정보를 노출하지 않는다.
- 새 환경 변수가 필요하면 안전한 placeholder와 함께 문서화한다.