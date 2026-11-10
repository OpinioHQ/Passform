import React from 'react';
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
  /** 수정·취소를 마친 뒤의 안내. */
  notice?: string;
  /** 수정·취소가 거절된 이유. */
  actionError?: string;
  onEdit: (responseId: string) => void;
  onCancel: (responseId: string) => Promise<void>;
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
            {detail.updated_at && ` · ${seoulTime(detail.updated_at)} 수정`}
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

/** 서버가 허용한 동작만 보인다. 취소는 되돌릴 수 없어 같은 자리에서 한 번 더 묻는다(사람 결정, 2026-11-08). */
function ChangeActions({
  detail,
  onEdit,
  onCancel,
}: {
  detail: ResponseHistoryDetail;
  onEdit: () => void;
  onCancel: () => Promise<void>;
}) {
  const [confirming, setConfirming] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  if (confirming) {
    return (
      <>
        <span className="small muted">취소하면 되돌릴 수 없어요.</span>
        <Button
          disabled={busy}
          onClick={() => {
            setBusy(true);
            void onCancel().finally(() => {
              setBusy(false);
              setConfirming(false);
            });
          }}
        >
          취소 확인
        </Button>
        <Button variant="secondary" disabled={busy} onClick={() => setConfirming(false)}>
          돌아가기
        </Button>
      </>
    );
  }
  return (
    <>
      {detail.editable && <Button onClick={onEdit}>답변 수정</Button>}
      {detail.cancellable && (
        <Button variant="ghost" onClick={() => setConfirming(true)}>
          응답 취소
        </Button>
      )}
    </>
  );
}

/** 제출 이력(E5 #43). 목록은 `GET /me/responses`, 상세는 `GET /me/responses/{response_id}`다. 수정·취소는 T10 #61. */
export function HistoryPage({
  items,
  problem,
  selectedId,
  detail,
  onSelect,
  onNavigate,
  notice,
  actionError,
  onEdit,
  onCancel,
}: Props) {
  return (
    <div className="page-shell">
      <Heading
        eyebrow="내 워크스페이스 / 제출 내역"
        title="제출 내역"
        desc="내가 보낸 답과 제출 당시의 질문을 함께 보관해요."
      />
      {notice && (
        <p className="info" role="status">
          {notice}
        </p>
      )}
      {actionError && (
        <p className="error" role="alert">
          {actionError}
        </p>
      )}
      {problem === 'login' ? (
        <Panel className="login-prompt">
          <p className="muted">로그인하면 제출 내역을 볼 수 있어요.</p>
          <Link className="btn btn-primary" to={paths.login}>
            로그인
          </Link>
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
              {detail.status === 'ready' && selectedId !== undefined && (
                <ChangeActions
                  key={selectedId}
                  detail={detail.detail}
                  onEdit={() => onEdit(selectedId)}
                  onCancel={() => onCancel(selectedId)}
                />
              )}
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
