# 빌더 분기 편집 위임 기록 (#42)

기록 대상: E4 빌더 분기 편집. 실행일 2026-11-07. 프롬프트는 사람이 검토해 발행했고 §1에 그대로 둔다.

## §1 프롬프트

### 첫 지시

```text
이번 작업은 이슈 #42(E4 빌더 분기 편집)뿐이다. 다른 이슈는 구현하지 않는다. 나는 ye-nni이고 리뷰어는 Psoyeon1117이다. 셸은 Windows PowerShell이다.

# 목표
기본 빌더에서 분기 규칙을 추가·수정·삭제하고, 분기 폼의 시작 방식(한 질문씩·섹션별 페이지)을 고르게 한다. 만든 분기가 미리보기와 실제 응답 경로에 같은 규칙으로 적용되는지 실제 화면에서 확인한다. 서버 API와 서버 검증, 화면 경로 계산은 이미 main에 있다. 서버 코드는 고치지 않는다.

# 비용 규칙 (주간 한도가 빠듯하다. 지킨다)
- 서브에이전트를 띄우지 않는다. SPEC, ARCHITECTURE 전체를 읽지 않는다. 아래 지정한 곳만 읽는다.
- 새 라이브러리를 더하지 않는다. 디자인을 다듬지 않는다. 기존 컴포넌트와 CSS를 쓴다.
- 일반 테스트는 아래 목록만 쓴다. 단계가 끝날 때마다 로컬에 커밋한다.
- 위임 기록과 PR 본문은 짧게, 최종 보고는 40줄 안팎으로 쓴다.

# 시작 확인 (하나라도 어긋나면 멈추고 알린다)
1. git status로 추적 파일 변경이 없는지 본다. 미추적 docs/SPEC_followups.md는 정상이다.
2. git fetch origin, git switch main, git merge --ff-only origin/main.
3. gh pr view 82로 PR #82(T4 응답 화면)가 머지됐는지 본다. 머지되지 않았으면 구현하지 말고 멈춰 알린다. main에 frontend/src/response/responsePath.ts가 있는지 확인한다.
4. main에서 브랜치 feat/branch-editor를 만든다.
5. frontend에서 npm run lint, npm run typecheck, npm test가 통과하는지 본다. 테스트는 VITE_USE_API=false로 돌린다(frontend/.env.local이 VITE_USE_API=true라 Vitest도 읽는다).

# 먼저 읽을 것 (이곳만)
AGENTS.md, gh issue view 42, docs/SPEC.md 177~178행, 211행, AC8·AC44·AC46·AC81(내용으로 찾는다), docs/ARCHITECTURE.md 170행(F1)과 173행(F4), src/passform/schemas/form.schema.json의 branch_rule, src/main/java/com/opinio/passform/domain/form/FormPublishValidator.java(서버 의미 검증, 읽기만), frontend/src/builder의 formDefinition.ts·QuestionEditor.tsx·FormSettingsPanel.tsx·messages.ts, frontend/src/response/responsePath.ts, frontend/src/pages/ResponsePage.tsx의 미리보기 부분, docs/prompts/delegation_form_builder.md 앞부분(기록 형식).

# 절대 규칙
- 기존 테스트(frontend/src/**/*.test.*)의 단언과 기대값을 고치지 않는다. 고쳐야만 통과하면 멈추고 묻는다. 판정 기준(tests/harness, data/seed, 두 스키마 폴더, src/response_analysis/prompts, config/rag.yaml, evals/evalset.jsonl)을 고치지 않는다. deny를 셸로 우회하지 않는다.
- 분기 판정 규칙을 새로 만들지 않는다. 미리보기와 응답 경로는 responsePath.ts를 그대로 쓴다. 배포 전 검사는 서버 FormPublishValidator의 규칙을 옮긴 것이고, 판정의 정본은 서버다.
- 인증 우회를 넣지 않는다. 키와 비밀값을 파일, 커밋, 출력, VITE_ 변수에 넣지 않는다.
- TypeScript strict, any 금지. 화면에 question_id, section_id, q1, v1, 오류 코드 원문을 쓰지 않는다. 문항은 "섹션 2의 3번 문항: 문구"처럼, 섹션은 제목으로 보여 준다.
- 커밋·PR에 에이전트 이름이나 생성 문구를 넣지 않는다. PR 본문에 사람을 @로 언급하지 않는다. _local/과 docs/SPEC_followups.md는 커밋하지 않는다. git add는 경로를 명시하고 스테이징 목록을 확인한 뒤 커밋한다.

# 바꿔도 되는 것 / 안 되는 것
- 바꿔도 됨: frontend/src(기존 테스트 제외), docs/prompts의 새 기록 파일.
- 안 됨: src/main, src/test, tests/java, 판정 기준, docs/SPEC.md, docs/ARCHITECTURE.md, AGENTS.md, build.gradle, package.json의 의존성.
- ResponsePage와 responsePath.ts는 T4가 만든 것이다. 미리보기의 AI 고지 외에는 고치지 않는다. 고쳐야 하면 멈추고 묻는다.

# 구현 범위
분기 편집 (SPEC 178행, F1)
- 문항마다 규칙 목록: 추가, 삭제, 위·아래 순서 변경(위에서부터 검사하므로 순서가 뜻을 가진다). 규칙마다 when.match(모두 만족 all / 하나라도 만족 any), 조건 목록(문항, 선택지), 이동할 곳(뒤쪽 섹션, 뒤쪽 문항, 제출 {"submit": true}).
- 조건에 고를 수 있는 것은 그 문항 자신과 앞의 선택형 문항, 그 문항에 실제로 있는 선택지만이다. 이동할 곳은 뒤쪽만 고를 수 있다. 목록을 그렇게 만들어 틀린 규칙을 처음부터 못 만들게 한다.
- 섹션별 페이지(section_page)에서는 섹션의 마지막 문항에만 규칙을 둘 수 있고, 이동할 곳은 뒤쪽 섹션이나 제출뿐이다(AC46).
- 저장 형식은 form.schema.json의 branch_rule 그대로다. 규칙이 없으면 branch_rules는 []다.

편집과의 연결 (사람 결정)
- 선택지 문구를 고치면 같은 폼에서 그 선택지를 가리키는 조건도 새 문구로 바꾼다.
- 문항·선택지·섹션을 지우거나 순서를 바꾸거나 유형을 바꿔 규칙이 깨지면 자동으로 지우지 않는다. 그 규칙에 "가리키는 문항이 없어요"처럼 문제를 표시하고, 배포 전 검사가 막는다. 초안 저장은 막지 않는다(서버도 초안은 의미 검증을 하지 않는다).
- 복제 규칙(SPEC 154행: 복제본의 branch_rules는 비움)은 그대로 둔다.

시작 방식 (SPEC 177행, AC46)
- 분기가 없으면 one_question·all_questions, 있으면 one_question·section_page 중에서 고른다. 분기가 생겨 지금 값이 허용 밖이 되면 그 사실을 알리고 고르게 한다. 조용히 바꾸지 않는다.
- E3이 남긴 "분기 N개, 분기 편집은 준비 중" 안내와 문항의 분기 배지를 실제 편집으로 바꾼다.

배포 전 검사와 오류 표시
- publishProblems에 서버 FormPublishValidator와 같은 분기·시작 방식 검사를 더하고, 어느 문항의 몇 번째 규칙인지 쉬운 말로 알린다. 서버가 invalid_branch, invalid_display_mode를 주면 messages.ts의 문구로 보여 준다.

미리보기의 AI 요약 고지 (사람 결정, E3에서 이어진 누락)
- 지금 toPreviewForm이 consent.ai_summary를 항상 null로 만든다. settings.ai_summary.enabled가 true이면 SPEC 211행의 현재 고지 값으로 채운다. 값은 frontend/src/builder에 상수 하나로 두고 "정본은 서버 ConsentNotices, SPEC 211행" 주석을 단다. 꺼져 있으면 null이다.

# 이 PR에서 하지 않는다 (미룸)
분기 흐름 그래프(E14), 자동 저장(E14), 빠른 모드(E13), 서버의 빈 선택지 검사(후속 PR), "기타" 선택지(11/11 뒤), 화면 경로 계산과 응답 화면 동작 변경(T4).

# 일반 테스트 (이것만)
- 폼 정의 함수: 규칙 추가·삭제·순서 변경, 선택지 문구 변경 때 조건 문구 따라감, 문항 삭제 때 규칙이 지워지지 않고 문제로 잡힘, 저장 형식이 스키마의 branch_rule과 같음.
- 배포 전 검사: 앞쪽·자기 자신·없는 대상, 뒤 문항이나 선택형이 아닌 문항을 가리키는 조건, 없는 선택지, section_page에서 중간 문항의 규칙과 문항 대상 이동, 분기 있는 폼의 all_questions. 서버 FormPublishValidatorTest와 같은 입력을 쓰고 대응표를 위임 기록에 남긴다.
- 미리보기: 분기 폼에서 선택지를 고르면 다음 화면이 responsePath.ts의 결과와 같다(1건). AI 요약을 켠 폼의 미리보기에 고지와 선택 체크박스가 보인다(1건).
여기 없는 경우가 필요해 보이면 더하지 말고 PR에 "시험하지 않음"으로 적는다.

# 사람 확인 (push 전에 멈춘다)
자동 검사가 통과하면 push하지 말고 멈춰 아래를 사람에게 안내한다. 백엔드는 이 PC의 개발 DB 사정 때문에 DB_NAME=passform_e3로 띄운다(public을 건드리지 않는다). 5173에 이미 Vite가 떠 있으면 그대로 쓴다. 각 항목에 "무엇을 누르면 무엇이 보여야 통과"를 버튼의 실제 이름으로 적는다. 에이전트는 로그인 정보를 입력하지 않는다.
1. 새 폼에 섹션 3개와 선택형 문항을 만들고, 1번 문항에 "예면 섹션 3으로" 규칙을 단다. 시작 방식은 한 질문씩.
2. 미리보기에서 "예"를 고르면 섹션 3으로, "아니요"면 다음 섹션으로 간다.
3. 배포해 공유 링크를 시크릿 창에서 열어 같은 이동이 되는지 본다. 제출까지 해 본다(제출 API는 main에 있다).
4. 시작 방식을 섹션별 페이지로 바꾸면 중간 문항의 규칙이 문제로 표시되고 배포가 막힌다.
5. 규칙이 가리키는 선택지 문구를 고치면 규칙도 따라 바뀌고, 그 문항을 지우면 규칙에 문제가 표시된다.
6. 모두 만족·하나라도 만족 조건이 미리보기에서 맞게 동작한다(AC44의 예: 두 조건).
7. AI 요약을 켠 초안의 미리보기 마지막 단계에 고지와 선택 체크박스가 보인다.
화면 수정 의견이 오면 이 범위 안에서만 고친다.

# 완료 기준
- frontend에서 npm run lint, npm run typecheck, npm test 통과. 건수를 보고한다. 기존 테스트 파일은 변경 없음.
- 사람 확인 7개 항목의 결과를 보고한다.
- 서버를 고치지 않았으니 .\gradlew.bat cleanTest build는 돌리지 않는다. CI가 맡는다.
- git diff origin/main...HEAD --name-only가 허용 범위뿐이다. 커밋 뒤 git status --short에 docs/SPEC_followups.md만 남는다.
- docs/prompts/delegation_branch_editor.md에 "사람이 검토해 발행", 이 프롬프트, 회차, 사람 결정, 사람 확인 결과, 서버 검증과의 대응표, 미룸을 짧게 기록한다.
- PR의 CI에서 build와 frontend job이 통과한다.

# 중단 조건 (멈추고 보고한다)
- 같은 실패로 3회차를 넘길 때, 작업이 3시간을 넘길 때. 사람의 답을 기다린 시간은 빼고 잰다. 지금까지 한 것을 커밋하고 어디서 막혔는지 쓴다.
- SPEC·AGENTS에 없는 판단이 필요할 때, 서버 검증이나 responsePath.ts가 SPEC과 달라 보일 때(고치지 말고 알린다), 기존 테스트를 고쳐야 할 때. 객관식으로 묻고 추천안을 첫 번째에 둔다. 한 번에 묶어서 묻는다.

# 커밋과 보고
- 커밋 "{tag}: 한글 메시지 (#42)", 50자 이내, 파일명·디렉터리명 금지.
- 사람 확인이 끝나면 push 전에 다시 묻지 않고 올린다. 범위를 넘으면 push하지 말고 멈춘다. amend와 force push를 하지 않는다. PR 전에 main이 바뀌었으면 merge로 합친다.
- PR 제목 "[feat] E4 빌더 분기 편집 (#42)", 본문은 README 형식, 관련 이슈 "Closes #42", --reviewer Psoyeon1117. 다른 이슈가 알아야 할 것을 짧게 적는다: E14(분기 그래프가 쓸 함수), E13(빠른 모드의 분기 여부), T4(미리보기 AI 고지 상수 위치).
- PR을 연 뒤 CI를 확인하고 멈춘다. 머지하지 않고 다음 이슈로 넘어가지 않는다. 띄운 백엔드는 PR을 연 뒤 끈다.
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 사람 확인 결과 / 지시와 다르게 한 것 / 확인하지 못한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
```

## §2 실패·수정 회차

| # | 실패 | 원인 | 수정 |
|---|---|---|---|
| 1 | 시작 시 typecheck 1건·테스트 파일 1개 | main에 더해진 `yaml` 의존성이 로컬 `node_modules`에 없음 | `npm ci`가 5173에서 돌던 Vite가 잡은 파일 때문에 중간에 실패해 `node_modules`가 비었다. Vite 종료는 권한에서 거부돼 Vite를 둔 채 `npm install --no-save`로 lock 파일 그대로 복구. package.json·lock 변경 없음. 시작 기준선 16파일 192건 통과 |
| 2 | 새 미리보기 테스트 2건 | 새 파일에 jsdom 환경 지정이 없음 | 파일 첫 줄에 `// @vitest-environment jsdom`(기존 화면 테스트와 같음). 1회 |

## §3 사람 결정

- 프롬프트의 사람 결정(선택지 문구 따라감, 깨진 규칙은 지우지 않고 표시, 미리보기 AI 고지 상수)대로 했다.
- 선택지 문구가 같은 선택지가 여럿이면 어느 쪽을 가리키는지 알 수 없어 조건을 따라 바꾸지 않는다(같은 문구는 이미 저장 전 검사가 막는다).
- 사람 확인 뒤 의견 4건에 대한 결정(객관식):
  1. 분기·시작 방식 문제가 있으면 미리보기를 열지 않고 문제 목록을 보인다(분기 아닌 미완성은 지금처럼 허용).
  2. 미리보기·저장·배포 버튼을 빌더 맨 아래에도 둔다(이 PR, 범위 확장). 기존 테스트가 버튼 이름으로 찾아 아래쪽은 접근성 이름에 "(아래)"를 붙였다.
  3. 폼 설정을 오른쪽에 고정하는 배치는 공통 CSS 변경이라 별도 작은 PR로 미룬다.
  4. 응답 화면의 필수·선택 동의를 한 박스로 합치고, AI 고지도 접어 각 문구 오른쪽 "내용 보기"로 편다(이 PR, T4 화면). 체크박스는 따로 둔다. 기존 테스트 `ResponsePage.screen.test.tsx`의 AI 고지 테스트에 "접혀 있음 확인·내용 보기 클릭" 두 줄을 더하는 것을 승인. 기존 단언·기대값은 그대로다.

## §4 사람 확인 결과

| 항목 | 결과 |
|---|---|
| 1~7 | 통과 |
| 의견 반영 1(미리보기 막기), 2(하단 버튼), 4(동의 한 박스) | 2차 통과 |
| 의견 3(폼 설정 오른쪽 고정) | 미룸(별도 PR) |

## §5 서버 검증과의 대응표

`frontend/src/builder/branchRules.test.ts`가 서버 `FormPublishValidatorTest`와 같은 정의(SampleForms.branched/unbranched)를 쓴다. 서버는 오류 코드 하나, 화면은 규칙별 문구를 낸다.

| 서버 테스트(입력) | 서버 결과 | 화면 테스트의 문구 |
|---|---|---|
| 분기_없는_폼과_section_page_분기_폼은_통과한다 | 통과 | 문제 없음 |
| AC8 `q2/예/section none` | invalid_branch | 이동할 섹션이 없어요. |
| AC8 `q2/예/question none` | invalid_branch | 이동할 문항이 없어요. |
| AC8 `q2/예/section s1` | invalid_branch | 이동할 섹션이 이 문항의 섹션과 같거나 앞에 있어요. |
| AC8 `q2/예/question q2`, `question q1` | invalid_branch | 이동할 문항이 이 문항과 같거나 앞에 있어요. |
| AC8 `none/예/submit` | invalid_branch | 조건이 가리키는 문항이 없어요. |
| AC8 `q2/없는 선택지/submit` | invalid_branch | 조건의 선택지 "없는 선택지"가 그 문항에 없어요. |
| AC8 `q4/소감/submit`, `q3/역할/submit` | invalid_branch | 조건의 문항이 이 문항보다 뒤에 있어요. |
| (화면만) q3 규칙이 자기 자신 `q3/역할`을 조건으로 씀 | 서버 같은 기준(선택형 아님) | 조건의 문항이 선택형이 아니에요. |
| AC46 one_question 뒤쪽 문항·제출 분기, 앞 문항 조건 | 통과 | 문제 없음 |
| AC46 분기 없는 폼 + section_page | invalid_display_mode | 분기가 없는 폼은 "섹션별 페이지"로 시작할 수 없어요… |
| AC46 분기 있는 폼 + all_questions | invalid_display_mode | 분기가 있는 폼은 "전체 질문 보기"로 시작할 수 없어요… |
| AC46 section_page 중간 문항 규칙 | invalid_display_mode | 섹션별 페이지에서는 섹션의 마지막 문항에만 분기를 둘 수 있어요. |
| AC46 section_page 문항 대상 | invalid_display_mode | 섹션별 페이지에서는 문항으로 이동할 수 없어요… |

## §6 미룸

분기 흐름 그래프(E14), 자동 저장(E14), 빠른 모드(E13), 서버의 빈 선택지 검사(후속 PR), "기타" 선택지(11/11 뒤), 화면 경로 계산과 응답 화면 동작 변경(T4).
