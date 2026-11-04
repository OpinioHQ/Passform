import type { AnswerValue } from '../api/types';

// 저장은 UTC, 화면 표시는 Asia/Seoul(AGENTS 5절).
const dateTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDateTime(value: string | null): string {
  return value ? dateTime.format(new Date(value)) : '없음';
}

export function formatAnswer(value: AnswerValue | null): string {
  if (value === null) {
    return '';
  }
  return Array.isArray(value) ? value.join(', ') : value;
}

export function versionLabel(versions: number[]): string {
  return `폼 버전 ${versions.join('·')}`;
}
