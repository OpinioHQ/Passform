import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, useLiveApi } from '../api/client';
import { isFormClosedError } from '../builder/messages';
import { useNotice } from '../components/AppLayout';
import { Heading, Panel } from '../components/UI';
import { useDemoData } from '../demo/DemoDataContext';
import { demoForm, demoSavedForms, demoSubmission } from '../demo/data';
import { HistoryPage } from '../pages/HistoryPage';
import { InboxPage } from '../pages/InboxPage';
import { ResponsePage } from '../pages/ResponsePage';
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
  const { setNotice } = useNotice();
  const { addResponse } = useDemoData();
  const [form, setForm] = React.useState<PublicForm | null>(useLiveApi ? null : demoForm);
  const [failure, setFailure] = React.useState('');
  const [busy, setBusy] = React.useState(false);
  React.useEffect(() => {
    if (!useLiveApi) return;
    let active = true;
    setForm(null);
    setFailure('');
    const request = slug !== undefined ? api.formBySlug(slug) : api.formById(formId ?? '');
    request
      .then((next) => {
        if (active) setForm(next);
      })
      .catch((error: unknown) => {
        if (!active) return;
        setFailure(
          isFormClosedError(error)
            ? '마감된 폼이에요. 더 이상 응답을 받지 않아요.'
            : '폼을 열 수 없어요.',
        );
      });
    return () => {
      active = false;
    };
  }, [slug, formId]);
  if (!form) {
    return (
      <div className="page-shell">
        <Heading eyebrow="응답 작성" title="응답 작성" />
        <Panel>
          <p className="muted">{failure || '폼을 불러오는 중이에요.'}</p>
        </Panel>
      </div>
    );
  }
  async function submit(payload: SubmitResponseInput) {
    if (!form) return;
    setBusy(true);
    try {
      const record = useLiveApi
        ? await api.submit(form.form_id, payload)
        : demoSubmission(form, payload);
      addResponse(record);
      navigate(paths.myResponses);
    } catch (e) {
      setNotice(e instanceof Error ? e.message : '제출 요청이 실패했습니다.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <ResponsePage
      key={form.form_id}
      form={form}
      onBack={() => navigate(paths.myForms)}
      onSubmit={submit}
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
