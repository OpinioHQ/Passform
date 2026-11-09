import React from 'react';
import { Badge, Button, Heading, Panel } from '../components/UI';
import type { FormResults, QuestionType } from '../types/api';
import { ExportDialog } from './ExportDialog';

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

/** 결과 대시보드(SPEC 234행, AC19). 숫자는 서버가 센 값을 그대로 보인다. */
export function ResultsPage({ formId, results }: { formId: string; results: FormResults }) {
  const [view, setView] = React.useState<'combined' | number>('combined');
  const [exportOpen, setExportOpen] = React.useState(false);
  const selected =
    view === 'combined' ? undefined : results.versions.find((v) => v.form_version === view);
  return (
    <div className="page-shell">
      <Heading
        eyebrow="제작자 워크스페이스 / 결과"
        title="결과"
        desc="선택형 문항의 응답 수예요. 통합 보기는 문구·유형이 같은 문항의 같은 선택지만 합쳐요."
        action={
          <Button variant="secondary" onClick={() => setExportOpen(true)}>
            내보내기
          </Button>
        }
      />
      <div className="metric-grid">
        <Panel className="metric">
          <div className="metric-top">
            <span>전체 응답</span>
            <strong>{results.response_count}</strong>
          </div>
          <small>모든 폼 버전의 제출 응답</small>
        </Panel>
        {selected && (
          <Panel className="metric">
            <div className="metric-top">
              <span>{versionLabel([selected.form_version])} 응답</span>
              <strong>{selected.response_count}</strong>
            </div>
            <small>이 버전으로 제출된 응답</small>
          </Panel>
        )}
      </div>
      <div className="tab-bar" role="tablist" aria-label="보기">
        <button
          role="tab"
          aria-selected={view === 'combined'}
          className={view === 'combined' ? 'selected' : ''}
          onClick={() => setView('combined')}
        >
          통합 보기
        </button>
        {results.versions.map((v) => (
          <button
            key={v.form_version}
            role="tab"
            aria-selected={view === v.form_version}
            className={view === v.form_version ? 'selected' : ''}
            onClick={() => setView(v.form_version)}
          >
            {versionLabel([v.form_version])}
          </button>
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
      {exportOpen && (
        <ExportDialog
          formId={formId}
          versions={results.versions.map((v) => v.form_version)}
          onClose={() => setExportOpen(false)}
        />
      )}
    </div>
  );
}
