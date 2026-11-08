import React from 'react';
import { ApiError } from '../api/client';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import {
  analyzeErrorMessage,
  clarifyView,
  coverageNotice,
  matchedLines,
  presetGroups,
  type Choice,
} from '../explore/exploreView';
import type {
  AnalyzeClarifyResponse,
  AnalyzeOkResponse,
  AnalyzeRequest,
  AnalyzeResponse,
  AnalyzeSummary,
  SummaryEvidence,
} from '../types/analyze';
import type { CreatorFormDefinition, ResponseDetail } from '../types/api';
import { versionLabel } from './ResultsPage';

/** 목록은 이만큼씩 상세를 읽는다. 순서는 result.response_ids 그대로다(AC62). */
const LIST_PAGE = 20;

type Turn =
  | { id: number; state: 'loading'; asked: string }
  | { id: number; state: 'done'; asked: string; response: AnalyzeResponse }
  | { id: number; state: 'failed'; asked: string; message: string };

function CoverageBox({ response }: { response: AnalyzeOkResponse }) {
  const [open, setOpen] = React.useState(false);
  const notice = coverageNotice(response.coverage, response.matched);
  if (notice.facts.length === 0) return null;
  return (
    <div className="explore-coverage">
      {notice.facts.map((fact) => (
        <p key={fact}>{fact}</p>
      ))}
      <button
        type="button"
        className="explore-why"
        aria-label="이유 보기"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        ?
      </button>
      {open && (
        <ul className="explore-reasons">
          {notice.reasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function MatchedBox({ response }: { response: AnalyzeOkResponse }) {
  const lines = matchedLines(response.matched);
  return (
    <div className="explore-matched">
      <b>연결 결과</b>
      {lines.length === 0 ? (
        <p className="muted small">조건 없이 모든 제출 응답에서 찾았어요.</p>
      ) : (
        <ul>
          {lines.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

const answerText = (value: string | string[]) => (Array.isArray(value) ? value.join(', ') : value);

function ListResult({
  ids,
  loadResponse,
}: {
  ids: string[];
  loadResponse: (responseId: string) => Promise<ResponseDetail>;
}) {
  const [shown, setShown] = React.useState(Math.min(LIST_PAGE, ids.length));
  const [details, setDetails] = React.useState<Map<string, ResponseDetail | 'failed'>>(new Map());
  React.useEffect(() => {
    let active = true;
    const pending = ids.slice(0, shown).filter((id) => !details.has(id));
    if (pending.length === 0) return;
    Promise.all(
      pending.map((id) =>
        loadResponse(id).then(
          (detail): [string, ResponseDetail | 'failed'] => [id, detail],
          (): [string, ResponseDetail | 'failed'] => [id, 'failed'],
        ),
      ),
    ).then((loaded) => {
      if (!active) return;
      setDetails((prev) => {
        const next = new Map(prev);
        for (const [id, detail] of loaded) next.set(id, detail);
        return next;
      });
    });
    return () => {
      active = false;
    };
  }, [ids, shown, details, loadResponse]);
  return (
    <>
      <ol className="explore-list">
        {ids.slice(0, shown).map((id, index) => {
          const detail = details.get(id);
          return (
            <li key={id} className="explore-response">
              <div className="explore-response-head">
                <b>응답 {index + 1}</b>
                {detail && detail !== 'failed' && (
                  <Badge tone="blue">{versionLabel([detail.form_version])}</Badge>
                )}
              </div>
              {detail === undefined && <p className="muted small">불러오는 중이에요.</p>}
              {detail === 'failed' && <p className="muted small">이 응답을 불러오지 못했어요.</p>}
              {detail && detail !== 'failed' && (
                <dl>
                  {detail.content.map((item) => (
                    <div key={item.question_id}>
                      <dt>{item.question_note}</dt>
                      <dd>{answerText(item.value) || '—'}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </li>
          );
        })}
      </ol>
      {shown < ids.length && (
        <Button
          variant="secondary"
          onClick={() => setShown(Math.min(shown + LIST_PAGE, ids.length))}
        >
          더 보기
        </Button>
      )}
    </>
  );
}

function Evidence({ items }: { items: SummaryEvidence[] }) {
  if (items.length === 0) return null;
  return (
    <ul className="explore-evidence" aria-label="근거 발췌">
      {items.map((e) => (
        <li key={`${e.response_id}|${e.question_id}`}>
          <q>{e.excerpt}</q> <span className="muted small">{versionLabel([e.form_version])}</span>
        </li>
      ))}
    </ul>
  );
}

function summaryStatusMessage(summary: AnalyzeSummary, count: number): string | null {
  switch (summary.status) {
    case 'ok':
      return null;
    case 'no_matches':
      return '조건에 맞는 응답이 없어 요약할 내용이 없어요.';
    case 'no_consented_responses':
      return `조건에 맞는 응답 ${count}건 모두 AI 요약에 동의하지 않아 요약하지 않았어요.`;
    case 'insufficient_evidence':
      return `'${summary.topic?.query ?? ''}' 관련 답을 충분히 찾지 못해 요약하지 않았어요.`;
    case 'too_many_responses':
      return '요약할 응답이 너무 많아요. 조건을 더해 범위를 좁혀 다시 물어봐 주세요.';
  }
}

function SummaryResult({ summary, count }: { summary: AnalyzeSummary; count: number }) {
  const status = summaryStatusMessage(summary, count);
  return (
    <div className="explore-summary">
      {status && <p className="explore-status">{status}</p>}
      {summary.excluded_no_consent_count > 0 && summary.status !== 'no_consented_responses' && (
        <p className="muted small">
          AI 요약에 동의하지 않은 {summary.excluded_no_consent_count}건은 요약에서 뺐어요.
        </p>
      )}
      {summary.items.map((item) => (
        <Panel className="result-card" key={`${item.type}|${item.target.question_note}`}>
          <div className="result-card-head">
            <h3>{item.target.question_note}</h3>
            <Badge tone="blue">{versionLabel(item.target.form_versions)}</Badge>
          </div>
          {item.type === 'themes' ? (
            <ol className="explore-themes">
              {item.themes.map((theme) => (
                <li key={theme.label}>
                  <div className="result-option-top">
                    <b>{theme.label}</b>
                    <strong>{theme.count}건</strong>
                  </div>
                  <p>{theme.text ?? '근거로 쓸 답을 찾지 못해 요약문을 만들지 않았어요.'}</p>
                  <Evidence items={theme.evidence} />
                </li>
              ))}
            </ol>
          ) : (
            <ul className="result-options">
              {item.options.map((option) => (
                <li className="result-option-top" key={option.label}>
                  <span>
                    {option.label}{' '}
                    <span className="muted small">{versionLabel(option.form_versions)}</span>
                  </span>
                  <strong>{option.count}명</strong>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      ))}
      {summary.topic && summary.topic.text !== null && (
        <Panel className="result-card">
          <div className="result-card-head">
            <h3>{`'${summary.topic.query}'에 대한 의견`}</h3>
          </div>
          <p>{summary.topic.text}</p>
          <Evidence items={summary.topic.evidence} />
        </Panel>
      )}
    </div>
  );
}

function OkResult({
  response,
  loadResponse,
}: {
  response: AnalyzeOkResponse;
  loadResponse: (responseId: string) => Promise<ResponseDetail>;
}) {
  const { result } = response;
  return (
    <div className="explore-result">
      <p className="explore-count">
        조건에 맞는 응답 <strong>{result.count}</strong>건
      </p>
      <CoverageBox response={response} />
      <MatchedBox response={response} />
      {result.operation === 'list' && (
        <ListResult ids={result.response_ids} loadResponse={loadResponse} />
      )}
      {result.operation === 'summarize' && (
        <SummaryResult summary={result.summary} count={result.count} />
      )}
    </div>
  );
}

function ClarifyResult({
  response,
  onChoose,
  onResults,
}: {
  response: AnalyzeClarifyResponse;
  onChoose: (choice: Choice) => void;
  onResults: () => void;
}) {
  const view = clarifyView(response);
  return (
    <div className="explore-clarify">
      <p>{response.clarify.message}</p>
      {view.choices.length > 0 && (
        <div className="button-row explore-choices">
          {view.choices.map((choice) => (
            <Button key={choice.label} variant="secondary" onClick={() => onChoose(choice)}>
              {choice.label}
            </Button>
          ))}
        </div>
      )}
      {view.questions.length > 0 && (
        <>
          <ul className="explore-hints">
            {view.questions.map((q) => (
              <li key={q.note}>
                <b>{q.note}</b> <span className="muted small">{versionLabel(q.versions)}</span>
                {q.options.length > 0 && <div className="small">{q.options.join(' · ')}</div>}
              </li>
            ))}
          </ul>
          <p className="muted small">위 문항이나 선택지 표현으로 다시 물어봐 주세요.</p>
        </>
      )}
      {view.toResults && (
        <Button variant="secondary" onClick={onResults}>
          결과 화면의 버전별 보기
        </Button>
      )}
    </div>
  );
}

/** 탐색 화면(E8 #46). 자연어로 묻고 서버가 준 근거와 함께 결과를 본다. */
export function ExplorePage({
  definition,
  analyze,
  loadResponse,
  onBuilder,
  onResults,
}: {
  definition: CreatorFormDefinition;
  analyze: (body: AnalyzeRequest) => Promise<AnalyzeResponse>;
  loadResponse: (responseId: string) => Promise<ResponseDetail>;
  onBuilder: () => void;
  onResults: () => void;
}) {
  const [query, setQuery] = React.useState('');
  const [turn, setTurn] = React.useState<Turn | null>(null);
  const seq = React.useRef(0);
  const presets = React.useMemo(() => presetGroups(definition), [definition]);
  const loading = turn?.state === 'loading';

  function send(body: AnalyzeRequest, asked: string) {
    const id = ++seq.current;
    setTurn({ id, state: 'loading', asked });
    analyze(body).then(
      (response) => {
        if (id === seq.current) setTurn({ id, state: 'done', asked, response });
      },
      (error: unknown) => {
        if (id !== seq.current) return;
        const status = error instanceof ApiError ? error.status : null;
        setTurn({ id, state: 'failed', asked, message: analyzeErrorMessage(status) });
      },
    );
  }

  const choose = (choice: Choice) => send({ context: choice.context }, choice.label);

  return (
    <div className="page-shell explore-page">
      <Heading
        eyebrow="제작자 워크스페이스 / 응답 탐색"
        title="모인 답변을 살펴보세요"
        desc={`${definition.title || '제목 없는 폼'} · 모든 폼 버전의 제출 응답에서 찾아요.`}
        action={
          <div className="button-row">
            <Button variant="secondary" onClick={onBuilder}>
              폼 편집
            </Button>
            <Button variant="secondary" onClick={onResults}>
              결과 보기
            </Button>
          </div>
        }
      />
      <Panel className="explore-ask">
        <form
          className="composer"
          onSubmit={(e) => {
            e.preventDefault();
            const text = query.trim();
            if (!text || loading) return;
            send({ query: text }, text);
          }}
        >
          <input
            aria-label="응답에 대해 묻기"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="예: 3학년은 몇 명이야?"
          />
          <button aria-label="질문 보내기" type="submit" disabled={loading}>
            <Icon name="arrow" size={18} />
          </button>
        </form>
        {presets.length > 0 && (
          <div className="explore-presets">
            {presets.map((group) => (
              <div key={group.title} className="explore-preset-group">
                <span className="muted small">{group.title}</span>
                <div className="button-row">
                  {group.choices.map((choice) => (
                    <button
                      key={choice.label}
                      type="button"
                      className="filter-chip"
                      disabled={loading}
                      onClick={() => choose(choice)}
                    >
                      {choice.label}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </Panel>
      {turn && (
        <Panel className="explore-answer" key={turn.id}>
          <p className="chat-bubble user">{turn.asked}</p>
          {turn.state === 'loading' && <p className="muted">분석하는 중이에요.</p>}
          {turn.state === 'failed' && (
            <p className="error" role="alert">
              {turn.message}
            </p>
          )}
          {turn.state === 'done' && turn.response.status === 'ok' && (
            <OkResult response={turn.response} loadResponse={loadResponse} />
          )}
          {turn.state === 'done' && turn.response.status === 'clarify' && (
            <ClarifyResult response={turn.response} onChoose={choose} onResults={onResults} />
          )}
        </Panel>
      )}
    </div>
  );
}
