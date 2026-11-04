import type { AnalysisResponse, ClarifyOption, QuestionOption, RequestContext } from '../api/types';
import { Button, Card } from './ui';

const OPERATION_LABELS: Record<string, string> = { count: '인원 수', list: '응답 목록', summarize: '요약' };
const MATCH_LABELS: Record<string, string> = { all: '모두 만족', any: '하나라도 만족' };

interface Props {
  clarify: NonNullable<AnalysisResponse['clarify']>;
  context: RequestContext | null;
  disabled: boolean;
  onFollowUp: (label: string, context: RequestContext) => void;
  onShowDashboard: () => void;
}

function isQuestionOption(option: ClarifyOption): option is QuestionOption {
  return typeof option === 'object';
}

export default function ClarifyCard({ clarify, context, disabled, onFollowUp, onShowDashboard }: Props) {
  const { reason, message, options } = clarify;
  const stringOptions = options.filter((option): option is string => typeof option === 'string');
  const questionOptions = options.filter(isQuestionOption);

  const buttons: { label: string; context: RequestContext }[] = [];
  if (context) {
    if (reason === 'missing_operation' || reason === 'summary_disabled') {
      stringOptions.forEach((operation) =>
        buttons.push({
          label: OPERATION_LABELS[operation] ?? operation,
          context: { ...context, operation: operation as RequestContext['operation'] },
        }),
      );
    }
    if (reason === 'missing_match' || (reason === 'ambiguous_match' && stringOptions.length > 0)) {
      stringOptions.forEach((match) =>
        buttons.push({ label: MATCH_LABELS[match] ?? match, context: { ...context, match: match as RequestContext['match'] } }),
      );
    }
    if (reason === 'double_negation') {
      const { match: _omitted, ...withoutMatch } = context;
      void _omitted;
      stringOptions.forEach((sentence) => buttons.push({ label: sentence, context: withoutMatch }));
    }
    if (reason === 'unsortable_field' && context.sort) {
      const { order } = context.sort;
      uniqueNotes(questionOptions).forEach((note) =>
        buttons.push({ label: `${note}(으)로 정렬`, context: { ...context, sort: { by: note, order } } }),
      );
    }
    if (reason === 'unsummarizable_field' || (reason === 'unknown_condition' && context.target)) {
      uniqueNotes(questionOptions).forEach((note) => buttons.push({ label: `${note} 요약`, context: { ...context, target: note } }));
    }
    // 같은 표현이 여러 문항에 걸리면 "문항 - 값"으로 고정해 다시 묻는다(AC25).
    if (reason === 'ambiguous_match' && questionOptions.length > 0 && context.conditions.length === 1) {
      const condition = context.conditions[0];
      uniqueNotes(questionOptions).forEach((note) =>
        buttons.push({ label: `${note}의 ${condition}`, context: { ...context, conditions: [`${note} - ${condition}`] } }),
      );
    }
  }

  const listed = reason === 'unknown_condition' && !context?.target ? mergeByNote(questionOptions) : [];

  return (
    <Card className="border-accent">
      <p className="text-xs font-medium text-accent">조금 더 알려 주세요</p>
      <p className="mt-1 text-sm leading-relaxed">{message}</p>
      {buttons.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {buttons.map((button) => (
            <Button key={button.label} disabled={disabled} onClick={() => onFollowUp(button.label, button.context)}>{button.label}</Button>
          ))}
        </div>
      ) : null}
      {reason === 'unsupported_scope' || reason === 'missing_operation' ? (
        <button type="button" onClick={onShowDashboard} className="mt-3 text-sm text-accent underline">통계 화면 보기</button>
      ) : null}
      {listed.length > 0 ? (
        <ul className="mt-3 grid gap-1 text-sm text-muted">
          {listed.map(([note, choices]) => (
            <li key={note}>{note}: {choices.join(', ')}</li>
          ))}
        </ul>
      ) : null}
    </Card>
  );
}

function uniqueNotes(options: QuestionOption[]): string[] {
  return [...new Set(options.map((option) => option.question_note))];
}

// 같은 문항이 버전마다 반복되지 않게 문구로 묶고 선택지를 합친다.
function mergeByNote(options: QuestionOption[]): [string, string[]][] {
  const merged = new Map<string, Set<string>>();
  for (const option of options) {
    const choices = merged.get(option.question_note) ?? new Set<string>();
    option.options?.forEach((choice) => choices.add(choice));
    merged.set(option.question_note, choices);
  }
  return [...merged].map(([note, choices]) => [note, [...choices]]);
}
