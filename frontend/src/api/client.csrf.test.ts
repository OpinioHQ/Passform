// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './client';

function stubFetch() {
  const fetchMock = vi.fn<typeof fetch>(() => Promise.resolve(new Response('{}', { status: 201 })));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});

const body = { form_version: 1, answers: [], consent_agreed: true as const };

describe('csrfHeaders', () => {
  it('XSRF-TOKEN 쿠키를 X-XSRF-TOKEN 헤더로 옮기고 다른 헤더를 지우지 않는다', async () => {
    document.cookie = 'other=1';
    document.cookie = 'XSRF-TOKEN=token%2Dvalue';
    const fetchMock = stubFetch();
    await api.submit('f1', body);
    expect(fetchMock.mock.calls[0][1]?.headers).toEqual({
      'X-XSRF-TOKEN': 'token-value',
      'Content-Type': 'application/json',
    });
  });

  it('쿠키가 없으면 헤더를 넣지 않는다', async () => {
    const fetchMock = stubFetch();
    await api.submit('f1', body);
    expect(fetchMock.mock.calls[0][1]?.headers).toEqual({ 'Content-Type': 'application/json' });
  });

  it('이름이 비슷한 쿠키를 토큰으로 읽지 않는다', async () => {
    document.cookie = 'MY-XSRF-TOKEN=wrong';
    const fetchMock = stubFetch();
    await api.submit('f1', body);
    expect(fetchMock.mock.calls[0][1]?.headers).toEqual({ 'Content-Type': 'application/json' });
    document.cookie = 'MY-XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });
});
