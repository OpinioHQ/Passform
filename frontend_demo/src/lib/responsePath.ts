import type { Answers, BranchRule, Question, Section } from '../api/types';

// 화면의 페이지 넘기기용 경로 계산. 제출 때는 서버가 ResponsePathCalculator로 다시 계산하므로 여기 결과를 믿지 않는다.
// 규칙이 바뀌면 백엔드와 함께 바꾼다(SPEC 5절 178행).

function ruleMatches(rule: BranchRule, answers: Answers): boolean {
  const conditions = rule.when?.conditions ?? [];
  if (conditions.length === 0) {
    return false;
  }
  const holds = (questionId: string, option: string) => {
    const value = answers[questionId];
    return Array.isArray(value) ? value.includes(option) : value === option;
  };
  return rule.when.match === 'any'
    ? conditions.some((condition) => holds(condition.question_id, condition.option))
    : conditions.every((condition) => holds(condition.question_id, condition.option));
}

function goesToSubmit(rule: BranchRule): boolean {
  return rule.go_to === 'submit' || (typeof rule.go_to === 'object' && rule.go_to.submit === true);
}

export function flatten(sections: Section[]): Question[] {
  return sections.flatMap((section) => section.questions);
}

export function computePath(sections: Section[], answers: Answers): Question[] {
  const ordered = flatten(sections);
  const path: Question[] = [];
  let index = 0;
  while (index < ordered.length) {
    const current = ordered[index];
    if (!current) {
      break;
    }
    path.push(current);
    const rule = current.branch_rules.find((candidate) => ruleMatches(candidate, answers));
    if (!rule) {
      index += 1;
      continue;
    }
    if (goesToSubmit(rule)) {
      break;
    }
    const goTo = typeof rule.go_to === 'object' ? rule.go_to : {};
    const targetId = goTo.question_id
      ?? sections.find((section) => section.section_id === goTo.section_id)?.questions[0]?.question_id;
    const target = ordered.findIndex((question) => question.question_id === targetId);
    index = target > index ? target : index + 1;
  }
  return path;
}

export function isBlank(value: Answers[string] | undefined): boolean {
  if (value === undefined) {
    return true;
  }
  return Array.isArray(value) ? value.length === 0 : value.trim() === '';
}
