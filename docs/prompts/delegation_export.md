# 내보내기 위임 기록 (#62)

기록 대상: T11 내보내기. 실행일 2026-11-09. 프롬프트는 §6에 그대로 둔다. 선행 작업 T2(#50, PR #76), T6(#54, PR #80), E7(#45, PR #92)이 main에 있는 것을 확인했다. E8(#46, PR #94)은 작업 중에 main에 머지되어(§2 6회차) 병합한 뒤 탐색 화면에 연결했다.

SPEC 추적표의 AC48~AC50 골든 id는 `golden_cases.yaml`에 없다. 프롬프트대로 일반 테스트로 하고 테스트 이름에 id를 적었다. 케이스는 더하지 않았다.

## §1 수용 기준별 처리

| AC | 테스트 줄 항목 | 처리 | 근거 |
|---|---|---|---|
| AC48 | JSON·YAML을 파싱해 비교 → 같음 | 자동(실제 Postgres) | `export_json_yaml_same` |
| AC48 | 폼 정의가 각각 유효 | 자동 | `export_json_yaml_same` |
| AC48 | CSV 행 수 = 헤더 1 + 제출 응답 수 | 자동 | `export_csv_rows` |
| AC48 | 버전별 문항 열 구분 | 자동 | `export_form_versions_separate` |
| AC48 | checkbox 답 "기획; 디자인" | 자동 | `export_csv_rows` |
| AC48 | 건너뛴 문항 빈 칸 | 자동 | `export_csv_rows` |
| AC49 | `list`의 응답 집합·순서가 분석과 일치, 버전 3 제외 | 자동 | `export_filtered_matches_analyze` |
| AC49 | `count`·`summarize`는 버전·제출 시각·ID 순서 | 자동 | `export_non_list_time_order`, `summarize_내보내기는` |
| AC49 | 판정 불가 응답 제외 | 자동 | `export_excludes_not_evaluable` |
| AC49 | `clarify`이면 파일을 만들지 않음 | 자동 | `clarify이면_파일_없이` |
| AC49 | 탐색 화면에서 필터 결과 받기 | 자동 + 사람 확인 | `AI 결과 내보내기는 분석 응답의 context만 보내고 버전을 고르지 않는다`, §5 실제 화면 확인 7~9번 |
| AC49 | 탐색 화면의 되묻기 | 자동(내보내기 창), 사람 확인(도우미) | `AI 결과 내보내기가 되물으면 파일을 저장하지 않고 창에 질문을 보여 준다`, §5 실제 화면 확인 10번 |
| AC50 | `=HYPERLINK(...)` → `'=HYPERLINK(...)` | 자동 + 사람 확인 | `csv_formula_escaped`, §5 실제 화면 확인 4·5번 |

**수용 기준:** 자동 테스트 통과만으로 수용하지 않았다. 사람이 실행 화면을 두 눈으로 직접 확인하고 정상 작동함을 기준으로 수용했다(2026-11-09). 확인한 항목과 결과는 §5의 실제 화면 확인 표에 있다. 내보내기 창 안의 되묻기만 자동 테스트로 수용했다. 버튼이 분석에 성공한 결과에만 있어, 분석 뒤 폼이나 응답이 바뀌어야 나오는 경우라 화면에서 일부러 만들기 어렵다.

## §2 실패·수정 회차

| 회차 | 실패 | 원인 | 수정 |
|---|---|---|---|
| 1 | 프론트 테스트 3건: JSON 경로에서 저장·되묻기 표시가 되지 않음 | jsdom의 `Blob`에 `text()`가 없어 되묻기 판별이 예외로 빠졌다 | 응답을 바이트로 받아 `TextDecoder`로 판별하고, 파일은 그 바이트로 만든다 |
| 2 | 프론트 26건 실패(그중 23건은 내 변경과 무관) | 로컬 `frontend/.env.local`의 `VITE_USE_API=true`가 데모 모드 테스트를 깨뜨린다. CI에는 이 파일이 없다 | 구현은 고치지 않았다. `VITE_USE_API=false`로 실행 |
| 3 | 프론트 시간 초과 10건, 제한 시간을 늘린 뒤 Autofill 1건 | 병렬 실행 부하. Autofill은 단독 3회 모두 통과 | 구현은 고치지 않았다. 제한 시간을 늘리고 작업자 수를 줄여 실행 |
| 4 | 결함 주입 백엔드 16종이 0초에 끝남 | 스크립트가 Gradle을 실행하지 못했다. XML이 없어 전부 무효로 판정됐다 | `gradlew.bat` 절대 경로로 실행해 18종을 처음부터 다시 돌렸다 |
| 5 | 백엔드 테스트 2건: CSV 앞 3바이트와 0건 헤더 | BOM 상수를 이스케이프 표기로 바꾸는 치환 명령이 잘못 적용돼 상수가 `FEFF` 글자가 됐다 | 상수를 U+FEFF 이스케이프로 고치고 전체 빌드를 다시 돌렸다 |
| 6 | 커밋과 push 뒤 `main` 병합 충돌 2개(`ResultsPage.tsx`, `creatorRoutes.tsx`) | PR을 올리기 직전에 E8이 머지됐고, 결과 화면이 `ExplorePage` 안의 대시보드로 합쳐졌다 | 사람이 "병합 + 탐색 화면 연결"을 골랐다. E8 쪽을 기준으로 충돌을 풀고 내보내기 버튼을 `ExplorePage`로 옮겼다. 전체 테스트와 결함 주입을 다시 돌렸다 |

## §3 결함 주입 (22건)

각 주입은 파일을 바꾸고 결과 파일을 지운 뒤 좁혀 실행하고, 백엔드는 `build/test-results` XML, 프론트는 Vitest JUnit XML에서 기대한 테스트의 실제 실행과 실패를 확인했다. E8 병합과 탐색 화면 연결 뒤 22건 전부를 다시 돌린 결과다. 백엔드는 25건, 프론트는 20건이 실행됐다. 항목마다 되돌렸고 끝난 뒤 파일 해시가 주입 전과 일치했다. 커밋하지 않았다.

| # | 주입 | 실패한 기대 테스트 |
|---|---|---|
| 1 | 상태·삭제 조건 없이 응답을 읽는다 | `AC20_작성_중_포기_삭제된_응답은_내보내지_않는다` |
| 2 | 소유권 검사를 뺀다 | `AC2_제작자가_아니면_403이고_응답_내용이_없다` |
| 3 | 판정 불가 응답을 내보낸다 | `export_excludes_not_evaluable` |
| 4 | `list`를 분석 순서 대신 버전 순서로 낸다 | `export_filtered_matches_analyze` |
| 5 | `list`가 아닌 내보내기에 제출 시각 내림차순을 쓴다 | `export_non_list_time_order` |
| 6 | `form_versions`와 `context`를 함께 허용한다 | `form_versions와_context를_함께_보내면_400_invalid_context다` |
| 7 | `clarify`인데 빈 파일을 낸다 | `clarify이면_파일_없이_같은_clarify를_돌려준다` |
| 8 | `summarize` 내보내기가 요약을 부른다 | `summarize_내보내기는_요약_경로를_부르지_않고_시각_순서다` |
| 9 | YAML에서 숫자처럼 보이는 답을 숫자로 낸다 | `값처럼_보이는_문자열_답과_시각은_YAML에서도_문자열이다` |
| 10 | 폼 정의를 제출 버전 대신 최신 버전에서 넣는다 | `export_json_yaml_same` |
| 11 | CSV 헤더에 최신 문구를 쓴다 | `export_form_versions_separate` |
| 12 | CSV 수식 방지를 뺀다 | `csv_formula_escaped` |
| 13 | 수식 방지를 답 셀에만 하고 문항 문구는 뺀다 | `수식으로_시작하는_문항_문구도_헤더에서_막는다` |
| 14 | 쉼표·줄바꿈이 있는 셀을 따옴표로 감싸지 않는다 | `쉼표_따옴표_줄바꿈이_있는_답은_따옴표로_감싸_왕복한다` |
| 15 | checkbox 구분자를 바꾼다 | `export_csv_rows` |
| 16 | 수식 방지를 JSON에도 적용한다 | `수식_방지는_JSON과_YAML의_답을_바꾸지_않는다` |
| 17 | 화면에서 `clarify` 응답을 파일로 저장한다 | `되묻기면 파일을 저장하지 않고 질문을 보여 주며 창을 유지한다` |
| 18 | 화면에서 판정 불가 안내를 뺀다 | `일부만 확인했으면 항상 알리고 이유는 물음표를 눌러야 보인다` |
| 19 | 필터 내보내기에 `context`를 보내지 않는다 | `AI 결과 내보내기는 분석 응답의 context만 보내고 버전을 고르지 않는다` |
| 20 | 필터 내보내기 창에 `coverage`를 넘기지 않는다 | `AI 결과 내보내기 창은 분석의 coverage로 일부만 확인했음을 알린다` |
| 21 | 머리글 내보내기에 버전을 넘기지 않는다 | `머리글 내보내기는 제출 버전이 체크된 창을 열고 전체 응답을 받는다` |
| 22 | AI 결과 내보내기가 전체 내보내기를 연다 | `AI 결과 내보내기는 분석 응답의 context만 보내고 버전을 고르지 않는다` |

## §4 설계 결정: 판정 불가 범위는 파일이 아니라 화면에서만 안내한다

사람이 정한 것(2026-11-09). 파일에는 판정 불가 범위를 넣지 않고, 화면이 분석 응답의 `coverage`로 "전체 응답 14건 중 11건만 확인했어요"처럼 안내한다.

이유:

1. **파일 형식이 SPEC으로 고정돼 있다.** JSON·YAML의 키는 `{form_versions, responses}`이고 CSV의 열은 고정 4열과 버전별 문항 열이다(SPEC 241~242행). `coverage` 키나 안내 행을 더하면 이 형식을 받아 쓰는 쪽이 깨진다.
2. **CSV에는 넣을 자리가 없다.** AC48은 행 수를 "헤더 1 + 응답 수"로 정한다. 안내 행은 이 규칙을 어기고, 스프레드시트에서 응답 행으로 섞여 읽힌다.
3. **범위는 파일의 내용이 아니라 요청의 결과다.** AC49대로 파일에는 일치한 응답만 들어가므로, 판정 불가 응답은 애초에 파일에 없다. 전체·버전 범위 내보내기에는 판정 자체가 없어서, 넣으면 필터 내보내기에서만 파일 모양이 달라진다.
4. **화면에 이미 값이 있다.** 같은 `context`의 분석 응답에 `coverage`가 있으므로(AGENTS 3절 3항) 내보내기가 다시 계산하거나 다른 경로를 만들 필요가 없다. 안내 문구 규칙(AGENTS 91~92행)도 화면 규칙이다.

SPEC 244행의 "결과와 내보내기에 표시할 판정 불가 범위"는 내보내기 **화면**에 표시하는 것으로 읽었다.

감수하는 점: 파일만 따로 전달받은 사람은 일부만 확인된 결과라는 것을 파일에서 알 수 없다. 파일 안에서도 알려야 한다면 SPEC 241~242행의 형식을 먼저 바꿔야 한다.

코드 위치: `domain/export/ExportWriter.java`(파일에 넣지 않음), `frontend/src/pages/ExportDialog.tsx`의 `CoverageNotice`(화면 안내), `export_excludes_not_evaluable`(파일에 범위 키가 없음을 검사).

## §5 사람이 정한 답과 화면 확인

SPEC에 없어 객관식으로 물은 것(2026-11-09):

| 항목 | 답 |
|---|---|
| CSV 인코딩과 줄바꿈 | UTF-8 BOM + CRLF |
| 지원하지 않는 `format`, 빈 `form_versions`, 없는 버전 번호 | 모두 `400 invalid_request` |
| `form_versions` 출력에 넣을 버전 | 내보낸 응답이 있는 버전만 |
| 내보낼 응답 0건 | 200과 빈 파일(JSON·YAML은 빈 배열 두 개, CSV는 고정 4열 헤더만) |
| 버전 범위를 고르는 화면 | 내보내기 창의 버전 체크박스, 기본은 전체 선택 |
| 파일 이름 | `passform-export-날짜.확장자`, 날짜는 Asia/Seoul. `SecurityConfig`가 `Content-Disposition`을 노출하지 않아 화면에서 정한다 |

프롬프트에서 해석한 것: 헤더 칸은 `<버전>.<문항 ID>`로 시작해 칸 단위 검사로는 수식 방지가 걸리지 않는다. 프롬프트가 `=`·`+`·`-`·`@`로 시작하는 문항 문구의 방지를 요구하므로 문구 앞에 `'`를 붙였다(`1.q1 '=SUM(1)`).

새 의존성은 없다. YAML은 이미 컴파일 경로에 있는 `org.yaml:snakeyaml`을 쓰고, CSV는 직접 쓴다.

실제 화면 확인(2026-11-09, 사람이 개발 서버의 실행 화면에서 직접 확인. 시험 폼은 제출 응답 14건, 작성 중·포기 응답 각 1건):

| # | 항목 | 기대 결과 | 결과 |
|---|---|---|---|
| 1 | 결과 화면의 내보내기에서 버전 3개를 모두 골라 CSV·JSON·YAML 받기 | `passform-export-날짜.확장자`로 저장되고 응답 14건, 작성 중·포기 응답 제외 | 정상 |
| 2 | 폼 버전 2만 끄고 받기 | 폼 버전 1·3의 응답 9건만 들어 있음 | 정상 |
| 3 | 버전 체크를 모두 끄기 | 파일 받기 버튼이 비활성화됨 | 정상 |
| 4 | 받은 CSV를 스프레드시트로 열기 | 한글이 깨지지 않음 | 정상 |
| 5 | CSV의 `=HYPERLINK(...)` 응답 확인 | 글자로 보이고 링크로 실행되지 않음 | 정상 |
| 6 | E8 병합 뒤 대시보드 머리글의 내보내기 | 버전 체크박스 3개가 있고 응답 14건 | 정상 |
| 7 | AI 질문으로 목록을 받고 **이 결과 내보내기** 열기 | 버전 체크박스가 없음 | 정상 |
| 8 | 같은 창의 안내 | 일부만 확인했다는 문구가 보이고 `?`를 누르면 이유가 나옴 | 정상 |
| 9 | CSV·JSON·YAML 받기 | 화면 목록과 응답 수·순서가 같음 | 정상 |
| 10 | 모호한 질문 | 도우미가 되묻고 파일이 만들어지지 않음 | 정상 |

실제 화면을 본 뒤 사람이 요청한 수정(SPEC·AGENTS·`analyze_api.md`에 어긋나지 않음을 확인하고 반영, 사람이 화면에서 다시 확인):

| 요청 | 처리 |
|---|---|
| 내보내기 창 안내의 `?`가 줄바꿈되니 문구 왼쪽에 둔다 | `?`를 문구 왼쪽에 고정했다. 문구는 항상 보이고 이유는 눌러야 보이는 동작은 그대로다 |
| 탐색 도우미가 길다. "전체 - 전체 응답 목록" 대신 버튼만 두거나 더보기에 넣는다 | "전체" 묶음 제목을 없앴다. 더보기에 넣으면 E8의 기존 테스트(`ExplorePage.test.tsx`의 버튼 표시 검사)를 고쳐야 하고, 선택형 문항이 없는 폼에서는 더보기 버튼이 없어 목록을 부를 수 없다 |

특정 의견 요약 결과의 내보내기에는 그 주제를 언급한 응답만이 아니라 조건에 맞는 응답 전체가 들어간다. SPEC의 "`context`의 필터 결과"대로다.

## §6 발행한 프롬프트

```text
[구현 내용]
T11(#62): `POST /forms/{form_id}/export`로 전체 응답과 AI 필터 결과를 JSON·YAML·CSV로 내보내고, 행 순서 규칙, CSV 수식 방지, 판정 불가 범위 안내, 화면 연결을 만든다. 저장소 C:\Users\namhu\claude-practice\Passform, 브랜치 feat/export(최신 origin/main에서 분기), 셸 Windows PowerShell.

[목표]
제작자가 실제 화면에서 세 형식의 파일을 받는다. 필터 내보내기는 같은 `context`로 분석했을 때 일치한 응답만 담고, CSV 셀은 수식으로 실행되지 않는다. AC48, AC49, AC50을 테스트로 보인다.

[절대 규칙]
- AGENTS.md 3~5절을 따른다.
- 시작 전에 T2(#50), T6(#54), E7(#45)이 main에 있는지 확인한다. 없으면 구현하지 말고 알린다.
- 응답은 `ResponseRepository.findSubmittedNotDeletedByFormId`로만 읽는다. 다른 읽기 경로를 만들지 않는다.
- 필터는 `AnalysisService`의 되묻기·매칭·조건 평가·정렬을 그대로 쓴다. 평가를 다시 구현하지 않는다. 호출 구조를 나눌 수는 있지만 `analyze`의 결과는 바뀌면 안 된다.
- `context` 내보내기는 `operation`이 `summarize`여도 주제 분류, 임베딩, 요약문 작성을 부르지 않는다.
- LLM을 부르는 동안 DB 연결을 잡지 않는다. 내보내기 전체를 트랜잭션으로 감싸지 않는다.
- 파일 형식에는 SPEC 241~242행에 없는 키와 열을 더하지 않는다. 판정 불가 범위는 분석 응답의 `coverage`로 화면에서 안내한다.
- 수식 방지는 CSV에만 한다. JSON·YAML의 답은 원문 그대로다.
- 응답 내용을 로그에 남기지 않는다. 인증 우회(X-Demo-User 등)와 새 permitAll을 넣지 않고, 현재 사용자는 CurrentUserService로 얻는다. 테스트는 spring-security-test 모의 인증을 쓰고 LLM은 모의 객체로 둔다.
- 다음은 바꾸지 않는다: ResponseRepository, SecurityConfig, DB 스키마와 Flyway, CI, 스키마 파일, 프롬프트, SPEC, ARCHITECTURE, AGENTS, data/seed, tests/harness. 바꿔야만 끝나면 멈추고 묻는다.
- 새 의존성은 이유를 적어 승인받은 뒤에만 추가한다(예: YAML·CSV 라이브러리). 이미 런타임에 있는 라이브러리를 먼저 확인한다.
- SPEC에 없는 동작은 추측하지 말고, 맥락과 선택지를 적어 객관식으로 묻는다. 예: CSV의 BOM과 줄바꿈, `form_versions`가 빈 배열이거나 없는 버전일 때, 응답이 없는 버전을 포함할지, 응답 0건, 잘못된 `format`의 오류 코드, 파일 이름, 버전 범위를 고르는 화면.
- 같은 실패가 3회차를 넘거나 작업이 2시간을 넘으면 멈추고 보고한다. 작업 트리는 그대로 둔다.

[참조]
- 이슈 #62
- AGENTS.md 3~6절, CSV 76행, 내보내기 행 순서 88행, 화면 규칙 91~94행, 세션 규칙과 공통 파일 97~105행
- docs/SPEC.md
  - 범위: 55행, 동의와의 관계 213행
  - API와 형식: 230~244행
  - 수정 뒤 반영 223행, 전체 삭제 224행
  - 수용 기준과 테스트 줄: AC2(374행), AC20(504행), AC75(506행), AC48~AC50(555~560행), AC74(487~488행)
  - 추적표: 627·628·652행
- docs/ARCHITECTURE.md 3절, 10절(138행)
- docs/api/analyze_api.md, docs/api/analyze_examples.json
- src/passform/schemas/form.schema.json, src/response_analysis/schemas/request_context.schema.json
- data/seed/forms/form_b_mixed.json, data/seed/README.md(rb12, rb15, rb16, 배열 순서 85행)
- src/main/java/com/opinio/passform/의 service/analysis/AnalysisService.java·RequestContextSchema.java, controller/AnalysisController.java·ResultController.java, service/ResultService.java·FormSchemaValidator.java·CurrentUserService.java, repository/, domain/form/, domain/response/, domain/error/, controller/advice/
- src/test/java/com/opinio/passform/의 controller/ResultApiTest.java, service/analysis/AnalysisDatabaseTest.java·AnalysisServiceTest.java, support/
- frontend/src/의 pages/ResultsPage.tsx·ExplorePage.tsx, routes/creatorRoutes.tsx, api/client.ts, types/analyze.ts·results.ts, App.routes.test.tsx(데모 모드의 내보내기)

[완료 기준]
- 이슈 #62 완료 조건과 AGENTS.md 6절을 충족한다.
- 테스트 근거:
  - AC48, AC49, AC50 테스트 줄의 각 항목. `form_b_mixed.json`으로 버전 3개와 판정 불가를 확인한다.
  - JSON·YAML을 파싱한 객체가 같다. 숫자·날짜·`true`·`null`처럼 보이는 문자열 답과 시각도 문자열로 같다.
  - 각 버전의 `form`이 `form.schema.json`을 통과하고 그 응답의 제출 버전 정의다.
  - CSV: 쉼표, 따옴표, 줄바꿈이 있는 답의 왕복, 헤더의 제출 당시 문구, `=`·`+`·`-`·`@`로 시작하는 답과 문항 문구, checkbox 잇기, 수정되지 않은 응답의 빈 `updated_at`
  - `form_versions` 범위, `form_versions`와 `context`를 함께 보낸 `400 invalid_context`, 스키마에 맞지 않는 `context`의 400, `clarify`일 때 파일 없이 같은 `clarify`
  - `summarize` `context` 내보내기에서 분류·임베딩·요약 클라이언트 호출 0회, LLM 실패 때 `503 llm_unavailable`
  - AC20: `in_progress`·`abandoned`·제작자가 지운 응답 제외, AC74: 수정된 답 반영
  - 비로그인 401, 제작자가 아니면 403이고 응답 내용 없음, 없는 폼 404
  - 추적표의 골든 id가 golden_cases.yaml에 있으면 실행기로 돌린다. 없으면 일반 테스트로 하고 테스트 이름이나 보고에 id를 적는다. YAML에 케이스를 더하지 않는다.
  - 화면: 세 형식 받기, JSON 파일과 `clarify` 응답 구분, `clarify`면 파일을 저장하지 않고 되묻기 표시, 실패 안내, 판정 불가가 있으면 일부만 확인했다는 안내(AGENTS.md 91~92행)
  - 기존 데모 모드의 내보내기 테스트가 그대로 통과한다.
- AI 필터 결과의 내보내기 화면은 탐색 화면(E8 #46)에 잇는다. E8이 main에 있으면 연결한다. 없으면 API와 결과 화면의 전체 내보내기까지 하고 필터 내보내기 화면은 "미확인 — E8 대기"로 남긴다.
- 실제 화면 확인을 사람에게 요청하고 결과를 기록한다.
  - 결과 화면에서 전체 응답과 버전 범위를 세 형식으로 받기
  - 받은 CSV를 스프레드시트로 열어 한글이 깨지지 않고 수식이 실행되지 않는지 확인
  - 탐색 화면에서 필터 결과 받기와 되묻기(위 조건일 때)
  - 시험 데이터용 스크립트는 에이전트가 준비하되, 승인 없이 개발 DB에 실행하지 않는다.
- 결함 주입: 아래를 하나씩 넣었을 때 테스트가 실패해야 한다. 기대한 테스트가 실제로 실행됐는지 build/test-results XML(프런트는 Vitest 결과)로 확인하고, 실행되지 않았으면 판정 무효로 다시 돌린다. 주입은 커밋하지 않고, 항목마다 되돌린다.
  - 상태·삭제 조건 없이 응답을 읽는다
  - 소유권 검사를 뺀다
  - 판정 불가 응답을 내보낸다
  - `list`를 분석 순서 대신 버전 순서로 낸다
  - `list`가 아닌 내보내기에 제출 시각 내림차순을 쓴다
  - `form_versions`와 `context`를 함께 허용한다
  - `clarify`인데 빈 파일을 낸다
  - `summarize` 내보내기가 요약을 부른다
  - YAML에서 숫자처럼 보이는 답을 숫자로 낸다
  - 폼 정의를 제출 버전 대신 최신 버전에서 넣는다
  - CSV 헤더에 최신 문구를 쓴다
  - CSV 수식 방지를 뺀다
  - 수식 방지를 답 셀에만 하고 문항 문구는 뺀다
  - 쉼표·줄바꿈이 있는 셀을 따옴표로 감싸지 않는다
  - checkbox 구분자를 바꾼다
  - 수식 방지를 JSON에도 적용한다
  - 화면에서 `clarify` 응답을 파일로 저장한다
  - 화면에서 판정 불가 안내를 뺀다
- 위임 기록을 docs/prompts/delegation_export.md에 남긴다. 넣을 것: 이 프롬프트 원본, 회차 표, 결함 주입 결과, 사람이 정한 답.

[범위 밖]
- 폼 삭제 전 내보내기 연결(AC67, T12)
- 첨부 파일 원본의 내보내기
- 요약 결과의 내보내기
- 탐색 화면 자체(E8). 이번 작업은 내보내기 연결만 더한다.
- 내보내기 작업 큐와 비동기 처리

[깃허브 규칙]
- 사람의 명시적 승인을 받은 뒤에만 커밋·push·PR을 한다. 승인 요청에는 `git status --short`, `git diff --stat`, 핵심 diff, 테스트 집계, 제안 커밋 메시지를 보여 준다. 커밋 승인은 push 승인이 아니다.
- 커밋은 AGENTS.md 5절 협업 항목과 README.md를 따른다. 공통 파일(types, client, 공통 컴포넌트) 변경은 별도 커밋으로 나눈다.
- PR 제목: `[feat] 내보내기 (#62)`. 본문은 README.md를 따른다. "미확인 — E8 대기"가 남으면 `Closes #62` 대신 `#62`로 적는다.
- 리뷰어는 gh pr create --reviewer ye-nni로 지정한다.
- main에 직접 push하거나 머지하지 않는다.

[보고]
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 테이블 표 / 추가한 의존성과 이유 / 지시와 다르게 한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
- 테스트마다 통과 이유를 코드 위치와 함께 한 줄로 적는다.
- AC48~AC50의 테스트 줄 항목마다 자동 테스트, 사람 확인, 미확인 중 무엇으로 처리했는지 적는다.

```
