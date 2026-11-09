import type { QuestionType } from '../types/api';

/** 문항 유형 이름표. 빌더와 결과 화면이 함께 쓴다(같은 문항이 화면마다 다른 이름으로 보이지 않게). */
export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  short_answer: '단답형',
  long_answer: '장문형',
  multiple_choice: '단일 선택',
  dropdown: '드롭다운',
  checkbox: '복수 선택',
};
