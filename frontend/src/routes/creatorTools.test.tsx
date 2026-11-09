// @vitest-environment jsdom
// E14 #59: 제작자 도구(QR·폼 복사·초안 자동 저장)를 실제 API 모드 화면으로 시험한다. API는 fetch 모의로 바꾼다.
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const templates = [
  { consent_template_id: 'consent_default', purpose: '기본 목적', retention: '기본 보유' },
];

const definition = (state: {
  status: string;
  share_url: string | null;
  version: number | null;
}) => ({
  title: '서버 폼',
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
          question_note: '좋아하는 색은?',
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
  status: state.status,
  share_url: state.share_url,
  definition_version: state.version,
});

const summary = (formId: string, status: string, shareUrl: string | null) => ({
  form_id: formId,
  title: formId === 'f1' ? '서버 폼' : '초안 폼',
  status,
  deadline: null,
  share_url: shareUrl,
  response_count: 0,
});

type Reply = { status: number; body?: unknown; bytes?: Uint8Array<ArrayBuffer>; type?: string };
type Handler = (method: string, path: string, body: unknown) => Reply | Promise<Reply> | undefined;

function stubApi(handler: Handler) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    const path = url.replace('http://api.test', '');
    const body: unknown = init?.body ? JSON.parse(init.body as string) : undefined;
    const reply: Reply =
      path === '/me' ? { status: 401 } : ((await handler(method, path, body)) ?? { status: 500 });
    if (reply.bytes) {
      return new Response(new Blob([reply.bytes], { type: reply.type }), { status: reply.status });
    }
    return new Response(reply.body === undefined ? '' : JSON.stringify(reply.body), {
      status: reply.status,
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  const calls = (method: string, path: string) =>
    fetchMock.mock.calls.filter(
      ([url, init]) => url === `http://api.test${path}` && (init?.method ?? 'GET') === method,
    );
  return { fetchMock, calls };
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

const png = new Uint8Array([137, 80, 78, 71]);

beforeEach(() => {
  window.scrollTo = vi.fn();
  URL.createObjectURL = vi.fn(() => 'blob:qr');
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe('QR (AC10)', () => {
  it('내가 만든 폼에서 공유 링크가 있는 폼만 QR을 보이고 PNG로 내려받게 한다', async () => {
    const { calls } = stubApi((method, path) => {
      if (method === 'GET' && path === '/me/created-forms') {
        return {
          status: 200,
          body: [summary('f1', 'open', 'https://app.test/f/abc'), summary('f2', 'draft', null)],
        };
      }
      if (method === 'GET' && path === '/forms/f1/qr') {
        return { status: 200, bytes: png, type: 'image/png' };
      }
      return undefined;
    });
    await openLive('/creator');
    await screen.findByText('서버 폼');
    expect(screen.queryByRole('button', { name: '초안 폼 QR 보기' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '서버 폼 QR 보기' }));
    const image = (await screen.findByAltText('서버 폼 공유 링크 QR')) as HTMLImageElement;
    expect(image.getAttribute('src')).toBe('blob:qr');
    const download = screen.getByRole('link', { name: 'PNG 내려받기' });
    expect(download.getAttribute('href')).toBe('blob:qr');
    expect(download.hasAttribute('download')).toBe(true);
    expect(calls('GET', '/forms/f1/qr').length).toBeGreaterThan(0);
  });

  it('빌더의 공유 링크 옆에서 QR을 보이고, 409면 배포 전이라고 알린다', async () => {
    let qr: Reply = { status: 200, bytes: png, type: 'image/png' };
    stubApi((method, path) => {
      if (method === 'GET' && path === '/forms/f1/definition') {
        return {
          status: 200,
          body: definition({ status: 'open', share_url: 'https://app.test/f/abc', version: 1 }),
        };
      }
      if (method === 'GET' && path === '/consent-templates')
        return { status: 200, body: templates };
      if (method === 'GET' && path === '/forms/f1/qr') return qr;
      return undefined;
    });
    await openLive('/creator/forms/f1/edit');
    await screen.findByLabelText('폼 제목');

    fireEvent.click(screen.getByRole('button', { name: 'QR 보기' }));
    expect(await screen.findByAltText('서버 폼 공유 링크 QR')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'QR 닫기' }));
    qr = { status: 409, body: { error: 'not_open' } };
    fireEvent.click(screen.getByRole('button', { name: 'QR 보기' }));
    expect((await screen.findByRole('alert')).textContent).toBe('아직 배포하지 않아 QR이 없어요.');
  });
});
