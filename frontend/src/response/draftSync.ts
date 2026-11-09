/**
 * 응답 초안의 저장 위치와 자동 저장(T5 #53, SPEC 5절 205~208행, AC82·AC83).
 * - 로그인: 계정에만 저장한다(`PUT /me/forms/{form_id}/response-draft`). 브라우저에는 두지 않는다(사람 결정).
 * - 비로그인: 이 브라우저의 localStorage에만 저장한다. 키는 폼마다 다르고 기한은 없다(사람 결정).
 * - 자동 저장은 마지막 입력 1초 뒤에 한 번 보낸다(사람 결정). 브라우저 저장은 바로 쓴다.
 * 초안 저장은 동의나 제출이 아니다. 제출 검증도 하지 않는다.
 */
import React from 'react';
import { api } from '../api/client';
import type { AnswerValue, PublicForm, ResponseAnswer } from '../types/api';
import type { AnswerMap } from './responsePath';

export const AUTOSAVE_DELAY_MS = 1000;

/** 브라우저 초안. 작성 시작 버전의 정의를 함께 둬 새 버전이 나와도 옛 문항으로 보여 줄 수 있다. */
export interface BrowserDraft {
  form_version: number;
  form_definition: PublicForm;
  answers: ResponseAnswer[];
  saved_at: string;
  /** 저장할 때의 화면 주소. 공유 링크가 404가 되면 어느 초안을 지울지 이 값으로 찾는다. */
  path?: string;
}

/** 폼마다 다른 키다. 다른 폼의 초안과 섞이지 않는다. */
export function browserDraftKey(formId: string): string {
  return `passform:draft:${formId}`;
}

function storage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage;
  } catch {
    return null;
  }
}

function isAnswerList(value: unknown): value is ResponseAnswer[] {
  return (
    Array.isArray(value) &&
    value.every(
      (item) =>
        typeof item === 'object' &&
        item !== null &&
        typeof (item as ResponseAnswer).question_id === 'string',
    )
  );
}

export function readBrowserDraft(formId: string): BrowserDraft | null {
  const raw = storage()?.getItem(browserDraftKey(formId));
  if (!raw) return null;
  try {
    const draft = JSON.parse(raw) as Partial<BrowserDraft>;
    if (
      typeof draft.form_version !== 'number' ||
      !draft.form_definition ||
      !isAnswerList(draft.answers)
    ) {
      return null;
    }
    return draft as BrowserDraft;
  } catch {
    return null;
  }
}

export function removeBrowserDraft(formId: string): void {
  storage()?.removeItem(browserDraftKey(formId));
}

const DRAFT_PREFIX = 'passform:draft:';

/**
 * 폼이 삭제된 것을 확인했을 때 그 폼의 브라우저 초안을 지운다(SPEC 5절 208행). 지운 폼 ID를 돌려준다.
 * 슬러그 주소에서는 폼 ID를 모르므로 저장할 때 남긴 주소로 찾는다.
 */
export function removeBrowserDraftsAt(path: string): string[] {
  const store = storage();
  if (!store) return [];
  const removed: string[] = [];
  for (const key of Object.keys(store)) {
    if (!key.startsWith(DRAFT_PREFIX)) continue;
    const formId = key.slice(DRAFT_PREFIX.length);
    if (readBrowserDraft(formId)?.path === path) {
      store.removeItem(key);
      removed.push(formId);
    }
  }
  return removed;
}

/** 화면의 답을 저장할 목록으로 바꾼다. 아직 아무것도 고르거나 쓰지 않은 문항은 넣지 않는다. */
export function answersToSave(answers: AnswerMap): ResponseAnswer[] {
  return Object.entries(answers)
    .filter((entry): entry is [string, AnswerValue] => {
      const value = entry[1];
      return value !== undefined && value !== '' && !(Array.isArray(value) && value.length === 0);
    })
    .map(([question_id, value]) => ({ question_id, value }));
}

export function answersToMap(answers: ResponseAnswer[]): AnswerMap {
  return Object.fromEntries(answers.map((a) => [a.question_id, a.value]));
}

/** 초안을 쓰는 곳. 화면은 이 인터페이스만 보고 계정·브라우저를 구분하지 않는다. */
export interface DraftSink {
  kind: 'account' | 'browser';
  /** 실패하면 reject한다. 화면은 성공한 것처럼 보이지 않는다. */
  save(answers: ResponseAnswer[]): Promise<void>;
  clear(): Promise<void>;
  /** 계정 초안의 ID. 제출 때 `draft_response_id`로 보낸다. 브라우저 초안은 `null`이다. */
  draftResponseId(): string | null;
  /** 제출에 성공한 뒤 부른다. 계정은 제출된 ID를 잊고, 브라우저는 남은 초안을 지운다(SPEC 5절 216행). */
  submitted(): void;
}

export function browserSink(form: PublicForm): DraftSink {
  return {
    kind: 'browser',
    save: async (answers) => {
      const store = storage();
      if (!store) throw new Error('이 브라우저에는 저장할 수 없어요');
      const draft: BrowserDraft = {
        form_version: form.definition_version,
        form_definition: form,
        answers,
        saved_at: new Date().toISOString(),
        path: window.location.pathname,
      };
      store.setItem(browserDraftKey(form.form_id), JSON.stringify(draft));
    },
    clear: async () => removeBrowserDraft(form.form_id),
    draftResponseId: () => null,
    submitted: () => removeBrowserDraft(form.form_id),
  };
}

export function accountSink(form: PublicForm, responseId: string | null): DraftSink {
  let id = responseId;
  return {
    kind: 'account',
    save: async (answers) => {
      const saved = await api.saveResponseDraft(form.form_id, {
        form_version: form.definition_version,
        answers,
      });
      id = saved.response_id;
    },
    clear: async () => {
      await api.deleteResponseDraft(form.form_id);
      id = null;
    },
    draftResponseId: () => id,
    submitted: () => {
      id = null;
    },
  };
}

export type AutosaveStatus = 'idle' | 'pending' | 'saving' | 'saved' | 'failed';

export interface Autosave {
  status: AutosaveStatus;
  savedAt: Date | null;
  /** 답이 바뀌었을 때 부른다. 브라우저는 바로, 계정은 1초 뒤에 저장한다. */
  schedule(answers: AnswerMap): void;
  /** 기다리는 저장을 지금 보내고 끝날 때까지 기다린다. 제출 직전에 쓴다. 실패해도 reject하지 않는다. */
  flush(): Promise<void>;
  /**
   * 기다리는 저장을 버리고, 이미 보낸 저장이 끝날 때까지 기다린다. 제출에 성공한 뒤나 초안을 지울 때 쓴다.
   * 초안을 지울 때는 이것을 기다린 뒤 지운다. 늦게 처리된 저장이 지운 초안을 되살리지 않게 하기 위해서다.
   * 실패해도 reject하지 않는다.
   */
  cancel(): Promise<void>;
}

/** 자동 저장 상태. 저장 중이거나 보내지 않은 변경이 있으면 화면을 나갈 때 경고한다(SPEC 5절 205행). */
export function useAutosave(sink: DraftSink | null): Autosave {
  const [status, setStatus] = React.useState<AutosaveStatus>('idle');
  const [savedAt, setSavedAt] = React.useState<Date | null>(null);
  const timer = React.useRef<ReturnType<typeof setTimeout> | null>(null);
  const pending = React.useRef<AnswerMap | null>(null);
  const inFlight = React.useRef<Promise<void> | null>(null);
  const generation = React.useRef(0);

  const run = React.useCallback((): Promise<void> => {
    if (!sink || pending.current === null) return inFlight.current ?? Promise.resolve();
    const answers = answersToSave(pending.current);
    pending.current = null;
    const mine = ++generation.current;
    setStatus('saving');
    const previous = inFlight.current ?? Promise.resolve();
    // 저장은 순서대로 보낸다. 늦게 끝난 옛 요청이 새 답을 덮지 않는다.
    const next = previous
      .then(() => sink.save(answers))
      .then(
        () => {
          if (mine === generation.current) {
            setStatus(pending.current === null ? 'saved' : 'pending');
            setSavedAt(new Date());
          }
        },
        () => {
          // 실패는 성공한 척하지 않는다. 마지막으로 저장된 내용은 저장소에 그대로 있다.
          if (mine === generation.current) setStatus('failed');
        },
      );
    inFlight.current = next;
    return next;
  }, [sink]);

  const schedule = React.useCallback(
    (answers: AnswerMap) => {
      if (!sink) return;
      pending.current = answers;
      if (timer.current) clearTimeout(timer.current);
      if (sink.kind === 'browser') {
        void run();
        return;
      }
      setStatus('pending');
      timer.current = setTimeout(() => {
        timer.current = null;
        void run();
      }, AUTOSAVE_DELAY_MS);
    },
    [sink, run],
  );

  const flush = React.useCallback(async () => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
    await run();
  }, [run]);

  const cancel = React.useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    pending.current = null;
    generation.current++;
    setStatus('idle');
    return inFlight.current ?? Promise.resolve();
  }, []);

  const unsaved = status === 'pending' || status === 'saving';
  // 화면에 "저장됨"이 보이는 시점에는 경고가 이미 풀려 있어야 하므로 화면 반영과 같은 때 해제한다.
  React.useLayoutEffect(() => {
    if (!unsaved) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [unsaved]);

  // 화면을 떠날 때 기다리던 저장을 보낸다. 앱 안의 이동에서도 마지막 답을 잃지 않는다.
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

  return { status, savedAt, schedule, flush, cancel };
}
