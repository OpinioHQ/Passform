// @vitest-environment jsdom
import { act, cleanup, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { HeroAnimation } from './HeroAnimation';
import { LazyHeroAnimation } from './LazyHeroAnimation';

/**
 * 이 파일 안에서만 matchMedia를 흉내 낸다. reduce가 true면 움직임 줄이기 설정, wide는 1024px 이상.
 * 돌려준 resize(wide)로 폭이 바뀐 것처럼 change 알림을 보낸다.
 */
function stubMatchMedia({
  reduce,
  finePointer,
  wide = true,
}: {
  reduce: boolean;
  finePointer: boolean;
  wide?: boolean;
}) {
  let isWide = wide;
  const listeners = new Set<() => void>();
  vi.stubGlobal(
    'matchMedia',
    vi.fn((query: string) => ({
      get matches() {
        if (query.includes('prefers-reduced-motion')) return reduce;
        if (query.includes('min-width')) return isWide;
        return finePointer;
      },
      media: query,
      onchange: null,
      addEventListener: vi.fn((_type: string, listener: () => void) => listeners.add(listener)),
      removeEventListener: vi.fn((_type: string, listener: () => void) =>
        listeners.delete(listener),
      ),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(() => false),
    })),
  );
  return {
    resize(next: boolean) {
      isWide = next;
      act(() => listeners.forEach((listener) => listener()));
    },
  };
}

function renderArt() {
  const view = render(<HeroAnimation />);
  const root = view.container.firstElementChild as HTMLElement;
  return { ...view, root };
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('HeroAnimation', () => {
  it('바깥 틀은 aria-hidden이고 글자·제목·누를 수 있는 요소가 없다', () => {
    stubMatchMedia({ reduce: false, finePointer: true });
    const { root } = renderArt();
    expect(root.getAttribute('aria-hidden')).toBe('true');
    expect(root.textContent).toBe('');
    expect(root.querySelectorAll('text, h1, h2, h3, h4, h5, h6, [role="heading"]')).toHaveLength(0);
    expect(
      root.querySelectorAll('a, button, input, select, textarea, [role="button"], [tabindex]'),
    ).toHaveLength(0);
    expect(root.querySelector('svg')).toBeTruthy();
  });

  it('움직임 줄이기면 움직이지 않는 완성 상태로 그린다', () => {
    stubMatchMedia({ reduce: true, finePointer: true });
    const { root } = renderArt();
    expect(root.dataset.motion).toBe('static');
  });

  it('움직임 줄이기가 아니면 움직이는 상태로 그린다', () => {
    stubMatchMedia({ reduce: false, finePointer: true });
    const { root } = renderArt();
    expect(root.dataset.motion).toBe('animated');
  });

  it('matchMedia가 없는 환경에서도 오류 없이 움직이지 않는 상태로 그린다', () => {
    expect(typeof window.matchMedia).toBe('undefined');
    const { root } = renderArt();
    expect(root.dataset.motion).toBe('static');
    expect(root.textContent).toBe('');
  });

  it('돌던 중 언마운트하고 탭 표시가 바뀌어도 오류가 없다', async () => {
    stubMatchMedia({ reduce: false, finePointer: true });
    const errors = vi.spyOn(console, 'error');
    const { unmount } = renderArt();
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));
    unmount();
    document.dispatchEvent(new Event('visibilitychange'));
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(errors).not.toHaveBeenCalled();
  });

  it('그림 조각이 오기 전에는 같은 자리(글자 없는 빈 틀)를 잡고, 오면 그림으로 바뀐다', async () => {
    stubMatchMedia({ reduce: true, finePointer: true, wide: true });
    const { container } = render(<LazyHeroAnimation />);
    const placeholder = container.firstElementChild as HTMLElement;
    expect(placeholder.className).toBe('landing-art');
    expect(placeholder.getAttribute('aria-hidden')).toBe('true');
    expect(placeholder.textContent).toBe('');
    await waitFor(() => expect(container.querySelector('svg')).toBeTruthy());
    const root = container.firstElementChild as HTMLElement;
    expect(root.className).toBe('landing-art');
    expect(root.dataset.motion).toBe('static');
  });
});

describe('LazyHeroAnimation 폭 기준(1024px)', () => {
  it('1024px 미만이면 그림도 빈 자리도 그리지 않는다', () => {
    stubMatchMedia({ reduce: false, finePointer: true, wide: false });
    const { container } = render(<LazyHeroAnimation />);
    expect(container.innerHTML).toBe('');
  });

  it('1024px 이상이면 빈 자리(또는 그림)를 그린다', () => {
    stubMatchMedia({ reduce: false, finePointer: true, wide: true });
    const { container } = render(<LazyHeroAnimation />);
    expect(container.querySelector('.landing-art')).toBeTruthy();
  });

  it('matchMedia가 없으면 폭을 모르므로 그리지 않는다', () => {
    expect(typeof window.matchMedia).toBe('undefined');
    const { container } = render(<LazyHeroAnimation />);
    expect(container.innerHTML).toBe('');
  });

  it('창을 좁히면 그림을 내리고 다시 넓히면 돌아온다', async () => {
    const media = stubMatchMedia({ reduce: false, finePointer: true, wide: true });
    const errors = vi.spyOn(console, 'error');
    const { container } = render(<LazyHeroAnimation />);
    await waitFor(() => expect(container.querySelector('svg')).toBeTruthy());
    media.resize(false);
    expect(container.innerHTML).toBe('');
    media.resize(true);
    expect(container.querySelector('.landing-art')).toBeTruthy();
    await waitFor(() => expect(container.querySelector('svg')).toBeTruthy());
    expect(errors).not.toHaveBeenCalled();
  });
});
