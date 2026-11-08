// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { demoForm } from '../demo/data';
import type { ResponseHistoryDetail, ResponseHistoryItem } from '../types/api';

const me = {
  user_id: 'u1',
  name: '가상 사용자',
  email: 'virtual@example.com',
  deadline_reminder: true,
};
const liveForm = { ...demoForm, form_id: 'live-form', title: '서버에서 받은 폼' };

const historyItem: ResponseHistoryItem = {
  response_id: 'r1',
  form_id: 'f1',
  form_title: '행사 참석 신청',
  form_version: 1,
  submitted_at: '2026-11-04T15:30:00Z',
  updated_at: null,
  editable: false,
  cancellable: false,
};

const historyDetail: ResponseHistoryDetail = {
  form_title: '행사 참석 신청',
  form_version: 1,
  form_definition: null,
  content: [{ question_id: 'q1', question_note: '행사에 참석하나요?', value: '예' }],
  attachments: [],
  submitted_at: '2026-11-04T15:30:00Z',
  updated_at: null,
  editable: false,
  cancellable: false,
};

type Routes = Record<string, { status: number; body?: unknown }>;

/** 주소 끝으로 응답을 고른다. 없는 주소는 404다. */
function stubApi(routes: Routes) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const path = url.replace('http://api.test', '');
    const key = `${init?.method ?? 'GET'} ${path}`;
    const hit = routes[key] ?? { status: 404, body: { error: 'not_found' } };
    const text = hit.body === undefined ? '' : JSON.stringify(hit.body);
    return Promise.resolve(new Response(text, { status: hit.status }));
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

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe('제출 이력 (AC15)', () => {
  it('상세는 서버가 준 제출 버전의 문구와 답, 폼 버전, 서울 시각을 그린다', async () => {
    stubApi({
      'GET /me': { status: 200, body: me },
      'GET /me/responses': { status: 200, body: [historyItem] },
      'GET /me/responses/r1': { status: 200, body: historyDetail },
    });
    await openLive('/me/responses');
    expect(await screen.findByText(/행사에 참석하나요\?/)).toBeTruthy();
    expect(screen.getByText('예')).toBeTruthy();
    expect(screen.getByText(/폼 버전 1/)).toBeTruthy();
    // 2026-11-04T15:30Z는 서울 2026-11-05 00:30이다.
    expect(screen.getAllByText(/2026\. 11\. 5\./).length).toBeGreaterThan(0);
    expect(screen.queryByText(/v1/)).toBeNull();
    expect(screen.queryByText('r1')).toBeNull();
    expect(screen.queryByText(/가상 값이 남아 있습니다/)).toBeNull();
  });
});

describe('응답 화면의 내 폼함에 저장 (AC17)', () => {
  it('비로그인이면 버튼이 보이지 않는다', async () => {
    stubApi({
      'GET /me': { status: 401 },
      'GET /forms/live-form': { status: 200, body: liveForm },
    });
    await openLive('/forms/live-form');
    expect(await screen.findByText('서버에서 받은 폼')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /내 폼함에 저장/ })).toBeNull();
  });

  it('로그인이면 누를 때 저장하고 저장했다고 알린다', async () => {
    const fetchMock = stubApi({
      'GET /me': { status: 200, body: me },
      'GET /forms/live-form': { status: 200, body: liveForm },
      'POST /me/saved-forms': { status: 201 },
    });
    await openLive('/forms/live-form');
    fireEvent.click(await screen.findByRole('button', { name: /내 폼함에 저장/ }));
    expect(await screen.findByText('내 폼함에 저장했어요')).toBeTruthy();
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(post?.[0]).toBe('http://api.test/me/saved-forms');
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({ form_id: 'live-form' });
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /내 폼함에 저장/ })).toBeNull(),
    );
  });
});
