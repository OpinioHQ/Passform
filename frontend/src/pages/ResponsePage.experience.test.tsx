// @vitest-environment jsdom
/**
 * 제출 완료 화면의 테스트 기간 한정 사용 경험 평가(AC95, SPEC 4절 59~61행)와 로그인 응답의 제출 내역 바로가기.
 * 서버 판정은 모의 `onSubmit`이 돌려주는 201 본문과 모의 `api.rateExperience`로 대신한다.
 */
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from '../api/client';
import type { DraftSink } from '../response/draftSync';
import type {
  ExperienceRequest,
  PublicForm,
  SubmitResponseInput,
  SubmittedResponse,
} from '../types/api';
import { ResponsePage } from './ResponsePage';

const form: PublicForm = {
  form_id: 'f1',
  definition_version: 1,
  title: '행사 참가 신청',
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

function created(token: string | null): SubmittedResponse {
  return {
    response_id: 'r1',
    form_version: 1,
    status: 'submitted',
    content: [{ question_id: 'q1', question_note: '하고 싶은 말', value: '잘 부탁해요' }],
    attachments: [],
    submitted_at: '2026-11-25T00:00:00Z',
    updated_at: null,
    experience_token: token,
  };
}

function sink(kind: DraftSink['kind']): DraftSink {
  return {
    kind,
    save: () => Promise.resolve(),
    clear: () => Promise.resolve(),
    draftResponseId: () => null,
    submitted: () => undefined,
  };
}

let onSubmit: Mock<(body: SubmitResponseInput) => Promise<SubmittedResponse | void>>;
let rate: Mock<(formId: string, responseId: string, body: ExperienceRequest) => Promise<void>>;

function open(draftSink?: DraftSink) {
  return render(
    <MemoryRouter initialEntries={['/f/abc']}>
      <Routes>
        <Route
          path="/f/abc"
          element={
            <ResponsePage
              form={form}
              onSubmit={onSubmit}
              saving={false}
              draft={draftSink ? { sink: draftSink } : undefined}
            />
          }
        />
        <Route path="/me/responses/:responseId" element={<p>제출 내역 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function submitAnswer() {
  fireEvent.change(screen.getByLabelText('하고 싶은 말'), { target: { value: '잘 부탁해요' } });
  fireEvent.click(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의합니다/ }));
  fireEvent.click(screen.getByRole('button', { name: /^답변 제출/ }));
  await screen.findByText('응답을 제출했어요');
}

const button = (name: string) => screen.getByRole('button', { name }) as HTMLButtonElement;

beforeEach(() => {
  window.scrollTo = vi.fn();
  onSubmit = vi.fn<(body: SubmitResponseInput) => Promise<SubmittedResponse | void>>(() =>
    Promise.resolve(created('token-1')),
  );
  rate = vi.fn<(formId: string, responseId: string, body: ExperienceRequest) => Promise<void>>(() =>
    Promise.resolve(),
  );
  vi.spyOn(api, 'rateExperience').mockImplementation(rate);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('AC95 사용 경험 평가 (테스트 기간 한정)', () => {
  it('표가 있으면 완료 사실 아래에 두 문항과 4단계 선택지, 두 버튼을 보인다', async () => {
    open();
    await submitAnswer();

    expect(screen.getByText('「행사 참가 신청」에 응답해 주셔서 고마워요.')).toBeTruthy();
    expect(screen.getByText('이번 폼을 작성하는 과정은 얼마나 쉬웠나요? (선택)')).toBeTruthy();
    expect(screen.getByLabelText('어떤 점을 개선하면 좋을까요? (선택)')).toBeTruthy();
    expect(screen.getAllByRole('radio').map((r) => r.parentElement?.textContent)).toEqual([
      '매우 어려움',
      '어려움',
      '쉬움',
      '매우 쉬움',
    ]);
    expect(screen.queryByText('보통')).toBeNull();
    expect(button('평가 보내기')).toBeTruthy();
    expect(button('건너뛰기')).toBeTruthy();
    expect(button('새 응답 작성')).toBeTruthy();
  });

  it('표가 없으면(제출이 본문 없이 끝나거나 null) 지금 완료 화면 그대로다', async () => {
    onSubmit.mockResolvedValueOnce(undefined);
    open();
    await submitAnswer();
    expect(screen.queryByText(/얼마나 쉬웠나요/)).toBeNull();
    cleanup();

    onSubmit.mockResolvedValueOnce(created(null));
    open();
    await submitAnswer();
    expect(screen.queryByText(/얼마나 쉬웠나요/)).toBeNull();
    expect(screen.queryByRole('button', { name: '평가 보내기' })).toBeNull();
    expect(button('새 응답 작성')).toBeTruthy();
  });

  it('고르고 보내면 그 응답의 표와 함께 보내고 "평가를 보냈어요."로 바뀐다', async () => {
    open();
    await submitAnswer();
    fireEvent.click(screen.getByRole('radio', { name: '쉬움' }));
    fireEvent.change(screen.getByLabelText('어떤 점을 개선하면 좋을까요? (선택)'), {
      target: { value: '  다음 버튼이 더 컸으면 해요 ' },
    });
    fireEvent.click(button('평가 보내기'));

    expect(await screen.findByText('평가를 보냈어요.')).toBeTruthy();
    expect(rate).toHaveBeenCalledWith('f1', 'r1', {
      token: 'token-1',
      ease: 'easy',
      improvement: '다음 버튼이 더 컸으면 해요',
    });
    expect(screen.queryByText(/얼마나 쉬웠나요/)).toBeNull();
    expect(screen.getByText('응답을 제출했어요')).toBeTruthy();
  });

  it('보내기에 실패해도 제출 완료 표시는 그대로이고, 한 줄 안내와 다시 보내기만 둔다', async () => {
    rate.mockRejectedValueOnce(new ApiError(500, { error: 'internal_error' }));
    open();
    await submitAnswer();
    fireEvent.click(screen.getByRole('radio', { name: '매우 쉬움' }));
    fireEvent.click(button('평가 보내기'));

    expect(await screen.findByText('평가를 보내지 못했어요.')).toBeTruthy();
    expect(screen.getByText('응답을 제출했어요')).toBeTruthy();
    expect(screen.queryByText(/얼마나 쉬웠나요/)).toBeNull();
    expect(screen.queryByRole('button', { name: '평가 보내기' })).toBeNull();

    fireEvent.click(button('다시 보내기'));
    expect(await screen.findByText('평가를 보냈어요.')).toBeTruthy();
    expect(rate).toHaveBeenCalledTimes(2);
    expect(rate.mock.calls[1][2]).toEqual({ token: 'token-1', ease: 'very_easy' });
  });

  it('건너뛰면 평가만 사라지고 완료 표시는 남는다', async () => {
    open();
    await submitAnswer();
    fireEvent.click(button('건너뛰기'));

    expect(screen.queryByText(/얼마나 쉬웠나요/)).toBeNull();
    expect(screen.queryByRole('button', { name: '평가 보내기' })).toBeNull();
    expect(screen.getByText('응답을 제출했어요')).toBeTruthy();
    expect(rate).not.toHaveBeenCalled();
  });

  it('두 문항이 모두 비면(공백만 써도) 보내지 않는다', async () => {
    open();
    await submitAnswer();
    expect(button('평가 보내기').disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('어떤 점을 개선하면 좋을까요? (선택)'), {
      target: { value: '   ' },
    });
    fireEvent.click(button('평가 보내기'));
    expect(button('평가 보내기').disabled).toBe(true);
    expect(rate).not.toHaveBeenCalled();
  });
});

describe('제출 내역에서 보기 (로그인 응답자)', () => {
  it('로그인 응답이고 응답 ID를 알면 버튼이 있고 누르면 그 제출 내역으로 간다', async () => {
    open(sink('account'));
    await submitAnswer();
    fireEvent.click(button('제출 내역에서 보기'));
    expect(await screen.findByText('제출 내역 화면')).toBeTruthy();
  });

  it('비로그인 응답이나 응답 ID를 모르면 버튼이 없다', async () => {
    open(sink('browser'));
    await submitAnswer();
    expect(screen.queryByRole('button', { name: '제출 내역에서 보기' })).toBeNull();
    cleanup();

    onSubmit.mockResolvedValueOnce(undefined);
    open(sink('account'));
    await submitAnswer();
    expect(screen.queryByRole('button', { name: '제출 내역에서 보기' })).toBeNull();
  });
});
