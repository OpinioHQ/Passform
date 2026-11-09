// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CreatedPage } from './CreatedPage';

const props = {
  forms: [],
  load: 'ready' as const,
  onCreate: vi.fn(),
  onEdit: vi.fn(),
  onResults: vi.fn(),
};

afterEach(cleanup);

describe('내가 만든 폼의 저장 공간 (AC65)', () => {
  it('사용량과 한도를 500MB 기준으로 보인다', () => {
    render(
      <CreatedPage {...props} storage={{ used_bytes: 120_500_000, limit_bytes: 500_000_000 }} />,
    );
    expect(screen.getByText('120.5MB / 500MB 사용 중')).toBeTruthy();
  });

  it('사용량을 못 읽으면 칸을 숨기고 목록은 그대로다', () => {
    render(<CreatedPage {...props} storage={null} />);
    expect(screen.queryByText('저장 공간')).toBeNull();
    expect(screen.getByText('아직 만든 폼이 없어요. 새 폼을 만들어 보세요.')).toBeTruthy();
  });
});
