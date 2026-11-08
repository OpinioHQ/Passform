/**
 * 응답 화면의 [내 정보로 채우기](T9 #60, AC4·AC6). 공개 경로이므로 버튼을 누를 때만 프로필을 부른다.
 * 제안을 받아 넘기기만 한다. 저장하지 않는다.
 */
import React from 'react';
import { ApiError, api, googleLoginUrl } from '../api/client';
import { Button } from '../components/UI';
import { paths } from '../routes/paths';
import type { AutofillResult, Profile } from '../types/api';

/** 사람이 정한 것(2026-11-08): 비로그인에게는 버튼 대신 로그인 안내를 보인다. */
export function AutofillLoginHint() {
  return (
    <p className="info autofill-hint">
      로그인하면 내 프로필로 채울 수 있어요. <a href={googleLoginUrl}>로그인</a>
    </p>
  );
}

function errorText(e: unknown): string {
  if (e instanceof ApiError && e.status === 401) return '로그인이 필요해요. 다시 로그인해 주세요.';
  return '불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
}

export function AutofillPanel({
  formId,
  onFilled,
  onClosed,
}: {
  formId: string;
  onFilled: (result: AutofillResult) => void;
  onClosed: () => void;
}) {
  const [profiles, setProfiles] = React.useState<Profile[] | null>(null);
  const [selected, setSelected] = React.useState('');
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState('');

  function showList(list: Profile[]) {
    setProfiles(list);
    setSelected(list[0]?.profile_id ?? '');
  }

  async function load() {
    setBusy(true);
    setError('');
    try {
      showList(await api.profiles());
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  async function fill() {
    setBusy(true);
    setError('');
    try {
      onFilled(await api.autofill(formId, selected));
      setProfiles(null);
    } catch (e) {
      const code = e instanceof ApiError ? (e.detail as { error?: unknown } | null)?.error : null;
      if (code === 'form_closed') {
        onClosed();
      } else if (e instanceof ApiError && e.status === 404) {
        setError('프로필을 찾을 수 없어요. 삭제되었을 수 있어요.');
        // 목록을 읽지 못하면 지운 프로필을 선택지에 남기지 않고 처음 버튼으로 돌아간다.
        await api.profiles().then(showList, () => setProfiles(null));
      } else {
        setError(errorText(e));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="autofill-panel">
      {profiles === null && (
        <Button variant="secondary" onClick={() => void load()} disabled={busy}>
          내 정보로 채우기
        </Button>
      )}
      {profiles?.length === 0 && (
        <>
          <p className="info">
            저장된 프로필이 없어요.{' '}
            <a href={paths.myProfile} target="_blank" rel="noopener noreferrer">
              프로필 만들기
            </a>
          </p>
          <div className="button-row">
            <Button variant="secondary" onClick={() => void load()} disabled={busy}>
              다시 불러오기
            </Button>
            <Button variant="ghost" onClick={() => setProfiles(null)} disabled={busy}>
              닫기
            </Button>
          </div>
        </>
      )}
      {profiles && profiles.length > 0 && (
        <div className="button-row">
          <label className="autofill-select">
            <span className="small muted">채울 프로필</span>
            <select value={selected} onChange={(e) => setSelected(e.target.value)}>
              {profiles.map((profile) => (
                <option key={profile.profile_id} value={profile.profile_id}>
                  {profile.label}
                </option>
              ))}
            </select>
          </label>
          <Button onClick={() => void fill()} disabled={busy}>
            채우기
          </Button>
          <Button variant="ghost" onClick={() => setProfiles(null)} disabled={busy}>
            닫기
          </Button>
        </div>
      )}
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
