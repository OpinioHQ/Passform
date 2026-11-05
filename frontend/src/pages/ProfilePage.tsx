import React from 'react';
import { Badge, Button, Heading, Panel } from '../components/UI';
import { demoUser } from '../demo/screens';
export function ProfilePage() {
  const [editing, setEditing] = React.useState(false);
  const [name, setName] = React.useState(demoUser.name);
  const [email, setEmail] = React.useState(demoUser.email);
  return (
    <div className="page-shell">
      <Heading
        eyebrow="내 워크스페이스 / 내 프로필"
        title="내 프로필"
        desc="반복 입력하는 본인 정보를 관리하세요."
        action={
          <Button onClick={() => setEditing(!editing)}>
            {editing ? '수정 종료' : '프로필 편집'}
          </Button>
        }
      />
      <Panel className="profile-card">
        <div className="profile-title">
          <h2>{demoUser.profileLabel}</h2>
          <Badge tone="blue">데모</Badge>
        </div>
        <p className="muted small">`SPEC.md`의 허용된 본인 정보 항목만 사용합니다.</p>
        <label className="field">
          <span>이름</span>
          <input disabled={!editing} value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label className="field">
          <span>이메일</span>
          <input disabled={!editing} value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        {editing && (
          <p className="small muted">
            이 값은 현재 화면 미리보기 전용이며 서버나 계정에 저장되지 않습니다.
          </p>
        )}
      </Panel>
    </div>
  );
}
