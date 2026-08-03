# News

## 문서 역할

이 문서는 News List와 News Detail의 제품 요구사항을 정의한다. 공통 시각 언어와 접근성은 루트 [`DESIGN.md`](../../../DESIGN.md), 전체 기능 관계와 내비게이션은 상위 [`docs/feature/README.md`](../README.md)를 따른다.

## 구현 상태 (2026-07-22)

- **Backend: 구현 완료.** `GET /api/v1/news`와 `GET /api/v1/news/{articleId}/{slug}`는 route, scraper, parser, mapper, response 및 fixture/route 테스트로 구현되어 있다.
- **App: 미구현.** 목록·상세 Compose UI, remote DTO/Domain 매핑, pagination 상태, 본문 렌더링과 내부 링크 navigation은 아직 구현되어 있지 않다.

## 목적과 사용자 가치

- 최신 Valorant eSports 뉴스를 모바일에서 빠르게 훑고 앱 안에서 읽을 수 있게 한다.
- 기사에 등장하는 Team과 Player를 관련 상세 화면으로 연결해 단순 뉴스 열람을 탐색의 시작점으로 만든다.
- VLR.GG의 웹 문서 구조를 앱에 노출하지 않고, 읽기 순서가 명확한 모바일 콘텐츠로 변환한다.

## MVP 범위

### News List

- 최신순 뉴스 목록
- 페이지 기반 추가 로딩
- 뉴스 제목, 작성자, 작성 시각
- thumbnail/card 없이 divider로 구분한 flat full-row
- 항목 선택을 통한 News Detail 이동

### News Detail

- 제목, 작성자, 작성 시각
- 문단형 본문
- 이미지와 캡션
- 순서가 있는 목록과 순서가 없는 목록
- 본문 링크의 타입 구분
- Team 및 Player 링크의 앱 내부 상세 이동

## 제외 범위

- Twitch, X(Twitter) 등 iframe 또는 embed 콘텐츠의 앱 내 재생
- 본문 내 Event 및 Match 링크의 내부 화면 연결
- 외부 웹 링크의 MVP 내 별도 브라우징 정책
- 댓글, 포럼, AI 요약, 오프라인 기사 보관
- 서버의 이전 성공 응답을 이용한 stale fallback

제외된 링크나 embed는 본문을 오염시키지 않아야 한다. 지원하지 않는 콘텐츠를 임의 텍스트로 풀거나 유효한 내부 링크처럼 표현하지 않는다.

## 진입과 이탈 내비게이션

### 진입

- Bottom navigation의 `News` 탭에서 News List로 진입한다.
- News는 최상위 탭이므로 공통 Top App Bar와 Search 액션을 제공한다.

### 이탈

- News List의 항목을 누르면 News Detail을 push한다.
- News Detail의 Team 링크는 Team Detail로, Player 링크는 Player Detail로 이동한다.
- Back은 직전 화면으로 돌아가며, News List의 로딩된 페이지와 스크롤 위치를 보존한다.
- Search를 열었다가 Back으로 돌아오는 경우에도 기존 News 상태를 보존한다.

News Detail은 bottom-navigation 목적지가 아니다. Event 및 Match 내부 이동은 MVP에서 제공하지 않는다.

## 화면 및 콘텐츠 계층

### News List

1. Top App Bar: 화면 제목, Search 액션
2. 최신 뉴스 목록
3. 각 divider row 전체가 News Detail target이며 제목을 우선하고 작성자와 게시 시각을 보조 정보로 표시
4. 다음 페이지 로딩 또는 페이지 로딩 실패 표시

### News Detail

1. Back-only Top App Bar
2. 작성자와 작성 시각
3. 원문 순서를 보존한 본문 block
4. 문단, 이미지/캡션, 목록, 링크가 포함된 텍스트

본문은 HTML 문자열이나 WebView 전체 페이지가 아니라 구조화된 콘텐츠 block으로 표현한다. 이미지가 없어도 글의 읽기 순서와 의미가 유지되어야 한다.

## 표시 데이터와 선택성

### News summary

- 기사를 안정적으로 다시 식별할 수 있는 식별자 또는 canonical reference
- 제목
- 작성자
- 작성 시각

### News article

- summary의 기본 정보
- 순서를 가진 article block 목록
- 텍스트 block
- 이미지 block과, 원문에 존재하는 경우의 캡션
- list block과 각 항목
- 링크 label과 링크 분류 정보

제목과 본문은 정상적인 기사에 필수다. 캡션은 원문에 없을 수 있다. 이미지 또는 일부 지원 불가 block이 없다는 이유만으로 기사 전체를 실패시키지는 않되, 필수 본문 구조를 해석하지 못하면 parsing failure로 처리한다. UI 표시용 상대 시간 문자열은 Domain Model이 아니라 UI에서 생성한다.

## 화면 상태

### News List

- `Loading`: 최초 목록을 불러오는 중이며 skeleton으로 목록의 구조를 예고한다.
- `Populated`: 한 개 이상의 뉴스가 최신순으로 표시된다.
- `Empty`: 정상 응답이지만 표시할 뉴스가 없다. 로딩 화면과 구분되는 안내를 표시한다.
- `Pagination error`: 기존 페이지는 유지되지만 다음 페이지 요청이 실패했다. 목록 전체를 오류 화면으로 대체하지 않고 하단에서 재시도를 제공한다.
- `Error`: 최초 요청이 실패해 표시할 데이터가 없다. 안전한 일반 오류 문구와 재시도를 제공한다.
- `Stale`: MVP는 이전 서버 성공 응답을 최신 데이터처럼 반환하지 않는다. 향후 앱이 이전 데이터를 유지한다면 갱신 실패와 마지막 확인 시점을 명시하기 전에는 `Populated`로 표현할 수 없다.

### News Detail

- `Loading`: 제목/본문의 안정적인 skeleton을 표시한다.
- `Populated`: 필수 기사 정보와 해석된 본문을 순서대로 표시한다.
- `Partial`: 필수 본문은 읽을 수 있으나 선택적 이미지, 캡션 또는 지원하지 않는 embed가 누락된 상태다. 누락된 block 때문에 주변 본문 순서가 깨지지 않아야 한다.
- `Empty`: 유효한 기사 응답이지만 본문이 비어 있다. 정상 기사처럼 표시하지 않는다.
- `Error`: 네트워크 또는 필수 본문 parsing 실패로 기사를 표시할 수 없다.
- `Stale`: News List와 동일하게 MVP의 서버 stale fallback은 없다.

## 사용자 인터랙션

- 뉴스 행 선택: News Detail 이동
- 목록 끝 접근: 다음 페이지가 있으면 중복 없이 한 번만 추가 요청하고 footer spinner를 표시
- 페이지 로딩 실패 재시도: 실패한 페이지부터 footer action으로 다시 요청
- Pull-to-refresh: 기존 목록을 초기화하고 `page=1`만 요청하며 성공 시 page 1 결과로 교체
- Team/Player 본문 링크 선택: 해당 앱 상세 화면 이동
- 뒤로가기: 직전 화면과 News List 상태 복원
- Search 선택: 현재 화면 위에 Search Screen push

중복된 pagination 요청으로 동일 항목이 반복 삽입되지 않아야 한다. initial loading/empty/initial error/pagination error를 구분하고 initial error는 전체 화면 Retry, pagination error는 하단 Retry로 복구한다. Pull-to-refresh 중 중복 요청을 막고, 링크는 색상만으로 구분하지 않는다.

## 앱과 서버 책임 경계

### 서버

- 요청 시점에 대상 VLR.GG 문서를 가져온다.
- `Scraper → Parser → SourceModel → Mapper → Response` 경계를 유지한다.
- News List의 페이지 정보를 앱 친화적인 summary로 가공한다.
- News Detail의 본문을 순서가 보존된 block으로 변환하고 링크를 분류한다.
- DOM selector, Jsoup type, hover card, raw HTML을 public response에 노출하지 않는다.
- upstream 통신 실패와 필수 구조 parsing 실패를 각각 `UPSTREAM_NETWORK_FAILURE`, `SOURCE_PARSING_FAILURE`로 안전하게 반환한다.

### 앱

- remote DTO를 app Domain Model로 매핑한다.
- pagination 상태, 화면 상태, 스크롤/내비게이션 복원을 관리한다.
- article block을 Compose UI로 렌더링한다.
- Team/Player 링크를 Screen callback으로 내부 내비게이션에 연결한다.
- raw exception, HTTP code, server 내부 메시지를 사용자에게 노출하지 않는다.

## Upstream 및 parser 메모

이 절은 제품 동작이 아니라 구현 시 검증할 외부 문서 가정이다.

### URL 예시

```text
https://www.vlr.gg/news
https://www.vlr.gg/news/?page=2
```

News Detail의 실제 canonical URL은 News List에서 얻은 기사 reference를 사용한다.

## Server API v1 contract

News 구현은 아래 versioned JSON contract를 사용한다. `publishedAt`은 source가 제공한 게시 시각 텍스트이며, 상대 시간 같은 UI formatting은 앱에서 처리한다. raw HTML, CSS selector, Jsoup type, upstream page URL, 외부 링크 URL, 내부 오류는 response에 포함하지 않는다.

### News List

`GET /api/v1/news?page={page}`

- `page`는 생략하면 `1`이며, `1`부터 `10000`까지의 leading zero 없는 정수 하나만 허용한다. 다른 query parameter나 중복 parameter는 `INVALID_REQUEST`다.
- 성공 response는 `{ page, nextPage, items }`다. `nextPage`는 다음 페이지가 없으면 `null`이다.
- 각 item은 `{ reference, title, author, publishedAt }`다. `reference`는 `{articleId}/{slug}` 형식의 canonical relative reference다.

```json
{
  "page": 1,
  "nextPage": 2,
  "items": [
    {
      "reference": "101/champions-run",
      "title": "Champions run",
      "author": "raezeri",
      "publishedAt": "July 13, 2026"
    }
  ]
}
```

### News Detail

`GET /api/v1/news/{articleId}/{slug}`

- `articleId`와 `slug`는 List가 반환한 `reference`의 두 segment를 그대로 사용한다. 둘 중 하나라도 canonical 형식이 아니면 `INVALID_REQUEST`다.
- 성공 response는 `{ reference, title, author, publishedAt, blocks }`다. block은 원문 순서를 유지하는 tagged object다: `paragraph`의 `content`, `image`의 `imageUrl`/선택 `caption`, `list`의 `ordered`/`items`.
- paragraph와 list item의 content는 `text` 또는 `link` tagged object다. link의 `kind`는 `TEAM`, `PLAYER`, `EVENT`, `MATCH`, `INTERNAL_UNSUPPORTED`, `EXTERNAL` 중 하나다. Team과 Player만 app-routable `reference`를 가지며, Event·Match는 type만 보존하고 MVP에서 route하지 않는다.

모든 비성공 response는 공통 `{ code, message }` envelope를 사용한다. invalid page/reference는 `400 INVALID_REQUEST`, transport failure는 `502 UPSTREAM_NETWORK_FAILURE`, 필수 DOM structure failure는 `502 SOURCE_PARSING_FAILURE`다.

### News Detail parsing 경계

- 기사 본문의 기준 영역은 `.article-body`다.
- `style`, `script`, `.wf-hover-card`, `.article-ref-card`, hidden hover-card text, sidebar, comments는 본문에서 제외한다.
- 단순 `text()` 병합으로 DOM 전체를 평탄화하지 않고 문단, 이미지/캡션, 목록, 링크의 원래 순서를 보존한다.
- `/team/{id}/{slug}`는 Team, `/player/{id}/{slug}`는 Player로 분류하고 MVP에서 내부 라우팅한다.
- Event, Match 및 외부 URL은 타입을 잃지 않되 MVP 내부 라우팅 대상으로 취급하지 않는다.
- 최소 HTML fixture로 hover-card 텍스트 비혼입, Team/Player 링크 분류, 이미지/캡션 분리, bullet list 순서를 검증한다.

VLR.GG DOM은 불안정한 외부 contract다. selector나 보정 규칙은 parser 내부에만 두며 이 제품 문서에 고정하지 않는다.

## 검증 가능한 수용 기준

- [ ] News 탭에서 thumbnail/card 없이 divider full-row로 제목, 작성자, 게시 시각이 포함된 최신 뉴스 목록을 확인할 수 있다.
- [ ] 목록 끝에 도달하면 다음 페이지가 중복 없이 추가되고 기존 항목과 스크롤 상태가 유지된다.
- [ ] 다음 페이지 로딩만 실패하면 기존 목록이 남고 실패한 페이지를 재시도할 수 있다.
- [ ] 뉴스 항목을 누르면 제목, 작성자, 작성 시각과 순서가 보존된 본문이 표시된다.
- [ ] 문단, 이미지/캡션, bullet/numbered list가 서로 섞이지 않고 원문의 읽기 순서대로 렌더링된다.
- [ ] hover card, reference card, script, style, sidebar, comments의 텍스트가 기사 본문에 포함되지 않는다.
- [ ] Team 링크는 Team Detail로, Player 링크는 Player Detail로 이동한다.
- [ ] Back으로 News List에 돌아오면 이전 페이지와 스크롤 위치가 복원된다.
- [ ] initial loading, empty, initial error, pagination error가 서로 구분되고 initial은 전체 화면 Retry, pagination은 하단 Retry를 제공한다.
- [ ] Pull-to-refresh는 데이터를 초기화한 뒤 `page=1`만 요청하고 성공 시 기존 목록을 교체한다.
- [ ] 서버 response와 앱 모델에 raw HTML, Jsoup type, CSS selector가 노출되지 않는다.
- [x] parser fixture가 hover-card 비혼입, 내부 링크 분류, 이미지/캡션, 목록 처리를 검증한다.

## 열린 결정

현재 News MVP 구현을 막는 제품 결정은 없다. 구현 착수 시 대표 기사 canonical URL fixture와 이미지 로딩 실패 표현을 확정하되, 이는 위 범위를 변경하지 않는다.
