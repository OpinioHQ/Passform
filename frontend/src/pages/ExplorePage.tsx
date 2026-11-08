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
  type PresetGroup,
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
import { versionLabel, type DashboardSlots } from './ResultsPage';

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
                  <strong>{option.count}</strong>
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
}: {
  response: AnalyzeClarifyResponse;
  onChoose: (choice: Choice) => void;
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
        <p className="muted small">대시보드의 폼 버전 탭에서 버전별 결과를 볼 수 있어요.</p>
      )}
    </div>
  );
}

/**
 * 결과 대시보드 + 탐색 도우미(E8 #46). 이 컴포넌트는 묻기 상태만 갖고,
 * AI 탐색 결과 카드와 도우미 패널을 대시보드의 두 자리(aiResult, aside)에 넘긴다.
 * 대시보드 숫자(폼 전체 기준)는 바꾸지 않는다. definition을 못 읽으면(null) 묻기 밑 버튼만 빠진다.
 */
export function ExplorePage({
  definition,
  analyze,
  loadResponse,
  onBuilder,
  renderDashboard,
}: {
  definition: CreatorFormDefinition | null;
  analyze: (body: AnalyzeRequest) => Promise<AnalyzeResponse>;
  loadResponse: (responseId: string) => Promise<ResponseDetail>;
  onBuilder: () => void;
  renderDashboard: (slots: DashboardSlots) => React.ReactNode;
}) {
  const [assistantOpen, setAssistantOpen] = React.useState(true);
  const [moreOptions, setMoreOptions] = React.useState(false);
  const [query, setQuery] = React.useState('');
  const [turn, setTurn] = React.useState<Turn | null>(null);
  // 왼쪽 결과는 마지막 ok 응답을 둔다. 되묻기 중에도 직전 결과가 남는다.
  const [shown, setShown] = React.useState<{
    id: number;
    asked: string;
    response: AnalyzeOkResponse;
  } | null>(null);
  const seq = React.useRef(0);
  const presets = React.useMemo(() => (definition ? presetGroups(definition) : []), [definition]);
  const optionPresets = presets.filter((g) => g.kind === 'option');
  const mainPresets = presets.filter((g) => g.kind === 'main');
  const loading = turn?.state === 'loading';

  function send(body: AnalyzeRequest, asked: string) {
    const id = ++seq.current;
    setTurn({ id, state: 'loading', asked });
    analyze(body).then(
      (response) => {
        if (id !== seq.current) return;
        setTurn({ id, state: 'done', asked, response });
        if (response.status === 'ok') setShown({ id, asked, response });
      },
      (error: unknown) => {
        if (id !== seq.current) return;
        const status = error instanceof ApiError ? error.status : null;
        setTurn({ id, state: 'failed', asked, message: analyzeErrorMessage(status) });
      },
    );
  }

  const choose = (choice: Choice) => send({ context: choice.context }, choice.label);

  const presetGroup = (group: PresetGroup) => (
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
  );

  return (
    <div className="page-shell explore-page">
      <Heading
        eyebrow="제작자 워크스페이스 / 결과 대시보드"
        title="결과 대시보드"
        action={
          <div className="button-row">
            <Button variant="secondary" onClick={onBuilder}>
              폼 편집
            </Button>
            <Button variant="secondary" onClick={() => setAssistantOpen(!assistantOpen)}>
              <Icon name="message" size={16} /> 탐색 도우미 {assistantOpen ? '끄기' : '켜기'}
            </Button>
          </div>
        }
      />
      {renderDashboard({
        aiResult: shown && (
          <Panel className="result-panel explore-answer" key={shown.id}>
            <div className="section-title">
              <div>
                <h2>AI 탐색 결과</h2>
                <p className="muted small explore-asked">{shown.asked}</p>
              </div>
              <Button variant="secondary" onClick={() => setShown(null)}>
                지우기
              </Button>
            </div>
            <OkResult response={shown.response} loadResponse={loadResponse} />
          </Panel>
        ),
        aside: assistantOpen && (
          <Panel className="assistant-panel">
            <div className="assistant-head">
              <div>
                <span className="assistant-symbol">
                  <Icon name="message" size={17} />
                </span>
                <b>탐색 도우미</b>
                <p className="muted small">이 폼의 응답을 말로 물어보세요.</p>
              </div>
              <button
                className="icon-btn"
                aria-label="탐색 도우미 닫기"
                onClick={() => setAssistantOpen(false)}
              >
                <Icon name="x" />
              </button>
            </div>
            <div className="chat-body">
              {!turn && (
                <div className="chat-bubble bot">
                  <p>예: "3학년 응답은 몇 건이야?", "지원 동기 요약해줘"</p>
                </div>
              )}
              {turn && (
                <>
                  <p className="chat-bubble user">{turn.asked}</p>
                  <div className="chat-bubble bot" key={turn.id}>
                    {turn.state === 'loading' && <p className="muted">분석하는 중이에요.</p>}
                    {turn.state === 'failed' && (
                      <p className="error" role="alert">
                        {turn.message}
                      </p>
                    )}
                    {turn.state === 'done' && turn.response.status === 'ok' && (
                      <p>
                        조건에 맞는 응답 {turn.response.result.count}건을 찾았어요. 대시보드 위
                        결과에서 확인해 주세요.
                      </p>
                    )}
                    {turn.state === 'done' && turn.response.status === 'clarify' && (
                      <ClarifyResult response={turn.response} onChoose={choose} />
                    )}
                  </div>
                </>
              )}
            </div>
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
                placeholder="예: 3학년 응답은 몇 건이야?"
              />
              <button aria-label="질문 보내기" type="submit" disabled={loading}>
                <Icon name="arrow" size={18} />
              </button>
            </form>
            {presets.length > 0 && (
              // 접기 버튼을 펼친 목록 위에 두어 항상 보이게 하고, 목록은 이 칸 안에서 스크롤한다.
              <div className="explore-presets">
                {mainPresets.map(presetGroup)}
                {optionPresets.length > 0 && (
                  <button
                    type="button"
                    className="explore-more"
                    aria-expanded={moreOptions}
                    onClick={() => setMoreOptions(!moreOptions)}
                  >
                    {moreOptions ? '선택지별 응답 수 접기' : '선택지별 응답 수 더 보기'}
                  </button>
                )}
                {moreOptions && optionPresets.map(presetGroup)}
              </div>
            )}
          </Panel>
        ),
      })}
    </div>
  );
}
