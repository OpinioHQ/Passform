import type {
  AnalyzeRequest,
  AnalyzeResponse,
  ConsentTemplate,
  CreatedForm,
  CreatorFormDefinition,
  CreatorFormSummary,
  FormDefinition,
  FormResults,
  FormStateView,
  FormStatus,
  MeResponse,
  PublicForm,
  ResponseDetail,
  ResponseHistoryItem,
  ResponseDraft,
  SaveResponseDraftInput,
  SavedFormSummary,
  SavedResponseDraft,
  SubmitResponseInput,
  SubmittedResponse,
} from '../types/api';

export const useLiveApi = import.meta.env.VITE_USE_API === 'true';
const baseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

/** Google 로그인 시작 주소. fetch가 아니라 전체 페이지 이동으로 연다. */
export const googleLoginUrl = `${baseUrl}/oauth2/authorization/google`;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly detail: unknown,
  ) {
    super(`API 요청 실패 (${status})`);
  }
}

/** 서버의 모든 응답이 주는 `XSRF-TOKEN` 쿠키를 헤더로 옮긴다. 쿠키가 없으면 넣지 않는다. */
function csrfHeaders(): Record<string, string> {
  if (typeof document === 'undefined') return {};
  const token = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie)?.[1];
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {};
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

// 로그인 상태는 한 페이지에서 한 번만 묻는다. 헤더와 응답 화면이 같은 결과를 쓴다. 로그인은 전체 페이지 이동이라 새로 읽힌다.
let mePromise: Promise<MeResponse> | null = null;
function me(): Promise<MeResponse> {
  if (!mePromise) {
    mePromise = request<MeResponse>('/me');
    // 401(비로그인)은 그대로 둔다. 그 밖의 실패는 다음에 다시 묻는다.
    mePromise.catch((e: unknown) => {
      if (!(e instanceof ApiError && e.status === 401)) mePromise = null;
    });
  }
  return mePromise;
}

/** 실제 API 연결점. 화면이 호출하는 것은 `/me`, `/me/forms`, 폼 공개 조회, 응답 초안, 제출, 결과, 응답 상세, 빌더뿐이다. */
export const api = {
  me,
  savedForms: () => request<SavedFormSummary[]>('/me/forms'),
  createdForms: () => request<CreatorFormSummary[]>('/me/created-forms'),
  consentTemplates: () => request<ConsentTemplate[]>('/consent-templates'),
  createForm: (definition: FormDefinition) => request<CreatedForm>('/forms', json(definition)),
  formDefinition: (formId: string) =>
    request<CreatorFormDefinition>(`/forms/${segment(formId)}/definition`),
  updateForm: (formId: string, body: Partial<FormDefinition> & { status?: FormStatus }) =>
    request<FormStateView>(`/forms/${segment(formId)}`, { ...json(body), method: 'PATCH' }),
  formBySlug: (slug: string) => request<PublicForm>(`/f/${segment(slug)}`),
  formById: (formId: string) => request<PublicForm>(`/forms/${segment(formId)}`),
  responseDraft: (formId: string) =>
    request<ResponseDraft>(`/me/forms/${segment(formId)}/response-draft`),
  saveResponseDraft: (formId: string, body: SaveResponseDraftInput) =>
    request<SavedResponseDraft>(`/me/forms/${segment(formId)}/response-draft`, {
      ...json(body),
      method: 'PUT',
    }),
  deleteResponseDraft: (formId: string) =>
    request<void>(`/me/forms/${segment(formId)}/response-draft`, { method: 'DELETE' }),
  submit: (formId: string, body: SubmitResponseInput) =>
    request<SubmittedResponse>(`/forms/${segment(formId)}/responses`, json(body)),
  analyze: (formId: string, body: AnalyzeRequest) =>
    request<AnalyzeResponse>(`/forms/${segment(formId)}/analyze`, json(body)),
  myResponses: () => request<ResponseHistoryItem[]>('/me/responses'),
  results: (formId: string) => request<FormResults>(`/forms/${segment(formId)}/results`),
  responseDetail: (formId: string, responseId: string) =>
    request<ResponseDetail>(`/forms/${segment(formId)}/responses/${segment(responseId)}`),
};
