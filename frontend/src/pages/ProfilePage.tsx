import React from 'react';
import { ApiError, googleLoginUrl } from '../api/client';
import { Button, Heading, Panel } from '../components/UI';
import type { PersonalInfo, Profile, ProfileInput, ProfileKey } from '../types/api';

const FIELDS: { key: ProfileKey; label: string; placeholder?: string }[] = [
  { key: 'name', label: '이름' },
  { key: 'student_id', label: '학번' },
  { key: 'department', label: '학과' },
  { key: 'phone_number', label: '휴대전화' },
  { key: 'email', label: '이메일' },
  { key: 'address', label: '주소' },
  { key: 'birth_date', label: '생년월일', placeholder: 'YYYY-MM-DD' },
];

const LABEL_MAX = 30;

interface Editing {
  profileId: string | null;
  label: string;
  values: PersonalInfo;
}

interface Props {
  /** `null`이면 불러오는 중이다. */
  profiles: Profile[] | null;
  loadError: 'login' | 'failed' | null;
  onCreate: (input: ProfileInput) => Promise<void>;
  onUpdate: (profileId: string, input: ProfileInput) => Promise<void>;
  onDelete: (profileId: string) => Promise<void>;
}

function saveErrorText(e: unknown): string {
  if (e instanceof ApiError) {
    const detail = (e.detail ?? {}) as { error?: unknown; keys?: unknown };
    if (detail.error === 'unsupported_key' && Array.isArray(detail.keys)) {
      return `저장할 수 없는 항목이 있어요: ${detail.keys.join(', ')}`;
    }
    if (e.status === 401) return '로그인이 필요해요. 다시 로그인해 주세요.';
    if (e.status === 404) return '이미 삭제된 프로필이에요. 새로고침해 주세요.';
    if (e.status === 400) return '입력한 내용을 확인해 주세요.';
  }
  return '저장하지 못했어요. 잠시 후 다시 시도해 주세요.';
}

function ProfileEditor({
  editing,
  onChange,
  onSave,
  onCancel,
  busy,
  error,
}: {
  editing: Editing;
  onChange: (next: Editing) => void;
  onSave: () => void;
  onCancel: () => void;
  busy: boolean;
  error: string;
}) {
  return (
    <Panel className="profile-card">
      <h2>{editing.profileId ? '프로필 수정' : '새 프로필'}</h2>
      <label className="field">
        <span>프로필 이름</span>
        <input
          value={editing.label}
          maxLength={LABEL_MAX}
          placeholder="예: 학교, 동아리"
          onChange={(e) => onChange({ ...editing, label: e.target.value })}
        />
      </label>
      {FIELDS.map((field) => (
        <label className="field" key={field.key}>
          <span>{field.label}</span>
          <input
            value={editing.values[field.key] ?? ''}
            placeholder={field.placeholder}
            onChange={(e) =>
              onChange({ ...editing, values: { ...editing.values, [field.key]: e.target.value } })
            }
          />
        </label>
      ))}
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
      <div className="button-row profile-actions">
        <Button onClick={onSave} disabled={busy}>
          {busy ? '저장 중…' : '저장'}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={busy}>
          취소
        </Button>
      </div>
    </Panel>
  );
}

/** `/me/profile`(T9 #60). 저장한 프로필은 응답 화면의 [내 정보로 채우기]에서 고른다. */
export function ProfilePage({ profiles, loadError, onCreate, onUpdate, onDelete }: Props) {
  const [editing, setEditing] = React.useState<Editing | null>(null);
  const [confirming, setConfirming] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState('');
  const [listError, setListError] = React.useState('');

  function startEditing(next: Editing) {
    setEditing(next);
    setConfirming(null);
    setError('');
    setListError('');
  }

  async function save() {
    if (!editing) return;
    const label = editing.label.trim();
    if (!label) {
      setError('프로필 이름을 입력해 주세요.');
      return;
    }
    const personal_info: PersonalInfo = {};
    for (const { key } of FIELDS) {
      const value = editing.values[key]?.trim();
      if (value) personal_info[key] = value;
    }
    setBusy(true);
    setError('');
    try {
      const input = { label, personal_info };
      if (editing.profileId) await onUpdate(editing.profileId, input);
      else await onCreate(input);
      setEditing(null);
    } catch (e) {
      setError(saveErrorText(e));
    } finally {
      setBusy(false);
    }
  }

  async function remove(profileId: string) {
    setBusy(true);
    setListError('');
    try {
      await onDelete(profileId);
      setConfirming(null);
      setEditing((current) => (current?.profileId === profileId ? null : current));
    } catch {
      setListError('삭제하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setBusy(false);
    }
  }

  const canAdd = profiles !== null && !loadError && !editing;
  return (
    <div className="page-shell">
      <Heading
        eyebrow="내 워크스페이스 / 내 프로필"
        title="내 프로필"
        desc="자주 쓰는 본인 정보를 저장해 두면 폼에서 골라 채울 수 있어요."
        action={
          canAdd && (
            <Button onClick={() => startEditing({ profileId: null, label: '', values: {} })}>
              프로필 추가
            </Button>
          )
        }
      />
      {loadError === 'login' && (
        <Panel className="profile-card login-prompt">
          <p className="muted">로그인하면 프로필을 관리할 수 있어요.</p>
          <a className="btn btn-primary" href={googleLoginUrl}>
            Google로 로그인
          </a>
        </Panel>
      )}
      {loadError === 'failed' && (
        <Panel className="profile-card">
          <p className="muted">프로필을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
        </Panel>
      )}
      {!loadError && profiles === null && (
        <Panel className="profile-card">
          <p className="muted">프로필을 불러오는 중이에요.</p>
        </Panel>
      )}
      {editing && (
        <ProfileEditor
          editing={editing}
          onChange={setEditing}
          onSave={() => void save()}
          onCancel={() => setEditing(null)}
          busy={busy}
          error={error}
        />
      )}
      {listError && (
        <p className="error" role="alert">
          {listError}
        </p>
      )}
      {!loadError && profiles?.length === 0 && !editing && (
        <Panel className="profile-card">
          <p className="muted">
            아직 프로필이 없어요. 이름, 연락처처럼 자주 쓰는 정보를 저장해 보세요.
          </p>
        </Panel>
      )}
      {profiles?.map((profile) => (
        <Panel className="profile-card" key={profile.profile_id}>
          <div className="profile-title">
            <h2>{profile.label}</h2>
          </div>
          <dl className="profile-values">
            {FIELDS.filter((field) => profile.personal_info[field.key]).map((field) => (
              <div className="profile-row" key={field.key}>
                <dt>{field.label}</dt>
                <dd>{profile.personal_info[field.key]}</dd>
              </div>
            ))}
          </dl>
          <div className="button-row profile-actions">
            {confirming === profile.profile_id ? (
              <>
                <span className="small muted">이 프로필을 지울까요?</span>
                <Button onClick={() => void remove(profile.profile_id)} disabled={busy}>
                  삭제 확인
                </Button>
                <Button variant="secondary" onClick={() => setConfirming(null)} disabled={busy}>
                  취소
                </Button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  className="btn btn-secondary"
                  aria-label={`${profile.label} 수정`}
                  onClick={() =>
                    startEditing({
                      profileId: profile.profile_id,
                      label: profile.label,
                      values: profile.personal_info,
                    })
                  }
                >
                  수정
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  aria-label={`${profile.label} 삭제`}
                  onClick={() => setConfirming(profile.profile_id)}
                >
                  삭제
                </button>
              </>
            )}
          </div>
        </Panel>
      ))}
    </div>
  );
}
