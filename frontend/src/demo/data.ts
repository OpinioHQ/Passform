import type { FormDefinition, SavedFormSummary, SubmittedResponse } from '../types/api';

/** Figma 무드보드의 예시 문구에 맞춘 가상 데이터. 실제 분석 수치가 아니다. */
export const demoForm: FormDefinition = {
  form_id: 'demo-form-team-experience',
  title: '10월 팀 경험 설문',
  note: '이번 달 함께 일한 경험을 나눠주세요. 약 3분이 걸려요.',
  definition_version: 1,
  deadline: '2026-11-10T18:00:00+09:00',
  status: 'open',
  consent: {
    items: ['문항별 응답 내용'],
    purpose: '팀 업무 경험 개선',
    retention: '테스트 종료 후 삭제',
    refusal_notice: '동의하지 않으면 제출할 수 없습니다.',
    ai_summary: null,
  },
  sections: [
    {
      section_id: 's1',
      title: '함께 일하는 경험',
      questions: [
        {
          question_id: 'q1',
          question_note: '이름',
          question_type: 'short_answer',
          required: true,
          options: [],
          profile_key: 'name',
          character_limit: 30,
          question_description: '이름을 입력해 주세요',
        },
        {
          question_id: 'q2',
          question_note: '이메일',
          question_type: 'short_answer',
          required: true,
          options: [],
          profile_key: 'email',
          character_limit: 100,
          question_description: '이메일을 입력해 주세요',
        },
        {
          question_id: 'q3',
          question_note: '소속 팀',
          question_type: 'dropdown',
          required: true,
          options: ['프로덕트디자인', '개발', '피플', '운영'],
        },
        {
          question_id: 'q4',
          question_note: '이번 달의 협업 경험은 어땠나요?',
          question_type: 'multiple_choice',
          required: true,
          options: ['아쉬워요', '보통이에요', '만족해요'],
        },
        {
          question_id: 'q5',
          question_note: '더 나은 협업을 위해 바라는 점',
          question_type: 'long_answer',
          required: false,
          options: [],
          character_limit: 100,
        },
      ],
    },
  ],
};
export const demoSavedForms: SavedFormSummary[] = [
  {
    form_id: demoForm.form_id,
    title: demoForm.title,
    deadline: demoForm.deadline,
    state: 'saved',
    has_draft: true,
  },
  {
    form_id: 'demo-workshop',
    title: '11월 사내 워크숍 일정',
    deadline: '2026-11-12T18:00:00+09:00',
    state: 'saved',
    has_draft: false,
  },
  {
    form_id: 'demo-tools',
    title: '업무 도구 사용 현황',
    deadline: '2026-11-15T18:00:00+09:00',
    state: 'saved',
    has_draft: false,
  },
  {
    form_id: 'demo-book',
    title: '사내 북클럽 참여 신청',
    deadline: '2026-10-25T18:00:00+09:00',
    state: 'saved',
    has_draft: false,
  },
];
const names = ['김지윤', '박서연', '이도현', '최유진', '정하늘', '윤서진'];
const opinions = [
  '회의 시간을 줄여요',
  '의견을 편하게 나눴어요',
  '회의록 공유가 좋아요',
  '집중 시간이 더 필요해요',
  '협업 방식이 명확해요',
  '피드백 시간을 정해요',
];
export const demoResponses: SubmittedResponse[] = names.map((name, i) => ({
  response_id: `demo-r${String(i + 1).padStart(3, '0')}`,
  form_version: 1,
  status: 'submitted',
  submitted_at: `2026-10-${28 - Math.floor(i / 2)}T${10 + i}:42:00+09:00`,
  updated_at: null,
  content: [
    { question_id: 'q1', question_note: '이름', value: name },
    { question_id: 'q2', question_note: '이메일', value: `demo${i + 1}@example.test` },
    { question_id: 'q3', question_note: '소속 팀', value: '프로덕트디자인' },
    { question_id: 'q4', question_note: '이번 달의 협업 경험은 어땠나요?', value: '만족해요' },
    { question_id: 'q5', question_note: '더 나은 협업을 위해 바라는 점', value: opinions[i] },
  ],
}));
export function answerOf(r: SubmittedResponse, id: string): string {
  const x = r.content.find((v) => v.question_id === id)?.value;
  return Array.isArray(x) ? x.join(', ') : x || '—';
}
