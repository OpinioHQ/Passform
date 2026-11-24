// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { demoForm } from '../demo/data';

function CurrentPath() {
  return <output data-testid="path">{useLocation().pathname}</output>;
}

/** 모드와 예시 폼 slug를 정해 앱을 홈에서 연다. 서버는 401로만 답한다. */
async function openHome(live: boolean, slug: string) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', live ? 'true' : 'false');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  vi.stubEnv('VITE_EXAMPLE_FORM_SLUG', slug);
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.resolve(new Response('{}', { status: 401 }))),
  );
  const { App } = await import('../App');
  render(
    <MemoryRouter initialEntries={['/']}>
      <App />
      <CurrentPath />
    </MemoryRouter>,
  );
}

async function pathAfterClick(live: boolean, slug: string, name: string) {
  await openHome(live, slug);
  fireEvent.click(screen.getByRole('button', { name }));
  const path = screen.getByTestId('path').textContent;
  cleanup();
  return path;
}

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
});

describe('홈 버튼이 여는 주소', () => {
  it('실제 모드 + 예시 폼 slug: 둘러보기는 예시 폼, 탐색 보기는 내가 만든 폼', async () => {
    expect(await pathAfterClick(true, 'w61wO7cxg4RUuBsupjq7Aw', '서비스 둘러보기')).toBe(
      '/f/w61wO7cxg4RUuBsupjq7Aw',
    );
    expect(await pathAfterClick(true, 'w61wO7cxg4RUuBsupjq7Aw', '답변 탐색 보기')).toBe('/creator');
  });

  it('실제 모드 + 빈 slug: 둘 다 내가 만든 폼', async () => {
    expect(await pathAfterClick(true, '', '서비스 둘러보기')).toBe('/creator');
    expect(await pathAfterClick(true, '', '답변 탐색 보기')).toBe('/creator');
  });

  it('가짜 데이터 모드: 둘 다 데모 폼 탐색 화면', async () => {
    const demoExplore = `/creator/forms/${demoForm.form_id}/explore`;
    expect(await pathAfterClick(false, 'w61wO7cxg4RUuBsupjq7Aw', '서비스 둘러보기')).toBe(
      demoExplore,
    );
    expect(await pathAfterClick(false, 'w61wO7cxg4RUuBsupjq7Aw', '답변 탐색 보기')).toBe(
      demoExplore,
    );
  });
});
