import { describe, expect, it } from 'vitest';
import { displayTitle } from './formTitle';

// 빈 제목 폼은 사용자에게 "제목 없는 폼"으로 보인다(사람 결정, #165).
describe('displayTitle', () => {
  it.each([
    ['빈 문자열', ''],
    ['공백만', '   '],
    ['줄바꿈·탭만', '\n\t '],
    ['null', null],
    ['undefined', undefined],
  ])('%s이면 "제목 없는 폼"이다', (_, title) => {
    expect(displayTitle(title)).toBe('제목 없는 폼');
  });

  it('제목이 있으면 원래 값을 그대로 돌려준다', () => {
    expect(displayTitle('행사 참석 신청')).toBe('행사 참석 신청');
    expect(displayTitle(' 앞뒤 공백 ')).toBe(' 앞뒤 공백 ');
  });
});
