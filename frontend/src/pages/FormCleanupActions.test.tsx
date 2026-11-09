// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '../api/client';
import { FormCleanupActions } from './FormCleanupActions';

const onCleaned = vi.fn();
const click = (name: string | RegExp) => fireEvent.click(screen.getByRole('button', { name }));
const show = (responseCount: number) =>
  render(
    <FormCleanupActions
      formId="f1"
      name="가상 폼"
      responseCount={responseCount}
      onCleaned={onCleaned}
    />,
  );

beforeEach(() => {
  onCleaned.mockReset();
  URL.createObjectURL = vi.fn(() => 'blob:x');
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('폼 삭제 전 내보내기 (AC67)', () => {
  it('delete_form_offers_export 응답이 있으면 내보내기를 묻고 첨부 미포함을 안내한다', () => {
    show(2);
    click('가상 폼 폼 삭제');
    expect(screen.getByText(/받은 응답 2건을 삭제 전에 파일로 내보낼까요/)).toBeTruthy();
    expect(screen.getByTestId('no-attachment-notice').textContent).toContain(
      '첨부 파일 원본이 포함되지 않아요',
    );
    expect(screen.getAllByRole('radio')).toHaveLength(3);
  });

  it('내보내기가 성공한 뒤에 삭제한다', async () => {
    const order: string[] = [];
    vi.spyOn(api, 'exportForm').mockImplementation(() => {
      order.push('export');
      return Promise.resolve({ status: 'file', file: new Blob(['x']) });
    });
    vi.spyOn(api, 'deleteForm').mockImplementation(() => {
      order.push('delete');
      return Promise.resolve();
    });
    show(2);
    click('가상 폼 폼 삭제');
    fireEvent.click(screen.getByRole('radio', { name: 'YAML' }));
    click('내보내고 삭제');
    await waitFor(() => expect(onCleaned).toHaveBeenCalledWith('form'));
    expect(order).toEqual(['export', 'delete']);
    expect(api.exportForm).toHaveBeenCalledWith('f1', { format: 'yaml' });
  });

  it('delete_form_export_failure_keeps_data 내보내기가 실패하면 삭제하지 않는다', async () => {
    vi.spyOn(api, 'exportForm').mockRejectedValue(new ApiError(500, {}));
    const remove = vi.spyOn(api, 'deleteForm').mockResolvedValue(undefined);
    show(2);
    click('가상 폼 폼 삭제');
    click('내보내고 삭제');
    expect((await screen.findByRole('alert')).textContent).toContain('폼을 삭제하지 않았어요');
    expect(remove).not.toHaveBeenCalled();
    expect(onCleaned).not.toHaveBeenCalled();
  });

  it('되묻기로 파일이 없으면 삭제하지 않는다', async () => {
    vi.spyOn(api, 'exportForm').mockResolvedValue({
      status: 'clarify',
      clarify: {} as never,
    });
    const remove = vi.spyOn(api, 'deleteForm').mockResolvedValue(undefined);
    show(1);
    click('가상 폼 폼 삭제');
    click('내보내고 삭제');
    await screen.findByRole('alert');
    expect(remove).not.toHaveBeenCalled();
  });

  it('건너뛰기를 고르면 내보내지 않고 삭제한다', async () => {
    const exporter = vi.spyOn(api, 'exportForm');
    vi.spyOn(api, 'deleteForm').mockResolvedValue(undefined);
    show(2);
    click('가상 폼 폼 삭제');
    click('내보내지 않고 삭제');
    await waitFor(() => expect(onCleaned).toHaveBeenCalledWith('form'));
    expect(exporter).not.toHaveBeenCalled();
  });

  it('응답이 0건이면 내보내기를 묻지 않고 삭제 확인만 한다', async () => {
    vi.spyOn(api, 'deleteForm').mockResolvedValue(undefined);
    show(0);
    click('가상 폼 폼 삭제');
    expect(screen.queryByTestId('no-attachment-notice')).toBeNull();
    click('삭제');
    await waitFor(() => expect(onCleaned).toHaveBeenCalledWith('form'));
  });

  it('삭제가 실패하면 알리고 목록을 바꾸지 않는다', async () => {
    vi.spyOn(api, 'deleteForm').mockRejectedValue(new ApiError(500, {}));
    show(0);
    click('가상 폼 폼 삭제');
    click('삭제');
    expect((await screen.findByRole('alert')).textContent).toContain('삭제하지 못했어요');
    expect(onCleaned).not.toHaveBeenCalled();
  });
});

describe('응답·첨부 전체 삭제 (AC66, AC70)', () => {
  it('확인한 뒤에만 응답 전체를 지운다', async () => {
    const remove = vi.spyOn(api, 'deleteReceivedResponses').mockResolvedValue(undefined);
    show(2);
    click('가상 폼 응답 전체 삭제');
    expect(remove).not.toHaveBeenCalled();
    click('삭제');
    await waitFor(() => expect(onCleaned).toHaveBeenCalledWith('responses'));
    expect(remove).toHaveBeenCalledWith('f1');
  });

  it('첨부 전체 삭제는 답변이 유지된다고 안내하고 취소할 수 있다', () => {
    const remove = vi.spyOn(api, 'deleteResponseAttachments').mockResolvedValue(undefined);
    show(2);
    click('가상 폼 응답 첨부 전체 삭제');
    expect(screen.getByText(/답변 내용과 응답 수는 그대로/)).toBeTruthy();
    click('취소');
    expect(remove).not.toHaveBeenCalled();
  });

  it('응답이 없으면 응답과 첨부 삭제 버튼을 막는다', () => {
    show(0);
    expect(
      (screen.getByRole('button', { name: '가상 폼 응답 전체 삭제' }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
    expect(
      (screen.getByRole('button', { name: '가상 폼 응답 첨부 전체 삭제' }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
  });
});
