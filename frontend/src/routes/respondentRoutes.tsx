import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError, api, useLiveApi } from '../api/client';
import { Heading, Panel } from '../components/UI';
import { useDemoData } from '../demo/DemoDataContext';
import {
  demoForm,
  demoHistoryDetail,
  demoHistoryItem,
  demoSavedForms,
  demoSubmission,
} from '../demo/data';
import { HistoryPage, type HistoryDetailState } from '../pages/HistoryPage';
import { InboxPage, type InboxProblem } from '../pages/InboxPage';
import { ClosedNotice, ResponsePage } from '../pages/ResponsePage';
import { DraftGate } from '../response/DraftGate';
import type {
  PublicForm,
  ResponseHistoryItem,
  SavedFormSummary,
  SubmitResponseInput,
} from '../types/api';
import { paths } from './paths';

const isUnauthorized = (e: unknown) => e instanceof ApiError && e.status === 401;

export function MyFormsRoute() {
  const navigate = useNavigate();
  const [forms, setForms] = React.useState<SavedFormSummary[] | null>(
    useLiveApi ? null : demoSavedForms,
  );
  const [problem, setProblem] = React.useState<InboxProblem | null>(null);
  React.useEffect(() => {
    if (!useLiveApi) return;
    let active = true;
    api
      .savedForms()
      .then((next) => {
        if (active) setForms(next);
      })
      .catch((e: unknown) => {
        if (active) setProblem(isUnauthorized(e) ? 'login' : 'failed');
      });
    return () => {
      active = false;
    };
  }, []);
  return <InboxPage forms={forms} problem={problem} onOpen={(id) => navigate(paths.form(id))} />;
}

/**
 * 로그인 사용자에게만 응답 화면의 "내 폼함에 저장"을 보인다. `/me`는 헤더와 같은 결과를 쓴다.
 * 이미 내 폼함에 있으면 `saved`다. 비로그인이거나 확인하지 못하면 `null`이고 버튼을 보이지 않는다.
 */
function useInboxEntry(formId: string | undefined): 'saved' | 'unsaved' | null {
  const [entry, setEntry] = React.useState<'saved' | 'unsaved' | null>(null);
  React.useEffect(() => {
    if (!useLiveApi || formId === undefined) return;
    let active = true;
    setEntry(null);
    api
      .me()
      .then(() => api.savedForms())
      .then((forms) => {
        if (active) setEntry(forms.some((f) => f.form_id === formId) ? 'saved' : 'unsaved');
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [formId]);
  return entry;
}

/** `/f/:slug`와 `/forms/:formId`가 함께 쓴다. 공개 경로라 로그인 전용 API를 부르지 않는다. */
export function FormResponseRoute() {
  const { slug, formId } = useParams();
  const navigate = useNavigate();
  const { addResponse } = useDemoData();
  const [form, setForm] = React.useState<PublicForm | null>(useLiveApi ? null : demoForm);
  const [failure, setFailure] = React.useState<'closed' | 'not_found' | 'failed' | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [reloads, setReloads] = React.useState(0);
  const inboxEntry = useInboxEntry(form?.form_id);
  React.useEffect(() => {
    if (!useLiveApi) return;
    let active = true;
    setForm(null);
    setFailure(null);
    const request = slug !== undefined ? api.formBySlug(slug) : api.formById(formId ?? '');
    request
      .then((next) => {
        if (active) setForm(next);
      })
      .catch((e: unknown) => {
        if (!active) return;
        const code = e instanceof ApiError ? (e.detail as { error?: unknown } | null)?.error : null;
        if (code === 'form_closed') setFailure('closed');
        else if (e instanceof ApiError && e.status === 404) setFailure('not_found');
        else setFailure('failed');
      });
    return () => {
      active = false;
    };
  }, [slug, formId, reloads]);
  if (!form) {
    return (
      <div className="page-shell response-page">
        <Heading eyebrow="응답 작성" title="응답 작성" />
        {failure === 'closed' ? (
          <ClosedNotice />
        ) : (
          <Panel>
            <p className="muted">
              {failure === 'not_found'
                ? '폼을 찾을 수 없어요.'
                : failure === 'failed'
                  ? '폼을 열 수 없어요. 잠시 후 다시 시도해 주세요.'
                  : '폼을 불러오는 중이에요.'}
            </p>
            {failure === 'not_found' && (
              <p className="small muted">주소를 확인하거나 폼을 보낸 사람에게 문의해 주세요.</p>
            )}
          </Panel>
        )}
      </div>
    );
  }
  // 실패는 화면이 서버 오류 코드를 읽어 안내하도록 그대로 던진다.
  async function submit(payload: SubmitResponseInput) {
    if (!form) return;
    setBusy(true);
    try {
      if (useLiveApi) {
        await api.submit(form.form_id, payload);
      } else {
        addResponse(demoSubmission(form, payload));
      }
    } finally {
      setBusy(false);
    }
  }
  const page = {
    form,
    onBack: () => navigate(paths.myForms),
    onSubmit: submit,
    onReload: () => setReloads((n) => n + 1),
    saving: busy,
    onSaveToInbox: inboxEntry ? () => api.saveForm({ form_id: form.form_id }) : undefined,
    savedInInbox: inboxEntry === 'saved',
  };
  const key = `${form.form_id}:${form.definition_version}:${reloads}`;
  // 실제 API에서만 초안을 저장·복원한다(T5 #53). 가짜 데이터 모드는 저장하지 않는다.
  return useLiveApi ? <DraftGate key={key} {...page} /> : <ResponsePage key={key} {...page} />;
}

/** `/me/responses`는 첫 항목을, `/me/responses/:responseId`는 그 항목을 보여 준다. */
export function MyResponsesRoute() {
  const { responseId } = useParams();
  const navigate = useNavigate();
  const { responses } = useDemoData();
  const demoItems = React.useMemo(() => responses.map(demoHistoryItem), [responses]);
  const [liveItems, setLiveItems] = React.useState<ResponseHistoryItem[] | null>(null);
  const [problem, setProblem] = React.useState<'login' | 'failed' | null>(null);
  const [liveDetail, setLiveDetail] = React.useState<HistoryDetailState>({ status: 'idle' });
  const items = useLiveApi ? liveItems : demoItems;
  const selectedId = responseId ?? items?.[0]?.response_id;

  React.useEffect(() => {
    if (!useLiveApi) return;
    let active = true;
    api
      .myResponses()
      .then((next) => {
        if (active) setLiveItems(next);
      })
      .catch((e: unknown) => {
        if (active) setProblem(isUnauthorized(e) ? 'login' : 'failed');
      });
    return () => {
      active = false;
    };
  }, []);

  React.useEffect(() => {
    if (!useLiveApi || selectedId === undefined) return;
    let active = true;
    setLiveDetail({ status: 'loading' });
    api
      .myResponse(selectedId)
      .then((detail) => {
        if (active) setLiveDetail({ status: 'ready', detail });
      })
      .catch((e: unknown) => {
        if (!active) return;
        setLiveDetail({
          status: e instanceof ApiError && e.status === 404 ? 'not_found' : 'failed',
        });
      });
    return () => {
      active = false;
    };
  }, [selectedId]);

  const demoDetail = React.useMemo<HistoryDetailState>(() => {
    if (selectedId === undefined) return { status: 'idle' };
    const found = responses.find((r) => r.response_id === selectedId);
    return found ? { status: 'ready', detail: demoHistoryDetail(found) } : { status: 'not_found' };
  }, [responses, selectedId]);

  return (
    <HistoryPage
      items={items}
      problem={problem}
      selectedId={selectedId}
      detail={useLiveApi ? liveDetail : demoDetail}
      onSelect={(id) => navigate(paths.myResponse(id))}
      onNavigate={() => navigate(paths.myForms)}
    />
  );
}
