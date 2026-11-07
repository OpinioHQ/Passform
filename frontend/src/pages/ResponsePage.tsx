import React from 'react';
import { formatKst } from '../builder/deadline';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import { demoResponseScreen } from '../demo/screens';
import type { PublicForm, Question, ResponseAnswer, SubmitResponseInput } from '../types/api';

interface Props {
  form: PublicForm;
  onBack: () => void;
  onSubmit: (data: SubmitResponseInput) => Promise<void>;
  saving: boolean;
  onDraftChange?: (answers: Record<string, string>) => void;
  /** 빌더 미리보기(AC81). 제출을 부르지 않고, 데모 값과 임시 저장을 숨긴다. */
  preview?: boolean;
}
export function ResponsePage({ form, onBack, onSubmit, saving, onDraftChange, preview }: Props) {
  const [answers, setAnswers] = React.useState<Record<string, string>>({});
  const [consent, setConsent] = React.useState(false);
  const [autofill, setAutofill] = React.useState(false);
  const [error, setError] = React.useState('');
  const [saved, setSaved] = React.useState(false);
  const [previewNotice, setPreviewNotice] = React.useState(false);
  const questions = form.sections.flatMap((x) => x.questions);
  const filled = questions.filter((x) => Boolean(answers[x.question_id])).length;
  const percent = questions.length ? Math.round((filled / questions.length) * 100) : 0;
  function update(k: string, v: string) {
    const next = { ...answers, [k]: v };
    setAnswers(next);
    setSaved(false);
    onDraftChange?.(next);
  }
  function applyProfile() {
    setAnswers((p) => ({ ...p, ...demoResponseScreen.autofillAnswers }));
    setAutofill(false);
    setSaved(false);
  }
  function storeDraft() {
    setSaved(true);
    onDraftChange?.(answers);
  }
  async function submit() {
    if (preview) {
      setError('');
      setPreviewNotice(true);
      return;
    }
    const missing = questions.filter((q) => q.required && !answers[q.question_id]?.trim());
    if (missing.length) {
      setError(`필수 문항 ${missing.map((q) => q.question_note).join(', ')}을(를) 채워 주세요.`);
      return;
    }
    if (!consent) {
      setError('필수 개인정보 동의가 필요합니다.');
      return;
    }
    if (answers.q2 && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(answers.q2)) {
      setError('이메일 형식을 확인해 주세요.');
      return;
    }
    setError('');
    const body: SubmitResponseInput = {
      form_version: form.definition_version,
      answers: Object.entries(answers)
        .filter(([, v]) => v !== '')
        .map(([question_id, value]): ResponseAnswer => ({ question_id, value })),
      consent_agreed: true,
    };
    await onSubmit(body);
  }
  function renderInput(q: Question) {
    const value = answers[q.question_id] || '';
    if (q.question_type === 'dropdown')
      return (
        <select value={value} onChange={(e) => update(q.question_id, e.target.value)}>
          <option value="">선택해 주세요</option>
          {q.options.map((o) => (
            <option key={o}>{o}</option>
          ))}
        </select>
      );
    if (q.question_type === 'multiple_choice' || q.question_type === 'checkbox')
      return (
        <div className="option-stack">
          {q.options.map((o) => (
            <label className={`option ${value === o ? 'checked' : ''}`} key={o}>
              <input
                type="radio"
                name={q.question_id}
                checked={value === o}
                onChange={() => update(q.question_id, o)}
              />
              {o}
            </label>
          ))}
        </div>
      );
    if (q.question_type === 'long_answer')
      return (
        <>
          <textarea
            value={value}
            maxLength={q.character_limit || undefined}
            placeholder={preview ? undefined : demoResponseScreen.longAnswerPlaceholder}
            onChange={(e) => update(q.question_id, e.target.value)}
            rows={4}
          />
          <span className="field-count">
            {value.length}/{q.character_limit || '∞'}
          </span>
        </>
      );
    return (
      <>
        <input
          type={q.profile_key === 'email' ? 'email' : 'text'}
          value={value}
          maxLength={q.character_limit || undefined}
          placeholder={q.question_description || `${q.question_note}을(를) 입력해 주세요`}
          onChange={(e) => update(q.question_id, e.target.value)}
        />
        <span className="field-count">
          {value.length}/{q.character_limit || '∞'}
        </span>
      </>
    );
  }
  return (
    <div className="page-shell response-page">
      <Heading
        eyebrow={
          preview
            ? '제작자 워크스페이스 / 폼 편집 / 미리보기'
            : '내 워크스페이스  /  내 폼함 / 응답 작성'
        }
        title={preview ? '미리보기' : '응답 작성'}
        desc={
          preview
            ? '응답자에게 보이는 모습이에요. 저장 전 내용이고, 답을 입력해도 응답이 만들어지지 않아요.'
            : '질문을 확인하고, 편한 속도로 답변해 주세요.'
        }
        action={
          <Button variant="secondary" onClick={onBack}>
            <Icon name="back" size={16} /> {preview ? '편집으로 돌아가기' : '내 폼함으로'}
          </Button>
        }
      />
      <Panel className="response-hero">
        <div>
          <Badge tone="amber">{preview ? '미리보기' : '작성 중 · 데모'}</Badge>
          <h2>{form.title}</h2>
          {form.note.trim() && <p className="form-note">{form.note}</p>}
          <p className="muted">
            {!preview && `${demoResponseScreen.owner} · `}
            {form.deadline ? `${formatKst(form.deadline)} 마감` : '마감 없음'}
          </p>
          {form.payment_link?.startsWith('https://') && (
            <p className="small">
              송금 링크:{' '}
              <a href={form.payment_link} target="_blank" rel="noopener noreferrer">
                {form.payment_link}
              </a>
            </p>
          )}
        </div>
        <div className="completion">
          <span>나의 작성 진행</span>
          <strong>
            {filled} / {questions.length}문항 · {percent}%
          </strong>
          <div className="track">
            <div style={{ width: `${percent}%` }} />
          </div>
          {!preview && (
            <small className="muted">
              현재 브라우저의 데모 상태입니다. 실제 계정 초안 저장과 별개예요.
            </small>
          )}
        </div>
      </Panel>
      {form.sections.map((section, sectionIndex) => (
        <Panel className="questions" key={section.section_id}>
          <div className="section-title">
            <div>
              <span className="overline">
                {String(sectionIndex + 1).padStart(2, '0')} · {section.title}
              </span>
              {!preview && <h3>{demoResponseScreen.sectionHeading}</h3>}
            </div>
            {!preview && (
              <Button variant="secondary" onClick={() => setAutofill(true)}>
                내 정보로 채우기
              </Button>
            )}
          </div>
          {section.questions.map((q, i) => (
            <div className="question" key={q.question_id}>
              <div className="question-heading">
                <span className="question-num">{String(i + 1).padStart(2, '0')}</span>
                <label htmlFor={q.question_id}>{q.question_note}</label>
                <Badge tone={q.required ? 'blue' : 'neutral'}>{q.required ? '필수' : '선택'}</Badge>
              </div>
              {q.question_description && <small className="muted">{q.question_description}</small>}
              <div className="question-input" id={q.question_id}>
                {renderInput(q)}
              </div>
              {q.profile_key && <small className="muted">본인 정보</small>}
            </div>
          ))}
        </Panel>
      ))}
      <Panel className="consent">
        <label>
          <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />{' '}
          개인정보 수집 및 이용에 동의합니다. (필수)
        </label>
        <p className="small muted">
          수집 항목: {form.consent.items.join(', ')} · 목적: {form.consent.purpose}
        </p>
        <p className="small muted keep-lines">보유 기간: {form.consent.retention}</p>
        <p className="small muted">{form.consent.refusal_notice}</p>
      </Panel>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {previewNotice && (
        <p className="info" role="status">
          미리보기라 제출되지 않아요.
        </p>
      )}
      <div className="form-footer">
        {!preview && (
          <Button variant="secondary" onClick={storeDraft}>
            임시 저장
          </Button>
        )}
        <span className="small muted">
          {saved
            ? '데모 초안이 현재 화면에 보관되었습니다.'
            : '필수 문항을 채우면 제출할 수 있어요.'}
        </span>
        <Button onClick={submit} disabled={saving}>
          {saving ? '제출 중…' : '답변 제출'} <Icon name="arrow" size={16} />
        </Button>
      </div>
      {autofill && (
        <div className="modal-scrim" role="presentation">
          <div className="modal" role="dialog" aria-label="내 정보로 채우기">
            <button className="modal-close" onClick={() => setAutofill(false)} aria-label="닫기">
              <Icon name="x" />
            </button>
            <Badge tone="blue">적용 전 확인</Badge>
            <h2>내 정보로 채우기</h2>
            <p className="muted">프로필에서 가져온 값을 확인하고 수정해 주세요.</p>
            <Panel>
              {demoResponseScreen.autofillRows.map((row) => (
                <div className="profile-row" key={row.label}>
                  <b>{row.label}</b>
                  <span>{row.value}</span>
                </div>
              ))}
            </Panel>
            <p className="small muted">
              제작자가 본인 정보로 지정한 문항에만 적용합니다. 실제 프로필 API와는 아직 연결되지
              않았어요.
            </p>
            <Button onClick={applyProfile}>
              확인한 {demoResponseScreen.autofillRows.length}개 값 적용
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
