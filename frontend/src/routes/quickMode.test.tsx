// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setOwnInfo } from '../builder/formDefinition';
import { questionFromTemplate } from '../builder/quickMode';
import type { QuestionTemplateView } from '../types/api';

// 빠른 모드(SPEC 46행·179행, AC7·AC42·AC71·AC72·AC81·AC84)와 "새 폼 만들기"의 모드 고르기(사람 결정, #58).

const templates = [
  { consent_template_id: 'consent_default', purpose: '기본 목적', retention: '기본 보유' },
];

const suggestionsFor: Record<string, QuestionTemplateView[]> = {
  'apply/university': [
    {
      template_id: 'basic_01',
      question_note: '이름',
      question_type: 'short_answer',
      options: [],
      input_format: { kind: 'name' },
    },
    {
      template_id: 'apply_univ_02',
      question_note: '학과(전공)',
      question_type: 'short_answer',
      options: [],
      input_format: null,
    },
  ],
  'apply/anyone': [
    {
      template_id: 'basic_01',
      question_note: '이름',
      question_type: 'short_answer',
      options: [],
      input_format: { kind: 'name' },
    },
  ],
  'apply/worker': [
    {
      template_id: 'apply_work_02',
      question_note: '참석 가능한 시간대를 모두 골라 주세요',
      question_type: 'checkbox',
      options: ['평일 오전', '주말'],
      input_format: null,
    },
  ],
};

const emptyDraft = () => ({
  title: '',
  note: '',
  settings: { editable: false, cancellable: false, display: { default_mode: 'one_question' } },
  payment_link: null,
  consent_template_id: 'consent_default',
  note_attachments: [],
  sections: [{ section_id: 's1', title: '', questions: [] }],
  status: 'draft',
  share_url: null,
  definition_version: null,
});

type Reply = { status: number; body?: unknown };

function stubApi(
  patch: (body: Record<string, unknown>) => Reply | Promise<Reply> = () => ({ status: 200 }),
  draft: () => unknown = emptyDraft,
) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    const path = url.replace('http://api.test', '');
    const body = init?.body ? (JSON.parse(init.body as string) as Record<string, unknown>) : {};
    const query = new URLSearchParams(path.split('?')[1] ?? '');
    let reply: Reply = { status: 500 };
    if (path === '/me') reply = { status: 401 };
    else if (path === '/me/created-forms') reply = { status: 200, body: [] };
    else if (path === '/consent-templates') reply = { status: 200, body: templates };
    else if (method === 'POST' && path === '/forms')
      reply = { status: 201, body: { form_id: 'f1', status: 'draft' } };
    else if (path === '/forms/f1/definition') reply = { status: 200, body: draft() };
    else if (path.startsWith('/question-templates'))
      reply = {
        status: 200,
        body: suggestionsFor[`${query.get('purpose')}/${query.get('audience')}`] ?? [],
      };
    else if (method === 'PATCH' && path === '/forms/f1') reply = await patch(body);
    return new Response(reply.body === undefined ? '' : JSON.stringify(reply.body), {
      status: reply.status,
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  const calls = (method: string, prefix: string) =>
    fetchMock.mock.calls.filter(
      ([url, init]) =>
        url.startsWith(`http://api.test${prefix}`) && (init?.method ?? 'GET') === method,
    );
  return { calls };
}

function CurrentPath() {
  return <output data-testid="path">{useLocation().pathname}</output>;
}

async function openLive(path: string) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', 'true');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  const { App } = await import('../App');
  render(
    <React.StrictMode>
      <MemoryRouter initialEntries={[path]}>
        <App />
        <CurrentPath />
      </MemoryRouter>
    </React.StrictMode>,
  );
}

const pathText = () => screen.getByTestId('path').textContent;

async function pickPurposeAndAudience(purpose: string, audience: string) {
  fireEvent.click(await screen.findByLabelText(purpose));
  fireEvent.click(screen.getByLabelText(audience));
  fireEvent.click(screen.getByRole('button', { name: '다음' }));
  // 2단계 제목·설명을 지나 3단계 추천 문항으로 간다.
  fireEvent.click(await screen.findByRole('button', { name: '다음' }));
}

const button = (name: string | RegExp) => screen.getByRole('button', { name });

async function addFromSuggestions(label: RegExp) {
  fireEvent.click(await screen.findByLabelText(label));
  fireEvent.click(button('적용하고 다음'));
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

describe('추천 문항 복사(AC7)', () => {
  it('문구·유형·선택지·입력 형식과 출처를 복사하고 본인 정보는 확인 전까지 null이다', () => {
    const question = questionFromTemplate({
      template_id: 'reservation_all_03',
      question_note: '연락처',
      question_type: 'short_answer',
      options: [],
      input_format: { kind: 'mobile_phone' },
    });
    expect(question).toMatchObject({
      template_id: 'reservation_all_03',
      question_note: '연락처',
      question_type: 'short_answer',
      options: [],
      input_format: { kind: 'mobile_phone' },
      profile_key: null,
      required: false,
      attachments: [],
      branch_rules: [],
    });
    expect(setOwnInfo(question, true).profile_key).toBe('phone_number');
  });

  it('입력 형식이 없는 템플릿은 input_format 키를 두지 않는다(스키마가 null을 받지 않는다)', () => {
    const question = questionFromTemplate(suggestionsFor['apply/worker'][0]);
    expect('input_format' in question).toBe(false);
    expect(question.options).toEqual(['평일 오전', '주말']);
  });
});

describe('새 폼 만들기의 모드 고르기', () => {
  it('빠른 모드를 고르면 초안을 하나 만들고 빠른 모드 화면으로 간다', async () => {
    const { calls } = stubApi();
    await openLive('/creator');
    fireEvent.click(await screen.findByRole('button', { name: /새 폼 만들기/ }));
    const dialog = screen.getByRole('dialog', { name: '만드는 방식 고르기' });
    fireEvent.click(within(dialog).getByRole('button', { name: /빠른 모드/ }));

    await waitFor(() => expect(pathText()).toBe('/creator/forms/f1/quick'));
    expect(calls('POST', '/forms')).toHaveLength(1);
    expect(await screen.findByText('무엇을 위한 폼인가요?')).toBeTruthy();
  });

  it('기본 모드를 고르면 지금과 같이 기본 모드 편집 화면으로 간다', async () => {
    stubApi();
    await openLive('/creator');
    fireEvent.click(await screen.findByRole('button', { name: /새 폼 만들기/ }));
    fireEvent.click(screen.getByRole('button', { name: /기본 모드/ }));

    await waitFor(() => expect(pathText()).toBe('/creator/forms/f1/edit'));
  });

  it('닫기를 누르면 아무것도 만들지 않는다', async () => {
    const { calls } = stubApi();
    await openLive('/creator');
    fireEvent.click(await screen.findByRole('button', { name: /새 폼 만들기/ }));
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(calls('POST', '/forms')).toHaveLength(0);
  });
});

describe('빠른 모드 화면', () => {
  it('목적·대상 → 제목·설명 → 추천 문항 순서이고, 이전으로 돌아가 대상을 바꾸면 다시 불러온다(AC71)', async () => {
    const { calls } = stubApi();
    await openLive('/creator/forms/f1/quick');
    await pickPurposeAndAudience('신청', '대학생');
    expect(await screen.findByLabelText(/학과\(전공\)/)).toBeTruthy();
    expect(screen.getByRole('group', { name: '기본 정보' })).toBeTruthy();
    expect(calls('GET', '/question-templates?purpose=apply&audience=university')).toHaveLength(1);

    fireEvent.click(button('이전'));
    expect(await screen.findByLabelText('폼 제목')).toBeTruthy();
    fireEvent.click(button('이전'));
    fireEvent.click(screen.getByLabelText('직장인'));
    fireEvent.click(button('다음'));
    fireEvent.click(await screen.findByRole('button', { name: '다음' }));
    expect(await screen.findByLabelText(/참석 가능한 시간대/)).toBeTruthy();
    expect(screen.queryByLabelText(/학과\(전공\)/)).toBeNull();
  });

  it('누구나를 고르면 anyone으로 묻는다', async () => {
    const { calls } = stubApi();
    await openLive('/creator/forms/f1/quick');
    await pickPurposeAndAudience('신청', '누구나');
    expect(await screen.findByLabelText(/^이름/)).toBeTruthy();
    expect(calls('GET', '/question-templates?purpose=apply&audience=anyone')).toHaveLength(1);
  });

  it('위 단계 이름을 누르면 그 단계로 간다', async () => {
    stubApi();
    await openLive('/creator/forms/f1/quick');
    fireEvent.click(await screen.findByRole('button', { name: '2. 제목·설명' }));
    expect(await screen.findByLabelText('폼 제목')).toBeTruthy();
    fireEvent.click(button('5. 설정·배포'));
    expect(await screen.findByRole('button', { name: '배포' })).toBeTruthy();
    expect(button('5. 설정·배포').getAttribute('aria-current')).toBe('step');
  });

  it('추가한 문항은 폼 직접 편집에서 기본 모드 편집기로 고치고, 같은 폼 정의로 자동 저장한다(AC7·AC42·AC72·AC84)', async () => {
    const patches: Record<string, unknown>[] = [];
    stubApi((body) => {
      patches.push(body);
      return { status: 200, body: { status: 'draft', share_url: null, definition_version: null } };
    });
    await openLive('/creator/forms/f1/quick');
    fireEvent.click(await screen.findByLabelText('신청'));
    fireEvent.click(screen.getByLabelText('대학생'));
    fireEvent.click(button('다음'));
    fireEvent.change(await screen.findByLabelText('폼 제목'), { target: { value: '동아리 신청' } });
    fireEvent.click(button('다음'));
    await addFromSuggestions(/^이름/);

    fireEvent.click(await screen.findByRole('button', { name: '폼 직접 편집' }));
    expect((screen.getByLabelText('질문 1 문구') as HTMLInputElement).value).toBe('이름');
    const ownInfo = screen.getByRole('group', { name: '응답자 본인의 이름인가요?' });
    expect((within(ownInfo).getByLabelText('아니요') as HTMLInputElement).checked).toBe(true);

    fireEvent.click(button('직접 질문 추가'));
    fireEvent.change(screen.getByLabelText('질문 2 문구'), { target: { value: '하고 싶은 말' } });

    expect(await screen.findByText('저장됨', {}, { timeout: 4000 })).toBeTruthy();
    const saved = patches[patches.length - 1] as {
      status?: string;
      title: string;
      sections: { questions: Record<string, unknown>[] }[];
    };
    expect(saved.status).toBeUndefined();
    expect(saved.title).toBe('동아리 신청');
    const [fromTemplate, own] = saved.sections[0].questions;
    expect(fromTemplate).toMatchObject({
      template_id: 'basic_01',
      question_note: '이름',
      input_format: { kind: 'name' },
      profile_key: null,
    });
    expect(own).toMatchObject({ template_id: null, question_note: '하고 싶은 말' });
  });

  it('폼 미리보기는 응답을 만들지 않고, 직접 편집 뒤 다시 미리보기로 돌아온다(AC81)', async () => {
    const { calls } = stubApi();
    await openLive('/creator/forms/f1/quick');
    await pickPurposeAndAudience('신청', '대학생');
    await addFromSuggestions(/학과\(전공\)/);

    expect(await screen.findByRole('heading', { level: 1, name: '미리보기' })).toBeTruthy();
    // 미리보기 단계에서도 빠른 모드 제목·안내가 남는다(사람 요청).
    expect(screen.getByRole('heading', { level: 1, name: '빠른 모드로 만들기' })).toBeTruthy();
    // 한 질문씩 폼이라 응답 화면과 같이 시작 버튼을 먼저 보인다. 시작하면 고른 추천 문항이 보인다.
    fireEvent.click(button('시작하기'));
    expect(await screen.findByText('학과(전공)')).toBeTruthy();

    fireEvent.click(button('폼 직접 편집'));
    expect((screen.getByLabelText('질문 1 문구') as HTMLInputElement).value).toBe('학과(전공)');
    // 위·아래 두 곳에 있다. 아래 것(이전·다음 줄)으로 마친다.
    expect(button('편집 마치고 미리보기')).toBeTruthy();
    fireEvent.click(button('편집 마치고 미리보기 (아래)'));
    expect(screen.getByRole('heading', { level: 1, name: '미리보기' })).toBeTruthy();
    expect(calls('POST', '/forms/f1/responses')).toHaveLength(0);
  });

  it('체크한 순서대로 번호가 보이고 그 순서로 폼에 들어간다', async () => {
    const patches: Record<string, unknown>[] = [];
    stubApi((body) => {
      patches.push(body);
      return { status: 200, body: { status: 'draft', share_url: null, definition_version: null } };
    });
    await openLive('/creator/forms/f1/quick');
    await pickPurposeAndAudience('신청', '대학생');
    const major = await screen.findByLabelText(/학과\(전공\)/);
    fireEvent.click(major);
    fireEvent.click(screen.getByLabelText(/^이름/));
    expect(within(major.closest('label') as HTMLElement).getByText('1번')).toBeTruthy();
    expect(
      within(screen.getByLabelText(/^이름/).closest('label') as HTMLElement).getByText('2번'),
    ).toBeTruthy();

    fireEvent.click(button('적용하고 다음'));
    fireEvent.click(await screen.findByRole('button', { name: '폼 직접 편집' }));
    expect((screen.getByLabelText('질문 1 문구') as HTMLInputElement).value).toBe('학과(전공)');
    expect((screen.getByLabelText('질문 2 문구') as HTMLInputElement).value).toBe('이름');
  });

  it('추천 문항 단계로 돌아오면 넣은 문항이 체크된 채이고, 체크를 풀고 적용하면 폼에서 빠진다', async () => {
    stubApi();
    await openLive('/creator/forms/f1/quick');
    await pickPurposeAndAudience('신청', '대학생');
    await addFromSuggestions(/학과\(전공\)/);
    await screen.findByRole('button', { name: '폼 직접 편집' });

    fireEvent.click(button('3. 추천 문항'));
    const major = (await screen.findByLabelText(/학과\(전공\)/)) as HTMLInputElement;
    expect(major.checked).toBe(true);
    expect(button('다음')).toBeTruthy();

    fireEvent.click(major);
    fireEvent.click(button('적용하고 다음'));
    fireEvent.click(await screen.findByRole('button', { name: '폼 직접 편집' }));
    expect(screen.queryByLabelText('질문 1 문구')).toBeNull();
  });

  it('설정·배포에서 배포하면 status open으로 현재 정의를 보내고 공유 링크를 보인다', async () => {
    const patches: Record<string, unknown>[] = [];
    stubApi((body) => {
      patches.push(body);
      return body.status === 'open'
        ? {
            status: 200,
            body: { status: 'open', share_url: 'https://app.test/f/abc', definition_version: 1 },
          }
        : { status: 200, body: { status: 'draft', share_url: null, definition_version: null } };
    });
    await openLive('/creator/forms/f1/quick');
    await pickPurposeAndAudience('신청', '대학생');
    await addFromSuggestions(/학과\(전공\)/);
    fireEvent.click(await screen.findByRole('button', { name: '다음' }));
    fireEvent.click(button('배포'));

    expect(await screen.findByDisplayValue('https://app.test/f/abc')).toBeTruthy();
    const published = patches.find((p) => p.status === 'open') as {
      sections: { questions: { template_id: string }[] }[];
    };
    expect(published.sections[0].questions[0].template_id).toBe('apply_univ_02');
  });

  it('기본 모드로 열기는 지금 편집을 저장한 뒤에만 옮기고, 실패하면 남아서 알린다(코덱스 검토 1)', async () => {
    let fail = true;
    const patches: Record<string, unknown>[] = [];
    stubApi((body) => {
      patches.push(body);
      return fail
        ? { status: 500 }
        : { status: 200, body: { status: 'draft', share_url: null, definition_version: null } };
    });
    await openLive('/creator/forms/f1/quick');
    fireEvent.click(await screen.findByRole('button', { name: '2. 제목·설명' }));
    fireEvent.change(await screen.findByLabelText('폼 제목'), {
      target: { value: '옮기기 전 제목' },
    });
    // 자동 저장을 기다리지 않고 바로 누른다.
    fireEvent.click(button('기본 모드로 열기'));

    expect(await screen.findByText(/저장 실패/)).toBeTruthy();
    expect(pathText()).toBe('/creator/forms/f1/quick');

    fail = false;
    fireEvent.click(button('기본 모드로 열기'));
    await waitFor(() => expect(pathText()).toBe('/creator/forms/f1/edit'));
    expect((patches[patches.length - 1] as { title: string }).title).toBe('옮기기 전 제목');
  });

  it('배포 요청 중에는 설정을 잠그고, 배포 뒤 성공 화면을 보인다(코덱스 검토 2)', async () => {
    let release: (reply: Reply) => void = () => undefined;
    stubApi((body) =>
      body.status === 'open'
        ? new Promise<Reply>((resolve) => {
            release = resolve;
          })
        : { status: 200, body: { status: 'draft', share_url: null, definition_version: null } },
    );
    await openLive('/creator/forms/f1/quick');
    await pickPurposeAndAudience('신청', '대학생');
    await addFromSuggestions(/학과\(전공\)/);
    fireEvent.click(await screen.findByRole('button', { name: '다음' }));
    const consent = await screen.findByLabelText('동의 템플릿');
    expect(consent.matches(':disabled')).toBe(false);

    fireEvent.click(button('배포'));
    await waitFor(() => expect(consent.matches(':disabled')).toBe(true));
    expect(consent.closest('fieldset')?.hasAttribute('disabled')).toBe(true);

    release({
      status: 200,
      body: { status: 'open', share_url: 'https://app.test/f/abc', definition_version: 1 },
    });
    expect(await screen.findByDisplayValue('https://app.test/f/abc')).toBeTruthy();
    expect(screen.queryByText(/저장 실패/)).toBeNull();
  });

  it('섹션이 없는 초안에서도 추천 적용과 직접 추가가 새 섹션 하나에 들어간다(코덱스 검토 3)', async () => {
    const patches: Record<string, unknown>[] = [];
    stubApi(
      (body) => {
        patches.push(body);
        return {
          status: 200,
          body: { status: 'draft', share_url: null, definition_version: null },
        };
      },
      () => ({ ...emptyDraft(), sections: [] }),
    );
    await openLive('/creator/forms/f1/quick');
    await pickPurposeAndAudience('신청', '대학생');
    await addFromSuggestions(/학과\(전공\)/);
    fireEvent.click(await screen.findByRole('button', { name: '폼 직접 편집' }));
    fireEvent.click(button('직접 질문 추가'));

    expect(await screen.findByText('저장됨', {}, { timeout: 4000 })).toBeTruthy();
    const saved = patches[patches.length - 1] as {
      sections: { section_id: string; questions: { template_id: string | null }[] }[];
    };
    expect(saved.sections).toHaveLength(1);
    expect(saved.sections[0].section_id).toMatch(/^s_/);
    expect(saved.sections[0].questions.map((q) => q.template_id)).toEqual(['apply_univ_02', null]);
  });
});
