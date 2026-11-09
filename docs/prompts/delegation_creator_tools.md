# 제작자 도구 위임 기록 (#59)

기록 대상: E14 제작자 자동 저장, 폼 복사, QR, 분기 그래프. 실행일 2026-11-09. 작성: 프롬프트 세션 초안을 사람이 검토해 발행했고 §1에 그대로 둔다.

## §1 프롬프트

```text
이번 작업은 이슈 #59(E14 제작자 자동 저장, 폼 복사, QR, 분기 그래프)뿐이다. 나는 ye-nni이고 리뷰어는 Psoyeon1117이다. 셸은 Windows PowerShell이다. 11/11 시연 전에 끝내는 것이 목표다.

# 사람이 정한 것 (바꾸지 않는다)
- QR은 이슈대로 서버 API(GET /forms/{form_id}/qr)로 만들고, ZXing(com.google.zxing core와 javase, 같은 버전, Maven Central의 최신 안정판) 하나만 새 라이브러리로 더한다. 그 밖의 새 라이브러리(서버·화면 모두)는 더하지 않는다.
- 분기 그래프 화면은 그래프 라이브러리 없이 그린다(목록·들여쓰기·화살표 글자나 간단한 SVG면 된다).
- 미룸(이번 PR에서 하지 않는다):
  - 복사의 409 storage_quota_exceeded(용량 계산이 아직 없다, T12 #63)
  - 빠른 모드 자동 저장(AC84 모바일 쪽, E13 #58이 main에 들어온 뒤)
  - 폼 삭제, 첨부 복사

# 비용 규칙 (주간 한도가 빠듯하다. 지킨다)
- 서브에이전트를 띄우지 않는다.
- SPEC 전체를 읽지 않는다. 아래 행만 읽는다.
- 결함 주입은 기능마다 1건, 모두 4건까지.
- 단계마다 로컬에 커밋한다.
- 최종 보고는 40줄 안팎으로 쓴다.

# 시작 확인 (하나라도 어긋나면 멈추고 알린다)
1. C:\Opinio와 C:\Opinio-deploy는 다른 작업의 폴더다. 그 폴더의 파일과 브랜치는 건드리지 않는다.
2. C:\Opinio에서 git fetch origin을 한 뒤 다음을 실행한다. 이후 작업은 C:\Opinio-e14에서만 한다.
   git worktree add ..\Opinio-e14 -b feat/creator-tools origin/main
3. C:\Opinio\.env를 C:\Opinio-e14\.env로 복사한다. 이 복사본은 worktree를 지울 때까지 지우지 않는다. 값은 출력하지 않는다.
4. C:\Opinio-e14\frontend에서 npm ci를 한다(이 폴더의 node_modules만 만든다. 다른 폴더의 실행 중인 서버는 건드리지 않는다).
5. .\gradlew.bat cleanTest build가 지금 통과하는지 본다.
   - DB는 실행 중인 opinio-db-1을 쓴다. docker compose로 DB를 새로 띄우거나 내리지 않는다.
   - 다른 세션과 동시에 gradle 테스트를 돌리지 않는다. 다른 세션이 테스트 중인지 모르면 묻는다.

# 먼저 읽을 것 (이곳만)
- AGENTS.md 2·3·5절(자동으로 읽힌다)
- gh issue view 59
- docs/SPEC.md: 118, 125~126, 153, 157~158, 204행, AC2(374행), AC84(396~397), AC40(414~415), AC45(432), AC90(447~448), AC10(476), AC 표 589·619·624·661행
- docs/ARCHITECTURE.md에서 copy, qr, branch-graph, 자동 저장을 언급한 곳만(검색해서 그 행만)
- 서버: controller/FormController.java, service/FormService.java, service/ShareUrls.java, domain/form/ResponsePathCalculator.java, domain/form/FormDefinition.java, domain/error/ErrorCode.java, controller/advice/ApiExceptionHandler.java와 그 테스트
- 화면: pages/BuilderPage.tsx, pages/CreatedPage.tsx, routes/creatorRoutes.tsx, api/client.ts, types/form.ts, builder/branchRules.ts, response/draftSync.ts(자동 저장 상태와 나갈 때 경고를 어떻게 했는지)

# 절대 규칙
- 판정 기준 파일과 기존 테스트를 고치지 않는다(AGENTS 4절). 기존 테스트가 깨지면 고치지 말고 멈춰 묻는다. 새 테스트 파일과 새 테스트 함수는 더해도 된다.
- 권한: copy·qr·branch-graph는 그 폼의 제작자만. 로그인 없으면 401, 제작자가 아니면 403이고 내용을 반환하지 않는다(AC2). 없는 폼은 기존 방식대로.
- 인증 우회, demo 프로필, X-Demo-User를 넣지 않는다. feat/demo 코드는 참고만 하고 재사용하기 전에 알린다.
- 분기 해석은 공통화한다(AGENTS 5절). 그래프의 대상 위치·기본 다음 이동 계산을 ResponsePathCalculator와 따로 새로 짜지 않는다. 필요하면 그 안의 계산(예: targetIndex)을 공유 가능한 메서드로 꺼내되, 기존 동작과 기존 테스트 결과를 바꾸지 않는다.
- 현재 시각은 주입한 Clock으로만 얻는다. 엔티티를 API 응답에 직접 쓰지 않는다. 새 오류 코드(not_open 409)는 advice 매핑 테스트를 더한다.
- 커밋과 PR: 에이전트 이름이나 생성 문구를 넣지 않는다. PR 본문에 사람을 @로 언급하지 않는다. _local/과 docs/SPEC_followups.md는 커밋하지 않는다. git add는 경로를 명시하고, git add 성공과 스테이징 목록을 확인한 뒤 커밋한다.

# 만들 것 (이 순서로, 기능마다 커밋)
1. 분기 그래프 GET /forms/{form_id}/branch-graph (AC45, SPEC 125행)
   - 현재 저장된 폼 정의(초안이면 저장된 초안)로 만든다.
   - 노드: 섹션, 문항, 제출(submit) 하나. 엣지: 규칙마다 rule 엣지 1개(rule_index 포함), 문항마다 default 엣지 1개(섹션 마지막 문항은 다음 섹션 첫 문항, 마지막 섹션 마지막 문항은 submit).
   - 화면: 빌더에서 "분기 흐름 보기"로 연다. 문항은 문항 문구로 보여 준다(q1 같은 ID를 보이지 않는다, AGENTS 5절). 기본 이동과 규칙 이동을 구분해 보여 주고, 저장된 내용 기준이라는 것을 한 줄로 알린다.
   - 테스트 이름에 branch_graph_matches_rules를 쓴다.
2. QR GET /forms/{form_id}/qr (AC10, SPEC 126·204행)
   - share_url을 담은 PNG. share_url이 없으면(한 번도 open하지 않음) 409 {error: "not_open"}.
   - 테스트: 만든 PNG를 ZXing으로 다시 읽어 share_url과 같은지(qr_decodes_to_share_url), 초안은 409(qr_draft_409).
   - 화면: 공유 링크가 보이는 곳(내가 만든 폼 목록 CreatedPage, 빌더의 배포 뒤 공유 링크 옆)에서 QR을 보고 PNG로 내려받을 수 있다.
3. 폼 복사 POST /forms/{form_id}/copy (AC40, SPEC 118·157·158행, AC90)
   - 섹션·문항·분기·설정이 같은 새 draft 폼을 요청한 제작자 소유로 만든다. 응답, share_url, slug, definition_version, 배포 때 고정한 고지는 가져오지 않는다. 원본은 바뀌지 않는다. 응답 201 {form_id, status: "draft"}.
   - 복사본은 ai_summary.enabled 값을 가져오지만 draft라 바꿀 수 있다(AC90).
   - 제목은 원본 그대로 둔다(AC40 "id 제외 같음"). 바꾸고 싶은 이유가 생기면 멈추고 묻는다.
   - 테스트: 응답 있는 폼 복사 → 새 폼 정의가 원본과 같음(form_id 제외), 응답 0건, share_url 없음, 원본 불변(copy_form_without_responses). 복사본에서 ai_summary를 바꿀 수 있음.
   - 화면: 내가 만든 폼 목록에서 "복사" → 새 초안 빌더로 이동.
4. 제작자 초안 자동 저장 (AC84 기본 모드, SPEC 153행)
   - draft 폼을 기본 모드에서 편집하면 입력이 멈춘 뒤 잠시 기다렸다가 PATCH로 현재 폼 정의를 저장한다. 같은 시점에 요청이 겹치지 않게 한다(앞 저장이 끝나기 전에 바뀐 내용은 그 뒤에 다시 보낸다).
   - 자동 저장은 saveProblems 같은 의미 검증을 하지 않는다. 미완성 분기·동의 템플릿 상태도 초안으로 저장한다. 배포하지 않고 definition_version을 늘리지 않는다.
   - 저장 중·완료·실패를 화면에 구분한다. 실패하면 완료로 보이지 않고, 저장이 끝나지 않은 채 나가려 하면 경고한다(브라우저 닫기와 앱 안 이동 모두). draftSync.ts의 방식을 참고하되 응답 쪽 코드는 바꾸지 않는다.
   - open·closed 폼은 지금처럼 명시적 저장만 한다. draft에서도 기존 "저장" 버튼은 남긴다(기존 테스트를 지키기 위해).
   - 테스트 이름에 creator_draft_autosave_both_modes(기본 모드 부분), creator_autosave_no_publish, creator_autosave_failure_visible을 쓴다. 서버 쪽으로 "버전 1·응답 0건 폼을 draft로 되돌린 뒤 PATCH해도 definition_version 1 유지"가 기존 테스트에 없으면 더한다.

# 테스트 (일반 목록, 이것만)
- 서버: 위 기능별 테스트, 권한(401·403) 각 API 1건씩, not_open advice 매핑.
- 화면: 자동 저장 상태 3가지와 나갈 때 경고, 복사 버튼, QR 표시, 그래프의 문구 표시. 테스트는 VITE_USE_API=false로 돌린다.
- 결함 주입: 기능마다 1건(예: default 엣지 하나 빼기, QR에 다른 URL, 복사에 share_url 남기기, 실패를 완료로 표시). 해당 테스트가 실패하는지 확인하고 되돌린다. 주입은 커밋하지 않는다.

# 사람 확인 (push 전에 멈추고 요청한다)
- 실행: C:\Opinio-e14에서 DB_NAME=passform_e3로 백엔드 bootRun, frontend는 VITE_USE_API=true로 npm run dev. 실행 명령과 주소를 보고에 적는다. Google 로그인은 사람이 한다.
- 사람이 확인할 것(무엇을 누르면 무엇이 보여야 통과):
  1. 새 폼 만들기 → 제목과 문항 하나 입력 → 손을 떼면 "저장 중…" 뒤 "저장됨"이 보인다 → 새로고침해도 입력이 남아 있다.
  2. 개발자 도구 Network에서 오프라인으로 바꾸고 문항 문구를 바꾼다 → "저장 실패"가 보이고 "저장됨"으로 바뀌지 않는다 → 이 상태에서 탭을 닫으려 하면 경고가 뜬다.
  3. 폼을 배포 → 내가 만든 폼 목록에서 QR 보기 → 휴대폰 카메라로 찍으면 그 폼 응답 화면이 열린다(같은 와이파이에서 열리지 않으면 QR 안의 주소가 공유 링크와 같은지만 본다).
  4. 같은 목록에서 "복사" → 새 초안 빌더가 열리고 문항·분기·설정이 같다. 원본에는 변화가 없고, 복사본에는 공유 링크가 없다.
  5. 분기가 있는 폼에서 "분기 흐름 보기" → 모든 규칙 이동과 기본 이동이 문항 문구로 보인다.
- 사람이 화면 수정 의견을 주면 이 이슈 범위 안에서만 반영한다. 다른 담당 화면을 바꿔야 하면 객관식으로 묻고, 바꿨으면 PR 본문에 적는다.

# 완료 기준
- .\gradlew.bat cleanTest build 통과(build/test-results/test XML 기준 건수와 skip 0 보고).
- frontend에서 npm run lint, npm run typecheck, npm test 통과(건수 보고). 로컬에서 Autofill·DraftGate 시험이 5초 시간 초과로 가끔 실패하는 것은 알려진 문제다. 그 파일을 바꾸지 않았으면 다시 한 번 돌려 보고 결과를 적는다.
- 사람 확인 1~5 통과.
- git diff origin/main...HEAD --name-only에 판정 기준 파일과 기존 테스트 변경이 없다(새 테스트 파일·새 테스트 함수만).
- docs/prompts/delegation_creator_tools.md에 짧게 기록한다: 작성은 "프롬프트 세션 초안을 사람이 검토해 발행", 이 프롬프트, 사람 결정, 회차, 미룸.
- PR의 CI에서 build와 frontend job 통과.

# 중단 조건
- 같은 실패로 3회차를 넘길 때, 작업이 4시간을 넘길 때.
- SPEC·AGENTS·ARCHITECTURE에 없는 판단이 필요할 때, 기존 테스트를 고쳐야 할 때, 판정 기준 파일을 바꿔야 할 때.
- 멈추면 맥락을 적은 객관식으로 묻고 추천안을 첫 번째에 "(추천)"으로 둔다.

# 커밋과 보고
- 커밋: "{tag}: 한글 메시지 (#59)", 50자 이내, 파일명·디렉터리명 없이.
- 사람 확인을 통과하면 push하고 PR을 연다.
  - 제목: "[feat] E14 자동 저장, 복사, QR, 분기 그래프 (#59)"
  - 본문: README 형식, "Closes #59". 미룸(복사 용량 검사 T12, 빠른 모드 자동 저장 E13)을 적는다. --reviewer Psoyeon1117
- PR을 연 뒤 CI를 확인하고 멈춘다. 머지하지 않는다. worktree와 .env 복사본을 지우지 않는다.
- 보고 순서: 1) 실행한 명령과 결과(건수) 2) 기능별 변경 파일과 근거 AC 3) 결함 주입 결과 4) 지시와 다르게 한 것 5) 판단이 필요해 멈춘 것 6) 사람 확인 결과 7) PR 링크와 CI
```

## §2 사람 결정

- 프롬프트의 "사람이 정한 것"(ZXing 3.5.4 하나만, 그래프 라이브러리 없음, 미룸 목록).
- 작업 중 결정 1건: `NOT_OPEN`을 더하면 기존 `ApiExceptionHandlerTest`의 매개변수 시험(모든 오류 코드가 `EXPECTED` 표에 있어야 함)이 실패한다. `EXPECTED`에 `NOT_OPEN → 409` 한 줄만 더하기로 했다(ErrorCode.java 6행의 절차, #44·#60·#61과 같음). 기존 기대값·단언문은 그대로다.

## §3 회차

| 회차 | 내용 | 결과 |
| --- | --- | --- |
| 1 | 분기 그래프 API·화면. 기본 다음 이동은 `ResponsePathCalculator.nextIndex`로 꺼내 공유 | 서버 3건·화면 2건 통과, 결함 주입(마지막 default 엣지 빼기) 2건 실패 확인 |
| 2 | QR API·화면, `not_open` 409. `FormService` 생성자를 골든 테스트가 직접 쓰므로 `FormQrService`로 분리 | 서버 4건 통과, 결함 주입(다른 URL) 2건 실패 확인 |
| 3 | 폼 복사 API·목록 버튼 | 서버 3건·화면 1건 통과, 결함 주입(복사본에 공유 링크) 2건 실패 확인 |
| 4 | 초안 자동 저장(1초 뒤, 순서대로 한 건씩), 상태 표시, 나갈 때 경고 | 화면 5건 통과, 결함 주입(실패를 저장됨으로) 1건 실패 확인 |
| 5 | 사람 확인 뒤 의견: "복제" 문구, 카드 버튼 순서 | 화면 시험 통과 |
| 6 | 사람 의견: 목록의 "QR 보기"를 공유 링크 오른쪽 밑줄 글자 버튼으로 옮김 | 화면 시험 통과, 사람 확인 통과 |

지시와 다르게 한 것

- 기존 테스트 파일 1개(`ApiExceptionHandlerTest`)에 표 한 줄을 더했다(§2 사람 결정).
- "버전 1·응답 0건 폼을 draft로 되돌린 뒤 PATCH해도 버전 1 유지"는 기존 `FormApiDatabaseTest.AC56_되돌렸다_다시_열_때_같으면_같은_버전_다르면_다음_버전이다`가 이미 확인해 더하지 않았다.
- 앱 안 이동 경고는 메뉴 링크·로고·바닥글 버튼 클릭을 가로채 확인을 받는다. 브라우저 뒤로 가기는 `BrowserRouter`에서 막을 수 없어 경고하지 않고, 기다리던 저장을 화면을 떠날 때 보낸다.

## §4 사람 확인 결과

- 실행: 백엔드 `DB_NAME=passform_e3`, `FRONTEND_ORIGIN=http://localhost:5174`로 `bootRun`, 프론트 `VITE_USE_API=true`로 `npm run dev -- --port 5174`. 5173은 다른 세션의 개발 서버가 쓰고 있어 건드리지 않았다.
- 1~5 모두 통과(자동 저장 중·완료, 오프라인 실패와 나갈 때 경고, QR, 복사, 분기 흐름).
- 화면 수정 의견(사람 결정): 목록의 "복사"를 "복제"로 바꿨다. "QR 보기"는 카드 버튼에서 빼고 공유 링크 URL 오른쪽의 밑줄 글자 버튼으로 옮겨, 공유 링크처럼 배포 전에는 보이지 않는다. 카드 버튼은 폼 편집, 복제, 결과 보기 순서다. 빌더의 QR 버튼은 그대로 둔다. 위치와 순서는 화면 시험이 확인한다.

## §5 미룸

- 복사의 `409 storage_quota_exceeded`(T12 #63), 빠른 모드 자동 저장(E13 #58 뒤), 폼 삭제, 첨부 복사(복사본은 정의 안의 첨부 정보를 그대로 가진다. 첨부 업로드가 아직 없다).
