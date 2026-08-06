# Data Layer

## Responsibility

Data Layer는 `app/shared`의 `commonMain/data` 아래에 두며, server API와 local storage를 Domain Layer의 repository contract 뒤로 감춘다. 앱 전체 Ktor client 구성은 `data` 아래에 중첩하지 않고 peer인 `commonMain/network`가 담당한다.

책임:

- Domain Layer의 Repository Interface 구현
- network client 설정과 remote 통신
- local storage/cache 접근
- transport DTO 및 local entity를 Domain 또는 app-facing model로 매핑
- remote/local 조합, freshness, fallback, `AppResult` 변환 정책 구현

Data Layer는 UiModel, UiState, navigation을 알지 못한다. 반대로 Domain Layer는 DTO, DAO, DataStore, Room entity를 알지 못한다.

## Source Set Placement

공통 계약과 정책은 `commonMain`에 둔다. `androidMain`, `iosMain`에는 common code로 해결할 수 없는 platform API 접근과 객체 생성만 둔다.

| 위치 | 책임 |
| --- | --- |
| `commonMain` | repository/data source contract와 impl, mapper, DTO/entity, shared Ktor 설정, Metro graph/binding, 공통 DataStore·Room API 사용 |
| `androidMain` | Android `Context`를 사용하는 DataStore 경로/인스턴스 생성, Room database builder 등 Android 전용 생성 코드 |
| `iosMain` | `NSDocumentDirectory` 경로를 사용하는 DataStore 생성, Room database builder 등 iOS 전용 생성 코드 |

platform API가 필요한 경우에만 `expect`/`actual` 또는 같은 역할의 좁은 platform factory contract를 사용한다. 공통 repository 정책이나 mapper를 platform source set으로 옮기지 않는다.

## Target Package Shape

```text
commonMain/.../
  di/
    AppGraph.kt
  network/
    HttpClient.kt
    NetworkConfig.kt
    di/
      NetworkBinding.kt
  data/
    di/
      DataBindings.kt
    repository/
      MatchRepositoryImpl.kt
      TeamRepositoryImpl.kt
    mapper/
      MatchMapper.kt
      TeamMapper.kt
    remote/
      RemoteMatchDataSource.kt
      impl/
        RemoteMatchDataSourceImpl.kt
      model/
        MatchResponseDto.kt
    local/
      LocalMatchDataSource.kt
      impl/
        LocalMatchDataSourceImpl.kt
      datastore/                 # Preferences storage가 필요한 기능에서만
        PreferencesKeys.kt        # 실제 datasource와 함께 생성
        model/                    # 구조화된 preference 값이 필요할 때만
      room/                      # relational/large DB가 필요한 기능에서만
        dao/
        entity/
```

이 트리는 허용된 목적지와 책임을 설명한다. 기능이 없는 빈 package·file을 미리 만들지 않는다.

## Network Package

`network`는 `data`와 같은 depth의 top-level package로 유지한다. Ktor client 생성과 모든 remote 호출에 공통인 설정만 담당하며, app-scoped provider는 `network/di/NetworkBinding.kt`에 둔다.

- `HttpClient.kt`의 common `expect`와 platform `actual`은 Android/iOS Ktor engine으로 client를 생성한다.
- `NetworkConfig`는 base URL, JSON serialization, logging, timeout처럼 공통 설정을 표현한다.
- API endpoint, request/response DTO, feature별 HTTP 호출은 `remote`에 둔다.
- Ktor client/builder 같은 외부 객체는 Metro DI boundary에서 제공하고, remote impl은 constructor injection으로 받는다.

## Remote Data Source

`remote`는 서버와의 JSON 통신을 담당한다. datasource contract와 impl을 같은 영역에 두고, impl은 interface 뒤에 숨긴다.

```text
remote/
  RemoteMatchDataSource.kt
  impl/
    RemoteMatchDataSourceImpl.kt
  model/
    MatchResponseDto.kt
```

규칙:

- `Remote<Feature>DataSource`는 remote 호출의 contract다.
- `impl`은 Ktor client/builder를 DI로 받아 HTTP 요청, response 검증, DTO deserialization까지만 수행한다.
- remote datasource는 Domain Model·UiModel을 만들지 않고 transport DTO를 반환한다.
- DTO는 서버 request/response transport shape만 표현하며 Compose state, platform type, 화면 표시 문자열을 포함하지 않는다.
- DTO는 기본적으로 `remote/model`에 둔다. feature별 datasource와 DTO가 커져 가독성이 떨어질 때 AI가 `remote/{feature}/model`로 함께 분리할 수 있다.
- remote interface는 repository impl이 DI로 주입받아 사용한다. repository가 HTTP client나 endpoint를 직접 호출하지 않는다.

서버 API contract는 서버와 앱의 별도 경계다. 실제 contract가 구현되는 기능에서는 DTO와 mapper test를 함께 검토한다.

## Local Data Source

`local`은 datasource contract와 impl을 가지며, 기능이 필요할 때 Preferences DataStore 또는 Room을 선택해 사용한다.

```text
local/
  Local<Feature>DataSource.kt
  impl/
    Local<Feature>DataSourceImpl.kt
  datastore/       # key-value persistence가 필요한 기능
  room/            # relational query 또는 큰 local DB가 필요한 기능
```

규칙:

- local datasource는 persistence read/write와 storage model 반환까지만 담당한다.
- local entity와 preference value는 storage shape이며 Domain Model을 대체하지 않는다.
- datasource interface는 `commonMain`에 두고 repository impl은 interface만 주입받는다.
- DataStore와 Room 의존성은 목표 기술 스택으로 관리하되, 실제 datasource·DAO·entity는 저장 요구가 생긴 기능에서만 구현한다.
- cache/freshness 정책이 아직 없는 기능은 local schema를 선행 생성하지 않는다.

### Preferences DataStore

key-value 저장에는 Preferences DataStore만 사용한다. `PreferencesKeys.kt`는 key를 한곳에서 관리하므로 실제 Preferences datasource를 도입할 때 `local/datastore`에 둔다. key가 없는 빈 file은 만들지 않는다.

- Android DataStore 인스턴스 생성에는 `Context`와 파일 path가 필요하다.
- iOS에서는 `NSDocumentDirectory`에서 DataStore path를 얻는다.
- 이처럼 platform path/API가 필요한 생성부만 `androidMain`/`iosMain`의 factory로 분리하고, datasource contract와 repository 정책은 `commonMain`에 유지한다.
- 단순 scalar key-value가 아닌 구조화된 preference 값을 저장해야 할 때만 `local/datastore/model`에 wrapper model을 둔다.

공식 KMP DataStore 지침은 [Preferences DataStore](https://developer.android.com/kotlin/multiplatform/datastore)를 기준으로 한다.

### Room

Room은 관계형 조회, 큰 데이터 집합, 명시적인 schema/DAO가 필요한 기능에서만 도입한다.

- DAO는 `local/room/dao`, entity는 `local/room/entity`에 둔다.
- database/DAO/entity의 공통 선언은 가능한 `commonMain`에 둔다.
- 실제 database builder는 platform API가 필요하므로 `androidMain`/`iosMain` factory에서 만든다.
- Room entity, DAO result를 repository contract나 UI로 직접 노출하지 않는다.

세부 platform builder 방식은 [KMP Room 공식 지침](https://developer.android.com/kotlin/multiplatform/room)을 따른다.

## Repository Implementation

RepositoryImpl은 `commonMain/data/repository`에 둔다.

- Domain Layer의 Repository Interface를 구현한다.
- remote/local datasource interface를 DI로 주입받아 조합한다.
- DTO/entity/preference value를 Domain 또는 app-facing model로 매핑한다.
- remote/local 선택, refresh, fallback, cache write, loading failure의 `AppResult.Failure` 변환은 RepositoryImpl이 담당한다.
- UI rendering 세부사항, 사용자 노출 문자열, navigation 정책은 넣지 않는다.
- 복잡한 비즈니스 규칙이 쌓이면 Domain UseCase로 이동할지 검토한다.

기능별 cache TTL, stale 허용 범위, pull-to-refresh, offline 동작은 실제 기능 요구가 생길 때 결정한다. 수동 재시도도 해당 화면의 요구가 있을 때만 UI event로 제공하며, Repository 결과에 retry flag를 넣지 않는다.

## Public Failure Contract

- HTTP, network, serialization, cache miss, local persistence 실패의 상세 분류는 Data Layer 내부의 처리·관측을 위한 정보다.
- RepositoryImpl은 non-cancellation loading failure를 `domain/AppResult.Failure`로 변환한다. coroutine cancellation은 변환하지 않고 전파한다.
- raw exception, HTTP code, server 내부 메시지, storage implementation detail을 repository contract·ViewModel·UI로 직접 노출하지 않는다.
- 초기 scraping 범위에서는 failure category, 자동 재시도, 오류별 UI 분기를 구현하지 않는다. 이러한 요구가 생기면 shared error model의 필요성을 Domain·Data·UI 경계에서 다시 검토한다.

## Mapper and Model Rules

이 문서에서 app-facing model은 Domain Model과 같은 의미다. mapper는 transport/storage model을 Domain Model로 변환한다.

`mapper`는 transport/storage model을 Domain 또는 app-facing model로 변환한다.

- mapper는 순수 함수를 우선하며 network, database, platform API를 호출하지 않는다.
- remote DTO는 `remote/model`, Room entity는 `local/room/entity`에 둔다.
- DataStore model은 필요한 경우에만 `local/datastore/model`에 둔다.
- 표시용 문자열, UI 상태, Compose type은 Domain Model이나 data mapper에 넣지 않는다.
- VLR.GG 응답 구조를 의미 있게 해석하는 mapper는 focused test를 작성한다.

## Metro DI

Metro의 app-level graph는 `commonMain/di/AppGraph.kt`, app-wide client binding은 `commonMain/network/di/NetworkBinding.kt`, feature Data Layer binding은 `commonMain/data/di/DataBindings.kt`에 둔다. graph의 runtime 준비와 navigation 경계는 `app-runtime.md`의 기본 원칙을 따른다. Data Layer와 network package는 app graph를 생성하거나 Compose composition local을 제공하지 않는다.

처음에는 하나의 `DataBindings.kt`에서 영역을 구분한다.

```kotlin
// Remote bindings

// Local bindings
```

- Data binding 구현체는 constructor injection을 우선한다.
- `HttpClient`, DataStore instance, Room database builder처럼 constructor만으로 만들기 어려운 외부 객체는 data DI boundary에서 제공한다.
- feature-local `remote/di`, `local/di`처럼 세부 package별 DI package를 만들지 않는다. `network/di`는 application-wide Ktor client provider를 data feature binding과 분리하기 위한 예외다.
- 한 feature binding 섹션이 커져 읽기 어려워지면 AI가 `data/di` 안의 peer file(`RemoteBindings.kt`, `LocalBindings.kt`)로 분리할 수 있다.

Metro binding의 역할은 [Metro dependency graph 문서](https://zacsweers.github.io/metro/latest/dependency-graphs/)를 따른다.

## Dependency Introduction Rules

이 문서는 다음 기술의 책임과 도입 조건을 정한다.

| 기술 | 책임 | 실제 도입 시점 |
| --- | --- | --- |
| Kotlinx Serialization | JSON DTO serialization | remote JSON 통신 구현 시 |
| Ktor Client | shared HTTP client와 remote 통신 | network/remote 구현 시 |
| Preferences DataStore | key-value persistence | 해당 저장 요구가 있는 기능 구현 시 |
| Room | relational 또는 큰 local DB persistence | 해당 저장 요구가 있는 기능 구현 시 |
| Metro | app/data object graph와 binding | DI를 사용하는 data 구현 시 |

실제 Gradle version/dependency 추가는 각 기능 구현 작업에서 `gradle/libs.versions.toml`과 모듈 build file을 함께 갱신하며 결정한다.

## Error and Testing Expectations

- HTTP, network, serialization, cache miss, local persistence 실패를 구분 가능한 error category로 변환한다.
- raw exception을 ViewModel이나 UI로 직접 노출하지 않는다.
- mapper test는 DTO/entity/preference value에서 Domain 또는 app-facing model로의 변환을 검증한다.
- repository test는 remote/local 조합, fallback, refresh, error mapping을 기능 정책에 맞게 검증한다.
- platform factory는 DataStore/Room을 실제 도입한 경우 각 target에서 smoke check 또는 해당 platform test로 검증한다.
- Data Layer 변경 후 가장 좁은 shared test task를 먼저 실행한다.
