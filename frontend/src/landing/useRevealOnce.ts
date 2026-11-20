import { useLayoutEffect, useRef, useState } from 'react';

/** 화면에 이만큼(요소 높이 비율) 들어오면 등장 효과를 재생한다. */
const PLAY_RATIO = 0.2;
/** 화면 아래로 이만큼 남았을 때 효과를 멈춘 시작 상태로 걸어 둔다(들어오기 전에 미리). */
const READY_MARGIN = '0px 0px 30% 0px';

/**
 * 요소가 화면에 처음 들어올 때 한 번 등장 효과를 낸다(다시 들어와도 그대로).
 * - idle: 효과 없음(처음부터 보임). IntersectionObserver가 없으면(jsdom 등) 계속 idle.
 * - ready: 들어오기 직전. CSS 애니메이션을 멈춘 시작 상태로 걸어 둔다(그 시작 상태로만 숨김).
 * - play: 20% 들어옴. 멈춰 둔 애니메이션을 재생한다.
 * 자바스크립트가 숨기지 않으므로 애니메이션이 없거나 실패해도 요소는 보인다.
 */
export type RevealStage = 'idle' | 'ready' | 'play';

function visibleRatio(element: Element) {
  const rect = element.getBoundingClientRect();
  if (!(rect.height > 0)) return rect.top < window.innerHeight ? 1 : 0;
  const visible = Math.min(rect.bottom, window.innerHeight) - Math.max(rect.top, 0);
  return Math.max(0, visible) / rect.height;
}

export function useRevealOnce<T extends Element>() {
  const ref = useRef<T>(null);
  const [stage, setStage] = useState<RevealStage>('idle');

  useLayoutEffect(() => {
    const element = ref.current;
    if (!element || typeof IntersectionObserver === 'undefined') return undefined;
    // 열자마자 이미 충분히 보이면 그리기 전에 바로 재생해 한 번 보였다 사라지는 깜빡임을 막는다.
    if (visibleRatio(element) >= PLAY_RATIO) {
      setStage('play');
      return undefined;
    }
    const ready = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setStage((now) => (now === 'idle' ? 'ready' : now));
          ready.disconnect();
        }
      },
      { rootMargin: READY_MARGIN },
    );
    const play = new IntersectionObserver(
      (entries) => {
        if (
          entries.some((entry) => entry.isIntersecting && entry.intersectionRatio >= PLAY_RATIO)
        ) {
          setStage('play');
          ready.disconnect();
          play.disconnect();
        }
      },
      { threshold: PLAY_RATIO },
    );
    ready.observe(element);
    play.observe(element);
    return () => {
      ready.disconnect();
      play.disconnect();
    };
  }, []);

  return { ref, stage };
}
