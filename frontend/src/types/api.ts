/** Passform docs/SPEC.md §5 참고. 구체적인 검증은 서버가 권위 있는 판정자다. */
export type UiMode = 'respondent' | 'creator';
export type FormStatus = 'draft' | 'open' | 'closed';
export type QuestionType =
  'short_answer' | 'long_answer' | 'multiple_choice' | 'checkbox' | 'dropdown';
export interface Question {
  question_id: string;
  question_note: string;
  question_type: QuestionType;
  required: boolean;
  options: string[];
  question_description?: string;
  character_limit?: number;
  profile_key?: string | null;
}
export interface FormSection {
  section_id: string;
  title: string;
  questions: Question[];
}
export interface FormDefinition {
  form_id: string;
  title: string;
  note: string;
  definition_version: number;
  deadline: string | null;
  status: FormStatus;
  sections: FormSection[];
  consent: {
    items: string[];
    purpose: string;
    retention: string;
    refusal_notice: string;
    ai_summary: null;
  };
}
export type AnswerValue = string | string[];
export interface ResponseAnswer {
  question_id: string;
  value: AnswerValue;
}
export interface SubmitResponseInput {
  form_version: number;
  answers: ResponseAnswer[];
  consent_agreed: true;
  ai_summary_agreed?: boolean;
  draft_response_id?: string;
}
export interface SubmittedResponse {
  response_id: string;
  form_version: number;
  status: 'submitted';
  submitted_at: string;
  updated_at: string | null;
  content: Array<{ question_id: string; question_note: string; value: AnswerValue }>;
}
export interface SavedFormSummary {
  form_id: string;
  title: string;
  deadline: string | null;
  state: 'saved' | 'submitted';
  has_draft: boolean;
}
export interface CreatorFormSummary {
  form_id: string;
  title: string;
  status: FormStatus;
  deadline: string | null;
  share_url: string | null;
  response_count: number;
}
export type MatchMode = 'all' | 'any' | 'not';
export interface RequestContext {
  conditions: string[];
  match?: MatchMode;
  operation: 'count' | 'list' | 'summarize';
  sort?: unknown;
  target?: string | null;
  topic?: string | null;
}
export interface AnalysisSuccess {
  status: 'ok';
  result: { count: number; response_ids: string[]; [key: string]: unknown };
  matched: unknown[];
  coverage?: unknown;
  clarify: null;
}
export interface AnalysisClarify {
  status: 'clarify';
  result: null;
  matched?: null;
  clarify: { reason: string; options?: unknown[]; question?: string };
}
export type AnalysisResult = AnalysisSuccess | AnalysisClarify;
