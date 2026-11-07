import { describe, expect, it } from 'vitest';
import type { CreatorFormDefinition, FormDefinition, Question, QuestionType } from '../types/api';
import {
  addOption,
  addQuestion,
  duplicateQuestion,
  duplicateSection,
  emptyQuestion,
  moveQuestion,
  moveSection,
  newFormDefinition,
  publishProblems,
  setCharacterLimit,
  setInputFormat,
  setOwnInfo,
  setQuestionNote,
  setQuestionType,
  toDefinition,
} from './formDefinition';

type Schema = boolean | { [key: string]: unknown };

/** 스키마 파일은 읽기만 한다. 검증 라이브러리를 더하지 않으려고 이 스키마가 쓰는 키워드만 해석한다. */
const schemaFiles = import.meta.glob<{ default: Schema }>(
  '../../../src/passform/schemas/form.schema.json',
  { eager: true },
);
const formSchema = Object.values(schemaFiles)[0].default;

function resolveRef(ref: string): Schema {
  let node: unknown = formSchema;
  for (const part of ref.replace('#/', '').split('/')) {
    node = (node as Record<string, unknown>)[part];
  }
  return node as Schema;
}

function typeMatches(type: string, value: unknown): boolean {
  if (type === 'null') return value === null;
  if (type === 'array') return Array.isArray(value);
  if (type === 'integer') return Number.isInteger(value);
  if (type === 'object')
    return typeof value === 'object' && value !== null && !Array.isArray(value);
  return typeof value === type;
}

function schemaErrors(schema: Schema, value: unknown, path = '$'): string[] {
  if (schema === true) return [];
  if (schema === false) return [`${path}: 허용되지 않는 값`];
  const errors: string[] = [];
  const list = (key: string) => schema[key] as Schema[];
  if (schema.$ref) return schemaErrors(resolveRef(schema.$ref as string), value, path);
  if (schema.type) {
    const types = ([] as string[]).concat(schema.type as string | string[]);
    if (!types.some((t) => typeMatches(t, value))) errors.push(`${path}: 형식`);
  }
  if (schema.enum && !(schema.enum as unknown[]).includes(value)) errors.push(`${path}: enum`);
  if ('const' in schema && schema.const !== value) errors.push(`${path}: const`);
  if (typeof value === 'string') {
    if (schema.minLength && value.length < (schema.minLength as number)) errors.push(path);
    if (schema.pattern && !new RegExp(schema.pattern as string).test(value)) errors.push(path);
  }
  if (typeof value === 'number' && schema.minimum !== undefined) {
    if (value < (schema.minimum as number)) errors.push(`${path}: minimum`);
  }
  if (Array.isArray(value)) {
    if (schema.maxItems !== undefined && value.length > (schema.maxItems as number)) {
      errors.push(`${path}: maxItems`);
    }
    if (schema.minItems !== undefined && value.length < (schema.minItems as number)) {
      errors.push(`${path}: minItems`);
    }
    if (schema.uniqueItems && new Set(value.map((x) => JSON.stringify(x))).size !== value.length) {
      errors.push(`${path}: uniqueItems`);
    }
    if (schema.items) {
      value.forEach((item, i) =>
        errors.push(...schemaErrors(schema.items as Schema, item, `${path}[${i}]`)),
      );
    }
  }
  if (typeMatches('object', value)) {
    const object = value as Record<string, unknown>;
    const properties = (schema.properties ?? {}) as Record<string, Schema>;
    for (const key of (schema.required ?? []) as string[]) {
      if (!(key in object)) errors.push(`${path}.${key}: 필수`);
    }
    for (const [key, item] of Object.entries(object)) {
      if (key in properties) errors.push(...schemaErrors(properties[key], item, `${path}.${key}`));
      else if (schema.additionalProperties === false) errors.push(`${path}.${key}: 추가 키`);
    }
  }
  for (const sub of (schema.allOf ?? []) as Schema[])
    errors.push(...schemaErrors(sub, value, path));
  if (schema.if !== undefined && schemaErrors(schema.if as Schema, value, path).length === 0) {
    errors.push(...schemaErrors(schema.then as Schema, value, path));
  }
  if (schema.oneOf) {
    const matches = list('oneOf').filter((sub) => schemaErrors(sub, value, path).length === 0);
    if (matches.length !== 1) errors.push(`${path}: oneOf`);
  }
  return errors;
}

const savedQuestion = (overrides: Partial<Question> = {}): Question => ({
  question_id: 'q-saved',
  template_id: 'tpl-1',
  question_note: '저장된 문항',
  question_description: '보조 설명',
  question_type: 'short_answer',
  options: [],
  required: true,
  input_format: { kind: 'name', placeholder: '홍길동' },
  profile_key: 'name',
  character_limit: 20,
  attachments: [
    { attachment_id: 'a1', filename: 'a.pdf', content_type: 'application/pdf', size_bytes: 3 },
  ],
  branch_rules: [
    {
      when: { match: 'any', conditions: [{ question_id: 'q-choice', option: '예' }] },
      go_to: { submit: true },
    },
  ],
  ...overrides,
});

const savedForm = (): FormDefinition => ({
  title: '저장된 폼',
  note: '설명',
  deadline: '2026-11-10T06:30:00Z',
  settings: {
    editable: true,
    cancellable: true,
    response_limit: 5,
    display: { default_mode: 'section_page' },
    ai_summary: { enabled: true },
  },
  payment_link: 'https://pay.example.com/a',
  consent_template_id: 'consent_default',
  note_attachments: [
    { attachment_id: 'n1', filename: 'n.pdf', content_type: 'application/pdf', size_bytes: 1 },
  ],
  sections: [
    { section_id: 's1', title: '첫 섹션', questions: [savedQuestion()] },
    { section_id: 's2', title: '둘째 섹션', questions: [] },
  ],
});

describe('form.schema.json 통과', () => {
  it('새 폼은 스키마를 통과한다', () => {
    expect(schemaErrors(formSchema, newFormDefinition('consent_default'))).toEqual([]);
    expect(schemaErrors(formSchema, newFormDefinition(null))).toEqual([]);
  });

  it('다섯 문항 유형 폼은 스키마를 통과한다', () => {
    const types: QuestionType[] = [
      'multiple_choice',
      'dropdown',
      'checkbox',
      'short_answer',
      'long_answer',
    ];
    const questions = types.map((type) => {
      const question = setQuestionType(setQuestionNote(emptyQuestion(), type), type);
      return type === 'short_answer' ? setInputFormat(question, 'email') : question;
    });
    const form: FormDefinition = {
      ...newFormDefinition('consent_default'),
      deadline: '2026-11-10T15:30:00+09:00',
      sections: [{ section_id: 's1', title: '섹션', questions }],
    };
    expect(schemaErrors(formSchema, form)).toEqual([]);
  });

  it('검증기가 스키마 위반을 잡는다 (대조군)', () => {
    const withQuestion = (question: Question) => ({
      ...newFormDefinition(null),
      sections: [{ section_id: 's1', title: '', questions: [question] }],
    });
    const base = emptyQuestion();
    expect(schemaErrors(formSchema, { ...newFormDefinition(null), status: 'draft' })).not.toEqual(
      [],
    );
    expect(
      schemaErrors(
        formSchema,
        withQuestion({ ...base, question_type: 'long_answer', input_format: { kind: 'name' } }),
      ),
    ).not.toEqual([]);
    expect(
      schemaErrors(
        formSchema,
        withQuestion({ ...base, question_type: 'checkbox', options: ['a', 'a'] }),
      ),
    ).not.toEqual([]);
    expect(
      schemaErrors(
        formSchema,
        withQuestion({ ...base, question_type: 'checkbox', character_limit: 3 }),
      ),
    ).not.toEqual([]);
    expect(schemaErrors(formSchema, withQuestion({ ...base, options: ['a'] }))).not.toEqual([]);
  });
});

describe('복제 (AC79)', () => {
  it('문항 복제는 새 ID를 주고 분기와 본인 정보를 비우며 원본을 바꾸지 않는다', () => {
    const form = savedForm();
    const before = structuredClone(form);
    const next = duplicateQuestion(form, 's1', 'q-saved');
    const [original, copy] = next.sections[0].questions;
    expect(form).toEqual(before);
    expect(original).toEqual(before.sections[0].questions[0]);
    expect(copy.question_id).not.toBe('q-saved');
    expect(copy.branch_rules).toEqual([]);
    expect(copy.profile_key).toBeNull();
    expect(copy.question_note).toBe('저장된 문항');
    expect(copy.input_format).toEqual({ kind: 'name', placeholder: '홍길동' });
    expect(copy.character_limit).toBe(20);
    expect(copy.required).toBe(true);
  });

  it('섹션 복제는 섹션과 자식 문항 ID를 모두 새로 주고 원본을 바꾸지 않는다', () => {
    const form = addQuestion(savedForm(), 's1');
    const before = structuredClone(form);
    const next = duplicateSection(form, 's1');
    expect(form).toEqual(before);
    expect(next.sections.map((s) => s.title)).toEqual(['첫 섹션', '첫 섹션', '둘째 섹션']);
    expect(next.sections[0]).toEqual(before.sections[0]);
    const copy = next.sections[1];
    expect(copy.section_id).not.toBe('s1');
    const ids = next.sections.flatMap((s) => s.questions.map((q) => q.question_id));
    expect(new Set(ids).size).toBe(4);
    expect(copy.questions.every((q) => q.branch_rules.length === 0)).toBe(true);
    expect(copy.questions.every((q) => q.profile_key === null)).toBe(true);
  });
});

describe('순서 이동', () => {
  it('섹션을 위아래로 옮기고 끝에서는 그대로 둔다', () => {
    const form = savedForm();
    const ids = (d: FormDefinition) => d.sections.map((s) => s.section_id);
    expect(ids(moveSection(form, 's2', -1))).toEqual(['s2', 's1']);
    expect(ids(moveSection(form, 's1', 1))).toEqual(['s2', 's1']);
    expect(ids(moveSection(form, 's1', -1))).toEqual(['s1', 's2']);
    expect(ids(moveSection(form, 's2', 1))).toEqual(['s1', 's2']);
    expect(ids(form)).toEqual(['s1', 's2']);
  });

  it('문항을 섹션 안에서 옮긴다', () => {
    const form = addQuestion(savedForm(), 's1');
    const added = form.sections[0].questions[1].question_id;
    const next = moveQuestion(form, 's1', added, -1);
    expect(next.sections[0].questions.map((q) => q.question_id)).toEqual([added, 'q-saved']);
    expect(moveQuestion(form, 's1', added, 1).sections[0].questions[1].question_id).toBe(added);
  });
});

describe('유형 변경은 스키마에 맞게 정리한다', () => {
  it('단답에서 장문으로 바꾸면 입력 형식과 본인 정보를 지우고 글자 수는 둔다', () => {
    const next = setQuestionType(savedQuestion(), 'long_answer');
    expect('input_format' in next).toBe(false);
    expect(next.profile_key).toBeNull();
    expect(next.character_limit).toBe(20);
    expect(next.options).toEqual([]);
  });

  it('선택형으로 바꾸면 입력 형식과 글자 수 키를 지우고 선택지를 하나 둔다', () => {
    const next = setQuestionType(savedQuestion(), 'checkbox');
    expect('input_format' in next).toBe(false);
    expect('character_limit' in next).toBe(false);
    expect(next.options).toEqual(['선택지 1']);
  });

  it('선택형에서 단답으로 바꾸면 선택지를 비운다', () => {
    const choice = setQuestionType(emptyQuestion(), 'dropdown');
    expect(setQuestionType(choice, 'short_answer').options).toEqual([]);
  });

  it('선택지를 더할 때 중복 문구를 만들지 않는다', () => {
    const question = {
      ...emptyQuestion(),
      question_type: 'checkbox' as const,
      options: ['선택지 2'],
    };
    const next = addOption(addOption(question));
    expect(new Set(next.options).size).toBe(next.options.length);
  });

  it('글자 수를 지우면 키가 사라진다', () => {
    expect('character_limit' in setCharacterLimit(savedQuestion(), null)).toBe(false);
  });
});

describe('본인 정보 초기화 (AC72)', () => {
  it('기본은 아니요이고 예로 확인하면 해당 키를 둔다', () => {
    const question = setInputFormat(emptyQuestion(), 'date');
    expect(question.profile_key).toBeNull();
    expect(setOwnInfo(question, true).profile_key).toBe('birth_date');
    expect(setOwnInfo(setOwnInfo(question, true), false).profile_key).toBeNull();
  });

  it('문구가 바뀌면 지운다', () => {
    expect(setQuestionNote(savedQuestion(), '바뀐 문구').profile_key).toBeNull();
  });

  it('입력 형식이 바뀌거나 없어지면 지운다', () => {
    expect(setInputFormat(savedQuestion(), 'address').profile_key).toBeNull();
    const removed = setInputFormat(savedQuestion(), null);
    expect(removed.profile_key).toBeNull();
    expect('input_format' in removed).toBe(false);
  });

  it('같은 값이면 지우지 않는다', () => {
    expect(setQuestionNote(savedQuestion(), '저장된 문항').profile_key).toBe('name');
    expect(setInputFormat(savedQuestion(), 'name').input_format?.placeholder).toBe('홍길동');
  });
});

describe('저장할 값', () => {
  it('상태 키를 빼고 나머지는 그대로 둔다', () => {
    const body: CreatorFormDefinition = {
      ...savedForm(),
      status: 'open',
      share_url: 'https://x/f/abc',
      definition_version: 2,
    };
    const definition = toDefinition(body);
    for (const key of ['status', 'share_url', 'definition_version']) {
      expect(key in definition).toBe(false);
    }
    expect(definition).toEqual(savedForm());
    expect('status' in body).toBe(true);
  });

  it('저장된 분기, 첨부, 출처, 설정은 다른 곳을 고쳐도 그대로다 (AC42)', () => {
    const form = savedForm();
    let next = addQuestion(form, 's2');
    next = { ...next, title: '바꾼 제목' };
    next = moveSection(next, 's2', -1);
    next = moveSection(next, 's2', 1);
    expect(next.note_attachments).toEqual(form.note_attachments);
    expect(next.settings).toEqual(form.settings);
    expect(next.deadline).toBe(form.deadline);
    expect(next.sections[0]).toEqual(form.sections[0]);
  });

  it('수정한 문항도 고치지 않은 값은 그대로다 (AC42)', () => {
    const question = savedQuestion();
    const next = setQuestionNote(question, '문구만 바꿈');
    expect(next.branch_rules).toEqual(question.branch_rules);
    expect(next.attachments).toEqual(question.attachments);
    expect(next.template_id).toBe('tpl-1');
    expect(next.question_description).toBe('보조 설명');
  });
});

describe('배포 전 검사', () => {
  it('선택형 문항에 빈 선택지가 하나라도 있으면 배포 문제로 알린다', () => {
    const withOptions = (options: string[]): FormDefinition => ({
      ...newFormDefinition('consent_default'),
      sections: [
        {
          section_id: 's1',
          title: '',
          questions: [
            { ...emptyQuestion(), question_note: '색', question_type: 'dropdown', options },
          ],
        },
      ],
    });
    expect(publishProblems(withOptions(['']))).toEqual([
      '1번째 문항(색)에 빈 선택지가 있어요. 선택지 문구를 입력해 주세요.',
    ]);
    expect(publishProblems(withOptions(['A', ' ']))).toEqual([
      '1번째 문항(색)에 빈 선택지가 있어요. 선택지 문구를 입력해 주세요.',
    ]);
    expect(publishProblems(withOptions(['A', 'B']))).toEqual([]);
  });
});
