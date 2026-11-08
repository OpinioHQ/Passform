// @vitest-environment jsdom
/**
 * 프로필 화면과 응답 화면의 자동채우기(T9 #60, AC3~AC6 화면 부분). `VITE_USE_API=true`로 앱을 열고 서버는 fetch 모의로 대신한다.
 * 테스트 이름 앞의 이름은 SPEC 추적표의 케이스 이름이다.
 */
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PublicForm, Question } from '../types/api';
import { AUTOSAVE_DELAY_MS } from './draftSync';

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

const nameQuestion = question('q1', '본인 이름', { profile_key: 'name' });
const emailQuestion = question('q2', '이메일', { profile_key: 'email' });
const motiveQuestion = question('q3', '지원 동기');

function publicForm(
  sections: PublicForm['sections'],
  mode: 'all_questions' | 'one_question',
): PublicForm {
  return {
    form_id: 'f1',
    definition_version: 1,
    title: '동아리 신청',
    note: '',
    note_attachments: [],
    deadline: null,
    display: { default_mode: mode, allowed_modes: ['one_question', 'all_questions'] },
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

const simpleForm = publicForm(
  [{ section_id: 's1', title: '신청', questions: [nameQuestion, emailQuestion, motiveQuestion] }],
  'all_questions',
);

const SCHOOL = { profile_id: 'p1', label: '학교', personal_info: { name: '김가상' } };
const CLUB = { profile_id: 'p2', label: '동아리', personal_info: { name: '박가상' } };

type Reply = { status: number; body?: unknown };
type Handler = (method: string, path: string, body: unknown) => Reply | undefined;
let fetchMock: ReturnType<typeof vi.fn>;

async function openLive(path: string, handler: Handler) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    const reply = handler(method, url.replace('http://api.test', ''), body) ?? {
      status: 404,
      body: { error: 'not_found' },
    };
    return Promise.resolve(
      new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
        status: reply.status,
      }),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  const { App } = await import('../App');
  return render(
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

const field = async (label: string) => (await screen.findByLabelText(label)) as HTMLInputElement;
const value = async (label: string) => (await field(label)).value;
const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const ME = {
  user_id: 'u1',
  name: '실제 사용자',
  email: 'real@example.com',
  deadline_reminder: true,
};

interface Server {
  form?: PublicForm;
  login?: boolean;
  profiles?: unknown[];
  autofill?: Reply;
  extra?: Handler;
}

function server({
  form = simpleForm,
  login = true,
  profiles = [SCHOOL, CLUB],
  autofill,
  extra,
}: Server): Handler {
  return (method, path, body) => {
    if (path === '/me')
      return login ? { status: 200, body: ME } : { status: 401, body: { error: 'x' } };
    if (method === 'GET' && path === '/f/s1') return { status: 200, body: form };
    if (method === 'GET' && path === '/me/profiles') return { status: 200, body: profiles };
    if (method === 'POST' && path === '/forms/f1/autofill' && autofill) return autofill;
    if (method === 'PUT' && path === '/me/forms/f1/response-draft') {
      return { status: 200, body: { response_id: 'd1', status: 'in_progress', saved_at: 'x' } };
    }
    if (method === 'POST' && path === '/forms/f1/responses') return { status: 201, body: {} };
    return extra?.(method, path, body);
  };
}

const NAME_ONLY: Reply = {
  status: 200,
  body: {
    answers: [{ question_id: 'q1', value: '김가상' }],
    unfilled: ['q2', 'q3'],
  },
};

async function fill() {
  fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));
  fireEvent.click(await screen.findByRole('button', { name: '채우기' }));
  await screen.findByText(/채웠어요|채울 수 있는 빈 문항이 없어요/);
}

async function submitAnswers() {
  fireEvent.click(await screen.findByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
  fireEvent.click(await screen.findByRole('button', { name: /답변 제출/ }));
  await screen.findByText('응답을 제출했어요');
}

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

describe('응답 화면의 내 정보로 채우기 (AC4, AC6)', () => {
  it('autofill_selected_profile 고른 프로필로 요청하고 제안값을 보인다', async () => {
    await openLive('/f/s1', server({ autofill: NAME_ONLY }));
    fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));
    fireEvent.change(await screen.findByLabelText('채울 프로필'), { target: { value: 'p2' } });
    fireEvent.click(screen.getByRole('button', { name: '채우기' }));

    await waitFor(async () => expect(await value('본인 이름')).toBe('김가상'));
    expect(calls('POST', '/forms/f1/autofill')).toEqual([{ profile_id: 'p2' }]);
    expect(screen.getByTestId('suggested-q1')).toBeTruthy();
  });

  it('autofill_value_reviewable unfilled 문항은 빈 채로 둔다', async () => {
    await openLive('/f/s1', server({ autofill: NAME_ONLY }));
    await fill();

    expect(await value('이메일')).toBe('');
    expect(await value('지원 동기')).toBe('');
    expect(screen.queryByTestId('suggested-q2')).toBeNull();
  });

  it('채우기만으로는 초안을 저장하지 않는다', async () => {
    await openLive('/f/s1', server({ autofill: NAME_ONLY }));
    await fill();

    await wait(AUTOSAVE_DELAY_MS + 200);
    expect(calls('PUT', '/me/forms/f1/response-draft')).toEqual([]);
  });

  it('응답자가 답을 직접 고치면 채운 값까지 함께 저장한다', async () => {
    await openLive('/f/s1', server({ autofill: NAME_ONLY }));
    await fill();

    fireEvent.change(await field('지원 동기'), { target: { value: '해 보고 싶어요' } });

    await waitFor(
      () =>
        expect(calls('PUT', '/me/forms/f1/response-draft')).toEqual([
          {
            form_version: 1,
            answers: [
              { question_id: 'q1', value: '김가상' },
              { question_id: 'q3', value: '해 보고 싶어요' },
            ],
          },
        ]),
      { timeout: AUTOSAVE_DELAY_MS + 1000 },
    );
  });

  it('autofill_value_reviewable 제안값을 고치면 고친 값으로 제출한다', async () => {
    await openLive('/f/s1', server({ autofill: NAME_ONLY }));
    await fill();

    fireEvent.change(await field('본인 이름'), { target: { value: '김수정' } });
    expect(screen.queryByTestId('suggested-q1')).toBeNull();
    await submitAnswers();

    expect(calls('POST', '/forms/f1/responses')[0].answers).toEqual([
      { question_id: 'q1', value: '김수정' },
    ]);
  });

  it('고치지 않은 제안값도 그대로 제출한다', async () => {
    await openLive('/f/s1', server({ autofill: NAME_ONLY }));
    await fill();
    await submitAnswers();

    expect(calls('POST', '/forms/f1/responses')[0].answers).toEqual([
      { question_id: 'q1', value: '김가상' },
    ]);
  });

  it('이미 쓴 답은 덮어쓰지 않고 빈 문항만 채운다', async () => {
    const both: Reply = {
      status: 200,
      body: {
        answers: [
          { question_id: 'q1', value: '김가상' },
          { question_id: 'q2', value: 'a@example.com' },
        ],
        unfilled: ['q3'],
      },
    };
    await openLive('/f/s1', server({ autofill: both }));
    fireEvent.change(await field('본인 이름'), { target: { value: '내가 쓴 이름' } });
    await fill();

    expect(await value('본인 이름')).toBe('내가 쓴 이름');
    expect(await value('이메일')).toBe('a@example.com');
    expect(
      screen.getByText(/프로필 값 1개를 채웠어요.*이미 쓴 답 1개는 그대로 뒀어요/),
    ).toBeTruthy();
  });

  it('제안을 기다리는 동안 쓴 답도 덮어쓰지 않는다', async () => {
    let release: (reply: Response) => void = () => undefined;
    await openLive('/f/s1', server({}));
    const original = fetchMock.getMockImplementation() as (
      u: string,
      i?: RequestInit,
    ) => Promise<Response>;
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      url.endsWith('/autofill')
        ? new Promise<Response>((resolve) => {
            release = resolve;
          })
        : original(url, init),
    );
    fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));
    fireEvent.click(await screen.findByRole('button', { name: '채우기' }));
    fireEvent.change(await field('본인 이름'), { target: { value: '기다리며 쓴 이름' } });

    release(new Response(JSON.stringify(NAME_ONLY.body), { status: 200 }));
    await screen.findByText(/이미 쓴 답 1개는 그대로 뒀어요/);

    expect(await value('본인 이름')).toBe('기다리며 쓴 이름');
  });

  it('지금 화면에 없는 문항의 값은 무시한다', async () => {
    const other: Reply = {
      status: 200,
      body: {
        answers: [{ question_id: 'q9', value: '새 버전 문항' }],
        unfilled: [],
      },
    };
    await openLive('/f/s1', server({ autofill: other }));
    await fill();

    expect(screen.getByText('채울 수 있는 빈 문항이 없어요.')).toBeTruthy();
    await submitAnswers();
    expect(calls('POST', '/forms/f1/responses')[0].answers).toEqual([]);
  });

  it('checkbox의 제안값을 선택으로 보인다', async () => {
    const form = publicForm(
      [
        {
          section_id: 's1',
          title: '신청',
          questions: [
            question('q1', '학과', {
              question_type: 'checkbox',
              options: ['소프트웨어학과', '경영학과'],
              profile_key: 'department',
            }),
          ],
        },
      ],
      'all_questions',
    );
    const checkbox: Reply = {
      status: 200,
      body: { answers: [{ question_id: 'q1', value: ['경영학과'] }], unfilled: [] },
    };
    await openLive('/f/s1', server({ form, autofill: checkbox }));
    await fill();

    expect((screen.getByRole('checkbox', { name: '경영학과' }) as HTMLInputElement).checked).toBe(
      true,
    );
    expect(
      (screen.getByRole('checkbox', { name: '소프트웨어학과' }) as HTMLInputElement).checked,
    ).toBe(false);
  });

  it('채운 값으로 분기 경로를 다시 계산한다', async () => {
    const form = publicForm(
      [
        {
          section_id: 's1',
          title: '첫째',
          questions: [
            question('q1', '학과', {
              question_type: 'multiple_choice',
              options: ['소프트웨어학과', '경영학과'],
              profile_key: 'department',
              branch_rules: [
                {
                  when: { match: 'all', conditions: [{ question_id: 'q1', option: '경영학과' }] },
                  go_to: { section_id: 's3' },
                },
              ],
            }),
          ],
        },
        { section_id: 's2', title: '둘째', questions: [question('q2', '건너뛸 문항')] },
        { section_id: 's3', title: '셋째', questions: [question('q3', '마지막 문항')] },
      ],
      'one_question',
    );
    const branch: Reply = {
      status: 200,
      body: { answers: [{ question_id: 'q1', value: '경영학과' }], unfilled: ['q2', 'q3'] },
    };
    await openLive('/f/s1', server({ form, autofill: branch }));
    await fill();

    fireEvent.click(screen.getByRole('button', { name: /시작하기/ }));
    expect((screen.getByRole('radio', { name: '경영학과' }) as HTMLInputElement).checked).toBe(
      true,
    );
    fireEvent.click(screen.getByRole('button', { name: /다음/ }));

    expect(await screen.findByLabelText('마지막 문항')).toBeTruthy();
    expect(screen.queryByLabelText('건너뛸 문항')).toBeNull();
  });

  it('프로필이 없으면 프로필 만들기를 안내한다', async () => {
    await openLive('/f/s1', server({ profiles: [] }));
    fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));

    const link = (await screen.findByRole('link', { name: '프로필 만들기' })) as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/me/profile');
  });

  it('프로필 목록은 버튼을 누를 때만 부른다', async () => {
    await openLive('/f/s1', server({}));
    await screen.findByRole('button', { name: '내 정보로 채우기' });

    expect(calls('GET', '/me/profiles')).toEqual([]);
  });

  it('닫힌 폼이면 마감 안내를 보인다', async () => {
    await openLive('/f/s1', server({ autofill: { status: 409, body: { error: 'form_closed' } } }));
    fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));
    fireEvent.click(await screen.findByRole('button', { name: '채우기' }));

    expect(await screen.findByText(/마감되었어요/)).toBeTruthy();
  });

  it('프로필이 삭제되었으면 안내하고 목록을 다시 읽는다', async () => {
    let lists = 0;
    const handler = server({ autofill: { status: 404, body: { error: 'not_found' } } });
    await openLive('/f/s1', (method, path, body) => {
      if (method === 'GET' && path === '/me/profiles') {
        lists += 1;
        return { status: 200, body: lists === 1 ? [SCHOOL, CLUB] : [CLUB] };
      }
      return handler(method, path, body);
    });
    fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));
    fireEvent.click(await screen.findByRole('button', { name: '채우기' }));

    expect(await screen.findByText(/프로필을 찾을 수 없어요/)).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('option', { name: '학교' })).toBeNull());
    expect(screen.getByRole('option', { name: '동아리' })).toBeTruthy();
  });

  it('요청이 실패하면 쓴 답을 그대로 두고 안내한다', async () => {
    await openLive(
      '/f/s1',
      server({ autofill: { status: 500, body: { error: 'internal_error' } } }),
    );
    fireEvent.change(await field('지원 동기'), { target: { value: '쓴 답' } });
    fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));
    fireEvent.click(await screen.findByRole('button', { name: '채우기' }));

    expect(await screen.findByText(/불러오지 못했어요/)).toBeTruthy();
    expect(await value('지원 동기')).toBe('쓴 답');
  });
});

describe('버튼을 보이는 조건', () => {
  it('비로그인에게는 로그인 안내만 보이고 로그인 전용 API를 부르지 않는다', async () => {
    await openLive('/f/s1', server({ login: false }));

    expect(await screen.findByText(/로그인하면 내 프로필로 채울 수 있어요/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: '내 정보로 채우기' })).toBeNull();
    expect(calls('GET', '/me/profiles')).toEqual([]);
  });

  it('프로필 문항이 없는 폼에는 보이지 않는다', async () => {
    const form = publicForm(
      [{ section_id: 's1', title: '신청', questions: [motiveQuestion] }],
      'all_questions',
    );
    await openLive('/f/s1', server({ form }));

    await field('지원 동기');
    expect(screen.queryByRole('button', { name: '내 정보로 채우기' })).toBeNull();
    expect(screen.queryByText(/로그인하면 내 프로필로/)).toBeNull();
  });

  it('미리보기에서는 초안 저장소가 있어도 보이지 않는다', async () => {
    const { ResponsePage } = await import('../pages/ResponsePage');
    const sink = {
      kind: 'account' as const,
      save: () => Promise.resolve(),
      clear: () => Promise.resolve(),
      draftResponseId: () => null,
      submitted: () => undefined,
    };
    render(
      <ResponsePage
        form={simpleForm}
        preview
        onSubmit={() => Promise.resolve()}
        saving={false}
        draft={{ sink }}
      />,
    );

    expect(screen.queryByRole('button', { name: '내 정보로 채우기' })).toBeNull();
  });

  it('가짜 데이터 모드에서는 보이지 않는다', async () => {
    const { ResponsePage } = await import('../pages/ResponsePage');
    render(<ResponsePage form={simpleForm} onSubmit={() => Promise.resolve()} saving={false} />);

    expect(screen.queryByRole('button', { name: '내 정보로 채우기' })).toBeNull();
    expect(screen.queryByText(/로그인하면 내 프로필로/)).toBeNull();
  });
});

describe('프로필 화면 (AC3, AC5)', () => {
  const profileServer = (initial: unknown[], extra?: Handler): Handler => {
    let list = initial;
    return (method, path, body) => {
      if (path === '/me') return { status: 200, body: ME };
      if (method === 'GET' && path === '/me/profiles') return { status: 200, body: list };
      const reply = extra?.(method, path, body);
      if (reply) return reply;
      if (method === 'POST' && path === '/me/profiles') {
        const input = body as object;
        list = [{ profile_id: 'new', ...input }, ...list];
        return { status: 201, body: { profile_id: 'new' } };
      }
      if (method === 'PUT' && path === '/me/profiles/p1') {
        list = list.map((p) =>
          (p as { profile_id: string }).profile_id === 'p1'
            ? { profile_id: 'p1', ...(body as object) }
            : p,
        );
        return { status: 200 };
      }
      if (method === 'DELETE' && path === '/me/profiles/p1') {
        list = list.filter((p) => (p as { profile_id: string }).profile_id !== 'p1');
        return { status: 204 };
      }
      return undefined;
    };
  };

  it('실제 API 모드에서는 서버 값만 보이고 데모 사용자 값이 없다', async () => {
    await openLive('/me/profile', profileServer([SCHOOL]));

    expect(await screen.findByText('김가상')).toBeTruthy();
    expect(screen.queryByText('업무용 프로필')).toBeNull();
    expect(screen.queryByText('김지윤')).toBeNull();
    expect(screen.queryByText('jiyun@atelier.team')).toBeNull();
    expect(screen.queryByText(/가상 값이 남아 있습니다/)).toBeNull();
    expect(document.body.textContent).not.toContain('데모');
  });

  it('프로필을 추가한다', async () => {
    await openLive('/me/profile', profileServer([]));
    fireEvent.click(await screen.findByRole('button', { name: '프로필 추가' }));
    fireEvent.change(await field('프로필 이름'), { target: { value: ' 학교 ' } });
    fireEvent.change(await field('이름'), { target: { value: '김가상' } });
    fireEvent.change(await field('주소'), { target: { value: '가상로 1' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByRole('heading', { name: '학교' })).toBeTruthy();
    expect(calls('POST', '/me/profiles')).toEqual([
      { label: '학교', personal_info: { name: '김가상', address: '가상로 1' } },
    ]);
  });

  it('프로필 이름이 없으면 보내지 않는다', async () => {
    await openLive('/me/profile', profileServer([]));
    fireEvent.click(await screen.findByRole('button', { name: '프로필 추가' }));
    fireEvent.click(await screen.findByRole('button', { name: '저장' }));

    expect(await screen.findByText('프로필 이름을 입력해 주세요.')).toBeTruthy();
    expect(calls('POST', '/me/profiles')).toEqual([]);
  });

  it('프로필을 수정한다', async () => {
    await openLive('/me/profile', profileServer([SCHOOL]));
    fireEvent.click(await screen.findByRole('button', { name: '학교 수정' }));
    expect(await value('이름')).toBe('김가상');
    fireEvent.change(await field('이름'), { target: { value: '김수정' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByText('김수정')).toBeTruthy();
    expect(calls('PUT', '/me/profiles/p1')).toEqual([
      { label: '학교', personal_info: { name: '김수정' } },
    ]);
  });

  it('프로필을 삭제 확인 뒤 지운다', async () => {
    await openLive('/me/profile', profileServer([SCHOOL]));
    fireEvent.click(await screen.findByRole('button', { name: '학교 삭제' }));
    expect(calls('DELETE', '/me/profiles/p1')).toEqual([]);
    fireEvent.click(await screen.findByRole('button', { name: '삭제 확인' }));

    await waitFor(() => expect(screen.queryByRole('heading', { name: '학교' })).toBeNull());
    expect(calls('DELETE', '/me/profiles/p1')).toHaveLength(1);
  });

  it('profile_rejects_sensitive_key unsupported_key를 안내하고 입력을 유지한다', async () => {
    await openLive(
      '/me/profile',
      profileServer([], (method, path) =>
        method === 'POST' && path === '/me/profiles'
          ? { status: 400, body: { error: 'unsupported_key', keys: ['resident_number'] } }
          : undefined,
      ),
    );
    fireEvent.click(await screen.findByRole('button', { name: '프로필 추가' }));
    fireEvent.change(await field('프로필 이름'), { target: { value: '학교' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByText('저장할 수 없는 항목이 있어요: resident_number')).toBeTruthy();
    expect(await value('프로필 이름')).toBe('학교');
  });

  it('비로그인이면 로그인을 안내한다', async () => {
    await openLive('/me/profile', (_m, path) =>
      path === '/me/profiles' || path === '/me' ? { status: 401, body: { error: 'x' } } : undefined,
    );

    expect(await screen.findByRole('link', { name: 'Google로 로그인' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: '프로필 추가' })).toBeNull();
  });

  it('프로필 목록을 읽지 못하면 안내한다', async () => {
    await openLive('/me/profile', (_m, path) =>
      path === '/me/profiles' ? { status: 500, body: { error: 'x' } } : { status: 200, body: ME },
    );

    expect(await screen.findByText(/프로필을 불러오지 못했어요/)).toBeTruthy();
  });

  it('삭제에 실패하면 안내하고 목록을 유지한다', async () => {
    await openLive(
      '/me/profile',
      profileServer([SCHOOL], (method, path) =>
        method === 'DELETE' && path === '/me/profiles/p1'
          ? { status: 500, body: { error: 'x' } }
          : undefined,
      ),
    );
    fireEvent.click(await screen.findByRole('button', { name: '학교 삭제' }));
    fireEvent.click(await screen.findByRole('button', { name: '삭제 확인' }));

    expect(await screen.findByText(/삭제하지 못했어요/)).toBeTruthy();
    expect(screen.getByRole('heading', { name: '학교' })).toBeTruthy();
  });
});

describe('코드 검토 반영', () => {
  const listThenFail = (first: unknown[], extra?: Handler): Handler => {
    let lists = 0;
    return (method, path, body) => {
      if (path === '/me') return { status: 200, body: ME };
      if (method === 'GET' && path === '/me/profiles') {
        lists += 1;
        return lists === 1 ? { status: 200, body: first } : { status: 500, body: { error: 'x' } };
      }
      return extra?.(method, path, body);
    };
  };

  it('저장 뒤 목록을 다시 읽지 못해도 저장 실패로 안내하지 않는다', async () => {
    await openLive(
      '/me/profile',
      listThenFail([], (method, path) =>
        method === 'POST' && path === '/me/profiles'
          ? { status: 201, body: { profile_id: 'new' } }
          : undefined,
      ),
    );
    fireEvent.click(await screen.findByRole('button', { name: '프로필 추가' }));
    fireEvent.change(await field('프로필 이름'), { target: { value: '학교' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByRole('heading', { name: '학교' })).toBeTruthy();
    expect(screen.queryByText(/저장하지 못했어요/)).toBeNull();
    expect(calls('POST', '/me/profiles')).toHaveLength(1);
  });

  it('삭제 뒤 목록을 다시 읽지 못해도 지운 프로필은 사라진다', async () => {
    await openLive(
      '/me/profile',
      listThenFail([SCHOOL], (method, path) =>
        method === 'DELETE' && path === '/me/profiles/p1' ? { status: 204 } : undefined,
      ),
    );
    fireEvent.click(await screen.findByRole('button', { name: '학교 삭제' }));
    fireEvent.click(await screen.findByRole('button', { name: '삭제 확인' }));

    await waitFor(() => expect(screen.queryByRole('heading', { name: '학교' })).toBeNull());
    expect(screen.queryByText(/삭제하지 못했어요/)).toBeNull();
  });

  it('수정 중인 프로필을 지우면 편집기를 닫는다', async () => {
    await openLive(
      '/me/profile',
      listThenFail([SCHOOL], (method, path) =>
        method === 'DELETE' && path === '/me/profiles/p1' ? { status: 204 } : undefined,
      ),
    );
    fireEvent.click(await screen.findByRole('button', { name: '학교 수정' }));
    await field('프로필 이름');
    fireEvent.click(screen.getByRole('button', { name: '학교 삭제' }));
    fireEvent.click(await screen.findByRole('button', { name: '삭제 확인' }));

    await waitFor(() => expect(screen.queryByLabelText('프로필 이름')).toBeNull());
  });

  it('프로필이 없을 때 다시 불러오거나 닫을 수 있다', async () => {
    let lists = 0;
    const handler = server({});
    await openLive('/f/s1', (method, path, body) => {
      if (method === 'GET' && path === '/me/profiles') {
        lists += 1;
        return { status: 200, body: lists === 1 ? [] : [SCHOOL] };
      }
      return handler(method, path, body);
    });
    fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));
    fireEvent.click(await screen.findByRole('button', { name: '다시 불러오기' }));

    expect(await screen.findByRole('option', { name: '학교' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));
    expect(await screen.findByRole('button', { name: '내 정보로 채우기' })).toBeTruthy();
  });

  it('없는 프로필 뒤 목록을 다시 읽지 못하면 지운 선택지를 남기지 않는다', async () => {
    let lists = 0;
    const handler = server({ autofill: { status: 404, body: { error: 'not_found' } } });
    await openLive('/f/s1', (method, path, body) => {
      if (method === 'GET' && path === '/me/profiles') {
        lists += 1;
        return lists === 1
          ? { status: 200, body: [SCHOOL] }
          : { status: 500, body: { error: 'x' } };
      }
      return handler(method, path, body);
    });
    fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));
    fireEvent.click(await screen.findByRole('button', { name: '채우기' }));

    expect(await screen.findByText(/프로필을 찾을 수 없어요/)).toBeTruthy();
    expect(await screen.findByRole('button', { name: '내 정보로 채우기' })).toBeTruthy();
    expect(screen.queryByRole('option', { name: '학교' })).toBeNull();
  });

  const twoProfiles = (): Server => ({
    extra: (method, path, body) => {
      if (method !== 'POST' || path !== '/forms/f1/autofill') return undefined;
      return (body as { profile_id: string }).profile_id === 'p1'
        ? {
            status: 200,
            body: {
              answers: [
                { question_id: 'q1', value: '김가상' },
                { question_id: 'q2', value: 'school@example.com' },
              ],
              unfilled: ['q3'],
            },
          }
        : {
            status: 200,
            body: { answers: [{ question_id: 'q1', value: '박가상' }], unfilled: ['q2', 'q3'] },
          };
    },
  });

  async function fillWith(profileId: string) {
    fireEvent.click(await screen.findByRole('button', { name: '내 정보로 채우기' }));
    fireEvent.change(await screen.findByLabelText('채울 프로필'), {
      target: { value: profileId },
    });
    fireEvent.click(screen.getByRole('button', { name: '채우기' }));
    await screen.findByRole('button', { name: '내 정보로 채우기' });
  }

  it('고치지 않은 제안값은 다른 프로필로 바꿔 채우고 새 프로필에 없는 값은 비운다', async () => {
    await openLive('/f/s1', server(twoProfiles()));
    await fillWith('p1');
    expect(await value('본인 이름')).toBe('김가상');

    await fillWith('p2');

    expect(await value('본인 이름')).toBe('박가상');
    expect(await value('이메일')).toBe('');
    expect(screen.queryByText(/이미 쓴 답/)).toBeNull();
    expect(screen.queryByTestId('suggested-q2')).toBeNull();
    expect(calls('PUT', '/me/forms/f1/response-draft')).toEqual([]);
  });

  it('직접 고친 답은 다른 프로필로 다시 채워도 유지한다', async () => {
    await openLive('/f/s1', server(twoProfiles()));
    await fillWith('p1');
    fireEvent.change(await field('본인 이름'), { target: { value: '직접 고침' } });

    await fillWith('p2');

    expect(await value('본인 이름')).toBe('직접 고침');
    expect(await value('이메일')).toBe('');
    expect(screen.getByText(/이미 쓴 답 1개는 그대로 뒀어요/)).toBeTruthy();
  });
});
