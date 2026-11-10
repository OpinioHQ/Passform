import React from 'react';
import { api } from '../api/client';
import { branchPublishProblems } from '../builder/branchRules';
import { useCreatorAutosave, useLeaveWarning } from '../builder/creatorAutosave';
import { FormSettingsPanel } from '../builder/FormSettingsPanel';
import {
  addQuestion,
  duplicateQuestion,
  moveQuestion,
  publishProblems,
  removeQuestion,
  replaceQuestion,
  toDefinition,
  toPreviewForm,
} from '../builder/formDefinition';
import { friendlyError } from '../builder/messages';
import { QuestionEditor } from '../builder/QuestionEditor';
import {
  appendQuestions,
  AUDIENCE_LABELS,
  PURPOSE_LABELS,
  questionFromTemplate,
} from '../builder/quickMode';
import { Badge, Button, Heading, Panel } from '../components/UI';
import { QUESTION_TYPE_LABELS } from '../components/questionTypeLabels';
import type {
  ConsentTemplate,
  CreatorFormDefinition,
  FormDefinition,
  FormStateView,
  QuestionTemplateView,
  QuickAudience,
  QuickPurpose,
} from '../types/api';
import { ResponsePage } from './ResponsePage';

type Step = 'purpose' | 'pick' | 'questions' | 'review';

const STEPS: { step: Step; label: string }[] = [
  { step: 'purpose', label: '목적·대상' },
  { step: 'pick', label: '추천 문항' },
  { step: 'questions', label: '문항 편집' },
  { step: 'review', label: '검토·배포' },
];

type Suggestions =
  { state: 'loading' } | { state: 'ready'; items: QuestionTemplateView[] } | { state: 'failed' };

/**
 * 빠른 모드(SPEC 46행, 179행, AC71). 목적·대상 → 추천 문항 → 문항 편집·직접 추가 → 검토·미리보기·배포.
 * 저장 형식은 기본 모드와 같은 폼 정의이고(AC42), 문항 편집·설정·자동 저장·미리보기는 기본 모드의 것을 그대로 쓴다.
 */
export function QuickBuilderPage({
  formId,
  initial,
  templates,
  onBasic,
  onDone,
}: {
  formId: string;
  initial: CreatorFormDefinition;
  templates: ConsentTemplate[] | null;
  /** 같은 폼을 기본 모드 편집 화면으로 연다. */
  onBasic: () => void;
  /** 배포 뒤 내가 만든 폼 목록으로 간다. */
  onDone: () => void;
}) {
  const [definition, setDefinition] = React.useState<FormDefinition>(() => toDefinition(initial));
  const [formState, setFormState] = React.useState<FormStateView>({
    status: initial.status,
    share_url: initial.share_url,
    definition_version: initial.definition_version,
  });
  // 다시 열었을 때 문항이 있으면 편집 단계부터 이어서 한다(AC84).
  const [step, setStep] = React.useState<Step>(() =>
    initial.sections.some((s) => s.questions.length > 0) ? 'questions' : 'purpose',
  );
  const [purpose, setPurpose] = React.useState<QuickPurpose | null>(null);
  const [audience, setAudience] = React.useState<QuickAudience | null>(null);
  const [suggestions, setSuggestions] = React.useState<Suggestions>({ state: 'loading' });
  const [picked, setPicked] = React.useState<string[]>([]);
  const [dirty, setDirty] = React.useState(false);
  const [publishing, setPublishing] = React.useState(false);
  const [failure, setFailure] = React.useState('');
  const [problems, setProblems] = React.useState<string[]>([]);
  const [previewing, setPreviewing] = React.useState(false);
  const [copyNote, setCopyNote] = React.useState('');
  const pageRef = React.useRef<HTMLDivElement>(null);
  const { status, share_url: shareUrl, definition_version: version } = formState;

  // 기본 모드와 같은 자동 저장(SPEC 153행, AC84). draft만 저장하고 배포·버전 증가는 하지 않는다.
  const autosaveRequest = React.useCallback(
    (def: FormDefinition) => api.updateForm(formId, def),
    [formId],
  );
  const markSaved = React.useCallback(() => setDirty(false), []);
  const autosave = useCreatorAutosave(autosaveRequest, markSaved);
  const { schedule } = autosave;
  React.useEffect(() => {
    if (status === 'draft' && dirty) schedule(definition);
  }, [status, dirty, definition, schedule]);
  useLeaveWarning(
    status === 'draft' && dirty,
    pageRef,
    '저장되지 않은 변경이 있어요. 이 화면을 나갈까요?',
  );

  // 목적이나 대상을 바꾸면 그 두 선택에 맞게 다시 불러온다(AC71).
  React.useEffect(() => {
    if (step !== 'pick' || !purpose || !audience) return;
    let active = true;
    setSuggestions({ state: 'loading' });
    setPicked([]);
    api
      .questionTemplates(purpose, audience)
      .then((items) => {
        if (active) setSuggestions({ state: 'ready', items });
      })
      .catch(() => {
        if (active) setSuggestions({ state: 'failed' });
      });
    return () => {
      active = false;
    };
  }, [step, purpose, audience]);

  function edit(change: (def: FormDefinition) => FormDefinition) {
    setDefinition(change);
    setDirty(true);
    setFailure('');
    setProblems([]);
  }

  function addPicked() {
    if (suggestions.state !== 'ready') return;
    const chosen = suggestions.items.filter((t) => picked.includes(t.template_id));
    if (chosen.length > 0) edit((d) => appendQuestions(d, chosen.map(questionFromTemplate)));
    setStep('questions');
  }

  async function publish() {
    const found = publishProblems(definition);
    if (found.length > 0) {
      setFailure('');
      setProblems(found);
      return;
    }
    setPublishing(true);
    setFailure('');
    setProblems([]);
    // 기본 모드와 같이 기다리는 자동 저장을 버리고 현재 편집 전체를 보낸다.
    await autosave.cancel();
    try {
      setFormState(await api.updateForm(formId, { ...definition, status: 'open' }));
      setDirty(false);
    } catch (error) {
      setFailure(`배포하지 못했어요. 초안은 그대로예요. ${friendlyError(error)}`);
    } finally {
      setPublishing(false);
    }
  }

  function openPreview() {
    const found = branchPublishProblems(definition);
    if (found.length > 0) {
      setFailure('');
      setProblems(['미리보기 전에 분기 설정을 고쳐 주세요.', ...found]);
      return;
    }
    setPreviewing(true);
  }

  async function copyShareUrl() {
    try {
      await navigator.clipboard.writeText(shareUrl ?? '');
      setCopyNote('복사했어요.');
    } catch {
      setCopyNote('복사하지 못했어요. 주소를 직접 복사해 주세요.');
    }
  }

  // 기본 모드의 미리보기를 그대로 쓴다. 실제 Response를 만들지 않는다(AC81).
  if (previewing) {
    const consent =
      templates?.find((t) => t.consent_template_id === definition.consent_template_id) ?? null;
    return (
      <ResponsePage
        preview
        form={{ ...toPreviewForm(definition, consent), form_id: formId }}
        onBack={() => setPreviewing(false)}
        onSubmit={() => Promise.resolve()}
        saving={false}
      />
    );
  }

  const heading = (
    <Heading
      eyebrow="제작자 워크스페이스 / 빠른 모드"
      title="빠른 모드로 만들기"
      desc="목적과 대상을 고르면 알맞은 문항을 추천해 드려요."
      action={
        <Button variant="secondary" onClick={onBasic} disabled={publishing}>
          기본 모드로 열기
        </Button>
      }
    />
  );

  if (status !== 'draft') {
    return (
      <div className="page-shell quick-page" ref={pageRef}>
        {heading}
        <Panel className="quick-panel">
          <Badge tone="green">배포 중{version !== null && ` · 폼 버전 ${version}`}</Badge>
          <p>배포했어요. 공유 링크로 응답을 받을 수 있어요.</p>
          {shareUrl && (
            <div className="builder-share">
              <label className="builder-field">
                <span>공유 링크</span>
                <input readOnly value={shareUrl} />
              </label>
              <Button variant="secondary" onClick={copyShareUrl}>
                링크 복사
              </Button>
              {copyNote && <small className="muted">{copyNote}</small>}
            </div>
          )}
          <div className="button-row">
            <Button onClick={onDone}>내가 만든 폼으로</Button>
          </div>
        </Panel>
      </div>
    );
  }

  const saveText =
    autosave.status === 'saving'
      ? '저장 중…'
      : autosave.status === 'saved'
        ? '저장됨'
        : autosave.status === 'failed'
          ? '저장 실패 · 지금 편집 내용은 아직 저장되지 않았어요.'
          : null;
  const allQuestions = definition.sections.flatMap((s) => s.questions);
  const lastSectionId = definition.sections[definition.sections.length - 1]?.section_id;

  return (
    <div className="page-shell quick-page" ref={pageRef}>
      {heading}
      <ol className="quick-steps" aria-label="빠른 모드 단계">
        {STEPS.map(({ step: s, label }, index) => (
          <li
            key={s}
            className={s === step ? 'current' : undefined}
            aria-current={s === step ? 'step' : undefined}
          >
            {index + 1}. {label}
          </li>
        ))}
      </ol>
      <p className="small muted" role="status">
        {publishing ? (
          '배포 중…'
        ) : saveText ? (
          <span className={autosave.status === 'failed' ? 'error' : undefined}>{saveText}</span>
        ) : null}
      </p>
      {failure && (
        <p className="error" role="alert">
          {failure}
        </p>
      )}
      {problems.length > 0 && (
        <div className="error" role="alert">
          {problems.map((problem) => (
            <p key={problem}>{problem}</p>
          ))}
        </div>
      )}

      {step === 'purpose' && (
        <Panel className="quick-panel">
          <fieldset className="quick-choices">
            <legend>무엇을 위한 폼인가요?</legend>
            <div className="option-stack">
              {(Object.keys(PURPOSE_LABELS) as QuickPurpose[]).map((value) => (
                <label className={`option ${purpose === value ? 'checked' : ''}`} key={value}>
                  <input
                    type="radio"
                    name="quick-purpose"
                    checked={purpose === value}
                    onChange={() => setPurpose(value)}
                  />
                  {PURPOSE_LABELS[value]}
                </label>
              ))}
            </div>
          </fieldset>
          <fieldset className="quick-choices">
            <legend>누가 응답하나요?</legend>
            <div className="option-stack">
              {(Object.keys(AUDIENCE_LABELS) as QuickAudience[]).map((value) => (
                <label className={`option ${audience === value ? 'checked' : ''}`} key={value}>
                  <input
                    type="radio"
                    name="quick-audience"
                    checked={audience === value}
                    onChange={() => setAudience(value)}
                  />
                  {AUDIENCE_LABELS[value]}
                </label>
              ))}
            </div>
          </fieldset>
          <div className="button-row">
            <Button onClick={() => setStep('pick')} disabled={!purpose || !audience}>
              추천 문항 보기
            </Button>
            {allQuestions.length > 0 && (
              <Button variant="secondary" onClick={() => setStep('questions')}>
                문항 편집으로
              </Button>
            )}
          </div>
        </Panel>
      )}

      {step === 'pick' && purpose && audience && (
        <Panel className="quick-panel">
          <h2>
            {PURPOSE_LABELS[purpose]} · {AUDIENCE_LABELS[audience]}에 맞는 추천 문항
          </h2>
          <p className="small muted">
            필요한 문항을 골라 추가하세요. 추가한 뒤에도 고칠 수 있어요.
          </p>
          {suggestions.state === 'loading' && (
            <p className="muted">추천 문항을 불러오는 중이에요.</p>
          )}
          {suggestions.state === 'failed' && (
            <p className="error" role="alert">
              추천 문항을 불러오지 못했어요. 직접 질문을 추가할 수 있어요.
            </p>
          )}
          {suggestions.state === 'ready' && (
            <div className="option-stack" role="group" aria-label="추천 문항">
              {suggestions.items.map((t) => {
                const checked = picked.includes(t.template_id);
                return (
                  <label
                    className={`option quick-suggestion ${checked ? 'checked' : ''}`}
                    key={t.template_id}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() =>
                        setPicked((list) =>
                          checked
                            ? list.filter((id) => id !== t.template_id)
                            : [...list, t.template_id],
                        )
                      }
                    />
                    <span className="quick-suggestion-text">
                      {t.question_note}
                      <small className="muted">
                        {QUESTION_TYPE_LABELS[t.question_type]}
                        {t.options.length > 0 && ` · ${t.options.join(', ')}`}
                      </small>
                    </span>
                  </label>
                );
              })}
            </div>
          )}
          <div className="button-row">
            <Button variant="secondary" onClick={() => setStep('purpose')}>
              이전
            </Button>
            <Button onClick={addPicked}>
              {picked.length > 0 ? `${picked.length}개 추가하고 다음` : '추가하지 않고 다음'}
            </Button>
          </div>
        </Panel>
      )}

      {step === 'questions' && (
        <>
          {allQuestions.length === 0 && (
            <Panel className="quick-panel">
              <p className="muted">
                아직 문항이 없어요. 추천 문항을 고르거나 직접 질문을 추가하세요.
              </p>
            </Panel>
          )}
          {definition.sections.map((section) =>
            section.questions.map((question, questionIndex) => {
              const sectionId = section.section_id;
              const questionId = question.question_id;
              return (
                <Panel className="quick-panel" key={questionId}>
                  <QuestionEditor
                    definition={definition}
                    question={question}
                    number={allQuestions.findIndex((q) => q.question_id === questionId) + 1}
                    canMoveUp={questionIndex > 0}
                    canMoveDown={questionIndex < section.questions.length - 1}
                    onChange={(next) => edit((d) => replaceQuestion(d, sectionId, next))}
                    onEdit={edit}
                    onMove={(delta) => edit((d) => moveQuestion(d, sectionId, questionId, delta))}
                    onDuplicate={() => edit((d) => duplicateQuestion(d, sectionId, questionId))}
                    onRemove={() => edit((d) => removeQuestion(d, sectionId, questionId))}
                  />
                </Panel>
              );
            }),
          )}
          <div className="button-row quick-actions">
            <Button
              variant="secondary"
              onClick={() => lastSectionId && edit((d) => addQuestion(d, lastSectionId))}
            >
              직접 질문 추가
            </Button>
            <Button variant="secondary" onClick={() => setStep('purpose')}>
              추천 문항 더 보기
            </Button>
            <Button onClick={() => setStep('review')}>검토로</Button>
          </div>
        </>
      )}

      {step === 'review' && (
        <>
          <Panel className="quick-panel builder-intro">
            <input
              className="builder-title"
              value={definition.title}
              aria-label="폼 제목"
              placeholder="폼 제목을 입력해 주세요"
              onChange={(e) => {
                const title = e.target.value;
                edit((d) => ({ ...d, title }));
              }}
            />
            <textarea
              aria-label="폼 설명"
              value={definition.note}
              rows={4}
              placeholder="폼 설명을 입력해 주세요"
              onChange={(e) => {
                const note = e.target.value;
                edit((d) => ({ ...d, note }));
              }}
            />
            <ol className="quick-review-list">
              {allQuestions.map((q) => (
                <li key={q.question_id}>
                  {q.question_note.trim() || '(문구 없음)'}{' '}
                  <small className="muted">{QUESTION_TYPE_LABELS[q.question_type]}</small>
                </li>
              ))}
            </ol>
          </Panel>
          <FormSettingsPanel
            definition={definition}
            firstPublished={version !== null}
            templates={templates}
            onChange={edit}
          />
          <div className="button-row quick-actions">
            <Button variant="secondary" onClick={() => setStep('questions')} disabled={publishing}>
              문항 편집으로
            </Button>
            <Button variant="secondary" onClick={openPreview} disabled={publishing}>
              미리보기
            </Button>
            <Button onClick={() => void publish()} disabled={publishing}>
              배포
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
