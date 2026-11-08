# 제출 후 수정·취소 위임 기록 (#61)

기록 대상: T10 제출 후 수정·취소. 실행일 2026-11-08. 프롬프트는 §5에 그대로 둔다. 선행 작업 T2(#50), T3(#51), E5(#43)가 닫힌 것을 확인했다.

SPEC 추적표의 AC16·AC17·AC74 골든 id는 `golden_cases.yaml`에 없다. 이슈의 1순위 골든도 "없음"이다. 프롬프트대로 일반 테스트로 하고 이름 앞에 id를 적었으며 YAML에 케이스를 더하지 않았다. 판정용 골든은 사람이 X2 #67에서 쓴다. 이 테스트의 기대값은 에이전트가 썼으므로 AC 판정이 아니라 구현 근거다.

## §1 수용 기준별 처리

| AC | 테스트 줄 항목 | 처리 | 근거 |
|---|---|---|---|
| AC16 | 마감 1분 지난 폼의 수정·취소 409 `form_closed` | 자동(실제 Postgres) | `edit_after_deadline` |
| AC16 | 마감 지난 폼의 제출 409 | 기존 테스트 | `submit_after_deadline`(T3 골든) |
| AC16 | `closed` 폼의 수정·취소 409 | 자동 | `edit_closed_form` |
| AC16 | `editable: false` → 수정 409, 취소 성공 | 자동 | `not_editable_patch` |
| AC16 | `cancellable: false` → 수정 성공, 취소 409 | 자동 | `not_cancellable_delete` |
| AC16 | 텍스트 이력만 남은 응답 409 `history_only` | 자동 | `history_only_not_editable` 2건 |
| AC17 | 2건 제출하고 1건 취소 → `submitted` 유지 | 자동, 사람 확인 | `partial_cancel_keeps_submitted` |
| AC17 | 모두 취소 → `saved` | 자동, 사람 확인 | `cancel_returns_to_saved` |
| AC17 | 마감 알림 대상에 다시 포함 | 범위 밖(E12) | 이번 작업은 `saved` 복귀까지 |
| AC17 | 제작자 전체 삭제 뒤 `submitted` 유지 | 자동 | `bulk_delete_keeps_submitted_history` |
| AC74 | 옛 버전 응답을 제출 버전 문항으로 수정 | 자동, 사람 확인 | `response_edit_uses_original_version` |
| AC74 | 같은 ID·최초 제출 시각, 수정 시각, 응답 수 불변 | 자동, 사람 확인 | `response_edit_overwrites_same_id` |
| AC74 | 집계·이력·분석 갱신 | 자동, 사람 확인 | 같은 이름의 두 번째 테스트 |
| AC74 | 내보내기 값 갱신 | **미확인 — T11 대기** | 내보내기 API가 아직 없다 |
| AC74 | 취소 → 응답 수 1 감소, 다른 응답 불변 | 자동, 사람 확인 | `response_cancel_removes_one` |

## §2 실패·수정 회차

| 회차 | 실패 | 원인 | 수정 |
|---|---|---|---|
| 1 | 프론트 테스트 1건: 마지막 응답을 취소한 뒤 지운 답이 화면에 잠깐 남음 | 목록을 비운 렌더와 상세를 비우는 effect 사이의 한 프레임 | 취소에 성공하는 시점에 상세 상태를 목록과 함께 비웠다 |
| 2 | `main` 병합 충돌 5개 파일 | T9(#91)가 `ResponsePage.tsx`, `App.tsx`, `ErrorCode.java`, `ApiExceptionHandlerTest.java`, 라우트 문서를 함께 고쳤다 | 사람 승인으로 양쪽을 모두 살렸다. 수정 화면에 자동채우기가 나오지 않는 것을 테스트로 고정했다. 이 병합은 사람 화면 확인 뒤에 했다 |

그 밖의 테스트와 결함 주입은 한 번에 통과했다.

## §3 결함 주입 (19건)

각 주입은 파일을 바꾸고, 결과 파일을 지운 뒤 좁혀 실행하고, 백엔드는 `build/test-results` XML, 프론트는 Vitest JSON에서 기대한 테스트가 실행되어 실패했는지 확인한 뒤 되돌렸다. 커밋하지 않았다.

| # | 주입 | 실패한 기대 테스트 |
|---|---|---|
| 1 | 수정을 최신 버전으로 검증 | `response_edit_uses_original_version` |
| 2 | 수정이 새 응답을 만듦 | `response_edit_overwrites_same_id` 외 3건 |
| 3 | 수정이 `submitted_at`을 덮어씀 | `response_edit_overwrites_same_id` |
| 4 | 수정이 `ai_summary_agreed`를 바꿈 | `수정은_AI_요약_동의를_바꾸지_않고` |
| 5 | 수정의 소유자 조건을 뺌 | `다른_사용자의_응답은_수정이_404` |
| 6 | 취소의 소유자 조건을 뺌 | `다른_사용자의_응답은_취소가_404` |
| 7 | 작성 중 응답의 수정·취소 허용 | `작성_중_응답은_수정과_취소가_404다` |
| 8 | `not_editable` 검사를 뺌 | `not_editable_patch` 외 2건 |
| 9 | `not_cancellable` 검사를 뺌 | `not_cancellable_delete` 외 1건 |
| 10 | 마감 시각 검사를 뺌 | `edit_after_deadline` 외 2건 |
| 11 | 제작자가 지운 응답을 막지 않음 | `history_only_not_editable` 외 2건 |
| 12 | `form_closed`를 먼저 판정 | `history_only가_form_closed보다_먼저다` |
| 13 | 다른 제출이 남아도 `saved` | `partial_cancel_keeps_submitted` 외 1건 |
| 14 | 남은 제출에서 제작자가 지운 이력을 뺌 | `bulk_delete_keeps_submitted_history` |
| 15 | 모두 취소해도 `submitted` | `cancel_returns_to_saved` 외 1건 |
| 16 | 이력에 폼 설정 값을 그대로 냄 | `이력은_닫힌_폼에서_false다` 외 3건 |
| 17 | `editable: false`여도 수정 버튼 표시 | 버튼 표시 테스트 외 3건 |
| 18 | 수정 화면에서 초안 저장 | `답을 바꿔도 초안을 저장하지 않고` |
| 19 | 수정 화면에 최신 폼 정의 표시 | `response_edit_uses_original_version` 외 9건 |

## §4 사람이 정한 답과 화면 확인 (2026-11-08)

| 질문 | 답 |
|---|---|
| 첨부가 연결된 응답의 취소 | 연결만 끊고 행은 남긴다. 원본 정리는 T12가 `s3_key`로 한다 |
| `PATCH`에 `attachments`가 올 때 | `400 invalid_request`. 생략하면 기존 파일 유지 |
| 취소 확인 방식 | 화면 안 2단계 확인 |
| 완료 뒤 이동 | 수정은 그 응답 상세, 취소는 목록. 각각 안내 문구를 보인다 |

**실제 화면 확인(사람):** 1~7번 모두 정상. 로그인 제출 뒤 수정, 제작자 결과 갱신, 옛 버전 응답을 제출 당시 문항으로 수정, 취소, 수정·취소 불가 폼과 마감 지난 폼의 버튼 숨김을 확인했다. 내 폼함은 새 응답을 취소했을 때 같은 폼에 시드 응답이 남아 `제출됨`을 유지했고, 시드 응답까지 취소하자 미제출로 돌아갔다. 안내문이 시드 응답의 영향을 잘못 적었으나, 일부 취소와 전체 취소를 모두 확인한 결과가 되었다.

**알려진 한계:** 내보내기 갱신은 T11 대기다. 응답 파일 교체와 S3 원본 삭제는 T12 범위다. 수정 화면에서는 동의를 다시 묻지 않고 송금 안내를 보이지 않는다.

## §5 발행한 프롬프트

```text
[구현 내용]
T10(#61): 응답자의 제출 후 수정·취소를 만든다. `PATCH·DELETE /me/responses/{response_id}`, 제출 당시 버전으로 재검증, 취소 뒤 내 폼함 상태 복귀, 제출 이력 화면의 수정·취소가 포함된다. 저장소 C:\Users\namhu\claude-practice\Passform, 브랜치 feat/response-edit-cancel(최신 origin/main에서 분기), 셸 Windows PowerShell.

[목표]
응답자가 실제 화면에서 허용된 제출을 고치거나 취소한다. 수정은 같은 `response_id`와 최초 `submitted_at`을 유지하고, 취소는 그 응답만 결과와 본인 이력에서 뺀다. AC16, AC17(취소 부분), AC74를 테스트로 보인다.

[절대 규칙]
- AGENTS.md 3~5절을 따른다.
- 시작 전에 T2(#50), T3(#51), E5(#43)가 main에 있는지 확인한다. 없으면 구현하지 말고 알린다.
- 인증 우회(X-Demo-User 등)와 새 permitAll을 넣지 않는다. 현재 사용자는 CurrentUserService로 얻고, 테스트는 spring-security-test 모의 인증을 쓴다.
- 수정 검증은 제출 경로의 경로 계산과 답 검증을 그대로 쓰고, 정의만 그 응답의 `form_version`에서 읽는다. 별도 검증을 새로 만들지 않는다.
- 수정·취소는 제출과 같은 폼 행 잠금 아래에서 판정한다. 마감과 권한은 폼의 현재 값으로 본다(SPEC 159행).
- 취소는 응답 행 삭제로 한다. `abandoned`는 다른 용도로 예약되어 있어 취소 표시로 쓰지 않는다(SPEC 86행).
- 수정 화면에서 답을 바꿔도 초안 저장을 보내지 않는다. 수정은 `PATCH`로만 저장하고 새 `in_progress` 응답을 만들지 않는다.
- `PATCH`는 `ai_summary_agreed`와 동의 상태를 바꾸지 않는다(SPEC 215행).
- 기존 테스트 변경 금지의 예외는 하나다. 새 오류 코드 `history_only`, `not_editable`, `not_cancellable`을 ApiExceptionHandlerTest의 EXPECTED 표에 한 줄씩 더한다(이슈 #61 지시).
- 다음은 바꾸지 않는다: SecurityConfig, DB 스키마와 Flyway, CI, build.gradle, SPEC, ARCHITECTURE, AGENTS, data/seed, tests/harness, ResponseRepository의 기존 쿼리, 제출 경로의 판정 동작. 새 조회 메서드 추가와 동작을 바꾸지 않는 검증 코드 재사용 정리는 할 수 있다. 그 밖을 바꿔야만 끝나면 멈추고 묻는다.
- 새 의존성은 이유를 적어 승인받은 뒤에만 추가한다.
- SPEC에 없는 동작은 추측하지 말고, 맥락과 선택지를 적어 객관식으로 묻는다. 예: 첨부가 연결된 응답의 수정·취소 처리, `PATCH`에 `attachments`가 올 때, 취소 확인 방식, 수정 완료 뒤 이동할 화면.
- 같은 실패가 3회차를 넘거나 작업이 2시간을 넘으면 멈추고 보고한다. 작업 트리는 그대로 둔다.

[참조]
- 이슈 #61
- AGENTS.md 3~6절, 분기 공통화 77행, Clock 79행, 세션 규칙과 공통 파일 97~105행
- docs/SPEC.md
  - 범위: 42행, 48행, `abandoned` 86행
  - 버전과 운영 설정: 156행, 159행, 상태 전이 175행
  - API: 192~195행, 200행
  - 수정 검증: 209행, 동의 215행, 형식 검증 217행
  - 첨부·수정·취소·이력·권한: 222~226행
  - 수용 기준과 테스트 줄: AC2(374행), AC53(392~393행), AC73(404~405행), AC85(422~423행), AC16(485~486행), AC74(487~488행), AC17(494~495행)
  - 추적표: 595·596·651행
- docs/ARCHITECTURE.md 3절, 10절(responses 122행, 삭제 규칙 141행~)
- docs/prompts/delegation_my_forms.md 143행, delegation_submission.md
- src/main/resources/db/migration/V1__create_schema.sql의 responses, attachments
- src/main/java/com/opinio/passform/의 domain/response/Response.java, domain/inbox/SavedForm.java, domain/form/Form.java·AnswerValidator.java·ResponsePathCalculator.java, domain/error/, repository/ResponseRepository.java·SavedFormRepository.java·AttachmentRepository.java, service/SubmissionService.java·ResponseHistoryService.java·CurrentUserService.java, controller/MyFormsController.java, controller/advice/ApiExceptionHandler.java
- src/test/java/com/opinio/passform/의 controller/MyFormsApiTest.java, controller/advice/ApiExceptionHandlerTest.java, service/SubmissionServiceTest.java, support/
- tests/harness/golden_cases.yaml의 submission_cases, data/seed/forms/form_a_versions.json, data/seed/README.md
- frontend/src/의 pages/HistoryPage.tsx·ResponsePage.tsx·MyForms.live.test.tsx, response/responsePath.ts·draftSync.ts, routes/respondentRoutes.tsx·paths.ts, api/client.ts, types/response.ts
- frontend/docs/ROUTES_AND_API.md 10~11행

[완료 기준]
- 이슈 #61 완료 조건과 AGENTS.md 6절을 충족한다.
- 테스트 근거(실제 Postgres로 확인):
  - AC16, AC74 테스트 줄의 각 항목과 AC17의 취소 항목(일부 취소 → `submitted` 유지, 모두 취소 → `saved`, 제작자 삭제 이력만 남으면 `submitted`). `saved_at`과 `has_draft`는 그대로다.
  - AC74의 집계·이력·분석 갱신. 내보내기 API(T11 #62)가 있으면 내보내기도 확인하고, 없으면 보고에 "미확인 — T11 대기"로 적는다.
  - AC53·AC73의 수정 항목: 형식·글자 수 위반 수정은 400이고 기존 응답이 바뀌지 않는다.
  - 판정 순서: `history_only` → `form_closed` → `not_editable`·`not_cancellable` → 답 검증
  - 비로그인 401, 다른 사용자·없는 응답·작성 중 응답·비로그인 제출 응답은 404
  - `GET /me/responses`와 상세의 `editable`·`cancellable`이 SPEC 226행의 조건을 따른다. 판정은 수정·취소 API와 한 곳에서 나온다.
  - 같은 응답의 동시 취소·수정에서 500이 나지 않고 응답 수가 어긋나지 않는다.
  - 첨부 행이 연결된 응답의 취소에서 500이 나지 않는다.
  - 추적표의 AC16·AC17·AC74 골든 id가 golden_cases.yaml에 있으면 실행기로 돌린다. 없으면 일반 테스트로 하고 테스트 이름이나 보고에 id를 적는다. YAML에 케이스를 더하지 않는다.
  - 화면: 허용될 때만 수정·취소 버튼 표시, 조회 전용 이력에 버튼 없음, 수정 화면의 제출 버전 문항과 기존 답, 분기 문항 수정 때 경로 재계산, 409 오류별 안내, 취소 뒤 목록 갱신
- 실제 화면 확인을 사람에게 요청하고 결과를 기록한다.
  - 로그인 제출 뒤 이력에서 수정: 같은 응답, 최초 제출 시각 유지, 수정 시각 표시, 제작자 결과 갱신
  - 새 버전 배포 뒤 옛 응답을 제출 당시 문항으로 수정
  - 취소 뒤 이력과 제작자 결과에서 제거, 내 폼함 상태 변화
  - 마감·수정 불가 폼에서 버튼이 보이지 않음
  - 시험 데이터용 스크립트는 에이전트가 준비하되, 승인 없이 개발 DB에 실행하지 않는다.
- 결함 주입: 아래를 하나씩 넣었을 때 테스트가 실패해야 한다. 기대한 테스트가 실제로 실행됐는지 build/test-results XML(프런트는 Vitest 결과)로 확인하고, 실행되지 않았으면 판정 무효로 다시 돌린다. 주입은 커밋하지 않고, 항목마다 되돌린다.
  - 수정을 폼의 최신 버전으로 검증한다
  - 수정이 새 응답을 만든다
  - 수정이 `submitted_at`을 덮어쓴다
  - 수정이 `ai_summary_agreed`를 바꾼다
  - 수정에서 소유자 조건을 뺀다
  - 취소에서 소유자 조건을 뺀다
  - 작성 중 응답의 수정·취소를 허용한다
  - `not_editable` 검사를 뺀다
  - `not_cancellable` 검사를 뺀다
  - 마감 시각 검사를 뺀다
  - 제작자가 지운 응답을 `history_only`로 막지 않는다
  - `history_only`보다 `form_closed`를 먼저 판정한다
  - 다른 제출이 남아 있어도 `saved`로 돌린다
  - 남은 제출을 셀 때 제작자가 지운 이력을 뺀다
  - 모두 취소해도 `submitted`로 둔다
  - 이력의 `editable`·`cancellable`에 폼 설정 값을 그대로 낸다
  - 화면에서 `editable: false`여도 수정 버튼을 보인다
  - 수정 화면에서 답을 바꾸면 초안 저장을 보낸다
  - 수정 화면에 최신 폼 정의를 보인다
- 위임 기록을 docs/prompts/delegation_response_edit_cancel.md에 남긴다. 넣을 것: 이 프롬프트 원본, 회차 표, 결함 주입 결과, 사람이 정한 답.

[범위 밖]
- 응답 파일 업로드, multipart 수정과 파일 교체, S3 원본 삭제, `storage_quota_exceeded`(T12)
- 받은 응답 전체 삭제와 폼 삭제, 조회 전용 이력 화면(T12). 이번 작업은 `history_only` 판정과 버튼 숨김만 한다.
- 내보내기 구현(T11)
- 마감 알림 발송(E12). 이번 작업은 `saved` 복귀까지 한다.
- 동의 변경·철회

[깃허브 규칙]
- 사람의 명시적 승인을 받은 뒤에만 커밋·push·PR을 한다. 승인 요청에는 `git status --short`, `git diff --stat`, 핵심 diff, 테스트 집계, 제안 커밋 메시지를 보여 준다. 커밋 승인은 push 승인이 아니다.
- 커밋은 AGENTS.md 5절 협업 항목과 README.md를 따른다. 공통 파일(paths, types, client, 공통 컴포넌트) 변경은 별도 커밋으로 나눈다.
- PR 제목: `[feat] 제출 후 수정·취소 (#61)`. 본문은 README.md를 따른다.
- 리뷰어는 gh pr create --reviewer ye-nni로 지정한다.
- main에 직접 push하거나 머지하지 않는다.

[보고]
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 테이블 표 / 추가한 의존성과 이유 / 지시와 다르게 한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
- 테스트마다 통과 이유를 코드 위치와 함께 한 줄로 적는다.
- AC16, AC17, AC74의 테스트 줄 항목마다 자동 테스트, 사람 확인, 범위 밖 중 무엇으로 처리했는지 적는다.
```
