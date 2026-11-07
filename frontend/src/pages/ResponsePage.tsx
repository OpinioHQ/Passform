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
import {
  type AnswerMap,
  calculateResponsePath,
  pagesOf,
  remainingIsKnown,
} from '../response/responsePath';
import type {
  AnswerValue,
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
      <h2>{title ? `「${title}」은(는) 마감되었어요` : '마감된 폼이에요'}</h2>
      <p className="muted">
        응답을 더 받지 않는 폼이에요. 궁금한 점은 폼을 보낸 사람에게 문의해 주세요.
      </p>
    </Panel>
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
}: Props & { onRestart: () => void }) {
  const defaultMode = form.display.default_mode;
  const switchable = form.display.allowed_modes.length > 1;
  const allQuestions = React.useMemo(() => form.sections.flatMap((s) => s.questions), [form]);
  const [answers, setAnswers] = React.useState<AnswerMap>({});
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
    setAnswers((prev) => ({ ...prev, [question.question_id]: value }));
    // 고친 문항의 지난 오류만 지운다. 새 오류는 "다음"이나 "제출"을 누를 때만 알린다.
    setErrors((prev) => {
      if (!(question.question_id in prev)) return prev;
      const next = { ...prev };
      delete next[question.question_id];
      return next;
    });
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
    };
    setBusy(true);
    try {
      await onSubmit(body);
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
        <Button onClick={onReload ?? onRestart}>최신 폼 다시 열기</Button>
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
        <Panel className="consent">
          <h3>개인정보 수집·이용 동의</h3>
          <dl className="consent-terms">
            <dt>수집 항목</dt>
            <dd>{form.consent.items.join(', ')}</dd>
            <dt>목적</dt>
            <dd>{form.consent.purpose}</dd>
            <dt>보유 기간</dt>
            <dd>{form.consent.retention}</dd>
            <dt>동의 거부</dt>
            <dd>{form.consent.refusal_notice}</dd>
          </dl>
          <label>
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
            />
            개인정보 수집·이용에 동의합니다 (필수)
          </label>
          {consentError && (
            <p className="field-error" role="alert">
              {consentError}
            </p>
          )}
        </Panel>
        {notice && (
          <Panel className="consent ai-consent">
            <h3>AI 요약을 위한 외부 전송 안내 (선택)</h3>
            <dl className="consent-terms">
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
            <label>
              <input
                type="checkbox"
                checked={aiConsent}
                onChange={(e) => setAiConsent(e.target.checked)}
              />
              AI 요약을 위한 외부 전송에 동의합니다 (선택)
            </label>
          </Panel>
        )}
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
        {submitting ? '제출 중…' : '제출'} <Icon name="arrow" size={16} />
      </Button>
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
          <Badge tone="blue">응답 받는 중</Badge>
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
        </div>
      </Panel>
      <PaymentLink link={form.payment_link} />
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

/** `/f/:slug`, `/forms/:formId`의 응답 화면(T4 #52). 로그인 없이 쓸 수 있다. */
export function ResponsePage(props: Props) {
  const [attempt, setAttempt] = React.useState(0);
  return (
    <div className="page-shell response-page">
      <Heading
        eyebrow="응답 작성"
        title="응답 작성"
        desc="질문을 확인하고, 편한 속도로 답변해 주세요."
        action={
          props.onBack && (
            <Button variant="secondary" onClick={props.onBack}>
              <Icon name="back" size={16} /> 내 폼함으로
            </Button>
          )
        }
      />
      <ResponseForm key={attempt} {...props} onRestart={() => setAttempt((n) => n + 1)} />
    </div>
  );
}
