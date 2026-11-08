/**
 * 내 폼함의 탭과 정렬(SPEC 39행, AGENTS.md 5절 "내 폼함"). 서버는 최근 저장순 목록과 `closed`만 주고, 나머지는 화면이 계산한다.
 * 미제출은 `state`가 `saved`인 폼(`has_draft`와 무관), 제출은 `state`가 `submitted`인 폼이다.
 */
import type { SavedFormSummary } from '../types/api';

export type InboxTab = 'all' | 'draft' | 'pending' | 'closed';
export type InboxOrder = 'recent' | 'deadline_soon' | 'deadline_late' | 'title';

export const inboxTabs: ReadonlyArray<readonly [InboxTab, string]> = [
  ['all', '전체'],
  ['draft', '작성 중'],
  ['pending', '미제출'],
  ['closed', '마감됨'],
];

export const inboxOrders: ReadonlyArray<readonly [InboxOrder, string]> = [
  ['recent', '최근 저장순'],
  ['deadline_soon', '마감 임박순'],
  ['deadline_late', '마감 늦은 순'],
  ['title', '제목순'],
];

/** 전체 밖에서 들어가는 탭은 최대 하나다. 열린 제출 폼(초안 없음)은 전체에만 있다. */
export function tabOf(form: SavedFormSummary): Exclude<InboxTab, 'all'> | null {
  if (form.closed) return 'closed';
  if (form.has_draft) return 'draft';
  if (form.state === 'saved') return 'pending';
  return null;
}

export function inTab(form: SavedFormSummary, tab: InboxTab): boolean {
  return tab === 'all' || tabOf(form) === tab;
}

const time = (iso: string | null): number | null => (iso === null ? null : Date.parse(iso));

/** 값이 없으면 뒤로 보낸다. `desc`면 큰 값이 앞이다. */
function byTime(a: string | null, b: string | null, desc: boolean): number {
  const x = time(a);
  const y = time(b);
  if (x === null || y === null) return x === y ? 0 : x === null ? 1 : -1;
  return desc ? y - x : x - y;
}

/** 서버의 UUID 순서(소문자 16진 문자열 순)와 같다. */
function byFormId(a: SavedFormSummary, b: SavedFormSummary): number {
  return a.form_id < b.form_id ? -1 : a.form_id > b.form_id ? 1 : 0;
}

/** 마감순의 무리: 열린 미제출(마감 있음) → 열린 미제출(마감 없음) → 닫힌 미제출 → 제출. */
function deadlineGroup(form: SavedFormSummary): number {
  if (form.state === 'submitted') return 3;
  if (form.closed) return 2;
  return form.deadline === null ? 1 : 0;
}

function byDeadline(a: SavedFormSummary, b: SavedFormSummary, soonFirst: boolean): number {
  const group = deadlineGroup(a) - deadlineGroup(b);
  if (group !== 0) return group;
  switch (deadlineGroup(a)) {
    case 0:
      return byTime(a.deadline, b.deadline, !soonFirst);
    case 2:
      return byTime(a.deadline, b.deadline, true);
    case 3:
      return byTime(a.submitted_at, b.submitted_at, true);
    default:
      return 0;
  }
}

const titleCollator = new Intl.Collator('ko');

export function sortInbox(forms: SavedFormSummary[], order: InboxOrder): SavedFormSummary[] {
  const compare = (a: SavedFormSummary, b: SavedFormSummary): number => {
    switch (order) {
      case 'recent':
        return byTime(a.saved_at, b.saved_at, true);
      case 'deadline_soon':
        return byDeadline(a, b, true);
      case 'deadline_late':
        return byDeadline(a, b, false);
      case 'title':
        return titleCollator.compare(a.title, b.title);
    }
  };
  return [...forms].sort((a, b) => compare(a, b) || byFormId(a, b));
}
