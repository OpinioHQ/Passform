import React from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError, api, useLiveApi } from '../api/client';
import { DeadlineReminderSwitch } from '../components/DeadlineReminder';
import { Button, Heading, Panel } from '../components/UI';
import { useDemoData } from '../demo/DemoDataContext';
import {
  demoForm,
  demoHistoryDetail,
  demoHistoryItem,
  demoSavedForms,
  demoSubmission,
} from '../demo/data';
import { demoUser } from '../demo/screens';
import { HistoryPage, type HistoryDetailState } from '../pages/HistoryPage';
import { InboxPage, type InboxProblem } from '../pages/InboxPage';
import { ProfilePage } from '../pages/ProfilePage';
import { ClosedNotice, ResponsePage } from '../pages/ResponsePage';
import { DraftGate } from '../response/DraftGate';
import { changeErrorText } from '../response/changeErrors';
import { answersToMap, removeBrowserDraft, removeBrowserDraftsAt } from '../response/draftSync';
import { ANONYMOUS_SCOPE, fileDraftKey, removeFileDraft } from '../response/fileDrafts';
import type {
  Profile,
  ProfileInput,
  PublicForm,
  ResponseHistoryDetail,
  ResponseHistoryItem,
  SavedFormSummary,
  SubmitFiles,
  SubmitResponseInput,
} from '../types/api';
import { paths } from './paths';

const isUnauthorized = (e: unknown) => e instanceof ApiError && e.status === 401;

const demoProfiles: Profile[] = [
  {
    profile_id: 'demo-profile',
    label: demoUser.profileLabel,
    personal_info: { name: demoUser.name, email: demoUser.email },
  },
];

/** `/me/profile`. 실제 API에서는 서버 값만 보이고, 가짜 데이터 모드에서는 화면 상태로만 바뀐다. */
export function MyProfileRoute() {
  const [profiles, setProfiles] = React.useState<Profile[] | null>(
    useLiveApi ? null : demoProfiles,
  );
  const [loadError, setLoadError] = React.useState<'login' | 'failed' | null>(null);

  React.useEffect(() => {
    if (!useLiveApi) return;
    let active = true;
    api
      .profiles()
      .then((next) => {
        if (active) setProfiles(next);
      })
      .catch((e: unknown) => {
        if (active) setLoadError(e instanceof ApiError && e.status === 401 ? 'login' : 'failed');
      });
    return () => {
      active = false;
    };
  }, []);

  // 서버가 성공을 돌려준 뒤에는 목록을 다시 읽지 않고 화면에 바로 반영한다. 재조회 실패를 저장 실패로 보이면 다시 눌러 중복이 생긴다.
  async function create(input: ProfileInput) {
    const id = useLiveApi
      ? (await api.createProfile(input)).profile_id
      : `demo-profile-${Date.now()}`;
    setProfiles((list) => [{ profile_id: id, ...input }, ...(list ?? [])]);
  }

  async function update(profileId: string, input: ProfileInput) {
    if (useLiveApi) await api.updateProfile(profileId, input);
    setProfiles((list) =>
      (list ?? []).map((p) =>
        p.profile_id === profileId ? { profile_id: profileId, ...input } : p,
      ),
    );
  }

  async function remove(profileId: string) {
    if (useLiveApi) await api.deleteProfile(profileId);
    setProfiles((list) => (list ?? []).filter((p) => p.profile_id !== profileId));
  }

  return (
    <ProfilePage
      profiles={profiles}
      loadError={loadError}
      onCreate={create}
      onUpdate={update}
      onDelete={remove}
      reminder={useLiveApi ? <DeadlineReminderSwitch /> : undefined}
    />
  );
}

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

/** 폼이 없어진 것을 확인하면 이 브라우저에 남은 그 폼의 답과 파일을 지운다(SPEC 5절 208행). */
function forgetDraftsOfMissingForm(formId: string | undefined) {
  const ids = removeBrowserDraftsAt(window.location.pathname);
  if (formId !== undefined && !ids.includes(formId)) {
    removeBrowserDraft(formId);
    ids.push(formId);
  }
  const forget = (scope: string) =>
    ids.forEach((id) => void removeFileDraft(fileDraftKey(scope, id)).catch(() => undefined));
  forget(ANONYMOUS_SCOPE);
  api.me().then(
    (me) => forget(me.user_id),
    () => undefined,
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
        else if (e instanceof ApiError && e.status === 404) {
          setFailure('not_found');
          forgetDraftsOfMissingForm(formId);
        } else setFailure('failed');
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
  async function submit(payload: SubmitResponseInput, files?: SubmitFiles) {
    if (!form) return;
    setBusy(true);
    try {
      if (useLiveApi) {
        await (files?.files.length
          ? api.submitWithFiles(form.form_id, payload, files.files)
          : api.submit(form.form_id, payload));
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
  const location = useLocation();
  const notice = (location.state as { notice?: string } | null)?.notice;
  const [actionError, setActionError] = React.useState('');
  const [detailReloads, setDetailReloads] = React.useState(0);
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
    if (!useLiveApi) return;
    if (selectedId === undefined) {
      setLiveDetail({ status: 'idle' });
      return;
    }
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
  }, [selectedId, detailReloads]);

  // 거절되면 허용 여부가 달라졌을 수 있어 상세를 다시 읽는다.
  async function cancelResponse(id: string) {
    setActionError('');
    try {
      await api.cancelResponse(id);
      // 지운 응답의 내용이 다음 화면에 잠깐이라도 남지 않게 목록과 함께 비운다.
      setLiveDetail({ status: 'idle' });
      setLiveItems((list) => (list ?? []).filter((item) => item.response_id !== id));
      navigate(paths.myResponses, { replace: true, state: { notice: '응답을 취소했어요.' } });
    } catch (e) {
      setActionError(changeErrorText(e));
      setDetailReloads((n) => n + 1);
    }
  }

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
      notice={notice}
      actionError={actionError}
      onEdit={(id) => navigate(paths.myResponseEdit(id))}
      onCancel={cancelResponse}
    />
  );
}

/** 제출 후 수정(T10 #61). 그 응답의 제출 버전 정의로 응답 화면을 그대로 쓴다. 초안 저장소를 넘기지 않아 `PATCH`로만 저장한다. */
export function ResponseEditRoute() {
  const { responseId = '' } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = React.useState<ResponseHistoryDetail | null>(null);
  const [failure, setFailure] = React.useState<'not_found' | 'failed' | null>(null);
  const [busy, setBusy] = React.useState(false);
  React.useEffect(() => {
    if (!useLiveApi) return;
    let active = true;
    api
      .myResponse(responseId)
      .then((next) => {
        if (active) setDetail(next);
      })
      .catch((e: unknown) => {
        if (active) setFailure(e instanceof ApiError && e.status === 404 ? 'not_found' : 'failed');
      });
    return () => {
      active = false;
    };
  }, [responseId]);
  const back = () => navigate(paths.myResponse(responseId));

  async function save(payload: SubmitResponseInput, files?: SubmitFiles) {
    setBusy(true);
    try {
      await (files
        ? api.editResponse(responseId, payload.answers, files)
        : api.editResponse(responseId, payload.answers));
      navigate(paths.myResponse(responseId), { state: { notice: '답변을 수정했어요.' } });
    } finally {
      setBusy(false);
    }
  }

  const form = detail?.form_definition;
  if (!detail || !form || !detail.editable) {
    const message = !useLiveApi
      ? '가상 데이터 모드에서는 답변을 수정할 수 없어요.'
      : failure === 'not_found'
        ? '이 제출 내역을 찾을 수 없어요.'
        : failure === 'failed'
          ? '제출 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
          : detail
            ? '이 응답은 지금 수정할 수 없어요.'
            : '불러오는 중이에요.';
    return (
      <div className="page-shell response-page">
        <Heading eyebrow="내 워크스페이스 / 제출 내역 / 수정" title="답변 수정" />
        <Panel>
          <p className="muted">{message}</p>
          <Button variant="secondary" onClick={back}>
            제출 내역으로
          </Button>
        </Panel>
      </div>
    );
  }
  return (
    <ResponsePage
      form={form}
      onSubmit={save}
      saving={busy}
      onBack={back}
      editing={{ initialAnswers: answersToMap(detail.content), attachments: detail.attachments }}
    />
  );
}
