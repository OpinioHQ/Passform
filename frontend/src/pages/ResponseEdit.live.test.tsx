// @vitest-environment jsdom
/**
 * 제출 후 수정·취소 화면(T10 #61, AC16, AC17, AC74 화면 부분). `VITE_USE_API=true`로 앱을 열고 서버는 fetch 모의로 대신한다.
 * 테스트 이름 앞의 이름은 SPEC 추적표의 케이스 이름이다.
 */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTOSAVE_DELAY_MS } from '../response/draftSync';
import type {
  PublicForm,
  Question,
  ResponseHistoryDetail,
  ResponseHistoryItem,
} from '../types/api';

const me = { user_id: 'u1', name: '가상 사용자', email: 'v@example.com', deadline_reminder: true };

const question = (id: string, note: string, extra: Partial<Question> = {}): Question => ({
  question_id: id,
  template_id: null,
  question_note: note,
  question_type: 'short_answer',
  options: [],
  required: false,
  profile_key: null,
  attachments: [],
  branch_rules: [],
  ...extra,
});

function publicForm(
  sections: PublicForm['sections'],
  mode: PublicForm['display']['default_mode'] = 'all_questions',
): PublicForm {
  return {
    form_id: 'f1',
    definition_version: 1,
    title: '행사 참석 신청',
    note: '',
    note_attachments: [],
    deadline: null,
    display: {
      default_mode: mode,
      allowed_modes: mode === 'section_page' ? [mode] : ['one_question', 'all_questions'],
    },
    payment_link: null,
    consent: {
      items: ['문항별 응답 내용'],
      purpose: '신청 확인',
      retention: '학기 종료 후 삭제',
      refusal_notice: '동의하지 않으면 제출할 수 없습니다.',
      ai_summary: null,
    },
    sections,
  };
}

const oldForm = publicForm([
  {
    section_id: 's1',
    title: '신청',
    questions: [question('q1', '옛 문항 문구'), question('q2', '하고 싶은 말')],
  },
]);

const item = (id: string, title = '행사 참석 신청'): ResponseHistoryItem => ({
  response_id: id,
  form_id: 'f1',
  form_title: title,
  form_version: 1,
  submitted_at: '2026-11-04T15:30:00Z',
  updated_at: null,
  editable: true,
  cancellable: true,
});

const detail = (extra: Partial<ResponseHistoryDetail> = {}): ResponseHistoryDetail => ({
  form_title: '행사 참석 신청',
  form_version: 1,
  form_definition: oldForm,
  content: [{ question_id: 'q1', question_note: '옛 문항 문구', value: '처음 답' }],
  attachments: [],
  submitted_at: '2026-11-04T15:30:00Z',
  updated_at: null,
  editable: true,
  cancellable: true,
  ...extra,
});

type Reply = { status: number; body?: unknown };
type Routes = Record<string, Reply | (() => Reply)>;
let fetchMock: ReturnType<typeof vi.fn>;

async function openLive(path: string, routes: Routes) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  const all: Routes = { 'GET /me': { status: 200, body: me }, ...routes };
  fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url.replace('http://api.test', '')}`;
    const found = all[key];
    const hit = typeof found === 'function' ? found() : found;
    const reply = hit ?? { status: 404, body: { error: 'not_found' } };
    return Promise.resolve(
      new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
        status: reply.status,
      }),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  const { App } = await import('../App');
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

const calls = (method: string, path: string) =>
  fetchMock.mock.calls
    .map((call) => call as [string, RequestInit | undefined])
    .filter(([url, init]) => (init?.method ?? 'GET') === method && url === `http://api.test${path}`)
    .map(([, init]) => (init?.body ? JSON.parse(String(init.body)) : undefined));

const historyRoutes = (d: ResponseHistoryDetail, list = [item('r1')]): Routes => ({
  'GET /me/responses': { status: 200, body: list },
  'GET /me/responses/r1': { status: 200, body: d },
});
const button = (name: string | RegExp) => screen.queryByRole('button', { name });
const field = async (label: string) => (await screen.findByLabelText(label)) as HTMLInputElement;

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

describe('이력의 수정·취소 버튼 (AC16)', () => {
  it.each([
    [true, true],
    [true, false],
    [false, true],
    [false, false],
  ])('editable %s, cancellable %s 일 때 그 버튼만 보인다', async (editable, cancellable) => {
    await openLive('/me/responses/r1', historyRoutes(detail({ editable, cancellable })));
    await screen.findByText('처음 답');
    expect(Boolean(button('답변 수정'))).toBe(editable);
    expect(Boolean(button('응답 취소'))).toBe(cancellable);
  });

  it('history_only_not_editable 조회 전용 이력에는 버튼이 없다', async () => {
    await openLive(
      '/me/responses/r1',
      historyRoutes(detail({ form_definition: null, editable: false, cancellable: false })),
    );
    await screen.findByText('처음 답');
    expect(button('답변 수정')).toBeNull();
    expect(button('응답 취소')).toBeNull();
  });

  it('response_edit_overwrites_same_id 수정 시각을 함께 보인다', async () => {
    await openLive(
      '/me/responses/r1',
      historyRoutes(detail({ updated_at: '2026-11-05T15:30:00Z' })),
    );
    expect(await screen.findByText(/2026\. 11\. 6\..*수정/)).toBeTruthy();
  });
});

describe('수정 화면 (AC74)', () => {
  it('response_edit_uses_original_version 제출 버전 문항과 기존 답을 보이고 동의를 다시 묻지 않는다', async () => {
    const latest = publicForm([
      { section_id: 's1', title: '새', questions: [question('q9', '새 버전 문항')] },
    ]);
    await openLive('/me/responses/r1/edit', {
      ...historyRoutes(detail()),
      'GET /forms/f1': { status: 200, body: latest },
    });

    expect((await field('옛 문항 문구')).value).toBe('처음 답');
    expect(screen.queryByLabelText('새 버전 문항')).toBeNull();
    expect(screen.queryByText(/개인정보 수집·이용에 동의합니다/)).toBeNull();
    expect((button(/수정 저장/) as HTMLButtonElement).disabled).toBe(false);
  });

  it('답을 바꿔도 초안을 저장하지 않고 PATCH로만 저장한다', async () => {
    await openLive('/me/responses/r1/edit', {
      ...historyRoutes(detail()),
      'PATCH /me/responses/r1': { status: 200, body: {} },
    });
    fireEvent.change(await field('옛 문항 문구'), { target: { value: '고친 답' } });
    await new Promise((resolve) => setTimeout(resolve, AUTOSAVE_DELAY_MS + 200));

    expect(calls('PUT', '/me/forms/f1/response-draft')).toEqual([]);
    expect(localStorage.length).toBe(0);
    fireEvent.click(button(/수정 저장/) as HTMLElement);

    await waitFor(() =>
      expect(calls('PATCH', '/me/responses/r1')).toEqual([
        { answers: [{ question_id: 'q1', value: '고친 답' }] },
      ]),
    );
    expect(calls('POST', '/forms/f1/responses')).toEqual([]);
  });

  it('수정을 마치면 그 응답의 상세로 돌아가 안내한다', async () => {
    let saved = false;
    await openLive('/me/responses/r1/edit', {
      'GET /me/responses': { status: 200, body: [item('r1')] },
      'GET /me/responses/r1': () => ({
        status: 200,
        body: saved
          ? detail({
              content: [{ question_id: 'q1', question_note: '옛 문항 문구', value: '고친 답' }],
            })
          : detail(),
      }),
      'PATCH /me/responses/r1': () => {
        saved = true;
        return { status: 200, body: {} };
      },
    });
    fireEvent.change(await field('옛 문항 문구'), { target: { value: '고친 답' } });
    fireEvent.click(button(/수정 저장/) as HTMLElement);

    expect(await screen.findByText('답변을 수정했어요.')).toBeTruthy();
    expect(await screen.findByText('고친 답')).toBeTruthy();
  });

  it('분기 문항을 고치면 경로를 다시 계산한다', async () => {
    const branched = publicForm(
      [
        {
          section_id: 's1',
          title: '첫째',
          questions: [
            question('q1', '참석하나요', {
              question_type: 'multiple_choice',
              options: ['예', '아니요'],
              required: true,
              branch_rules: [
                {
                  when: { match: 'all', conditions: [{ question_id: 'q1', option: '아니요' }] },
                  go_to: { section_id: 's3' },
                },
              ],
            }),
          ],
        },
        { section_id: 's2', title: '둘째', questions: [question('q2', '건너뛸 문항')] },
        { section_id: 's3', title: '셋째', questions: [question('q3', '마지막 문항')] },
      ],
      'section_page',
    );
    await openLive(
      '/me/responses/r1/edit',
      historyRoutes(
        detail({
          form_definition: branched,
          content: [{ question_id: 'q1', question_note: '참석하나요', value: '예' }],
        }),
      ),
    );

    fireEvent.click(await screen.findByRole('radio', { name: '아니요' }));
    fireEvent.click(button(/다음/) as HTMLElement);

    expect(await screen.findByLabelText('마지막 문항')).toBeTruthy();
    expect(screen.queryByLabelText('건너뛸 문항')).toBeNull();
  });

  it.each([
    ['form_closed', /폼이 마감되어/],
    ['not_editable', /제작자가 응답 수정을 허용하지 않아요/],
    ['history_only', /조회만 할 수 있어요/],
  ])('409 %s 를 안내하고 쓴 답을 유지한다', async (code, message) => {
    await openLive('/me/responses/r1/edit', {
      ...historyRoutes(detail()),
      'PATCH /me/responses/r1': { status: 409, body: { error: code } },
    });
    fireEvent.change(await field('옛 문항 문구'), { target: { value: '고친 답' } });
    fireEvent.click(button(/수정 저장/) as HTMLElement);

    expect(await screen.findByText(message)).toBeTruthy();
    expect((await field('옛 문항 문구')).value).toBe('고친 답');
  });

  it('서버의 답 검증 오류는 그 문항 아래에 보인다', async () => {
    await openLive('/me/responses/r1/edit', {
      ...historyRoutes(detail()),
      'PATCH /me/responses/r1': {
        status: 400,
        body: { error: 'required_missing', question_ids: ['q2'] },
      },
    });
    await field('옛 문항 문구');
    fireEvent.click(button(/수정 저장/) as HTMLElement);

    expect(await screen.findByText(/답해 주세요|필수/)).toBeTruthy();
  });

  it('수정할 수 없는 응답은 입력 화면을 보이지 않는다', async () => {
    await openLive('/me/responses/r1/edit', historyRoutes(detail({ editable: false })));

    expect(await screen.findByText('이 응답은 지금 수정할 수 없어요.')).toBeTruthy();
    expect(screen.queryByLabelText('옛 문항 문구')).toBeNull();
  });

  it('프로필 문항이 있어도 수정 화면에는 자동채우기를 보이지 않는다', async () => {
    const profileForm = publicForm([
      {
        section_id: 's1',
        title: '신청',
        questions: [question('q1', '옛 문항 문구', { profile_key: 'name' })],
      },
    ]);
    await openLive(
      '/me/responses/r1/edit',
      historyRoutes(detail({ form_definition: profileForm })),
    );

    await field('옛 문항 문구');
    expect(button('내 정보로 채우기')).toBeNull();
    expect(screen.queryByText(/로그인하면 내 프로필로/)).toBeNull();
    expect(calls('GET', '/me/profiles')).toEqual([]);
  });

  it('없는 응답은 안내한다', async () => {
    await openLive('/me/responses/none/edit', {});

    expect(await screen.findByText('이 제출 내역을 찾을 수 없어요.')).toBeTruthy();
  });
});

describe('취소 (AC17, AC74)', () => {
  async function openCancel() {
    fireEvent.click((await screen.findByRole('button', { name: '응답 취소' })) as HTMLElement);
    return (await screen.findByRole('button', { name: '취소 확인' })) as HTMLElement;
  }

  it('확인을 눌러야 지운다', async () => {
    await openLive('/me/responses/r1', {
      ...historyRoutes(detail()),
      'DELETE /me/responses/r1': { status: 204 },
    });
    await openCancel();

    expect(calls('DELETE', '/me/responses/r1')).toEqual([]);
    fireEvent.click(button('돌아가기') as HTMLElement);
    expect(button('응답 취소')).toBeTruthy();
    expect(calls('DELETE', '/me/responses/r1')).toEqual([]);
  });

  it('response_cancel_removes_one 취소하면 목록에서 사라지고 남은 응답을 보여 준다', async () => {
    await openLive('/me/responses/r1', {
      'GET /me/responses': { status: 200, body: [item('r1', '지울 폼'), item('r2', '남을 폼')] },
      'GET /me/responses/r1': { status: 200, body: detail() },
      'GET /me/responses/r2': {
        status: 200,
        body: detail({
          content: [{ question_id: 'q1', question_note: '옛 문항 문구', value: '남은 답' }],
        }),
      },
      'DELETE /me/responses/r1': { status: 204 },
    });
    fireEvent.click(await openCancel());

    expect(await screen.findByText('응답을 취소했어요.')).toBeTruthy();
    expect(await screen.findByText('남은 답')).toBeTruthy();
    expect(screen.queryByText('지울 폼')).toBeNull();
    expect(calls('DELETE', '/me/responses/r1')).toHaveLength(1);
  });

  it('마지막 응답을 취소하면 빈 목록이 되고 지운 내용이 남지 않는다', async () => {
    await openLive('/me/responses/r1', {
      ...historyRoutes(detail()),
      'DELETE /me/responses/r1': { status: 204 },
    });
    fireEvent.click(await openCancel());

    expect(await screen.findByText('아직 제출한 응답이 없어요.')).toBeTruthy();
    expect(screen.queryByText('처음 답')).toBeNull();
    expect(button('답변 수정')).toBeNull();
  });

  it.each([
    ['form_closed', /폼이 마감되어/],
    ['not_cancellable', /제작자가 응답 취소를 허용하지 않아요/],
    ['history_only', /조회만 할 수 있어요/],
  ])('409 %s 를 안내하고 상세를 다시 읽어 버튼을 숨긴다', async (code, message) => {
    let reads = 0;
    await openLive('/me/responses/r1', {
      'GET /me/responses': { status: 200, body: [item('r1')] },
      'GET /me/responses/r1': () => {
        reads += 1;
        return {
          status: 200,
          body: detail(reads === 1 ? {} : { editable: false, cancellable: false }),
        };
      },
      'DELETE /me/responses/r1': { status: 409, body: { error: code } },
    });
    fireEvent.click(await openCancel());

    expect(await screen.findByText(message)).toBeTruthy();
    await waitFor(() => expect(button('응답 취소')).toBeNull());
    expect(screen.getByText('처음 답')).toBeTruthy();
  });
});
