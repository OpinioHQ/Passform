// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FormResults, ResponseDetail } from '../types/api';

/** AC19 테스트 줄의 모양: 버전 1 예·아니요, 버전 2 예·아니요·보류. 숫자는 서버가 준 값을 그대로 보이는지 본다. */
const results: FormResults = {
  response_count: 7,
  combined: {
    questions: [
      {
        question_id: 'q1',
        question_note: '참석하나요?',
        question_type: 'multiple_choice',
        form_versions: [1, 2],
        options: [
          { label: '예', count: 3, form_versions: [1, 2] },
          { label: '아니요', count: 3, form_versions: [1, 2] },
          { label: '보류', count: 1, form_versions: [2] },
        ],
      },
    ],
  },
  versions: [
    {
      form_version: 1,
      response_count: 3,
      questions: [
        { question_id: 'q1', question_note: '참석하나요?', counts: { 예: 2, 아니요: 1 } },
      ],
    },
    {
      form_version: 2,
      response_count: 4,
      questions: [
        {
          question_id: 'q1',
          question_note: '참석할 예정인가요?',
          counts: { 예: 1, 아니요: 2, 보류: 1 },
        },
      ],
    },
  ],
};

const detail: ResponseDetail = {
  response_id: '7d7e0a52-0000-4000-8000-000000000001',
  form_version: 1,
  submitted_at: '2026-10-08T02:30:00Z',
  updated_at: null,
  content: [
    { question_id: 'q2', question_note: '학년', value: '3학년' },
    { question_id: 'q3', question_note: '관심 분야', value: ['기획', '디자인'] },
  ],
  attachments: [],
};

function respond(status: number, body: unknown) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }));
}

async function openLiveAt(path: string, routes: Record<string, () => Promise<Response>>) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  const fetchMock = vi.fn((url: string) => {
    const handler = routes[url.replace('http://api.test', '')];
    return handler ? handler() : respond(401, { error: 'unauthorized' });
  });
  vi.stubGlobal('fetch', fetchMock);
  const { App } = await import('../App');
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
  return () => fetchMock.mock.calls.map((call) => String(call[0]));
}

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
});

const card = (note: string) =>
  screen.getByRole('heading', { level: 2, name: note }).closest('section')!;
const optionRow = (section: HTMLElement, label: string) =>
  within(section).getByText(label, { exact: true }).closest('li')!;

describe('결과 화면', () => {
  it('통합 보기가 기본이고 합친 수와 출처 버전을 보인다', async () => {
    const urls = await openLiveAt('/creator/forms/abc/results', {
      '/forms/abc/results': () => respond(200, results),
    });
    expect(await screen.findByRole('heading', { level: 2, name: '참석하나요?' })).toBeTruthy();
    expect(urls()).toContain('http://api.test/forms/abc/results');
    expect(screen.getByRole('tab', { name: '통합 보기' }).getAttribute('aria-selected')).toBe(
      'true',
    );
    expect(screen.getByText('전체 응답 7건')).toBeTruthy();
    const combined = card('참석하나요?');
    expect(within(combined).getByText('폼 버전 1·2')).toBeTruthy();
    expect(within(optionRow(combined, '예')).getByText('3')).toBeTruthy();
    expect(within(optionRow(combined, '보류')).getByText('1')).toBeTruthy();
    expect(within(optionRow(combined, '보류')).getByText('폼 버전 2에만 있음')).toBeTruthy();
    expect(within(optionRow(combined, '예')).queryByText(/에만 있음/)).toBeNull();
  });

  it('버전 탭을 누르면 그 버전의 문구와 수만 보이고 전체 응답 수는 그대로다', async () => {
    await openLiveAt('/creator/forms/abc/results', {
      '/forms/abc/results': () => respond(200, results),
    });
    fireEvent.click(await screen.findByRole('tab', { name: '폼 버전 2' }));
    expect(screen.getByRole('tab', { name: '폼 버전 2' }).getAttribute('aria-selected')).toBe(
      'true',
    );
    expect(screen.queryByRole('heading', { level: 2, name: '참석하나요?' })).toBeNull();
    const version2 = card('참석할 예정인가요?');
    expect(within(optionRow(version2, '아니요')).getByText('2')).toBeTruthy();
    expect(screen.getByText('전체 응답 7건')).toBeTruthy();
    expect(screen.getByText('폼 버전 2 응답 4건')).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: '폼 버전 1' }));
    expect(within(optionRow(card('참석하나요?'), '예')).getByText('2')).toBeTruthy();
    expect(screen.queryByText('보류')).toBeNull();

    fireEvent.click(screen.getByRole('tab', { name: '통합 보기' }));
    expect(within(optionRow(card('참석하나요?'), '예')).getByText('3')).toBeTruthy();
    // 버전을 봐도 전체 응답 수는 그대로다.
    expect(screen.getByText('전체 응답 7건')).toBeTruthy();
    expect(screen.queryByText(/v\d|q\d/)).toBeNull();
  });

  it('남의 폼이면 제작자만 볼 수 있다고 알린다', async () => {
    await openLiveAt('/creator/forms/abc/results', {
      '/forms/abc/results': () => respond(403, { error: 'forbidden' }),
    });
    expect(await screen.findByText('이 폼을 만든 사람만 볼 수 있어요.')).toBeTruthy();
  });

  it('응답이 없으면 안내한다', async () => {
    await openLiveAt('/creator/forms/abc/results', {
      '/forms/abc/results': () =>
        respond(200, { response_count: 0, combined: { questions: [] }, versions: [] }),
    });
    expect(await screen.findByText('아직 제출된 응답이 없어요.')).toBeTruthy();
  });
});

describe('응답 상세 화면', () => {
  it('제출 버전의 문구와 답을 보이고 응답 ID와 v1을 쓰지 않는다', async () => {
    const urls = await openLiveAt(`/creator/forms/abc/responses/${detail.response_id}`, {
      [`/forms/abc/responses/${detail.response_id}`]: () => respond(200, detail),
    });
    expect(await screen.findByText('학년')).toBeTruthy();
    expect(urls()).toContain(`http://api.test/forms/abc/responses/${detail.response_id}`);
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('응답 상세');
    expect(screen.getByText('3학년')).toBeTruthy();
    expect(screen.getByText('기획, 디자인')).toBeTruthy();
    expect(screen.getByText('폼 버전 1의 문항으로 제출된 응답이에요.')).toBeTruthy();
    expect(screen.getByText(/제출 2026\. 10\. 8\. 오전 11:30:00/)).toBeTruthy();
    expect(screen.queryByText(new RegExp(detail.response_id))).toBeNull();
    expect(screen.queryByText(/v1|q2/)).toBeNull();
  });

  it('없는 응답이면 404 안내를 보인다', async () => {
    await openLiveAt('/creator/forms/abc/responses/nope', {
      '/forms/abc/responses/nope': () => respond(404, { error: 'not_found' }),
    });
    expect(await screen.findByText('응답을 찾을 수 없어요.')).toBeTruthy();
  });
});

describe('가짜 데이터 모드', () => {
  it('결과와 상세는 API를 부르지 않고 가상 값도 보이지 않는다', async () => {
    const fetchMock = vi.fn(() => Promise.reject(new Error('부르지 않는다')));
    vi.stubGlobal('fetch', fetchMock);
    const { App } = await import('../App');
    for (const path of ['/creator/forms/abc/results', '/creator/forms/abc/responses/r1']) {
      render(
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>,
      );
      expect(screen.getByText('이 화면은 서버에 연결했을 때 실제 응답으로만 보여요.')).toBeTruthy();
      expect(screen.queryByRole('tab')).toBeNull();
      cleanup();
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
