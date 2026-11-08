# 내 폼함과 제출 이력 위임 기록 (#43)

사람이 검토해 발행.

## §1 위임 프롬프트 원본

작성: 사람. 2026-11-08 실행.

```text
이번 작업은 이슈 #43(E5 내 폼함과 제출 이력)뿐이다. 다른 이슈는 구현하지 않는다. 나는 ye-nni이고 리뷰어는 Psoyeon1117이다. 셸은 Windows PowerShell이다.

# 목표
로그인 사용자가 저장한 폼과 제출한 폼을 내 폼함에서 보고(최근 저장순 기본, 마감 임박순·마감 늦은 순·제목순, 탭 전체·작성 중·미제출·마감됨), 제출 이력에서 제출 당시 버전의 문구로 자기 답을 다시 본다. 서버 API와 화면 연결을 함께 끝내고 실제 화면에서 확인한다.

# 비용 규칙 (주간 한도가 빠듯하다. 지킨다)
- 서브에이전트를 띄우지 않는다. SPEC, ARCHITECTURE 전체를 읽지 않는다. 아래 지정한 곳만 읽는다.
- 새 라이브러리를 더하지 않는다. 디자인을 다듬지 않는다. 기존 컴포넌트와 CSS를 쓴다.
- 일반 테스트는 아래 목록만 쓴다. 단계(서버 → 화면 → 기록)가 끝날 때마다 로컬에 커밋한다.
- 위임 기록과 PR 본문은 짧게, 최종 보고는 40줄 안팎으로 쓴다.

# 시작 확인 (하나라도 어긋나면 멈추고 알린다)
1. git status로 추적 파일 변경이 없는지 본다. 미추적 docs/SPEC_followups.md는 정상이다.
2. git fetch origin, git switch main, git merge --ff-only origin/main. main에 PR #85(응답 초안)가 들어 있는지 git log로 본다.
3. main에서 브랜치 feat/my-forms-history를 만든다.
4. docker compose up -d db 뒤 .\gradlew.bat test --tests "*AccessGoldenTest"를 돌려 other_users_response_404가 "라우트 미구현"으로 skip되는지 본다(지금 상태 기록).
5. frontend에서 npm run lint, npm run typecheck, npm test가 통과하는지 본다. 테스트는 VITE_USE_API=false로 돌린다(frontend/.env.local이 VITE_USE_API=true라 Vitest도 읽는다). npm ci는 돌리지 않는다. 의존성 오류가 나면 5173의 Vite를 사람이 끈 뒤에만 npm ci를 돌리고, 끌 수 없으면 멈춰 묻는다.

# 먼저 읽을 것 (이곳만)
AGENTS.md(특히 5절 "내 폼함" 정렬과 "제출 이력" 정렬), gh issue view 43, docs/SPEC.md 39행(탭 정의), 189~200행(API 표의 /me/responses, /me/saved-forms, /me/forms), AC2·AC15·AC17·AC68(내용으로 찾는다), tests/harness/golden_cases.yaml의 other_users_response_404, tests/java/com/opinio/passform/golden/AccessGoldenTest.java, data/seed/forms/form_b_mixed.json의 rb01, src/main/java/com/opinio/passform의 domain/inbox, domain/response/Response.java, service/DraftService.java·SubmissionService.java, controller/DraftController.java(형식 참고), frontend/src의 api/client.ts·types/response.ts·pages/InboxPage.tsx·HistoryPage.tsx·demo/demoRoutes.ts·routes/respondentRoutes.tsx, docs/prompts/delegation_response_draft.md 앞부분(기록 형식).

# 절대 규칙
- 기존 테스트(src/test, tests/java, frontend/src/**/*.test.*)의 단언과 기대값을 고치지 않는다. 판정 기준(tests/harness, data/seed, 두 스키마 폴더, src/response_analysis/prompts, config/rag.yaml, evals/evalset.jsonl)을 고치지 않는다. deny를 셸로 우회하지 않는다. 아래 "사람이 미리 승인한 기존 파일 변경" 두 가지만 예외다.
- 접근: 모든 /me API는 로그인 필수(없으면 401). 남의 응답·남의 내 폼함은 있는지도 알리지 않고 404(AC2). 응답 본문에 남의 답을 넣지 않는다.
- 인증 우회를 넣지 않는다(X-Demo-User 같은 것 금지). 키와 비밀값을 파일, 커밋, 출력, VITE_ 변수에 넣지 않는다.
- 현재 시각은 주입한 Clock으로만 얻는다. closed는 서버가 계산한다(Form.status가 closed이거나 now ≥ deadline).
- controller → service → repository / domain 계층, 엔티티를 응답에 직접 쓰지 않는다. 새 Flyway 파일이 필요하면 만들기 전에 멈추고 묻는다(지금 V1·V2뿐이고 saved_forms 테이블은 이미 있다).
- TypeScript strict, any 금지. API 타입은 SPEC 표의 필드 이름 그대로. 화면에 response_id, form_id, v1, 오류 코드 원문을 쓰지 않는다. 버전은 "폼 버전 1"처럼 쓴다.
- 커밋·PR에 에이전트 이름이나 생성 문구를 넣지 않는다. PR 본문에 사람을 @로 언급하지 않는다. _local/과 docs/SPEC_followups.md는 커밋하지 않는다. git add는 경로를 명시하고 스테이징 목록을 확인한 뒤 커밋한다.

# 사람이 미리 승인한 기존 파일 변경 (이 범위를 넘으면 멈추고 묻는다)
1. tests/java/.../golden/AccessGoldenTest.java: 경로의 시드 응답 ID(예: rb01)를 UUID로 바꾸고, 그 응답을 fixture 내용 그대로 actor가 아닌 사용자 소유로 저장소(모의 객체)에 두는 준비 코드만 더한다. must_not_contain 검사가 실제로 그 응답 원문을 대상으로 의미가 있게 한다. YAML, 기대값, 단언, skip 조건과 메시지는 바꾸지 않는다.
2. frontend/src/App.routes.test.tsx의 '가짜 값이 남은 화면에는 띠를 보이고…' 테스트(340행 근처): 내 폼함에서 가짜 값을 걷어 내 demoRoutes에서 빼면, 이 테스트의 첫 경로 '/me/forms'만 아직 가짜 값이 남는 '/me/profile'로 바꾼다. 단언 문구는 그대로.
그 밖의 기존 테스트가 깨지면 고치지 말고 멈춰 묻는다.

# 구현 범위
서버
- POST /me/saved-forms {form_id}: 배포된(초안 아닌) 폼만. 처음이면 saved로 넣고 201, 이미 있으면 200(saved_at 그대로). 남의 초안이나 없는 폼은 404.
- AC17 연결: 로그인 사용자의 제출(직접 제출과 초안 제출 모두)이 성공하면 같은 트랜잭션에서 내 폼함 항목을 submitted로 만든다. 없으면 saved_at=제출 시각으로 넣는다. 항목은 폼당 하나.
- GET /me/forms: SPEC 200행 필드 그대로(form_id, title, deadline, state, has_draft, saved_at, submitted_at, closed). has_draft는 그 사용자의 in_progress 응답이 있는지, submitted_at은 그 사용자의 submitted 응답 중 가장 최근 submitted_at. 정렬은 saved_at 내림차순 → form_id 오름차순.
- GET /me/responses: 본인의 submitted 응답만, SPEC 192행 필드. 정렬은 submitted_at 내림차순 → response_id 오름차순.
- GET /me/responses/{response_id}: 본인 것만, 남의 것·없는 것·작성 중인 것은 404. 문항과 문구는 그 응답의 form_version 정의에서 가져온다(AC15). editable·cancellable은 폼 설정 값을 그대로 준다(동작은 T10).
화면
- 내 폼함(InboxPage): 가짜 값을 모두 걷어 내고 GET /me/forms로 그린다. 정렬 선택(최근 저장순 기본, "기본순"이라고 쓰지 않음, 마감 임박순, 마감 늦은 순, 제목순)과 탭 네 개는 AGENTS 5절·SPEC 39행 정의대로 화면에서 계산한다. 카드를 누르면 /forms/:formId로 연다. 요약 숫자 칸은 목록에서 계산한 값으로 바꾸거나 뺀다. 빈 목록·401·오류 안내를 둔다.
- 제출 이력(HistoryPage와 상세): GET /me/responses, GET /me/responses/{id}로 그린다. 상세는 제출 당시 문항 문구와 답, 제출 시각(Asia/Seoul)을 보인다. 첨부는 파일 이름만 보이고 다운로드는 하지 않는다.
- 응답 화면 머리에 "내 폼함에 저장" 버튼: 로그인 사용자에게만 보이고, 누르면 POST 뒤 "내 폼함에 저장했어요"를 보인다. 응답 화면의 다른 동작은 바꾸지 않는다.
- demoRoutes에서 가짜 값을 모두 걷어 낸 경로(myForms, myResponses, myResponse)를 뺀다.

# 이 PR에서 하지 않는다 (미룸)
제출 후 수정·취소와 그에 따른 saved 복귀(T10), 알림·마감 알림(E12), 응답 첨부 다운로드, 폼·받은 응답 삭제 뒤 조회 전용 이력의 화면(T12, 서버는 historyFormTitle이 있으면 쓰기만), AC17의 골든(save_form_once 등, 사람이 아직 쓰지 않음).

# 일반 테스트 (이것만)
서버: 저장 201·200·남의 초안 404 / 제출 성공 뒤 내 폼함 submitted(저장 안 했던 폼, 이미 saved였던 폼 각 1건, 초안 제출 1건) / GET /me/forms의 정렬·has_draft·closed(고정 Clock, deadline 지남 1건) / GET /me/responses 정렬과 작성 중 제외 / 상세가 옛 버전 문구를 돌려줌 / 남의 응답 404와 본문에 원문 없음 / 로그인 없음 401.
화면: 탭 네 개 분류(SPEC 39행 표의 경계값: 작성 중이면서 제출됨, 마감됐는데 작성 중), 세 가지 정렬과 같을 때 form_id 순, 이력 상세가 제출 버전 문구를 그림, 저장 버튼이 비로그인일 때 안 보임.
여기 없는 경우가 필요해 보이면 더하지 말고 PR에 "시험하지 않음"으로 적는다.

# 사람 확인 (push 전에 멈춘다)
자동 검사가 통과하면 push하지 말고 멈춰 아래를 사람에게 안내한다. 백엔드는 이 PC의 개발 DB 사정 때문에 DB_NAME=passform_e3로 띄운다(public을 건드리지 않는다). 5173에 이미 Vite가 떠 있으면 그대로 쓴다. 각 항목에 "무엇을 누르면 무엇이 보여야 통과"를 버튼과 탭의 실제 이름으로 적는다. 에이전트는 로그인 정보를 입력하지 않는다. 필요한 폼(마감 지난 폼, 마감 없는 폼 등)은 사람이 빌더로 만들도록 순서를 적어 준다.
1. 남의 폼을 열어 "내 폼함에 저장"을 누르면 내 폼함 맨 위(최근 저장순)에 나오고, 탭 "미제출"에 들어간다.
2. 다른 폼에 답을 쓰다 나가면 그 폼이 "작성 중" 탭에 나오고, 누르면 이어 쓰기가 된다.
3. 저장하지 않은 폼에 바로 제출하면 내 폼함에 들어오고, 탭 "작성 중"·"미제출"·"마감됨"에는 없고 "전체"에만 있다.
4. 마감이 지난 폼은 "마감됨" 탭에만 나온다.
5. 정렬을 마감 임박순·마감 늦은 순·제목순으로 바꾸면 순서가 AGENTS 5절대로 바뀐다.
6. 제출 이력에서 제출한 응답을 열면 내 답이 보이고, 제작자가 문항 문구를 바꿔 다시 배포한 뒤에도 옛 문구로 보인다.
7. 내 폼함과 제출 이력에 "가상 값이 남아 있습니다" 띠가 없다. 휴대폰 너비(개발자 도구)에서 가로 스크롤이 없다(AC68).
화면 수정 의견이 오면 이 범위 안에서만 고친다. 다른 담당의 화면(응답 화면 등)을 바꾸게 되면 객관식으로 묻고 PR 본문에 적는다.

# 완료 기준
- .\gradlew.bat cleanTest build 통과, AccessGoldenTest에서 other_users_response_404가 skip 0으로 통과. 테스트 건수와 skip 수를 XML(build/test-results)에서 세어 보고한다.
- frontend에서 npm run lint, npm run typecheck, npm test 통과. 건수를 보고한다.
- 사람 확인 7개 항목의 결과를 보고한다.
- git diff origin/main...HEAD --name-only가 허용 범위뿐이다(src/main, 새 src/test 파일, 위 승인한 두 기존 테스트 파일, frontend/src, docs/prompts의 새 기록). 커밋 뒤 git status --short에 docs/SPEC_followups.md만 남는다.
- docs/prompts/delegation_my_forms.md에 "사람이 검토해 발행", 이 프롬프트, 회차, 사람 결정(위 승인 두 가지 포함), 사람 확인 결과, 골든 통과 이유 한 줄, 미룸을 짧게 기록한다.
- PR의 CI에서 build와 frontend job이 통과한다.

# 중단 조건 (멈추고 보고한다)
- 같은 실패로 3회차를 넘길 때, 작업이 4시간을 넘길 때. 사람의 답을 기다린 시간은 빼고 잰다. 지금까지 한 것을 커밋하고 어디서 막혔는지 쓴다.
- SPEC·AGENTS에 없는 판단이 필요할 때, 새 Flyway가 필요할 때, 위 승인 범위 밖의 기존 테스트를 고쳐야 할 때, 골든 실행기를 승인 범위 밖으로 바꿔야 할 때. 객관식으로 묻고 추천안을 첫 번째에 둔다. 한 번에 묶어서 묻는다.

# 커밋과 보고
- 커밋 "{tag}: 한글 메시지 (#43)", 50자 이내, 파일명·디렉터리명 금지.
- 사람 확인이 끝나면 push 전에 다시 묻지 않고 올린다. 범위를 넘으면 push하지 말고 멈춘다. amend와 force push를 하지 않는다. PR 전에 main이 바뀌었으면 merge로 합친다.
- PR 제목 "[feat] E5 내 폼함과 제출 이력 (#43)", 본문은 README 형식, 관련 이슈 "Closes #43", --reviewer Psoyeon1117. 다른 이슈가 알아야 할 것을 짧게 적는다: T10(취소 때 saved 복귀를 넣을 자리, editable·cancellable 표시 위치), E12(미제출 판정에 쓸 내 폼함 조회), T12(조회 전용 이력 표시).
- PR을 연 뒤 CI를 확인하고 멈춘다. 머지하지 않고 다음 이슈로 넘어가지 않는다. 띄운 백엔드는 PR을 연 뒤 끈다.
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 사람 확인 결과 / 지시와 다르게 한 것 / 확인하지 못한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
```

## §2 사람이 정한 답

| 질문 | 답 |
|---|---|
| 미리 승인 1: `AccessGoldenTest` 준비 코드 | 준비 코드만 고친다. YAML·기대값·단언·skip은 그대로 |
| 미리 승인 2: `App.routes.test.tsx` 띠 테스트 | 첫 경로만 `/me/profile`로. 같은 이유로 334행 테스트(`/me/responses`가 API를 부르게 됨)도 경로만 `/me/profile`로 바꾸는 것을 승인 |
| `SubmissionService` 생성자에 `SavedFormRepository`가 더해져 `SubmissionServiceTest`·`SubmissionControllerTest`가 컴파일되지 않음 | 모의 객체 인자·`@MockitoBean` 준비만 더한다(단언 불변) |
| 같은 이유로 골든 실행기 `SubmissionGoldenTest`가 컴파일되지 않음 | 생성자 인자 하나만 더한다 |
| 사람 확인에서 나온 의견: 이미 저장한 폼에도 "내 폼함에 저장" 버튼이 보임 | 응답 화면이 로그인일 때 `GET /me/forms`로 저장 여부를 보고 "저장된 폼"을 보인다. 응답 화면의 API 호출을 고정한 기존 `App.routes.test.tsx` 302·316행 테스트의 기대 URL에 `/me/forms`를 더하고 제목·주석을 맞춘다(사람이 에이전트 추천안을 승인) |
| 사람 확인에서 나온 의견: 마감 늦은 순에서 마감 없는 폼이 아래로 감 | 바꾸지 않는다. AGENTS.md 85행 정의("이후 같음")대로다. 바꾸려면 AGENTS·SPEC을 먼저 고친다(후속) |

에이전트가 SPEC 안에서 정한 것:

- 제출 이력 상세 `GET /me/responses/{response_id}`는 공개 조회와 같은 모양의 제출 버전 정의(`form_definition`)와 문항별 `question_note`·`value`, 첨부 메타데이터를 준다. 잘못된 ID 형식도 404다.
- 내 폼함 목록의 제목은 마지막 배포 정의에서 읽는다. 폼이 없거나 제목을 못 읽는 항목은 목록에서 뺀다(삭제 뒤 표시는 T12).
- 저장은 `users` 행을 잠가 같은 사용자의 동시 저장을 한 번에 하나씩 처리한다.

## §3 회차 표

| 회차 | 테스트 (통과 · 실패) | 실패 | 바꾼 것 |
|---|---|---|---|
| 1 서버 | 백엔드 475 · 0(skip 3, 기존 E7 분석 골든) | 첫 컴파일: 골든 실행기 `SubmissionGoldenTest`의 옛 생성자 | 사람 승인 뒤 인자 하나 추가 |
| 2 화면 | 프런트 251 · 0 | Prettier 미정리 6개 파일 | 손댄 파일만 Prettier로 정리 |
| 3 사람 확인 의견 | 프런트 252 · 0 | 기존 라우트 테스트 2건(응답 화면의 호출 목록 고정) | 사람 승인 뒤 기대 URL에 `/me/forms` 추가(실제 호출 순서대로) |

## §4 실제 화면 확인 (사람, 2026-11-08)

서버는 이 브랜치 코드로 `DB_NAME=passform_e3`에 띄웠다. 로그인은 사람이 했다.

| 확인 | 결과 |
|---|---|
| 1. 남의 폼 "내 폼함에 저장" → 맨 위, "미제출" 탭 | 됨 |
| 2. 쓰다 나간 폼이 "작성 중"에 있고 이어 쓰기 | 됨 |
| 3. 저장 없이 바로 제출한 폼은 "전체"에만 | 됨 |
| 4. 마감 지난 폼은 "마감됨"에만 | 됨 |
| 5. 정렬 세 가지가 AGENTS 5절대로 | 됨. 마감 늦은 순에서 마감 없는 폼이 아래로 가는 것은 정의대로이며 후속으로 남김 |
| 6. 제출 이력에서 문구를 바꿔 다시 배포한 뒤에도 옛 문구 | 됨 |
| 7. 가짜 값 띠 없음, 휴대폰 너비 가로 스크롤 없음 | 됨 |
| 추가: 이미 저장한 폼은 "저장된 폼"으로 보임(회차 3) | 됨 |

## §5 골든 통과 이유

`other_users_response_404`: 실행기가 rb01을 fixture 원문 그대로 `respondent` 소유로 저장소에 두므로, `other_user`의 `GET /me/responses/{rb01 UUID}`는 소유자 조건에서 걸러져 404이고 본문에 자기소개 원문이 없다.

## §6 미룸

- T10: 제출 후 수정·취소와 취소 때 `saved` 복귀(`SavedForm.markSubmitted` 옆에 둘 자리). 이력 상세는 `editable`·`cancellable`을 이미 받는다.
- E12: 알림·마감 알림. 미제출 판정은 `InboxService`·`SavedFormRepository.findByUserIdOrderBySavedAtDescFormIdAsc`를 쓸 수 있다.
- 응답 첨부 다운로드, T12(삭제 뒤 조회 전용 이력 화면), AC17 골든(사람이 아직 쓰지 않음).
- 마감 늦은 순에서 마감 없는 폼의 위치(AGENTS·SPEC 결정 필요).
- 시험하지 않음: 초안 첫 저장과 제출이 동시에 내 폼함 행을 넣는 경쟁, 실제 첨부가 있는 이력 상세.

## 수용 판단

(사람이 적는다)
