import type {
  AnalyzeRequest,
  AnalyzeResponse,
  Attachment,
  AutofillResult,
  BranchGraph,
  ConsentTemplate,
  CreatedForm,
  CreatedProfile,
  CreatorFormDefinition,
  CreatorFormSummary,
  EditedResponse,
  ExportRequest,
  ExportResult,
  FileToSend,
  FormDefinition,
  FormResults,
  FormStateView,
  FormStatus,
  MeResponse,
  Profile,
  ProfileInput,
  PublicForm,
  ResponseAnswer,
  ResponseDetail,
  ResponseHistoryDetail,
  ResponseHistoryItem,
  ResponseDraft,
  SaveFormInput,
  SaveResponseDraftInput,
  SavedFormSummary,
  SavedResponseDraft,
  StorageUsage,
  SubmitFiles,
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

/** 폼 설명란(`questionId`가 null)이나 문항의 첨부 경로. 받는 주소와 지우는 주소가 같다. */
function attachmentPath(formId: string, questionId: string | null): string {
  const question = questionId === null ? '' : `/questions/${segment(questionId)}`;
  return `/forms/${segment(formId)}${question}/attachments`;
}

/** 서버가 권한을 확인한 뒤 잠깐 유효한 주소로 보낸다. `img`와 링크에 그대로 쓴다. */
export function attachmentUrl(formId: string, questionId: string | null, attachmentId: string) {
  return `${baseUrl}${attachmentPath(formId, questionId)}/${segment(attachmentId)}`;
}

/** 브라우저가 multipart 경계를 정하도록 Content-Type을 넣지 않는다. */
function uploadAttachment(formId: string, questionId: string | null, file: File) {
  const body = new FormData();
  body.append('file', file);
  return request<Attachment>(attachmentPath(formId, questionId), { method: 'POST', body });
}

/** 응답에 붙은 파일. 제작자와 제출한 본인만 받는다(SPEC 198행). */
export function responseAttachmentUrl(formId: string, responseId: string, attachmentId: string) {
  return `${baseUrl}/forms/${segment(formId)}/responses/${segment(responseId)}/attachments/${segment(attachmentId)}`;
}

/** 답은 `data` JSON 부분으로, 파일과 문항 ID는 같은 순서로 보낸다. Content-Type은 브라우저가 정한다. */
function multipartBody(data: unknown, files: FileToSend[]): FormData {
  const body = new FormData();
  body.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  files.forEach(({ question_id, file }) => {
    body.append('files', file);
    body.append('file_question_ids', question_id);
  });
  return body;
}

function editResponse(responseId: string, answers: ResponseAnswer[], change?: SubmitFiles) {
  const path = `/me/responses/${segment(responseId)}`;
  const data = { answers, ...(change?.keep ? { attachments: change.keep } : {}) };
  if (change && change.files.length > 0) {
    return request<EditedResponse>(path, {
      method: 'PATCH',
      body: multipartBody(data, change.files),
    });
  }
  return request<EditedResponse>(path, { ...json(data), method: 'PATCH' });
}

/**
 * 파일은 바이트 그대로 받는다. `text()`로 읽으면 CSV의 BOM이 사라진다.
 * 되묻기와 JSON 파일은 Content-Type이 같아서 본문의 `status`로 구분한다. 파일의 최상위에는 `status`가 없다.
 */
async function exportForm(formId: string, body: ExportRequest): Promise<ExportResult> {
  const init = json(body);
  const res = await fetch(`${baseUrl}/forms/${segment(formId)}/export`, {
    ...init,
    credentials: 'include',
    headers: { ...csrfHeaders(), ...init.headers },
  });
  if (!res.ok) throw new ApiError(res.status, parseErrorBody(await res.text()));
  const bytes = await res.arrayBuffer();
  const contentType = res.headers.get('Content-Type') ?? '';
  if (contentType.includes('application/json')) {
    const parsed = JSON.parse(new TextDecoder().decode(bytes)) as Partial<
      Extract<ExportResult, { status: 'clarify' }>
    >;
    if (parsed.status === 'clarify' && parsed.clarify) {
      return { status: 'clarify', clarify: parsed.clarify };
    }
  }
  return { status: 'file', file: new Blob([bytes], { type: contentType }) };
}

/** 공유 링크 QR PNG(SPEC 126행). 이미지라 바이트 그대로 받는다. 오류(401·403·409)는 JSON 본문이다. */
async function formQr(formId: string): Promise<Blob> {
  const res = await fetch(`${baseUrl}/forms/${segment(formId)}/qr`, {
    credentials: 'include',
    headers: csrfHeaders(),
  });
  if (!res.ok) throw new ApiError(res.status, parseErrorBody(await res.text()));
  return res.blob();
}

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

/** 실제 API 연결점. 화면이 호출하는 것은 `/me`, 프로필, 자동채우기, 내 폼함·저장, 제출 이력, 폼 공개 조회, 응답 초안, 제출, 결과, 응답 상세, 빌더뿐이다. */
export const api = {
  me,
  profiles: () => request<Profile[]>('/me/profiles'),
  createProfile: (body: ProfileInput) => request<CreatedProfile>('/me/profiles', json(body)),
  updateProfile: (profileId: string, body: ProfileInput) =>
    request<void>(`/me/profiles/${segment(profileId)}`, { ...json(body), method: 'PUT' }),
  deleteProfile: (profileId: string) =>
    request<void>(`/me/profiles/${segment(profileId)}`, { method: 'DELETE' }),
  autofill: (formId: string, profileId: string) =>
    request<AutofillResult>(`/forms/${segment(formId)}/autofill`, json({ profile_id: profileId })),
  savedForms: () => request<SavedFormSummary[]>('/me/forms'),
  createdForms: () => request<CreatorFormSummary[]>('/me/created-forms'),
  consentTemplates: () => request<ConsentTemplate[]>('/consent-templates'),
  createForm: (definition: FormDefinition) => request<CreatedForm>('/forms', json(definition)),
  formDefinition: (formId: string) =>
    request<CreatorFormDefinition>(`/forms/${segment(formId)}/definition`),
  updateForm: (formId: string, body: Partial<FormDefinition> & { status?: FormStatus }) =>
    request<FormStateView>(`/forms/${segment(formId)}`, { ...json(body), method: 'PATCH' }),
  uploadAttachment,
  deleteReceivedResponses: (formId: string) =>
    request<void>(`/forms/${segment(formId)}/responses`, { method: 'DELETE' }),
  deleteResponseAttachments: (formId: string) =>
    request<void>(`/forms/${segment(formId)}/response-attachments`, { method: 'DELETE' }),
  deleteForm: (formId: string) => request<void>(`/forms/${segment(formId)}`, { method: 'DELETE' }),
  deleteAttachment: (formId: string, questionId: string | null, attachmentId: string) =>
    request<void>(`${attachmentPath(formId, questionId)}/${segment(attachmentId)}`, {
      method: 'DELETE',
    }),
  storage: () => request<StorageUsage>('/me/storage'),
  copyForm: (formId: string) =>
    request<CreatedForm>(`/forms/${segment(formId)}/copy`, { method: 'POST' }),
  branchGraph: (formId: string) => request<BranchGraph>(`/forms/${segment(formId)}/branch-graph`),
  formQr,
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
  submitWithFiles: (formId: string, body: SubmitResponseInput, files: FileToSend[]) =>
    request<SubmittedResponse>(`/forms/${segment(formId)}/responses`, {
      method: 'POST',
      body: multipartBody(body, files),
    }),
  analyze: (formId: string, body: AnalyzeRequest) =>
    request<AnalyzeResponse>(`/forms/${segment(formId)}/analyze`, json(body)),
  saveForm: (body: SaveFormInput) => request<void>('/me/saved-forms', json(body)),
  myResponses: () => request<ResponseHistoryItem[]>('/me/responses'),
  myResponse: (responseId: string) =>
    request<ResponseHistoryDetail>(`/me/responses/${segment(responseId)}`),
  editResponse,
  cancelResponse: (responseId: string) =>
    request<void>(`/me/responses/${segment(responseId)}`, { method: 'DELETE' }),
  results: (formId: string) => request<FormResults>(`/forms/${segment(formId)}/results`),
  responseDetail: (formId: string, responseId: string) =>
    request<ResponseDetail>(`/forms/${segment(formId)}/responses/${segment(responseId)}`),
  exportForm,
};
