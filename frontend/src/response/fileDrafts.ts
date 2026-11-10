/**
 * 제출 전 첨부 파일의 브라우저 보관(SPEC 205행, AC82). 서버에 올리지 않고 사용량에도 세지 않는다.
 * 키는 계정과 폼으로 나눠 다른 계정으로 로그인하면 보이지 않는다.
 * IndexedDB를 쓸 수 없는 환경에서는 보관하지 않고 조용히 넘어간다. 화면의 파일은 그 시간 동안 유지된다.
 */
import type { FileToSend } from '../types/api';

const DB_NAME = 'passform-files';
const STORE = 'drafts';

export const ANONYMOUS_SCOPE = 'anonymous';

export function fileDraftKey(scope: string, formId: string): string {
  return `${scope}:${formId}`;
}

function openDatabase(): Promise<IDBDatabase | null> {
  if (typeof indexedDB === 'undefined') return Promise.resolve(null);
  return new Promise((resolve, reject) => {
    const opening = indexedDB.open(DB_NAME, 1);
    opening.onupgradeneeded = () => opening.result.createObjectStore(STORE);
    opening.onsuccess = () => resolve(opening.result);
    opening.onerror = () => reject(opening.error ?? new Error('브라우저 저장소를 열지 못했어요'));
  });
}

async function run<T>(
  mode: IDBTransactionMode,
  action: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T | undefined> {
  const database = await openDatabase();
  if (!database) return undefined;
  try {
    return await new Promise<T>((resolve, reject) => {
      const transaction = database.transaction(STORE, mode);
      const request = action(transaction.objectStore(STORE));
      // 요청이 성공해도 커밋 단계에서 중단될 수 있어 트랜잭션 결과로 판정한다.
      transaction.oncomplete = () => resolve(request.result);
      transaction.onabort = () => reject(transaction.error ?? new Error('브라우저 저장소 오류'));
      transaction.onerror = () =>
        reject(request.error ?? transaction.error ?? new Error('브라우저 저장소 오류'));
    });
  } finally {
    database.close();
  }
}

export async function loadFileDraft(key: string): Promise<FileToSend[]> {
  const stored = await run<FileToSend[] | undefined>('readonly', (store) => store.get(key));
  return Array.isArray(stored) ? stored : [];
}

export async function saveFileDraft(key: string, files: FileToSend[]): Promise<void> {
  if (files.length === 0) {
    await removeFileDraft(key);
    return;
  }
  await run('readwrite', (store) => store.put(files, key));
}

export async function removeFileDraft(key: string): Promise<void> {
  await run('readwrite', (store) => store.delete(key));
}
