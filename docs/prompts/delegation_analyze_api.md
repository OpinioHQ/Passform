# 분석 API 위임 기록 (#44)

기록 대상: E6 분석 API와 되묻기. 실행일 2026-11-07. 사람이 검토해 발행한 프롬프트로 진행했다. 프롬프트는 §5에 그대로 둔다.

## §1 필수 골든 15건이 통과하는 이유

| 케이스 | 이유 |
|---|---|
| `unknown_condition_all_versions` | 매처 결과가 비어 표 6행. 응답이 있는 버전의 선택형 문항을 버전·문항 순서로 낸다 |
| `ambiguous_single_choice` | 한 버전의 `dropdown`에 서로 다른 선택지 둘이 `all`로 걸려 표 7행 전자, `["any"]` |
| `checkbox_all_ok` | `checkbox`는 단일 선택 검사에서 빠진다. `ConditionEvaluator`가 rb06, rb07을 낸다 |
| `ambiguous_question_meaning_across_versions` | 같은 조건이 `question_id` q1, q7에 걸려 표 7행 후자, 매칭 순서로 후보를 낸다 |
| `missing_match_two_conditions` | 조건 2개에 `match`가 없고 원문에 부정 표현이 없어 `["all", "any"]` |
| `missing_operation` | `operation`이 null이고 AI 요약이 켜져 세 동작을 낸다. 재호출하지 않는다 |
| `unsupported_statistics_not_mapped_to_summary` | 원문의 "통계"를 코드가 먼저 보고 파서의 `summarize`를 실행하지 않는다 |
| `double_negation_unanswered` | `unanswered` 매칭과 `match: not`이 함께 와 표 8행, 문장은 문항 문구로 만든다 |
| `extra_key_retry_once` | `group_by`가 스키마 위반이라 1회 재호출, 다시 실패해 `parse_failed`, 파서 2회 |
| `confirmed_context_skips_parser` | `{context}`는 파서를 건너뛰고 v1·v2 5건 일치, 문항 없는 v3 4건은 coverage로 간다 |
| `natural_language_version_scope_rejected` | "버전 1"을 파서 전에 잡아 파서 0회, `context` null |
| `all_responses_count_supported` | 일반어만 있어 되살릴 표현이 없고, 저장소가 준 제출 응답 14건을 센다 |
| `parser_dropped_condition_not_counted` | "수의학과"를 되살려 매칭으로 보내고, 매칭이 없어 표 6행. 전체 건수로 나가지 않는다 |
| `unsummarizable_target_clarify` | "이름"이 q1과 문구 완전 일치하고 `profile_key`가 있어 표 10행. 후보는 설정값으로 고른 q8~q10 |
| `non_owner_analyze_403` | 유효한 CSRF 토큰을 넣어도 service 소유 확인이 403을 낸다. 소유 확인을 빼면 200이 된다(주입 5) |

skip 2건 `branch_skipped_condition_not_evaluable`, `branch_visited_blank_is_false`는 사람이 승인한 E7(#45) 몫이고 E7이 지운다.

## §2 실패·수정 회차

| 회차 | 실패 | 원인 | 수정 |
|---|---|---|---|
| 1 | 컴파일 오류 | 스텁 생성자 인자 수가 실행기와 달랐다 | 인자 추가 |
| 2 | 골든 9건 NPE | `Set.of().contains(null)`이 입력 형식 없는 문항에서 터졌다 | `instanceof`로 확인 |
| 3 | 파서 클라이언트 테스트 | `parse_query.md` 7행 설명문에도 마커 글자가 있어 `indexOf`로 찾으면 프롬프트가 "`와 `"만 남았다 | 마커만 있는 줄을 찾는다 |
| 4 | 1차 코드 검토 반영 4건. 테스트를 먼저 써서 고치기 전 실패를 확인했다 | ① 버려질 키에 담긴 표현을 복원하지 않아 전체 건수가 나갔다 ② 동작어 필터가 "수의학과만세어줘"를 통째로 지웠다 ③ 여러 낱말의 단일 부정을 혼합 부정으로 봤다 ④ `must_not_include`가 `response_ids` null이면 통과했다 | ① 그 동작에서 쓰이는 키만 인정 ② 알려진 동작어만 꼬리에서 뗀다 ③ 조건 하나의 부정이고 남은 낱말이 그 조건 안이면 혼합이 아니다 ④ 배열이 없으면 실패 |
| 5 | #78 머지로 PR이 충돌해 CI가 돌지 않았다 | `ErrorCode`, `ApiExceptionHandler`, `ApiExceptionHandlerTest`에 양쪽이 오류 코드를 더했다 | 사람 승인으로 `main`을 merge하고 양쪽 줄을 모두 남겼다. 기존 값은 바꾸지 않았다 |

**3회차의 영향:** `origin/feat/demo`의 `PromptLoader`가 같은 `indexOf` 방식이고 같은 7행이 있다. 코드와 파일 내용으로 보면 시험 데모의 실제 호출은 프롬프트 없이 나갔을 수 있다. 다시 실행해 확인하지는 않았다. 3-1절 출력 패턴과 프롬프트 v3 판단은 재측정 전에 확인이 필요하다.

## §3 결함 주입 (8건)

| # | 주입 | 대상 | 결과 |
|---|---|---|---|
| 1 | 스텁이 고정된 틀린 결과를 냄 | 실행기 | 14건 모두 실패. 상태, 파서 호출 수, 건수, 판정 불가 수에서 잡음 |
| 2 | 스텁이 스키마 위반 응답을 냄 | 실행기 | 14건 모두 응답 스키마 위반으로 실패 |
| 3 | 재호출을 2회로 늘림 | 구현 | `extra_key_retry_once` 실패 |
| 4 | 조건 되살리기를 끔 | 구현 | `parser_dropped_condition_not_counted` 실패(`ok`로 나감) |
| 5 | 소유 확인을 뺌 | 구현 | `non_owner_analyze_403`(403→200)과 DB 테스트 2건 실패 |
| 6 | `input_format` 개인정보 판정을 끔 | 구현 | `unsummarizable_target_clarify`의 `expect_options` 실패 |
| 7 | 목록 순서를 뒤집음 | 구현 | `checkbox_all_ok`의 `expect_response_ids` 실패 |
| 8 | 요약 꺼짐 판정을 조건 수에 묶음 | 구현 | 표 순서 테스트 실패 |

각 주입은 임시로 넣고 `--tests`로 좁혀 실행한 뒤 되돌렸다. 커밋하지 않았다.

## §4 사람 결정과 미룸

**사람 결정(프롬프트의 범위 경계):** 모의 매처로 골든 판정, 표 1~8행과 10행, 조건 없음·sort 없는 count·list까지, target은 문구 완전 일치, 개인정보·요약 가능은 설정값으로 판정, AI 요약 여부는 최신 배포 정의(배포 전이면 작업 정의), 소유 확인을 본문 판정보다 먼저. 이후 1차 코드 검토 반영 4건(§2 4회차)을 사람이 해당 행을 확인해 승인했다.

**구현 중 고른 것(SPEC에 답이 없어 PR에 적었다):**

- 표 7행 "뜻이 다른"은 `question_id`가 다른 것으로 본다. 같은 ID의 문구만 바뀐 경우("학년", "현재 학년")는 되묻지 않는다.
- 통계 요청은 조건을 되살리지 않고 파서 출력을 그대로 돌려준다. 되살리면 "통계"가 조건으로 들어간다.
- 부정 표현이 있는 원문의 조건을 되살릴 때는 한 덩어리로 둔다. 2개 이상을 되살리면 `match`를 지워 다시 묻는다.
- 본문에 `query`, `context` 밖의 키가 있거나 `query`가 비어 있으면 `invalid_context`다.
- 제출 응답이 없는 폼에 조건을 보내면 빈 후보의 `unknown_condition`이 된다. 시험하지 않았다(운영 매처는 지금 503이다).

**미룸:** 실제 매처(E7), 분기 건너뜀과 응답 경로 계산(E7), sort 있는 list(E7), 요약(T7), 실제 OpenAI 왕복 확인(E8), 매처 품질과 evals(E7, E11), 분석 화면(E8), 추가 골든.

## §5 발행한 프롬프트

```text
이번 작업은 이슈 #44(E6 분석 API와 되묻기)뿐이다. 다른 이슈는 구현하지 않는다. 나는 ye-nni이고 리뷰어는 Psoyeon1117이다. 셸은 Windows PowerShell이다.

# 목표
POST /forms/{form_id}/analyze의 {query}, {context}를 만든다. 범위 판정, 파서 호출과 재검증, 파서 출력 보정, 되묻기 판정, 조건 없음과 정렬 없는 count·list 결과까지다. 화면은 E8이 연결한다.

# 비용 규칙 (주간 한도가 빠듯하다. 지킨다)
- 서브에이전트를 띄우지 않는다. SPEC, ARCHITECTURE 전체를 읽지 않는다. 아래 지정한 행만 읽는다.
- 골든이 잡는 규칙을 일반 테스트로 중복하지 않는다. 일반 테스트는 아래 "일반 테스트" 목록만 쓴다. 테스트를 늘려 안심하지 않는다.
- 결함 주입은 8건 이내로 한다. 중간 실행은 --tests로 좁히고, cleanTest build는 시작과 마지막에만 돌린다.
- 단계가 끝날 때마다 로컬에 커밋한다. 세션이 한도로 끊겨도 이어 갈 수 있어야 한다.
- 위임 기록과 PR 본문은 짧게 쓴다. 최종 보고는 40줄 안팎으로 쓴다.

# 시작 확인 (하나라도 어긋나면 멈추고 알린다)
1. git status로 추적 파일 변경이 없는지 본다. 미추적 docs/SPEC_followups.md는 정상이다.
2. git fetch origin, git switch main, git merge --ff-only origin/main을 실행한다.
3. tests/harness/golden_cases.yaml에 이슈 #44의 필수 골든 15건이 있는지 본다. 14건은 analysis_cases, non_owner_analyze_403은 access_cases에 있다.
4. docker compose up -d db를 실행하고 저장소 루트의 .env를 확인한다. 없으면 만들지 말고 알린다. 비밀값을 출력하지 않는다.
5. .\gradlew.bat cleanTest build가 시작 시점에 통과하는지 본다. 같은 PC에서 다른 build를 동시에 돌리지 않는다.
6. main에서 브랜치 feat/analyze-api를 만든다.
7. gh pr view 78로 상태를 본다. 아래 "PR #78과의 겹침"을 따른다.

# 먼저 읽을 것 (이 행만)
AGENTS.md, gh issue view 44, docs/SPEC.md 235행, 305~316행, 334~349행, AC22·25·26·27·29·30·32·75·77·92(내용으로 찾는다), docs/ARCHITECTURE.md 179~195행과 202행, docs/api/analyze_api.md, src/response_analysis/schemas의 두 스키마, parse_query.md 상단 표와 마커, tests/harness/golden_cases.yaml 1~36행과 analysis_cases, access_cases의 non_owner_analyze_403, docs/spikes/2026-11-04_test_demo.md 3-1절과 3-3절, 3-4절, src/main/java/.../domain/analysis/ConditionEvaluator.java, Response.toResponseRecord, ResponseRepository, docs/prompts/delegation_form_definition.md 앞부분(기록 형식).
docs/api/analyze_examples.json과 origin/feat/demo를 기대값 근거로 쓰지 않는다. origin/feat/demo의 OpenAiClient, PromptLoader, ParsedContextRepair는 구조 참고만 된다. 재사용하면 PR에 적고, demo 패키지 이름을 가져오지 않는다.

# 절대 규칙
- 판정 기준(tests/harness, data/seed, 두 스키마 폴더, src/response_analysis/prompts, config/rag.yaml, evals/evalset.jsonl)과 domain/analysis의 잠긴 타입, ConditionEvaluator의 시그니처와 ConditionEvaluatorGoldenTest를 고치지 않는다. deny를 셸로 우회하지 않는다.
- 기존 테스트의 단언문과 기대값을 고치지 않는다. @Disabled나 skip으로 통과시키지 않는다. 아래에 적은 2건의 skip만 예외다. 코드를 더할 때 ApiExceptionHandlerTest의 EXPECTED 표에 줄을 더하는 것은 된다.
- 숫자를 LLM에 맡기지 않는다. 집계, 대상 응답, 되묻기 판정은 코드로 한다. 파서 출력은 request_context.schema.json으로 서버에서 다시 검증한다. 파서 재호출은 형식 오류에만 1회 한다. operation null은 스키마에 맞으니 재호출하지 않는다.
- LLM 호출은 llm 패키지에만 둔다. 파서에는 제작자의 질의만 보낸다. 응답 값을 보내지 않는다. 테스트에서 실제 LLM을 부르지 않는다.
- OpenAI 키는 OS 환경변수 OPENAI_API_KEY로만 읽고 파일, 로그, 출력에 쓰지 않는다. 키가 없어도 앱이 뜨고 빌드와 테스트가 통과해야 한다. 호출 실패와 키 없음은 503 llm_unavailable이다. 모델은 AGENTS 7절의 gpt-6-luna, reasoning effort none, temperature 0이다. 프롬프트는 parse_query.md의 마커 안을 그대로 쓴다.
- 현재 사용자는 CurrentUserService로 얻고, 현재 시각은 주입한 Clock으로만 얻는다. 인증 우회를 넣지 않는다. 엔티티를 API에 직접 쓰지 않는다. controller → service → repository 계층을 지킨다.
- message는 화면에 그대로 나간다. "판정 불가", q1, 응답 ID, v1을 쓰지 말고 문항 문구와 "폼 버전 1"로 쓴다.
- docs/SPEC.md, docs/ARCHITECTURE.md, AGENTS.md, frontend/를 고치지 않는다.
- 커밋·PR에 에이전트 이름이나 생성 문구를 넣지 않는다. PR 본문에 사람을 @로 언급하지 않는다. _local/과 docs/SPEC_followups.md는 커밋하지 않는다. git add는 경로를 명시하고 스테이징 목록을 확인한 뒤 커밋한다.

# 바꿔도 되는 것 / 안 되는 것
- 바꿔도 됨: src/main(Form.java 제외), src/test, tests/java의 새 AnalysisGoldenTest, docs/prompts의 새 기록 파일.
- tests/java의 AccessGoldenTest는 설정만 고쳐도 된다: 모의 파서·매처 bean, fixture 값 주입, 경로의 시드 폼 ID(form_b)를 UUID로 바꾸기. 단언, skip 규칙, 판정 키는 고치지 않는다. 이것으로 안 되면 멈추고 묻는다.
- 안 됨: 위 절대 규칙의 파일, Form.java(#78과 #75가 고친다. 소유·삭제 판정은 기존 getter로 한다), 머지된 마이그레이션.

# 범위 경계 (사람 결정)
이 PR에서 한다:
- 인터페이스 ConditionMatcher를 두고 골든은 모의 매처로 판정한다.
- clarify 표 1~8행과 10행을 표의 순서대로 판정한다. 6행과 7행 중 sort.by에 관한 부분과 9행은 E7이다.
- ConditionEvaluator를 연결해 조건 없음과 sort 없는 count·list 결과, matched(question_note 포함), coverage를 낸다.
- target은 문항 문구 완전 일치로 찾는다. 어느 버전에도 없으면 6행, 뜻이 다른 여러 문항이면 7행, 선택형·개인정보 문항이면 10행이다.
- 요약 가능 문항과 개인정보 문항은 AGENTS 3절 1항과 SPEC 316행의 설정값으로 판정한다. 문구로 판정하지 않는다.
- AI 요약 사용 여부는 첫 배포 뒤 잠기므로 최신 배포 정의에서 읽고, 배포 전이면 작업 정의에서 읽는다.
- UUID가 아닌 form_id는 404, 삭제된 폼은 404, 제작자가 아니면 403이다. 형식 오류 때문에 권한 판정이 앞서거나 뒤바뀌지 않게 소유 확인을 본문 판정보다 먼저 한다.

이 PR에서 하지 않는다(미룸):
- 실제 매처 구현(E7). 그때까지 운영 bean은 조건이 있으면 503 llm_unavailable을 낸다. 조건이 없으면 빈 매칭을 돌려준다. "매칭 없음"으로 돌려 unknown_condition이 되게 하지 않는다.
- 분기 건너뜀과 T3의 응답 경로 계산(E7). PR #75의 클래스를 가져오지 않는다.
- sort가 있는 list(E7), 되묻기를 통과한 summarize의 실제 요약(T7). 둘 다 이름이 구분되는 미구현 예외로 실패한다. 예외 메시지에 "not implemented"를 쓰지 않는다(AccessGoldenTest가 그 메시지를 skip으로 본다). 다른 순서나 빈 요약을 내지 않는다.
- 실제 OpenAI 왕복 확인(E8 화면 완료 때), 매처 품질과 evals(E7, E11), 분석 화면(E8), 추가 골든.

# 파서 출력 보정 (SPEC 334행)
all_responses_count_supported와 parser_dropped_condition_not_counted는 파서 출력이 같고 원문만 다르다. 조건이 빈 출력은 원문에서 동작어와 일반어(전체, 응답, 학생, 수, 명, 알려줘, 보여줘 등)를 뺀 뒤 남는 표현이 있으면 조건으로 되살려 매칭으로 보낸다. 판단이 애매하면 되묻는 쪽으로 기울인다. 틀린 숫자를 내는 것보다 낫다. 이 판정은 {query}에만 적용하고 {context}에는 적용하지 않는다.
parse_query.md나 판정 기준을 바꿔야만 풀리면 멈추고 묻는다.

# 골든 먼저
1. 구현 전에 analysis_cases만 읽는 tests/java의 AnalysisGoldenTest를 만든다. 실행 수준은 service이고 저장소, 파서, 매처는 모의 객체다(ARCHITECTURE G2, G3). 기대값은 YAML에만 둔다. 허용 키 밖의 키는 거부하고, 판정 키가 없거나 케이스가 비면 실패한다.
2. 모의 파서는 parser_outputs를 호출 순서대로 돌려주고 호출 수를 센다. 목록이 모자라면 실패한다. 모의 매처는 matching을 그대로 돌려준다. 저장소 모의는 Response 엔티티를 fixture에서 만든다.
3. 시드 ID(ra01, rb01, form_b)는 UUID가 아니다. 실행기에서 시드 ID 순서를 유지하는 UUID로 바꾸고, 결과를 시드 ID로 되돌려 비교한다. 순서까지 검사한다.
4. expect_error가 없으면 오류 없이 끝나야 통과다. expect_reason이 있으면 status: clarify와 result: null까지 검사한다. expect_reason이 없는 케이스는 status: ok여야 한다. 모든 케이스의 응답을 analyze_response.schema.json으로 검증한다.
5. branch_skipped_condition_not_evaluable, branch_visited_blank_is_false 2건은 E7 몫이다. 이름을 고정한 목록으로 Assumptions.abort하고 이유에 "E7 #45"를 적는다. 그 밖의 이름이 이 목록에 들어가면 실패하게 한다. 이 skip은 사람이 승인한 것이고 E7이 지운다.
6. 스텁으로 먼저 돌려 실패를 확인하고, 판정 키를 빠뜨리지 않는지 결함 주입으로 확인한 뒤 구현한다.
7. non_owner_analyze_403은 기존 AccessGoldenTest로 skip 0이 되어야 한다. CSRF 거부로 나온 403을 소유권 판정으로 세지 않는지 확인한다.

# 일반 테스트 (이것만)
골든에 없는 SPEC 규칙이다. 실제 DB가 필요 없는 것은 service 수준으로 쓴다.
- AC30: 한 번 실패 후 정상이면 파서 2회, ok. 파서 호출 실패와 키 없음은 503.
- AC32: {query}와 같은 값의 {context}가 같은 결과를 낸다. match "and"나 operation "statistics"가 든 context, query와 context를 함께 보내거나 둘 다 없으면 400 invalid_context.
- AC77: "버전별 응답 수 알려줘"는 unsupported_scope, 파서 0회. "평균 나이 알려줘"는 missing_operation.
- AC27, SPEC 340·341행: AI 요약이 꺼진 폼은 options에 summarize가 없고, summarize는 summary_disabled다.
- AC26, SPEC 310행: 혼합 부정 "소프트웨어학과가 아닌 4학년 수"는 missing_match, options [].
- AC92: target이 선택형 문항이면 unsummarizable_field다.
- 표 순서: 여러 사유가 겹치면 위의 것 하나만 나온다. 1건이면 된다.
- 미구현 경계: sort 있는 list, 되묻기를 통과한 summarize가 결과를 내지 않고 실패한다. 조건이 있는데 운영 매처면 503이다.
- 실제 Postgres에서 1건: 소유자가 {context} count를 요청하면 ok, 다른 사용자는 403이고 내용이 없으며, 없는 폼과 UUID가 아닌 ID는 404다. 인증 없으면 401이다.
- 새 오류 코드의 advice 매핑(ApiExceptionHandlerTest의 EXPECTED): invalid_context 400, llm_unavailable 503.
- 파서 클라이언트: 로컬 스텁 서버에 요청 형태(모델, 스키마, 키 헤더)가 맞는지 1건. 키 값은 단언 메시지와 로그에 남기지 않는다.
여기 없는 경우가 필요해 보이면 추가하지 말고 PR에 "시험하지 않음"으로 적는다. SPEC에 답이 없는 기대값을 지어내지 않는다.

# PR #78과의 겹침
PR #78(E2, main에 아직 없을 수 있음)은 ErrorCode, ApiExceptionHandler, ApiExceptionHandlerTest, FormRepository, ResponseRepository, Form을 고치고 FormService, FormJson, FormSchemaValidator를 더한다.
- 머지됐으면 시작 때 그 위에서 작업한다. 안 됐으면 그 클래스 이름을 새로 만들지 말고 다른 이름을 쓴다. 필요하면 git show origin/feat/form-definition:<경로>로 본다.
- 공통 파일은 줄 추가 위주로 고친다. PR 전에 #78이 머지됐는지 보고, 머지됐으면 main을 합친 뒤(merge, force push 없음) cleanTest build를 다시 돌린다. 아직이면 git merge-tree로 충돌 파일을 PR에 적는다.
- 판정 기준을 풀어 충돌을 해결하지 않는다.

# 완료 기준
- .\gradlew.bat cleanTest build 성공. 결과 XML의 tests, failures, errors, skipped, timestamp를 보고한다.
- AnalysisGoldenTest: 14건 통과, 2건 skip(E7), 실패 0. AccessGoldenTest: non_owner_analyze_403 통과, skip은 other_users_response_404 1건만. 조건 평가 골든은 그대로 통과한다.
- 필수 15건을 왜 통과하는지 한 줄씩 위임 기록에 쓴다.
- 커버리지 검사 통과.
- git diff origin/main...HEAD --name-only가 허용 범위뿐이다. 커밋 뒤 git status --short에 docs/SPEC_followups.md만 남는다.
- docs/prompts/delegation_analyze_api.md에 "사람이 검토해 발행", 이 프롬프트, 실패·수정 회차, 결함 주입, 사람 결정, 미룸을 짧게 기록한다.
- PR의 CI에서 build와 frontend job이 통과한다.

# 중단 조건 (멈추고 보고한다)
- 같은 실패로 3회차를 넘길 때, 작업이 3시간을 넘길 때. 사람의 답을 기다린 시간은 빼고 잰다. 지금까지 한 것을 커밋하고 어디서 막혔는지 쓴다.
- SPEC·AGENTS에 없는 판단이 필요할 때, 골든과 SPEC이 충돌할 때, 판정 기준이나 기존 단언을 바꿔야 할 때, 이슈가 너무 커서 나눠야 할 때. 객관식으로 묻고 추천안을 첫 번째에 둔다. 한 번에 묶어서 묻는다.

# 커밋과 보고
- 커밋 "{tag}: 한글 메시지 (#44)", 50자 이내, 파일명·디렉터리명 금지. 공통 파일 변경은 커밋을 나눈다.
- 변경이 범위 안이면 push 전에 묻지 않고 올린다. 범위를 넘으면 push하지 말고 멈춘다. amend와 force push를 하지 않는다.
- PR 제목 "[feat] E6 분석 API와 되묻기 (#44)", 본문은 README 형식, 관련 이슈 "Closes #44", --reviewer Psoyeon1117. 다른 이슈가 알아야 할 것을 짧게 적는다: E7이 이어받을 ConditionMatcher와 지울 skip 2건, 미구현 예외 위치, T7이 이어받을 지점, E8이 쓸 응답 형태, 새 오류 코드.
- PR을 연 뒤 CI를 확인하고 멈춘다. 머지하지 않고 다음 이슈로 넘어가지 않는다.
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 골든과 결함 주입 결과 / 지시와 다르게 한 것 / 확인하지 못한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
```
