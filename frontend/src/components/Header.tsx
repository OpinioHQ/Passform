import React from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useLiveApi } from '../api/client';
import { demoUser } from '../demo/screens';
import { paths } from '../routes/paths';
import { Brand, Icon, Badge } from './UI';

const menus: Array<{ label: string; to: string }> = [
  { label: '내 폼함', to: paths.myForms },
  { label: '제출 내역', to: paths.myResponses },
  { label: '내 프로필', to: paths.myProfile },
  { label: '내가 만든 폼', to: paths.creator },
];

/** 메뉴는 모든 사용자에게 같다. 실제 로그인 상태(`GET /me`)는 T2에서 연결한다. */
export function Header() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = React.useState(false);
  const onFormScreen = pathname.startsWith('/f/') || pathname.startsWith('/forms/');
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
          <Link className="nav-link" to={paths.login}>
            로그인
          </Link>
        ) : (
          <>
            <Badge>{demoUser.workspace}</Badge>
            <button className="icon-btn" aria-label="알림 보기" onClick={() => setOpen(!open)}>
              <Icon name="bell" />
            </button>
            <span className="avatar">{demoUser.initial}</span>
            <span className="account-name">{demoUser.name}</span>
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
