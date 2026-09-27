# Domain Layer

Domain은 `app/shared/src/commonMain/.../domain`에서 app-facing model, repository contract와 공통 결과를 정의한다. Ktor DTO, DataStore/Room, platform API, Compose, UI state와 navigation에는 의존하지 않는다.

## Model 경계

- Repository는 Domain Model을 반환한다. DTO와 storage entity는 Data Layer에 남긴다.
- UI가 그대로 표시할 수 있는 Domain Model은 `UiState`에서 직접 사용해도 된다.
- 형식화된 문자열, 색상·icon, 화면별 그룹화·선택 상태는 UI 전용 model에 둔다.
- Domain Model에는 Compose/platform type, navigation 정보나 transport 이름을 넣지 않는다.

## `AppResult`

실패할 수 있는 repository의 공개 결과는 다음 세 상태를 사용한다.

```kotlin
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data object Failure : AppResult<Nothing>
    data class Busy(val retryDelay: Duration) : AppResult<Nothing>
}
```

- `Failure`에는 분류, HTTP code, raw exception, server message와 retry flag를 넣지 않는다.
- 공개 API가 429/`RATE_LIMITED` 또는 503/`SERVER_BUSY`로 알려 준 과부하만 `Busy`로 변환한다. 다른 non-cancellation 실패는 `Failure`다.
- `Busy.retryDelay`는 UI의 수동 재시도 안내를 위한 값이며 자동 재시도 명령이 아니다. 화면은 작업 identity와 monotonic deadline을 소유한다.
- cancellation은 결과로 바꾸지 않고 전파한다.
- `onFailure`와 `onBusy` 소비자는 서로 다른 상태를 처리하며 한 결과에 두 오류 UI를 표시하지 않는다.

세부 HTTP 경계는 [`../architecture/server-public-api-protection.md`](../architecture/server-public-api-protection.md)를 따른다.

## Repository와 UseCase

Repository interface는 Domain Model 또는 `AppResult<Domain Model>`만 노출한다. Data source, DTO/entity와 구현 예외는 Data Layer가 숨긴다.

UseCase는 필수가 아니다. 단순 repository 호출 wrapper는 만들지 않고 다음 중 하나가 있을 때만 도입한다.

- 여러 ViewModel이 같은 domain policy를 재사용한다.
- 여러 repository를 조합한다.
- 정렬, 필터링, 상태 판정처럼 명시적인 앱 정책이 있다.
- 독립적으로 검증할 domain rule이 있다.

새 결과 분기를 추가하면 모든 소비자와 fake를 함께 갱신한다. ViewModel은 `AppResult`를 화면 상태로 바꾸고, UseCase가 있으면 정책과 결과 전파를 `commonTest`에서 검증한다.
