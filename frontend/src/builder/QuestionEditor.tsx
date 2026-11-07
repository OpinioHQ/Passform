import React from 'react';
import { Badge } from '../components/UI';
import type { FormDefinition, InputFormatKind, Question, QuestionType } from '../types/api';
import { BranchRulesEditor } from './BranchRulesEditor';
import { renameOption } from './branchRules';
import {
  addOption,
  isChoiceType,
  ownInfoQuestion,
  removeOption,
  setCharacterLimit,
  setInputFormat,
  setOwnInfo,
  setQuestionNote,
  setQuestionType,
} from './formDefinition';

export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  short_answer: '단답형',
  long_answer: '장문형',
  multiple_choice: '단일 선택',
  dropdown: '드롭다운',
  checkbox: '복수 선택',
};

const INPUT_FORMAT_LABELS: Record<InputFormatKind, string> = {
  name: '이름',
  mobile_phone: '휴대전화 번호',
  landline_phone: '집전화 번호',
  address: '주소',
  student_id: '학번',
  email: '이메일',
  date: '날짜',
};

/** 양의 정수만 받는다. 비우면 `null`이고, 그 밖의 입력은 무시한다(`undefined`). */
export function parsePositiveInt(text: string): number | null | undefined {
  if (text.trim() === '') return null;
  const value = Number(text);
  return Number.isInteger(value) && value >= 1 ? value : undefined;
}

/** 한 줄에 여럿 있어 화면 글자만으로는 어느 문항의 버튼인지 구분되지 않는다. */
export function RowButton({
  label,
  text,
  onClick,
  disabled,
}: {
  label: string;
  text: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      className="btn btn-ghost"
      aria-label={label}
      onClick={onClick}
      disabled={disabled}
    >
      {text}
    </button>
  );
}

export function QuestionEditor({
  definition,
  question: q,
  number,
  canMoveUp,
  canMoveDown,
  onChange,
  onEdit,
  onMove,
  onDuplicate,
  onRemove,
}: {
  definition: FormDefinition;
  question: Question;
  number: number;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onChange: (next: Question) => void;
  /** 다른 문항의 분기 조건까지 바꾸는 편집(선택지 문구, 분기 규칙). */
  onEdit: (change: (def: FormDefinition) => FormDefinition) => void;
  onMove: (delta: -1 | 1) => void;
  onDuplicate: () => void;
  onRemove: () => void;
}) {
  const [ownInfoCleared, setOwnInfoCleared] = React.useState(false);
  const name = `질문 ${number}`;
  const duplicated = (option: string) => q.options.filter((o) => o === option).length > 1;

  /** 문구나 형식을 바꿔 본인 정보 선택이 지워졌으면 다시 확인하도록 알린다(AC72). */
  function changeWithOwnInfoReset(next: Question) {
    if (q.profile_key !== null && next.profile_key === null) setOwnInfoCleared(true);
    onChange(next);
  }

  return (
    <div className="builder-question">
      <div className="question-top">
        <span className="overline">질문 {String(number).padStart(2, '0')}</span>
        <Badge>{QUESTION_TYPE_LABELS[q.question_type]}</Badge>
        <div className="button-row builder-actions">
          <RowButton
            label={`${name} 위로`}
            text="위로"
            onClick={() => onMove(-1)}
            disabled={!canMoveUp}
          />
          <RowButton
            label={`${name} 아래로`}
            text="아래로"
            onClick={() => onMove(1)}
            disabled={!canMoveDown}
          />
          <RowButton label={`${name} 복제`} text="복제" onClick={onDuplicate} />
          <RowButton label={`${name} 삭제`} text="삭제" onClick={onRemove} />
        </div>
      </div>
      <input
        value={q.question_note}
        aria-label={`${name} 문구`}
        placeholder="질문 문구를 입력해 주세요"
        onChange={(e) => changeWithOwnInfoReset(setQuestionNote(q, e.target.value))}
      />
      <label className="builder-field">
        <span>보조 설명 (선택)</span>
        <input
          value={q.question_description ?? ''}
          onChange={(e) => onChange({ ...q, question_description: e.target.value || null })}
        />
      </label>
      <label className="builder-field">
        <span>문항 유형</span>
        <select
          value={q.question_type}
          onChange={(e) => onChange(setQuestionType(q, e.target.value as QuestionType))}
        >
          {Object.entries(QUESTION_TYPE_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>

      {isChoiceType(q.question_type) && (
        <div className="builder-field">
          <span>선택지</span>
          {q.options.map((option, index) => (
            <div className="builder-option" key={index}>
              <input
                value={option}
                aria-label={`${name} 선택지 ${index + 1}`}
                onChange={(e) => {
                  const text = e.target.value;
                  onEdit((d) => renameOption(d, q.question_id, index, text));
                }}
              />
              <RowButton
                label={`${name} 선택지 ${index + 1} 삭제`}
                text="삭제"
                onClick={() => onChange(removeOption(q, index))}
              />
              {duplicated(option) && <small className="muted">같은 문구의 선택지가 있어요.</small>}
            </div>
          ))}
          <RowButton
            label={`${name} 선택지 추가`}
            text="선택지 추가"
            onClick={() => onChange(addOption(q))}
          />
        </div>
      )}

      {q.question_type === 'short_answer' && (
        <>
          <label className="builder-field">
            <span>입력 형식</span>
            <select
              value={q.input_format?.kind ?? ''}
              onChange={(e) =>
                changeWithOwnInfoReset(
                  setInputFormat(q, (e.target.value || null) as InputFormatKind | null),
                )
              }
            >
              <option value="">형식 없음</option>
              {Object.entries(INPUT_FORMAT_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <p className="small muted">
            이름·연락처·주소·학번·이메일처럼 개인정보를 묻는 문항에는 입력 형식을 지정해 주세요.
          </p>
          {q.input_format && (
            <fieldset className="builder-field builder-own-info">
              <legend>{ownInfoQuestion(q.input_format.kind)}</legend>
              <label>
                <input
                  type="radio"
                  name={`own-info-${q.question_id}`}
                  checked={q.profile_key !== null}
                  onChange={() => {
                    setOwnInfoCleared(false);
                    onChange(setOwnInfo(q, true));
                  }}
                />{' '}
                예
              </label>
              <label>
                <input
                  type="radio"
                  name={`own-info-${q.question_id}`}
                  checked={q.profile_key === null}
                  onChange={() => {
                    setOwnInfoCleared(false);
                    onChange(setOwnInfo(q, false));
                  }}
                />{' '}
                아니요
              </label>
              {ownInfoCleared && (
                <small className="muted">
                  문구나 입력 형식이 바뀌어 선택을 지웠어요. 다시 확인해 주세요.
                </small>
              )}
            </fieldset>
          )}
        </>
      )}

      {!isChoiceType(q.question_type) && (
        <label className="builder-field">
          <span>최대 글자 수 (선택)</span>
          <input
            type="number"
            min={1}
            step={1}
            value={q.character_limit ?? ''}
            onChange={(e) => {
              const limit = parsePositiveInt(e.target.value);
              if (limit !== undefined) onChange(setCharacterLimit(q, limit));
            }}
          />
        </label>
      )}

      <label className="builder-check">
        <input
          type="checkbox"
          checked={q.required}
          onChange={(e) => onChange({ ...q, required: e.target.checked })}
        />{' '}
        필수 문항
      </label>

      <BranchRulesEditor
        definition={definition}
        questionId={q.question_id}
        name={name}
        onChange={onEdit}
      />
    </div>
  );
}
