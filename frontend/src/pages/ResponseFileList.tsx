import type { ResponseAttachment, ResponseContentItem } from '../types/api';

/** 내려받을 주소를 만든다. `null`이면 링크 없이 이름만 보인다(가짜 데이터 모드, 폼 ID 모름). */
export type FileLink = ((attachmentId: string) => string) | null;

/** 그 문항에 첨부된 파일. */
export function filesOfQuestion(files: ResponseAttachment[], questionId: string) {
  return files.filter((file) => file.question_id === questionId);
}

/** 답 없이 파일만 낸 문항의 파일. 놓치지 않게 답 목록 아래에 따로 보인다. */
export function filesWithoutAnswer(content: ResponseContentItem[], files: ResponseAttachment[]) {
  const answered = new Set(content.map((item) => item.question_id));
  return files.filter((file) => !answered.has(file.question_id));
}

/**
 * 제작자가 보는 응답 첨부(SPEC 5절 198·222·224행). 응답 상세와 결과 화면 목록이 같이 쓴다.
 * 지운 원본은 이름과 "(삭제된 파일)"만 보이고 링크가 없다.
 */
export function ResponseFileList({
  files,
  linkOf,
}: {
  files: ResponseAttachment[];
  linkOf: FileLink;
}) {
  if (files.length === 0) return null;
  return (
    <ul className="small muted attachment-list">
      {files.map((file) => (
        <li key={file.attachment_id}>
          {file.available && linkOf ? (
            <a href={linkOf(file.attachment_id)} download={file.filename}>
              {file.filename}
            </a>
          ) : (
            file.filename
          )}
          {!file.available && ' (삭제된 파일)'}
        </li>
      ))}
    </ul>
  );
}
