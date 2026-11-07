# 기본 빌더 위임 기록 (#41)

기록 대상: E3 기본 빌더. 실행일 2026-11-07. 프롬프트는 사람이 검토해 발행했고 §1에 그대로 둔다.

## §1 프롬프트

### 첫 지시

```text
이번 작업은 이슈 #41(E3 기본 빌더)뿐이다. 다른 이슈는 구현하지 않는다. 나는 ye-nni이고 리뷰어는 Psoyeon1117이다. 셸은 Windows PowerShell이다.

# 목표
데스크톱 기본 모드에서 실제 API로 폼을 만들고, 저장하고, 다시 열고, 미리 보고, 배포해 공유 링크를 받는다. 내가 만든 폼 화면도 실제 목록을 보여 준다. 서버 API는 이미 main에 있다. 서버 코드는 고치지 않는다. 틀은 완성본이 아니라 출발점이다. 동작에 필요한 만큼 고치고, 이 화면들의 가짜 데이터를 실제 API로 바꾼다.

# 비용 규칙 (주간 한도가 빠듯하다. 지킨다)
- 서브에이전트를 띄우지 않는다. SPEC, ARCHITECTURE 전체를 읽지 않는다. 아래 지정한 곳만 읽는다.
- 새 라이브러리를 더하지 않는다(드래그, 폼, 상태 관리, Playwright 모두). 순서 변경은 위·아래 버튼으로 한다.
- 디자인을 다듬지 않는다. 기존 컴포넌트와 CSS를 쓰고 동작을 우선한다. 글씨체, 색, 자잘한 화면 수정은 미룬다.
- 일반 테스트는 아래 목록만 쓴다. 단계가 끝날 때마다 로컬에 커밋한다. 세션이 끊겨도 이어 갈 수 있어야 한다.
- 위임 기록과 PR 본문은 짧게 쓴다. 최종 보고는 40줄 안팎으로 쓴다.

# 시작 확인 (하나라도 어긋나면 멈추고 알린다)
1. git status로 추적 파일 변경이 없는지 본다. 미추적 docs/SPEC_followups.md는 정상이다.
2. git fetch origin, git switch main, git merge --ff-only origin/main. PR #78의 FormController가 있는지 확인한다.
3. main에서 브랜치 feat/form-builder를 만든다.
4. docker compose up -d db. .env에 GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET 줄이 있는지 이름만 확인한다. 값을 출력하거나 보지 않는다. 없으면 멈추지 말고 구현은 진행하되, 첫 보고에 "실제 화면 확인 불가"라고 적는다.
5. frontend에서 npm run lint, npm run typecheck, npm test가 시작 시점에 통과하는지 본다.

# 먼저 읽을 것 (이곳만)
AGENTS.md, gh issue view 41, docs/SPEC.md 114~120행(API), 150~170행, 174~177행, 180행, 204행, 210행, AC11·12·13·42·53·72·73·79·80·81(내용으로 찾는다), src/passform/schemas/form.schema.json, frontend/src의 pages/BuilderPage.tsx, pages/CreatedPage.tsx, pages/ResponsePage.tsx, routes/creatorRoutes.tsx, routes/paths.ts, api/client.ts, types/form.ts, App.routes.test.tsx, docs/prompts/delegation_form_definition.md 앞부분(기록 형식). PR #78 본문의 "E3이 쓸 API"는 gh pr view 78로 본다.

# 절대 규칙
- 기존 테스트(frontend/src/**/*.test.*)의 단언과 기대값을 고치지 않는다. 고쳐야만 통과하면 멈추고 묻는다. 판정 기준(tests/harness, data/seed, 두 스키마 폴더, src/response_analysis/prompts, config/rag.yaml, evals/evalset.jsonl)을 고치지 않는다. deny를 셸로 우회하지 않는다.
- 인증 우회(X-Demo-User 등)를 넣지 않는다. 키와 비밀값을 파일, 커밋, 출력, VITE_ 변수에 넣지 않는다. Google 비밀값을 보거나 로그인 정보를 입력하지 않는다.
- TypeScript strict, any 금지. API 타입은 백엔드 DTO와 같은 이름을 쓴다. 화면에 q1, 응답 ID, v1, profile_key, 오류 코드 원문을 쓰지 않는다. "폼 버전 1", "본인 정보"처럼 쓴다.
- 시간: 저장은 UTC, 표시는 Asia/Seoul. 마감은 타임존이 든 ISO 8601로 보낸다.
- 송금은 링크 입력만 한다. 송금 완료 표시를 만들지 않는다.
- 커밋·PR에 에이전트 이름이나 생성 문구를 넣지 않는다. PR 본문에 사람을 @로 언급하지 않는다. _local/과 docs/SPEC_followups.md는 커밋하지 않는다. git add는 경로를 명시하고 스테이징 목록을 확인한 뒤 커밋한다. 명령을 ;로 이어 add 실패 뒤 commit이 실행되지 않게 한다.

# 바꿔도 되는 것 / 안 되는 것
- 바꿔도 됨: frontend/src(기존 테스트 제외), docs/prompts의 새 기록 파일, docs/SPEC.md 210행 한 곳(아래 지시대로).
- 로컬에만: frontend/.env.local에 VITE_USE_API=true. 만들기 전에 git check-ignore로 무시되는지 확인한다. 무시되지 않으면 만들지 말고 알린다. 커밋하지 않는다.
- 안 됨: src/main, src/test, tests/java, 위 판정 기준, AGENTS.md, docs/ARCHITECTURE.md, build.gradle, package.json의 의존성.
- ResponsePage는 T4가 다시 만든다. 미리보기에 필요한 최소 변경만 하고 PR에 적는다.

# SPEC 한 줄 (사람 결정, 별도 커밋)
docs/SPEC.md 210행 맨 끝에 다음 문장을 이어 붙인다. 새 줄을 만들지 않는다. 다른 문서가 SPEC 행 번호를 가리키므로 줄 수를 유지한다. 이 파일은 CRLF이니 줄 끝을 바꾸지 않는다. 수정 뒤 git diff --stat이 1줄 변경인지 확인한다.
 빌더는 새 폼에 기본 동의 템플릿(`consent_default`)을 미리 선택하고, 서버는 비어 오면 `consent_template_required`로 거절한다.

# 구현 범위
저장 형식
- 화면 상태는 폼 정의 JSON 하나로 둔다. 모드별 저장 형식을 만들지 않는다. 폼 정의 편집은 순수 함수 모듈로 분리해 테스트한다.
- 스키마가 초안에도 적용된다는 점에 맞춘다: additionalProperties false, options 중복 금지, 단답·장문은 options 빈 배열, input_format은 단답에만, character_limit은 단답·장문에만이다. input_format이 없을 때는 키를 뺀다(null 불가). 유형을 바꾸면 맞지 않는 값을 지운다. 선택지를 더할 때 중복 문구가 생기지 않게 한다.
- GET definition 응답의 status, share_url, definition_version은 PATCH에 보내지 않는다. 보내면 schema_invalid다.
- 저장된 branch_rules, attachments, note_attachments, template_id, question_description, settings의 다른 값은 UI가 없어도 지우지 않는다. 그대로 다시 저장해 값이 바뀌지 않아야 한다(AC42). 분기가 있는 폼이면 "분기 N개, 분기 편집은 준비 중"처럼 읽기 전용으로 알린다.
- question_id, section_id는 처음 만들 때 고유한 값(crypto.randomUUID 기반)을 주고 이후 바꾸지 않는다. 버전을 넘어 같은 문항을 가리키기 때문이다.

새 폼과 저장
- /creator/forms/new에 들어오면 POST /forms로 초안을 만들고 편집 주소로 replace 이동한다. StrictMode에서 두 번 만들어지지 않게 막는다. 필수 키 7개를 모두 보낸다: title "", note "", settings {editable false, cancellable false, display {default_mode: one_question}}, payment_link null, consent_template_id, note_attachments [], sections(빈 문항의 첫 섹션 1개). consent_template_id는 GET /consent-templates의 consent_default다. 없으면 첫 항목, 목록이 비면 null로 만들고 화면에 알린다.
- 편집 화면은 GET /forms/{form_id}/definition으로 연다. 401이면 로그인 화면으로 보낸다. 403·404는 안내한다.
- draft는 "초안 저장"으로 PATCH(status 없음)한다. open·closed는 "변경 저장"으로 PATCH하고, 응답자가 보는 내용이 바뀌면 새 폼 버전이 된다고 안내한다. 저장 중·완료·실패를 구분해 보여 주고, 실패한 변경을 저장된 것으로 표시하지 않는다. 자동 저장은 하지 않는다(E14).

편집
- 섹션: 추가, 제목 수정, 삭제, 복제, 위·아래 이동.
- 문항: 추가, 수정, 삭제, 복제, 섹션 안에서 위·아래 이동. 복제 규칙은 SPEC 154행 그대로다: 새 ID, branch_rules 비움, profile_key null. 원본은 바뀌지 않는다.
- 문항 설정: 유형 5종, 문구, 보조 설명, 선택지 편집, 필수 여부, 입력 형식(단답), 최대 글자 수(단답·장문), 본인 정보 확인(SPEC 167행의 쉬운 말, 기본 "아니요", 문구나 입력 형식이 바뀌면 지우고 다시 묻기, AC72).
- 폼 설정: 제목, 설명, 마감, 제출 후 수정 허용, 제출 취소 허용, 선착순 인원, 시작 방식(분기 없는 폼은 one_question·all_questions), AI 요약 사용(첫 배포 전에만 바꿀 수 있고 그 뒤에는 잠금 안내, SPEC 157행), 동의 템플릿 선택과 그 purpose·retention 표시(retention은 줄바꿈을 살린다), 송금 링크.
- 작은 고침: plus.svg가 흰색이라 흰 배경 버튼에서 안 보이는 문제, 폼 설명 칸이 rows=2로 좁은 문제. 빠른 모드 탭은 숨긴다(E13).

미리보기 (AC81)
- 저장 전의 현재 편집 내용을 ResponsePage로 보여 주고 API를 부르지 않는다. 제출을 눌러도 Response를 만들지 않고 "미리보기라 제출되지 않아요"라고 알린다. 돌아오면 편집 내용이 그대로다. 분기 이동은 E4다.

배포와 상태
- "배포": PATCH에 현재 정의와 status open을 함께 보낸다. 서버가 판정의 정본이다. 다만 문항 0개, 문구 없음, 선택지 없음은 서버가 schema_invalid만 주므로 요청 전에 어느 문항인지 알린다.
- 성공하면 share_url과 복사 버튼, "폼 버전 N"을 보여 준다. QR은 E14다.
- open이면 "응답 받기 끄기"(closed), closed면 "다시 열기"(open). open → draft 되돌리기는 만들지 않는다(미룸).
- 오류 코드를 쉬운 말로 옮긴다: consent_template_required, invalid_payment_link, invalid_branch, invalid_display_mode, ai_summary_locked, invalid_transition, response_limit_reached, schema_invalid. 실패하면 편집 내용과 서버 상태를 유지한다.

내가 만든 폼
- GET /me/created-forms로 목록, 상태, 마감, 응답 수, 공유 링크를 보여 준다. 빈 목록 안내를 둔다. 카드에서 편집으로 간다. 결과와 탐색 화면은 아직 가짜 데이터이니 실제 API 모드에서는 그 버튼을 숨긴다(T6, E8이 연결한다).

데모 모드
- VITE_USE_API=false의 기존 라우트 테스트가 통과해야 한다. 데모 모드는 최소한으로 유지하고 공들이지 않는다. 데이터 출처는 route 또는 작은 API 계층에서 나눈다.

# 이 PR에서 하지 않는다 (미룸)
자동 저장과 나갈 때 경고(E14, AC84), 분기 편집과 section_page 선택(E4), 빠른 모드와 모바일 제작(E13), 첨부 업로드(T12), 폼 복사·삭제 UI(E14, T12), QR·분기 그래프(E14), 응답 화면 본 동작과 글자 수 카운터(T4), 결과·탐색 연결(T6, E8), 추천 문항(E13), open → draft 되돌리기.

# 일반 테스트 (이것만)
- 폼 정의 함수: 문항 복제(새 ID, branch_rules 비움, profile_key null, 원본 불변), 섹션 복제(자식 ID 모두 새 값), 순서 이동, 유형 변경 때 스키마에 맞게 정리, 문구·입력 형식 변경 때 본인 정보 초기화(AC72), 상태 키 제거, 저장된 분기·첨부 보존(AC42).
- 만든 정의가 form.schema.json을 통과하는지: 새 폼과 다섯 문항 유형 폼 각 1건. 스키마 파일은 읽기만 한다. 프론트에 검증 라이브러리를 더하지 않는다. 안 되면 실제 화면 확인에서 보고 "시험하지 않음"으로 적는다.
- 마감 변환: KST 입력이 타임존 포함 ISO로 나가고, UTC 응답이 KST로 보인다.
- 빌더 화면(api 모의): 새 폼 진입 때 POST 1회와 편집 주소 이동, 기본 동의 템플릿 선택, 저장 실패 때 "저장됨"이 안 보임, 배포 400 때 상태 유지와 쉬운 말 안내, 배포 성공 때 공유 링크 표시, 미리보기 제출에 API 호출 없음(AC81).
- 내가 만든 폼(api 모의): 목록과 빈 목록, 실제 API 모드에서 가짜 데이터가 안 보임.
여기 없는 경우가 필요해 보이면 더하지 말고 PR에 "시험하지 않음"으로 적는다.

# 사람 확인 (push 전에 멈춘다)
자동 검사가 통과하면 push하지 말고 멈춰 아래를 사람에게 안내한다. 필요하면 .\gradlew.bat bootRun과 npm run dev를 띄워 준다(개발 스키마에 V1, V2가 적용된다). 에이전트는 로그인 정보를 입력하지 않는다. 결과를 사람에게 받아 위임 기록에 적는다.
1. http://localhost:5173/login에서 Google 로그인, /creator가 실제 목록으로 뜬다.
2. 새 폼: 동의 템플릿이 미리 선택돼 있다. 다섯 문항 유형, 입력 형식, 본인 정보, 글자 수, 필수, 설명, 마감, 송금 링크를 넣고 초안 저장.
3. 새로고침해 구조, 순서, 설정이 그대로다. 문항과 섹션을 복제, 삭제, 이동하고 다시 저장, 새로고침.
4. 미리보기에서 저장 전 문구가 보이고, 제출해도 응답이 안 생기며, 돌아오면 그대로다.
5. 문구 없는 문항이나 https가 아닌 링크로 배포하면 쉬운 말로 거절되고 초안이 그대로다.
6. 배포해 공유 링크를 받는다. 시크릿 창에서 열리고, 수집 항목·목적·보유 기간·거부 안내가 보인다.
7. 문구를 고쳐 변경 저장하면 폼 버전 2가 되고 공유 링크는 같다. 응답 받기 끄기 뒤 공유 링크가 마감 안내를 보인다. 다시 열면 열린다.
8. /creator에 상태와 공유 링크가 맞게 나온다.
화면 수정 의견이 오면 이 범위 안에서만 고친다. 글씨체·색은 미룬다.

# 완료 기준
- frontend에서 npm run lint, npm run typecheck, npm test 통과. 건수를 보고한다. 기존 테스트 파일은 변경 없음.
- 사람 확인 8개 항목의 결과를 보고한다.
- 서버를 고치지 않았으니 .\gradlew.bat cleanTest build는 돌리지 않는다. CI가 맡는다.
- git diff origin/main...HEAD --name-only가 허용 범위뿐이다. docs/SPEC.md는 1줄 변경이다. 커밋 뒤 git status --short에 docs/SPEC_followups.md만 남는다.
- docs/prompts/delegation_form_builder.md에 "사람이 검토해 발행", 이 프롬프트, 실패·수정 회차, 사람 결정, 사람 확인 결과, 미룸을 짧게 기록한다.
- PR의 CI에서 build와 frontend job이 통과한다.

# 중단 조건 (멈추고 보고한다)
- 같은 실패로 3회차를 넘길 때, 작업이 3시간을 넘길 때. 사람의 답을 기다린 시간은 빼고 잰다. 지금까지 한 것을 커밋하고 어디서 막혔는지 쓴다.
- SPEC·AGENTS에 없는 판단이 필요할 때, 서버 동작이 SPEC과 달라 보일 때(서버를 고치지 말고 알린다), 기존 테스트를 고쳐야 할 때, 이슈가 너무 커서 나눠야 할 때. 객관식으로 묻고 추천안을 첫 번째에 둔다. 한 번에 묶어서 묻는다.

# 커밋과 보고
- 커밋 "{tag}: 한글 메시지 (#41)", 50자 이내, 파일명·디렉터리명 금지. SPEC 변경은 커밋을 나눈다.
- 사람 확인이 끝나면 push 전에 다시 묻지 않고 올린다. 범위를 넘으면 push하지 말고 멈춘다. amend와 force push를 하지 않는다.
- PR 전에 main이 바뀌었으면 merge로 합친다. PR 제목 "[feat] E3 기본 빌더 (#41)", 본문은 README 형식, 관련 이슈 "Closes #41", --reviewer Psoyeon1117. 다른 이슈가 알아야 할 것을 짧게 적는다: E4(분기 UI를 붙일 자리, section_page), E14(자동 저장을 붙일 지점), E13(폼 정의 함수 재사용), T4(미리보기가 쓰는 ResponsePage의 props, retention 줄바꿈), T12(첨부 값 보존 위치).
- PR을 연 뒤 CI를 확인하고 멈춘다. 머지하지 않고 다음 이슈로 넘어가지 않는다.
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 사람 확인 결과 / 지시와 다르게 한 것 / 확인하지 못한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
```

### 사람 확인 중간 결과 뒤 지시 (요약)

- 고칠 것: 응답 화면의 가짜 제작자 이름과 가짜 섹션 제목 제거(섹션 제목은 `sections[].title`, AC80), 공개 조회 409 `form_closed`는 "응답이 마감된 폼이에요.", 404는 "폼을 찾을 수 없어요."(AC41), 동의 내용 접기(보기 B, "내용 보기" 버튼, 키보드로 열고 닫기, 미리보기도 같게). 새 테스트는 세 가지에 각 1건.
- 고치지 않는 것: 복수 선택이 라디오로 나오는 것, "내 정보로 채우기", 미리보기의 필수 검사(T4).
- 추가: PR #80(결과 화면)과 #75(제출)가 main에 머지됐다. push 전에 main을 merge하고(rebase·force 없음), 충돌은 양쪽 기능을 모두 살린다. 실제 API 모드의 `/creator` 카드에 결과 화면으로 가는 버튼을 보인다. 탐색 버튼은 계속 숨긴다(E8).

## §2 실패·수정 회차

| # | 실패 | 원인 | 수정 |
|---|---|---|---|
| 1 | 새 미리보기 테스트 1건 | 화면에 `role="status"`가 여럿이라 `getByRole('status')`가 실패 | 새 테스트의 조회만 문구로 바꿈. 1회 |
| 2 | `bootRun` 실패 | 개발 DB `public`에 Flyway 이력 없는 표 6개와 데이터(응답 67건 등)가 있어 마이그레이션 거부 | `public`은 건드리지 않고 빈 DB `passform_e3`를 만들어 이번 실행만 `DB_NAME`으로 연결. V1·V2 적용 확인 |
| 3 | `npm run dev` 실패 | 5173을 11/05에 시작된 같은 저장소의 Vite가 쓰고 있음 | 그 프로세스가 현재 코드와 `VITE_USE_API=true`를 내보내는 것을 확인하고 그대로 씀 |
| 4 | 기존 라우트 테스트 20건 | 사람 확인용 `frontend/.env.local`을 Vitest도 읽어 데모 테스트가 실제 API 모드로 돔. 코드 문제 아님 | 로컬에서는 `VITE_USE_API=false`로 실행. CI에는 이 파일이 없음. 1회 |
| 5 | main merge 충돌 | `client.ts` import 목록, `creatorRoutes.tsx`에 양쪽이 함수를 더함 | 양쪽 모두 살림. lint·typecheck·test 107건 통과 |
| 6 | 1차 코드 검토: 빈 선택지(`[""]`)로 배포됨 | 배포 전 검사가 선택지 개수만 봄. 서버도 개수만 봐서 응답자가 필수 문항을 제출할 수 없음 | 배포 전 검사에서 앞뒤 공백을 지운 문구가 빈 선택지가 있으면 알리고 요청하지 않음. 초안 저장은 막지 않음. 새 테스트는 고치기 전에 실패, 고친 뒤 108건 통과. 서버는 후속 PR |

## §3 사람 결정

- SPEC 210행 끝에 기본 동의 템플릿 문장 추가(별도 커밋, 1줄 변경).
- Q1 A: `App.routes.test.tsx` 327행의 띠 확인 경로를 `/creator`에서 `/me/forms`로 바꾸는 것을 승인. 단언은 그대로다. `demoRoutes.ts`에서 `/creator`만 뺐다.
- Q2 A: 집전화 형식의 본인 정보도 `phone_number`로 저장한다. SPEC에 집전화용 키가 없고, 프로필의 연락처 키가 하나뿐이라서다.
- Q3 B: `passform_e3`는 지우지 않고 남긴다. `public`도 건드리지 않는다. 이 DB는 §2의 2번 때문에 사람 확인용으로 만들었다.
- 동의 접기(보기 B): 평소에는 체크박스, 필수 동의 문구, "내용 보기"만 보이고 누르면 내용이 펼쳐진다. 법령 위험은 PR 본문에 적었다.
- 결과 버튼: main에 T6 결과 화면이 들어와, 실제 API 모드의 카드에 "결과 보기"를 보인다. 탐색 버튼은 계속 숨긴다.
- 1차 코드 검토 반영은 1건(빈 선택지 배포 차단)이다. 미리보기가 시작 방식을 따르지 않는 것은 고치지 않고, T4의 시작 방식 구현을 따르도록 기록만 했다.

## §4 사람 확인 결과

| 항목 | 결과 |
|---|---|
| 1, 8 | 통과(2차). 1차에는 가짜 카드와 띠를 확인하지 못해 다시 봤다. `/creator`에는 띠가 없다. 공유 링크 화면의 띠는 남은 가짜 값("내 정보로 채우기" 등, T4) 때문에 의도대로 남는다 |
| 2, 3, 4, 5 | 통과 |
| 6 | 2차 통과. 1차에 가짜 제작자 이름과 섹션 제목이 보여 고쳤다. 동의는 접혀 있고 "내용 보기"로 펼쳐진다 |
| 7 | 2차 통과. 1차에 마감 안내인지 알기 어려워 문구를 고쳤다. 꺼진 폼은 "응답이 마감된 폼이에요.", 없는 링크는 "폼을 찾을 수 없어요." |
| 결과 버튼 | 사람 확인 뒤에 더해 실제 화면 확인은 안 했다. 테스트로만 확인했다 |

## §5 미룸

자동 저장과 나갈 때 경고(E14, AC84), 분기 편집과 `section_page` 선택(E4), 빠른 모드와 모바일 제작(E13), 첨부 업로드(T12), 폼 복사·삭제 UI(E14, T12), QR·분기 그래프(E14), 탐색 연결(E8), 추천 문항(E13), open → draft 되돌리기. 응답 화면에서 복수 선택이 라디오로 나오는 것, "내 정보로 채우기"의 가짜 값, 미리보기의 필수 검사, AI 요약 고지 표시는 T4가 맡는다.
