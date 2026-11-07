/**
 * 화면의 미리 검사가 서버 `AnswerValidator`와 같은 값을 받아들이고 거부하는지 본다(AC73, AC53, SPEC 5절 161~166행).
 * 값은 SPEC AC73 테스트 줄(405행)의 예시다. 최종 판정은 서버가 한다.
 */
import { describe, expect, it } from 'vitest';
import type { Question } from '../types/api';
import { checkAnswer, formatAccepts, isAnswered } from './answerCheck';

const base: Question = {
  question_id: 'q1',
  template_id: null,
  question_note: '문항',
  question_type: 'short_answer',
  options: [],
  required: true,
  profile_key: null,
  attachments: [],
  branch_rules: [],
};

describe('입력 형식 (AC73)', () => {
  it.each([
    ['date', '2026-02-28', true],
    ['date', '2026-02-30', false],
    ['date', '2026-2-28', false],
    ['mobile_phone', '010-1234-5678', true],
    ['mobile_phone', '011-123-4567', true],
    ['mobile_phone', '01012345678', true],
    ['mobile_phone', '010 1234 5678', true],
    ['mobile_phone', '010123456', false],
    ['mobile_phone', '010-1234-567a', false],
    ['mobile_phone', '0101234567', false],
    ['mobile_phone', '1234567890', false],
    ['mobile_phone', '01212345678', false],
    ['landline_phone', '02-123-4567', true],
    ['landline_phone', '02123456', false],
    ['email', 'a@example.com', true],
    ['email', ' a@example.com ', true],
    ['email', 'a@@example.com', false],
    ['email', 'a@example', false],
    ['name', '  김하나 ', true],
  ] as const)('%s %s → %s', (kind, value, accepted) => {
    expect(formatAccepts(kind, value)).toBe(accepted);
  });

  it('text_format_trims_and_checks_required: 필수 문항에 공백만 쓰면 비운 답이다', () => {
    expect(checkAnswer({ ...base, input_format: { kind: 'name' } }, '   ')).toBe(
      'required_missing',
    );
    expect(checkAnswer({ ...base, input_format: { kind: 'name' } }, ' 김하나 ')).toBeNull();
  });
});

describe('글자 수 (AC53)', () => {
  const limited = { ...base, character_limit: 100 };
  it('정확히 100자는 통과하고 101자는 거부한다. 공백·줄바꿈도 센다', () => {
    expect(checkAnswer(limited, 'a'.repeat(100))).toBeNull();
    expect(checkAnswer(limited, 'a'.repeat(101))).toBe('character_limit_exceeded');
    expect(checkAnswer(limited, `${'a'.repeat(99)}\n `)).toBe('character_limit_exceeded');
  });
});

describe('비운 답과 선택지 (서버 present·invalid_option과 같음)', () => {
  const choice: Question = { ...base, question_type: 'checkbox', options: ['가', '나'] };
  it('선택형은 빈 값·빈 배열만 비운 답이고 공백 값은 선택지 밖이다', () => {
    expect(isAnswered(choice, [])).toBe(false);
    expect(checkAnswer(choice, [])).toBe('required_missing');
    expect(checkAnswer(choice, ['가', ' '])).toBe('invalid_option');
    expect(checkAnswer(choice, ['가', '나'])).toBeNull();
  });
});
