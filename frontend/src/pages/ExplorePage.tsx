import React from 'react';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import { answerOf } from '../demo/data';
import { demoExplore } from '../demo/screens';
import type { SubmittedResponse } from '../types/api';
const { questionIds } = demoExplore;
export function ExplorePage({
  responses,
  onBuilder,
}: {
  responses: SubmittedResponse[];
  onBuilder: () => void;
}) {
  const [assistant, setAssistant] = React.useState(true);
  const [query, setQuery] = React.useState('');
  const [mode, setMode] = React.useState<'all' | 'satisfied'>('all');
  const [message, setMessage] = React.useState('');
  const filtered =
    mode === 'satisfied'
      ? responses.filter(
          (x) =>
            answerOf(x, questionIds.experience) === demoExplore.satisfiedAnswer &&
            answerOf(x, questionIds.team) === demoExplore.team,
        )
      : responses;
  function run() {
    if (!query.trim()) return;
    if (demoExplore.queryPattern.test(query)) {
      setMode('satisfied');
      setMessage(demoExplore.matchedMessage);
    } else {
      setMessage(demoExplore.unknownMessage);
    }
  }
  function downloadCsv() {
    const cells = (a: string[]) =>
      a
        .map((v) => {
          const safe = /^[=+\-@]/.test(v) ? "'" + v : v;
          return '"' + safe.replace(/"/g, '""') + '"';
        })
        .join(',');
    const text = [
      cells(demoExplore.columns),
      ...filtered.map((r) =>
        cells([
          answerOf(r, questionIds.name),
          answerOf(r, questionIds.team),
          answerOf(r, questionIds.experience),
          r.submitted_at,
          answerOf(r, questionIds.wish),
        ]),
      ),
    ].join('\r\n');
    const blob = new Blob(['\ufeff' + text], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = demoExplore.csvFilename;
    a.click();
    URL.revokeObjectURL(a.href);
  }
  return (
    <div className="page-shell explore-page">
      <Heading
        eyebrow="제작자 워크스페이스 / 응답 탐색"
        title="모인 답변을 살펴보세요"
        desc={demoExplore.description}
        action={
          <div className="button-row">
            <Badge tone="green">{demoExplore.statusBadge}</Badge>
            <Button variant="secondary" onClick={onBuilder}>
              폼 편집
            </Button>
          </div>
        }
      />
      <div className="metric-grid">
        {[
          ['전체 응답', String(responses.length), '저장된 데모 제출 건'],
          [
            demoExplore.satisfiedAnswer,
            String(
              responses.filter(
                (x) => answerOf(x, questionIds.experience) === demoExplore.satisfiedAnswer,
              ).length,
            ),
            '가상 응답 기준',
          ],
          ['응답 버전', demoExplore.versionCount, demoExplore.versionNote],
          ['현재 탐색 결과', String(filtered.length), '확정된 데모 조건'],
        ].map(([k, v, d]) => (
          <Panel className="metric" key={k}>
            <div className="metric-top">
              <span>{k}</span>
              <strong>{v}</strong>
            </div>
            <small>{d}</small>
          </Panel>
        ))}
      </div>
      <div className={`explore-grid ${assistant ? 'with-assistant' : ''}`}>
        <div className="explore-left">
          <Panel className="result-panel">
            <div className="section-title">
              <div>
                <h2>
                  응답 목록 <span className="subnum">{filtered.length}</span>
                </h2>
                <p className="muted small">필요할 때만 켜서 답변을 찾아보세요.</p>
              </div>
              <Button variant="secondary" onClick={() => setAssistant(!assistant)}>
                <Icon name="message" size={16} /> 답변 도우미 {assistant ? '끄기' : '켜기'}
              </Button>
            </div>
            <div className="filter-bar">
              <Button variant="secondary" onClick={() => setMode('all')}>
                전체 응답
              </Button>
              {mode === 'satisfied' && (
                <button className="filter-chip" onClick={() => setMode('all')}>
                  {demoExplore.filterChip}
                </button>
              )}
            </div>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    {demoExplore.columns.map((column) => (
                      <th key={column}>{column}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((x) => (
                    <tr key={x.response_id}>
                      <td>{answerOf(x, questionIds.name)}</td>
                      <td>{answerOf(x, questionIds.team)}</td>
                      <td>
                        <Badge tone="green">{answerOf(x, questionIds.experience)}</Badge>
                      </td>
                      <td>{new Date(x.submitted_at).toLocaleDateString('ko-KR')}</td>
                      <td>{answerOf(x, questionIds.wish)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="table-footer">
              조건과 일치하는 데모 응답 {filtered.length}개를 표시했어요.{' '}
              <span>
                1–{filtered.length} / {filtered.length}
              </span>
            </div>
          </Panel>
          <Panel className="export-panel">
            <h3>
              <Icon name="download" size={18} /> 파일로 내보내기
            </h3>
            <div className="button-row">
              <Badge tone="blue">CSV · 데모</Badge>
              <span className="small muted">표에 보이는 가상 응답만 다운로드합니다.</span>
              <Button variant="secondary" onClick={downloadCsv}>
                <Icon name="download" size={15} /> CSV 내보내기
              </Button>
            </div>
          </Panel>
        </div>
        {assistant && (
          <Panel className="assistant-panel">
            <div className="assistant-head">
              <div>
                <span className="assistant-symbol">
                  <Icon name="message" size={17} />
                </span>
                <b>답변 도우미</b>
                <p className="muted small">이 설문의 답변을 대화로 찾아보세요.</p>
              </div>
              <button
                className="icon-btn"
                aria-label="도우미 닫기"
                onClick={() => setAssistant(false)}
              >
                <Icon name="x" />
              </button>
            </div>
            <div className="chat-body">
              <div className="chat-bubble user">{demoExplore.exampleQuestion}</div>
              <div className="chat-bubble bot">
                <b>Passform · 답변 도우미</b>
                <p>조건에 맞는 응답을 왼쪽 목록에서 확인할 수 있어요.</p>
                <Badge tone="blue">데모 응답</Badge>
              </div>
              {query && message && (
                <>
                  <div className="chat-bubble user">{query}</div>
                  <div className="chat-bubble bot">
                    <b>Passform · 답변 도우미</b>
                    <p>{message}</p>
                  </div>
                </>
              )}
            </div>
            <form
              className="composer"
              onSubmit={(e) => {
                e.preventDefault();
                run();
              }}
            >
              <input
                aria-label="답변 도우미 요청"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="응답에 대해 질문해 보세요"
              />
              <button aria-label="질문 보내기" type="submit">
                <Icon name="arrow" size={18} />
              </button>
            </form>
          </Panel>
        )}
      </div>
    </div>
  );
}
