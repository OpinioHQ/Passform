// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const templates = [
  { consent_template_id: 'other', purpose: '다른 목적', retention: '다른 보유' },
  { consent_template_id: 'consent_default', purpose: '기본 목적', retention: '기본 보유\n둘째 줄' },
];

const draft = (questionNote = '좋아하는 색은?') => ({
  title: '서버의 초안',
  note: '',
  settings: { editable: false, cancellable: false, display: { default_mode: 'one_question' } },
  payment_link: null,
  consent_template_id: 'consent_default',
  note_attachments: [],
  sections: [
    {
      section_id: 's1',
      title: '',
      questions: [
        {
          question_id: 'q1',
          template_id: null,
          question_note: questionNote,
          question_type: 'short_answer',
          options: [],
          required: false,
          profile_key: null,
          attachments: [],
          branch_rules: [],
        },
      ],
    },
  ],
  status: 'draft',
  share_url: null,
  definition_version: null,
});

type Reply = { status: number; body?: unknown };
type Handler = (method: string, path: string, body: unknown) => Reply | undefined;

function stubApi(handler: Handler) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    const path = url.replace('http://api.test', '');
    const body: unknown = init?.body ? JSON.parse(init.body as string) : undefined;
    const reply =
      path === '/me' ? { status: 401 } : (handler(method, path, body) ?? { status: 500 });
    return Promise.resolve(
      new Response(reply.body === undefined ? '' : JSON.stringify(reply.body), {
        status: reply.status,
      }),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  const calls = (method: string, path: string) =>
    fetchMock.mock.calls.filter(
      ([url, init]) => url === `http://api.test${path}` && (init?.method ?? 'GET') === method,
    );
  const bodyOf = (call: [string, RequestInit?]) =>
    JSON.parse(call[1]?.body as string) as Record<string, unknown>;
  return { fetchMock, calls, bodyOf };
}

function CurrentPath() {
  return <output data-testid="path">{useLocation().pathname}</output>;
}

async function openLive(path: string) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  const { App } = await import('../App');
  render(
    <React.StrictMode>
      <MemoryRouter initialEntries={[path]}>
        <App />
        <CurrentPath />
      </MemoryRouter>
    </React.StrictMode>,
  );
}

const editPath = '/creator/forms/f1/edit';
const definitionHandler =
  (patch: Handler = () => undefined): Handler =>
  (method, path, body) => {
    if (method === 'GET' && path === '/forms/f1/definition') return { status: 200, body: draft() };
    if (method === 'GET' && path === '/consent-templates') return { status: 200, body: templates };
    if (method === 'PATCH' && path === '/forms/f1') return patch(method, path, body);
    return undefined;
  };

const button = (name: string) => screen.getByRole('button', { name });

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe('새 폼', () => {
  it('들어오면 초안을 한 번만 만들고 기본 동의 템플릿을 골라 편집 주소로 간다', async () => {
    let created: unknown = null;
    const { calls, bodyOf } = stubApi((method, path, body) => {
      if (method === 'GET' && path === '/consent-templates') {
        return { status: 200, body: templates };
      }
      if (method === 'POST' && path === '/forms') {
        created = body;
        return { status: 201, body: { form_id: 'f1', status: 'draft' } };
      }
      if (method === 'GET' && path === '/forms/f1/definition') {
        return {
          status: 200,
          body: {
            ...(created as object),
            status: 'draft',
            share_url: null,
            definition_version: null,
          },
        };
      }
      return undefined;
    });
    await openLive('/creator/forms/new');
    await waitFor(() => expect(screen.getByTestId('path').textContent).toBe(editPath));

    const posts = calls('POST', '/forms');
    expect(posts).toHaveLength(1);
    const body = bodyOf(posts[0]);
    expect(Object.keys(body).sort()).toEqual(
      [
        'consent_template_id',
        'note',
        'note_attachments',
        'payment_link',
        'sections',
        'settings',
        'title',
      ].sort(),
    );
    expect(body.consent_template_id).toBe('consent_default');

    const select = (await screen.findByLabelText('동의 템플릿')) as HTMLSelectElement;
    expect(select.value).toBe('consent_default');
    expect(screen.getByText(/기본 목적/)).toBeTruthy();
    expect(screen.getByText(/기본 보유/).className).toContain('keep-lines');
  });
});

describe('저장', () => {
  it('실패하면 저장됨을 보이지 않고, 성공하면 상태 키 없이 보내 저장됨을 보인다', async () => {
    let reply: Reply = { status: 500 };
    const { calls, bodyOf } = stubApi(definitionHandler(() => reply));
    await openLive(editPath);
    const title = (await screen.findByLabelText('폼 제목')) as HTMLInputElement;
    fireEvent.change(title, { target: { value: '고친 제목' } });

    fireEvent.click(button('초안 저장'));
    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain('저장하지 못했어요');
    expect(screen.queryByText(/저장됨/)).toBeNull();
    expect(title.value).toBe('고친 제목');

    reply = { status: 200, body: { status: 'draft', share_url: null, definition_version: null } };
    fireEvent.click(button('초안 저장'));
    expect(await screen.findByText(/저장됨/)).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();

    const sent = bodyOf(calls('PATCH', '/forms/f1')[1]);
    expect(sent.title).toBe('고친 제목');
    for (const key of ['status', 'share_url', 'definition_version']) {
      expect(key in sent).toBe(false);
    }
  });
});

describe('배포', () => {
  it('400이면 상태를 유지하고 코드 대신 쉬운 말로 알린다', async () => {
    stubApi(definitionHandler(() => ({ status: 400, body: { error: 'invalid_payment_link' } })));
    await openLive(editPath);
    const title = (await screen.findByLabelText('폼 제목')) as HTMLInputElement;
    fireEvent.change(title, { target: { value: '유지될 제목' } });

    fireEvent.click(button('배포'));
    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('https://');
    expect(alert.textContent).not.toContain('invalid_payment_link');
    expect(screen.getByText('초안')).toBeTruthy();
    expect(screen.queryByLabelText('공유 링크')).toBeNull();
    expect(title.value).toBe('유지될 제목');
    expect(button('배포')).toBeTruthy();
  });

  it('성공하면 공유 링크와 폼 버전을 보여 주고 status open을 함께 보낸다', async () => {
    const { calls, bodyOf } = stubApi(
      definitionHandler(() => ({
        status: 200,
        body: { status: 'open', share_url: 'https://app.test/f/abc', definition_version: 1 },
      })),
    );
    await openLive(editPath);
    await screen.findByLabelText('폼 제목');

    fireEvent.click(button('배포'));
    const link = (await screen.findByLabelText('공유 링크')) as HTMLInputElement;
    expect(link.value).toBe('https://app.test/f/abc');
    expect(button('링크 복사')).toBeTruthy();
    expect(screen.getByText('배포 중 · 폼 버전 1')).toBeTruthy();
    expect(button('응답 받기 끄기')).toBeTruthy();

    const sent = bodyOf(calls('PATCH', '/forms/f1')[0]);
    expect(sent.status).toBe('open');
    expect(sent.title).toBe('서버의 초안');
  });
});

describe('미리보기 (AC81)', () => {
  it('저장 전 내용을 보여 주고 제출해도 API를 부르지 않으며 돌아오면 그대로다', async () => {
    const { fetchMock } = stubApi(definitionHandler());
    await openLive(editPath);
    const note = await screen.findByLabelText('질문 1 문구');
    fireEvent.change(note, { target: { value: '저장 전 문구' } });
    await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(0));
    const before = fetchMock.mock.calls.length;

    fireEvent.click(button('미리보기'));
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('미리보기');
    // 한 질문씩 폼이라 응답 화면과 같이 시작 버튼을 먼저 보인다(T4 #52, 사람 결정 A안).
    expect(screen.queryByText('저장 전 문구')).toBeNull();
    fireEvent.click(button('시작하기'));
    expect(screen.getByText('저장 전 문구')).toBeTruthy();
    fireEvent.click(button('다음'));
    fireEvent.click(button('내용 보기'));
    expect(screen.getByText(/기본 보유/).className).toContain('keep-lines');

    fireEvent.click(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
    fireEvent.click(screen.getByRole('button', { name: /답변 제출/ }));
    expect(screen.getByText('미리보기라 제출되지 않아요.')).toBeTruthy();
    expect(fetchMock.mock.calls.length).toBe(before);

    fireEvent.click(screen.getByRole('button', { name: /편집으로 돌아가기/ }));
    expect((screen.getByLabelText('질문 1 문구') as HTMLInputElement).value).toBe('저장 전 문구');
    expect(fetchMock.mock.calls.length).toBe(before);
  });
});

describe('내가 만든 폼', () => {
  it('실제 목록과 결과 버튼을 보여 주고 가짜 데이터와 탐색 버튼을 보이지 않는다', async () => {
    stubApi((method, path) =>
      method === 'GET' && path === '/me/created-forms'
        ? {
            status: 200,
            body: [
              {
                form_id: 'f1',
                title: '서버 폼',
                status: 'open',
                deadline: null,
                share_url: 'https://app.test/f/abc',
                response_count: 3,
              },
            ],
          }
        : undefined,
    );
    await openLive('/creator');
    expect(await screen.findByText('서버 폼')).toBeTruthy();
    expect(screen.getByText('배포 중')).toBeTruthy();
    expect(screen.getByText('3')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'https://app.test/f/abc' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /응답 탐색/ })).toBeNull();
    expect(screen.queryByText('함께 일하는 경험에 대한 설문')).toBeNull();

    expect(screen.getByRole('button', { name: '서버 폼 결과' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: '서버 폼 편집' }));
    expect(screen.getByTestId('path').textContent).toBe('/creator/forms/f1/edit');
  });

  it('빈 목록이면 안내하고 가짜 카드를 보이지 않는다', async () => {
    stubApi((method, path) =>
      method === 'GET' && path === '/me/created-forms' ? { status: 200, body: [] } : undefined,
    );
    await openLive('/creator');
    expect(await screen.findByText(/아직 만든 폼이 없어요/)).toBeTruthy();
    expect(screen.queryByText('함께 일하는 경험에 대한 설문')).toBeNull();
    expect(screen.queryByRole('button', { name: /폼 편집/ })).toBeNull();
  });
});
