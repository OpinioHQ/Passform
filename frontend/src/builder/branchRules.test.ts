/**
 * 분기 편집 함수와 배포 전 분기·시작 방식 검사(AC8, AC46).
 * AC8과 section_page 검사의 입력은 서버 `SampleForms.branched()`와 같다. `unbranched()`는 서버 것이 아니라
 * `branched()`에서 규칙을 뺀 것이고, 서버의 "분기 없는 폼" 검사와는 규칙 유무·시작 방식만 같다. 대응표는
 * `docs/prompts/delegation_branch_editor.md`에 있다. 판정의 정본은 서버다.
 */
import { describe, expect, it } from 'vitest';
import type { BranchRule, BranchTarget, DisplayMode, FormDefinition, Question } from '../types/api';
import {
  addRule,
  branchPublishProblems,
  moveRule,
  removeRule,
  renameOption,
  setRuleTarget,
  updateRule,
} from './branchRules';
import { publishProblems, removeQuestion } from './formDefinition';

const q = (
  id: string,
  note: string,
  type: Question['question_type'],
  options: string[] = [],
  rules: BranchRule[] = [],
): Question => ({
  question_id: id,
  template_id: null,
  question_note: note,
  question_type: type,
  options,
  required: false,
  profile_key: null,
  attachments: [],
  branch_rules: rules,
});

const rule = (questionId: string, option: string, goTo: BranchTarget): BranchRule => ({
  when: { match: 'all', conditions: [{ question_id: questionId, option }] },
  go_to: goTo,
});

/** 서버 `SampleForms.branched()`와 같은 정의. */
function branched(): FormDefinition {
  return {
    title: '가상 분기',
    note: '',
    deadline: null,
    settings: {
      editable: false,
      cancellable: false,
      response_limit: null,
      display: { default_mode: 'section_page' },
    },
    payment_link: null,
    consent_template_id: 'consent_default',
    note_attachments: [],
    sections: [
      {
        section_id: 's1',
        title: '첫째',
        questions: [
          q('q1', '학년', 'dropdown', ['1학년', '2학년']),
          q(
            'q2',
            '참여하나요',
            'multiple_choice',
            ['예', '아니요'],
            [rule('q2', '아니요', { section_id: 's3' })],
          ),
        ],
      },
      { section_id: 's2', title: '둘째', questions: [q('q3', '역할', 'short_answer')] },
      { section_id: 's3', title: '셋째', questions: [q('q4', '소감', 'long_answer')] },
    ],
  };
}

/** `branched()`에서 규칙을 빼고 one_question으로 바꾼 것. 서버 `SampleForms.unbranched()`와는 규칙 유무·시작 방식만 같다. */
function unbranched(): FormDefinition {
  const def = branched();
  def.settings.display.default_mode = 'one_question';
  def.sections[0].questions[1].branch_rules = [];
  return def;
}

const withMode = (def: FormDefinition, mode: DisplayMode): FormDefinition => ({
  ...def,
  settings: { ...def.settings, display: { default_mode: mode } },
});

/** 서버 테스트의 `setRules(definition, section, question, rule)`. */
function setRules(def: FormDefinition, s: number, i: number, rules: BranchRule[]): FormDefinition {
  const next = structuredClone(def);
  next.sections[s].questions[i].branch_rules = rules;
  return next;
}

const rulesOf = (def: FormDefinition, s: number, i: number) =>
  def.sections[s].questions[i].branch_rules;

describe('분기 편집 함수', () => {
  it('규칙을 추가·삭제하고 위·아래로 옮긴다. 새 규칙은 자기 문항의 첫 선택지와 뒤쪽 첫 섹션을 가리킨다', () => {
    let def = unbranched();
    def = addRule(def, 'q2');
    expect(rulesOf(def, 0, 1)).toEqual([rule('q2', '예', { section_id: 's2' })]);
    def = addRule(def, 'q2');
    def = updateRule(def, 'q2', 1, (r) => setRuleTarget(r, { submit: true }));
    def = moveRule(def, 'q2', 1, -1);
    expect(rulesOf(def, 0, 1).map((r) => r.go_to)).toEqual([
      { submit: true },
      { section_id: 's2' },
    ]);
    // 맨 위에서 더 올리면 그대로다.
    expect(moveRule(def, 'q2', 0, -1)).toEqual(def);
    def = removeRule(def, 'q2', 0);
    expect(rulesOf(def, 0, 1)).toEqual([rule('q2', '예', { section_id: 's2' })]);
  });

  it('조건으로 쓸 선택형 문항이 없으면 규칙을 만들지 않는다', () => {
    const def = unbranched();
    def.sections[0].questions = [q('q1', '이름', 'short_answer')];
    expect(addRule(def, 'q1')).toBe(def);
  });

  it('선택지 문구를 고치면 그 선택지를 가리키는 조건이 새 문구를 따라간다', () => {
    let def = setRules(withMode(branched(), 'one_question'), 0, 1, [
      rule('q1', '1학년', { section_id: 's3' }),
      rule('q2', '아니요', { submit: true }),
    ]);
    def = renameOption(def, 'q1', 0, '신입생');
    expect(def.sections[0].questions[0].options).toEqual(['신입생', '2학년']);
    expect(rulesOf(def, 0, 1)).toEqual([
      rule('q1', '신입생', { section_id: 's3' }),
      rule('q2', '아니요', { submit: true }),
    ]);
  });

  it('문항을 지워도 규칙은 지우지 않고 배포 전 문제로 잡는다', () => {
    let def = setRules(withMode(branched(), 'one_question'), 0, 1, [
      rule('q1', '1학년', { section_id: 's3' }),
    ]);
    def = removeQuestion(def, 's1', 'q1');
    expect(rulesOf(def, 0, 0)).toEqual([rule('q1', '1학년', { section_id: 's3' })]);
    expect(publishProblems(def)).toEqual([
      '섹션 1의 1번 문항: 참여하나요의 1번째 분기 규칙: 조건이 가리키는 문항이 없어요.',
    ]);
  });

  it('저장 형식이 form.schema.json의 branch_rule과 같다', () => {
    let def = unbranched();
    def = addRule(def, 'q2');
    def = updateRule(def, 'q2', 0, (r) => ({ ...r, when: { ...r.when, match: 'any' } }));
    def = addRule(def, 'q2');
    def = updateRule(def, 'q2', 1, (r) => setRuleTarget(r, { submit: true }));
    def = addRule(def, 'q2');
    def = updateRule(def, 'q2', 2, (r) => setRuleTarget(r, { question_id: 'q4' }));
    const rules = rulesOf(def, 0, 1);
    expect(rules).toHaveLength(3);
    for (const r of rules) expect(branchRuleErrors(r)).toEqual([]);
    expect(rulesOf(unbranched(), 0, 0)).toEqual([]);
  });
});

describe('배포 전 분기·시작 방식 검사 (서버 FormPublishValidatorTest와 같은 입력)', () => {
  it('분기 없는 폼과 section_page 분기 폼은 통과한다', () => {
    expect(publishProblems(unbranched())).toEqual([]);
    expect(publishProblems(branched())).toEqual([]);
  });

  it.each([
    // go_to가 없는 대상, 같은 위치, 앞쪽(AC8)
    ['q2', '예', { section_id: 'none' }, '이동할 섹션이 없어요.'],
    ['q2', '예', { question_id: 'none' }, '이동할 문항이 없어요.'],
    ['q2', '예', { section_id: 's1' }, '이동할 섹션이 이 문항의 섹션과 같거나 앞에 있어요.'],
    ['q2', '예', { question_id: 'q2' }, '이동할 문항이 이 문항과 같거나 앞에 있어요.'],
    ['q2', '예', { question_id: 'q1' }, '이동할 문항이 이 문항과 같거나 앞에 있어요.'],
    // 조건이 없는 문항·선택지, 뒤 문항, 선택형이 아닌 문항(AC8)
    ['none', '예', { submit: true }, '조건이 가리키는 문항이 없어요.'],
    ['q2', '없는 선택지', { submit: true }, '조건의 선택지 "없는 선택지"가 그 문항에 없어요.'],
    ['q4', '소감', { submit: true }, '조건의 문항이 이 문항보다 뒤에 있어요.'],
    ['q3', '역할', { submit: true }, '조건의 문항이 이 문항보다 뒤에 있어요.'],
  ] as const)('AC8 잘못된 분기: %s %s %o', (questionId, option, goTo, problem) => {
    const def = setRules(withMode(branched(), 'one_question'), 0, 1, [
      rule(questionId, option, goTo),
    ]);
    expect(branchPublishProblems(def)).toEqual([
      `섹션 1의 2번 문항: 참여하나요의 1번째 분기 규칙: ${problem}`,
    ]);
  });

  it('선택형이 아닌 앞 문항을 조건으로 쓰면 막는다', () => {
    const def = setRules(withMode(branched(), 'one_question'), 1, 0, [
      rule('q3', '역할', { submit: true }),
    ]);
    expect(branchPublishProblems(def)).toEqual([
      '섹션 2의 1번 문항: 역할의 1번째 분기 규칙: 조건의 문항이 선택형이 아니에요.',
    ]);
  });

  it('AC46 one_question은 뒤쪽 문항과 제출로 분기할 수 있고 앞 문항을 조건으로 쓸 수 있다', () => {
    let def = withMode(branched(), 'one_question');
    def = setRules(def, 0, 0, [rule('q1', '1학년', { question_id: 'q3' })]);
    def = setRules(def, 0, 1, [rule('q1', '2학년', { submit: true })]);
    expect(publishProblems(def)).toEqual([]);
  });

  it('AC46 분기가 없는 폼은 section_page를 쓸 수 없다', () => {
    expect(branchPublishProblems(withMode(unbranched(), 'section_page'))).toEqual([
      '분기가 없는 폼은 "섹션별 페이지"로 시작할 수 없어요. 시작 방식을 다시 골라 주세요.',
    ]);
  });

  it('AC46 분기가 있는 폼은 all_questions를 쓸 수 없다', () => {
    expect(branchPublishProblems(withMode(branched(), 'all_questions'))).toEqual([
      '분기가 있는 폼은 "전체 질문 보기"로 시작할 수 없어요. 시작 방식을 다시 골라 주세요.',
    ]);
  });

  it('AC46 section_page의 규칙은 섹션 마지막 문항에만 둔다', () => {
    const def = setRules(branched(), 0, 0, [rule('q1', '1학년', { section_id: 's3' })]);
    expect(branchPublishProblems(def)).toEqual([
      '섹션 1의 1번 문항: 학년의 1번째 분기 규칙: 섹션별 페이지에서는 섹션의 마지막 문항에만 분기를 둘 수 있어요.',
    ]);
  });

  it('AC46 section_page는 문항으로 분기할 수 없다', () => {
    const def = setRules(branched(), 0, 1, [rule('q2', '아니요', { question_id: 'q4' })]);
    expect(branchPublishProblems(def)).toEqual([
      '섹션 1의 2번 문항: 참여하나요의 1번째 분기 규칙: 섹션별 페이지에서는 문항으로 이동할 수 없어요. 섹션이나 제출을 골라 주세요.',
    ]);
  });
});

/** 스키마 파일의 `branch_rule` 정의로 규칙 하나를 본다. 이 정의가 쓰는 키워드만 해석한다. */
const schemaFiles = import.meta.glob<{ default: Record<string, unknown> }>(
  '../../../src/passform/schemas/form.schema.json',
  { eager: true },
);
type Node = Record<string, unknown>;
const definitions = Object.values(schemaFiles)[0].default.definitions as Record<string, Node>;

function objectErrors(schema: Node, value: unknown, path: string): string[] {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return [`${path}: 형식`];
  const object = value as Record<string, unknown>;
  const properties = (schema.properties ?? {}) as Record<string, Node>;
  const errors = ((schema.required ?? []) as string[])
    .filter((key) => !(key in object))
    .map((key) => `${path}.${key}: 필수`);
  for (const [key, item] of Object.entries(object)) {
    if (!(key in properties)) errors.push(`${path}.${key}: 추가 키`);
    else errors.push(...valueErrors(properties[key], item, `${path}.${key}`));
  }
  return errors;
}

function valueErrors(schema: Node, value: unknown, path: string): string[] {
  if (schema.oneOf) {
    const matches = (schema.oneOf as Node[]).filter(
      (s) => valueErrors(s, value, path).length === 0,
    );
    return matches.length === 1 ? [] : [`${path}: oneOf`];
  }
  if ('const' in schema) return schema.const === value ? [] : [`${path}: const`];
  if (schema.enum) return (schema.enum as unknown[]).includes(value) ? [] : [`${path}: enum`];
  if (schema.type === 'object') return objectErrors(schema, value, path);
  if (schema.type === 'array') {
    if (!Array.isArray(value)) return [`${path}: 형식`];
    const errors = value.length < ((schema.minItems as number) ?? 0) ? [`${path}: minItems`] : [];
    value.forEach((item, i) =>
      errors.push(...valueErrors(schema.items as Node, item, `${path}[${i}]`)),
    );
    return errors;
  }
  if (schema.type === 'string') {
    if (typeof value !== 'string') return [`${path}: 형식`];
    return value.length < ((schema.minLength as number) ?? 0) ? [`${path}: minLength`] : [];
  }
  return [];
}

const branchRuleErrors = (value: unknown) => valueErrors(definitions.branch_rule, value, '$');
