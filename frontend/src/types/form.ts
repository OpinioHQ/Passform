/** 폼 정의. `src/passform/schemas/form.schema.json`과 같은 구조다. */
export type QuestionType =
  'multiple_choice' | 'dropdown' | 'checkbox' | 'short_answer' | 'long_answer';
export type ProfileKey =
  'name' | 'student_id' | 'department' | 'phone_number' | 'email' | 'address' | 'birth_date';
export type InputFormatKind =
  'name' | 'mobile_phone' | 'landline_phone' | 'address' | 'student_id' | 'email' | 'date';
export type DisplayMode = 'one_question' | 'all_questions' | 'section_page';

export interface InputFormat {
  kind: InputFormatKind;
  placeholder?: string;
}
export interface Attachment {
  attachment_id: string;
  filename: string;
  content_type: string;
  size_bytes: number;
}
export interface BranchCondition {
  question_id: string;
  option: string;
}
export type BranchTarget = { section_id: string } | { question_id: string } | { submit: true };
export interface BranchRule {
  when: { match: 'all' | 'any'; conditions: BranchCondition[] };
  go_to: BranchTarget;
}
export interface Question {
  question_id: string;
  template_id: string | null;
  question_note: string;
  question_description?: string | null;
  question_type: QuestionType;
  options: string[];
  required: boolean;
  input_format?: InputFormat;
  profile_key: ProfileKey | null;
  character_limit?: number | null;
  attachments: Attachment[];
  branch_rules: BranchRule[];
}
export interface FormSection {
  section_id: string;
  title: string;
  questions: Question[];
}
export interface FormSettings {
  editable: boolean;
  cancellable: boolean;
  response_limit?: number | null;
  display: { default_mode: DisplayMode };
  ai_summary?: { enabled: boolean };
}
export interface FormDefinition {
  title: string;
  note: string;
  deadline?: string | null;
  settings: FormSettings;
  payment_link: string | null;
  consent_template_id: string | null;
  note_attachments: Attachment[];
  sections: FormSection[];
}

export type FormStatus = 'draft' | 'open' | 'closed';

/** `GET /forms/{form_id}/definition`(SPEC 116행). 폼 정의 밖의 값을 함께 준다. */
export interface CreatorFormDefinition extends FormDefinition {
  status: FormStatus;
  share_url: string | null;
  definition_version: number | null;
}

/** `PATCH /forms/{form_id}`의 응답(SPEC 117행). */
export type FormStateView = Pick<
  CreatorFormDefinition,
  'status' | 'share_url' | 'definition_version'
>;
/** `POST /forms`의 응답(SPEC 115행). */
export interface CreatedForm {
  form_id: string;
  status: FormStatus;
}
/** `GET /consent-templates`(SPEC 114행). */
export interface ConsentTemplate {
  consent_template_id: string;
  purpose: string;
  retention: string;
}
/** 빠른 모드의 제작 목적·응답 대상. 서버 V3 초기 데이터의 값이다. */
export type QuickPurpose = 'apply' | 'survey' | 'reservation' | 'quiz';
export type QuickAudience = 'high_school' | 'university' | 'worker' | 'anyone';
/** `GET /question-templates`(SPEC 113행). 본인 정보 여부(`profile_key`)는 템플릿에 없다(AC7). */
export interface QuestionTemplateView {
  template_id: string;
  question_note: string;
  question_type: QuestionType;
  options: string[];
  input_format: InputFormat | null;
}

/** AI 요약을 켠 폼의 외부 전송 고지(SPEC 187행, 211행). */
export interface AiSummaryNotice {
  provider: string;
  processing_country: string;
  items: string[];
  purpose: string;
  retention: string;
  refusal_effect: string;
  checked_on: string;
}
/** `GET /f/{slug}`, `GET /forms/{form_id}`의 공개 조회(SPEC 187행). */
export interface PublicForm {
  form_id: string;
  definition_version: number;
  title: string;
  note: string;
  note_attachments: Attachment[];
  deadline: string | null;
  sections: FormSection[];
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
/** `GET /me/created-forms`(SPEC 120행). */
export interface CreatorFormSummary {
  form_id: string;
  title: string;
  status: FormStatus;
  deadline: string | null;
  share_url: string | null;
  response_count: number;
}
/** `GET /forms/{form_id}/branch-graph`(SPEC 125행). 노드 id는 섹션·문항 ID이거나 `submit`이다. */
export interface BranchGraphNode {
  id: string;
  type: 'section' | 'question' | 'submit';
}
export interface BranchGraphEdge {
  from: string;
  to: string;
  kind: 'rule' | 'default';
  /** rule 엣지에만 있다. 그 문항 `branch_rules`의 위치(0부터). */
  rule_index?: number;
}
export interface BranchGraph {
  nodes: BranchGraphNode[];
  edges: BranchGraphEdge[];
}
