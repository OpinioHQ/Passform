// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { FormResults, TextAnswer } from '../types/api';
import { ResultsDashboard } from './ResultsPage';

/** 사람 결정(2026-11-09): 결과 대시보드의 단답·장문 카드. 서버가 준 순서·답을 그대로 그리는지 본다. */
const answer = (id: string, version: number, value: string, at: string): TextAnswer => ({
  response_id: id,
  form_version: version,
  value,
  submitted_at: at,
});

const longMotive = '처음 지원합니다.\n두 번째 줄까지 이어지는 아주 긴 지원 동기입니다.';
const v2Age = answer('r3', 2, '23', '2026-11-03T00:00:00Z');
const v1Motive = answer('r1', 1, longMotive, '2026-11-01T00:00:00Z');
const v1Script = answer('r2', 1, '<script>alert(1)</script>', '2026-11-02T00:00:00Z');

const results: FormResults = {
  response_count: 3,
  combined: {
    questions: [
      {
        question_id: 'q1',
        question_note: '참석하나요?',
        question_type: 'multiple_choice',
        form_versions: [1, 2],
        options: [{ label: '예', count: 3, form_versions: [1, 2] }],
      },
    ],
    text_questions: [
      {
        question_id: 'q2',
        question_note: '나이',
        question_type: 'short_answer',
        form_versions: [2],
        answers: [v2Age],
      },
      {
        question_id: 'q3',
        question_note: '지원 동기',
        question_type: 'long_answer',
        form_versions: [1],
        answers: [v1Script, v1Motive],
      },
    ],
    question_order: [
      { question_id: 'q3', question_note: '지원 동기', question_type: 'long_answer' },
      { question_id: 'q1', question_note: '참석하나요?', question_type: 'multiple_choice' },
      { question_id: 'q2', question_note: '나이', question_type: 'short_answer' },
    ],
  },
  versions: [
    {
      form_version: 1,
      response_count: 2,
      questions: [{ question_id: 'q1', question_note: '참석하나요?', counts: { 예: 2 } }],
      text_questions: [
        {
          question_id: 'q3',
          question_note: '지원 동기',
          question_type: 'long_answer',
          answers: [v1Script, v1Motive],
        },
      ],
      question_order: [
        { question_id: 'q3', question_note: '지원 동기', question_type: 'long_answer' },
        { question_id: 'q1', question_note: '참석하나요?', question_type: 'multiple_choice' },
      ],
    },
    {
      form_version: 2,
      response_count: 1,
      questions: [{ question_id: 'q1', question_note: '참석하나요?', counts: { 예: 1 } }],
      text_questions: [
        {
          question_id: 'q2',
          question_note: '나이',
          question_type: 'short_answer',
          answers: [v2Age],
        },
      ],
      question_order: [
        { question_id: 'q1', question_note: '참석하나요?', question_type: 'multiple_choice' },
        { question_id: 'q2', question_note: '나이', question_type: 'short_answer' },
      ],
    },
  ],
};

const card = (note: string) =>
  screen.getByRole('heading', { level: 2, name: note }).closest('section')!;
const headings = () => screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);

afterEach(cleanup);

describe('결과 대시보드 단답·장문 카드', () => {
  it('question_order대로 섞어 놓고, 긴 답은 한 줄로 접혀 있다가 자세히 보기로 전부 보인다', () => {
    render(<ResultsDashboard results={results} />);
    expect(headings()).toEqual(['지원 동기', '참석하나요?', '나이']);
    const motive = card('지원 동기');
    expect(within(motive).getByText('장문형')).toBeTruthy();
    expect(within(motive).getByText('답 2건')).toBeTruthy();

    const line = within(motive).getByText(
      (_, el) => el?.textContent === longMotive && el.tagName === 'P',
    );
    expect(line.className).toBe('result-text-line');
    const row = line.closest('li')!;
    fireEvent.click(within(row).getByRole('button', { name: '자세히 보기' }));
    expect(line.className).toBe('result-text-full');
    expect(line.textContent).toBe(longMotive);
    fireEvent.click(within(row).getByRole('button', { name: '접기' }));
    expect(line.className).toBe('result-text-line');
  });

  it('<script> 같은 글자는 HTML이 아니라 글자로 보인다', () => {
    render(<ResultsDashboard results={results} />);
    expect(within(card('지원 동기')).getByText('<script>alert(1)</script>')).toBeTruthy();
    expect(document.querySelector('.result-texts script')).toBeNull();
  });

  it('폼 버전 탭에서는 그 버전의 답만 보이고 화면에 응답 ID를 쓰지 않는다', () => {
    render(<ResultsDashboard results={results} />);
    fireEvent.click(screen.getByRole('tab', { name: '폼 버전 2' }));
    expect(headings()).toEqual(['참석하나요?', '나이']);
    expect(within(card('나이')).getByText('23')).toBeTruthy();
    expect(screen.queryByText('<script>alert(1)</script>')).toBeNull();
    expect(screen.queryByText(/\br\d\b/)).toBeNull();
  });
});
