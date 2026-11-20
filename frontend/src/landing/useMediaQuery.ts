import { useCallback, useSyncExternalStore } from 'react';

function hasMatchMedia() {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function';
}

/** 미디어 쿼리가 맞는지. matchMedia가 없는 환경(jsdom 등)에서는 null. 바뀌면 다시 그린다. */
export function useMediaQuery(query: string): boolean | null {
  const subscribe = useCallback(
    (onChange: () => void) => {
      if (!hasMatchMedia()) return () => undefined;
      const list = window.matchMedia(query);
      list.addEventListener('change', onChange);
      return () => list.removeEventListener('change', onChange);
    },
    [query],
  );
  return useSyncExternalStore(subscribe, () =>
    hasMatchMedia() ? window.matchMedia(query).matches : null,
  );
}
