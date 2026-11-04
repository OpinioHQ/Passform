# 폼 문항 정보

- 상태: 끝의 Q2·Q3는 리뷰에서 팀이 확정한다.
- 스키마: `docs/api/form_question_info.schema.json` / 예시: 아래 JSON(시드 폼 4개에서 생성해 스키마 통과를 확인했다. 생성한 파일은 저장소에 넣지 않았다)
- 버전별 문항 ID, 제목, 유형, 선택지, 개인정보 여부에 스파이크가 쓰던 번호를 더했다.

## 무엇인가

분석 흐름이 폼을 볼 때 쓰는 형태다. **저장하지 않는다.** 요청마다 `form_versions`의 폼 정의(`form.schema.json`)에서 코드로 만든다. 응답 값은 들어가지 않는다.

```json
{
  "form_id": "form_b",
  "title": "프로젝트 팀원 모집",
  "latest_version": 3,
  "versions": [
    {
      "form_version": 1,
      "questions": [
        {
          "number": 1,
          "latest_number": null,
          "question_id": "q1",
          "question_note": "소속 학과",
          "question_type": "dropdown",
          "options": ["소프트웨어학과", "컴퓨터공학과", "경영학과"],
          "is_personal": true
        }
      ]
    }
  ]
}
```

## 만드는 규칙

| 필드 | 규칙 | 근거 |
|---|---|---|
| `versions` | 현재 `submitted` 응답이 남은 버전. 없으면 최신 버전 하나 | SPEC 343행, AC22 |
| `number` | 그 버전에서 섹션 순서대로 이어 센 번호 | 스파이크 |
| `latest_number` | 같은 `question_id`의 최신 버전 번호. 없으면 `null` | `docs/spikes/condition_matching_data/label_rules.md` 4절(팀 결정) |
| `question_id` | 버전을 넘어 같은 문항의 기준 | AC19, 시드 README |
| `is_personal` | `profile_key`가 있거나 `input_format.kind`가 이름·휴대전화·집전화·주소·학번·이메일 | SPEC 168행 |

## 누가 쓰나

| 쓰는 곳 | 쓰는 필드 |
|---|---|
| 매처(규칙) | `options`, `question_note`, `latest_number` |
| 매처(LLM) | `is_personal`을 뺀 전부. 아래 입력 형식 |
| `sort.by`·`target` 해석 | `question_note`, `question_type`, `latest_number` |
| 되묻기 후보 | `form_version`, `question_id`, `question_note`, `options` |
| 요약 대상 판정 | `question_type`, `is_personal` |

요약 가능 문항은 `short_answer`·`long_answer` 중 `is_personal: false`다(SPEC 316행). 분기 건너뜀 판정은 이 정보가 아니라 폼 정의 전체와 응답 경로로 한다.

## 매처 입력과 출력 (스파이크 그대로)

입력은 `docs/spikes/condition_matching_prompt.md`의 형식이다. 프롬프트를 고치지 않으려고 필드를 맞췄다.

```
<conditions>{"c0":"컴공","c1":"주말"}</conditions>
<form>{"versions":[{"form_version":1,"questions":[{"number":1,"latest_number":1,"question_id":"q3","question_note":"소속 학과","question_type":"dropdown","options":[...]}]}]}</form>
```

출력은 조건·버전마다 허용 값 하나다. 스키마 `enum`으로 강제하고 서버에서 다시 확인한다.

```json
{ "c0": { "v1": "q3|option|컴퓨터공학과", "v2": "q3|option|컴퓨터공학과" }, "c1": { "v1": "NONE", "v2": "q11|option|주말" } }
```

값은 `문항id|option|선택지`, `문항id|answered`, `문항id|unanswered`, `NONE`이다(`docs/spikes/condition_matching_data/label_rules.md` 1절). `NONE`은 `matched`에 넣지 않고 그 버전을 판정 불가로 둔다.

## 결정할 것

| # | 내용 | 제안 | 이유 |
|---|---|---|---|
| Q1 | 개인정보 문항의 **문구와 선택지**를 매처 LLM에 보내는가. 예: 소속 학과(`profile_key: department`)의 선택지 | **결정(2026-11-04, 사용자): 보낸다. 응답 값만 금지** | AGENTS 3절 1항의 금지 대상을 응답 값으로 읽었다. 안 보내면 "컴공 몇 명"을 풀 수 없다 |
| Q2 | 저장 여부 | 저장 안 함 | 폼 정의에서 항상 만들 수 있다 |
| Q3 | 프론트에도 이 형태를 줄지 | 주지 않음 | 프론트는 `analyze` 응답과 공개 폼 정의만 쓴다 |
