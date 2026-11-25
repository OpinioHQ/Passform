// @vitest-environment jsdom
/**
 * 비로그인 응답자의 입력 전 저장 안내(SPEC 5절 212행, AC82). `VITE_USE_API=true`에서 공유 링크 라우트로 열고
 * 서버는 fetch 모의로 대신한다.
 */
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PublicForm } from '../types/api';

const form: PublicForm = {
  form_id: 'form-1',
  definition_version: 1,
  title: '동아리 신청',
  note: '',
  note_attachments: [],
  deadline: null,
  display: { default_mode: 'all_questions', allowed_modes: ['one_question', 'all_questions'] },
  payment_link: null,
  consent: {
    items: ['문항별 응답 내용'],
    purpose: '신청 확인',
    retention: '학기 종료 후 삭제',
    refusal_notice: '동의하지 않으면 제출할 수 없습니다.',
    ai_summary: null,
  },
  sections: [
    {
      section_id: 's1',
      title: '신청',
      questions: [
        {
          question_id: 'q1',
          template_id: null,
          question_note: '하고 싶은 말',
          question_type: 'short_answer',
          options: [],
          required: false,
          profile_key: null,
          attachments: [],
          branch_rules: [],
        },
      ],
    },
  ],
};

beforeEach(() => {
  window.scrollTo = vi.fn();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
});

describe('비로그인 입력 전 저장 안내', () => {
  it('답을 입력하기 전 상태 줄은 이 브라우저에만 저장된다는 한 문장이다', async () => {
    vi.resetModules();
    vi.stubEnv('VITE_USE_API', 'true');
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        const path = url.replace('http://api.test', '');
        const reply =
          path === '/me'
            ? { status: 401, body: { error: 'unauthorized' } }
            : path === '/f/form-1'
              ? { status: 200, body: form }
              : { status: 404, body: { error: 'not_found' } };
        return Promise.resolve(new Response(JSON.stringify(reply.body), { status: reply.status }));
      }),
    );
    const { App } = await import('../App');
    render(
      <MemoryRouter initialEntries={['/f/form-1']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByLabelText('하고 싶은 말');
    expect(screen.getByTestId('autosave-status').textContent).toBe(
      '답은 이 브라우저에만 저장돼요.',
    );
  });
});
