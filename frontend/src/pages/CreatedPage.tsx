import { formatKst } from '../builder/deadline';
import { statusLabel } from '../builder/messages';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import type { CreatorFormSummary } from '../types/api';

export type CreatedFormsLoad = 'loading' | 'failed' | 'ready';

/** 결과·탐색 버튼은 T6, E8이 실제 화면을 연결할 때 더한다. */
export function CreatedPage({
  forms,
  load,
  onCreate,
  onEdit,
}: {
  forms: CreatorFormSummary[];
  load: CreatedFormsLoad;
  onCreate: () => void;
  onEdit: (formId: string) => void;
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
              </div>
            </Panel>
          );
        })}
      </div>
    </div>
  );
}
