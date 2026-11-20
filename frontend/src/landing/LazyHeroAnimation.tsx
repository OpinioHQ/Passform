import { lazy, Suspense } from 'react';
import { useMediaQuery } from './useMediaQuery';

/** 그림을 두는 폭. 이보다 좁으면 그림이 버튼 아래로 내려가 어색하므로 넣지 않는다(styles.css와 같은 기준). */
const WIDE = '(min-width: 1024px)';

/** 그림이 오기 전(또는 못 받았을 때) 같은 크기로 잡아 두는 빈 자리. 글자·버튼이 밀리지 않는다. */
function HeroArtPlaceholder() {
  return <div className="landing-art" aria-hidden="true" data-motion="loading" />;
}

// 그림 조각(motion 포함)은 첫 번들에서 빼고, 넓은 화면의 홈에서만 따로 받는다.
const HeroAnimation = lazy(() =>
  import('./HeroAnimation').then(
    (mod) => ({ default: mod.HeroAnimation }),
    () => ({ default: HeroArtPlaceholder }),
  ),
);

export function LazyHeroAnimation() {
  // 폭을 모르면(matchMedia 없음) 넣지 않는다. 좁아지면 내려서 타이머·애니메이션이 정리된다.
  const wide = useMediaQuery(WIDE);
  if (wide !== true) return null;
  return (
    <Suspense fallback={<HeroArtPlaceholder />}>
      <HeroAnimation />
    </Suspense>
  );
}
