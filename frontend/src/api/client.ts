import type { AnalysisResult, CreatorFormSummary, FormDefinition, SavedFormSummary, SubmitResponseInput, SubmittedResponse } from '../types/api';

export const useLiveApi = import.meta.env.VITE_USE_API === 'true';
const baseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly detail: unknown) { super(`API 요청 실패 (${status})`); }
}
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, {
    credentials: 'include',
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  });
  if (!res.ok) {
    let detail: unknown;
    try { detail = await res.json(); } catch { detail = await res.text(); }
    throw new ApiError(res.status, detail);
  }
  return await res.json() as T;
}
const json = (data: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(data) });
/** 실제 API 연결점. 인증·CSRF·OAuth는 백엔드 설정과 함께 최종 검증 필요. */
export const api = {
  savedForms: () => request<SavedFormSummary[]>('/me/forms'),
  createdForms: () => request<CreatorFormSummary[]>('/me/created-forms'),
  formBySlug: (slug: string) => request<FormDefinition>(`/f/${encodeURIComponent(slug)}`),
  formById: (id: string) => request<FormDefinition>(`/forms/${encodeURIComponent(id)}`),
  submit: (formId: string, body: SubmitResponseInput) => request<SubmittedResponse>(`/forms/${encodeURIComponent(formId)}/responses`, json(body)),
  analyze: (formId: string, query: string) => request<AnalysisResult>(`/forms/${encodeURIComponent(formId)}/analyze`, json({ query })),
  results: <T,>(formId: string) => request<T>(`/forms/${encodeURIComponent(formId)}/results`),
  myResponses: <T,>() => request<T>('/me/responses'),
};
