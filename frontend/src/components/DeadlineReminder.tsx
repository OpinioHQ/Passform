import React from 'react';
import { api } from '../api/client';

type Listener = (value: boolean) => void;
const listeners = new Set<Listener>();

/**
 * 마감 알림 켬·끔(`PATCH /me/settings`의 `deadline_reminder`, SPEC 101행).
 * 헤더의 알림 목록과 내 프로필이 함께 열려 있어도 같은 값을 보이도록 바뀐 값을 서로 알린다.
 */
function useDeadlineReminder() {
  const [value, setValue] = React.useState<boolean | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState('');
  React.useEffect(() => {
    let active = true;
    api.me().then(
      (account) => {
        if (active) setValue(account.deadline_reminder);
      },
      () => undefined,
    );
    const listener: Listener = (next) => setValue(next);
    listeners.add(listener);
    return () => {
      active = false;
      listeners.delete(listener);
    };
  }, []);
  async function change(next: boolean) {
    setBusy(true);
    setError('');
    try {
      await api.updateSettings({ deadline_reminder: next });
      listeners.forEach((listener) => listener(next));
    } catch {
      setError('알림 설정을 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setBusy(false);
    }
  }
  return { value, busy, error, change };
}

/** 로그인한 사용자의 마감 알림 스위치. 값을 불러오기 전과 저장하는 동안에는 누를 수 없다. */
export function DeadlineReminderSwitch() {
  const { value, busy, error, change } = useDeadlineReminder();
  return (
    <>
      <label className="reminder-switch">
        <span>마감 하루 전 알림</span>
        <input
          type="checkbox"
          role="switch"
          checked={value ?? false}
          disabled={value === null || busy}
          onChange={(e) => void change(e.target.checked)}
        />
      </label>
      <p className="muted">저장한 미제출 폼을 이메일과 사이트 내 알림으로 알려드려요.</p>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </>
  );
}
