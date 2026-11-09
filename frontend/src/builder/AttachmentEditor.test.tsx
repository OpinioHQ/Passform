// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '../api/client';
import type { Attachment } from '../types/api';
import { AttachmentEditor } from './AttachmentEditor';

const saved: Attachment = {
  attachment_id: 'a1',
  filename: '안내문.pdf',
  content_type: 'application/pdf',
  size_bytes: 3,
};

function Harness({
  locked = false,
  questionId = 'q1',
}: {
  locked?: boolean;
  questionId?: string | null;
}) {
  const [files, setFiles] = React.useState<Attachment[]>([saved]);
  return (
    <AttachmentEditor
      formId="f1"
      questionId={questionId}
      label="질문 1"
      files={files}
      locked={locked}
      onChange={setFiles}
    />
  );
}

const picker = () => screen.getByLabelText('질문 1 파일 첨부') as HTMLInputElement;
const file = new File(['x'], 'a.png', { type: 'image/png' });

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('제작자 첨부 칸 (AC63)', () => {
  it('파일을 고르면 그 문항으로 올리고 목록에 더한다', async () => {
    const upload = vi.spyOn(api, 'uploadAttachment').mockResolvedValue({
      attachment_id: 'a2',
      filename: 'a.png',
      content_type: 'image/png',
      size_bytes: 1,
    });
    render(<Harness />);
    fireEvent.change(picker(), { target: { files: [file] } });
    await screen.findByText(/a\.png/);
    expect(upload).toHaveBeenCalledWith('f1', 'q1', file);
  });

  it('폼 설명란은 문항 ID 없이 올린다', async () => {
    const upload = vi.spyOn(api, 'uploadAttachment').mockResolvedValue(saved);
    render(<Harness questionId={null} />);
    fireEvent.change(picker(), { target: { files: [file] } });
    await waitFor(() => expect(upload).toHaveBeenCalledWith('f1', null, file));
  });

  it('빼기는 서버가 성공한 뒤에만 목록에서 지운다', async () => {
    const remove = vi.spyOn(api, 'deleteAttachment').mockResolvedValue(undefined);
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '질문 1 안내문.pdf 빼기' }));
    await waitFor(() => expect(screen.queryByText(/안내문\.pdf/)).toBeNull());
    expect(remove).toHaveBeenCalledWith('f1', 'q1', 'a1');
  });

  it.each([
    ['invalid_file_type', 400, /PDF, MP4 파일만/],
    ['storage_quota_exceeded', 409, /저장 공간 500MB/],
    ['storage_unavailable', 503, /파일 저장소에 연결하지 못했어요/],
  ])('%s 오류를 안내하고 목록을 그대로 둔다', async (code, status, text) => {
    vi.spyOn(api, 'uploadAttachment').mockRejectedValue(new ApiError(status, { error: code }));
    render(<Harness />);
    fireEvent.change(picker(), { target: { files: [file] } });
    expect((await screen.findByRole('alert')).textContent).toMatch(text);
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
  });

  it('제거가 실패하면 목록을 유지하고 안내한다', async () => {
    vi.spyOn(api, 'deleteAttachment').mockRejectedValue(
      new ApiError(503, { error: 'storage_unavailable' }),
    );
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '질문 1 안내문.pdf 빼기' }));
    await screen.findByRole('alert');
    expect(screen.getByText(/안내문\.pdf/)).toBeTruthy();
  });

  it('저장하지 않은 변경이 있으면 첨부를 막고 이유를 보인다', () => {
    render(<Harness locked />);
    expect(picker().disabled).toBe(true);
    expect(
      (screen.getByRole('button', { name: '질문 1 안내문.pdf 빼기' }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
    expect(screen.getByText('변경을 저장한 뒤 파일을 첨부할 수 있어요.')).toBeTruthy();
  });
});
