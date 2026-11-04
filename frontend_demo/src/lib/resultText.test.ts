import { describe, expect, it } from 'vitest';

import type { AnalysisResponse, Coverage, Matched } from '../api/types';
import { countCaption, evidenceLines, interpretedConditions, resultNotes } from './resultText';

const match = (condition: string, version: number, note: string, option: string): Matched => ({
  condition,
  form_version: version,
  question_id: 'q9',
  question_note: note,
  kind: 'option',
  option,
});

const response = (coverage: Partial<Coverage>, matched: Matched[], excluded = 0): AnalysisResponse => ({
  status: 'ok',
  context: { conditions: matched.map((entry) => entry.condition), operation: 'count' },
  matched,
  coverage: { total_submitted_count: 39, not_evaluable_count: 0, not_evaluable_by_version: [], ...coverage },
  result: {
    operation: 'count',
    count: 6,
    response_ids: null,
    summary: { status: 'ok', excluded_no_consent_count: excluded, items: [], topic: null },
  },
  clarify: null,
});

const INTERNAL_TERMS = /판정 불가|\bq\d+\b|\bv\d+\b/;

describe('resultNotes', () => {
  it('문항이 없던 버전을 내부 용어 없이 설명한다', () => {
    const notes = resultNotes(response(
      { not_evaluable_count: 22, not_evaluable_by_version: [{ form_version: 1, response_count: 22, unavailable_conditions: ['주말에 활동하고 싶은'] }] },
      [match('주말에 활동하고 싶은', 2, '희망 활동 시간', '주말')],
    ));
    expect(notes).toEqual(['폼 버전 1의 응답 22건은 "희망 활동 시간" 문항이 없던 때 받은 것이라 세지 않았어요.']);
    expect(notes.join(' ')).not.toMatch(INTERNAL_TERMS);
  });

  it('분기로 건너뛴 응답은 다른 이유로 설명한다', () => {
    const notes = resultNotes(response(
      { not_evaluable_count: 7, not_evaluable_by_version: [{ form_version: 1, response_count: 7, unavailable_conditions: ['팀장'] }] },
      [match('팀장', 1, '맡은 역할', '팀장')],
    ));
    expect(notes[0]).toContain('건너뛰어');
    expect(notes[0]).not.toMatch(INTERNAL_TERMS);
  });

  it('두 이유가 섞이면 함께 적는다', () => {
    const notes = resultNotes(response(
      { not_evaluable_count: 3, not_evaluable_by_version: [{ form_version: 1, response_count: 3, unavailable_conditions: ['팀장', '주말'] }] },
      [match('팀장', 1, '맡은 역할', '팀장'), match('주말', 2, '희망 활동 시간', '주말')],
    ));
    expect(notes[0]).toContain('없거나 건너뛰어');
  });

  it('정렬·요약 제외와 미동의도 설명에 넣는다', () => {
    const notes = resultNotes(response(
      { sort_unavailable_by_version: [3], summary_unavailable_by_version: [{ form_version: 2, response_count: 16 }] },
      [],
      8,
    ));
    expect(notes).toHaveLength(3);
    expect(notes.join(' ')).toContain('동의하지 않은 8건');
  });

  it('설명할 것이 없으면 비어 있다', () => {
    expect(resultNotes(response({}, []))).toEqual([]);
  });
});

describe('countCaption', () => {
  it('일부만 확인했으면 설명을 열지 않아도 드러난다', () => {
    const caption = countCaption(response({ not_evaluable_count: 22 }, [match('주말', 2, '희망 활동 시간', '주말')]));
    expect(caption).toBe('전체 39건 중 확인할 수 있는 17건에서 찾았어요');
    expect(caption).not.toMatch(INTERNAL_TERMS);
  });

  it('조건 유무에 따라 문구가 다르다', () => {
    expect(countCaption(response({}, []))).toBe('받은 응답 전체예요');
    expect(countCaption(response({}, [match('경영학과', 1, '소속 학과', '경영학과')]))).toContain('조건에 맞는');
  });
});

describe('근거 표시', () => {
  const matched = [match('컴공', 1, '소속 학과', '컴퓨터공학과'), match('컴공', 2, '소속 학과', '컴퓨터공학과')];

  it('원문 표현 대신 연결된 문항과 선택지를 보여 주고 버전을 합친다', () => {
    expect(interpretedConditions(['컴공'], matched)).toEqual(['소속 학과: 컴퓨터공학과']);
    expect(evidenceLines(matched)).toEqual(['"컴공" → 소속 학과: 컴퓨터공학과 (폼 버전 1·2)']);
  });

  it('문항 ID를 보여 주지 않는다', () => {
    expect(evidenceLines(matched).join(' ')).not.toMatch(INTERNAL_TERMS);
  });

  it('답함·비움을 문장으로 쓴다', () => {
    const unanswered: Matched = { ...matched[0]!, condition: '역할 미응답', kind: 'unanswered', option: null, question_note: '맡은 역할' };
    expect(interpretedConditions(['역할 미응답'], [unanswered])).toEqual(['맡은 역할에 답하지 않음']);
    expect(interpretedConditions(['연결 안 됨'], [])).toEqual(['연결 안 됨']);
  });
});
