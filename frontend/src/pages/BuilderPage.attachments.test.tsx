// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { duplicateQuestion } from '../builder/formDefinition';
import type { CreatorFormDefinition, FormDefinition } from '../types/api';
import { BuilderPage } from './BuilderPage';

const definition: FormDefinition = {
  title: '가상 폼',
  note: '',
  settings: { editable: false, cancellable: false, display: { default_mode: 'one_question' } },
  payment_link: null,
  consent_template_id: 'consent_default',
  note_attachments: [],
  sections: [
    {
      section_id: 's1',
      title: '',
      questions: [
        {
          question_id: 'q1',
          template_id: null,
          question_note: '문항',
          question_type: 'short_answer',
          options: [],
          required: false,
          profile_key: null,
          attachments: [],
          branch_rules: [],
        },
      ],
    },
  ],
};

const initial = (status: 'draft' | 'open', version: number | null): CreatorFormDefinition => ({
  ...definition,
  status,
  share_url: null,
  definition_version: version,
});

const uploaded = {
  attachment_id: 'a1',
  filename: 'a.png',
  content_type: 'image/png',
  size_bytes: 1,
};
const file = new File(['x'], 'a.png', { type: 'image/png' });
const notePicker = () => screen.getByLabelText('폼 설명 파일 첨부') as HTMLInputElement;
const questionPicker = () => screen.getByLabelText('질문 1 파일 첨부') as HTMLInputElement;

window.scrollTo = vi.fn();

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('빌더의 첨부 연결 (AC63)', () => {
  it('설명란과 문항 첨부 칸이 있고 초안 첨부는 저장 안 한 변경으로 치지 않는다', async () => {
    const upload = vi.spyOn(api, 'uploadAttachment').mockResolvedValue(uploaded);
    const reload = vi.spyOn(api, 'formDefinition');
    render(<BuilderPage formId="f1" initial={initial('draft', null)} templates={null} />);
    expect(questionPicker().disabled).toBe(false);

    fireEvent.change(notePicker(), { target: { files: [file] } });

    await screen.findByRole('button', { name: '폼 설명 a.png 빼기' });
    expect(upload).toHaveBeenCalledWith('f1', null, file);
    expect(screen.queryByText(/저장하지 않은 변경/)).toBeNull();
    expect(reload).not.toHaveBeenCalled();
  });

  it('배포된 폼에 첨부하면 서버가 만든 새 폼 버전을 표시한다', async () => {
    vi.spyOn(api, 'uploadAttachment').mockResolvedValue(uploaded);
    vi.spyOn(api, 'formDefinition').mockResolvedValue(initial('open', 2));
    render(<BuilderPage formId="f1" initial={initial('open', 1)} templates={null} />);
    expect(screen.getByText(/폼 버전 1/)).toBeTruthy();

    fireEvent.change(questionPicker(), { target: { files: [file] } });

    await waitFor(() => expect(screen.getByText(/폼 버전 2/)).toBeTruthy());
  });

  it('저장하지 않은 편집이 있으면 첨부를 막는다', () => {
    render(<BuilderPage formId="f1" initial={initial('draft', null)} templates={null} />);
    fireEvent.change(screen.getByLabelText('폼 제목'), { target: { value: '바꾼 제목' } });
    expect(notePicker().disabled).toBe(true);
    expect(questionPicker().disabled).toBe(true);
  });

  it('문항 복제본은 원본의 첨부를 가져가지 않는다', () => {
    const form = structuredClone(definition);
    form.sections[0].questions[0].attachments = [uploaded];
    const next = duplicateQuestion(form, 's1', 'q1');
    expect(next.sections[0].questions[0].attachments).toEqual([uploaded]);
    expect(next.sections[0].questions[1].attachments).toEqual([]);
  });
});
