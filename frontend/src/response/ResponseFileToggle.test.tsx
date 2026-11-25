// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ResponsePage } from '../pages/ResponsePage';
import type { FileToSend, PublicForm, Question, ResponseAttachment } from '../types/api';
import * as fileDrafts from './fileDrafts';

/** 문항별 응답 파일 첨부 켜기/끄기. 3문항 중 2번만 켠 폼에서 2번에만 첨부 버튼이 있다. */

function question(id: string, note: string, allow?: boolean): Question {
  return {
    question_id: id,
    template_id: null,
    question_note: note,
    question_type: 'long_answer',
    options: [],
    required: false,
    profile_key: null,
    attachments: [],
    ...(allow === undefined ? {} : { allow_response_files: allow }),
    branch_rules: [],
  };
}

function formOf(
  questions: Question[],
  mode: 'one_question' | 'all_questions' = 'all_questions',
): PublicForm {
  return {
    form_id: 'f1',
    definition_version: 1,
    title: '가상 폼',
    note: '',
    note_attachments: [],
    deadline: null,
    sections: [{ section_id: 's1', title: '기본', questions }],
    display: { default_mode: mode, allowed_modes: ['one_question', 'all_questions'] },
    payment_link: null,
    consent: {
      items: ['문항별 응답 내용'],
      purpose: '목적',
      retention: '보유',
      refusal_notice: '안내',
      ai_summary: null,
    },
  };
}

const onlySecond = (mode?: 'one_question' | 'all_questions') =>
  formOf(
    [question('q1', '첫째'), question('q2', '둘째', true), question('q3', '셋째', false)],
    mode,
  );
const legacy = formOf([question('q1', '첫째'), question('q2', '둘째'), question('q3', '셋째')]);

const pickerOf = (note: string) => screen.queryByLabelText(`${note} 파일 첨부`);
const pdf = new File(['x'], '제출.pdf', { type: 'application/pdf' });
const submit = () => {
  fireEvent.click(screen.getByLabelText(/개인정보 수집·이용에 동의합니다/));
  fireEvent.click(screen.getByRole('button', { name: /답변 제출/ }));
};

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('응답 파일 첨부 켜기/끄기', () => {
  it('전체 질문 보기에서 켠 2번에만 첨부 버튼이 보인다', () => {
    render(
      <ResponsePage
        form={onlySecond()}
        onSubmit={vi.fn()}
        saving={false}
        files={{ storageKey: null }}
      />,
    );
    expect(pickerOf('첫째')).toBeNull();
    expect(pickerOf('둘째')).not.toBeNull();
    expect(pickerOf('셋째')).toBeNull();
  });

  it('한 질문씩 보기에서도 2번 화면에만 첨부 버튼이 보인다', () => {
    render(
      <ResponsePage
        form={onlySecond('one_question')}
        onSubmit={vi.fn()}
        saving={false}
        files={{ storageKey: null }}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /시작하기/ }));
    expect(screen.getByText('첫째')).toBeTruthy();
    expect(pickerOf('첫째')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /다음/ }));
    expect(pickerOf('둘째')).not.toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /다음/ }));
    expect(screen.getByText('셋째')).toBeTruthy();
    expect(pickerOf('셋째')).toBeNull();
  });

  it('필드가 없는 기존 폼은 첨부 버튼 없이 답만 제출된다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={legacy}
        onSubmit={onSubmit}
        saving={false}
        files={{ storageKey: null }}
      />,
    );
    expect(screen.queryAllByLabelText(/파일 첨부$/)).toHaveLength(0);
    submit();
    await screen.findByText('응답을 제출했어요');
    expect(onSubmit.mock.calls[0]).toHaveLength(1);
  });

  it('브라우저에 보관한 파일이 꺼진 문항에 남아 있어도 제출에 넣지 않는다', async () => {
    const stale = new File(['x'], '예전.pdf', { type: 'application/pdf' });
    const stored: FileToSend[] = [
      { question_id: 'q1', file: stale },
      { question_id: 'q2', file: pdf },
    ];
    vi.spyOn(fileDrafts, 'loadFileDraft').mockResolvedValue(stored);
    vi.spyOn(fileDrafts, 'saveFileDraft').mockResolvedValue(undefined);
    vi.spyOn(fileDrafts, 'removeFileDraft').mockResolvedValue(undefined);
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={onlySecond()}
        onSubmit={onSubmit}
        saving={false}
        files={{ storageKey: 'u1:f1' }}
      />,
    );
    await screen.findByText(/제출\.pdf/);
    expect(screen.queryByText(/예전\.pdf/)).toBeNull();
    submit();
    await screen.findByText('응답을 제출했어요');
    expect(onSubmit.mock.calls[0][1]).toEqual({ files: [{ question_id: 'q2', file: pdf }] });
  });

  it('꺼진 문항만 남은 보관 파일은 제출하지 않고 보관 안내도 띄우지 않는다', async () => {
    const stale = new File(['x'], '예전.pdf', { type: 'application/pdf' });
    vi.spyOn(fileDrafts, 'loadFileDraft').mockResolvedValue([{ question_id: 'q1', file: stale }]);
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={onlySecond()}
        onSubmit={onSubmit}
        saving={false}
        files={{ storageKey: 'u1:f1' }}
      />,
    );
    await waitFor(() => expect(fileDrafts.loadFileDraft).toHaveBeenCalled());
    expect(screen.queryByTestId('file-draft-notice')).toBeNull();
    submit();
    await screen.findByText('응답을 제출했어요');
    expect(onSubmit.mock.calls[0]).toHaveLength(1);
  });
});

describe('응답 수정 화면의 켜기/끄기', () => {
  const oldFile: ResponseAttachment = {
    attachment_id: 'a1',
    question_id: 'q1',
    filename: '예전.pdf',
    content_type: 'application/pdf',
    size_bytes: 3,
    available: true,
  };
  const save = () => fireEvent.click(screen.getByRole('button', { name: /수정 저장/ }));

  it('켠 2번에만 첨부 버튼이 있고, 꺼진 1번의 기존 파일은 보이며 뺄 수 있다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={onlySecond()}
        onSubmit={onSubmit}
        saving={false}
        editing={{ initialAnswers: { q1: '답' }, attachments: [oldFile] }}
      />,
    );
    expect(pickerOf('첫째')).toBeNull();
    expect(pickerOf('둘째')).not.toBeNull();
    expect(pickerOf('셋째')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '예전.pdf 빼기' }));
    save();
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0][1]).toEqual({ files: [], keep: [] });
  });

  it('기존 파일을 건드리지 않으면 유지한 채 답만 수정한다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={onlySecond()}
        onSubmit={onSubmit}
        saving={false}
        editing={{ initialAnswers: { q1: '답' }, attachments: [oldFile] }}
      />,
    );
    expect(screen.getByText(/예전\.pdf/)).toBeTruthy();
    save();
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0]).toHaveLength(1);
  });
});
