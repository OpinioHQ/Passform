// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { BranchGraph, FormDefinition, Question } from '../types/api';
import { BranchFlowView } from './BranchFlowView';

const question = (id: string, note: string, extra: Partial<Question> = {}): Question => ({
  question_id: id,
  template_id: null,
  question_note: note,
  question_type: 'multiple_choice',
  options: ['예', '아니요'],
  required: false,
  profile_key: null,
  attachments: [],
  branch_rules: [],
  ...extra,
});

const definition: FormDefinition = {
  title: '가상 분기',
  note: '',
  settings: { editable: false, cancellable: false, display: { default_mode: 'one_question' } },
  payment_link: null,
  consent_template_id: 'consent_default',
  note_attachments: [],
  sections: [
    {
      section_id: 's1',
      title: '첫째',
      questions: [
        question('q1', '참여하나요', {
          branch_rules: [
            {
              when: { match: 'all', conditions: [{ question_id: 'q1', option: '아니요' }] },
              go_to: { section_id: 's2' },
            },
            {
              when: { match: 'any', conditions: [{ question_id: 'q1', option: '예' }] },
              go_to: { submit: true },
            },
          ],
        }),
        question('q2', '역할'),
      ],
    },
    { section_id: 's2', title: '', questions: [question('q3', '소감')] },
  ],
};

const graph: BranchGraph = {
  nodes: [
    { id: 's1', type: 'section' },
    { id: 'q1', type: 'question' },
    { id: 'q2', type: 'question' },
    { id: 's2', type: 'section' },
    { id: 'q3', type: 'question' },
    { id: 'submit', type: 'submit' },
  ],
  edges: [
    { from: 'q1', to: 's2', kind: 'rule', rule_index: 0 },
    { from: 'q1', to: 'submit', kind: 'rule', rule_index: 1 },
    { from: 'q1', to: 'q2', kind: 'default' },
    { from: 'q2', to: 'q3', kind: 'default' },
    { from: 'q3', to: 'submit', kind: 'default' },
  ],
};

afterEach(cleanup);

describe('분기 흐름 보기 (AC45)', () => {
  it('branch_graph_matches_rules: 규칙 이동과 기본 이동을 문항 문구로 구분해 보이고 ID를 보이지 않는다', async () => {
    render(
      <BranchFlowView load={() => Promise.resolve({ graph, definition })} onClose={() => {}} />,
    );

    const rules = await screen.findAllByText(/분기 \d:/);
    expect(rules.map((r) => r.textContent)).toEqual([
      '↳ 분기 1: "참여하나요"에서 "아니요" 선택 → 섹션 2 (섹션 처음)',
      '↳ 분기 2: "참여하나요"에서 "예" 선택 → 제출',
    ]);
    const defaults = screen.getAllByText(/기본 다음/).map((d) => d.textContent);
    expect(defaults).toEqual([
      '↓ 기본 다음 → 섹션 1의 2번 문항: 역할',
      '↓ 기본 다음 → 섹션 2의 1번 문항: 소감',
      '↓ 기본 다음 → 제출',
    ]);
    expect(
      screen.getByText('저장된 내용 기준이에요. 저장하지 않은 편집은 반영되지 않아요.'),
    ).toBeTruthy();

    const text = screen.getByRole('region', { name: '분기 흐름' }).textContent ?? '';
    for (const id of ['q1', 'q2', 'q3', 's1', 's2']) expect(text).not.toContain(id);
  });

  it('불러오지 못하면 알린다', async () => {
    render(<BranchFlowView load={() => Promise.reject(new Error('x'))} onClose={() => {}} />);
    expect((await screen.findByRole('alert')).textContent).toContain('불러오지 못했어요');
  });
});
