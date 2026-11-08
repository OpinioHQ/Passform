import React from 'react';
import { ApiError } from '../api/client';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import {
  type AnswerProblem,
  checkAnswer,
  formatHint,
  isAnswered,
  problemMessage,
} from '../response/answerCheck';
import { AutofillLoginHint, AutofillPanel } from '../response/AutofillPanel';
import { type AutosaveStatus, type DraftSink, useAutosave } from '../response/draftSync';
import {
  type AnswerMap,
  calculateResponsePath,
  pagesOf,
  remainingIsKnown,
} from '../response/responsePath';
import type {
  AnswerValue,
  AutofillResult,
  DisplayMode,
  PublicForm,
  Question,
  ResponseAnswer,
  SubmitResponseInput,
} from '../types/api';

interface Props {
  form: PublicForm;
  /** 성공하면 resolve, 서버가 거부하면 `ApiError`로 reject한다. 화면이 오류를 읽어 안내한다. */
  onSubmit: (data: SubmitResponseInput) => Promise<void>;
  saving: boolean;
  onBack?: () => void;
  /** 최신 폼을 다시 받는다(AC76). 없으면 같은 정의로 처음부터 다시 쓴다. */
  onReload?: () => void;
  /** 빌더 미리보기(AC81, E3 #81). 응답자 화면과 같게 동작하되 제출을 부르지 않는다(사람 결정). */
  preview?: boolean;
  /** 자동 저장(T5 #53). 없으면 저장하지 않는다(가짜 데이터 모드, 미리보기). */
  draft?: { sink: DraftSink; initialAnswers?: AnswerMap };
  /** 초안을 불러오지 못했을 때처럼 화면 위에 늘 보일 안내. */
  draftNotice?: string;
  /** 내 폼함에 저장(E5 #43, AC17). 로그인 사용자에게만 넘긴다. 없으면 버튼을 보이지 않는다. */
  onSaveToInbox?: () => Promise<void>;
  /** 이미 내 폼함에 있으면 버튼 대신 "저장된 폼"을 보인다. */
  savedInInbox?: boolean;
}

type Outcome = 'submitted' | 'closed' | 'version_changed';

const viewLabels: Record<DisplayMode, string> = {
  one_question: '한 질문씩',
  all_questions: '전체 질문 보기',
  section_page: '섹션별 보기',
};

const answerProblems: readonly string[] = [
  'required_missing',
  'invalid_input_format_value',
  'invalid_option',
  'character_limit_exceeded',
];

const seoulTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** 화면에 Asia/Seoul로 보여 준다(AGENTS.md 5절). */
export function deadlineText(deadline: string | null): string {
  return deadline ? `${seoulTime.format(new Date(deadline))}까지 받아요` : '마감 기한이 없어요';
}

/** `https`로 시작하는 올바른 URL만 링크로 연다(AC11). 서버도 저장할 때 막지만 화면에서 다시 확인한다. */
export function isHttpsLink(link: string): boolean {
  try {
    return new URL(link).protocol === 'https:';
  } catch {
    return false;
  }
}

export function PaymentLink({ link }: { link: string | null }) {
  if (!link) return null;
  return (
    <Panel className="payment-link">
      <h3>송금 안내</h3>
      <p className="small muted">
        제작자가 남긴 송금 링크예요. Passform은 송금을 처리하거나 송금했는지 확인하지 않아요.
      </p>
      {isHttpsLink(link) ? (
        <a href={link} target="_blank" rel="noopener noreferrer">
          {link}
        </a>
      ) : (
        <p className="small muted">안전하지 않은 주소라 링크를 열지 않아요.</p>
      )}
    </Panel>
  );
}

/** 닫혔거나 마감이 지난 폼(AC41). 공개 조회와 제출의 `409 form_closed`가 함께 쓴다. */
export function ClosedNotice({ title }: { title?: string }) {
  return (
    <Panel className="response-notice">
      <Badge tone="neutral">마감</Badge>
      <h2>{title ? `「${title}」은(는) 마감되었어요` : '응답이 마감된 폼이에요.'}</h2>
      <p className="muted">
        응답을 더 받지 않는 폼이에요. 궁금한 점은 폼을 보낸 사람에게 문의해 주세요.
      </p>
    </Panel>
  );
}

const seoulClock = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** 자동 저장 상태(SPEC 5절 205행). 실패는 성공한 척하지 않는다(AC82). */
function AutosaveStatusLine({
  kind,
  status,
  savedAt,
  restored,
}: {
  kind: DraftSink['kind'];
  status: AutosaveStatus;
  savedAt: Date | null;
  restored: boolean;
}) {
  const place = kind === 'account' ? '계정에' : '이 브라우저에';
  if (status === 'failed') {
    return (
      <small className="autosave-status failed" role="alert" data-testid="autosave-status">
        저장하지 못했어요. 마지막으로 저장된 내용은 그대로 있어요.
      </small>
    );
  }
  const text =
    status === 'pending' || status === 'saving'
      ? '저장 중…'
      : status === 'saved' && savedAt
        ? `${place} 저장됨 · ${seoulClock.format(savedAt)}`
        : restored
          ? `${place} 저장한 답을 불러왔어요.`
          : kind === 'account'
            ? '답을 입력하면 계정에 자동으로 저장돼요.'
            : '답은 이 브라우저에만 저장돼요. 저장 내용을 지우거나 다른 브라우저에서 열면 이어 쓸 수 없어요.';
  return (
    <small className="autosave-status" role="status" data-testid="autosave-status">
      {text}
    </small>
  );
}

function emptyValue(question: Question): AnswerValue {
  return question.question_type === 'checkbox' ? [] : '';
}

/** 응답 한 번. 제출 후 "새 응답 작성"을 누르면 바깥에서 새로 만든다. */
function ResponseForm({
  form,
  onSubmit,
  saving,
  onReload,
  onRestart,
  preview,
  draft,
  draftNotice,
}: Props & { onRestart: () => void }) {
  const defaultMode = form.display.default_mode;
  const switchable = form.display.allowed_modes.length > 1;
  const allQuestions = React.useMemo(() => form.sections.flatMap((s) => s.questions), [form]);
  const [answers, setAnswers] = React.useState<AnswerMap>(() => draft?.initialAnswers ?? {});
  // 요청이 끝날 때의 답을 본다. 기다리는 동안 쓴 답을 덮지 않기 위해서다.
  const latestAnswers = React.useRef(answers);
  latestAnswers.current = answers;
  // 프로필에서 채웠고 아직 고치지 않은 값.
  const [suggested, setSuggested] = React.useState<Record<string, AnswerValue>>({});
  const latestSuggested = React.useRef(suggested);
  latestSuggested.current = suggested;
  const [autofillNotice, setAutofillNotice] = React.useState('');
  const sink = draft?.sink ?? null;
  const autosave = useAutosave(sink);
  const restored = Boolean(draft?.initialAnswers && Object.keys(draft.initialAnswers).length > 0);
  const [view, setView] = React.useState<DisplayMode>(defaultMode);
  const [started, setStarted] = React.useState(defaultMode !== 'one_question');
  const [pageIndex, setPageIndex] = React.useState(0);
  const [errors, setErrors] = React.useState<Record<string, string>>({});
  const [consent, setConsent] = React.useState(false);
  const [aiConsent, setAiConsent] = React.useState(false);
  const [consentError, setConsentError] = React.useState('');
  const [formError, setFormError] = React.useState('');
  const [outcome, setOutcome] = React.useState<Outcome | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [previewNotice, setPreviewNotice] = React.useState(false);
  // 개인정보 동의 내용은 평소에 접어 두고 버튼으로 펼친다(이슈 #41 결정). AI 요약 고지도 같은 박스에서 접어 둔다(사람 결정, #42).
  const [consentOpen, setConsentOpen] = React.useState(false);
  const consentDetailId = React.useId();
  const [aiNoticeOpen, setAiNoticeOpen] = React.useState(false);
  const aiNoticeId = React.useId();
  const [focusId, setFocusId] = React.useState<string | null>(null);
  const [scrollTarget, setScrollTarget] = React.useState<string | null>(null);

  const paged = view !== 'all_questions';
  const pages = paged ? pagesOf(form.sections, defaultMode, view, answers) : [];
  const current = Math.min(pageIndex, pages.length);
  const onConsentPage = paged && current === pages.length;

  React.useEffect(() => {
    if (!scrollTarget) return;
    document.getElementById(`question-${scrollTarget}`)?.scrollIntoView?.({ block: 'center' });
    setScrollTarget(null);
  }, [scrollTarget]);

  function update(question: Question, value: AnswerValue) {
    const next = { ...answers, [question.question_id]: value };
    setAnswers(next);
    // 답이 바뀌면 자동 저장한다. 초안 저장은 동의나 제출이 아니다(SPEC 5절 205·207행).
    autosave.schedule(next);
    setSuggested((prev) => {
      if (!(question.question_id in prev)) return prev;
      const remaining = { ...prev };
      delete remaining[question.question_id];
      return remaining;
    });
    // 고친 문항의 지난 오류만 지운다. 새 오류는 "다음"이나 "제출"을 누를 때만 알린다.
    setErrors((prev) => {
      if (!(question.question_id in prev)) return prev;
      const next = { ...prev };
      delete next[question.question_id];
      return next;
    });
  }

  // 사람이 정한 것(2026-11-08): 빈 문항만 채운다. 아직 고치지 않은 제안값은 빈 문항으로 보고 새 프로필 값으로 바꾼다.
  // 채우기만으로는 저장하지 않고, 응답자가 답을 직접 고칠 때 채운 값까지 함께 저장한다.
  // 지금 화면에 없는 문항(그 사이 새로 배포된 버전)의 값은 무시한다.
  function applyAutofill(result: AutofillResult) {
    const current = latestAnswers.current;
    const byId = new Map(allQuestions.map((q) => [q.question_id, q]));
    const next = { ...current };
    for (const id of Object.keys(latestSuggested.current)) delete next[id];
    const filled: Record<string, AnswerValue> = {};
    let kept = 0;
    for (const { question_id, value } of result.answers) {
      const question = byId.get(question_id);
      if (!question) continue;
      if (isAnswered(question, next[question_id])) {
        kept += 1;
      } else {
        next[question_id] = value;
        filled[question_id] = value;
      }
    }
    const count = Object.keys(filled).length;
    setAnswers(next);
    setSuggested(filled);
    setErrors((prev) => Object.fromEntries(Object.entries(prev).filter(([id]) => !(id in filled))));
    const parts = [
      count > 0
        ? `프로필 값 ${count}개를 채웠어요. 제출 전에 확인하고 필요하면 고쳐 주세요.`
        : '채울 수 있는 빈 문항이 없어요.',
    ];
    if (kept > 0) parts.push(`이미 쓴 답 ${kept}개는 그대로 뒀어요.`);
    setAutofillNotice(parts.join(' '));
  }

  // 버전이 바뀐 뒤 "최신 폼 다시 열기"는 옛 답을 지우고 새로 시작하는 선택이다(T4 사람 결정, SPEC 5절 208행).
  // 지우지 못해도 다시 연다. 그때는 옛 초안 안내가 다시 지울 기회를 준다.
  async function reopenLatest() {
    const settled = autosave.cancel();
    // 이미 보낸 저장이 끝난 뒤 지운다. 지운 뒤에 저장이 처리되면 초안이 되살아난다.
    if (sink) {
      await settled;
      await sink.clear().catch(() => undefined);
    }
    (onReload ?? onRestart)();
  }

  // 응답자가 작성 중 초안을 직접 지운다(SPEC 5절 208행). 지우지 못하면 답은 그대로 둔다.
  async function discardDraft() {
    if (!sink) return;
    // 이미 보낸 저장이 끝난 뒤 지운다. 지운 뒤에 저장이 처리되면 초안이 되살아난다.
    await autosave.cancel();
    try {
      await sink.clear();
      onRestart();
    } catch {
      setFormError('작성한 답을 지우지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
  }

  function problemsOf(questions: Question[]): Record<string, string> {
    const found: Record<string, string> = {};
    for (const question of questions) {
      const problem = checkAnswer(question, answers[question.question_id]);
      if (problem) found[question.question_id] = problemMessage(question, problem);
    }
    return found;
  }

  /** 보던 문항. 보기를 바꿔도 이 문항으로 간다(AC47). */
  function currentQuestionId(): string | null {
    if (paged)
      return onConsentPage ? 'consent' : (pages[current]?.questions[0]?.question_id ?? null);
    return focusId;
  }

  function switchView(next: DisplayMode) {
    if (next === view) return;
    const target = currentQuestionId();
    if (next === 'all_questions') {
      // 전체 보기의 현재 문항을 보던 문항으로 맞춘다. 예전 초점이 남으면 되돌아올 때 그 문항으로 간다(PR #82 리뷰).
      setFocusId(target);
      setScrollTarget(target);
    } else {
      const nextPages = pagesOf(form.sections, defaultMode, 'one_question', answers);
      const at =
        target === 'consent'
          ? nextPages.length
          : nextPages.findIndex((p) => p.questions[0].question_id === target);
      setPageIndex(at < 0 ? 0 : at);
      setStarted(true);
    }
    setView(next);
  }

  function goNext() {
    const found = problemsOf(pages[current].questions);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setPageIndex(current + 1);
  }

  function showServerProblem(code: AnswerProblem, ids: string[]) {
    const byId = new Map(allQuestions.map((q) => [q.question_id, q]));
    const found: Record<string, string> = {};
    ids.forEach((id) => {
      const question = byId.get(id);
      if (question) found[id] = problemMessage(question, code);
    });
    setErrors(found);
    if (paged) {
      const at = pages.findIndex((page) => page.questions.some((q) => q.question_id in found));
      if (at >= 0) setPageIndex(at);
    } else if (ids[0]) {
      setScrollTarget(ids[0]);
    }
  }

  async function submit() {
    setFormError('');
    setConsentError('');
    const onPath = calculateResponsePath(form.sections, defaultMode, answers).onPath;
    const found = problemsOf(onPath);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      const first = onPath.find((q) => q.question_id in found);
      if (paged)
        setPageIndex(
          Math.max(
            0,
            pages.findIndex((p) => p.questions.some((q) => q === first)),
          ),
        );
      else if (first) setScrollTarget(first.question_id);
      return;
    }
    // 미리보기는 같은 검사를 거친 뒤 제출만 하지 않는다. 응답·결과 집계를 만들지 않는다(SPEC 5절 181행).
    if (preview) {
      setPreviewNotice(true);
      return;
    }
    setBusy(true);
    // 기다리던 자동 저장을 먼저 끝낸다. 그래야 계정 초안이 하나뿐이고 그 ID로 제출한다(SPEC 5절 216행).
    await autosave.flush();
    const draftResponseId = sink?.draftResponseId() ?? null;
    // 경로는 보내지 않는다. 건너뛴 문항의 답은 서버가 다시 계산해 뺀다(AGENTS.md 5절).
    const body: SubmitResponseInput = {
      form_version: form.definition_version,
      answers: allQuestions
        .filter((q) => isAnswered(q, answers[q.question_id]))
        .map((q): ResponseAnswer => ({
          question_id: q.question_id,
          value: answers[q.question_id] as AnswerValue,
        })),
      consent_agreed: true,
      ...(form.consent.ai_summary ? { ai_summary_agreed: aiConsent } : {}),
      ...(draftResponseId ? { draft_response_id: draftResponseId } : {}),
    };
    try {
      await onSubmit(body);
      // 성공한 뒤에만 초안을 정리한다. 실패하면 서버 초안과 브라우저 초안을 그대로 둔다.
      void autosave.cancel();
      sink?.submitted();
      setOutcome('submitted');
    } catch (e) {
      const detail =
        e instanceof ApiError && typeof e.detail === 'object' && e.detail !== null
          ? (e.detail as { error?: unknown; question_ids?: unknown })
          : {};
      const code = typeof detail.error === 'string' ? detail.error : '';
      const ids = Array.isArray(detail.question_ids)
        ? detail.question_ids.filter((id): id is string => typeof id === 'string')
        : [];
      if (code === 'form_closed') setOutcome('closed');
      else if (code === 'form_version_changed') setOutcome('version_changed');
      else if (code === 'consent_required')
        setConsentError('필수 동의가 필요해요. 동의해야 제출할 수 있어요.');
      else if (answerProblems.includes(code)) showServerProblem(code as AnswerProblem, ids);
      else if (e instanceof ApiError && e.status === 404)
        setFormError('폼을 찾을 수 없어요. 삭제되었을 수 있어요.');
      else setFormError('제출하지 못했어요. 쓴 답은 그대로 있으니 잠시 후 다시 시도해 주세요.');
    } finally {
      setBusy(false);
    }
  }

  if (outcome === 'closed') return <ClosedNotice title={form.title} />;
  if (outcome === 'version_changed') {
    return (
      <Panel className="response-notice">
        <Badge tone="amber">폼이 바뀌었어요</Badge>
        <h2>제작자가 폼을 새로 배포했어요</h2>
        <p className="muted">
          열어 둔 폼이 최신이 아니라 제출하지 않았어요. 최신 폼을 다시 열어 처음부터 응답해 주세요.
        </p>
        <Button onClick={() => void reopenLatest()}>최신 폼 다시 열기</Button>
      </Panel>
    );
  }
  if (outcome === 'submitted') {
    return (
      <>
        <Panel className="response-notice">
          <Badge tone="green">제출 완료</Badge>
          <h2>응답을 제출했어요</h2>
          <p className="muted">「{form.title}」에 응답해 주셔서 고마워요.</p>
          <Button variant="secondary" onClick={onRestart}>
            새 응답 작성
          </Button>
        </Panel>
        <PaymentLink link={form.payment_link} />
      </>
    );
  }

  function renderInput(question: Question) {
    const id = question.question_id;
    const value = answers[id] ?? emptyValue(question);
    const describedBy =
      [
        question.question_description ? `desc-${id}` : '',
        question.input_format ? `hint-${id}` : '',
        errors[id] ? `error-${id}` : '',
      ]
        .filter(Boolean)
        .join(' ') || undefined;
    const invalid = errors[id] ? true : undefined;
    if (question.question_type === 'dropdown') {
      return (
        <select
          id={`input-${id}`}
          value={value as string}
          aria-describedby={describedBy}
          aria-invalid={invalid}
          onChange={(e) => update(question, e.target.value)}
        >
          <option value="">선택해 주세요</option>
          {question.options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      );
    }
    if (question.question_type === 'multiple_choice' || question.question_type === 'checkbox') {
      const multiple = question.question_type === 'checkbox';
      const chosen = Array.isArray(value) ? value : value ? [value] : [];
      return (
        <div
          className="option-stack"
          role="group"
          aria-labelledby={`label-${id}`}
          aria-describedby={describedBy}
        >
          {question.options.map((option) => {
            const checked = chosen.includes(option);
            return (
              <label className={`option ${checked ? 'checked' : ''}`} key={option}>
                <input
                  type={multiple ? 'checkbox' : 'radio'}
                  name={id}
                  checked={checked}
                  onChange={() =>
                    update(
                      question,
                      multiple
                        ? checked
                          ? chosen.filter((item) => item !== option)
                          : question.options.filter(
                              (item) => item === option || chosen.includes(item),
                            )
                        : option,
                    )
                  }
                />
                {option}
              </label>
            );
          })}
        </div>
      );
    }
    const text = value as string;
    const limit = question.character_limit ?? null;
    const counter = limit ? (
      <span
        className={`field-count ${text.length > limit ? 'over' : ''}`}
        aria-label={`글자 수 ${text.length}/${limit}`}
      >
        {text.length}/{limit}
      </span>
    ) : null;
    if (question.question_type === 'long_answer') {
      return (
        <>
          <textarea
            id={`input-${id}`}
            value={text}
            rows={4}
            aria-describedby={describedBy}
            aria-invalid={invalid}
            onChange={(e) => update(question, e.target.value)}
          />
          {counter}
        </>
      );
    }
    const kind = question.input_format?.kind;
    const type =
      kind === 'email'
        ? 'email'
        : kind === 'mobile_phone' || kind === 'landline_phone'
          ? 'tel'
          : 'text';
    return (
      <>
        <input
          id={`input-${id}`}
          type={type}
          inputMode={kind === 'date' ? 'numeric' : undefined}
          value={text}
          placeholder={
            question.input_format?.placeholder ?? (kind === 'date' ? 'YYYY-MM-DD' : undefined)
          }
          aria-describedby={describedBy}
          aria-invalid={invalid}
          onChange={(e) => update(question, e.target.value)}
        />
        {counter}
      </>
    );
  }

  function renderQuestion(question: Question, number: number) {
    const id = question.question_id;
    const choiceGroup =
      question.question_type === 'multiple_choice' || question.question_type === 'checkbox';
    return (
      <div
        className="question"
        key={id}
        id={`question-${id}`}
        onFocusCapture={() => setFocusId(id)}
      >
        <div className="question-heading">
          <span className="question-num">{String(number).padStart(2, '0')}</span>
          {choiceGroup ? (
            <span className="question-label" id={`label-${id}`}>
              {question.question_note}
            </span>
          ) : (
            <label className="question-label" id={`label-${id}`} htmlFor={`input-${id}`}>
              {question.question_note}
            </label>
          )}
          <Badge tone={question.required ? 'blue' : 'neutral'}>
            {question.required ? '필수' : '선택'}
          </Badge>
        </div>
        {question.question_description && (
          <p className="question-desc muted" id={`desc-${id}`}>
            {question.question_description}
          </p>
        )}
        {question.attachments.length > 0 && (
          <ul className="attachment-list">
            {question.attachments.map((file) => (
              <li key={file.attachment_id}>{file.filename}</li>
            ))}
          </ul>
        )}
        <div className="question-input">{renderInput(question)}</div>
        {id in suggested && (
          <p className="field-hint autofill-suggested" data-testid={`suggested-${id}`}>
            프로필에서 채운 값이에요. 고칠 수 있어요.
          </p>
        )}
        {question.input_format && (
          <p className="field-hint muted" id={`hint-${id}`}>
            {formatHint(question.input_format.kind)}
          </p>
        )}
        {errors[id] && (
          <p className="field-error" id={`error-${id}`} role="alert">
            {errors[id]}
          </p>
        )}
      </div>
    );
  }

  const numberOf = new Map(allQuestions.map((q, i) => [q.question_id, i + 1]));

  function consentBlock() {
    const notice = form.consent.ai_summary;
    return (
      <div id="question-consent">
        {/* 필수·선택 동의를 한 박스에 두고 체크박스는 따로 둔다. 상세는 각 문구 옆 "내용 보기"로 편다(사람 결정, #42). */}
        <Panel className="consent">
          <h3>개인정보 수집·이용 동의</h3>
          <div className="button-row">
            <label>
              <input
                type="checkbox"
                checked={consent}
                onChange={(e) => setConsent(e.target.checked)}
              />
              개인정보 수집·이용에 동의합니다 (필수)
            </label>
            <button
              type="button"
              className="btn btn-ghost consent-toggle"
              aria-expanded={consentOpen}
              aria-controls={consentDetailId}
              onClick={() => setConsentOpen((open) => !open)}
            >
              {consentOpen ? '내용 접기' : '내용 보기'}
            </button>
          </div>
          {consentOpen && (
            <dl className="consent-terms" id={consentDetailId}>
              <dt>수집 항목</dt>
              <dd>{form.consent.items.join(', ')}</dd>
              <dt>목적</dt>
              <dd>{form.consent.purpose}</dd>
              <dt>보유 기간</dt>
              <dd className="keep-lines">{form.consent.retention}</dd>
              <dt>동의 거부</dt>
              <dd>{form.consent.refusal_notice}</dd>
            </dl>
          )}
          {consentError && (
            <p className="field-error" role="alert">
              {consentError}
            </p>
          )}
          {notice && (
            <>
              <div className="button-row ai-consent">
                <label>
                  <input
                    type="checkbox"
                    checked={aiConsent}
                    onChange={(e) => setAiConsent(e.target.checked)}
                  />
                  AI 요약을 위한 외부 전송에 동의합니다 (선택)
                </label>
                <button
                  type="button"
                  className="btn btn-ghost consent-toggle"
                  aria-label={`AI 요약을 위한 외부 전송 안내 ${aiNoticeOpen ? '접기' : '보기'}`}
                  aria-expanded={aiNoticeOpen}
                  aria-controls={aiNoticeId}
                  onClick={() => setAiNoticeOpen((open) => !open)}
                >
                  {aiNoticeOpen ? '내용 접기' : '내용 보기'}
                </button>
              </div>
              {aiNoticeOpen && (
                <dl className="consent-terms" id={aiNoticeId}>
                  <dt>제공받는 곳</dt>
                  <dd>{notice.provider}</dd>
                  <dt>처리 국가</dt>
                  <dd>{notice.processing_country}</dd>
                  <dt>항목</dt>
                  <dd>{notice.items.join(', ')}</dd>
                  <dt>목적</dt>
                  <dd>{notice.purpose}</dd>
                  <dt>보유 기간</dt>
                  <dd>{notice.retention}</dd>
                  <dt>거부하면</dt>
                  <dd>{notice.refusal_effect}</dd>
                  <dt>확인일</dt>
                  <dd>{notice.checked_on}</dd>
                </dl>
              )}
            </>
          )}
        </Panel>
      </div>
    );
  }

  const submitting = busy || saving;
  const submitRow = (
    <div className="form-footer">
      {paged && (
        <Button
          variant="secondary"
          onClick={() => setPageIndex(current - 1)}
          disabled={current === 0}
        >
          이전
        </Button>
      )}
      <span className="small muted">
        {consent
          ? '제출하면 제작자에게 응답이 전달돼요.'
          : '필수 동의에 체크하면 제출할 수 있어요.'}
      </span>
      <Button onClick={submit} disabled={!consent || submitting}>
        {submitting ? '제출 중…' : '답변 제출'} <Icon name="arrow" size={16} />
      </Button>
      {previewNotice && (
        <p className="info" role="status">
          미리보기라 제출되지 않아요.
        </p>
      )}
    </div>
  );

  function progress() {
    if (!paged) {
      const answered = allQuestions.filter((q) => isAnswered(q, answers[q.question_id])).length;
      return {
        label: `답한 문항 ${answered}/${allQuestions.length}`,
        detail: '모든 문항을 한 화면에 보여 드려요.',
        ratio: answered / Math.max(1, allQuestions.length),
      };
    }
    if (!started)
      return { label: '시작 전', detail: '시작하면 지금 위치를 보여 드려요.', ratio: null };
    if (onConsentPage)
      return { label: '마지막 단계', detail: '동의를 확인하고 제출해 주세요.', ratio: 1 };
    const sectionsOnPath = pages
      .map((p) => p.section)
      .filter((s, i, list) => list.indexOf(s) === i);
    const section = pages[current].section;
    const sectionAt = sectionsOnPath.indexOf(section) + 1;
    if (!remainingIsKnown(pages, current, answers)) {
      return {
        label: `${sectionAt}번째 섹션 · ${section.title}${view === 'one_question' ? ` · ${current + 1}번째 문항` : ''}`,
        detail: '남은 분량은 답에 따라 달라져요.',
        ratio: null,
      };
    }
    const sectionsLeft = sectionsOnPath.length - sectionAt;
    return {
      label: `섹션 ${sectionAt}/${sectionsOnPath.length} · ${section.title}${view === 'one_question' ? ` · 문항 ${current + 1}/${pages.length}` : ''}`,
      detail: `남은 섹션 ${sectionsLeft}개${view === 'one_question' ? ` · 남은 문항 ${pages.length - current - 1}개` : ''}`,
      ratio: current / pages.length,
    };
  }
  const shown = progress();

  return (
    <>
      <Panel className="response-hero">
        <div className="response-hero-text">
          <Badge tone={preview ? 'amber' : 'blue'}>{preview ? '미리보기' : '응답 받는 중'}</Badge>
          <h2>{form.title}</h2>
          {form.note.trim() && <p className="form-note">{form.note}</p>}
          <p className="muted">{deadlineText(form.deadline)}</p>
          {form.note_attachments.length > 0 && (
            <ul className="attachment-list">
              {form.note_attachments.map((file) => (
                <li key={file.attachment_id}>{file.filename}</li>
              ))}
            </ul>
          )}
        </div>
        <div className="completion" aria-label="작성 진행">
          <span>작성 진행</span>
          <strong data-testid="progress-label">{shown.label}</strong>
          {shown.ratio !== null && (
            <div className="track" aria-hidden="true">
              <div style={{ width: `${Math.round(shown.ratio * 100)}%` }} />
            </div>
          )}
          <small className="muted" data-testid="progress-detail">
            {shown.detail}
          </small>
          {sink && (
            <AutosaveStatusLine
              kind={sink.kind}
              status={autosave.status}
              savedAt={autosave.savedAt}
              restored={restored}
            />
          )}
        </div>
      </Panel>
      {draftNotice && (
        <p className="info" role="status">
          {draftNotice}
        </p>
      )}
      {sink && (restored || autosave.status !== 'idle') && (
        <div className="form-footer draft-actions">
          <Button variant="ghost" onClick={() => void discardDraft()}>
            작성한 답 지우기
          </Button>
        </div>
      )}
      <PaymentLink link={form.payment_link} />
      {!preview &&
        sink &&
        allQuestions.some((q) => q.profile_key) &&
        (sink.kind === 'account' ? (
          <AutofillPanel
            formId={form.form_id}
            onFilled={applyAutofill}
            onClosed={() => setOutcome('closed')}
          />
        ) : (
          <AutofillLoginHint />
        ))}
      {autofillNotice && (
        <p className="info" role="status">
          {autofillNotice}
        </p>
      )}
      {switchable && (
        <div className="view-switch" role="group" aria-label="보기 방식">
          {form.display.allowed_modes.map((mode) => (
            <button
              key={mode}
              type="button"
              className={`view-switch-btn ${view === mode ? 'active' : ''}`}
              aria-pressed={view === mode}
              onClick={() => switchView(mode)}
            >
              {viewLabels[mode]}
            </button>
          ))}
        </div>
      )}
      {formError && (
        <p className="error" role="alert">
          {formError}
        </p>
      )}
      {!paged &&
        form.sections.map((section) => (
          <Panel className="questions" key={section.section_id}>
            <div className="section-title">
              <h3>{section.title}</h3>
            </div>
            {section.questions.map((q) => renderQuestion(q, numberOf.get(q.question_id) ?? 0))}
          </Panel>
        ))}
      {!paged && consentBlock()}
      {!paged && submitRow}
      {paged && !started && (
        <div className="form-footer">
          <Button onClick={() => setStarted(true)}>
            시작하기 <Icon name="arrow" size={16} />
          </Button>
        </div>
      )}
      {paged && started && !onConsentPage && (
        <>
          <Panel className="questions">
            <div className="section-title">
              <h3>{pages[current].section.title}</h3>
            </div>
            {pages[current].questions.map((q) =>
              renderQuestion(q, numberOf.get(q.question_id) ?? 0),
            )}
          </Panel>
          <div className="form-footer">
            <Button
              variant="secondary"
              onClick={() => setPageIndex(current - 1)}
              disabled={current === 0}
            >
              이전
            </Button>
            <Button onClick={goNext}>
              다음 <Icon name="arrow" size={16} />
            </Button>
          </div>
        </>
      )}
      {paged && started && onConsentPage && (
        <>
          {consentBlock()}
          {submitRow}
        </>
      )}
    </>
  );
}

function SaveToInbox({ onSave, saved }: { onSave: () => Promise<void>; saved: boolean }) {
  const [state, setState] = React.useState<'idle' | 'saving' | 'saved' | 'failed'>('idle');
  if (saved && state !== 'saved') return <Badge tone="neutral">저장된 폼</Badge>;
  if (state === 'saved') {
    return (
      <span className="small muted" role="status">
        내 폼함에 저장했어요
      </span>
    );
  }
  return (
    <>
      <Button
        variant="secondary"
        disabled={state === 'saving'}
        onClick={() => {
          setState('saving');
          onSave().then(
            () => setState('saved'),
            () => setState('failed'),
          );
        }}
      >
        <Icon name="bookmark" size={16} /> 내 폼함에 저장
      </Button>
      {state === 'failed' && (
        <span className="small muted" role="alert">
          저장하지 못했어요. 다시 시도해 주세요.
        </span>
      )}
    </>
  );
}

/** `/f/:slug`, `/forms/:formId`의 응답 화면(T4 #52). 로그인 없이 쓸 수 있다. */
export function ResponsePage(props: Props) {
  const [attempt, setAttempt] = React.useState(0);
  return (
    <div className="page-shell response-page">
      <Heading
        eyebrow={props.preview ? '제작자 워크스페이스 / 폼 편집 / 미리보기' : '응답 작성'}
        title={props.preview ? '미리보기' : '응답 작성'}
        desc={
          props.preview
            ? '응답자에게 보이는 모습이에요. 저장 전 내용이고, 답을 입력해도 응답이 만들어지지 않아요.'
            : '질문을 확인하고, 편한 속도로 답변해 주세요.'
        }
        action={
          (props.onBack || props.onSaveToInbox) && (
            <div className="button-row">
              {props.onSaveToInbox && !props.preview && (
                <SaveToInbox onSave={props.onSaveToInbox} saved={props.savedInInbox ?? false} />
              )}
              {props.onBack && (
                <Button variant="secondary" onClick={props.onBack}>
                  <Icon name="back" size={16} />{' '}
                  {props.preview ? '편집으로 돌아가기' : '내 폼함으로'}
                </Button>
              )}
            </div>
          )
        }
      />
      <ResponseForm
        key={attempt}
        {...props}
        // 복원은 처음 한 번만 한다. 지우거나 새로 쓰면 빈 답으로 시작하고 같은 저장소에 이어 저장한다.
        draft={attempt === 0 || !props.draft ? props.draft : { sink: props.draft.sink }}
        onRestart={() => setAttempt((n) => n + 1)}
      />
    </div>
  );
}
