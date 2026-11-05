import { describe, expect, it } from 'vitest';
import { answerOf, demoForm, demoResponses, demoSavedForms } from './data';

describe('Passform demo fixture', () => {
  it('contains five supported questions', () => {
    expect(demoForm.sections.flatMap((x) => x.questions)).toHaveLength(5);
    expect(
      demoForm.sections[0].questions.every((q) =>
        ['short_answer', 'long_answer', 'multiple_choice', 'checkbox', 'dropdown'].includes(
          q.question_type,
        ),
      ),
    ).toBe(true);
  });
  it('uses submitted responses only for demo results', () => {
    expect(demoResponses.every((r) => r.status === 'submitted')).toBe(true);
    expect(answerOf(demoResponses[0], 'q4')).toBe('만족해요');
  });
  it('has exactly one resumable form', () => {
    expect(demoSavedForms.filter((f) => f.has_draft)).toHaveLength(1);
  });
});
