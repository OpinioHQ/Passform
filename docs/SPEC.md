# 제품 스펙 (SDD) — Passform

> 스펙 주도 개발(SDD). 이 문서가 "무엇을 만들지"의 단일 진실 공급원이다.
> 범위는 **MVP**(4절 포함)다. 캡디 추가 개발(시간이 남으면, 우선순위 순)과 추후 개발도 4절에 적어 두고, 착수할 때 AC를 확정한다.
> AC 번호는 한 번 붙이면 바꾸지 않는다. 기능이 늘면 뒤 번호를 이어 붙인다 — 골든 케이스와 이슈가 번호로 이 문서를 가리키기 때문이다.

## 1. 문제

소속 때문에 폼에 반복해서 응답하는 대학생이, 같은 기본 정보를 폼마다 다시 쓰고, 받은 폼의 마감을 놓치며, 제출한 내용을 다시 볼 수 없어 입력 수고와 제출 실패, 제출 후 불안을 겪는다. 2차로, 폼을 만드는 운영진은 모인 응답을 확인·정리하려고 응답 화면과 엑셀 시트를 오간다. (상세: `docs/PROBLEM.md`)

## 2. 타깃 사용자

- 1차: 소속 때문에 폼에 반복해서 응답하는 대학생(응답자)
- 2차: 그런 폼을 만들어 응답을 모으는 대학생 운영진(제작자)
- 계정은 하나다. 제작자·응답자는 사용자 종류가 아니라 폼과의 관계(`Form.owner_id`)이고, 화면만 오른쪽 위 스위치로 응답자 모드·제작자 모드를 바꾼다.
- 비대상: 직장 내 만족도 조사 응답자, 보상형 설문 응답자, 통계 분석이 목적인 연구 설문 작성자, 입금 대조가 목적인 총무

## 3. 핵심 기능 (한 문장)

응답자는 **한 번 등록한 프로필로 폼을 채우고, 저장한 폼의 마감과 제출 내역을 한곳에서 관리**하며, 제작자는 기본·빠른 모드로 폼을 만들어 링크·QR로 배포하고 **모인 응답을 자연어로 물어 어떤 문항·선택지로 셌는지와 함께** 결과를 받아 AI에 넣기 좋은 형식으로 내보낸다. 확정할 수 없는 요청은 추측하지 않고 되묻는다.

> 조건 → 문항·선택지 매칭을 규칙으로 할지 LLM으로 할지는 스파이크 `docs/spikes/condition_matching.md`의 결과로 확정한다(AC21).
> 폼 정의(섹션·문항·분기·설정)는 하나의 JSON 구조 `src/passform/schemas/form.schema.json`을 정본으로 한다. 빌더 두 모드의 저장, 복사, 내보내기, AI 결과 탐색의 문항 정보, 이후 구글폼 불러오기가 모두 이 구조를 읽고 쓴다. DB에 이 구조를 통째로 저장할지(JSONB)는 아키텍처(강의 6)에서 정한다.

## 4. 범위

### 포함 — MVP

| 기능 | 이 스펙에서의 동작 | 근거 |
|---|---|---|
| 소셜 로그인 | OAuth 2.0 소셜 로그인. 로그인하지 않아도 배포된 폼에는 응답할 수 있다 | (전제 기능) |
| 화면 모드 스위치 | 오른쪽 위 스위치로 응답자 모드·제작자 모드 전환, 마지막 모드를 계정에 저장. 권한은 바꾸지 않는다 | (계획 — 인터뷰 근거 없음) |
| 다중 프로필 | 학교·직장·동아리 등 프로필 여러 개 생성·수정·삭제, 허용 항목(주소·생년월일 포함)만 | 로그 3(기본정보 반복 입력) + 계획서(다중 프로필) |
| 자동채우기 | 제작자가 응답자 본인 정보라고 확인한 문항에만 `Question.profile_key`를 설정한다. 응답자는 [내 정보로 채우기]로 고른 프로필의 값을 제안받아 확인·수정한 뒤 제출한다 | 로그 3(기본정보 반복 입력·자동 입력 요구) |
| 개인정보 동의 템플릿 | 모든 배포 폼에서 `input_format`의 입력 범주와 일반 문항 응답 내용을 수집 항목으로 알리고, 목적·보유 기간·동의 거부 안내를 보여 준다. 동의해야 제출할 수 있다 | 로그 4, 9; 모든 폼 적용은 제품 정책 |
| 설문지 저장 | 응답자: 받은 폼을 내 폼함에 저장. 제작자: 만들던 폼 초안 저장, 내가 만든 폼 목록, 폼 복사 | 로그 2·7(마감 놓침), 관찰 1(이전 폼 참고 4회) |
| 작성 중 자동 임시 저장 | 응답자가 폼에 작성한 답과 제작자가 만들던 초안을 입력 중 자동으로 저장한다. 화면을 나갔다 돌아와도 이어서 작성할 수 있고, 응답 초안은 제출 전까지 제작자의 결과에 나타나지 않는다 | 로그 4·8·9(장문 설문 중단), 관찰 2; 제작자 자동 저장은 제품 정책 |
| 마감기한 알림 | 저장했지만 제출하지 않은 폼의 마감 전 이메일 + 사이트 내 알림(사용자가 끌 수 있음). 카카오톡은 가능하면 추가(AC59, 별도 AC) | 로그 2·7(마감 놓침) |
| 제출 이력 | 제출한 응답 다시 보기. 제작자는 응답자의 수정·취소 허용 여부를 각각 정한다. 허용된 수정은 기존 응답을 덮어쓰고, 폼이 바뀌어도 제출 당시 문항을 기준으로 이력을 보여 준다. 수정·취소는 폼이 열려 있고 마감 전일 때만 가능 | 로그 3, 4, 6; 권한 분리·폼 버전 보존은 제품 정책 |
| 파일 첨부·업로드 | 제작자는 폼 설명란과 문항에 이미지·PDF·MP4를 첨부한다. 응답 화면에서 이미지만 바로 보이고 PDF·MP4는 다운로드해 본다. 응답자는 문항별 답에 파일을 첨부할 수 있고, 문항 하나의 답에 첨부한 파일 합계는 25MB 이하 | 로그 6(문항의 시각 자료), 로그 1·2(응답 파일 제출 제약) |
| 클라우드 저장 공간·정리 | 계정마다 500MB. 내가 만든 폼과 받은 응답의 사용량을 확인한다. 정리는 폼 삭제, 그 폼의 받은 응답 전체 삭제, 그 폼의 응답 첨부 전체 삭제 중에서 고른다. 개별 응답이나 특정 응답자의 첨부만 삭제할 수는 없다. 폼 삭제 전에는 받은 응답의 내보내기 여부를 묻는다 | (제품 정책) |
| 폼 빌더 — 기본 모드 | 구글폼과 비슷한 화면. 섹션과 문항 각각 추가·수정·삭제·복제·순서 변경, 문항 유형 5종, 입력 형식, 본인 정보 여부, 필수·설명, 미리보기, 상세 분기 설정 | 로그 1, 관찰 1; 입력 형식은 로그 6·8, 관찰 1·2 |
| 폼 빌더 — 빠른 모드(선택형 제작 흐름) | 제작 목적·응답 대상 선택 → 그 맥락에 맞는 추천 문항 선택 → 직접 질문 추가 → 검토·미리보기. 선택·직접 추가한 문항 모두 입력 형식과 본인 정보 여부를 설정할 수 있고 기본 모드와 같은 폼 정의로 저장한다 | 로그 1(다수 문항 제작 부담), 로그 3(기본문항 자동 생성 요구), 관찰 1(이전 폼 참고); 목적·대상별 추천은 설계 가설 |
| 모바일 웹 응답·제작 | 모바일 브라우저에서 프로필·자동채우기, 내 폼함·마감·제출 이력, 폼 응답·파일 첨부를 사용한다. 폼 제작은 빠른 모드로 생성·저장·배포하고, 기본 모드 제작은 데스크톱 웹에서 제공한다 | 로그 3(모바일 제작 제약); 모바일 응답 범위는 제품 정책 |
| 폼 설정 | 응답 받기 켜기·끄기(= `status` open/closed), 마감일, 응답 수정 허용·취소 허용 각각 설정, 응답 화면 보기 방식. 응답 제출 횟수는 제한하지 않음 | 관찰 1(폼 설정 사용), 로그 2(마감), 로그 3(응답 수정); 취소 허용 분리는 제품 정책 |
| 조건분기 | 문항별 보기에서는 각 문항 뒤에 뒤쪽 문항·섹션이나 제출로 분기한다. 섹션별 보기에서는 섹션의 모든 질문에 답한 뒤 뒤쪽 섹션이나 제출로만 분기한다. 여러 답의 조합을 조건으로 쓸 수 있고, 제작자에게 분기 흐름을 그림으로 보여 준다 | 로그 3(로직 시각화 불편) |
| 응답 화면 보기 방식 | 제작자가 기본 시작 방식을 정한다. 분기가 없는 폼은 응답자가 「한 질문씩」(한 화면에 질문 하나)과 「전체 질문 보기」(한 페이지에 모든 섹션·질문) 사이를 전환할 수 있다. 분기가 있는 폼은 제작자가 정한 「한 질문씩」 또는 「섹션별 보기」(한 화면에 한 섹션의 모든 질문)로만 응답하고 전환 버튼은 보이지 않는다. 작성 중 현재 단계와 확인 가능한 남은 분량을 표시한다 | 로그 7(긴 페이지 스크롤 부담), 로그 4·8·9(장문 설문 중단), 로그 4·9(전체 분량 파악 어려움) |
| 공유 링크·QR코드 | 배포한 폼의 공유 링크와 그 링크를 담은 QR 이미지 | (기본 폼 기능 / 계획서 — 인터뷰 근거 없음) |
| 송금·결제 링크 연결 | 제작자가 넣은 송금 링크를 응답 화면에 표시, 응답자는 외부 앱으로 이동 | 로그 10, 관찰 2 |
| 결과 대시보드 | 폼 전체 제출 응답 수를 보여 주고, 문항·선택지 통계는 「통합 보기」와 「버전별 보기」 사이에서 전환한다. 통합 보기에서는 같은 문항·선택지만 합산하고 바뀐 항목은 버전 표시와 함께 구분한다 | 로그 1, 2, 관찰 1; 통합·버전 선택은 제품 정책 |
| AI 기반 대시보드 필터링·답변 | 자연어를 강의 3의 `RequestContext`(`conditions`·`match`·`operation`·`sort`)로 구조화한다. 해당 폼의 모든 제출 버전을 대상으로 조건에 맞는 응답의 `count`·`list`·`summarize`, 목록 정렬·되묻기를 제공한다. 각 버전의 제출 당시 문항으로 조건을 판정하고 판정 불가 응답 수를 알린다. 건수는 코드가 계산한다 | 로그 1·2, 관찰 1(결과 확인·정리 문제; 자연어 방식은 설계 가설); 전체 버전 검색은 제품 정책 |
| 내보내기(JSON·YAML·CSV) | 전체 응답 또는 AI 필터 결과를 내보낸다. 여러 버전의 응답은 출처 버전을 표시하고 JSON·YAML에는 각 버전의 폼 정의를 포함한다 | 로그 2(엑셀 연동 불편), 관찰 1(응답 탭·시트 병행); 버전 구분은 제품 정책 |

### 캡디 추가 개발 — 시간이 남으면, 이 순서로

| 순서 | 기능 | 계획 동작 | 근거 | AC |
|---|---|---|---|---|
| 1 | 캘린더 | 내 폼함에 저장한 폼의 마감일을 달력에 표시 | 로그 2·7(마감 놓침) | AC52 (초안) |
| 2 | AI 다듬기 | 장문 답의 글자 수 세기, 맞춤법 교정, 글 다듬기 제안 | 관찰 2(외부 창 6번), 로그 7, 10 | AC53~AC55 (초안) |
| 3 | 구글폼 불러오기 | 구글폼을 폼 정의(`form.schema.json`)로 변환해 가져오기 | 관찰 1(이전 구글폼 템플릿 4회 참고) | 착수 시 작성 |
| 4 | 협업 | 운영진 여러 명이 한 폼을 편집·결과 열람 | 로그 3(공동작업·공유), 관찰 1(드라이브 공유) | 착수 시 작성 |
| 5 | 이탈 구간 분석 | 작성 중 자동 임시 저장은 MVP에 포함한다. 섹션별 중단 위치를 제작자에게 집계해 보여 주는 분석은 이 단계에서 개발한다 | 로그 4·8·9(장문 설문 중단), 로그 6(제작자 이탈 우려) | 착수 시 작성 |

### 추후 개발 — 캡디 이후(시간이 남으면 시도)

앱, 공개 폼 목록 보기, 경품·보상 즉시 제공, 유료 결제, 송금·결제 서비스 연동(외부 결제 서비스 API를 부르는 것까지 — Passform이 직접 돈을 처리하지는 않는다), 자연어 기반 AI 폼 생성("~ 만들어줘"), AI 보고서 작성. 앱 구현은 추후 개발이며 캡디에서는 모바일 웹을 먼저 완성한다.

### 비포함 (위 어디에도 없는 것)

- 외부 폼(구글폼 등) 링크를 내 폼함에 저장 — Passform 폼만 저장한다
- 송금 완료 확인·입금 대조, 응답자의 '송금 완료' 체크
- 문항 유형 5종 밖의 별도 유형(선형 배율 등). 날짜 입력은 `short_answer`의 `input_format`으로 MVP에 포함한다
- 문항 문구를 읽고 프로필 항목을 추측하는 AI 매핑, 주민등록번호 등 민감정보의 프로필 저장
- AI 결과 탐색에서 주관식 답의 내용으로 거르는 조건, 한 조건이 여러 선택지를 묶는 표현(예: "공대"), 평균·비율 등 통계 연산, 그룹별 집계(`group_by`), 자연어로 폼 버전 범위를 지정하거나 버전별·문항별 통계를 요청하는 동작. 대시보드의 전체·버전별 문항 통계는 MVP에 포함한다
- 실제 은행 송금·결제 처리
- Google Sheets·Google Drive 자체 기능 구현
- 외부 AI 서비스 자체 구현
- 카카오톡 등 외부 서비스 자체 기능 구현

> 마지막 네 줄은 `docs/ontology.yaml`의 "범위 밖"과 같은 항목이다.
> 카카오톡 알림(AC59)을 시도했는데 안 되면 이 목록으로 옮기고 "시도 후 포기"로 남긴다.
> 작성 중 임시 저장은 MVP에 포함한다. 임시 저장된 답은 제출 응답 수·결과·AI 분석·내보내기에 포함하지 않는다. `abandoned` 판정과 이탈 구간 분석은 추가 개발 5에서 정한다.

## 5. 인터페이스

인증 구분: **공개**(로그인 불필요) / **로그인** / **제작자**(로그인 + 그 폼의 `owner_id`). 로그인이 필요한데 없으면 `401`, 남의 리소스는 `404`, 제작자 전용을 다른 사용자가 부르면 `403`. 화면 모드는 인증 구분에 영향을 주지 않는다.

웹과 향후 앱은 같은 폼 정의 JSON과 응답·제작 API를 사용한다. 제출 검증·분기·집계·권한 판정은 서버에서 수행하며, 모바일 웹 화면에만 있는 규칙으로 저장 형식이나 API 동작을 나누지 않는다. 캡디의 제공 화면은 반응형 웹이다.

### 계정·설정·프로필·자동채우기

| 엔드포인트 | 인증 | 입력 | 출력 |
|---|---|---|---|
| `GET /oauth2/authorization/{provider}` | 공개 | — | 소셜 로그인 후 세션 발급 |
| `GET /me` | 로그인 | — | `{user_id, name, email, ui_mode, deadline_reminder}` |
| `GET /me/storage` | 로그인 | — | `{used_bytes, limit_bytes: 500000000}` — 내가 만든 폼, 그 폼에 받은 제출 응답, 내 계정에 보관한 작성 중 응답의 사용량 |
| `PATCH /me/settings` | 로그인 | `{ui_mode?: "respondent" \| "creator", deadline_reminder?: boolean}` | `200` (기본값 `respondent`, `true`) |
| `GET /me/profiles` | 로그인 | — | `[{profile_id, label, personal_info}]` |
| `POST /me/profiles` · `PUT /me/profiles/{profile_id}` | 로그인 | `{label, personal_info}` | `201 {profile_id}` · `200` / `400 {error: "unsupported_key", keys: []}` |
| `DELETE /me/profiles/{profile_id}` | 로그인 | — | `204` |
| `POST /forms/{form_id}/autofill` | 로그인 | `{profile_id}` | `{answers: [{question_id, value}], unfilled: [question_id]}` — 저장·제출하지 않음 |

- `personal_info`의 키는 닫힌 목록이다: `name`, `student_id`, `department`, `phone_number`, `email`, `address`, `birth_date`.

### 폼 제작

| 엔드포인트 | 인증 | 입력 | 출력 |
|---|---|---|---|
| `GET /question-templates` | 로그인 | 쿼리 `purpose`, `audience`(빠른 모드에서 고른 제작 목적·응답 대상) | `[{template_id, question_note, question_type, options, input_format}]` — 두 선택에 맞는 추천 문항. 본인 정보 여부는 템플릿에서 미리 확정하지 않음 |
| `GET /consent-templates` | 로그인 | — | `[{consent_template_id, purpose, retention}]` |
| `POST /forms` | 로그인(만든 사람이 `owner_id`) | 폼 정의(아래) | `201 {form_id, status: "draft"}` / `400 {error: "schema_invalid" \| "invalid_payment_link"}` / `409 {error: "storage_quota_exceeded"}` |
| `GET /forms/{form_id}/definition` | 제작자 | — | 작업 중인 최신 폼 정의 전체(`form.schema.json` 그대로, 상태와 무관) + `{status, share_url, definition_version}`. `definition_version`은 마지막 배포 버전이며 첫 배포 전 초안에서는 `null` |
| `PATCH /forms/{form_id}` | 제작자 | 폼 정의의 최상위 키 일부 + `status` | `200 {status, share_url, definition_version}`(`definition_version`은 첫 배포 전 `null`) / `400 {error: "schema_invalid" \| "invalid_payment_link" \| "invalid_branch" \| "invalid_display_mode" \| "consent_template_required"}` / `409 {error: "invalid_transition" \| "storage_quota_exceeded"}` |
| `POST /forms/{form_id}/copy` | 제작자 | — | `201 {form_id, status: "draft"}` / `409 {error: "storage_quota_exceeded"}` |
| `DELETE /forms/{form_id}` | 제작자 | — | `204` — 폼 정의와 그 폼의 받은 응답·첨부 전체 삭제 |
| `GET /me/created-forms` | 로그인 | — | `[{form_id, title, status, deadline, share_url, response_count}]` |
| `POST /forms/{form_id}/attachments` | 제작자 | 폼 설명란 파일 1개(`multipart/form-data`) | `201 {attachment_id, filename, content_type, size_bytes}` / `400 {error: "invalid_file_type"}` / `409 {error: "storage_quota_exceeded"}` |
| `DELETE /forms/{form_id}/attachments/{attachment_id}` | 제작자 | — | `204` — 폼 설명란 첨부 목록에서 제거 |
| `POST /forms/{form_id}/questions/{question_id}/attachments` | 제작자 | 파일 1개(`multipart/form-data`) | `201 {attachment_id, filename, content_type, size_bytes}` / `400 {error: "invalid_file_type"}` / `409 {error: "storage_quota_exceeded"}` |
| `DELETE /forms/{form_id}/questions/{question_id}/attachments/{attachment_id}` | 제작자 | — | `204` — 문항의 첨부 목록에서 제거 |
| `GET /forms/{form_id}/branch-graph` | 제작자 | — | `{nodes: [{id, type: "section" \| "question" \| "submit"}], edges: [{from, to, kind: "rule" \| "default", rule_index?}]}` |
| `GET /forms/{form_id}/qr` | 제작자 | — | `image/png` (`share_url`을 담은 QR) / `409 {error: "not_open"}`(`share_url` 없음) |

폼 정의(`form.schema.json`의 요지; 아래는 분기 대상과 동의 템플릿이 아직 완성되지 않은 `draft` 예시):

```json
{
  "title": "", "note": "", "deadline": "2026-11-01T23:59:00+09:00",
  "settings": {
    "editable": false, "cancellable": false,
    "display": {"default_mode": "section_page"}
  },
  "payment_link": null, "consent_template_id": null, "note_attachments": [],
  "sections": [
    {"section_id": "s1", "title": "기본 정보", "questions": [
      {"question_id": "q1", "template_id": null, "question_note": "학과", "question_type": "dropdown",
       "options": ["소프트웨어학과", "컴퓨터공학과"], "required": true, "profile_key": "department", "attachments": [],
       "branch_rules": [
         {"when": {"match": "all", "conditions": [{"question_id": "q1", "option": "소프트웨어학과"}]},
          "go_to": {"section_id": "s3"}}
       ]}
    ]}
  ]
}
```

- `PATCH`는 보낸 최상위 키를 통째로 바꾼다(`sections`를 보내면 섹션 전체 교체). 검증은 바꾼 뒤의 전체 정의로 한다.
- 제작자가 `draft` 폼을 기본·빠른 모드에서 편집하면 입력 변경을 자동 저장한다. 처음 만드는 폼은 작업을 시작할 때 `POST /forms`로 제작자 소유 초안을 만들고, 이후 `PATCH /forms/{form_id}`로 현재 폼 정의를 저장한다. 저장된 초안은 다시 열어 이어서 편집할 수 있다. 자동 저장 중·완료·실패를 화면에 구분해 보여 주고, 저장이 끝나기 전에 화면을 나가려 하면 경고하며 실패한 변경을 저장된 것으로 표시하지 않는다. 자동 저장은 초안의 의미 검증이나 배포를 실행하지 않고 `definition_version`도 늘리지 않는다. `open`·`closed` 폼의 편집은 기존 배포 버전 규칙에 따라 제작자가 명시적으로 저장한다.
- 기본 모드에서 섹션과 문항의 추가·수정·삭제·복제·순서 변경은 현재 폼 정의 JSON을 편집한다. 문항 복제는 같은 폼 안에 새 `question_id`를 가진 문항을, 섹션 복제는 새 `section_id`와 각각 새 `question_id`를 가진 문항들을 만든다. 복제본의 문구·추가 설명·유형·선택지·필수 여부·입력 형식은 복사하지만 `branch_rules`는 비우고 `profile_key`는 `null`로 두어 분기와 응답자 본인 정보 여부를 제작자가 다시 설정한다. 원본은 바뀌지 않으며, 섹션·문항의 저장된 순서와 설정은 초안을 다시 열어도 유지된다. 폼 전체 복제는 별도의 `POST /forms/{form_id}/copy`로 처리한다.
- `definition_version`은 마지막으로 배포 검증을 통과한 폼 정의 버전이다. 첫 배포 전 `draft`에서는 `null`이며, 처음 `open`할 때 검증을 통과한 정의를 버전 1로 저장한다. `open → draft`로 되돌린 뒤의 작업 초안도 새 버전이 아니다. `draft`의 폼 정의·첨부를 수정하거나 불완전한 상태로 저장해도 번호는 그대로 둔다. 다시 `open`할 때 의미 검증을 통과하면 아래에 열거한 버전 대상 정의가 마지막 배포본과 다른 경우에만 다음 번호로 저장하고, 같으면 기존 번호를 유지한다. 실패하면 `draft` 상태와 작업 초안을 유지하며 마지막 배포 정의·버전은 바꾸지 않는다.
- `open`·`closed` 상태에서 제목·설명·섹션·문항·선택지·순서·분기·보기 방식·동의 문구·설명란/문항 첨부 등 **응답자가 보는 폼 정의**가 바뀌면, 변경 요청 전체를 검증한 뒤 다음 버전으로 저장한다. 별도 첨부 추가·삭제 API도 같은 규칙을 따른다. 이전 배포 버전은 불변으로 보존하고 각 `Response`는 제출할 때 사용한 `form_version`을 가리킨다. `status`·`deadline`·`settings.editable`·`settings.cancellable`처럼 응답 가능 여부와 수정·취소 권한을 정하는 운영 설정은 현재 값을 적용하며, 이 설정만 바꿔도 폼 정의 버전을 늘리지는 않는다. 폼 정의를 바꾸는 `PATCH`를 응답이 있다는 이유만으로 막지 않는다.
- 제출 버전의 폼 제목·문항 문구·유형·선택지·순서·분기·입력 형식·동의 문구는 과거 응답을 읽고 수정할 수 있도록 보존한다. 버전 스냅샷에 남은 과거 운영 설정은 현재 수정·취소 권한이나 마감 판정에 쓰지 않는다. 응답 이력의 `form_title`과 문항 문구도 제출 버전에서 읽는다.
- `Question.input_format`은 선택적인 입력 형식 객체다. MVP의 `kind`는 `name`, `mobile_phone`, `landline_phone`, `address`, `student_id`, `email`, `date`다. 이 형식은 `short_answer` 문항에 적용하며, 형식을 지정하지 않은 문항도 만들 수 있다. 안내용 `placeholder`는 검증 기준이 아니다. 제출과 제출 후 수정에 같은 규칙을 적용하고, 형식이 있는 필수 문항의 값이 앞뒤 공백을 제거한 뒤 비어 있으면 기존 필수 문항 검증으로 거부한다. 주소는 한 칸으로 받는다. 일반적인 예약 날짜는 응답자 프로필의 `birth_date`와 다르다.
  - `name`·`address`·`student_id`: 앞뒤 공백을 제거해 저장한다. 필수 여부 외의 별도 패턴 검증은 하지 않는다.
  - `mobile_phone`·`landline_phone`: 입력의 공백·하이픈을 제거한 뒤 숫자만 남아야 한다. 휴대전화는 10~11자리, 집전화는 9~11자리면 허용하고 숫자만 저장한다.
  - `email`: 앞뒤 공백을 제거하고, 공백 없이 `@`가 정확히 하나 있으며 `@` 앞이 비어 있지 않고 뒤에는 비어 있지 않은 부분이 점(`.`)으로 구분된 도메인만 허용한다. 제거한 앞뒤 공백 외의 글자는 그대로 저장한다.
  - `date`: `YYYY-MM-DD` 형태의 실제 날짜만 허용하고 그 형식으로 저장한다. 날짜는 별도 문항 유형이 아니라 `short_answer`의 입력 형식이다.
- `Question.question_note`는 문항 문구이고, 폼 정의 JSON의 선택적 구현 필드 `question_description`은 응답자에게 보여 주는 문항별 보조 설명이다. 기본 모드는 `multiple_choice`(단일 선택), `dropdown`(단일 선택), `checkbox`(복수 선택), `short_answer`, `long_answer`를 모두 편집할 수 있다. 선택형 문항은 선택지, 모든 문항은 문구·설명·필수 여부를 저장하고 응답 화면에 적용한다.
- `Question.profile_key`는 동의서 생성 기준이 아니라 응답자 **본인 정보** 자동채우기 키다. 기본·빠른 모드 모두 입력 형식과 문항에 맞춰 제작자에게 이름은 “응답자 본인의 이름인가요?”, 주소는 “응답자 본인의 주소인가요?”, 날짜는 “응답자의 생년월일인가요?”처럼 묻는다. 기본값은 “아니요”(`profile_key: null`)이고, “예”로 확인한 경우에만 각각 `name`, `address`, `birth_date` 등 해당 프로필 키를 설정한다. 문항 문구나 입력 형식을 바꾸면 기존 본인 정보 선택을 지우고 다시 확인한다. 빠른 모드의 추천 문항에도 같은 규칙을 적용하며, 제작자 화면에는 `profile_key`라는 기술 용어를 노출하지 않는다.
- `Form.note`는 설명 문구 그대로 두고, 폼 정의 JSON의 구현 필드 `note_attachments`에 설명란 첨부 파일을 둔다. `Form.attachments`라는 온톨로지 속성은 새로 만들지 않는다. `Question.attachments`는 문항 첨부다. 두 첨부 목록에는 `attachment_id`, `filename`, `content_type`, `size_bytes`를 담는다. 제작자의 첨부 파일에는 별도의 파일당·폼당·문항당 용량 제한이 없으며, 계정의 500MB 한도만 적용한다. 별도 첨부 API는 성공할 때만 현재 작업 정의의 첨부 목록을 바꾼다. 배포된 `open`·`closed` 폼에서는 첨부 추가·제거와 새 불변 버전 저장을 한 번의 성공으로 처리하고, `draft`에서는 작업 초안만 바꾼다. 파일 형식·용량·정의 검증이나 저장이 실패하면 첨부 목록·버전·사용량을 바꾸지 않고 임시 파일도 남기지 않는다.
- 과거 폼 버전의 설명란·문항에 연결된 파일은 새 버전에서 첨부를 제거해도 해당 버전의 원본을 계속 제공한다. 과거 버전이 참조하는 동안 제작자 500MB 사용량에 포함하고, 폼 전체 삭제 시 버전별 첨부 원본도 삭제한다. 과거 버전 파일은 제작자와 그 버전의 응답을 제출한 로그인 사용자만 열람할 수 있다.
- 사용자의 클라우드 용량은 500MB(500,000,000바이트)다. 내가 만든 폼 정의·첨부와 그 폼에 받은 응답 내용·첨부의 저장 크기를 `used_bytes`로 합산한다. 다른 사람의 폼에 내가 제출한 응답 파일은 폼 제작자의 용량에만 센다. 비로그인 응답의 파일도 폼 제작자의 용량에 센다. 제작자가 폼의 받은 응답 전체를 삭제한 뒤 응답자에게 남는 텍스트 제출 이력은 제작자의 사용량에서 제외한다. 새 저장·수정·응답 수신으로 한도를 넘으면 아무것도 바꾸지 않고 `409 storage_quota_exceeded`를 반환하며, 응답자에게는 제작자 저장 공간이 부족하다고 안내한다.
- 로그인 응답자가 계정에 자동 저장한 **미제출 답**은 그 응답자 자신의 500MB 사용량에 포함하고 제작자의 사용량에는 포함하지 않는다. 제출에 성공하면 미제출 답의 응답자 사용량을 해제하고 받은 제출 응답을 제작자 사용량에 계산한다. 제출 전 첨부 원본을 같은 기기의 브라우저에만 보관한 동안에는 서버 사용량에 포함하지 않고, 제출할 때 제작자의 남은 용량과 문항별 25MB 한도를 검사한다.
- 제작자가 폼 삭제를 누르면, 화면은 먼저 현재 받은 응답을 JSON·YAML·CSV로 내보낼지 묻는다. 내보내기를 선택한 경우 파일 생성이 성공한 뒤 삭제할 수 있고, 내보내지 않기를 명시적으로 선택하면 바로 삭제할 수 있다. 내보내기가 실패하면 자동으로 삭제하지 않는다. 이 형식의 내보내기에는 첨부 파일 원본이 포함되지 않음을 삭제 전에 알린다.
- 검증은 두 단계다. 형식 검증(`form.schema.json`, `https` 링크)은 항상 한다. 의미 검증(분기, 보기 방식, 동의 템플릿)은 `draft`에서 `open`으로 바꿀 때와 `open`·`closed` 폼을 저장할 때 한다 — 만들던 초안은 미완성이어도 저장된다.
- `status` 전이: `draft → open`, `open ↔ closed`, 그리고 응답이 0건일 때만 `open → draft`. 그 밖의 전이는 `409 invalid_transition`. `share_url`은 처음 `open`할 때 한 번 만들고 다시 열어도 바꾸지 않는다. 응답 받기 끄기는 `closed`다.
- `display.default_mode`는 제작자가 정하는 필수 시작값이다. 분기 규칙이 없으면 `one_question` | `all_questions` 중에서 고르고, 응답자는 「한 질문씩」과 「전체 질문 보기」 사이를 언제든 전환할 수 있다. 분기 규칙이 하나라도 있으면 `one_question` | `section_page` 중에서 고르고, 응답자에게 전환 버튼을 보여 주지 않는다. 응답 도중 방식을 바꿔도 작성한 답은 유지하고 보던 문항으로 이동하며, 전환 때문에 시작 화면으로 되돌아가지 않는다. `one_question`으로 처음 폼에 들어오면 제목·설명·설명란 첨부와 시작 버튼을 먼저 보여 주고, 버튼을 누르면 첫 문항을 보여 준다. `all_questions`는 제목·설명·설명란 첨부 아래에 모든 섹션의 제목과 문항을 한 페이지에 보여 준다. `section_page`는 제목·설명·설명란 첨부와 첫 섹션의 모든 문항을 첫 화면에 보여 준다.
- `branch_rules`는 위에서부터 검사해 처음 맞는 규칙으로 이동한다. `one_question`에서는 각 문항의 답을 확정하고 다음 화면으로 넘어갈 때 규칙을 적용하며, 뒤쪽 `question_id`, `section_id`, `"submit"`으로 이동할 수 있다. 규칙이 맞지 않으면 다음 문항(섹션 끝이면 다음 섹션)으로 간다. `section_page`에서는 한 섹션의 문항을 모두 보여 주고 다음 화면으로 넘어갈 때만 규칙을 적용한다. 규칙은 그 섹션의 마지막 문항에만 두고 뒤쪽 `section_id` 또는 `"submit"`으로만 이동할 수 있다. 규칙이 맞지 않으면 다음 섹션으로 간다. 두 방식 모두 `conditions`는 규칙이 붙은 문항과 그보다 앞의 선택형 문항만, 그 문항에 실제로 있는 선택지로 가리킬 수 있다. `checkbox` 조건은 "그 선택지를 포함함"이다.
- 빠른 모드는 제작 목적(예: 신청·설문조사·예약·퀴즈)과 응답 대상(예: 고등학생·대학생·직장인)을 먼저 고르게 한다. 이 둘에 맞춰 추천 문항을 제시하고, 제작자는 필요한 문항을 고른 뒤 직접 질문을 추가하고 검토·미리보기한다. 예를 들어 예약을 고르면 “예약자 성함”, “예약 날짜” 같은 문항을 추천한다. 예약 관리나 퀴즈 자동 채점 기능을 뜻하지 않는다. 추천 문항은 `template_id`의 문구·유형·선택지·`input_format`을 복사하되 `profile_key`는 `null`로 시작해 제작자가 본인 정보 여부를 확인한다. 직접 만든 문항에도 동일한 입력 형식·본인 정보 설정을 제공한다. 두 모드는 같은 폼 정의를 주고받는다.
- 기본·빠른 모드의 미리보기는 저장 전의 현재 편집 내용을 응답자 화면 방식으로 보여 준다. 분기 규칙이 있으면 같은 경로 계산 규칙으로 이동을 확인하고, 미리보기의 답 입력·제출 조작은 실제 `Response`나 결과 집계를 만들지 않는다. 미리보기에서 돌아와도 작업 중인 폼 정의는 유지된다.
- `deadline`은 선택이다. ISO 8601(타임존 포함)로 받아 UTC로 저장하고, 화면에는 Asia/Seoul로 보여 준다.

### 응답·내 폼함·알림

| 엔드포인트 | 인증 | 입력 | 출력 |
|---|---|---|---|
| `GET /f/{slug}` · `GET /forms/{form_id}` | 공개 | — | `{form_id, definition_version, title, note, note_attachments, deadline, sections, display: {default_mode, allowed_modes}, payment_link, consent: {items, purpose, retention, refusal_notice}}` — 최신 버전. 분기 규칙이 없으면 `allowed_modes: ["one_question", "all_questions"]`, 있으면 `[default_mode]` / `404`(초안·삭제된 폼) / `409 {error: "form_closed"}` |
| `POST /forms/{form_id}/responses` | 공개 | 파일 없으면 JSON `{form_version, answers: [{question_id, value}], consent_agreed: true, draft_response_id?}`; 파일이 있으면 같은 값의 JSON 부분과 각 파일의 `question_id`를 담은 `multipart/form-data`. `draft_response_id`는 로그인한 본인의 작성 중 응답에만 사용 | `201 {response_id, form_version, status: "submitted", content, attachments, submitted_at, updated_at: null}` / `400 {error: "consent_required" \| "required_missing" \| "invalid_input_format_value" \| "invalid_file_type", question_ids?}` / `413 {error: "file_too_large"}`(문항별 25MB 초과) / `404`(삭제된 폼 또는 본인 소유가 아닌 초안 ID) / `409 {error: "form_closed" \| "form_version_changed" \| "storage_quota_exceeded"}` |
| `GET /me/forms/{form_id}/response-draft` | 로그인 | — | `{response_id, form_version, form_definition, answers: [{question_id, value}], saved_at}` — 작성 시작 버전의 문항을 포함한 본인의 작성 중 답만 / `404`(초안 없음) |
| `PUT /me/forms/{form_id}/response-draft` | 로그인 | `{form_version, answers: [{question_id, value}]}` — 미완성 답 허용 | `200` 또는 첫 저장 `201 {response_id, status: "in_progress", saved_at}` / `409 {error: "form_closed" \| "form_version_changed" \| "storage_quota_exceeded"}` |
| `DELETE /me/forms/{form_id}/response-draft` | 로그인 | — | `204` — 본인의 미제출 답만 지움 |
| `GET /me/responses` | 로그인 | — | `[{response_id, form_id, form_title, form_version, submitted_at, updated_at, editable, cancellable}]` — 제출 이력만, 작성 중 응답 제외 |
| `GET /me/responses/{response_id}` | 로그인 | — | `{form_title, form_version, form_definition, content: [{question_id, question_note, value}], attachments, submitted_at, updated_at, editable, cancellable}` — 문항·문구는 제출 버전 기준. 폼·받은 응답 전체 삭제로 조회 전용이 되면 `form_definition: null` |
| `PATCH /me/responses/{response_id}` | 로그인 | `{answers, attachments?}`(새 파일이 있으면 각 파일의 `question_id`를 담은 `multipart/form-data`) | `200 {response_id, form_version, content, attachments, submitted_at, updated_at}` / `400 {error: "required_missing" \| "invalid_input_format_value" \| "invalid_file_type"}` / `413 {error: "file_too_large"}` / `409 {error: "history_only" \| "not_editable" \| "form_closed" \| "storage_quota_exceeded"}` |
| `DELETE /me/responses/{response_id}` | 로그인 | — | `204` / `409 {error: "history_only" \| "not_cancellable" \| "form_closed"}` |
| `GET /forms/{form_id}/attachments/{attachment_id}` | 현재 버전의 `open` 폼은 공개; 제작자와 해당 버전의 응답을 제출한 로그인 사용자는 과거·마감 버전도 열람 | — | 폼 안내에 첨부된 파일. 초안은 제작자만 열람 |
| `GET /forms/{form_id}/questions/{question_id}/attachments/{attachment_id}` | 현재 버전의 `open` 폼은 공개; 제작자와 해당 버전의 응답을 제출한 로그인 사용자는 과거·마감 버전도 열람 | — | 문항에 첨부된 파일. 초안은 제작자만 열람 |
| `GET /forms/{form_id}/responses/{response_id}/attachments/{attachment_id}` | 제작자 또는 그 응답을 제출한 로그인 사용자 | — | 첨부 파일 / `410 {error: "attachment_removed"}`(첨부 원본이 삭제된 경우) |
| `POST /me/saved-forms` | 로그인 | `{form_id}` | `201` (이미 저장했으면 `200`, 중복 저장 없음) / `404`(남의 초안) |
| `GET /me/forms` | 로그인 | — | `[{form_id, title, deadline, state: "saved" \| "submitted", has_draft: boolean}]` — 작성 중인 폼도 내 폼함에서 다시 열 수 있음 |
| `GET /me/notifications` | 로그인 | — | `[{notification_id, form_id, type: "deadline", created_at, read}]` |
| `PATCH /me/notifications/{notification_id}` | 로그인 | `{read: true}` | `200` |

- `share_url`은 `https://<도메인>/f/{slug}`이고, 폼을 처음 `open`할 때 만들어진다. QR은 이 URL을 담는다.
- 응답자가 문항 답을 바꾸면 작성 중 내용을 자동 저장하고 저장 중·완료·실패를 화면에 표시한다. 저장이 끝나기 전에 화면을 나가려 하면 경고한다. **비로그인** 응답의 답·작성 시작 버전의 폼 정의·제출 전 첨부 파일은 현재 브라우저의 지속 저장소에만 보관해 같은 기기·브라우저에서 다시 열 때 복원한다. **로그인** 응답의 답은 본인만 접근할 수 있는 `Response.status: in_progress`로 계정에 자동 저장하며, 다른 기기에서도 이어 쓸 수 있다. 로그인 응답의 제출 전 첨부 파일은 현재 브라우저에만 보관하므로 다른 기기에서 이어 쓸 때는 파일을 다시 선택하라고 안내한다. 브라우저에 보관하는 로그인 사용자의 파일은 계정과 폼·응답별로 구분해 다른 계정으로 로그인해도 보이지 않게 한다. 브라우저의 저장 내용을 지우거나 다른 브라우저로 접속하면 그곳에만 둔 초안과 첨부 파일은 복원할 수 없다고 표시한다.
- 로그인 응답에는 폼당 동시에 작성 중인 응답을 하나만 두고, 답을 처음 입력해 계정 초안을 만들 때 그 폼을 내 폼함에도 저장한다. 이미 제출한 이력이 있어도 새 응답 초안을 만들 수 있고, 내 폼함의 `state`는 기존 제출 여부를 따르되 `has_draft`로 작성 중 여부를 별도로 보여 준다. 비로그인 초안은 내 폼함이나 계정의 제출 이력에 나타나지 않는다. 자동 저장 실패는 성공한 척하지 않고 경고하며, 마지막으로 저장된 내용은 유지한다.
- 미완성 응답 초안에는 필수 문항·형식·분기 완성 여부를 제출 기준으로 검사하지 않는다. 로그인 초안의 `submitted_at`은 `null`이며, 제출 후 응답 수정 시각인 `updated_at`도 아직 `null`이다. 초안은 제작자에게 보이지 않고 `Result.response_count`·대시보드·AI 분석·내보내기·제출 이력에 포함되지 않는다. 폼 동의는 응답을 제작자에게 **제출할 때** 확인한다. 자동 임시 저장만으로 동의하거나 제출한 것으로 처리하지 않는다.
- 응답 초안의 `form_version`은 작성하기 시작한 배포 버전이다. 제작자가 새 버전을 배포하면 기존 초안을 자동으로 새 문항에 끼워 넣거나 제출하지 않고, 저장된 답을 기존 버전 문항과 함께 보여 주며 기존 초안을 지우고 최신 폼으로 새로 시작할 선택을 제공한다. 응답자는 작성 중 초안을 직접 지울 수도 있다. 폼이 닫혔거나 마감되면 초안은 보존하되 제출은 막는다. 폼 자체를 삭제하면 그 폼의 계정 초안을 지우고, 브라우저 초안은 해당 폼에 다시 접근해 삭제 상태를 확인할 때 지운다. 제작자가 **받은 응답 전체**를 삭제하는 경우에는 아직 제출되지 않은 응답자 초안을 지우지 않는다.
- 새 응답은 공개 조회에서 받은 최신 `definition_version`을 `form_version`으로 보낸다. 그 사이 제작자가 응답 화면의 폼 정의를 바꿨다면 서버는 `409 form_version_changed`를 반환하고 응답·파일을 저장하지 않는다. 화면은 최신 폼을 다시 열어 응답하도록 안내한다. 이미 제출된 응답을 수정할 때에는 현재 폼 버전 대신 그 응답의 불변 제출 버전으로 문항·분기·필수값·입력 형식을 검증한다.
- 배포된 모든 폼은 동의 템플릿을 적용한다. 공개 조회의 `consent.items`는 문항의 `input_format`이 있으면 그 넓은 범주(예: 본인·부모님 이름 모두 “이름”), 없으면 “문항별 응답 내용”으로 만든다. 같은 범주는 중복 표시하지 않는다. `profile_key` 유무는 항목 생성이나 동의 필수 여부에 영향을 주지 않는다. `purpose`, `retention`은 제작자가 선택한 템플릿에서, 동의 거부 안내는 기본 문구에서 가져온다. 동의하지 않은 제출은 `submitted` 응답이나 서버 첨부를 만들지 않고 기존 작성 중 초안은 유지한다.
- 제출할 때는 최신 폼 버전·공개 상태·마감·폼 동의·분기 경로·필수 문항·입력 형식·파일 종류·문항별 25MB·제작자 500MB 한도를 모두 다시 검사한다. 로그인 사용자가 본인 `draft_response_id`를 보낸 경우 성공한 요청에서 같은 `Response`를 `in_progress → submitted`로 바꾸고 `submitted_at`을 기록하며, 비로그인 브라우저 초안은 새 `Response`를 만든다. 성공 후 브라우저에 남은 해당 초안·첨부 임시 파일을 지우고 새 제출을 시작할 수 있다. 검증·용량 부족으로 제출에 실패하면 서버 초안과 브라우저 초안을 보존한다.
- 자동채우기로 제안된 값은 응답자가 화면에서 확인·수정할 수 있으며, 자동채우기 요청 자체는 응답을 저장하거나 제출하지 않는다. 제출·수정할 때는 `input_format`의 값 검증·저장 규칙을 서버에서도 적용하고, 형식에 맞지 않으면 `400 invalid_input_format_value`로 응답과 파일 모두 저장하지 않는다.
- 응답 화면의 현재 단계와 남은 분량은 `sections` 순서, 현재 문항, 응답으로 결정된 분기 경로에서 계산한다. 아직 답하지 않은 문항의 분기로 경로가 달라질 수 있으면 전체 문항 수나 백분율을 확정된 값처럼 표시하지 않는다. 진행 정보는 `Form`이나 `Response`의 속성으로 저장하지 않는다.
- 제작자의 폼 설명란·문항 첨부는 이미지(`jpg`, `jpeg`, `png`, `gif`, `webp`)·PDF·MP4만 허용한다. 응답자 업로드는 PDF·이미지와 `zip`, `ppt`, `pptx`, `doc`, `docx`, `hwp`, `hwpx`를 허용한다. 확장자와 실제 파일 형식을 확인하고 이 목록 밖의 실행·스크립트 형식은 거부한다. ZIP은 서버에서 자동으로 압축 해제하거나 실행하지 않는다.
- 응답 화면에서 제작자가 첨부한 이미지는 폼 설명란이나 해당 문항 안에 바로 표시한다. PDF·MP4는 설명란과 문항 어디에 첨부했든 다운로드 링크만 제공하며, 화면 내 문서 보기·동영상 재생 기능은 제공하지 않는다. PDF·MP4 파일 응답은 브라우저가 바로 열지 않도록 다운로드로 전달한다.
- 응답자가 문항 하나의 답에 첨부하는 파일의 합계는 25MB(25,000,000바이트) 이하이다. 파일 한 개도 이 합계를 넘을 수 없다. 응답 한 건의 전체 파일 합계에는 별도 한도가 없고, 폼 제작자의 남은 500MB 용량만 적용한다. 문항별 한도를 넘으면 `413 file_too_large`, 제작자 용량을 넘으면 `409 storage_quota_exceeded`를 반환한다.
- `Response.attachments`는 제출한 파일의 `question_id`, `attachment_id`, `filename`, `content_type`, `size_bytes`, `available` 목록이다. 파일은 답을 제출한 문항에 연결한다. 파일이 포함된 제출·수정은 모든 문항의 파일 검증과 용량 확인을 통과한 뒤에만 응답과 파일을 함께 저장한다. 수정에서 첨부 목록을 생략하면 기존 파일을 유지하고, 새 목록을 보내면 교체한다. 실패한 요청은 기존 응답을 바꾸지 않고 임시 파일도 남기지 않는다. 첨부를 제거하거나 응답을 취소하면 연결을 끊고, 다른 폼 버전·문항·응답에서도 참조하지 않는 파일만 삭제한다. 비로그인 제출자는 제출 후 계정 기반 파일 조회를 할 수 없다.
- 응답 수정의 `answers`는 제출 버전 문항에 대한 전체 답 목록이다. 검증에 성공하면 **기존 `Response`의 답·보낸 첨부 목록을 덮어쓴다**. `response_id`, `form_version`, 최초 `submitted_at`은 유지하고 `updated_at`에 수정 시각(UTC)을 기록한다. 수정하지 않은 응답의 `updated_at`은 `null`이다. 새 응답을 만들지 않으므로 `response_count`는 유지되지만 선택지 집계·AI 조회·내보내기·응답자 이력에는 수정된 현재 답이 반영된다. 응답자가 자기 제출을 취소하면 그 응답과 첨부를 결과·분석·내보내기·본인 제출 이력에서 제거하고 `response_count`를 1 줄인다. 제작자의 폼 단위 전체 삭제와는 다른 동작이다.
- 제작자가 폼의 받은 응답 전체를 삭제하면 폼 정의와 공유 링크는 유지되고, 기존 응답은 제작자의 결과·분석·내보내기에서 모두 사라지며 제작자 용량에서 빠진다. 로그인 응답자의 제출 이력에는 폼 제목·제출 시각·문항 문구·답변 내용이 조회 전용으로 남고, 첨부 원본은 삭제되어 `available: false`로 표시한다. 내 폼함의 기존 제출 상태는 유지한다. 폼이 여전히 열려 있으면 새 응답을 다시 제출할 수 있다. 제작자가 폼 자체를 삭제할 때도 폼 정의·받은 응답·모든 첨부 원본을 삭제하지만 응답자의 텍스트 제출 이력은 조회 전용으로 유지한다. 삭제된 폼의 공유 링크로 새 응답을 제출할 수 없다.
- 로그인 사용자의 제출은 그 사용자의 응답으로 기록한다. 비로그인 제출은 이력·내 폼함에 남지 않는다.
- 폼의 `settings.editable`과 `settings.cancellable`은 각각 응답자 본인의 제출 응답 수정과 취소를 허용한다(기본값 모두 `false`). 제출 이력의 `editable`·`cancellable`은 폼이 존재해 `open` 상태이고 현재 시각이 `deadline` 전이며 해당 설정이 `true`이고 그 응답이 제작자의 결과에도 남아 있을 때만 각각 `true`다. `deadline`이 없으면 시간 조건을 통과한다. 제작자는 이 설정과 무관하게 폼 단위의 받은 응답 전체 또는 첨부 전체를 삭제할 수 있다. 폼 또는 받은 응답 전체를 삭제해 텍스트 이력만 남았다면 두 값이 모두 `false`이고 수정·취소 요청은 `409 history_only`다.
- 응답 데이터와 API 어디에도 송금 완료 여부를 담는 필드가 없다.
- 알림 메일은 `User.email`(소셜 로그인 계정의 이메일)로 보낸다. `Profile.personal_info.email`은 자동채우기 값일 뿐이다.

### 결과·내보내기 (제작자)

| 엔드포인트 | 인증 | 입력 | 출력 |
|---|---|---|---|
| `GET /forms/{form_id}/results` | 제작자 | — | `{response_count, combined: {questions: [{question_id, question_note, question_type, form_versions, options: [{label, count, form_versions}]}]}, versions: [{form_version, response_count, questions: [{question_id, question_note, counts: {선택지: 수}}]}]}` — 화면에서 통합·버전별 보기 전환 |
| `POST /forms/{form_id}/analyze` | 제작자 | `{query}` 또는 `{context}` — 대시보드의 버전별 보기 선택과 무관하게 해당 폼의 모든 제출 버전을 분석 | 아래 / `400 {error: "invalid_context"}` — 자연어에서 특정 버전 분석이나 지원 밖 통계를 요구하면 안내·되묻기 |
| `GET /forms/{form_id}/responses/{response_id}` | 제작자 | — | `{response_id, form_version, submitted_at, updated_at, content: [{question_id, question_note, value}], attachments}` — 해당 제출 버전의 문항 문구 / `404`(없는 응답 또는 다른 폼의 응답) |
| `DELETE /forms/{form_id}/responses` | 제작자 | — | `204` — 그 폼에 현재 받은 응답 전체와 응답 첨부 전체 삭제, 폼 정의는 유지 |
| `DELETE /forms/{form_id}/response-attachments` | 제작자 | — | `204` — 그 폼에 현재 받은 응답의 첨부 원본 전체 삭제, 답변 내용과 응답 수는 유지 |
| `POST /forms/{form_id}/export` | 제작자 | `{format: "json" \| "yaml" \| "csv", form_versions?, context?}` — `form_versions`는 필터 없는 대시보드 내보내기에만 사용하고 `context`와 함께 보내면 `400 invalid_context` | 파일. 범위와 `context`가 모두 없으면 전체 버전의 응답을 내보냄. `context`가 있으면 모든 버전의 AI 필터 결과만 / `{status: "clarify", clarify}` / `400 {error: "invalid_context"}` |

- 내보내기 JSON·YAML: `{form_versions: [{form_version, form: <그 버전의 폼 정의>}], responses: [{response_id, form_version, submitted_at, updated_at, answers: [{question_id, value}]}]}`. 응답마다 출처 버전을 붙이고, 포함된 버전의 폼 정의를 각각 넣는다. 배열의 응답 순서는 아래 규칙을 따른다.
- CSV: 첫 행은 `response_id`, `form_version`, `submitted_at`, `updated_at`, 그다음 각 버전의 문항마다 `<form_version>.<question_id> <제출 당시 문항 문구>`. 이후 행은 응답 하나씩이며, 그 응답의 버전에 없는 문항은 빈 칸이다. `checkbox` 답은 `; `로 잇고, 분기로 건너뛴 문항도 빈 칸이다. 폼 정의 전체는 넣지 않는다.
- 내보내기 행 순서: `context.operation`이 `list`면 같은 `context`로 분석한 `result.response_ids` 순서다. 그 밖에는 버전 오름차순 → 그 버전 안에서 `submitted_at` 오름차순 → `response_id` 오름차순이다. JSON·YAML의 `responses` 배열과 CSV 행에 동일하게 적용한다.
- AI 분석과 `context`가 있는 내보내기는 해당 폼에 현재 남아 있는 **모든 버전의 제출 응답**을 후보로 삼고, 각 응답의 제출 당시 폼 정의로 조건·선택지·분기 경로를 검증한다. 조건을 판정할 수 없는 응답은 일치·불일치 어느 쪽으로도 세지 않고, 결과와 내보내기에 표시할 판정 불가 범위를 별도로 계산한다. 같은 표현이 뜻이 다른 문항에 걸려 어느 문항을 뜻하는지 모호하면 후보 문항을 보여 주며 되묻는다.

`analyze` 출력:

```json
{
  "status": "ok",
  "context": { "conditions": ["소프트웨어학과"], "operation": "list" },
  "matched": [
    {"condition": "소프트웨어학과", "form_version": 1, "question_id": "q1", "kind": "option", "option": "소프트웨어학과"},
    {"condition": "소프트웨어학과", "form_version": 2, "question_id": "q1", "kind": "option", "option": "소프트웨어학과"}
  ],
  "coverage": { "total_submitted_count": 9, "not_evaluable_count": 4, "not_evaluable_by_version": [{"form_version": 3, "response_count": 4, "unavailable_conditions": ["소프트웨어학과"]}] },
  "result": { "operation": "list", "count": 5, "response_ids": ["r01", "r02", "r03", "r04", "r05"], "summary": null },
  "clarify": null
}
```

- `status`가 `ok`이면 `result`·`matched`가 있고 `clarify`는 `null`, `clarify`이면 그 반대다.
- `{context}`(되묻기 후 확정한 `RequestContext`)로 요청하면 파서를 부르지 않는다. `context`는 `src/response_analysis/schemas/request_context.schema.json`의 `conditions`·`match`·`operation`·`sort`만 사용하며, 스키마에 맞지 않으면 `400 invalid_context`다. 자연어 파싱 프롬프트는 `src/response_analysis/prompts/parse_query.md` 그대로 사용한다.
- AI 분석 범위는 **해당 폼에 현재 남아 있는 모든 버전의 `submitted` 응답**으로 고정한다. 대시보드가 버전별 통계를 보여 주고 있어도 AI 분석 범위는 바뀌지 않는다. `RequestContext`에는 버전 범위·보기 방식 필드를 넣지 않는다. 자연어에서 “버전 1만”, “버전별로”처럼 분석 범위를 제한·비교하라는 요청은 파서에 넘기기 전에 `unsupported_scope`로 안내하고 전체 버전 분석 또는 대시보드 보기를 제안한다. 범위 표현을 일반 응답 조건으로 취급하거나 조용히 무시하지 않는다.
- `conditions`는 항상 존재한다. `[]`는 조건 없이 폼의 모든 버전에 현재 남아 있는 `submitted` 응답 전체를 대상으로 한다. `operation`도 항상 존재하고 `count`·`summarize`·`list` 중 하나이며, 동작을 확정할 수 없으면 `null`이다. 평균·비율·그룹별 집계·문항별 통계 요청을 `count`나 `summarize`로 바꿔 실행하지 않고 `missing_operation`과 지원 동작을 안내한다. 문항·선택지 전체 통계는 AI와 별개로 결과 대시보드에서 계산·표시한다.
- `match`: `all` = 나열한 조건을 모두 만족, `any` = 하나라도 만족, `not` = 나열한 조건 중 어느 것도 만족하지 않음. 생략은 조건이 0개 또는 1개일 때 허용한다.
- 원문 요청이 긍정·부정 조건을 섞어 단일 `match`로 표현되지 않으면 `all`·`any`·`not` 중 하나로 뜻을 바꾸지 않는다. `clarify.reason: missing_match`, `options: []`로 요청을 나누어 달라고 안내한다.
- `sort`는 `list`에만 적용한다. 다른 `operation`과 함께 오면 정렬을 적용하거나 `sort.by`를 검증하지 않고 무시한다. `sort.by`는 각 제출 버전의 정의에서 해석한다. 어느 버전에도 문항이 없거나 같은 표현이 뜻이 다른 문항에 걸리면 되묻는다. 숫자·날짜의 값 형식 또는 선택지 정의 순서가 버전 사이에서 비교 가능해야 하며 그렇지 않으면 `unsortable_field`로 되묻는다. 일부 버전에만 문항이 없으면 그 버전 응답을 빈 정렬값처럼 맨 뒤에 놓고 `coverage.sort_unavailable_by_version: [form_version, ...]`에 알린다. `sort.order`는 `asc` 또는 `desc`이며 방향을 말하지 않으면 `asc`다. 값이 같거나 `sort`가 없으면 모든 버전을 합쳐 `response_id` 오름차순이다.
- 서버는 각 응답의 제출 당시 버전에서 각 조건을 `true`·`false`·`판정 불가`로 평가한다. 해당 문항·선택지가 그 버전에 없거나 분기로 그 문항을 접하지 않았다면 판정 불가다. `all`은 하나라도 `false`면 불일치, 전부 `true`면 일치이고 그 밖에는 판정 불가다. `any`는 하나라도 `true`면 일치, 전부 `false`면 불일치이고 그 밖에는 판정 불가다. `not`은 `any` 결과를 뒤집되 판정 불가는 그대로 둔다. 조건이 없으면 모든 제출 응답이 일치한다. 판정 불가 응답은 일치 응답으로 세거나 “아닌” 응답으로 취급하지 않는다.
- `matched[]`에는 실제 문항·선택지에 매칭된 **조건·제출 버전별** 근거를 담고 `form_version`을 표시한다. `kind`는 `option`(선택지 일치, `option`에 선택지) / `answered`·`unanswered`(그 문항에 답했는지, `option`은 `null`)다. `coverage`는 `{total_submitted_count, not_evaluable_count, not_evaluable_by_version: [{form_version, response_count, unavailable_conditions}], sort_unavailable_by_version?}`이며 판정 불가의 버전·조건별 이유를 담는다. 일부 버전에 해당 문항이 없어도 나머지 버전의 일치 응답은 반환한다. 어느 버전에서도 조건을 매칭할 수 없거나 같은 표현이 뜻이 다른 문항에 걸려 안전하게 특정할 수 없으면 되묻는다. AI 필터 결과를 내보낼 때도 다운로드 전에 같은 판정 불가 범위를 제작자에게 보여 준다.
- `result.count`는 모든 제출 버전에서 조건에 **일치**한 응답 수(온톨로지의 파생값 `Result.filtered_response_count`)다. `result.response_ids`는 `list` 결과와 `summarize`에 실제 사용한 응답 ID 목록이고 `count`에서는 `null`이다. 요약 ID 순서는 정렬 없는 `list`와 같다. 요약 프롬프트에는 각 응답의 제출 버전과 그 버전의 문항 문구를 데이터로 전달하며 수치 집계는 LLM에 맡기지 않는다. 제작자 화면은 `list`의 `response_ids` 순서대로 상세 API를 조회한다. AI 필터·판정 불가 제외를 적용해도 폼 전체 `Result.response_count`는 바뀌지 않는다.
- 결과 대시보드 통계는 AI 분석과 별개로 코드가 계산한다. 통합 보기는 `question_id`·문항 문구·문항 유형이 같은 문항끼리 묶고 **같은 선택지 문구**의 응답 수만 합친다. 선택지별 실제 출처 버전을 표시하고, 달라진 문항·선택지는 별도 항목으로 보여 준다. 버전별 보기는 각 제출 버전의 정의와 응답만 사용한다.
- `clarify`: `{reason, message, options[]}`. 여러 사유가 해당하면 **표에서 위에 있는 것 하나만** 반환한다.

| 순서 | reason | 언제 | `options` | 강의 3 실패 모드 |
|---|---|---|---|---|
| 1 | `unsupported_scope` | 자연어에서 특정 버전만 분석하거나 버전별 AI 결과를 비교하도록 요청함. 쿼리를 파서에 보내기 전에 판정 | 전체 버전 AI 분석·대시보드 버전별 보기 안내 | 제품 정책 |
| 2 | `parse_failed` | 형식 오류 재요청 후에도 스키마 검증 실패 | `[]` | 4, 9, 10 |
| 3 | `missing_operation` | `operation`이 `null`이거나 평균·비율·그룹별 집계·문항별 통계처럼 지원하지 않는 동작을 요청함 | `["count", "list", "summarize"]`와 대시보드 안내 | 2, 5 |
| 4 | `missing_match` | 조건이 2개 이상인데 `match`가 없음. 또는 원문 요청에 긍정·부정 조건이 섞여 단일 `match`로 표현할 수 없음 | 일반: `["all", "any"]`; 혼합 부정: `[]`와 요청을 나누어 달라는 안내 | (필드 표: match 수집 필수) |
| 5 | `unknown_condition` | 조건이나 `list`의 `sort.by`가 현재 응답이 남은 모든 폼 버전에서 문항·선택지에 매칭되지 않음 | 버전 번호를 붙인 선택형 문항 목록 `[{form_version, question_id, question_note, options}]` | 7 |
| 6 | `ambiguous_match` | 단일 선택 문항의 서로 다른 선택지를 `all`로 묶거나, 같은 조건 표현이 뜻이 다른 여러 문항에 매칭됨 | 전자: `["any"]`; 후자: 버전 번호가 붙은 후보 문항 목록 | 3; 버전 충돌은 제품 정책 |
| 7 | `double_negation` | "미응답" 조건과 `match: not`이 함께 옴 | 확정 요청 문장 1개 | 8 |
| 8 | `unsortable_field` | `list`의 `sort.by`가 정렬할 수 없는 문항에 매칭 | 버전 번호가 붙은 정렬 가능한 문항 목록 | 6 |

### 추가 개발 (착수 시 확정)

| 엔드포인트 | 인증 | 입력 | 출력 |
|---|---|---|---|
| `GET /me/calendar?month=YYYY-MM` | 로그인 | — | `[{date, forms: [{form_id, title, state}]}]` |
| `POST /forms/{form_id}/questions/{question_id}/polish` | 공개 | `{text, kind: "spelling" \| "polish"}` | `{suggestion, char_count}` |

### 운영

`GET /health` (공개) → `{"status": "ok"}`

## 6. 수용 기준 (테스트로 검증 — Definition of Done)

각 AC는 EARS 문형 "[조건]일 때, Passform은 [동작]한다"로 쓰고, 조건 자리의 유형을 [ ]에 표시한다. 테스트 입력은 `data/seed/`의 시드 폼·프로필·응답이다. 테스트에서 LLM과 메일 발송은 모의 객체이며, 실제 모델의 품질은 evals(강의 7)에서 판정한다. 같은 주제의 AC는 번호와 관계없이 한 절에 모았다.

### 계정·화면 모드·프로필·자동채우기

- **AC1 [이벤트 기반]**: 사용자가 처음 소셜 로그인하면, Passform은 소셜 계정의 이메일로 `User`를 만들고 세션을 발급한다. 같은 계정으로 다시 로그인하면 새 `User`를 만들지 않는다.
  - 테스트: 모의 OAuth로 2회 로그인 → `User` 1개, `GET /me` 200
- **AC37 [이벤트 기반]**: 사용자가 화면 모드를 바꾸면, Passform은 그 모드를 계정에 저장해 다음 로그인에서도 같은 모드로 돌려준다. 모드는 권한을 바꾸지 않는다.
  - 테스트: `ui_mode: creator` 저장 → 재로그인 후 `GET /me`의 `ui_mode` = `creator` / 응답자 모드에서도 내가 만든 폼의 결과 조회 200, 남의 폼 결과 조회 403
- **AC2 [상시 적용]**: Passform은 항상 5절 인증 구분대로 접근을 제한한다 — 로그인이 필요한데 없으면 `401`, 남의 프로필·응답은 `404`, 제작자가 아닌 사용자의 폼 정의 조회·수정·복사·삭제, 폼·문항 첨부 관리, 받은 응답·첨부 전체 삭제, QR·분기 그래프·결과·분석·내보내기는 `403`이며 응답 내용을 반환하지 않는다.
- **AC3 [상시 적용]**: Passform은 항상 프로필에 허용 목록의 키만 저장한다 — 목록 밖 키가 하나라도 오면 아무것도 저장하지 않고 `400 unsupported_key`를 반환한다.
  - 테스트: `personal_info`의 `address`·`birth_date` 저장·재조회 성공 / `resident_number` 포함 → 400, 저장된 프로필 불변
- **AC4 [이벤트 기반]**: 응답자가 프로필을 여러 개 저장하고 그중 하나로 자동채우기를 요청하면, Passform은 고른 프로필의 값으로 채운다.
  - 테스트: 학교(학과 = 소프트웨어학과)·동아리(학과 없음, 이메일 다름) 프로필 → 각각으로 자동채우기한 `answers`가 그 프로필 값과 일치
- **AC5 [이벤트 기반]**: 응답자가 프로필을 삭제하면, Passform은 그 프로필의 `personal_info`를 지우고 이후 자동채우기에 쓰지 않는다. 이미 제출한 응답은 바꾸지 않는다.
  - 테스트: 삭제 후 그 `profile_id`로 자동채우기 → `404`, 삭제 전에 제출한 응답의 `content` 불변
- **AC6 [이벤트 기반]**: 응답자가 자동채우기를 요청하면, Passform은 제작자가 본인 정보라고 확인해 `profile_key`를 설정한 문항 중 고른 프로필에 값이 있는 문항만 그 값으로 제안한다. 선택형 문항은 프로필 값이 선택지와 정확히 같을 때만 채운다. 나머지는 `unfilled`에 담고, 응답자가 제안값을 확인·수정할 수 있게 하며, 이 요청만으로 `Response`를 만들거나 바꾸지 않는다.
  - 테스트: 본인 이름·주소·생년월일 문항은 프로필 값 제안, 같은 이름 형식의 부모님 이름 문항(`profile_key: null`)과 지원 동기는 `unfilled` / 제안된 이름을 화면에서 수정한 뒤 제출하면 수정값으로 저장 / 학과 값이 선택지와 다르면 `unfilled` / 요청 전후 `Response` 수 동일

### 폼 빌더·설정·저장

- **AC42 [상시 적용]**: Passform은 항상 기본 모드와 빠른 모드의 폼을 같은 폼 정의(`form.schema.json`)로 저장한다 — 어느 모드에서 만든 폼이든 다른 모드에서 열면 문항의 `input_format`과 제작자가 확인한 `profile_key`를 포함해 같은 정의가 나온다.
  - 테스트: 빠른 모드로 만든 폼의 `GET /forms/{id}/definition`이 스키마를 통과하고, 기본 모드에서 열어 `input_format`·`profile_key`가 유지되는지 확인 / 그 정의를 그대로 `PATCH`해도 바뀌는 값이 없음
- **AC79 [이벤트 기반]**: 제작자가 데스크톱 기본 모드의 실제 제작 화면에서 섹션과 문항을 각각 추가·수정·삭제·복제하거나 순서를 바꾸고 초안을 저장하면, Passform은 그 구조와 설정을 폼 정의에 반영하고 다시 열었을 때 같은 순서로 보여 준다. 문항 복제는 새 `question_id`, 섹션 복제는 새 `section_id`와 새 자식 `question_id`를 쓰며 원본은 그대로 둔다. 복제된 문항의 `branch_rules`는 비우고 `profile_key`는 `null`로 두어 다시 설정하게 한다.
  - 테스트: 제작 화면에서 섹션 2개와 문항들을 만든 뒤 문항 문구·설명·선택지·필수 여부·입력 형식을 수정하고, 문항과 섹션을 각각 복제·순서 변경·삭제해 초안 저장 → 다시 열면 남은 섹션·문항과 순서·설정이 화면 및 `GET /forms/{id}/definition`에서 일치 / 복제본 ID는 원본과 다르고 원본 불변, 복제본의 분기 규칙 없음·`profile_key: null` / JSON만 직접 전송한 결과로 제작 화면 판정을 대신하지 않음
- **AC80 [이벤트 기반]**: 제작자가 데스크톱 기본 모드에서 다섯 문항 유형과 문항별 문구·보조 설명·선택지·필수 여부·입력 형식을 설정해 저장하면, Passform은 다시 연 제작 화면과 배포된 응답 화면에 그 유형과 설정을 동일하게 적용한다. 본인 정보 여부의 확인·초기화와 입력값 검증은 AC72·73을 따른다.
  - 테스트: 제작 화면에서 `multiple_choice`·`dropdown`·`checkbox`·`short_answer`·`long_answer`를 각각 만들고 선택형의 선택지, 모든 문항의 설명·필수 여부 및 짧은 답의 날짜 입력 형식을 설정 → 초안 저장·재열기 후 설정 유지 / 배포 후 객관식·드롭다운은 단일 선택, 체크박스는 복수 선택, 짧은 답·장문 답은 각각 입력란을 보여 주고 문항 설명·필수 표시·날짜 안내가 제작 화면의 설정과 일치
- **AC81 [이벤트 기반]**: 제작자가 기본·빠른 모드에서 저장 전 미리보기를 열면, Passform은 현재 편집 중인 폼 정의를 응답자 화면 방식으로 보여 주고 같은 분기 경로 규칙을 적용한다. 미리보기에서 답을 입력하거나 제출 동작을 해도 실제 `Response`·결과 집계는 만들지 않으며 돌아오면 편집 내용은 유지된다.
  - 테스트: 두 제작 모드 각각에서 저장하지 않은 문항 문구가 미리보기에 즉시 나타남 / 기본 모드에서 문항 설명·보기 방식 변경도 미리보기에 반영 / 분기 폼의 선택지를 고르면 미리보기 다음 문항·섹션이 실제 응답 경로와 일치 / 미리보기에서 제출 후 `Response` 수·`Result.response_count` 불변, 편집 화면으로 돌아와 저장·재열기해도 편집 내용 유지
- **AC84 [이벤트 기반]**: 제작자가 기본·빠른 모드에서 `draft` 폼을 편집하면, Passform은 변경 내용을 자동 저장하고 화면을 나갔다 다시 열었을 때 마지막 저장 완료 상태부터 이어서 편집하게 한다. 자동 저장은 폼을 배포하거나 `definition_version`을 늘리지 않는다. 저장에 실패하면 완료로 표시하지 않는다.
  - 테스트: 데스크톱 기본 모드와 모바일 빠른 모드에서 각각 문항을 바꾼 뒤 저장 완료 표시를 확인하고 화면을 나갔다 돌아오면 편집 내용 유지 / 미완성 분기·동의 템플릿 상태도 초안으로 저장 / 버전 1의 응답 0건인 폼을 `draft`로 되돌린 뒤 자동 저장해도 배포 버전은 1 유지 / 저장 실패 시 이전 저장본 유지·화면에 실패 표시
- **AC7 [이벤트 기반]**: 제작자가 추천 문항(`template_id`)을 추가하면, Passform은 그 템플릿의 `question_note`, `question_type`, `options`, `input_format`을 복사하고 `profile_key`는 확인 전까지 `null`로 둔다.
  - 테스트: “연락처” 템플릿으로 추가 → 전화번호 입력 형식은 복사되고 `profile_key: null`; 본인 연락처라고 확인한 뒤에만 `phone_number`가 되어 자동채우기 대상이 됨
- **AC71 [이벤트 기반]**: 제작자가 빠른 모드에서 제작 목적과 응답 대상을 고르면, Passform은 두 선택에 맞는 추천 문항을 보여 주고 선택한 문항과 직접 추가한 질문을 함께 검토·미리보기하게 한다.
  - 테스트: 목적 `예약`을 고르면 예약자 성함·예약 날짜에 해당하는 추천 문항을 선택할 수 있음 / 같은 목적에서 응답 대상을 바꾸면 추천 문항을 해당 대상에 맞게 다시 제시 / 직접 만든 질문도 추가해 저장·미리보기 / 목적 `퀴즈`에서는 그 목적에 맞는 문항 추천(예약 관리·자동 채점 기능은 없음)
- **AC72 [이벤트 기반]**: 제작자가 기본·빠른 모드에서 문항의 입력 형식을 정하면, Passform은 두 모드 모두 응답자 본인 정보인지 쉬운 말로 묻고 기본값을 “아니요”로 둔다. “예”로 확인한 문항에만 해당 `profile_key`를 설정하며, 문항 문구나 입력 형식이 바뀌면 그 선택을 지우고 다시 확인한다.
  - 테스트: 두 모드에서 각각 이름 형식의 “부모님 이름”을 추가 → 기본 `profile_key: null` / “응답자 본인의 이름인가요?”에 “예” → `name` / 주소·날짜 형식에서 본인 주소·생년월일 질문에 “예” → 각각 `address`·`birth_date` / 문구 또는 형식 변경 → `null`, 다시 확인 전에는 자동채우기 안 됨
- **AC73 [이벤트 기반]**: 제작자가 `short_answer` 문항에 입력 형식을 설정하면, Passform은 응답 화면에서 그 형식에 맞게 안내하고 5절의 `kind`별 규칙으로 제출·수정 값을 서버에서 검증·저장한다. 형식에 맞지 않으면 `400 invalid_input_format_value`를 반환하고 기존 응답·파일을 바꾸지 않는다.
  - 테스트: 날짜 `2026-02-28` 제출 → 그대로 저장, `2026-02-30` → 400 / 휴대전화 `010-1234-5678` → `01012345678` 저장, 9자리 또는 문자 포함 → 400 / 집전화 `02-123-4567` → `021234567` 저장, 8자리 → 400 / 이메일 `a@example.com` → 저장, `a@@example.com` → 400 / 이름·주소·학번의 앞뒤 공백 제거 후 저장, 필수 문항에 공백만 제출 → `400 required_missing` / 수정 허용·마감 전 폼에서도 같은 규칙 적용, 실패하면 기존 응답·파일 불변
- **AC38 [상시 적용]**: Passform은 항상 새 폼을 `draft`로 만들고, `draft` 폼은 제작자 외에는 조회·응답·저장할 수 없게 한다. 첫 배포 전에는 `definition_version: null`이고, 제작자가 처음 `open`으로 바꿔 검증을 통과하면 `share_url`과 `definition_version: 1`을 만든다.
  - 테스트: 만든 직후 제작자의 `GET /forms/{id}/definition` → `definition_version: null`, 다른 사용자의 `GET /forms/{id}`·응답 제출·내 폼함 저장 → 모두 `404` / `PATCH status: open` 뒤 `share_url`로 공개 조회하면 `definition_version: 1`
- **AC56 [이벤트 기반]**: 제작자가 기본 시작 방식을 정해 `draft` 폼을 저장하면, Passform은 형식 검증만 하고 분기 규칙이나 동의 템플릿이 미완성이어도 저장하며 `definition_version`을 올리지 않는다. 그 폼을 `open`으로 바꿀 때 기본 시작 방식과 분기 규칙의 적합성까지 의미 검증을 하고, 통과하지 못하면 해당 `400`을 반환하며 작업 초안·마지막 배포 정의와 버전은 유지한다. 검증에 성공해 이전 배포본과 버전 대상 정의가 다를 때만 다음 버전을 만든다.
  - 테스트: 동의 템플릿 없는 첫 초안 저장 → 201, `definition_version: null` / 그 상태로 `open` → `400 consent_template_required`, `status: draft`, 버전 `null` / 버전 1의 응답 0건인 폼을 `open → draft`로 되돌려 미완성 초안 저장 → 버전 1 유지 / 재배포 검증 실패 → 초안·버전 1 불변 / 초안을 고쳐 재배포 성공 → 버전 2, 다시 초안으로 되돌렸다가 정의 변경 없이 재배포 → 버전 2 유지
- **AC57 [예외 대응]**: 허용되지 않는 상태 전이(`closed → draft`, 응답이 있는 폼의 `open → draft` 등)를 요청하면, Passform은 `409 invalid_transition`을 반환하고 상태를 바꾸지 않는다. 다시 `open`해도 `share_url`은 바뀌지 않는다.
- **AC58 [이벤트 기반]**: 제작자가 `open`·`closed` 폼의 문항 문구·선택지·순서·분기 규칙·설명란/문항 첨부 등 응답자가 보는 정의를 수정하면, Passform은 새 정의를 검증한 뒤 다음 `definition_version`으로 저장하고 이전 버전과 그 버전에서 받은 응답의 문항·답·첨부 연결을 그대로 보존한다. 운영 설정만 바꾸거나 정의를 바꾸지 않으면 버전을 늘리지 않는다. 첨부 추가·삭제 API도 이 규칙을 적용한다.
  - 테스트: 버전 1에 응답 1건을 받은 뒤 문항 문구·선택지·순서·분기를 바꾸거나 문항을 삭제해 저장 → 버전 2 배포, 기존 응답 상세에는 버전 1 문구·답 유지 / `editable`·`cancellable`만 변경 → 버전 번호 불변
- **AC39 [이벤트 기반]**: 사용자가 내가 만든 폼 목록을 열면, Passform은 그 사용자가 `owner_id`인 폼만 `draft`·`open`·`closed` 상태와 함께 반환한다.
- **AC40 [이벤트 기반]**: 제작자가 폼을 복사하면, Passform은 섹션·문항·분기·설정이 같은 새 `draft` 폼을 그 제작자 소유로 만든다. 응답과 `share_url`은 복사하지 않고 원본은 바뀌지 않는다.
  - 테스트: 응답 5건 있는 폼 복사 → 새 폼의 폼 정의가 원본과 같음(id 제외), 응답 0건, `share_url` 없음
- **AC69 [이벤트 기반]**: 제작자가 모바일 웹에서 폼을 만들면, Passform은 빠른 모드에서 목적·대상 선택, 추천·직접 추가 문항의 본인 정보 설정, 초안 저장·배포를 지원하고 데스크톱에서 만든 폼과 같은 폼 정의 JSON을 저장한다. 모바일 웹에서는 기본 모드 제작 화면으로 진입시키지 않고 데스크톱에서 이용하도록 안내한다.
  - 테스트: 모바일 너비 360px에서 빠른 모드의 목적·대상 선택 → 추천·직접 추가 문항 구성과 본인 정보 확인 → 초안 저장·배포 → 같은 계정의 데스크톱에서 동일한 폼 정의 조회 / 모바일 기본 모드 진입 시 데스크톱 안내
- **AC12 [예외 대응]**: 문항의 `input_format`이나 `profile_key` 유무와 관계없이 폼을 `consent_template_id` 없이 `open`하거나 `open`·`closed` 상태로 저장하면, Passform은 `400 consent_template_required`를 반환하고 폼을 저장하지 않는다.
  - 테스트: 이름·전화번호 형식이지만 `profile_key: null`인 폼과 형식 없는 일반 문항만 있는 폼 모두 동의 템플릿 없이 배포 시 400, 상태·정의 불변
- **AC11 [상시 적용]**: Passform은 항상 `payment_link`로 `https` URL만 저장하고 응답 화면에 그대로 보여 주며, 송금을 처리하거나 송금 완료 여부를 저장하지 않는다.
  - 테스트: `http://`·`javascript:` 링크 → `400 invalid_payment_link` / 폼·응답 API 응답에 송금 상태 필드 없음

### 조건분기·보기 방식

- **AC8 [예외 대응]**: `open`하거나 `open`·`closed` 폼을 저장할 때 분기 규칙의 `go_to`가 폼에 없는 섹션·문항이거나 같은 위치·앞쪽을 가리키거나, `conditions`가 뒤 문항·선택형이 아닌 문항·그 문항에 없는 선택지를 가리키면, Passform은 `400 invalid_branch`를 반환하고 저장하지 않는다.
- **AC44 [이벤트 기반]**: 분기 조건이 여러 답의 조합이면, Passform은 `when.match`(all/any)대로 조합이 맞을 때만 그 규칙의 `go_to`로 보낸다. 여러 규칙이 맞으면 위에 있는 규칙을 따른다.
  - 테스트: `all` "q2 = 예, q3 = 4학년 → s4"에 (예, 4학년) → s4, (예, 3학년) → 다음 섹션 / `any` 같은 조건에 (아니오, 4학년) → s4
- **AC9 [이벤트 기반]**: 응답자가 분기로 일부 섹션·문항을 건너뛰면, Passform은 건너뛴 문항을 필수 검사에서 빼고 제출 내용에 저장하지 않는다. 경로는 서버가 제출된 답으로 다시 계산한다.
  - 테스트: q2에서 "아니오" → s3로 분기, s2의 문항이 필수여도 제출 `201`, `content`에 s2 문항 없음 / 클라이언트가 건너뛴 문항의 답을 보내도 저장되지 않음
- **AC45 [이벤트 기반]**: 제작자가 분기 흐름을 요청하면, Passform은 섹션·문항·제출을 노드로, 규칙마다 `rule` 엣지 1개를, 문항마다 기본 다음 이동 `default` 엣지 1개(섹션 마지막 문항은 다음 섹션의 첫 문항으로, 마지막 섹션의 마지막 문항은 제출로)를 반환한다.
  - 테스트: 규칙 3개 폼 → `kind: rule` 엣지 3개, 각 엣지의 `to`가 규칙의 `go_to`와 같음
- **AC46 [예외 대응]**: 분기 규칙이 없을 때 `display.default_mode`가 `one_question`·`all_questions` 밖이거나, 분기 규칙이 있을 때 `one_question`·`section_page` 밖이면, Passform은 `open` 전환이나 `open`·`closed` 폼 저장 시 `400 invalid_display_mode`를 반환하고 저장하지 않는다. `section_page`에서 섹션의 마지막 문항이 아닌 곳에 `branch_rules`를 두거나 `go_to.question_id`로 분기해도 같은 오류를 반환한다. `one_question`에서는 각 문항에 분기 규칙을 두고 뒤쪽 문항·섹션 또는 제출로 이동할 수 있다.
  - 테스트: 분기 없는 폼의 `all_questions`·`one_question` → 저장 성공, `section_page` → `400 invalid_display_mode` / 분기 있는 폼의 `all_questions` → `400 invalid_display_mode` / 섹션별 보기에서 중간 문항에 분기 규칙 또는 문항 대상 분기 → `400 invalid_display_mode` / 마지막 문항에서 뒤쪽 섹션으로 분기 → 저장 성공 / 문항별 보기에서 중간 문항에서 뒤쪽 문항으로 분기 → 저장 성공
- **AC47 [이벤트 기반]**: 제작자가 정한 `display.default_mode`로 폼을 열면, Passform은 분기 규칙이 없는 폼에만 응답자가 「한 질문씩」과 「전체 질문 보기」 사이를 전환하는 버튼을 보여 준다. 분기 규칙이 있는 폼에는 버튼을 보여 주지 않는다. 전환 시 작성한 답과 현재 문항은 유지하고 시작 화면으로 되돌아가지 않는다. 처음 `one_question`으로 들어온 응답자에게는 제목·설명과 시작 버튼을 먼저 보여 주고 버튼을 누른 뒤 첫 문항을, `all_questions`는 제목·설명과 모든 섹션·문항을 한 페이지에, `section_page`는 제목·설명과 첫 섹션의 모든 문항을 첫 화면에 보여 준다.
  - 테스트: `display.default_mode` 없이 폼 생성 → `400 schema_invalid` / 분기 없는 폼 → `allowed_modes: ["one_question", "all_questions"]`, 두 방식 전환 중 입력 답·현재 문항 유지 / 분기 있는 폼 → `allowed_modes: [default_mode]`, 전환 버튼 없음 / 문항별 보기의 시작 버튼 전에는 첫 문항 숨김 / 전체 질문 보기의 섹션 제목과 모든 문항 표시 / 섹션별 보기의 첫 섹션 문항 전체 표시
- **AC60 [이벤트 기반]**: 응답자가 폼을 작성하면, Passform은 현재 섹션·문항과 확정 가능한 남은 분량을 화면에 보여 준다. 아직 결정되지 않은 분기로 경로가 달라질 수 있으면 전체 문항 수나 백분율을 확정된 값으로 보여 주지 않는다.
  - 테스트: 분기 없는 3개 섹션 폼의 두 번째 섹션 → 현재 위치와 남은 1개 섹션 표시 / 미응답 선택지에 따라 뒤 경로가 달라지는 폼 → 답하기 전 확정된 전체 문항 수·백분율 표시 없음

### 개인정보 동의

- **AC13 [이벤트 기반]**: 배포된 폼을 열면, Passform은 `profile_key`와 무관하게 문항의 `input_format` 범주와 형식 없는 문항의 “문항별 응답 내용”으로 수집 항목을 만들고, 동의 문구에 목적·보유 기간·동의 거부 안내와 함께 보여 준다.
  - 테스트: 본인 이름(`profile_key: name`)·부모님 이름(`profile_key: null`)·전화번호 형식 문항·형식 없는 자유 입력 문항 → `consent.items`에 “이름”은 한 번만, “전화번호”와 “문항별 응답 내용”은 포함 / 목적·보유 기간·동의 거부 안내 표시
- **AC14 [예외 대응]**: 배포된 폼에서 `consent_agreed`가 `true`가 아닌 채로 제출하면, Passform은 문항의 형식·자동채우기 설정과 관계없이 `400 consent_required`를 반환하고 `submitted` 응답이나 서버 첨부를 새로 저장하지 않는다. 작성 중 초안은 그대로 둔다.
  - 테스트: 형식 없는 문항만 있는 폼에서 `consent_agreed` 생략 또는 `false` → 400, 제출 응답 수·제작자 첨부 사용량 불변, 로그인 응답 초안과 같은 브라우저의 파일 임시 저장 유지

### 파일 첨부·업로드

- **AC63 [이벤트 기반]**: 제작자가 폼 설명란 또는 문항에 허용 형식의 파일을 첨부하면, Passform은 각각 폼 정의 JSON의 `note_attachments` 또는 `Question.attachments`에 연결한다. 별도 파일 크기 제한은 적용하지 않고 제작자의 500MB 용량만 확인한다. 첨부 추가·삭제 API가 `open`·`closed` 폼에서 성공하면 새 불변 폼 정의 버전을 만들고, `draft`에서는 작업 초안만 바꾼다. 실패하면 첨부 목록·버전·사용량을 바꾸지 않고 임시 파일도 남기지 않는다. 현재 배포 버전의 파일은 공개 폼에서, 과거 버전의 파일은 제작자와 그 버전에 응답한 로그인 사용자만 열람할 수 있다. 새 버전에서 파일을 제거해도 과거 버전이 참조하면 원본과 사용량을 유지한다. 응답 화면에서는 이미지에만 바로 보기를 제공하고 PDF·MP4는 다운로드로 제공한다.
  - 테스트: 버전 1의 문항에 파일을 붙여 응답을 받은 뒤 삭제 API로 첨부 제거 → 버전 2 생성, 버전 1 정의와 원본 불변, 버전 1 응답자는 원본 다운로드 가능, 새 공개 폼 방문자는 과거 파일 접근 불가, 제작자 사용량 유지 / 폼 전체 삭제 뒤 원본 다운로드 불가
  - 테스트: 첫 배포 전 초안에서 업로드·삭제 → `definition_version: null` / 배포된 버전 1의 설명란 또는 문항에 별도 업로드 API로 파일 추가 → 버전 2, 이전 버전 정의 불변 / 배포 폼에서 저장 실패·용량 초과·잘못된 형식 → 기존 첨부 목록·버전·사용량 불변, 임시 파일 없음
  - 테스트: 제작자가 설명란과 문항에 이미지·PDF·MP4 첨부 → 이미지에만 응답 화면 내 바로 보기, PDF·MP4에는 다운로드 링크만 표시 / PDF·MP4를 요청하면 다운로드 응답 / 초안의 첨부는 제작자만 열람 / 확장자만 PDF로 바꾼 실행 파일 → `400 invalid_file_type`, 목록 불변
- **AC64 [이벤트 기반]**: 응답자가 허용 형식의 파일을 문항별 답에 첨부하면, Passform은 같은 `question_id`의 파일 크기 합계가 25MB 이하이고 제작자에게 저장 공간이 남은 경우에만 그 파일을 제출된 `Response`에 연결한다. 응답 한 건의 전체 파일 합계에는 별도 한도를 두지 않는다. 제작자와 제출한 로그인 사용자만 이후 파일을 열람할 수 있다. 파일 검증이나 제출이 실패하면 새 제출 응답·서버 첨부를 만들지 않고 작성 중 초안은 유지한다.
  - 테스트: 한 문항에 ZIP 10MB+15MB 첨부 → 제출 성공 / 같은 문항에 20MB+6MB → `413 file_too_large`, 새 응답·파일 없음 / 서로 다른 두 문항에 각각 25MB → 문항별 한도 통과 / 다른 응답자 파일 조회 `404`, 비로그인 제출자는 계정 기반 재조회 불가 / 수정 중 파일 검사 실패 → 기존 응답·첨부 불변
- **AC65 [상시 적용]**: Passform은 항상 사용자마다 내가 만든 폼·받은 제출 응답·내 계정에 저장한 미제출 답의 크기 합계가 500MB를 넘지 않게 한다. 폼 제작·첨부·수정·복사, 응답 초안 저장이나 제출 응답 수신으로 한도를 넘으면 `409 storage_quota_exceeded`를 반환하고 기존 자료와 사용량을 바꾸지 않는다. 제출된 응답 파일은 로그인 여부와 관계없이 폼 제작자의 용량에만 계산한다. 제출 전 브라우저에만 보관한 파일은 서버 사용량에 포함하지 않는다.
  - 테스트: 기존 사용량과 새 저장 크기의 합이 정확히 500MB → 허용, 이어 사용량을 1바이트라도 늘리는 저장 → `409`, `GET /me/storage` 사용량 불변 / 로그인 응답 초안의 답은 응답자 용량에 포함, 제출 성공 후 응답자 초안 사용량 해제·제작자 제출 응답 사용량 증가 / 비로그인 파일 응답도 제작자 사용량 증가 / 폼의 응답 첨부 전체 삭제 → 해당 파일만큼 제작자 사용량 감소
- **AC66 [이벤트 기반]**: 제작자가 한 폼의 받은 응답 전체 삭제를 실행하면, Passform은 그 폼의 기존 응답 전부를 한 번에 결과·분석·내보내기에서 제외하고 제작자 사용량을 줄인다. 삭제 도중 실패하면 일부 응답만 삭제하지 않는다. 폼 정의와 공유 링크는 유지한다. 로그인 응답자의 제출 이력은 제출 버전의 문항·답변 문구를 조회 전용으로 유지하고, 삭제한 첨부 원본은 `available: false`로 표시하며 다운로드를 허용하지 않는다. `settings.editable`·`settings.cancellable`은 제작자의 전체 삭제 권한에 영향을 주지 않는다.
  - 테스트: 제출 응답 2건이 있는 폼에서 응답 전체 삭제 → `response_count: 0`, 조건 없는 `count: 0`, 내보내기 응답 행 0건, 삭제된 두 응답의 제작자 상세 조회 `404` / 제출자 이력의 문항·답변은 남고 첨부는 `available: false`, 다운로드 `410`, 수정·취소 불가 / 제작자 사용량 감소 / 폼이 계속 `open`이면 새 응답 제출 가능
- **AC67 [이벤트 기반]**: 제작자가 폼 삭제를 선택하면, Passform은 삭제 전에 받은 응답을 기존 JSON·YAML·CSV 형식으로 내보낼지 묻고 첨부 원본은 해당 내보내기에 포함되지 않음을 알린다. 내보내기를 선택했다면 성공한 뒤에, 건너뛰기를 명시했다면 즉시 폼 정의와 받은 응답 전체를 삭제한다. 내보내기 실패만으로 삭제하지 않는다. 삭제 뒤 응답자의 텍스트 제출 이력은 조회 전용으로 유지하되 첨부 원본과 폼 공유 링크는 더는 열리지 않으며 수정·취소·재제출도 불가능하다.
  - 테스트: 내보내기 선택→성공→삭제 / 내보내기 실패→폼·응답 그대로 / 건너뛰기 선택→삭제 / 삭제 후 제작자 목록·결과에서 제외되고 저장 사용량 감소, 응답자 이력의 제출 버전 문구 유지·`editable: false`·`cancellable: false`, 수정·취소 `409 history_only`, 공유 링크 조회·재제출 `404`
- **AC70 [이벤트 기반]**: 제작자가 폼의 응답 첨부 전체 삭제를 선택하면, Passform은 그 폼에 현재 받은 모든 응답의 첨부 원본을 한 번에 삭제하고 `available: false`로 표시한다. 삭제 도중 실패하면 일부 응답자의 파일만 삭제하지 않는다. 응답 내용과 `response_count`·분석 결과의 응답 수는 바꾸지 않고, 삭제한 파일만큼 제작자 용량을 확보한다. 특정 응답이나 응답자만 골라 파일을 삭제하는 기능은 제공하지 않는다.
  - 테스트: 제출 응답 2건에 각각 파일이 있을 때 전체 첨부 삭제 → 두 파일 모두 다운로드 불가, 두 응답의 텍스트와 `response_count: 2` 유지, 제작자 사용량 감소 / 특정 `response_id`만 지정한 삭제 API 없음

### 배포·응답·제출 이력

- **AC82 [이벤트 기반]**: 응답자가 로그인 여부와 관계없이 폼을 작성하다 화면을 나가면, Passform은 저장 완료된 답을 다시 열 때 복원한다. 로그인한 응답의 답은 본인 계정에, 비로그인 응답의 답과 제출 전 첨부 파일은 현재 브라우저에 자동 저장한다. 로그인한 응답의 첨부 파일도 제출 전에는 현재 브라우저에만 두며 다른 기기에서 열면 재첨부를 안내한다. 저장 실패는 화면에 알린다.
  - 테스트: 로그인 사용자가 답과 파일을 입력해 저장 완료를 확인한 뒤 같은 브라우저에서 새로고침하면 둘 다 복원 / 다른 기기에서 로그인하면 답은 복원하고 파일은 다시 첨부하도록 안내 / 비로그인 사용자가 같은 브라우저에서 다시 열면 답·파일 복원, 다른 브라우저에는 초안 없음 / 모바일 브라우저에서도 복원 / 저장 실패 시 성공 표시 없음
- **AC83 [이벤트 기반]**: 로그인 응답자의 계정 초안은 본인만 보는 `in_progress` 응답이며 폼당 한 건만 유지한다. 초안만으로 폼 동의·제출을 처리하거나 제작자의 응답 수·결과·분석·내보내기를 바꾸지 않는다. 폼 동의와 제출 검증을 통과하면 본인 초안의 같은 `response_id`를 `submitted`로 바꾸고 응답 수를 1 늘린다. 제출 실패·버전 변경·폼 마감 때에는 초안을 보존하며, 폼 자체가 삭제되면 초안도 지운다.
  - 테스트: 로그인 사용자의 미완성 답 자동 저장 → `GET /me/forms/{id}/response-draft`에서 본인만 조회, 제작자 결과 `response_count: 0` / 동의 없는 제출 400 뒤 초안 유지 / 최신 버전에서 동의·필수값 검증 후 제출 → 같은 ID가 `submitted`, 최초 `submitted_at` 기록, 결과 1건, 브라우저 임시 파일 정리 / 다른 사용자의 `draft_response_id` 제출 거부 / 새 배포 버전으로 바뀌면 옛 초안을 기존 문항과 함께 보여 주되 그대로 제출 불가 / 받은 응답 전체 삭제에는 초안 유지, 폼 삭제에는 초안 제거
- **AC41 [이벤트 기반]**: 응답자가 `share_url`로 들어오면, Passform은 `open` 폼을 보여 주고, `closed`이거나 마감이 지난 폼에는 `409 form_closed`로 마감 안내를 한다.
- **AC10 [이벤트 기반]**: 제작자가 QR을 요청하면, Passform은 그 폼의 `share_url`을 담은 PNG를 반환한다. 한 번도 `open`하지 않아 `share_url`이 없으면 `409 not_open`을 반환한다.
  - 테스트: 반환 이미지를 디코드한 값 = `GET /forms/{id}/definition`의 `share_url` / 초안 → 409
- **AC43 [예외 대응]**: 응답자가 서버가 계산한 분기 경로 위의 필수 문항을 비운 채 제출하면, Passform은 `400 required_missing`과 그 문항 id를 반환하고 저장하지 않는다.
- **AC51 [이벤트 기반]**: 같은 사용자가 같은 폼에 다시 제출하거나 비로그인 사용자가 여러 번 제출하면, Passform은 제출 횟수만을 이유로 거부하지 않고 각 제출을 별도의 `Response`로 저장한다. 폼의 공개 상태·마감·필수 문항·파일 용량 등 다른 제출 조건은 그대로 적용한다.
  - 테스트: 로그인 사용자가 같은 `open` 폼에 두 번 제출 → 서로 다른 `response_id` 2개 / 비로그인 사용자가 두 번 제출 → 제출 2건 / 두 번째 제출 시 폼이 마감됐으면 `409 form_closed`
- **AC15 [이벤트 기반]**: 응답자가 제출하면, Passform은 제출한 폼 버전·문항·답 전체를 반환하고, 로그인 사용자에게는 이후 `GET /me/responses/{response_id}`에서 제출 당시 버전의 문항·답을 반환한다. 이력 조회는 `editable`·`cancellable`과 관계없이 항상 허용한다.
  - 테스트: 제출 본문의 `answers`와 이력 상세의 `content`가 문항별로 일치 / 폼의 현재 문구가 바뀐 뒤에도 옛 응답 이력에는 제출 당시 문구 표시 / 두 권한 모두 `false`인 폼의 이력 조회 가능 / 비로그인 제출도 `201`
- **AC68 [이벤트 기반]**: 응답자가 모바일 웹을 사용하면, Passform은 프로필·자동채우기·내 폼함·마감 알림·제출 이력과 허용된 응답 화면 방식의 문항 입력·분기 이동·파일 첨부·제출을 가로 스크롤 없이 터치로 사용할 수 있게 보여 준다. 분기 없는 폼의 보기 전환 버튼도 모바일에서 제공한다. 로그인 여부나 화면 너비 때문에 해당 응답 기능을 빼지 않는다.
  - 테스트: 모바일 너비 360px에서 비로그인 폼 응답·파일 첨부·제출 가능 / 분기 없는 폼에서 「한 질문씩」·「전체 질문 보기」 전환 가능, 답변 유지 / 로그인해 프로필 자동채우기, 내 폼함 저장, 제출 이력 조회 가능 / 분기 폼에서도 가로 스크롤 없음 / 키보드가 열려도 현재 입력란과 이동·제출 버튼에 접근 가능
- **AC16 [예외 대응]**: 폼이나 받은 응답 전체가 삭제되어 텍스트 이력만 남은 응답의 수정·취소에는 `409 history_only`를 반환한다. 그 외에 `Form.status`가 `closed`이거나 현재 시각이 `deadline` 이상이면 새 제출과 기존 응답의 수정·취소에 `409 form_closed`를 반환한다. 폼이 열려 있고 마감 전이어도 `settings.editable`이 `false`이면 수정에 `409 not_editable`, `settings.cancellable`이 `false`이면 취소에 `409 not_cancellable`을 반환한다. 실패한 요청은 기존 응답을 바꾸지 않는다. `deadline`이 없는 폼은 `open` 상태인 동안 마감 조건을 통과한다.
  - 테스트: 고정 시계로 `deadline`이 1분 지난 `open` 폼에 제출·수정·취소 → 모두 `409 form_closed` / `closed` 폼에서 수정·취소 → `409 form_closed` / `editable: false`, `cancellable: true`인 `open`·마감 전 폼 → 수정 409, 취소 성공 / 반대 설정 → 수정 성공, 취소 409 / 텍스트 이력만 남은 응답 수정·취소 → `409 history_only`
- **AC74 [이벤트 기반]**: 응답자가 허용된 수정을 완료하면, Passform은 제출 당시 폼 버전으로 전체 답을 다시 검증하고 같은 `Response`를 덮어쓴다. `response_id`·`form_version`·최초 `submitted_at`과 `response_count`는 유지하고 `updated_at`을 기록하며, 이력·집계·AI 결과·내보내기는 새 답을 사용한다. 응답자가 허용된 취소를 하면 그 응답만 결과와 본인 제출 이력에서 제거한다.
  - 테스트: 버전 1 응답 제출 후 폼 버전 2에서 해당 문항을 삭제 → 옛 응답 수정 화면은 버전 1 문항 표시, 버전 1 규칙으로 수정 성공 / 같은 ID·최초 제출 시각 유지, 수정 시각 기록, 응답 수 불변, 선택지 집계·이력·내보내기 값 갱신 / 그 응답 취소 → 응답 수 1 감소, 다른 응답 불변
- **AC76 [예외 대응]**: 응답자가 열어 둔 폼보다 최신 `definition_version`이 배포된 뒤 옛 `form_version`으로 새 제출을 시도하면, Passform은 `409 form_version_changed`를 반환하고 응답·파일을 만들지 않으며 최신 폼을 다시 열도록 안내한다.
  - 테스트: 버전 1을 연 뒤 제작자가 버전 2를 배포 → 버전 1로 제출 409, 기존 응답 수·첨부 불변 / 다시 열어 받은 버전 2로 제출 성공

### 내 폼함·마감 알림

- **AC17 [이벤트 기반]**: 로그인 사용자가 폼을 저장하거나 작성 중 답을 처음 자동 저장하면, Passform은 그 폼을 내 폼함에 `saved`로 한 번만 넣는다. 그 사용자가 제출하면 저장하지 않았던 폼이라도 `submitted`로 넣는다. 같은 폼에 여러 번 제출해도 내 폼함 항목은 하나이며, 응답을 취소했을 때 취소되지 않은 제출 기록(제작자의 응답 전체 삭제로 조회 전용이 된 이력 포함)이 남아 있으면 `submitted`, 하나도 남지 않으면 `saved`로 둔다. 새 작성 중 답이 있으면 제출 이력과 관계없이 `has_draft: true`로 표시한다. 화면 모드와 관계없이 누구나 저장할 수 있다.
  - 테스트: 같은 폼 2번 저장 → 내 폼함에 1건 / 작성 중 답 첫 자동 저장 → `saved`, `has_draft: true` / 저장 없이 제출 → `submitted`, `has_draft: false` / 같은 폼에 2건 제출하고 1건 취소 → `submitted` 유지 / 모든 제출 응답을 직접 취소 → `saved`, 마감 알림 대상에 다시 포함 / 제작자가 응답 전체 삭제 → 제출 이력과 `submitted` 상태 유지
- **AC18 [이벤트 기반]**: 알림 스케줄러가 돌 때 저장한 폼의 마감까지 남은 시간이 0보다 크고 `DEADLINE_REMINDER_HOURS`(기본 24) 이하이면, Passform은 그 폼을 저장했지만 제출하지 않았고 마감 알림을 켠 사용자에게 아직 보내지 않은 경우에만 이메일 1통과 사이트 내 알림 1건을 보낸다.
  - 테스트: 고정 시계로 마감 23시간 전에 스케줄러 2회 실행 → 미제출·알림 켬 사용자에게 메일 1통·알림 1건, 제출한 사용자·알림 끈 사용자 0건 / 마감 10시간 전에 저장한 폼도 다음 실행에서 1회 발송 / `deadline` 없는 폼은 발송 없음
- **AC59 [이벤트 기반] (MVP 시도 — 초안)**: AC18의 발송 조건이 맞고 사용자가 카카오톡 알림을 연결했으면, Passform은 카카오톡 알림 1건도 보낸다. 같은 사용자·같은 폼에 두 번 보내지 않는다. 연동이 안 되면 이 AC는 비포함으로 옮긴다.

### 결과·AI 결과 탐색

- **AC19 [이벤트 기반]**: 제작자가 결과를 열면, Passform은 폼의 전체 제출 응답 수와 문항·선택지 통합 통계 및 제출 버전별 통계를 반환한다. 통합 통계는 같은 `question_id`·문구·유형의 문항과 같은 선택지 문구만 합산하고, 다른 항목은 출처 버전을 붙여 구분한다.
  - 테스트: 버전 1의 `q1` 선택지 `예`·`아니요`와 버전 2의 같은 문구·유형 `q1` 선택지 `예`·`아니요`·`보류` → 통합 `예`·`아니요`는 두 버전의 건수 합, `보류`는 버전 2 건수만 반영하고 출처 표시 / 버전 2의 `q1` 문구나 유형이 달라지면 통합에서 별도 문항 / 버전별 통계는 각 버전 응답만, 전체 `response_count`는 두 버전 응답 수의 합
- **AC20 [상시 적용]**: Passform은 항상 결과 집계, AI 결과 탐색, 내보내기에서 `Response.status`가 `submitted`이고 제작자가 삭제하지 않은 응답만 다룬다. `Result.response_count`는 버전과 무관하게 이 폼에 현재 남아 있는 제출 응답 전체 수이고, AI 필터만으로는 바뀌지 않는다.
  - 테스트: 시드에 `in_progress`·`abandoned` 응답을 섞음 → 폼 전체 `response_count`는 버전별 남은 `submitted` 수의 합 / 대시보드에서 버전 1만 보거나 AI로 조건 필터링해도 폼 전체 수 불변 / 폼의 받은 응답 전체 삭제 → `response_count: 0`
- **AC75 [상시 적용]**: Passform은 AI 분석·`context` 필터 내보내기의 범위를 항상 해당 폼의 모든 제출 버전으로 고정한다. `RequestContext`는 강의 3 스키마의 네 필드만 허용한다. 각 응답은 제출 당시 문항 정의로 평가하고, AI 결과에 출처 버전과 판정 불가 범위를 표시한다. 필터 없는 내보내기만 대시보드에서 버전 범위를 고를 수 있다.
  - 테스트: 버전 1·2에 제출 응답이 있을 때 `conditions: []`의 `count`는 두 버전 합계 / 대시보드에서 버전 1 통계를 연 상태의 `analyze`도 두 버전 대상 / `{context}`에 `form_versions`·`view` 키나 `operation: "statistics"`가 있으면 스키마 검증 실패로 `400 invalid_context` / 필터 없는 JSON·YAML은 두 버전의 정의와 응답 출처 유지
- **AC77 [예외 대응]**: 제작자가 자연어 AI에 특정 버전만 분석·비교하거나 버전별 결과를 요청하면, Passform은 그 표현을 응답 조건으로 해석하거나 전체 버전 결과로 바꾸지 않고 `clarify.reason: unsupported_scope`로 전체 버전 분석과 대시보드 버전별 보기를 안내한다. 평균·비율·그룹별 집계·문항별 통계 요청도 `count`·`summarize`로 바꿔 실행하지 않고 지원 동작과 대시보드를 안내한다.
  - 테스트: “버전 1만 보여줘”·“버전별 응답 수 알려줘” → `unsupported_scope`, 파서 호출 0회 / “학과별 통계 보여줘”·“평균 나이 알려줘” → 결과 없음, `missing_operation`과 지원 동작 3개 안내 / 단순 “전체 응답 수 알려줘” → `count`
- **AC78 [이벤트 기반]**: 제작자가 모든 버전을 대상으로 자연어 조건을 요청하면, Passform은 각 응답의 제출 버전에서 조건을 판정하여 일치한 응답만 `count`·`list`·`summarize`에 사용한다. 판정 불가 응답은 일치·불일치 어느 쪽에도 넣지 않고 버전별 수와 이유를 `coverage`에 표시한다. 한 버전에 문항이 없다는 이유만으로 다른 버전의 일치 응답을 버리지 않는다.
  - 테스트: 버전 1의 소프트웨어학과 2건, 버전 2의 3건, 학과 문항이 없는 버전 3의 제출 응답 4건 → “소프트웨어학과 학생만 보여줘”는 5건과 ID 5개, `coverage.total_submitted_count: 9`, `not_evaluable_count: 4`, 버전 3의 이유 표시 / “소프트웨어학과가 아닌 학생 수”에서도 버전 3의 4건은 반대 조건에 넣지 않음 / `conditions: []`이면 9건 모두 대상
- **AC21 [상시 적용]**: Passform은 항상 매칭 평가셋에서 조건·제출 버전별 매칭 결과((`form_version`, `question_id`, `kind`, `option`) 또는 매칭 불가)가 사람 라벨과 일치하는 요청이 95% 이상이고, 매칭 불가가 아닌 틀린 매칭(오매칭)이 2.5% 이하다.
  - 판정: 평가셋과 라벨 규칙은 스파이크와 같다. 매칭을 규칙으로 구현하면 골든 테스트, LLM으로 구현하면 evals에서 잰다.
- **AC22 [상시 적용]**: Passform은 항상 조건과 `list`의 `sort.by`를 제출 버전별 문항에 대조한다. 조건이 모든 응답 보유 버전에서 매칭되지 않거나 `sort.by`가 어느 버전에도 없으면 `count: 0`을 반환하지 않고 `clarify.reason: unknown_condition`과 후보 문항을 반환한다. 일부 버전에서만 조건이 없으면 판정 가능한 버전은 계산하고 판정 불가 범위를 밝힌다.
  - 테스트: 어느 버전에도 없는 "경영학과" 조건 → `result` 없음, `options`에 버전별 학과 문항·선택지 포함 / 버전 1에만 있는 "소프트웨어학과" 조건 → 버전 1의 일치 응답 반환, 다른 버전의 판정 불가 수 표시 / 폼에 없는 "키" 정렬 → `unknown_condition`
- **AC23 [이벤트 기반]**: 분석 결과를 반환하면, Passform은 실제로 매칭된 조건·제출 버전마다 `matched` 근거와 `form_version`을 반환하고 매칭할 문항이 없는 버전은 `coverage`에 표시한다.
  - 테스트: 버전 1·2의 학과 문항에서 같은 조건이 매칭되고 버전 3에는 문항이 없음 → `matched`는 버전 1·2의 `question_id` 근거 2개, `coverage`는 버전 3의 판정 불가 수·조건을 표시
- **AC24 [상시 적용]**: Passform은 항상 버전별로 각 조건을 참·거짓·판정 불가로 평가한다. `all`은 하나라도 거짓이면 거짓, 전부 참이면 참이고, `any`는 하나라도 참이면 참, 전부 거짓이면 거짓이다. 나머지는 판정 불가이며 `not`은 `any`의 참·거짓만 뒤집는다. 판정 불가 응답은 어느 결과에도 넣지 않는다.
  - 테스트: `not` + [소프트웨어학과] → 학과 문항이 있는 버전의 비해당 응답만 세고 문항이 없는 버전은 제외 / `any` + [소프트웨어학과, 4학년]에서 학과 문항이 없어도 4학년이 참이면 포함 / `conditions: []`의 `count`·`list` → 전체 `submitted` 수·ID 목록
- **AC25 [예외 대응]**: 단일 선택 문항(`multiple_choice`, `dropdown`)의 서로 다른 선택지 둘 이상이 `match: all`로 묶이거나, 같은 자연어 조건이 버전 사이에서 뜻이 다른 여러 문항에 매칭되면, Passform은 0이나 추측한 결과를 반환하지 않고 `clarify.reason: ambiguous_match`로 되묻는다. `checkbox`는 복수 선택이므로 같은 문항의 두 선택지를 `all`로 계산할 수 있다.
  - 테스트: 소프트웨어학과·컴퓨터공학과(dropdown) + `all` → `ambiguous_match` / 기획·디자인(checkbox) + `all` → `ok` / 한 버전의 “소속 학과”와 다른 버전의 “희망 학과”가 같은 조건 후보가 됨 → 버전별 후보를 제시하며 되묻기
- **AC26 [예외 대응]**: 조건이 2개 이상인데 `match`가 없으면, Passform은 결과를 내지 않고 `clarify.reason: missing_match`로 "모두 해당"인지 "하나라도 해당"인지 되묻는다. 자연어 요청에 긍정·부정 조건이 섞여 단일 `match`로 표현할 수 없을 때도 같은 사유로 결과 없이 요청을 나누어 달라고 안내한다. 조건이 0개 또는 1개이고 `match`가 없으면 이 사유로 되묻지 않는다.
  - 테스트: "소프트웨어학과가 아닌 4학년 수" → `missing_match`, `result` 없음, `options: []`, 요청 분리 안내 / 조건 0개·1개에서 `match` 생략 → `missing_match` 아님
- **AC27 [예외 대응]**: 파서 출력의 `operation`이 `null`이거나 원문이 지원하지 않는 통계 동작을 명시하면, Passform은 이를 다른 동작으로 바꾸지 않고 `clarify.reason: missing_operation`과 지원하는 동작 `count`·`list`·`summarize`를 반환한다. 문항별 통계 요청에는 대시보드도 안내한다.
  - 테스트: 모의 파서가 `{"conditions":[],"operation":null}` 반환 → 파서 호출 1회, `missing_operation`, `options`에 세 동작 포함 / “학과별 통계”를 파서가 `summarize`로 잘못 해석해도 결과 없이 대시보드 안내
- **AC28 [예외 대응]**: `list`에서 `sort.by`가 `long_answer`·`checkbox` 문항이거나, `short_answer` 값이 정규화 가능한 숫자·날짜가 아니거나, 버전 사이에서 값 형식·선택지 순서를 비교할 수 없으면, Passform은 정렬하지 않고 `clarify.reason: unsortable_field`와 정렬 가능한 문항 목록을 반환한다. 선택형 문항은 선택지 정의 순서로 정렬한다. 일부 제출 버전에만 정렬 문항이 없으면 그 버전 응답은 맨 뒤에 두고 범위를 알린다.
  - 테스트: `sort.by` = 자기소개(long_answer) → `unsortable_field`, `options`에 자기소개 없음 / 나이 값에 "23살" 포함 → `unsortable_field` / `input_format`이 날짜인 `YYYY-MM-DD` 값은 날짜순으로 정렬 / 버전 간 같은 선택지의 정의 순서가 달라 비교 불가 → `unsortable_field` / 버전 2에 나이 문항 없음 → 버전 2 응답을 맨 뒤에 놓고 `coverage.sort_unavailable_by_version`에 버전 2 표시
- **AC61 [이벤트 기반]**: `list` 요청에 `sort.by`가 있으면, Passform은 모든 제출 버전의 응답을 해당 문항의 값과 `sort.order`로 한꺼번에 정렬하고 방향이 없으면 `asc`로 처리한다. 빈 값이나 그 버전에 정렬 문항이 없는 응답은 맨 뒤, 같은 값은 `response_id` 오름차순이다. `count`·`summarize`에 붙은 `sort`는 무시한다.
  - 테스트: 버전 1·2의 `list` + "나이순" → 두 버전 전체의 나이 오름차순, 같은 값은 `response_id` 오름차순 / `count` + 정렬할 수 없는 문항의 `sort` → 정렬 관련 `clarify` 없이 같은 `count`
- **AC29 [예외 대응]**: `conditions`에 "미응답" 조건과 `match: not`이 함께 오면, Passform은 결과를 내지 않고 `clarify.reason: double_negation`으로 확정 요청("3번 문항 미응답자 수")을 확인받는다.
- **AC30 [예외 대응]**: 파서 출력이 JSON이 아니거나 스키마 검증에 실패하면, Passform은 오류 메시지를 붙여 파서를 1회만 다시 부르고, 다시 실패하면 결과를 추측하지 않고 `clarify.reason: parse_failed`를 반환한다.
  - 테스트: 모의 파서가 `"group_by"` 키를 두 번 출력 → 파서 호출 2회, `parse_failed` / 한 번 실패 후 정상 → `ok`
- **AC31 [예외 대응]**: 응답 원문(`Response.content`)에 지시문이 들어 있으면, Passform은 그것을 데이터로만 다룬다 — `count`, `list`는 바뀌지 않고, `summary`는 그 지시를 따르지 않는다.
  - 판정: `count`·`list`·요약에 쓴 `response_ids`는 골든 테스트, `summary`가 지시를 따르지 않는지는 레드티밍 실습(evals)
- **AC32 [이벤트 기반]**: 제작자가 되묻기 후 확정한 `context`로 요청하면, Passform은 파서를 부르지 않고 5절 `clarify` 표 3~8행의 순서로 검사한 뒤 전체 제출 버전에서 결과를 낸다. `context`가 강의 3 스키마에 맞지 않으면 `400 invalid_context`를 반환한다.
  - 테스트: `{context}` 요청 → 파서 호출 0회, `{query}`로 같은 context가 나왔을 때와 같은 결과·판정 불가 범위 / `match: "and"` 또는 `operation: "statistics"`가 든 context → 400
- **AC62 [이벤트 기반]**: 제작자가 `list` 결과의 `response_id`로 해당 폼의 응답 상세를 조회하면, Passform은 그 제출 응답의 문항별 답을 반환한다. 다른 폼의 응답 ID는 `404`, 다른 사용자가 제작한 폼은 `403`을 반환한다. 화면에 보이는 응답은 `response_ids`의 순서와 개수(`result.count`)를 따른다.

### 내보내기

- **AC48 [이벤트 기반]**: 제작자가 JSON·YAML로 내보내면, Passform은 `{form_versions: [{form_version, form}], responses: [{response_id, form_version, ...}]}`를 반환하고 두 형식을 읽었을 때 같은 객체이며, 각 버전의 `form`은 `form.schema.json`을 통과한다. CSV는 버전과 제출 당시 문항 문구를 구분한 첫 행 뒤에 응답 하나당 한 행을 둔다.
  - 테스트: 버전 2개인 시드 폼의 JSON·YAML을 각각 파싱해 비교 → 같음, 두 폼 정의가 각각 유효 / CSV 행 수 = 헤더 1 + 모든 버전의 `submitted` 응답 수, 버전별 문항 열 구분, checkbox 답 "기획; 디자인", 건너뛴 문항 빈 칸
- **AC49 [이벤트 기반]**: 제작자가 AI 필터 결과를 `context`로 내보내기를 고르면, Passform은 같은 `context`로 전체 제출 버전을 분석했을 때 **일치한 응답만** 내보낸다. 판정 불가 응답은 포함하지 않는다. `context.operation`이 `list`이면 분석의 `result.response_ids`와 같은 순서로, 그 밖에는 버전 오름차순 → 버전 안의 `submitted_at` 오름차순 → `response_id` 오름차순으로 내보낸다. `context`가 결과를 내지 못하면(`clarify`) 내보내지 않고 같은 `clarify`를 반환한다.
  - 테스트: 버전 1·2에서 조건 일치, 버전 3에서 조건 판정 불가이고 ID·제출 시각 순서가 다름 → `list` 내보내기 JSON·YAML·CSV의 응답 집합·순서가 분석의 `result.response_ids`와 일치하고 버전 3 제외 / `count`·`summarize` 내보내기는 버전·제출 시각·ID 순서 / `clarify`이면 파일을 만들지 않음
- **AC50 [상시 적용]**: Passform은 항상 CSV 셀이 `=`, `+`, `-`, `@`로 시작하면 앞에 `'`를 붙여 내보낸다 — 응답 원문이 스프레드시트에서 수식으로 실행되지 않게 한다.
  - 테스트: 답이 `=HYPERLINK(...)`인 시드 응답 → CSV 셀 = `'=HYPERLINK(...)`

### 품질 목표 (계획서)

- **AC33 [이벤트 기반]**: `GET /health` 요청이 오면, Passform은 200과 `{"status":"ok"}`를 반환한다.
- **AC34 [상시 적용]**: Passform은 항상 동시 사용자 300명이 10분 동안 요청할 때 요청 성공률 99% 이상, p95 응답시간 2초 이하를 유지한다. 실패는 5xx와 타임아웃이다.
  - 판정: 부하 테스트 스크립트(`load/`). 대상은 폼 조회·자동채우기·제출·내 폼함이다. `analyze`는 LLM 응답 지연이 커서 이 AC에서 제외한다.
- **AC35 [상시 적용]**: Passform은 항상 AI 결과 탐색 평가셋(100건 이상)에서 질의의 95% 이상에 기대 결과 또는 기대 되묻기 사유와 일치하는 응답을 낸다.
  - 판정: `evals/evalset.jsonl`(강의 7). 질의마다 정답(`count`, `response_ids` 또는 `clarify.reason`)을 미리 정하고 일치하면 성공으로 센다. `summarize`는 `response_ids`로 판정한다.
- **AC36 [상시 적용]**: 핵심 도메인 로직(프로필·자동채우기·폼 정의·분기·응답·알림·분석·내보내기의 service·domain 계층)의 라인 테스트 커버리지는 항상 70% 이상이다.
  - 판정: CI의 커버리지 리포트(JaCoCo)

### 캡디 추가 개발 (초안 — 착수할 때 확정하고 골든 케이스를 쓴다)

- **AC52 [이벤트 기반] (추가 1 · 캘린더)**: 사용자가 캘린더의 한 달을 열면, Passform은 내 폼함에 저장한 폼 중 `deadline`이 있는 폼을 Asia/Seoul 기준 마감 날짜에 `state`와 함께 보여 준다.
- **AC53 [상시 적용] (추가 2 · AI 다듬기)**: Passform은 항상 장문 답의 글자 수를 공백 포함으로 세고, 문항의 `input_format` 글자 수 제한과 함께 보여 준다 — 화면과 서버가 같은 규칙으로 센다.
- **AC54 [이벤트 기반] (추가 2 · AI 다듬기)**: 응답자가 맞춤법 교정이나 글 다듬기를 요청하면, Passform은 제안만 반환하고, 응답자가 수락할 때만 답을 바꾼다. 원문을 자동으로 바꾸지 않는다.
- **AC55 [상시 적용] (추가 2 · AI 다듬기)**: Passform은 항상 다듬기 요청에 그 장문 문항의 답만 LLM에 보내고, 다른 문항(특히 `profile_key` 문항)의 값은 보내지 않는다.

| AC | 골든 케이스 id (`tests/harness/golden_cases.yaml`, 강의 5) 또는 판정 위치 | 구현 상태 |
|---|---|---|
| AC1 | `first_login_creates_user` | 미구현 |
| AC2 | `unauthenticated_401`, `other_users_response_404`, `non_owner_analyze_403` | 미구현 |
| AC3 | `profile_address_birth_date_allowed`, `profile_rejects_sensitive_key` | 미구현 |
| AC4 | `autofill_selected_profile` | 미구현 |
| AC5 | `deleted_profile_not_used` | 미구현 |
| AC6 | `autofill_own_info_only`, `autofill_option_mismatch`, `autofill_value_reviewable` | 미구현 |
| AC7 | `template_question_copies_input_format`, `template_profile_key_needs_confirmation` | 미구현 |
| AC8 | `branch_to_missing_target`, `branch_backward`, `branch_condition_invalid` | 미구현 |
| AC9 | `skipped_questions_not_required`, `skipped_answers_dropped` | 미구현 |
| AC10 | `qr_decodes_to_share_url`, `qr_draft_409` | 미구현 |
| AC11 | `payment_link_https_only` | 미구현 |
| AC12 | `every_open_form_needs_consent_template`, `unformatted_form_needs_consent_template` | 미구현 |
| AC13 | `consent_lists_input_formats`, `consent_generic_answer_item` | 미구현 |
| AC14 | `submit_without_consent`, `unformatted_submit_needs_consent` | 미구현 |
| AC15 | `submitted_content_roundtrip`, `submission_uses_original_form_version`, `anonymous_submit` | 미구현 |
| AC16 | `submit_after_deadline`, `edit_after_deadline`, `edit_closed_form`, `history_only_not_editable`, `not_editable_patch`, `not_cancellable_delete` | 미구현 |
| AC17 | `save_form_once`, `submit_adds_to_inbox`, `partial_cancel_keeps_submitted`, `cancel_returns_to_saved`, `bulk_delete_keeps_submitted_history` | 미구현 |
| AC18 | `deadline_reminder_once`, `reminder_saved_within_window`, `reminder_opt_out` | 미구현 |
| AC19 | `results_option_counts_combined_and_by_version`, `results_changed_question_separate` | 미구현 |
| AC20 | `submitted_only`, `response_count_across_versions` | 미구현 |
| AC21 | `matching_labeled_set` (규칙이면 골든 / LLM이면 evals) | 스파이크 대기 |
| AC22 | `unknown_condition_all_versions`, `unknown_sort_field`, `condition_missing_one_version_keeps_matches` | 미구현 |
| AC23 | `matched_for_applicable_versions`, `coverage_for_unavailable_version` | 미구현 |
| AC24 | `match_not_excludes_unknown`, `match_any_known_true_with_unknown` | 미구현 |
| AC25 | `ambiguous_single_choice`, `checkbox_all_ok`, `ambiguous_question_meaning_across_versions` | 미구현 |
| AC26 | `missing_match_two_conditions` | 미구현 |
| AC27 | `missing_operation`, `unsupported_statistics_not_mapped_to_summary` | 미구현 |
| AC28 | `unsortable_long_answer`, `unsortable_text_age`, `sort_missing_version_last` | 미구현 |
| AC29 | `double_negation_unanswered` | 미구현 |
| AC30 | `extra_key_retry_once` | 미구현 |
| AC31 | `injection_in_response` (+ 레드티밍) | 미구현 |
| AC32 | `confirmed_context_skips_parser`, `confirmed_context_all_versions`, `invalid_context_400` | 미구현 |
| AC33 | `health` | 미구현 |
| AC34 | 부하 테스트 `load/` | 미구현 |
| AC35 | evals `evals/evalset.jsonl` | 미구현 |
| AC36 | CI 커버리지 리포트 | 미구현 |
| AC37 | `ui_mode_persists`, `ui_mode_no_permission_change` | 미구현 |
| AC38 | `draft_hidden_from_others`, `first_draft_version_null`, `open_creates_share_url`, `first_open_creates_version_one` | 미구현 |
| AC39 | `created_forms_owner_only` | 미구현 |
| AC40 | `copy_form_without_responses` | 미구현 |
| AC41 | `share_url_open_form`, `share_url_closed_form` | 미구현 |
| AC42 | `quick_and_basic_same_definition`, `own_info_setting_survives_mode_switch` | 미구현 |
| AC43 | `required_missing_visited_only` | 미구현 |
| AC44 | `branch_all_conditions`, `branch_any_conditions`, `branch_first_rule_wins` | 미구현 |
| AC45 | `branch_graph_matches_rules` | 미구현 |
| AC46 | `unbranched_display_modes`, `branched_display_modes`, `section_page_branch_only_at_end`, `section_page_rejects_question_target`, `one_question_allows_question_branch` | 미구현 |
| AC47 | `unbranched_respondent_switch_preserves_answers`, `branched_mode_fixed`, `all_questions_sections_visible`, `section_page_first_screen`, `one_question_intro_then_first_question` | 미구현 |
| AC48 | `export_json_yaml_same`, `export_csv_rows`, `export_form_versions_separate`, `export_flat_responses_with_version` | 미구현 |
| AC49 | `export_filtered_matches_analyze`, `export_non_list_time_order`, `export_excludes_not_evaluable` | 미구현 |
| AC50 | `csv_formula_escaped` | 미구현 |
| AC51 | `repeat_submission_allowed`, `anonymous_repeat_submission_allowed` | 미구현 |
| AC52~AC55 | (추가 개발 — 착수 시 작성) | 추가 개발 |
| AC56 | `draft_saves_incomplete`, `open_runs_semantic_checks`, `reopen_validates_draft_version` | 미구현 |
| AC57 | `invalid_status_transition`, `share_url_stable_on_reopen` | 미구현 |
| AC58 | `form_edit_creates_immutable_version`, `attachment_change_creates_immutable_version`, `operating_setting_keeps_version` | 미구현 |
| AC59 | `kakao_reminder_once` (연동 시) | MVP 시도 |
| AC60 | `respondent_progress_known_path`, `respondent_progress_branch_unknown` | 미구현 |
| AC61 | `list_sort_default_asc`, `list_sort_all_versions`, `non_list_sort_ignored` | 미구현 |
| AC62 | `creator_list_response_detail`, `creator_response_other_form_404` | 미구현 |
| AC63 | `form_and_question_attachments_allowed`, `attachment_api_version_atomic`, `draft_attachment_keeps_version`, `question_attachment_owner_only_draft`, `creator_attachment_rejects_type` | 미구현 |
| AC64 | `response_attachment_per_question_25mb`, `response_attachment_atomic_failure`, `response_attachment_access` | 미구현 |
| AC65 | `storage_quota_at_limit`, `storage_quota_rejects_overflow`, `response_file_charged_to_creator` | 미구현 |
| AC66 | `creator_delete_all_responses`, `bulk_delete_preserves_submitter_history`, `bulk_delete_releases_storage` | 미구현 |
| AC67 | `delete_form_offers_export`, `delete_form_export_failure_keeps_data`, `delete_form_preserves_submitter_history` | 미구현 |
| AC68 | `mobile_respondent_full_flow`, `mobile_respondent_no_horizontal_scroll` | 미구현 |
| AC69 | `mobile_quick_builder_publish`, `mobile_basic_builder_desktop_notice` | 미구현 |
| AC70 | `delete_all_response_attachments`, `attachment_purge_keeps_response_count` | 미구현 |
| AC71 | `quick_purpose_audience_recommendations`, `quick_custom_question_preview` | 미구현 |
| AC72 | `own_info_confirmation_both_builders`, `own_info_reset_after_question_change` | 미구현 |
| AC73 | `date_format_accepts_valid_date`, `date_format_rejects_invalid_date`, `phone_format_normalizes_and_checks_length`, `email_format_checks_structure`, `text_format_trims_and_checks_required` | 미구현 |
| AC74 | `response_edit_overwrites_same_id`, `response_edit_uses_original_version`, `response_cancel_removes_one` | 미구현 |
| AC75 | `analyze_all_versions_fixed_scope`, `context_matches_lecture03_schema`, `full_export_groups_versions` | 미구현 |
| AC76 | `stale_form_version_rejected`, `latest_form_version_submits` | 미구현 |
| AC77 | `natural_language_version_scope_rejected`, `unsupported_statistics_explained`, `all_responses_count_supported` | 미구현 |
| AC78 | `all_versions_condition_filter_with_coverage`, `negation_does_not_count_unknown`, `no_condition_counts_all` | 미구현 |
| AC79 | `basic_builder_edit_save_reopen`, `question_and_section_duplicate_new_ids`, `duplicate_resets_branch_and_profile_key` | 미구현 |
| AC80 | `basic_builder_five_question_types`, `basic_builder_settings_roundtrip`, `published_controls_match_builder` | 미구현 |
| AC81 | `builder_preview_unsaved_definition`, `builder_preview_branch_path`, `builder_preview_no_response` | 미구현 |
| AC82 | `respondent_draft_restores_same_browser`, `login_draft_text_cross_device`, `draft_file_same_browser_only` | 미구현 |
| AC83 | `in_progress_owner_only_and_not_counted`, `submit_draft_same_response_id`, `stale_draft_not_submitted` | 미구현 |
| AC84 | `creator_draft_autosave_both_modes`, `creator_autosave_no_publish`, `creator_autosave_failure_visible` | 미구현 |
