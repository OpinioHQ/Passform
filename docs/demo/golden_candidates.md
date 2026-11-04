# 골든 케이스 후보 — MVP 전

> **강의 5에서 골든 케이스는 사람이 쓴다.** 구현을 보고 기대값을 정하지 않기 위해서다. 이 문서는 무엇을 먼저 쓸지 고르는 재료이고, 케이스 이름과 조건은 SPEC AC 표(580~671행)와 AC 테스트 문구에서 옮겼다. **기대 숫자는 넣지 않았다.** 시험 데모 출력을 그대로 옮기면 안 되니, fixture를 보고 사람이 직접 세어 적는다.

## 구조 결정 (먼저)

| # | 내용 | 제안 | 근거 |
|---|---|---|---|
| G1 | `golden_cases.yaml` 안에서 기능 나누기 | `cases`는 조건 평가 전용으로 두고 기능별 최상위 키 추가(`analysis_cases`, `submission_cases` 등) | 현재 하니스는 `cases`만 읽어 전부 평가 함수 케이스로 처리한다(`ConditionEvaluatorGoldenTest.java:228`). 다른 최상위 키는 읽지 않는다 |
| G2 | 실행 수준 | service 수준. LLM과 저장소는 모의 객체 | CI에 DB가 없다(`ci.yml`). 실제 DB 시험은 별도 결정 |
| G3 | 파서 입력 | 모의 파서에 고정 출력을 준다 | 테스트에서 실제 LLM을 부르지 않는다(AGENTS 5절). 실제 출력 패턴은 `시험데모_결과_2026-11-04.md` 3-1절 |
| G4 | 강의 5 절차를 어디까지 | 아래 1순위는 전체 절차, 나머지는 공지를 보고 결정 | 7일 안에 전부는 어렵다 |

## 1순위 — 동료가 쓰기 전에 필요 (틀리면 피해가 크다)

**권한·개인정보**

| AC | 케이스 | 무엇을 본다 |
|---|---|---|
| AC2 | `unauthenticated_401`, `other_users_response_404`, `non_owner_analyze_403` | 비로그인, 남의 응답, 남의 폼 분석 차단 |
| AC92 | `personal_question_never_sent`, `contact_masked_in_long_answer`, `unsummarizable_target_clarify` | 모의 LLM이 받은 본문에 개인정보 문항 값과 연락처가 없음 |
| AC91 | `ai_summary_optional_consent_submit`, `no_consent_excluded_from_summary_only` | 선택 동의 거부해도 제출, 요약에서만 빠짐 |

**동의·제출**

| AC | 케이스 | 무엇을 본다 |
|---|---|---|
| AC12·13·14 | `every_open_form_needs_consent_template`, `consent_lists_input_formats`, `submit_without_consent` | 동의 없이 배포·제출 불가 |
| AC9 | `skipped_questions_not_required`, `skipped_answers_dropped` | 서버가 경로를 다시 계산 |
| AC43 | `required_missing_visited_only` | 경로 위 필수 문항만 |
| AC16·41 | `submit_after_deadline`, `share_url_closed_form` | 마감 후 제출 거부 |
| AC76 | `stale_form_version_rejected`, `latest_form_version_submits` | 옛 버전 제출 거부 |
| AC73 | `phone_format_normalizes_and_checks_length`, `email_format_checks_structure`, `date_format_rejects_invalid_date` | 입력 형식. **휴대전화 규칙 변경(010은 11자리) 먼저 SPEC 162행 반영** |
| AC85 | `response_limit_auto_close`, `response_limit_concurrent_submit` | 선착순. 동시성은 시험 데모에서 검증 못 함 |

**숫자 (되묻기·결과 처리)**

| AC | 케이스 | 무엇을 본다 |
|---|---|---|
| AC22 | `unknown_condition_all_versions`, `unknown_sort_field` | 없는 조건은 0건이 아니라 되묻기 |
| AC25 | `ambiguous_single_choice`, `checkbox_all_ok`, `ambiguous_question_meaning_across_versions` | 모순·모호 |
| AC26 | `missing_match_two_conditions` | `match` 없음, 긍정·부정 혼합 |
| AC27 | `missing_operation`, `unsupported_statistics_not_mapped_to_summary`, `missing_operation_without_summary_option` | 통계를 다른 동작으로 안 바꿈 |
| AC29 | `double_negation_unanswered` | 미응답 + not |
| AC30 | `extra_key_retry_once` | 재호출 1회, 이후 `parse_failed` |
| AC32 | `confirmed_context_skips_parser`, `invalid_context_400` | `{context}`는 파서 0회 |
| AC77 | `natural_language_version_scope_rejected`, `unsupported_statistics_explained`, `all_responses_count_supported` | 범위 요청은 파서 0회 |

**SPEC에 없어 새 id가 필요한 것 (사람이 판단)**

| 후보 id | 무엇을 본다 | 근거 |
|---|---|---|
| `parser_dropped_condition_not_counted` | 모의 파서가 `conditions: []`, `count`를 내도 "수의학과 학생 수"가 전체 건수로 나가지 않음 | 시험 데모 최대 문제. 보정 단계(A6)를 SPEC에 넣을 때 |
| `branch_skipped_condition_not_evaluable` | 분기로 건너뛴 응답은 판정 불가 | SPEC 313행. 조건 평가 §3의 q6 한계. fixture `rb05`, `rb08`, `rb13` |
| `branch_visited_blank_is_false` | 경로상 비운 응답은 거짓 | 시드 README 가정 14. fixture `rb09` |

## 2순위 — 첫 버전 동작에 필요

| 영역 | AC와 케이스 |
|---|---|
| 배포·상태·버전 | AC38 `draft_hidden_from_others`, `first_open_creates_version_one` / AC56 `draft_saves_incomplete`, `open_runs_semantic_checks` / AC57 `invalid_status_transition` / AC58 `form_edit_creates_immutable_version`, `operating_setting_keeps_version` |
| 분기 규칙 | AC8 `branch_to_missing_target`, `branch_backward`, `branch_condition_invalid` / AC44 `branch_all_conditions`, `branch_any_conditions`, `branch_first_rule_wins` |
| 요약 코드 부분 | AC87 `theme_counts_from_labels`, `theme_order_deterministic` / AC88 `evidence_within_matched_consented` / AC89 `topic_summary_has_no_count`, `topic_no_evidence_no_text` / AC93 `summary_no_matches_status`, `summary_over_limit_no_llm_call` |
| 결과·이력 | AC19 `results_option_counts_combined_and_by_version` / AC20 `submitted_only` / AC15 `submitted_content_roundtrip`, `submission_uses_original_form_version` / AC17 `save_form_once`, `submit_adds_to_inbox` |
| 정렬 | AC28 `unsortable_long_answer`, `unsortable_text_age` / AC61 `list_sort_all_versions`, `non_list_sort_ignored` |

## 3순위 — 범위가 정해진 뒤

프로필·자동채우기(AC3~AC6), 자동 저장(AC82, AC83), 수정·취소(AC74), 내보내기(AC48~AC50), 첨부·용량(AC63~AC67, AC70), 알림(AC18), QR(AC10).

## 쓸 때 주의

- fixture는 `data/seed`만 쓴다. 비분기 폼, 동의 시나리오처럼 없는 경우는 사람이 fixture를 추가한다. 시드 README의 가정 형식을 따른다.
- `data/demo`의 시험 데모 시드는 골든 fixture가 아니다.
- 잠금 범위를 넓힐 때 `.claude/settings.json` deny와 AGENTS 4절 1항을 같이 고친다.
