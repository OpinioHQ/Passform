// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { demoForm } from '../demo/data';
import type { PublicForm } from '../types/api';
import { ResponsePage } from './ResponsePage';

const form: PublicForm = {
  ...demoForm,
  sections: demoForm.sections.map((section) => ({ ...section, title: '기본 정보' })),
  consent: {
    ...demoForm.consent,
    items: ['이름', '문항별 응답 내용'],
    purpose: '행사 진행',
    retention: '첫째 줄\n둘째 줄',
    refusal_notice: '동의를 거부할 수 있습니다.',
  },
};

function renderPage() {
  render(
    <ResponsePage
      form={form}
      onBack={vi.fn()}
      onSubmit={vi.fn(() => Promise.resolve())}
      saving={false}
    />,
  );
}

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe('응답 화면', () => {
  it('섹션 제목은 폼 정의에서 읽고 가짜 제작자 이름과 섹션 제목을 쓰지 않는다 (AC80)', () => {
    renderPage();
    expect(screen.getByRole('heading', { level: 3, name: '기본 정보' })).toBeTruthy();
    expect(screen.queryByText(/아틀리에/)).toBeNull();
    expect(screen.queryByText('함께 일하는 경험')).toBeNull();
  });

  it('동의 내용은 접혀 있고 버튼으로 펼치고 접는다', () => {
    renderPage();
    const toggle = screen.getByRole('button', { name: '내용 보기' });
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByText(/행사 진행/)).toBeNull();

    fireEvent.click(toggle);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByText(/이름, 문항별 응답 내용/)).toBeTruthy();
    expect(screen.getByText(/행사 진행/)).toBeTruthy();
    expect(screen.getByText(/첫째 줄/).className).toContain('keep-lines');
    expect(screen.getByText('동의를 거부할 수 있습니다.')).toBeTruthy();

    fireEvent.click(toggle);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByText(/행사 진행/)).toBeNull();
  });

  it('공유 링크가 마감이면 마감 안내를, 없으면 찾을 수 없다고 보인다 (AC41)', async () => {
    vi.resetModules();
    vi.stubEnv('VITE_USE_API', 'true');
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (url.endsWith('/f/closed')) {
          return Promise.resolve(
            new Response(JSON.stringify({ error: 'form_closed' }), { status: 409 }),
          );
        }
        if (url.endsWith('/f/missing')) {
          return Promise.resolve(
            new Response(JSON.stringify({ error: 'not_found' }), { status: 404 }),
          );
        }
        return Promise.resolve(new Response('', { status: 401 }));
      }),
    );
    const { App } = await import('../App');
    const open = (path: string) =>
      render(
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>,
      );

    open('/f/closed');
    expect(await screen.findByText('응답이 마감된 폼이에요.')).toBeTruthy();
    cleanup();
    open('/f/missing');
    expect(await screen.findByText('폼을 찾을 수 없어요.')).toBeTruthy();
  });
});
