/** 응답 제출과 내 폼함·제출 이력. SPEC 5절 188~200행의 입출력과 같은 이름을 쓴다. */
import type { Attachment, PublicForm } from './form';

export type AnswerValue = string | string[];
export interface ResponseAnswer {
  question_id: string;
  value: AnswerValue;
}
export interface ResponseContentItem {
  question_id: string;
  question_note: string;
  value: AnswerValue;
}
/** `Response.attachments`(SPEC 222행). */
export interface ResponseAttachment extends Attachment {
  question_id: string;
  available: boolean;
}
export interface SubmitResponseInput {
  form_version: number;
  answers: ResponseAnswer[];
  consent_agreed: true;
  ai_summary_agreed?: boolean;
  draft_response_id?: string;
}
/** `PUT /me/forms/{form_id}/response-draft`의 입력(SPEC 190행). 미완성 답을 받는다. */
export interface SaveResponseDraftInput {
  form_version: number;
  answers: ResponseAnswer[];
}
/** `PUT /me/forms/{form_id}/response-draft`의 200·201(SPEC 190행). */
export interface SavedResponseDraft {
  response_id: string;
  status: 'in_progress';
  saved_at: string;
}
/** `GET /me/forms/{form_id}/response-draft`(SPEC 189행). `form_definition`은 작성 시작 버전의 공개 조회 모양이다. */
export interface ResponseDraft {
  response_id: string;
  form_version: number;
  form_definition: PublicForm;
  answers: ResponseAnswer[];
  saved_at: string;
}
/** `POST /forms/{form_id}/responses`의 201(SPEC 188행). */
export interface SubmittedResponse {
  response_id: string;
  form_version: number;
  status: 'submitted';
  content: ResponseContentItem[];
  attachments: ResponseAttachment[];
  submitted_at: string;
  updated_at: null;
}
/** `GET /me/forms`(SPEC 200행). `closed`는 서버가 계산한다. */
export interface SavedFormSummary {
  form_id: string;
  title: string;
  deadline: string | null;
  state: 'saved' | 'submitted';
  has_draft: boolean;
  saved_at: string;
  submitted_at: string | null;
  closed: boolean;
}
/** `POST /me/saved-forms`의 입력(SPEC 199행). 201은 처음 저장, 200은 이미 저장함이다. */
export interface SaveFormInput {
  form_id: string;
}
/** `GET /me/responses/{response_id}`(SPEC 193행). 문항·문구는 제출 버전 기준이다. */
export interface ResponseHistoryDetail {
  form_title: string;
  form_version: number;
  /** 폼·받은 응답 전체 삭제로 조회 전용이 되면 `null`(T12). */
  form_definition: PublicForm | null;
  content: ResponseContentItem[];
  attachments: ResponseAttachment[];
  submitted_at: string;
  updated_at: string | null;
  editable: boolean;
  cancellable: boolean;
}
/** `GET /me/responses`의 항목(SPEC 192행). */
export interface ResponseHistoryItem {
  response_id: string;
  form_id: string;
  form_title: string;
  form_version: number;
  submitted_at: string;
  updated_at: string | null;
  editable: boolean;
  cancellable: boolean;
}
