/**
 * 서버 `ResponsePathCalculatorTest`와 1:1로 같은 상황을 화면 계산에 돌린다(AGENTS.md 5절 "같은 응답 경로 계산").
 * 테스트 이름 앞의 Java 메서드 이름이 대응하는 서버 테스트다. 대응표는 `docs/prompts/delegation_response_screen.md`에 있다.
 */
import { describe, expect, it } from 'vitest';
import type {
  BranchCondition,
  BranchRule,
  BranchTarget,
  DisplayMode,
  FormSection,
  Question,
} from '../types/api';
import { type AnswerMap, calculateResponsePath, pagesOf, remainingIsKnown } from './responsePath';

const YES_NO = ['예', '아니오'];
const GRADES = ['3학년', '4학년'];

function question(
  id: string,
  type: Question['question_type'],
  required: boolean,
  options: string[],
  ...rules: BranchRule[]
): Question {
  return {
    question_id: id,
    template_id: null,
    question_note: id,
    question_type: type,
    options,
    required,
    profile_key: null,
    attachments: [],
    branch_rules: rules,
  };
}
const text = (id: string, required: boolean) => question(id, 'short_answer', required, []);
const section = (id: string, ...questions: Question[]): FormSection => ({
  section_id: id,
  title: id,
  questions,
});
const when = (questionId: string, option: string): BranchCondition => ({
  question_id: questionId,
  option,
});
const rule = (
  match: 'all' | 'any',
  goTo: BranchTarget,
  ...conditions: BranchCondition[]
): BranchRule => ({
  when: { match, conditions },
  go_to: goTo,
});
const toSection = (id: string): BranchTarget => ({ section_id: id });
const toQuestion = (id: string): BranchTarget => ({ question_id: id });
const toSubmit = (): BranchTarget => ({ submit: true });

interface Definition {
  mode: DisplayMode;
  sections: FormSection[];
}
const definition = (mode: DisplayMode, sections: FormSection[]): Definition => ({ mode, sections });
const path = (form: Definition, answers: AnswerMap) =>
  calculateResponsePath(form.sections, form.mode, answers);
const ids = (questions: Question[]) => questions.map((q) => q.question_id);

function combination(match: 'all' | 'any'): Definition {
  return definition('section_page', [
    section(
      's1',
      question('q2', 'multiple_choice', true, YES_NO),
      question(
        'q3',
        'multiple_choice',
        true,
        GRADES,
        rule(match, toSection('s4'), when('q2', '예'), when('q3', '4학년')),
      ),
    ),
    section('s2', text('q4', false)),
    section('s4', text('q5', false)),
  ]);
}

describe('화면 경로 계산 (ResponsePathCalculatorTest와 1:1)', () => {
  it('AC9_아니오면_s3로_건너뛰고_s2_문항은_경로_밖이다', () => {
    const form = definition('section_page', [
      section(
        's1',
        question('q1', 'multiple_choice', true, YES_NO),
        question(
          'q2',
          'multiple_choice',
          true,
          YES_NO,
          rule('all', toSection('s3'), when('q2', '아니오')),
        ),
      ),
      section('s2', text('q3', true)),
      section('s3', text('q4', true)),
    ]);
    const result = path(form, { q1: '예', q2: '아니오' });
    expect(ids(result.onPath)).toEqual(['q1', 'q2', 'q4']);
    expect(ids(result.skipped)).toEqual(['q3']);
  });

  it('AC44_all은_조합이_모두_맞아야_이동한다', () => {
    expect(ids(path(combination('all'), { q2: '예', q3: '4학년' }).onPath)).toEqual([
      'q2',
      'q3',
      'q5',
    ]);
    expect(ids(path(combination('all'), { q2: '예', q3: '3학년' }).onPath)).toEqual([
      'q2',
      'q3',
      'q4',
      'q5',
    ]);
  });

  it('AC44_any는_하나만_맞아도_이동한다', () => {
    expect(ids(path(combination('any'), { q2: '아니오', q3: '4학년' }).onPath)).toEqual([
      'q2',
      'q3',
      'q5',
    ]);
  });

  it('AC44_여러_규칙이_맞으면_위의_규칙을_따른다', () => {
    const form = definition('one_question', [
      section(
        's1',
        question(
          'q1',
          'multiple_choice',
          true,
          YES_NO,
          rule('all', toQuestion('q3'), when('q1', '예')),
          rule('all', toQuestion('q4'), when('q1', '예')),
        ),
        text('q2', false),
        text('q3', false),
        text('q4', false),
      ),
    ]);
    expect(ids(path(form, { q1: '예' }).onPath)).toEqual(['q1', 'q3', 'q4']);
  });

  it('checkbox_조건은_그_선택지를_포함하면_맞다', () => {
    const form = definition('one_question', [
      section(
        's1',
        question(
          'q1',
          'checkbox',
          false,
          ['가', '나'],
          rule('all', toQuestion('q3'), when('q1', '나')),
        ),
        text('q2', false),
        text('q3', false),
      ),
    ]);
    expect(ids(path(form, { q1: ['가', '나'] }).onPath)).toEqual(['q1', 'q3']);
    expect(ids(path(form, { q1: ['가'] }).onPath)).toEqual(['q1', 'q2', 'q3']);
  });

  it('one_question은_규칙이_없으면_다음_문항이고_제출_이동이면_끝난다', () => {
    const form = definition('one_question', [
      section(
        's1',
        question(
          'q1',
          'multiple_choice',
          true,
          YES_NO,
          rule('all', toSubmit(), when('q1', '아니오')),
        ),
        text('q2', false),
      ),
      section('s2', text('q3', false)),
    ]);
    expect(ids(path(form, { q1: '예' }).onPath)).toEqual(['q1', 'q2', 'q3']);
    const submitted = path(form, { q1: '아니오' });
    expect(ids(submitted.onPath)).toEqual(['q1']);
    expect(ids(submitted.skipped)).toEqual(['q2', 'q3']);
  });

  it('section_page는_섹션을_한꺼번에_지나고_맞는_규칙이_없으면_다음_섹션이다', () => {
    const form = definition('section_page', [
      section('s1', question('q1', 'multiple_choice', true, YES_NO), text('q2', false)),
      section('s2', text('q3', false)),
    ]);
    expect(ids(path(form, {}).onPath)).toEqual(['q1', 'q2', 'q3']);
  });

  it('답이_없어도_경로를_계산한다', () => {
    expect(ids(path(combination('all'), {}).onPath)).toEqual(['q2', 'q3', 'q4', 'q5']);
  });

  it('건너뛴_문항에_보낸_답은_뒤의_분기에_영향을_주지_않는다', () => {
    const form = definition('one_question', [
      section(
        's1',
        question(
          'q1',
          'multiple_choice',
          true,
          YES_NO,
          rule('all', toQuestion('q3'), when('q1', '아니오')),
        ),
        question('q2', 'multiple_choice', false, YES_NO),
        question('q3', 'multiple_choice', false, YES_NO, rule('all', toSubmit(), when('q2', '예'))),
        text('q4', false),
      ),
    ]);
    expect(ids(path(form, { q1: '아니오', q2: '예', q3: '예' }).onPath)).toEqual([
      'q1',
      'q3',
      'q4',
    ]);
  });

  it('뒤로_가거나_없는_대상이어도_반복에_빠지지_않는다', () => {
    const form = definition('one_question', [
      section(
        's1',
        text('q1', false),
        question(
          'q2',
          'multiple_choice',
          false,
          YES_NO,
          rule('all', toQuestion('q1'), when('q2', '예')),
        ),
        question(
          'q3',
          'multiple_choice',
          false,
          YES_NO,
          rule('all', toSection('none'), when('q3', '예')),
        ),
        text('q4', false),
      ),
    ]);
    // Java의 assertTimeoutPreemptively 대신 vitest 기본 시간 제한(5초) 안에 끝나야 통과한다.
    expect(ids(path(form, { q2: '예', q3: '예' }).onPath)).toEqual(['q1', 'q2', 'q3', 'q4']);
  });

  it('대상_섹션이_비어_있으면_그다음_문항으로_간다', () => {
    const form = definition('section_page', [
      section(
        's1',
        question(
          'q1',
          'multiple_choice',
          true,
          YES_NO,
          rule('all', toSection('s3'), when('q1', '예')),
        ),
      ),
      section('s2', text('q2', false)),
      section('s3'),
      section('s4', text('q3', false)),
    ]);
    expect(ids(path(form, { q1: '예' }).onPath)).toEqual(['q1', 'q3']);
  });
});

describe('화면 전용: 페이지 나누기와 남은 분량 확정 (AC60)', () => {
  const branchless = definition('one_question', [
    section('s1', text('q1', false)),
    section('s2', text('q2', false), text('q3', false)),
    section('s3', text('q4', false)),
  ]);

  it('section_page는 경로 위 문항을 섹션 단위로 묶는다', () => {
    const pages = pagesOf(combination('all').sections, 'section_page', 'section_page', {
      q2: '예',
      q3: '4학년',
    });
    expect(pages.map((p) => [p.section.section_id, ids(p.questions)])).toEqual([
      ['s1', ['q2', 'q3']],
      ['s4', ['q5']],
    ]);
  });

  it('분기가 없으면 남은 분량이 확정된다', () => {
    const pages = pagesOf(branchless.sections, 'one_question', 'one_question', {});
    expect(remainingIsKnown(pages, 1, {})).toBe(true);
  });

  it('아직 답하지 않은 문항을 조건으로 쓰는 규칙이 앞에 있으면 확정하지 않는다', () => {
    const pages = pagesOf(combination('all').sections, 'section_page', 'section_page', {});
    expect(remainingIsKnown(pages, 0, {})).toBe(false);
    expect(remainingIsKnown(pages, 0, { q2: '예', q3: '4학년' })).toBe(true);
  });
});
