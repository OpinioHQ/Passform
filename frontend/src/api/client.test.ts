import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './client';

function stubFetch(status: number, body: string | null) {
  const fetchMock = vi.fn<typeof fetch>(() => Promise.resolve(new Response(body, { status })));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api client', () => {
  it('자격 증명을 항상 함께 보낸다', async () => {
    const fetchMock = stubFetch(200, '[]');
    await api.savedForms();
    expect(fetchMock.mock.calls[0][1]?.credentials).toBe('include');
  });

  it('204와 본문 없는 응답은 undefined로 돌려준다', async () => {
    stubFetch(204, null);
    await expect(api.savedForms()).resolves.toBeUndefined();
    stubFetch(200, '');
    await expect(api.savedForms()).resolves.toBeUndefined();
  });

  it('오류 응답의 본문을 JSON이든 문자열이든 ApiError에 담는다', async () => {
    stubFetch(409, '{"error":"form_closed"}');
    await expect(api.formById('f1')).rejects.toMatchObject({
      status: 409,
      detail: { error: 'form_closed' },
    });
    stubFetch(502, 'Bad Gateway');
    const failure = await api.formById('f1').catch((e: unknown) => e);
    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).detail).toBe('Bad Gateway');
  });

  it('analyze는 {query}와 {context}를 그대로 보낸다', async () => {
    const fetchMock = stubFetch(200, '{}');
    await api.analyze('f1', { query: '몇 명이야?' });
    await api.analyze('f1', { context: { conditions: ['학과 - 소프트웨어'], operation: 'count' } });
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/forms\/f1\/analyze$/);
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({ query: '몇 명이야?' });
    expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body))).toEqual({
      context: { conditions: ['학과 - 소프트웨어'], operation: 'count' },
    });
  });

  it('본문이 있는 요청에만 JSON 헤더를 붙이고 경로를 인코딩한다', async () => {
    const fetchMock = stubFetch(200, '{}');
    await api.formBySlug('a/b');
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/f\/a%2Fb$/);
    expect(fetchMock.mock.calls[0][1]?.headers).toEqual({});
    await api.analyze('f1', { query: 'q' });
    expect(fetchMock.mock.calls[1][1]?.headers).toEqual({ 'Content-Type': 'application/json' });
  });
});
