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

/** `/me`는 status로 답하고, 알림 목록은 주어진 응답을 준다. 그 밖의 경로는 빈 배열이다. */
function stub(meStatus: number, notifications: () => Promise<Response>) {
  const fetchMock = vi.fn((url: string) => {
    const path = url.replace('http://api.test', '');
    if (path === '/me') {
      return Promise.resolve(
        new Response(meStatus === 200 ? JSON.stringify(me) : '', { status: meStatus }),
      );
    }
    if (path === '/me/notifications') return notifications();
    return Promise.resolve(new Response('[]', { status: 200 }));
  });
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
const notificationCalls = (fetchMock: ReturnType<typeof stub>) =>
  fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/me/notifications'));

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe('알림 목록 응답 방어', () => {
  it.each([
    ['배열이 아닌 값', () => Promise.resolve(new Response('{}', { status: 200 }))],
    ['서버 오류', () => Promise.resolve(new Response('', { status: 500 }))],
    ['네트워크 실패', () => Promise.reject(new TypeError('network'))],
  ])('알림 API가 %s이어도 헤더가 그려지고 개수 없이 빈 목록을 보인다', async (_, notifications) => {
    const fetchMock = stub(200, notifications);
    await openLive('/me/responses');

    const bell = await banner().findByRole('button', { name: '마감 알림' });
    await waitFor(() => expect(notificationCalls(fetchMock)).toHaveLength(1));
    fireEvent.click(bell);

    expect(banner().getByText('가상 사용자')).toBeTruthy();
    expect(banner().queryByRole('button', { name: /안 읽은 알림/ })).toBeNull();
  });

  it('비로그인이면 알림 목록을 요청하지 않는다', async () => {
    const fetchMock = stub(401, () => Promise.resolve(new Response('[]', { status: 200 })));
    await openLive('/me/responses');

    expect(await banner().findByRole('link', { name: '로그인' })).toBeTruthy();
    expect(notificationCalls(fetchMock)).toHaveLength(0);
  });
});
