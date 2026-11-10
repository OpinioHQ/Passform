-- 빠른 모드 추천 문항(SPEC 5절 179행, #58). 에이전트 초안을 사람이 검토·승인한 표(docs/question_templates_draft.md) 그대로다.
-- purpose: apply(신청)·survey(설문조사)·reservation(예약)·quiz(퀴즈), audience: high_school·university·worker, 목적 공통은 audience all.
-- 기본 정보(basic_*)는 purpose·audience가 모두 all이고 어떤 선택에도 함께 추천한다(사람 결정, #58). 화면의 "누구나"(anyone)는 공통 문항과 기본 정보만 받는다.
-- 대상 공통(purpose all, audience university: univ_*)은 목적과 상관없이 그 대상에 추천한다(사람 결정, #58).
-- input_format은 short_answer에만 둔다(form.schema.json). profile_key는 템플릿에 두지 않는다(AC7).
-- 배포된 폼은 template_id만 보존하므로 이 행의 문구를 고치지 말고 새 ID로 행을 더한다.
INSERT INTO question_templates (template_id, purpose, audience, question_note, question_type, options, input_format) VALUES
('basic_01', 'all', 'all', '이름', 'short_answer', NULL, '{"kind": "name"}'),
('basic_02', 'all', 'all', '휴대폰 번호', 'short_answer', NULL, '{"kind": "mobile_phone"}'),
('basic_03', 'all', 'all', '생년월일', 'short_answer', NULL, '{"kind": "date"}'),
('univ_01', 'all', 'university', '학번', 'short_answer', NULL, '{"kind": "student_id"}'),
('univ_02', 'all', 'university', '학과(전공)', 'short_answer', NULL, NULL),
('apply_all_03', 'apply', 'all', '이메일', 'short_answer', NULL, '{"kind": "email"}'),
('apply_all_04', 'apply', 'all', '신청 동기를 적어 주세요', 'long_answer', NULL, NULL),
('apply_high_01', 'apply', 'high_school', '학년', 'dropdown', '["1학년", "2학년", "3학년"]', NULL),
('apply_high_02', 'apply', 'high_school', '보호자 연락처', 'short_answer', NULL, '{"kind": "mobile_phone"}'),
('apply_work_01', 'apply', 'worker', '소속(회사·부서)', 'short_answer', NULL, NULL),
('apply_work_02', 'apply', 'worker', '참석 가능한 시간대를 모두 골라 주세요', 'checkbox', '["평일 오전", "평일 오후", "평일 저녁", "주말"]', NULL),
('survey_all_01', 'survey', 'all', '전반적으로 얼마나 만족하셨나요?', 'multiple_choice', '["매우 만족", "만족", "보통", "불만족", "매우 불만족"]', NULL),
('survey_all_02', 'survey', 'all', '가장 좋았던 점을 적어 주세요', 'long_answer', NULL, NULL),
('survey_all_03', 'survey', 'all', '개선했으면 하는 점을 적어 주세요', 'long_answer', NULL, NULL),
('survey_all_04', 'survey', 'all', '다른 사람에게 추천하시겠어요?', 'multiple_choice', '["예", "아니요", "잘 모르겠어요"]', NULL),
('survey_high_01', 'survey', 'high_school', '학년', 'dropdown', '["1학년", "2학년", "3학년"]', NULL),
('survey_high_02', 'survey', 'high_school', '관심 있는 진로 분야를 모두 골라 주세요', 'checkbox', '["인문·사회", "자연·공학", "예체능", "의료·보건", "아직 모르겠어요"]', NULL),
('survey_univ_01', 'survey', 'university', '학년', 'dropdown', '["1학년", "2학년", "3학년", "4학년", "5학년 이상"]', NULL),
('survey_univ_02', 'survey', 'university', '소속 단과대학', 'short_answer', NULL, NULL),
('survey_work_01', 'survey', 'worker', '경력 연차', 'dropdown', '["1년 미만", "1~3년", "4~6년", "7~10년", "11년 이상"]', NULL),
('survey_work_02', 'survey', 'worker', '근무 형태', 'multiple_choice', '["출근", "재택", "혼합"]', NULL),
('reservation_all_01', 'reservation', 'all', '예약자 성함', 'short_answer', NULL, '{"kind": "name"}'),
('reservation_all_02', 'reservation', 'all', '예약 날짜', 'short_answer', NULL, '{"kind": "date"}'),
('reservation_all_04', 'reservation', 'all', '희망 시간대', 'multiple_choice', '["오전", "오후", "저녁"]', NULL),
('reservation_high_01', 'reservation', 'high_school', '학교 이름', 'short_answer', NULL, NULL),
('reservation_high_02', 'reservation', 'high_school', '보호자 연락처', 'short_answer', NULL, '{"kind": "mobile_phone"}'),
('reservation_univ_02', 'reservation', 'university', '사용 목적', 'multiple_choice', '["스터디", "동아리 활동", "수업 과제", "기타"]', NULL),
('reservation_work_01', 'reservation', 'worker', '소속(회사·부서)', 'short_answer', NULL, NULL),
('reservation_work_02', 'reservation', 'worker', '이용 인원', 'dropdown', '["1명", "2~4명", "5~9명", "10명 이상"]', NULL),
('quiz_all_02', 'quiz', 'all', '다음 중 정답을 하나 골라 주세요', 'multiple_choice', '["보기 1", "보기 2", "보기 3", "보기 4"]', NULL),
('quiz_all_03', 'quiz', 'all', '정답을 모두 골라 주세요', 'checkbox', '["보기 1", "보기 2", "보기 3", "보기 4"]', NULL),
('quiz_all_04', 'quiz', 'all', '답을 짧게 적어 주세요', 'short_answer', NULL, NULL),
('quiz_high_01', 'quiz', 'high_school', '학년·반·번호', 'short_answer', NULL, NULL),
('quiz_high_02', 'quiz', 'high_school', '풀이 과정을 적어 주세요', 'long_answer', NULL, NULL),
('quiz_univ_02', 'quiz', 'university', '자신의 생각을 근거와 함께 서술해 주세요', 'long_answer', NULL, NULL),
('quiz_work_01', 'quiz', 'worker', '소속 부서', 'short_answer', NULL, NULL),
('quiz_work_02', 'quiz', 'worker', '교육 내용 중 가장 기억에 남는 것을 적어 주세요', 'long_answer', NULL, NULL);
