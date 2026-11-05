import React from 'react';
import { Header, type PageName } from './components/Header';
import { Button } from './components/UI';
import { api, useLiveApi } from './api/client';
import { demoForm, demoResponses, demoSavedForms } from './demo/data';
import type {
  FormDefinition,
  SavedFormSummary,
  SubmitResponseInput,
  SubmittedResponse,
} from './types/api';
import { InboxPage } from './pages/InboxPage';
import { ResponsePage } from './pages/ResponsePage';
import { HistoryPage } from './pages/HistoryPage';
import { CreatedPage } from './pages/CreatedPage';
import { BuilderPage } from './pages/BuilderPage';
import { ExplorePage } from './pages/ExplorePage';
import { ProfilePage } from './pages/ProfilePage';
import { LandingPage } from './pages/LandingPage';

const supportedPages: PageName[] = [
  'landing',
  'inbox',
  'response',
  'history',
  'profile',
  'created',
  'builder',
  'explore',
];
function initialPage(): PageName {
  const value = window.location.hash.replace('#/', '');
  return supportedPages.includes(value as PageName) ? (value as PageName) : 'inbox';
}
export function App() {
  const [page, setPage] = React.useState<PageName>(initialPage);
  const [selectedFormId, setSelectedFormId] = React.useState(demoForm.form_id);
  const [form, setForm] = React.useState<FormDefinition>(demoForm);
  const [forms, setForms] = React.useState<SavedFormSummary[]>(demoSavedForms);
  const [responses, setResponses] = React.useState<SubmittedResponse[]>(demoResponses);
  const [busy, setBusy] = React.useState(false);
  const [notice, setNotice] = React.useState('');
  const navigate = React.useCallback((next: PageName) => {
    setPage(next);
    window.location.hash = `/${next}`;
    window.scrollTo(0, 0);
    setNotice('');
  }, []);
  React.useEffect(() => {
    const handler = () => setPage(initialPage());
    window.addEventListener('hashchange', handler);
    return () => window.removeEventListener('hashchange', handler);
  }, []);
  React.useEffect(() => {
    if (!useLiveApi) return;
    api
      .savedForms()
      .then(setForms)
      .catch(() =>
        setNotice('내 폼함 API 연결에 실패했습니다. 서버 및 인증 설정을 확인해 주세요.'),
      );
  }, []);
  async function openForm(id: string) {
    setSelectedFormId(id);
    setNotice('');
    if (useLiveApi) {
      try {
        setForm(await api.formById(id));
        navigate('response');
      } catch {
        setNotice('폼을 불러오지 못했습니다. 실제 폼 ID와 서버 인증을 확인해 주세요.');
      }
    } else {
      setForm(demoForm);
      navigate('response');
    }
  }
  async function submit(payload: SubmitResponseInput) {
    setBusy(true);
    try {
      let record: SubmittedResponse;
      if (useLiveApi) {
        record = await api.submit(selectedFormId, payload);
      } else {
        record = {
          response_id: `demo-r${Date.now()}`,
          form_version: payload.form_version,
          status: 'submitted',
          submitted_at: new Date().toISOString(),
          updated_at: null,
          content: payload.answers.map((a) => ({
            question_id: a.question_id,
            question_note:
              form.sections.flatMap((s) => s.questions).find((q) => q.question_id === a.question_id)
                ?.question_note || a.question_id,
            value: a.value,
          })),
        };
      }
      setResponses((prev) => [record, ...prev]);
      navigate('history');
    } catch (e) {
      setNotice(e instanceof Error ? e.message : '제출 요청이 실패했습니다.');
    } finally {
      setBusy(false);
    }
  }
  function saveReceivedForm() {
    setNotice('받은 폼 저장은 실제 API 연동 전입니다. 현재는 가상 폼 목록을 표시합니다.');
  }
  return (
    <div className="app">
      <Header page={page} navigate={navigate} />
      {!useLiveApi && (
        <div className="demo-ribbon">
          <span className="ribbon-dot" /> 디자인 프로토타입 · 가상 데이터 모드{' '}
          <span>— 실제 서비스 API 및 사용자 인증은 연결되지 않았습니다.</span>
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
        {page === 'landing' && (
          <LandingPage
            onStart={() => navigate('inbox')}
            onCreate={() => navigate('builder')}
            onExplore={() => navigate('explore')}
          />
        )}
        {page === 'inbox' && (
          <InboxPage forms={forms} onOpen={openForm} onCreated={saveReceivedForm} />
        )}
        {page === 'response' && (
          <ResponsePage
            form={form}
            onBack={() => navigate('inbox')}
            onSubmit={submit}
            saving={busy}
          />
        )}
        {page === 'history' && (
          <HistoryPage responses={responses} onNavigate={() => navigate('inbox')} />
        )}
        {page === 'profile' && <ProfilePage />}
        {page === 'created' && (
          <CreatedPage
            onBuilder={() => navigate('builder')}
            onExplore={() => navigate('explore')}
          />
        )}
        {page === 'builder' && (
          <BuilderPage
            initial={form}
            onPreview={() => navigate('response')}
            onExplore={() => navigate('explore')}
          />
        )}
        {page === 'explore' && (
          <ExplorePage responses={responses} onBuilder={() => navigate('builder')} />
        )}
      </main>
      <footer className="footer">
        <span>© 2026 Passform · 디자인 기반 프론트엔드 스캐폴드</span>
        <button onClick={() => navigate('landing')}>서비스 소개</button>
      </footer>
    </div>
  );
}
