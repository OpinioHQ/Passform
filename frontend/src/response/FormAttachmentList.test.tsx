// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { Attachment } from '../types/api';
import { FormAttachmentList } from './FormAttachmentList';

const image: Attachment = {
  attachment_id: 'a1',
  filename: '포스터.png',
  content_type: 'image/png',
  size_bytes: 1,
};
const pdf: Attachment = {
  attachment_id: 'a2',
  filename: '안내문.pdf',
  content_type: 'application/pdf',
  size_bytes: 1,
};
const video: Attachment = {
  attachment_id: 'a3',
  filename: '소개.mp4',
  content_type: 'video/mp4',
  size_bytes: 1,
};

afterEach(cleanup);

describe('응답 화면의 제작자 첨부 (AC63)', () => {
  it('이미지는 화면에 바로 보이고 문항 첨부 주소를 쓴다', () => {
    render(<FormAttachmentList formId="f1" questionId="q1" files={[image]} />);
    expect(screen.getByRole('img', { name: '포스터.png' }).getAttribute('src')).toMatch(
      /\/forms\/f1\/questions\/q1\/attachments\/a1$/,
    );
  });

  it('PDF와 MP4는 화면에서 열지 않고 다운로드 링크만 준다', () => {
    render(<FormAttachmentList formId="f1" questionId={null} files={[pdf, video]} />);
    expect(screen.queryByRole('img')).toBeNull();
    expect(document.querySelector('video, iframe, embed, object')).toBeNull();
    const link = screen.getByRole('link', { name: '안내문.pdf 내려받기' });
    expect(link.getAttribute('href')).toMatch(/\/forms\/f1\/attachments\/a2$/);
    expect(screen.getByRole('link', { name: '소개.mp4 내려받기' })).toBeTruthy();
  });

  it('첨부가 없으면 아무것도 그리지 않는다', () => {
    const { container } = render(<FormAttachmentList formId="f1" questionId={null} files={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('폼 ID가 없는 가짜 데이터 모드는 이름만 보인다', () => {
    render(<FormAttachmentList formId="" questionId={null} files={[image]} />);
    expect(screen.queryByRole('img')).toBeNull();
    expect(screen.getByText('포스터.png')).toBeTruthy();
  });
});
