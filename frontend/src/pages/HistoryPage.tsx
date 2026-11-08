import { Link } from 'react-router-dom';
import { Badge, Button, Heading, Panel } from '../components/UI';
import { paths } from '../routes/paths';
import type {
  AnswerValue,
  ResponseAttachment,
  ResponseHistoryDetail,
  ResponseHistoryItem,
} from '../types/api';
import { seoulTime } from './ResponseDetailPage';

export type HistoryDetailState =
  | { status: 'idle' | 'loading' | 'not_found' | 'failed' }
  | { status: 'ready'; detail: ResponseHistoryDetail };

interface Props {
  /** `null`이면 불러오는 중이다. */
  items: ResponseHistoryItem[] | null;
  problem: 'login' | 'failed' | null;
  /** 보고 있는 제출 내역. 주소에 없으면 목록의 첫 항목이다. */
  selectedId?: string;
  detail: HistoryDetailState;
  onSelect: (responseId: string) => void;
  onNavigate: () => void;
}

function valueText(value: AnswerValue | null): string {
  if (value === null || value.length === 0) return '응답하지 않음';
  return Array.isArray(value) ? value.join(', ') : value;
}

function Files({ files }: { files: ResponseAttachment[] }) {
  if (files.length === 0) return null;
  return (
    <ul className="small muted">
      {files.map((file) => (
        <li key={file.attachment_id}>
          {file.filename}
          {!file.available && ' (삭제된 파일)'}
        </li>
      ))}
    </ul>
  );
}

/** 제출 당시 버전의 문항 문구와 답(AC15). 첨부는 파일 이름만 보인다. */
function Detail({ detail }: { detail: ResponseHistoryDetail }) {
  const questionIds = new Set(detail.content.map((a) => a.question_id));
  const leftover = detail.attachments.filter((file) => !questionIds.has(file.question_id));
  return (
    <>
      <div className="history-success">
        <span className="success-icon">✓</span>
        <div>
          <h2>{detail.form_title}</h2>
          <p className="muted">
            제출 당시 문항 · 폼 버전 {detail.form_version} · {seoulTime(detail.submitted_at)} 제출
          </p>
        </div>
        <Badge tone="green">제출 완료</Badge>
      </div>
      <div className="answer-list">
        {detail.content.map((a, i) => (
          <div key={`${a.question_id}-${i}`} className="answer-line">
            <small>
              {String(i + 1).padStart(2, '0')} · {a.question_note}
            </small>
            <b>{valueText(a.value)}</b>
            <Files
              files={detail.attachments.filter((file) => file.question_id === a.question_id)}
            />
          </div>
        ))}
      </div>
      <Files files={leftover} />
    </>
  );
}

/** 제출 이력(E5 #43). 목록은 `GET /me/responses`, 상세는 `GET /me/responses/{response_id}`다. */
export function HistoryPage({ items, problem, selectedId, detail, onSelect, onNavigate }: Props) {
  return (
    <div className="page-shell">
      <Heading
        eyebrow="내 워크스페이스 / 제출 내역"
        title="제출한 답변"
        desc="내가 보낸 답과 제출 당시의 질문을 함께 보관해요."
      />
      {problem === 'login' ? (
        <Panel>
          <p className="muted">로그인하면 제출 내역을 볼 수 있어요.</p>
          <Link to={paths.login}>로그인</Link>
        </Panel>
      ) : problem === 'failed' ? (
        <Panel>
          <p className="muted">제출 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
        </Panel>
      ) : (
        <div className="history-layout">
          <Panel className="history-list">
            <h3>제출 내역 목록</h3>
            {items === null && <p className="muted">불러오는 중이에요.</p>}
            {items?.length === 0 && <p className="muted">아직 제출한 응답이 없어요.</p>}
            {items?.map((x) => (
              <button
                key={x.response_id}
                className={`history-choice ${x.response_id === selectedId ? 'active' : ''}`}
                onClick={() => onSelect(x.response_id)}
              >
                <strong>{x.form_title}</strong>
                <span>{seoulTime(x.submitted_at)}</span>
                <Badge tone="green">제출 완료</Badge>
              </button>
            ))}
          </Panel>
          <Panel className="history-detail">
            {detail.status === 'ready' ? (
              <Detail detail={detail.detail} />
            ) : (
              <p className="muted">
                {detail.status === 'not_found'
                  ? '이 제출 내역을 찾을 수 없어요.'
                  : detail.status === 'failed'
                    ? '제출 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
                    : detail.status === 'loading'
                      ? '불러오는 중이에요.'
                      : '목록에서 제출 내역을 고르세요.'}
              </p>
            )}
            <div className="button-row">
              <Button variant="secondary" onClick={onNavigate}>
                내 폼함으로
              </Button>
            </div>
          </Panel>
        </div>
      )}
    </div>
  );
}
