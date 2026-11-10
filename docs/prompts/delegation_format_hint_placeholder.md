# 위임 기록: 형식 안내를 입력란 안으로 (#114)

## 구현

- `response/answerCheck.ts`: 형식 안내를 문장(`sentence`)과 예시(`example`)로 나눴다. `formatHint`는 전과 같은 문장 전체를 돌려주고 형식 오류 문구(`problemMessage`)도 그대로 이것을 쓴다. 새 `formatPlaceholder`는 360px에서 잘리는 형식(`exampleOnlyKinds`)이면 `예: {example}`, 아니면 `formatHint`를 돌려준다.
- `pages/ResponsePage.tsx`: 문항 아래의 `hint-{id}` 문단을 지웠다. `input_format`이 있는 단답 입력란은 `placeholder`에 제작자 `input_format.placeholder`, 없으면 `formatPlaceholder`를 넣고 `format-input` 클래스를 붙인다. 날짜의 기존 기본값 `YYYY-MM-DD`는 없어졌다. 응답·제출 후 수정·빌더 미리보기는 모두 같은 `ResponseForm`을 쓰므로 한 곳만 바꿨다.
- `styles.css`: `.format-input:focus::placeholder { color: transparent; }`. 포커스하면 안내가 사라지고, 비어 있는 채로 벗어나면 다시 보인다. 자바스크립트로 값이나 속성을 바꾸지 않는다.
- 화면 읽기 프로그램: `hint-{id}`를 가리키던 `aria-describedby`를 지웠다. placeholder가 안내 문장 전체와 같으면(이름·주소·학번) 화면 읽기 프로그램이 placeholder를 읽으므로 설명을 따로 달지 않는다(같은 문장을 두 번 읽지 않게). placeholder가 문장 전체가 아니면(예시만이거나 제작자 문구) 화면에 보이지 않는 `sr-only` 설명(`format-{id}`)에 문장 전체를 넣고 `aria-describedby`로 잇는다. 오류가 나면 `error-{id}`도 함께 잇는 것은 전과 같다.
- 서버, API, 스키마, SPEC은 바꾸지 않았다. 새 의존성도 없다.

## 360px 측정 (실제 브라우저)

Chrome 헤드리스로 가짜 데이터 모드 응답 화면을 폭 360px 프레임에 띄우고, 형식 7종 문항을 데모 폼에 임시로 넣어 쟀다(임시 변경은 측정 뒤 되돌렸다). 입력란 글자 영역은 232px, 글꼴은 16px Pretendard Variable이다. 스크린샷으로도 같은 결과를 확인했다.

| 형식 | 문장 전체 너비 | 판정 | placeholder |
|---|---|---|---|
| `name` | 137px | 들어감 | 이름을 입력해 주세요. |
| `address` | 186px | 들어감 | 주소를 한 칸에 입력해 주세요. |
| `student_id` | 137px | 들어감 | 학번을 입력해 주세요. |
| `mobile_phone` | 336px | 잘림 | 예: 010-1234-5678 (136px) |
| `landline_phone` | 287px | 잘림 | 예: 02-123-4567 (119px) |
| `email` | 325px | 잘림 | 예: name@example.com (171px) |
| `date` | 413px | 잘림 | 예: 2026-11-20 (106px) |

잘리는 형식은 모두 예시가 있어 멈출 일이 없었다.

## 회차

| 회차 | 한 일 | 결과 |
|---|---|---|
| 1 | 구현, 360px 측정 | 잘리는 형식 4종을 `exampleOnlyKinds`로 고정 |
| 2 | 시험 추가와 기존 단언 2곳 수정 | lint·typecheck 통과, `ResponsePage.screen.test.tsx` 47건 통과 |
| 3 | 전체 시험 | 28건 실패(App.routes 27, ResultsPage 1). 수정 전 main에서도 같은 28건 실패. 로컬 `.env.local`의 `VITE_USE_API=true`가 Vitest에 읽혀 가짜 데이터 모드 시험이 깨진 것. `VITE_USE_API=false`(CI와 같은 조건)로 전체 468건 통과 |
| 4 | 결함 주입 7건 | 모두 유효 |

## 결함 주입 결과 (`ResponsePage.screen.test.tsx`, Vitest 결과에서 실패한 시험 이름으로 확인)

| 주입 | 기대 시험 | 실패 수 |
|---|---|---|
| 안내 문단을 지우지 않는다 | 문항 아래(파일 첨부 아래)에 따로 놓인 안내 문단이 없다 외 | 4 |
| placeholder에 안내를 넣지 않는다 | 360px에서 들어가는 형식은 문장 전체, 잘리는 형식은 예시만 넣는다 외 | 7 |
| 잘리는 형식에 문장 전체를 넣는다 | 360px에서 들어가는 형식은 문장 전체, 잘리는 형식은 예시만 넣는다 외 | 7 |
| 제작자 `placeholder`를 무시한다 | 제작자가 정의에 넣은 placeholder가 있으면 그것을 쓴다 | 1 |
| 형식 없는 문항에도 placeholder를 넣는다 | 형식 없는 문항에는 placeholder가 없다 | 1 |
| 형식 오류 문구에서 안내를 뺀다 | 형식 오류 문구에는 안내 문장 전체가 그대로 들어간다 | 1 |
| 수정 화면에는 적용하지 않는다 | 제출 후 수정 화면에도 같은 규칙을 쓴다 | 1 |

## 바꾼 기존 시험 단언 (`frontend/src/pages/ResponsePage.screen.test.tsx`)

사람이 이 작업으로 바꾸라고 지시한 동작(안내를 문항 아래 문단에서 입력란 안으로)을 고정한 단언만 바꿨다. 입력란 종류·오류 문구 기대값은 그대로다.

| 시험 | 전 | 후 |
|---|---|---|
| `형식마다 안내 문구를 늘 보이고 맞는 입력란을 쓴다` → `형식마다 입력란 안에 안내를 넣고 맞는 입력란을 쓴다` | `getByText('휴대전화 번호를 입력해 주세요. 예: 010-1234-5678')` | `input('휴대전화').placeholder`가 `예: 010-1234-5678` |
| 같은 시험 | `getByText('이메일을 입력해 주세요. 예: name@example.com')` | `input('이메일').placeholder`가 `예: name@example.com` |
| 같은 시험 | `getByText('날짜를 YYYY-MM-DD 형식으로 입력해 주세요. 예: 2026-11-20')`, `input('방문 날짜').placeholder`가 `YYYY-MM-DD` | `input('방문 날짜').placeholder`가 `예: 2026-11-20` |
| `basic_builder_five_question_types의 응답 화면` | `getByText('날짜를 YYYY-MM-DD 형식으로 입력해 주세요. 예: 2026-11-20')` | `input('짧은 답').placeholder`가 `예: 2026-11-20` |

## 사람이 정한 답

없음. 작업 중 객관식으로 물을 판단이 생기지 않았다.

## 사람 화면 확인

(요청 중. 결과를 받으면 적는다.)

## 원본 프롬프트

````text
[구현 내용]
응답 화면에서 입력 형식이 있는 문항(`input_format`)의 작성 안내 문구(예: "이름을 입력해 주세요.")를 문항 아래 별도 문단이 아니라 입력란 안의 placeholder로 옮기고, 응답자가 입력란을 누르면(포커스) 사라지게 한다. 화면만 고친다. 저장소 C:\Users\namhu\claude-practice\Passform, 브랜치 feat/format-hint-placeholder(최신 origin/main에서 분기), 셸 Windows PowerShell.

[목표]
응답자는 빈 입력란 안에서 무엇을 쓸지 보고, 입력란을 누르면 안내가 사라져 바로 쓸 수 있다. 문항 아래(파일 첨부 아래)에 따로 놓인 안내 문단은 없어진다. 형식이 틀렸을 때의 안내와 서버 검증은 그대로다.

[절대 규칙]
- AGENTS.md 3~5절을 따른다.
- 화면만 고친다. 서버 코드, API, 스키마 파일, SPEC, ARCHITECTURE, AGENTS, data/seed, tests/harness, CI는 바꾸지 않는다. 바꿔야만 끝나면 멈추고 묻는다.
- 대상은 `input_format`이 있는 입력란뿐이다. 형식 없는 문항, `question_description`, 자동채우기 안내("프로필에서 채운 값이에요."), 오류 문구의 위치는 바꾸지 않는다.
- 안내 문구는 `formatHint`(response/answerCheck.ts) 한 곳에서만 가져온다. 문구를 화면 코드에 다시 쓰지 않는다.
- 형식 오류 문구("형식이 맞지 않아요. …")에는 지금처럼 같은 안내가 들어가야 한다. placeholder가 사라진 뒤에도 틀렸을 때 예시를 다시 볼 수 있어야 하기 때문이다.
- 포커스 때 사라지는 것은 CSS(`:focus::placeholder`)로 한다. 입력값이나 `placeholder` 속성을 자바스크립트로 지웠다 되살리지 않는다. 입력란 밖을 누르면 비어 있을 때 다시 보여야 한다.
- 제작자가 정의에 넣은 `input_format.placeholder`가 있으면 그것을 쓰고, 없으면 `formatHint`를 쓴다. 날짜의 기존 기본값 `YYYY-MM-DD`는 `formatHint`로 바뀐다.
- 화면 읽기 프로그램이 안내를 놓치지 않게 한다. 지우는 `hint-{id}` 문단을 가리키던 `aria-describedby`도 함께 정리하고, 안내를 접근 가능한 설명으로 남기는 방법을 보고에 적는다.
- 360px 폭에서 입력란 안에서 잘리는 안내 문구는 예시 부분만 placeholder로 쓴다(예: 휴대전화는 "예: 010-1234-5678", 날짜는 "예: 2026-11-20"). 잘리지 않는 문구는 문장 전체를 쓴다. 어느 형식이 잘리는지는 실제 브라우저 360px 폭에서 확인해 보고에 적고, 형식마다 고정으로 정한다. 화면 폭에 따라 실행 중에 바꾸지 않는다. 예시도 `formatHint`와 같은 곳(answerCheck.ts)에서 가져오고 문구를 따로 쓰지 않는다. 예시가 없는 형식이 잘리면 멈추고 묻는다.
- 기존 테스트 변경 금지의 예외는 아래뿐이다. 사람이 이 작업으로 바꾸라고 지시한 동작을 고정한 단언이다. 바꾼 줄과 이유를 보고한다.
  - frontend/src/pages/ResponsePage.screen.test.tsx의 `형식마다 안내 문구를 늘 보이고 맞는 입력란을 쓴다`: 안내 문구를 본문 텍스트로 찾는 단언과 날짜 placeholder `YYYY-MM-DD` 단언을 placeholder 확인으로 바꾼다. 테스트 이름도 바뀐 동작에 맞게 고칠 수 있다.
  - 같은 파일에서 날짜 안내 문구를 본문 텍스트로 찾는 다른 단언(문항 유형별 입력란 테스트)도 같은 방식으로 바꾼다.
  - 위 단언 외의 기대값(입력란 종류, 오류 문구 등)은 바꾸지 않는다.
- 새 의존성을 추가하지 않는다.
- SPEC에 없는 동작은 추측하지 말고, 맥락과 선택지를 적어 객관식으로 묻는다.
- 같은 실패가 3회차를 넘거나 작업이 2시간을 넘으면 멈추고 보고한다. 작업 트리는 그대로 둔다.

[참조]
- AGENTS.md 3~6절, 화면 규칙 91~94행, 세션 규칙과 공통 파일 97~105행
- docs/SPEC.md `input_format`과 `placeholder` 161행, `question_description` 166행, AC73, AC68
- src/passform/schemas/form.schema.json의 `input_format.placeholder`
- frontend/src/의 pages/ResponsePage.tsx(`renderInput`, `renderQuestion`), response/answerCheck.ts(`formatHint`, `problemMessage`), 응답 화면 CSS
- frontend/src/pages/ResponsePage.screen.test.tsx, ResponsePage.test.tsx, response/Autofill.test.tsx, pages/ResponseEdit.live.test.tsx
- docs/prompts/delegation_response_screen.md(AC73 화면 처리)

[완료 기준]
- AGENTS.md 6절을 충족한다.
- 화면 테스트(Vitest):
  - 형식별 입력란의 `placeholder`가 `formatHint` 문구다. 잘리는 형식으로 정한 것은 예시 부분만이다. 제작자 `placeholder`가 있으면 그것이다.
  - 문항 아래의 별도 안내 문단(`hint-{id}`)이 없다.
  - 형식 없는 문항에는 placeholder가 없다(지금과 같음).
  - 형식 오류 문구에 안내가 그대로 들어 있다.
  - 응답 화면, 제출 후 수정 화면, 빌더 미리보기에 같은 규칙이 적용된다.
  - 기존 테스트가 위 예외 외에는 그대로 통과한다.
- 포커스 때 사라지는 것은 jsdom이 재지 못하므로 사람 확인으로 한다.
- 실제 화면 확인을 사람에게 요청하고 결과를 기록한다. 요청은 한 번에 끝나도록 아래 흐름 그대로, 누를 곳까지 적어서 보낸다. 시험 데이터 스크립트는 에이전트가 준비하되 승인 없이 개발 DB에 실행하지 않는다.
  1. 이름·휴대전화·이메일·날짜 형식 문항이 있는 폼을 공유 링크로 연다. 각 입력란 안에 안내가 보이고, 파일 첨부 아래에 안내 문단이 없는지 본다.
  2. 휴대전화 입력란을 누른다. 아무것도 쓰기 전에 안내가 사라지는지 본다. 빈칸으로 두고 다른 곳을 누르면 안내가 다시 보이는지 본다.
  3. 휴대전화에 `0101234`를 쓰고 [다음] 또는 [제출]을 누른다. 문항 아래 오류에 예시가 보이는지 본다.
  4. 로그인해 [내 정보로 채우기]를 누른다. 채워진 칸에는 안내가 보이지 않는지 본다.
  5. 360px 폭에서 1단계를 다시 보고, 안내 문구가 잘리는 칸이 없는지(긴 문구는 예시만 보이는지) 본다.
- 결함 주입: 아래를 하나씩 넣었을 때 테스트가 실패해야 한다. 기대한 테스트가 실제로 실행됐는지 Vitest 결과로 확인하고, 실행되지 않았으면 판정 무효로 다시 돌린다. 주입은 커밋하지 않고, 항목마다 되돌린다.
  - 안내 문단을 지우지 않는다
  - placeholder에 안내를 넣지 않는다
  - 잘리는 형식으로 정한 것에 문장 전체를 넣는다
  - 제작자 `placeholder`를 무시한다
  - 형식 없는 문항에도 placeholder를 넣는다
  - 형식 오류 문구에서 안내를 뺀다
  - 수정 화면에는 적용하지 않는다
- 위임 기록을 docs/prompts/delegation_format_hint_placeholder.md에 남긴다. 넣을 것: 이 프롬프트 원본, 회차 표, 결함 주입 결과, 사람이 정한 답, 바꾼 기존 테스트 단언.

[범위 밖]
- 서버의 형식 검증(AC73)
- 빌더에서 제작자가 `placeholder`를 입력하는 기능
- 프로필 화면 등 응답 화면 밖의 입력란
- 디자인 체계 전반(#106)

[깃허브 규칙]
- 사람의 명시적 승인을 받은 뒤에만 이슈 생성·커밋·push·PR을 한다. 이 작업의 이슈가 없으면 이슈 초안을 보여 승인받아 만들고 그 번호를 쓴다. 승인 요청에는 `git status --short`, `git diff --stat`, 핵심 diff, 테스트 집계, 제안 커밋 메시지를 보여 준다. 커밋 승인은 push 승인이 아니다.
- 커밋은 AGENTS.md 5절 협업 항목과 README.md를 따른다. `git add`는 경로를 명시한다.
- PR 제목: `[feat] 형식 안내를 입력란 안으로 (#이슈번호)`. 본문은 README.md를 따른다.
- 리뷰어는 gh pr create --reviewer ye-nni로 지정한다.
- main에 직접 push하거나 머지하지 않는다.

[보고]
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 테이블 표 / 추가한 의존성과 이유 / 지시와 다르게 한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
- 테스트마다 통과 이유를 코드 위치와 함께 한 줄로 적는다.
- 바꾼 기존 테스트 단언을 전후로 적는다.
````
