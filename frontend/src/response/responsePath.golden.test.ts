/**
 * 골든 케이스를 화면 경로 계산으로 다시 푼다. 케이스와 fixture를 복사하지 않고 원본 파일을 직접 읽는다.
 * 서버 실행기 `SubmissionGoldenTest`가 같은 케이스를 서버 계산으로 푼다. 두 쪽이 모두 통과하면 두 계산의 결과가 같다.
 */
import { describe, expect, it } from 'vitest';
import { parse } from 'yaml';
import goldenText from '../../../tests/harness/golden_cases.yaml?raw';
import type { AnswerValue, FormDefinition } from '../types/api';
import { isAnswered } from './answerCheck';
import { type AnswerMap, calculateResponsePath } from './responsePath';

interface GoldenAnswer {
  question_id: string;
  value: AnswerValue;
}
interface SubmissionCase {
  name: string;
  input: { fixture: string; form_version: number; answers: GoldenAnswer[] };
  expect_saved_answers?: GoldenAnswer[];
  expect_question_ids?: string[];
}
interface Fixture {
  form_versions: { form_version: number; form: FormDefinition }[];
}

const fixtures = import.meta.glob<Fixture>('../../../data/seed/forms/*.json', {
  eager: true,
  import: 'default',
});
const golden = parse(goldenText) as { submission_cases: SubmissionCase[] };

function caseNamed(name: string): SubmissionCase {
  const found = golden.submission_cases.find((c) => c.name === name);
  if (!found) throw new Error(`골든 케이스 ${name}이(가) 없다`);
  return found;
}

function solve(name: string) {
  const golden = caseNamed(name);
  const fixture = fixtures[`../../../${golden.input.fixture}`];
  if (!fixture) throw new Error(`fixture ${golden.input.fixture}을(를) 읽지 못했다`);
  const version = fixture.form_versions.find((v) => v.form_version === golden.input.form_version);
  if (!version) throw new Error(`버전 ${golden.input.form_version}이 fixture에 없다`);
  const answers: AnswerMap = Object.fromEntries(
    golden.input.answers.map((a) => [a.question_id, a.value]),
  );
  const form = version.form;
  const path = calculateResponsePath(form.sections, form.settings.display.default_mode, answers);
  return { golden, answers, path };
}

describe('골든 submission_cases를 화면 경로 계산으로 푼다', () => {
  it('skipped_answers_dropped: 경로 위 문항의 답만 남는다', () => {
    const { golden, path } = solve('skipped_answers_dropped');
    const onPath = new Set(path.onPath.map((q) => q.question_id));
    const kept = golden.input.answers.filter((a) => onPath.has(a.question_id));
    expect(golden.expect_saved_answers).toBeDefined();
    expect(kept).toEqual(golden.expect_saved_answers);
    // 건너뛴 문항이 실제로 있어야 이 케이스가 경로 계산을 시험한다.
    expect(golden.input.answers.length).toBeGreaterThan(kept.length);
  });

  it('required_missing_visited_only: 지나온 필수 빈 문항만 고른다', () => {
    const { golden, answers, path } = solve('required_missing_visited_only');
    const missing = path.onPath
      .filter((q) => q.required && !isAnswered(q, answers[q.question_id]))
      .map((q) => q.question_id);
    expect(golden.expect_question_ids).toBeDefined();
    expect(missing).toEqual(golden.expect_question_ids);
    // 건너뛴 필수 문항이 있어야 "지나온 문항만"을 시험한다.
    expect(path.skipped.some((q) => q.required)).toBe(true);
  });
});
