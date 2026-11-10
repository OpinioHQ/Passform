// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { ResponsePage } from '../pages/ResponsePage';
import type { FileToSend, PublicForm, ResponseAttachment } from '../types/api';
import * as fileDrafts from './fileDrafts';

const form: PublicForm = {
  form_id: 'f1',
  definition_version: 1,
  title: '가상 폼',
  note: '',
  note_attachments: [],
  deadline: null,
  sections: [
    {
      section_id: 's1',
      title: '기본',
      questions: [
        {
          question_id: 'q1',
          template_id: null,
          question_note: '하고 싶은 말',
          question_type: 'long_answer',
          options: [],
          required: false,
          profile_key: null,
          attachments: [],
          branch_rules: [],
        },
      ],
    },
  ],
  display: { default_mode: 'all_questions', allowed_modes: ['all_questions'] },
  payment_link: null,
  consent: {
    items: ['문항별 응답 내용'],
    purpose: '목적',
    retention: '보유',
    refusal_notice: '안내',
    ai_summary: null,
  },
};

const pdf = new File(['x'], '제출.pdf', { type: 'application/pdf' });
const picker = () => screen.getByLabelText('하고 싶은 말 파일 첨부') as HTMLInputElement;
const pick = (file: File) => fireEvent.change(picker(), { target: { files: [file] } });
const submit = () => {
  fireEvent.click(screen.getByLabelText(/개인정보 수집·이용에 동의합니다/));
  fireEvent.click(screen.getByRole('button', { name: /답변 제출/ }));
};

function bigFile(name: string, size: number): File {
  const file = new File(['x'], name);
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('응답 파일 첨부 (AC64, AC82)', () => {
  it('고른 파일을 그 문항으로 제출하고 성공하면 브라우저 보관분을 지운다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const remove = vi.spyOn(fileDrafts, 'removeFileDraft').mockResolvedValue(undefined);
    vi.spyOn(fileDrafts, 'saveFileDraft').mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={form}
        onSubmit={onSubmit}
        saving={false}
        files={{ storageKey: 'u1:f1' }}
      />,
    );
    pick(pdf);
    await screen.findByText(/제출\.pdf/);
    submit();
    await screen.findByText('응답을 제출했어요');
    expect(onSubmit.mock.calls[0][1]).toEqual({ files: [{ question_id: 'q1', file: pdf }] });
    expect(remove).toHaveBeenCalledWith('u1:f1');
  });

  it('파일이 없으면 기존처럼 답만 보낸다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ResponsePage form={form} onSubmit={onSubmit} saving={false} files={{ storageKey: null }} />,
    );
    submit();
    await screen.findByText('응답을 제출했어요');
    expect(onSubmit.mock.calls[0]).toHaveLength(1);
  });

  it('같은 브라우저에 보관한 파일을 복원하고 빼면 보관분도 바꾼다', async () => {
    const stored: FileToSend[] = [{ question_id: 'q1', file: pdf }];
    const load = vi.spyOn(fileDrafts, 'loadFileDraft').mockResolvedValue(stored);
    const save = vi.spyOn(fileDrafts, 'saveFileDraft').mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={form}
        onSubmit={vi.fn()}
        saving={false}
        files={{ storageKey: 'u1:f1' }}
      />,
    );
    await screen.findByText(/제출\.pdf/);
    expect(load).toHaveBeenCalledWith('u1:f1');
    fireEvent.click(screen.getByRole('button', { name: '제출.pdf 빼기' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith('u1:f1', []));
    expect(screen.queryByText(/제출\.pdf/)).toBeNull();
  });

  it('계정과 폼별로 다른 키를 쓴다', () => {
    expect(fileDrafts.fileDraftKey('u1', 'f1')).not.toBe(fileDrafts.fileDraftKey('u2', 'f1'));
    expect(fileDrafts.fileDraftKey('u1', 'f1')).not.toBe(fileDrafts.fileDraftKey('u1', 'f2'));
  });

  it('이어 쓰는데 이 브라우저에 파일이 없으면 다시 고르라고 안내한다', async () => {
    vi.spyOn(fileDrafts, 'loadFileDraft').mockResolvedValue([]);
    const sink = {
      kind: 'account' as const,
      save: vi.fn().mockResolvedValue(undefined),
      clear: vi.fn().mockResolvedValue(undefined),
      draftResponseId: () => 'd1',
      submitted: vi.fn(),
    };
    render(
      <ResponsePage
        form={form}
        onSubmit={vi.fn()}
        saving={false}
        draft={{ sink, initialAnswers: { q1: '이어 쓰는 답' } }}
        files={{ storageKey: 'u1:f1' }}
      />,
    );
    expect((await screen.findByTestId('file-draft-notice')).textContent).toContain(
      '다시 골라 주세요',
    );
  });

  it('한 문항의 합계가 25MB를 넘으면 추가하지 않고 그 문항에 안내한다', async () => {
    vi.spyOn(fileDrafts, 'saveFileDraft').mockResolvedValue(undefined);
    render(
      <ResponsePage form={form} onSubmit={vi.fn()} saving={false} files={{ storageKey: null }} />,
    );
    pick(bigFile('a.zip', 20_000_000));
    await screen.findByText(/a\.zip/);
    pick(bigFile('b.zip', 6_000_000));
    expect((await screen.findByRole('alert')).textContent).toContain('25MB까지');
    expect(screen.queryByText(/b\.zip/)).toBeNull();
  });

  it.each([
    ['invalid_file_type', 400, /PDF, ZIP/],
    ['file_too_large', 413, /25MB까지/],
    ['storage_quota_exceeded', 409, /저장 공간이 부족/],
    ['storage_unavailable', 503, /파일 저장소에 연결하지 못했어요/],
  ])('%s면 안내하고 파일을 그대로 둔다', async (code, status, text) => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(status, { error: code }));
    const remove = vi.spyOn(fileDrafts, 'removeFileDraft').mockResolvedValue(undefined);
    vi.spyOn(fileDrafts, 'saveFileDraft').mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={form}
        onSubmit={onSubmit}
        saving={false}
        files={{ storageKey: 'u1:f1' }}
      />,
    );
    pick(pdf);
    await screen.findByText(/제출\.pdf/);
    submit();
    expect((await screen.findByRole('alert')).textContent).toMatch(text);
    expect(screen.getByText(/제출\.pdf/)).toBeTruthy();
    expect(remove).not.toHaveBeenCalled();
  });

  it('미리보기와 파일을 받지 않는 화면에는 첨부 칸이 없다', () => {
    render(<ResponsePage form={form} onSubmit={vi.fn()} saving={false} preview />);
    expect(screen.queryByLabelText('하고 싶은 말 파일 첨부')).toBeNull();
    cleanup();
    render(<ResponsePage form={form} onSubmit={vi.fn()} saving={false} />);
    expect(screen.queryByLabelText('하고 싶은 말 파일 첨부')).toBeNull();
  });
});

describe('제출한 답 수정의 파일', () => {
  const existing: ResponseAttachment = {
    attachment_id: 'a1',
    question_id: 'q1',
    filename: '기존.pdf',
    content_type: 'application/pdf',
    size_bytes: 3,
    available: true,
  };
  const save = () => fireEvent.click(screen.getByRole('button', { name: /수정 저장/ }));

  it('파일을 건드리지 않으면 첨부 목록을 보내지 않는다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={form}
        onSubmit={onSubmit}
        saving={false}
        editing={{ initialAnswers: { q1: '답' }, attachments: [existing] }}
      />,
    );
    save();
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0]).toHaveLength(1);
  });

  it('기존 파일을 빼면 남길 목록을 보내고 새 파일과 함께 교체한다', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={form}
        onSubmit={onSubmit}
        saving={false}
        editing={{ initialAnswers: { q1: '답' }, attachments: [existing] }}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '기존.pdf 빼기' }));
    pick(pdf);
    await screen.findByText(/제출\.pdf/);
    save();
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0][1]).toEqual({
      files: [{ question_id: 'q1', file: pdf }],
      keep: [],
    });
  });

  it('삭제된 파일은 수정 칸에 보이지 않는다', () => {
    render(
      <ResponsePage
        form={form}
        onSubmit={vi.fn()}
        saving={false}
        editing={{
          initialAnswers: { q1: '답' },
          attachments: [{ ...existing, available: false }],
        }}
      />,
    );
    expect(screen.queryByText(/기존\.pdf/)).toBeNull();
  });
});
