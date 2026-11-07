// @vitest-environment jsdom
/**
 * `VITE_USE_API=true`의 응답 라우트(T4 #52). 서버는 fetch 모의로 대신한다.
 * 비로그인 제출도 `client.ts`의 CSRF 헤더를 쓰는지, 공개 조회의 409·404를 어떻게 안내하는지 본다.
 */
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PublicForm } from '../types/api';

const liveForm: PublicForm = {
  form_id: 'form-1',
  definition_version: 1,
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
      questions: [
        {
          question_id: 'q1',
          template_id: null,
          question_note: '하고 싶은 말',
          question_type: 'short_answer',
          options: [],
          required: true,
          profile_key: null,
          attachments: [],
          branch_rules: [],
        },
      ],
    },
  ],
};

type Reply = { status: number; body: unknown };
let replies: Record<string, Reply[]>;
let fetchMock: ReturnType<typeof vi.fn>;

/** 경로마다 차례로 돌려줄 응답. 마지막 응답은 계속 쓴다. `/me`는 비로그인(401)이다. */
function serve(path: string, ...list: Reply[]) {
  replies[path] = list;
}

async function openLive(path: string) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  fetchMock = vi.fn((url: string) => {
    const key = url.replace('http://api.test', '');
    const list = replies[key] ?? [{ status: 401, body: { error: 'unauthorized' } }];
    const reply = list.length > 1 ? (list.shift() as Reply) : list[0];
    return Promise.resolve(new Response(JSON.stringify(reply.body), { status: reply.status }));
  });
  vi.stubGlobal('fetch', fetchMock);
  const { App } = await import('../App');
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

const calls = () =>
  fetchMock.mock.calls.map((call) => {
    const [url, init] = call as [string, RequestInit | undefined];
    return { url, init };
  });

beforeEach(() => {
  window.scrollTo = vi.fn();
  replies = {};
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});

async function answerAndSubmit() {
  fireEvent.change(await screen.findByLabelText('하고 싶은 말'), {
    target: { value: '잘 부탁해요' },
  });
  fireEvent.click(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
  fireEvent.click(screen.getByRole('button', { name: /^제출/ }));
}

describe('공유 링크 응답 (VITE_USE_API=true)', () => {
  it('share_url_open_form의 화면: 로그인 없이 공개 조회한 폼을 보이고 가짜 값 띠가 없다', async () => {
    serve('/f/abc', { status: 200, body: liveForm });
    await openLive('/f/abc');
    expect(await screen.findByRole('heading', { level: 2, name: '동아리 신청' })).toBeTruthy();
    expect(screen.queryByText(/가상 값이 남아 있습니다/)).toBeNull();
  });

  it('share_url_closed_form의 화면: 공개 조회가 409 form_closed면 마감 안내를 보인다', async () => {
    serve('/f/abc', { status: 409, body: { error: 'form_closed' } });
    await openLive('/f/abc');
    expect(await screen.findByText('마감된 폼이에요')).toBeTruthy();
    expect(screen.queryByLabelText('하고 싶은 말')).toBeNull();
  });

  it('초안·삭제된 폼(404)은 찾을 수 없다고 안내한다', async () => {
    serve('/f/abc', { status: 404, body: { error: 'not_found' } });
    await openLive('/f/abc');
    expect(await screen.findByText(/폼을 찾을 수 없어요/)).toBeTruthy();
  });

  it('비로그인 제출도 XSRF-TOKEN 쿠키를 X-XSRF-TOKEN 헤더로 보내고 완료 화면을 보인다', async () => {
    document.cookie = 'XSRF-TOKEN=token-123';
    serve('/f/abc', { status: 200, body: liveForm });
    serve('/forms/form-1/responses', {
      status: 201,
      body: {
        response_id: 'r1',
        form_version: 1,
        status: 'submitted',
        content: [],
        attachments: [],
        submitted_at: '2026-11-07T00:00:00Z',
        updated_at: null,
      },
    });
    await openLive('/f/abc');
    await answerAndSubmit();
    expect(await screen.findByText('응답을 제출했어요')).toBeTruthy();
    const post = calls().find((c) => c.url === 'http://api.test/forms/form-1/responses');
    expect(post?.init?.method).toBe('POST');
    expect((post?.init?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('token-123');
    expect(JSON.parse(String(post?.init?.body))).toEqual({
      form_version: 1,
      answers: [{ question_id: 'q1', value: '잘 부탁해요' }],
      consent_agreed: true,
    });
  });

  it('latest_form_version_submits의 화면: 409 form_version_changed 뒤 다시 열면 최신 버전을 받아 빈 답으로 시작하고 그 버전으로 제출한다', async () => {
    const latest = { ...liveForm, definition_version: 2, title: '동아리 신청 (수정)' };
    serve('/f/abc', { status: 200, body: liveForm }, { status: 200, body: latest });
    serve(
      '/forms/form-1/responses',
      { status: 409, body: { error: 'form_version_changed' } },
      {
        status: 201,
        body: {
          response_id: 'r2',
          form_version: 2,
          status: 'submitted',
          content: [],
          attachments: [],
          submitted_at: '2026-11-07T00:00:00Z',
          updated_at: null,
        },
      },
    );
    await openLive('/f/abc');
    await answerAndSubmit();
    fireEvent.click(await screen.findByRole('button', { name: '최신 폼 다시 열기' }));
    expect(
      await screen.findByRole('heading', { level: 2, name: '동아리 신청 (수정)' }),
    ).toBeTruthy();
    expect((screen.getByLabelText('하고 싶은 말') as HTMLInputElement).value).toBe('');
    await answerAndSubmit();
    expect(await screen.findByText('응답을 제출했어요')).toBeTruthy();
    const posts = calls().filter((c) => c.url === 'http://api.test/forms/form-1/responses');
    expect(posts.map((c) => JSON.parse(String(c.init?.body)).form_version)).toEqual([1, 2]);
  });
});
