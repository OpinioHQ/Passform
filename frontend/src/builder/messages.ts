import { ApiError } from '../api/client';
import type { FormStatus } from '../types/api';

const BY_CODE: Record<string, string> = {
  consent_template_required: '동의 템플릿을 골라 주세요. 동의 템플릿이 없으면 배포할 수 없어요.',
  invalid_payment_link: '송금 링크는 https://로 시작하는 주소만 쓸 수 있어요.',
  invalid_branch:
    '분기 설정에 맞지 않는 문항이나 선택지가 있어요. 문항 순서와 선택지를 확인해 주세요.',
  invalid_display_mode:
    '지금 폼 구성에서 쓸 수 없는 시작 방식이에요. 시작 방식을 다시 골라 주세요.',
  ai_summary_locked: 'AI 요약 사용 여부는 첫 배포 뒤에는 바꿀 수 없어요.',
  invalid_transition: '지금 상태에서는 할 수 없는 변경이에요. 새로고침해 상태를 확인해 주세요.',
  response_limit_reached:
    '선착순 인원이 이미 받은 응답 수보다 많아야 해요. 인원을 늘린 뒤 다시 시도해 주세요.',
  schema_invalid: '입력한 내용 중 저장할 수 없는 값이 있어요. 문항과 설정을 확인해 주세요.',
};

function errorCode(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  const { detail } = error;
  if (typeof detail !== 'object' || detail === null || !('error' in detail)) return null;
  return typeof detail.error === 'string' ? detail.error : null;
}

/** 오류 코드 원문을 화면에 쓰지 않는다. */
export function friendlyError(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return '로그인이 끊겼어요. 다시 로그인한 뒤 시도해 주세요.';
  }
  return BY_CODE[errorCode(error) ?? ''] ?? '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
}

export const isFormClosedError = (error: unknown) => errorCode(error) === 'form_closed';

/** 마감이 지난 폼은 `open`이어도 닫힌 것으로 보여 준다. */
export function statusLabel(status: FormStatus, deadline: string | null | undefined, now: number) {
  if (status === 'draft') return '초안';
  if (status === 'closed') return '마감';
  return deadline && Date.parse(deadline) <= now ? '마감' : '배포 중';
}
