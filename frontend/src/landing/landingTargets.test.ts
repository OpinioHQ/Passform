import { describe, expect, it } from 'vitest';
import { demoForm } from '../demo/data';
import { landingTargets } from './landingTargets';

describe('홈 버튼 목적지', () => {
  it('실제 모드에 예시 폼 slug가 있으면 둘러보기는 예시 폼, 탐색 보기는 내가 만든 폼이다', () => {
    expect(landingTargets(true, 'w61wO7cxg4RUuBsupjq7Aw')).toEqual({
      tour: '/f/w61wO7cxg4RUuBsupjq7Aw',
      explore: '/creator',
    });
  });

  it.each([undefined, '', '   '])(
    '실제 모드에 예시 폼 slug가 없으면(%j) 둘 다 내가 만든 폼이다',
    (slug) => {
      expect(landingTargets(true, slug)).toEqual({ tour: '/creator', explore: '/creator' });
    },
  );

  it.each([undefined, 'w61wO7cxg4RUuBsupjq7Aw'])(
    '가짜 데이터 모드는 slug(%j)와 무관하게 둘 다 데모 폼 탐색 화면이다',
    (slug) => {
      const demoExplore = `/creator/forms/${demoForm.form_id}/explore`;
      expect(landingTargets(false, slug)).toEqual({ tour: demoExplore, explore: demoExplore });
    },
  );
});
