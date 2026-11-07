# 결과 통계와 결과 화면 위임 기록 (#54)

## §1 결함 주입

구현 뒤, 커밋 전에 실행했다. 항목마다 대상 파일을 바이트 그대로 백업하고 한 곳만 바꾼 뒤 `.\gradlew.bat test --tests *ResultStatisticsTest --tests *ResultApiTest --console=plain`를 실행했다. 끝나면 백업으로 되돌리고 파일 해시가 처음과 같은지 확인했다. 실행 전에 `build/test-results/test`를 지웠고, 기대한 테스트가 XML에 있고 skip이 아니며 실패했는지를 읽어 판정했다. 주입은 커밋하지 않았다. 마지막 항목 뒤 `git diff --stat -- src/main`은 의도한 `Form.java` 변경(9줄)만 보였다. rebase 뒤(§3 10회차)에 같은 스크립트로 7건을 다시 실행했다(아래 표 끝 줄).

| 주입 | 바꾼 곳 | 기대 실패 | 실제 결과 | 근거(XML의 실패한 테스트) |
|---|---|---|---|---|
| 1. 묶기 키에서 문구를 뺀다 | `ResultStatistics.GroupKey.of`의 문구를 `""`로 | 문구 변경 별도 문항, form_b | 잡음 | `ResultStatisticsTest` 2건(`AC19_버전_2의_문구가_달라지면_통합에서_별도_문항이다` 포함), `ResultApiTest` `form_b_결과는_통합과_버전별_통계를_돌려준다`(q2 "학년"·"현재 학년"이 합쳐짐) |
| 2. 묶기 키에서 유형을 뺀다 | 같은 곳의 유형을 `""`로 | 유형 변경 별도 문항 | 잡음 | `AC19_버전_2의_유형이_달라지면_통합에서_별도_문항이다`, `AC19_같은_문항의_같은_선택지만_합치고_보류는_버전_2만_출처로_붙는다`, `form_b_결과는_통합과_버전별_통계를_돌려준다`. 뒤의 두 건은 출력의 `question_type`도 묶기 키에서 나와 `""`가 되어 실패한 것이다 |
| 3. 선택지를 위치로 합산한다 | `GroupBuilder.add`가 문구 대신 앞 버전의 같은 위치 선택지에 더함 | 같은 위치 다른 문구, form_b | 잡음 | `같은_위치라도_문구가_다른_선택지는_합치지_않는다`, `form_b_결과는_통합과_버전별_통계를_돌려준다`(q1 "컴퓨터공학과"·"컴퓨터공학부"가 2번째 자리라 합쳐짐) |
| 4. 상태·삭제 조건 없이 응답을 읽는다 | `ResultService`의 두 곳을 `findAll()`을 폼으로만 거른 것으로 | form_b, AC20 | 잡음 | `form_b_결과는_통합과_버전별_통계를_돌려준다`(rb15·rb16 포함), `AC20_제작자가_삭제한_응답은_전체_수와_버전별_통계에서_빠진다`, `제출되지_않았거나_삭제된_응답의_상세는_404다` |
| 5. 소유권 검사를 뺀다 | `if (!form.isOwnedBy(userId))`를 `if (false)`로 | 403 | 잡음 | `AC62_다른_사용자가_제작한_폼은_403이다` |
| 6. 상세의 폼 일치 검사를 뺀다 | 상세 조회를 `findAll()`에서 제출·미삭제만 거르고 폼은 거르지 않게 | 다른 폼 응답 404 | 잡음 | `AC62_다른_폼의_응답_ID는_404다`, `제출되지_않았거나_삭제된_응답의_상세는_404다`. 뒤의 건은 주입이 삭제 여부를 메모리에서 걸렀는데, 테스트가 JDBC로 바꾼 `creator_deleted_at`이 1차 캐시의 엔티티에 반영되지 않아 생긴 부수 실패다. 실제 코드는 SQL에서 거른다 |
| 7. 상세 문구를 최신 버전에서 가져온다 | 상세의 정의 버전을 `form.definition_version`으로 | 제출 버전 문구 | 잡음 | `AC62_응답_상세는_제출_버전의_문구로_답을_돌려준다`(v1 응답의 q2가 "현재 학년"), `응답_상세에는_답한_문항만_있다` |

- 타이밍에 좌우되는 항목은 없어 항목마다 1번 실행했다. 2·6은 실패한 테스트 이름을 함께 남기도록 스크립트를 고친 뒤 한 번 더 실행했고 결과는 같았다.
- 스크립트 시행착오(판정 무효, 다시 실행): ① BOM 없는 스크립트를 Windows PowerShell 5.1이 ANSI로 읽어 구문 오류, 아무것도 바꾸지 않음 ② `$ErrorActionPreference = 'Stop'`에서 JVM 경고(stderr)가 예외가 되어 첫 항목에서 중단, `finally`로 복구됨 확인 ③ `cmd /c gradlew.bat`이 경로를 못 찾아 테스트가 돌지 않았고 XML이 없어 `ran=False`로 나와 무효 처리. 절대 경로로 고친 뒤 항목 5로 시험하고 7건 전체를 실행했다.

## §2 위임 프롬프트 원본

사람이 `T6_results_prompt.md`로 준 프롬프트 전문이다(수정 없음).

```text
[구현 내용]
T6(#54): GET /forms/{form_id}/results, GET /forms/{form_id}/responses/{response_id}, 결과 화면(통합·버전별 보기 전환), 응답 상세 화면을 만든다. 저장소 C:\Users\namhu\claude-practice\Passform, 브랜치 feat/results-dashboard(최신 origin/main에서 분기), 셸 Windows PowerShell.

[목표]
제작자가 실제 화면에서 자기 폼의 통합·버전별 통계와 응답 상세를 본다. 모든 숫자는 T1의 읽기 한 곳에서 나오고, AC19·AC20(결과 부분)·AC62를 테스트로 보인다.

[절대 규칙]
- AGENTS.md 4절·5절을 따른다.
- 결과와 상세는 응답을 ResponseRepository.findSubmittedNotDeletedByFormId로만 읽는다. 응답을 읽는 다른 경로를 만들지 않는다.
- X-Demo-User 같은 인증 우회나 새 permitAll을 넣지 않는다. T2(PR #76)가 main에 없으면 테스트에서는 모의 인증을 쓴다. 이때 인증 정보의 이름은 users ID로 두고, 현재 사용자 얻는 공용 코드는 만들지 않는다.
- 다음은 바꾸지 않는다: ResponseRepository, SecurityConfig, ErrorCode, ApiExceptionHandler, Flyway, CI, build.gradle, SPEC, ARCHITECTURE, AGENTS, data/seed. 바꿔야만 끝나면 멈추고 묻는다.
- SPEC에 없는 집계 동작은 추측하지 않는다. 구현 전에 맥락과 선택지를 적어 객관식으로 묻는다. 예: 통계에 넣을 문항 유형, 0건 선택지, form_versions의 뜻, 정렬.
- 같은 실패가 3회차를 넘거나 작업이 2시간을 넘으면 멈추고 보고한다. 작업 트리는 그대로 둔다.

[참조]
- 이슈 #54
- AGENTS.md 4~6절
- docs/SPEC.md 207·213행, 230~236행, 333행, 502~505행, 537행, 598·599·639행
- docs/ARCHITECTURE.md 3절, 10절(112~138행)
- src/main/java/com/opinio/passform/의 repository/, domain/form/, domain/response/, domain/error/, controller/advice/, config/SecurityConfig.java
- src/test/java/com/opinio/passform/repository/ResponseReadTest.java, support/
- data/seed/forms/form_b_mixed.json, data/seed/README.md
- frontend/src/의 App.tsx, routes/, api/client.ts, types/, pages/ExplorePage.tsx, frontend/docs/ROUTES_AND_API.md
- PR #69·#71 본문, PR #76(T2가 main에 없을 때만)

[완료 기준]
- 이슈 #54 완료 조건과 AGENTS.md 6절을 충족한다.
- 테스트 근거:
  - AC19·AC20 테스트 줄의 각 항목(응답 전체 삭제 항목은 제외)
  - form_b_mixed.json의 버전별 변경
  - AC62의 200·403·404
  - 두 API의 비로그인 401
  - 프런트의 보기 전환과 상세 표시
- `git grep`으로 결과·상세의 응답 조회가 findSubmittedNotDeletedByFormId뿐임을 보인다.
- 결함 주입: 아래를 하나씩 넣었을 때 테스트가 실패해야 한다. 기대한 테스트가 실제로 실행됐는지 build/test-results XML로 확인하고, 실행되지 않았으면 판정 무효로 다시 돌린다. 주입은 커밋하지 않고, 항목마다 되돌린다.
  - 묶기 키에서 문구를 뺀다
  - 묶기 키에서 유형을 뺀다
  - 선택지를 위치로 합산한다
  - 상태·삭제 조건 없이 응답을 읽는다
  - 소유권 검사를 뺀다
  - 상세의 폼 일치 검사를 뺀다
  - 상세 문구를 최신 버전에서 가져온다
- 실제 화면 확인: T2가 main에 있으면 사람에게 확인을 요청하고 결과를 기록한다. 없으면 "미확인 — T2 대기"로 남긴다.
- 위임 기록을 docs/prompts/delegation_results_dashboard.md에 남긴다. 넣을 것: 이 프롬프트 원본, 회차 표, 결함 주입 결과, 사람이 정한 답.

[범위 밖]
- 로그인·CSRF(T2)
- analyze API와 list 화면, 목록에서 상세로 가는 링크(E6·E7)
- 내보내기, 응답 전체 삭제, 첨부(T12 등). 상세의 attachments는 빈 배열로 둔다.
- 결과 캐시·집계 테이블

[깃허브 규칙]
- 사람의 명시적 승인을 받은 뒤에만 커밋·push·PR을 한다. 승인 요청에는 `git status --short`, `git diff --stat`, 핵심 diff, 테스트 집계, 제안 커밋 메시지를 보여 준다. 커밋 승인은 push 승인이 아니다.
- 커밋은 AGENTS.md 5절 협업 항목과 README.md를 따른다. 공통 파일(paths, App.tsx, types, client) 변경은 별도 커밋으로 나눈다.
- PR 제목: `[feat] 결과 통계와 결과 화면 (#54)`. 본문은 README.md를 따른다. "미확인 — T2 대기"가 남으면 `Closes #54` 대신 `#54`로 적는다.
- 리뷰어는 gh pr create --reviewer ye-nni로 지정한다.
- main에 직접 push하거나 머지하지 않는다.

[보고]
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 테이블 표 / 추가한 의존성과 이유 / 지시와 다르게 한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
- 테스트마다 통과 이유를 코드 위치와 함께 한 줄로 적는다.
- T2 머지 여부와, AC19·AC62 골든이 아직 YAML에 없다는 사실을 적는다.
```

## §3 회차 표

- 시작: 2026-11-07 오후(세션 시작), BASE `3009c2b`(origin/main, T2 PR #76 머지 포함). 화면 확인 뒤 `3a295ec`(E2 PR #78 머지 포함)로 rebase했다(10회차).
- 집계: 백엔드는 `build/test-results` XML, 프런트는 vitest 요약. 경과 시간은 사람을 기다린 시간을 뺀 대략값이다.

| 회차 | 대상 | 결과 | 실패한 테스트 | 원인 분류 | 고친 것 |
|---|---|---|---|---|---|
| 0 | `.\gradlew.bat cleanTest build` (변경 전) | 통과 | 없음 | 해당 없음 | 기준 확인 |
| 1 | `ResultStatisticsTest`·`ResultApiTest` | 19 통과 · 1 실패 | `없거나_삭제된_폼은_404다`: 기대 404, 실제 200 | 테스트 준비 오류 | JDBC로 `deleted_at`을 바꾼 뒤 같은 트랜잭션의 1차 캐시가 옛 엔티티를 돌려줌. 테스트에서 `EntityManager.clear()` |
| 2 | 같음 | 20 통과 | 없음 | 해당 없음 | — |
| 3 | 프런트 lint·typecheck·test | lint·typecheck 통과, test 19 실패 | 기존 `App.routes.test.tsx` 18건과 새 가짜 데이터 모드 1건 | 환경 | 로컬 `frontend/.env.local`(git 무시 파일)이 `VITE_USE_API=true`. CI와 같게 `VITE_USE_API=false`로 실행 |
| 4 | 프런트 test (`VITE_USE_API=false`) | 73 통과 · 1 실패 | `/creator/forms/abc/results 는 공통 레이아웃과 함께 "결과" 화면을 연다`: "가상 데이터 모드" 문구가 두 곳 | 구현 오류 | 안내 문구를 "이 화면은 서버에 연결했을 때 실제 응답으로만 보여요."로 바꿈(테스트는 고치지 않음) |
| 5 | 프런트 lint·typecheck·test | 통과 (74) | 없음 | 해당 없음 | — |
| 6 | `.\gradlew.bat cleanTest build` | 통과 | 없음 | 해당 없음 | — |
| 7 | 새 테스트 2개 | 20 통과 | 없음 | 해당 없음 | `SeedForms`가 배포 상태를 JDBC 대신 엔티티 필드로 넣게 바꿈(주입 7이 NPE가 아니라 문구 차이로 실패하도록) |
| 8 | 결함 주입 7건 | 7건 모두 잡음 | §1 | 해당 없음 | — |
| 9 | `.\gradlew.bat cleanTest build` (최종) | 통과. 160건 중 실패 0, skip 2, 줄 커버리지 99.6% | 없음. skip 2건은 T2부터 있던 `AccessGoldenTest`의 `other_users_response_404`(E5 경로), `non_owner_analyze_403`(E6 경로) | 해당 없음 | — |
| 10 | rebase 뒤 `.\gradlew.bat cleanTest build`, 프런트 lint·typecheck·test·build | 백엔드 통과(273건 중 실패 0, skip 2, 줄 커버리지 99.1%), 프런트 통과(74) | 없음 | 해당 없음 | E2가 `Form`에 같은 `isOwnedBy()`·`isDeleted()`를 넣어 제 추가분을 빼고 E2 것을 씀. `DefinitionQuestion`이 E2의 `FormJson`으로 정의를 읽게 바꿈 |
| 11 | rebase 뒤 결함 주입 7건 | 7건 모두 잡음. 실패한 테스트는 §1 표와 같음 | §1 | 해당 없음 | — |
| 12 | PR #80 GitHub Actions | `build` 통과, `frontend` 실패 | `ResultsPage.test.tsx` 상세 화면 테스트: CI의 Node가 `toLocaleString('ko-KR')`에서 "오전" 대신 "AM"을 냄(`2026. 10. 8. AM 11:30:00`) | 구현 오류(실행 환경의 로캘 데이터에 의존) | `seoulTime`이 숫자만 받아 "오전/오후"를 직접 붙이게 바꿈. 로컬에서 자정·정오 경계 5건이 기존 `ko-KR` 결과와 같음을 확인 |

같은 실패가 3회차를 넘은 것은 없다.

### 진행 중 사람이 정한 것 (2026-11-07, 구현 전 객관식)

| 질문 | 정한 답 |
|---|---|
| 통계에 넣을 문항 유형 | 선택형 3종(`multiple_choice`·`dropdown`·`checkbox`)만. 단답·장문은 빠진다 |
| 0건 선택지 | 그 버전 정의에 있는 선택지는 `count: 0`으로 포함 |
| `form_versions`의 뜻 | 그 문항·선택지가 정의에 있는 버전(0건이어도 포함) |
| 버전 범위·정렬 | 제출 응답이 1건 이상인 버전만. `versions`는 `form_version` 오름차순, `combined`의 문항·선택지는 낮은 버전부터 정의 순서로 처음 나온 순 |
| 응답 상세의 `content` | 답한 문항만, 제출 버전 정의의 순서. 정의에 없는 `question_id`의 답은 뺀다 |
| 정의 밖 답 | 세지 않고 무시(`response_count`에는 그대로 포함) |
| 폼 상태 | 삭제된 폼은 소유자 검사 전에 404, 배포 전 초안 폼은 200 빈 결과 |
| 응답 상세 화면 경로 | `/creator/forms/:formId/responses/:responseId` |

### 실제 화면 확인

T2(PR #76)가 main에 있어 사람에게 확인을 요청한다. 결과는 확인 뒤 여기에 적는다.

- 상태: 확인함(2026-11-07, 사람). rebase 전 코드(`3009c2b` 기준 작업 트리)로 확인했다.
- 준비: 로컬 전용 `_local/seed_form_b.ps1`로 form_b를 개발 DB `public`에 `namhui271@gmail.com` 소유로 넣고(form_id `7b84b2a4-494d-4ea9-8366-dfcaa2db1883`), `bootRun`과 `npm run dev`(`VITE_USE_API=true`)로 열었다. 스크립트는 커밋하지 않는다.
- 결과: 아래 21개 항목이 모두 기대대로였다(사람 보고 "화면 확인 다 맞아").

| # | 항목 | 기대 |
|---|---|---|
| 1~2 | 결과 상단·탭 | 전체 응답 14, 탭 통합 보기·폼 버전 1·2·3 |
| 3~4 | 통합 · 소속 학과 | 폼 버전 1·2, 소프트웨어학과 4·경영학과 4, 컴퓨터공학과 2 "폼 버전 1에만 있음", 컴퓨터공학부 1 "폼 버전 2에만 있음" |
| 5 | 통합 · 학년 | "학년"(폼 버전 1)과 "현재 학년"(폼 버전 2·3) 별도 |
| 6 | 통합 · 프로젝트 경험 | 예 11, 아니요 3 |
| 7 | 통합 · 관심 분야 | 기획 6, 디자인 4, 개발 4, 데이터 분석 2 "폼 버전 2·3에만 있음" |
| 8 | 통합 · 희망 학과 | 폼 버전 3, 경영학과 2, 데이터사이언스학과 1 |
| 9 | 통합 | 자기소개(장문) 없음 |
| 10~12 | 버전 탭 | 폼 버전 1 응답 6·2학년 2 / 폼 버전 2 응답 5·2학년 0 / 폼 버전 3 응답 3·팀장 0·팀원 2, 전체 14 유지 |
| 13 | 모든 탭 | v1, q1, 응답 ID 없음 |
| 14~15 | 상세 rb02 | 폼 버전 1, 제출 2026. 10. 8. 오전 11:30:00, 학년 3학년, 관심 분야 기획 |
| 16 | 상세 rb05 | 맡은 역할 없음 |
| 17 | 상세 rb15 | "응답을 찾을 수 없어요." |
| 18 | 모든 상세 | 응답 ID 없음 |
| 19 | 비로그인 | "로그인이 필요해요." |
| 20 | 다른 계정 | "이 폼을 만든 사람만 볼 수 있어요." |
| 21 | 360px | 페이지 가로 스크롤 없음 |

- 확인 중 알게 된 것: `/creator`(내가 만든 폼)는 아직 가짜 값이라 넣은 폼이 보이지 않는다. 서버 `GET /me/created-forms`는 E2 PR #78로 main에 있고, 화면 연결은 E3 #41(열림)이다. 그 전까지 결과·상세는 주소로만 열린다.

### 기타

- 테스트 기대값(form_b 결과 JSON, AC19 합성 케이스, 상세 JSON)은 에이전트가 fixture를 손으로 세어 쓴 것이다. AC19(`results_option_counts_combined_and_by_version`, `results_changed_question_separate`)와 AC62(`creator_list_response_detail`, `creator_response_other_form_404`)의 골든은 아직 `tests/harness/golden_cases.yaml`에 없다.
- 결과·상세가 응답을 읽는 곳은 `ResultService`의 `findSubmittedNotDeletedByFormId` 두 곳뿐이다(`git grep --untracked -n 'ResponseRepository\|responses\.find' -- src/main`).
- `PlaceholderPage.tsx`는 이제 쓰는 곳이 없지만 지우지 않았다.
- 다른 세션(T3, `_local/wt-t3`)이 같은 Postgres를 쓴다. DB 테스트는 시작할 때 `passform_test`를 비우므로 동시에 돌리지 않았다. rebase 뒤 검증 전에 DB 연결이 0인지 확인했다.
- 프런트 `prettier --check`는 작업 트리가 CRLF(`core.autocrlf=true`)라 경고를 내지만, `--end-of-line auto`로는 모든 파일이 통과한다. 커밋은 LF로 저장된다.

## 수용 판단

(사람이 적는다)
