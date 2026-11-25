// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { BuilderPage } from '../pages/BuilderPage';
import type { CreatorFormDefinition, FormDefinition, Question } from '../types/api';
import {
  addQuestion,
  allowsResponseFiles,
  duplicateQuestion,
  emptyQuestion,
  setAllowResponseFiles,
  setQuestionNote,
  setQuestionType,
} from './formDefinition';
import { QuestionEditor } from './QuestionEditor';
import { addQuestionAtEnd, questionFromTemplate } from './quickMode';

/** 제작 화면의 응답 파일 첨부 켜기/끄기. 기본 모드만 칸을 두고, 빠른 모드는 값을 바꾸지 않는다. */

const base: Question = { ...emptyQuestion(), question_id: 'q1', question_note: '문항' };

const definitionOf = (questions: Question[]): FormDefinition => ({
  title: '가상 폼',
  note: '',
  settings: { editable: false, cancellable: false, display: { default_mode: 'one_question' } },
  payment_link: null,
  consent_template_id: 'consent_default',
  note_attachments: [],
  sections: [{ section_id: 's1', title: '', questions }],
});

const initial = (def: FormDefinition): CreatorFormDefinition => ({
  ...def,
  status: 'draft',
  share_url: null,
  definition_version: null,
});

const toggle = (n: number) =>
  screen.getByLabelText(`질문 ${n} 응답자 파일 첨부 허용`) as HTMLInputElement;

window.scrollTo = vi.fn();

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('폼 정의 함수', () => {
  it('새 문항과 빠른 모드에서 만든 문항은 꺼져 있다', () => {
    expect(allowsResponseFiles(emptyQuestion())).toBe(false);
    expect(
      allowsResponseFiles(
        questionFromTemplate({
          template_id: 't1',
          question_note: '이름',
          question_type: 'short_answer',
          options: [],
          input_format: { kind: 'name' },
        }),
      ),
    ).toBe(false);
    const added = addQuestionAtEnd(definitionOf([])).sections[0].questions[0];
    expect(allowsResponseFiles(added)).toBe(false);
  });

  it('끄면 키를 빼서 필드가 없던 문항과 같은 정의가 된다', () => {
    const on = setAllowResponseFiles(base, true);
    expect(on.allow_response_files).toBe(true);
    expect(setAllowResponseFiles(on, false)).toEqual(base);
  });

  it('문구·유형을 고치거나 복제해도 켠 값이 유지된다', () => {
    const on = setAllowResponseFiles(base, true);
    expect(allowsResponseFiles(setQuestionNote(on, '바뀐 문구'))).toBe(true);
    expect(allowsResponseFiles(setQuestionType(on, 'checkbox'))).toBe(true);
    const def = duplicateQuestion(definitionOf([on]), 's1', 'q1');
    expect(def.sections[0].questions.map(allowsResponseFiles)).toEqual([true, true]);
    expect(allowsResponseFiles(addQuestion(def, 's1').sections[0].questions[2])).toBe(false);
  });
});

describe('기본 모드 화면', () => {
  it('새 문항은 꺼져 있고, 켠 값이 저장 요청에 들어가며 다시 열어도 켜져 있다', async () => {
    const update = vi.spyOn(api, 'updateForm').mockResolvedValue({
      status: 'draft',
      share_url: null,
      definition_version: null,
    });
    const view = render(
      <BuilderPage formId="f1" initial={initial(definitionOf([base]))} templates={null} />,
    );
    expect(toggle(1).checked).toBe(false);

    fireEvent.click(toggle(1));
    expect(toggle(1).checked).toBe(true);
    fireEvent.click(screen.getAllByRole('button', { name: '초안 저장' })[0]);
    await waitFor(() => expect(update).toHaveBeenCalled());
    const sent = update.mock.calls[update.mock.calls.length - 1][1] as FormDefinition;
    expect(sent.sections[0].questions[0].allow_response_files).toBe(true);

    view.unmount();
    render(
      <BuilderPage
        formId="f1"
        initial={initial({ ...definitionOf([]), sections: sent.sections })}
        templates={null}
      />,
    );
    expect(toggle(1).checked).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: '질문 1 복제' }));
    expect(toggle(2).checked).toBe(true);
  });
});

describe('빠른 모드의 문항 편집 칸', () => {
  it('켜기/끄기 칸이 없고 편집해도 켠 값을 그대로 넘긴다', () => {
    const onChange = vi.fn();
    const on = setAllowResponseFiles(base, true);
    // 빠른 모드(QuickBuilderPage)는 responseFileToggle 없이 이 편집 칸을 쓴다.
    render(
      <QuestionEditor
        definition={definitionOf([on])}
        question={on}
        number={1}
        canMoveUp={false}
        canMoveDown={false}
        onChange={onChange}
        onEdit={vi.fn()}
        onMove={vi.fn()}
        onDuplicate={vi.fn()}
        onRemove={vi.fn()}
      />,
    );
    expect(screen.queryByLabelText('질문 1 응답자 파일 첨부 허용')).toBeNull();
    fireEvent.change(screen.getByLabelText('질문 1 문구'), { target: { value: '바뀐 문구' } });
    expect((onChange.mock.calls[0][0] as Question).allow_response_files).toBe(true);
  });
});
