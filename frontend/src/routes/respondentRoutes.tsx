import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError, api, useLiveApi } from '../api/client';
import { useNotice } from '../components/AppLayout';
import { Heading, Panel } from '../components/UI';
import { useDemoData } from '../demo/DemoDataContext';
import { demoForm, demoSavedForms, demoSubmission } from '../demo/data';
import { HistoryPage } from '../pages/HistoryPage';
import { InboxPage } from '../pages/InboxPage';
import { ClosedNotice, ResponsePage } from '../pages/ResponsePage';
import type { PublicForm, SavedFormSummary, SubmitResponseInput } from '../types/api';
import { paths } from './paths';

export function MyFormsRoute() {
  const navigate = useNavigate();
  const { setNotice } = useNotice();
  const [forms, setForms] = React.useState<SavedFormSummary[]>(useLiveApi ? [] : demoSavedForms);
  React.useEffect(() => {
    if (!useLiveApi) return;
    let active = true;
    api
      .savedForms()
      .then((next) => {
        if (active) setForms(next);
      })
      .catch(() => {
        if (active) {
          setNotice('내 폼함 API 연결에 실패했습니다. 서버 및 인증 설정을 확인해 주세요.');
        }
      });
    return () => {
      active = false;
    };
  }, [setNotice]);
  return (
    <InboxPage
      forms={forms}
      onOpen={(id) => navigate(paths.form(id))}
      onCreated={() =>
        setNotice('받은 폼 저장은 실제 API 연동 전입니다. 현재는 가상 폼 목록을 표시합니다.')
      }
    />
  );
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
  return (
    <ResponsePage
      key={`${form.form_id}:${form.definition_version}:${reloads}`}
      form={form}
      onBack={() => navigate(paths.myForms)}
      onSubmit={submit}
      onReload={() => setReloads((n) => n + 1)}
      saving={busy}
    />
  );
}

/** `/me/responses`는 첫 항목을, `/me/responses/:responseId`는 그 항목을 보여 준다. */
export function MyResponsesRoute() {
  const { responseId } = useParams();
  const navigate = useNavigate();
  const { responses } = useDemoData();
  return (
    <HistoryPage
      responses={responses}
      selectedId={responseId}
      onSelect={(id) => navigate(paths.myResponse(id))}
      onNavigate={() => navigate(paths.myForms)}
    />
  );
}
