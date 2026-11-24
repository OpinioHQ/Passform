/** 홈 기능 카드 묶음(01·02·03)의 id. "서비스 둘러보기"가 여기로 스크롤한다. */
export const LANDING_FEATURES_ID = 'landing-features';

function prefersReducedMotion() {
  return (
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/** 홈 기능 카드 묶음으로 스크롤한다. 주소는 바꾸지 않는다. scrollIntoView가 없으면(jsdom 등) 아무것도 하지 않는다. */
export function scrollToLandingFeatures() {
  const target = document.getElementById(LANDING_FEATURES_ID);
  if (!target || typeof target.scrollIntoView !== 'function') return;
  target.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'start' });
}
