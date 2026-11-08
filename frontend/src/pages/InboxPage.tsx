import React from 'react';
import { Link } from 'react-router-dom';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import {
  inTab,
  inboxOrders,
  inboxTabs,
  sortInbox,
  tabOf,
  type InboxOrder,
  type InboxTab,
} from '../inbox/inboxView';
import { paths } from '../routes/paths';
import type { SavedFormSummary } from '../types/api';
import { deadlineText } from './ResponsePage';

export type InboxProblem = 'login' | 'failed';

interface Props {
  /** `null`이면 불러오는 중이다. */
  forms: SavedFormSummary[] | null;
  problem: InboxProblem | null;
  onOpen: (formId: string) => void;
}

const badges: Record<
  NonNullable<ReturnType<typeof tabOf>> | 'submitted',
  [string, 'blue' | 'amber' | 'neutral' | 'green']
> = {
  draft: ['작성 중', 'blue'],
  pending: ['미제출', 'amber'],
  closed: ['마감됨', 'neutral'],
  submitted: ['제출함', 'green'],
};

/** 내 폼함(E5 #43). 목록은 `GET /me/forms`, 탭과 정렬은 화면이 계산한다(SPEC 39행, AGENTS.md 5절). */
export function InboxPage({ forms, problem, onOpen }: Props) {
  const [tab, setTab] = React.useState<InboxTab>('all');
  const [order, setOrder] = React.useState<InboxOrder>('recent');
  const [filter, setFilter] = React.useState('');
  const all = forms ?? [];
  const shown = sortInbox(
    all.filter((f) => inTab(f, tab) && f.title.toLowerCase().includes(filter.toLowerCase())),
    order,
  );
  return (
    <div className="page-shell">
      <Heading
        eyebrow="내 워크스페이스  /  내 폼함"
        title="내 폼함"
        desc="저장한 폼과 제출한 폼을 한곳에서 확인하세요."
      />
      <div className="tab-bar" role="tablist" aria-label="내 폼함 탭">
        {inboxTabs.map(([key, label]) => (
          <button
            key={key}
            role="tab"
            aria-selected={tab === key}
            onClick={() => setTab(key)}
            className={tab === key ? 'selected' : ''}
          >
            {label} <span>{all.filter((f) => inTab(f, key)).length}</span>
          </button>
        ))}
      </div>
      <div className="list-tools">
        <label className="search-inline">
          <Icon name="search" size={16} />
          <input
            aria-label="폼 검색"
            placeholder="폼 이름 검색"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </label>
        <select
          aria-label="정렬"
          value={order}
          onChange={(e) => setOrder(e.target.value as InboxOrder)}
        >
          {inboxOrders.map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <div className="form-list">
        {problem === 'login' ? (
          <Panel>
            <p className="muted">로그인하면 내 폼함을 볼 수 있어요.</p>
            <Link to={paths.login}>로그인</Link>
          </Panel>
        ) : problem === 'failed' ? (
          <Panel>
            <p className="muted">내 폼함을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
          </Panel>
        ) : forms === null ? (
          <Panel>
            <p className="muted">내 폼함을 불러오는 중이에요.</p>
          </Panel>
        ) : all.length === 0 ? (
          <Panel>
            <p className="muted">
              아직 저장한 폼이 없어요. 받은 폼에서 &quot;내 폼함에 저장&quot;을 누르거나 답을 쓰면
              여기에 모여요.
            </p>
          </Panel>
        ) : shown.length === 0 ? (
          <Panel>
            <p className="muted">여기에 해당하는 폼이 없어요.</p>
          </Panel>
        ) : (
          shown.map((f) => {
            const [label, tone] = badges[tabOf(f) ?? 'submitted'];
            const resume = f.has_draft && !f.closed;
            return (
              <Panel className={`saved-form ${resume ? 'resume' : ''}`} key={f.form_id}>
                <div className="form-line">
                  <div>
                    <div className="card-status">
                      <Badge tone={tone}>{label}</Badge>
                      {f.state === 'submitted' && tabOf(f) !== null && (
                        <Badge tone="green">제출함</Badge>
                      )}
                    </div>
                    <h3>{f.title}</h3>
                    <div className="muted small">{deadlineText(f.deadline)}</div>
                  </div>
                  <Button
                    variant={resume ? 'primary' : 'secondary'}
                    onClick={() => onOpen(f.form_id)}
                  >
                    {resume ? '이어서 작성' : '폼 열기'} <Icon name="arrow" size={15} />
                  </Button>
                </div>
              </Panel>
            );
          })
        )}
      </div>
    </div>
  );
}
