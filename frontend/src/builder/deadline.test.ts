import { describe, expect, it } from 'vitest';
import { formatKst, isoToKstInput, kstInputToIso } from './deadline';

describe('마감 변환', () => {
  it('KST 입력을 타임존이 든 ISO 8601로 보낸다', () => {
    const iso = kstInputToIso('2026-11-10T15:30');
    expect(iso).toBe('2026-11-10T15:30:00+09:00');
    expect(new Date(iso ?? '').toISOString()).toBe('2026-11-10T06:30:00.000Z');
  });

  it('비운 입력은 null이다', () => {
    expect(kstInputToIso('')).toBeNull();
  });

  it('UTC 응답을 KST로 보여 준다', () => {
    expect(isoToKstInput('2026-11-10T06:30:00Z')).toBe('2026-11-10T15:30');
    // 날짜가 넘어가는 경우
    expect(isoToKstInput('2026-11-10T20:00:00Z')).toBe('2026-11-11T05:00');
    expect(formatKst('2026-11-10T20:00:00Z')).toContain('11. 11.');
  });

  it('값이 없으면 빈 칸이다', () => {
    expect(isoToKstInput(null)).toBe('');
    expect(isoToKstInput(undefined)).toBe('');
  });
});
