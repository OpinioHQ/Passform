// 백엔드 DTO와 같은 이름을 쓴다(AGENTS 5절). 필드는 SPEC 5절의 snake_case 그대로다.

export type QuestionType = 'multiple_choice' | 'dropdown' | 'checkbox' | 'short_answer' | 'long_answer';

export interface BranchCondition {
  question_id: string;
  option: string;
}

export interface BranchRule {
  when: { match?: 'all' | 'any'; conditions: BranchCondition[] };
  go_to: 'submit' | { section_id?: string; question_id?: string; submit?: boolean };
}

export interface Question {
  question_id: string;
  question_note: string;
  question_description?: string | null;
  question_type: QuestionType;
  options: string[];
  required: boolean;
  profile_key?: string | null;
  input_format?: { kind: string; placeholder?: string } | null;
  character_limit?: number | null;
  branch_rules: BranchRule[];
}

export interface Section {
  section_id: string;
  title: string;
  questions: Question[];
}

export type DisplayMode = 'one_question' | 'all_questions' | 'section_page';

export interface AiSummaryNotice {
  provider: string;
  processing_country: string;
  items: string;
  purpose: string;
  retention: string;
  refusal_effect: string;
  checked_on: string;
}

export interface PublicForm {
  form_id: string;
  definition_version: number;
  title: string;
  note: string | null;
  deadline: string | null;
  sections: Section[];
  display: { default_mode: DisplayMode; allowed_modes: DisplayMode[] };
  payment_link: string | null;
  consent: {
    items: string[];
    purpose: string;
    retention: string;
    refusal_notice: string;
    ai_summary: AiSummaryNotice | null;
  };
}

export type AnswerValue = string | string[];
export type Answers = Record<string, AnswerValue>;

export interface SubmittedResponse {
  response_id: string;
  form_version: number;
  status: 'submitted';
  content: { question_id: string; value: AnswerValue | null }[];
  submitted_at: string;
  updated_at: string | null;
}

export interface SavedForm {
  form_id: string;
  title: string;
  deadline: string | null;
  state: 'saved' | 'submitted';
  has_draft: boolean;
}

export interface CreatedForm {
  form_id: string;
  title: string;
  status: 'draft' | 'open' | 'closed';
  deadline: string | null;
  share_url: string;
  response_count: number;
}

export interface HistoryItem {
  response_id: string;
  form_id: string;
  form_title: string;
  form_version: number;
  submitted_at: string;
  updated_at: string | null;
  editable: boolean;
  cancellable: boolean;
}

export interface AnswerDetail {
  question_id: string;
  question_note: string;
  value: AnswerValue | null;
}

export interface ResponseDetail {
  response_id: string;
  form_title: string;
  form_version: number;
  content: AnswerDetail[];
  submitted_at: string;
}

export interface OwnerResponse {
  response_id: string;
  form_version: number;
  submitted_at: string;
  content: AnswerDetail[];
}

export interface OptionCount {
  label: string;
  count: number;
  form_versions: number[];
}

export interface CombinedQuestion {
  question_id: string;
  question_note: string;
  question_type: QuestionType;
  form_versions: number[];
  options: OptionCount[];
}

export interface Results {
  title: string;
  response_count: number;
  combined: { questions: CombinedQuestion[] };
  versions: {
    form_version: number;
    response_count: number;
    questions: { question_id: string; question_note: string; counts: Record<string, number> }[];
  }[];
}

export interface RequestContext {
  conditions: string[];
  match?: 'all' | 'any' | 'not';
  operation?: 'count' | 'list' | 'summarize' | null;
  sort?: { by: string; order: 'asc' | 'desc' };
  target?: string;
  topic?: string;
}

export interface Matched {
  condition: string;
  form_version: number;
  question_id: string;
  question_note: string | null;
  kind: 'option' | 'answered' | 'unanswered';
  option: string | null;
}

export interface Coverage {
  total_submitted_count: number;
  not_evaluable_count: number;
  not_evaluable_by_version: { form_version: number; response_count: number; unavailable_conditions: string[] }[];
  sort_unavailable_by_version?: number[];
  summary_unavailable_by_version?: { form_version: number; response_count: number }[];
}

export interface Evidence {
  response_id: string;
  form_version: number;
  question_id: string;
  excerpt: string;
}

export interface Theme {
  label: string;
  count: number;
  response_ids: string[];
  text: string | null;
  evidence: Evidence[];
}

export interface SummaryItem {
  type: 'themes' | 'option_stats';
  target: { question_id: string; question_note: string; form_versions: number[] };
  themes?: Theme[];
  options?: OptionCount[];
}

export type SummaryStatus = 'ok' | 'no_matches' | 'no_consented_responses' | 'insufficient_evidence' | 'too_many_responses';

export interface Summary {
  status: SummaryStatus;
  excluded_no_consent_count: number;
  items: SummaryItem[];
  topic: { query: string; text: string | null; evidence: Evidence[] } | null;
}

export type ClarifyReason =
  | 'unsupported_scope'
  | 'parse_failed'
  | 'missing_operation'
  | 'summary_disabled'
  | 'missing_match'
  | 'unknown_condition'
  | 'ambiguous_match'
  | 'double_negation'
  | 'unsortable_field'
  | 'unsummarizable_field';

export interface QuestionOption {
  form_version: number;
  question_id: string;
  question_note: string;
  options?: string[];
}

export type ClarifyOption = string | QuestionOption;

export interface AnalysisResponse {
  status: 'ok' | 'clarify';
  context: RequestContext | null;
  matched: Matched[] | null;
  coverage: Coverage | null;
  result: {
    operation: 'count' | 'list' | 'summarize';
    count: number;
    response_ids: string[] | null;
    summary: Summary | null;
  } | null;
  clarify: { reason: ClarifyReason; message: string; options: ClarifyOption[] } | null;
}
