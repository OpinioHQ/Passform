/** 미리보기의 보기 방식이 공개 조회의 `allowed_modes`와 같은 규칙인지 본다(AC47, AC81, 사람 결정 (가)). */
import { describe, expect, it } from 'vitest';
import type { FormDefinition, Question } from '../types/api';
import { toPreviewForm } from './formDefinition';

const question = (id: string, branched = false): Question => ({
  question_id: id,
  template_id: null,
  question_note: id,
  question_type: 'multiple_choice',
  options: ['예', '아니요'],
  required: false,
  profile_key: null,
  attachments: [],
  branch_rules: branched
    ? [
        {
          when: { match: 'all', conditions: [{ question_id: id, option: '예' }] },
          go_to: { submit: true },
        },
      ]
    : [],
});

const definition = (
  mode: FormDefinition['settings']['display']['default_mode'],
  branched: boolean,
): FormDefinition => ({
  title: '미리보기',
  note: '',
  settings: { editable: false, cancellable: false, display: { default_mode: mode } },
  payment_link: null,
  consent_template_id: 'consent_default',
  note_attachments: [],
  sections: [
    { section_id: 's1', title: '하나', questions: [question('q1')] },
    { section_id: 's2', title: '둘', questions: [question('q2', branched)] },
  ],
});

describe('toPreviewForm의 보기 방식', () => {
  it('분기가 없으면 한 질문씩과 전체 질문 보기를 오갈 수 있다', () => {
    expect(toPreviewForm(definition('all_questions', false), null).display).toEqual({
      default_mode: 'all_questions',
      allowed_modes: ['one_question', 'all_questions'],
    });
  });

  it('분기가 하나라도 있으면 시작 방식 하나만 둔다', () => {
    expect(toPreviewForm(definition('section_page', true), null).display).toEqual({
      default_mode: 'section_page',
      allowed_modes: ['section_page'],
    });
  });
});
