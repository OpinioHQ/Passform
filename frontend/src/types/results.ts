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
export interface VersionResults {
  form_version: number;
  response_count: number;
  questions: VersionQuestion[];
}
/** `GET /forms/{form_id}/results`(SPEC 234행). */
export interface FormResults {
  response_count: number;
  combined: { questions: CombinedQuestion[] };
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
