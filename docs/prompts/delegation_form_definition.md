# 폼 저장·배포·버전 API 위임 기록 (#40)

기록 대상: E2 폼 저장·배포·버전 API. 실행일 2026-11-07. 작성한 프롬프트는 §2에 그대로 둔다.

## §1 결함 주입

하나씩 임시로 넣고 `.\gradlew.bat test -x jacocoTestReport --continue`를 실행한 뒤 파일을 되돌렸다. 주입은 커밋하지 않았다. 항목마다 `build/test-results`를 지우고 XML을 읽어 판정했다. 타이밍에 좌우되는 항목이 없어 항목마다 1번 실행했다.

### 구현 전: 실행기 자체 (스텁 `FormService`, 대상 `FormGoldenTest`)

| 주입 | 기대 실패 | 실제 결과 | 근거 |
|---|---|---|---|
| 미구현 스텁 그대로 | 실패(skip 금지) | 잡음 | `UnsupportedOperationException`으로 실패. skip 0 |
| 오류 없이 정상 종료 | `expect_error` | 잡음 | 기대 `consent_template_required`, 실제 없음 |
| 다른 오류 코드로 거부 | `expect_error` | 잡음 | 기대 `consent_template_required`, 실제 `invalid_branch` |
| 맞는 코드로 거부하지만 `consent_template_id`를 먼저 지움 | `expect_unchanged` | **처음에는 놓침.** 실행기 수정 후 잡음 | 아래 설명, 수정 뒤 `expect_unchanged: consent_template_id` 실패 |

**놓친 이유:** 실행기의 "요청 전 값"이 엔티티의 Map을 그대로 참조해서, 값이 바뀌면 전후가 함께 바뀌어 항상 같았다. 요청 전후 값을 깊은 복사하도록 고쳤다. 이 실행기 버그는 결함 주입이 아니었으면 찾지 못했다.

### 구현 후: 구현에 주입

대상: `FormGoldenTest`, `FormApiDatabaseTest`, `FormPublishValidatorTest`, `FormVersionImmutabilityTest`, `PublicFormServiceTest`.

| # | 주입 | 실제 결과 | 잡은 테스트(대표) |
|---|---|---|---|
| G1 | 동의 템플릿 검사 제거 | 잡음 | `every_open_form_needs_consent_template`의 `expect_error`, DB 테스트 6건 |
| G2 | 동의 검사 전에 status를 open으로 바꿈 | 잡음 | 골든 `expect_unchanged: status` |
| G3 | 동의 검사 전에 작업 정의를 바꿈 | 잡음 | 골든 `expect_unchanged: consent_template_id` |
| G4 | 동의 검사 전에 버전을 기록함 | 잡음 | 골든 `expect_unchanged: definition_version` |
| T1 | `FormVersion`의 `@Immutable` 제거 | 잡음 | `읽어서_고치고_flush한_뒤_다시_읽어도_처음_값이다` |
| T2 | 운영 설정도 버전 대상으로 취급 | 잡음 | 전체 흐름 테스트에서 버전이 2가 아니라 3 |
| T3 | 초안 저장에도 의미 검증 | 잡음 | `AC56_draft_저장은_의미_검증을_하지_않고_버전을_올리지_않는다` |
| T4 | 새 버전마다 slug 재발급 | 잡음 | 전체 흐름 테스트의 `share_url` 불변 |
| T5 | 새 버전마다 AI 고지 다시 기록 | 잡음 | `AC90_고지는_첫_배포_때_기록하고_새_버전에서도_그대로다` |
| T6 | AI 요약 잠금 제거 | 잡음 | `AC90_첫_배포_뒤에는_되돌리거나_새_버전을_내도_바꿀_수_없다` |
| T7 | 응답이 있어도 open → draft 허용 | 잡음 | `AC57_허용되지_않는_전이는_invalid_transition이고_상태를_바꾸지_않는다` |
| T8 | 인원 재오픈 검사 제거 | 잡음 | `AC85_` 2건 |
| T9 | 초안도 공개 조회 | 잡음 | `AC38_없는_폼과_초안은_공개_조회에서_404다` |
| T10 | 마감 시각 경계 어김(`now == deadline`을 열림으로) | 잡음 | `마감_시각에_정확히_닫힌다` |
| T11 | 제작자 확인 제거 | 잡음 | `AC2_다른_사용자는_정의를_읽거나_고칠_수_없다` |
| T12 | 버전 저장 생략 | 잡음 | 전체 흐름 등 DB 테스트 20건 |
| T13 | `profile_key`로 수집 항목 생성 | 잡음 | `AC13_공개_조회는_SPEC_187행의_키와_동의_항목을_준다` |
| T14 | `invalid_payment_link`를 `schema_invalid`로 뭉침 | 잡음 | `AC11_https가_아닌_링크는_invalid_payment_link다` |

- G2~G4는 주로 골든이 잡는다. 실제 DB 테스트는 실패한 트랜잭션이 되돌려져 같은 결함이 드러나지 않을 수 있다. 메모리 저장소를 쓰는 골든이 "검증 전에 엔티티를 바꾸지 않는다"를 직접 본다.
- 주입 도구를 처음 돌렸을 때 두 번 실행되지 않았다(gradle 경로, JSON의 탭 문자). `tests=0`과 결과 파일 없음을 보고 알아차려 고친 뒤 다시 돌렸고, 위 표는 다시 돌린 결과다.

### 리뷰 반영 뒤: 문항 0개 배포 거부

대상: `FormApiDatabaseTest`, `FormPublishValidatorTest`.

| 주입 | 실제 결과 | 잡은 테스트 |
|---|---|---|
| `FormPublishValidator`의 문항 0개 검사 제거 | 잡음. 3건 실패 | `F4_문항이_하나도_없는_폼은_초안에_저장되지만_배포되지_않는다`, `F4_배포된_폼의_문항을_모두_지우는_저장은_거부하고_마지막_배포본은_그대로다`, `문항이_하나도_없으면_schema_invalid다` |

## §2 위임 프롬프트 원본

사람이 검토해 발행. 2026-11-07 실행.

```text
이번 작업은 이슈 #40(E2 폼 저장·배포·버전 API)뿐이다. 동의 템플릿 문구 작성도 포함한다. 다른 이슈는 구현하지 않는다. 나는 ye-nni이고 리뷰어는 Psoyeon1117이다. 셸은 Windows PowerShell이다.

# 목표
폼을 초안으로 저장하고, 검증해 배포하고, 버전을 보존하고, 공유 링크로 공개 조회하는 서버 API를 만든다. 화면은 E3이 연결한다.

# 시작 확인 (하나라도 어긋나면 멈추고 알린다)
1. git status로 추적 파일 변경이 없는지 본다. 미추적 docs/SPEC_followups.md는 정상이다.
2. git fetch origin, git switch main, git merge --ff-only origin/main을 실행한다.
3. main에 tests/harness/golden_cases.yaml의 every_open_form_needs_consent_template가 있는지 확인한다.
4. Docker와 저장소 루트의 .env를 확인하고 docker compose up -d db를 실행한다. 없으면 만들지 말고 알린다. 비밀값을 출력하지 않는다.
5. .\gradlew.bat cleanTest build가 시작 시점에 통과하는지 본다. 같은 PC에서 다른 폴더의 build를 동시에 돌리지 않는다(DatabaseTest가 passform_test를 비운다).
6. main에서 브랜치 feat/form-definition을 만든다.
7. PR #75의 상태를 본다(gh pr view 75). 아직 안 들어왔으면 아래 "PR #75와의 겹침"을 따른다.

# 먼저 읽을 것
AGENTS.md, gh issue view 40, docs/SPEC.md의 5절 폼 제작(115~120, 150~176행, 204행), 187행 공개 조회, 210~214행 동의, AC8·11·12·13·38·39·41·42·46·56·57·58·85·90, docs/ARCHITECTURE.md 170~173행(F1~F4)과 10절 골든 형식(199~203행), src/passform/schemas/form.schema.json, data/seed/README.md 가정 7·17, tests/harness/golden_cases.yaml의 form_cases, docs/prompts/delegation_google_login.md(기록 형식).
행 번호가 어긋나면 내용으로 찾는다.

# 1단계: 동의 템플릿 문구 (구현 전에 끝낸다)
1. 공식 자료를 직접 찾아 읽는다. 대상은 국가법령정보센터(law.go.kr)의 현행 개인정보 보호법과 시행령, 개인정보보호위원회(pipc.go.kr)의 동의 관련 공식 안내서다. 블로그와 법률 사무소 글은 근거로 쓰지 않는다.
   출발점: 동의 시 알릴 사항(제15조 제2항), 동의 받는 방법(제22조), 만 14세 미만 아동(제22조의2), 제3자 제공(제17조), 처리 위탁(제26조). 조문 번호는 내 기억이므로 원문으로 확인하고, 다르면 원문을 따른다.
2. 찾지 못하거나 사이트에 접근할 수 없으면 추측해서 쓰지 말고 멈춘다.
3. 아래를 먼저 보여 주고 내 승인을 받는다. 승인 전에는 Flyway를 쓰지 않는다.
   - consent_default의 purpose, retention 초안과 동의 거부 안내(SPEC 210행의 refusal_notice) 초안
   - 근거 표: 문구 / 조문 또는 안내서 위치 / URL / 확인일
   - 문구가 응답 화면에서 consent.items, purpose, retention, refusal_notice로 어떻게 보이는지 예시
4. 문구는 실제 동작과 맞아야 한다. 코드가 하지 않는 일을 약속하지 않는다.
   - 보유 기간은 SPEC 224행, AC66, AC67을 확인한다. 제작자가 받은 응답이나 폼을 삭제해도 로그인 응답자의 텍스트 제출 이력은 남는다. 자동 파기 기능은 없다. "제출 후 N년 뒤 파기"처럼 구현되지 않은 약속을 쓰지 않는다.
   - 응답자에게 쉬운 말로 쓴다. 조문 번호를 문구에 넣지 않는다.
5. 템플릿은 consent_default 하나로 시작한다. 시드가 이 ID를 쓴다. 더 필요하다고 판단되면 이유를 적어 묻는다.
6. 법령상 지금 계약으로 충족하기 어려운 점은 고치지 말고 객관식으로 묻는다. 특히 다음을 확인해 보고한다: 응답을 받는 주체(폼 제작자)를 알려야 하는지, 제작자에게 응답이 전달되는 구조, 만 14세 미만 응답자, consent 응답 필드가 부족한지. SPEC 변경이나 필드 추가가 필요하면 사람이 정한다.
7. 보고에 "AI가 공식 자료로 작성한 초안이며 법률 자문이 아니다"를 적는다. AI 요약 고지(SPEC 211행)는 이미 정해진 문구이므로 바꾸지 않는다. 어긋나 보이면 알린다.

# 절대 규칙
- 판정 기준(tests/harness, data/seed, 두 스키마 폴더, src/response_analysis/prompts, config/rag.yaml, evals/evalset.jsonl)을 고치지 않는다. deny를 셸로 우회하지 않는다.
- 기존 테스트의 단언문과 기대값을 고치지 않는다. @Disabled나 skip으로 통과시키지 않는다. 기존 테스트를 고쳐야만 통과하면 멈추고 묻는다. 코드를 더할 때 안내된 EXPECTED 표에 줄을 더하는 것은 된다.
- 엔티티를 API에 직접 쓰지 않는다. controller → service → repository 계층을 지킨다. 상태 전이와 불변식은 엔티티 메서드에 둔다. 현재 시각은 주입한 Clock으로만 얻는다.
- 키와 비밀값을 파일, 커밋, 출력에 넣지 않는다. 인증 우회를 넣지 않는다. 현재 사용자는 CurrentUserService로 얻는다.
- docs/SPEC.md, docs/ARCHITECTURE.md, AGENTS.md, frontend/를 고치지 않는다. SPEC의 "빌더가 기본 동의 템플릿을 미리 선택한다"는 이 PR에 넣지 않는다.
- 커밋·PR에 에이전트 이름이나 생성 문구를 넣지 않는다. PR 본문에 사람을 @로 언급하지 않는다. _local/과 docs/SPEC_followups.md는 커밋하지 않는다.
- git add는 경로를 명시하고, 스테이징 목록을 확인한 뒤 커밋한다. 명령을 ;로 이어 add 실패 뒤 commit이 실행되지 않게 한다.

# 바꿔도 되는 것 / 안 되는 것
- 바꿔도 됨: src/main, src/test, 새 Flyway 파일, tests/java의 새 FormGoldenTest, docs/prompts의 새 기록 파일.
- 안 됨: 위 절대 규칙의 파일, tests/java의 기존 실행기, 머지된 V1 마이그레이션. AccessGoldenTest가 새 컨트롤러나 서비스 때문에 깨지면 고치지 말고 알린다.

# 구현 범위 (이슈 #40)
- POST /forms, GET /forms/{form_id}/definition, PATCH /forms/{form_id}, 상태 전이, share_url 발급
- 형식 검증(form.schema.json, F3)과 의미 검증(F4)
- 버전 불변 저장과 운영 설정 분리(SPEC 155~156행, AC56, AC58)
- 공개 조회 GET /f/{slug}, GET /forms/{form_id}(consent 항목과 마감 안내, AC13, AC41). 초안과 삭제된 폼은 404다(AC38). UUID가 아닌 form_id도 404다.
- GET /me/created-forms, GET /consent-templates
- 첫 배포 때 AI 요약 고지 기록과 잠금(SPEC 157~158행, AC90), 인원 재오픈 거부(AC85의 response_limit_reached)
- 새 오류 코드의 advice 매핑 테스트(ApiExceptionHandlerTest의 EXPECTED)
- 동의 템플릿은 data/seed가 아니라 새 Flyway 파일로 넣는다. PR 전에 최신 번호를 다시 확인한다.

범위 밖(미룸): 폼 복사와 삭제 API, 첨부 API, 저장 용량(storage_quota_exceeded)은 다른 이슈다. 제출, 화면, 추천 문항도 구현하지 않는다. 판단이 필요하면 묻는다.

# 이번에 함께 넣을 결정
- share_url은 설정 passform.frontend-origin 뒤에 /f/{slug}를 붙인다. SPEC 204행의 <도메인>을 프론트 주소로 읽는다.
- FormVersion에 @Immutable을 붙인다. 지금은 getDefinition()이 수정 가능한 Map을 돌려줘 읽은 값을 바꾸면 flush 때 DB까지 바뀔 수 있다(코드로 판단). "읽어서 고치고 flush한 뒤 다시 읽어도 처음 값"인 실제 DB 테스트와, 버전 삭제가 막히지 않는지 보는 테스트를 더한다. 삭제가 막히면 멈추고 알린다.

# 골든 먼저
1. 구현 전에 form_cases만 읽는 tests/java의 FormGoldenTest를 만든다. 현재 하니스는 form_cases를 실행하지 않는다. 기대값은 YAML에만 둔다. 이 케이스의 note대로 form_a의 최신 정의를 응답 없는 초안으로 만들어 시작한다.
2. 실행기가 판정 키를 빠뜨리지 않는지 결함 주입으로 확인한다. 예: 동의 템플릿 검사 제거, expect_unchanged 위반. 잡히는지 기록한다.
3. 그다음 구현한다. skip 0으로 통과해야 한다.
추가 골든(consent_lists_input_formats, share_url_closed_form)은 main에 없으니 쓰지 않는다. 같은 규칙은 일반 테스트로 검사하고 PR에 적는다.

# PR #75와의 겹침
PR #75(T3, 아직 main에 없을 수 있음)는 Form.java, ErrorCode, ApiExceptionHandler, FormRepository, SecurityConfig를 고치고 domain/form/FormDefinition을 더한다.
- 같은 이름의 클래스를 새로 만들지 않는다. 필요하면 git show origin/feat/response-submission:<경로>로 보고, 머지됐으면 재사용한다. 안 됐으면 다른 이름으로 만들고 PR에 적는다.
- 공통 파일은 줄 추가 위주로 고치고, 시작과 PR 전에 #75의 상태와 충돌을 확인한다.

# 완료 기준
- .\gradlew.bat cleanTest build 성공. 결과 XML의 tests, failures, skipped, timestamp를 보고한다. FormGoldenTest는 통과 1, 실패 0, skip 0이다.
- 기존 조건 평가, 제출, 접근 골든과 로더가 그대로 통과한다. 접근 골든 2건의 라우트 미구현 skip은 이 PR에서 늘지 않는다.
- 실제 Postgres에서 시험한다: 초안 저장 → 배포 실패 때 초안·버전 불변 → 배포 → 버전 1과 share_url → 공개 조회 → 정의 수정 → 버전 2, 버전 1 그대로 → 운영 설정만 수정 → 버전 그대로.
- SPEC 115~120행과 187행의 입출력과 오류 코드대로 동작한다. 커버리지 검사를 통과한다.
- git diff origin/main...HEAD --name-only가 허용 범위뿐이다. 커밋 뒤 git status --short에 docs/SPEC_followups.md만 남는다.
- docs/prompts/delegation_form_definition.md에 위임 프롬프트, 실패·수정 루프, 결함 주입, 사람 결정, 문구의 근거 표를 기록한다.
- PR의 CI에서 build와 frontend job이 통과한다.

# 중단 조건 (멈추고 보고한다)
- 같은 실패로 3회차를 넘길 때, 또는 작업이 3시간을 넘길 때. 사람의 답을 기다린 시간은 빼고 잰다.
- SPEC·AGENTS에 없는 판단이 필요할 때, SPEC과 fixture가 충돌할 때, 판정 기준이나 기존 테스트를 바꿔야 할 때, 이슈가 너무 커서 나눠야 할 때. 객관식으로 묻고 추천안을 첫 번째에 둔다.

# 커밋과 보고
- 커밋 "{tag}: 한글 메시지 (#40)", 50자 이내, 파일명·디렉터리명 금지. 공통 파일 변경은 커밋을 나눈다.
- push 전에 커밋과 변경 파일 목록을 보여 주고 내 확인을 받는다.
- PR 제목 "[feat] E2 폼 저장·배포·버전 API (#40)", 본문은 README 형식, 관련 이슈 "Closes #40", --reviewer Psoyeon1117. 다른 이슈가 알아야 할 것을 적는다: 새 오류 코드, 공개 조회 응답, 폼 저장 사용법, consent_default, E3이 쓸 API. 동의 문구는 AI가 공식 자료로 초안을 쓰고 사람이 승인했으며 법률 자문이 아니라고 적는다.
- PR을 연 뒤 CI를 확인하고 멈춘다. 머지하지 않고 다음 이슈로 넘어가지 않는다.
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 골든과 결함 주입 결과 / 동의 문구와 근거 표 / 지시와 다르게 한 것 / 확인하지 못한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
```

## §3 회차 표

| 회차 | 한 일 | 결과 | 수정 |
|---|---|---|---|
| 시작 확인 | `docker compose up -d db` | 엔진 API 500, DB 없음. 재시작도 멈춤 | 멈추고 보고. 사람이 Docker를 업데이트해 복구 |
| 기준 build | `cleanTest build` | tests 140, failures 0, skipped 2 | 없음 |
| 코드 읽기 | `SchemaStartupTest` 54~56행 확인 | 마지막 버전을 "1"로 단언해 새 Flyway와 충돌 | 멈추고 물음. 사람 승인으로 기대값을 "2"로 변경 |
| 골든 1 | 스텁으로 `FormGoldenTest` 실행 | 실패(의도한 빨간불) | 없음 |
| 골든 2 | 스텁에 결함 3건 주입 | `tests=0`. gradle 경로 오류로 실행되지 않음 | 경로 수정 |
| 골든 3 | 다시 주입 | `expect_unchanged` 위반을 놓침 | 실행기가 요청 전 값을 깊은 복사하게 수정 |
| 골든 4 | 다시 주입 | 3건 모두 잡음 | 없음 |
| 구현 1 | 전체 테스트 | tests 247, 실패 4 | 아래 두 건 |
| | `AC12_AC8` 2건 | 분기 폼의 `one_question`은 AC46에서 허용인데 거부를 기대함 | 구현이 맞고 새 테스트가 틀림. 새 테스트의 입력 수정 |
| | `UniqueConstraintTest` 2건 | 새 DB 테스트가 커밋한 응답이 남아 전체 행 수가 어긋남 | 새 테스트가 끝난 뒤 자기 행을 지움. 기존 테스트는 고치지 않음 |
| 구현 2 | `cleanTest build` | tests 247, failures 0, skipped 2 | 없음 |
| 주입 1 | 구현에 18건 주입 | 결과 파일 없음. JSON의 탭 문자로 실행 전 종료 | 파서 설정 수정 |
| 주입 2 | 다시 주입 | 18건 모두 잡음. G4는 골든 결과가 잘림 | G4만 골든으로 다시 실행해 확인 |
| 마무리 | 인원 범위 초과 테스트 추가, `cleanTest build` | tests 248, failures 0, skipped 2 | 없음 |
| 리뷰 반영 | 비로그인 취소 문구, 문항 0개 배포 거부, 위험 목록·문서 보완, `cleanTest build` | tests 253, failures 0, skipped 2 | 없음 |

같은 실패로 3회차를 넘긴 항목은 없다.

## §4 사람 결정

| 질문 | 결정 | 영향 |
|---|---|---|
| 동의 문구 초안 | 초안대로 승인 | `V2__insert_consent_default.sql`, `ConsentNotices.REFUSAL_NOTICE` |
| 응답을 받는 주체 고지 | 현 계약 유지, 위험 기록 | 문구에 "폼을 만든 제작자"로 적음. `consent` 필드와 동의 수는 그대로 |
| 만 14세 미만 | 문구 없이 보고만 | `refusal_notice`에서 해당 문장 뺌. 연령 확인 없음 |
| `SchemaStartupTest` 54~56행 | 기대값을 "2"로 변경 | 다음에 Flyway를 더하는 이슈도 같은 줄을 고쳐야 한다 |
| F4의 문구·선택지, ID 중복 오류 코드 | 모두 `schema_invalid` | ID 중복 검사는 F4에 없던 것을 더한 것이다 |
| Docker 복구 | 재시작 승인 → 실패 후 사람이 업데이트 | 시작 확인 4번 재실행 |
| 문항 0개인 폼의 배포 | 거부(`schema_invalid`, F4에 추가) | 초안 저장은 그대로다. 문항이 있는 폼의 빈 섹션은 막지 않는다 |
| `retention`의 제출 취소 문장 | 로그인 제출만 취소할 수 있게 수정 | 머지 전이라 `V2`를 직접 고쳤다. 새 Flyway를 만들지 않았다 |
| `ApiExceptionHandlerTest`의 `Map.ofEntries` 변경 | 승인(diff에서 기존 4쌍의 값이 그대로인 것을 확인) | `Map.of`의 10쌍 제한 때문이다 |

사람에게 묻지 않고 정한 작은 판단이다. 리뷰에서 다르게 정할 수 있다.

| 판단 | 근거 |
|---|---|
| 없는 `status` 값은 `409 invalid_transition`이 아니라 `400 schema_invalid` | 전이 위반이 아니라 형식 오류다 |
| `status`가 현재와 같으면 전이가 아니라 저장으로 처리 | 전체 정의를 다시 보내는 클라이언트를 막지 않는다 |
| 없는 동의 템플릿 ID도 `consent_template_required` | 공개 조회가 문구를 찾지 못한다 |
| `open → draft`는 의미 검증 없이 저장 | 초안은 미완성을 허용한다(SPEC 174행) |
| 판정 순서: 소유 → 형식 → AI 잠금 → 전이 → 의미 → 인원 | SPEC에 순서가 없다. 400 계열 안에서는 117행 나열 순서를 따랐다 |
| 삭제된 폼은 제작자 API도 404 | SPEC 187행의 공개 조회와 맞췄다 |
| `FormController`는 AGENTS 5절의 DTO·`@Valid` 대신 JSON을 받아 `form.schema.json`으로 검증 | 폼 정의의 형식 검사는 스키마가 맡고(AGENTS 5절), 배열이나 문자열 본문도 `schema_invalid`로 답한다. 수용 여부는 리뷰에서 정한다 |

## §5 동의 문구와 근거

**AI가 공식 자료로 작성한 초안을 사람이 승인했다. 법률 자문이 아니다.** 확인일은 모두 2026-11-07이다.

```text
purpose
폼을 만든 제작자가 이 폼의 제목과 설명에 안내된 일을 진행하기 위해 응답을 받아 확인하고 집계·분석합니다. 로그인해 제출하면 내 제출 내역에서 다시 볼 수 있도록 계정에 연결해 보관합니다.

retention
정해진 보유 기간이나 자동 삭제는 없습니다. 제출한 응답은 제작자가 받은 응답이나 폼을 삭제할 때까지 보관합니다. 로그인해 제출했다면 제작자가 삭제한 뒤에도 내 제출 내역에 폼 제목, 제출 시각, 문항 문구, 답변 내용이 남고 첨부 파일은 삭제됩니다. 로그인해 제출했고 폼이 제출 취소를 허용하며 아직 열려 있으면 제출을 취소해 응답과 첨부를 없앨 수 있습니다. 로그인하지 않고 제출한 응답은 직접 취소할 수 없습니다. 그 밖에는 제출 내역을 직접 지우는 기능이 아직 없고, 제작자가 내려받은 파일은 Passform에서 지울 수 없습니다.

refusal_notice
동의를 거부할 수 있습니다. 동의하지 않으면 이 폼에 응답을 제출할 수 없습니다.
```

자료:

- 법: [개인정보 보호법](https://www.law.go.kr/LSW/lsInfoR.do?lsiSeq=283839&chrClsCd=010202&efYd=20260911&ancYnChk=0), 법률 제21445호, 2026.9.11. 시행
- 영: [개인정보 보호법 시행령](https://www.law.go.kr/LSW/lsInfoR.do?lsiSeq=289537&chrClsCd=010202&efYd=20260911&ancYnChk=0), 대통령령 제36671호, 2026.9.11. 시행
- 안내서: [개인정보 처리 통합 안내서(2025.7.)](https://pipc.go.kr/np/cop/bbs/selectBoardArticle.do?bbsId=BS217&mCode=G010030000&nttId=11352). 쪽수는 인쇄 쪽 기준이다. 옛 「알기 쉬운 개인정보 처리 동의 안내서」는 이 안내서에 흡수되어 폐지됐다(안내서 표지 뒤 제·개정 이력).

| 문구 | 근거 | URL |
|---|---|---|
| 이 폼의 제목과 설명에 안내된 일을 진행 | 법 15조②1호, 영 17조①2·3호 | 법, 영 |
| 폼을 만든 제작자가 응답을 받아 확인 | 법 17조②1·2호, 안내서 68쪽 | 법, 안내서 |
| 수집 항목(`consent.items`) | 법 15조②2호, SPEC 210행 | 법 |
| 정해진 기간 없음, 삭제할 때까지 | 법 15조②3호, 안내서 27쪽, SPEC 224행, AC66, AC67 | 법, 안내서 |
| 로그인 제출 내역과 첨부 삭제 | 법 15조②3호, 영 17조③3호, SPEC 224행 | 법, 영 |
| 제출 취소 가능 조건, 지우는 기능 없음 | 영 17조①2호, SPEC 195·215·223·225·226행 | 영 |
| 내려받은 파일은 지울 수 없음 | 영 17조①2호, SPEC 239행 | 영 |
| 거부할 수 있음, 제출 불가 | 법 15조②4호, 안내서 27쪽, SPEC 210행 | 법, 안내서 |

### 지금 계약으로 충족하기 어려운 점 (고치지 않고 기록)

| 쟁점 | 확인한 내용 | 현재 상태 |
|---|---|---|
| 받는 주체 고지 | 제3자 제공 동의에는 받는 자의 이름이나 상호를 알린다. 특정이 어려운 사정이 있을 때만 유형 고지가 가능하다(법 17조②1호, 안내서 68쪽) | "폼을 만든 제작자"로만 알린다 |
| 전달 구조 | 오픈마켓이 판매자에게 주문정보를 주는 것은 제3자 제공의 예시다(안내서 168쪽). 양쪽 이익이 섞인 위탁도 제3자 제공으로 판단될 수 있다(167쪽) | 어느 구조인지 확정하지 않았다 |
| 동의 개수 | 제3자 제공이면 수집·이용 동의와 구분해 각각 받는다(법 22조①1·2호) | `consent_agreed` 하나 |
| 만 14세 미만 | 처리자가 동의를 근거로 처리할 때 법정대리인 동의와 확인이 필요하다(법 22조의2①, 영 17조의2). 아동 이용이 예상되는 서비스는 연령 확인이 바람직하다(안내서 138쪽) | 연령 확인과 안내 문구 없음 |
| AI 요약 고지(SPEC 211행) | 국외 이전 별도 동의에는 이전 시기·방법, 받는 자의 연락처, 거부 방법·절차를 알린다(법 28조의8②) | 고지 객체에 필드가 없다. 처리 국가의 "확인 후 확정"이 응답자에게 보이고 첫 배포 때 고정된다. 문구는 SPEC 그대로 넣었다 |
| 파기·철회 | 불필요해지면 지체 없이 파기한다(법 21조①). 동의 철회 시 파기 등의 조치를 한다(법 37조①·③) | 자동 파기 없음. 제출 내역을 지울 경로 없음. SPEC 215행에서 철회 경로 미정 |
| 처리방침·위탁 공개 | 처리방침 공개(법 30조), 수탁자 공개(법 26조②) | SPEC에 해당 화면이 없다 |
| 민감정보 | 별도 동의가 필요하다(법 22조①5호, 23조) | 형식 없는 문항으로 받으면 구분하지 못한다 |
| 보유 기간 | 기간을 구체적으로 정해 알리는 것을 기본으로 하고, 특정할 수 없으면 기준을 알릴 수 있되 알릴 수 없는 사유는 처리자가 입증한다(안내서 27쪽) | 정해진 기간이 없고, 제작자가 삭제한 뒤의 로그인 제출 내역은 끝나는 기준도 없다 |
| 목적의 구체성 | 동의받으려는 내용이 구체적이고 명확할 것을 요구한다(영 17조①2호) | 문구가 목적을 "제목과 설명에 안내된 일"에 맡기는데 설명이 비어 있어도 배포된다 |
| 제출 전 자동 저장 | 필수 동의는 제출 때 받는다(SPEC 188행). 로그인 응답의 미완성 답은 그 전에 계정에 저장된다(SPEC 190·205행) | 이 저장의 처리 근거는 확인하지 않았다 |
| 문구가 기대는 기능 | 제출 취소(#61), 받은 응답·폼 삭제(#63)는 아직 없고 우선 2다 | 미뤄지면 문구가 실제 동작과 달라지므로 11/9 점검 때 본다 |

법령해석 문의처: 개인정보보호위원회 법령해석 지원센터 02-2100-3043(안내서 표지 뒤).

## 수용 판단

사람이 리뷰 뒤 적는다.
