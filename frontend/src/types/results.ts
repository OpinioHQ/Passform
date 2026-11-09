/** 결과 통계와 응답 상세. SPEC 5절 234·236행, 백엔드 `domain/result`의 DTO와 같은 이름을 쓴다. */
import type { QuestionType } from './form';
import type { ResponseAttachment, ResponseContentItem } from './response';

export interface CombinedOption {
  label: string;
  count: number;
  /** 이 선택지가 정의에 있는 제출 버전. */
  form_versions: number[];
}
/** `question_id`·문구·유형이 같은 문항 묶음. */
export interface CombinedQuestion {
  question_id: string;
  question_note: string;
  question_type: QuestionType;
  form_versions: number[];
  options: CombinedOption[];
}
export interface VersionQuestion {
  question_id: string;
  question_note: string;
  /** 선택지 문구 → 응답 수. 정의의 선택지 순서다. */
  counts: Record<string, number>;
}
/** 단답·장문 답 한 건. 빈 답은 서버가 넣지 않는다. */
export interface TextAnswer {
  response_id: string;
  form_version: number;
  value: string;
  submitted_at: string;
}
/** 단답·장문 문항 묶음. `answers`는 최신 제출이 먼저다. */
export interface CombinedTextQuestion {
  question_id: string;
  question_note: string;
  question_type: QuestionType;
  form_versions: number[];
  answers: TextAnswer[];
}
export interface VersionTextQuestion {
  question_id: string;
  question_note: string;
  question_type: QuestionType;
  answers: TextAnswer[];
}
/** 선택형과 단답·장문 카드를 섞어 놓을 순서. 세 값으로 두 목록의 항목을 찾는다. */
export interface QuestionRef {
  question_id: string;
  question_note: string;
  question_type: QuestionType;
}
/**
 * `text_questions`·`question_order`는 사람 결정(2026-11-09)으로 더한 필드다.
 * 더하기 전 서버 응답과 시험 데이터에는 없으므로 선택 필드로 두고, 없으면 빈 목록으로 본다.
 */
export interface VersionResults {
  form_version: number;
  response_count: number;
  questions: VersionQuestion[];
  text_questions?: VersionTextQuestion[];
  question_order?: QuestionRef[];
}
/** `GET /forms/{form_id}/results`(SPEC 234행). */
export interface FormResults {
  response_count: number;
  combined: {
    questions: CombinedQuestion[];
    text_questions?: CombinedTextQuestion[];
    question_order?: QuestionRef[];
  };
  versions: VersionResults[];
}
/** `GET /forms/{form_id}/responses/{response_id}`(SPEC 236행). 문구는 그 응답의 제출 버전 것이다. */
export interface ResponseDetail {
  response_id: string;
  form_version: number;
  submitted_at: string;
  updated_at: string | null;
  content: ResponseContentItem[];
  attachments: ResponseAttachment[];
}
