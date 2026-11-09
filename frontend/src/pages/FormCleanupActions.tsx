import React from 'react';
import { api } from '../api/client';
import { friendlyError } from '../builder/messages';
import type { ExportFormat } from '../types/api';
import { exportFilename, saveFile } from './ExportDialog';

export type CleanupKind = 'responses' | 'attachments' | 'form';

const FORMATS: Array<{ value: ExportFormat; label: string }> = [
  { value: 'csv', label: 'CSV' },
  { value: 'json', label: 'JSON' },
  { value: 'yaml', label: 'YAML' },
];

const CONFIRM_TEXT: Record<Exclude<CleanupKind, 'form'>, string> = {
  responses:
    '받은 응답과 응답 첨부를 모두 지워요. 되돌릴 수 없어요. 폼과 공유 링크는 그대로이고, 로그인한 응답자의 제출 이력에는 글자만 남아요.',
  attachments:
    '응답에 첨부된 파일을 모두 지워요. 되돌릴 수 없어요. 답변 내용과 응답 수는 그대로예요.',
};

/**
 * 폼 카드의 저장 공간 정리(SPEC 5절 44행, 173행, AC66, AC67, AC70). 특정 응답만 고르는 기능은 없다.
 * 폼 삭제는 받은 응답이 있으면 먼저 내보낼지 묻는다. 내보내기가 실패하면 삭제하지 않는다.
 */
export function FormCleanupActions({
  formId,
  name,
  responseCount,
  onCleaned,
}: {
  formId: string;
  name: string;
  responseCount: number;
  onCleaned: (kind: CleanupKind) => void;
}) {
  const [open, setOpen] = React.useState<CleanupKind | null>(null);
  const [format, setFormat] = React.useState<ExportFormat>('csv');
  const [busy, setBusy] = React.useState(false);
  const [failure, setFailure] = React.useState('');

  function choose(kind: CleanupKind | null) {
    setOpen(kind);
    setFailure('');
  }

  async function run(kind: CleanupKind, action: () => Promise<void>) {
    setBusy(true);
    setFailure('');
    try {
      await action();
      setOpen(null);
      onCleaned(kind);
    } catch (error) {
      setFailure(`삭제하지 못했어요. ${friendlyError(error)}`);
    } finally {
      setBusy(false);
    }
  }

  const removeResponses = () => run('responses', () => api.deleteReceivedResponses(formId));
  const removeAttachments = () => run('attachments', () => api.deleteResponseAttachments(formId));
  const removeForm = () => run('form', () => api.deleteForm(formId));

  // 파일 저장까지 성공해야 삭제한다(AC67).
  async function exportThenRemove() {
    setBusy(true);
    setFailure('');
    try {
      const result = await api.exportForm(formId, { format });
      if (result.status !== 'file') throw new Error('파일을 만들지 못했어요');
      saveFile(result.file, exportFilename(format));
    } catch {
      setFailure('내보내지 못해서 폼을 삭제하지 않았어요. 잠시 뒤 다시 시도해 주세요.');
      setBusy(false);
      return;
    }
    await removeForm();
  }

  const cancel = (
    <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => choose(null)}>
      취소
    </button>
  );

  return (
    <div className="form-cleanup">
      <div className="button-row">
        <button
          type="button"
          className="btn btn-ghost"
          aria-label={`${name} 응답 전체 삭제`}
          disabled={busy || responseCount === 0}
          onClick={() => choose('responses')}
        >
          응답 전체 삭제
        </button>
        <button
          type="button"
          className="btn btn-ghost"
          aria-label={`${name} 응답 첨부 전체 삭제`}
          disabled={busy || responseCount === 0}
          onClick={() => choose('attachments')}
        >
          응답 첨부 전체 삭제
        </button>
        <button
          type="button"
          className="btn btn-ghost"
          aria-label={`${name} 폼 삭제`}
          disabled={busy}
          onClick={() => choose('form')}
        >
          폼 삭제
        </button>
      </div>

      {(open === 'responses' || open === 'attachments') && (
        <div className="form-cleanup-confirm" role="group" aria-label={`${name} 삭제 확인`}>
          <p className="small">{CONFIRM_TEXT[open]}</p>
          <div className="button-row">
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={() => void (open === 'responses' ? removeResponses() : removeAttachments())}
            >
              {busy ? '삭제 중…' : '삭제'}
            </button>
            {cancel}
          </div>
        </div>
      )}

      {open === 'form' && responseCount === 0 && (
        <div className="form-cleanup-confirm" role="group" aria-label={`${name} 삭제 확인`}>
          <p className="small">폼을 삭제할까요? 되돌릴 수 없어요.</p>
          <div className="button-row">
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={() => void removeForm()}
            >
              {busy ? '삭제 중…' : '삭제'}
            </button>
            {cancel}
          </div>
        </div>
      )}

      {open === 'form' && responseCount > 0 && (
        <div className="form-cleanup-confirm" role="group" aria-label={`${name} 삭제 확인`}>
          <p className="small">
            받은 응답 {responseCount}건을 삭제 전에 파일로 내보낼까요? 폼을 삭제하면 되돌릴 수
            없어요.
          </p>
          <p className="small" data-testid="no-attachment-notice">
            내보내는 파일에는 첨부 파일 원본이 포함되지 않아요.
          </p>
          <div className="option-stack export-formats" role="radiogroup" aria-label="파일 형식">
            {FORMATS.map((option) => (
              <label
                className={`option ${format === option.value ? 'checked' : ''}`}
                key={option.value}
              >
                <input
                  type="radio"
                  name={`cleanup-format-${formId}`}
                  value={option.value}
                  checked={format === option.value}
                  onChange={() => setFormat(option.value)}
                />
                {option.label}
              </label>
            ))}
          </div>
          <div className="button-row">
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={() => void exportThenRemove()}
            >
              {busy ? '처리 중…' : '내보내고 삭제'}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => void removeForm()}
            >
              내보내지 않고 삭제
            </button>
            {cancel}
          </div>
        </div>
      )}

      {failure && (
        <p className="error" role="alert">
          {failure}
        </p>
      )}
    </div>
  );
}
