# Domain Layer

## Responsibility

Domain Layer는 `app/shared/src/commonMain/.../domain` 아래에 둔다. 앱의 app-facing business model과 repository contract를 정의하고, UI·Data·platform 구현이 만나는 경계를 제공한다.

```text
domain/
  AppResult.kt
  model/
  repository/
  usecase/       # 명시적인 domain policy가 있을 때만
```

Domain Layer의 책임:

- 앱에서 사용하는 순수 Domain Model 정의
- Repository Interface와 공통 결과 contract 정의
- 여러 화면에서 재사용되는 명시적 business policy 정의
- UI와 Data가 의존할 수 있는 안정적인 공통 경계 제공

Domain Layer는 다음을 알면 안 된다.

- Ktor client/server DTO, request/response transport shape
- Room, DataStore, DAO, entity 등 local persistence 구현
- Android Context, iOS API, platform type
- Compose UI, UiModel, UiState, navigation
- HTTP code, raw exception, server 내부 오류 메시지

공통 model, repository contract, `AppResult`, UseCase는 `commonMain`에 둔다. platform source set에는 Domain policy를 구현하지 않는다.

## Domain Model and UiModel Boundary

Domain Model은 repository contract가 반환하는 app-facing business model이다. DTO·entity와 별개의 모델이며 UI가 단순히 표시할 수 있다.

```kotlin
data class Match(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val startTime: String,
    val status: MatchStatus,
)
```

규칙:

- UI는 presentation 변환이 필요하지 않으면 Domain Model을 직접 `UiState`에 둘 수 있다.
- 날짜/시간 문자열, 상태별 label·색상·icon, 화면용 그룹화, 선택·확장 같은 UI 상태가 필요할 때만 UI Layer에서 UiModel을 만든다.
- UiModel은 UI Layer의 소유다. UiModel이 Domain Model을 포함하거나 감싸는 것은 허용하지만, Domain Model과 Domain Layer가 UiModel을 참조해서는 안 된다.
- Domain Model에는 화면 출력 전용 문자열, Compose state, navigation 정보, platform type을 넣지 않는다.
- Domain Model은 RemoteResponse, Request, DTO, Entity에 의존하지 않는다.

## AppResult

Repository가 데이터를 불러오는 public contract에는 공통 `AppResult<T>`를 사용한다. domain별 transport failure sealed type을 만들지 않는다.

```kotlin
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    object Failure : AppResult<Nothing>
}
```

`AppResult`는 `commonMain/domain/AppResult.kt`에 둔다.

규칙:

- 초기 `Failure`는 단일 generic failure다. error category, HTTP code, raw exception, server message, retry flag를 포함하지 않는다.
- Data Layer는 non-cancellation loading failure를 repository boundary에서 `Failure`로 변환한다.
- coroutine cancellation은 failure로 변환하지 않고 전파한다.
- ViewModel은 `AppResult`를 UI가 소비할 `UiState`로 변환한다. UI는 raw exception이나 Data Layer failure type을 해석하지 않는다.
- 오류별 분기, 자동 재시도, failure category가 실제 기능 요구가 될 때만 shared error model을 설계하고 Domain·Data·UI 문서를 함께 갱신한다.

## Repository Interface

초기 scraping 기능처럼 실패할 수 있는 data-loading repository method는 `AppResult<Domain Model>`을 반환한다. 실패 가능성이 없는 순수 query나 계산에만 Domain Model을 직접 반환할 수 있다.

Repository Interface는 `commonMain/domain/repository`에 둔다.

```kotlin
interface MatchRepository {
    suspend fun getUpcomingMatches(): AppResult<List<Match>>
    suspend fun getMatchDetail(matchId: String): AppResult<Match>
}
```

규칙:

- Repository Interface는 Domain Model 또는 `AppResult<Domain Model>`만 노출한다.
- Repository Interface는 UiModel, DTO, entity, data source 구현체, raw exception을 노출하지 않는다.
- Repository 구현체는 `commonMain/data/repository`에 둔다.
- repository는 Data Layer의 remote/local 조합과 오류 변환을 숨기고, Domain에는 안정적인 contract만 제공한다.

## UseCase

UseCase는 필수가 아니다. 단순히 Repository 메서드를 한 번 호출하거나 `AppResult`를 그대로 전달하는 UseCase는 만들지 않는다.

UseCase는 다음 조건 중 하나 이상을 만족할 때만 만든다.

- 여러 ViewModel에서 같은 domain policy를 재사용한다.
- 여러 Repository를 조합해야 한다.
- 정렬, 필터링, 상태 판단 등 명시적인 앱 정책이 있다.
- RepositoryImpl에 business policy가 과도하게 쌓이고 있다.
- 별도 단위 테스트가 필요한 domain rule이 있다.

좋은 예:

```kotlin
class GetUpcomingMatchesUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(): AppResult<List<Match>> {
        return when (val result = matchRepository.getUpcomingMatches()) {
            is AppResult.Success -> AppResult.Success(
                result.data.filter { it.status != MatchStatus.Finished },
            )
            AppResult.Failure -> AppResult.Failure
        }
    }
}
```

나쁜 예:

```kotlin
class GetMatchDetailUseCase(
    private val matchRepository: MatchRepository,
) {
    suspend operator fun invoke(matchId: String): AppResult<Match> {
        return matchRepository.getMatchDetail(matchId)
    }
}
```

위 예시는 repository call을 그대로 감싸기만 하므로 ViewModel에서 Repository를 직접 호출하는 편이 낫다.

## Testing Expectations

- `AppResult`를 소비하는 ViewModel은 success와 generic failure 상태 전환을 테스트한다.
- repository fake는 success와 `Failure`를 모두 반환할 수 있어야 한다.
- UseCase가 생긴 경우 domain policy와 `AppResult` propagation을 `commonTest`에서 검증한다.
- UiModel이 필요한 화면은 Domain Model에서 presentation state로의 변환을 UI Layer test에서 검증한다.
