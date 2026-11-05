import type {
  AnalyzeRequest,
  AnalyzeResponse,
  CreatorFormSummary,
  PublicForm,
  ResponseHistoryItem,
  SavedFormSummary,
  SubmitResponseInput,
  SubmittedResponse,
} from '../types/api';

export const useLiveApi = import.meta.env.VITE_USE_API === 'true';
const baseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly detail: unknown,
  ) {
    super(`API 요청 실패 (${status})`);
  }
}

/** CSRF 헤더를 넣을 자리. 서버의 CSRF 방식이 정해지면(T2) 여기만 고친다. */
function csrfHeaders(): Record<string, string> {
  return {};
}

function parseErrorBody(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, {
    ...init,
    credentials: 'include',
    headers: { ...csrfHeaders(), ...init.headers },
  });
  // 204와 본문 없는 200(예: PATCH /me/settings)은 undefined로 돌려준다.
  const text = await res.text();
  if (!res.ok) throw new ApiError(res.status, parseErrorBody(text));
  return (text ? JSON.parse(text) : undefined) as T;
}

const json = (data: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(data),
});

const segment = encodeURIComponent;

/** 실제 API 연결점. 화면이 호출하는 것은 `/me/forms`, 폼 공개 조회, 제출뿐이다. */
export const api = {
  savedForms: () => request<SavedFormSummary[]>('/me/forms'),
  createdForms: () => request<CreatorFormSummary[]>('/me/created-forms'),
  formBySlug: (slug: string) => request<PublicForm>(`/f/${segment(slug)}`),
  formById: (formId: string) => request<PublicForm>(`/forms/${segment(formId)}`),
  submit: (formId: string, body: SubmitResponseInput) =>
    request<SubmittedResponse>(`/forms/${segment(formId)}/responses`, json(body)),
  analyze: (formId: string, body: AnalyzeRequest) =>
    request<AnalyzeResponse>(`/forms/${segment(formId)}/analyze`, json(body)),
  myResponses: () => request<ResponseHistoryItem[]>('/me/responses'),
};
