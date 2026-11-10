// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const me = {
  user_id: 'u1',
  name: '가상 사용자',
  email: 'virtual@example.com',
  deadline_reminder: true,
};

const savedForm = {
  form_id: 'f1',
  title: '행사 참석 신청',
  deadline: '2026-11-12T05:00:00Z',
  state: 'saved',
  has_draft: false,
  saved_at: '2026-11-10T00:00:00Z',
  submitted_at: null,
  closed: false,
};

type Call = [string, RequestInit | undefined];
let fetchMock: ReturnType<typeof vi.fn>;

interface StubNotification {
  notification_id: string;
  form_id: string;
  type: 'deadline';
  created_at: string;
  read: boolean;
}

/** 서버처럼 읽음 처리를 기억한다. 화면을 옮기면 목록을 다시 읽기 때문이다. */
function stub(notifications: StubNotification[]) {
  fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const path = url.replace('http://api.test', '');
    const method = init?.method ?? 'GET';
    const ok = (body: unknown) =>
      Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
    if (method === 'GET' && path === '/me') return ok(me);
    if (method === 'GET' && path === '/me/notifications') return ok(notifications);
    if (method === 'GET' && path === '/me/forms') return ok([savedForm]);
    if (method === 'PATCH') {
      notifications.forEach((n) => {
        if (path === `/me/notifications/${n.notification_id}`) n.read = true;
      });
      return Promise.resolve(new Response('', { status: 200 }));
    }
    if (method === 'POST' && path === '/me/logout') {
      return Promise.resolve(new Response(null, { status: 204 }));
    }
    if (path.startsWith('/forms/')) {
      return Promise.resolve(new Response(JSON.stringify({ error: 'not_found' }), { status: 404 }));
    }
    return ok([]);
  });
  vi.stubGlobal('fetch', fetchMock);
}

const sent = (method: string, path: string) =>
  fetchMock.mock.calls
    .map((call) => call as Call)
    .filter(([url, init]) => (init?.method ?? 'GET') === method && url === `http://api.test${path}`)
    .map(([, init]) => (init?.body ? (JSON.parse(String(init.body)) as unknown) : undefined));

async function openLive(path: string) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  const { App } = await import('../App');
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

const banner = () => within(screen.getByRole('banner'));

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe('마감 알림(AC18)', () => {
  it('안 읽은 개수를 보이고, 목록에서 폼 제목과 한국 시각 마감을 보인다', async () => {
    stub([
      {
        notification_id: 'n1',
        form_id: 'f1',
        type: 'deadline',
        created_at: '2026-11-11T05:00:00Z',
        read: false,
      },
    ]);
    await openLive('/me/responses');

    const bell = await banner().findByRole('button', { name: '마감 알림, 안 읽은 알림 1개' });
    fireEvent.click(bell);

    const item = await banner().findByRole('button', { name: /행사 참석 신청/ });
    expect(item.textContent).toContain('2026년 11월 12일 14:00');
    expect(item.textContent).toContain('안 읽음');
  });

  it('알림을 누르면 읽음으로 바꾸고 그 폼을 열며 안 읽은 개수가 사라진다', async () => {
    stub([
      {
        notification_id: 'n1',
        form_id: 'f1',
        type: 'deadline',
        created_at: '2026-11-11T05:00:00Z',
        read: false,
      },
    ]);
    await openLive('/me/responses');
    fireEvent.click(await banner().findByRole('button', { name: '마감 알림, 안 읽은 알림 1개' }));

    fireEvent.click(await banner().findByRole('button', { name: /행사 참석 신청/ }));

    await waitFor(() => expect(sent('PATCH', '/me/notifications/n1')).toEqual([{ read: true }]));
    await waitFor(() => expect(sent('GET', '/forms/f1')).toHaveLength(1));
    expect(await banner().findByRole('button', { name: '마감 알림' })).toBeTruthy();
  });

  it('알림이 없으면 개수 없이 안내하고, 종 목록에는 켜기·끄기 스위치가 없다', async () => {
    stub([]);
    await openLive('/me/responses');
    fireEvent.click(await banner().findByRole('button', { name: '마감 알림' }));

    expect(await banner().findByText('아직 알림이 없어요.')).toBeTruthy();
    expect(banner().queryByRole('switch')).toBeNull();
  });

  it('사용자명을 누르면 계정 메뉴가 펼쳐지고, 스위치로 알림을 끄면 설정을 저장한다', async () => {
    stub([]);
    await openLive('/me/responses');
    fireEvent.click(await banner().findByRole('button', { name: '마감 알림' }));
    fireEvent.click(await banner().findByRole('button', { name: /가상 사용자/ }));

    expect(banner().queryByText('아직 알림이 없어요.')).toBeNull();
    const toggle = (await banner().findByRole('switch')) as HTMLInputElement;
    await waitFor(() => expect(toggle.disabled).toBe(false));
    expect(toggle.checked).toBe(true);
    fireEvent.click(toggle);

    await waitFor(() =>
      expect(sent('PATCH', '/me/settings')).toEqual([{ deadline_reminder: false }]),
    );
    await waitFor(() => expect(toggle.checked).toBe(false));
  });

  it('계정 메뉴의 로그아웃은 서버 세션을 끝내고 로그인 링크를 보인다', async () => {
    stub([]);
    await openLive('/me/responses');
    fireEvent.click(await banner().findByRole('button', { name: /가상 사용자/ }));

    fireEvent.click(await banner().findByRole('button', { name: '로그아웃' }));

    await waitFor(() => expect(sent('POST', '/me/logout')).toHaveLength(1));
    expect(await banner().findByRole('link', { name: '로그인' })).toBeTruthy();
    expect(banner().queryByText('가상 사용자')).toBeNull();
  });

  it('내 프로필에는 마감 알림 스위치를 두지 않는다(사람 결정)', async () => {
    stub([]);
    await openLive('/me/profile');

    const main = within(await screen.findByRole('main'));
    expect(await main.findByRole('heading', { name: '내 프로필' })).toBeTruthy();
    expect(main.queryByRole('switch')).toBeNull();
  });
});
