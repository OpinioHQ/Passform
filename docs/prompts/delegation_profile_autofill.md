# 프로필과 자동채우기 위임 기록 (#60)

기록 대상: T9 프로필과 자동채우기. 실행일 2026-11-08. 프롬프트는 §5에 그대로 둔다. 선행 작업 T1(#49), T2(#50), T4(#52)가 닫힌 것을 확인했다.

SPEC 추적표의 AC3~AC6 골든 id는 `golden_cases.yaml`에 없다. 이슈의 1순위 골든도 "없음"이다. 프롬프트대로 일반 테스트로 하고 이름 앞에 id를 적었으며 YAML에 케이스를 더하지 않았다. 판정용 골든은 사람이 X2 #67에서 쓴다. 이 테스트의 기대값은 에이전트가 썼으므로 AC 판정이 아니라 구현 근거다.

## §1 수용 기준별 처리

| AC | 테스트 줄 항목 | 처리 | 근거 |
|---|---|---|---|
| AC3 | `address`·`birth_date` 저장·재조회 | 자동 테스트(실제 Postgres) | `profile_address_birth_date_allowed` |
| AC3 | `resident_number` 포함 → 400, 저장 프로필 불변 | 자동 테스트(실제 Postgres) | `profile_rejects_sensitive_key` 2건 |
| AC3 | 화면의 `unsupported_key` 안내 | 자동 테스트 | `profile_rejects_sensitive_key unsupported_key를 안내` |
| AC4 | 두 프로필 각각의 `answers` | 자동 테스트, 사람 확인 2·4 | `autofill_selected_profile` |
| AC5 | 삭제 후 404, 제출 응답 불변 | 자동 테스트(실제 Postgres) | `deleted_profile_not_used` |
| AC5 | 고르는 목록에서 사라짐 | 사람 확인 6 | §4 |
| AC6 | 본인 정보 제안, `profile_key: null`과 지원 동기는 `unfilled` | 자동 테스트, 사람 확인 2 | `autofill_own_info_only` |
| AC6 | 선택지와 다르면 `unfilled` | 자동 테스트, 사람 확인 4 | `autofill_option_mismatch` |
| AC6 | 화면에서 수정한 값으로 제출 | 자동 테스트, 사람 확인 5 | `autofill_value_reviewable 제안값을 고치면` |
| AC6 | 요청 전후 `Response` 수 동일 | 자동 테스트(실제 Postgres), 사람 확인 3 | `autofill_value_reviewable 요청 전후` |
| AC68 | 로그인해 프로필 자동채우기(360px) | 사람 확인 7 | jsdom은 레이아웃을 재지 못한다 |
| AC68 | 비로그인 응답·파일 첨부·내 폼함·제출 이력 등 나머지 | 범위 밖 | T4·E5·T12 |

## §2 실패·수정 회차

| 회차 | 실패 | 원인 | 수정 |
|---|---|---|---|
| 1 | 프론트 테스트 3건 | 공통 `Button`이 `aria-label`을 넘기지 않아 프로필별 수정·삭제 이름을 찾지 못했다 | 공통 컴포넌트를 바꾸지 않고 그 두 버튼만 `<button>`으로 썼다 |
| 2 | 결함 주입 결과에서 무관한 항목(8, 12, 13번)에 목록 순서 테스트가 함께 실패 | 테스트의 기대 순서를 `UUID.compareTo`(부호 있는 비교)로 만들어 Postgres 순서와 달랐다. 구현이 아니라 테스트 문제였다 | 문자열 순서로 고치고 8회 연속 통과를 확인했다. 해당 주입을 다시 돌려 그 테스트만 실패함을 확인했다 |
| 3 | 코드 검토 지적 6건. 회귀 테스트를 먼저 써서 고치기 전 9건 실패를 확인했다 | ① NUL 문자가 500이 됨 ② 저장 성공 뒤 재조회 실패를 저장 실패로 표시 ③ 404 뒤 재조회 오류 처리 없음 ④ 프로필 0개 안내에서 빠져나올 수 없음 ⑤ 수정 중 삭제해도 편집기가 남음 ⑥ 고치지 않은 제안값까지 "쓴 답"으로 봄 | ① 저장 전 거절 ② 재조회 없이 화면에 반영 ③ 실패하면 처음 버튼으로 ④ 다시 불러오기·닫기 ⑤ 편집기 닫기 ⑥ 아래 사람 결정 |

## §3 결함 주입 (16건)

각 주입은 파일을 바꾸고, 결과 파일을 지운 뒤 좁혀 실행하고, 백엔드는 `build/test-results` XML, 프론트는 Vitest JSON에서 기대한 테스트가 실행되어 실패했는지 확인한 뒤 되돌렸다. 커밋하지 않았다. 코드 검토 반영 뒤 바뀐 코드에 걸리는 1, 2, 15, 16번은 다시 돌렸다.

| # | 주입 | 실패한 기대 테스트 |
|---|---|---|
| 1 | 허용 키 검사를 뺌 | `profile_rejects_sensitive_key` 외 2건 |
| 2 | 목록 밖 키만 버리고 저장 | `profile_rejects_sensitive_key` 외 2건 |
| 3 | 목록 조회의 사용자 조건을 뺌 | `다른_사용자의_프로필은_목록에_없다` 외 1건 |
| 4 | 수정의 소유자 조건을 뺌 | `다른_사용자의_프로필_수정은_404` 외 1건 |
| 5 | 삭제의 소유자 조건을 뺌 | `다른_사용자의_프로필_삭제는_404` |
| 6 | 자동채우기의 소유자 조건을 뺌 | `다른_사용자의_프로필로_자동채우기는_404` |
| 7 | 삭제가 값을 지우지 않음 | `deleted_profile_not_used` |
| 8 | 사용자의 첫 프로필로 채움 | `autofill_selected_profile` |
| 9 | `input_format.kind`로 채움 | `autofill_own_info_only` 외 3건 |
| 10 | 선택지와 다른 값도 채움 | `autofill_option_mismatch` |
| 11 | 값 없는 문항을 `unfilled`에서 뺌 | `autofill_own_info_only` 외 2건 |
| 12 | 자동채우기가 `Response`를 만듦 | `autofill_value_reviewable 요청 전후` |
| 13 | 초안 폼 허용 | `초안_폼은_404다` |
| 14 | 목록 오름차순 | `목록은_생성_시각_내림차순` |
| 15 | 화면에서 채우기만으로 초안 저장 | `채우기만으로는 초안을 저장하지 않는다` |
| 16 | 수정값 대신 제안값 제출 | `autofill_value_reviewable 제안값을 고치면` |

## §4 사람이 정한 답과 화면 확인 (2026-11-08)

| 질문 | 답 |
|---|---|
| 채운 값의 자동 저장 시작 | 응답자가 어떤 문항이든 직접 고칠 때 채운 값까지 함께 저장 |
| 이미 쓴 답 | 빈 문항만 채움. 아직 고치지 않은 제안값은 빈 문항으로 보고 새 프로필 값으로 바꿈(코드 검토 뒤 결정) |
| 마감된 폼 | `409 form_closed` |
| 작성 중 새 버전 배포 | 최신 배포 버전 기준, 화면에 없는 문항은 무시 |
| checkbox의 `value` | 한 항목 배열 |
| 버튼 표시 | `profile_key` 문항이 있을 때만. 비로그인은 로그인 안내, 미리보기·가짜 데이터 모드는 숨김 |
| `label`과 프로필 수 | 앞뒤 공백 제거 후 1~30자, 중복 허용, 상한 없음 |
| 프로필 값 | 문자열만, 빈 값은 키 제거, 형식은 저장할 때 검사하지 않음 |

**실제 화면 확인(사람):** 1~7번 모두 문제없음. 프로필 두 개 저장, 각각으로 채우기, 채우기만 하고 나갔을 때 응답과 내 폼함이 그대로인지, 값 수정 뒤 제출, 삭제 뒤 목록에서 사라짐, 360px 폭. 개발 DB로도 제출 응답 1건, 작성 중 초안 0건을 확인했다.

**화면 확인 뒤 바뀐 것:** §2의 3회차 수정은 화면 확인 다음에 했다. 다른 프로필로 다시 채우기와 목록 반영 방식이 바뀌었고, 자동 테스트로만 확인했다.

**알려진 한계:** 로그인 뒤 폼이 아니라 첫 화면으로 돌아간다(`SecurityConfig`, 이번 범위에서 바꿀 수 없음). 폼이 삭제된 404도 "프로필을 찾을 수 없어요"로 표시한다(서버 응답이 같음). 페이지 보기에서 앞 문항이 채워져 경로가 짧아지면 보던 위치가 바뀔 수 있다.

## §5 발행한 프롬프트

```text
[구현 내용]
T9(#60): 다중 프로필 CRUD(`/me/profiles`)와 `POST /forms/{form_id}/autofill`, 프로필 화면, 응답 화면의 [내 정보로 채우기]를 만든다. 저장소 C:\Users\namhu\claude-practice\Passform, 브랜치 feat/profile-autofill(최신 origin/main에서 분기), 셸 Windows PowerShell.

[목표]
응답자가 실제 화면에서 고른 프로필의 값을 제안받아 확인·수정한 뒤 제출한다. 자동채우기 요청만으로는 `Response`가 생기거나 바뀌지 않는다. AC3~AC6과 AC68의 프로필·자동채우기 부분을 테스트와 실제 화면으로 보인다.

[절대 규칙]
- AGENTS.md 3~5절을 따른다.
- 시작 전에 T1(#49), T2(#50), T4(#52)가 main에 있는지 확인한다. 없으면 구현하지 말고 알린다.
- 프로필의 소유자 판정은 CurrentUserService로 얻은 사용자로만 한다. 인증 우회(X-Demo-User 등)와 새 permitAll을 넣지 않는다. 테스트는 spring-security-test 모의 인증을 쓴다.
- 자동채우기 대상은 `profile_key`로만 정한다. 문항 문구나 `input_format`으로 추측하지 않는다.
- [내 정보로 채우기]를 누른 것만으로 초안 저장(`PUT /me/forms/{form_id}/response-draft`)이 나가면 안 된다. 그 뒤 언제부터 자동 저장을 시작할지는 아래 객관식으로 묻는다.
- 프로필 값을 로그와 오류 응답에 남기지 않는다. `unsupported_key`의 `keys`에는 키 이름만 넣는다.
- 기존 테스트 변경 금지의 예외는 하나다. 새 오류 코드 `unsupported_key`를 ApiExceptionHandlerTest의 EXPECTED 표에 한 줄 더한다(이슈 #60 지시).
- 다음은 바꾸지 않는다: SecurityConfig, DB 스키마와 Flyway, CI, build.gradle, SPEC, ARCHITECTURE, AGENTS, data/seed, tests/harness, 제출 검증(SubmissionService), 초안 저장 규칙. 바꿔야만 끝나면 멈추고 묻는다.
- 새 의존성은 이유를 적어 승인받은 뒤에만 추가한다.
- SPEC에 없는 동작은 추측하지 말고, 맥락과 선택지를 적어 객관식으로 묻는다. 예: 채운 값의 자동 저장 시작 시점, 이미 쓴 답을 덮어쓸지, 마감된 폼의 자동채우기, 옛 버전 초안을 쓰는 중의 자동채우기, checkbox 문항의 `value` 모양, 비로그인·미리보기에서 버튼 처리, `label` 규칙과 프로필 수 상한.
- 같은 실패가 3회차를 넘거나 작업이 2시간을 넘으면 멈추고 보고한다. 작업 트리는 그대로 둔다.

[참조]
- 이슈 #60
- AGENTS.md 3~6절, 프로필 목록 정렬 87행, 세션 규칙과 공통 파일 97~105행
- docs/SPEC.md
  - 범위: 35행, 76행
  - API와 허용 키: 94~107행
  - `profile_key`: 167행
  - 초안과 자동 저장: 205~208행, 자동채우기 값 217행, 알림 메일과의 구분 228행
  - 수용 기준과 테스트 줄: AC2(374행), AC3~AC6(375~382행), AC38(406행), AC68(483~484행)
  - 추적표: 582~585행, 645행
- docs/ARCHITECTURE.md 3절, 10절(profiles 119행)
- src/main/resources/db/migration/V1__create_schema.sql의 profiles
- src/main/java/com/opinio/passform/의 domain/user/Profile.java, repository/ProfileRepository.java, controller/AccountController.java, service/CurrentUserService.java·PublicFormService.java·FormService.java, domain/error/, controller/advice/ApiExceptionHandler.java
- src/test/java/com/opinio/passform/의 controller/AccountControllerTest.java, controller/advice/ApiExceptionHandlerTest.java, config/PublicPathRealServerTest.java, support/
- frontend/src/의 pages/ProfilePage.tsx·ResponsePage.tsx, response/draftSync.ts·DraftGate.tsx, api/client.ts, types/account.ts·form.ts, routes/paths.ts, demo/, App.routes.test.tsx
- frontend/docs/ROUTES_AND_API.md
- docs/prompts/delegation_response_screen.md(AC68 확인 방식), delegation_response_draft.md

[완료 기준]
- 이슈 #60 완료 조건과 AGENTS.md 6절을 충족한다.
- 테스트 근거:
  - AC3~AC6 테스트 줄의 각 항목. 저장·재조회, 삭제 후 제출 응답 불변, 요청 전후 `Response` 수는 실제 Postgres로 확인한다.
  - 프로필 API와 자동채우기의 비로그인 401, 다른 사용자의 프로필 404(조회 결과 제외, 수정, 삭제, 자동채우기)
  - 자동채우기 요청 전후로 `Response`와 내 폼함 행이 바뀌지 않는다.
  - 없는 폼, 초안 폼, 삭제된 폼의 자동채우기는 404다.
  - 프로필 목록 순서(AGENTS.md 87행)
  - 추적표의 AC3~AC6 골든 id가 golden_cases.yaml에 있으면 실행기로 돌린다. 없으면 일반 테스트로 하고 테스트 이름이나 보고에 id를 적는다. YAML에 케이스를 더하지 않는다.
  - 화면: 프로필 추가·수정·삭제, `unsupported_key` 안내, 프로필 고르기, 제안값 표시와 수정, 수정값으로 제출, `unfilled` 문항은 빈 채로 유지, 채운 값으로 분기 경로가 다시 계산됨
  - 실제 API 모드의 프로필 화면에 데모 사용자 값이 나오지 않는다.
- 실제 화면 확인을 사람에게 요청하고 결과를 기록한다.
  - 로그인 뒤 프로필 두 개 저장, 각각으로 채우기, 값 수정 뒤 제출
  - 채우기만 누르고 나갔을 때 응답과 내 폼함이 그대로인지
  - 프로필 삭제 뒤 그 프로필이 고르는 목록에서 사라지는지
  - 360px 폭에서 프로필 화면과 채우기를 가로 스크롤 없이 터치로 사용
  - 시험 데이터용 스크립트는 에이전트가 준비하되, 승인 없이 개발 DB에 실행하지 않는다.
- 결함 주입: 아래를 하나씩 넣었을 때 테스트가 실패해야 한다. 기대한 테스트가 실제로 실행됐는지 build/test-results XML(프런트는 Vitest 결과)로 확인하고, 실행되지 않았으면 판정 무효로 다시 돌린다. 주입은 커밋하지 않고, 항목마다 되돌린다.
  - 허용 키 검사를 뺀다
  - 목록 밖 키만 버리고 나머지를 저장한다
  - 목록 조회에서 사용자 조건을 뺀다
  - 수정에서 소유자 조건을 뺀다
  - 삭제에서 소유자 조건을 뺀다
  - 자동채우기에서 프로필 소유자 조건을 뺀다
  - 삭제가 프로필 값을 지우지 않는다
  - 고른 프로필 대신 사용자의 첫 프로필로 채운다
  - `profile_key` 대신 `input_format.kind`로 채운다
  - 선택지와 다른 프로필 값도 채운다
  - 값이 없는 문항을 `unfilled`에서 뺀다
  - 자동채우기 요청이 `Response`를 만든다
  - 초안 폼에도 자동채우기를 허용한다
  - 프로필 목록을 생성 시각 오름차순으로 낸다
  - 화면에서 채우기만으로 초안 저장을 보낸다
  - 화면에서 수정한 값 대신 제안값을 제출한다
- 위임 기록을 docs/prompts/delegation_profile_autofill.md에 남긴다. 넣을 것: 이 프롬프트 원본, 회차 표, 결함 주입 결과, 사람이 정한 답.

[범위 밖]
- 빌더의 본인 정보 확인과 `profile_key` 설정(E3·E13)
- 내 폼함과 제출 이력 화면(E5)
- 제출 후 수정·취소(T10)
- 응답 파일 업로드와 첨부(T12)
- 문항 문구를 읽어 프로필 항목을 추측하는 AI 매핑(SPEC 76행)

[깃허브 규칙]
- 사람의 명시적 승인을 받은 뒤에만 커밋·push·PR을 한다. 승인 요청에는 `git status --short`, `git diff --stat`, 핵심 diff, 테스트 집계, 제안 커밋 메시지를 보여 준다. 커밋 승인은 push 승인이 아니다.
- 커밋은 AGENTS.md 5절 협업 항목과 README.md를 따른다. 공통 파일(paths, types, client, 공통 컴포넌트) 변경은 별도 커밋으로 나눈다.
- PR 제목: `[feat] 프로필과 자동채우기 (#60)`. 본문은 README.md를 따른다.
- 리뷰어는 gh pr create --reviewer ye-nni로 지정한다.
- main에 직접 push하거나 머지하지 않는다.

[보고]
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 테이블 표 / 추가한 의존성과 이유 / 지시와 다르게 한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
- 테스트마다 통과 이유를 코드 위치와 함께 한 줄로 적는다.
- AC3~AC6, AC68의 테스트 줄 항목마다 자동 테스트, 사람 확인, 범위 밖 중 무엇으로 처리했는지 적는다.
```
