// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppLayout } from './AppLayout';

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
});

describe('공통 바닥', () => {
  it('개발용 문구 없이 저작권 표시와 서비스 소개 버튼만 보인다', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/" element={<div />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    const footer = screen.getByRole('contentinfo');
    expect(footer.textContent).not.toContain('스캐폴드');
    expect(within(footer).getByText('© 2026 Passform')).toBeTruthy();
    expect(within(footer).getByRole('button', { name: '서비스 소개' })).toBeTruthy();
  });
});
