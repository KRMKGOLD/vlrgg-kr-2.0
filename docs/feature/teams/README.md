# Team 기능 기획

## 목적과 사용자 가치

Team Detail은 사용자가 팀의 정체성, 예정·최근 경기, 현재 로스터, 관련 뉴스를 한 화면에서 확인하고 연결된 Match, Player, News로 탐색하게 한다. 정보가 적은 일회성 팀도 실제로 존재하는 범위만 정직하게 보여준다.

## 1차 MVP 범위

- 팀 기본 정보
- Upcoming Matches
- Recent Matches
- Current Roster의 Players와 Staff
- 관련 News
- Team 즐겨찾기 등록 및 해제
- 각 콘텐츠에서 Match Detail, Player Detail, News Detail로 이동

Team 즐겨찾기는 기기 로컬에만 저장하며 알림 구독을 만들지 않는다.

## 명시적 제외 범위

- Team 알림
- Transactions 전체 이력
- 팀 상세 Stats
- Ranking History
- Team Detail에서 Event Detail로 직접 이동하는 기능
- 계정 기반 동기화와 기기 간 즐겨찾기 공유

## 진입과 이탈 경로

### 진입

- Search의 Team 결과
- News 본문의 지원되는 내부 Team 링크
- Match Detail 또는 Match 목록의 Team 항목
- MyPage의 즐겨찾기 Team

### 이탈

- Upcoming/Recent Match 선택 → Match Detail
- Current Roster의 Player 선택 → Player Detail
- News 선택 → News Detail
- Back → 직전 화면과 해당 화면의 상태

Team Detail에서는 Event Detail로 직접 이동하지 않는다.

## 화면과 콘텐츠 계층

1. Top App Bar
   - Back
   - 화면 제목 또는 팀 이름
2. Team header
   - 팀 로고 또는 안정적인 placeholder
   - 팀 이름
   - 팀 태그와 지역 등 제공 가능한 기본 정보
   - 즐겨찾기 토글
3. Upcoming Matches
4. Recent Matches
5. Current Roster
   - Players
   - Staff
6. News

섹션이 비어 있으면 화면 전체를 실패로 처리하지 않고 해당 섹션의 빈 상태를 표시한다. 팀 이름과 즐겨찾기 동작은 화면의 가장 중요한 식별·개인화 요소다.

## 표시 데이터

| 영역 | 표시 데이터 |
| --- | --- |
| Team header | Team ID, 이름, 태그, 로고, 지역 등 upstream에서 확인 가능한 기본 정보 |
| Match | Match ID, 예정/완료 시각, 상태, 상대 팀, 스코어 또는 진행 정보, 이벤트 문맥 |
| Player | Player ID, handle, 실명·국적·역할 등 제공 가능한 기본 정보 |
| Staff | 이름, 역할 등 제공 가능한 기본 정보 |
| News | News ID, 제목, 작성자, 작성 시각 등 News 목록 계약의 요약 정보 |
| Favorite | 현재 Team의 로컬 즐겨찾기 여부 |

필드가 source에 존재하지 않는 경우를 빈 문자열이나 임의 값으로 대체하지 않는다. 세부 optionality는 서버 응답 계약을 설계할 때 fixture로 확정한다.

## 화면 상태

| 상태 | 동작 |
| --- | --- |
| Loading | header와 주요 섹션의 안정적인 skeleton을 표시한다. |
| Populated | 존재하는 기본 정보와 섹션을 계층에 맞게 표시한다. |
| Partial | 일부 섹션만 누락되면 성공한 섹션을 유지하고 누락 섹션을 별도로 안내한다. |
| Empty section | Match, Roster, News가 없으면 섹션별 명시적 빈 상태를 표시한다. |
| Error | Team Detail 자체를 불러오지 못하면 일반화된 오류와 재시도 동작을 표시한다. raw exception이나 파서 정보를 노출하지 않는다. |
| Stale | 앱이 이전 데이터를 유지해 표시하도록 구현하는 경우 마지막 갱신 시각과 오래된 데이터임을 명시한다. silent stale fallback은 사용하지 않는다. |

존재하는 정보가 적은 팀은 오류가 아니라 정상적인 부분/빈 콘텐츠로 처리한다.

## 사용자 인터랙션

- 즐겨찾기 토글을 누르면 해당 Team을 로컬 즐겨찾기에 추가하거나 제거한다.
- 즐겨찾기 변경은 즉시 화면과 MyPage에 일관되게 반영한다.
- 즐겨찾기 등록은 notification permission을 요구하거나 서버 알림 구독을 만들지 않는다.
- Match, Player, News 항목을 누르면 대응하는 Detail로 이동한다.
- 오류 상태의 재시도를 통해 Team Detail을 다시 요청할 수 있다.

## 앱·서버 책임 경계

### 앱

- 서버 Response를 app remote DTO로 역직렬화하고 Domain Model로 매핑한다.
- Team 즐겨찾기를 로컬 persistence에 저장하고 MyPage와 상태를 공유한다.
- 화면 상태, 날짜/시간 표시, 섹션 구성, navigation callback을 관리한다.
- Team 즐겨찾기와 notification 상태를 연결하지 않는다.

### 서버

- Team 페이지를 요청 시점에 수집하고 `Scraper → Parser → SourceModel → Mapper → Response` 경계를 지킨다.
- 기본 정보, 경기, 로스터, 뉴스 데이터를 앱에 적합한 응답으로 가공한다.
- upstream DOM 구조, selector, 원본 HTML, 내부 오류를 앱 응답에 노출하지 않는다.
- 일반 조회 실패 시 이전 결과를 성공 응답으로 반환하는 stale fallback을 사용하지 않는다.

## Upstream URL 및 파서 메모

제품 화면 계약과 분리해 parser fixture 선정에만 사용한다.

```text
https://www.vlr.gg/team/8185/kiwoom-drx
https://www.vlr.gg/team/19296/team-korea
```

- 일반적인 활동 팀과 이력이 적은 일회성 팀을 각각 fixture로 검증한다.
- 섹션 존재 여부와 필드 누락을 DOM 위치만으로 동일시하지 않는다.
- selector, 원본 HTML, canonical upstream URL은 public response나 client log에 노출하지 않는다.

## 테스트 가능한 수용 기준

- [ ] Team Detail은 기본 정보, Upcoming Matches, Recent Matches, Players, Staff, News를 서로 구분해 표시한다.
- [ ] 섹션 하나가 비어도 다른 성공 섹션은 계속 표시된다.
- [ ] 정보가 적은 일회성 팀이 전체 오류로 잘못 처리되지 않는다.
- [ ] Match, Player, News 항목은 각각 올바른 Detail로 이동한다.
- [ ] Team Detail에는 Event로 직접 이동하는 인터랙션이 없다.
- [ ] 즐겨찾기 등록 후 Team이 MyPage의 Team 그룹에 나타나고, 제거 후 사라진다.
- [ ] Team 즐겨찾기 등록·해제는 서버 notification subscription을 생성하거나 변경하지 않는다.
- [ ] loading, empty section, partial, error, stale 상태가 유효 콘텐츠와 시각적으로 구분된다.
- [ ] 서버 parser test는 일반 팀과 이력이 적은 팀 fixture를 모두 검증한다.

## 열린 결정

- Team 기본 정보에서 확정적으로 노출할 필드와 각 필드의 optionality는 실제 HTML fixture 분석 및 서버 응답 계약 작성 시 확정한다.
- Team 즐겨찾기 persistence 기술은 구현 단계에서 로컬 데이터 형태와 조회 요구에 맞춰 선택한다.
