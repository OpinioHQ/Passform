import { versionLabel } from '../pages/ResultsPage';
import type {
  AnalyzeClarifyResponse,
  AnalyzeOperation,
  ClarifyQuestionOption,
  Coverage,
  MatchedCondition,
  RequestContext,
} from '../types/analyze';
import type { CreatorFormDefinition, FormResults, Question } from '../types/api';

/**
 * 탐색 화면(E8 #46)이 서버 응답을 화면 문구로 바꾸는 순수 함수.
 * 숫자는 서버가 준 값만 쓴다. 건수·순서를 다시 세지 않는다(AGENTS 3절 3항).
 * 화면에는 문항 문구, "폼 버전 1"만 쓰고 question_id·reason 원문은 쓰지 않는다(analyze_api.md 5절).
 */

export const operationLabels: Record<AnalyzeOperation, string> = {
  count: '응답 수',
  list: '목록',
  summarize: '요약',
};

const kindLabels = { answered: '답함', unanswered: '비움' } as const;

/** 연결 결과. 같은 조건·문항·연결 값은 버전만 모아 한 줄로 보인다. */
export function matchedLines(matched: MatchedCondition[]): string[] {
  const groups = new Map<string, { head: string; versions: number[] }>();
  for (const m of matched) {
    const value = m.kind === 'option' ? m.option : kindLabels[m.kind];
    const head = `${m.condition} → ${m.question_note}: ${value}`;
    const group = groups.get(head) ?? { head, versions: [] };
    if (!group.versions.includes(m.form_version)) group.versions.push(m.form_version);
    groups.set(head, group);
  }
  return [...groups.values()].map(
    (g) => `${g.head}, ${versionLabel([...g.versions].sort((a, b) => a - b))}`,
  );
}

export interface CoverageNotice {
  /** 항상 보이는 사실. */
  facts: string[];
  /** 물음표를 눌러야 보이는 이유. */
  reasons: string[];
}

/** 일부만 확인한 사실과 이유. 이유 구분은 matched로 한다(analyze_api.md 5절). */
export function coverageNotice(coverage: Coverage, matched: MatchedCondition[]): CoverageNotice {
  const facts: string[] = [];
  const reasons: string[] = [];
  if (coverage.not_evaluable_count > 0) {
    facts.push(
      `전체 ${coverage.total_submitted_count}건 중 ${coverage.not_evaluable_count}건은 확인할 수 없어 빼고 찾았어요.`,
    );
    for (const v of coverage.not_evaluable_by_version) {
      const why = v.unavailable_conditions.map((condition) => {
        const linked = matched.some(
          (m) => m.condition === condition && m.form_version === v.form_version,
        );
        return linked
          ? `'${condition}' 문항을 분기로 건너뛰었어요`
          : `'${condition}'에 맞는 문항이 그 버전에 없어요`;
      });
      reasons.push(
        `${versionLabel([v.form_version])} 응답 ${v.response_count}건: ${why.join(', ')}.`,
      );
    }
  }
  const sortGaps = coverage.sort_unavailable_by_version ?? [];
  if (sortGaps.length > 0) {
    facts.push('정렬할 문항이 없는 응답은 목록 맨 뒤에 두었어요.');
    reasons.push(`${versionLabel(sortGaps)}에는 정렬할 문항이 없어요.`);
  }
  const summaryGaps = (coverage.summary_unavailable_by_version ?? []).filter(
    (v) => v.response_count > 0,
  );
  if (summaryGaps.length > 0) {
    facts.push('요약할 문항이 없는 버전의 응답은 요약에서 빠졌어요.');
    for (const v of summaryGaps) {
      reasons.push(
        `${versionLabel([v.form_version])} 응답 ${v.response_count}건에는 요약할 문항이 없어요.`,
      );
    }
  }
  return { facts, reasons };
}

export interface Choice {
  label: string;
  context: RequestContext;
}

export interface QuestionHint {
  note: string;
  versions: number[];
  options: string[];
}

/** 되묻기 화면: 고를 버튼, 참고로 보일 문항 목록, 결과 화면 안내 여부. */
export interface ClarifyView {
  choices: Choice[];
  questions: QuestionHint[];
  toResults: boolean;
}

/** 버전마다 나뉜 후보 문항을 문구 하나로 모은다. */
export function questionHints(options: ClarifyQuestionOption[]): QuestionHint[] {
  const byNote = new Map<string, QuestionHint>();
  for (const o of options) {
    const hint = byNote.get(o.question_note) ?? {
      note: o.question_note,
      versions: [],
      options: [],
    };
    if (!hint.versions.includes(o.form_version)) hint.versions.push(o.form_version);
    for (const option of o.options ?? [])
      if (!hint.options.includes(option)) hint.options.push(option);
    byNote.set(o.question_note, hint);
  }
  return [...byNote.values()].map((h) => ({
    ...h,
    versions: [...h.versions].sort((a, b) => a - b),
  }));
}

const hintLabel = (h: QuestionHint) => `${h.note} (${versionLabel(h.versions)})`;

function withoutMatch(context: RequestContext): RequestContext {
  const next = { ...context };
  delete next.match;
  return next;
}

/** "문항 문구 - 값"은 서버가 마지막 '-'에서 나눈다. 값에 '-'가 있으면 만들 수 없다. */
function noteCondition(note: string, value: string): string | null {
  return value.includes('-') ? null : `${note} - ${value}`;
}

/**
 * analyze_api.md 4절 표. 응답의 context를 고쳐 {context}로 다시 보낸다.
 * 서버는 어느 표현이 문제인지 따로 주지 않으므로 대상이 하나로 정해질 때만 버튼을 만들고,
 * 아니면 문항 목록을 보이고 다시 입력하게 한다.
 */
export function clarifyView(response: AnalyzeClarifyResponse): ClarifyView {
  const { clarify, context } = response;
  const view: ClarifyView = { choices: [], questions: [], toResults: false };
  switch (clarify.reason) {
    case 'unsupported_scope':
      return { ...view, toResults: true };
    case 'parse_failed':
      return view;
    case 'missing_operation':
    case 'summary_disabled':
      if (!context) return view;
      return {
        ...view,
        choices: clarify.options.map((op: AnalyzeOperation) => ({
          label: operationLabels[op],
          context: { ...context, operation: op },
        })),
      };
    case 'missing_match':
      if (!context) return view;
      return {
        ...view,
        choices: clarify.options.map((m) => ({
          label: m === 'all' ? '모두 만족하는 응답' : '하나라도 만족하는 응답',
          context: { ...context, match: m },
        })),
      };
    case 'double_negation':
      if (!context) return view;
      return { ...view, choices: [{ label: clarify.options[0], context: withoutMatch(context) }] };
    case 'unsortable_field':
      if (!context) return view;
      return {
        ...view,
        choices: questionHints(clarify.options).map((h) => ({
          label: hintLabel(h),
          context: { ...context, sort: { by: h.note, order: context.sort?.order ?? 'asc' } },
        })),
      };
    case 'unsummarizable_field':
      if (!context) return view;
      return {
        ...view,
        choices: questionHints(clarify.options).map((h) => ({
          label: hintLabel(h),
          context: { ...context, target: h.note },
        })),
      };
    case 'unknown_condition': {
      const hints = questionHints(clarify.options);
      const noConditions = context !== null && context.conditions.length === 0;
      // 서버는 조건 → target → sort.by 순으로 본다. 조건이 없을 때만 target·sort.by가 문제임이 확실하다.
      if (context && noConditions && context.operation === 'summarize' && context.target) {
        return {
          ...view,
          choices: hints.map((h) => ({
            label: hintLabel(h),
            context: { ...context, target: h.note },
          })),
        };
      }
      if (context && noConditions && context.operation === 'list' && context.sort) {
        const order = context.sort.order;
        return {
          ...view,
          choices: hints.map((h) => ({
            label: hintLabel(h),
            context: { ...context, sort: { by: h.note, order } },
          })),
        };
      }
      return { ...view, questions: hints };
    }
    case 'ambiguous_match': {
      if (!context) return view;
      if (clarify.options.some((o) => o === 'any')) {
        return {
          ...view,
          choices: [
            { label: '하나라도 해당하는 응답 보기', context: { ...context, match: 'any' } },
          ],
        };
      }
      const hints = questionHints(
        clarify.options.filter((o): o is ClarifyQuestionOption => typeof o !== 'string'),
      );
      const summarizeTarget = context.operation === 'summarize' && Boolean(context.target);
      const listSort = context.operation === 'list' && Boolean(context.sort);
      if (context.conditions.length === 1 && !summarizeTarget && !listSort) {
        const condition = context.conditions[0];
        const choices = hints.flatMap((h) => {
          const rewritten = noteCondition(h.note, condition);
          return rewritten
            ? [{ label: hintLabel(h), context: { ...context, conditions: [rewritten] } }]
            : [];
        });
        if (choices.length === hints.length) return { ...view, choices };
      }
      if (context.conditions.length === 0 && summarizeTarget) {
        return {
          ...view,
          choices: hints.map((h) => ({
            label: hintLabel(h),
            context: { ...context, target: h.note },
          })),
        };
      }
      if (context.conditions.length === 0 && listSort && context.sort) {
        const order = context.sort.order;
        return {
          ...view,
          choices: hints.map((h) => ({
            label: hintLabel(h),
            context: { ...context, sort: { by: h.note, order } },
          })),
        };
      }
      return { ...view, questions: hints };
    }
  }
}

const personalKinds = new Set([
  'name',
  'mobile_phone',
  'landline_phone',
  'address',
  'student_id',
  'email',
]);

/** 개인정보 문항(AGENTS 3절 1항): profile_key가 있거나 input_format이 개인정보 범주. */
function isPersonal(q: Question): boolean {
  return (
    q.profile_key !== null || (q.input_format ? personalKinds.has(q.input_format.kind) : false)
  );
}

export interface PresetGroup {
  title: string;
  /** main은 처음부터 보이고, option(선택지별 응답 수)은 "더 보기"로 펼친다. */
  kind: 'main' | 'option';
  choices: Choice[];
}

/**
 * 묻기 밑 버튼. 그 폼에서 되는 요청만 만든다(analyze_api.md 5절).
 * 요약 버튼은 폼 정의, 선택지 버튼은 제출 응답이 있는 버전(결과 API)으로 만든다.
 * 배포 전이면 응답이 없으므로 만들지 않는다. 문구가 겹치는 문항은 서버가 되묻게 되므로 뺀다.
 */
/** 현재 정의의 문항과 문구별 개수. 문구가 공백이거나 겹치는 문항은 unique에서 뺀다. */
function definitionQuestions(definition: CreatorFormDefinition) {
  const questions = definition.sections.flatMap((s) => s.questions);
  const noteCount = new Map<string, number>();
  for (const q of questions)
    noteCount.set(q.question_note, (noteCount.get(q.question_note) ?? 0) + 1);
  const unique = questions.filter(
    (q) => q.question_note.trim() && noteCount.get(q.question_note) === 1,
  );
  return { questions, noteCount, unique };
}

/** 요약할 수 있는 문항: 단답·장문이고 개인정보 문항이 아니다. */
function summarizableQuestions(unique: Question[]): Question[] {
  return unique.filter(
    (q) =>
      (q.question_type === 'short_answer' || q.question_type === 'long_answer') && !isPersonal(q),
  );
}

/**
 * 결과 API는 선택형 문항만 주므로, 서술형 문항이 제출 버전에 있는지는 "현재 정의 = 제출이 있는 버전"일 때만 안다.
 * 최신 버전에 제출이 없으면 문항 요약을 만들지 않는다(사람 결정, 코덱스 검토 반영).
 */
function latestVersionSubmitted(definition: CreatorFormDefinition, results: FormResults): boolean {
  return results.versions.some(
    (v) => v.form_version === definition.definition_version && v.response_count > 0,
  );
}

export function presetGroups(
  definition: CreatorFormDefinition,
  results: FormResults,
): PresetGroup[] {
  if (definition.definition_version === null) return [];
  const { noteCount, unique } = definitionQuestions(definition);

  const groups: PresetGroup[] = [
    {
      // 버튼 글자에 이미 "전체"가 있어 묶음 제목을 두지 않는다. 도우미 세로 길이를 줄인다(사람 요청).
      title: '',
      kind: 'main',
      choices: [{ label: '전체 응답 목록', context: { conditions: [], operation: 'list' } }],
    },
  ];
  const summaryOn = definition.settings.ai_summary?.enabled === true;
  const summarizable = summarizableQuestions(unique);
  if (summaryOn && summarizable.length > 0) {
    groups[0].choices.push({
      label: '전체 요약',
      context: { conditions: [], operation: 'summarize' },
    });
    if (latestVersionSubmitted(definition, results)) {
      groups.push({
        title: '문항 요약',
        kind: 'main',
        choices: summarizable.map((q) => ({
          label: `${q.question_note} 요약`,
          context: { conditions: [], operation: 'summarize', target: q.question_note },
        })),
      });
    }
  }
  for (const q of submittedChoiceQuestions(results, noteCount)) {
    const choices = q.options.flatMap((option) => {
      const condition = answerWords.has(option) ? null : noteCondition(q.note, option);
      return condition
        ? [
            {
              label: `${option} 응답 수`,
              context: { conditions: [condition], operation: 'count' as const },
            },
          ]
        : [];
    });
    if (choices.length > 0) {
      groups.push({ title: `${q.note} 선택지별 응답 수`, kind: 'option', choices });
    }
  }
  return groups;
}

/**
 * "문항 문구 - 값"에서 서버가 선택지보다 먼저 답함·비움으로 읽는 낱말.
 * 정본은 LiteralMatcher.java의 ANSWERED·UNANSWERED다. 바뀌면 함께 고친다.
 */
const answerWords = new Set(['응답', '답함', '작성함', '미응답', '안 씀', '비움']);

/**
 * 선택지 버튼의 대상: 제출 응답이 있는 버전의 선택형 문항(결과 API의 versions).
 * 현재 정의에만 있고 아직 제출이 없는 문항은 서버가 찾지 못하므로 뺀다.
 * 한 버전에서 문구가 겹치거나 현재 정의에서 문구가 겹치는 문항은 서버가 되묻게 되므로 뺀다.
 */
function submittedChoiceQuestions(
  results: FormResults,
  definitionNoteCount: Map<string, number>,
): Array<{ note: string; questionIds: string[]; options: string[] }> {
  return submittedChoiceIndex(results).filter(
    (q) => q.note.trim() && !q.repeated && (definitionNoteCount.get(q.note) ?? 0) <= 1,
  );
}

/** 제출 버전의 선택형 문항을 문구별로 모은 것. 거르기 전이다. */
function submittedChoiceIndex(
  results: FormResults,
): Array<{ note: string; questionIds: string[]; options: string[]; repeated: boolean }> {
  const byNote = new Map<string, { questionIds: string[]; options: string[] }>();
  const repeated = new Set<string>();
  for (const version of results.versions) {
    const seen = new Set<string>();
    for (const q of version.questions) {
      if (seen.has(q.question_note)) repeated.add(q.question_note);
      seen.add(q.question_note);
      const entry = byNote.get(q.question_note) ?? { questionIds: [], options: [] };
      if (!entry.questionIds.includes(q.question_id)) entry.questionIds.push(q.question_id);
      for (const option of Object.keys(q.counts)) {
        if (!entry.options.includes(option)) entry.options.push(option);
      }
      byNote.set(q.question_note, entry);
    }
  }
  return [...byNote.entries()].map(([note, entry]) => ({
    note,
    ...entry,
    repeated: repeated.has(note),
  }));
}

/** 예시가 없을 때 쓰는 문구. 조건 없이 세므로 어느 폼에서도 되묻지 않는다. */
export const fallbackExample = '전체 응답 몇 건이야?';

/**
 * 탐색 도우미 예시 문구. 그 폼에서 그대로 입력해도 되묻지 않는 질문만 만든다(사람 결정).
 * 기준은 묻기 밑 버튼(presetGroups)과 같다. 예시 문장에는 문항 이름이 없으므로
 * 같은 선택지 문구가 다른 문항에도 있으면 뺀다. 그대로 입력하는 문장이므로 서버가 질문 글을 먼저 보는
 * 버전 범위 표현(VERSION_SCOPE)과 일반어(GENERIC)도 뺀다(사람 결정, 검토 반영).
 * 첫 값은 건수 예시(없으면 대체 문구), 다음은 요약 예시다.
 */
export function exploreExamples(
  definition: CreatorFormDefinition | null,
  results: FormResults,
): string[] {
  if (!definition || definition.definition_version === null) return [fallbackExample];
  const { questions, noteCount, unique } = definitionQuestions(definition);
  const examples = [countExample(questions, noteCount, results) ?? fallbackExample];

  const summaryOn = definition.settings.ai_summary?.enabled === true;
  if (summaryOn && latestVersionSubmitted(definition, results)) {
    const summary = summarizableQuestions(unique)
      .map((q) => `${q.question_note} 요약해줘`)
      .find((sentence) => !versionScope.test(sentence));
    if (summary) examples.push(summary);
  }
  return examples;
}

/**
 * 서버가 파서보다 먼저 보고 unsupported_scope로 되묻는 버전 표현.
 * 정본은 QueryText.java의 VERSION_SCOPE다. 바뀌면 함께 고친다.
 */
const versionScope = new RegExp(
  '버전\\s*\\d+|\\d+\\s*번?째?\\s*버전|(?<![a-z0-9])v\\d+(?![a-z0-9])|버전\\s*(별|마다|끼리|간|사이|비교)' +
    '|(이전|예전|옛|구|최신|새|현재|지난|첫|첫번째|마지막|특정|각)\\s*버전',
  'i',
);

/**
 * 서버가 조건이 아닌 일반어로 보는 낱말. 파서가 조건에서 빼면 서버가 되살리지 않아 전체 응답을 센다.
 * 정본은 QueryText.java의 GENERIC·TRAILING_PARTICLE·isGeneric이다. 바뀌면 함께 고친다.
 */
const genericWords = new Set([
  '전체',
  '전부',
  '모든',
  '모두',
  '총',
  '다',
  '응답',
  '응답자',
  '응답수',
  '제출',
  '제출자',
  '결과',
  '학생',
  '지원자',
  '신청자',
  '참여자',
  '사람',
  '인원',
  '인원수',
  '수',
  '명',
  '명수',
  '명이야',
  '명인가요',
  '건',
  '건수',
  '개',
  '개수',
  '중',
  '중에서',
  '의견',
  '내용',
  '답변',
  '답',
  '목록',
  '리스트',
  '명단',
  '요약',
  '정리',
  '나열',
  '정렬',
  '알려',
  '보여',
  '폼',
  '설문',
  '설문조사',
]);
const trailingParticle = /(들|을|를|은|는|이|가|의|도|만|에)$/;

function isGenericWord(option: string): boolean {
  if (option.startsWith('몇') || genericWords.has(option)) return true;
  let stem = option;
  for (let count = 0; count < 2; count++) {
    stem = stem.replace(trailingParticle, '');
    if (genericWords.has(stem)) return true;
  }
  return false;
}

function countExample(
  questions: Question[],
  noteCount: Map<string, number>,
  results: FormResults,
): string | null {
  const shared = optionsInOtherQuestions(results);
  const byId = new Map(questions.map((q) => [q.question_id, q]));

  for (const q of submittedChoiceQuestions(results, noteCount)) {
    // 개인정보 여부는 현재 정의로만 안다. 현재 정의에 없는 문항은 확인할 수 없어 쓰지 않는다.
    const current = q.questionIds.map((id) => byId.get(id));
    if (current.some((d) => !d || isPersonal(d))) continue;
    const sentence = q.options
      .filter(
        (o) =>
          o.trim() &&
          !answerWords.has(o) &&
          !isGenericWord(o) &&
          noteCondition(q.note, o) !== null &&
          !shared.has(o),
      )
      .map((o) => `${o} 응답은 몇 건이야?`)
      .find((s) => !versionScope.test(s));
    if (sentence) return sentence;
  }
  return null;
}

/**
 * 문항 이름 없이 보내면 서버가 뜻이 다른 여러 문항에 걸렸다고 되묻는 선택지.
 * 서버 기준(ClarifyRules.differentMeanings): question_id도 다르고 문구·유형도 다른 문항에 걸리면 되묻는다.
 * 서버는 한 버전 안의 두 문항도 되묻지만, 그중 문구·유형이 같은 두 문항은 submittedChoiceQuestions가
 * 문구 겹침으로 이미 빼므로 여기서는 버전 구분 없이 센다. 거르기 전 모든 제출 문항으로 센다.
 */
function optionsInOtherQuestions(results: FormResults): Set<string> {
  const typeOf = new Map(
    results.combined.questions.map((q) => [
      `${q.question_id}\n${q.question_note}`,
      q.question_type,
    ]),
  );
  const seen = new Map<string, { ids: Set<string>; wordings: Set<string> }>();
  for (const version of results.versions) {
    for (const q of version.questions) {
      const type = typeOf.get(`${q.question_id}\n${q.question_note}`) ?? '';
      for (const option of Object.keys(q.counts)) {
        const entry = seen.get(option) ?? { ids: new Set(), wordings: new Set() };
        entry.ids.add(q.question_id);
        entry.wordings.add(`${q.question_note}\n${type}`);
        seen.set(option, entry);
      }
    }
  }
  const shared = new Set<string>();
  for (const [option, { ids, wordings }] of seen)
    if (ids.size >= 2 && wordings.size >= 2) shared.add(option);
  return shared;
}

/** 묻기 실패를 쉬운 말로. 오류 코드 원문은 보이지 않는다. */
export function analyzeErrorMessage(status: number | null): string {
  switch (status) {
    case 401:
      return '로그인이 필요해요. 다시 로그인한 뒤 물어봐 주세요.';
    case 403:
      return '이 폼을 만든 사람만 분석할 수 있어요.';
    case 404:
      return '이 폼을 찾을 수 없어요.';
    case 400:
      return '요청을 처리하지 못했어요. 질문을 바꿔 다시 물어봐 주세요.';
    case 503:
      return 'AI 분석을 지금 쓸 수 없어요. 잠시 뒤 다시 시도해 주세요.';
    default:
      return '분석하지 못했어요. 잠시 뒤 다시 시도해 주세요.';
  }
}
