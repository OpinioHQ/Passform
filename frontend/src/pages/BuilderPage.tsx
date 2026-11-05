import React from 'react';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import type { FormDefinition, Question } from '../types/api';
export function BuilderPage({
  initial,
  isNew = false,
  onPreview,
  onExplore,
}: {
  initial: Pick<FormDefinition, 'title' | 'note' | 'sections'>;
  isNew?: boolean;
  onPreview?: () => void;
  onExplore?: () => void;
}) {
  const [title, setTitle] = React.useState(initial.title);
  const [note, setNote] = React.useState(initial.note);
  const [questions, setQuestions] = React.useState<Question[]>(
    initial.sections.flatMap((s) => s.questions),
  );
  const [mode, setMode] = React.useState<'default' | 'quick'>('default');
  const [saved, setSaved] = React.useState('');
  function add() {
    setQuestions((q) => [
      ...q,
      {
        question_id: `draft-${q.length + 1}`,
        template_id: null,
        question_note: '새 질문',
        question_type: 'short_answer',
        required: false,
        options: [],
        profile_key: null,
        attachments: [],
        branch_rules: [],
      },
    ]);
    setSaved('');
  }
  function updateQuestion(id: string, v: string) {
    setQuestions((xs) => xs.map((x) => (x.question_id === id ? { ...x, question_note: v } : x)));
    setSaved('');
  }
  return (
    <div className="page-shell builder-page">
      <Heading
        eyebrow="제작자 워크스페이스 / 폼 편집"
        title="폼 편집"
        desc="질문을 구성하고, 확인한 뒤 배포할 수 있어요."
        action={
          <div className="button-row">
            <Button variant="secondary" onClick={onPreview} disabled={isNew}>
              미리보기
            </Button>
            <Button
              onClick={() =>
                setSaved('데모 화면에서 편집 내용을 보관했습니다. 실제 배포 API 연동 전입니다.')
              }
            >
              초안 저장
            </Button>
          </div>
        }
      />
      <div className="builder-tools">
        <div className="segmented">
          <button className={mode === 'default' ? 'active' : ''} onClick={() => setMode('default')}>
            기본 모드
          </button>
          <button className={mode === 'quick' ? 'active' : ''} onClick={() => setMode('quick')}>
            빠른 모드
          </button>
        </div>
        {isNew ? (
          <Badge>초안</Badge>
        ) : (
          <div className="button-row">
            <Badge tone="green">배포 중 · v1</Badge>
            <Button variant="secondary" onClick={onExplore}>
              응답 6
            </Button>
          </div>
        )}
      </div>
      <Panel className="builder-intro">
        <input
          className="builder-title"
          value={title}
          aria-label="폼 제목"
          onChange={(e) => setTitle(e.target.value)}
        />
        <textarea
          aria-label="폼 설명"
          value={note}
          rows={2}
          onChange={(e) => setNote(e.target.value)}
        />
        <p className="muted small">
          변경 사항은 실제 API의 저장·배포를 연결한 후에 서버에 반영됩니다.
        </p>
      </Panel>
      <Panel className="builder-section">
        <div className="section-title">
          {!isNew && <h3>섹션 01 · 함께 일하는 경험</h3>}
          <Badge tone="neutral">{mode === 'quick' ? '빠른 모드 미리보기' : '기본 모드'}</Badge>
        </div>
        {questions.map((q, i) => (
          <div className="builder-question" key={q.question_id}>
            <div className="question-top">
              <span className="overline">질문 {String(i + 1).padStart(2, '0')}</span>
              <Badge>
                {
                  (
                    {
                      short_answer: '단답형',
                      long_answer: '장문형',
                      multiple_choice: '단일 선택',
                      checkbox: '복수 선택',
                      dropdown: '드롭다운',
                    } as const
                  )[q.question_type]
                }
              </Badge>
            </div>
            <input
              value={q.question_note}
              aria-label={`질문 ${i + 1} 문구`}
              onChange={(e) => updateQuestion(q.question_id, e.target.value)}
            />
            <div className="muted small">
              {q.options.length ? q.options.join(' / ') : '응답자의 답변이 들어갈 자리'} ·{' '}
              {q.required ? '필수' : '선택'}
            </div>
          </div>
        ))}
        <div className="button-row">
          <Button variant="secondary" onClick={add}>
            <Icon name="plus" size={15} /> 문항 추가
          </Button>
          <Button
            variant="ghost"
            onClick={() => setSaved('섹션 추가는 실제 폼 정의 구조와 연결할 예정입니다.')}
          >
            섹션 추가
          </Button>
        </div>
      </Panel>
      {saved && (
        <p className="info" role="status">
          {saved}
        </p>
      )}
    </div>
  );
}
