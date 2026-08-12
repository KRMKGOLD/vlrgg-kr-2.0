# ADR-0002: News Image Loader

- Status: Accepted
- Date: 2026-08-11
- Decision scope: [GitHub Issue #35](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/35)의 H4-D0 image loader decision gate
- Related: [News](../../feature/news/README.md), [App Runtime Composition](../app-runtime.md), [App Architecture](../app-arch.md), [UI Layer](../ui-layer.md), [ADR-0001](0001-thin-app-runtime-kernel.md)

## Context

News Detail은 서버가 제공하는 `NewsArticleBlock.Image(imageUrl, caption)`을 Android와 iOS의 Compose Multiplatform UI에서 표시해야 한다. 이미지 요청의 loading 또는 failure가 문단, 목록, 캡션을 포함한 기사 전체의 읽기 가능성을 훼손해서는 안 된다.

현재 toolchain은 Compose Multiplatform 1.11.1, Kotlin 2.4.0, Ktor 3.5.0, Coroutines 1.10.2다. Coil 공식 문서는 일반적인 Compose Multiplatform 앱에 singleton-backed `coil-compose` API를 기본 경로로 제공하며, 별도 설정이 없다면 기본 `ImageLoader`를 지연 생성해 애플리케이션 전체에서 공유한다.

이미지 로딩은 교체 가능한 UI/runtime dependency다. Domain과 Data 계층의 모델 또는 repository 계약에 image loader type, cache policy, Compose type을 추가하지 않는다.

## Considered options

| Candidate | Evaluation | Result |
| --- | --- | --- |
| Coil 3.5.0 | Android/iOS Compose Multiplatform 지원, 현재 Kotlin·Compose·Coroutines 버전과 정렬, Ktor 3 adapter, memory/disk cache, Compose request lifecycle, deterministic fake engine을 제공한다. | Selected |
| Kamel | Compose Multiplatform 이미지 로딩을 지원하지만 현재 요구에서 Coil보다 분명한 이점이 없고 별도의 구성·cache 정책을 채택해야 한다. | Rejected |
| Landscapist | Compose image-loading integration layer가 필요한 요구가 없으며 현재 범위에는 추가 abstraction과 dependency가 된다. | Rejected |
| 직접 Ktor fetch/decode/cache 구현 | 동작을 완전히 통제할 수 있지만 cancellation, decoding, memory/disk cache와 test seam을 직접 소유해야 한다. | Rejected |

## Decision

### 1. Coil 3.5.0 and its Ktor 3 adapter are selected

News image pipeline은 Coil 3.5.0을 사용한다. 최소 dependency surface는 다음과 같다.

- `commonMain`: `io.coil-kt.coil3:coil-compose:3.5.0`
- `commonMain`: `io.coil-kt.coil3:coil-network-ktor3:3.5.0`
- `commonTest`: `io.coil-kt.coil3:coil-test:3.5.0`

기존 `androidMain`의 `io.ktor:ktor-client-android:3.5.0`과 `iosMain`의 `io.ktor:ktor-client-darwin:3.5.0`을 platform engine으로 재사용한다. `coil-network-cache-control`, SVG/GIF/video decoder module은 H4에 추가하지 않는다.

Coil 3.5.0은 Kotlin 2.4.0, Compose Multiplatform 1.11.1, Coroutines 1.10.2로 빌드된 stable release다. Android minimum SDK 23과 Java 11 bytecode 요구사항은 현재 앱의 minimum SDK 29와 JVM target 11에 들어온다. 현재 iOS target인 `iosArm64`와 `iosSimulatorArm64`는 지원 범위이며 제거된 `iosX64` target은 앱에 존재하지 않는다.

`coil-network-ktor3:3.5.0`은 Ktor 3 adapter이며 published metadata에서 `ktor-client-core:3.1.0`을 non-strict dependency로 요구한다. Ktor는 같은 major의 minor release를 backward-compatible functionality로 정의하고, 앱이 Ktor 3.5.0을 직접 선언하므로 Gradle resolution은 Ktor 3.5.0을 선택하는 것으로 판단한다. 다만 Coil upstream이 Ktor 3.5.0 조합을 별도로 인증하지는 않았으므로 Android/iOS dependency resolution, compilation, 실제 image fetch가 integration gate다.

### 2. Use Coil's default application-wide singleton

일반적인 Compose Multiplatform 앱을 위한 Coil 공식 기본 경로를 따른다. `coil-compose`의 singleton-backed `AsyncImage`는 현재 platform context로 기본 `ImageLoader`를 최초 요청 시 한 번 생성하고 이후 Android/iOS Compose UI에서 공유한다. loader의 memory cache, disk cache와 내부 network resource도 이 application-wide instance가 재사용한다.

- `ImageLoader`를 Metro `AppGraph`, Screen, navigation entry 또는 article block에 주입하지 않는다.
- app-owned `CompositionLocal`이나 platform별 graph wrapper를 추가하지 않는다.
- 별도 설정이 없으므로 production에서 `setSingletonImageLoaderFactory`를 호출하지 않는다.
- API repository가 사용하는 app `HttpClient`를 Coil에 주입하지 않는다. Coil Ktor 3 adapter가 소유하는 image 전용 client의 기본 설정을 사용해 API와 image pipeline의 header, timeout, failure와 cache 책임을 분리한다.
- custom cache, authenticated request 또는 공통 request 설정이 필요해지면 `setSingletonImageLoaderFactory`를 app root에서 최초 image request 전에 한 번 호출하는 방식을 재검토한다.

### 3. Use the singleton-backed AsyncImage overload

News Detail의 feature-local image renderer는 `coil-compose`의 `AsyncImage(model = ..., contentDescription = ...)` overload를 사용한다. loader parameter를 Screen, navigation callback, ViewModel, Domain 또는 Data 계층으로 전달하지 않는다.

News article image renderer는 News Detail package에 둔다. 두 번째로 동일한 image UI 계약을 가진 consumer가 실제로 생기기 전에는 범용 image component나 design-system abstraction으로 승격하지 않는다.

### 4. Coil default cache and request lifecycle policies are used

H4에서는 Coil singleton의 기본 memory cache, disk cache, request lifecycle과 network component 설정을 사용한다.

- 별도 cache directory, size percentage, cache key 또는 eviction policy를 지정하지 않는다.
- `coil-network-cache-control`을 추가하지 않는다. 따라서 network response는 Coil 기본 정책에 따라 disk cache에 기록된다.
- API client cache와 image cache를 결합하지 않는다.
- Compose에서 image model이 바뀌거나 image composable이 composition을 떠나면 `AsyncImage`가 해당 request를 취소하도록 맡긴다.
- ViewModel이 image request job, loading state, retry 또는 cancellation을 소유하지 않는다.
- 별도 prefetch 또는 수동 `ImageLoader.enqueue`는 H4 범위에 포함하지 않는다.

HTTP cache header 준수, cache size 조정, authenticated image request 또는 cache invalidation 요구가 관측되면 측정 자료와 server policy를 바탕으로 재검토한다.

### 5. Image failure is local and never replaces article content

이미지 요청 실패는 해당 image block의 image 영역만 failure 상태로 남긴다. fallback image와 자동 retry를 제공하지 않으며 raw exception, URL, HTTP status 또는 server message를 표시하지 않는다.

- article의 제목, metadata, 문단과 목록은 계속 표시한다.
- caption이 있으면 image 실패와 관계없이 원래 block 위치에 유지한다.
- image failure를 `NewsDetailUiState.Error` 또는 article-wide failure로 변환하지 않는다.
- loading/error UI는 image renderer 내부에 제한하며 주변 block의 순서와 layout 의미를 보존한다.
- null 또는 지원 불가능한 image data가 있더라도 내부 navigation이나 외부 embed로 추정하지 않는다.

Twitch, YouTube, X/Twitter 또는 다른 iframe/embed는 image가 아니며 Coil로 처리하지 않는다. 실제 기사 요구가 확인되면 보안, playback, consent와 외부 navigation 정책을 포함한 별도 issue/decision으로 다룬다.

### 6. Image behavior is tested through an explicit fake seam

`coil-test`의 `FakeImageLoaderEngine`을 설치한 test-owned `ImageLoader`를 Coil의 test-only singleton 교체 경계에 제공해 success와 failure를 deterministic하게 검증한다. production 코드에서 singleton을 교체하거나 network I/O에 의존하는 UI test를 만들지 않는다. singleton을 교체하는 테스트는 다른 테스트와 상태가 섞이지 않도록 자체적으로 loader를 설치하고 정리한다.

H4의 image 관련 최소 assertion은 다음과 같다.

- singleton-backed `AsyncImage`가 test-owned fake loader의 결과를 사용한다.
- image loading success가 해당 block과 caption 순서를 보존한다.
- image failure에도 앞뒤 문단, 목록, caption과 article content가 남는다.
- image failure가 article-wide Error나 내부 navigation을 발생시키지 않는다.
- composition에서 image renderer가 제거되면 진행 중 request가 lifecycle에 따라 취소된다.

현재 UI test infrastructure로 직접 검증하기 어려운 항목은 fake engine을 사용한 가장 좁은 Compose test와 Android/iOS compile 및 manual smoke evidence로 나눈다. 이를 위해 범용 UI test framework를 새로 만들지 않는다.

## Consequences

- Android/iOS가 Coil의 기본 singleton을 통해 하나의 Compose Multiplatform image pipeline과 cache를 공유한다.
- AppGraph와 platform entrypoint가 Coil `PlatformContext` 또는 `ImageLoader`를 알지 않으므로 runtime wiring이 단순하게 유지된다.
- Screen마다 loader parameter나 app-owned CompositionLocal이 확산되지 않는다.
- test-only singleton 교체는 명시적 DI보다 격리가 약하므로 image UI 테스트는 상태 설치와 정리 책임을 가진다.
- 기본 cache 정책은 초기 구현을 작게 유지하지만 HTTP `Cache-Control`을 준수하지 않고 network response를 disk cache에 기록한다.
- API client와 image client를 분리하므로 구성 결합은 줄지만 각 pipeline이 별도 network resources를 보유한다.
- image failure가 기사 전체 상태와 분리되어 텍스트 가독성은 유지되지만 H4에서는 실패한 이미지를 화면에서 직접 재시도할 수 없다.

## Non-goals and deferred work

- Team/Player 실제 Detail UI
- Event/Match article link navigation과 외부 브라우저 정책
- iframe, Twitch, YouTube, X/Twitter embed 또는 video playback
- SVG, GIF, animated image와 video frame decoder
- image prefetch, authenticated image headers, custom timeout와 retry policy
- custom cache directory/size/key, HTTP cache-control module, Room/DataStore cache
- 범용 rich-text engine 또는 범용 image component/design-system 확장
- Domain/Data model에 Coil, Compose 또는 cache type 추가
- News List 구조나 repository 계약 변경

## Verification contract

Dependency를 추가한 직후 News Detail 구현보다 먼저 다음 compatibility gate를 통과해야 한다.

1. Gradle `dependencyInsight`로 관련 Android와 iOS resolution에서 `io.ktor:ktor-client-core`가 3.5.0으로 선택되고 strict conflict 또는 예상하지 않은 engine이 없는지 확인한다.
2. `./gradlew :app:shared:compileAndroidMain`
3. `./gradlew :app:shared:compileKotlinIosSimulatorArm64`
4. Android와 iOS에서 HTTPS image 한 건의 loading, success와 failure를 smoke-test한다.

H4 screen packet 완료 전에는 다음 전체 경계를 검증한다.

- 가장 좁은 News Detail ViewModel/block/image test
- `./gradlew :app:shared:testAndroidHostTest`
- `./gradlew :app:shared:iosSimulatorArm64Test`
- `./gradlew :app:shared:compileAndroidMain`
- `./gradlew :app:shared:compileKotlinIosSimulatorArm64`
- `./gradlew :app:androidApp:assembleDebug`
- `git diff --check`
- Android/iOS Loading, Empty, Error, Content와 image-failure 상태 확인

## Revisit triggers

다음 조건이 생기면 이 결정을 새 ADR로 대체하거나 확장한다.

- Coil 또는 Ktor의 major upgrade, Kotlin/Compose toolchain 변경으로 binary/variant compatibility가 달라짐
- HTTP `Cache-Control`, stale failure, storage quota 또는 cache invalidation이 제품 요구가 됨
- authenticated image, custom header/timeout 또는 API client resource sharing이 필요해짐
- SVG, GIF, video frame 또는 iframe/embed를 실제 article block으로 지원해야 함
- 서로 다른 cache/auth 정책을 가진 두 번째 image pipeline이 필요함
- 두 번째 feature가 News와 동일한 image presentation 계약을 사용해 공통 component 승격 근거가 생김
- custom `ImageLoader` 설정이나 명시적 resource shutdown이 제품 기능이 됨
- `iosX64` 등 현재 지원하지 않는 platform target이 추가됨
- image failure에 retry, fallback 또는 별도 접근성 표현이 필요해짐

## References

- [Coil 3.5.0 release](https://github.com/coil-kt/coil/releases/tag/3.5.0)
- [Coil 3.5.0 changelog](https://github.com/coil-kt/coil/blob/3.5.0/CHANGELOG.md)
- [Coil image loader ownership and caching](https://coil-kt.github.io/coil/image_loaders/)
- [Coil Compose artifacts and Ktor 3 adapter](https://coil-kt.github.io/coil/getting_started/)
- [Coil 3.5.0 singleton AsyncImage source](https://github.com/coil-kt/coil/blob/3.5.0/coil-compose/src/commonMain/kotlin/coil3/compose/SingletonAsyncImage.kt)
- [Coil network images and Ktor engines](https://coil-kt.github.io/coil/network/)
- [Coil testing and FakeImageLoaderEngine](https://coil-kt.github.io/coil/testing/)
- [Coil 3.5.0 Ktor 3 module metadata](https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-network-ktor3/3.5.0/coil-network-ktor3-3.5.0.module)
- [Coil Apache-2.0 license](https://github.com/coil-kt/coil/blob/3.5.0/LICENSE.txt)
- [Ktor release and compatibility policy](https://ktor.io/docs/releases.html)
