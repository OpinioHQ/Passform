/** 시간은 UTC로 저장하고 Asia/Seoul로 보여 준다. 대한민국은 일광 절약 시간이 없어 +09:00으로 고정이다. */
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

/** 입력 칸(`datetime-local`)의 한국 시각을 타임존이 든 ISO 8601로 바꾼다. 비었으면 `null`이다. */
export function kstInputToIso(local: string): string | null {
  if (!local) return null;
  const withSeconds = local.length === 16 ? `${local}:00` : local;
  return `${withSeconds}+09:00`;
}

/** 서버 값을 입력 칸의 한국 시각으로 바꾼다. */
export function isoToKstInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const time = Date.parse(iso);
  if (Number.isNaN(time)) return '';
  return new Date(time + KST_OFFSET_MS).toISOString().slice(0, 16);
}

/** 화면 표시용. 예: `2026. 11. 10. 오후 3:30` */
export function formatKst(iso: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(iso));
}
