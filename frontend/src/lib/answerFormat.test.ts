import { describe, expect, it } from 'vitest';

import type { Question } from '../api/types';
import { answerProblem, formatHint } from './answerFormat';

const question = (extra: Partial<Question> = {}): Question => ({
  question_id: 'q',
  question_note: 'q',
  question_type: 'short_answer',
  options: [],
  required: false,
  branch_rules: [],
  ...extra,
});

const phone = question({ input_format: { kind: 'mobile_phone' } });

describe('answerProblem', () => {
  it('010은 11자리만 통과한다', () => {
    expect(answerProblem(phone, '010123456')).toContain('형식이 맞지 않습니다');
    expect(answerProblem(phone, '0101234567')).not.toBeNull();
    expect(answerProblem(phone, '010123456789')).not.toBeNull();
    expect(answerProblem(phone, '010-1234-567a')).not.toBeNull();
    expect(answerProblem(phone, '010-1234-5678')).toBeNull();
    expect(answerProblem(phone, '010 1234 5678')).toBeNull();
  });

  it('011·016~019는 10~11자리, 그 밖의 시작 번호는 거부한다', () => {
    expect(answerProblem(phone, '011-123-4567')).toBeNull();
    expect(answerProblem(phone, '019-1234-5678')).toBeNull();
    expect(answerProblem(phone, '01212345678')).not.toBeNull();
    expect(answerProblem(phone, '1234567890')).not.toBeNull();
  });

  it('집전화는 9자리부터 통과한다', () => {
    const landline = question({ input_format: { kind: 'landline_phone' } });
    expect(answerProblem(landline, '02-123-4567')).toBeNull();
    expect(answerProblem(landline, '12345678')).not.toBeNull();
  });

  it('이메일과 실제 날짜를 검사한다', () => {
    const email = question({ input_format: { kind: 'email' } });
    expect(answerProblem(email, 'a@b')).not.toBeNull();
    expect(answerProblem(email, ' a@b.co ')).toBeNull();
    const date = question({ input_format: { kind: 'date' } });
    expect(answerProblem(date, '2026-02-30')).not.toBeNull();
    expect(answerProblem(date, '2026-11-30')).toBeNull();
  });

  it('필수 문항을 비우면 알리고 선택 문항은 통과한다', () => {
    expect(answerProblem(question({ required: true }), '  ')).toBe('필수 문항입니다.');
    expect(answerProblem(question(), '')).toBeNull();
    expect(answerProblem(phone, '')).toBeNull();
  });

  it('글자 수 상한을 넘으면 알린다', () => {
    const limited = question({ character_limit: 3 });
    expect(answerProblem(limited, '세글자')).toBeNull();
    expect(answerProblem(limited, '네글자임')).toContain('3자');
  });

  it('형식이 없는 문항과 복수 선택은 형식을 보지 않는다', () => {
    expect(answerProblem(question(), '아무 값')).toBeNull();
    expect(answerProblem(question({ question_type: 'checkbox' }), ['기획'])).toBeNull();
  });
});

describe('formatHint', () => {
  it('형식이 있는 문항만 안내한다', () => {
    expect(formatHint(phone)).toContain('010-1234-5678');
    expect(formatHint(question())).toBeNull();
    expect(formatHint(question({ input_format: { kind: 'name' } }))).toBeNull();
  });
});
