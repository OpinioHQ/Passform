import React from 'react';
import { api } from '../api/client';
import type { Attachment } from '../types/api';
import { friendlyError } from './messages';

/** 제작자가 올릴 수 있는 형식(SPEC 219행). 최종 판정은 서버가 내용을 보고 한다. */
const ACCEPT = '.jpg,.jpeg,.png,.gif,.webp,.pdf,.mp4';

/**
 * 폼 설명란·문항 첨부. 올리기와 빼기는 그 자리에서 서버에 반영되고, 저장 버튼을 기다리지 않는다.
 * 저장하지 않은 변경이 있으면 막는다. 아직 서버에 없는 문항이거나, 배포 폼이면 편집 전 내용으로 새 버전이 되기 때문이다.
 */
export function AttachmentEditor({
  formId,
  questionId,
  label,
  files,
  locked,
  onChange,
}: {
  formId: string;
  questionId: string | null;
  label: string;
  files: Attachment[];
  locked: boolean;
  onChange: (change: (files: Attachment[]) => Attachment[]) => void;
}) {
  const [busy, setBusy] = React.useState(false);
  const [failure, setFailure] = React.useState('');

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setFailure('');
    try {
      await action();
    } catch (error) {
      setFailure(friendlyError(error));
    } finally {
      setBusy(false);
    }
  }

  function pick(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // 같은 파일을 다시 고를 수 있게 비운다.
    event.target.value = '';
    if (!file) return;
    void run(async () => {
      const added = await api.uploadAttachment(formId, questionId, file);
      onChange((current) => [...current, added]);
    });
  }

  function remove(file: Attachment) {
    void run(async () => {
      await api.deleteAttachment(formId, questionId, file.attachment_id);
      onChange((current) => current.filter((f) => f.attachment_id !== file.attachment_id));
    });
  }

  return (
    <div className="builder-field attachment-editor">
      <span>첨부 파일 (이미지, PDF, MP4)</span>
      {files.length > 0 && (
        <ul className="attachment-list">
          {files.map((file) => (
            <li key={file.attachment_id}>
              {file.filename}{' '}
              <button
                type="button"
                className="btn btn-ghost"
                aria-label={`${label} ${file.filename} 빼기`}
                disabled={busy || locked}
                onClick={() => remove(file)}
              >
                빼기
              </button>
            </li>
          ))}
        </ul>
      )}
      <input
        type="file"
        accept={ACCEPT}
        aria-label={`${label} 파일 첨부`}
        disabled={busy || locked}
        onChange={pick}
      />
      {busy && (
        <small className="muted" role="status">
          파일을 처리하는 중이에요.
        </small>
      )}
      {locked && <small className="muted">변경을 저장한 뒤 파일을 첨부할 수 있어요.</small>}
      {failure && (
        <small className="error" role="alert">
          {failure}
        </small>
      )}
    </div>
  );
}
