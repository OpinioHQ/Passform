import React from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError, useLiveApi } from '../api/client';
import { DEFAULT_CONSENT_TEMPLATE_ID } from '../builder/FormSettingsPanel';
import { newFormDefinition } from '../builder/formDefinition';
import { Button, Heading, Panel } from '../components/UI';
import { useDemoData } from '../demo/DemoDataContext';
import { DemoBuilderPage } from '../demo/DemoBuilderPage';
import { DemoCreatedPage } from '../demo/DemoCreatedPage';
import { DemoExplorePage } from '../demo/DemoExplorePage';
import { demoBlankForm, demoForm } from '../demo/data';
import { BuilderPage } from '../pages/BuilderPage';
import { CreatedPage, type CreatedFormsLoad } from '../pages/CreatedPage';
import { ExplorePage } from '../pages/ExplorePage';
import { ResponseDetailPage } from '../pages/ResponseDetailPage';
import { ResultsDashboard } from '../pages/ResultsPage';
import type { AnalyzeRequest } from '../types/analyze';
import type { ConsentTemplate, CreatorFormDefinition, CreatorFormSummary } from '../types/api';
import { paths } from './paths';

const isUnauthorized = (error: unknown) => error instanceof ApiError && error.status === 401;

function BuilderMessage({ text, children }: { text: string; children?: React.ReactNode }) {
  return (
    <div className="page-shell">
      <Heading eyebrow="제작자 워크스페이스 / 폼 편집" title="폼 편집" />
      <Panel>
        <p className="muted">{text}</p>
        {children}
      </Panel>
    </div>
  );
}

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

function LiveCreatedForms() {
  const navigate = useNavigate();
  const [forms, setForms] = React.useState<CreatorFormSummary[]>([]);
  const [load, setLoad] = React.useState<CreatedFormsLoad>('loading');
  React.useEffect(() => {
    let active = true;
    api
      .createdForms()
      .then((next) => {
        if (!active) return;
        setForms(next);
        setLoad('ready');
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (isUnauthorized(error)) navigate(paths.login, { replace: true });
        else setLoad('failed');
      });
    return () => {
      active = false;
    };
  }, [navigate]);
  return (
    <CreatedPage
      forms={forms}
      load={load}
      onCreate={() => navigate(paths.newForm)}
      onEdit={(id) => navigate(paths.editForm(id))}
      onResults={(id) => navigate(paths.formResults(id))}
    />
  );
}

const demoOnly = '이 화면은 서버에 연결했을 때 실제 응답으로만 보여요.';

export function FormResultsRoute() {
  const { formId = '' } = useParams();
  const navigate = useNavigate();
  const load = React.useCallback(() => api.results(formId), [formId]);
  const result = useServerData(load, '폼을 찾을 수 없어요.');
  // 묻기 밑 버튼용. 못 읽어도 대시보드와 도우미는 그대로 쓴다(버튼만 빠진다).
  const loadDefinition = React.useCallback(() => api.formDefinition(formId), [formId]);
  const definition = useServerData(loadDefinition, '폼을 찾을 수 없어요.');
  const analyze = React.useCallback((body: AnalyzeRequest) => api.analyze(formId, body), [formId]);
  const loadResponse = React.useCallback(
    (responseId: string) => api.responseDetail(formId, responseId),
    [formId],
  );
  const eyebrow = '제작자 워크스페이스 / 결과 대시보드';
  if (!useLiveApi) return <Waiting eyebrow={eyebrow} title="결과 대시보드" message={demoOnly} />;
  if (result.state === 'ready') {
    return (
      <ExplorePage
        key={formId}
        definition={definition.state === 'ready' ? definition.data : null}
        analyze={analyze}
        loadResponse={loadResponse}
        onBuilder={() => navigate(paths.editForm(formId))}
        renderDashboard={(slots) => <ResultsDashboard results={result.data} {...slots} />}
      />
    );
  }
  return (
    <Waiting
      eyebrow={eyebrow}
      title="결과 대시보드"
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
  if (useLiveApi) return <LiveCreatedForms />;
  return (
    <DemoCreatedPage
      onCreate={() => navigate(paths.newForm)}
      onEdit={() => navigate(paths.editForm(demoForm.form_id))}
      onExplore={() => navigate(paths.formExplore(demoForm.form_id))}
    />
  );
}

/** 작업을 시작할 때 제작자 소유 초안을 만든다(SPEC 153행). 기본 동의 템플릿을 미리 고른다(SPEC 210행). */
async function createDraft(): Promise<string> {
  const templates = await api.consentTemplates();
  const chosen =
    templates.find((t) => t.consent_template_id === DEFAULT_CONSENT_TEMPLATE_ID) ?? templates[0];
  const created = await api.createForm(newFormDefinition(chosen?.consent_template_id ?? null));
  return created.form_id;
}

function LiveNewForm() {
  const navigate = useNavigate();
  const [failed, setFailed] = React.useState(false);
  const [attempt, setAttempt] = React.useState(0);
  // StrictMode가 효과를 두 번 실행해도 초안을 하나만 만든다. ref는 그 사이에 유지된다.
  const started = React.useRef(false);
  const mounted = React.useRef(false);
  React.useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  React.useEffect(() => {
    if (started.current) return;
    started.current = true;
    createDraft()
      .then((formId) => {
        if (mounted.current) navigate(paths.editForm(formId), { replace: true });
      })
      .catch((error: unknown) => {
        if (!mounted.current) return;
        if (isUnauthorized(error)) {
          navigate(paths.login, { replace: true });
        } else {
          started.current = false;
          setFailed(true);
        }
      });
  }, [attempt, navigate]);
  if (!failed) return <BuilderMessage text="새 폼을 준비하는 중이에요." />;
  return (
    <BuilderMessage text="새 폼을 만들지 못했어요.">
      <Button
        variant="secondary"
        onClick={() => {
          setFailed(false);
          setAttempt((n) => n + 1);
        }}
      >
        다시 시도
      </Button>
    </BuilderMessage>
  );
}

export function NewFormRoute() {
  if (useLiveApi) return <LiveNewForm />;
  return <DemoBuilderPage initial={demoBlankForm} isNew />;
}

type EditLoad =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; definition: CreatorFormDefinition; templates: ConsentTemplate[] | null };

function LiveEditForm({ formId }: { formId: string }) {
  const navigate = useNavigate();
  const [load, setLoad] = React.useState<EditLoad>({ kind: 'loading' });
  React.useEffect(() => {
    let active = true;
    setLoad({ kind: 'loading' });
    Promise.all([api.formDefinition(formId), api.consentTemplates().catch(() => null)])
      .then(([definition, templates]) => {
        if (active) setLoad({ kind: 'ready', definition, templates });
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (isUnauthorized(error)) {
          navigate(paths.login, { replace: true });
          return;
        }
        const status = error instanceof ApiError ? error.status : 0;
        const message =
          status === 403
            ? '이 폼을 편집할 권한이 없어요.'
            : status === 404
              ? '이 폼을 찾을 수 없어요.'
              : '폼을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.';
        setLoad({ kind: 'error', message });
      });
    return () => {
      active = false;
    };
  }, [formId, navigate]);
  if (load.kind === 'loading') return <BuilderMessage text="폼을 불러오는 중이에요." />;
  if (load.kind === 'error') return <BuilderMessage text={load.message} />;
  return (
    <BuilderPage
      key={formId}
      formId={formId}
      initial={load.definition}
      templates={load.templates}
      onResults={() => navigate(paths.formResults(formId))}
    />
  );
}

export function EditFormRoute() {
  const { formId = '' } = useParams();
  const navigate = useNavigate();
  if (useLiveApi) return <LiveEditForm formId={formId} />;
  return (
    <DemoBuilderPage
      initial={demoForm}
      onPreview={() => navigate(paths.form(formId))}
      onExplore={() => navigate(paths.formExplore(formId))}
    />
  );
}

/**
 * 실제 모드의 탐색은 결과 화면 오른쪽 답변 도우미로 합쳤다(E8 #46). 옛 주소는 결과 화면으로 보낸다.
 * 가짜 데이터 모드는 시험 데모 화면을 그대로 쓴다.
 */
export function FormExploreRoute() {
  const { formId = '' } = useParams();
  const navigate = useNavigate();
  const { responses } = useDemoData();
  if (useLiveApi) return <Navigate to={paths.formResults(formId)} replace />;
  return (
    <DemoExplorePage responses={responses} onBuilder={() => navigate(paths.editForm(formId))} />
  );
}
