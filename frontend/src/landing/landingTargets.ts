import { demoForm } from '../demo/data';
import { paths } from '../routes/paths';

/** 홈의 "답변 탐색 보기"(explore)가 여는 주소. "서비스 둘러보기"는 주소를 바꾸지 않고 기능 카드로 스크롤한다. */
export interface LandingTargets {
  explore: string;
}

/**
 * 실제 API 모드: 탐색 보기는 내가 만든 폼.
 * 가짜 데이터 모드: 데모 폼의 탐색 화면.
 */
export function landingTargets(live: boolean): LandingTargets {
  if (!live) return { explore: paths.formExplore(demoForm.form_id) };
  return { explore: paths.creator };
}
