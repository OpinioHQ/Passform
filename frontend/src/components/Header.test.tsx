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

function stubMe(status: number) {
  const fetchMock = vi.fn((url: string) =>
    Promise.resolve(
      url.endsWith('/me')
        ? new Response(status === 200 ? JSON.stringify(me) : '', { status })
        : new Response('[]', { status: 200 }),
    ),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

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

describe('VITE_USE_API=true의 헤더', () => {
  it('로그인했으면 이름을 보이고 로그인 링크를 숨긴다', async () => {
    stubMe(200);
    await openLive('/me/responses');
    expect(await banner().findByText('가상 사용자')).toBeTruthy();
    expect(banner().queryByRole('link', { name: '로그인' })).toBeNull();
    expect(banner().queryByText('virtual@example.com')).toBeNull();
  });

  it('401이면 로그인 링크를 보인다', async () => {
    const fetchMock = stubMe(401);
    await openLive('/me/responses');
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(banner().getByRole('link', { name: '로그인' }).getAttribute('href')).toBe('/login');
    expect(banner().queryByText('가상 사용자')).toBeNull();
  });

  it('화면을 옮겨도 GET /me를 다시 부르지 않는다', async () => {
    const fetchMock = stubMe(200);
    await openLive('/me/responses');
    await banner().findByText('가상 사용자');
    fireEvent.click(banner().getByRole('link', { name: '내 프로필' }));
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/me'))).toHaveLength(1);
  });
});

describe('로그인 화면', () => {
  it('서버의 Google 로그인 주소로 전체 페이지 이동한다', async () => {
    stubMe(401);
    await openLive('/login');
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('로그인');
    expect(screen.getByRole('link', { name: 'Google로 로그인' }).getAttribute('href')).toBe(
      'http://api.test/oauth2/authorization/google',
    );
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('로그인에 실패해 돌아오면 안내한다', async () => {
    stubMe(401);
    await openLive('/login?error');
    expect(screen.getByRole('alert').textContent).toContain('로그인하지 못했습니다');
  });
});
