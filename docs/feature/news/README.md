# News

News는 최신 기사 목록과 구조화된 본문을 제공하고 본문의 Team·Player를 앱 Detail로 연결하는 Phase 1 기능이다. 서버·앱 구현과 자동화 검증은 완료됐고 실기기 렌더·접근성 확인은 남아 있다. 공통 pagination·refresh·오류 계약은 [Feature Guide](../README.md#공통-상태와-오류-계약)를 따른다.

## MVP 범위

- 최신순 목록, 페이지 추가 로딩, pull-to-refresh
- 제목·작성자·게시 시각을 담은 thumbnail 없는 divider full-row
- 제목·작성자·게시 시각과 원순서의 paragraph, image/caption, ordered/unordered list, link block
- 본문 Team·Player 링크의 내부 Detail 이동
- Back/Search 왕복 뒤 로드한 목록·상태 복원. 정밀 scroll 복원은 필수 완료 기준이 아니다.

Event·Match 본문 링크 이동, external link browsing 정책, embed 재생, 댓글·포럼·AI 요약·offline 보관은 제외한다. 지원하지 않는 block이나 embed를 임의 text 또는 유효한 내부 링크로 만들지 않는다.

## 상태와 인터랙션

- 목록은 Initial Loading, Populated, Empty, Initial Error, Pagination Error를 구분한다.
- 목록 끝 요청은 한 번만 실행하고 footer spinner를 사용한다. 실패 시 기존 항목을 유지하고 같은 page Retry를 제공한다.
- refresh는 기존 목록을 비우고 `page=1`만 요청한다. 진행 중 pagination을 취소하거나 이전 결과를 무시해 stale 항목 삽입을 막는다.
- Detail은 Loading, Populated, Empty, Error를 구분한다. 필수 제목·본문 구조 실패는 Error다.
- 선택적 image·caption·unsupported embed가 원문에 없으면 해당 요소만 생략한다. 이미지 로딩이 실패해도 본문과 기존 caption은 유지하며 별도 Partial이나 전역 경고를 만들지 않는다.
- 링크는 색상만으로 구분하지 않는다.

## 서버 API 계약

```text
GET /api/v1/news?page={page}
GET /api/v1/news/{articleId}/{slug}
```

- `page`는 기본 1, 범위 `1..10000`의 leading-zero 없는 단일 정수다. 다른·중복 query는 `400 INVALID_REQUEST`다.
- 목록 응답은 `{ page, nextPage, items }`, item은 canonical `{articleId}/{slug}` `reference`, `title`, `author`, `publishedAt`을 가진다. `nextPage=null`은 마지막 페이지다. `publishedAt`은 source text이며 상대 시간 formatting은 UI가 담당한다.
- Detail은 목록 reference의 두 segment를 그대로 사용하고 `{ reference, title, author, publishedAt, blocks }`를 반환한다. noncanonical reference는 `400 INVALID_REQUEST`다.
- block은 원순서의 `paragraph.content`, `image.imageUrl`/optional `caption`, `list.ordered`/`items` tagged object다. text content는 `text` 또는 `link`이고 link kind는 `TEAM`, `PLAYER`, `EVENT`, `MATCH`, `INTERNAL_UNSUPPORTED`, `EXTERNAL`이다. Team·Player만 routable reference를 가진다.
- `.article-body`만 본문으로 사용하고 script/style, hover/reference card, sidebar, comments를 제외한다. DOM 전체 `text()` 병합으로 구조를 평탄화하지 않는다.
- 제목과 본문은 필수다. image/caption은 선택이고 필수 구조 실패는 `SOURCE_PARSING_FAILURE`다.

대표 fixture는 hover-card 비혼입, Team/Player 링크 분류, image/caption 분리와 list 순서를 검증한다. selector·보정 규칙은 parser 내부에 두고 response에 raw HTML, selector, upstream page URL, external link URL이나 내부 오류를 포함하지 않는다. 본문 image의 검증된 `imageUrl`은 이 금지 대상이 아니다.

## 수용 기준

- [x] 목록·pagination·refresh가 중복 요청과 stale 삽입 없이 동작하고 초기/추가 실패를 구분한다.
- [x] 본문 block의 원순서와 구조를 보존하고 제외 DOM을 혼입하지 않는다.
- [x] Team·Player만 내부 이동하며 제외 링크·embed가 본문을 오염시키지 않는다.
- [x] 선택 콘텐츠 누락은 본문을 유지하고 필수 parsing failure는 Error로 처리한다.
- [ ] Android/iOS 실기기 렌더·접근성을 검증한다.
