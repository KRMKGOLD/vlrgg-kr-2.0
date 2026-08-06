---
name: coordinate-kmp-issue
description: 이 저장소의 OMX RALPLAN과 GitHub KMP App 이슈를 기반으로 개발자 주도 구현을 코디네이션한다. 사용자가 `$coordinate-kmp-issue`를 호출하거나, KMP App 이슈 #33~#49의 시작·재개·구현 중 상담·완료 리뷰·종료 준비를 요청하거나, 다음으로 진행 가능한 이슈와 개발 작업 패킷을 요청할 때 사용한다. 기본적으로 개발자는 코드를 구현하고 Codex는 범위 정리·설계 조언·리뷰를 수행한다. 사용자가 Codex 구현이나 GitHub·Git 변경을 명시적으로 요청하면 해당 범위에 한해 실행 모드로 전환한다.
---

# KMP 이슈 개발 코디네이터

`KMP App MVP` 마일스톤의 개발자 주도 구현을 지원한다.

## 역할 계약

- 사용자가 별도로 요청하지 않으면 사용자를 구현 담당자로 취급한다.
- 기본 가이드 모드에서 Codex는 저장소와 GitHub를 읽기 전용으로 조사한다.
- 기본 가이드 모드에서는 제품 코드, 테스트, 문서, Gradle 파일, RALPLAN을 직접 수정하지 않는다.
- 기본 가이드 모드에서는 브랜치 생성·전환, 커밋, 푸시, PR 생성·병합, 이슈 종료·수정 작업을 수행하지 않는다.
- 사용자가 운영 방식을 명시적으로 변경하지 않는 한 `$ultragoal`, `$team`, `$ralph`, `$autopilot`, 서브에이전트를 실행하지 않는다.
- 기존 작업 트리 변경은 보존하고, 근거 없이 현재 이슈 변경으로 취급하지 않는다.
- 실행 모드에 들어가기 직전 `mktemp -d`로 저장소 밖의 임시 디렉터리를 만들고 `git status --short --branch`, `git diff`, `git diff --cached`, `git ls-files --others --exclude-standard` 결과를 각각 기준선으로 저장한다. 임시 디렉터리 경로를 기록하고 현재 요청이 끝날 때까지 유지한다. 기준선 파일을 저장소 안에 만들거나 stage하지 않는다.
- `.omx`는 계획 근거로만 읽는다. host receipt, tracker, workflow state를 만들거나 추정하거나 수정하지 않으며, `.omx` 또는 `.gitignore`를 생성·복사·동기화·수정하지 않는다.
- 개발자에게 읽기 전용 작업 패킷을 제공하는 행위는 AI 실행 handoff가 아니므로 RALPLAN host receipt gate로 차단하지 않는다.

사용자가 Codex의 직접 구현, 수정, 테스트, 문서 갱신, Git 또는 GitHub 변경을 명시적으로 요청하면 별도의 재확인을 요구하지 않고 해당 범위에 한해 실행 모드로 전환한다. 이 명시적 요청은 자동 agentic handoff와 구분하며, host receipt 상태를 조작하지 않는다. 요청하지 않은 후속 외부 변경이나 범위 확장은 수행하지 않는다.

## canonical local RALPLAN resolution

연결된 Git/Orca worktree에서도 다음 순서로 canonical RALPLAN 경로를 해석한다.

1. `git rev-parse --show-toplevel` 결과를 `<root>`로 정하고 `<root>/.omx/plans/kmp-app-development-direction-ralplan.md`를 current 후보로 만든다.
2. `git rev-parse --path-format=absolute --git-common-dir` 결과를 절대 정규화한 `<common-dir>`로 정한다. `<common-dir>`의 이름이 `.git`이고 디렉터리인지 확인한 뒤, 그 부모 `<primary-root>`를 primary 후보로만 도출한다. 다음을 모두 확인해 `<primary-root>`가 같은 common dir을 쓰는 non-bare worktree인지 검증한다: `git -C <primary-root> rev-parse --is-bare-repository`가 `false`인지, `git -C <primary-root> rev-parse --path-format=absolute --git-common-dir`가 `<common-dir>`와 같은지, `git -C <primary-root> rev-parse --show-toplevel` 결과가 `<primary-root>`와 정확히 같은지. 하나라도 실패하면 unsafe derivation으로 `BLOCKED`를 반환한다.
3. current와 안전하게 도출한 primary 후보만 검사하고 arbitrary scan을 하지 않는다. 후보 경로를 절대 정규화해 비교하며 같은 경로는 하나의 후보로 취급한다. 한 후보만 존재하면 그 경로를 사용하고, 두 후보가 서로 다른 경로로 모두 존재하면 `cmp -s` 같은 byte-for-byte 비교 또는 SHA-256 같은 cryptographic digest로 비교한다. 불일치하면 두 위치와 이유를 포함해 `BLOCKED`를 반환하고, 일치하면 current 경로를 사용한다. 두 후보가 모두 없거나 derivation이 unsafe하면 후보 위치와 이유를 포함해 `BLOCKED`를 반환한다.
4. 이후 이슈 매핑과 작업 근거 수집에는 반드시 resolved RALPLAN의 절대 경로를 사용하고 cwd-relative `.omx/...` 경로를 가정하지 않는다. resolved RALPLAN과 `.omx`는 읽기 전용 evidence로만 취급하며, 파일이나 `.gitignore`를 생성·복사·동기화·수정하지 않는다.

## 대상 이슈 결정

대상 저장소는 정확히 `KRMKGOLD/vlrgg-kr-2.0`으로 제한한다.

1. 요청에 이슈 번호가 있으면 대상 저장소의 해당 이슈로 해석한다. URL이 있으면 URL에서 owner, repository, issue 번호를 추출하고 대상 저장소와 일치하는지 확인한다.
2. 이슈가 지정되지 않으면 대상 저장소에서 후보를 조회한다.
3. 명시적으로 지정한 이슈와 자동 조회한 모든 후보에 동일한 검증을 적용한다. 이슈 번호가 `#33`~`#49`이고, state가 `open`이며, milestone title이 정확히 `KMP App MVP`이고, `kmp-app` 라벨이 있는지 GitHub 응답으로 확인한다.
4. 하나라도 검증에 실패하면 작업 패킷을 만들거나 실행하지 말고 `BLOCKED`를 반환한다.
5. resolved RALPLAN 파일이 존재하고 `GitHub Issue Packaging` heading과 그 직속 표가 각각 정확히 하나인지 확인한다. 표에서 `https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/{번호}` 전체 URL을 고유 anchor로 사용해 정확히 한 행을 찾는다. 그 행의 `Included packets` 셀을 쉼표로 나누고 공백을 제거한 각 packet ID가 문서의 `Packets` 표들에서 정확히 한 행으로 해석되는지 확인한다. 파일·heading·표가 없거나 둘 이상이거나, 이슈 행 또는 packet ID 매핑이 0개 또는 여러 개면 `BLOCKED`를 반환한다.
6. 검증한 동일 저장소 경로 `repos/KRMKGOLD/vlrgg-kr-2.0/issues/{번호}/dependencies/blocked_by`로 실제 선행 관계를 확인한다. URL 입력에서 얻은 다른 저장소나 고정되지 않은 현재 저장소를 사용하지 않는다.
7. 모든 선행 이슈가 종료된 항목 중 `GitHub Issue Packaging` 표의 행 순서가 가장 빠른 이슈를 선택한다.
8. 선행 이슈가 열린 작업은 시작하지 않는다.

native GitHub 선행 관계가 없으면 기본 가이드 모드에서 개발 작업 패킷을 작성한다. RALPLAN receipt gate가 `complete: false`여도 작업 패킷 제공 자체를 막지 않는다.

GitHub 접근에 실패하면 로컬 매핑은 방향 확인에만 사용한다. 상태나 선행 관계를 추측하거나 작업 패킷을 만들거나 실행하지 말고 `BLOCKED`를 반환한다.

## 작업 근거 수집

선택한 이슈에 필요한 범위만 읽는다.

1. 루트 `AGENTS.md`와 관련 모듈의 `AGENTS.md`.
2. GitHub 이슈 본문, 라벨, 마일스톤, 담당자, 선행 관계.
3. resolved RALPLAN의 `GitHub Issue Packaging`에서 고유하게 매핑된 packet, 결정 gate, 완료 조건, 검증 항목, 중단 조건.
4. 관련 `docs/app-arch/`, `docs/feature/`, `DESIGN.md` 구간.
5. 예상 변경 지점의 현재 구현과 테스트.
6. `git status --short --branch`로 확인한 기존 사용자 변경.

GitHub 이슈는 개발 실행 묶음으로, RALPLAN packet은 세부 범위와 승인 기준으로 사용한다. 문서와 코드가 다르면 현재 코드를 먼저 확인하고 차이를 보고한다.

## 요청 상태 판별

사용자 요청과 제공된 증거를 보고 다음 상태 중 하나를 선택한다.

### 이슈 시작 또는 다음 이슈

다음 형식의 개발 작업 패킷을 작성한다.

1. 이슈 목표.
2. 선행 관계 충족 여부.
3. 구현 전에 확정해야 할 결정.
4. 예상 변경 파일과 각 파일의 책임.
5. 권장 구현 순서.
6. 단계별 완료 조건.
7. 작성해야 할 테스트.
8. 정확한 검증 명령.
9. 명시적인 제외 범위.
10. 작업을 멈추고 다시 계획해야 하는 조건.

확인된 사실과 권장안을 구분한다. 코드를 작성하거나 구현을 시작하지 않는다. 개발자가 작업할 수 있도록 패킷을 전달한 뒤 멈춘다.

사용자가 같은 요청에서 `구현해`, `수정해`, `테스트까지 진행해`처럼 Codex 실행을 명시하면 아래 `명시적 실행 요청` 절차로 전환한다.

### 구현 중 상담

개발자가 설계 질문, 코드 조각, 오류, 일부 diff를 제공하면 다음을 수행한다.

- 이슈와 RALPLAN 계약을 다시 확인한다.
- 가능한 선택지와 실제 trade-off를 설명한다.
- 저장소 구조에 맞는 권장안 하나를 제시한다.
- 범위, 테스트, lifecycle, Android/iOS 영향을 설명한다.
- 현재 이슈에 포함할지 후속 이슈로 분리할지 판정한다.
- 파일을 직접 수정하지 않는다.

### 구현 완료 리뷰

개발자가 구현 완료를 알리면 다음 순서로 검토한다.

1. PR이 있으면 GitHub에서 실제 base ref를 확인한다. PR이 없으면 현재 브랜치의 upstream 대상 또는 저장소의 remote default branch를 확인하고, 둘 중 하나를 비교 base로 고유하게 확정할 수 없으면 `BLOCKED`를 반환한다. 확정한 base와 `HEAD`의 merge-base를 계산한다.
2. merge-base부터 `HEAD`까지의 diff, staged diff, unstaged diff를 확인한다. 실행 기준선이 있으면 기준선 이후 새로 생긴 미추적 파일을 확인하고, 기준선이 없으면 현재 미추적 파일 전부를 검토 대상으로 포함해 기존 변경임을 입증하지 못한 항목을 제외하지 않는다.
3. 위 변경을 합쳐 이슈와 packet의 모든 완료 조건에 대조한다. 어느 변경 출처도 검토에서 제외하지 않는다.
4. 최신 테스트·빌드 출력을 확인하고, 필요한 읽기 전용 검증 명령을 안전한 범위에서 실행한다.
5. 범위, 아키텍처, lifecycle, cancellation, navigation restoration, 플랫폼 일관성을 해당 이슈 범위에 맞게 확인한다.
6. 문제를 심각도순으로 실제 파일 근거와 함께 보고한다.
7. 다음 중 하나만 최종 판정으로 반환한다.
   - `ACCEPTED`: 모든 완료 조건에 충분한 최신 증거가 있다.
   - `ITERATE`: 수정할 결함이나 빠진 검증이 있다.
   - `BLOCKED`: 외부 계약, 선행 작업, 환경, 사용자 결정이 필요하다.

모든 변경 출처를 검토하기 전에는 `ACCEPTED`를 반환하지 않는다. 테스트, 빌드, simulator, 실기기, 접근성 증거가 요구되는 이슈를 코드 확인만으로 `ACCEPTED` 처리하지 않는다.
필수 검증이 아직 실행되지 않았지만 현재 환경에서 실행 가능하면 `ITERATE`를 반환한다. 외부 환경·자격 증명·사용자 결정 없이는 실행할 수 없으면 `BLOCKED`를 반환한다.

### 명시적 실행 요청

사용자가 Codex에게 구현이나 상태 변경을 직접 요청한 경우에만 다음을 수행한다.

1. 대상 이슈와 native 선행 관계를 확인한다.
2. 요청한 구현·수정·테스트·문서·Git·GitHub 작업만 실행 범위로 고정한다.
3. 실행 모드 진입 직전 저장한 기준선과 계속 비교해 기존 사용자 변경을 보존하며 현재 이슈의 범위와 제외 조건을 따른다.
4. 커밋을 요청받았는데 기준선에 staged 변경이 있으면 index를 unstage하거나 다시 구성하지 말고 `BLOCKED`를 반환하며 commit, push, PR 생성을 중단한다. 기준선의 staged diff가 비어 있을 때만 현재 이슈 hunk를 stage한다. 기준선의 unstaged diff에 있던 경로는 항상 `git add -p`나 동등한 hunk 선택을 사용하고, 그 밖의 이슈 전용 경로만 명시적 path staging을 허용한다. 기준선의 미추적 경로는 기존 사용자 변경으로 취급해 수정하거나 stage하지 않으며, 현재 이슈가 같은 경로를 요구하면 `BLOCKED`를 반환한다. `git add .`처럼 범위가 넓은 staging은 사용하지 않는다.
5. `git diff --cached`를 기준선과 비교해 현재 이슈 hunk만 포함됐는지 확인한다. 기존 변경과 현재 이슈 변경이 같은 파일의 같은 hunk에서 겹치면 덮어쓰거나 임의로 분리하지 않고 `BLOCKED`를 반환한다. 두 경우 모두 commit, push, PR 생성은 중단한다.
6. 가장 좁은 테스트부터 실행하고 필요한 플랫폼 검증까지 확장한다.
7. 사용자가 요청하지 않은 `$ultragoal`, `$team`, `$ralph`, `$autopilot`을 자동 실행하지 않는다.
8. 결과를 변경 파일, 검증 증거, 남은 위험으로 정리한다.

`구현 방향 알려줘`, `작업 패킷 작성해`, `리뷰해`는 실행 요청이 아니다. `구현해`, `고쳐줘`, `커밋해`, `PR 만들어`, `이슈 닫아`처럼 실제 변경을 요구하는 표현만 해당 작업의 실행 권한으로 취급한다.

### 종료 준비

리뷰가 `ACCEPTED`이면 다음을 정리한다.

- 이슈 체크리스트와 packet gate의 최종 충족 여부.
- PR 변경 요약과 검증 섹션.
- 구현에 따라 갱신해야 할 문서.
- 이번 완료로 선행 차단이 해제되는 다음 이슈.
- 다음 이슈를 시작할 스킬 호출문.

사용자가 명시적으로 요청하기 전에는 이슈를 종료하거나 GitHub 상태를 변경하지 않는다. 명시적으로 요청하면 해당 상태 변경만 수행하고 결과를 검증한다.

## 중단 조건

다음 상황에서는 진행을 멈추고 정확한 차단 사유를 보고한다.

- 실제 선행 이슈가 아직 열려 있다.
- 필요한 서버·제품·플랫폼 계약이 없거나 서로 충돌한다.
- 요청한 변경이 이슈의 제외 범위를 넘어간다.
- dependency 또는 아키텍처 선택에 별도 승인이 필요하다.
- canonical RALPLAN 파일이나 `GitHub Issue Packaging`의 이슈→packet 고유 매핑을 확인할 수 없다.
- 실행 기준선의 기존 변경과 현재 이슈 변경이 같은 파일의 같은 hunk에서 겹친다.
- 필수 검증을 실행할 수 없거나 증거가 없다.
- 계속 진행하려면 사용자가 허용하지 않은 파괴적 작업, 자격 증명, 운영 환경, 외부 상태 변경이 필요하다.

진행을 이어가기 위해 placeholder 동작, production URL, 서버 DTO, 미확정 기능을 임의로 만들지 않는다.

## 호출 예시

```text
$coordinate-kmp-issue #33 시작
$coordinate-kmp-issue 다음으로 진행 가능한 KMP App 이슈 시작
$coordinate-kmp-issue #33 구현 중 설계 검토: HttpClient 종료 책임을 어디에 둘까?
$coordinate-kmp-issue #33 구현 완료 리뷰
$coordinate-kmp-issue #33 종료 준비와 다음 이슈 선정
$coordinate-kmp-issue #33을 Codex가 구현하고 테스트까지 진행해
```
