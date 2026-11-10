import type { FileToSend, ResponseAttachment } from '../types/api';

/** 응답자가 올릴 수 있는 형식(SPEC 219행). 최종 판정은 서버가 내용을 보고 한다. */
const ACCEPT = '.jpg,.jpeg,.png,.gif,.webp,.pdf,.zip,.ppt,.pptx,.doc,.docx,.hwp,.hwpx';

export const QUESTION_FILE_LIMIT_BYTES = 25_000_000;

/** 문항 아래의 작은 첨부 칸. 파일이 없으면 버튼만 보인다(사람 결정). */
export function ResponseFilePicker({
  label,
  existing,
  picked,
  onPick,
  onRemovePicked,
  onRemoveExisting,
}: {
  label: string;
  /** 수정 화면에서 남겨 둔 제출 파일. */
  existing: ResponseAttachment[];
  picked: FileToSend[];
  onPick: (files: File[]) => void;
  onRemovePicked: (item: FileToSend) => void;
  onRemoveExisting: (attachmentId: string) => void;
}) {
  return (
    <div className="response-files">
      {(existing.length > 0 || picked.length > 0) && (
        <ul className="attachment-list">
          {existing.map((file) => (
            <li key={file.attachment_id}>
              {file.filename}{' '}
              <button
                type="button"
                className="btn btn-ghost"
                aria-label={`${file.filename} 빼기`}
                onClick={() => onRemoveExisting(file.attachment_id)}
              >
                빼기
              </button>
            </li>
          ))}
          {picked.map((item, index) => (
            <li key={`${item.file.name}-${index}`}>
              {item.file.name}{' '}
              <button
                type="button"
                className="btn btn-ghost"
                aria-label={`${item.file.name} 빼기`}
                onClick={() => onRemovePicked(item)}
              >
                빼기
              </button>
            </li>
          ))}
        </ul>
      )}
      <label className="btn btn-ghost response-file-button">
        파일 첨부
        <input
          type="file"
          multiple
          accept={ACCEPT}
          aria-label={`${label} 파일 첨부`}
          onChange={(event) => {
            const files = Array.from(event.target.files ?? []);
            // 같은 파일을 다시 고를 수 있게 비운다.
            event.target.value = '';
            if (files.length > 0) onPick(files);
          }}
        />
      </label>
    </div>
  );
}
