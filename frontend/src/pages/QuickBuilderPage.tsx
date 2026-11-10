import React from 'react';
import { api } from '../api/client';
import { branchPublishProblems } from '../builder/branchRules';
import { useCreatorAutosave, useLeaveWarning } from '../builder/creatorAutosave';
import { FormSettingsPanel } from '../builder/FormSettingsPanel';
import {
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
  addQuestionAtEnd,
  appendQuestions,
  AUDIENCE_LABELS,
  isBasicTemplate,
  PURPOSE_LABELS,
  questionFromTemplate,
  removeTemplateQuestions,
  templateIdsIn,
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

type Step = 'purpose' | 'intro' | 'pick' | 'preview' | 'publish';

/** 단계 순서와 이름은 사람이 정했다(#58). */
const STEPS: { step: Step; label: string }[] = [
  { step: 'purpose', label: '목적·대상' },
  { step: 'intro', label: '제목·설명' },
  { step: 'pick', label: '추천 문항' },
  { step: 'preview', label: '폼 미리보기' },
  { step: 'publish', label: '설정·배포' },
];

const allQuestionsOf = (def: FormDefinition) => def.sections.flatMap((s) => s.questions);

type Suggestions =
  { state: 'loading' } | { state: 'ready'; items: QuestionTemplateView[] } | { state: 'failed' };

/**
 * 빠른 모드(SPEC 46행, 179행, AC71). 목적·대상 → 제목·설명 → 추천 문항 → 폼 미리보기(직접 편집) → 설정·배포.
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
  /** 내가 만든 폼 목록으로 간다. */
  onDone: () => void;
}) {
  const [definition, setDefinition] = React.useState<FormDefinition>(() => toDefinition(initial));
  const [formState, setFormState] = React.useState<FormStateView>({
    status: initial.status,
    share_url: initial.share_url,
    definition_version: initial.definition_version,
  });
  // 다시 열었을 때 문항이 있으면 미리보기부터 이어서 한다(AC84).
  const [step, setStep] = React.useState<Step>(() =>
    initial.sections.some((s) => s.questions.length > 0) ? 'preview' : 'purpose',
  );
  const [editing, setEditing] = React.useState(false);
  // 편집을 마칠 때마다 미리보기를 처음부터 다시 그린다.
  const [previewRound, setPreviewRound] = React.useState(0);
  const [purpose, setPurpose] = React.useState<QuickPurpose | null>(null);
  const [audience, setAudience] = React.useState<QuickAudience | null>(null);
  const [suggestions, setSuggestions] = React.useState<Suggestions>({ state: 'loading' });
  const [picked, setPicked] = React.useState<string[]>([]);
  const [dirty, setDirty] = React.useState(false);
  const [publishing, setPublishing] = React.useState(false);
  const [leaving, setLeaving] = React.useState(false);
  const [failure, setFailure] = React.useState('');
  const [problems, setProblems] = React.useState<string[]>([]);
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

  // 체크박스는 그 추천 문항이 폼에 들어 있는지를 보인다(사람 결정 A, #58).
  const addedTemplateIds = templateIdsIn(definition);

  function go(next: Step) {
    setFailure('');
    setProblems([]);
    setEditing(false);
    // 추천 문항 단계에 들어오면 이미 넣은 문항을 체크된 채로 보인다.
    if (next === 'pick') setPicked([...templateIdsIn(definition)]);
    setStep(next);
  }

  /**
   * 새로 체크한 문항은 체크한 순서대로 끝에 더하고(사람 결정, #58), 체크를 푼 문항은 폼에서 뺀다.
   * 이미 들어 있는 문항은 자리를 지키고, 이 목록에 없는 문항은 건드리지 않는다.
   */
  const pickChanges = () => {
    const items = suggestions.state === 'ready' ? suggestions.items : [];
    const byId = new Map(items.map((t) => [t.template_id, t]));
    const toAdd = picked
      .filter((id) => !addedTemplateIds.has(id))
      .map((id) => byId.get(id))
      .filter((t): t is QuestionTemplateView => t !== undefined);
    const toRemove = new Set(
      items
        .filter((t) => !picked.includes(t.template_id) && addedTemplateIds.has(t.template_id))
        .map((t) => t.template_id),
    );
    // 적용한 뒤의 문항 순서. 체크한 문항 옆에 "N번"으로 보인다.
    const order = [
      ...allQuestionsOf(definition)
        .map((q) => q.template_id)
        .filter((id) => id === null || !toRemove.has(id)),
      ...toAdd.map((t) => t.template_id),
    ];
    const positionOf = (id: string) => order.indexOf(id) + 1;
    return { toAdd, toRemove, positionOf };
  };

  function applyPicked() {
    const { toAdd, toRemove } = pickChanges();
    if (toAdd.length > 0 || toRemove.size > 0) {
      edit((d) =>
        appendQuestions(removeTemplateQuestions(d, toRemove), toAdd.map(questionFromTemplate)),
      );
    }
    go('preview');
  }

  function finishEditing() {
    setEditing(false);
    setPreviewRound((n) => n + 1);
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

  /**
   * 기본 모드로 옮기기 전에 지금 편집을 끝까지 저장한다(코덱스 검토 1). 기본 모드의 저장·배포와 같이
   * 기다리는 자동 저장을 정리한 뒤 현재 정의 전체를 보내고, 성공해야만 옮긴다. 실패하면 남아서 알린다.
   */
  async function openBasic() {
    if (status !== 'draft' || !dirty) {
      onBasic();
      return;
    }
    setLeaving(true);
    setFailure('');
    await autosave.cancel();
    try {
      await api.updateForm(formId, definition);
      setDirty(false);
      onBasic();
    } catch (error) {
      setFailure(`저장 실패 · 지금 편집 내용은 아직 저장되지 않았어요. ${friendlyError(error)}`);
    } finally {
      setLeaving(false);
    }
  }

  async function copyShareUrl() {
    try {
      await navigator.clipboard.writeText(shareUrl ?? '');
      setCopyNote('복사했어요.');
    } catch {
      setCopyNote('복사하지 못했어요. 주소를 직접 복사해 주세요.');
    }
  }

  const heading = (
    <Heading
      eyebrow="제작자 워크스페이스 / 빠른 모드"
      title="빠른 모드로 만들기"
      desc="목적과 대상을 고르면 알맞은 문항을 추천해 드려요."
      action={
        <Button
          variant="secondary"
          onClick={() => void openBasic()}
          disabled={publishing || leaving}
        >
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
          {/* 배포 뒤에도 자동 저장 실패를 숨기지 않는다(코덱스 검토 2). */}
          {autosave.status === 'failed' && (
            <p className="error" role="alert">
              저장 실패 · 지금 편집 내용은 아직 저장되지 않았어요.
            </p>
          )}
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
  const allQuestions = allQuestionsOf(definition);
  const pickPlan = pickChanges();
  const branchProblems = branchPublishProblems(definition);

  const top = (
    <>
      <ol className="quick-steps" aria-label="빠른 모드 단계">
        {/* 단계 이름을 눌러도 그 단계로 간다(사람 결정, #58). */}
        {STEPS.map(({ step: s, label }, index) => (
          <li key={s} className={s === step ? 'current' : undefined}>
            <button
              type="button"
              aria-current={s === step ? 'step' : undefined}
              onClick={() => go(s)}
              disabled={publishing}
            >
              {index + 1}. {label}
            </button>
          </li>
        ))}
      </ol>
      <p className="small muted" role="status">
        {publishing ? (
          '배포 중…'
        ) : leaving ? (
          '저장 중…'
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
    </>
  );

  /** 아래쪽 이동 줄: 이전·다음은 오른쪽에 나란히, 그 단계의 다른 버튼은 왼쪽(사람 결정, #58). */
  const nav = (back: React.ReactNode, next: React.ReactNode, left?: React.ReactNode) => (
    <div className="quick-nav">
      {left && <div className="quick-nav-left">{left}</div>}
      <div className="quick-nav-right">
        {back}
        {next}
      </div>
    </div>
  );
  const backTo = (target: Step) => (
    <Button variant="secondary" onClick={() => go(target)} disabled={publishing}>
      이전
    </Button>
  );

  // 4단계: 기존 미리보기를 그대로 보인다. 답을 입력해도 Response를 만들지 않는다(AC81).
  if (step === 'preview' && !editing && allQuestions.length > 0 && branchProblems.length === 0) {
    const consent =
      templates?.find((t) => t.consent_template_id === definition.consent_template_id) ?? null;
    return (
      <div className="page-shell quick-page" ref={pageRef}>
        {heading}
        {top}
        <div className="quick-tools">
          <Button variant="secondary" onClick={() => setEditing(true)}>
            폼 직접 편집
          </Button>
        </div>
        {/* 제작 화면과 응답자 화면의 경계를 테두리 상자로 구분한다(사람 요청, #58). */}
        <section className="quick-preview-frame" aria-label="응답자 화면 미리보기">
          <p className="quick-preview-label small">응답자 화면 미리보기</p>
          <ResponsePage
            key={previewRound}
            preview
            form={{ ...toPreviewForm(definition, consent), form_id: formId }}
            onSubmit={() => Promise.resolve()}
            saving={false}
          />
        </section>
        {nav(backTo('pick'), <Button onClick={() => go('publish')}>다음</Button>)}
      </div>
    );
  }

  return (
    <div className="page-shell quick-page" ref={pageRef}>
      {heading}
      {top}

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
          {nav(
            <Button variant="secondary" onClick={onDone}>
              내가 만든 폼으로
            </Button>,
            <Button onClick={() => go('intro')} disabled={!purpose || !audience}>
              다음
            </Button>,
          )}
        </Panel>
      )}

      {step === 'intro' && (
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
          {nav(backTo('purpose'), <Button onClick={() => go('pick')}>다음</Button>)}
        </Panel>
      )}

      {step === 'pick' && !(purpose && audience) && (
        <Panel className="quick-panel">
          <p className="muted">추천을 받으려면 목적과 대상을 먼저 골라 주세요.</p>
          {nav(backTo('purpose'), <Button onClick={() => go('preview')}>다음</Button>)}
        </Panel>
      )}

      {step === 'pick' && purpose && audience && (
        <Panel className="quick-panel">
          <h2>
            {PURPOSE_LABELS[purpose]} · {AUDIENCE_LABELS[audience]}에 맞는 추천 문항
          </h2>
          <p className="small muted quick-hint">
            체크한 순서대로 폼에 들어가요. 넣은 뒤에도 고칠 수 있어요.
          </p>
          {suggestions.state === 'loading' && (
            <p className="muted">추천 문항을 불러오는 중이에요.</p>
          )}
          {suggestions.state === 'failed' && (
            <p className="error" role="alert">
              추천 문항을 불러오지 못했어요. 다음 단계에서 직접 질문을 추가할 수 있어요.
            </p>
          )}
          {suggestions.state === 'ready' &&
            [
              { label: '기본 정보', items: suggestions.items.filter(isBasicTemplate) },
              {
                label: '추천 문항',
                items: suggestions.items.filter((t) => !isBasicTemplate(t)),
              },
            ]
              .filter((group) => group.items.length > 0)
              .map((group) => (
                <fieldset className="quick-choices" key={group.label}>
                  <legend>{group.label}</legend>
                  <div className="option-stack">
                    {group.items.map((t) => {
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
                          {checked && (
                            <span className="quick-order">
                              {pickPlan.positionOf(t.template_id)}번
                            </span>
                          )}
                        </label>
                      );
                    })}
                  </div>
                </fieldset>
              ))}
          {nav(
            backTo('intro'),
            <Button onClick={applyPicked}>
              {pickPlan.toAdd.length > 0 || pickPlan.toRemove.size > 0 ? '적용하고 다음' : '다음'}
            </Button>,
          )}
        </Panel>
      )}

      {step === 'preview' && (
        <>
          {editing && (
            <div className="quick-tools">
              <Button onClick={finishEditing}>편집 마치고 미리보기</Button>
            </div>
          )}
          {!editing && allQuestions.length === 0 && (
            <Panel className="quick-panel">
              <p className="muted">
                아직 문항이 없어요. 추천 문항을 고르거나 폼을 직접 편집해 질문을 추가하세요.
              </p>
              <div className="quick-tools">
                <Button variant="secondary" onClick={() => setEditing(true)}>
                  폼 직접 편집
                </Button>
              </div>
            </Panel>
          )}
          {!editing && branchProblems.length > 0 && (
            <Panel className="quick-panel">
              <div className="error" role="alert">
                <p>미리보기 전에 분기 설정을 고쳐 주세요.</p>
                {branchProblems.map((problem) => (
                  <p key={problem}>{problem}</p>
                ))}
              </div>
              <div className="quick-tools">
                <Button variant="secondary" onClick={() => setEditing(true)}>
                  폼 직접 편집
                </Button>
              </div>
            </Panel>
          )}
          {editing &&
            definition.sections.map((section) =>
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
          {editing && (
            <div className="button-row quick-add">
              <Button variant="secondary" onClick={() => edit(addQuestionAtEnd)}>
                직접 질문 추가
              </Button>
              <Button variant="secondary" onClick={() => go('pick')}>
                추천 문항 더 보기
              </Button>
            </div>
          )}
          {nav(
            backTo('pick'),
            <Button onClick={() => go('publish')}>다음</Button>,
            // 위쪽 버튼과 같은 일을 한다(사람 요청). 화면 읽기 프로그램이 구분하도록 이름에 "(아래)"를 붙인다.
            editing && (
              <button
                type="button"
                className="btn btn-secondary"
                onClick={finishEditing}
                aria-label="편집 마치고 미리보기 (아래)"
              >
                편집 마치고 미리보기
              </button>
            ),
          )}
        </>
      )}

      {step === 'publish' && (
        <>
          {/* 배포 요청 중에는 설정을 잠근다(코덱스 검토 2). 기본 모드의 builder-fieldset과 같은 방식이다. */}
          <fieldset className="quick-fieldset" disabled={publishing}>
            <FormSettingsPanel
              definition={definition}
              firstPublished={version !== null}
              templates={templates}
              onChange={edit}
            />
          </fieldset>
          {nav(
            backTo('preview'),
            <Button onClick={() => void publish()} disabled={publishing}>
              배포
            </Button>,
          )}
        </>
      )}
    </div>
  );
}
