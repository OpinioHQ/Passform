/**
 * `POST /forms/{form_id}/analyze`의 입출력.
 * `src/response_analysis/schemas/analyze_response.schema.json`과 같은 구조다.
 */
export type MatchMode = 'all' | 'any' | 'not';
export type AnalyzeOperation = 'count' | 'list' | 'summarize';

export interface RequestContext {
  conditions: string[];
  match?: MatchMode;
  operation: AnalyzeOperation | null;
  sort?: { by: string; order: 'asc' | 'desc' };
  target?: string;
  topic?: string;
}
/** 자연어 또는 되묻기 뒤 확정한 요청 중 하나만 보낸다(docs/api/analyze_api.md 1절). */
export type AnalyzeRequest =
  { query: string; context?: never } | { context: RequestContext; query?: never };

interface MatchedBase {
  condition: string;
  form_version: number;
  question_id: string;
  question_note: string;
}
export type MatchedCondition =
  | (MatchedBase & { kind: 'option'; option: string })
  | (MatchedBase & { kind: 'answered' | 'unanswered'; option: null });

export interface Coverage {
  total_submitted_count: number;
  not_evaluable_count: number;
  not_evaluable_by_version: Array<{
    form_version: number;
    response_count: number;
    unavailable_conditions: string[];
  }>;
  sort_unavailable_by_version?: number[];
  summary_unavailable_by_version?: Array<{ form_version: number; response_count: number }>;
}

export interface SummaryEvidence {
  response_id: string;
  form_version: number;
  question_id: string;
  excerpt: string;
}
export interface SummaryTarget {
  question_id: string;
  question_note: string;
  form_versions: number[];
}
export interface SummaryTheme {
  label: string;
  count: number;
  response_ids: string[];
  text: string | null;
  evidence: SummaryEvidence[];
}
export interface SummaryOptionStat {
  label: string;
  count: number;
  form_versions: number[];
}
export type SummaryItem =
  | { type: 'themes'; target: SummaryTarget; themes: SummaryTheme[] }
  | { type: 'option_stats'; target: SummaryTarget; options: SummaryOptionStat[] };
export type SummaryStatus =
  'ok' | 'no_matches' | 'no_consented_responses' | 'insufficient_evidence' | 'too_many_responses';
export interface AnalyzeSummary {
  status: SummaryStatus;
  excluded_no_consent_count: number;
  items: SummaryItem[];
  /** 특정 의견 요약. 개수 필드를 두지 않는다(AC89). */
  topic: { query: string; text: string | null; evidence: SummaryEvidence[] } | null;
}
export type AnalyzeResult =
  | { operation: 'count'; count: number; response_ids: null; summary: null }
  | { operation: 'list'; count: number; response_ids: string[]; summary: null }
  | { operation: 'summarize'; count: number; response_ids: string[]; summary: AnalyzeSummary };

export interface ClarifyQuestionOption {
  form_version: number;
  question_id: string;
  question_note: string;
  options?: string[];
}
interface ClarifyBase {
  message: string;
}
export type Clarify = ClarifyBase &
  (
    | { reason: 'unsupported_scope'; options: string[] }
    | { reason: 'parse_failed'; options: [] }
    | { reason: 'missing_operation'; options: AnalyzeOperation[] }
    | { reason: 'summary_disabled'; options: Array<'count' | 'list'> }
    | { reason: 'missing_match'; options: Array<'all' | 'any'> }
    | {
        reason: 'unknown_condition' | 'unsortable_field' | 'unsummarizable_field';
        options: ClarifyQuestionOption[];
      }
    | { reason: 'ambiguous_match'; options: Array<'any' | ClarifyQuestionOption> }
    | { reason: 'double_negation'; options: [string] }
  );
export type ClarifyReason = Clarify['reason'];

export interface AnalyzeOkResponse {
  status: 'ok';
  context: RequestContext;
  matched: MatchedCondition[];
  coverage: Coverage;
  result: AnalyzeResult;
  clarify: null;
}
export interface AnalyzeClarifyResponse {
  status: 'clarify';
  context: RequestContext | null;
  matched: null;
  coverage: null;
  result: null;
  clarify: Clarify;
}
export type AnalyzeResponse = AnalyzeOkResponse | AnalyzeClarifyResponse;
