import type { CombinedQuestion, PublicForm, RequestContext, Results } from '../api/types';

export interface ExampleQuestion {
  label: string;
  context: RequestContext;
}

const YES_NO = new Set(['예', '아니요', '네', '아니오']);

// 받침이 있으면 "이", 없으면 "가"
function subjectParticle(word: string): string {
  const last = word.charCodeAt(word.length - 1);
  if (last < 0xac00 || last > 0xd7a3) {
    return '이(가)';
  }
  return (last - 0xac00) % 28 === 0 ? '가' : '이';
}

// 여러 문항에 걸리면 되묻기가 되므로 한 문항에만 있고 응답이 있는 선택지만 고른다.
function usableOptions(questions: CombinedQuestion[]): Map<string, string> {
  const owners = new Map<string, Set<string>>();
  for (const question of questions) {
    for (const option of question.options) {
      const owner = owners.get(option.label) ?? new Set<string>();
      owner.add(question.question_id);
      owners.set(option.label, owner);
    }
  }
  const usable = new Map<string, string>();
  for (const question of questions) {
    if (usable.has(question.question_id)) {
      continue;
    }
    const option = question.options.find(
      (candidate) => candidate.count > 0 && !YES_NO.has(candidate.label) && owners.get(candidate.label)?.size === 1,
    );
    if (option) {
      usable.set(question.question_id, option.label);
    }
  }
  return usable;
}

// 버튼은 그 폼에서 실제로 되는 요청만 만든다. 파서를 거치지 않고 확정한 context로 보내므로 되묻기로 빠지지 않는다.
export function buildExamples(results: Results, form: PublicForm | null): ExampleQuestion[] {
  const examples: ExampleQuestion[] = [
    { label: '전체 응답 수', context: { conditions: [], operation: 'count' } },
  ];
  const options = [...usableOptions(results.combined.questions).values()];
  const [first, second] = options;
  if (first) {
    examples.push({ label: `${first} 응답 수`, context: { conditions: [first], operation: 'count' } });
    examples.push({ label: `${first} 응답 목록`, context: { conditions: [first], operation: 'list' } });
    examples.push({
      label: `${first}${subjectParticle(first)} 아닌 응답 수`,
      context: { conditions: [first], match: 'not', operation: 'count' },
    });
  }
  if (first && second) {
    examples.push({
      label: `${first} ${second} 응답 수`,
      context: { conditions: [first, second], match: 'all', operation: 'count' },
    });
  }
  if (form?.consent.ai_summary) {
    examples.push({ label: '전체 요약', context: { conditions: [], operation: 'summarize' } });
    const textQuestion = form.sections
      .flatMap((section) => section.questions)
      .find((question) => question.question_type === 'long_answer' && !question.profile_key && !question.input_format);
    if (textQuestion) {
      examples.push({
        label: `${textQuestion.question_note} 요약`,
        context: { conditions: [], operation: 'summarize', target: textQuestion.question_note },
      });
    }
  }
  return examples;
}
