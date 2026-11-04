import { describe, expect, it } from 'vitest';

import type { Question, Section } from '../api/types';
import { computePath, isBlank } from './responsePath';

const question = (id: string, extra: Partial<Question> = {}): Question => ({
  question_id: id,
  question_note: id,
  question_type: 'multiple_choice',
  options: ['예', '아니요'],
  required: false,
  branch_rules: [],
  ...extra,
});

const sections: Section[] = [
  {
    section_id: 's1',
    title: '1',
    questions: [
      question('q1', {
        branch_rules: [{ when: { match: 'all', conditions: [{ question_id: 'q1', option: '아니요' }] }, go_to: { section_id: 's3' } }],
      }),
    ],
  },
  { section_id: 's2', title: '2', questions: [question('q2')] },
  {
    section_id: 's3',
    title: '3',
    questions: [
      question('q3', {
        question_type: 'checkbox',
        branch_rules: [{ when: { match: 'any', conditions: [{ question_id: 'q3', option: '예' }] }, go_to: 'submit' }],
      }),
      question('q4'),
    ],
  },
];

const ids = (answers: Parameters<typeof computePath>[1]) => computePath(sections, answers).map((q) => q.question_id);

describe('computePath', () => {
  it('분기 규칙이 맞으면 건너뛴다', () => {
    expect(ids({ q1: '아니요' })).toEqual(['q1', 'q3', 'q4']);
  });

  it('규칙이 맞지 않으면 다음 문항으로 간다', () => {
    expect(ids({ q1: '예' })).toEqual(['q1', 'q2', 'q3', 'q4']);
  });

  it('checkbox 조건은 선택지 포함이고 submit이면 멈춘다', () => {
    expect(ids({ q1: '예', q3: ['아니요', '예'] })).toEqual(['q1', 'q2', 'q3']);
  });

  it('답이 없으면 분기하지 않는다', () => {
    expect(ids({})).toEqual(['q1', 'q2', 'q3', 'q4']);
  });
});

describe('isBlank', () => {
  it('공백 문자열과 빈 배열을 빈 답으로 본다', () => {
    expect(isBlank('  ')).toBe(true);
    expect(isBlank([])).toBe(true);
    expect(isBlank(undefined)).toBe(true);
    expect(isBlank('값')).toBe(false);
    expect(isBlank(['값'])).toBe(false);
  });
});
