import type {
  BranchCondition,
  BranchRule,
  BranchTarget,
  DisplayMode,
  FormDefinition,
  Question,
} from '../types/api';

/**
 * 분기 규칙 편집과 배포 전 검사. 규칙의 해석(경로 계산)은 `response/responsePath.ts`가 맡고, 여기서는 만들지 않는다.
 * 배포 전 검사는 서버 `FormPublishValidator`의 분기·시작 방식 검사를 옮긴 것이며 판정의 정본은 서버다.
 * 화면 목록은 고를 수 있는 것만 보여 줘 틀린 규칙을 처음부터 못 만들게 하고, 편집으로 깨진 규칙은 지우지 않고 문제로 보인다.
 */

const CHOICE_TYPES: readonly string[] = ['multiple_choice', 'dropdown', 'checkbox'];
const isChoice = (q: Question) => CHOICE_TYPES.includes(q.question_type);

/** 전체 문항 순서 안의 위치. 서버 `FormPublishValidator.Question`과 같은 값이다. */
export interface PlacedQuestion {
  question: Question;
  sectionIndex: number;
  /** 섹션 안의 순서(0부터). */
  indexInSection: number;
  /** 폼 전체의 순서(0부터). */
  position: number;
  lastInSection: boolean;
}

export function placeQuestions(def: FormDefinition): PlacedQuestion[] {
  const placed: PlacedQuestion[] = [];
  def.sections.forEach((section, sectionIndex) =>
    section.questions.forEach((question, indexInSection) =>
      placed.push({
        question,
        sectionIndex,
        indexInSection,
        position: placed.length,
        lastInSection: indexInSection === section.questions.length - 1,
      }),
    ),
  );
  return placed;
}

/** 섹션은 제목으로 보여 준다. 제목이 없으면 순서로 부른다. */
export function sectionLabel(def: FormDefinition, sectionIndex: number): string {
  return def.sections[sectionIndex]?.title.trim() || `섹션 ${sectionIndex + 1}`;
}

/** "섹션 2의 3번 문항: 문구". ID는 화면에 쓰지 않는다. */
export function questionLabel(placed: PlacedQuestion): string {
  const base = `섹션 ${placed.sectionIndex + 1}의 ${placed.indexInSection + 1}번 문항`;
  const note = placed.question.question_note.trim();
  return note ? `${base}: ${note}` : base;
}

export const hasBranchRules = (def: FormDefinition) =>
  def.sections.some((s) => s.questions.some((q) => q.branch_rules.length > 0));

/** 분기 유무에 따라 고를 수 있는 시작 방식(SPEC 177행, AC46). */
export const allowedDisplayModes = (branched: boolean): DisplayMode[] =>
  branched ? ['one_question', 'section_page'] : ['one_question', 'all_questions'];

/** 조건에 쓸 수 있는 문항: 그 문항 자신과 앞의 선택형 문항(SPEC 178행). */
export function conditionCandidates(def: FormDefinition, ownerId: string): PlacedQuestion[] {
  const placed = placeQuestions(def);
  const owner = placed.find((p) => p.question.question_id === ownerId);
  if (!owner) return [];
  return placed.filter((p) => p.position <= owner.position && isChoice(p.question));
}

export interface TargetChoice {
  go_to: BranchTarget;
  label: string;
}

/** 이동할 수 있는 곳: 뒤쪽 섹션, 뒤쪽 문항(`section_page`에서는 빼고), 제출. */
export function targetCandidates(def: FormDefinition, ownerId: string): TargetChoice[] {
  const placed = placeQuestions(def);
  const owner = placed.find((p) => p.question.question_id === ownerId);
  if (!owner) return [];
  const choices: TargetChoice[] = [];
  def.sections.forEach((section, index) => {
    if (index > owner.sectionIndex) {
      choices.push({
        go_to: { section_id: section.section_id },
        label: `섹션: ${sectionLabel(def, index)}`,
      });
    }
  });
  if (def.settings.display.default_mode !== 'section_page') {
    placed
      .filter((p) => p.position > owner.position)
      .forEach((p) =>
        choices.push({ go_to: { question_id: p.question.question_id }, label: questionLabel(p) }),
      );
  }
  choices.push({ go_to: { submit: true }, label: '제출' });
  return choices;
}

/** `section_page`에서는 섹션의 마지막 문항에만 규칙을 둔다(AC46). */
export const canHoldRules = (def: FormDefinition, placed: PlacedQuestion) =>
  def.settings.display.default_mode !== 'section_page' || placed.lastInSection;

function defaultCondition(def: FormDefinition, ownerId: string): BranchCondition | null {
  const candidates = conditionCandidates(def, ownerId);
  // 자기 자신이 선택형이면 자기 자신, 아니면 가장 가까운 앞 문항.
  const source = candidates[candidates.length - 1];
  if (!source) return null;
  return { question_id: source.question.question_id, option: source.question.options[0] ?? '' };
}

function withRules(
  def: FormDefinition,
  questionId: string,
  change: (rules: BranchRule[]) => BranchRule[],
): FormDefinition {
  return {
    ...def,
    sections: def.sections.map((s) => ({
      ...s,
      questions: s.questions.map((q) =>
        q.question_id === questionId ? { ...q, branch_rules: change(q.branch_rules) } : q,
      ),
    })),
  };
}

/** 조건으로 쓸 선택형 문항이 없으면 규칙을 만들지 않는다(스키마가 조건 1개 이상을 요구한다). */
export function canAddRule(def: FormDefinition, questionId: string): boolean {
  return defaultCondition(def, questionId) !== null;
}

export function addRule(def: FormDefinition, questionId: string): FormDefinition {
  const condition = defaultCondition(def, questionId);
  if (!condition) return def;
  const targets = targetCandidates(def, questionId);
  const rule: BranchRule = {
    when: { match: 'all', conditions: [condition] },
    go_to: targets[0].go_to,
  };
  return withRules(def, questionId, (rules) => [...rules, rule]);
}

export function removeRule(def: FormDefinition, questionId: string, index: number): FormDefinition {
  return withRules(def, questionId, (rules) => rules.filter((_, i) => i !== index));
}

/** 위에서부터 검사하므로 순서가 뜻을 가진다(SPEC 178행, AC44). */
export function moveRule(
  def: FormDefinition,
  questionId: string,
  index: number,
  delta: -1 | 1,
): FormDefinition {
  return withRules(def, questionId, (rules) => {
    const target = index + delta;
    if (index < 0 || target < 0 || target >= rules.length) return rules;
    const next = [...rules];
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  });
}

export function updateRule(
  def: FormDefinition,
  questionId: string,
  index: number,
  change: (rule: BranchRule) => BranchRule,
): FormDefinition {
  return withRules(def, questionId, (rules) => rules.map((r, i) => (i === index ? change(r) : r)));
}

export const setRuleMatch = (rule: BranchRule, match: 'all' | 'any'): BranchRule => ({
  ...rule,
  when: { ...rule.when, match },
});

export const setRuleTarget = (rule: BranchRule, goTo: BranchTarget): BranchRule => ({
  ...rule,
  go_to: goTo,
});

export function addCondition(def: FormDefinition, questionId: string, index: number) {
  const condition = defaultCondition(def, questionId);
  if (!condition) return def;
  return updateRule(def, questionId, index, (rule) => ({
    ...rule,
    when: { ...rule.when, conditions: [...rule.when.conditions, condition] },
  }));
}

/** 조건은 1개 이상이어야 한다(스키마). 마지막 하나는 지우지 않는다. */
export function removeCondition(rule: BranchRule, index: number): BranchRule {
  if (rule.when.conditions.length <= 1) return rule;
  return {
    ...rule,
    when: { ...rule.when, conditions: rule.when.conditions.filter((_, i) => i !== index) },
  };
}

export function setCondition(
  rule: BranchRule,
  index: number,
  condition: BranchCondition,
): BranchRule {
  return {
    ...rule,
    when: {
      ...rule.when,
      conditions: rule.when.conditions.map((c, i) => (i === index ? condition : c)),
    },
  };
}

/**
 * 선택지 문구를 고치면 같은 폼에서 그 선택지를 가리키는 조건도 따라간다(사람 결정).
 * 같은 문구의 선택지가 여럿이면 어느 쪽을 가리키는지 모르므로 조건은 그대로 둔다.
 */
export function renameOption(
  def: FormDefinition,
  questionId: string,
  index: number,
  text: string,
): FormDefinition {
  const owner = def.sections.flatMap((s) => s.questions).find((q) => q.question_id === questionId);
  if (!owner || index < 0 || index >= owner.options.length) return def;
  const before = owner.options[index];
  const follow = owner.options.filter((o) => o === before).length === 1;
  return {
    ...def,
    sections: def.sections.map((s) => ({
      ...s,
      questions: s.questions.map((q) => {
        const options =
          q.question_id === questionId
            ? q.options.map((o, i) => (i === index ? text : o))
            : q.options;
        const branch_rules = follow
          ? q.branch_rules.map((rule) => ({
              ...rule,
              when: {
                ...rule.when,
                conditions: rule.when.conditions.map((c) =>
                  c.question_id === questionId && c.option === before ? { ...c, option: text } : c,
                ),
              },
            }))
          : q.branch_rules;
        return { ...q, options, branch_rules };
      }),
    })),
  };
}

/** 조건 하나의 문제. 서버 `checkConditions`와 같은 기준이다. */
export function conditionProblem(
  def: FormDefinition,
  ownerId: string,
  condition: BranchCondition,
): string | null {
  const placed = placeQuestions(def);
  const owner = placed.find((p) => p.question.question_id === ownerId);
  const target = placed.find((p) => p.question.question_id === condition.question_id);
  if (!owner || !target) return '조건이 가리키는 문항이 없어요.';
  if (target.position > owner.position) return '조건의 문항이 이 문항보다 뒤에 있어요.';
  if (!isChoice(target.question)) return '조건의 문항이 선택형이 아니에요.';
  if (!target.question.options.includes(condition.option)) {
    return `조건의 선택지 "${condition.option}"가 그 문항에 없어요.`;
  }
  return null;
}

/** 이동할 곳의 문제. 서버 `checkTarget`과 `section_page` 검사(AC46)와 같은 기준이다. */
export function targetProblem(
  def: FormDefinition,
  ownerId: string,
  goTo: BranchTarget,
): string | null {
  if ('submit' in goTo) return null;
  const placed = placeQuestions(def);
  const owner = placed.find((p) => p.question.question_id === ownerId);
  if (!owner) return '이동할 곳이 없어요.';
  if ('section_id' in goTo) {
    const index = def.sections.findIndex((s) => s.section_id === goTo.section_id);
    if (index < 0) return '이동할 섹션이 없어요.';
    if (index <= owner.sectionIndex) return '이동할 섹션이 이 문항의 섹션과 같거나 앞에 있어요.';
    return null;
  }
  const target = placed.find((p) => p.question.question_id === goTo.question_id);
  if (!target) return '이동할 문항이 없어요.';
  if (target.position <= owner.position) return '이동할 문항이 이 문항과 같거나 앞에 있어요.';
  if (def.settings.display.default_mode === 'section_page') {
    return '섹션별 페이지에서는 문항으로 이동할 수 없어요. 섹션이나 제출을 골라 주세요.';
  }
  return null;
}

/** 규칙 하나의 문제 목록. 편집 화면과 배포 전 검사가 같이 쓴다. */
export function ruleProblems(def: FormDefinition, ownerId: string, rule: BranchRule): string[] {
  const problems: string[] = [];
  const owner = placeQuestions(def).find((p) => p.question.question_id === ownerId);
  if (owner && !canHoldRules(def, owner)) {
    problems.push('섹션별 페이지에서는 섹션의 마지막 문항에만 분기를 둘 수 있어요.');
  }
  for (const condition of rule.when.conditions) {
    const problem = conditionProblem(def, ownerId, condition);
    if (problem && !problems.includes(problem)) problems.push(problem);
  }
  const target = targetProblem(def, ownerId, rule.go_to);
  if (target) problems.push(target);
  return problems;
}

const DISPLAY_NAMES: Record<DisplayMode, string> = {
  one_question: '한 질문씩',
  all_questions: '전체 질문 보기',
  section_page: '섹션별 페이지',
};

/** 지금 시작 방식이 분기 유무에 맞지 않을 때의 안내. 맞으면 `null`이다. */
export function displayModeProblem(def: FormDefinition): string | null {
  const mode = def.settings.display.default_mode;
  const branched = hasBranchRules(def);
  if (allowedDisplayModes(branched).includes(mode)) return null;
  return branched
    ? `분기가 있는 폼은 "${DISPLAY_NAMES[mode]}"로 시작할 수 없어요. 시작 방식을 다시 골라 주세요.`
    : `분기가 없는 폼은 "${DISPLAY_NAMES[mode]}"로 시작할 수 없어요. 시작 방식을 다시 골라 주세요.`;
}

/** 배포 전 분기·시작 방식 검사(서버 `checkBranches`, `checkDisplayMode`). */
export function branchPublishProblems(def: FormDefinition): string[] {
  const problems: string[] = [];
  for (const placed of placeQuestions(def)) {
    placed.question.branch_rules.forEach((rule, index) => {
      for (const problem of ruleProblems(def, placed.question.question_id, rule)) {
        problems.push(`${questionLabel(placed)}의 ${index + 1}번째 분기 규칙: ${problem}`);
      }
    });
  }
  const mode = displayModeProblem(def);
  if (mode) problems.push(mode);
  return problems;
}
