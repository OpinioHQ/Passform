/** 제출 후 수정·취소의 오류 안내(T10 #61, SPEC 5절 194~195행). */
import { ApiError } from '../api/client';

/** 응답 파일 오류 안내. 답과 파일은 화면에 그대로 있다. */
const FILE_ERRORS: Record<string, string> = {
  invalid_file_type: '이미지, PDF, ZIP, PPT, DOC, HWP 파일만 첨부할 수 있어요.',
  file_too_large: '한 문항에 첨부한 파일의 합계는 25MB까지예요.',
  storage_quota_exceeded:
    '폼을 만든 사람의 저장 공간이 부족해 저장하지 못했어요. 폼을 보낸 사람에게 알려 주세요.',
  storage_unavailable:
    '파일 저장소에 연결하지 못했어요. 쓴 답과 파일은 그대로 있으니 잠시 후 다시 시도해 주세요.',
};

export function fileErrorText(code: string): string | null {
  return FILE_ERRORS[code] ?? null;
}

export function changeErrorText(e: unknown): string {
  if (e instanceof ApiError) {
    const code = (e.detail as { error?: unknown } | null)?.error;
    if (code === 'form_closed') return '폼이 마감되어 수정하거나 취소할 수 없어요.';
    if (code === 'history_only') return '폼이나 받은 응답이 삭제되어 조회만 할 수 있어요.';
    if (code === 'not_editable') return '제작자가 응답 수정을 허용하지 않아요.';
    if (code === 'not_cancellable') return '제작자가 응답 취소를 허용하지 않아요.';
    if (typeof code === 'string' && fileErrorText(code)) return fileErrorText(code) as string;
    if (e.status === 404) return '이 제출 내역을 찾을 수 없어요.';
    if (e.status === 401) return '로그인이 필요해요. 다시 로그인해 주세요.';
  }
  return '처리하지 못했어요. 잠시 후 다시 시도해 주세요.';
}
