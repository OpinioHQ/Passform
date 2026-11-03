## §0 위임 대상 선정 (강의 5 A절, 팀 결정 2026-11-03)

- 첫 위임 기능: `com.opinio.passform.domain.analysis.ConditionEvaluator` — RequestContext의 conditions·match, 매처 출력(조건·버전별 매칭), 버전별 폼 정의, 응답 목록을 받아 일치 응답 수·ID(오름차순), matched, coverage를 돌려준다.
- 대상 AC: AC24, AC78, AC23, AC20 일부, AC22 일부, AC31 일부(count·list).
- 직접 담당한 것(사람): 3값 판정 규칙과 coverage 구성 규칙(SPEC 5절), 골든 케이스 15건의 기대값 확정(Claude 초안 → 팀 확인).
- 위임한 이유: 입출력이 SPEC 5절 JSON으로 고정되어 있고, 매칭 방식(스파이크)과 무관한 순수 함수라 완료 여부를 골든 케이스로 판정할 수 있다.
- 직접 담당한 이유: 판정 불가를 어떻게 세는지가 "숫자를 지어내지 않는다"(절대 규칙 3)를 가르는 제품 판단이기 때문이다.
- 범위 밖: 분기로 건너뛴 문항의 판정 불가(응답 경로 계산, 2차 위임), clarify 판정, 정렬, 출력 형태(count/list/summarize).
- 위치: domain 패키지(DB·LLM 없는 순수 판정 로직, AC36 커버리지 대상).
- 골든 케이스: tests/harness/golden_cases.yaml(15건: 정상 7, 경계 4, 금지 행동 4), fixture는 data/seed/forms의 form_a·form_b(기준 커밋 15210e3).

## §1 테스트 하니스 위임과 결함 주입 기록

- 하니스 기준 커밋: `2df1c02` (`test: 조건 평가 골든 테스트 하니스 추가 (#36)`, CI 통과). 이후 테스트 코드·골든 케이스·fixture 변경은 사람이 승인하며(AGENTS.md 4절 1항), 수용 전에 `git diff 2df1c02 -- tests/ data/seed/`로 대조한다.
- 하니스: `tests/java/com/opinio/passform/golden/ConditionEvaluatorGoldenTest.java`
  - `@TestFactory`가 `tests/harness/golden_cases.yaml`의 케이스마다 동적 테스트 하나를 만든다(테스트 이름 = 케이스 `name`). 케이스에 있는 판정 키만 `assertAll`로 검사하고, 실패 메시지에 판정 키 이름을 붙인다.
  - 기대값은 평가 호출 전에 읽는다. 스텁 상태에서도 기대값 형식 오류는 skip이 아니라 실패다.
  - skip은 평가 함수가 메시지 `not implemented`인 `UnsupportedOperationException`을 던질 때만이다. 다른 예외와 `null` 결과는 실패다.
  - 로더 검사 `골든_케이스_파일은_판정_가능한_형태다()`(항상 실행): YAML 중복 키, `cases` 비어 있음, `name` 없음, 이름 중복, 판정 키 없음, 허용 키 밖의 키(케이스·`input`·`input.context`), `input.fixture` 파일 없음.
  - 평가 함수에 넘기는 목록·맵은 수정할 수 없는 컬렉션이다. 구현이 입력을 고치면 실패한다.
- 평가 함수 입출력: `com.opinio.passform.domain.analysis`의 `ConditionEvaluator`(스텁)와 타입 8개. 의존성은 추가하지 않았다(SnakeYAML 2.6, Jackson 3.1.7이 이미 클래스패스에 있음).

### (1) 수집과 실행 확인

- 명령: `.\gradlew.bat test` (기준 커밋, 스텁 상태). 결과는 `build/test-results/test/*.xml`에서 집계했다.
- 골든 케이스 15 / 로더 검사 1 / 수집 노드 16 / passed 1(로더), failed 0, skipped 15(골든 전부, "ConditionEvaluator 미구현")
- `.\gradlew.bat build` 성공. domain 줄 커버리지는 11/13(84.6%, 기준 70%)이지만, 하니스가 입력·기대값 레코드를 만들며 실행한 줄이고 판정 로직은 아직 없다.

### (2) 결함 주입 및 검출 확인

명령은 모두 `.\gradlew.bat test --tests "*ConditionEvaluatorGoldenTest"`이다. 5-1~5-4는 `ConditionEvaluator.java`만 바꿨고, 5-5는 기준 커밋을 임시 worktree로 꺼내 그 사본의 YAML에만 케이스를 덧붙였다((3) 참고). 모든 실패는 판정 키 이름이 붙은 `AssertionFailedError`였고, 수집 오류나 무관한 예외로 실패한 것은 없다.

| 항목 | 주입한 결함 | 기대한 실패 | 실제 결과 | 판정 |
|---|---|---|---|---|
| 5-1 통과 가능 확인 | YAML에서 입력(conditions·match·fixture 응답 ID 집합)이 같은 케이스의 기대값을 돌려주는 임시 구현. 입력이 같은 `all_versions_…`·`matched_for_…`·`coverage_for_…`는 기대값을 합쳐 돌려준다 | 골든 15건 통과, skip 0 | passed 16, failed 0, skipped 0 | 충족 — 이후 실패를 검출 근거로 쓸 수 있다 |
| 5-2 판정 로직 생략 | 조건과 무관하게 제출 응답 전부를 일치로(count = 제출 수, ID 오름차순, matched = 입력 그대로, 판정 불가 0) | 조건이 있는 13건 실패 | passed 4(로더, `no_condition_counts_all`, `submitted_only`, `matched_for_applicable_versions`), failed 12, skipped 0 | 13건 중 12건 검출. `matched_for_applicable_versions`는 미검출(아래 한계) |
| 5-3 금지된 결과 | 5-1에 더해 `must_not_include` ID를 `response_ids` 끝에 끼워 넣음 | `must_not_include`가 있는 6건이 그 단언문으로 실패 | passed 10, failed 6, skipped 0. 6건 모두 `must_not_include`와 `expect_response_ids`가 실패 | 충족 |
| 5-4 순서 무시 | 5-1에 더해 `response_ids`를 역순으로 | ID가 2개 이상인 `expect_response_ids` 10건이 순서 단언문으로 실패 | passed 6, failed 10, skipped 0. 10건 모두 `expect_response_ids(순서 포함)` 하나만 실패 | 충족 |
| 5-5a note만 있는 케이스 | YAML 사본 끝에 판정 키 없는 `injected_note_only` 추가 | 로더 검사 실패 | 17 노드: failed 2, skipped 15. 로더 `cases[15] injected_note_only: 판정 키 없음`, 케이스 자체 `케이스 형식 오류: [판정 키 없음]` | 충족 |
| 5-5b 오타 키 | YAML 사본 끝에 `expect_cnt: 9`(+ 정상 `expect_response_ids`)인 `injected_typo_key` 추가 | 로더 검사 실패 | 17 노드: failed 2, skipped 15. 로더 `cases[15] injected_typo_key: 허용 키 밖의 키 [expect_cnt]`, 케이스 자체도 같은 형식 오류 | 충족 |

5-2에서 실패한 케이스와 단언문:

| 케이스 | 실패한 단언문 |
|---|---|
| `all_versions_condition_filter_with_coverage` | `expect_count`(5 ≠ 9), `expect_response_ids`, `expect_not_evaluable_count`(4 ≠ 0) |
| `coverage_for_unavailable_version` | `expect_not_evaluable_by_version` |
| `option_renamed_version_not_evaluable` | `expect_count`, `expect_response_ids`, `expect_not_evaluable_count`, `expect_not_evaluable_by_version` |
| `checkbox_all_requires_both_options` | `expect_count`, `expect_response_ids`, `must_not_include`(rb02, rb12, rb13) |
| `unanswered_kind_counts_blank_only` | `expect_count`, `expect_response_ids` |
| `answered_kind_excludes_blank` | `expect_count`(13 ≠ 14), `expect_response_ids`, `must_not_include`(rb10) |
| `match_any_known_true_with_unknown` | `expect_count`, `expect_response_ids`, `expect_not_evaluable_count`, `expect_not_evaluable_by_version` |
| `match_all_unknown_when_no_false` | `expect_count`, `expect_response_ids`, `expect_not_evaluable_count`, `expect_not_evaluable_by_version` |
| `condition_missing_one_version_keeps_matches` | `expect_count`, `expect_response_ids`, `expect_not_evaluable_count`, `expect_not_evaluable_by_version` |
| `negation_does_not_count_unknown` | `expect_count`(0 ≠ 9), `expect_response_ids`, `expect_not_evaluable_count`, `must_not_include`(ra04, ra06, ra07, ra09) |
| `match_not_excludes_unknown` | `expect_count`, `expect_response_ids`, `expect_not_evaluable_count`, `expect_not_evaluable_by_version`, `must_not_include`(rb04, rb12, rb13) |
| `injection_in_response` | `expect_count`(4 ≠ 14), `expect_response_ids`, `must_not_include`(rb11) |

5-3에서 `must_not_include`가 잡은 ID: `checkbox_all_requires_both_options`(rb02, rb12, rb13), `answered_kind_excludes_blank`(rb10), `negation_does_not_count_unknown`(ra04, ra06, ra07, ra09), `match_not_excludes_unknown`(rb04, rb12, rb13, rb15, rb16), `injection_in_response`(rb11, rb15, rb16), `submitted_only`(rb15, rb16).

5-4에서 통과한 5건은 `expect_response_ids`의 ID가 0~1개(`unanswered_kind_counts_blank_only`, `match_all_unknown_when_no_false`, `negation_does_not_count_unknown`)이거나 그 키가 없는 케이스(`matched_for_applicable_versions`, `coverage_for_unavailable_version`)다.

### (3) 원복 및 재실행 확인

- 5-1~5-4: 항목마다 `ConditionEvaluator.java`를 작업 폴더 밖에 백업하고 결함 구현으로 바꿔 실행한 뒤 백업으로 복구했다. 복구 후 SHA-256이 백업과 같았고(`94D15DD9…`), `git status --short`에는 추적 파일 변경이 없었으며(이 작업과 무관한 미추적 `docs/SPEC_followups.md`만 있음), `git diff 2df1c02 --stat`은 빈 출력이었다.
- 5-5: 저장소의 `golden_cases.yaml`은 `.claude/settings.json`의 `Edit(/tests/harness/**)` deny 대상이라 고치지 않았다. 기준 커밋 `2df1c02`를 임시 worktree(detached)로 꺼내 그 사본에 케이스를 덧붙여 실행하고, 매번 `git checkout --`으로 원복했다(사본 SHA-256 `9A0B7892…`가 전후 같음). 끝난 뒤 worktree를 제거했다.
- 최종 확인: 저장소의 `golden_cases.yaml`·`ConditionEvaluator.java`가 `2df1c02`의 blob과 같고(`206a1fb`, `2cc6880`), `git diff 2df1c02 -- tests/ data/seed/`는 빈 출력이다. 원복 상태로 `.\gradlew.bat build`가 성공했다(골든 16 노드: 로더 통과, 골든 15 skip / `HealthControllerTest` 2 통과).

### 검출 한계와 사람이 정할 것

- `matched_for_applicable_versions`는 판정 키가 `expect_matched` 하나이고, fixture의 모든 버전에 제출 응답이 있어서 기대값이 입력 `matching`과 같다. 그래서 입력을 그대로 돌려주는 구현(5-2)을 이 케이스 혼자서는 잡지 못한다. 입력이 같은 `all_versions_condition_filter_with_coverage`·`coverage_for_unavailable_version`이 그 구현을 잡으므로 파일 전체로는 검출된다. YAML 머리말의 "제출 응답이 있는 버전의 항목만 matched에 넣는다" 규칙을 검사하려면 제출 응답이 없는 버전에 매칭이 있는 케이스가 필요하다. 추가 여부는 사람이 정한다.
- 결함은 판정 로직 생략·금지 ID 출력·순서 무시·형식 오류 케이스만 주입했다. 3값 판정의 개별 오류(예: 판정 불가를 불일치로 세기)는 따로 주입하지 않았다.
- `.claude/settings.json`의 deny 목록에 `tests/java/**`가 없다. AGENTS.md 4절 1항은 `tests/java/`(골든 테스트) 수정을 금지하지만 도구가 막지는 않는다. 기능 위임 전에 `Edit(/tests/java/**)`를 deny에 추가할지 정한다.
- 평가 함수 입력이 수정 불가 컬렉션이라는 점과 skip 조건(`not implemented` 메시지)을 기능 위임 프롬프트(§2)에 적는다.

## §2 기능 위임 프롬프트 원본

## §3 검증 루프 기록

## §4 수용 판단
