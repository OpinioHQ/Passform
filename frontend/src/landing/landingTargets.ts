import { demoForm } from '../demo/data';
import { paths } from '../routes/paths';

/** 홈의 "서비스 둘러보기"(tour)와 "답변 탐색 보기"(explore)가 여는 주소. */
export interface LandingTargets {
  tour: string;
  explore: string;
}

/**
 * 실제 API 모드: 둘러보기는 예시 폼 응답 화면, 예시 폼 slug가 비어 있으면 내가 만든 폼. 탐색 보기는 내가 만든 폼.
 * 가짜 데이터 모드: 둘 다 데모 폼의 탐색 화면.
 */
export function landingTargets(live: boolean, exampleFormSlug: string | undefined): LandingTargets {
  if (!live) {
    const demoExplore = paths.formExplore(demoForm.form_id);
    return { tour: demoExplore, explore: demoExplore };
  }
  const slug = exampleFormSlug?.trim() ?? '';
  return {
    tour: slug === '' ? paths.creator : paths.publicForm(slug),
    explore: paths.creator,
  };
}
