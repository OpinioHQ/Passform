import React from 'react';
import { formatKst } from '../builder/deadline';
import { ShareQr } from '../builder/ShareQr';
import { statusLabel } from '../builder/messages';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import type { CreatorFormSummary } from '../types/api';

export type CreatedFormsLoad = 'loading' | 'failed' | 'ready';

/** 응답 탐색(E8 #46)은 결과 화면 상단 버튼으로 들어간다. */
export function CreatedPage({
  forms,
  load,
  onCreate,
  onEdit,
  onResults,
  loadQr,
  onCopy,
}: {
  forms: CreatorFormSummary[];
  load: CreatedFormsLoad;
  onCreate: () => void;
  onEdit: (formId: string) => void;
  onResults: (formId: string) => void;
  /** 공유 링크가 있는 폼의 QR PNG(AC10). 없으면 QR 버튼을 보이지 않는다. */
  loadQr?: (formId: string) => Promise<Blob>;
  /** 폼 복사(AC40). 성공하면 부른 쪽이 새 초안 빌더로 이동한다. 실패하면 reject한다. */
  onCopy?: (formId: string) => Promise<void>;
}) {
  const now = Date.now();
  const [qrFor, setQrFor] = React.useState<string | null>(null);
  const [copying, setCopying] = React.useState<string | null>(null);
  const [copyFailed, setCopyFailed] = React.useState<string | null>(null);
  async function copy(formId: string) {
    if (!onCopy) return;
    setCopying(formId);
    setCopyFailed(null);
    try {
      await onCopy(formId);
    } catch {
      setCopyFailed(formId);
    } finally {
      setCopying(null);
    }
  }
  // ShareQr는 load가 바뀔 때만 다시 받는다. 열린 카드 하나의 함수만 만든다.
  const loadOpenQr = React.useCallback(
    () => (qrFor && loadQr ? loadQr(qrFor) : Promise.reject(new Error('QR 없음'))),
    [qrFor, loadQr],
  );
  return (
    <div className="page-shell">
      <Heading
        eyebrow="제작자 워크스페이스 / 내가 만든 폼"
        title="내가 만든 폼"
        desc="폼을 만들고 배포한 뒤, 모인 답변을 살펴보세요."
        action={
          <Button onClick={onCreate}>
            <Icon name="plus" size={16} /> 새 폼 만들기
          </Button>
        }
      />
      {load === 'loading' && <p className="muted">목록을 불러오는 중이에요.</p>}
      {load === 'failed' && (
        <p className="error" role="alert">
          목록을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.
        </p>
      )}
      {load === 'ready' && forms.length === 0 && (
        <p className="muted">아직 만든 폼이 없어요. 새 폼을 만들어 보세요.</p>
      )}
      <div className="creator-grid">
        {forms.map((form) => {
          const label = statusLabel(form.status, form.deadline, now);
          const name = form.title.trim() || '제목 없는 폼';
          const showingQr = qrFor === form.form_id;
          return (
            <Panel className="creator-card" key={form.form_id}>
              <div className="creator-title">
                <Badge
                  tone={label === '배포 중' ? 'green' : label === '마감' ? 'amber' : 'neutral'}
                >
                  {label}
                </Badge>
                <h2>{form.title.trim() || '제목 없는 폼'}</h2>
              </div>
              <div className="creator-metrics">
                <div>
                  <small>전체 응답</small>
                  <strong>{form.response_count}</strong>
                </div>
                <div>
                  <small>마감</small>
                  <strong>{form.deadline ? formatKst(form.deadline) : '없음'}</strong>
                </div>
              </div>
              {form.share_url && (
                <p className="small muted builder-share-url">
                  공유 링크: <a href={form.share_url}>{form.share_url}</a>
                </p>
              )}
              <div className="button-row">
                <button
                  type="button"
                  className="btn btn-secondary"
                  aria-label={`${form.title.trim() || '제목 없는 폼'} 편집`}
                  onClick={() => onEdit(form.form_id)}
                >
                  <Icon name="edit" size={16} /> 폼 편집
                </button>
                {onCopy && (
                  <button
                    type="button"
                    className="btn btn-secondary"
                    aria-label={`${name} 복제`}
                    disabled={copying !== null}
                    onClick={() => void copy(form.form_id)}
                  >
                    {copying === form.form_id ? '복제 중…' : '복제'}
                  </button>
                )}
                {form.share_url && loadQr && (
                  <button
                    type="button"
                    className="btn btn-secondary"
                    aria-label={`${name} QR ${showingQr ? '닫기' : '보기'}`}
                    aria-expanded={showingQr}
                    onClick={() => setQrFor(showingQr ? null : form.form_id)}
                  >
                    {showingQr ? 'QR 닫기' : 'QR 보기'}
                  </button>
                )}
                <button
                  type="button"
                  className="btn btn-primary"
                  aria-label={`${form.title.trim() || '제목 없는 폼'} 결과`}
                  onClick={() => onResults(form.form_id)}
                >
                  결과 보기 <Icon name="arrow" size={16} />
                </button>
              </div>
              {copyFailed === form.form_id && (
                <p className="error" role="alert">
                  복제하지 못했어요. 잠시 뒤 다시 시도해 주세요.
                </p>
              )}
              {showingQr && <ShareQr load={loadOpenQr} label={name} />}
            </Panel>
          );
        })}
      </div>
    </div>
  );
}
