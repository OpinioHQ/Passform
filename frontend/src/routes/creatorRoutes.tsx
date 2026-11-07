import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError, api, useLiveApi } from '../api/client';
import { Heading, Panel } from '../components/UI';
import { useDemoData } from '../demo/DemoDataContext';
import { demoBlankForm, demoForm } from '../demo/data';
import { BuilderPage } from '../pages/BuilderPage';
import { CreatedPage } from '../pages/CreatedPage';
import { ExplorePage } from '../pages/ExplorePage';
import { ResponseDetailPage } from '../pages/ResponseDetailPage';
import { ResultsPage } from '../pages/ResultsPage';
import { paths } from './paths';

type Load<T> =
  { state: 'loading' } | { state: 'ready'; data: T } | { state: 'failed'; message: string };

function failureMessage(error: unknown, notFound: string): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return '로그인이 필요해요.';
    if (error.status === 403) return '이 폼을 만든 사람만 볼 수 있어요.';
    if (error.status === 404) return notFound;
  }
  return '불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.';
}

/** 서버 값만 쓴다. 가짜 데이터 모드에서는 API를 부르지 않고 안내만 보인다. */
function useServerData<T>(load: () => Promise<T>, notFound: string): Load<T> {
  const [result, setResult] = React.useState<Load<T>>({ state: 'loading' });
  React.useEffect(() => {
    if (!useLiveApi) return;
    let active = true;
    setResult({ state: 'loading' });
    load()
      .then((data) => {
        if (active) setResult({ state: 'ready', data });
      })
      .catch((error: unknown) => {
        if (active) setResult({ state: 'failed', message: failureMessage(error, notFound) });
      });
    return () => {
      active = false;
    };
  }, [load, notFound]);
  return result;
}

function Waiting({ eyebrow, title, message }: { eyebrow: string; title: string; message: string }) {
  return (
    <div className="page-shell">
      <Heading eyebrow={eyebrow} title={title} />
      <Panel>
        <p className="muted">{message}</p>
      </Panel>
    </div>
  );
}

const demoOnly = '이 화면은 서버에 연결했을 때 실제 응답으로만 보여요.';

export function FormResultsRoute() {
  const { formId = '' } = useParams();
  const load = React.useCallback(() => api.results(formId), [formId]);
  const result = useServerData(load, '폼을 찾을 수 없어요.');
  const eyebrow = '제작자 워크스페이스 / 결과';
  if (!useLiveApi) return <Waiting eyebrow={eyebrow} title="결과" message={demoOnly} />;
  if (result.state === 'ready') return <ResultsPage key={formId} results={result.data} />;
  return (
    <Waiting
      eyebrow={eyebrow}
      title="결과"
      message={result.state === 'failed' ? result.message : '결과를 불러오는 중이에요.'}
    />
  );
}

export function FormResponseDetailRoute() {
  const { formId = '', responseId = '' } = useParams();
  const load = React.useCallback(
    () => api.responseDetail(formId, responseId),
    [formId, responseId],
  );
  const result = useServerData(load, '응답을 찾을 수 없어요.');
  const eyebrow = '제작자 워크스페이스 / 응답 상세';
  if (!useLiveApi) return <Waiting eyebrow={eyebrow} title="응답 상세" message={demoOnly} />;
  if (result.state === 'ready') return <ResponseDetailPage detail={result.data} />;
  return (
    <Waiting
      eyebrow={eyebrow}
      title="응답 상세"
      message={result.state === 'failed' ? result.message : '응답을 불러오는 중이에요.'}
    />
  );
}

export function CreatedFormsRoute() {
  const navigate = useNavigate();
  return (
    <CreatedPage
      onCreate={() => navigate(paths.newForm)}
      onEdit={() => navigate(paths.editForm(demoForm.form_id))}
      onExplore={() => navigate(paths.formExplore(demoForm.form_id))}
    />
  );
}

/** 실제 빌더와 저장은 E3에서 만든다. 여기서는 빈 폼으로 화면만 연다. */
export function NewFormRoute() {
  return <BuilderPage initial={demoBlankForm} isNew />;
}

export function EditFormRoute() {
  const { formId = '' } = useParams();
  const navigate = useNavigate();
  return (
    <BuilderPage
      initial={demoForm}
      onPreview={() => navigate(paths.form(formId))}
      onExplore={() => navigate(paths.formExplore(formId))}
    />
  );
}

export function FormExploreRoute() {
  const { formId = '' } = useParams();
  const navigate = useNavigate();
  const { responses } = useDemoData();
  return <ExplorePage responses={responses} onBuilder={() => navigate(paths.editForm(formId))} />;
}
