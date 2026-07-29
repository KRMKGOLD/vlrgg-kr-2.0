# Documentation Guide

## 목적

`docs/`는 VLR.GG Mobile 2.0의 기능 기획과 앱·서버 아키텍처를 장기적으로 관리하는 source of truth다. 임시 조사, 인터뷰, 실행 계획, 검토 산출물은 `.omx/`에서 관리하고 장기 합의만 `docs/`에 반영한다.

## 구조

```text
docs/
  README.md
  feature/
    README.md
    <feature>/
      README.md
  app-arch/
    app-arch.md
    ui-layer.md
    domain-layer.md
    data-layer.md
  architecture/
    server-arch.md
    server-fcm-stage1.md
    adr/
      0001-match-notification-stage1-storage-and-provider-boundary.md
```

| 위치 | 책임 |
| --- | --- |
| [`feature/`](feature/README.md) | 전체 MVP 지도, 기능별 사용자 흐름, 화면 상태, 노출 데이터, 수용 기준 |
| [`app-arch/`](app-arch/app-arch.md) | Compose Multiplatform 앱의 모듈·UI·Domain·Data 경계 |
| [`architecture/`](architecture/server-arch.md) | Ktor 서버, scraping, API 오류, 공통 server policy와 서버 전용 ADR |
| [`../DESIGN.md`](../DESIGN.md) | 공통 visual language, component, 접근성, interaction contract |
| `.omx/` | 임시 계획, 인터뷰, 분석, 검토와 실행 상태 |

현재 제품 기획 문서는 `feature/`만 사용한다. `plans/`와 `operations/` 문서는 만들지 않는다.

## Feature 문서 규칙

- 전체 기능 관계와 공통 navigation은 `feature/README.md`가 소유한다.
- 각 기능은 `feature/<feature>/README.md` 하나로 시작한다.
- 화면이나 계약이 커져 한 파일의 책임이 불분명할 때만 하위 문서로 분리한다.
- 아직 작업하지 않는 기능의 빈 문서나 placeholder directory를 만들지 않는다.
- feature 문서는 목적, MVP 범위, 제외 범위, navigation, 화면 상태, 노출 데이터, interaction, app/server 경계, 수용 기준을 포함한다.
- upstream URL과 parser 주의사항은 제품 동작과 구분해 같은 feature 문서의 별도 section에 둔다.
- 같은 정책을 여러 문서에 복제하지 않고 canonical 문서에 링크한다.

## 이름과 변경 규칙

- directory와 filename은 소문자 `kebab-case`를 사용한다.
- 문서 링크는 저장소 기준 상대 경로를 사용한다.
- 장기 문서에는 필요할 때 `Status`, `Last reviewed`, `Related` metadata를 둔다.
- 기능 범위가 바뀌면 해당 feature 문서와 `feature/README.md`를 함께 갱신한다.
- navigation, theme, 공통 interaction이 바뀌면 `DESIGN.md`도 함께 확인한다.
- module/dependency/layer 경계가 바뀌면 관련 architecture 문서를 함께 갱신한다.
- 코드와 문서가 충돌하면 현재 구현과 변경 의도를 확인하고 같은 작업에서 정합화한다.

## Source of Truth

- 기능 기획: [`feature/README.md`](feature/README.md)
- 디자인 시스템: [`../DESIGN.md`](../DESIGN.md)
- 앱 전체 구조: [`app-arch/app-arch.md`](app-arch/app-arch.md)
- UI 계층: [`app-arch/ui-layer.md`](app-arch/ui-layer.md)
- Domain 계층: [`app-arch/domain-layer.md`](app-arch/domain-layer.md)
- Data 계층: [`app-arch/data-layer.md`](app-arch/data-layer.md)
- 서버 구조: [`architecture/server-arch.md`](architecture/server-arch.md)
- Match 알림 Stage 1 서버 계약: [`architecture/server-fcm-stage1.md`](architecture/server-fcm-stage1.md)
- Match 알림 Stage 1 ADR: [`architecture/adr/0001-match-notification-stage1-storage-and-provider-boundary.md`](architecture/adr/0001-match-notification-stage1-storage-and-provider-boundary.md)
- 저장소 작업 규칙: [`../AGENTS.md`](../AGENTS.md)
