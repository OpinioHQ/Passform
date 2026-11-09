# AI 요약 위임 기록 (#55)

기록 대상: T7 AI 요약. 실행일 2026-11-08. 프롬프트는 §5에 그대로 둔다. 시험 데모(`feat/demo`)의 요약 코드는 구조만 참고했고 복사하지 않았다.

## §1 필수 골든 3건이 통과하는 이유

| 케이스 | 이유 | 코드 위치 |
|---|---|---|
| `personal_question_never_sent` | 요약 대상을 `isSummarizable`(개인정보 문항 제외)로 고르고 `q8`은 답이 전부 날짜라 유형 A에서 빠진다. 선택지 값은 `option_stats`로 코드가 세고 외부로 보내지 않는다 | `SummaryService.groups`, `themeItems`, `optionStats` |
| `contact_masked_in_long_answer` | 외부로 나가는 답은 만들 때 한 번 가리고, 분류·임베딩·요약문이 모두 그 값만 쓴다. 전화번호 구분자는 선택이다 | `SummaryService.groups`, `ContactMasker.mask` |
| `no_consent_excluded_from_summary_only` | 외부 전송은 동의 응답만이고, `count`·`response_ids`·미동의 수는 일치 응답 전체로 낸다. rs07은 저장소가 주는 제출 응답에 없다 | `SummaryService.summarize`, `AnalysisService.summaryResult` |

모의 분류기·임베딩은 요청 본문에 실제로 온 답에만 값을 준다. 표에 없는 답을 받으면 실패한다(`SummaryGoldenTest.Scenario`). 금지어 검사가 저절로 통과하지 않게 세 경계가 불렸는지 먼저 본다.

## §2 실패·수정 회차

| 회차 | 실패 | 원인 | 수정 |
|---|---|---|---|
| 1 | 테스트 컴파일 오류 | varargs가 아닌 메서드에 `@SafeVarargs` | 맞는 메서드로 옮겼다 |
| 2 | 일반 테스트 3건 | 테스트의 모의 점수 함수: 삼항 연산의 null 언박싱(NPE), 라벨 문자열까지 답처럼 점수를 줌. AC31의 금지어가 프롬프트 예시 문구와 같았다 | 테스트 입력을 고쳤다. 구현은 그대로다 |
| 3 | 결함 주입 1차 판정 무효 | `gradlew` 상대 경로로 Gradle이 실행되지 않아 이전 XML을 읽었다. 패턴 2건은 셸 이스케이프와 탭 수가 달랐다 | 절대 경로, 실행 전 XML 삭제, XML 없으면 무효 처리 |
| 4 | 주입 6번이 NPE로 실패 | 주입이 집계가 아니라 다른 곳을 터뜨렸다 | "코드가 센 값 대신 LLM이 준 라벨 수"로 다시 주입해 AC87 단언으로 확인했다 |
| 5 | 전체 빌드에서 `AccessGoldenTest` 6건 | 이 테스트는 service만 스캔해 `SummaryService`의 llm 의존성을 찾지 못했다 | 사람 승인으로 `@MockitoBean` 필드 하나를 더했다 |

환경: Docker 데몬이 꺼져 있던 동안에는 DB가 필요 없는 테스트만 돌렸다. `frontend/node_modules`가 `main`보다 오래되어 `npm ci`로 맞췄다.

## §3 결함 주입 (11건)

각 주입은 파일을 바꾸고, `build/test-results/test`를 지운 뒤 `--tests`로 좁혀 실행하고, XML에서 기대한 테스트가 실행되어 실패했는지 확인한 뒤 되돌렸다. 커밋하지 않았다.

| # | 주입 | 실패한 기대 테스트 |
|---|---|---|
| 1 | 개인정보 문항 판정에서 `profile_key` 조건을 뺌 | `AC92_개인정보_문항의_값은_어떤_요청_본문에도_없다` 외 3건 |
| 2 | 하이픈 없는 전화번호를 가리지 않음 | `contact_masked_in_long_answer` |
| 3 | 이메일을 가리지 않음 | `contact_masked_in_long_answer` 외 1건 |
| 4 | 미동의 응답의 답을 분류에 보냄 | `no_consent_excluded_from_summary_only`, AC91 외 2건 |
| 5 | `count`를 동의 응답 수로 냄 | `no_consent_excluded_from_summary_only`, AC91 외 4건 |
| 6 | 주제 개수를 LLM 출력에서 가져옴 | `AC87_개수는_코드가_라벨을_세고_많은_순서이며_기타는_맨_뒤다` 외 1건 |
| 7 | `기타`를 맨 뒤로 보내지 않음 | `AC87_기타가_가장_많아도_맨_뒤다` |
| 8 | 유형 D에 개수를 넣음 | `AC89_유형_D는_items가_없고_topic에_개수가_없으며_count는_그대로다` 외 7건(응답 스키마 위반 포함) |
| 9 | 근거를 다른 주제의 응답에서 고름 | `AC88_근거는_그_주제의_응답_안에서만_고르고_top_k와_요약문_입력도_그_범위다` 외 1건 |
| 10 | 상한을 넘어도 LLM을 부름 | `AC93_동의한_일치_응답이_상한을_넘으면_too_many_responses이고_외부_호출이_없다` |
| 11 | 응답 원문을 데이터 구분자 밖에 넣음 | `AC31_답_속_지시문은_건수와_목록을_바꾸지_않고_데이터_구분자_안에만_있다` |

주입 1은 처음에는 입력 형식이 있는 문항만으로는 본문 검사에서 드러나지 않았다. AC92 테스트에 `profile_key`만 있는 문항을 더한 뒤 돌렸다.

## §4 사람이 정한 답 (2026-11-08)

| 질문 | 답 |
|---|---|
| 요약 분기를 지우면 깨지는 기존 테스트 | `AnalysisServiceTest` 87행·`AnalysisGoldenTest` 294행은 생성자 인자만 추가. 316행은 `status: ok`로 단언 변경. 381행은 삭제하지 않고 "요약 서비스가 낸 summary를 돌려준다"로 내용 교체 |
| `AccessGoldenTest`의 컨텍스트 실패 | `SummaryService`의 `@MockitoBean` 필드와 import만 추가. 케이스·단언·YAML은 그대로 |
| 라벨 종류가 `max_themes`를 넘을 때 | 일반 라벨 상위 5개 + `기타`로 총 6개 |
| 분류 응답에 라벨이 빠지거나 빈 경우 | 재시도 없이 전체 503. 요약문이 비거나 깨져도 같다 |
| 유형 A의 숫자·날짜 단답 | 문항 단위. 동의 답이 전부 숫자·날짜인 단답 문항만 뺀다 |
| 동의 답이 없는 문항 | 유형 A는 뺀다. 유형 B·C는 `themes: []`로 둔다 |
| 개인정보 선택형 문항의 `option_stats` | 넣지 않는다 |
| `evidence[].excerpt` | 가린 문장. `max_chars`를 넘으면 자르고 `…`를 붙인다 |
| `summary_unavailable_by_version` | 일치 응답이 1건 이상인 버전만. 없으면 키를 뺀다 |

**구현 중 고른 것(SPEC에 답이 없어 PR에 적는다):** `config/rag.yaml`을 서버가 읽도록 `build.gradle`의 리소스 경로에 한 줄을 더했다(의존성 추가 없음). 분류 입력의 키는 응답 ID 대신 묶음별 짧은 키다. 요약문은 순서대로 호출한다(병렬화는 측정 뒤 판단). `option_stats`는 `themes` 뒤에 둔다(SPEC 예시와 같음).

**미확인 — E8 대기:** E8(#46)이 `main`에 없어 화면 표시는 만들지 않았다. 실제 OpenAI 호출도 하지 않았고, 요청 형태는 로컬 스텁 서버로만 확인했다. 조건이 있는 요약은 E7(#45)의 실제 매처가 들어와야 운영에서 동작한다(지금 운영 매처는 조건이 있으면 503이다).

**실제 호출 시도(2026-11-08):** `form_summary`를 로컬 개발 DB에 넣고 호출을 시도했지만, 키가 서버에 등록되지 않아 OpenAI로 요청이 나가지 않았다(503). 비용이 드는 호출이라 사람이 보류를 결정했고, E8 때 화면 확인과 함께 한다.

**E8 뒤 마무리(2026-11-09):** 위의 "미확인 — E8 대기"는 아래로 해소했다. 요약 결과의 화면 표시는 E8(#46, PR #94)이 `frontend/src/pages/ExplorePage.tsx`에 구현했고(주제·개수·요약문·근거, 선택지 통계, 특정 의견, `summary.status` 안내, 미동의 제외 안내), 이 작업에서 프론트와 서버 코드는 더 바꾸지 않았다. E7(#45)도 닫혀 조건 있는 요약이 운영 매처로 동작한다.

사람 확인은 `main` `27923ac`, 실제 OpenAI, 실제 화면(탐색 도우미), `form_summary` 시드(제출 6건, 미동의 2건)로 했다. OpenAI 호출 수와 비용은 세지 않았다. 문항 요약과 특정 의견 요약은 E8 사람 확인(`delegation_explore_screen.md` §6)에서도 통과했다.

| # | 입력 | 유형 | 결과 |
|---|---|---|---|
| 1 | 전체 응답 요약해줘 | A 전체 | 통과 |
| 2 | 지원 동기 요약해줘 | B 문항 | 통과 |
| 3 | 건의 사항에서 가장 많이 나온 의견은? | C 빈도 | 요약이 나오지 않음. 되물은 뒤 매칭 실패로 끝남. 틀린 숫자는 없었다 |
| 4 | 면접 시간 안내가 필요하다는 의견 요약해줘 | D 특정 의견 | 통과 |
| 5 | 소프트웨어학과 지원 동기 요약해줘 | 조건 + 문항 | 통과 |

사람이 전체 결과를 통과로 보고했다. 확인 항목은 미동의 응답 문장이 근거에 없음, 전화번호·이메일 가림, 개인정보 문항 값이 보이지 않음이다. 3번은 요약 단계 전에 파서에서 갈린 것으로, 사람이 파서 문제로 판단했다. 파서 재측정(T8, #56)과 프롬프트 채택 결정에서 다룬다.

**리뷰 반영(PR #89, 2026-11-08):** 국가번호 뒤 지역번호(`+82-2`, `+82-31`)와 여러 글자 구분자(`010 - 1234 - 5678`)도 가리도록 `ContactMasker`를 고치고 `ContactMaskerTest`에 세 형식을 더했다. 수정 전 코드에서 세 형식이 실패하는 것을 확인했다.

**미룸(11/11 뒤):** 요약문 작성 입력도 화면 근거와 같은 200자 발췌를 쓰므로, 관련 내용이 200자 뒤에만 있는 장문은 모델이 그 내용을 보지 못한다. 관련 구간 발췌 등은 11/11 뒤에 판단한다.

## 수용 기준 기록

**기존 테스트 수정 승인(사람, 2026-11-08):** AI 요약 구현 전에 연결된 기능에서 오류가 나지 않도록 둔 임시 내용을 AI 요약 기능에 맞게 수정한 것이다. 판정 기준을 낮추려는 수정이 아니다.

| 위치 | 수정 |
|---|---|
| `AnalysisServiceTest` 316행 | 요약 대기 예외로 "되묻지 않음"을 간접 확인하던 것을 `status: ok`, `clarify: null`로 직접 확인 |
| `AnalysisServiceTest` 381행 | "T7 전까지 대기 예외로 실패"를 "요약 서비스가 낸 `summary`를 돌려준다"로 교체 |
| `AnalysisServiceTest` 87행, `AnalysisGoldenTest` 294행 | 생성자에 요약 서비스 인자 추가 |
| `AccessGoldenTest` | 요약 서비스의 `@MockitoBean` 필드 추가 |

골든 케이스, 기대값, `golden_cases.yaml`, fixture는 바꾸지 않았다.

**통과 확인:** `gradlew cleanTest build` 541건 중 실패 0, skip 4건(E7 3건, `other_users_response_404`). 이 4건은 이번 작업 전부터 있던 skip이다. 필수 골든 3건은 skip 없이 통과했다. 줄 커버리지 98.7%. 프론트엔드 lint, typecheck, test 235건 통과.

## §5 발행한 프롬프트

```text
[구현 내용]
T7(#55): `POST /forms/{form_id}/analyze`의 `summarize` 단계를 만든다. 대상 확정과 동의 제외, 개인정보 문항 제외, 연락처 가림, 주제 분류와 코드 집계, 근거 검색, 요약문 작성, 요약 결과의 화면 표시가 포함된다. 저장소 C:\Users\namhu\claude-practice\Passform, 브랜치 feat/ai-summary(최신 origin/main에서 분기), 셸 Windows PowerShell.

[목표]
제작자가 요약을 요청하면 코드가 정한 대상과 개수로 주제 요약이 나오고, 미동의 응답·개인정보 문항·연락처는 외부 요청에 나가지 않는다. 필수 골든 3건을 통과하고 AC31, AC86~AC89, AC91~AC93을 테스트로 보인다.

[절대 규칙]
- AGENTS.md 3~5절을 따른다.
- 시작 전에 선행 작업 E6(#44), 필수 골든 3건(`summary_cases`), 요약용 fixture(`form_summary.json`)가 main에 있는지 확인한다. 없으면 구현하지 말고 알린다.
- 외부 전송 금지 판정은 LLM·임베딩 클라이언트가 실제로 받은 요청 본문 전체로 한다. 중간 목록만 검사하지 않는다.
- 응답은 AnalysisService가 이미 쓰는 읽기 한 곳에서 온 것만 쓴다. 응답을 읽는 다른 경로를 만들지 않는다.
- LLM·임베딩을 부르는 동안 DB 연결을 잡지 않는다(AnalysisService 41행의 방식).
- OpenAI 키를 출력하거나 저장소·로그에 남기지 않는다. 응답 원문과 외부 요청 본문을 로그에 남기지 않는다.
- 다음은 바꾸지 않는다: `ResponseRepository`, `SecurityConfig`, `RequestContext`와 그 스키마, Flyway, CI, SPEC, ARCHITECTURE, AGENTS. `failIfStepPending`은 요약 분기만 지운다. 바꿔야만 끝나면 멈추고 묻는다.
- 프롬프트 파일과 `config/rag.yaml`을 바꿔야만 끝나면 멈추고 묻는다(AGENTS.md 4절).
- 새 의존성은 이유를 적어 승인받은 뒤에만 추가한다.
- SPEC에 없는 동작은 추측하지 말고, 맥락과 선택지를 적어 객관식으로 묻는다. 예: 라벨 수가 상한을 넘을 때, 분류 응답에서 일부 답의 라벨이 빠졌을 때.
- 같은 실패가 3회차를 넘거나 작업이 2시간을 넘으면 멈추고 보고한다. 작업 트리는 그대로 둔다.

[참조]
- 이슈 #55
- AGENTS.md 3~6절(LLM 경계와 응답 원문 75·76행, 프롬프트 버전 95행)
- docs/SPEC.md
  - 개인정보 문항 168행, AI 요약 동의 211~213행
  - 분석 API 235행, 범위 244행
  - 요약 규칙 316~332행, 되묻기 표 347·349행
  - 수용 기준과 테스트 줄: AC91(449행~), AC31(533행~), AC86~AC89(538~545행), AC92·AC93(546~549행)
  - 추적표 610행, 663~670행
- docs/ARCHITECTURE.md 2절, 3절, 4~8절, 10절의 G2(180행)와 골든 형식 표(203행)
- docs/api/analyze_api.md, docs/api/analyze_examples.json
- docs/spikes/2026-11-04_test_demo.md 2-6절, 4-4절
- docs/prompts/delegation_analyze_api.md
- src/response_analysis/prompts/classify_themes.md, summarize.md
- src/response_analysis/schemas/analyze_response.schema.json
- config/rag.yaml
- tests/harness/golden_cases.yaml의 머리 주석(349~352행), summary_cases(499행~)
- data/seed/forms/form_summary.json, data/seed/README.md
- tests/java/com/opinio/passform/golden/AnalysisGoldenTest.java
- src/main/java/com/opinio/passform/의 service/analysis/, llm/, domain/analysis/, domain/response/, domain/error/ErrorCode.java
- src/test/java/com/opinio/passform/llm/OpenAiQueryParserTest.java
- frontend/src/의 pages/ExplorePage.tsx, types/analyze.ts, api/client.ts

[완료 기준]
- 이슈 #55 완료 조건과 AGENTS.md 6절을 충족한다.
- 골든 실행기(tests/java의 새 파일)가 `summary_cases`를 읽어 케이스마다 동적 테스트를 만든다. 케이스를 코드에 복사하지 않는다. 필수 골든 3건이 실제로 실행되어 통과하고 skip이 0이다. 다른 골든도 모두 통과한다.
- 실행기의 모의 분류기와 임베딩은 실제로 요청받은 답의 `labels`·`scores`만 돌려준다. 요청받지 않은 답의 값을 돌려줘 통과시키지 않는다.
- 일반 테스트(LLM·임베딩은 모의 객체): AC31, AC86~AC89, AC91~AC93의 테스트 줄 각 항목과 LLM 실패 시 `503 llm_unavailable`
- 응답이 `analyze_response.schema.json`을 통과한다.
- 결함 주입: 아래를 하나씩 넣었을 때 테스트가 실패해야 한다. 기대한 테스트가 실제로 실행됐는지 build/test-results XML로 확인하고, 실행되지 않았으면 판정 무효로 다시 돌린다. 주입은 커밋하지 않고, 항목마다 되돌린다.
  - 개인정보 문항 판정에서 `profile_key` 조건을 뺀다
  - 하이픈 없는 전화번호를 가리지 않는다
  - 이메일을 가리지 않는다
  - 미동의 응답의 답을 분류에 보낸다
  - `count`를 동의 응답 수로 낸다
  - 주제 개수를 LLM 출력에서 가져온다
  - `기타`를 맨 뒤로 보내지 않는다
  - 유형 D에 개수를 넣는다
  - 근거를 다른 주제의 응답에서 고른다
  - 상한을 넘어도 LLM을 부른다
  - 응답 원문을 데이터 구분자 밖에 넣는다
- 실제 요약 확인: 화면 표시는 E8(#46)이 main에 있을 때 만든다. 있으면 사람에게 실제 화면과 실제 OpenAI 호출 확인을 요청하고 결과를 기록한다. 없으면 API까지 구현하고 "미확인 — E8 대기"로 남긴다.
- 위임 기록을 docs/prompts/delegation_ai_summary.md에 남긴다. 넣을 것: 이 프롬프트 원본, 회차 표, 결함 주입 결과, 사람이 정한 답.

[범위 밖]
- 파서·매처 프롬프트 검토와 재측정(T8)
- 정렬 목록(E7)
- 탐색 화면 자체(E8). 이번 작업은 요약 결과 표시만 더한다.
- evals와 실제 품질 측정(E11)
- 요약 결과의 내보내기(T11)
- 임베딩 영속 저장, 캐시, rerank

[깃허브 규칙]
- 사람의 명시적 승인을 받은 뒤에만 커밋·push·PR을 한다. 승인 요청에는 `git status --short`, `git diff --stat`, 핵심 diff, 테스트 집계, 제안 커밋 메시지를 보여 준다. 커밋 승인은 push 승인이 아니다.
- 커밋은 AGENTS.md 5절 협업 항목과 README.md를 따른다. 골든 실행기와 공통 파일(types, client) 변경은 별도 커밋으로 나눈다.
- PR 제목: `[feat] AI 요약 (#55)`. 본문은 README.md를 따른다. "미확인 — E8 대기"가 남으면 `Closes #55` 대신 `#55`로 적는다.
- 리뷰어는 gh pr create --reviewer ye-nni로 지정한다.
- main에 직접 push하거나 머지하지 않는다.

[보고]
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 테이블 표 / 추가한 의존성과 이유 / 지시와 다르게 한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
- 필수 골든 3건과 새 테스트마다 통과 이유를 코드 위치와 함께 한 줄로 적는다.
- 실제 OpenAI 호출 여부와 확인한 범위를 적는다.
```
