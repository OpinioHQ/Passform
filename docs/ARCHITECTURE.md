# Passform 아키텍처

> **11/18 전 사람이 확정한다.** `☐`는 아직 안 정한 것이고, "제안"은 확정 전의 안이다. 강의 6 지침의 절 구성(1~9절)을 따르고, SPEC 25행이 아키텍처로 넘긴 저장 방식을 10절에 더했다.

## 1. 한눈에 보는 데이터 흐름

**AI 결과 탐색:** 제작자 발화 → 범위 판정 → 파싱(`RequestContext`) → 되묻기 → 조건 매칭 → 되묻기 → 조건 평가(`ConditionEvaluator`) → 결과 처리 → 요약(분류·근거 검색·요약문) → 응답

**폼 수집:** 제작자 빌더(폼 정의 JSON) → 배포(버전 고정) → 공유 링크 → 응답자 작성 → 서버 재검증 → 제출 → 내 폼함·이력 / 결과

## 2. 구성 요소

파일명은 시험 데모 기준의 계획이다. 구현하면서 실제 이름으로 고친다.

| 구성 요소 | 하는 일 | 출처 | 상태 |
|---|---|---|---|
| `domain/analysis/ConditionEvaluator` | 참·거짓·판정 불가, all/any/not | 강의 5 | main에 있음, 수용됨 |
| `src/response_analysis/prompts/parse_query.md`, `schemas/request_context.schema.json` | 파서 프롬프트와 스키마 | 강의 3 | main에 있음. v3 결정 대기 |
| `src/passform/schemas/form.schema.json` | 폼 정의 | 강의 4 | 저장소에 있음 |
| `llm/QueryParser` | 자연어 → `RequestContext`, 재검증, 형식 오류 1회 재호출 | 강의 3·6 | MVP |
| `domain/analysis/QueryGuard` | 범위·통계 원문 규칙 | 강의 6 | MVP |
| `service/ConditionMatchingService`, `llm/ConditionLlmMatcher` | 규칙 우선, 못 푼 조건만 LLM. 프롬프트는 임시본(`src/response_analysis/prompts/match_conditions.md`), AC21 미충족 | 스파이크 | MVP |
| `domain/form/ResponsePathCalculator` | 분기 경로. 제출 검증과 판정이 공유 | 강의 4 | MVP |
| `service/AnalysisService` | 순서 고정 흐름 | 강의 6 | MVP |
| `service/SummaryService`, `llm/ThemeClassifier`, `llm/SummaryWriter` | 요약 | 강의 6 | MVP |
| `service/SubmissionService`, `domain/form/AnswerValidator` | 제출 재검증 | 강의 4 | MVP |
| `llm/LlmClient`, `OpenAiClient` | 외부 호출 경계. 테스트는 모의 객체 | AGENTS 5절 | MVP |
| `frontend/` | React, TypeScript, Tailwind | 강의 4 | 아직 미추적. 이슈 E1에서 커밋 |

**오케스트레이션:** workflow. 별도 프레임워크 없이 함수 조합(5절).

## 3. 도구 목록

**결정(2026-11-04, 사용자): 모델이 도구를 고르는 function calling은 쓰지 않고 코드가 순서를 고정한다.** 2026-11-05 공지에서 이에 대한 다른 요구가 없음을 확인했다.

- 이유: 단계를 미리 열거할 수 있고, 숫자와 대상 응답은 코드가 정해야 한다(절대 규칙 3). 모델이 다음 단계를 정할 지점이 없다.
- 아래 표는 workflow가 코드로 부르는 단계다. 모델이 고르는 도구가 아니므로 설명은 사람이 읽는 문장이다. 시그니처 타입은 온톨로지 클래스 이름을 쓴다.

| 단계 | 시그니처 | 설명 | 부작용 | 외부 전송 |
|---|---|---|---|---|
| parse_query | `(query: string) -> RequestContext` | 자연어 요청을 구조로 바꾼다 | 없음(읽기) | 발화 |
| match_conditions | `(conditions: [string], form: Form) -> [ConditionMatch]` | 조건을 버전별 문항·선택지에 연결한다. 글자가 같으면 규칙으로 풀고 못 푼 조건만 LLM에 보낸다 | 없음(읽기) | 조건, 문항 문구·선택지(개인정보 문항 포함). 응답 값은 보내지 않는다 |
| filter_responses | `(context: RequestContext, matched: [ConditionMatch], responses: [Response]) -> Result` | 조건에 맞는 제출 응답을 고른다 | 없음(읽기) | 없음 |
| classify_themes | `(answers: [AnswerChunk]) -> [ThemeLabel]` | 답마다 주제 라벨 하나를 붙인다. 개수는 코드가 센다 | 없음(읽기) | AI 요약에 동의한 일치 응답의 대상 문항 답 전체. 개인정보 문항 제외, 전화번호·이메일 가림 |
| search_evidence | `(query: string, answers: [AnswerChunk]) -> [AnswerChunk]` | 요약 근거를 임베딩으로 찾는다 | 없음(읽기) | classify_themes와 같은 범위의 답(임베딩) |
| write_summary | `(evidence: [AnswerChunk], counts: [ThemeLabel]) -> string` | 근거만 보고 요약문을 쓴다 | 없음(읽기) | 근거와 코드가 센 주제별 개수 |

`ConditionMatch`, `AnswerChunk`, `ThemeLabel`은 구현 타입이다(온톨로지 클래스 아님). `AnswerChunk`는 Response의 답 하나를 검색 단위로 본 것이고, `ThemeLabel`은 응답 하나에 붙은 주제 라벨이다.

**구현 위치** (이름은 구현하면서 실제 이름으로 고친다)

- parse_query → `llm/QueryParser`
- match_conditions → `service/ConditionMatchingService`, `llm/ConditionLlmMatcher`
- filter_responses → `domain/analysis/ConditionEvaluator.evaluate(conditions, match, matching, responses)`. 코드의 입력은 `ResponseRecord`, 출력은 `EvaluationResult`이고 `count`가 `Result.filtered_response_count`다
- classify_themes → `llm/ThemeClassifier`
- search_evidence → `service/SummaryService`와 임베딩 클라이언트
- write_summary → `llm/SummaryWriter`

**쓰기 도구 없음.** 모델 출력으로 데이터가 바뀌는 경로가 없어 사용자 승인 단계가 없다. 분석은 읽기만 하며, 분석 경로가 DB에 쓰지 않음을 테스트로 확인한다.

## 4. RAG

상세는 `config/rag.yaml`.

- 대상: 일치하고 AI 요약에 동의한 응답의 주관식 답. 답 1개 = 1청크.
- 저장소: 없음. 요청마다 임베딩한다. 대상이 조건마다 다르고 응답 원문 임베딩을 남기지 않으려는 선택이다.
- 검색 파라미터: 주제 `top_k 3`, `min_score 0.2` / 의견 `top_k 3`, `min_score 0.35`. **둘 다 임시값이다.**
- **검색에 맡기지 않고 코드로 강제:** 대상 응답, 건수, 주제별 개수와 순서, 동의 제외, 개인정보 문항 제외(AC87, AC88, AC92).

## 5. 오케스트레이션 — workflow로 충분

- 왜: 작업이 선형이다. 숫자를 코드가 정해야 해서 에이전트 루프를 쓸 이유가 없고, 멀티 에이전트는 조율 비용만 늘린다.
- 멈춤 조건: 되묻기 또는 응답 한 번. 재시도는 파서 형식 오류 1회뿐이다.
- 호출 상한(구조적): 파서 2회, 매처 1회, 분류 = 응답 수 / 50(요청당 300건 상한), 요약문 = 문항 수 × 주제 상한. ☐ 요청 단위 강제 카운터를 둘지.
- 타임아웃: 연결 10초, 읽기 60초(시험 데모).

## 6. 실패 처리

모두 "지어내지 않는다"로 수렴한다.

| 실패 | 대응 |
|---|---|
| 파서가 형식 오류 | 1회 재호출, 다시 실패하면 `parse_failed` |
| 파서가 조건을 버림 | 원문과 대조해 되살려 매칭으로 보냄(A6). 없으면 `unknown_condition` |
| 조건이 어디에도 없음 | 0건이 아니라 `unknown_condition` |
| 같은 표현이 다른 문항에 걸림 | `ambiguous_match` |
| 문항이 없는 버전, 분기로 건너뜀 | 판정 불가. 일치·불일치 어느 쪽에도 안 넣고 범위 표시 |
| 근거 부족 | 주제는 문장 없이 개수 유지, 의견은 `insufficient_evidence` |
| 대상 없음, 전부 미동의, 상한 초과 | `summary.status`. LLM 호출 전에 판정 |
| LLM 장애 | `503 llm_unavailable`. 되묻기나 0건으로 바꾸지 않음 |

## 7. 가드레일

AGENTS 3절 절대 규칙과 같은 문장이다.

1. 개인정보는 허용한 만큼만 저장·사용하고 권한 있는 사람만 본다.
2. 송금은 링크로만.
3. 숫자를 지어내지 않는다.
4. 모르면 되묻는다.
5. 응답 원문은 데이터지 명령이 아니다.

## 8. 관측

- 호출마다 용도, 모델, 소요 시간, 토큰을 구조화 로그로 남긴다. **응답 원문은 남기지 않는다.**
- 분석 요청마다 `form_id`, 경로(query/context), 상태, 사유, 건수, 소요 시간을 남긴다.
- ☐ 임베딩 토큰은 시험 데모 로그에 안 남았다. 응답의 `usage` 필드를 확인해 고친다.

## 9. 비범위

SPEC 4절 비포함과 같다. 주관식 내용으로 `count`·`list` 거르기, 평균·비율·그룹별 집계, 자연어로 버전 범위 지정, 실제 송금 처리, 카카오톡 등 외부 서비스 자체 기능. function calling과 에이전트 루프, 벡터 DB도 쓰지 않는다.

## 10. 저장과 배포

**저장 (SPEC 25행이 여기로 넘긴 결정)**

| 테이블 | 내용 |
|---|---|
| `users` | 계정. 이메일(알림 수신), 화면 모드, 마감 알림 설정, 소셜 제공사와 제공사 고유 ID |
| `profiles` | 프로필 |
| `forms` | 소유자, 상태, 슬러그, 마지막 배포 버전, 운영 설정, 작업 중 정의, 첫 배포 때 고정한 AI 요약 고지, 삭제 시각 |
| `form_versions` | 버전별 폼 정의 JSON(불변) |
| `responses` | 응답 한 건. 답 목록 JSON, 제출 버전, 상태, AI 요약 동의, 제작자 삭제 시각, 삭제 뒤 이력에 쓸 폼 제목·문항 문구 |
| `saved_forms`, `notifications` | 내 폼함, 알림 |
| `consent_templates`, `question_templates` | 템플릿 |
| `attachments` | 첨부 메타데이터. 원본은 S3 |

- **결정(2026-11-04, 사용자):** 문항·답변 테이블은 만들지 않고 폼 정의와 답을 `jsonb` 컬럼에 둔다. DB 변경은 Flyway로 관리한다. SPEC 25행이 이 결정을 아키텍처로 넘겼고 SPEC·AGENTS에 다른 기준은 없다.
- 근거: 시험 데모에서 버전 판정, 이력, 통계, 분석이 모두 이 구조로 동작했다. 문항이 버전마다 바뀌어 테이블로 나누면 동기화가 늘어난다.
- 조건 판정은 어차피 코드에서 한다. 응답마다 제출 버전과 분기 경로로 참·거짓·판정 불가를 가리므로, 답변 테이블로 나눠도 읽어서 코드로 판정한다.
- 측정(2026-11-04, 이 노트북, 요청 1건, 분기 경로 계산과 동시 접속 제외): 5,000건 기준 읽기 87ms, JSON 해석과 조건 평가 27ms, 건수 2ms, 제출 1건 저장 1ms. LLM 호출(1~13초)에 비해 작다.
- 약점: 폼 하나의 응답을 매번 전부 읽는다. AC34(동시 300명)는 안 쟀다. 분석·통계 지연이 문제가 되면 캐시나 집계 테이블을 더한다.
- `jsonb`를 고른 이유: DB가 JSON 문법을 검사하고 나중에 SQL 집계가 가능하다. **시험 데모는 `text`로만 동작을 확인했으니, Hibernate·Jackson 3 매핑은 첫 구현 때 먼저 확인한다.** 매핑이 안 되면 엔티티는 문자열로 받고 서비스에서 변환한다. 그래도 안 되어 `text`로 바꿔야 하면 결정이 달라지므로 사람에게 먼저 알린다.
- Flyway를 고른 이유: 동료 데이터가 생기면 `ddl-auto`를 쓸 수 없다. `build.gradle`에 의존성을 추가해야 한다.

**제약과 읽기 규칙**

- 유일 제약: `saved_forms(user, form)`(AC17), `notifications(user, form, type)`(AGENTS 5절), `form_versions(form, version)`, `forms.slug`, 소셜 제공사·고유 ID. 로그인 작성 중 응답은 폼당 하나(SPEC 206행, Flyway 부분 인덱스).
- 결과·분석·내보내기는 한 곳에서 응답을 읽고, `submitted`이면서 제작자가 삭제하지 않은 응답만 다룬다(AC20).
- AI 요약 고지는 첫 배포 때 `forms`에 기록해 고정한다. 복사본은 자기 첫 배포 때 새로 기록한다(SPEC 158행, AC90).

**삭제 규칙 (T12에서 구현, SPEC 224행, AC66, AC67)**

- 받은 응답 전체 삭제: 한 트랜잭션에서 로그인 응답은 제작자 삭제 시각을 기록하고 이력에 쓸 폼 제목·문항 문구를 복사한다. 비로그인 응답은 지운다.
- 폼 삭제: 위와 같이 처리하고 폼 정의와 버전을 지운다. 이력이 `form_id`를 돌려주므로(SPEC 192행) `forms` 행은 삭제 시각과 함께 남긴다. 공유 링크는 404다.
- 제출 때 복사하지 않는 이유: 버전 정의가 불변이라 삭제 때 복사해도 같고, 매번 복사하면 500MB 사용량에 중복으로 잡힐 수 있다.
- ☐ 폼 삭제 뒤 `saved_forms`를 내 폼함에 어떻게 보일지. SPEC에 없어 T12 착수 전에 사람이 정한다.

**파일 (제안, 팀원 확인)**

- 업로드는 SPEC 5절대로 서버가 `multipart/form-data`로 받아 검증한 뒤 S3에 저장한다. 응답과 파일을 함께 저장해야 하기 때문이다(SPEC 222행).
- 다운로드는 서버가 권한을 확인한 뒤 Presigned URL로 넘긴다.

**배포**

- **결정(2026-11-05, 사용자):** 도메인 하나를 사서 프론트는 `app.<도메인>`(Vercel, 루트 `frontend/`), 백엔드는 `api.<도메인>`(AWS)로 둔다. 소셜 로그인은 Google이다.
- 이유: 도메인이 아예 다르면 세션 쿠키가 제3자 쿠키가 되어 Safari 등에서 로그인이 깨질 수 있다. Vercel 프록시는 요청이 120초로 제한되고, 업로드 본문 한도와 `vercel.app` 승인 가능 여부를 확인하지 못했다. 백엔드 HTTPS에 어차피 도메인이 필요하다.
- 세션 쿠키는 `Secure`, `SameSite=Lax`. CORS는 `app.<도메인>` 출처만 허용하고 자격 증명을 허용한다. CSRF는 켜 두고, 로그인 없는 응답 제출(SPEC 188행)까지 확인한다.
- Google 콜백은 `https://api.<도메인>/login/oauth2/code/google`, 권한은 `openid`, `email`, `profile`만 쓴다. 등록하지 않은 Google 계정으로 로그인되는지 직접 확인한다.
- **제안(E9 착수 전 확정):** EC2 1대에 docker compose로 백엔드, Postgres, HTTPS 프록시를 둔다. DB는 하루 1회 백업한다. 동료 소수가 일주일 쓰는 규모를 가정했고, 동시 300명(AC34)은 보장하지 못한다.
- **제안(E9 착수 전 확정):** 비밀값은 AWS SSM Parameter Store에 두고 서버 환경변수로 주입한다. 저장소, `VITE_` 변수, Vercel 설정에 넣지 않는다.
- OpenAI: 프로젝트 전용 키를 쓰고 프로젝트 한도에서 "Enforce a hard limit"을 켠다. 켜지 않으면 호출이 멈추지 않는다. 초과 시 오는 429 `project_spend_limit_exceeded`는 `503 llm_unavailable`로 처리한다. 시연 중 멈추지 않도록 한도를 넉넉히 잡는다.
- 첫 폼을 배포하기 전에 SPEC 362행대로 OpenAI 프로젝트의 데이터 위치와 공유 설정을 확인한다. 고지는 첫 배포 때 고정되기 때문이다.
- ☐ 도메인 구입. E9 첫날 Google 로그인 왕복을 먼저 시험한다.
- 시험 데모의 `demo` 프로필과 `X-Demo-User`는 배포에 쓰지 않는다.

**폼 스키마 결정 (리뷰에서 팀 확정)**

| # | 내용 | 제안 | 이유 |
|---|---|---|---|
| F1 | 분기의 제출 표현. SPEC은 `go_to(section_id/question_id/submit)`만 적음 | `{"submit": true}` | 시드와 같은 객체 형태. 문자열과 섞이면 파싱이 갈린다 |
| F2 | `settings.ai_summary`, `response_limit` | 선택. 없으면 꺼짐, 제한 없음 | 시드에 없고, 시드는 잠겨 있다 |
| F3 | 형식 검증에 넣을 것 | 선택지 중복 금지, 단답·장문에 선택지 금지, 입력 형식은 단답만, 글자 수는 단답·장문만 | 초안에서도 틀린 구조는 저장하지 않는다 |
| F4 | 의미 검증(배포 때 서버가 함) | 분기 대상 존재·뒤쪽, 조건 문항·선택지 존재, 보기 방식 조합, 동의 템플릿, 선택형 선택지 1개 이상, 문항 문구 비어 있지 않음 | SPEC 174행. 초안은 미완성이어도 저장 |

**골든 구조 (리뷰에서 팀 확정)**

| # | 내용 | 제안 | 근거 |
|---|---|---|---|
| G1 | `golden_cases.yaml` 안에서 기능 나누기 | `cases`는 조건 평가 전용으로 두고 기능별 최상위 키 추가(`analysis_cases`, `submission_cases` 등) | 현재 하니스는 `cases`만 읽어 전부 평가 함수 케이스로 처리한다(`ConditionEvaluatorGoldenTest.java:228`). 다른 최상위 키는 읽지 않는다 |
| G2 | 실행 수준 | service 수준. LLM과 저장소는 모의 객체. `access_cases`만 MVC 슬라이스 | 케이스를 빠르게 돌린다. 권한은 필터와 컨트롤러를 지나야 판정된다. 모의 저장소로는 동시성을 판정할 수 없어 동시 제출은 T3 완료 조건에서 따로 시험한다 |
| G3 | 파서 입력 | 모의 파서에 고정 출력을 준다 | 테스트에서 실제 LLM을 부르지 않는다(AGENTS 5절). 실제 출력 패턴은 `docs/spikes/2026-11-04_test_demo.md` 3-1절 |

**골든 작성 시점**

- **결정(2026-11-05, 사용자):** 1순위 42건을 필수 29건, 추가 12건, 통합 시험 1건으로 나눈다. 필수 29건은 구현 전에 구현 담당자가 한 번에 쓴다. 빨리 끝나면 추가 12건도 구현 전에 쓴다. 추가 건은 구현을 막지 않고, 못 쓰면 X2에서 쓰며 구현 뒤 작성임을 위임 기록에 밝힌다. `response_limit_concurrent_submit`은 YAML이 아니라 T3의 실제 DB 통합 시험으로 둔다. 에이전트는 기대값을 쓰지 않는다.

**골든 형식 (리뷰에서 팀 확정)**

공통: 케이스마다 `name`, `ac`, `type`(정상/경계/금지 행동), `input`, `note`와 판정 키 1개 이상. 정해진 키 밖의 키는 로더가 거부한다. 판정 키는 케이스에 있는 것만 검사한다. 시드 폼의 소유자는 `owner`, 시드 응답의 제출자는 `respondent`로 본다. `cases`는 조건 평가 전용으로 유지한다.

| 최상위 키 | 이슈 | `input` | 판정 키 |
|---|---|---|---|
| `access_cases` | T2, E5, E6 | `fixture`, `actor`(`anonymous`·`owner`·`respondent`·`other_user`), `method`, `path` | `expect_status`, `must_not_contain` |
| `form_cases` | E2 | `fixture`, `actor`, `change`(바꿀 최상위 키), `action` | `expect_status`, `expect_error`, `expect_unchanged` |
| `submission_cases` | T3 | `fixture`, `now`, `form_version`, `answers`, `consent_agreed`, `ai_summary_agreed` | `expect_status`, `expect_error`, `expect_question_ids`, `expect_saved_answers`, `expect_response_count` |
| `analysis_cases` | E6, E7 | `fixture`, `query` 또는 `context`, `parser_outputs`(호출 순서대로), `matching`(`cases`와 같은 형태) | `expect_status`, `expect_error`, `expect_reason`, `expect_options`, `expect_parser_calls`, `expect_count`, `expect_response_ids`, `expect_not_evaluable_count`, `expect_not_evaluable_by_version`, `must_not_include` |
| `summary_cases` | T7 | `fixture`, `context`, `labels`, `scores` | `expect_summary_status`, `expect_count`, `expect_response_ids`, `expect_excluded_no_consent_count`, `expect_theme_counts`, `llm_must_not_contain`, `must_not_include` |

`analysis_cases`의 `expect_status`는 HTTP 상태다. `expect_reason`이 있으면 `status: clarify`와 `result: null`까지 함께 검사한다.

새 fixture 2개는 골든 작성의 맨 앞 단계이며 담당은 팀원이다: 요약용 폼(AI 요약 켬, 개인정보 문항, 이름·전화번호·이메일·날짜 입력 형식 문항 포함(생년월일 확인 문항 포함), 동의·미동의 혼합, 장문 속 연락처), 필수 문항 분기 폼(분기로 건너뛰는 문항이 필수). 사람이 `data/seed`에 추가하고 시드 README 표를 갱신한다.
