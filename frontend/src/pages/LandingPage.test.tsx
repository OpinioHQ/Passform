// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { LandingPage } from './LandingPage';

afterEach(() => {
  cleanup();
});

describe('LandingPage', () => {
  it('하단의 개인정보처리방침 링크는 정적 파일 /privacy.html을 가리킨다', () => {
    const noop = () => undefined;
    render(<LandingPage onStart={noop} onCreate={noop} onExplore={noop} />);
    expect(screen.getByRole('link', { name: '개인정보처리방침' }).getAttribute('href')).toBe(
      '/privacy.html',
    );
  });
});
