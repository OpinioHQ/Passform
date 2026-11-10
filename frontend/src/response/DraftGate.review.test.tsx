// @vitest-environment jsdom
/**
 * PR #85 리뷰 반영(T5 #53)의 회귀 테스트. `VITE_USE_API=true`에서 공유 링크 라우트로 열고 서버는 fetch 모의로 대신한다.
 * - 리뷰 1번: `/me`가 401이 아닌 이유로 실패하면 로그인 여부를 모르므로 이 브라우저에 답을 남기지 않는다(AGENTS 3절 1항, AC82).
 * - 리뷰 2번: 저장 중에 "응답 전체 지우기"를 누르면 보낸 저장이 끝난 뒤 지운다. 지운 초안이 되살아나지 않는다(SPEC 5절 208행).
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

const form: PublicForm = {
  form_id: 'form-1',
  definition_version: 2,
  title: '동아리 신청',
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
      questions: [question('q1', '하고 싶은 말')],
    },
  ],
};

const ME = {
  user_id: 'u1',
  name: '가상 사용자',
  email: 'virtual@example.com',
  deadline_reminder: true,
};
const draftPath = '/me/forms/form-1/response-draft';

type Reply = { status: number; body?: unknown };
/** `'network'`는 fetch가 reject되는 경우(네트워크 오류)다. Promise를 돌려주면 그 응답이 끝날 때까지 기다린다. */
type Handler = (method: string, path: string) => Reply | 'network' | Promise<Reply> | undefined;
let fetchMock: ReturnType<typeof vi.fn>;

async function openLive(handler: Handler) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    const target = url.replace('http://api.test', '');
    const found = handler(method, target);
    if (found === 'network') throw new TypeError('Failed to fetch');
    const reply = (await found) ?? { status: 404, body: { error: 'not_found' } };
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
      status: reply.status,
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  const { App } = await import('../App');
  return render(
    <MemoryRouter initialEntries={['/f/form-1']}>
      <App />
    </MemoryRouter>,
  );
}

const sent = (method: string, path: string) =>
  fetchMock.mock.calls
    .map((call) => call as [string, RequestInit | undefined])
    .filter(
      ([url, init]) => (init?.method ?? 'GET') === method && url === `http://api.test${path}`,
    );

const field = async (label: string) => (await screen.findByLabelText(label)) as HTMLInputElement;
const type = async (label: string, value: string) =>
  fireEvent.change(await field(label), { target: { value } });
const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

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

describe('리뷰 1번: 로그인 여부를 모르면 브라우저에 저장하지 않는다 (AC82)', () => {
  const cases: [string, Reply | 'network'][] = [
    ['`/me`가 500', { status: 500, body: { error: 'internal_error' } }],
    ['`/me`가 네트워크 오류', 'network'],
  ];

  it.each(cases)(
    '%s이면 자동 저장을 멈추고 답을 이 브라우저와 계정 어디에도 쓰지 않는다',
    async (_, me) => {
      await openLive((method, path) => {
        if (path === '/me') return me;
        if (method === 'GET' && path === '/f/form-1') return { status: 200, body: form };
        return undefined;
      });
      expect(await screen.findByText(/자동 저장을 멈췄어요/)).toBeTruthy();
      await type('하고 싶은 말', '010-0000-0000');
      await wait(1300);
      expect(localStorage.length).toBe(0);
      expect(sent('PUT', draftPath)).toHaveLength(0);
      expect(sent('GET', draftPath)).toHaveLength(0);
    },
  );

  it('`/me`가 401이면 비로그인으로 보고 이 브라우저에 저장한다', async () => {
    await openLive((method, path) => {
      if (path === '/me') return { status: 401, body: { error: 'unauthorized' } };
      if (method === 'GET' && path === '/f/form-1') return { status: 200, body: form };
      return undefined;
    });
    await type('하고 싶은 말', '잘 부탁해요');
    await waitFor(() => expect(localStorage.getItem('passform:draft:form-1')).not.toBeNull());
    expect(sent('GET', draftPath)).toHaveLength(0);
  });
});

describe('리뷰 2번: 저장 중에 지우면 저장이 끝난 뒤 지운다 (SPEC 5절 208행)', () => {
  it('보낸 PUT이 끝나기 전에는 DELETE를 보내지 않고, 끝난 뒤 한 번 보낸다', async () => {
    const order: string[] = [];
    let finishPut: (reply: Reply) => void = () => undefined;
    await openLive((method, path) => {
      if (path === '/me') return { status: 200, body: ME };
      if (method === 'GET' && path === '/f/form-1') return { status: 200, body: form };
      if (method === 'GET' && path === draftPath)
        return { status: 404, body: { error: 'not_found' } };
      if (method === 'PUT' && path === draftPath) {
        order.push('PUT 받음');
        return new Promise<Reply>((resolve) => {
          finishPut = (reply) => {
            order.push('PUT 끝남');
            resolve(reply);
          };
        });
      }
      if (method === 'DELETE' && path === draftPath) {
        order.push('DELETE 받음');
        return { status: 204 };
      }
      return undefined;
    });

    await type('하고 싶은 말', '지울 답');
    await waitFor(() => expect(sent('PUT', draftPath)).toHaveLength(1), { timeout: 3000 });

    fireEvent.click(screen.getByRole('button', { name: '응답 전체 지우기' }));
    await wait(200);
    expect(sent('DELETE', draftPath)).toHaveLength(0);

    finishPut({
      status: 201,
      body: { response_id: 'draft-1', status: 'in_progress', saved_at: '2026-11-08T00:00:00Z' },
    });
    await waitFor(() => expect(sent('DELETE', draftPath)).toHaveLength(1));
    expect(order).toEqual(['PUT 받음', 'PUT 끝남', 'DELETE 받음']);
    await waitFor(() =>
      expect(screen.getByLabelText<HTMLInputElement>('하고 싶은 말').value).toBe(''),
    );
    // 지운 뒤 같은 답을 다시 저장하지 않는다.
    await wait(1300);
    expect(sent('PUT', draftPath)).toHaveLength(1);
  });
});
