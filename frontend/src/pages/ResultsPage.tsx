import React from 'react';
import { Badge, Panel } from '../components/UI';
import { QUESTION_TYPE_LABELS } from '../components/questionTypeLabels';
import type {
  CombinedQuestion,
  CombinedTextQuestion,
  FormResults,
  QuestionRef,
  QuestionType,
  TextAnswer,
  VersionQuestion,
  VersionResults,
  VersionTextQuestion,
} from '../types/api';

/** 화면 문구는 "폼 버전 1"로 쓴다(AGENTS 5절). */
export function versionLabel(versions: number[]): string {
  return `폼 버전 ${versions.join('·')}`;
}

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
        <strong>{count}</strong>
      </div>
      <div className="track" aria-hidden="true">
        <div style={{ width: `${max === 0 ? 0 : (count / max) * 100}%` }} />
      </div>
    </li>
  );
}

const maxOf = (counts: number[]) => counts.reduce((a, b) => Math.max(a, b), 0);

/**
 * 답 한 줄. 접혀 있으면 한 줄 말줄임, 펼치면 줄바꿈을 살린 전체 답. 텍스트로만 그린다.
 * "자세히 보기"는 한 줄에서 실제로 잘릴 때만 보인다. 폭이 바뀌면(화면 회전·창 크기) 다시 잰다.
 */
function TextAnswerRow({ value }: { value: string }) {
  const [open, setOpen] = React.useState(false);
  const [clipped, setClipped] = React.useState(false);
  const line = React.useRef<HTMLParagraphElement>(null);
  React.useLayoutEffect(() => {
    const el = line.current;
    if (!el || open) return;
    const measure = () => setClipped(el.scrollWidth > el.clientWidth);
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [value, open]);
  return (
    <li className="result-text">
      <p ref={line} className={open ? 'result-text-full' : 'result-text-line'}>
        {value}
      </p>
      {(open || clipped) && (
        <button
          type="button"
          className="result-text-toggle"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          {open ? '접기' : '자세히 보기'}
        </button>
      )}
    </li>
  );
}

/** 단답·장문 카드. 답 수는 서버가 준 목록 길이다. 목록은 카드 안에서 스크롤한다. */
function TextCard({
  note,
  type,
  versions,
  answers,
}: {
  note: string;
  type: QuestionType;
  versions?: number[];
  answers: TextAnswer[];
}) {
  return (
    <Panel className="result-card">
      <div className="result-card-head">
        <h2>{note}</h2>
        <div className="button-row">
          <Badge>{QUESTION_TYPE_LABELS[type]}</Badge>
          {versions && <Badge tone="blue">{versionLabel(versions)}</Badge>}
        </div>
      </div>
      <p className="muted small result-text-count">답 {answers.length}건</p>
      {answers.length > 0 && (
        <ul className="result-texts">
          {answers.map((a) => (
            <TextAnswerRow key={a.response_id} value={a.value} />
          ))}
        </ul>
      )}
    </Panel>
  );
}

const sameRef = (a: QuestionRef, b: QuestionRef) =>
  a.question_id === b.question_id &&
  a.question_note === b.question_note &&
  a.question_type === b.question_type;

type CombinedCard =
  { kind: 'choice'; question: CombinedQuestion } | { kind: 'text'; question: CombinedTextQuestion };

/** 통합 보기 카드 순서는 서버의 question_order다. 없으면(옛 응답) 선택형 뒤에 단답·장문을 놓는다. */
function combinedCards(combined: FormResults['combined']): CombinedCard[] {
  const choices = combined.questions;
  const texts = combined.text_questions ?? [];
  if (!combined.question_order) {
    return [
      ...choices.map((question) => ({ kind: 'choice' as const, question })),
      ...texts.map((question) => ({ kind: 'text' as const, question })),
    ];
  }
  return combined.question_order.flatMap((ref): CombinedCard[] => {
    const choice = choices.find((q) => sameRef(q, ref));
    if (choice) return [{ kind: 'choice', question: choice }];
    const text = texts.find((q) => sameRef(q, ref));
    return text ? [{ kind: 'text', question: text }] : [];
  });
}

/** 선택형 문항의 유형은 question_order에서 읽는다(versions[].questions에는 유형이 없다). 없으면 배지를 뺀다. */
type VersionCard =
  | { kind: 'choice'; question: VersionQuestion; type?: QuestionType }
  | { kind: 'text'; question: VersionTextQuestion };

/** 한 버전 안에서 question_id는 겹치지 않는다. */
function versionCards(version: VersionResults): VersionCard[] {
  const choices = version.questions;
  const texts = version.text_questions ?? [];
  if (!version.question_order) {
    return [
      ...choices.map((question) => ({ kind: 'choice' as const, question })),
      ...texts.map((question) => ({ kind: 'text' as const, question })),
    ];
  }
  return version.question_order.flatMap((ref): VersionCard[] => {
    const choice = choices.find((q) => q.question_id === ref.question_id);
    if (choice) return [{ kind: 'choice', question: choice, type: ref.question_type }];
    const text = texts.find((q) => q.question_id === ref.question_id);
    return text ? [{ kind: 'text', question: text }] : [];
  });
}

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
      {/* 폼 전체 제출 응답 수는 어느 탭에서나 보인다(SPEC 53행, AC19). 버전 탭은 그 버전 수를 덧붙인다. */}
      <p className="results-count">
        <span>전체 응답 {results.response_count}건</span>
        {selected && (
          <>
            <span aria-hidden="true"> · </span>
            <span>
              {versionLabel([selected.form_version])} 응답 {selected.response_count}건
            </span>
          </>
        )}
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
            combinedCards(results.combined).map(({ kind, question: q }) =>
              kind === 'text' ? (
                <TextCard
                  key={`text|${q.question_id}|${q.question_note}|${q.question_type}`}
                  note={q.question_note}
                  type={q.question_type}
                  versions={q.form_versions}
                  answers={q.answers}
                />
              ) : (
                <Panel
                  className="result-card"
                  key={`${q.question_id}|${q.question_note}|${q.question_type}`}
                >
                  <div className="result-card-head">
                    <h2>{q.question_note}</h2>
                    <div className="button-row">
                      <Badge>{QUESTION_TYPE_LABELS[q.question_type]}</Badge>
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
              ),
            )}
          {selected &&
            versionCards(selected).map((card) => {
              if (card.kind === 'text') {
                const q = card.question;
                return (
                  <TextCard
                    key={`text|${q.question_id}`}
                    note={q.question_note}
                    type={q.question_type}
                    versions={[selected.form_version]}
                    answers={q.answers}
                  />
                );
              }
              const q = card.question;
              const entries = Object.entries(q.counts);
              return (
                <Panel className="result-card" key={q.question_id}>
                  <div className="result-card-head">
                    <h2>{q.question_note}</h2>
                    <div className="button-row">
                      {card.type && <Badge>{QUESTION_TYPE_LABELS[card.type]}</Badge>}
                      <Badge tone="blue">{versionLabel([selected.form_version])}</Badge>
                    </div>
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
