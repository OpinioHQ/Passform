import type {
  CreatorFormDefinition,
  FormDefinition,
  FormSection,
  InputFormatKind,
  ProfileKey,
  PublicForm,
  Question,
  QuestionType,
} from '../types/api';

/**
 * 폼 정의 편집 함수. 화면 상태는 `form.schema.json` 구조 하나이고, 어느 모드든 이 함수들로 고친다.
 * 모두 새 값을 돌려주고 입력을 바꾸지 않는다. 화면에 없는 값(분기, 첨부, 추천 문항 출처)은 건드리지 않는다.
 */

const CHOICE_TYPES: readonly QuestionType[] = ['multiple_choice', 'dropdown', 'checkbox'];

export const isChoiceType = (type: QuestionType) => CHOICE_TYPES.includes(type);

/** 버전을 넘어 같은 문항을 가리키므로 한 번 준 ID는 바꾸지 않는다. */
const newId = (prefix: 's' | 'q') => `${prefix}_${crypto.randomUUID()}`;

export function emptyQuestion(): Question {
  return {
    question_id: newId('q'),
    template_id: null,
    question_note: '',
    question_type: 'short_answer',
    options: [],
    required: false,
    profile_key: null,
    attachments: [],
    branch_rules: [],
  };
}

export function emptySection(): FormSection {
  return { section_id: newId('s'), title: '', questions: [] };
}

/** `POST /forms`의 필수 키 7개를 모두 채운 빈 폼. */
export function newFormDefinition(consentTemplateId: string | null): FormDefinition {
  return {
    title: '',
    note: '',
    settings: { editable: false, cancellable: false, display: { default_mode: 'one_question' } },
    payment_link: null,
    consent_template_id: consentTemplateId,
    note_attachments: [],
    sections: [emptySection()],
  };
}

/** 정의 밖의 상태 키를 뺀다. 그대로 `PATCH`하면 `schema_invalid`다. */
export function toDefinition(body: CreatorFormDefinition): FormDefinition {
  const definition: FormDefinition & Partial<CreatorFormDefinition> = { ...body };
  delete definition.status;
  delete definition.share_url;
  delete definition.definition_version;
  return definition;
}

function moved<T>(items: T[], index: number, delta: -1 | 1): T[] {
  const target = index + delta;
  if (index < 0 || target < 0 || target >= items.length) return items;
  const next = [...items];
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

export function moveSection(def: FormDefinition, sectionId: string, delta: -1 | 1): FormDefinition {
  const index = def.sections.findIndex((s) => s.section_id === sectionId);
  return { ...def, sections: moved(def.sections, index, delta) };
}

export function updateSectionTitle(
  def: FormDefinition,
  sectionId: string,
  title: string,
): FormDefinition {
  return {
    ...def,
    sections: def.sections.map((s) => (s.section_id === sectionId ? { ...s, title } : s)),
  };
}

export function addSection(def: FormDefinition): FormDefinition {
  return { ...def, sections: [...def.sections, emptySection()] };
}

export function removeSection(def: FormDefinition, sectionId: string): FormDefinition {
  return { ...def, sections: def.sections.filter((s) => s.section_id !== sectionId) };
}

/** 복제본은 새 ID를 갖고 분기와 본인 정보를 다시 정한다(SPEC 154행). */
function cloneQuestion(question: Question): Question {
  return {
    ...structuredClone(question),
    question_id: newId('q'),
    branch_rules: [],
    profile_key: null,
  };
}

function insertedAfter<T>(items: T[], index: number, item: T): T[] {
  return [...items.slice(0, index + 1), item, ...items.slice(index + 1)];
}

export function duplicateSection(def: FormDefinition, sectionId: string): FormDefinition {
  const index = def.sections.findIndex((s) => s.section_id === sectionId);
  if (index < 0) return def;
  const source = def.sections[index];
  const copy: FormSection = {
    section_id: newId('s'),
    title: source.title,
    questions: source.questions.map(cloneQuestion),
  };
  return { ...def, sections: insertedAfter(def.sections, index, copy) };
}

function withQuestions(
  def: FormDefinition,
  sectionId: string,
  change: (questions: Question[]) => Question[],
): FormDefinition {
  return {
    ...def,
    sections: def.sections.map((s) =>
      s.section_id === sectionId ? { ...s, questions: change(s.questions) } : s,
    ),
  };
}

export function addQuestion(def: FormDefinition, sectionId: string): FormDefinition {
  return withQuestions(def, sectionId, (questions) => [...questions, emptyQuestion()]);
}

export function removeQuestion(
  def: FormDefinition,
  sectionId: string,
  questionId: string,
): FormDefinition {
  return withQuestions(def, sectionId, (questions) =>
    questions.filter((q) => q.question_id !== questionId),
  );
}

export function duplicateQuestion(
  def: FormDefinition,
  sectionId: string,
  questionId: string,
): FormDefinition {
  return withQuestions(def, sectionId, (questions) => {
    const index = questions.findIndex((q) => q.question_id === questionId);
    return index < 0 ? questions : insertedAfter(questions, index, cloneQuestion(questions[index]));
  });
}

export function moveQuestion(
  def: FormDefinition,
  sectionId: string,
  questionId: string,
  delta: -1 | 1,
): FormDefinition {
  return withQuestions(def, sectionId, (questions) =>
    moved(
      questions,
      questions.findIndex((q) => q.question_id === questionId),
      delta,
    ),
  );
}

export function replaceQuestion(
  def: FormDefinition,
  sectionId: string,
  next: Question,
): FormDefinition {
  return withQuestions(def, sectionId, (questions) =>
    questions.map((q) => (q.question_id === next.question_id ? next : q)),
  );
}

/** 문구가 바뀌면 본인 정보 선택을 지우고 다시 확인한다(AC72). */
export function setQuestionNote(question: Question, note: string): Question {
  if (note === question.question_note) return question;
  return { ...question, question_note: note, profile_key: null };
}

/** 형식이 바뀌면 본인 정보 선택을 지운다(AC72). 형식이 없으면 키를 뺀다(스키마가 null을 받지 않는다). */
export function setInputFormat(question: Question, kind: InputFormatKind | null): Question {
  if ((question.input_format?.kind ?? null) === kind) return question;
  const next: Question = { ...question, profile_key: null };
  if (kind) next.input_format = { kind };
  else delete next.input_format;
  return next;
}

/** 유형을 바꾸면 그 유형에 맞지 않는 값을 지운다. 스키마는 초안에도 적용된다. */
export function setQuestionType(question: Question, type: QuestionType): Question {
  if (type === question.question_type) return question;
  const next: Question = { ...question, question_type: type };
  if (type !== 'short_answer' && next.input_format) {
    delete next.input_format;
    next.profile_key = null;
  }
  if (isChoiceType(type)) {
    delete next.character_limit;
    if (next.options.length === 0) next.options = ['선택지 1'];
  } else {
    next.options = [];
  }
  return next;
}

/** 새 선택지는 기존 문구와 겹치지 않는 이름을 받는다. */
export function addOption(question: Question): Question {
  let n = question.options.length + 1;
  while (question.options.includes(`선택지 ${n}`)) n += 1;
  return { ...question, options: [...question.options, `선택지 ${n}`] };
}

export function setOption(question: Question, index: number, text: string): Question {
  return { ...question, options: question.options.map((o, i) => (i === index ? text : o)) };
}

export function removeOption(question: Question, index: number): Question {
  return { ...question, options: question.options.filter((_, i) => i !== index) };
}

export function setCharacterLimit(question: Question, limit: number | null): Question {
  const next: Question = { ...question };
  if (limit === null) delete next.character_limit;
  else next.character_limit = limit;
  return next;
}

const OWN_INFO: Record<InputFormatKind, { key: ProfileKey; ask: string }> = {
  name: { key: 'name', ask: '응답자 본인의 이름인가요?' },
  mobile_phone: { key: 'phone_number', ask: '응답자 본인의 휴대전화 번호인가요?' },
  landline_phone: { key: 'phone_number', ask: '응답자 본인의 집전화 번호인가요?' },
  address: { key: 'address', ask: '응답자 본인의 주소인가요?' },
  student_id: { key: 'student_id', ask: '응답자 본인의 학번인가요?' },
  email: { key: 'email', ask: '응답자 본인의 이메일인가요?' },
  date: { key: 'birth_date', ask: '응답자의 생년월일인가요?' },
};

export const ownInfoQuestion = (kind: InputFormatKind) => OWN_INFO[kind].ask;

export function setOwnInfo(question: Question, yes: boolean): Question {
  const kind = question.input_format?.kind;
  return { ...question, profile_key: yes && kind ? OWN_INFO[kind].key : null };
}

export const countBranchRules = (def: FormDefinition) =>
  def.sections.flatMap((s) => s.questions).reduce((sum, q) => sum + q.branch_rules.length, 0);

function describeQuestion(def: FormDefinition, question: Question): string {
  const all = def.sections.flatMap((s) => s.questions);
  const position = all.findIndex((q) => q.question_id === question.question_id) + 1;
  const note = question.question_note.trim();
  return note ? `${position}번째 문항(${note})` : `${position}번째 문항`;
}

/** 스키마가 초안에도 막는 값. 보내면 `schema_invalid`만 받으므로 저장 전에 어느 문항인지 알린다. */
export function saveProblems(def: FormDefinition): string[] {
  return def.sections
    .flatMap((s) => s.questions)
    .filter((q) => new Set(q.options).size !== q.options.length)
    .map((q) => `${describeQuestion(def, q)}에 같은 문구의 선택지가 있어요.`);
}

/** 서버가 `schema_invalid`만 주는 배포 조건(FormPublishValidator). 판정은 서버가 정본이다. */
export function publishProblems(def: FormDefinition): string[] {
  const questions = def.sections.flatMap((s) => s.questions);
  if (questions.length === 0) return ['문항을 하나 이상 추가해 주세요.'];
  const problems = saveProblems(def);
  for (const q of questions) {
    if (!q.question_note.trim())
      problems.push(`${describeQuestion(def, q)}의 문구를 입력해 주세요.`);
    if (isChoiceType(q.question_type) && q.options.length === 0) {
      problems.push(`${describeQuestion(def, q)}에 선택지를 추가해 주세요.`);
    }
  }
  return problems;
}

const CONSENT_LABELS: Record<InputFormatKind, string> = {
  name: '이름',
  mobile_phone: '휴대전화 번호',
  landline_phone: '집전화 번호',
  address: '주소',
  student_id: '학번',
  email: '이메일',
  date: '날짜',
};

/** 저장 전 정의를 응답 화면이 읽는 모양으로 만든다. 수집 항목은 서버의 ConsentItems와 같은 규칙이다. */
export function toPreviewForm(
  def: FormDefinition,
  consent: { purpose: string; retention: string } | null,
): PublicForm {
  const items = def.sections
    .flatMap((s) => s.questions)
    .map((q) => (q.input_format ? CONSENT_LABELS[q.input_format.kind] : '문항별 응답 내용'));
  return {
    form_id: '',
    definition_version: 0,
    title: def.title,
    note: def.note,
    note_attachments: def.note_attachments,
    deadline: def.deadline ?? null,
    sections: def.sections,
    display: { default_mode: def.settings.display.default_mode, allowed_modes: [] },
    payment_link: def.payment_link,
    consent: {
      items: [...new Set(items)],
      purpose: consent?.purpose ?? '',
      retention: consent?.retention ?? '',
      refusal_notice:
        '동의를 거부할 수 있습니다. 동의하지 않으면 이 폼에 응답을 제출할 수 없습니다.',
      ai_summary: null,
    },
  };
}
