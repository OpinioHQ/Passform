# Google 로그인 위임 기록 (#50)

## §1 결함 주입

승인 B 커밋(`870fe23`) 뒤에 실행했다. 하나씩 임시로 넣고 `.\gradlew.bat --no-daemon --console=plain cleanTest test --continue`를 실행한 뒤 되돌렸으며, 항목마다 `git diff HEAD`가 빈 것을 확인했다. 주입은 커밋하지 않았다. 실행 전에 `build/test-results`를 지웠고, 항목마다 XML의 테스트 117건을 읽어 판정했다. 스크립트는 세션 쿠키 항목 하나로 먼저 시험한 뒤 나머지 5건을 실행했다.

| 주입 | 기대 실패 | 실제 결과 | 근거(XML의 테스트 이름과 결과) |
|---|---|---|---|
| 모든 요청 permitAll | `unauthenticated_401` | 잡음 | `AccessGoldenTest` `unauthenticated_401` 실패: `expect_status` 기대 401, 실제 404 |
| 인증 진입점을 로그인 302로 변경 | `unauthenticated_401` | 잡음 | `AccessGoldenTest` `unauthenticated_401` 실패: `expect_status` 기대 401, 실제 302 |
| 사용자 조회 제거(로그인마다 새 User) | AC1 테스트 | 잡음 | `GoogleLoginDatabaseTest` `AC1_모의_OAuth로_2회_로그인하면_User_1개이고_GET_me가_200이다` 실패: 사용자 수 기대 10, 실제 11 |
| CSRF 비활성화 | CSRF 403 테스트 | 잡음 | `SecurityConfigTest` `CSRF_토큰이_없거나_다르면_403이다` 실패: 기대 403, 실제 201 |
| CORS 출처 와일드카드 | CORS 테스트 | 잡음 | `SecurityConfigTest` `다른_출처는_허용하지_않는다` 실패: 기대 403, 실제 200 |
| 세션 쿠키 Secure 기본값 끄기 | 쿠키 테스트 | 잡음 | `SecurityDefaultsTest` `세션_쿠키의_기본값은_Secure와_SameSite_Lax다` 실패: true 기대, 실제 false |

- 사용자 조회 제거는 조회만 지우면 유일 제약 복구 코드가 기존 User를 찾아 결함이 되지 않는다. 그래서 고유 ID에 임의 값을 붙여 로그인마다 새 User가 생기게 했다.
- 타이밍에 좌우되는 항목은 없어 항목마다 1번 실행했다.

## §2 위임 프롬프트 원본

작성: 사람. 2026-11-06 실행.

```text
Google 로그인, GET /me, PATCH /me/settings, 로그인 화면, 쿠키·CORS·CSRF 설정을 구현해 T2(#50)의 필수 골든 unauthenticated_401을 통과시키고 CSRF를 켠 채 비로그인 제출이 동작하게 해줘. 저장소 C:\Users\namhu\claude-practice\Passform, 새 브랜치 feat/google-login(최신 origin/main에서 분기), 셸 Windows PowerShell. 골든 기준 커밋 d82bec1. 시작 시 origin/main 커밋을 BASE로, 시작 시각을 Get-Date로 기록한다. 커밋·push·PR은 [6]의 승인을 받은 뒤에만 한다.

[0. 시작 전 확인 — 하나라도 어긋나면 구현하지 말고 멈추고 보고]
- git fetch origin 후 git status로 추적 파일 변경이 없는지 확인한다. 변경이 있으면 건드리지 않고 멈춘다. 없으면 `git switch -c feat/google-login origin/main`으로 시작한다.
- 선행 작업이 main에 있다: E1(frontend/, frontend/src/api/client.ts의 csrfHeaders), T1(V1__create_schema.sql, User, UserRepository, ApiExceptionHandler, DatabaseTest).
- origin/main의 golden_cases.yaml 최상위 키 access_cases에 unauthenticated_401이 있다.
- `git diff d82bec1 origin/main -- tests/harness/golden_cases.yaml data/seed/forms/form_required_branch.json`을 본다. access_cases나 이 fixture가 달라졌으면 멈춘다. 다른 최상위 키만 달라졌으면 그 사실을 기록하고 진행한다.
- T3(#51, PR #75)가 main에 머지됐는지 기록한다. [3]의 비로그인 제출 확인 방법이 이 결과로 갈린다.
- tests/harness/golden_cases.yaml 끝에 빈 줄을 추가하는 Edit를 시도한다. 거부되면 통과다. 거부되지 않고 파일이 바뀌면 `git checkout -- tests/harness/golden_cases.yaml`로 되돌리고, git diff가 빈 것을 확인한 뒤 멈추고 보고한다.

[1. 목표와 읽을 것]
- 목표: Google 계정으로 로그인해 세션을 받고, 로그인이 필요한 API는 비로그인 요청에 302가 아니라 401을 돌려준다. CSRF를 끄지 않고 비로그인 응답 제출 경로가 동작한다.
- 행 번호는 main@d82bec1 기준이다. 어긋나면 내용으로 찾는다.
- AGENTS.md 전체. 특히 3절(52행~), 4절(60행~), 5절 세션 규칙(97행~), 6절(106행~), 7절 환경변수(120행).
- docs/SPEC.md: 소셜 로그인 33행, 인증 구분 90행, 계정 API 98·99·101행, 공개 제출 188행, 로그인 제출 기록 225행, AC1(370행), AC2(374행). AC 바로 아래 "테스트" 줄이 일반 테스트의 기대값 출처다.
- docs/ARCHITECTURE.md: users 118행, 유일 제약 137행, 배포·쿠키·CORS·CSRF·Google 155~158행, G2 실행 수준 180행, 골든 형식 187행~(CSRF 토큰 194행, access_cases 199행).
- tests/harness/golden_cases.yaml의 "필수 골든" 머리 주석과 access_cases(353행~).
- 코드: V1__create_schema.sql의 users(5~14행), domain/user/User.java, config/SecurityConfig.java, controller/advice/ApiExceptionHandler.java, 기존 HealthControllerTest·ApiExceptionHandlerTest.
- 화면: frontend/src/api/client.ts, components/Header.tsx, App.tsx, routes/paths.ts, App.routes.test.tsx, api/client.test.ts, frontend/docs/ROUTES_AND_API.md(8·24·34·35행), frontend/.env.example.
- 기존 하니스 ConditionEvaluatorGoldenTest.java(읽기만): 새 실행기의 로더 규칙을 맞추는 본보기다.
- PR #69 본문 "다른 이슈가 알아야 할 것", PR #71 본문의 공통 파일 규칙.
- PR #75(T3, Draft)의 SecurityConfig 변경과 제출 때 user_id를 얻는 코드: 읽기만 한다. 현재 사용자 조회 방법이 T3와 맞물리게 하기 위해서다.
- 참고만: origin/feat/demo의 demo/config/DemoSecurityConfig.java는 CSRF를 끄고 X-Demo-User를 쓰는 시연 전용이다. 따라 하지 않는다.

[2. 절대 규칙(AGENTS.md·ARCHITECTURE)]
- CSRF를 전역이나 경로 단위로 끄지 않는다. 세션 쿠키 값을 URL이나 응답 본문으로 노출하지 않는다.
- 비로그인 요청은 로그인이 필요한 API에서 401이다. 로그인 페이지로 302 하지 않는다. oauth2Login을 켠 뒤에도 HttpStatusEntryPoint를 유지한다.
- 세션 쿠키는 Secure, SameSite=Lax가 기본값이다. 로컬 HTTP 개발에서만 환경변수로 Secure를 끌 수 있다.
- CORS는 설정한 프런트 출처 하나만 허용하고 자격 증명을 허용한다. 와일드카드 금지.
- Google 권한은 openid, email, profile만 쓴다.
- Google 클라이언트 ID와 비밀값은 OS 환경변수나 .env로만 받는다. 저장소, 이슈, PR, 로그, VITE_ 변수에 넣지 않는다. .env.example에는 빈 변수 이름만 적는다. .env의 값은 출력하지 않는다.
- 이메일·이름·토큰을 로그에 남기지 않는다.
- X-Demo-User 같은 인증 우회를 넣지 않는다.
- 현재 시각은 주입한 Clock으로 얻고, 시각은 UTC로 저장한다.
- 계층은 controller → service → repository/domain이다. 엔티티를 API에 직접 쓰지 않는다. HTTP 상태 매핑은 advice에서만 한다.
- 프런트는 TypeScript strict, any 금지다. API 호출은 client.ts에만, CSRF 헤더는 csrfHeaders()에만, 경로는 paths.ts에만 둔다.

[3. 만드는 것 / 바꿔도 되는 것 / 안 되는 것]
만드는 것:
- 로그인과 User 생성(AC1): (oauth_provider, oauth_subject)로 찾고, 없으면 만든다. 재로그인은 새 User를 만들지 않는다. deadline_reminder 기본값은 true다. 동시 첫 로그인으로 유일 제약 위반이 나도 500을 내지 않고 기존 User로 로그인한다. 로그인 성공 뒤 설정한 프런트 출처로 보낸다. 요청 파라미터로 받은 주소로는 보내지 않는다.
- GET /me: {user_id, name, email, deadline_reminder}. PATCH /me/settings: {deadline_reminder?: boolean}를 받아 본문 없는 200을 돌려준다. 키를 생략하면 값을 유지한다. boolean이 아니면 400 invalid_request다.
- 현재 사용자 조회 방법 하나를 만들어 이후 이슈(T3, E5 등)가 쓰게 한다. 비로그인이면 없음으로 처리할 수 있어야 한다. PR 본문에 사용법을 적는다.
- CSRF: XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더 방식을 기본으로 한다. client.ts의 csrfHeaders()를 채운다.
  - 비로그인 브라우저도 첫 변경 요청 전에 토큰을 받을 수 있어야 한다.
  - app.<도메인>과 api.<도메인>이 나뉜 배포에서도 프런트가 토큰을 읽을 수 있게 쿠키 도메인을 환경변수로 둔다.
  - 이를 위해 SPEC에 없는 새 공개 API가 필요하면 멈추고 객관식으로 묻는다.
- CORS와 세션 쿠키 설정. 출처와 쿠키 값은 환경변수로 받고, 로컬 기본 출처는 http://localhost:5173이다.
- 화면:
  - /login 자리표시자를 실제 로그인 화면으로 바꾼다. 제목 "로그인"을 유지하고, 전체 페이지 이동으로 /oauth2/authorization/google을 연다.
  - Header는 VITE_USE_API=true일 때 GET /me 결과로 로그인 상태를 표시한다. 401이면 기존 로그인 링크를 보인다.
  - 데모 모드 화면은 그대로 둔다.
- 비로그인 제출 확인:
  - T3가 main에 있으면: CSRF 토큰을 넣은 비로그인 POST /forms/{form_id}/responses가 201이고, 토큰이 없으면 403인지 실제 경로로 시험한다. 브라우저로도 확인한다.
  - T3가 main에 없으면: SecurityConfig에 POST /forms/*/responses permitAll 한 줄을 더하고(T3와 같은 줄이라 나중 PR이 충돌을 푼다), 테스트 전용 컨트롤러로 같은 내용을 시험한다. 운영 코드에 가짜 제출 API를 넣지 않는다. 실제 제출 확인은 "미확인 — T3 대기"로 보고하고 완료로 세지 않는다.

골든 실행기 tests/java/com/opinio/passform/golden/AccessGoldenTest.java (구현보다 먼저 만든다):
- access_cases만 읽어 케이스마다 동적 테스트 하나를 만든다. 테스트 이름은 케이스 name이고, 케이스를 코드에 복사하지 않는다.
- MockMvc에 실제 SecurityConfig와 service, 모의 저장소를 붙여 실행한다(G2). E5·E6도 쓰도록 actor 4종(anonymous, owner, respondent, other_user)을 처리한다. POST 등 변경 요청에는 유효한 CSRF 토큰을 넣는다.
- 입력 키: fixture, actor, method, path, body. 판정 키: expect_status, expect_error, must_not_contain. 케이스에 있는 키만 검사한다.
- 로더 검사(항상 실행): YAML 중복 키, 비어 있는 목록, 이름 없음·중복, 판정 키 없음, 허용 키 밖의 키, fixture 파일 없음을 실패로 처리한다. 판단할 수 없는 입력을 조용히 통과시키지 않는다.
- skip은 메시지가 "not implemented"인 UnsupportedOperationException일 때만 한다. 다른 예외는 실패다.
- unauthenticated_401의 경로 GET /me/forms는 E5 범위다. 이 API를 T2에서 만들지 않는다. 보안 필터의 401로 통과해야 한다.

새 일반 테스트:
- 모의 OAuth로 2회 로그인 → User 1개, GET /me 200(AC1 테스트 줄, 실제 Postgres). 다른 Google 계정이면 User 2개.
- GET /me 비로그인 401, 응답 필드 정확히 4개. PATCH /me/settings 값 변경·생략·잘못된 값·비로그인 401·CSRF 토큰 없음 403.
- CSRF: 토큰 없는 변경 요청 403, 토큰 있는 요청 통과.
- CORS: 허용 출처의 사전 요청에 자격 증명 허용 헤더가 있고, 다른 출처는 허용되지 않는다. 401 응답에도 허용 출처의 CORS 헤더가 있다.
- 세션 쿠키 설정: 기본값에서 Secure, SameSite=Lax.
- 프런트: 로그인 화면 링크, 로그인·비로그인 Header, csrfHeaders가 쿠키를 헤더로 옮기는지(새 테스트로 추가).
- 기대값은 SPEC·ARCHITECTURE에서 가져온다. 없는 값을 기대값으로 만들지 않는다.

조심할 것:
- Google 키가 없어도 .\gradlew.bat build, CI, 앱 기동이 되어야 한다(CI 설정은 못 바꾼다). 로그인만 안 되는 상태는 괜찮다.
- HealthControllerTest, ApiExceptionHandlerTest는 @WebMvcTest에서 SecurityConfig를 Import한다. 이 테스트들이 수정 없이 통과해야 한다.

바꿔도 됨: 위 파일, config/, controller/, service/, domain/user/, repository/UserRepository, application.properties, src/test/resources/application-test.properties, .env.example, frontend/src의 login·Header·client·types 관련 파일, frontend/.env.example(공개 값만), frontend/docs/ROUTES_AND_API.md의 T2 상태, docs/prompts/delegation_google_login.md.
안 됨: tests/harness/**, data/seed/**, ConditionEvaluatorGoldenTest.java, ConditionEvaluator.java와 입출력 타입 8개, 스키마·프롬프트 파일, config/rag.yaml, evals/evalset.jsonl, Flyway 마이그레이션(추가·수정 모두), CI 설정, docs/SPEC.md, AGENTS.md, docs/ARCHITECTURE.md. 기존 테스트(src/test, tests/java, frontend/src/**/*.test.*)의 단언문을 바꾸지 않는다. @Disabled·skip·커버리지 제외를 쓰지 않는다. 셸 명령으로 deny를 우회하지 않는다. 기대값을 쓰거나 고치지 않는다.
범위 밖(구현하지 않음):
- 제출 API 본체와 응답 화면(T3, T4)
- GET /me/forms 등 내 폼함·제출 이력(E5), 소유권 403·404 판정(E5, E6)
- 프로필 API·화면(T9), GET /me/storage(T12)
- 헤더 마감 알림 스위치의 서버 저장(E12). PATCH /me/settings API까지만 만든다.
- 도메인·HTTPS·배포와 프록시 설정(E9)
- SPEC에 없는 로그아웃 API

[4. 진행 순서, 루프, 중단 조건]
진행 순서:
1. docker compose up -d db로 DB를 띄운다. 실행기와 필요한 스텁을 만든다. .\gradlew.bat cleanTest test로 로더 통과, 조건 평가 골든 18건 통과, 기존 테스트 유지를 확인한다. unauthenticated_401은 이미 통과할 수 있다(기존 SecurityConfig가 401을 돌려준다). 그 결과를 그대로 적는다. [6]의 승인 A를 받아 커밋한다. 이 커밋이 RUNNER다. 승인 전에는 구현을 시작하지 않는다. 이후 실행기는 원인이 하니스 오류일 때만 고치고, 판정을 약하게 만들지 않으며, 고친 내용을 표에 적는다.
2. 구현 회차마다 .\gradlew.bat cleanTest test를 실행하고 build/test-results XML로 집계한다. 조건 평가 골든, 접근 골든, 로더, 그 밖의 테스트를 나눠 센다. 화면을 바꾼 회차에는 frontend에서 npm test도 실행한다.
3. docs/prompts/delegation_google_login.md §3 표에 한 줄씩 적는다: 회차 / 접근 골든 통과·실패·skip / 실패한 테스트 / 원인 분류(구현 오류·컨텍스트 누락·하니스 오류·환경) / 고친 것 / 경과 시간. 경과 시간에서 사람을 기다린 시간은 뺀다.
4. 전체 최대 6회차로 한다.
5. 자동 테스트가 통과하면 사람에게 실제 로그인 확인을 요청한다. 사람이 할 일을 적어 준다:
   - Google OAuth 클라이언트에 콜백 http://localhost:8080/login/oauth2/code/google 등록
   - .env에 GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET(이름은 구현에서 정한 것) 입력
   - 백엔드와 프런트(VITE_USE_API=true)를 켜고 로그인 → Header의 이름 표시 → 새로고침 후 유지 → 같은 계정 재로그인 후 users 1행 확인
   - 등록하지 않은 Google 계정으로 로그인이 되는지 확인(ARCHITECTURE 158행)
   에이전트는 Google 비밀값을 보거나 로그인 정보를 입력하지 않는다. 결과를 사람에게 받아 기록한다.

아래는 미리 정한 기본값이다. 이대로 구현하고 "지시와 다르게 한 것" 앞에 해석으로 적는다. 골든과 충돌하면 멈춘다:
- 재로그인 시 Google의 이름·이메일이 달라져도 User 값을 갱신하지 않는다.
- Google 응답에 email이 없으면 로그인 실패로 처리하고 User를 만들지 않는다. name이 없으면 email을 name으로 저장한다.
- 세션은 있으나 User 행이 없으면 401이다.

# 스크립트와 결함 주입 실행 규칙
- 명령과 스크립트는 비대화형으로 실행한다. 입력을 기다릴 수 있는 명령을 쓰지 않고, 시간 제한을 둔다. Gradle은 .\gradlew.bat --no-daemon --console=plain으로 실행한다.
- 스크립트를 쓰기 전에 항목 하나로 먼저 시험한다.
- 실행 전에 이전 결과를 지우고, 실행 후 다음을 모두 확인해야 그 회차를 유효로 친다: 종료 코드, build/test-results XML이 새로 생겼는지, 기대한 테스트가 XML에 있는지.
- 결함 주입 결과는 "잡음", "못 잡음", "판정 무효" 중 하나로 적는다. 기대한 테스트가 실제로 실행됐는데 통과했을 때만 "못 잡음"이다. 실행되지 않았거나 컴파일 오류면 "판정 무효"이고, 고쳐서 다시 실행한다.
- "못 잡음"이 나오면 XML 원본으로 다시 확인하고, 그래도 같으면 멈추고 보고한다.
- 스크립트의 요약만 보고하지 않는다. 항목마다 근거로 XML의 테스트 이름과 결과를 함께 적는다.
- 타이밍에 좌우되는 항목은 정한 횟수만큼 반복한 뒤 판정한다. 횟수와 실패 횟수를 적는다.
- 스크립트의 실수와 다시 실행한 내역도 §3 표에 적는다(원인 분류: 환경 또는 하니스 오류).

# 중단 조건 (멈추고 보고한다)
- 같은 실패로 3회차를 넘길 때, 6회차를 다 쓰고도 끝나지 않을 때, 작업이 2시간을 넘길 때. 회차마다 경과 시간을 확인한다.
- 골든 기대값이 SPEC과 맞지 않아 보일 때. 기대값은 고치지 않는다.
- 케이스·fixture·SPEC·잠긴 타입·판정 기준을 바꿔야만 통과할 때. 실행기의 판정을 약하게 만들어야만 통과할 때.
- 새 fixture가 필요할 때(이슈 #50 주의 사항). 구현하지 말고 알린다.
- jsonb를 text로 바꾸거나, Flyway 마이그레이션을 고치거나 더하거나, CI를 바꿔야 할 때. 기존 테스트의 단언문을 바꿔야만 통과할 때.
- CSRF를 끄거나 Secure·SameSite 기본값을 바꿔야만 동작할 때.
- SPEC·ARCHITECTURE와 위 기본값에 없는 컬럼·제약·API·제품 동작을 판단해야 할 때. 추측하지 말고 맥락을 적은 객관식으로 묻는다.
- 시험 데모 코드를 재사용하고 싶을 때.
- 범위 밖 작업 없이는 끝낼 수 없을 때.
- Postgres를 띄울 수 없어 DB 테스트를 실행할 수 없을 때. skip으로 넘기지 않는다.
- 새 의존성이 필요할 때. build.gradle에 OAuth2 클라이언트와 시험 의존성이 이미 있으니 먼저 확인하고, 그래도 필요하면 이유와 함께 묻는다.
- 멈출 때 보고: 멈춘 조건, 시도한 것과 회차 표, 막힌 곳, 사람이 정할 것(객관식이면 선택지 2~4개와 각 영향, 추천안). 작업 트리는 그대로 두고, 승인 없이 커밋하거나 되돌리지 않는다. 답을 받기 전에 다음 단계로 넘어가지 않는다.

[5. 완료 기준]
- unauthenticated_401 실행·통과, skip 0, 로더 통과. 조건 평가 골든 18건 통과.
- AC1 테스트 통과, [3]의 일반 테스트 전부 통과.
- 실제 화면에서 로그인 동작: 사람의 확인 결과를 기록한다.
- CSRF를 켠 채 비로그인 제출 동작: T3가 main에 있으면 실제 경로로 확인한다. 없으면 "미확인 — T3 대기"로 남기고 이슈를 닫지 않는다.
- .\gradlew.bat cleanTest build 통과(커버리지 기준 그대로). frontend에서 npm ci, npm run lint, npm run typecheck, npm test, npm run build 통과.
- 결함 주입(승인 B의 커밋 뒤에 실행, 커밋하지 않음): 아래를 하나씩 임시로 넣고 [4]의 실행 규칙대로 판정한 뒤 되돌린다. 항목마다 `git diff HEAD`가 빈 것을 확인하고 §1 표에 "주입 / 기대 실패 / 실제 결과 / 근거"를 적는다. 전 항목이 "잡음"이어야 한다.
  - 모든 요청 permitAll → unauthenticated_401
  - 인증 진입점을 로그인 302로 변경 → unauthenticated_401
  - 사용자 조회 제거(로그인마다 새 User) → AC1 테스트
  - CSRF 비활성화 → CSRF 403 테스트
  - CORS 출처 와일드카드 → CORS 테스트
  - 세션 쿠키 Secure 기본값 끄기 → 쿠키 테스트
- 다음 diff가 모두 비어 있다: `git diff BASE -- tests/harness data/seed docs/SPEC.md docs/ARCHITECTURE.md AGENTS.md build.gradle .github src/passform src/response_analysis config evals src/main/resources/db/migration`, ConditionEvaluatorGoldenTest.java, ConditionEvaluator.java, 입출력 타입 8개. build.gradle을 승인받아 바꿨다면 그 줄만 있다.
- 기존 테스트 파일의 diff가 비어 있다. RUNNER 이후 실행기 diff가 있으면 §3 표의 근거와 맞다.
- `git grep`으로 Google 비밀값, X-Demo-User, CSRF 비활성화 코드가 없음을 확인한다.
- GitHub Actions 통과(`gh pr checks`).

[6. 승인, 커밋, PR, 보고]
승인 규칙:
- 사람의 명시적 승인 없이 git add, git commit, git push, PR 생성을 하지 않는다. 도구의 권한 창이나 자동 승인은 이 승인을 대신하지 않는다.
- 승인을 요청할 때 다음을 보여 주고 답을 기다린다: `git status --short`, `git diff --stat`, 파일별 변경 요약, 사람이 봐야 할 핵심 diff, 테스트 집계, 제안 커밋 메시지, 대상 경로. .env, _local/, docs/SPEC_followups.md가 없다는 것도 확인해 적는다.
- 승인은 보여 준 변경과 메시지에만 유효하다. 그 뒤 내용이 바뀌면 다시 받는다. 커밋 승인은 push 승인이 아니다.

승인 지점:
- 승인 A — 실행기 커밋: `test: 접근 골든 실행기 추가 (#50)`
- 승인 B — 구현 커밋(결함 주입 전, 실제 로그인 확인 후):
  - `feat: 쿠키와 CORS, CSRF 설정 (#50)` (공통 파일 변경만 분리)
  - `feat: Google 로그인과 계정 API 구현 (#50)`
  - `feat: 로그인 화면과 헤더 상태 연결 (#50)`
- 승인 C — 기록 커밋: `docs: 로그인 위임 루프 기록 (#50)`. 기록 파일은 §1 결함 주입, §2 이 프롬프트 원본, §3 회차 표로 구성하고, 수용 판단은 사람이 적도록 비워 둔다.
- 승인 D — push와 PR: 브랜치, 커밋 목록, PR 제목과 본문 전체를 보여 준다. CI가 실패해 코드를 고치면 다시 승인받는다. main에 직접 push하지 않고, 머지하지 않는다.

커밋 규칙: {tag}: 한글 메시지 (#50), 콜론 뒤에만 공백, 50자 이내, 파일명·디렉터리명 금지, 에이전트 이름이나 생성 문구 금지. git add는 경로를 명시한다.

PR 규칙:
- 제목: `[feat] Google 로그인과 권한 (#50)`
- 본문은 README.md의 PR 본문 형식을 따른다. 관련 이슈는 완료 기준을 모두 채웠으면 `Closes #50`, "미확인 — T3 대기"가 남으면 `#50`으로 적는다. 공통 파일 변경, 현재 사용자 조회 사용법, 새 환경변수를 적는다.
- 리뷰어는 gh pr create --reviewer ye-nni로 지정한다.

보고:
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 테이블 표 / 추가한 의존성과 이유 / 지시와 다르게 한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
- "완료 기준 항목별 결과"에 BASE·RUNNER 커밋, 경과 시간, 결함 주입 결과를 포함하고, unauthenticated_401을 왜 통과하는지 코드 위치와 함께 한 줄로 설명한다.
- "테이블 표"는 §3 회차 표다.
- "지시와 다르게 한 것"에 해석한 기본값과 알려진 한계를 함께 적는다.
```

## §3 회차 표

- 시작: 2026-11-06T00:10:45+09:00, BASE `d82bec1`, RUNNER `5df2c21`
- 집계: `.\gradlew.bat --no-daemon --console=plain cleanTest test`의 `build/test-results` XML. 경과 시간은 시작부터이며 사람을 기다린 시간은 뺐다.

| 회차 | 접근 골든 통과·실패·skip | 실패한 테스트 | 원인 분류 | 고친 것 | 경과 시간 |
|---|---|---|---|---|---|
| 1 | 1 · 0 · 0 (조건 평가 18, 로더 3, 그 밖 95 통과, 프런트 67 통과) | 없음. 단, `npm run typecheck`와 `npm run build`가 실패(`client.csrf.test.ts`의 `consent_agreed` 타입) | 구현 오류 | 첫 구현 | 약 25분 |
| 2 | 1 · 0 · 0 (서버 변경 없음, 프런트 67 통과, lint·typecheck·build 통과) | 없음 | 해당 없음 | 테스트 입력의 타입 수정 | 약 27분 |

### 실제 로그인 확인 (사람, 2026-11-06)

사람이 Google OAuth 클라이언트를 만들고 `.env`에 값을 넣어 직접 확인했다. 에이전트는 비밀값을 보거나 로그인 정보를 입력하지 않았다.

| # | 확인 | 결과 |
|---|---|---|
| 1 | 로그인 후 프런트 첫 화면으로 돌아옴 | 성공 |
| 2 | 헤더에 이름 표시 | 성공 |
| 3 | 새로고침 후 유지 | 성공 |
| 4 | 같은 계정 재로그인 후 `users` 수가 1만 늘어난 상태 | 성공 |
| 5 | 등록하지 않은 Google 계정으로 로그인(ARCHITECTURE 158행) | 로그인됨 |

### 진행 중 사람이 정한 것

- 헤더의 `GET /me` (2026-11-06, 선택지 A): 지시대로 헤더가 `GET /me`를 부르면 `App.routes.test.tsx`의 호출 목록 단언 4건(305, 312, 317, 323행)이 실패한다. 구현 전에 멈춰 물었고, 사람이 모든 화면에서 호출하도록 정하고 그 4줄과 `ROUTES_AND_API.md` 22행의 수정을 승인했다. PR에 함께 적기로 했다.

### 기타

- 시작 전 확인: `golden_cases.yaml` 끝에 빈 줄을 더하는 Edit는 권한 설정으로 거부됐다(통과). T3(PR #75)는 main에 없어 비로그인 제출은 "미확인 — T3 대기"다.
- RUNNER(`5df2c21`) 이후 실행기 diff는 없다.
- 스크립트 실수와 다시 실행한 내역은 없다.

## 수용 판단

(사람이 적는다)
