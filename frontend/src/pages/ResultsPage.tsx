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

/**
 * 탭 글자 밑에 그 보기의 제출 응답 수를 둔다. 탭 이름은 글자만으로 두고 수는 설명으로 잇는다.
 */
function ViewTab({
  label,
  count,
  selected,
  onSelect,
}: {
  label: string;
  count: number;
  selected: boolean;
  onSelect: () => void;
}) {
  const countId = React.useId();
  return (
    <button
      role="tab"
      aria-label={label}
      aria-describedby={countId}
      aria-selected={selected}
      className={selected ? 'selected' : ''}
      onClick={onSelect}
    >
      {label}
      <span className="tab-count" id={countId}>
        {count}건
      </span>
    </button>
  );
}

/**
 * 결과 대시보드(SPEC 234행, AC19). 숫자는 서버가 센 값을 그대로 보인다.
 * 통합 보기 탭에는 전체 응답 수, 버전 탭에는 그 버전의 응답 수를 둔다.
 * 결과 화면은 이 대시보드와 탐색 도우미(E8 #46)를 나란히 놓는다(pages/ExplorePage).
 */
export function ResultsDashboard({ results }: { results: FormResults }) {
  const [view, setView] = React.useState<'combined' | number>('combined');
  const selected =
    view === 'combined' ? undefined : results.versions.find((v) => v.form_version === view);
  return (
    <div className="results-dashboard">
      <div className="tab-bar" role="tablist" aria-label="보기">
        <ViewTab
          label="통합 보기"
          count={results.response_count}
          selected={view === 'combined'}
          onSelect={() => setView('combined')}
        />
        {results.versions.map((v) => (
          <ViewTab
            key={v.form_version}
            label={versionLabel([v.form_version])}
            count={v.response_count}
            selected={view === v.form_version}
            onSelect={() => setView(v.form_version)}
          />
        ))}
      </div>
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
  );
}
