import React from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useLiveApi } from '../api/client';
import { paths } from '../routes/paths';
import type { UiMode } from '../types/api';
import { Brand, Icon, Badge } from './UI';

const menusByMode: Record<UiMode, Array<{ label: string; to: string }>> = {
  respondent: [
    { label: '내 폼함', to: paths.myForms },
    { label: '제출 내역', to: paths.myResponses },
    { label: '내 프로필', to: paths.myProfile },
  ],
  creator: [{ label: '내가 만든 폼', to: paths.creator }],
};
const modeHome: Record<UiMode, string> = {
  respondent: paths.myForms,
  creator: paths.creator,
};
const modeLabels: Array<{ mode: UiMode; label: string }> = [
  { mode: 'respondent', label: '응답자' },
  { mode: 'creator', label: '제작자' },
];

function initialUiMode(pathname: string): UiMode {
  return pathname === paths.creator || pathname.startsWith(`${paths.creator}/`)
    ? 'creator'
    : 'respondent';
}

/**
 * 화면 모드는 메뉴와 첫 화면만 바꾼다. 권한을 판정하지 않고 라우트 접근도 막지 않는다.
 * 계정 저장(`PATCH /me/settings`)과 실제 로그인 상태(`GET /me`)는 T2에서 연결한다.
 */
export function Header() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = React.useState(false);
  const [uiMode, setUiMode] = React.useState<UiMode>(() => initialUiMode(pathname));
  const onFormScreen = pathname.startsWith('/f/') || pathname.startsWith('/forms/');
  function switchMode(next: UiMode) {
    setUiMode(next);
    navigate(modeHome[next]);
  }
  return (
    <header className="global-header">
      <Brand onClick={() => navigate(paths.home)} />
      <nav aria-label="주 메뉴" className="main-nav">
        {menusByMode[uiMode].map((m) => (
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
        <div className="segmented mode-switch" role="group" aria-label="화면 모드">
          {modeLabels.map(({ mode, label }) => (
            <button
              key={mode}
              className={uiMode === mode ? 'active' : ''}
              aria-pressed={uiMode === mode}
              onClick={() => switchMode(mode)}
            >
              {label}
            </button>
          ))}
        </div>
        {useLiveApi ? (
          <Link className="nav-link" to={paths.login}>
            로그인
          </Link>
        ) : (
          <>
            <Badge>데모 워크스페이스</Badge>
            <button className="icon-btn" aria-label="알림 보기" onClick={() => setOpen(!open)}>
              <Icon name="bell" />
            </button>
            <span className="avatar">지</span>
            <span className="account-name">김지윤</span>
            <button className="icon-btn" aria-label="계정 메뉴" onClick={() => setOpen(!open)}>
              <Icon name="chevron" size={15} />
            </button>
            {open && (
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
