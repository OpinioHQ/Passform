// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FormResults, ResponseDetail, ResponseList } from '../types/api';
import { paths } from '../routes/paths';
import { ResultsDashboard } from './ResultsPage';

// 결과 대시보드의 응답별 보기(AC19, AC62). 목록 순서는 서버가 준 response_ids 그대로다.
const results: FormResults = {
  response_count: 3,
  combined: {
    questions: [
      {
        question_id: 'q1',
        question_note: '참석하나요?',
        question_type: 'multiple_choice',
        form_versions: [1, 2],
        options: [
          { label: '예', count: 2, form_versions: [1, 2] },
          { label: '아니요', count: 1, form_versions: [1, 2] },
        ],
      },
    ],
  },
  versions: [
    {
      form_version: 1,
      response_count: 1,
      questions: [
        { question_id: 'q1', question_note: '참석하나요?', counts: { 예: 1, 아니요: 0 } },
      ],
    },
    {
      form_version: 2,
      response_count: 2,
      questions: [
        { question_id: 'q1', question_note: '참석하나요?', counts: { 예: 1, 아니요: 1 } },
      ],
    },
  ],
};

const empty: FormResults = { response_count: 0, combined: { questions: [] }, versions: [] };

const idOf = (n: number) => `7d7e0a52-0000-4000-8000-${String(n).padStart(12, '0')}`;

/** 응답 n은 답이 `답 n`이고, 첫 응답만 폼 버전 2다(버전이 섞인 목록). */
const detailOf = (id: string): ResponseDetail => {
  const n = Number(id.slice(-12));
  return {
    response_id: id,
    form_version: n === 1 ? 2 : 1,
    submitted_at: '2026-11-01T00:00:00Z',
    updated_at: null,
    content: [{ question_id: 'q1', question_note: '참석하나요?', value: `답 ${n}` }],
    attachments: [],
  };
};

const listOf = (count: number): ResponseList => ({
  response_ids: Array.from({ length: count }, (_, i) => idOf(i + 1)),
});

function renderDashboard({
  data = results,
  loadResponseList = vi.fn(() => Promise.resolve(listOf(3))),
  aiResult,
  aside,
}: {
  data?: FormResults;
  loadResponseList?: () => Promise<ResponseList>;
  aiResult?: React.ReactNode;
  aside?: React.ReactNode;
} = {}) {
  const loadResponse = vi.fn((id: string) => Promise.resolve(detailOf(id)));
  render(
    <ResultsDashboard
      results={data}
      aiResult={aiResult}
      aside={aside}
      loadResponseList={loadResponseList}
      loadResponse={loadResponse}
      formId="abc"
    />,
  );
  return { loadResponseList, loadResponse };
}

const tab = (name: string) => screen.getByRole('tab', { name });
const responseItems = () =>
  screen.getAllByRole('listitem').filter((li) => li.classList.contains('explore-response'));

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
});

describe('응답별 보기 탭', () => {
  it('탭을 누르면 선택되고 목록이 응답 1부터 서버 순서로 나온다', async () => {
    const { loadResponseList } = renderDashboard();
    expect(tab('응답별 보기').getAttribute('aria-selected')).toBe('false');

    fireEvent.click(tab('응답별 보기'));
    expect(tab('응답별 보기').getAttribute('aria-selected')).toBe('true');
    expect(tab('통합 보기').getAttribute('aria-selected')).toBe('false');
    expect(await screen.findByText('답 1')).toBeTruthy();
    const items = responseItems();
    expect(items).toHaveLength(3);
    items.forEach((item, index) => {
      expect(within(item).getByText(`응답 ${index + 1}`)).toBeTruthy();
      expect(within(item).getByText(`답 ${index + 1}`)).toBeTruthy();
    });
    expect(within(items[0]).getByText('폼 버전 2')).toBeTruthy();
    expect(within(items[1]).getByText('폼 버전 1')).toBeTruthy();
    // 문항 카드는 이 탭에서 보이지 않는다.
    expect(screen.queryByRole('heading', { level: 2, name: '참석하나요?' })).toBeNull();
    expect(screen.getByText('전체 응답 3건')).toBeTruthy();
    expect(loadResponseList).toHaveBeenCalledTimes(1);
  });

  it('응답 ID와 v1·q1을 화면에 쓰지 않는다', async () => {
    renderDashboard();
    fireEvent.click(tab('응답별 보기'));
    await screen.findByText('답 3');
    const text = document.body.textContent ?? '';
    for (let n = 1; n <= 3; n++) expect(text).not.toContain(idOf(n));
    expect(text).not.toMatch(/v\d|q\d/);
  });

  it('통합 보기로 돌아오면 문항 카드가 다시 보이고, 다시 열어도 목록을 또 부르지 않는다', async () => {
    const { loadResponseList } = renderDashboard();
    fireEvent.click(tab('응답별 보기'));
    await screen.findByText('답 1');

    fireEvent.click(tab('통합 보기'));
    expect(tab('통합 보기').getAttribute('aria-selected')).toBe('true');
    expect(screen.getByRole('heading', { level: 2, name: '참석하나요?' })).toBeTruthy();
    expect(screen.queryByText('답 1')).toBeNull();

    fireEvent.click(tab('폼 버전 2'));
    fireEvent.click(tab('응답별 보기'));
    expect(await screen.findByText('답 1')).toBeTruthy();
    expect(loadResponseList).toHaveBeenCalledTimes(1);
  });

  it('21건 이상이면 20건 뒤에 더 보기가 나오고, 상세 링크는 새 탭으로 열린다', async () => {
    renderDashboard({ loadResponseList: () => Promise.resolve(listOf(21)) });
    fireEvent.click(tab('응답별 보기'));
    await screen.findByText('답 20');
    expect(responseItems()).toHaveLength(20);
    const link = screen.getByRole('link', { name: '응답 1 상세 보기' });
    expect(link.getAttribute('href')).toBe(paths.formResponse('abc', idOf(1)));
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');

    fireEvent.click(screen.getByRole('button', { name: '더 보기' }));
    expect(await screen.findByText('답 21')).toBeTruthy();
    expect(responseItems()).toHaveLength(21);
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull();
  });

  it('이 탭에서는 AI 탐색 결과를 숨기고 탐색 도우미는 그대로 둔다', async () => {
    renderDashboard({ aiResult: <p>AI 탐색 결과 자리</p>, aside: <p>탐색 도우미 자리</p> });
    expect(screen.getByText('AI 탐색 결과 자리')).toBeTruthy();

    fireEvent.click(tab('응답별 보기'));
    await screen.findByText('답 1');
    expect(screen.queryByText('AI 탐색 결과 자리')).toBeNull();
    expect(screen.getByText('탐색 도우미 자리')).toBeTruthy();

    fireEvent.click(tab('폼 버전 1'));
    expect(screen.getByText('AI 탐색 결과 자리')).toBeTruthy();
  });

  it('목록을 불러오지 못하면 탭 안에 알린다', async () => {
    renderDashboard({ loadResponseList: () => Promise.reject(new Error('실패')) });
    fireEvent.click(tab('응답별 보기'));
    expect((await screen.findByRole('alert')).textContent).toBe(
      '응답 목록을 불러오지 못했어요. 잠시 뒤 새로고침해 주세요.',
    );
    expect(screen.queryAllByRole('listitem')).toHaveLength(0);
  });

  it('응답이 0건이면 기존 안내 하나만 보인다', async () => {
    const { loadResponseList } = renderDashboard({
      data: empty,
      loadResponseList: vi.fn(() => Promise.resolve(listOf(0))),
    });
    fireEvent.click(tab('응답별 보기'));
    await waitFor(() => expect(loadResponseList).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByText('응답 목록을 불러오는 중이에요.')).toBeNull());
    expect(screen.getAllByText('아직 제출된 응답이 없어요.')).toHaveLength(1);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('목록 함수를 넘기지 않으면 탭이 없다', () => {
    render(<ResultsDashboard results={results} />);
    expect(screen.queryByRole('tab', { name: '응답별 보기' })).toBeNull();
    expect(screen.getAllByRole('tab')).toHaveLength(3);
  });
});

describe('결과 화면의 응답 목록 API', () => {
  beforeEach(() => {
    window.scrollTo = vi.fn();
  });

  it('결과 화면을 열 때는 부르지 않고 탭을 처음 누를 때 한 번만 부른다', async () => {
    vi.resetModules();
    vi.stubEnv('VITE_USE_API', 'true');
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
    const respond = (body: unknown) => Promise.resolve(new Response(JSON.stringify(body)));
    const fetchMock = vi.fn((url: string) => {
      const path = url.replace('http://api.test', '');
      if (path === '/forms/abc/results') return respond(results);
      if (path === '/forms/abc/responses') return respond(listOf(3));
      if (path.startsWith('/forms/abc/responses/'))
        return respond(detailOf(path.split('/').pop()!));
      return Promise.resolve(
        new Response(JSON.stringify({ error: 'unauthorized' }), { status: 401 }),
      );
    });
    vi.stubGlobal('fetch', fetchMock);
    const listCalls = () =>
      fetchMock.mock.calls.filter(([url]) => String(url) === 'http://api.test/forms/abc/responses');
    const { App } = await import('../App');
    render(
      <MemoryRouter initialEntries={['/creator/forms/abc/results']}>
        <App />
      </MemoryRouter>,
    );
    const responsesTab = await screen.findByRole('tab', { name: '응답별 보기' });
    expect(screen.getByRole('heading', { level: 2, name: '참석하나요?' })).toBeTruthy();
    expect(listCalls()).toHaveLength(0);

    fireEvent.click(responsesTab);
    expect(listCalls()).toHaveLength(1);
    expect(await screen.findByText('답 1')).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: '통합 보기' }));
    fireEvent.click(screen.getByRole('tab', { name: '응답별 보기' }));
    expect(listCalls()).toHaveLength(1);
  });
});
