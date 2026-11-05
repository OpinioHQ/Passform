import React from 'react';
import { Outlet, useLocation, useNavigate, useOutletContext } from 'react-router-dom';
import { useLiveApi } from '../api/client';
import { showsDemoValues } from '../demo/demoRoutes';
import { paths } from '../routes/paths';
import { Header } from './Header';
import { Button } from './UI';

interface AppOutletContext {
  setNotice: (message: string) => void;
}

/** 모든 라우트가 공유하는 틀: 헤더, 가상 데이터 안내 띠, 상단 알림, 푸터. */
export function AppLayout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [notice, setNotice] = React.useState('');
  React.useEffect(() => {
    window.scrollTo(0, 0);
    setNotice('');
  }, [pathname]);
  const context: AppOutletContext = { setNotice };
  return (
    <div className="app">
      <Header />
      {!useLiveApi && (
        <div className="demo-ribbon">
          <span className="ribbon-dot" /> 디자인 프로토타입 · 가상 데이터 모드{' '}
          <span>— 실제 서비스 API 및 사용자 인증은 연결되지 않았습니다.</span>
        </div>
      )}
      {useLiveApi && showsDemoValues(pathname) && (
        <div className="demo-ribbon">
          <span className="ribbon-dot" /> 이 화면에는 가상 값이 남아 있습니다{' '}
          <span>— 실제 서버 값과 섞여 보일 수 있습니다.</span>
        </div>
      )}
      {notice && (
        <div className="top-notice" role="alert">
          {notice}
          <Button variant="ghost" onClick={() => setNotice('')}>
            닫기
          </Button>
        </div>
      )}
      <main>
        <Outlet context={context} />
      </main>
      <footer className="footer">
        <span>© 2026 Passform · 디자인 기반 프론트엔드 스캐폴드</span>
        <button onClick={() => navigate(paths.home)}>서비스 소개</button>
      </footer>
    </div>
  );
}

/** 라우트 안의 화면이 상단 알림을 띄울 때 쓴다. */
export function useNotice() {
  return useOutletContext<AppOutletContext>();
}
