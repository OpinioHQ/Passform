/** 제출 후 수정·취소의 오류 안내(T10 #61, SPEC 5절 194~195행). */
import { ApiError } from '../api/client';

export function changeErrorText(e: unknown): string {
  if (e instanceof ApiError) {
    const code = (e.detail as { error?: unknown } | null)?.error;
    if (code === 'form_closed') return '폼이 마감되어 수정하거나 취소할 수 없어요.';
    if (code === 'history_only') return '폼이나 받은 응답이 삭제되어 조회만 할 수 있어요.';
    if (code === 'not_editable') return '제작자가 응답 수정을 허용하지 않아요.';
    if (code === 'not_cancellable') return '제작자가 응답 취소를 허용하지 않아요.';
    if (e.status === 404) return '이 제출 내역을 찾을 수 없어요.';
    if (e.status === 401) return '로그인이 필요해요. 다시 로그인해 주세요.';
  }
  return '처리하지 못했어요. 잠시 후 다시 시도해 주세요.';
}
