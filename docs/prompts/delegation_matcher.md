# 매처와 조건 평가 연결 위임 기록 (#45)

기록 대상: E7 매처와 조건 평가 연결. 실행일 2026-11-08. 사람이 검토해 발행한 프롬프트로 진행했다. 프롬프트는 §7에 그대로 두고, #89 머지 뒤의 후속 지시는 §6에 요약한다.

## §1 AC 상태 (SPEC은 고치지 않고 여기에만 적는다)

| AC | 이 작업 뒤 상태 | 근거 |
|---|---|---|
| AC21 | 미충족 유지. 규칙 매처 + 임시 프롬프트 LLM 매처 운영, 재도전·evals 미룸 | §6 |
| AC22 | 일부 구현. `parser_partially_dropped_condition_clarify`, `question_wording_not_dropped_condition` 통과. `unknown_sort_field` 골든은 아직 없음 | §2 |

## §2 골든 통과 이유

| 케이스 | 이유 |
|---|---|
| `branch_skipped_condition_not_evaluable` | 서버가 q5 답으로 경로를 다시 계산해 rb05·rb08·rb13은 q6(s2)을 건너뛴다. 그 응답에서만 q6 매칭을 빼 버전마다 1건 판정 불가, rb09는 q6을 지나 비워 거짓, 팀장 4건 |
| `branch_visited_blank_is_false` | 같은 경로 계산으로 rb09는 거짓을 `not`이 뒤집어 일치, 건너뛴 3건은 판정 불가로 남는다. 혼합 부정 판정에서 문항 문구 낱말("맡은 역할이")을 조건으로 세지 않아 `missing_match`로 가지 않는다 |
| `parser_partially_dropped_condition_clarify` | 원문의 "3학년"이 응답 보유 버전의 선택지이고 파서 조건에 없어 되살린다. 조건 2개, `match` 없음이라 표 5행 `["all","any"]`, 파서 1회 |
| `question_wording_not_dropped_condition` | "학년이"는 선택지가 아니라 되살리지 않고 "3학년"은 이미 조건에 있다. 조건 1개라 되묻지 않고 ra01·ra09 2건, 판정 불가 0 |

분석 골든 22건 통과, skip 0. 기존 골든 결과는 그대로다.

## §3 회차

| 회차 | 결과 | 원인 | 고친 것 |
|---|---|---|---|
| 기준 | 분석 골든 22건 중 skip 3, 실패 0 | - | - |
| 2단계 1회 | `branch_visited_blank_is_false` 실패(`missing_match`) | 혼합 부정 판정이 문항 문구를 긍정 조건으로 셈 | 문항 문구 낱말 제외(`QueryText`) |
| 4단계 1회 | 새 정렬 테스트 6건 `invalid_context` | 스키마가 `sort.order`를 필수로 둠 | 테스트 요청에 `order`를 넣음(구현 변경 없음) |
| 최종 | `.\gradlew.bat cleanTest build` 성공, 487건·skip 0·실패 0 | - | - |

## §4 사람 결정과 범위

- 2026-11-08: `AnalysisServiceTest`의 자리표시 테스트 `정렬_목록은_다른_순서를_내지_않고_실패한다`만 지운다.
- 2026-11-08: 정렬은 버전 사이 선택지가 글자·순서까지 같을 때만 한다(추가만 돼도 `unsortable_field`). `sort_unavailable_by_version`에는 결과 목록에 응답이 있는 버전만 넣는다.
- 5단계(LLM 매처): 1차 세션에서는 #89 미머지로 멈췄고, #89 머지(2026-11-08 21:01, `0200bf9`) 뒤 이어서 했다(§6).
- 미룸: AC21 충족과 재도전, 실제 OpenAI 왕복 확인(E8), 탐색 화면(E8), summarize(T7), 매처 프롬프트 수정, evals(E11).

## §5 해석과 시험하지 않은 것

- `sort.by` 미매칭의 후보는 SPEC 표 6행대로 선택형 문항 목록이다. 다른 뜻의 여러 문항이면 표 7행 `ambiguous_match`로 후보 문항을 낸다.
- 숫자 단답은 `[+-]?\d+(\.\d+)?`, 날짜는 `input_format.kind: date`의 `YYYY-MM-DD`만 정렬한다. 판정은 그 버전의 제출 응답 전체 값으로 한다.
- 선택지 되살림은 낱말 경계(앞은 글자가 아님, 뒤는 끝·공백·문장부호·조사)에서만 한다.
- 시험하지 않음: `sort.by`의 `unknown_condition`·`ambiguous_match`, 분기와 정렬의 조합, "문항 문구 - 선택지" 형태의 규칙 매칭, 운영 빈 조합(DB 테스트의 컨텍스트 기동으로만 확인).

## §6 main 합치기와 5단계 (2026-11-08, 사람 후속 지시)

후속 지시: #89 머지 뒤 `git merge origin/main`, 충돌은 양쪽 기능을 모두 살려 풀고, 5단계를 처음 프롬프트대로 한다. #89의 `LlmClient`를 쓰고 새 HTTP 클라이언트를 만들지 않는다.

합치기 충돌과 푼 방식

| 파일 | 푼 방식 |
|---|---|
| `AnalysisService` | 필드는 #89의 `SummaryService`와 이 PR의 `PathAwareEvaluation`을 함께 둠. 결과 처리는 경로 반영 평가 뒤 summarize면 #89의 `summaryResult`, 아니면 정렬. 대기 단계가 모두 구현돼 `failIfStepPending`을 지움 |
| `AnalysisServiceTest` | #89가 바꾼 요약 테스트를 살리고, 사람이 승인한 정렬 자리표시 테스트 삭제를 유지 |
| `AnalyzeView`(자동 병합 뒤 수정) | `CoverageView`에 #89의 `summary_unavailable_by_version`을 더해 정렬·요약 범위를 함께 냄 |
| `AnalysisGoldenTest`(자동 병합) | 빈 skip 집합·이름·메시지와 #89의 `mock(SummaryService.class)`가 함께 남음 |
| `ListSortTest`(이 PR의 새 파일) | 생성자에 `mock(SummaryService.class)` 추가 |
| `AnalysisStepPendingException` | 쓰는 곳이 없어져 지움 |

합친 뒤 `cleanTest build` 성공: 564건, skip 0, 실패 0(`SummaryGoldenTest` 4건 포함).

5단계 결과

- `LlmConditionMatcher`: `match_conditions.md` 마커 안 프롬프트(줄 전체 마커, `PromptData.promptOf`), `{numbering_rule}`은 팀 결정 (가)의 `latest` 문구. 입력은 조건 문구와 문항 번호·문구·유형·선택지뿐이고 출력은 조건·버전별 enum(strict). 허용 밖 값과 `NONE`은 버린다.
- 운영 빈: `RuleFirstConditionMatcher(LlmConditionMatcher)`. `PendingConditionMatcher`는 운영 빈이 아니고 기존 service 테스트용으로 남겼다.
- 키가 없을 때: 빌드·테스트는 돈다. 호출 시 `OpenAiClient`가 `LLM_UNAVAILABLE`을 던져 파서와 같은 503이다(`LlmConditionMatcherTest`로 확인).
- 일반 테스트: 허용 밖 값 버림, 응답 값(전화번호·장문 답)이 요청 본문에 없음, 규칙으로 푼 조건은 보내지 않음.
- 최종 `cleanTest build` 성공: 568건, skip 0, 실패 0. 실제 OpenAI 왕복은 하지 않았다(E8).
- 한계: `latest_number`는 넘겨받은 버전 중 가장 큰 버전 기준이다. 번호 조건은 규칙으로 풀리지 않아 모든 응답 보유 버전과 함께 가므로 응답 보유 최신 버전이 기준이 되고, 응답 없는 더 새 배포 버전은 보지 않는다.

코덱스 검토 반영 (2026-11-08, 읽기 전용 검토 4건을 프롬프트 세션이 확인, `DroppedOptionRevivalTest`에 1건씩 추가, `cleanTest build` 572건·skip 0·실패 0)

- 혼합 부정: 문항 문구 거르기가 부정어("제외 사유"의 "제외")까지 지워 수를 셌다. 부정어는 거르지 않고 부정으로 센다.
- 되살림 거르기: 조건 문자열 포함 여부 대신 원문에서 조건·target·topic·sort.by가 나온 자리 안인지로 거른다("기획팀 기획인" → "기획" 되살림).
- 낱말 끝: 공백뿐 아니라 문장부호·기호에서도 끊는다("소프트웨어학과,3학년" → "소프트웨어학과" 되살림).
- 일반어: "수"·"명"·"몇" 등 이 파일의 일반어와 같은 선택지는 되살리지 않는다(요일 선택지 "수"가 있어도 "학생 수"는 조건이 아니다).

## §7 위임 프롬프트 원본

````text
이번 작업은 이슈 #45(E7 매처와 조건 평가 연결)뿐이다. 다른 이슈는 구현하지 않는다. 나는 ye-nni이고 리뷰어는 Psoyeon1117이다. 셸은 Windows PowerShell이다.

# 목표
분석 요청(POST /forms/{form_id}/analyze)에서 조건이 있는 count·list가 실제로 계산되게 한다. 글자가 같은 조건은 규칙으로 매칭하고, 못 푼 조건만 LLM 매처로 보낸다. 분기로 건너뛴 문항은 판정 불가로, list의 sort를 SPEC대로 처리하고, 파서가 조건을 일부 버리면 원문에서 되살린다. 지금 skip인 분석 골든 3건을 통과시킨다.

# 비용 규칙 (주간 한도가 빠듯하다. 지킨다)
- 서브에이전트를 띄우지 않는다. SPEC, ARCHITECTURE 전체를 읽지 않는다. 아래 지정한 곳만 읽는다.
- 새 라이브러리를 더하지 않는다. 일반 테스트는 아래 목록만 쓴다. 단계마다 로컬에 커밋한다.
- 위임 기록과 PR 본문은 짧게, 최종 보고는 40줄 안팎으로 쓴다.

# 작업 폴더 (E5 세션이 C:\Opinio의 브랜치를 계속 쓸 수 있어 worktree를 쓴다)
1. C:\Opinio에서 git status로 추적 파일 변경이 없는지만 본다(미추적 docs/SPEC_followups.md는 정상). C:\Opinio의 브랜치는 바꾸지 않는다.
2. git fetch origin 뒤 git worktree add C:\Opinio-e7 -b feat/matcher-connect origin/main. 이후 모든 작업은 C:\Opinio-e7에서 한다.
3. C:\Opinio\.env를 C:\Opinio-e7\.env로 복사한다(내용을 출력하지 않는다). 이 복사본은 지우지 않는다. worktree는 머지 뒤 내가 지운다.
4. DB는 이미 실행 중인 opinio-db-1(C:\Opinio의 compose)을 쓴다. C:\Opinio-e7에서 docker compose up을 하지 않는다. docker ps로 opinio-db-1이 healthy인지 본다. 아니면 멈추고 묻는다. 다른 세션과 동시에 gradle 테스트를 돌리지 않는다(테스트가 passform_test 스키마를 비운다). 다른 세션이 테스트 중인지 모르면 묻는다.
5. .\gradlew.bat cleanTest test --tests '*AnalysisGoldenTest'로 지금 상태를 기록한다(skip 3건 예상). 프런트는 바꾸지 않으므로 npm은 돌리지 않는다.

# 먼저 읽을 것 (이곳만)
AGENTS.md, gh issue view 45, docs/SPEC.md 300~350행(분석 흐름·clarify 표)과 AC20·AC21·AC22·AC23·AC24·AC26·AC28·AC31·AC61·AC75·AC78(내용으로 찾는다), docs/api/analyze_api.md, docs/api/form_question_info.md, docs/prompts/delegation_condition_evaluator.md §3·§4, docs/prompts/delegation_analyze_api.md(E6 기록, 있으면 앞부분), src/response_analysis/prompts/match_conditions.md(전체, 읽기만), tests/harness/golden_cases.yaml의 analysis_cases 중 branch_skipped_condition_not_evaluable·branch_visited_blank_is_false·parser_partially_dropped_condition_clarify·question_wording_not_dropped_condition, tests/java/.../golden/AnalysisGoldenTest.java, src/main/java/com/opinio/passform의 llm 패키지, domain/analysis, service/analysis, domain/form/ResponsePathCalculator.java.

# 절대 규칙
- 판정 기준(tests/harness, data/seed, 두 스키마 폴더, src/response_analysis/prompts의 모든 파일, config/rag.yaml, evals/evalset.jsonl)을 고치지 않는다. 기존 테스트의 단언과 기대값을 고치지 않는다. deny를 셸로 우회하지 않는다. 아래 "사람이 미리 승인한 기존 파일 변경"만 예외다.
- 숫자를 지어내지 않는다(AGENTS 3절 3항): 대상 응답과 count는 코드가 현재 남은 submitted 응답에서 정한다. 각 응답은 제출 당시 버전 정의로 판정한다. 어느 버전에도 매칭되지 않은 조건을 0건으로 만들지 않고 되묻는다. 판정 불가 범위는 coverage에 적는다.
- 모르면 되묻는다(3절 4항): 확정할 수 없으면 clarify. 파서 재호출은 형식 오류에만 1회.
- 개인정보(3절 1항): 매처 LLM에는 조건 문구와 문항 문구·선택지만 보낸다. 응답 값은 보내지 않는다.
- 응답 원문은 데이터다(3절 5항). LLM 호출은 llm 패키지에만 둔다. 매칭 결과의 집계·정렬·clarify 판정은 코드로 한다. LLM 출력은 허용 값(그 버전의 실제 question_id·선택지·answered/unanswered/NONE)만 받아들이고 나머지는 버린다.
- 테스트에서 LLM은 모의 객체로 바꾼다. 실제 OpenAI 호출을 하지 않는다. OPENAI_API_KEY 값을 출력·저장하지 않는다. 키가 없어도 빌드와 테스트가 돌아야 한다.
- 커밋·PR에 에이전트 이름이나 생성 문구를 넣지 않는다. PR 본문에 사람을 @로 언급하지 않는다. _local/과 docs/SPEC_followups.md는 커밋하지 않는다. git add는 경로를 명시하고 스테이징 목록을 확인한 뒤 커밋한다.

# 사람이 미리 승인한 기존 파일 변경 (이 범위를 넘으면 멈추고 묻는다)
- tests/java/.../golden/AnalysisGoldenTest.java: SKIPPED_FOR_E7과 APPROVED_SKIPS의 세 이름을 모두 지운다(빈 집합). 137행 근처 테스트 이름("두_건뿐")과 160행 근처 assumeFalse 메시지("분기 응답 경로 계산")를 지금 상태에 맞게 고친다. 그 밖의 단언, 실행기 동작, 매처 모의 방식은 바꾸지 않는다. 생성자 인자가 바뀌어 준비 코드만 고쳐야 하면 그것도 허용한다.
그 밖의 기존 테스트가 깨지면 고치지 말고 멈춰 묻는다.

# 구현 범위 (이 순서로, 단계마다 커밋)
1. 규칙 매처: 조건 글자가 그 버전 선택지와 같으면 option, "문항 - 응답/미응답" 형태로 문항이 글자로 정해지면 answered/unanswered. 못 푼 (조건, 버전)만 다음 단계로 넘긴다. ConditionMatcher 시그니처는 바꾸지 않는다.
2. 평가 연결: 매칭 결과를 ConditionEvaluator에 연결해 조건 있는 count·list를 계산하고 matched·coverage를 채운다. 분기 건너뜀은 평가 함수 밖에서 ResponsePathCalculator로 각 응답의 경로를 다시 계산해, 경로 밖 문항의 조건은 판정 불가, 경로 안에서 비운 문항은 false로 둔다(골든 두 건).
3. 일부 버림 되살림(SPEC 334행): 파서 출력 스키마 재검증 뒤 clarify 표 3행 판정 전에 코드로 보정한다. 원문 query에 글자 그대로 있는데 파서 conditions 어디에도 들어가지 않은 선택지 문구(모든 응답 보유 버전의 선택지)를 조건으로 되살린다. 문항 문구·일반어를 되살리지 않는다(question_wording_not_dropped_condition이 이것을 지킨다). 되살린 뒤 표대로 판정한다(조건 2개 이상인데 match 없으면 missing_match).
4. list 정렬: SPEC 311행, AC28, AC61, AGENTS 5절. sort.by 매칭(규칙 매처와 같은 방식), 정렬 불가는 unsortable_field, 어느 버전에도 없으면 unknown_condition, 빈 값·그 버전에 없는 문항은 맨 뒤, 같으면 response_id 오름차순, count·summarize의 sort는 무시. 일부 버전만 정렬 불가인 범위는 coverage의 sort_unavailable_by_version.
5. LLM 매처: 여기서 gh pr view 89로 PR #89(T7 AI 요약)가 머지됐는지 본다. 머지됐으면 git merge origin/main으로 합친 뒤 #89의 llm/LlmClient·OpenAiClient를 써서 match_conditions.md 마커 안 프롬프트로 못 푼 조건만 보낸다. 프롬프트는 줄 전체가 마커인 줄로 읽는다(OpenAiQueryParser.java:44와 같은 방식). {numbering_rule}은 SPEC과 docs/spikes/condition_matching_prompt.md에서 정한 문구로 채운다. 운영 빈을 PendingConditionMatcher에서 규칙+LLM 매처로 바꾼다. #89가 머지되지 않았으면 1~4를 커밋하고 멈춰 보고한다(5단계를 별도 클라이언트로 만들지 않는다).

# 이 PR에서 하지 않는다 (미룸)
AC21 충족(스파이크 미달, 재도전 미룸), 실제 OpenAI 왕복 확인(E8), 탐색 화면(E8), summarize(T7), 매처 프롬프트 수정(판정 기준), evals(E11).

# 일반 테스트 (이것만)
규칙 매처: 같은 글자 option, "문항 - 미응답", 버전마다 다른 선택지, 못 푼 조건만 넘김. LLM 매처(5단계를 했을 때만): 허용 밖 값 버림, 응답 값이 요청 본문에 없음, 규칙으로 푼 조건은 보내지 않음. 되살림: 선택지 문구 되살림, 이미 들어간 조건의 일부 글자는 되살리지 않음. 정렬: AC28의 다섯 테스트 문장, AC61의 빈 값 맨 뒤와 동률 순서.
여기 없는 경우가 필요해 보이면 더하지 말고 PR에 "시험하지 않음"으로 적는다.

# 완료 기준
- .\gradlew.bat cleanTest build 통과(커버리지 검사 포함). AnalysisGoldenTest skip 0, 기존 골든 결과 그대로. 전체 건수와 skip 수를 build/test-results의 XML에서 세어 보고한다.
- 각 골든(새로 통과한 3건과 question_wording_not_dropped_condition)이 왜 통과하는지 한 줄씩 위임 기록에 적는다.
- git diff origin/main...HEAD --name-only가 src/main, 새 src/test 파일, AnalysisGoldenTest(승인 범위), docs/prompts의 새 기록뿐이다.
- docs/prompts/delegation_matcher.md에 "사람이 검토해 발행", 이 프롬프트, 회차, 사람 결정, 골든 통과 이유, 5단계 여부, 미룸을 짧게 기록한다. 위임 기록 문서 상단의 AC 표(SPEC의 AC21·AC22 행 상태)는 SPEC을 고치지 말고 기록에만 적는다.
- PR의 CI에서 build와 frontend job이 통과한다.

# 중단 조건 (멈추고 보고한다)
- 같은 실패로 3회차를 넘길 때, 작업이 4시간을 넘길 때. 사람의 답을 기다린 시간은 빼고 잰다. 지금까지 한 것을 커밋하고 어디서 막혔는지 쓴다.
- SPEC·AGENTS에 없는 판단이 필요할 때(예: 되살림 범위가 골든 note와 부딪힘, 판정 불가와 false의 경계), 골든을 통과하려면 판정 기준·실행기의 매처 모의 방식을 바꿔야 할 때, 위 승인 범위 밖의 기존 테스트를 고쳐야 할 때, 5단계에서 #89가 머지되지 않았을 때. 객관식으로 묻고 추천안을 첫 번째에 둔다. 한 번에 묶어서 묻는다.

# 커밋과 보고
- 커밋 "{tag}: 한글 메시지 (#45)", 50자 이내, 파일명·디렉터리명 금지.
- 자동 검사가 통과하면 push 전에 다시 묻지 않고 올린다. 범위를 넘으면 push하지 말고 멈춘다. amend와 force push를 하지 않는다. PR 전에 main이 바뀌었으면 merge로 합친다(#89·#90과 겹치면 충돌을 풀고 무엇을 어떻게 풀었는지 보고).
- PR 제목 "[feat] E7 매처와 조건 평가 연결 (#45)", 본문은 README 형식, 관련 이슈 "Closes #45"(5단계를 못 했으면 Closes 대신 "관련 #45"와 남은 일), --reviewer Psoyeon1117. 다른 이슈가 알아야 할 것을 짧게 적는다: E8(실제 OpenAI 왕복 확인할 것, clarify 사유별 화면), T8(matched·coverage 형태), T11(내보내기의 list 순서).
- PR을 연 뒤 CI를 확인하고 멈춘다. 머지하지 않고 다음 이슈로 넘어가지 않는다. .env 복사본과 worktree는 지우지 않는다.
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 골든별 통과 이유 / 지시와 다르게 한 것 / 확인하지 못한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
````
