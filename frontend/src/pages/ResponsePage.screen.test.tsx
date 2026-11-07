// @vitest-environment jsdom
/**
 * 응답 화면(T4 #52)의 AC별 화면 테스트. E3(#81)의 `ResponsePage.test.tsx`와 파일을 나눴다. 테스트 이름 앞의 이름은 SPEC 추적표(590~668행)의 케이스 이름이다.
 * 서버 판정은 모의 `onSubmit`이 돌려주는 오류로 대신한다. 실제 서버와의 동작은 사람이 실제 화면에서 확인한다.
 */
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import type { PublicForm, Question, SubmitResponseInput } from '../types/api';
import { ResponsePage } from './ResponsePage';

function q(id: string, note: string, overrides: Partial<Question> = {}): Question {
  return {
    question_id: id,
    template_id: null,
    question_note: note,
    question_type: 'short_answer',
    options: [],
    required: false,
    profile_key: null,
    attachments: [],
    branch_rules: [],
    ...overrides,
  };
}

const consent = {
  items: ['이름', '문항별 응답 내용'],
  purpose: '행사 참가 확인',
  retention: '행사 종료 후 1개월',
  refusal_notice: '동의하지 않으면 제출할 수 없습니다.',
  ai_summary: null,
};

function form(overrides: Partial<PublicForm> = {}): PublicForm {
  return {
    form_id: 'f1',
    definition_version: 3,
    title: '행사 참가 신청',
    note: '',
    note_attachments: [],
    deadline: '2026-11-30T14:59:00Z',
    display: { default_mode: 'all_questions', allowed_modes: ['one_question', 'all_questions'] },
    payment_link: null,
    consent,
    sections: [
      { section_id: 's1', title: '기본 정보', questions: [q('q1', '이름', { required: true })] },
      {
        section_id: 's2',
        title: '참가 정보',
        questions: [q('q2', '하고 싶은 말', { question_type: 'long_answer' }), q('q3', '연락처')],
      },
      { section_id: 's3', title: '마무리', questions: [q('q4', '기타')] },
    ],
    ...overrides,
  };
}

/** form_required_branch와 같은 모양의 분기 폼. q1 "아니요"면 s2를 건너뛴다. */
function branchedForm(mode: 'section_page' | 'one_question' = 'section_page'): PublicForm {
  return form({
    display: { default_mode: mode, allowed_modes: [mode] },
    sections: [
      {
        section_id: 's1',
        title: '참석 여부',
        questions: [
          q('q1', '행사에 참석하나요?', {
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
      {
        section_id: 's2',
        title: '참석 정보',
        questions: [
          q('q2', '참석 날짜', { required: true, input_format: { kind: 'date' } }),
          q('q3', '식사 신청', {
            question_type: 'multiple_choice',
            options: ['신청', '미신청'],
            required: true,
          }),
        ],
      },
      {
        section_id: 's3',
        title: '마무리',
        questions: [
          q('q4', '소속', {
            question_type: 'dropdown',
            options: ['학생회', '동아리'],
            required: true,
          }),
        ],
      },
    ],
  });
}

let onSubmit: Mock<(body: SubmitResponseInput) => Promise<void>>;
let onReload: Mock<() => void>;

function open(value: PublicForm) {
  return render(
    <ResponsePage form={value} onSubmit={onSubmit} onReload={onReload} saving={false} />,
  );
}

const input = (label: string) => screen.getByLabelText(label) as HTMLInputElement;
const type = (label: string, value: string) =>
  fireEvent.change(input(label), { target: { value } });
const click = (name: string | RegExp) => fireEvent.click(screen.getByRole('button', { name }));
const agree = () =>
  fireEvent.click(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
const submitButton = () => screen.getByRole('button', { name: /^답변 제출/ }) as HTMLButtonElement;
const alerts = () => screen.queryAllByRole('alert').map((el) => el.textContent);
const lastBody = () => onSubmit.mock.calls[onSubmit.mock.calls.length - 1][0];
const rejectWith = (status: number, detail: unknown) =>
  onSubmit.mockImplementationOnce(() => Promise.reject(new ApiError(status, detail)));

beforeEach(() => {
  window.scrollTo = vi.fn();
  onSubmit = vi.fn<(body: SubmitResponseInput) => Promise<void>>(() => Promise.resolve());
  onReload = vi.fn<() => void>();
});

afterEach(() => cleanup());

describe('AC47 보기 방식과 전환', () => {
  it('unbranched_respondent_switch_preserves_answers: 전환해도 답과 보던 문항을 유지하고 시작 화면으로 가지 않는다', () => {
    open(form());
    type('이름', '김하나');
    fireEvent.focus(input('하고 싶은 말'));
    type('하고 싶은 말', '잘 부탁해요');
    click('한 질문씩');
    // 보던 문항(q2)으로 간다. 시작 버튼이 아니다.
    expect(screen.queryByRole('button', { name: /시작하기/ })).toBeNull();
    expect(input('하고 싶은 말').value).toBe('잘 부탁해요');
    expect(screen.queryByLabelText('이름')).toBeNull();
    click('이전');
    expect(input('이름').value).toBe('김하나');
    click('전체 질문 보기');
    expect(input('이름').value).toBe('김하나');
    expect(input('하고 싶은 말').value).toBe('잘 부탁해요');
    const switcher = screen.getByRole('group', { name: '보기 방식' });
    expect(
      within(switcher).getByRole('button', { name: '전체 질문 보기' }).getAttribute('aria-pressed'),
    ).toBe('true');
  });

  it('unbranched_respondent_switch_preserves_answers: 한 질문씩 → 전체 → 한 질문씩 왕복해도 지금 문항으로 돌아온다', () => {
    open(
      form({
        display: { default_mode: 'one_question', allowed_modes: ['one_question', 'all_questions'] },
      }),
    );
    click(/시작하기/);
    // 실제 입력처럼 첫 문항에 초점을 둔 뒤 쓴다. 이 초점이 왕복 뒤에 남으면 안 된다(PR #82 리뷰).
    fireEvent.focus(input('이름'));
    type('이름', '김하나');
    click(/다음/);
    expect(screen.getByLabelText('하고 싶은 말')).toBeTruthy();
    click('전체 질문 보기');
    click('한 질문씩');
    expect(screen.getByLabelText('하고 싶은 말')).toBeTruthy();
    expect(screen.queryByLabelText('이름')).toBeNull();
    click('이전');
    expect(input('이름').value).toBe('김하나');
  });

  it('branched_mode_fixed: 분기가 있는 폼에는 전환 버튼이 없다', () => {
    open(branchedForm());
    expect(screen.queryByRole('group', { name: '보기 방식' })).toBeNull();
    expect(screen.queryByRole('button', { name: '한 질문씩' })).toBeNull();
  });

  it('all_questions_sections_visible: 모든 섹션 제목과 문항을 한 페이지에 보여 준다', () => {
    open(form());
    for (const title of ['기본 정보', '참가 정보', '마무리']) {
      expect(screen.getByRole('heading', { level: 3, name: title })).toBeTruthy();
    }
    for (const label of ['이름', '하고 싶은 말', '연락처', '기타'])
      expect(screen.getByLabelText(label)).toBeTruthy();
  });

  it('section_page_first_screen: 첫 화면에 제목·설명과 첫 섹션의 모든 문항을 보여 준다', () => {
    open({ ...branchedForm(), note: '참가 여부를 알려 주세요.' });
    expect(screen.getByRole('heading', { level: 2, name: '행사 참가 신청' })).toBeTruthy();
    expect(screen.getByText('참가 여부를 알려 주세요.')).toBeTruthy();
    expect(screen.getByRole('group', { name: '행사에 참석하나요?' })).toBeTruthy();
    expect(screen.queryByLabelText('참석 날짜')).toBeNull();
  });

  it('one_question_intro_then_first_question: 시작 버튼을 누르기 전에는 첫 문항을 숨긴다', () => {
    open(
      form({
        display: { default_mode: 'one_question', allowed_modes: ['one_question', 'all_questions'] },
      }),
    );
    expect(screen.getByRole('heading', { level: 2, name: '행사 참가 신청' })).toBeTruthy();
    expect(screen.queryByLabelText('이름')).toBeNull();
    click(/시작하기/);
    expect(screen.getByLabelText('이름')).toBeTruthy();
    expect(screen.queryByLabelText('하고 싶은 말')).toBeNull();
  });
});

describe('AC60 진행 표시', () => {
  it('respondent_progress_known_path: 분기 없는 3개 섹션 폼의 두 번째 섹션에서 현재 위치와 남은 1개 섹션을 보인다', () => {
    open(
      form({
        display: { default_mode: 'one_question', allowed_modes: ['one_question', 'all_questions'] },
      }),
    );
    click(/시작하기/);
    type('이름', '김하나');
    click(/다음/);
    expect(screen.getByTestId('progress-label').textContent).toBe(
      '섹션 2/3 · 참가 정보 · 문항 2/4',
    );
    expect(screen.getByTestId('progress-detail').textContent).toBe('남은 섹션 1개 · 남은 문항 2개');
  });

  it('respondent_progress_branch_unknown: 답하기 전에는 전체 수·백분율을 확정해 보이지 않는다', () => {
    const { container } = open(branchedForm());
    const label = screen.getByTestId('progress-label').textContent ?? '';
    expect(label).not.toMatch(/\d+\/\d+/);
    expect(label).not.toMatch(/%/);
    expect(screen.getByTestId('progress-detail').textContent).toBe(
      '남은 분량은 답에 따라 달라져요.',
    );
    expect(container.querySelector('.completion .track')).toBeNull();
  });
});

describe('경로에 따른 페이지 넘기기 (AC9 화면 부분)', () => {
  it('skipped_answers_dropped의 화면: "아니요"면 다음 화면이 건너뛴 섹션이 아니라 마무리 섹션이다', () => {
    open(branchedForm());
    fireEvent.click(screen.getByRole('radio', { name: '아니요' }));
    click(/다음/);
    expect(screen.getByRole('heading', { level: 3, name: '마무리' })).toBeTruthy();
    expect(screen.queryByLabelText('참석 날짜')).toBeNull();
  });

  it('required_missing_visited_only의 화면: 지나온 필수 빈 문항(q4)에만 오류를 보인다', () => {
    open(branchedForm());
    fireEvent.click(screen.getByRole('radio', { name: '아니요' }));
    click(/다음/);
    click(/다음/);
    expect(alerts()).toEqual(['필수 문항이에요. 답을 입력해 주세요.']);
    expect(screen.getByLabelText('소속').getAttribute('aria-invalid')).toBe('true');
  });

  it('제출 본문에 경로를 넣지 않고 서버가 다시 계산하게 둔다', async () => {
    open(branchedForm());
    fireEvent.click(screen.getByRole('radio', { name: '예' }));
    click(/다음/);
    type('참석 날짜', '2026-11-20');
    fireEvent.click(screen.getByRole('radio', { name: '신청' }));
    click('이전');
    fireEvent.click(screen.getByRole('radio', { name: '아니요' }));
    click(/다음/);
    fireEvent.change(screen.getByLabelText('소속'), { target: { value: '동아리' } });
    click(/다음/);
    agree();
    fireEvent.click(submitButton());
    await screen.findByText('응답을 제출했어요');
    expect(Object.keys(lastBody()).sort()).toEqual(['answers', 'consent_agreed', 'form_version']);
    expect(lastBody().form_version).toBe(3);
  });
});

describe('AGENTS.md 5절 오류는 "다음"을 누를 때 문항 아래에', () => {
  it('입력 중에는 오류를 보이지 않고 "다음"을 누르면 그 문항 아래에 보인다', () => {
    open(branchedForm('one_question'));
    click(/시작하기/);
    fireEvent.click(screen.getByRole('radio', { name: '예' }));
    click(/다음/);
    type('참석 날짜', '2026-02-30');
    expect(alerts()).toEqual([]);
    click(/다음/);
    const error = screen.getByRole('alert');
    expect(error.textContent).toContain('형식이 맞지 않아요');
    expect(error.closest('.question')?.id).toBe('question-q2');
    expect(screen.getByLabelText('참석 날짜')).toBeTruthy();
  });

  it('필수 문항을 비우고 "다음"을 누르면 넘어가지 않는다', () => {
    open(branchedForm());
    click(/다음/);
    expect(alerts()).toEqual(['필수 문항이에요. 답을 입력해 주세요.']);
    expect(screen.getByRole('group', { name: '행사에 참석하나요?' })).toBeTruthy();
  });
});

describe('AC53 글자 수', () => {
  const limited = () =>
    form({
      sections: [
        {
          section_id: 's1',
          title: '의견',
          questions: [
            q('q1', '한 줄 소감', { character_limit: 100 }),
            q('q2', '자세한 의견', { question_type: 'long_answer', character_limit: 100 }),
            q('q3', '제한 없는 답'),
          ],
        },
      ],
    });

  it('character_count_display_and_validation: 공백·줄바꿈을 포함한 글자 수를 현재/상한으로 보인다', () => {
    open(limited());
    type('한 줄 소감', '가나다 라마바 사');
    type('자세한 의견', '가나다\n라마 바사.');
    expect(screen.getAllByText('9/100')).toHaveLength(1);
    expect(screen.getAllByText('10/100')).toHaveLength(1);
    // 제한이 없는 문항에는 표시하지 않는다.
    expect(input('제한 없는 답').parentElement?.querySelector('.field-count')).toBeNull();
  });

  it('101자는 입력은 남기되 제출을 막고, 정확히 100자는 제출된다', async () => {
    open(limited());
    type('자세한 의견', 'a'.repeat(101));
    expect((screen.getByLabelText('자세한 의견') as HTMLTextAreaElement).value).toHaveLength(101);
    expect(screen.getByText('101/100').className).toContain('over');
    agree();
    fireEvent.click(submitButton());
    expect(alerts()).toEqual(['최대 100자까지 입력할 수 있어요.']);
    expect(onSubmit).not.toHaveBeenCalled();
    type('자세한 의견', 'a'.repeat(100));
    fireEvent.click(submitButton());
    await screen.findByText('응답을 제출했어요');
    expect(lastBody().answers).toEqual([{ question_id: 'q2', value: 'a'.repeat(100) }]);
  });

  it('서버가 character_limit_exceeded를 돌려주면 그 문항 아래에 보인다', async () => {
    rejectWith(400, { error: 'character_limit_exceeded', question_ids: ['q1'] });
    open(limited());
    type('한 줄 소감', '짧은 답');
    agree();
    fireEvent.click(submitButton());
    const error = await screen.findByRole('alert');
    expect(error.closest('.question')?.id).toBe('question-q1');
  });
});

describe('AC73 입력 형식 안내', () => {
  const formatted = () =>
    form({
      sections: [
        {
          section_id: 's1',
          title: '연락처',
          questions: [
            q('q1', '휴대전화', { input_format: { kind: 'mobile_phone' }, required: true }),
            q('q2', '이메일', { input_format: { kind: 'email' } }),
            q('q3', '방문 날짜', { input_format: { kind: 'date' } }),
          ],
        },
      ],
    });

  it('형식마다 안내 문구를 늘 보이고 맞는 입력란을 쓴다', () => {
    open(formatted());
    expect(screen.getByText('휴대전화 번호를 입력해 주세요. 예: 010-1234-5678')).toBeTruthy();
    expect(screen.getByText('이메일을 입력해 주세요. 예: name@example.com')).toBeTruthy();
    expect(
      screen.getByText('날짜를 YYYY-MM-DD 형식으로 입력해 주세요. 예: 2026-11-20'),
    ).toBeTruthy();
    expect(input('휴대전화').type).toBe('tel');
    expect(input('이메일').type).toBe('email');
    expect(input('방문 날짜').placeholder).toBe('YYYY-MM-DD');
  });

  it('형식에 맞지 않으면 제출 전에 알리고, 서버의 invalid_input_format_value도 문항 아래에 보인다', async () => {
    open(formatted());
    type('휴대전화', '0101234567');
    type('이메일', 'a@@example.com');
    agree();
    fireEvent.click(submitButton());
    expect(alerts()).toHaveLength(2);
    expect(onSubmit).not.toHaveBeenCalled();
    type('휴대전화', '010-1234-5678');
    type('이메일', 'a@example.com');
    rejectWith(400, { error: 'invalid_input_format_value', question_ids: ['q2'] });
    fireEvent.click(submitButton());
    const error = await screen.findByRole('alert');
    expect(error.closest('.question')?.id).toBe('question-q2');
  });
});

describe('AC80 응답 화면의 다섯 문항 유형', () => {
  it('basic_builder_five_question_types의 응답 화면: 유형별 입력과 설명·필수 표시가 설정과 같다', () => {
    open(
      form({
        sections: [
          {
            section_id: 's1',
            title: '유형',
            questions: [
              q('q1', '객관식', {
                question_type: 'multiple_choice',
                options: ['가', '나'],
                required: true,
                question_description: '하나만 골라요',
              }),
              q('q2', '드롭다운', { question_type: 'dropdown', options: ['다', '라'] }),
              q('q3', '체크박스', { question_type: 'checkbox', options: ['마', '바', '사'] }),
              q('q4', '짧은 답', { input_format: { kind: 'date' } }),
              q('q5', '장문 답', { question_type: 'long_answer' }),
            ],
          },
        ],
      }),
    );
    const single = screen.getByRole('group', { name: '객관식' });
    expect(within(single).getAllByRole('radio')).toHaveLength(2);
    expect(screen.getByText('하나만 골라요')).toBeTruthy();
    expect(screen.getByLabelText('드롭다운').tagName).toBe('SELECT');
    const multiple = screen.getByRole('group', { name: '체크박스' });
    fireEvent.click(within(multiple).getByRole('checkbox', { name: '사' }));
    fireEvent.click(within(multiple).getByRole('checkbox', { name: '마' }));
    expect(
      within(multiple)
        .getAllByRole('checkbox')
        .filter((c) => (c as HTMLInputElement).checked),
    ).toHaveLength(2);
    expect(screen.getByLabelText('짧은 답').tagName).toBe('INPUT');
    expect(
      screen.getByText('날짜를 YYYY-MM-DD 형식으로 입력해 주세요. 예: 2026-11-20'),
    ).toBeTruthy();
    expect(screen.getByLabelText('장문 답').tagName).toBe('TEXTAREA');
    const badges = screen.getAllByText(/^(필수|선택)$/).map((el) => el.textContent);
    expect(badges).toEqual(['필수', '선택', '선택', '선택', '선택']);
  });

  it('체크박스 답은 선택지 순서의 배열로 보낸다', async () => {
    open(
      form({
        sections: [
          {
            section_id: 's1',
            title: '유형',
            questions: [
              q('q1', '체크박스', { question_type: 'checkbox', options: ['마', '바', '사'] }),
            ],
          },
        ],
      }),
    );
    fireEvent.click(screen.getByRole('checkbox', { name: '사' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '마' }));
    agree();
    fireEvent.click(submitButton());
    await screen.findByText('응답을 제출했어요');
    expect(lastBody().answers).toEqual([{ question_id: 'q1', value: ['마', '사'] }]);
  });
});

describe('AC13·AC14 동의', () => {
  it('consent_lists_input_formats의 화면: 수집 항목·목적·보유 기간·동의 거부 안내를 "내용 보기"로 보인다', () => {
    open(form({ consent: { ...consent, retention: '행사 종료 후 1개월\n이후 파기' } }));
    const toggle = screen.getByRole('button', { name: '내용 보기' });
    // 평소에는 접혀 있다(이슈 #41 결정).
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByText('행사 참가 확인')).toBeNull();
    fireEvent.click(toggle);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByText(/행사 종료 후 1개월/).className).toContain('keep-lines');
    expect(screen.getByText('이름, 문항별 응답 내용')).toBeTruthy();
    expect(screen.getByText('행사 참가 확인')).toBeTruthy();
    expect(screen.getByText('동의하지 않으면 제출할 수 없습니다.')).toBeTruthy();
  });

  it('submit_without_consent의 화면: 필수 동의 전에는 제출 버튼이 꺼져 있다', () => {
    open(form());
    type('이름', '김하나');
    expect(submitButton().disabled).toBe(true);
    fireEvent.click(submitButton());
    expect(onSubmit).not.toHaveBeenCalled();
    agree();
    expect(submitButton().disabled).toBe(false);
  });

  it('서버가 consent_required를 돌려주면 동의 아래에 알리고 답을 지우지 않는다', async () => {
    rejectWith(400, { error: 'consent_required' });
    open(form());
    type('이름', '김하나');
    agree();
    fireEvent.click(submitButton());
    expect((await screen.findByRole('alert')).textContent).toContain('필수 동의가 필요해요');
    expect(input('이름').value).toBe('김하나');
  });
});

describe('AC91 AI 요약 선택 동의', () => {
  const notice = {
    provider: 'OpenAI',
    processing_country: '미국',
    items: ['AI 요약 대상 주관식 응답 내용'],
    purpose: '제작자의 응답 요약·주제 분류',
    retention: '최대 30일',
    refusal_effect: '제출할 수 있으며, 내 응답은 AI 요약에서만 제외된다',
    checked_on: '2026-10-28',
  };

  it('ai_summary_optional_consent_submit의 화면: 필수 동의와 별개인 고지와 선택 체크박스를 제출 직전에 보이고, 안 골라도 제출한다', async () => {
    open(form({ consent: { ...consent, ai_summary: notice } }));
    const optional = screen.getByRole('checkbox', {
      name: /AI 요약을 위한 외부 전송에 동의합니다 \(선택\)/,
    });
    expect(optional).not.toBe(
      screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }),
    );
    for (const value of Object.values(notice).flat()) expect(screen.getByText(value)).toBeTruthy();
    // 제출 버튼 바로 앞(폼 맨 끝)에 있다.
    expect(
      optional.compareDocumentPosition(submitButton()) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    type('이름', '김하나');
    agree();
    expect(submitButton().disabled).toBe(false);
    fireEvent.click(submitButton());
    await screen.findByText('응답을 제출했어요');
    expect(lastBody().ai_summary_agreed).toBe(false);
    expect(lastBody().consent_agreed).toBe(true);
  });

  it('선택 동의를 고르면 true로 보낸다', async () => {
    open(form({ consent: { ...consent, ai_summary: notice } }));
    type('이름', '김하나');
    agree();
    fireEvent.click(screen.getByRole('checkbox', { name: /AI 요약을 위한 외부 전송/ }));
    fireEvent.click(submitButton());
    await screen.findByText('응답을 제출했어요');
    expect(lastBody().ai_summary_agreed).toBe(true);
  });

  it('AI 요약이 꺼진 폼은 고지와 선택 체크박스를 보이지 않고 값을 보내지 않는다', async () => {
    open(form());
    expect(screen.queryByText(/AI 요약을 위한 외부 전송/)).toBeNull();
    expect(screen.getAllByRole('checkbox')).toHaveLength(1);
    type('이름', '김하나');
    agree();
    fireEvent.click(submitButton());
    await screen.findByText('응답을 제출했어요');
    expect('ai_summary_agreed' in lastBody()).toBe(false);
  });
});

describe('AC11 송금 링크', () => {
  it('payment_link_https_only의 화면: https 링크를 그대로 보여 주고 새 창으로 연다', () => {
    open(form({ payment_link: 'https://pay.example.com/r/abc?x=1' }));
    const link = screen.getByRole('link', { name: 'https://pay.example.com/r/abc?x=1' });
    expect(link.getAttribute('href')).toBe('https://pay.example.com/r/abc?x=1');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
    expect(
      screen.getByText(/Passform은 송금을 처리하거나 송금했는지 확인하지 않아요/),
    ).toBeTruthy();
  });

  it.each(['http://pay.example.com', 'javascript:alert(1)', '//pay.example.com', '송금'])(
    '%s는 링크로 열지 않는다',
    (link) => {
      open(form({ payment_link: link }));
      expect(screen.queryByRole('link')).toBeNull();
      expect(screen.getByText('안전하지 않은 주소라 링크를 열지 않아요.')).toBeTruthy();
    },
  );

  it('송금 링크가 없으면 안내도 없다', () => {
    open(form());
    expect(screen.queryByText('송금 안내')).toBeNull();
  });
});

describe('AC41 마감 안내와 AC76 버전 변경', () => {
  it('마감을 Asia/Seoul 시각으로 보인다', () => {
    open(form());
    expect(screen.getByText('2026년 11월 30일 23:59까지 받아요')).toBeTruthy();
  });

  it('share_url_closed_form의 제출 부분: 제출이 409 form_closed면 마감 안내를 보인다', async () => {
    rejectWith(409, { error: 'form_closed' });
    open(form());
    type('이름', '김하나');
    agree();
    fireEvent.click(submitButton());
    expect(await screen.findByText('「행사 참가 신청」은(는) 마감되었어요')).toBeTruthy();
    expect(screen.queryByLabelText('이름')).toBeNull();
  });

  it('stale_form_version_rejected의 화면: 최신 폼을 다시 열도록 안내하고 버튼으로 다시 받는다', async () => {
    rejectWith(409, { error: 'form_version_changed' });
    open(form());
    type('이름', '김하나');
    agree();
    fireEvent.click(submitButton());
    expect(await screen.findByText('제작자가 폼을 새로 배포했어요')).toBeTruthy();
    click('최신 폼 다시 열기');
    expect(onReload).toHaveBeenCalledTimes(1);
  });
});

describe('제출 뒤', () => {
  it('완료 화면에 송금 링크를 다시 보이고 새 응답을 시작할 수 있다', async () => {
    open(form({ payment_link: 'https://pay.example.com/r/abc' }));
    type('이름', '김하나');
    agree();
    fireEvent.click(submitButton());
    expect(await screen.findByText('응답을 제출했어요')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'https://pay.example.com/r/abc' })).toBeTruthy();
    click('새 응답 작성');
    expect(input('이름').value).toBe('');
  });

  it('알 수 없는 실패는 답을 지우지 않고 다시 시도하라고 알린다', async () => {
    onSubmit.mockImplementationOnce(() => Promise.reject(new Error('network')));
    open(form());
    type('이름', '김하나');
    agree();
    fireEvent.click(submitButton());
    expect((await screen.findByRole('alert')).textContent).toContain('제출하지 못했어요');
    expect(input('이름').value).toBe('김하나');
  });
});

describe('빌더 미리보기 (AC81, 사람 결정: 응답자 화면과 같게)', () => {
  function openPreview(value: PublicForm) {
    return render(
      <ResponsePage preview form={value} onSubmit={onSubmit} onBack={vi.fn()} saving={false} />,
    );
  }

  it('시작 방식·검사를 응답 화면과 같게 거치고, 제출은 부르지 않고 안내만 한다', () => {
    openPreview(
      form({
        display: { default_mode: 'one_question', allowed_modes: ['one_question', 'all_questions'] },
      }),
    );
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('미리보기');
    expect(screen.getByRole('button', { name: /편집으로 돌아가기/ })).toBeTruthy();
    expect(screen.queryByLabelText('이름')).toBeNull();
    click(/시작하기/);
    click(/다음/);
    expect(alerts()).toEqual(['필수 문항이에요. 답을 입력해 주세요.']);
    type('이름', '김하나');
    for (let i = 0; i < 4; i++) click(/다음/);
    agree();
    fireEvent.click(submitButton());
    expect(screen.getByRole('status').textContent).toBe('미리보기라 제출되지 않아요.');
    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.queryByText('응답을 제출했어요')).toBeNull();
  });

  it('응답 화면에는 미리보기 안내가 없다', () => {
    open(form());
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('응답 작성');
    expect(screen.queryByText('미리보기라 제출되지 않아요.')).toBeNull();
  });
});
