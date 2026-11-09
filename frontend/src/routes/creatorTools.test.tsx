// @vitest-environment jsdom
// E14 #59: 제작자 도구(QR·폼 복사·초안 자동 저장)를 실제 API 모드 화면으로 시험한다. API는 fetch 모의로 바꾼다.
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// 자동 저장 간격(1초)만 줄여 시험을 빠르게 한다. 그 밖의 응답 초안 코드는 그대로 쓴다.
vi.mock('../response/draftSync', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../response/draftSync')>()),
  AUTOSAVE_DELAY_MS: 30,
}));

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
type Handler = (
  method: string,
  path: string,
  body: unknown,
) => Reply | undefined | Promise<Reply | undefined>;

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

const editPath = '/creator/forms/f1/edit';

/** 초안 하나를 서버처럼 들고 있다. PATCH는 보낸 최상위 키를 바꾸고, `patch`가 응답을 정한다. */
function draftServer(patch: (body: Record<string, unknown>) => Reply | Promise<Reply> = () => ok) {
  let saved: Record<string, unknown> = definition({
    status: 'draft',
    share_url: null,
    version: null,
  });
  const patches: Record<string, unknown>[] = [];
  const api = stubApi(async (method, path, body) => {
    if (method === 'GET' && path === '/forms/f1/definition') return { status: 200, body: saved };
    if (method === 'GET' && path === '/consent-templates') return { status: 200, body: templates };
    if (method === 'PATCH' && path === '/forms/f1') {
      const sent = body as Record<string, unknown>;
      patches.push(sent);
      const reply = await patch(sent);
      if (reply.status === 200) saved = { ...saved, ...sent };
      return reply;
    }
    return undefined;
  });
  return { ...api, patches };
}

const ok: Reply = {
  status: 200,
  body: { status: 'draft', share_url: null, definition_version: null },
};
// 빌더 위쪽의 저장 상태 줄. 주소를 보이는 <output>도 status 역할이라 위치로 고른다.
const statusText = () =>
  document.querySelector('.builder-tools [role="status"]')?.textContent ?? '';

describe('제작자 초안 자동 저장 (AC84 기본 모드)', () => {
  it('creator_draft_autosave_both_modes: 기본 모드에서 입력이 멈추면 저장 중·저장됨을 보이고 다시 열면 이어진다', async () => {
    let release: (reply: Reply) => void = () => {};
    const server = draftServer(() => new Promise<Reply>((resolve) => (release = resolve)));
    await openLive(editPath);
    const note = await screen.findByLabelText('질문 1 문구');
    fireEvent.change(note, { target: { value: '바뀐 문구' } });
    expect(statusText()).toContain('저장하지 않은 변경이 있어요.');

    await waitFor(() => expect(statusText()).toBe('저장 중…'));
    release(ok);
    await waitFor(() => expect(statusText()).toBe('저장됨'));
    expect(server.patches).toHaveLength(1);

    cleanup();
    await openLive(editPath);
    expect(((await screen.findByLabelText('질문 1 문구')) as HTMLInputElement).value).toBe(
      '바뀐 문구',
    );
  });

  it('creator_autosave_no_publish: status 없이 정의만 보내고, 의미 검증 없이 미완성 초안도 저장하며 초안으로 남는다', async () => {
    const server = draftServer();
    await openLive(editPath);
    const note = await screen.findByLabelText('질문 1 문구');
    fireEvent.change(note, { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('폼 제목'), { target: { value: '' } });

    await waitFor(() => expect(statusText()).toBe('저장됨'));
    expect(server.patches).toHaveLength(1);
    const sent = server.patches[0];
    for (const key of ['status', 'share_url', 'definition_version'])
      expect(key in sent).toBe(false);
    expect(sent.title).toBe('');
    expect(screen.getByText('초안')).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('저장이 끝나기 전에 바뀐 내용은 겹치지 않게 그 뒤에 마지막 내용으로 보낸다', async () => {
    const releases: ((reply: Reply) => void)[] = [];
    const server = draftServer(() => new Promise<Reply>((resolve) => releases.push(resolve)));
    await openLive(editPath);
    const title = await screen.findByLabelText('폼 제목');
    fireEvent.change(title, { target: { value: '첫 제목' } });
    await waitFor(() => expect(server.patches).toHaveLength(1));

    fireEvent.change(title, { target: { value: '둘째' } });
    fireEvent.change(title, { target: { value: '마지막 제목' } });
    await new Promise((resolve) => setTimeout(resolve, 120));
    expect(server.patches).toHaveLength(1);
    expect(statusText()).not.toBe('저장됨');

    releases[0](ok);
    await waitFor(() => expect(server.patches).toHaveLength(2));
    expect(server.patches[1].title).toBe('마지막 제목');
    expect(statusText()).not.toBe('저장됨');
    releases[1](ok);
    await waitFor(() => expect(statusText()).toBe('저장됨'));
  });

  it('creator_autosave_failure_visible: 실패하면 저장됨으로 바뀌지 않고, 나가려 하면 경고한다', async () => {
    let reply: Reply = { status: 500, body: { error: 'internal_error' } };
    draftServer(() => reply);
    await openLive(editPath);
    fireEvent.change(await screen.findByLabelText('폼 제목'), {
      target: { value: '못 저장한 제목' },
    });

    await waitFor(() => expect(statusText()).toContain('저장 실패'));
    expect(statusText()).not.toContain('저장됨');
    expect((screen.getByLabelText('폼 제목') as HTMLInputElement).value).toBe('못 저장한 제목');

    // 브라우저 닫기·새로고침
    const leaving = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(leaving);
    expect(leaving.defaultPrevented).toBe(true);

    // 앱 안 이동: 취소하면 머물고, 확인하면 나간다.
    const confirm = vi.fn(() => false);
    vi.stubGlobal('confirm', confirm);
    const menu = screen.getByRole('navigation', { name: '주 메뉴' }).querySelector('a');
    expect(menu).toBeTruthy();
    fireEvent.click(menu as HTMLAnchorElement);
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('path').textContent).toBe(editPath);

    // 다시 고쳐 저장에 성공하면 경고가 풀린다.
    reply = ok;
    fireEvent.change(screen.getByLabelText('폼 제목'), { target: { value: '저장한 제목' } });
    await waitFor(() => expect(statusText()).toBe('저장됨'));
    const after = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(after);
    expect(after.defaultPrevented).toBe(false);
    fireEvent.click(menu as HTMLAnchorElement);
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('path').textContent).not.toBe(editPath);
  });

  it('배포된 폼은 자동 저장하지 않는다', async () => {
    const { calls } = stubApi((method, path) => {
      if (method === 'GET' && path === '/forms/f1/definition') {
        return {
          status: 200,
          body: definition({ status: 'open', share_url: 'https://app.test/f/abc', version: 1 }),
        };
      }
      if (method === 'GET' && path === '/consent-templates')
        return { status: 200, body: templates };
      return undefined;
    });
    await openLive(editPath);
    fireEvent.change(await screen.findByLabelText('폼 제목'), { target: { value: '고친 제목' } });
    await new Promise((resolve) => setTimeout(resolve, 150));
    expect(calls('PATCH', '/forms/f1')).toHaveLength(0);
    expect(statusText()).toContain('저장하지 않은 변경이 있어요.');
  });
});

describe('폼 복사 (AC40)', () => {
  it('목록에서 복사하면 새 초안 빌더로 가고, 실패하면 머문 채 알린다', async () => {
    let copyReply: Reply = { status: 500, body: { error: 'internal_error' } };
    const { calls } = stubApi((method, path) => {
      if (method === 'GET' && path === '/me/created-forms') {
        return { status: 200, body: [summary('f1', 'open', 'https://app.test/f/abc')] };
      }
      if (method === 'POST' && path === '/forms/f1/copy') return copyReply;
      if (method === 'GET' && path === '/forms/f9/definition') {
        return {
          status: 200,
          body: definition({ status: 'draft', share_url: null, version: null }),
        };
      }
      if (method === 'GET' && path === '/consent-templates')
        return { status: 200, body: templates };
      return undefined;
    });
    await openLive('/creator');
    await screen.findByText('서버 폼');
    // 카드 버튼 순서: 폼 편집, 복제, 결과 보기. QR 보기는 공유 링크 오른쪽에 있다(사람 결정).
    const card = screen.getByRole('button', { name: '서버 폼 복제' }).closest('.button-row');
    expect(
      Array.from(card?.querySelectorAll('button') ?? []).map((b) => b.textContent?.trim()),
    ).toEqual(['폼 편집', '복제', '결과 보기']);

    fireEvent.click(screen.getByRole('button', { name: '서버 폼 복제' }));
    expect((await screen.findByRole('alert')).textContent).toContain('복제하지 못했어요');
    expect(screen.getByTestId('path').textContent).toBe('/creator');

    copyReply = { status: 201, body: { form_id: 'f9', status: 'draft' } };
    fireEvent.click(screen.getByRole('button', { name: '서버 폼 복제' }));
    await waitFor(() =>
      expect(screen.getByTestId('path').textContent).toBe('/creator/forms/f9/edit'),
    );
    expect(((await screen.findByLabelText('폼 제목')) as HTMLInputElement).value).toBe('서버 폼');
    expect(screen.queryByLabelText('공유 링크')).toBeNull();
    expect(calls('POST', '/forms/f1/copy')).toHaveLength(2);
  });
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

    // 공유 링크 줄 안, 링크 바로 오른쪽의 글자 버튼이다. 공유 링크가 없는 초안에는 없다.
    const qrButton = screen.getByRole('button', { name: '서버 폼 QR 보기' });
    expect(qrButton.className).toBe('text-button');
    expect(qrButton.previousElementSibling?.textContent).toBe('https://app.test/f/abc');
    fireEvent.click(qrButton);
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
