# 분석 API 입출력

- 상태: 6절의 제안은 리뷰에서 팀이 확정한다. SPEC 5절(235행, 246~349행)을 기계가 읽을 수 있게 옮기고, 시험 데모(`docs/spikes/2026-11-04_test_demo.md`)에서 드러난 빈칸을 결정 항목으로 적었다.
- 출력 스키마: `src/response_analysis/schemas/analyze_response.schema.json` / 예시 28개: `docs/api/analyze_examples.json`
- 스키마는 잠긴 폴더에 사람이 넣었다. 예시는 화면 가짜 데이터와 API 테스트가 함께 쓴다.

## 1. 요청

```
POST /forms/{form_id}/analyze     제작자(로그인 + owner_id)
{ "query": "소프트웨어학과 4학년 몇 명이야?" }     자연어
{ "context": { "conditions": [...], "operation": "count", ... } }     되묻기 뒤 확정한 요청. 파서를 부르지 않는다(AC32)
```

| 상태 | 언제 |
|---|---|
| `200` | 결과(`status: ok`)와 되묻기(`status: clarify`) 모두. 되묻기는 오류가 아니다 |
| `400 {error: "invalid_context"}` | `context`가 `request_context.schema.json`에 안 맞음. `query`와 `context`를 함께 보내거나 둘 다 없음(결정 A1) |
| `401` / `403` / `404` | 비로그인 / 다른 사람의 폼 / 없는 폼 |
| `503 {error: "llm_unavailable"}` | LLM 호출 실패(결정 A5). 되묻기나 0건으로 바꾸지 않는다 |

## 2. 처리 순서 (①~⑦)

코드가 고정한 순서다. 모델이 다음 단계를 정하지 않는다.

| 단계 | 하는 일 | LLM | 되묻기 사유 |
|---|---|---|---|
| 0 | 권한, 범위 표현 판정 | 없음 | 1 `unsupported_scope` |
| ① 파서 | 자연어 → `RequestContext`, 스키마 재검증, 형식 오류만 1회 재호출 | `gpt-6-luna` | 2 `parse_failed` |
| ② 되묻기 A | 통계 표현, `operation`, 요약 사용 여부, `match` | 없음 | 3 `missing_operation`, 4 `summary_disabled`, 5 `missing_match` |
| ③ 매처 | 조건을 버전별 문항·선택지에 연결. `form_question_info.md` | 못 푼 조건만 | |
| ② 되묻기 B | 연결 결과 검사 | 없음 | 6 `unknown_condition`, 7 `ambiguous_match`, 8 `double_negation`, 9 `unsortable_field`, 10 `unsummarizable_field` |
| ④ 조건 평가 | `ConditionEvaluator`로 참·거짓·판정 불가 | 없음 | |
| ⑤ 결과 처리 | 건수, 목록, 정렬, 범위 표시 | 없음 | |
| ⑥ 요약 | 대상 확정 → 주제 분류 → 개수 집계(코드) → 근거 검색 → 요약문 | 분류·임베딩·요약문 | 되묻기 아님. `summary.status` |
| ⑦ 응답 | 이 문서의 출력 | 없음 | |

여러 사유가 해당하면 번호가 앞선 것 하나만 돌려준다. `{context}`로 오면 0과 ①을 건너뛰고 ②부터 한다.

## 3. 출력

`status`로 나뉜다. 형태는 `src/response_analysis/schemas/analyze_response.schema.json`이 정본이다.

```jsonc
// 결과
{ "status": "ok", "context": {...}, "matched": [...], "coverage": {...}, "result": {...}, "clarify": null }
// 되묻기
{ "status": "clarify", "context": {...} | null, "matched": null, "coverage": null, "result": null, "clarify": {...} }
```

- `result.response_ids`: `count`는 `null`, `list`는 정렬된 ID, `summarize`는 일치 응답 전체(동의 무관).
- `result.summary`: `summarize`에서만 객체다. `status`가 `ok`가 아니면 `items: []`이고 `result.count`는 그대로다.
- 상세 답은 `GET /forms/{form_id}/responses/{response_id}`로 따로 읽는다(AC62).

## 4. 되묻기 뒤 다시 보내는 방법

**화면이 응답의 `context`를 고쳐 `{context}`로 다시 보낸다.** 서버는 이전 대화를 기억하지 않는다. 시험 데모에서 이 방식으로 동작을 확인했다.

| 사유 | `options` | 화면 | 다음 요청 |
|---|---|---|---|
| `unsupported_scope` | 안내 문구 | 안내, 버전별 통계로 이동 | 없음. 새 질문 |
| `parse_failed` | `[]` | 다시 입력 안내 | 없음. 새 질문 |
| `missing_operation` | `count`·`list`·`summarize` | 버튼 | `operation`을 고른 값으로 |
| `summary_disabled` | `count`·`list` | 버튼 | `operation`을 고른 값으로 |
| `missing_match` | `all`·`any` | 버튼 | `match`를 고른 값으로 |
| `missing_match` | `[]`(긍정·부정 혼합) | 나눠서 물어보라는 안내 | 없음. 새 질문 |
| `unknown_condition`(조건) | 선택형 문항 목록 | 목록 표시, 다시 입력 | 없음. 새 질문(결정 A7) |
| `unknown_condition`(`sort.by`, `target`) | 문항 목록 | 버튼 | `sort.by` 또는 `target`을 문항 문구로 |
| `ambiguous_match` | `["any"]` | 버튼 | `match: "any"` |
| `ambiguous_match` | 후보 문항 목록 | 버튼 | 그 조건을 `"문항 문구 - 값"`으로 |
| `double_negation` | 확정 문장 1개 | 버튼 | `match`를 지움 |
| `unsortable_field` | 정렬 가능한 문항 | 버튼 | `sort.by`를 문항 문구로 |
| `unsummarizable_field` | 요약 가능한 문항 | 버튼 | `target`을 문항 문구로 |

## 5. 제작자 화면 규칙 (사용자 확인, 2026-11-04)

API에는 `question_id`, `response_id`, `not_evaluable_*`가 그대로 있지만 **화면에는 내부 용어를 쓰지 않는다.**

- "판정 불가", `q1`, 응답 ID, `v1`을 보여 주지 않는다. 문항 문구, "응답 1", "폼 버전 1"로 쓴다.
- 일부만 확인했다는 사실은 항상 보인다. 예: "전체 39건 중 확인할 수 있는 17건에서 찾았어요". 이유는 물음표를 눌러야 보인다.
- 이유 구분은 `matched`로 한다: 그 조건이 그 버전에 연결돼 있으면 분기로 건너뛴 것, 없으면 문항이 없던 버전이다.
- 묻기 밑 버튼은 그 폼에서 되는 요청만 만들고 `{context}`로 보낸다.

## 6. 결정할 것

| # | 내용 | 제안 | 이유 |
|---|---|---|---|
| A1 | 입력 이름. 시험 전 계획은 `question`, SPEC은 `query` | SPEC대로 `query`. 둘 다 또는 둘 다 없으면 400 | SPEC이 정본 |
| A2 | 되묻기 응답의 `context`. SPEC 예시는 `ok`뿐 | 항상 포함, `operation: null`도 키를 둠. 파서 전 종료는 `null` | 없으면 화면이 다시 보낼 수 없다. 시험 데모는 키를 빼서 스키마에 어긋났다 |
| A3 | 되묻기 후 재요청 | 4절 표 | 서버가 상태를 안 가져도 된다 |
| A4 | `matched[].question_note` 추가 | 추가. SPEC 253행 수정 | 없으면 화면이 문항 문구를 보여 줄 수 없다 |
| A5 | LLM 장애 응답 | `503 llm_unavailable` | SPEC에 없다. 숫자를 지어내지 않는다 |
| A6 | 파서 출력 보정 단계 | 유지하고 SPEC에 적음. 프롬프트 v3는 재측정 뒤 결정 | 측정에서 조건을 버리는 일이 반복됐다 |
| A7 | `unknown_condition`에서 어느 조건이 문제인지 | MVP는 문구로만 알리고 다시 입력. 눌러서 바꾸려면 `clarify.conditions` 추가 | 범위를 줄인다 |
| A8 | 요약 중 LLM이 실패하면 전체 503이라 건수도 못 봄 | MVP는 유지 | 부분 응답은 SPEC에 없는 상태가 늘어난다 |
| A9 | 매처 방식 | 글자 일치는 규칙, 못 푼 조건만 LLM. 화면에 연결 결과 표시. 재도전은 별도 결정 | 스파이크 1차 미달(37/40), AC21 미충족 |

## 7. 알려진 한계

- 범위 판정은 명시적 표현만 잡는다. "수정 전 응답만"은 매처가 막았을 뿐 보장이 아니다.
- 질문 속 인젝션은 별도 방어가 없다. 시험에서 결과가 안 나온 것은 보정이 우연히 막은 것이다.
- 지연(실측): 건수·목록 1.2~3.6초, 특정 문항 요약 7~10초, 전체 요약 12.7초(39건). 300건은 안 재봤다.
