import { describe, expect, it } from 'vitest';

import type { CombinedQuestion, PublicForm, Results } from '../api/types';
import { buildExamples } from './exampleQuestions';

const question = (id: string, options: [string, number][]): CombinedQuestion => ({
  question_id: id,
  question_note: id,
  question_type: 'multiple_choice',
  form_versions: [1],
  options: options.map(([label, count]) => ({ label, count, form_versions: [1] })),
});

const results = (questions: CombinedQuestion[]): Results => ({
  title: 't',
  response_count: 3,
  combined: { questions },
  versions: [],
});

const form = (aiSummary: boolean): PublicForm =>
  ({
    consent: { ai_summary: aiSummary ? { provider: 'OpenAI' } : null },
    sections: [
      {
        section_id: 's',
        title: 's',
        questions: [
          { question_id: 'name', question_note: '이름', question_type: 'long_answer', profile_key: 'name', options: [], required: false, branch_rules: [] },
          { question_id: 'why', question_note: '지원 동기', question_type: 'long_answer', options: [], required: false, branch_rules: [] },
        ],
      },
    ],
  }) as unknown as PublicForm;

const labels = (questions: CombinedQuestion[], publicForm: PublicForm | null = null) =>
  buildExamples(results(questions), publicForm).map((example) => example.label);

describe('buildExamples', () => {
  it('폼의 선택지로 버튼을 만들고 받침에 맞춰 조사를 고른다', () => {
    const made = labels([question('q1', [['소프트웨어학과', 2]]), question('q2', [['4학년', 1]])]);
    expect(made).toEqual(['전체 응답 수', '소프트웨어학과 응답 수', '소프트웨어학과 응답 목록', '소프트웨어학과가 아닌 응답 수', '소프트웨어학과 4학년 응답 수']);
    expect(labels([question('q1', [['4학년', 1]])])).toContain('4학년이 아닌 응답 수');
  });

  it('응답이 없는 선택지와 예·아니요, 여러 문항에 걸린 선택지는 쓰지 않는다', () => {
    expect(labels([question('q1', [['없는값', 0], ['예', 3]])])).toEqual(['전체 응답 수']);
    expect(labels([question('q1', [['경영학과', 2]]), question('q7', [['경영학과', 1]])])).toEqual(['전체 응답 수']);
  });

  it('지원하지 않는 통계나 버전 요청은 버튼으로 만들지 않는다', () => {
    const made = labels([question('q1', [['소프트웨어학과', 2]])], form(true)).join(' ');
    expect(made).not.toMatch(/평균|비율|버전|통계/);
  });

  it('요약 버튼은 AI 요약을 켠 폼에서 개인정보가 아닌 장문 문항으로만 만든다', () => {
    expect(labels([], form(true))).toEqual(['전체 응답 수', '전체 요약', '지원 동기 요약']);
    expect(labels([], form(false))).toEqual(['전체 응답 수']);
    expect(labels([], null)).toEqual(['전체 응답 수']);
  });

  it('버튼은 파서 없이 보낼 수 있는 확정 요청을 담는다', () => {
    const [, , , negative] = buildExamples(results([question('q1', [['소프트웨어학과', 2]])]), null);
    expect(negative?.context).toEqual({ conditions: ['소프트웨어학과'], match: 'not', operation: 'count' });
  });
});
