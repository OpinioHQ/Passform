import { describe, expect, it } from 'vitest';
import type { SavedFormSummary } from '../types/api';
import { inTab, sortInbox, tabOf, type InboxTab } from './inboxView';

function form(id: string, patch: Partial<SavedFormSummary> = {}): SavedFormSummary {
  return {
    form_id: id,
    title: id,
    deadline: null,
    state: 'saved',
    has_draft: false,
    saved_at: '2026-11-01T00:00:00Z',
    submitted_at: null,
    closed: false,
    ...patch,
  };
}

const ids = (forms: SavedFormSummary[]) => forms.map((f) => f.form_id);
const tabs: InboxTab[] = ['all', 'draft', 'pending', 'closed'];
const tabsOf = (f: SavedFormSummary) => tabs.filter((t) => inTab(f, t));

describe('내 폼함 탭 (SPEC 39행)', () => {
  it('작성 중이면서 제출된 폼은 작성 중 탭에 들어간다', () => {
    const f = form('a', {
      state: 'submitted',
      has_draft: true,
      submitted_at: '2026-11-01T00:00:00Z',
    });
    expect(tabsOf(f)).toEqual(['all', 'draft']);
  });

  it('마감됐는데 작성 중인 폼은 마감됨 탭에만 들어간다', () => {
    expect(tabsOf(form('a', { has_draft: true, closed: true }))).toEqual(['all', 'closed']);
  });

  it('마감된 제출 폼은 마감됨 탭에 들어간다', () => {
    expect(tabsOf(form('a', { state: 'submitted', closed: true }))).toEqual(['all', 'closed']);
  });

  it('저장만 한 열린 폼은 미제출 탭에 들어간다', () => {
    expect(tabsOf(form('a'))).toEqual(['all', 'pending']);
  });

  it('열린 제출 폼(초안 없음)은 전체에만 있다', () => {
    const f = form('a', { state: 'submitted', submitted_at: '2026-11-01T00:00:00Z' });
    expect(tabOf(f)).toBeNull();
    expect(tabsOf(f)).toEqual(['all']);
  });
});

describe('내 폼함 정렬 (AGENTS.md 5절)', () => {
  const openSoon = form('f4', {
    deadline: '2026-11-10T00:00:00Z',
    saved_at: '2026-11-01T00:00:00Z',
  });
  const openLate = form('f3', { deadline: '2026-11-20T00:00:00Z', has_draft: true });
  const openNone = form('f2', { title: '가나다' });
  const closedOld = form('f5', { deadline: '2026-10-01T00:00:00Z', closed: true });
  const closedNew = form('f6', { deadline: '2026-10-20T00:00:00Z', closed: true });
  const closedNone = form('f7', { closed: true });
  const submittedOld = form('f8', { state: 'submitted', submitted_at: '2026-10-02T00:00:00Z' });
  const submittedNew = form('f9', {
    state: 'submitted',
    submitted_at: '2026-10-25T00:00:00Z',
    deadline: '2026-11-11T00:00:00Z',
  });
  const all = [
    submittedOld,
    closedNone,
    openNone,
    closedOld,
    submittedNew,
    openLate,
    closedNew,
    openSoon,
  ];

  it('마감 임박순: 열린 미제출 마감 오름차순 → 마감 없음 → 닫힌 미제출 마감 내림차순(없으면 뒤) → 제출 최근순', () => {
    expect(ids(sortInbox(all, 'deadline_soon'))).toEqual([
      'f4',
      'f3',
      'f2',
      'f6',
      'f5',
      'f7',
      'f9',
      'f8',
    ]);
  });

  it('마감 늦은 순: 열린 미제출만 마감 내림차순이고 이후는 같다', () => {
    expect(ids(sortInbox(all, 'deadline_late'))).toEqual([
      'f3',
      'f4',
      'f2',
      'f6',
      'f5',
      'f7',
      'f9',
      'f8',
    ]);
  });

  it('제목순은 가나다순이다', () => {
    const titled = [
      form('x1', { title: '하늘' }),
      form('x2', { title: '가을' }),
      form('x3', { title: '나무' }),
    ];
    expect(ids(sortInbox(titled, 'title'))).toEqual(['x2', 'x3', 'x1']);
  });

  it('최근 저장순은 saved_at 내림차순이다', () => {
    const saved = [
      form('a', { saved_at: '2026-11-01T00:00:00Z' }),
      form('b', { saved_at: '2026-11-03T00:00:00Z' }),
    ];
    expect(ids(sortInbox(saved, 'recent'))).toEqual(['b', 'a']);
  });

  it.each(['recent', 'deadline_soon', 'deadline_late', 'title'] as const)(
    '%s 에서 값이 모두 같으면 form_id 오름차순이다',
    (order) => {
      const same = { title: '같은 제목', deadline: '2026-11-10T00:00:00Z' };
      const tied = [form('c9', same), form('0a', same), form('b1', same)];
      expect(ids(sortInbox(tied, order))).toEqual(['0a', 'b1', 'c9']);
    },
  );
});
