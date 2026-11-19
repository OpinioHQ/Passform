import { lazy, Suspense } from 'react';

/** 그림이 오기 전(또는 못 받았을 때) 같은 크기로 잡아 두는 빈 자리. 글자·버튼이 밀리지 않는다. */
function HeroArtPlaceholder() {
  return <div className="landing-art" aria-hidden="true" data-motion="loading" />;
}

// 그림 조각(motion 포함)은 첫 번들에서 빼고 홈에 들어올 때만 따로 받는다.
const HeroAnimation = lazy(() =>
  import('./HeroAnimation').then(
    (mod) => ({ default: mod.HeroAnimation }),
    () => ({ default: HeroArtPlaceholder }),
  ),
);

export function LazyHeroAnimation() {
  return (
    <Suspense fallback={<HeroArtPlaceholder />}>
      <HeroAnimation />
    </Suspense>
  );
}
