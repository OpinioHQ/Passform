import { Route, Routes, useNavigate } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { DemoDataProvider } from './demo/DemoDataContext';
import { demoForm } from './demo/data';
import { LandingPage } from './pages/LandingPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { PlaceholderPage } from './pages/PlaceholderPage';
import { ProfilePage } from './pages/ProfilePage';
import {
  CreatedFormsRoute,
  EditFormRoute,
  FormExploreRoute,
  NewFormRoute,
} from './routes/creatorRoutes';
import { paths, routePatterns } from './routes/paths';
import { FormResponseRoute, MyFormsRoute, MyResponsesRoute } from './routes/respondentRoutes';

function LandingRoute() {
  const navigate = useNavigate();
  return (
    <LandingPage
      onStart={() => navigate(paths.myForms)}
      onCreate={() => navigate(paths.newForm)}
      onExplore={() => navigate(paths.formExplore(demoForm.form_id))}
    />
  );
}

export function App() {
  return (
    <DemoDataProvider>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path={routePatterns.home} element={<LandingRoute />} />
          <Route
            path={routePatterns.login}
            element={<PlaceholderPage eyebrow="계정" title="로그인" issue="T2" />}
          />
          <Route path={routePatterns.myForms} element={<MyFormsRoute />} />
          <Route path={routePatterns.myResponses} element={<MyResponsesRoute />} />
          <Route path={routePatterns.myResponse} element={<MyResponsesRoute />} />
          <Route path={routePatterns.myProfile} element={<ProfilePage />} />
          <Route path={routePatterns.publicForm} element={<FormResponseRoute />} />
          <Route path={routePatterns.form} element={<FormResponseRoute />} />
          <Route path={routePatterns.creator} element={<CreatedFormsRoute />} />
          <Route path={routePatterns.newForm} element={<NewFormRoute />} />
          <Route path={routePatterns.editForm} element={<EditFormRoute />} />
          <Route
            path={routePatterns.formResults}
            element={<PlaceholderPage eyebrow="제작자 워크스페이스" title="결과" issue="T6" />}
          />
          <Route path={routePatterns.formExplore} element={<FormExploreRoute />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </DemoDataProvider>
  );
}
