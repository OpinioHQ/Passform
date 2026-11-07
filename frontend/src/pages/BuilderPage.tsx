import React from 'react';
import { api } from '../api/client';
import { branchPublishProblems } from '../builder/branchRules';
import { FormSettingsPanel } from '../builder/FormSettingsPanel';
import {
  addQuestion,
  addSection,
  duplicateQuestion,
  duplicateSection,
  moveQuestion,
  moveSection,
  publishProblems,
  removeQuestion,
  removeSection,
  replaceQuestion,
  saveProblems,
  toDefinition,
  toPreviewForm,
  updateSectionTitle,
} from '../builder/formDefinition';
import { friendlyError, statusLabel } from '../builder/messages';
import { QuestionEditor, RowButton } from '../builder/QuestionEditor';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import type {
  ConsentTemplate,
  CreatorFormDefinition,
  FormDefinition,
  FormStateView,
  FormStatus,
} from '../types/api';
import { ResponsePage } from './ResponsePage';

type Pending = '' | 'save' | 'publish' | 'status';

const PENDING_TEXT: Record<Exclude<Pending, ''>, string> = {
  save: '저장 중…',
  publish: '배포 중…',
  status: '상태를 바꾸는 중…',
};

const STATUS_TONE: Record<FormStatus, 'neutral' | 'green' | 'amber'> = {
  draft: 'neutral',
  open: 'green',
  closed: 'amber',
};

export function BuilderPage({
  formId,
  initial,
  templates,
}: {
  formId: string;
  initial: CreatorFormDefinition;
  templates: ConsentTemplate[] | null;
}) {
  const [definition, setDefinition] = React.useState<FormDefinition>(() => toDefinition(initial));
  const [formState, setFormState] = React.useState<FormStateView>({
    status: initial.status,
    share_url: initial.share_url,
    definition_version: initial.definition_version,
  });
  const [dirty, setDirty] = React.useState(false);
  const [pending, setPending] = React.useState<Pending>('');
  const [outcome, setOutcome] = React.useState('');
  const [failure, setFailure] = React.useState('');
  const [problems, setProblems] = React.useState<string[]>([]);
  const [copyNote, setCopyNote] = React.useState('');
  const [previewing, setPreviewing] = React.useState(false);

  const { status, share_url: shareUrl, definition_version: version } = formState;
  const busy = pending !== '';
  const messagesRef = React.useRef<HTMLDivElement>(null);

  // 아래쪽 버튼을 눌러도 결과와 문제 목록(맨 위)이 보이게 한다. 이미 보이면 움직이지 않는다.
  React.useEffect(() => {
    if (outcome || failure || problems.length > 0) {
      messagesRef.current?.scrollIntoView?.({ block: 'nearest' });
    }
  }, [outcome, failure, problems]);

  function edit(change: (def: FormDefinition) => FormDefinition) {
    setDefinition(change);
    setDirty(true);
    setOutcome('');
    setFailure('');
    setProblems([]);
  }

  async function send(
    kind: Exclude<Pending, ''>,
    body: Partial<FormDefinition> & { status?: FormStatus },
    describe: (view: FormStateView) => string,
    failureText: string,
  ) {
    setPending(kind);
    setOutcome('');
    setFailure('');
    setProblems([]);
    try {
      const view = await api.updateForm(formId, body);
      setFormState(view);
      if (kind !== 'status') setDirty(false);
      setOutcome(describe(view));
    } catch (error) {
      setFailure(`${failureText} ${friendlyError(error)}`);
    } finally {
      setPending('');
    }
  }

  function save() {
    const found = saveProblems(definition);
    if (found.length > 0) {
      setOutcome('');
      setFailure('');
      setProblems(found);
      return;
    }
    void send(
      'save',
      definition,
      (view) =>
        view.definition_version !== version
          ? `저장됨 · 폼 버전 ${view.definition_version}이 되었어요.`
          : '저장됨',
      '저장하지 못했어요. 지금 편집 내용은 아직 저장되지 않았어요.',
    );
  }

  function publish() {
    const found = publishProblems(definition);
    if (found.length > 0) {
      setOutcome('');
      setFailure('');
      setProblems(found);
      return;
    }
    void send(
      'publish',
      { ...definition, status: 'open' },
      (view) => `배포했어요 · 폼 버전 ${view.definition_version}`,
      '배포하지 못했어요. 초안은 그대로예요.',
    );
  }

  /** 상태만 바꾼다. 저장하지 않은 편집 내용은 함께 보내지 않고 화면에 남긴다. */
  function changeStatus(next: 'open' | 'closed') {
    void send(
      'status',
      { status: next },
      () => (next === 'closed' ? '응답 받기를 껐어요.' : '다시 열었어요.'),
      '상태를 바꾸지 못했어요.',
    );
  }

  async function copyShareUrl() {
    try {
      await navigator.clipboard.writeText(shareUrl ?? '');
      setCopyNote('복사했어요.');
    } catch {
      setCopyNote('복사하지 못했어요. 주소를 직접 복사해 주세요.');
    }
  }

  /** 분기·시작 방식에 문제가 있으면 서버가 허용하지 않을 경로를 보여 주게 되므로 미리보기를 열지 않는다(사람 결정). */
  function openPreview() {
    const found = branchPublishProblems(definition);
    if (found.length > 0) {
      setOutcome('');
      setFailure('');
      setProblems(['미리보기 전에 분기 설정을 고쳐 주세요.', ...found]);
      return;
    }
    setPreviewing(true);
  }

  /**
   * 맨 위와 맨 아래에 같은 작업 버튼을 둔다(사람 요청). 아래쪽은 화면 글자는 같고, 화면 읽기 프로그램이
   * 위쪽과 구분하도록 이름에 "(아래)"를 붙인다.
   */
  function actions(placement: 'top' | 'bottom') {
    // 공통 Button은 이름을 따로 받지 않아 같은 클래스의 버튼을 쓴다.
    const action = (text: string, onClick: () => void, primary = false) => (
      <button
        type="button"
        className={`btn btn-${primary ? 'primary' : 'secondary'}`}
        onClick={onClick}
        disabled={busy}
        aria-label={placement === 'bottom' ? `${text} (아래)` : undefined}
      >
        {text}
      </button>
    );
    return (
      <div className="button-row">
        {action('미리보기', openPreview)}
        {action(status === 'draft' ? '초안 저장' : '변경 저장', save)}
        {status === 'draft' && action('배포', publish, true)}
        {status === 'open' && action('응답 받기 끄기', () => changeStatus('closed'))}
        {status === 'closed' && action('다시 열기', () => changeStatus('open'), true)}
      </div>
    );
  }

  if (previewing) {
    const consent =
      templates?.find((t) => t.consent_template_id === definition.consent_template_id) ?? null;
    return (
      <ResponsePage
        preview
        form={toPreviewForm(definition, consent)}
        onBack={() => setPreviewing(false)}
        onSubmit={() => Promise.resolve()}
        saving={false}
      />
    );
  }

  const questionsBefore = (sectionIndex: number) =>
    definition.sections.slice(0, sectionIndex).reduce((sum, s) => sum + s.questions.length, 0);

  return (
    <div className="page-shell builder-page">
      <Heading
        eyebrow="제작자 워크스페이스 / 폼 편집"
        title="폼 편집"
        desc="질문을 구성하고, 확인한 뒤 배포할 수 있어요."
        action={actions('top')}
      />
      <div ref={messagesRef}>
        <div className="builder-tools">
          <div className="button-row">
            <Badge tone={STATUS_TONE[status]}>
              {statusLabel(status, definition.deadline, Date.now())}
              {version !== null && ` · 폼 버전 ${version}`}
            </Badge>
            <span className="small muted" role="status">
              {busy ? PENDING_TEXT[pending as Exclude<Pending, ''>] : outcome}
              {!busy &&
                dirty &&
                !failure &&
                (outcome ? ' · ' : '') + '저장하지 않은 변경이 있어요.'}
            </span>
          </div>
        </div>
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
      </div>
      {status !== 'draft' && (
        <p className="small muted">
          변경은 저장해야 반영돼요. 응답자가 보는 내용이 바뀌면 새 폼 버전이 되고, 공유 링크는
          그대로예요.
        </p>
      )}
      {shareUrl && (
        <Panel className="builder-section builder-share">
          <label className="builder-field">
            <span>공유 링크</span>
            <input readOnly value={shareUrl} />
          </label>
          <Button variant="secondary" onClick={copyShareUrl}>
            링크 복사
          </Button>
          {copyNote && <small className="muted">{copyNote}</small>}
        </Panel>
      )}
      <fieldset className="builder-fieldset" disabled={busy}>
        <Panel className="builder-intro">
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
            rows={5}
            placeholder="폼 설명을 입력해 주세요"
            onChange={(e) => {
              const note = e.target.value;
              edit((d) => ({ ...d, note }));
            }}
          />
        </Panel>
        {definition.sections.map((section, sectionIndex) => {
          const sectionId = section.section_id;
          const sectionName = `섹션 ${sectionIndex + 1}`;
          return (
            <Panel className="builder-section" key={sectionId}>
              <div className="section-title">
                <input
                  className="builder-section-title"
                  value={section.title}
                  aria-label={`${sectionName} 제목`}
                  placeholder={`${sectionName} 제목`}
                  onChange={(e) => {
                    const title = e.target.value;
                    edit((d) => updateSectionTitle(d, sectionId, title));
                  }}
                />
                <div className="button-row builder-actions">
                  <RowButton
                    label={`${sectionName} 위로`}
                    text="위로"
                    disabled={sectionIndex === 0}
                    onClick={() => edit((d) => moveSection(d, sectionId, -1))}
                  />
                  <RowButton
                    label={`${sectionName} 아래로`}
                    text="아래로"
                    disabled={sectionIndex === definition.sections.length - 1}
                    onClick={() => edit((d) => moveSection(d, sectionId, 1))}
                  />
                  <RowButton
                    label={`${sectionName} 복제`}
                    text="복제"
                    onClick={() => edit((d) => duplicateSection(d, sectionId))}
                  />
                  <RowButton
                    label={`${sectionName} 삭제`}
                    text="삭제"
                    onClick={() => edit((d) => removeSection(d, sectionId))}
                  />
                </div>
              </div>
              {section.questions.map((question, questionIndex) => {
                const questionId = question.question_id;
                return (
                  <QuestionEditor
                    key={questionId}
                    definition={definition}
                    question={question}
                    number={questionsBefore(sectionIndex) + questionIndex + 1}
                    canMoveUp={questionIndex > 0}
                    canMoveDown={questionIndex < section.questions.length - 1}
                    onChange={(next) => edit((d) => replaceQuestion(d, sectionId, next))}
                    onEdit={edit}
                    onMove={(delta) => edit((d) => moveQuestion(d, sectionId, questionId, delta))}
                    onDuplicate={() => edit((d) => duplicateQuestion(d, sectionId, questionId))}
                    onRemove={() => edit((d) => removeQuestion(d, sectionId, questionId))}
                  />
                );
              })}
              <div className="button-row">
                <RowButton
                  label={`${sectionName}에 문항 추가`}
                  text="문항 추가"
                  onClick={() => edit((d) => addQuestion(d, sectionId))}
                />
              </div>
            </Panel>
          );
        })}
        <div className="button-row builder-add-section">
          <Button variant="secondary" onClick={() => edit(addSection)}>
            <Icon name="plus" size={15} /> 섹션 추가
          </Button>
        </div>
        <FormSettingsPanel
          definition={definition}
          firstPublished={version !== null}
          templates={templates}
          onChange={edit}
        />
      </fieldset>
      <div className="builder-add-section">{actions('bottom')}</div>
    </div>
  );
}
