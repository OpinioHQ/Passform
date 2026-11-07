/**
 * "다음"과 "제출"을 누를 때 화면이 먼저 알려 주는 검사. 서버 `AnswerValidator`(SPEC 5절 161~166행)와 같은 기준을 쓰지만
 * 판정은 서버가 한다. 화면을 통과해도 서버가 거부하면 서버 응답을 따른다.
 */
import type { AnswerValue, InputFormatKind, Question } from '../types/api';
import { answerValues } from './responsePath';

export type AnswerProblem =
  'required_missing' | 'invalid_input_format_value' | 'invalid_option' | 'character_limit_exceeded';

const isChoice = (question: Question) =>
  question.question_type !== 'short_answer' && question.question_type !== 'long_answer';

/** 서버 `present`와 같다. 선택형은 빈 값·빈 배열만, 주관식은 공백만 있어도 비운 답이다. */
export function isAnswered(question: Question, value: AnswerValue | undefined): boolean {
  if (value === undefined) return false;
  if (!isChoice(question)) return answerValues(value).length > 0;
  return Array.isArray(value) ? value.length > 0 : value !== '';
}

function digits(value: string): string | null {
  const number = value.replace(/[\s-]/g, '');
  return /^[0-9]+$/.test(number) ? number : null;
}

function validDate(value: string): boolean {
  const match = /^([0-9]{4})-([0-9]{2})-([0-9]{2})$/.exec(value);
  if (!match) return false;
  const [year, month, day] = match.slice(1).map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return (
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
  );
}

/** 서버 `normalize`가 값을 받아들이는지. */
export function formatAccepts(kind: InputFormatKind, value: string): boolean {
  switch (kind) {
    case 'name':
    case 'address':
    case 'student_id':
      return true;
    case 'mobile_phone': {
      const number = digits(value);
      if (number === null) return false;
      return number.startsWith('010') ? number.length === 11 : /^01[16-9][0-9]{7,8}$/.test(number);
    }
    case 'landline_phone': {
      const number = digits(value);
      return number !== null && number.length >= 9 && number.length <= 11;
    }
    case 'email':
      return /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/.test(value.trim());
    case 'date':
      return validDate(value);
  }
}

/** 서버와 같은 순서(필수 → 선택지 → 형식 → 글자 수)로 문항 하나의 첫 문제를 돌려준다. */
export function checkAnswer(
  question: Question,
  value: AnswerValue | undefined,
): AnswerProblem | null {
  if (!isAnswered(question, value)) return question.required ? 'required_missing' : null;
  const raw = Array.isArray(value) ? value : [value as string];
  if (isChoice(question) && !raw.every((item) => question.options.includes(item)))
    return 'invalid_option';
  const text = raw[0];
  if (question.input_format && !formatAccepts(question.input_format.kind, text)) {
    return 'invalid_input_format_value';
  }
  // 서버 `String.length()`와 같은 UTF-16 길이다. 공백·줄바꿈도 1자로 센다.
  if (question.character_limit && text.length > question.character_limit)
    return 'character_limit_exceeded';
  return null;
}

const formatHints: Record<InputFormatKind, string> = {
  name: '이름을 입력해 주세요.',
  address: '주소를 한 칸에 입력해 주세요.',
  student_id: '학번을 입력해 주세요.',
  mobile_phone: '휴대전화 번호를 입력해 주세요. 예: 010-1234-5678',
  landline_phone: '전화번호를 입력해 주세요. 예: 02-123-4567',
  email: '이메일을 입력해 주세요. 예: name@example.com',
  date: '날짜를 YYYY-MM-DD 형식으로 입력해 주세요. 예: 2026-11-20',
};

/** 입력 형식이 있는 문항 아래에 늘 보이는 안내(AC73). */
export function formatHint(kind: InputFormatKind): string {
  return formatHints[kind];
}

export function problemMessage(question: Question, problem: AnswerProblem): string {
  switch (problem) {
    case 'required_missing':
      return '필수 문항이에요. 답을 입력해 주세요.';
    case 'invalid_option':
      return '보기에 있는 선택지를 골라 주세요.';
    case 'invalid_input_format_value':
      return question.input_format
        ? `형식이 맞지 않아요. ${formatHint(question.input_format.kind)}`
        : '형식이 맞지 않아요.';
    case 'character_limit_exceeded':
      return `최대 ${question.character_limit}자까지 입력할 수 있어요.`;
  }
}
