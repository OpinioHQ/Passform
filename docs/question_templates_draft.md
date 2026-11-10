# 추천 문항 초안 (E13, #58)

> 에이전트 초안. 사람이 검토·수정해 승인한 표만 Flyway V3 초기 데이터로 넣는다.
> 근거: SPEC 179행(목적 예시 4개·대상 예시 3개, 예약 관리·퀴즈 채점 아님), 113행(응답 모양), AC7(복사 필드, `profile_key`는 템플릿에 없음).

## 저장값 (API 쿼리 값과 같다)

| 화면 표시 | purpose | | 화면 표시 | audience |
|---|---|---|---|---|
| 신청 | `apply` | | 고등학생 | `high_school` |
| 설문조사 | `survey` | | 대학생 | `university` |
| 예약 | `reservation` | | 직장인 | `worker` |
| 퀴즈 | `quiz` | | 누구나 | `anyone` (쿼리 값만, 행 없음) |
| (기본 정보) | `all` | | (목적 공통) | `all` |

- `template_id`는 `{purpose}_{all|high|univ|work}_{두 자리}`.
- `input_format`은 `short_answer`에만 단다(form.schema.json 143행). 값은 `{"kind": ...}`만, placeholder 없음.
- 선택지가 없는 유형은 options 칸을 비우고 SQL에서는 `NULL`로 넣는다(화면에서 추가할 때 `[]`로 바꾼다).

## 표 (37개: 기본 정보 3 + 대학생 공통 2 + 목적마다 공통 2~4 + 대상별 1~2)

- 1차 승인(40개) 뒤 사람 결정으로 고쳤다: 이름·휴대폰 번호·생년월일을 기본 정보(`basic_*`, purpose·audience 모두 `all`)로 두어 어떤 선택에도 함께 추천하고, 겹치는 `apply_all_01`·`apply_all_02`·`reservation_all_03`·`quiz_all_01`을 뺐다. 대상 "누구나"(`anyone`)는 목적 공통 문항과 기본 정보만 받는다.
- 2차 사람 결정: 대학생이면 목적과 상관없이 학번·학과(전공)를 추천 문항에 넣는다. 대상 공통 행 `univ_01`·`univ_02`(purpose `all`, audience `university`)를 더하고 겹치는 `apply_univ_01`·`apply_univ_02`·`reservation_univ_01`·`quiz_univ_01`을 뺐다.
- 조회 순서: 기본 정보 먼저, 그다음 `template_id` 오름차순.

| template_id | purpose | audience | question_note | question_type | options | input_format |
|---|---|---|---|---|---|---|
| basic_01 | all | all | 이름 | short_answer | | name |
| basic_02 | all | all | 휴대폰 번호 | short_answer | | mobile_phone |
| basic_03 | all | all | 생년월일 | short_answer | | date |
| univ_01 | all | university | 학번 | short_answer | | student_id |
| univ_02 | all | university | 학과(전공) | short_answer | | |
| apply_all_03 | apply | all | 이메일 | short_answer | | email |
| apply_all_04 | apply | all | 신청 동기를 적어 주세요 | long_answer | | |
| apply_high_01 | apply | high_school | 학년 | dropdown | 1학년 / 2학년 / 3학년 | |
| apply_high_02 | apply | high_school | 보호자 연락처 | short_answer | | mobile_phone |
| apply_work_01 | apply | worker | 소속(회사·부서) | short_answer | | |
| apply_work_02 | apply | worker | 참석 가능한 시간대를 모두 골라 주세요 | checkbox | 평일 오전 / 평일 오후 / 평일 저녁 / 주말 | |
| survey_all_01 | survey | all | 전반적으로 얼마나 만족하셨나요? | multiple_choice | 매우 만족 / 만족 / 보통 / 불만족 / 매우 불만족 | |
| survey_all_02 | survey | all | 가장 좋았던 점을 적어 주세요 | long_answer | | |
| survey_all_03 | survey | all | 개선했으면 하는 점을 적어 주세요 | long_answer | | |
| survey_all_04 | survey | all | 다른 사람에게 추천하시겠어요? | multiple_choice | 예 / 아니요 / 잘 모르겠어요 | |
| survey_high_01 | survey | high_school | 학년 | dropdown | 1학년 / 2학년 / 3학년 | |
| survey_high_02 | survey | high_school | 관심 있는 진로 분야를 모두 골라 주세요 | checkbox | 인문·사회 / 자연·공학 / 예체능 / 의료·보건 / 아직 모르겠어요 | |
| survey_univ_01 | survey | university | 학년 | dropdown | 1학년 / 2학년 / 3학년 / 4학년 / 5학년 이상 | |
| survey_univ_02 | survey | university | 소속 단과대학 | short_answer | | |
| survey_work_01 | survey | worker | 경력 연차 | dropdown | 1년 미만 / 1~3년 / 4~6년 / 7~10년 / 11년 이상 | |
| survey_work_02 | survey | worker | 근무 형태 | multiple_choice | 출근 / 재택 / 혼합 | |
| reservation_all_01 | reservation | all | 예약자 성함 | short_answer | | name |
| reservation_all_02 | reservation | all | 예약 날짜 | short_answer | | date |
| reservation_all_04 | reservation | all | 희망 시간대 | multiple_choice | 오전 / 오후 / 저녁 | |
| reservation_high_01 | reservation | high_school | 학교 이름 | short_answer | | |
| reservation_high_02 | reservation | high_school | 보호자 연락처 | short_answer | | mobile_phone |
| reservation_univ_02 | reservation | university | 사용 목적 | multiple_choice | 스터디 / 동아리 활동 / 수업 과제 / 기타 | |
| reservation_work_01 | reservation | worker | 소속(회사·부서) | short_answer | | |
| reservation_work_02 | reservation | worker | 이용 인원 | dropdown | 1명 / 2~4명 / 5~9명 / 10명 이상 | |
| quiz_all_02 | quiz | all | 다음 중 정답을 하나 골라 주세요 | multiple_choice | 보기 1 / 보기 2 / 보기 3 / 보기 4 | |
| quiz_all_03 | quiz | all | 정답을 모두 골라 주세요 | checkbox | 보기 1 / 보기 2 / 보기 3 / 보기 4 | |
| quiz_all_04 | quiz | all | 답을 짧게 적어 주세요 | short_answer | | |
| quiz_high_01 | quiz | high_school | 학년·반·번호 | short_answer | | |
| quiz_high_02 | quiz | high_school | 풀이 과정을 적어 주세요 | long_answer | | |
| quiz_univ_02 | quiz | university | 자신의 생각을 근거와 함께 서술해 주세요 | long_answer | | |
| quiz_work_01 | quiz | worker | 소속 부서 | short_answer | | |
| quiz_work_02 | quiz | worker | 교육 내용 중 가장 기억에 남는 것을 적어 주세요 | long_answer | | |
