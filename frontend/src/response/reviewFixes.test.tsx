// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '../api/client';
import { ResponsePage } from '../pages/ResponsePage';
import type { FileToSend, PublicForm } from '../types/api';
import { DraftGate } from './DraftGate';
import { browserDraftKey } from './draftSync';
import * as fileDrafts from './fileDrafts';

// #107 리뷰 1·3·4번(AC82).
const form: PublicForm = {
  form_id: 'f1',
  definition_version: 1,
  title: '가상 폼',
  note: '',
  note_attachments: [],
  deadline: null,
  sections: [
    {
      section_id: 's1',
      title: '기본',
      questions: [
        {
          question_id: 'q1',
          template_id: null,
          question_note: '하고 싶은 말',
          question_type: 'long_answer',
          options: [],
          required: false,
          profile_key: null,
          attachments: [],
          branch_rules: [],
        },
      ],
    },
  ],
  display: { default_mode: 'all_questions', allowed_modes: ['all_questions'] },
  payment_link: null,
  consent: {
    items: ['문항별 응답 내용'],
    purpose: '목적',
    retention: '보유',
    refusal_notice: '안내',
    ai_summary: null,
  },
};

const oldFile = new File(['o'], '보관.pdf', { type: 'application/pdf' });
const newFile = new File(['n'], '새파일.pdf', { type: 'application/pdf' });

beforeEach(() => {
  window.scrollTo = vi.fn();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('로그인 전 파일을 계정으로 옮기기 (리뷰 1번)', () => {
  it('계정 보관함에 저장하지 못하면 로그인 전 파일을 지우지 않고 안내한다', async () => {
    localStorage.setItem(
      browserDraftKey('f1'),
      JSON.stringify({
        form_version: 1,
        form_definition: form,
        answers: [{ question_id: 'q1', value: '로그인 전 답' }],
        saved_at: '2026-11-01T00:00:00Z',
      }),
    );
    vi.spyOn(api, 'me').mockResolvedValue({
      user_id: 'u1',
      name: '가상',
      email: 'virtual@example.com',
      deadline_reminder: false,
    });
    vi.spyOn(api, 'responseDraft').mockRejectedValue(new ApiError(404, {}));
    vi.spyOn(api, 'saveResponseDraft').mockResolvedValue({
      response_id: 'd1',
      status: 'in_progress',
      saved_at: '2026-11-01T00:00:00Z',
    });
    vi.spyOn(fileDrafts, 'loadFileDraft').mockResolvedValue([{ question_id: 'q1', file: oldFile }]);
    vi.spyOn(fileDrafts, 'saveFileDraft').mockRejectedValue(new Error('용량 부족'));
    const remove = vi.spyOn(fileDrafts, 'removeFileDraft').mockResolvedValue(undefined);

    render(<DraftGate form={form} onSubmit={vi.fn()} saving={false} />);
    fireEvent.click(await screen.findByRole('button', { name: '이어서 쓰기' }));

    expect(await screen.findByText(/파일을 계정으로 옮기지 못했어요/)).toBeTruthy();
    expect(remove).not.toHaveBeenCalledWith('anonymous:f1');
  });
});

describe('늦게 끝난 복원 (리뷰 4번)', () => {
  it('복원이 끝나기 전에 고른 파일을 복원 결과로 덮지 않는다', async () => {
    let finishRestore: (files: FileToSend[]) => void = () => undefined;
    vi.spyOn(fileDrafts, 'loadFileDraft').mockReturnValue(
      new Promise((resolve) => {
        finishRestore = resolve;
      }),
    );
    vi.spyOn(fileDrafts, 'saveFileDraft').mockResolvedValue(undefined);
    render(
      <ResponsePage
        form={form}
        onSubmit={vi.fn()}
        saving={false}
        files={{ storageKey: 'u1:f1' }}
      />,
    );

    fireEvent.change(screen.getByLabelText('하고 싶은 말 파일 첨부'), {
      target: { files: [newFile] },
    });
    await screen.findByText(/새파일\.pdf/);
    finishRestore([{ question_id: 'q1', file: oldFile }]);

    await waitFor(() => expect(fileDrafts.loadFileDraft).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.getByText(/새파일\.pdf/)).toBeTruthy();
    expect(screen.queryByText(/보관\.pdf/)).toBeNull();
  });
});

/** IndexedDB의 열기·트랜잭션 순서만 흉내 낸다. `abort`면 요청은 성공하고 트랜잭션만 중단된다. */
function fakeIndexedDb(outcome: 'complete' | 'abort') {
  const transaction: Partial<IDBTransaction> = { error: null };
  const shared = transaction as Partial<IDBTransaction> & { objectStore?: () => unknown };
  const request: Partial<IDBRequest> = { result: undefined };
  const store = { put: () => request, get: () => request, delete: () => request };
  const database = {
    transaction: () => {
      setTimeout(() => {
        request.onsuccess?.call(request as IDBRequest, new Event('success'));
        const handler = outcome === 'complete' ? transaction.oncomplete : transaction.onabort;
        handler?.call(transaction as IDBTransaction, new Event(outcome));
      }, 0);
      shared.objectStore = () => store as unknown as IDBObjectStore;
      return shared as unknown as IDBTransaction;
    },
    close: () => undefined,
    createObjectStore: () => undefined,
  };
  const opening: Partial<IDBOpenDBRequest> = { result: database as unknown as IDBDatabase };
  return {
    open: () => {
      setTimeout(
        () => opening.onsuccess?.call(opening as IDBOpenDBRequest, new Event('success')),
        0,
      );
      return opening as IDBOpenDBRequest;
    },
  } as unknown as IDBFactory;
}

describe('브라우저 보관의 성공 판정 (리뷰 3번)', () => {
  it('요청이 성공해도 트랜잭션이 중단되면 저장 실패다', async () => {
    vi.stubGlobal('indexedDB', fakeIndexedDb('abort'));
    await expect(
      fileDrafts.saveFileDraft('u1:f1', [{ question_id: 'q1', file: newFile }]),
    ).rejects.toThrow();
  });

  it('트랜잭션이 커밋되면 저장 성공이다', async () => {
    vi.stubGlobal('indexedDB', fakeIndexedDb('complete'));
    await expect(
      fileDrafts.saveFileDraft('u1:f1', [{ question_id: 'q1', file: newFile }]),
    ).resolves.toBeUndefined();
  });
});
