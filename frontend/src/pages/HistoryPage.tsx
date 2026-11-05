import React from 'react';
import { Badge, Button, Heading, Panel } from '../components/UI';
import type { SubmittedResponse } from '../types/api';
export function HistoryPage({
  responses,
  onNavigate,
}: {
  responses: SubmittedResponse[];
  onNavigate: () => void;
}) {
  const [selected, setSelected] = React.useState(0);
  const r = responses[selected];
  return (
    <div className="page-shell">
      <Heading
        eyebrow="내 워크스페이스 / 제출 내역"
        title="제출한 답변"
        desc="내가 보낸 생각과 제출 당시의 질문을 함께 보관해요."
      />
      <div className="history-layout">
        <Panel className="history-list">
          <h3>제출 내역 목록</h3>
          {responses.map((x, i) => (
            <button
              key={x.response_id}
              className={`history-choice ${i === selected ? 'active' : ''}`}
              onClick={() => setSelected(i)}
            >
              <strong>{i === 0 ? '10월 팀 경험 설문' : '팀 경험 설문 · 데모 응답'}</strong>
              <span>{new Date(x.submitted_at).toLocaleString('ko-KR')}</span>
              <Badge tone="green">제출 완료</Badge>
            </button>
          ))}
        </Panel>
        <Panel className="history-detail">
          <div className="history-success">
            <span className="success-icon">✓</span>
            <div>
              <h2>답변이 잘 전달됐어요.</h2>
              <p className="muted">제출 당시 문항과 답변을 보여 드립니다.</p>
            </div>
            <Badge tone="green">제출 완료</Badge>
          </div>
          <h2>10월 팀 경험 설문</h2>
          <p className="muted">제출 당시 문항 · v{r?.form_version || 1}</p>
          <div className="answer-list">
            {r?.content.map((a, i) => (
              <div key={`${a.question_id}-${i}`} className="answer-line">
                <small>
                  {String(i + 1).padStart(2, '0')} · {a.question_note}
                </small>
                <b>{Array.isArray(a.value) ? a.value.join(', ') : a.value}</b>
              </div>
            ))}
          </div>
          <div className="button-row">
            <Button variant="secondary" onClick={onNavigate}>
              내 폼함으로
            </Button>
            <span className="small muted">
              응답 수정·취소는 실제 서버 권한과 정책에 연결 후 지원합니다.
            </span>
          </div>
        </Panel>
      </div>
    </div>
  );
}
