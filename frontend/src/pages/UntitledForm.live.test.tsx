// @vitest-environment jsdom
/**
 * 제목이 빈 폼은 사용자에게 보이는 곳에 "제목 없는 폼"으로 보이고, 제목이 있으면 그 제목 그대로 보인다(사람 결정, #165).
 * 저장값·서버 검증은 바꾸지 않으므로 서버는 fetch 모의로 빈 제목을 그대로 돌려준다.
 */
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  PublicForm,
  ResponseHistoryDetail,
  ResponseHistoryItem,
  SavedFormSummary,
  SubmitResponseInput,
  SubmittedResponse,
} from '../types/api';
import { ResponsePage } from './ResponsePage';

const me = {
  user_id: 'u1',
  name: '가상 사용자',
  email: 'virtual@example.com',
  deadline_reminder: true,
};

function publicForm(title: string, version = 1): PublicForm {
  return {
    form_id: 'f1',
    definition_version: version,
    title,
    note: '',
    note_attachments: [],
    deadline: null,
    display: { default_mode: 'all_questions', allowed_modes: ['one_question', 'all_questions'] },
    payment_link: null,
    consent: {
      items: ['문항별 응답 내용'],
      purpose: '행사 참가 확인',
      retention: '행사 종료 후 1개월',
      refusal_notice: '동의하지 않으면 제출할 수 없습니다.',
      ai_summary: null,
    },
    sections: [
      {
        section_id: 's1',
        title: '기본',
        questions: [
          {
            question_id: 'q1',
            template_id: null,
            question_note: version === 1 ? '하고 싶은 말' : '새 문항',
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
  };
}

const saved = (title: string): SavedFormSummary => ({
  form_id: 'f1',
  title,
  deadline: '2026-11-12T05:00:00Z',
  state: 'saved',
  has_draft: false,
  saved_at: '2026-11-10T00:00:00Z',
  submitted_at: null,
  closed: false,
});

const historyItem = (title: string): ResponseHistoryItem => ({
  response_id: 'r1',
  form_id: 'f1',
  form_title: title,
  form_version: 1,
  submitted_at: '2026-11-04T15:30:00Z',
  updated_at: null,
  editable: false,
  cancellable: false,
});

const historyDetail = (title: string): ResponseHistoryDetail => ({
  form_title: title,
  form_version: 1,
  form_definition: null,
  content: [{ question_id: 'q1', question_note: '하고 싶은 말', value: '잘 부탁해요' }],
  attachments: [],
  submitted_at: '2026-11-04T15:30:00Z',
  updated_at: null,
  editable: false,
  cancellable: false,
});

type Routes = Record<string, { status: number; body?: unknown }>;

/** 주소로 응답을 고른다. 없는 주소는 404다. */
async function openLive(path: string, routes: Routes) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init?: RequestInit) => {
      const key = `${init?.method ?? 'GET'} ${url.replace('http://api.test', '')}`;
      const hit = routes[key] ?? { status: 404, body: { error: 'not_found' } };
      const text = hit.body === undefined ? '' : JSON.stringify(hit.body);
      return Promise.resolve(new Response(text, { status: hit.status }));
    }),
  );
  const { App } = await import('../App');
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  window.scrollTo = vi.fn();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
  vi.restoreAllMocks();
});

describe.each([
  ['빈 제목', '', '제목 없는 폼'],
  ['공백만 있는 제목', '   ', '제목 없는 폼'],
  ['제목이 있는 폼', '행사 참석 신청', '행사 참석 신청'],
])('%s', (_, title, shown) => {
  it('응답 화면 제목과 완료 문장에 보인다', async () => {
    const onSubmit = vi.fn<(body: SubmitResponseInput) => Promise<SubmittedResponse | void>>(() =>
      Promise.resolve({
        response_id: 'r1',
        form_version: 1,
        status: 'submitted',
        content: [{ question_id: 'q1', question_note: '하고 싶은 말', value: '잘 부탁해요' }],
        attachments: [],
        submitted_at: '2026-11-25T00:00:00Z',
        updated_at: null,
        experience_token: null,
      }),
    );
    render(
      <MemoryRouter>
        <ResponsePage form={publicForm(title)} onSubmit={onSubmit} saving={false} />
      </MemoryRouter>,
    );
    expect(screen.getByRole('heading', { level: 2, name: shown })).toBeTruthy();

    fireEvent.change(screen.getByLabelText('하고 싶은 말'), { target: { value: '잘 부탁해요' } });
    fireEvent.click(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
    fireEvent.click(screen.getByRole('button', { name: /^답변 제출/ }));

    expect(await screen.findByText(`「${shown}」에 응답해 주셔서 고마워요.`)).toBeTruthy();
  });

  it('내 폼함 카드에 보인다', async () => {
    await openLive('/me/forms', {
      'GET /me': { status: 200, body: me },
      'GET /me/forms': { status: 200, body: [saved(title)] },
    });
    expect(await screen.findByRole('heading', { level: 3, name: shown })).toBeTruthy();
  });

  it('제출 내역 목록과 상세에 보인다', async () => {
    await openLive('/me/responses', {
      'GET /me': { status: 200, body: me },
      'GET /me/responses': { status: 200, body: [historyItem(title)] },
      'GET /me/responses/r1': { status: 200, body: historyDetail(title) },
    });
    expect(await screen.findByRole('heading', { level: 2, name: shown })).toBeTruthy();
    expect(screen.getAllByText(shown).some((el) => el.tagName === 'STRONG')).toBe(true);
  });

  it('알림 창에 보인다', async () => {
    await openLive('/me/responses', {
      'GET /me': { status: 200, body: me },
      'GET /me/responses': { status: 200, body: [] },
      'GET /me/forms': { status: 200, body: [saved(title)] },
      'GET /me/notifications': {
        status: 200,
        body: [
          {
            notification_id: 'n1',
            form_id: 'f1',
            type: 'deadline',
            created_at: '2026-11-11T05:00:00Z',
            read: false,
          },
        ],
      },
    });
    const banner = within(screen.getByRole('banner'));
    fireEvent.click(await banner.findByRole('button', { name: '마감 알림, 안 읽은 알림 1개' }));
    const item = await banner.findByRole('button', { name: /안 읽음/ });
    expect(item.querySelector('.notification-title')?.textContent).toBe(shown);
  });

  it('이전 버전 초안의 이어 쓰기 안내에 보인다', async () => {
    localStorage.setItem(
      'passform:draft:f1',
      JSON.stringify({
        form_version: 1,
        form_definition: publicForm(title, 1),
        answers: [{ question_id: 'q1', value: '옛 답' }],
        saved_at: '2026-11-07T00:00:00Z',
      }),
    );
    await openLive('/f/f1', {
      'GET /me': { status: 401, body: { error: 'unauthorized' } },
      'GET /f/f1': { status: 200, body: publicForm(title, 2) },
    });
    expect(await screen.findByText('작성하던 답은 이전 버전 기준이에요')).toBeTruthy();
    expect(
      screen.getByRole('heading', { level: 3, name: `${shown} · 폼 버전 1에서 저장한 답` }),
    ).toBeTruthy();
  });
});
