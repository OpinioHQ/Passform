import { formatKst } from '../builder/deadline';
import { statusLabel } from '../builder/messages';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import type { CreatorFormSummary, StorageUsage } from '../types/api';

export type CreatedFormsLoad = 'loading' | 'failed' | 'ready';

/** 한도가 500,000,000바이트라 화면도 1MB를 1,000,000바이트로 쓴다. */
const megabytes = (bytes: number) => `${Math.round(bytes / 100_000) / 10}MB`;

function StorageMeter({ usage }: { usage: StorageUsage }) {
  const percent = Math.min(100, Math.round((usage.used_bytes / usage.limit_bytes) * 100));
  return (
    <Panel className="storage-meter">
      <div className="storage-meter-text">
        <strong>저장 공간</strong>
        <span>
          {megabytes(usage.used_bytes)} / {megabytes(usage.limit_bytes)} 사용 중
        </span>
      </div>
      <div className="track" aria-hidden="true">
        <div style={{ width: `${percent}%` }} />
      </div>
    </Panel>
  );
}

/** 응답 탐색(E8 #46)은 결과 화면 상단 버튼으로 들어간다. */
export function CreatedPage({
  forms,
  load,
  onCreate,
  onEdit,
  onResults,
  storage,
}: {
  forms: CreatorFormSummary[];
  load: CreatedFormsLoad;
  /** 못 읽었으면 `null`이고 칸을 보이지 않는다. 폼 목록은 그대로 쓴다. */
  storage?: StorageUsage | null;
  onCreate: () => void;
  onEdit: (formId: string) => void;
  onResults: (formId: string) => void;
}) {
  const now = Date.now();
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
      {storage && <StorageMeter usage={storage} />}
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
                <button
                  type="button"
                  className="btn btn-primary"
                  aria-label={`${form.title.trim() || '제목 없는 폼'} 결과`}
                  onClick={() => onResults(form.form_id)}
                >
                  결과 보기 <Icon name="arrow" size={16} />
                </button>
              </div>
            </Panel>
          );
        })}
      </div>
    </div>
  );
}
