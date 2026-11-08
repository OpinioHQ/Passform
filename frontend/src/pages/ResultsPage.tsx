import React from 'react';
import { Badge, Panel } from '../components/UI';
import type { FormResults, QuestionType } from '../types/api';

/** 화면 문구는 "폼 버전 1"로 쓴다(AGENTS 5절). */
export function versionLabel(versions: number[]): string {
  return `폼 버전 ${versions.join('·')}`;
}

const typeLabels: Record<QuestionType, string> = {
  multiple_choice: '객관식',
  dropdown: '드롭다운',
  checkbox: '체크박스',
  short_answer: '단답',
  long_answer: '장문',
};

function sameVersions(left: number[], right: number[]): boolean {
  return left.length === right.length && left.every((version, i) => version === right[i]);
}

function OptionRow({
  label,
  count,
  max,
  source,
}: {
  label: string;
  count: number;
  max: number;
  source?: string;
}) {
  return (
    <li className="result-option">
      <div className="result-option-top">
        <span>
          {label}
          {source && (
            <>
              {' '}
              <Badge tone="amber">{source}</Badge>
            </>
          )}
        </span>
        <strong>{count}명</strong>
      </div>
      <div className="track" aria-hidden="true">
        <div style={{ width: `${max === 0 ? 0 : (count / max) * 100}%` }} />
      </div>
    </li>
  );
}

const maxOf = (counts: number[]) => counts.reduce((a, b) => Math.max(a, b), 0);

function ViewTab({
  label,
  selected,
  onSelect,
}: {
  label: string;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      role="tab"
      aria-selected={selected}
      className={selected ? 'selected' : ''}
      onClick={onSelect}
    >
      {label}
    </button>
  );
}

/** 탐색 도우미(E8 #46)가 끼워 넣는 자리. 없으면 대시보드만 전체 폭으로 보인다. */
export interface DashboardSlots {
  /** 문항 카드 위에 놓는 AI 탐색 결과. */
  aiResult?: React.ReactNode;
  /** 문항 카드 오른쪽 열. 윗선과 높이를 카드 영역에 맞춘다. */
  aside?: React.ReactNode;
}

/**
 * 결과 대시보드(SPEC 234행, AC19). 숫자는 서버가 센 값을 그대로 보인다.
 * 탭 → 고른 보기의 응답 수 → (문항 카드 | 탐색 도우미) 순서다.
 */
export function ResultsDashboard({
  results,
  aiResult,
  aside,
}: { results: FormResults } & DashboardSlots) {
  const [view, setView] = React.useState<'combined' | number>('combined');
  const selected =
    view === 'combined' ? undefined : results.versions.find((v) => v.form_version === view);
  return (
    <div className="results-dashboard">
      <div className="tab-bar" role="tablist" aria-label="보기">
        <ViewTab
          label="통합 보기"
          selected={view === 'combined'}
          onSelect={() => setView('combined')}
        />
        {results.versions.map((v) => (
          <ViewTab
            key={v.form_version}
            label={versionLabel([v.form_version])}
            selected={view === v.form_version}
            onSelect={() => setView(v.form_version)}
          />
        ))}
      </div>
      <p className="results-count">
        {selected
          ? `${versionLabel([selected.form_version])} 응답 ${selected.response_count}건`
          : `전체 응답 ${results.response_count}건`}
      </p>
      <div className={`explore-grid ${aside ? 'with-assistant' : ''}`}>
        <div className="explore-left">
          {aiResult}
          {results.versions.length === 0 && (
            <Panel className="result-card">
              <p className="muted">아직 제출된 응답이 없어요.</p>
            </Panel>
          )}
          {view === 'combined' &&
            results.combined.questions.map((q) => (
              <Panel
                className="result-card"
                key={`${q.question_id}|${q.question_note}|${q.question_type}`}
              >
                <div className="result-card-head">
                  <h2>{q.question_note}</h2>
                  <div className="button-row">
                    <Badge>{typeLabels[q.question_type]}</Badge>
                    <Badge tone="blue">{versionLabel(q.form_versions)}</Badge>
                  </div>
                </div>
                <ul className="result-options">
                  {q.options.map((o) => (
                    <OptionRow
                      key={o.label}
                      label={o.label}
                      count={o.count}
                      max={maxOf(q.options.map((x) => x.count))}
                      source={
                        sameVersions(o.form_versions, q.form_versions)
                          ? undefined
                          : `${versionLabel(o.form_versions)}에만 있음`
                      }
                    />
                  ))}
                </ul>
              </Panel>
            ))}
          {selected &&
            selected.questions.map((q) => {
              const entries = Object.entries(q.counts);
              return (
                <Panel className="result-card" key={q.question_id}>
                  <div className="result-card-head">
                    <h2>{q.question_note}</h2>
                  </div>
                  <ul className="result-options">
                    {entries.map(([label, count]) => (
                      <OptionRow
                        key={label}
                        label={label}
                        count={count}
                        max={maxOf(entries.map(([, c]) => c))}
                      />
                    ))}
                  </ul>
                </Panel>
              );
            })}
        </div>
        {aside && <div className="assistant-col">{aside}</div>}
      </div>
    </div>
  );
}
