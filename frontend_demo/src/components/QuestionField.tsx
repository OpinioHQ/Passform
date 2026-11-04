import type { AnswerValue, Question } from '../api/types';
import { formatHint } from '../lib/answerFormat';

interface Props {
  question: Question;
  value: AnswerValue | undefined;
  error: string | null;
  onChange: (value: AnswerValue) => void;
}

const INPUT = 'mt-2 w-full rounded-lg border border-line bg-surface px-3 py-2 text-base';

const INPUT_MODES: Record<string, 'tel' | 'email' | 'numeric'> = {
  mobile_phone: 'tel',
  landline_phone: 'tel',
  email: 'email',
};

export default function QuestionField({ question, value, error, onChange }: Props) {
  const text = typeof value === 'string' ? value : '';
  const selected = Array.isArray(value) ? value : [];
  const limit = question.character_limit ?? null;
  const hint = formatHint(question);
  const kind = question.input_format?.kind;

  return (
    <fieldset>
      <legend className="font-medium">
        {question.question_note}
        {question.required ? <span className="ml-1 text-danger">*</span> : null}
      </legend>
      {question.question_description ? <p className="mt-1 text-sm text-muted">{question.question_description}</p> : null}

      {question.question_type === 'multiple_choice' ? (
        <div className="mt-2 grid gap-1">
          {question.options.map((option) => (
            <label key={option} className="flex min-h-11 items-center gap-2 text-sm">
              <input
                type="radio"
                name={question.question_id}
                checked={value === option}
                onChange={() => onChange(option)}
                className="h-5 w-5"
              />
              {option}
            </label>
          ))}
        </div>
      ) : null}

      {question.question_type === 'dropdown' ? (
        <select value={text} onChange={(event) => onChange(event.target.value)} className={INPUT}>
          <option value="">선택해 주세요</option>
          {question.options.map((option) => (
            <option key={option} value={option}>{option}</option>
          ))}
        </select>
      ) : null}

      {question.question_type === 'checkbox' ? (
        <div className="mt-2 grid gap-1">
          {question.options.map((option) => (
            <label key={option} className="flex min-h-11 items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={selected.includes(option)}
                onChange={(event) =>
                  onChange(event.target.checked ? [...selected, option] : selected.filter((item) => item !== option))
                }
                className="h-5 w-5"
              />
              {option}
            </label>
          ))}
        </div>
      ) : null}

      {question.question_type === 'short_answer' ? (
        <input
          value={text}
          onChange={(event) => onChange(event.target.value)}
          placeholder={question.input_format?.placeholder}
          inputMode={kind ? INPUT_MODES[kind] : undefined}
          aria-invalid={error !== null}
          className={INPUT}
        />
      ) : null}

      {question.question_type === 'long_answer' ? (
        <textarea value={text} rows={4} onChange={(event) => onChange(event.target.value)} className={INPUT} />
      ) : null}

      {limit !== null ? (
        <p className={`mt-1 text-right text-xs ${[...text].length > limit ? 'text-danger' : 'text-muted'}`}>
          {[...text].length}/{limit}
        </p>
      ) : null}
      {hint && !error ? <p className="mt-1 text-xs text-muted">{hint}</p> : null}
      {error ? <p className="mt-1 text-sm text-danger">{error}</p> : null}
    </fieldset>
  );
}
