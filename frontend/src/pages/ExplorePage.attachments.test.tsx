// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ResponseAttachment, ResponseDetail } from '../types/api';

// 결과 화면 응답 목록의 첨부와 상세 링크(#63 후속, SPEC 5절 198·222·224행).
const file = (id: string, questionId: string, available = true): ResponseAttachment => ({
  attachment_id: id,
  question_id: questionId,
  filename: `${id}.pdf`,
  content_type: 'application/pdf',
  size_bytes: 1,
  available,
});

const detail = (responseId: string, attachments: ResponseAttachment[]): ResponseDetail => ({
  response_id: responseId,
  form_version: 1,
  submitted_at: '2026-11-01T00:00:00Z',
  updated_at: null,
  content: [
    { question_id: 'q1', question_note: '학년', value: '1학년' },
    { question_id: 'q2', question_note: '하고 싶은 말', value: '안녕하세요' },
  ],
  attachments,
});

async function open(
  live: boolean,
  details: Record<string, ResponseDetail>,
  formId: string | undefined = 'f1',
) {
  vi.resetModules();
  vi.stubEnv('VITE_USE_API', live ? 'true' : 'false');
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  const { ListResult } = await import('./ExplorePage');
  const { responseAttachmentUrl } = await import('../api/client');
  const { paths } = await import('../routes/paths');
  const ids = Object.keys(details);
  render(
    <ListResult ids={ids} formId={formId} loadResponse={(id) => Promise.resolve(details[id])} />,
  );
  await screen.findAllByText('학년');
  return { responseAttachmentUrl, paths };
}

const items = () =>
  screen.getAllByRole('listitem').filter((li) => li.classList.contains('explore-response'));

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe('결과 화면 응답 목록의 첨부', () => {
  it('첨부는 그 문항의 답 아래에 보이고 내려받기 주소로 연결된다', async () => {
    const { responseAttachmentUrl } = await open(true, { r1: detail('r1', [file('a1', 'q2')]) });
    const answer = screen.getByText('안녕하세요').closest('dd') as HTMLElement;
    const link = within(answer).getByRole('link', { name: 'a1.pdf' });
    expect(link.getAttribute('href')).toBe(responseAttachmentUrl('f1', 'r1', 'a1'));
    expect(
      within(screen.getByText('1학년').closest('dd') as HTMLElement).queryByRole('link'),
    ).toBeNull();
  });

  it('답 없이 파일만 낸 문항의 파일도 보인다', async () => {
    const { responseAttachmentUrl } = await open(true, { r1: detail('r1', [file('a9', 'q5')]) });
    expect(screen.getByRole('link', { name: 'a9.pdf' }).getAttribute('href')).toBe(
      responseAttachmentUrl('f1', 'r1', 'a9'),
    );
  });

  it('지운 파일은 이름과 삭제 표시만 있고 링크가 없다', async () => {
    await open(true, { r1: detail('r1', [file('a1', 'q2', false)]) });
    expect(screen.getByText(/a1\.pdf/).textContent).toContain('(삭제된 파일)');
    expect(screen.queryByRole('link', { name: /a1\.pdf/ })).toBeNull();
  });

  it('첨부가 없는 응답은 파일 목록을 그리지 않는다', async () => {
    await open(true, { r1: detail('r1', []) });
    expect(document.querySelector('.explore-response .attachment-list')).toBeNull();
  });

  it('응답마다 자기 응답의 주소로 파일과 상세를 연결한다', async () => {
    const { responseAttachmentUrl, paths } = await open(true, {
      r1: detail('r1', [file('a1', 'q2')]),
      r2: detail('r2', [file('a2', 'q2')]),
    });
    const [first, second] = items();
    expect(within(second).getByRole('link', { name: 'a2.pdf' }).getAttribute('href')).toBe(
      responseAttachmentUrl('f1', 'r2', 'a2'),
    );
    const firstDetail = within(first).getByRole('link', { name: '응답 1 상세 보기' });
    expect(firstDetail.getAttribute('href')).toBe(paths.formResponse('f1', 'r1'));
    expect(firstDetail.getAttribute('target')).toBe('_blank');
    expect(
      within(second).getByRole('link', { name: '응답 2 상세 보기' }).getAttribute('href'),
    ).toBe(paths.formResponse('f1', 'r2'));
    expect(first.textContent).not.toContain('r1');
    expect(second.textContent).not.toContain('r2');
  });

  it('가짜 데이터 모드에서는 내려받기 링크 없이 이름만 보인다', async () => {
    await open(false, { r1: detail('r1', [file('a1', 'q2')]) });
    expect(screen.getByText('a1.pdf')).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'a1.pdf' })).toBeNull();
  });
});
