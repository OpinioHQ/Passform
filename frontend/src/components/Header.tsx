import React from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { api, useLiveApi } from '../api/client';
import { demoUser } from '../demo/screens';
import { paths } from '../routes/paths';
import type { MeResponse } from '../types/api';
import { DeadlineReminderSwitch } from './DeadlineReminder';
import { NotificationBell } from './NotificationBell';
import { Brand, Button, Icon, Badge } from './UI';

const menus: Array<{ label: string; to: string }> = [
  { label: '내 폼함', to: paths.myForms },
  { label: '제출 내역', to: paths.myResponses },
  { label: '내 프로필', to: paths.myProfile },
  { label: '내가 만든 폼', to: paths.creator },
];

/** 메뉴는 모든 사용자에게 같다. 실제 API 모드에서는 앱을 열 때 `GET /me`로 로그인 상태를 한 번 묻는다. */
export function Header() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [reminderOpen, setReminderOpen] = React.useState(false);
  const [accountOpen, setAccountOpen] = React.useState(false);
  // 가상 데이터 미리보기에서는 화면 상태로만 둔다. 실제 API 모드는 계정 메뉴의 스위치가 `PATCH /me/settings`로 저장한다.
  const [deadlineReminder, setDeadlineReminder] = React.useState(true);
  const [me, setMe] = React.useState<MeResponse | null>(null);
  const [loggingOut, setLoggingOut] = React.useState(false);
  const [logoutError, setLogoutError] = React.useState('');
  React.useEffect(() => {
    if (!useLiveApi) return;
    let active = true;
    // 401이면 비로그인이다. 그 밖의 실패도 로그인 링크를 보인다.
    api.me().then(
      (account) => {
        if (active) setMe(account);
      },
      () => {
        if (active) setMe(null);
      },
    );
    return () => {
      active = false;
    };
  }, []);
  const onFormScreen = pathname.startsWith('/f/') || pathname.startsWith('/forms/');
  function toggleReminder() {
    setReminderOpen(!reminderOpen);
    setAccountOpen(false);
  }
  function toggleAccount() {
    setAccountOpen(!accountOpen);
    setReminderOpen(false);
  }
  /** 서버 세션을 끝내고 첫 화면으로 간다. 로그인 상태는 다음에 다시 묻는다. */
  async function logout() {
    setLoggingOut(true);
    setLogoutError('');
    try {
      await api.logout();
      setMe(null);
      setAccountOpen(false);
      navigate(paths.home);
    } catch {
      setLogoutError('로그아웃하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setLoggingOut(false);
    }
  }
  return (
    <header className="global-header">
      <Brand onClick={() => navigate(paths.home)} />
      <nav aria-label="주 메뉴" className="main-nav">
        {menus.map((m) => (
          <NavLink
            key={m.to}
            to={m.to}
            className={({ isActive }) =>
              `nav-link ${isActive || (m.to === paths.myForms && onFormScreen) ? 'active' : ''}`
            }
          >
            {m.label}
          </NavLink>
        ))}
      </nav>
      <div className="account-actions">
        {useLiveApi ? (
          me?.name ? (
            <>
              <NotificationBell
                open={reminderOpen}
                onToggle={toggleReminder}
                onClose={() => setReminderOpen(false)}
              />
              <button
                type="button"
                className="account-trigger"
                aria-expanded={accountOpen}
                onClick={toggleAccount}
              >
                <span className="avatar">{me.name.slice(0, 1)}</span>
                <span className="account-name">{me.name}</span>
              </button>
              {accountOpen && (
                <div className="account-popover account-menu">
                  <DeadlineReminderSwitch />
                  <Button variant="secondary" onClick={() => void logout()} disabled={loggingOut}>
                    로그아웃
                  </Button>
                  {logoutError && (
                    <p className="error" role="alert">
                      {logoutError}
                    </p>
                  )}
                </div>
              )}
            </>
          ) : (
            <Link className="nav-link" to={paths.login}>
              로그인
            </Link>
          )
        ) : (
          <>
            <Badge>{demoUser.workspace}</Badge>
            <button
              className="icon-btn"
              aria-label="마감 알림 설정"
              aria-expanded={reminderOpen}
              onClick={toggleReminder}
            >
              <Icon name="bell" />
            </button>
            {reminderOpen && (
              <div className="account-popover reminder-popover">
                <label className="reminder-switch">
                  <span>마감 하루 전 알림</span>
                  <input
                    type="checkbox"
                    role="switch"
                    checked={deadlineReminder}
                    onChange={(e) => setDeadlineReminder(e.target.checked)}
                  />
                </label>
                <p className="muted">저장한 미제출 폼을 이메일과 사이트 내 알림으로 알려드려요.</p>
              </div>
            )}
            <span className="avatar">{demoUser.initial}</span>
            <span className="account-name">{demoUser.name}</span>
            <button
              className="icon-btn"
              aria-label="계정 메뉴"
              aria-expanded={accountOpen}
              onClick={toggleAccount}
            >
              <Icon name="chevron" size={15} />
            </button>
            {accountOpen && (
              <div className="account-popover">
                이 화면은 가상 데이터 미리보기입니다.
                <br />
                실제 로그인은 백엔드 연동이 필요합니다.
              </div>
            )}
          </>
        )}
      </div>
    </header>
  );
}
