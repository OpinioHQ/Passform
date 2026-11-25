// @vitest-environment jsdom
/** 제작자 첨부 목록은 제목·설명 글자와 같은 줄에서 시작하도록 전용 클래스를 붙인다(공용 목록 규칙은 그대로). */
import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { FormAttachmentList } from './FormAttachmentList';

afterEach(cleanup);

describe('제작자 첨부 목록 정렬', () => {
  it('목록에 공용 클래스와 form-attachments 클래스가 함께 붙는다', () => {
    const { container } = render(
      <FormAttachmentList
        formId="f1"
        questionId={null}
        files={[
          {
            attachment_id: 'a1',
            filename: '포스터.png',
            content_type: 'image/png',
            size_bytes: 1,
          },
        ]}
      />,
    );
    const list = container.querySelector('ul');
    expect(list?.classList.contains('attachment-list')).toBe(true);
    expect(list?.classList.contains('form-attachments')).toBe(true);
  });
});
