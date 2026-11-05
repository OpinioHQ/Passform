import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
export function CreatedPage({
  onBuilder,
  onExplore,
}: {
  onBuilder: () => void;
  onExplore: () => void;
}) {
  return (
    <div className="page-shell">
      <Heading
        eyebrow="제작자 워크스페이스 / 내가 만든 폼"
        title="내가 만든 폼"
        desc="폼을 만들고 배포한 뒤, 모인 답변을 살펴보세요."
        action={
          <Button onClick={onBuilder}>
            <Icon name="plus" size={16} /> 새 폼 만들기
          </Button>
        }
      />
      <div className="creator-grid">
        <Panel className="creator-card">
          <div className="creator-title">
            <Badge tone="green">배포 중 · v1</Badge>
            <h2>10월 팀 경험 설문</h2>
            <p className="muted">함께 일하는 경험에 대한 설문</p>
          </div>
          <div className="creator-metrics">
            <div>
              <small>전체 응답</small>
              <strong>6</strong>
            </div>
            <div>
              <small>폼 상태</small>
              <strong>열림</strong>
            </div>
          </div>
          <div className="button-row">
            <Button variant="secondary" onClick={onBuilder}>
              <Icon name="edit" size={16} /> 폼 편집
            </Button>
            <Button onClick={onExplore}>
              응답 탐색 <Icon name="arrow" size={16} />
            </Button>
          </div>
        </Panel>
        <Panel className="creator-card dashed-card">
          <div className="create-icon">
            <Icon name="plus" size={26} />
          </div>
          <h2>새로운 폼을 만들어보세요</h2>
          <p className="muted">기본 편집 화면에서 문항을 구성할 수 있어요.</p>
          <Button variant="secondary" onClick={onBuilder}>
            폼 제작 시작
          </Button>
        </Panel>
      </div>
    </div>
  );
}
