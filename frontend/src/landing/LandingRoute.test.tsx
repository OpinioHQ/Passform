// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { demoForm } from '../demo/data';
import { LANDING_FEATURES_ID } from './landingFeatures';

function CurrentPath() {
  return <output data-testid="path">{useLocation().pathname}</output>;
}

/** 모드를 정해 앱을 홈에서 연다. 서버는 401로만 답한다. */
async function openHome(live: boolean) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', live ? 'true' : 'false');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
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

async function pathAfterClick(live: boolean, name: string) {
  await openHome(live);
  fireEvent.click(screen.getByRole('button', { name }));
  const path = screen.getByTestId('path').textContent;
  cleanup();
  return path;
}

interface ScrollCall {
  element: Element;
  options: boolean | ScrollIntoViewOptions | undefined;
}

/** jsdom에는 scrollIntoView가 없어 이 파일 안에서만 붙인다. 어느 요소에서 어떤 옵션으로 불렸는지 모은다. */
function stubScrollIntoView() {
  const calls: ScrollCall[] = [];
  Element.prototype.scrollIntoView = function scrollIntoView(
    this: Element,
    options?: boolean | ScrollIntoViewOptions,
  ) {
    calls.push({ element: this, options });
  };
  return calls;
}

/** 움직임 줄이기 설정만 흉내 낸다. 다른 미디어 쿼리는 맞지 않는 것으로 답한다. */
function stubReducedMotion(reduce: boolean) {
  vi.stubGlobal(
    'matchMedia',
    vi.fn((query: string) => ({
      matches: reduce && query === '(prefers-reduced-motion: reduce)',
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(() => false),
    })),
  );
}

/** 불린 요소가 id를 단 기능 카드 묶음인지 확인할 수 있게 줄인다. */
function scrolledTargets(calls: ScrollCall[]) {
  return calls.map(({ element }) => ({
    id: element.id,
    featureGrid: element.classList.contains('landing-feature-grid'),
  }));
}

const featureGrid = { id: LANDING_FEATURES_ID, featureGrid: true };

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
  Reflect.deleteProperty(Element.prototype, 'scrollIntoView');
});

describe('홈 버튼이 여는 주소', () => {
  it('실제 모드: 둘러보기는 / 그대로 기능 카드로 스크롤, 탐색 보기는 내가 만든 폼', async () => {
    const calls = stubScrollIntoView();
    expect(await pathAfterClick(true, '서비스 둘러보기')).toBe('/');
    expect(scrolledTargets(calls)).toEqual([featureGrid]);
    expect(await pathAfterClick(true, '답변 탐색 보기')).toBe('/creator');
  });

  it('가짜 데이터 모드: 둘러보기는 / 그대로 기능 카드로 스크롤, 탐색 보기는 데모 폼 탐색 화면', async () => {
    const demoExplore = `/creator/forms/${demoForm.form_id}/explore`;
    const calls = stubScrollIntoView();
    expect(await pathAfterClick(false, '서비스 둘러보기')).toBe('/');
    expect(scrolledTargets(calls)).toEqual([featureGrid]);
    expect(await pathAfterClick(false, '답변 탐색 보기')).toBe(demoExplore);
  });
});

describe('둘러보기 스크롤 방식', () => {
  it.each([
    { live: true, reduce: false, behavior: 'smooth' },
    { live: true, reduce: true, behavior: 'auto' },
    { live: false, reduce: false, behavior: 'smooth' },
    { live: false, reduce: true, behavior: 'auto' },
  ])(
    '실제 모드 $live, 움직임 줄이기 $reduce: 기능 카드로 $behavior 스크롤 한 번',
    async ({ live, reduce, behavior }) => {
      stubReducedMotion(reduce);
      const calls = stubScrollIntoView();
      expect(await pathAfterClick(live, '서비스 둘러보기')).toBe('/');
      expect(scrolledTargets(calls)).toEqual([featureGrid]);
      expect(calls[0]?.options).toEqual({ behavior, block: 'start' });
    },
  );

  it.each([true, false])(
    'scrollIntoView가 없는 환경(실제 모드 %s)에서 눌러도 오류 없이 / 그대로',
    async (live) => {
      expect(typeof Element.prototype.scrollIntoView).toBe('undefined');
      const errors: unknown[] = [];
      const onError = (event: ErrorEvent) => errors.push(event.error);
      window.addEventListener('error', onError);
      try {
        await openHome(live);
        expect(() =>
          fireEvent.click(screen.getByRole('button', { name: '서비스 둘러보기' })),
        ).not.toThrow();
        expect(screen.getByTestId('path').textContent).toBe('/');
      } finally {
        window.removeEventListener('error', onError);
      }
      expect(errors).toEqual([]);
    },
  );
});
