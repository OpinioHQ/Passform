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
  canAdd = true,
}: {
  label: string;
  /** 수정 화면에서 남겨 둔 제출 파일. */
  existing: ResponseAttachment[];
  picked: FileToSend[];
  onPick: (files: File[]) => void;
  onRemovePicked: (item: FileToSend) => void;
  onRemoveExisting: (attachmentId: string) => void;
  /** 제작자가 첨부를 켠 문항만 새 파일을 고를 수 있다. 꺼진 문항은 이미 제출한 파일을 보고 뺄 수만 있다. */
  canAdd?: boolean;
}) {
  return (
    <div className="response-files">
      {/* 고른 파일은 왼쪽부터 나란히, 파일 첨부 버튼은 오른쪽 끝에 둔다(폭이 모자라면 줄바꿈). */}
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
      {canAdd && (
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
      )}
    </div>
  );
}
