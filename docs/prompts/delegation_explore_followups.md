# 탐색·결과 화면 보완 위임 기록 (관련 #46)

기록 대상: E8(#46, PR #94) 후속 보완 A·C·D·E·F. 실행일 2026-11-09. 사람이 검토해 발행한 프롬프트로 진행했다. 프롬프트는 §5에 그대로 둔다.

## §1 결과

| 항목 | 한 것 | 근거 |
|---|---|---|
| A | 파서 출력의 조건이 비고 `operation`이 `summarize`이며 `target`·`topic`이 있으면 남은 낱말을 되살리지 않고, 원문에 글자 그대로 있는 선택지 문구만 되살린다(`reviveDroppedOptions`와 같은 규칙). "있어·있어요·있나·있나요·있니·있는지·있을까·있을까요" 끝말은 동작어처럼 뗀다. `count`·`list`·동작 없음은 그대로 낱말을 되살린다 | SPEC 334행, AGENTS 3절 3·4항. `DroppedConditionRevivalTest` 4건 |
| C | `unknown_condition`(조건) 되묻기의 후보 선택지를 버튼으로. 누르면 입력창이 "원래 질문 + 공백 + 선택지"가 되고 초점이 간다. 보내지 않는다 | analyze_api.md A7(다시 입력)을 넘지 않는 입력 보조. `ExplorePage.test.tsx` 1건 |
| D | 결과 대시보드 막대 옆 "N명" → "N" | 탐색 결과 선택지 통계와 같은 표기 |
| E | 결과 화면의 유형 이름표를 지우고 빌더 이름표를 `components/questionTypeLabels.ts`로 빼 함께 쓴다. 단일 선택 문항이 결과 화면에서도 "단일 선택" | 응답 화면 글자는 그대로 |
| E+ | 사람 요청(사람 확인 1번 중): 빌더 유형 목록 순서를 단답형 → 장문형 → 단일 선택 → 복수 선택 → 드롭다운으로 | 순서를 단언하는 기존 테스트 없음 |
| F | 서버 되묻기 문구(`ClarifyRules`)의 "인원 수" → "응답 수" 3곳 | 이 문구를 단언하는 테스트 없음 |

A가 막는 경우: "비용이 부담된다는 의견있어?"를 파서가 `topic` 요약으로 읽고 조건을 비우면, "의견있어"가 조건으로 되살아나 `unknown_condition`이 나던 것.
A가 유지하는 경우: `count`·`list`에서 조건을 비운 출력은 낱말을 되살려 되묻는다(조건 빠진 숫자 방지). 동작 없음은 그대로 `missing_operation`. 요약이어도 원문의 선택지 문구("3학년")는 조건으로 되살린다.

## §2 사람 결정과 승인된 테스트 변경

- 승인된 기존 테스트 변경(사람, 2026-11-09): `ResultsPage.test.tsx` 막대 옆 건수 기대값 5곳. `getByText('3명')`·`'1명'`·`'2명'`·`'2명'`·`'3명'` → `'3'`·`'1'`·`'2'`·`'2'`·`'3'`. 찾는 범위(`optionRow` 안)와 숫자는 그대로. 유형 글자를 단언하는 곳은 없어 바꾸지 않았다.
- 기존 테스트를 고치지 않으려고 `QueryText.reviveDroppedConditions(query, parsed)` 2인자 형태를 남겼다(`AnalysisServiceTest` 241행이 쓴다. 문항 정의 없이 보므로 요약에서는 아무것도 되살리지 않는다).
- 공용 이름표 파일은 AGENTS의 "공통 파일은 작은 PR 먼저" 대신 지시 E 범위로 이 PR에 넣었다.

### 남은 미룸 (별도 PR 후보, 이슈는 만들지 않음)

| # | 내용 | 비고 |
|---|---|---|
| 1-1 | "응답 받기 끄기" 버튼 글자 | 사람 결정 대기 |
| 5 | 문항마다 가장 많은 응답의 막대 색 | 사람 결정 대기 |
| B | 버튼(이번 지시에서 제외) | - |
| - | 동작 없음 되묻기에서 "요약"을 고르면 되살린 낱말("비용이" 등)로 `unknown_condition`이 날 수 있다 | 지시 A (2)대로 유지. 사람 확인에서는 보이지 않았다 |

처리되어 미룸에서 뺀 것: 유형 순서 2(사람 요청으로 E+), 서버 되묻기 문구 "인원 수"(F).

## §3 회차와 자동 검사

| 회차 | 결과 |
|---|---|
| 기준 | `gradlew cleanTest build` 통과, 테스트 662건·skip 0·실패 0, 분석 골든 22/22. frontend lint·typecheck 통과, 25파일 339건 |
| 구현 뒤 | build 통과, 666건·skip 0·실패 0(새 4건), 분석 골든 22/22. frontend lint·typecheck 통과, 340건(새 1건) |
| 순서 변경 뒤 | lint·typecheck 통과. 전체 테스트는 백엔드·Vite가 함께 떠 있을 때 시간 초과(5초)로 실패가 회마다 달랐다(20건 → 2건, `DraftGate` 등 바뀌지 않은 파일). 그 파일들과 빌더·결과 시험을 따로 돌리면 76건 통과. PR CI로 다시 확인 |

## §4 사람 확인 (실제 OpenAI, 2026-11-09, DB `passform_e3`)

| # | 결과 |
|---|---|
| 1 막대 숫자만, 유형 이름 빌더와 같음 | 통과. 확인 중 유형 목록 순서 변경 요청 → E+로 반영 |
| 2 "비용이 부담된다는 의견있어?" 2~3회 | 통과. "의견있어"를 못 찾았다는 되묻기가 다시 나오지 않음 |
| 3 "남학생 몇 건이야?" → 후보 선택지 누르기 | 통과. 입력창 끝에 붙고 보내지 않음 |
| 4 "3학년 몇 건이야?" | 통과 |

## §5 프롬프트

```
이번 작업은 탐색·결과 화면 보완 4건뿐이다(E8 #46의 후속, 관련 #46). 다른 이슈는 구현하지 않는다. 나는 ye-nni이고 리뷰어는 Psoyeon1117이다. 셸은 Windows PowerShell이다.

# 비용 규칙 (주간 한도가 빠듯하다. 지킨다)
- 서브에이전트를 띄우지 않는다. SPEC 전체를 읽지 않는다. 아래 지정한 곳만 읽는다. 새 라이브러리 없음. 단계마다 로컬 커밋. 최종 보고 30줄 안팎.

# 시작 확인 (하나라도 어긋나면 멈추고 알린다)
1. gh pr view 94로 PR #94(E8)가 머지됐는지 본다. 아니면 구현하지 말고 멈춘다.
2. git status로 추적 파일 변경이 없는지 본다(미추적 docs/SPEC_followups.md는 정상). git fetch origin, git switch main, git merge --ff-only origin/main, 브랜치 fix/explore-followups를 만든다.
3. DB는 실행 중인 opinio-db-1을 쓴다(docker compose를 새로 띄우거나 내리지 않는다. 다른 세션과 동시에 gradle 테스트를 돌리지 않는다). .\gradlew.bat cleanTest build와 frontend npm run lint·typecheck·test(VITE_USE_API=false)가 지금 통과하는지 본다. npm ci는 돌리지 않는다.

# 먼저 읽을 것 (이곳만)
AGENTS.md(3절 3·4항, 5절), docs/SPEC.md 334행(파서 출력 보정)과 clarify 표(336행부터), docs/api/analyze_api.md 4~6절, src/main/java/com/opinio/passform/service/analysis/QueryText.java·AnalysisService.java(보정 호출부), src/test/java/.../service/analysis/DroppedOptionRevivalTest.java, tests/harness/golden_cases.yaml의 analysis_cases 이름 목록, frontend/src/pages/ExplorePage.tsx·ResultsPage.tsx·ResultsPage.test.tsx, frontend/src/explore/exploreView.ts, frontend/src/builder/QuestionEditor.tsx(유형 이름표), docs/prompts/delegation_explore_screen.md(기록 형식·미룸 표).

# 절대 규칙
- 판정 기준(tests/harness, data/seed, 두 스키마 폴더, src/response_analysis/prompts, config/rag.yaml, evals/evalset.jsonl)을 고치지 않는다. 기존 테스트의 단언·기대값은 아래 "승인된 기존 테스트 변경"만 바꾼다. 그 밖에 깨지면 멈추고 묻는다.
- 숫자를 지어내지 않는다(AGENTS 3절 3항). A를 고치면서 조건이 빠진 숫자가 나가는 경로를 새로 만들지 않는다. 확정할 수 없으면 되묻는다(3절 4항).
- 화면에 내부 용어(q1, v1, response_id, "판정 불가", reason 원문)를 쓰지 않는다. TypeScript strict, any 금지.
- 커밋·PR에 에이전트 이름이나 생성 문구 금지. PR 본문에 사람을 @로 언급하지 않는다. _local/과 docs/SPEC_followups.md는 커밋하지 않는다. git add는 경로를 명시한다.

# 승인된 기존 테스트 변경 (사람, 2026-11-09)
- frontend/src/pages/ResultsPage.test.tsx: 막대 옆 건수 기대값 "3명"·"2명" 등을 숫자만("3"·"2")으로 바꾼다. 찾는 범위(optionRow 안)와 숫자는 그대로 둔다. 숫자만으로는 같은 행에서 여러 요소가 잡히면 찾는 방식만 좁힌다(단언을 빼지 않는다).
- 유형 이름 통일로 ResultsPage.test.tsx에서 결과 화면의 유형 글자를 단언하는 곳이 있으면 빌더 이름표의 글자로 바꾼다. 다른 테스트 파일(ResponsePage.screen.test.tsx 등)은 바꾸지 않는다.

# 할 것
A. 되살리기(서버 QueryText.reviveDroppedConditions): 파서 conditions가 비고 operation이 summarize이며 topic이나 target이 있으면, 남은 낱말을 조건으로 되살리지 않는다(선택지 문구 되살림 reviveDroppedOptions의 규칙은 그대로). "있어·있나요·있니·있는지·있을까·있나" 같은 끝말은 일반어처럼 떼어 본다. count·list에서 조건을 비운 출력은 지금처럼 되살려 되묻는다(조건 빠진 숫자 방지는 유지). 일반 테스트: (1) "비용이 부담된다는 의견있어?" + {conditions:[], operation:"summarize", topic:"비용이 부담된다는"} → 되살린 조건 없음, (2) 같은 query + {conditions:[], operation:null} → 그대로 missing_operation 판정으로 감, (3) "남학생 몇 명" + {conditions:[], operation:"count"} → 지금처럼 되살려 되묻기, (4) "3학년 학생들의 걱정되는 점 요약해줘" + {conditions:[], operation:"summarize", target:"걱정되는 점"} → "3학년"은 선택지라 되살림(폼에 3학년 선택지가 있을 때). 분석 골든 22건은 그대로 통과해야 한다.
C. 되묻기 후보 누르기(화면): unknown_condition 되묻기에서 서버가 준 후보 문항의 선택지를 누를 수 있게 한다. 누르면 입력창에 원래 질문이 들어 있고 그 끝에 " <선택지>"가 붙으며 입력창에 초점이 간다. 바로 보내지 않는다(사람이 고쳐서 보낸다). analyze_api.md A7("문구로 알리고 다시 입력")을 넘지 않는 입력 보조다. 일반 테스트 1건.
D. 막대 건수(화면 ResultsPage OptionRow): "6명" → "6". 단위 없이 숫자만. 탐색 결과의 선택지 통계와 같은 표기.
E. 유형 이름 통일(화면): ResultsPage가 따로 가진 유형 이름표를 지우고 빌더의 이름표(QuestionEditor.tsx의 것)를 공용으로 빼서 함께 쓴다. 결과 화면에서 단일 선택 문항이 빌더와 같은 이름으로 보인다. 응답 화면 등 다른 화면의 글자는 바꾸지 않는다.
F. 서버 되묻기 문구(ClarifyRules.java 44·50·54행)의 "인원 수"를 "응답 수"로 바꾼다. 화면 버튼 표기와 맞춘다. 이 문구를 단언하는 테스트가 있으면 고치지 말고 멈춰 묻는다.

# 사람 확인 (push 전에 멈춘다)
백엔드는 사람이 자기 PowerShell에서 OPENAI_API_KEY를 그 창에만 넣고 DB_NAME=passform_e3로 띄운다(세션은 키 자리를 비운 명령 모양만 준다). 5173이 떠 있으면 그대로 쓴다. 항목마다 무엇을 하면 무엇이 보여야 통과인지 적는다. 실제 질문은 5회 안팎.
1. 결과 대시보드 막대 옆이 숫자만이고, 유형 이름이 빌더와 같다.
2. "비용이 부담된다는 의견있어?"를 2~3번 묻는다 → "의견있어"를 못 찾았다는 되묻기가 다시 나오지 않는다(요약이나 동작 고르기 되묻기는 정상).
3. 없는 낱말로 조건을 묻는다(예: "남학생 몇 건이야?") → 되묻기 후보의 선택지를 누르면 입력창 끝에 붙고 보내지는 않는다.
4. "3학년 몇 건이야?" 같은 기존 질문이 그대로 된다.

# 완료 기준
- .\gradlew.bat cleanTest build 통과, XML에서 전체·skip·실패 수. 분석 골든 22건 통과. frontend lint·typecheck·test 통과(건수).
- 사람 확인 4개 결과.
- git diff origin/main...HEAD --name-only가 QueryText(와 필요한 analysis 서비스 파일), 새·기존 일반 테스트(승인 범위), frontend/src, docs/prompts의 기록뿐.
- docs/prompts/delegation_explore_followups.md에 "사람이 검토해 발행", 이 프롬프트, 사람 결정, 승인된 테스트 변경 전후, 사람 확인 결과, 남은 미룸(버튼 글자 1-1, 유형 순서 2, 막대 색 5, 서버 되묻기 문구 "인원 수", B 버튼)을 짧게.
- PR CI build·frontend 통과.

# 중단 조건
- 같은 실패로 3회차를 넘길 때, 3시간을 넘길 때(사람 답 기다린 시간 제외). SPEC·AGENTS에 없는 판단, 골든이 깨질 때, 승인 밖 기존 테스트를 고쳐야 할 때. 객관식으로 묻고 추천안을 첫 번째에 둔다.

# 커밋과 보고
- 커밋 "{tag}: 한글 메시지 (#46)", 50자 이내, 파일명·디렉터리명 금지.
- 사람 확인 뒤 push, PR 제목 "[fix] 탐색·결과 화면 보완 (#46)", 본문 README 형식, "관련 #46", --reviewer Psoyeon1117. 본문에 승인된 기존 테스트 변경과 이유, A가 막는 경우와 유지하는 경우를 적는다.
- PR을 연 뒤 CI를 확인하고 멈춘다. 머지하지 않는다. 사람이 띄운 서버는 사람이 끈다.
- 보고: 실행한 명령과 결과 / 할 것 A·C·D·E별 결과 / 승인된 테스트 변경 전후 / 사람 확인 결과 / 지시와 다르게 한 것 / PR 링크와 CI.
```
