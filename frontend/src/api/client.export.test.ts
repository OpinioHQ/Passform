import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './client';

function stubFetch(status: number, body: BodyInit, contentType: string) {
  const fetchMock = vi.fn<typeof fetch>(() =>
    Promise.resolve(new Response(body, { status, headers: { 'Content-Type': contentType } })),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api.exportForm', () => {
  it('자격 증명과 함께 POST하고 요청을 그대로 보낸다', async () => {
    const fetchMock = stubFetch(200, 'a', 'text/csv;charset=UTF-8');
    await api.exportForm('f 1', { format: 'csv', form_versions: [1] });
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/forms/f%201/export');
    expect(init?.method).toBe('POST');
    expect(init?.credentials).toBe('include');
    expect(JSON.parse(init?.body as string)).toEqual({ format: 'csv', form_versions: [1] });
  });

  it('CSV의 BOM을 지우지 않고 바이트 그대로 돌려준다', async () => {
    stubFetch(200, new Uint8Array([0xef, 0xbb, 0xbf, 0x61]), 'text/csv;charset=UTF-8');
    const result = await api.exportForm('f1', { format: 'csv' });
    expect(result.status).toBe('file');
    if (result.status !== 'file') return;
    expect(Array.from(new Uint8Array(await result.file.arrayBuffer()))).toEqual([
      0xef, 0xbb, 0xbf, 0x61,
    ]);
  });

  it('JSON 응답의 status가 clarify일 때만 되묻기다', async () => {
    const clarify = { reason: 'missing_operation', message: '무엇을 할까요?', options: ['count'] };
    stubFetch(200, JSON.stringify({ status: 'clarify', clarify }), 'application/json');
    await expect(api.exportForm('f1', { format: 'json' })).resolves.toEqual({
      status: 'clarify',
      clarify,
    });

    stubFetch(200, '{"form_versions":[],"responses":[]}', 'application/json');
    await expect(api.exportForm('f1', { format: 'json' })).resolves.toMatchObject({
      status: 'file',
    });
  });

  it('실패는 ApiError로 낸다', async () => {
    stubFetch(400, '{"error":"invalid_context"}', 'application/json');
    const failure = await api.exportForm('f1', { format: 'csv' }).catch((e: unknown) => e);
    expect(failure).toBeInstanceOf(ApiError);
    expect(failure).toMatchObject({ status: 400, detail: { error: 'invalid_context' } });
  });
});
