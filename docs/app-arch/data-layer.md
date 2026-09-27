# Data Layer

Data Layer는 `app/shared/src/commonMain/.../data`에서 server API와 local storage를 Domain repository 뒤로 감싼다. 공통 Ktor client 구성은 peer package인 `network`가 맡는다.

## 경계와 배치

- `remote`: HTTP 요청, status 확인, DTO 역직렬화
- `local`: persistence read/write와 storage model
- `repository`: remote/local 조합, mapper와 `AppResult` 변환
- `network`: graph당 하나의 Ktor client와 공통 설정
- `data/di`: feature data binding

공통 contract와 정책은 `commonMain`에 둔다. Android `Context`, iOS directory, Ktor engine처럼 플랫폼 API가 필요한 객체 생성만 platform source set에 둔다. Repository와 mapper 정책을 플랫폼별로 복제하지 않는다.

## Remote와 model

- Remote data source는 Ktor client를 주입받아 feature endpoint를 호출하고 transport DTO를 반환한다. Domain/UI model을 만들지 않는다.
- Repository가 DTO/storage model을 Domain Model로 변환한다. Mapper는 가능한 순수 함수로 두며 network, database와 platform API를 호출하지 않는다.
- DTO, storage entity와 preference value를 repository contract나 UI에 노출하지 않는다.
- 표시용 문자열, Compose state와 navigation 정보는 mapper나 Domain Model에 넣지 않는다.

실제 구현은 feature가 커질 때만 하위 package로 나눈다. 빈 interface, model 또는 package를 미리 만들지 않는다.

## Local storage

현재 즐겨찾기는 Preferences DataStore를 사용하며 공통 data source와 플랫폼별 파일 생성 경계를 가진다. 단순 key-value에는 DataStore를 사용한다.

Room은 관계형 조회, 큰 데이터 집합과 명시적인 schema/DAO가 실제로 필요할 때만 도입한다. 도입 시 entity/DAO는 가능한 `commonMain`, database builder만 platform source set에 두고 storage model을 외부에 노출하지 않는다. Cache/freshness 요구가 없는 기능에는 local schema를 만들지 않는다.

## Repository와 실패

- Repository implementation은 Domain repository를 구현하고 data source를 주입받아 조합한다.
- 기능별 refresh, fallback, cache TTL과 offline 정책은 기능 문서가 정할 때만 구현한다.
- coroutine cancellation은 다시 던진다. 그 밖의 일반 실패는 `AppResult.Failure`로 변환한다.
- 공개 조회 helper는 429/`RATE_LIMITED`와 503/`SERVER_BUSY`만 `AppResult.Busy`로 변환한다. 플랫폼 HTML, 알 수 없는 code와 제한을 넘은 오류 body는 일반 실패다.
- 오류 body는 8KiB와 초과 판별용 1 byte까지만 읽고 남은 channel을 취소한다.
- `Retry-After`는 정수 1~60초만 허용하며 누락·오류 값은 2초를 사용한다.
- Data Layer는 자동 재시도하거나 dialog 상태를 소유하지 않는다. raw exception, HTTP code, server 내부 message와 storage 세부사항도 ViewModel/UI에 노출하지 않는다.

## DI와 도입 기준

`AppGraph`가 app-wide client와 data binding을 연결한다. 구현체는 constructor injection을 우선하고, DataStore instance처럼 별도 생성이 필요한 객체만 data DI boundary에서 제공한다. Binding이 실제로 커질 때만 peer file로 분리한다.

Kotlinx Serialization과 Ktor는 remote JSON 통신, DataStore는 key-value 저장에 사용한다. Room은 위 조건이 충족되기 전에는 dependency와 scaffolding을 추가하지 않는다. Dependency 변경은 `gradle/libs.versions.toml`과 해당 build file에서 함께 관리한다.

Mapper의 의미 변환, repository의 조합·오류 경계와 platform storage factory를 변경하면 가장 좁은 shared/platform test로 검증한다.
