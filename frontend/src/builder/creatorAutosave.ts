/**
 * 제작자 초안 자동 저장(SPEC 153행, AC84 기본 모드).
 * - 입력이 멈추고 1초 뒤에 현재 폼 정의 전체를 `PATCH /forms/{form_id}`로 보낸다. 응답 초안 자동 저장과 같은 간격이다.
 * - 저장은 한 번에 하나만 보낸다. 앞 저장이 끝나기 전에 바뀐 내용은 그 뒤에 다시 보낸다.
 * - 의미 검증(saveProblems)·배포를 하지 않는다. status를 보내지 않으므로 draft로 남고 definition_version도 그대로다.
 * - 실패하면 저장된 것으로 보이지 않는다. 서버에는 마지막으로 성공한 저장본이 그대로 있다.
 * 응답 쪽 `response/draftSync.ts`의 방식을 따르되, 응답 코드는 건드리지 않으려고 따로 둔다.
 */
import React from 'react';
import { AUTOSAVE_DELAY_MS } from '../response/draftSync';
import type { FormDefinition } from '../types/api';

export type CreatorAutosaveStatus = 'idle' | 'pending' | 'saving' | 'saved' | 'failed';

export interface CreatorAutosave {
  status: CreatorAutosaveStatus;
  /** 편집이 바뀔 때 부른다. 마지막 호출 1초 뒤에 저장한다. */
  schedule(definition: FormDefinition): void;
  /**
   * 기다리는 저장을 버리고, 이미 보낸 저장이 끝날 때까지 기다린다. 명시적 저장·배포·상태 변경 직전에 써서
   * 요청이 겹치지 않게 한다. 실패해도 reject하지 않는다.
   */
  cancel(): Promise<void>;
}

/**
 * `save`가 reject하면 실패다. `onSaved`는 저장이 성공했고 그 뒤에 기다리는 변경이 없을 때만 부른다
 * (화면의 "저장하지 않은 변경" 표시를 지울 때 쓴다).
 */
export function useCreatorAutosave(
  save: (definition: FormDefinition) => Promise<unknown>,
  onSaved: () => void,
): CreatorAutosave {
  const [status, setStatus] = React.useState<CreatorAutosaveStatus>('idle');
  const timer = React.useRef<ReturnType<typeof setTimeout> | null>(null);
  const pending = React.useRef<FormDefinition | null>(null);
  const inFlight = React.useRef<Promise<void> | null>(null);
  const generation = React.useRef(0);

  const run = React.useCallback((): Promise<void> => {
    const definition = pending.current;
    if (definition === null) return inFlight.current ?? Promise.resolve();
    pending.current = null;
    const mine = ++generation.current;
    setStatus('saving');
    const previous = inFlight.current ?? Promise.resolve();
    // 앞 저장이 끝난 뒤에 보낸다. 늦게 끝난 옛 요청이 새 편집을 덮지 않는다.
    const next = previous
      .then(() => save(definition))
      .then(
        () => {
          if (mine !== generation.current) return;
          if (pending.current === null) {
            setStatus('saved');
            onSaved();
          } else {
            setStatus('pending');
          }
        },
        () => {
          // 실패는 성공한 척하지 않는다. 기다리는 새 편집이 있으면 그 저장이 다시 시도한다.
          if (mine === generation.current) setStatus('failed');
        },
      );
    inFlight.current = next;
    return next;
  }, [save, onSaved]);

  const schedule = React.useCallback(
    (definition: FormDefinition) => {
      pending.current = definition;
      if (timer.current) clearTimeout(timer.current);
      setStatus((current) => (current === 'saving' ? current : 'pending'));
      timer.current = setTimeout(() => {
        timer.current = null;
        void run();
      }, AUTOSAVE_DELAY_MS);
    },
    [run],
  );

  const cancel = React.useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    pending.current = null;
    generation.current++;
    setStatus('idle');
    return inFlight.current ?? Promise.resolve();
  }, []);

  // 화면을 떠날 때 기다리던 저장을 보낸다. 앱 안의 이동에서도 마지막 편집을 잃지 않는다.
  React.useEffect(
    () => () => {
      if (timer.current) {
        clearTimeout(timer.current);
        timer.current = null;
        void run();
      }
    },
    [run],
  );

  return { status, schedule, cancel };
}

/** 앱 안에서 다른 화면으로 가는 링크·버튼이다. 빌더 안의 버튼과 내려받기 링크는 이동이 아니다. */
const LEAVING = 'a[href], .global-header .brand, footer button';

/**
 * 저장이 끝나지 않은 채 화면을 나가려 하면 경고한다(SPEC 153행). 브라우저 닫기·새로고침은 beforeunload로,
 * 앱 안 이동(메뉴 링크·로고·바닥글)은 클릭을 가로채 확인을 받는다. 취소하면 이동하지 않는다.
 */
export function useLeaveWarning(
  unsaved: boolean,
  page: React.RefObject<HTMLElement | null>,
  message: string,
) {
  React.useLayoutEffect(() => {
    if (!unsaved) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    // 라우터의 링크 처리보다 먼저 받도록 문서의 캡처 단계에 둔다.
    const guard = (event: MouseEvent) => {
      const target = event.target instanceof Element ? event.target.closest(LEAVING) : null;
      if (
        !target ||
        target.hasAttribute('download') ||
        target.getAttribute('target') === '_blank'
      ) {
        return;
      }
      if (page.current?.contains(target)) return;
      if (!window.confirm(message)) {
        event.preventDefault();
        event.stopPropagation();
      }
    };
    window.addEventListener('beforeunload', warn);
    document.addEventListener('click', guard, true);
    return () => {
      window.removeEventListener('beforeunload', warn);
      document.removeEventListener('click', guard, true);
    };
  }, [unsaved, page, message]);
}
