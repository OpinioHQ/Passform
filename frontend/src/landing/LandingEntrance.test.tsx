// @vitest-environment jsdom
import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LandingPage } from '../pages/LandingPage';

const noop = () => undefined;

function renderLanding() {
  const view = render(<LandingPage onStart={noop} onCreate={noop} onExplore={noop} />);
  const grid = view.container.querySelector('.landing-feature-grid') as HTMLElement;
  return { ...view, grid };
}

/** 이 파일 안에서만 쓰는 IntersectionObserver 흉내. 콜백을 직접 불러 화면 진입을 만든다. */
function stubObserver() {
  const callbacks: IntersectionObserverCallback[] = [];
  const disconnect = vi.fn();
  vi.stubGlobal(
    'IntersectionObserver',
    vi.fn((callback: IntersectionObserverCallback) => {
      callbacks.push(callback);
      return { observe: vi.fn(), unobserve: vi.fn(), disconnect, takeRecords: () => [] };
    }),
  );
  const enter = () =>
    act(() => {
      callbacks.forEach((callback) =>
        callback(
          [{ isIntersecting: true, intersectionRatio: 1 } as IntersectionObserverEntry],
          {} as IntersectionObserver,
        ),
      );
    });
  return { enter, disconnect };
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('홈 등장 효과', () => {
  it('제목을 줄별로 감싸도 h1 글자는 그대로이고, 왼쪽 글자는 정해진 순서로 떠오른다', () => {
    const { container } = renderLanding();
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(
      '폼의 시작부터,마지막 답변까지.',
    );
    const order = [...container.querySelectorAll('.landing-hero-copy .landing-rise')].map(
      (element) =>
        [...element.classList].find((name) => /^landing-rise-\d$/.test(name)) ?? 'landing-rise-0',
    );
    expect(order).toEqual([
      'landing-rise-0',
      'landing-rise-1',
      'landing-rise-2',
      'landing-rise-3',
      'landing-rise-4',
    ]);
  });

  it('IntersectionObserver가 없으면 기능 카드에 등장 효과 없이 처음부터 보인다', () => {
    expect(typeof globalThis.IntersectionObserver).toBe('undefined');
    const { grid } = renderLanding();
    expect(grid.classList.contains('landing-reveal')).toBe(false);
    expect(grid.querySelectorAll('.panel')).toHaveLength(3);
  });

  it('기능 카드가 화면 아래에 있으면 들어올 때 한 번만 효과를 붙인다', () => {
    const { enter, disconnect } = stubObserver();
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      top: 5000,
    } as DOMRect);
    const { grid } = renderLanding();
    expect(grid.classList.contains('landing-reveal')).toBe(false);
    enter();
    expect(grid.classList.contains('landing-reveal')).toBe(true);
    expect(grid.classList.contains('landing-reveal-wait')).toBe(false);
    expect(disconnect).toHaveBeenCalled();
  });

  it('열자마자 기능 카드가 화면 안이면 바로 효과를 붙인다', () => {
    stubObserver();
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      top: 0,
    } as DOMRect);
    const { grid } = renderLanding();
    expect(grid.classList.contains('landing-reveal')).toBe(true);
  });
});
