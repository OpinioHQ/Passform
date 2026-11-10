import type {
  FormDefinition,
  Question,
  QuestionTemplateView,
  QuickAudience,
  QuickPurpose,
} from '../types/api';
import { emptyQuestion } from './formDefinition';

/** 빠른 모드에서 고르는 값(SPEC 179행 예시). 서버 V3 초기 데이터와 같은 값이다. */
export const PURPOSE_LABELS: Record<QuickPurpose, string> = {
  apply: '신청',
  survey: '설문조사',
  reservation: '예약',
  quiz: '퀴즈',
};

export const AUDIENCE_LABELS: Record<QuickAudience, string> = {
  high_school: '고등학생',
  university: '대학생',
  worker: '직장인',
  // 세 대상에 들지 않거나 섞인 경우. 목적 공통 문항과 기본 정보만 받는다(사람 결정, #58).
  anyone: '누구나',
};

/**
 * 어떤 선택에도 함께 추천하는 기본 정보(이름·휴대폰 번호·생년월일, 사람 결정 #58).
 * 응답 모양은 SPEC 113행의 다섯 키뿐이라 V3 초기 데이터의 `basic_` 접두사로 구분한다.
 */
export const isBasicTemplate = (template: QuestionTemplateView) =>
  template.template_id.startsWith('basic_');

/**
 * 추천 문항을 폼 문항으로 바꾼다(AC7). 문구·유형·선택지·입력 형식을 복사하고 출처 `template_id`를 남긴다.
 * 본인 정보 여부는 템플릿에서 정하지 않으므로 `profile_key`는 null로 시작한다(SPEC 167행, AC72).
 */
export function questionFromTemplate(template: QuestionTemplateView): Question {
  const question: Question = {
    ...emptyQuestion(),
    template_id: template.template_id,
    question_note: template.question_note,
    question_type: template.question_type,
    options: [...template.options],
    profile_key: null,
  };
  if (template.input_format) question.input_format = { ...template.input_format };
  return question;
}

/** 마지막 섹션 끝에 문항을 더한다. 기본 모드와 같은 폼 정의를 그대로 쓴다(AC42). */
export function appendQuestions(def: FormDefinition, questions: Question[]): FormDefinition {
  if (questions.length === 0 || def.sections.length === 0) return def;
  const last = def.sections.length - 1;
  return {
    ...def,
    sections: def.sections.map((section, index) =>
      index === last ? { ...section, questions: [...section.questions, ...questions] } : section,
    ),
  };
}
