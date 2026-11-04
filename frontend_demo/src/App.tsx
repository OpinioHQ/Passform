import { useState } from 'react';
import { Link, NavLink, Navigate, Route, Routes } from 'react-router-dom';

import { currentDemoUser, setDemoUser, type DemoUser } from './api/client';
import CreatorFormsPage from './pages/CreatorFormsPage';
import FormResultsPage from './pages/FormResultsPage';
import HistoryDetailPage from './pages/HistoryDetailPage';
import MyFormsPage from './pages/MyFormsPage';
import RespondPage from './pages/RespondPage';

const navClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-accent-soft font-medium text-accent' : 'text-muted hover:text-ink'}`;

export default function App() {
  const [user, setUser] = useState<DemoUser>(currentDemoUser());

  const changeUser = (next: DemoUser) => {
    setDemoUser(next);
    setUser(next);
  };

  return (
    <div className="min-h-screen">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
          <Link to="/" className="font-semibold">Passform</Link>
          <nav className="flex gap-1">
            <NavLink to="/me/forms" className={navClass}>응답자</NavLink>
            <NavLink to="/creator" className={navClass}>제작자</NavLink>
          </nav>
          <label className="ml-auto flex items-center gap-2 text-xs text-muted">
            데모 계정
            <select
              value={user}
              onChange={(event) => changeUser(event.target.value as DemoUser)}
              className="rounded-md border border-line bg-surface px-2 py-1 text-ink"
            >
              <option value="demo-user">데모 사용자</option>
              <option value="other-user">다른 사용자</option>
            </select>
          </label>
        </div>
      </header>
      {/* 계정이 바뀌면 화면의 데이터를 전부 다시 읽는다. */}
      <main key={user} className="mx-auto max-w-4xl px-4 py-6">
        <Routes>
          <Route path="/" element={<Navigate to="/me/forms" replace />} />
          <Route path="/me/forms" element={<MyFormsPage />} />
          <Route path="/me/responses/:responseId" element={<HistoryDetailPage />} />
          <Route path="/f/:slug" element={<RespondPage />} />
          <Route path="/forms/:formId" element={<RespondPage />} />
          <Route path="/creator" element={<CreatorFormsPage />} />
          <Route path="/creator/forms/:formId" element={<FormResultsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
