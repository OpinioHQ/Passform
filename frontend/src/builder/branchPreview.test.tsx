// @vitest-environment jsdom
/** 빌더 미리보기가 만든 분기를 응답 경로와 같은 규칙으로 따르고(AC81), AI 요약 고지를 보이는지 본다. */
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ResponsePage } from '../pages/ResponsePage';
import { calculateResponsePath } from '../response/responsePath';
import type { FormDefinition, Question } from '../types/api';
import { AI_SUMMARY_NOTICE } from './aiSummaryNotice';
import { toPreviewForm } from './formDefinition';

const question = (id: string, note: string, overrides: Partial<Question> = {}): Question => ({
  question_id: id,
  template_id: null,
  question_note: note,
  question_type: 'short_answer',
  options: [],
  required: false,
  profile_key: null,
  attachments: [],
  branch_rules: [],
  ...overrides,
});

const definition = (overrides: Partial<FormDefinition['settings']> = {}): FormDefinition => ({
  title: '분기 미리보기',
  note: '',
  settings: {
    editable: false,
    cancellable: false,
    display: { default_mode: 'one_question' },
    ...overrides,
  },
  payment_link: null,
  consent_template_id: 'consent_default',
  note_attachments: [],
  sections: [
    {
      section_id: 's1',
      title: '첫째',
      questions: [
        question('q1', '참여하나요', {
          question_type: 'multiple_choice',
          options: ['예', '아니요'],
          branch_rules: [
            {
              when: { match: 'all', conditions: [{ question_id: 'q1', option: '예' }] },
              go_to: { section_id: 's3' },
            },
          ],
        }),
      ],
    },
    { section_id: 's2', title: '둘째', questions: [question('q2', '역할')] },
    { section_id: 's3', title: '셋째', questions: [question('q3', '소감')] },
  ],
});

const consent = { purpose: '행사 운영', retention: '행사 종료 후 파기' };

function openPreview(def: FormDefinition) {
  render(
    <ResponsePage
      preview
      form={toPreviewForm(def, consent)}
      onSubmit={vi.fn(() => Promise.resolve())}
      onBack={vi.fn()}
      saving={false}
    />,
  );
}

const click = (name: RegExp | string) => fireEvent.click(screen.getByRole('button', { name }));

beforeEach(() => {
  window.scrollTo = vi.fn();
});
afterEach(() => cleanup());

describe('빌더 미리보기', () => {
  it('builder_preview_branch_path: 선택지를 고른 뒤 다음 화면이 responsePath.ts의 경로와 같다', () => {
    const def = definition();
    const nextAfter = (answer: string) =>
      calculateResponsePath(def.sections, 'one_question', { q1: answer }).onPath[1].question_note;
    expect([nextAfter('예'), nextAfter('아니요')]).toEqual(['소감', '역할']);

    openPreview(def);
    click(/시작하기/);
    fireEvent.click(screen.getByRole('radio', { name: '예' }));
    click(/다음/);
    expect(screen.getByLabelText(nextAfter('예'))).toBeTruthy();
    expect(screen.queryByLabelText('역할')).toBeNull();

    click('이전');
    fireEvent.click(screen.getByRole('radio', { name: '아니요' }));
    click(/다음/);
    expect(screen.getByLabelText(nextAfter('아니요'))).toBeTruthy();
    expect(screen.queryByLabelText('소감')).toBeNull();
  });

  it('AI 요약을 켠 폼은 고지와 선택 체크박스를 보이고, 끈 폼은 고지가 없다', () => {
    const off = definition({ display: { default_mode: 'all_questions' } });
    off.sections[0].questions[0].branch_rules = [];
    expect(toPreviewForm(off, consent).consent.ai_summary).toBeNull();

    openPreview({ ...off, settings: { ...off.settings, ai_summary: { enabled: true } } });
    expect(
      screen.getByRole('checkbox', { name: /AI 요약을 위한 외부 전송에 동의합니다 \(선택\)/ }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'AI 요약을 위한 외부 전송 안내 보기' }));
    expect(screen.getByText(AI_SUMMARY_NOTICE.provider)).toBeTruthy();
    expect(screen.getByText(AI_SUMMARY_NOTICE.refusal_effect)).toBeTruthy();
  });
});
