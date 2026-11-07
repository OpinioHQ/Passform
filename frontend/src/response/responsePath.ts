/**
 * 응답 화면의 경로 계산. 페이지 넘기기와 진행 표시가 이 파일만 쓴다(사람 결정 A안).
 * 규칙은 서버 `domain/form/ResponsePathCalculator.java`와 같아야 한다. 두 계산이 같은 결과를 내는지는
 * `responsePath.test.ts`(Java 테스트와 1:1)와 `responsePath.golden.test.ts`(골든 케이스)로 확인한다.
 * 화면은 이 결과로 미리 안내할 뿐이며, 서버에 경로를 보내지 않는다. 최종 판정은 서버가 답으로 다시 계산한다.
 */
import type {
  AnswerValue,
  BranchRule,
  BranchTarget,
  DisplayMode,
  FormSection,
  Question,
} from '../types/api';

export interface ResponsePath {
  onPath: Question[];
  skipped: Question[];
}

/** 화면의 답 상태. 값이 없는 문항은 키가 없거나 빈 값이다. */
export type AnswerMap = Record<string, AnswerValue | undefined>;

/** 서버 `Answer.values()`와 같다. 공백만 있는 값은 비운 답이다. */
export function answerValues(value: AnswerValue | undefined): string[] {
  if (value === undefined) return [];
  const items = Array.isArray(value) ? value : [value];
  return items.filter((item) => item.trim() !== '');
}

function ruleMatches(rule: BranchRule, visited: Map<string, string[]>): boolean {
  const holds = (c: { question_id: string; option: string }) =>
    (visited.get(c.question_id) ?? []).includes(c.option);
  return rule.when.match === 'all'
    ? rule.when.conditions.every(holds)
    : rule.when.conditions.some(holds);
}

/** 대상의 전체 문항 순서 위치. 빈 섹션이면 그다음 문항 위치이고, 찾지 못하면 -1이다. */
function targetIndex(sections: FormSection[], goTo: BranchTarget): number {
  let index = 0;
  for (const section of sections) {
    if ('section_id' in goTo && section.section_id === goTo.section_id) return index;
    for (const question of section.questions) {
      if ('question_id' in goTo && question.question_id === goTo.question_id) return index;
      index++;
    }
  }
  return -1;
}

function lastIndexOfSection(sections: FormSection[], index: number): number {
  let end = 0;
  for (const section of sections) {
    end += section.questions.length;
    if (index < end) return end - 1;
  }
  throw new Error('문항 위치가 정의 밖이다');
}

/**
 * 답으로 응답 경로를 계산한다. `displayMode`는 응답자가 고른 보기가 아니라 폼의 `default_mode`다
 * (서버도 정의의 값으로 계산한다).
 */
export function calculateResponsePath(
  sections: FormSection[],
  displayMode: DisplayMode,
  answers: AnswerMap,
): ResponsePath {
  const questions = sections.flatMap((section) => section.questions);
  const sectionPage = displayMode === 'section_page';
  const onPath: Question[] = [];
  // 경로에서 만난 문항의 답만 분기 조건에 쓴다. 건너뛴 문항의 답이 경로를 바꾸면 안 된다.
  const visited = new Map<string, string[]>();
  let index = 0;
  while (index < questions.length) {
    const last = sectionPage ? lastIndexOfSection(sections, index) : index;
    for (const question of questions.slice(index, last + 1)) {
      onPath.push(question);
      visited.set(question.question_id, answerValues(answers[question.question_id]));
    }
    // 위에서부터 검사해 처음 맞는 규칙을 따른다.
    const goTo = questions[last].branch_rules.find((rule) => ruleMatches(rule, visited))?.go_to;
    if (goTo && 'submit' in goTo) break;
    // 뒤쪽이 아니거나 없는 대상이면 다음으로 넘어가 반복에 빠지지 않는다.
    const target = goTo ? targetIndex(sections, goTo) : -1;
    index = target > last ? target : last + 1;
  }
  const onPathIds = new Set(onPath.map((question) => question.question_id));
  return { onPath, skipped: questions.filter((question) => !onPathIds.has(question.question_id)) };
}

/** 화면 한 장. `one_question`은 문항 하나, `section_page`는 섹션 하나다. */
export interface ResponsePage {
  section: FormSection;
  questions: Question[];
}

/** 지금 답으로 정해진 경로를 화면 단위로 나눈다. `all_questions`는 이 함수를 쓰지 않는다. */
export function pagesOf(
  sections: FormSection[],
  displayMode: DisplayMode,
  viewMode: 'one_question' | 'section_page',
  answers: AnswerMap,
): ResponsePage[] {
  const sectionOf = new Map<string, FormSection>();
  sections.forEach((section) =>
    section.questions.forEach((question) => sectionOf.set(question.question_id, section)),
  );
  const pages: ResponsePage[] = [];
  for (const question of calculateResponsePath(sections, displayMode, answers).onPath) {
    const section = sectionOf.get(question.question_id) as FormSection;
    const previous = pages[pages.length - 1];
    if (viewMode === 'section_page' && previous?.section === section) {
      previous.questions.push(question);
    } else {
      pages.push({ section, questions: [question] });
    }
  }
  return pages;
}

/**
 * 현재 화면부터 뒤 경로가 답으로 이미 정해졌는지. 현재 화면이나 그 뒤 경로의 규칙이 아직 답하지 않은 문항을
 * 조건으로 쓰면 경로가 달라질 수 있으므로 남은 분량을 확정하지 않는다(SPEC 5절 219행, AC60).
 */
export function remainingIsKnown(
  pages: ResponsePage[],
  current: number,
  answers: AnswerMap,
): boolean {
  return pages
    .slice(current)
    .flatMap((page) => page.questions)
    .every((question) =>
      question.branch_rules.every((rule) =>
        rule.when.conditions.every(
          (condition) => answerValues(answers[condition.question_id]).length > 0,
        ),
      ),
    );
}
