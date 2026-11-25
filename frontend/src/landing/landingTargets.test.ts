import { describe, expect, it } from 'vitest';
import { demoForm } from '../demo/data';
import { landingTargets } from './landingTargets';

describe('홈 버튼 목적지', () => {
  it('실제 모드의 탐색 보기는 내가 만든 폼이다', () => {
    expect(landingTargets(true)).toEqual({
      explore: '/creator',
    });
  });

  it('가짜 데이터 모드의 탐색 보기는 데모 폼 탐색 화면이다', () => {
    const demoExplore = `/creator/forms/${demoForm.form_id}/explore`;
    expect(landingTargets(false)).toEqual({ explore: demoExplore });
  });
});
