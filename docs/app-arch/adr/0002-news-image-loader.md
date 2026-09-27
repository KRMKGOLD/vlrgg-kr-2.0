# ADR-0002: News Image Loader

- Status: Accepted
- Date: 2026-08-11
- Scope: [Issue #35](https://github.com/KRMKGOLD/vlrgg-kr-2.0/issues/35) H4-D0
- Related: [News](../../feature/news/README.md), [Runtime](../app-runtime.md)

## Context

News Detail은 서버의 image block을 Android/iOS Compose UI에서 표시한다. 이미지 요청 실패가 기사 본문과 caption의 읽기 가능성을 훼손하면 안 된다. Image loader는 UI/runtime dependency이며 Domain/Data 계약에 스며들지 않아야 한다.

## Decision

### Coil singleton

`coil-compose`와 Ktor 3 adapter의 기본 application-wide singleton `ImageLoader`를 사용한다. 선택 당시 버전은 Coil 3.5.0(`coil-network-ktor3:3.5.0`)이며 현재 버전은 [version catalog](../../../gradle/libs.versions.toml)가 소유한다. News renderer는 singleton-backed `AsyncImage`를 사용한다.

- `ImageLoader`를 Metro graph, Screen, ViewModel, navigation entry 또는 article model에 주입하지 않는다.
- API용 Ktor client와 image client는 분리한다.
- 기본 memory/disk cache와 request lifecycle을 사용한다. 별도 cache size/key, prefetch, 수동 enqueue와 자동 retry는 두지 않는다.
- Renderer는 News feature에 둔다. 같은 표시 계약을 쓰는 두 번째 feature가 생길 때만 공통 component를 검토한다.

현재 dependency는 `coil-compose`, `coil-network-ktor3`, test의 `coil-test`다. SVG/GIF/video decoder와 HTTP cache-control module은 요구가 생기기 전에는 추가하지 않는다.

### 실패 격리

Image loading/failure는 해당 block 내부 상태로 제한하고 기사 전체 `Content` 상태를 유지한다.

- 제목, metadata, 앞뒤 문단과 목록을 계속 표시한다.
- Caption은 image 실패와 관계없이 원래 위치에 유지한다.
- Article-wide error, `Partial` 상태, 전역 경고, fallback image와 자동 retry로 승격하지 않는다.
- Raw exception, URL, HTTP status와 server message를 표시하지 않는다.
- iframe, Twitch, YouTube와 외부 embed를 image로 추정해 처리하지 않는다.

접근성 설명은 article block의 의미를 따르며 `contentDescription`을 loader나 Domain 정책으로 만들지 않는다.

### 검증 seam

Image UI는 `FakeImageLoaderEngine`을 설치한 test-owned loader로 success/failure를 결정적으로 검증한다. Test가 singleton 설치와 정리를 소유하며 production 코드는 교체 seam을 노출하지 않는다.

최소 회귀 계약은 block/caption 순서, 실패 뒤 기사 본문 유지, article-wide 상태·navigation 불변과 composition 이탈 시 request 취소다. Android/iOS compile과 HTTPS image smoke test는 dependency/platform 호환성을 확인한다.

## 결과

- App graph와 API client가 image pipeline 설정을 알지 않는다.
- 기본 cache는 작고 일관된 초기 구현을 제공하지만 HTTP cache-control, 인증 header와 별도 retry를 지원하지 않는다.
- 실패한 이미지는 직접 재시도할 수 없지만 기사 텍스트는 계속 읽을 수 있다.

## 재검토 조건

- 인증 header, custom timeout/cache, HTTP cache-control 또는 API client 공유가 필요할 때
- SVG, GIF, video/embed를 지원할 때
- 두 번째 image pipeline이나 공통 image component의 실제 사용처가 생길 때
- Image 실패에 retry, fallback 또는 별도 접근성 표현이 필요할 때
- Coil/Ktor major 또는 지원 platform target이 바뀔 때

## 참고

- [Coil image loader](https://coil-kt.github.io/coil/image_loaders/)
- [Coil Compose와 Ktor adapter](https://coil-kt.github.io/coil/getting_started/)
- [Coil testing](https://coil-kt.github.io/coil/testing/)
