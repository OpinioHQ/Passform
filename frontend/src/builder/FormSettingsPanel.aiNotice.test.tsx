// @vitest-environment jsdom
// AI 요약 잠김 안내는 첫 배포 전(초안)에도 보이고, 체크박스는 첫 배포 뒤에만 잠긴다
// (사람 결정 #165, SPEC 164행 settings.ai_summary.enabled, AC90).
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BuilderPage } from '../pages/BuilderPage';
import { QuickBuilderPage } from '../pages/QuickBuilderPage';
import type { CreatorFormDefinition } from '../types/api';

const NOTICE = 'AI 요약 사용 여부는 첫 배포 뒤에는 바꿀 수 없어요.';

const templates = [
  { consent_template_id: 'consent_default', purpose: '기본 목적', retention: '기본 보유' },
];

const form = (
  version: number | null,
  status: CreatorFormDefinition['status'] = version === null ? 'draft' : 'open',
): CreatorFormDefinition => ({
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
  status,
  share_url: version === null ? null : 'https://app.example.com/f/abc',
  definition_version: version,
});

const aiSummary = () => screen.getByRole('checkbox', { name: /AI 요약 사용/ }) as HTMLInputElement;

beforeEach(() => {
  window.scrollTo = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('기본 모드 폼 설정의 AI 요약 안내', () => {
  it('초안이면 안내가 보이고 체크박스를 누를 수 있다', () => {
    render(<BuilderPage formId="f1" initial={form(null)} templates={templates} />);
    expect(screen.getByText(NOTICE)).toBeTruthy();
    expect(aiSummary().disabled).toBe(false);
  });

  it('첫 배포 뒤에는 안내가 그대로 보이고 체크박스가 잠긴다', () => {
    render(<BuilderPage formId="f1" initial={form(1)} templates={templates} />);
    expect(screen.getByText(NOTICE)).toBeTruthy();
    expect(aiSummary().disabled).toBe(true);
  });
});

// 빠른 모드는 배포 중인 폼에서 설정 단계를 보이지 않는다. 첫 배포 뒤 설정이 보이는 경우는
// 배포했다가 초안으로 되돌린 폼(status draft, definition_version 있음)이다.
describe('빠른 모드 설정·배포 단계의 AI 요약 안내', () => {
  const openQuick = (version: number | null) => {
    render(
      <MemoryRouter>
        <QuickBuilderPage
          formId="f1"
          initial={form(version, 'draft')}
          templates={templates}
          onBasic={vi.fn()}
          onDone={vi.fn()}
        />
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole('button', { name: '5. 설정·배포' }));
  };

  it('초안이면 안내가 보이고 체크박스를 누를 수 있다', () => {
    openQuick(null);
    expect(screen.getByText(NOTICE)).toBeTruthy();
    expect(aiSummary().disabled).toBe(false);
  });

  it('첫 배포 뒤 초안으로 되돌린 폼에서는 안내가 그대로 보이고 체크박스가 잠긴다', () => {
    openQuick(1);
    expect(screen.getByText(NOTICE)).toBeTruthy();
    expect(aiSummary().disabled).toBe(true);
  });
});
