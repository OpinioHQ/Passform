// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AnalyzeOkResponse, AnalyzeRequest, AnalyzeResponse } from '../types/analyze';
import type { CreatorFormDefinition, ResponseDetail } from '../types/api';
import { ExplorePage } from './ExplorePage';

// 분석 API 예시(docs/api/analyze_examples.json)를 그대로 서버 응답으로 쓴다.
const files = import.meta.glob<Record<string, { response: unknown }>>(
  '../../../docs/api/analyze_examples.json',
  { eager: true, import: 'default' },
);
const examples = Object.values(files)[0];
const example = <T extends AnalyzeResponse = AnalyzeResponse>(name: string): T =>
  structuredClone(examples[name].response) as T;

const definition: CreatorFormDefinition = {
  title: '동아리 모집',
  note: '',
  settings: {
    editable: true,
    cancellable: true,
    display: { default_mode: 'all_questions' },
    ai_summary: { enabled: true },
  },
  payment_link: null,
  consent_template_id: 'consent_basic',
  note_attachments: [],
  sections: [],
  status: 'open',
  share_url: null,
  definition_version: 2,
};

afterEach(cleanup);

function renderPage(responses: AnalyzeResponse[], details?: (id: string) => ResponseDetail) {
  const analyze = vi.fn<(body: AnalyzeRequest) => Promise<AnalyzeResponse>>();
  for (const r of responses) analyze.mockResolvedValueOnce(r);
  const loadResponse = vi.fn((id: string) =>
    details ? Promise.resolve(details(id)) : Promise.reject(new Error('부르지 않는다')),
  );
  render(
    <ExplorePage
      definition={definition}
      analyze={analyze}
      loadResponse={loadResponse}
      onBuilder={() => {}}
      dashboard={<p>대시보드</p>}
    />,
  );
  return { analyze, loadResponse };
}

function ask(text: string) {
  fireEvent.change(screen.getByLabelText('응답에 대해 묻기'), { target: { value: text } });
  fireEvent.click(screen.getByRole('button', { name: '질문 보내기' }));
}

describe('결과', () => {
  it('count: 서버가 센 건수와 "조건 → 문항 문구: 선택지, 폼 버전" 연결 결과를 보인다', async () => {
    const { analyze } = renderPage([example('ok_count')]);
    ask('소프트웨어학과 4학년 몇 명이야?');
    expect(await screen.findByText('4')).toBeTruthy();
    expect(analyze).toHaveBeenCalledWith({ query: '소프트웨어학과 4학년 몇 명이야?' });
    expect(
      screen.getByText('소프트웨어학과 → 소속 학과: 소프트웨어학과, 폼 버전 1·2'),
    ).toBeTruthy();
    expect(screen.getByText('4학년 → 학년: 4학년, 폼 버전 1·2')).toBeTruthy();
  });

  it('list: result.response_ids 순서 그대로 "응답 N"과 문항 문구·답을 보인다', async () => {
    const response = example<AnalyzeOkResponse>('ok_list_sorted');
    const ids = response.result.response_ids ?? [];
    const answerOf = (id: string) => `답-${ids.indexOf(id) * 7}`;
    const { loadResponse } = renderPage([response], (id) => ({
      response_id: id,
      form_version: 1,
      submitted_at: '2026-11-01T00:00:00Z',
      updated_at: null,
      content: [{ question_id: 'q5', question_note: '나이', value: answerOf(id) }],
      attachments: [],
    }));
    ask('소프트웨어학과 나이 많은 순으로 보여줘');
    await waitFor(() => expect(screen.getAllByText('나이')).toHaveLength(ids.length));
    expect(loadResponse.mock.calls.map((call) => call[0])).toEqual(ids);
    const items = screen
      .getAllByRole('listitem')
      .filter((li) => li.className === 'explore-response');
    expect(items.map((li) => within(li).getByText(/^응답 \d+$/).textContent)).toEqual(
      ids.map((_, i) => `응답 ${i + 1}`),
    );
    expect(items.map((li) => li.querySelector('dd')?.textContent)).toEqual(ids.map(answerOf));
  });

  it('summarize: 주제 라벨·개수·요약문·근거 발췌와 미동의 제외 수를 보인다', async () => {
    renderPage([example('ok_summarize_target')]);
    ask('지원 동기 요약해줘');
    expect(await screen.findByText('이력서용 프로젝트')).toBeTruthy();
    expect(screen.getByText('AI 요약에 동의하지 않은 8건은 요약에서 뺐어요.')).toBeTruthy();
    const theme = screen.getByText('이력서용 프로젝트').closest('li') as HTMLElement;
    expect(within(theme).getByText('5건')).toBeTruthy();
    expect(
      within(theme).getByText(/졸업 전에 이력서에 남길 만한 프로젝트를 완성하고 싶다는/),
    ).toBeTruthy();
    expect(
      within(theme).getAllByText('졸업 전에 이력서에 남길 만한 프로젝트를 하나 완성하고 싶습니다.'),
    ).toHaveLength(3);
  });

  it('summarize 특정 의견: 요약문과 근거만 보인다', async () => {
    renderPage([example('ok_summarize_topic')]);
    ask('비용이 부담된다는 의견 요약해줘');
    expect(await screen.findByText("'비용이 부담된다'에 대한 의견")).toBeTruthy();
    expect(screen.getByText('회비와 서버 비용이 부담될 수 있다는 우려가 있습니다.')).toBeTruthy();
    expect(screen.getAllByText('회비와 서버 비용이 부담될까 걱정됩니다.')).toHaveLength(3);
  });

  it.each([
    ['ok_summary_no_matches', '조건에 맞는 응답이 없어 요약할 내용이 없어요.'],
    [
      'ok_summary_no_consented_responses',
      '조건에 맞는 응답 2건 모두 AI 요약에 동의하지 않아 요약하지 않았어요.',
    ],
    [
      'ok_summary_insufficient_evidence',
      "'우주여행' 관련 답을 충분히 찾지 못해 요약하지 않았어요.",
    ],
    [
      'ok_summary_too_many_responses',
      '요약할 응답이 너무 많아요. 조건을 더해 범위를 좁혀 다시 물어봐 주세요.',
    ],
  ])('summary.status %s 를 안내한다', async (name, message) => {
    renderPage([example(name)]);
    ask('요약해줘');
    expect(await screen.findByText(message)).toBeTruthy();
  });
});

describe('일부만 확인', () => {
  it('사실은 항상 보이고, 분기로 건너뛴 이유는 물음표를 눌러야 보인다', async () => {
    renderPage([example('ok_count_branch_skipped')]);
    ask('팀장 아닌 사람 몇 명?');
    expect(
      await screen.findByText('전체 38건 중 9건은 확인할 수 없어 빼고 찾았어요.'),
    ).toBeTruthy();
    expect(screen.queryByText(/분기로 건너뛰었어요/)).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '이유 보기' }));
    expect(screen.getByText("폼 버전 1 응답 6건: '팀장' 문항을 분기로 건너뛰었어요.")).toBeTruthy();
    expect(screen.getByText("폼 버전 2 응답 3건: '팀장' 문항을 분기로 건너뛰었어요.")).toBeTruthy();
  });

  it('그 버전에 연결된 문항이 없으면 문항이 없던 버전으로 알린다', async () => {
    renderPage([example('ok_count_not_evaluable')]);
    ask('주말 가능한 사람 몇 명?');
    expect(
      await screen.findByText('전체 38건 중 22건은 확인할 수 없어 빼고 찾았어요.'),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '이유 보기' }));
    expect(
      screen.getByText("폼 버전 1 응답 22건: '주말'에 맞는 문항이 그 버전에 없어요."),
    ).toBeTruthy();
  });
});

describe('되묻기', () => {
  it('missing_match: 고른 match를 넣은 {context}로 다시 보낸다', async () => {
    const clarify = example('clarify_5_missing_match');
    const { analyze } = renderPage([clarify, example('ok_count')]);
    ask('소프트웨어학과 4학년 몇 명?');
    fireEvent.click(await screen.findByRole('button', { name: '모두 만족하는 응답' }));
    expect(await screen.findByText('4')).toBeTruthy();
    expect(analyze).toHaveBeenLastCalledWith({
      context: { conditions: ['소프트웨어학과', '4학년'], operation: 'count', match: 'all' },
    });
  });

  it('ambiguous_match 후보 문항: 그 조건을 "문항 문구 - 값"으로 바꿔 보낸다', async () => {
    const { analyze } = renderPage([example('clarify_7_ambiguous_question'), example('ok_count')]);
    ask('경영학과 몇 명?');
    expect(await screen.findByRole('button', { name: '희망 학과 (폼 버전 3)' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '소속 학과 (폼 버전 1·2)' }));
    await waitFor(() => expect(analyze).toHaveBeenCalledTimes(2));
    expect(analyze).toHaveBeenLastCalledWith({
      context: { conditions: ['소속 학과 - 경영학과'], operation: 'count' },
    });
  });

  it('double_negation: 확정 문장을 고르면 match를 지워 보낸다', async () => {
    const { analyze } = renderPage([example('clarify_8_double_negation'), example('ok_count')]);
    ask('8번 미응답 아닌 사람');
    fireEvent.click(await screen.findByRole('button', { name: '8번 문항 - 미응답 응답' }));
    await waitFor(() => expect(analyze).toHaveBeenCalledTimes(2));
    expect(analyze).toHaveBeenLastCalledWith({
      context: { conditions: ['8번 문항 - 미응답'], operation: 'count' },
    });
  });
});

describe('화면 글자', () => {
  const internal = [
    /\bq\d+/,
    /\bv\d+/,
    /\br[a-z]?\d{2,}/,
    /판정 불가/,
    /response_id|question_id|not_evaluable/,
    /unsupported_scope|parse_failed|missing_operation|summary_disabled|missing_match/,
    /unknown_condition|ambiguous_match|double_negation|unsortable_field|unsummarizable_field/,
  ];

  it.each([
    'ok_count_branch_skipped',
    'ok_count_not_evaluable',
    'ok_list_sort_unavailable',
    'ok_summarize_partial_version',
    'clarify_1_unsupported_scope',
    'clarify_6_unknown_condition',
    'clarify_7_ambiguous_question',
    'clarify_9_unsortable_field',
  ])('%s 에 내부 용어가 없다', async (name) => {
    renderPage([example(name)], (id) => ({
      response_id: id,
      form_version: 3,
      submitted_at: '2026-11-01T00:00:00Z',
      updated_at: null,
      content: [{ question_id: 'q1', question_note: '학과', value: '경영학과' }],
      attachments: [],
    }));
    ask('질문');
    await screen.findByText(/질문/, { selector: '.chat-bubble' });
    await waitFor(() => expect(screen.queryByText('분석하는 중이에요.')).toBeNull());
    const why = screen.queryByRole('button', { name: '이유 보기' });
    if (why) fireEvent.click(why);
    await waitFor(() => expect(screen.queryByText('불러오는 중이에요.')).toBeNull());
    const text = document.body.textContent ?? '';
    for (const pattern of internal) expect(text).not.toMatch(pattern);
  });

  it('근거 발췌의 <script> 같은 글자는 HTML이 아니라 글자로 보인다', async () => {
    const response = example<AnalyzeOkResponse>('ok_summarize_target');
    const summary = response.result.summary;
    if (summary?.items[0]?.type !== 'themes') throw new Error('예시가 바뀌었다');
    summary.items[0].themes[0].evidence[0].excerpt = '<script>alert(1)</script><b>굵게</b>';
    renderPage([response]);
    ask('지원 동기 요약해줘');
    expect(await screen.findByText('<script>alert(1)</script><b>굵게</b>')).toBeTruthy();
    expect(document.querySelector('.explore-evidence script')).toBeNull();
    expect(document.querySelector('.explore-evidence b')).toBeNull();
  });
});
