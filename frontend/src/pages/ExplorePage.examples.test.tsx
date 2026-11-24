// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { presetGroups } from '../explore/exploreView';
import type { CreatorFormDefinition, FormResults, Question, QuestionType } from '../types/api';
import { ExplorePage } from './ExplorePage';

afterEach(cleanup);

const question = (
  id: string,
  note: string,
  type: QuestionType,
  options: string[] = [],
  extra: Partial<Question> = {},
): Question => ({
  question_id: id,
  template_id: null,
  question_note: note,
  question_type: type,
  options,
  required: false,
  profile_key: null,
  attachments: [],
  branch_rules: [],
  ...extra,
});

const form = (
  questions: Question[],
  options: { summary?: boolean; version?: number | null } = {},
): CreatorFormDefinition => ({
  title: '동아리 모집',
  note: '',
  settings: {
    editable: true,
    cancellable: true,
    display: { default_mode: 'all_questions' },
    ai_summary: { enabled: options.summary ?? false },
  },
  payment_link: null,
  consent_template_id: 'consent_basic',
  note_attachments: [],
  sections: [{ section_id: 's1', title: '', questions }],
  status: 'open',
  share_url: null,
  definition_version: options.version === undefined ? 1 : options.version,
});

/** 결과 API: 버전별 선택형 문항과 선택지별 수. */
const submitted = (
  ...versions: Array<{
    version?: number;
    count?: number;
    questions: Array<[id: string, note: string, options: string[]]>;
  }>
): FormResults => ({
  response_count: versions.reduce((n, v) => n + (v.count ?? 1), 0),
  combined: { questions: [] },
  versions: versions.map((v) => ({
    form_version: v.version ?? 1,
    response_count: v.count ?? 1,
    questions: v.questions.map(([id, note, options]) => ({
      question_id: id,
      question_note: note,
      counts: Object.fromEntries(options.map((o) => [o, 1])),
    })),
  })),
});

function renderExamples(definition: CreatorFormDefinition | null, results: FormResults) {
  render(
    <ExplorePage
      definition={definition}
      results={results}
      analyze={() => Promise.reject(new Error('부르지 않는다'))}
      loadResponse={() => Promise.reject(new Error('부르지 않는다'))}
      onBuilder={() => {}}
      renderDashboard={({ aside }) => <>{aside}</>}
    />,
  );
  const bubble = screen.getByText(/^예: "/).textContent;
  const placeholder = screen.getByLabelText<HTMLInputElement>('응답에 대해 묻기').placeholder;
  return { bubble, placeholder };
}

const grade = question('q1', '학년', 'multiple_choice', ['1학년', '2학년']);
const motive = question('q2', '지원 동기', 'long_answer');
const gradeResults = submitted({ questions: [['q1', '학년', ['1학년', '2학년']]] });

describe('탐색 도우미 예시는 그 폼의 문항으로 만든다', () => {
  it('선택형만 있는 폼은 맨 앞 문항의 맨 앞 선택지로 건수 예시만 만든다', () => {
    const { bubble, placeholder } = renderExamples(form([grade], { summary: true }), gradeResults);
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?"');
    expect(placeholder).toBe('예: 1학년 응답은 몇 건이야?');
  });

  it('AI 요약이 켜져 있고 최신 버전에 제출이 있으면 요약 예시를 더한다', () => {
    const { bubble, placeholder } = renderExamples(
      form([grade, motive], { summary: true }),
      gradeResults,
    );
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?", "지원 동기 요약해줘"');
    expect(placeholder).toBe('예: 1학년 응답은 몇 건이야?');
  });

  it('AI 요약이 꺼져 있으면 요약 예시를 만들지 않는다', () => {
    const { bubble } = renderExamples(form([grade, motive], { summary: false }), gradeResults);
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?"');
  });

  it('AI 요약이 켜져 있어도 최신 버전에 제출이 없으면 요약 예시를 만들지 않는다', () => {
    const { bubble } = renderExamples(
      form([grade, motive], { summary: true, version: 2 }),
      gradeResults,
    );
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?"');
  });

  it('요약 예시는 개인정보·문구가 겹치는 문항을 건너뛴 맨 앞 요약 가능 문항을 쓴다', () => {
    const name = question('q3', '이름', 'short_answer', [], {
      input_format: { kind: 'name' },
    });
    const dupA = question('q4', '하고 싶은 말', 'short_answer');
    const dupB = question('q5', '하고 싶은 말', 'long_answer');
    const { bubble } = renderExamples(
      form([name, dupA, dupB, grade, motive], { summary: true }),
      gradeResults,
    );
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?", "지원 동기 요약해줘"');
  });

  it('체크박스 문항의 선택지를 쓴다', () => {
    const activity = question('q1', '관심 활동', 'checkbox', ['공연', '전시']);
    const { bubble } = renderExamples(
      form([activity]),
      submitted({ questions: [['q1', '관심 활동', ['공연', '전시']]] }),
    );
    expect(bubble).toBe('예: "공연 응답은 몇 건이야?"');
  });

  it('드롭다운 문항의 선택지를 쓴다', () => {
    const campus = question('q1', '캠퍼스', 'dropdown', ['서울', '안성']);
    const { bubble } = renderExamples(
      form([campus]),
      submitted({ questions: [['q1', '캠퍼스', ['서울', '안성']]] }),
    );
    expect(bubble).toBe('예: "서울 응답은 몇 건이야?"');
  });

  it('첫 문항이 개인정보 문항이면 건너뛴다', () => {
    const department = question('q0', '학과', 'dropdown', ['경영학과', '컴퓨터공학과'], {
      profile_key: 'department',
    });
    const { bubble } = renderExamples(
      form([department, grade]),
      submitted({
        questions: [
          ['q0', '학과', ['경영학과', '컴퓨터공학과']],
          ['q1', '학년', ['1학년', '2학년']],
        ],
      }),
    );
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?"');
  });

  it('제출이 없는 버전에만 있는 문항·선택지는 쓰지 않는다', () => {
    const newGrade = question('q1', '학년', 'multiple_choice', ['3학년', '1학년']);
    const time = question('q2', '희망 활동 시간', 'multiple_choice', ['평일', '주말']);
    // 현재 정의(버전 2)의 "3학년"과 "희망 활동 시간"은 제출이 없다. 결과 API에는 버전 1만 있다.
    const { bubble } = renderExamples(
      form([time, newGrade], { version: 2 }),
      submitted({ version: 1, questions: [['q1', '학년', ['1학년']]] }),
    );
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?"');
  });

  it('답함·비움으로 읽히는 선택지(answerWords)는 쓰지 않는다', () => {
    const join = question('q1', '참여 여부', 'multiple_choice', [
      '응답',
      '답함',
      '작성함',
      '미응답',
      '안 씀',
      '비움',
      '보류',
    ]);
    const { bubble } = renderExamples(
      form([join]),
      submitted({
        questions: [
          ['q1', '참여 여부', ['응답', '답함', '작성함', '미응답', '안 씀', '비움', '보류']],
        ],
      }),
    );
    expect(bubble).toBe('예: "보류 응답은 몇 건이야?"');
  });

  it('현재 정의에서 문구가 겹치는 문항은 쓰지 않는다', () => {
    const a = question('q1', '학년', 'multiple_choice', ['1학년']);
    const b = question('q2', '학년', 'dropdown', ['2학년']);
    const campus = question('q3', '캠퍼스', 'dropdown', ['서울']);
    const { bubble } = renderExamples(
      form([a, b, campus]),
      submitted({
        questions: [
          ['q1', '학년', ['1학년']],
          ['q3', '캠퍼스', ['서울']],
        ],
      }),
    );
    expect(bubble).toBe('예: "서울 응답은 몇 건이야?"');
  });

  it('한 버전 안에서 문구가 겹치는 문항은 쓰지 않는다', () => {
    // 버전 1에서는 두 문항이 모두 "학년"이었고, 현재 정의(버전 2)에서 하나만 이름을 바꿨다.
    const renamed = question('q2', '학년(편입)', 'multiple_choice', ['2학년']);
    const campus = question('q3', '캠퍼스', 'dropdown', ['서울']);
    const { bubble } = renderExamples(
      form([grade, renamed, campus], { version: 2 }),
      submitted({
        version: 1,
        questions: [
          ['q1', '학년', ['1학년']],
          ['q2', '학년', ['2학년']],
          ['q3', '캠퍼스', ['서울']],
        ],
      }),
    );
    expect(bubble).toBe('예: "서울 응답은 몇 건이야?"');
  });

  it('같은 선택지 문구가 다른 문항에도 있으면("예") 쓰지 않는다', () => {
    const attend = question('q1', '참석하나요?', 'multiple_choice', ['예', '보류']);
    const agree = question('q2', '사진 촬영에 동의하나요?', 'multiple_choice', ['예', '아니요']);
    const { bubble } = renderExamples(
      form([attend, agree]),
      submitted({
        questions: [
          ['q1', '참석하나요?', ['예', '보류']],
          ['q2', '사진 촬영에 동의하나요?', ['예', '아니요']],
        ],
      }),
    );
    expect(bubble).toBe('예: "보류 응답은 몇 건이야?"');
  });

  it('버전 사이에 문구만 바뀐 같은 문항(question_id 같음)의 선택지는 쓴다', () => {
    const current = question('q2', '현재 학년', 'multiple_choice', ['1학년', '2학년']);
    const { bubble } = renderExamples(
      form([current], { version: 2 }),
      submitted(
        { version: 1, questions: [['q2', '학년', ['1학년', '2학년']]] },
        { version: 2, questions: [['q2', '현재 학년', ['1학년', '2학년']]] },
      ),
    );
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?"');
  });

  it('버전 사이에 question_id와 문구가 모두 다른 문항에 있는 선택지는 쓰지 않는다', () => {
    const wanted = question('q7', '희망 학과', 'dropdown', ['경영학과', '데이터사이언스학과']);
    const { bubble } = renderExamples(
      form([wanted], { version: 2 }),
      submitted(
        { version: 1, questions: [['q1', '소속 학과', ['경영학과']]] },
        { version: 2, questions: [['q7', '희망 학과', ['경영학과', '데이터사이언스학과']]] },
      ),
    );
    expect(bubble).toBe('예: "데이터사이언스학과 응답은 몇 건이야?"');
  });

  it('서버가 버전 범위로 읽는 표현이 든 선택지는 쓰지 않는다(unsupported_scope)', () => {
    const app = question('q1', '어느 앱을 쓰나요?', 'multiple_choice', [
      '구 버전',
      '새버전',
      'v2',
      '버전 3',
      '2번째 버전',
      '버전별',
      '웹',
    ]);
    const { bubble } = renderExamples(
      form([app]),
      submitted({
        questions: [
          [
            'q1',
            '어느 앱을 쓰나요?',
            ['구 버전', '새버전', 'v2', '버전 3', '2번째 버전', '버전별', '웹'],
          ],
        ],
      }),
    );
    expect(bubble).toBe('예: "웹 응답은 몇 건이야?"');
  });

  it('"버전"이 들어가도 버전 범위 표현이 아니면 쓴다', () => {
    const app = question('q1', '앱 종류', 'multiple_choice', ['무료 버전', 'dev2']);
    const { bubble } = renderExamples(
      form([app]),
      submitted({ questions: [['q1', '앱 종류', ['무료 버전', 'dev2']]] }),
    );
    expect(bubble).toBe('예: "무료 버전 응답은 몇 건이야?"');
  });

  it('요약 예시도 버전 범위 표현이 든 문항은 건너뛴다', () => {
    const feedback = question('q3', '새 버전 사용 소감', 'long_answer');
    const { bubble } = renderExamples(
      form([grade, feedback, motive], { summary: true }),
      gradeResults,
    );
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?", "지원 동기 요약해줘"');
  });

  it('요약 가능 문항이 모두 버전 범위 표현이면 요약 예시를 만들지 않는다', () => {
    const feedback = question('q3', 'V2 사용 소감', 'long_answer');
    const { bubble } = renderExamples(form([grade, feedback], { summary: true }), gradeResults);
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?"');
  });

  it('서버가 일반어로 보는 선택지("학생", 조사 붙은 꼴, "몇"으로 시작)는 쓰지 않는다', () => {
    const who = question('q1', '신분', 'multiple_choice', [
      '학생',
      '지원자들',
      '사람들이',
      '몇몇',
      '직장인',
    ]);
    const { bubble } = renderExamples(
      form([who]),
      submitted({
        questions: [['q1', '신분', ['학생', '지원자들', '사람들이', '몇몇', '직장인']]],
      }),
    );
    expect(bubble).toBe('예: "직장인 응답은 몇 건이야?"');
  });

  it('일반어가 낱말 안에 들어 있기만 하면 쓴다("대학생")', () => {
    const who = question('q1', '신분', 'multiple_choice', ['대학생', '직장인']);
    const { bubble } = renderExamples(
      form([who]),
      submitted({ questions: [['q1', '신분', ['대학생', '직장인']]] }),
    );
    expect(bubble).toBe('예: "대학생 응답은 몇 건이야?"');
  });

  it('개인정보 문항에만 겹치는 선택지도 쓰지 않는다', () => {
    const dept = question('q0', '학과', 'dropdown', ['기타'], { profile_key: 'department' });
    const route = question('q1', '알게 된 경로', 'multiple_choice', ['기타', '지인 소개']);
    const { bubble } = renderExamples(
      form([dept, route]),
      submitted({
        questions: [
          ['q0', '학과', ['기타']],
          ['q1', '알게 된 경로', ['기타', '지인 소개']],
        ],
      }),
    );
    expect(bubble).toBe('예: "지인 소개 응답은 몇 건이야?"');
  });

  it('"-"가 있는 선택지는 쓰지 않는다(서버가 "문항 - 값"으로 나눈다)', () => {
    const time = question('q1', '시간', 'multiple_choice', ['10-12시', '오후']);
    const { bubble } = renderExamples(
      form([time]),
      submitted({ questions: [['q1', '시간', ['10-12시', '오후']]] }),
    );
    expect(bubble).toBe('예: "오후 응답은 몇 건이야?"');
  });

  it('현재 정의에 없는 문항은 개인정보 여부를 몰라 쓰지 않는다', () => {
    const { bubble } = renderExamples(
      form([grade], { version: 2 }),
      submitted(
        { version: 1, questions: [['q9', '지난 문항', ['가']]] },
        { version: 2, questions: [['q1', '학년', ['1학년']]] },
      ),
    );
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?"');
  });

  it('공백 문구 문항과 공백 선택지는 건너뛴다', () => {
    const blank = question('q0', ' ', 'multiple_choice', ['가']);
    const spaced = question('q1', '학년', 'multiple_choice', [' ', '1학년']);
    const { bubble } = renderExamples(
      form([blank, spaced]),
      submitted({
        questions: [
          ['q0', ' ', ['가']],
          ['q1', '학년', [' ', '1학년']],
        ],
      }),
    );
    expect(bubble).toBe('예: "1학년 응답은 몇 건이야?"');
  });
});

describe('예시를 만들 수 없으면 대체 문구를 쓴다', () => {
  const fallback = '예: "전체 응답 몇 건이야?"';

  it('폼 정의를 불러오지 못했으면(definition null)', () => {
    const { bubble, placeholder } = renderExamples(null, gradeResults);
    expect(bubble).toBe(fallback);
    expect(placeholder).toBe('예: 전체 응답 몇 건이야?');
  });

  it('첫 배포 전이면(definition_version null)', () => {
    const { bubble } = renderExamples(
      form([grade, motive], { summary: true, version: null }),
      gradeResults,
    );
    expect(bubble).toBe(fallback);
  });

  it('선택형 문항에 선택지가 없으면', () => {
    const empty = question('q1', '학년', 'multiple_choice', []);
    const { bubble } = renderExamples(
      form([empty]),
      submitted({ questions: [['q1', '학년', []]] }),
    );
    expect(bubble).toBe(fallback);
  });

  it('건수 예시가 없어도 요약 예시는 그 뒤에 붙는다', () => {
    const { bubble, placeholder } = renderExamples(
      form([motive], { summary: true }),
      submitted({ questions: [] }),
    );
    expect(bubble).toBe('예: "전체 응답 몇 건이야?", "지원 동기 요약해줘"');
    expect(placeholder).toBe('예: 전체 응답 몇 건이야?');
  });
});

describe('묻기 밑 버튼(presetGroups)의 결과는 그대로다', () => {
  it('요약·선택지 버튼을 예전 기준대로 만든다', () => {
    const dept = question('q0', '학과', 'dropdown', ['기타'], { profile_key: 'department' });
    const attend = question('q1', '참석하나요?', 'multiple_choice', ['예', '응답', '보류']);
    const name = question('q3', '이름', 'short_answer', [], { input_format: { kind: 'name' } });
    const groups = presetGroups(
      form([dept, attend, motive, name], { summary: true }),
      submitted({
        questions: [
          ['q0', '학과', ['기타']],
          ['q1', '참석하나요?', ['예', '응답', '보류']],
        ],
      }),
    );
    expect(groups.map((g) => [g.title, g.kind, g.choices.map((c) => c.label)])).toEqual([
      ['', 'main', ['전체 응답 목록', '전체 요약']],
      ['문항 요약', 'main', ['지원 동기 요약']],
      ['학과 선택지별 응답 수', 'option', ['기타 응답 수']],
      ['참석하나요? 선택지별 응답 수', 'option', ['예 응답 수', '보류 응답 수']],
    ]);
    expect(groups[1].choices[0].context).toEqual({
      conditions: [],
      operation: 'summarize',
      target: '지원 동기',
    });
    expect(groups[3].choices[0].context).toEqual({
      conditions: ['참석하나요? - 예'],
      operation: 'count',
    });
  });
});
