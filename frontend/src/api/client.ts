import type {
  AnalysisResponse,
  Answers,
  CreatedForm,
  HistoryItem,
  OwnerResponse,
  PublicForm,
  RequestContext,
  ResponseDetail,
  Results,
  SavedForm,
  SubmittedResponse,
} from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;
  readonly questionIds: string[];

  constructor(status: number, code: string | null, questionIds: string[]) {
    super(code ?? `HTTP ${status}`);
    this.status = status;
    this.code = code;
    this.questionIds = questionIds;
  }
}

export type DemoUser = 'demo-user' | 'other-user';

const USER_KEY = 'passform.demoUser';

export function currentDemoUser(): DemoUser {
  try {
    return localStorage.getItem(USER_KEY) === 'other-user' ? 'other-user' : 'demo-user';
  } catch {
    return 'demo-user';
  }
}

export function setDemoUser(user: DemoUser): void {
  try {
    localStorage.setItem(USER_KEY, user);
  } catch {
    // 저장할 수 없으면 기본 계정으로 동작한다.
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', 'X-Demo-User': currentDemoUser() },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const json: unknown = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = (json ?? {}) as { error?: string; question_ids?: string[] };
    throw new ApiError(response.status, error.error ?? null, error.question_ids ?? []);
  }
  return json as T;
}

export const api = {
  formBySlug: (slug: string) => request<PublicForm>('GET', `/f/${encodeURIComponent(slug)}`),
  formById: (formId: string) => request<PublicForm>('GET', `/forms/${encodeURIComponent(formId)}`),
  submit: (
    formId: string,
    payload: { form_version: number; answers: { question_id: string; value: Answers[string] }[]; consent_agreed: boolean; ai_summary_agreed: boolean },
  ) => request<SubmittedResponse>('POST', `/forms/${formId}/responses`, payload),
  myForms: () => request<SavedForm[]>('GET', '/me/forms'),
  history: () => request<HistoryItem[]>('GET', '/me/responses'),
  historyDetail: (responseId: string) => request<ResponseDetail>('GET', `/me/responses/${responseId}`),
  createdForms: () => request<CreatedForm[]>('GET', '/me/created-forms'),
  results: (formId: string) => request<Results>('GET', `/forms/${formId}/results`),
  ownerResponse: (formId: string, responseId: string) =>
    request<OwnerResponse>('GET', `/forms/${formId}/responses/${responseId}`),
  analyzeQuery: (formId: string, query: string) => request<AnalysisResponse>('POST', `/forms/${formId}/analyze`, { query }),
  analyzeContext: (formId: string, context: RequestContext) =>
    request<AnalysisResponse>('POST', `/forms/${formId}/analyze`, { context }),
};
