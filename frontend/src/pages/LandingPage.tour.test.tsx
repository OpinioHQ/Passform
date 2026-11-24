// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { LandingPage } from './LandingPage';

afterEach(() => {
  cleanup();
});

const noop = () => undefined;
const click = (name: string) => fireEvent.click(screen.getByRole('button', { name }));

describe('홈의 둘러보기·탐색 보기 버튼', () => {
  it('onTour가 있으면 둘러보기는 onTour, 탐색 보기는 onExplore를 부른다', () => {
    const onTour = vi.fn();
    const onExplore = vi.fn();
    render(<LandingPage onStart={noop} onCreate={noop} onExplore={onExplore} onTour={onTour} />);
    click('서비스 둘러보기');
    expect(onTour).toHaveBeenCalledTimes(1);
    expect(onExplore).not.toHaveBeenCalled();
    click('답변 탐색 보기');
    expect(onExplore).toHaveBeenCalledTimes(1);
    expect(onTour).toHaveBeenCalledTimes(1);
  });

  it('onTour가 없으면 두 버튼 모두 onExplore를 부른다', () => {
    const onExplore = vi.fn();
    render(<LandingPage onStart={noop} onCreate={noop} onExplore={onExplore} />);
    click('서비스 둘러보기');
    click('답변 탐색 보기');
    expect(onExplore).toHaveBeenCalledTimes(2);
  });
});
