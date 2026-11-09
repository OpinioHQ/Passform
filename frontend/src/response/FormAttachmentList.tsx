import { attachmentUrl } from '../api/client';
import type { Attachment } from '../types/api';

/**
 * 제작자가 붙인 파일을 응답 화면에 보인다. 이미지만 바로 보이고 PDF·MP4는 다운로드 링크만 준다(SPEC 220행, AC63).
 * `formId`가 비어 있으면(가짜 데이터 모드) 받을 곳이 없어 이름만 보인다.
 */
export function FormAttachmentList({
  formId,
  questionId,
  files,
}: {
  formId: string;
  questionId: string | null;
  files: Attachment[];
}) {
  if (files.length === 0) return null;
  return (
    <ul className="attachment-list">
      {files.map((file) => {
        const url = formId ? attachmentUrl(formId, questionId, file.attachment_id) : null;
        return (
          <li key={file.attachment_id}>
            {url === null ? (
              file.filename
            ) : file.content_type.startsWith('image/') ? (
              <img className="attachment-image" src={url} alt={file.filename} loading="lazy" />
            ) : (
              <a href={url} download={file.filename}>
                {file.filename} 내려받기
              </a>
            )}
          </li>
        );
      })}
    </ul>
  );
}
