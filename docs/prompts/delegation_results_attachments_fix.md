# 위임 기록: 결과 화면 응답 첨부와 상세 링크 (#63 후속)

## 원인

| 원인 | 내용 |
|---|---|
| 목록이 첨부를 그리지 않음 | 결과 화면(`ExplorePage`)의 `ListResult`가 응답 상세를 불러오면서 `attachments`를 받지만 답(`content`)만 그렸다 |
| 상세로 가는 링크가 없음 | T12에서 응답 상세 화면에 다운로드 링크를 넣었지만, 결과 화면 어디에도 그 화면으로 가는 링크가 없었다. 원래 E6·E7이 달 예정이었으나 생기지 않았다 |

## 사람이 정한 답 (2026-11-10)

| 질문 | 답 |
|---|---|
| 상세 링크를 어떻게 열지 | 새 탭으로 연다. 결과 화면의 탐색 질문과 목록이 화면 상태에만 있어서, 같은 탭이면 돌아올 때 사라진다 |

## 구현

- `pages/ResponseFileList.tsx`: 응답 상세와 결과 목록이 함께 쓰는 첨부 목록. `ResponseDetailPage`의 표시를 그대로 옮겼다(문항별, 답 없는 문항의 파일은 아래에 따로, 지운 파일은 이름과 "(삭제된 파일)").
- `ExplorePage.ListResult`: 이미 불러온 `ResponseDetail.attachments`로 문항별 파일을 보이고, 응답마다 `paths.formResponse`로 상세를 새 탭에 연다. 내려받기 주소는 `responseAttachmentUrl`로만 만들고, 가짜 데이터 모드나 `formId`가 없으면 링크 없이 이름만 보인다.
- 서버와 API는 바꾸지 않았다. 새 요청도 없다.

## 회차

| 회차 | 한 일 | 결과 |
|---|---|---|
| 1 | 충돌 확인: 같은 파일을 건드리는 열린 PR 없음, #108 머지됨 | 진행 |
| 2 | 구현과 화면 시험 6건 | lint·typecheck 통과. 전체 실행에서 74건이 5초 시간 초과로 실패 |
| 3 | 원인 확인: 수정 전 main에서도 같은 시간 초과. PC 부하 문제 | 시간 제한 30초로 전체 460건 통과, 관련 파일 5개 51건 통과 |
| 4 | 결함 주입 6건 | 모두 유효 |

## 결함 주입 결과 (`ExplorePage.attachments.test.tsx` 6건, Vitest JSON 결과로 확인)

| 주입 | 기대 시험 | 실패 수 |
|---|---|---|
| 목록에서 첨부를 그리지 않는다 | 첨부는 그 문항의 답 아래에 | 5 |
| 답 없이 파일만 낸 문항의 파일을 뺀다 | 답 없이 파일만 낸 문항의 파일도 | 1 |
| `available: false`에도 링크를 건다 | 지운 파일은 이름과 삭제 표시만 | 1 |
| 링크에 다른 응답의 `response_id`를 넣는다 | 응답마다 자기 응답의 주소로 | 1 |
| 상세 링크를 다른 응답으로 건다 | 응답마다 자기 응답의 주소로 | 1 |
| 가짜 데이터 모드에서도 링크를 건다 | 가짜 데이터 모드에서는 | 1 |

## 사람 화면 확인 (2026-11-11, 로컬 서버와 실제 S3 버킷)

완료 기준의 1~5단계 모두 통과했다: 로그인 응답(파일)·비로그인 응답(파일 없음) 제출, 결과 목록에서 파일 내려받기, 상세 링크가 새 탭으로 열리고 같은 파일 내려받기, 응답 첨부 전체 삭제 뒤 "(삭제된 파일)"만 보이고 링크 없음, 360px에서 가로 스크롤 없음.

첫 제출은 503이었다. 로컬 서버의 S3 자격 증명 문제였고(저장과 정리 모두 실패), 자격 증명을 다시 넣은 뒤 통과했다. 이번 화면 수정과는 관계없다.

## 원본 프롬프트

````text
[구현 내용]
T12(#63) 후속 수정: 결과 화면(ExplorePage)의 응답 목록에 응답 첨부 파일 이름과 다운로드 링크를 보이고, 목록의 응답마다 응답 상세 화면(`/creator/forms/:formId/responses/:responseId`)으로 가는 링크를 단다. 서버는 바꾸지 않는다. 저장소 C:\Users\namhu\claude-practice\Passform, 브랜치 fix/results-attachments(최신 origin/main에서 분기), 셸 Windows PowerShell.

[목표]
제작자가 결과 화면에서 받은 응답의 파일을 바로 내려받고, 응답 한 건을 상세 화면으로 열 수 있다. 지운 파일은 이름과 삭제 표시만 보이고 링크가 없다.

[절대 규칙]
- AGENTS.md 3~5절을 따른다.
- 화면만 고친다. 서버 코드, API, SecurityConfig, DB 스키마와 Flyway, CI, SPEC, ARCHITECTURE, AGENTS, data/seed, tests/harness는 바꾸지 않는다. 서버를 바꿔야만 끝나면 멈추고 묻는다.
- 목록은 이미 불러온 `ResponseDetail.attachments`만 쓴다. 파일 표시를 위해 응답을 다시 부르거나 새 요청을 더하지 않는다.
- 다운로드 주소는 `responseAttachmentUrl`로만 만든다. 주소를 문자열로 직접 쓰지 않는다. 상세 화면 주소는 `paths.formResponse`로만 만든다.
- 파일 표시는 ResponseDetailPage의 방식과 같아야 한다: 문항별로 답 아래에 두고, 답 없이 파일만 낸 문항의 파일도 빠뜨리지 않고, `available: false`면 링크 없이 "(삭제된 파일)"을 붙인다. 두 화면이 같은 규칙을 쓰도록 공통으로 뽑을 수 있고, 그 경우 ResponseDetailPage의 화면 결과는 바뀌면 안 된다.
- 가짜 데이터 모드(`VITE_USE_API`가 `true`가 아님)에서는 HistoryPage처럼 링크 없이 이름만 보인다.
- 제작자 화면에 응답 ID, q1, v1을 쓰지 않는다(AGENTS.md 91행). 링크 문구도 "응답 1"처럼 쓴다.
- 새 의존성을 추가하지 않는다.
- SPEC에 없는 동작은 추측하지 말고, 맥락과 선택지를 적어 객관식으로 묻는다. 예: 상세 링크를 새 탭으로 열지, 상세에서 결과 화면으로 돌아올 때 목록 위치와 질문을 유지할지.
- 같은 실패가 3회차를 넘거나 작업이 2시간을 넘으면 멈추고 보고한다. 작업 트리는 그대로 둔다.

[참조]
- 이슈 #63(닫힘), docs/prompts/delegation_attachments_storage_delete.md, docs/prompts/delegation_results_dashboard.md 72행
- AGENTS.md 3~6절, 화면 규칙 91~94행, 세션 규칙과 공통 파일 97~105행
- docs/SPEC.md 응답 첨부 조회 198행, 첨부 목록 222행, 전체 삭제 224행, 제작자 응답 상세 236행, AC62, AC66, AC70
- frontend/src/의 pages/ExplorePage.tsx(`ListResult`)·ResponseDetailPage.tsx·HistoryPage.tsx, routes/creatorRoutes.tsx·paths.ts, api/client.ts(`responseAttachmentUrl`), types/results.ts·response.ts
- frontend/src/pages/ExplorePage.test.tsx, ExplorePage.presets.test.tsx
- frontend/docs/ROUTES_AND_API.md 주 4

[완료 기준]
- AGENTS.md 6절을 충족한다.
- 화면 테스트(Vitest):
  - 목록의 응답에 첨부가 있으면 해당 문항의 답 아래에 파일 이름과 `responseAttachmentUrl` 링크가 보인다.
  - 답 없이 파일만 낸 문항의 파일도 보인다.
  - `available: false` 파일은 이름과 "(삭제된 파일)"만 보이고 링크가 없다.
  - 첨부가 없는 응답은 지금과 같게 보인다.
  - 응답마다 상세 링크가 `paths.formResponse(formId, responseId)`로 걸리고 화면 문구에 응답 ID가 없다.
  - 가짜 데이터 모드에서는 다운로드 링크가 없다.
  - 기존 ExplorePage·ResponseDetailPage·HistoryPage 테스트가 그대로 통과한다.
- 실제 화면 확인을 사람에게 요청하고 결과를 기록한다. 요청은 한 번에 끝나도록 아래 흐름 그대로, 누를 곳까지 적어서 보낸다. 시험 데이터 스크립트는 에이전트가 준비하되 승인 없이 개발 DB나 실제 버킷에 실행하지 않는다.
  1. 파일을 받는 폼에 로그인 응답 1건(파일 있음)과 비로그인 응답 1건(파일 없음)을 제출한다.
  2. 제작자로 결과 화면 → 탐색에서 응답 목록이 나오는 요청을 보낸다. 첫 응답의 답 아래 파일 이름을 눌러 내려받는다.
  3. 같은 응답의 상세 링크를 눌러 상세 화면이 열리고 같은 파일을 내려받을 수 있는지 본다. 뒤로 가기로 결과 화면에 돌아온다.
  4. 결과 화면에서 응답 첨부 전체 삭제를 실행한 뒤 2단계를 다시 해서 "(삭제된 파일)"만 보이고 링크가 없는지 본다.
  5. 360px 폭에서 2~3단계를 가로 스크롤 없이 할 수 있는지 본다.
- 결함 주입: 아래를 하나씩 넣었을 때 테스트가 실패해야 한다. 기대한 테스트가 실제로 실행됐는지 Vitest 결과로 확인하고, 실행되지 않았으면 판정 무효로 다시 돌린다. 주입은 커밋하지 않고, 항목마다 되돌린다.
  - 목록에서 첨부를 그리지 않는다
  - 답 없이 파일만 낸 문항의 파일을 뺀다
  - `available: false`에도 링크를 건다
  - 링크에 다른 응답의 `response_id`를 넣는다
  - 상세 링크를 빼거나 다른 응답으로 건다
  - 가짜 데이터 모드에서도 링크를 건다
- 위임 기록을 docs/prompts/delegation_results_attachments_fix.md에 남긴다. 넣을 것: 이 프롬프트 원본, 원인(목록이 첨부를 그리지 않음, 상세로 가는 링크가 없음), 회차 표, 결함 주입 결과, 사람이 정한 답.

[범위 밖]
- 서버의 첨부·다운로드 동작
- 결과 대시보드(ResultsPage)의 통계 화면
- 응답 상세 화면 자체의 변경(공통으로 뽑는 정리 제외)
- 탐색 질문과 분석 동작

[깃허브 규칙]
- 사람의 명시적 승인을 받은 뒤에만 커밋·push·PR을 한다. 승인 요청에는 `git status --short`, `git diff --stat`, 핵심 diff, 테스트 집계, 제안 커밋 메시지를 보여 준다. 커밋 승인은 push 승인이 아니다.
- 커밋은 AGENTS.md 5절 협업 항목과 README.md를 따른다. 공통 컴포넌트를 새로 뽑으면 별도 커밋으로 나눈다. `git add`는 경로를 명시한다.
- PR 제목: `[fix] 결과 화면 응답 첨부와 상세 링크 (#63)`. 본문은 README.md를 따르고 관련 이슈는 `Closes` 없이 `#63`으로 적는다.
- 리뷰어는 gh pr create --reviewer ye-nni로 지정한다.
- main에 직접 push하거나 머지하지 않는다.

[보고]
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 테이블 표 / 추가한 의존성과 이유 / 지시와 다르게 한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
- 테스트마다 통과 이유를 코드 위치와 함께 한 줄로 적는다.
````
