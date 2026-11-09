// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { CreatorFormDefinition, FormResults } from '../types/api';
import { ExplorePage } from './ExplorePage';

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
  definition_version: 1,
};

const results: FormResults = { response_count: 0, combined: { questions: [] }, versions: [] };

afterEach(cleanup);

describe('탐색 도우미의 묻기 밑 버튼', () => {
  it('전체 응답 목록 버튼은 묶음 제목 없이 바로 보인다', () => {
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

    expect(screen.getByRole('button', { name: '전체 응답 목록' })).toBeTruthy();
    expect(screen.queryByText('전체')).toBeNull();
  });
});
