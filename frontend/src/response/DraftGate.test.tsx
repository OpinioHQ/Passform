// @vitest-environment jsdom
/**
 * 응답 초안의 자동 저장과 복원(T5 #53, AC82, AC83 화면 부분). `VITE_USE_API=true`에서 공유 링크 라우트로 연다.
 * 서버는 fetch 모의로 대신한다. 테스트 이름 앞의 이름은 SPEC 추적표(659·660행)의 케이스 이름이다.
 */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PublicForm } from '../types/api';

const question = (id: string, note: string) => ({
  question_id: id,
  template_id: null,
  question_note: note,
  question_type: 'short_answer' as const,
  options: [],
  required: false,
  profile_key: null,
  attachments: [],
  branch_rules: [],
});

function publicForm(formId: string, version: number, title = '동아리 신청'): PublicForm {
  return {
    form_id: formId,
    definition_version: version,
    title,
    note: '',
    note_attachments: [],
    deadline: null,
    display: { default_mode: 'all_questions', allowed_modes: ['one_question', 'all_questions'] },
    payment_link: null,
    consent: {
      items: ['문항별 응답 내용'],
      purpose: '신청 확인',
      retention: '학기 종료 후 삭제',
      refusal_notice: '동의하지 않으면 제출할 수 없습니다.',
      ai_summary: null,
    },
    sections: [
      {
        section_id: 's1',
        title: '신청',
        questions: [
          question('q1', '하고 싶은 말'),
          question('q2', version === 1 ? '옛 문항' : '새 문항'),
        ],
      },
    ],
  };
}

type Reply = { status: number; body?: unknown };
type Handler = (method: string, path: string, body: unknown) => Reply | undefined;
let fetchMock: ReturnType<typeof vi.fn>;

const ME = {
  user_id: 'u1',
  name: '가상 사용자',
  email: 'virtual@example.com',
  deadline_reminder: true,
};
const submitted = (id: string) => ({
  status: 201,
  body: {
    response_id: id,
    form_version: 2,
    status: 'submitted',
    content: [],
    attachments: [],
    submitted_at: '2026-11-07T00:00:00Z',
    updated_at: null,
  },
});

async function openLive(path: string, handler: Handler) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    const target = url.replace('http://api.test', '');
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    const reply = handler(method, target, body) ?? { status: 404, body: { error: 'not_found' } };
    return Promise.resolve(
      new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
        status: reply.status,
      }),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  const { App } = await import('../App');
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

const calls = (method: string, path: string) =>
  fetchMock.mock.calls
    .map((call) => call as [string, RequestInit | undefined])
    .filter(([url, init]) => (init?.method ?? 'GET') === method && url === `http://api.test${path}`)
    .map(([, init]) => (init?.body ? JSON.parse(String(init.body)) : undefined));

const field = async (label: string) => (await screen.findByLabelText(label)) as HTMLInputElement;
const type = async (label: string, value: string) =>
  fireEvent.change(await field(label), { target: { value } });
const statusLine = () => screen.getByTestId('autosave-status');
const draftKey = (formId: string) => `passform:draft:${formId}`;

function storeBrowserDraft(form: PublicForm, answers: { question_id: string; value: string }[]) {
  localStorage.setItem(
    draftKey(form.form_id),
    JSON.stringify({
      form_version: form.definition_version,
      form_definition: form,
      answers,
      saved_at: '2026-11-07T00:00:00Z',
    }),
  );
}

const anonymous =
  (form: PublicForm, extra?: Handler): Handler =>
  (method, path, body) => {
    if (path === '/me') return { status: 401, body: { error: 'unauthorized' } };
    if (method === 'GET' && path === `/f/${form.form_id}`) return { status: 200, body: form };
    return extra?.(method, path, body);
  };

const loggedIn =
  (form: PublicForm, extra?: Handler): Handler =>
  (method, path, body) => {
    if (path === '/me') return { status: 200, body: ME };
    if (method === 'GET' && path === `/f/${form.form_id}`) return { status: 200, body: form };
    return extra?.(method, path, body);
  };

beforeEach(() => {
  window.scrollTo = vi.fn();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
});

describe('비로그인: 이 브라우저에만 저장 (AC82)', () => {
  const form = publicForm('form-1', 2);

  it('respondent_draft_restores_same_browser: 쓰면 바로 저장되고, 다시 열면 복원된다', async () => {
    await openLive('/f/form-1', anonymous(form));
    await field('하고 싶은 말');
    expect(statusLine().textContent).toContain('이 브라우저에만 저장돼요');
    await type('하고 싶은 말', '잘 부탁해요');
    await waitFor(() => expect(statusLine().textContent).toContain('이 브라우저에 저장됨'));
    expect(JSON.parse(localStorage.getItem(draftKey('form-1')) ?? '{}').answers).toEqual([
      { question_id: 'q1', value: '잘 부탁해요' },
    ]);
    // 초안은 서버로 보내지 않는다.
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('response-draft'))).toBe(
      false,
    );

    cleanup();
    await openLive('/f/form-1', anonymous(form));
    expect((await field('하고 싶은 말')).value).toBe('잘 부탁해요');
    expect(statusLine().textContent).toContain('저장한 답을 불러왔어요');
  });

  it('다른 브라우저(저장 내용 없음)에는 초안이 없다', async () => {
    await openLive('/f/form-1', anonymous(form));
    await type('하고 싶은 말', '잘 부탁해요');
    cleanup();
    localStorage.clear();
    await openLive('/f/form-1', anonymous(form));
    expect((await field('하고 싶은 말')).value).toBe('');
  });

  it('초안은 폼마다 따로 저장해 다른 폼에 섞이지 않는다', async () => {
    const other = publicForm('form-2', 2, '다른 폼');
    await openLive('/f/form-1', anonymous(form));
    await type('하고 싶은 말', '폼 1의 답');
    cleanup();
    await openLive('/f/form-2', anonymous(other));
    expect((await field('하고 싶은 말')).value).toBe('');
    await type('하고 싶은 말', '폼 2의 답');
    expect(JSON.parse(localStorage.getItem(draftKey('form-1')) ?? '{}').answers[0].value).toBe(
      '폼 1의 답',
    );
  });

  it('제출에 성공하면 브라우저 초안을 지운다(SPEC 5절 216행)', async () => {
    await openLive(
      '/f/form-1',
      anonymous(form, (method, path) =>
        method === 'POST' && path === '/forms/form-1/responses' ? submitted('r1') : undefined,
      ),
    );
    await type('하고 싶은 말', '잘 부탁해요');
    fireEvent.click(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
    fireEvent.click(screen.getByRole('button', { name: /^답변 제출/ }));
    await screen.findByText('응답을 제출했어요');
    expect(localStorage.getItem(draftKey('form-1'))).toBeNull();
    expect(calls('POST', '/forms/form-1/responses')[0].draft_response_id).toBeUndefined();
  });

  it('제출이 실패하면 브라우저 초안을 남긴다', async () => {
    await openLive(
      '/f/form-1',
      anonymous(form, (method, path) =>
        method === 'POST' && path === '/forms/form-1/responses'
          ? { status: 400, body: { error: 'consent_required' } }
          : undefined,
      ),
    );
    await type('하고 싶은 말', '잘 부탁해요');
    fireEvent.click(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
    fireEvent.click(screen.getByRole('button', { name: /^답변 제출/ }));
    await screen.findByText(/필수 동의가 필요해요/);
    expect(localStorage.getItem(draftKey('form-1'))).not.toBeNull();
  });

  it('옛 버전 초안은 기존 문항과 함께 보이고, 지우면 최신 폼으로 새로 시작한다', async () => {
    storeBrowserDraft(publicForm('form-1', 1), [{ question_id: 'q2', value: '옛 답' }]);
    await openLive('/f/form-1', anonymous(form));
    expect(await screen.findByText('작성하던 답은 이전 버전 기준이에요')).toBeTruthy();
    expect(screen.getByText(/폼 버전 1에서 저장한 답/)).toBeTruthy();
    expect(screen.getByText('옛 문항')).toBeTruthy();
    expect(screen.getByText('옛 답')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^답변 제출/ })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '기존 답 지우고 새로 시작' }));
    expect((await field('새 문항')).value).toBe('');
    expect(localStorage.getItem(draftKey('form-1'))).toBeNull();
  });
});

describe('로그인: 계정에 저장 (AC82, AC83)', () => {
  const form = publicForm('form-1', 2);
  const draftPath = '/me/forms/form-1/response-draft';
  const accountDraft = (version: number, value: string) => ({
    response_id: 'draft-1',
    form_version: version,
    form_definition: publicForm('form-1', version),
    answers: [{ question_id: version === 1 ? 'q2' : 'q1', value }],
    saved_at: '2026-11-07T00:00:00Z',
  });

  it('login_draft_text_cross_device: 다른 기기에서 열어도 계정 초안의 답을 복원한다', async () => {
    await openLive(
      '/f/form-1',
      loggedIn(form, (method, path) =>
        method === 'GET' && path === draftPath
          ? { status: 200, body: accountDraft(2, '계정의 답') }
          : undefined,
      ),
    );
    expect((await field('하고 싶은 말')).value).toBe('계정의 답');
    expect(statusLine().textContent).toContain('계정에 저장한 답을 불러왔어요');
    expect(localStorage.length).toBe(0);
  });

  it('입력이 멈추고 1초 뒤 한 번 저장하고, 제출은 그 초안 ID로 한다', async () => {
    let saves = 0;
    await openLive(
      '/f/form-1',
      loggedIn(form, (method, path) => {
        if (method === 'PUT' && path === draftPath) {
          saves++;
          return {
            status: saves === 1 ? 201 : 200,
            body: {
              response_id: 'draft-9',
              status: 'in_progress',
              saved_at: '2026-11-07T00:00:00Z',
            },
          };
        }
        if (method === 'POST' && path === '/forms/form-1/responses') return submitted('draft-9');
        return undefined;
      }),
    );
    await type('하고 싶은 말', '잘');
    await type('하고 싶은 말', '잘 부탁');
    await type('하고 싶은 말', '잘 부탁해요');
    expect(statusLine().textContent).toBe('저장 중…');
    expect(calls('PUT', draftPath)).toHaveLength(0);
    await waitFor(() => expect(statusLine().textContent).toContain('계정에 저장됨'), {
      timeout: 3000,
    });
    expect(calls('PUT', draftPath)).toEqual([
      { form_version: 2, answers: [{ question_id: 'q1', value: '잘 부탁해요' }] },
    ]);
    // 초안 저장은 동의·제출이 아니다. 제출 API를 부르지 않는다.
    expect(calls('POST', '/forms/form-1/responses')).toHaveLength(0);

    fireEvent.click(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
    fireEvent.click(screen.getByRole('button', { name: /^답변 제출/ }));
    await screen.findByText('응답을 제출했어요');
    expect(calls('POST', '/forms/form-1/responses')[0]).toMatchObject({
      draft_response_id: 'draft-9',
      consent_agreed: true,
      form_version: 2,
    });
  });

  it('자동 저장 전에 제출하면 먼저 저장해 같은 초안으로 제출한다', async () => {
    await openLive(
      '/f/form-1',
      loggedIn(form, (method, path) => {
        if (method === 'PUT' && path === draftPath) {
          return {
            status: 201,
            body: {
              response_id: 'draft-5',
              status: 'in_progress',
              saved_at: '2026-11-07T00:00:00Z',
            },
          };
        }
        if (method === 'POST' && path === '/forms/form-1/responses') return submitted('draft-5');
        return undefined;
      }),
    );
    await type('하고 싶은 말', '바로 제출');
    fireEvent.click(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
    fireEvent.click(screen.getByRole('button', { name: /^답변 제출/ }));
    await screen.findByText('응답을 제출했어요');
    expect(calls('PUT', draftPath)).toHaveLength(1);
    expect(calls('POST', '/forms/form-1/responses')[0].draft_response_id).toBe('draft-5');
  });

  it('저장에 실패하면 경고하고 저장됨을 보이지 않는다', async () => {
    await openLive(
      '/f/form-1',
      loggedIn(form, (method, path) =>
        method === 'PUT' && path === draftPath
          ? { status: 500, body: { error: 'internal_error' } }
          : undefined,
      ),
    );
    await type('하고 싶은 말', '잘 부탁해요');
    const failure = await screen.findByRole('alert', {}, { timeout: 3000 });
    expect(failure.textContent).toBe('저장하지 못했어요. 마지막으로 저장된 내용은 그대로 있어요.');
    expect(screen.queryByText(/저장됨/)).toBeNull();
  });

  it('저장이 끝나기 전에 화면을 나가려 하면 경고한다', async () => {
    await openLive(
      '/f/form-1',
      loggedIn(form, (method, path) =>
        method === 'PUT' && path === draftPath
          ? {
              status: 201,
              body: {
                response_id: 'draft-1',
                status: 'in_progress',
                saved_at: '2026-11-07T00:00:00Z',
              },
            }
          : undefined,
      ),
    );
    await type('하고 싶은 말', '잘 부탁해요');
    const leaving = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(leaving);
    expect(leaving.defaultPrevented).toBe(true);

    await waitFor(() => expect(statusLine().textContent).toContain('계정에 저장됨'), {
      timeout: 3000,
    });
    const afterSave = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(afterSave);
    expect(afterSave.defaultPrevented).toBe(false);
  });

  it('stale_draft_not_submitted의 화면: 옛 버전 초안은 기존 문항과 보이고, 지우면 DELETE 뒤 새로 시작한다', async () => {
    await openLive(
      '/f/form-1',
      loggedIn(form, (method, path) => {
        if (method === 'GET' && path === draftPath)
          return { status: 200, body: accountDraft(1, '옛 답') };
        if (method === 'DELETE' && path === draftPath) return { status: 204 };
        return undefined;
      }),
    );
    expect(await screen.findByText('작성하던 답은 이전 버전 기준이에요')).toBeTruthy();
    expect(screen.getByText('옛 문항')).toBeTruthy();
    expect(screen.getByText('옛 답')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '기존 답 지우고 새로 시작' }));
    expect((await field('새 문항')).value).toBe('');
    expect(calls('DELETE', draftPath)).toHaveLength(1);
  });

  it('계정 초안이 없고 이 브라우저에 로그인 전 초안이 있으면 묻고, 이어 쓰면 계정에 옮긴 뒤 브라우저 초안을 지운다', async () => {
    storeBrowserDraft(form, [{ question_id: 'q1', value: '로그인 전 답' }]);
    await openLive(
      '/f/form-1',
      loggedIn(form, (method, path) =>
        method === 'PUT' && path === draftPath
          ? {
              status: 201,
              body: {
                response_id: 'draft-2',
                status: 'in_progress',
                saved_at: '2026-11-07T00:00:00Z',
              },
            }
          : undefined,
      ),
    );
    expect(await screen.findByText('이 브라우저에 로그인 전에 쓰던 답이 있어요')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '이어서 쓰기' }));
    expect((await field('하고 싶은 말')).value).toBe('로그인 전 답');
    expect(calls('PUT', draftPath)).toEqual([
      { form_version: 2, answers: [{ question_id: 'q1', value: '로그인 전 답' }] },
    ]);
    expect(localStorage.getItem(draftKey('form-1'))).toBeNull();
  });

  it('로그인 전 초안을 새로 쓰기로 고르면 브라우저 초안은 그대로 두고 빈 답으로 시작한다', async () => {
    storeBrowserDraft(form, [{ question_id: 'q1', value: '로그인 전 답' }]);
    await openLive('/f/form-1', loggedIn(form));
    fireEvent.click(await screen.findByRole('button', { name: '새로 쓰기' }));
    expect((await field('하고 싶은 말')).value).toBe('');
    expect(localStorage.getItem(draftKey('form-1'))).not.toBeNull();
    expect(calls('PUT', draftPath)).toHaveLength(0);
  });

  it('계정 초안을 읽지 못하면 자동 저장을 멈춰 저장된 초안을 덮지 않는다', async () => {
    await openLive(
      '/f/form-1',
      loggedIn(form, (method, path) =>
        method === 'GET' && path === draftPath
          ? { status: 500, body: { error: 'internal_error' } }
          : undefined,
      ),
    );
    expect(await screen.findByText(/자동 저장을 멈췄어요/)).toBeTruthy();
    await type('하고 싶은 말', '새 답');
    await new Promise((resolve) => setTimeout(resolve, 1300));
    expect(calls('PUT', draftPath)).toHaveLength(0);
  });

  it('응답 전체 지우기는 계정 초안을 지우고 빈 답으로 돌아간다', async () => {
    await openLive(
      '/f/form-1',
      loggedIn(form, (method, path) => {
        if (method === 'GET' && path === draftPath)
          return { status: 200, body: accountDraft(2, '계정의 답') };
        if (method === 'DELETE' && path === draftPath) return { status: 204 };
        return undefined;
      }),
    );
    expect((await field('하고 싶은 말')).value).toBe('계정의 답');
    fireEvent.click(screen.getByRole('button', { name: '응답 전체 지우기' }));
    await waitFor(() => expect(calls('DELETE', draftPath)).toHaveLength(1));
    await waitFor(() =>
      expect(screen.getByLabelText<HTMLInputElement>('하고 싶은 말').value).toBe(''),
    );
  });
});
