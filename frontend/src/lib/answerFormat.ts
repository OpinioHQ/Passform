import type { AnswerValue, Question } from '../api/types';
import { isBlank } from './responsePath';

// 다음 화면으로 넘어가기 전에 알려 주기 위한 검사다. 제출 때는 서버가 AnswerValidator로 다시 검사한다(SPEC 5절 160~166행).
// 규칙이 바뀌면 백엔드와 함께 바꾼다.

const EMAIL = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
// 010은 11자리, 011·016~019는 10~11자리. SPEC 162행보다 엄격하다(팀 결정 2026-11-04, SPEC 수정 필요).
const MOBILE_PHONE = /^(010\d{8}|01[16789]\d{7,8})$/;

const FORMAT_HINTS: Record<string, string> = {
  mobile_phone: '예: 010-1234-5678, 하이픈은 있어도 됩니다.',
  landline_phone: '숫자 9~11자리, 하이픈은 있어도 됩니다.',
  email: '예: name@example.com',
  date: '예: 2026-11-30',
};

const FORMAT_PROBLEMS: Record<string, string> = {
  mobile_phone: '휴대전화번호 형식이 맞지 않습니다. 예: 010-1234-5678',
  landline_phone: '전화번호는 숫자 9~11자리로 입력해 주세요.',
  email: '이메일 형식이 맞지 않습니다. 예: name@example.com',
  date: '실제 날짜를 YYYY-MM-DD 형식으로 입력해 주세요.',
};

function withoutSeparators(value: string): string {
  return value.replace(/[\s-]/g, '');
}

function isRealDate(value: string): boolean {
  if (!DATE.test(value)) {
    return false;
  }
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().startsWith(value);
}

function matchesFormat(kind: string, value: string): boolean {
  switch (kind) {
    case 'mobile_phone':
      return MOBILE_PHONE.test(withoutSeparators(value));
    case 'landline_phone':
      return /^\d{9,11}$/.test(withoutSeparators(value));
    case 'email':
      return EMAIL.test(value);
    case 'date':
      return isRealDate(value);
    default:
      return true;
  }
}

export function formatHint(question: Question): string | null {
  const kind = question.input_format?.kind;
  return kind ? (FORMAT_HINTS[kind] ?? null) : null;
}

export function answerProblem(question: Question, value: AnswerValue | undefined): string | null {
  if (isBlank(value)) {
    return question.required ? '필수 문항입니다.' : null;
  }
  if (typeof value !== 'string') {
    return null;
  }
  const kind = question.input_format?.kind;
  const text = kind ? value.trim() : value;
  if (kind && !matchesFormat(kind, text)) {
    return FORMAT_PROBLEMS[kind] ?? '입력 형식이 맞지 않습니다.';
  }
  const limit = question.character_limit ?? null;
  if (limit !== null && [...text].length > limit) {
    return `${limit}자 이내로 입력해 주세요.`;
  }
  return null;
}
