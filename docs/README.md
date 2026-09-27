# 문서 지도

`docs/`는 제품 기획과 아키텍처·운영 계약을 관리합니다. 임시 조사·계획·검토·실행 증거는 `.omx/`에 두고 장기 결정만 이곳에 반영합니다.

| 문서 | 소유하는 내용 |
| --- | --- |
| [Feature Guide](feature/README.md) | MVP와 Stage 2 범위, 공통 흐름, 기능별 요구사항·수용 기준 |
| [DESIGN.md](../DESIGN.md) | 시각·컴포넌트·접근성·interaction 계약 |
| [앱 구조](app-arch/app-arch.md) | 모듈과 레이어 경계 |
| [앱 runtime](app-arch/app-runtime.md) | 플랫폼 소유 graph, ViewModel scope, navigation 복원 |
| [UI](app-arch/ui-layer.md) · [Domain](app-arch/domain-layer.md) · [Data](app-arch/data-layer.md) | 각 레이어의 구현 규칙 |
| [서버 구조](architecture/server-arch.md) | scraping·API·오류 경계 |
| [서버 알림 기반](architecture/server-fcm-stage1.md) | Stage 1.1 offline 계약과 Stage 2 미구현 경계 |
| [공개 조회 보호](architecture/server-public-api-protection.md) | 요청 제한과 앱 Busy 처리 |
| [서버 배포·복구](architecture/server-container-deployment.md) | Cloud Run 배포, rollback, 비용 중단, 운영 알림 검증·복구 |
| [CI/CD](ci-cd.md) | 검증 gate와 로컬 benchmark |
| [앱 배포](app-release.md) | Android internal·iOS TestFlight 절차와 계정·서명 gate |
| [Crashlytics](app-crashlytics.md) | Firebase 설정, 수집 정책, crash 검증 |
| [AGENTS.md](../AGENTS.md) | 저장소 작업·commit·PR 규칙 |

각 아키텍처 문서는 관련 ADR을 연결합니다. 현재 동작은 본문에서, 결정 이유와 재검토 조건은 ADR에서 확인합니다.

## 갱신 규칙

- 기능은 `feature/<feature>/README.md` 하나로 시작합니다. 목적·포함/제외 범위·이동·상태·데이터·interaction·앱/서버 경계·수용 기준을 담고, 책임이 커질 때만 나눕니다. parser 메모는 제품 동작과 구분합니다.
- 공통 기능 관계와 navigation은 `feature/README.md`가 소유합니다. 같은 정책을 복사하지 않고 해당 문서로 연결합니다.
- 시작하지 않은 기능의 빈 문서나 `plans/`, `operations/` 같은 별도 문서 체계를 미리 만들지 않습니다.
- 파일·디렉터리는 소문자 `kebab-case`, 링크는 상대 경로를 사용합니다. 상태나 검증 시점이 중요한 문장에는 기준 시점을 남깁니다.
- 범위 변경 시 기능 문서와 Feature Guide를, navigation·theme·공통 interaction 변경 시 `DESIGN.md`를, 모듈·의존성·레이어 변경 시 아키텍처 문서를 함께 확인합니다.
- 코드와 문서가 다르면 현재 구현과 변경 의도를 확인합니다. 미구현 요구사항을 구현 상태에 맞춰 없애거나, 검증하지 않은 동작을 완료로 표시하지 않습니다.
