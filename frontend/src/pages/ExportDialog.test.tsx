// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Coverage, FormResults, RequestContext } from '../types/api';
import { ExportDialog, exportFilename } from './ExportDialog';
import { ResultsPage } from './ResultsPage';

const context: RequestContext = { conditions: ['소프트웨어학과'], match: 'all', operation: 'list' };
const partial: Coverage = {
  total_submitted_count: 14,
  not_evaluable_count: 3,
  not_evaluable_by_version: [
    { form_version: 3, response_count: 3, unavailable_conditions: ['소프트웨어학과'] },
  ],
};
const clarifyBody = JSON.stringify({
  status: 'clarify',
  clarify: { reason: 'missing_operation', message: '무엇을 할까요?', options: ['count', 'list'] },
});
const fileBody = JSON.stringify({ form_versions: [], responses: [] });

function stubExport(status: number, body: string, contentType: string) {
  const fetchMock = vi.fn<typeof fetch>(() =>
    Promise.resolve(new Response(body, { status, headers: { 'Content-Type': contentType } })),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const sentBody = (fetchMock: ReturnType<typeof stubExport>) =>
  JSON.parse(fetchMock.mock.calls[0][1]?.body as string) as Record<string, unknown>;

let createObjectURL: ReturnType<typeof vi.fn<typeof URL.createObjectURL>>;
let downloads: string[];

beforeEach(() => {
  createObjectURL = vi.fn<typeof URL.createObjectURL>(() => 'blob:export');
  URL.createObjectURL = createObjectURL;
  URL.revokeObjectURL = vi.fn();
  downloads = [];
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    downloads.push(this.download);
  });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

const submit = () => fireEvent.click(screen.getByRole('button', { name: /파일 받기/ }));

describe('내보내기 창', () => {
  it.each([
    ['CSV', 'csv', 'text/csv;charset=UTF-8'],
    ['JSON', 'json', 'application/json'],
    ['YAML', 'yaml', 'application/yaml'],
  ])('%s를 고르면 그 형식을 요청해 파일로 저장하고 창을 닫는다', async (label, format, type) => {
    const fetchMock = stubExport(200, fileBody, type);
    const onClose = vi.fn();
    render(<ExportDialog formId="f1" onClose={onClose} />);

    fireEvent.click(screen.getByRole('radio', { name: label }));
    submit();

    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    expect(sentBody(fetchMock)).toEqual({ format });
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(downloads[0]).toMatch(new RegExp(`^passform-export-\\d{8}\\.${format}$`));
  });

  it('파일 이름의 날짜는 Asia/Seoul 기준이다', () => {
    expect(exportFilename('csv', new Date('2026-11-09T15:30:00Z'))).toBe(
      'passform-export-20261110.csv',
    );
  });

  it('버전을 모두 고르면 범위를 보내지 않고 일부면 그 버전만 보낸다', async () => {
    const fetchMock = stubExport(200, 'a', 'text/csv;charset=UTF-8');
    const onClose = vi.fn();
    render(<ExportDialog formId="f1" versions={[1, 2, 3]} onClose={onClose} />);

    fireEvent.click(screen.getByRole('checkbox', { name: '폼 버전 2' }));
    submit();

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(sentBody(fetchMock)).toEqual({ format: 'csv', form_versions: [1, 3] });
  });

  it('버전을 전부 끄면 받을 수 없다', () => {
    render(<ExportDialog formId="f1" versions={[1]} onClose={vi.fn()} />);
    fireEvent.click(screen.getByRole('checkbox', { name: '폼 버전 1' }));
    expect((screen.getByRole('button', { name: /파일 받기/ }) as HTMLButtonElement).disabled).toBe(
      true,
    );
  });

  it('context가 있으면 범위 없이 context만 보낸다', async () => {
    const fetchMock = stubExport(200, 'a', 'text/csv;charset=UTF-8');
    const onClose = vi.fn();
    render(<ExportDialog formId="f1" versions={[1, 2]} context={context} onClose={onClose} />);

    expect(screen.queryByRole('checkbox')).toBeNull();
    submit();

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(sentBody(fetchMock)).toEqual({ format: 'csv', context });
  });

  it('되묻기면 파일을 저장하지 않고 질문을 보여 주며 창을 유지한다', async () => {
    stubExport(200, clarifyBody, 'application/json');
    const onClose = vi.fn();
    render(<ExportDialog formId="f1" context={context} onClose={onClose} />);

    fireEvent.click(screen.getByRole('radio', { name: 'JSON' }));
    submit();

    expect((await screen.findByRole('alert')).textContent).toContain('무엇을 할까요?');
    expect(createObjectURL).not.toHaveBeenCalled();
    expect(downloads).toEqual([]);
    expect(onClose).not.toHaveBeenCalled();
  });

  it('JSON 파일은 되묻기로 오인하지 않고 내용 그대로 저장한다', async () => {
    stubExport(200, fileBody, 'application/json');
    const onClose = vi.fn();
    render(<ExportDialog formId="f1" context={context} onClose={onClose} />);
    fireEvent.click(screen.getByRole('radio', { name: 'JSON' }));
    submit();

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    const saved = createObjectURL.mock.calls[0][0] as Blob;
    const text = await new Promise<string>((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.readAsText(saved);
    });
    expect(text).toBe(fileBody);
  });

  it.each([
    [401, '로그인이 필요해요.'],
    [403, '이 폼을 만든 사람만 내보낼 수 있어요.'],
    [404, '폼을 찾을 수 없어요.'],
    [503, 'AI 분석을 지금 쓸 수 없어요. 잠시 뒤 다시 시도해 주세요.'],
    [500, '내보내지 못했어요. 잠시 뒤 다시 시도해 주세요.'],
  ])('%i 실패를 안내하고 파일을 저장하지 않는다', async (status, text) => {
    stubExport(status, '{"error":"x"}', 'application/json');
    render(<ExportDialog formId="f1" onClose={vi.fn()} />);

    submit();

    expect((await screen.findByRole('alert')).textContent).toBe(text);
    expect(createObjectURL).not.toHaveBeenCalled();
  });

  it('일부만 확인했으면 항상 알리고 이유는 물음표를 눌러야 보인다', () => {
    render(<ExportDialog formId="f1" context={context} coverage={partial} onClose={vi.fn()} />);

    const notice = screen.getByRole('status');
    expect(notice.textContent).toContain('전체 응답 14건 중 11건만 확인했어요');
    expect(notice.textContent).toContain('확인하지 못한 3건은 파일에 들어가지 않아요');
    expect(notice.textContent).not.toContain('폼 버전 3');

    fireEvent.click(within(notice).getByRole('button', { name: '이유 보기' }));

    expect(notice.textContent).toContain(
      '폼 버전 3 응답 3건은 소프트웨어학과 조건을 확인할 수 없어요.',
    );
    expect(notice.textContent).not.toContain('판정 불가');
  });

  it('모두 확인했거나 범위 정보가 없으면 안내하지 않는다', () => {
    const complete = { ...partial, not_evaluable_count: 0, not_evaluable_by_version: [] };
    const { rerender } = render(
      <ExportDialog formId="f1" context={context} coverage={complete} onClose={vi.fn()} />,
    );
    expect(screen.queryByRole('status')).toBeNull();
    rerender(<ExportDialog formId="f1" onClose={vi.fn()} />);
    expect(screen.queryByRole('status')).toBeNull();
  });
});

describe('결과 화면의 내보내기', () => {
  const results: FormResults = {
    response_count: 2,
    combined: { questions: [] },
    versions: [
      { form_version: 1, response_count: 1, questions: [] },
      { form_version: 2, response_count: 1, questions: [] },
    ],
  };

  it('제출 버전이 체크된 창을 열고 전체 응답을 파일로 받는다', async () => {
    const fetchMock = stubExport(200, 'a', 'text/csv;charset=UTF-8');
    render(<ResultsPage formId="f1" results={results} />);

    expect(screen.queryByRole('dialog')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '내보내기' }));
    const dialog = screen.getByRole('dialog', { name: '내보내기' });
    expect(
      (within(dialog).getByRole('checkbox', { name: '폼 버전 1' }) as HTMLInputElement).checked,
    ).toBe(true);
    expect(
      (within(dialog).getByRole('checkbox', { name: '폼 버전 2' }) as HTMLInputElement).checked,
    ).toBe(true);
    submit();

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(String(fetchMock.mock.calls[0][0])).toContain('/forms/f1/export');
    expect(sentBody(fetchMock)).toEqual({ format: 'csv' });
    expect(createObjectURL).toHaveBeenCalledTimes(1);
  });
});
