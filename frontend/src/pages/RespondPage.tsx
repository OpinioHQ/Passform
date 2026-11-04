import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { api, ApiError } from '../api/client';
import type { Answers, DisplayMode, PublicForm, Question, SubmittedResponse } from '../api/types';
import QuestionField from '../components/QuestionField';
import { Button, Card, Notice, PageTitle } from '../components/ui';
import { answerProblem } from '../lib/answerFormat';
import { formatDateTime } from '../lib/format';
import { computePath, isBlank } from '../lib/responsePath';

const ERROR_MESSAGES: Record<string, string> = {
  consent_required: '개인정보 수집·이용에 동의해야 제출할 수 있습니다.',
  required_missing: '필수 문항에 답해 주세요.',
  invalid_input_format_value: '입력 형식이 맞지 않는 문항이 있습니다.',
  invalid_option: '선택할 수 없는 값이 있습니다.',
  character_limit_exceeded: '글자 수를 넘은 문항이 있습니다.',
  form_closed: '이 폼은 마감되었습니다.',
  form_version_changed: '그 사이 폼이 바뀌었습니다. 페이지를 새로고침해 최신 폼으로 다시 작성해 주세요.',
};

export default function RespondPage() {
  const { slug, formId } = useParams();
  const [form, setForm] = useState<PublicForm | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    (formId ? api.formById(formId) : api.formBySlug(slug ?? ''))
      .then((value) => {
        if (active) {
          setForm(value);
        }
      })
      .catch((reason: unknown) => {
        if (active) {
          setLoadError(reason instanceof ApiError && reason.code === 'form_closed' ? '이 폼은 마감되었습니다.' : '폼을 찾을 수 없습니다.');
        }
      });
    return () => {
      active = false;
    };
  }, [slug, formId]);

  if (loadError) {
    return <Notice tone="warn">{loadError}</Notice>;
  }
  if (!form) {
    return <p className="text-sm text-muted">불러오는 중…</p>;
  }
  return <RespondForm form={form} />;
}

function RespondForm({ form }: { form: PublicForm }) {
  const [mode, setMode] = useState<DisplayMode>(form.display.default_mode);
  const [answers, setAnswers] = useState<Answers>({});
  const [pageIndex, setPageIndex] = useState(0);
  const [started, setStarted] = useState(form.display.default_mode !== 'one_question');
  const [atConsent, setAtConsent] = useState(false);
  const [consent, setConsent] = useState(false);
  const [aiConsent, setAiConsent] = useState(false);
  const [problems, setProblems] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState<SubmittedResponse | null>(null);

  const path = useMemo(() => computePath(form.sections, answers), [form.sections, answers]);
  const pages = useMemo(() => buildPages(form, path, mode), [form, path, mode]);
  const page = pages[Math.min(pageIndex, pages.length - 1)] ?? [];

  const setAnswer = (questionId: string, value: Answers[string]) => {
    setAnswers((previous) => ({ ...previous, [questionId]: value }));
    setProblems(({ [questionId]: _fixed, ...rest }) => {
      void _fixed;
      return rest;
    });
  };

  const changeMode = (next: DisplayMode) => {
    // 전환해도 답은 유지하고 보던 문항으로 이동한다(SPEC 5절 177행).
    const current = page[0]?.question_id;
    const nextPages = buildPages(form, path, next);
    const found = nextPages.findIndex((questions) => questions.some((question) => question.question_id === current));
    setMode(next);
    setStarted(true);
    setPageIndex(found >= 0 ? found : 0);
  };

  // 필수·형식·글자 수를 넘어가기 전에 알려 준다. 맨 끝 제출에서야 되돌아가지 않게 한다.
  const problemsOn = (questions: Question[]) => {
    const found: Record<string, string> = {};
    for (const question of questions) {
      const problem = answerProblem(question, answers[question.question_id]);
      if (problem) {
        found[question.question_id] = problem;
      }
    }
    return found;
  };

  const goNext = () => {
    const found = problemsOn(page);
    if (Object.keys(found).length > 0) {
      setProblems(found);
      return;
    }
    if (pageIndex >= pages.length - 1) {
      setAtConsent(true);
    } else {
      setPageIndex(pageIndex + 1);
    }
    window.scrollTo({ top: 0 });
  };

  const submit = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const onPath = path
        .map((question) => ({ question_id: question.question_id, value: answers[question.question_id] }))
        .filter((answer): answer is { question_id: string; value: Answers[string] } => !isBlank(answer.value));
      const result = await api.submit(form.form_id, {
        form_version: form.definition_version,
        answers: onPath,
        consent_agreed: consent,
        ai_summary_agreed: aiConsent,
      });
      setSubmitted(result);
      window.scrollTo({ top: 0 });
    } catch (reason) {
      if (reason instanceof ApiError) {
        const message = ERROR_MESSAGES[reason.code ?? ''] ?? '제출하지 못했습니다. 입력한 내용은 그대로 있습니다.';
        setError(message);
        if (reason.questionIds.length > 0) {
          setProblems(Object.fromEntries(reason.questionIds.map((id) => [id, message])));
          setAtConsent(false);
          const target = pages.findIndex((questions) => questions.some((question) => reason.questionIds.includes(question.question_id)));
          setPageIndex(target >= 0 ? target : 0);
        }
      } else {
        setError('제출하지 못했습니다. 입력한 내용은 그대로 있습니다.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (submitted) {
    return (
      <>
        <PageTitle title="제출했습니다" description={`${form.title} · ${formatDateTime(submitted.submitted_at)}`} />
        <Card>
          <p className="text-sm text-muted">제출한 내용은 로그인한 계정의 제출 이력에서 다시 볼 수 있습니다.</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Link to={`/me/responses/${submitted.response_id}`} className="rounded-lg bg-accent px-4 py-2 text-sm text-white">제출 내용 보기</Link>
            <Link to="/me/forms" className="rounded-lg border border-line px-4 py-2 text-sm">내 폼함으로</Link>
          </div>
        </Card>
      </>
    );
  }

  const header = <PageTitle title={form.title} description={form.note ?? undefined} />;
  const modeSwitch = form.display.allowed_modes.length > 1 ? (
    <div className="mb-4 flex gap-2">
      <Button variant={mode === 'one_question' ? 'primary' : 'secondary'} onClick={() => changeMode('one_question')}>한 질문씩</Button>
      <Button variant={mode === 'all_questions' ? 'primary' : 'secondary'} onClick={() => changeMode('all_questions')}>전체 질문 보기</Button>
    </div>
  ) : null;

  if (!started) {
    return (
      <>
        {header}
        {modeSwitch}
        <Card>
          <p className="text-sm text-muted">마감: {formatDateTime(form.deadline)}</p>
          <Button variant="primary" className="mt-4" onClick={() => setStarted(true)}>시작하기</Button>
        </Card>
      </>
    );
  }

  if (atConsent) {
    return (
      <>
        {header}
        <Card className="grid gap-4">
          <div>
            <h2 className="font-medium">개인정보 수집·이용 동의</h2>
            <dl className="mt-2 grid gap-1 text-sm">
              <div><dt className="inline text-muted">수집 항목: </dt><dd className="inline">{form.consent.items.join(', ')}</dd></div>
              <div><dt className="inline text-muted">목적: </dt><dd className="inline">{form.consent.purpose}</dd></div>
              <div><dt className="inline text-muted">보유 기간: </dt><dd className="inline">{form.consent.retention}</dd></div>
            </dl>
            <p className="mt-2 text-sm text-muted">{form.consent.refusal_notice}</p>
            <label className="mt-3 flex min-h-11 items-center gap-2 text-sm">
              <input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)} className="h-5 w-5" />
              위 내용에 동의합니다. (필수)
            </label>
          </div>
          {form.consent.ai_summary ? (
            <div className="border-t border-line pt-4">
              <h2 className="font-medium">AI 요약 외부 전송 동의 (선택)</h2>
              <dl className="mt-2 grid gap-1 text-sm">
                <div><dt className="inline text-muted">제공사: </dt><dd className="inline">{form.consent.ai_summary.provider} ({form.consent.ai_summary.processing_country})</dd></div>
                <div><dt className="inline text-muted">항목: </dt><dd className="inline">{form.consent.ai_summary.items}</dd></div>
                <div><dt className="inline text-muted">목적: </dt><dd className="inline">{form.consent.ai_summary.purpose}</dd></div>
                <div><dt className="inline text-muted">보유 기간: </dt><dd className="inline">{form.consent.ai_summary.retention}</dd></div>
                <div><dt className="inline text-muted">거부 시: </dt><dd className="inline">{form.consent.ai_summary.refusal_effect}</dd></div>
              </dl>
              <label className="mt-3 flex min-h-11 items-center gap-2 text-sm">
                <input type="checkbox" checked={aiConsent} onChange={(event) => setAiConsent(event.target.checked)} className="h-5 w-5" />
                AI 요약을 위한 외부 전송에 동의합니다. (선택)
              </label>
            </div>
          ) : null}
          {error ? <Notice tone="danger">{error}</Notice> : null}
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => setAtConsent(false)}>이전</Button>
            <Button variant="primary" disabled={submitting} onClick={() => void submit()}>{submitting ? '제출 중…' : '제출하기'}</Button>
          </div>
        </Card>
      </>
    );
  }

  return (
    <>
      {header}
      {modeSwitch}
      <div className="grid gap-3">
        {page.map((question) => (
          <Card key={question.question_id}>
            <QuestionField
              question={question}
              value={answers[question.question_id]}
              error={problems[question.question_id] ?? null}
              onChange={(value) => setAnswer(question.question_id, value)}
            />
          </Card>
        ))}
      </div>
      {error ? <div className="mt-3"><Notice tone="danger">{error}</Notice></div> : null}
      <div className="mt-4 flex flex-wrap gap-2">
        {pageIndex > 0 ? <Button onClick={() => setPageIndex(pageIndex - 1)}>이전</Button> : null}
        <Button variant="primary" onClick={goNext}>{pageIndex >= pages.length - 1 ? '동의하고 제출로' : '다음'}</Button>
      </div>
    </>
  );
}

// 현재 답으로 계산한 경로 위의 문항만 화면에 올린다.
function buildPages(form: PublicForm, path: Question[], mode: DisplayMode): Question[][] {
  if (mode === 'all_questions') {
    return [path];
  }
  if (mode === 'one_question') {
    return path.map((question) => [question]);
  }
  const onPath = new Set(path.map((question) => question.question_id));
  return form.sections
    .map((section) => section.questions.filter((question) => onPath.has(question.question_id)))
    .filter((questions) => questions.length > 0);
}
