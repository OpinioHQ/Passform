/**
 * 공유 링크로 연 폼의 초안을 정한다(T5 #53). 응답 화면에 줄 초안 저장소와 복원할 답을 고르고,
 * 옛 버전 초안이면 기존 문항과 함께 보여 주고 지운 뒤 새로 시작하게 한다(SPEC 5절 208행).
 * - 로그인: 계정 초안을 먼저 본다. 없고 이 브라우저에 같은 버전의 초안이 있으면 이어 쓸지 묻는다(사람 결정).
 * - 비로그인: 이 브라우저의 초안만 본다. 다른 브라우저에는 없다.
 */
import React from 'react';
import { ApiError, api } from '../api/client';
import { Badge, Button, Panel } from '../components/UI';
import { ResponsePage } from '../pages/ResponsePage';
import type { AnswerValue, PublicForm, ResponseAnswer, SubmitResponseInput } from '../types/api';
import {
  type DraftSink,
  accountSink,
  answersToMap,
  browserSink,
  readBrowserDraft,
  removeBrowserDraft,
} from './draftSync';
import type { AnswerMap } from './responsePath';

type Session =
  | { step: 'loading' }
  | { step: 'ready'; sink: DraftSink | null; answers?: AnswerMap; notice?: string }
  | {
      step: 'stale';
      sink: DraftSink;
      version: number;
      definition: PublicForm;
      answers: ResponseAnswer[];
    }
  | { step: 'import'; sink: DraftSink; answers: ResponseAnswer[] };

interface Props {
  form: PublicForm;
  onSubmit: (data: SubmitResponseInput) => Promise<void>;
  saving: boolean;
  onBack?: () => void;
  onReload?: () => void;
  onSaveToInbox?: () => Promise<void>;
  savedInInbox?: boolean;
}

function isDraftShape(value: unknown): value is { response_id: string; form_version: number } {
  const draft = value as {
    response_id?: unknown;
    form_version?: unknown;
    answers?: unknown;
  } | null;
  return (
    typeof draft === 'object' &&
    draft !== null &&
    typeof draft.response_id === 'string' &&
    typeof draft.form_version === 'number' &&
    Array.isArray(draft.answers)
  );
}

const AUTOSAVE_STOPPED = '작성 중인 답을 불러오지 못해 자동 저장을 멈췄어요. 새로고침해 주세요.';

/**
 * 401일 때만 비로그인으로 본다. 그 밖의 실패(서버 오류, 네트워크 오류)는 로그인 여부를 모르므로
 * `null`을 돌려준다. 로그인 사용자의 답이 이 브라우저에 남지 않게 하기 위해서다(AGENTS 3절 1항).
 */
async function loggedIn(): Promise<boolean | null> {
  try {
    await api.me();
    return true;
  } catch (e) {
    return e instanceof ApiError && e.status === 401 ? false : null;
  }
}

async function decide(form: PublicForm): Promise<Session> {
  const browser = readBrowserDraft(form.form_id);
  const login = await loggedIn();
  if (login === null) return { step: 'ready', sink: null, notice: AUTOSAVE_STOPPED };
  if (!login) {
    const sink = browserSink(form);
    if (!browser) return { step: 'ready', sink };
    if (browser.form_version !== form.definition_version) {
      return {
        step: 'stale',
        sink,
        version: browser.form_version,
        definition: browser.form_definition,
        answers: browser.answers,
      };
    }
    return { step: 'ready', sink, answers: answersToMap(browser.answers) };
  }
  try {
    const draft = await api.responseDraft(form.form_id);
    if (!isDraftShape(draft)) throw new Error('초안 모양이 아니다');
    const sink = accountSink(form, draft.response_id);
    if (draft.form_version !== form.definition_version) {
      return {
        step: 'stale',
        sink,
        version: draft.form_version,
        definition: draft.form_definition,
        answers: draft.answers,
      };
    }
    return { step: 'ready', sink, answers: answersToMap(draft.answers) };
  } catch (e) {
    if (!(e instanceof ApiError && e.status === 404)) {
      // 계정 초안을 읽지 못하면 자동 저장을 끈다. 빈 답으로 저장된 초안을 덮지 않기 위해서다.
      return {
        step: 'ready',
        sink: null,
        notice: AUTOSAVE_STOPPED,
      };
    }
  }
  const sink = accountSink(form, null);
  if (browser && browser.form_version === form.definition_version && browser.answers.length > 0) {
    return { step: 'import', sink, answers: browser.answers };
  }
  return { step: 'ready', sink };
}

function valueText(value: AnswerValue): string {
  return Array.isArray(value) ? value.join(', ') : value;
}

export function DraftGate({ form, ...rest }: Props) {
  const [session, setSession] = React.useState<Session>({ step: 'loading' });
  const [error, setError] = React.useState('');
  const [busy, setBusy] = React.useState(false);
  React.useEffect(() => {
    let active = true;
    void decide(form).then((next) => {
      if (active) setSession(next);
    });
    return () => {
      active = false;
    };
  }, [form]);

  async function startOver(sink: DraftSink) {
    setBusy(true);
    setError('');
    try {
      await sink.clear();
      setSession({
        step: 'ready',
        sink: sink.kind === 'account' ? accountSink(form, null) : browserSink(form),
      });
    } catch {
      setError('이전 답을 지우지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setBusy(false);
    }
  }

  async function importBrowser(sink: DraftSink, answers: ResponseAnswer[]) {
    setBusy(true);
    setError('');
    try {
      await sink.save(answers);
      removeBrowserDraft(form.form_id);
      setSession({ step: 'ready', sink, answers: answersToMap(answers) });
    } catch {
      setError(
        '계정에 옮기지 못했어요. 브라우저의 답은 그대로 있어요. 잠시 후 다시 시도해 주세요.',
      );
    } finally {
      setBusy(false);
    }
  }

  if (session.step === 'loading') {
    return (
      <div className="page-shell response-page">
        <Panel>
          <p className="muted">작성 중인 답을 확인하고 있어요.</p>
        </Panel>
      </div>
    );
  }

  if (session.step === 'stale') {
    const byId = new Map(session.answers.map((a) => [a.question_id, a.value]));
    return (
      <div className="page-shell response-page">
        <Panel className="response-notice">
          <Badge tone="amber">폼이 바뀌었어요</Badge>
          <h2>작성하던 답은 이전 버전 기준이에요</h2>
          <p className="muted">
            제작자가 폼을 새로 배포해서 이 답은 그대로 제출할 수 없어요. 아래에서 저장된 답을 확인한
            뒤, 지우고 최신 폼으로 새로 시작해 주세요.
          </p>
          <Button onClick={() => void startOver(session.sink)} disabled={busy}>
            기존 답 지우고 새로 시작
          </Button>
          {error && (
            <p className="field-error" role="alert">
              {error}
            </p>
          )}
        </Panel>
        <Panel className="questions stale-draft">
          <h3>
            {session.definition.title} · 폼 버전 {session.version}에서 저장한 답
          </h3>
          <dl className="consent-terms">
            {session.definition.sections
              .flatMap((s) => s.questions)
              .filter((q) => byId.has(q.question_id))
              .map((q) => (
                <React.Fragment key={q.question_id}>
                  <dt>{q.question_note}</dt>
                  <dd>{valueText(byId.get(q.question_id) as AnswerValue)}</dd>
                </React.Fragment>
              ))}
          </dl>
        </Panel>
      </div>
    );
  }

  if (session.step === 'import') {
    return (
      <div className="page-shell response-page">
        <Panel className="response-notice">
          <Badge tone="blue">이어서 쓰기</Badge>
          <h2>이 브라우저에 로그인 전에 쓰던 답이 있어요</h2>
          <p className="muted">이어서 쓰면 계정에 저장하고 이 브라우저의 답은 지워요.</p>
          <div className="button-row">
            <Button
              onClick={() => void importBrowser(session.sink, session.answers)}
              disabled={busy}
            >
              이어서 쓰기
            </Button>
            <Button
              variant="secondary"
              onClick={() => setSession({ step: 'ready', sink: session.sink })}
              disabled={busy}
            >
              새로 쓰기
            </Button>
          </div>
          {error && (
            <p className="field-error" role="alert">
              {error}
            </p>
          )}
        </Panel>
      </div>
    );
  }

  return (
    <ResponsePage
      form={form}
      {...rest}
      draft={session.sink ? { sink: session.sink, initialAnswers: session.answers } : undefined}
      draftNotice={session.notice}
    />
  );
}
