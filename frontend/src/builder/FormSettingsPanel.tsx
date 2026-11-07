import { Panel } from '../components/UI';
import type { ConsentTemplate, DisplayMode, FormDefinition, FormSettings } from '../types/api';
import { isoToKstInput, kstInputToIso } from './deadline';
import { countBranchRules } from './formDefinition';
import { parsePositiveInt } from './QuestionEditor';

const DISPLAY_LABELS: Record<DisplayMode, string> = {
  one_question: '한 질문씩',
  all_questions: '전체 질문 보기',
  section_page: '섹션별 페이지',
};

export const DEFAULT_CONSENT_TEMPLATE_ID = 'consent_default';

const templateLabel = (template: ConsentTemplate, index: number) =>
  template.consent_template_id === DEFAULT_CONSENT_TEMPLATE_ID
    ? '기본 동의 안내'
    : `동의 안내 ${index + 1}`;

function DisplayModeField({
  mode,
  branchCount,
  onChange,
}: {
  mode: DisplayMode;
  branchCount: number;
  onChange: (mode: DisplayMode) => void;
}) {
  if (branchCount > 0) {
    return (
      <div className="builder-field">
        <span>시작 방식</span>
        <strong>{DISPLAY_LABELS[mode]}</strong>
        <small className="muted">분기 {branchCount}개, 분기 편집은 준비 중이에요.</small>
      </div>
    );
  }
  return (
    <label className="builder-field">
      <span>시작 방식</span>
      <select value={mode} onChange={(e) => onChange(e.target.value as DisplayMode)}>
        <option value="one_question">{DISPLAY_LABELS.one_question}</option>
        <option value="all_questions">{DISPLAY_LABELS.all_questions}</option>
        {mode === 'section_page' && (
          <option value="section_page" disabled>
            {DISPLAY_LABELS.section_page}
          </option>
        )}
      </select>
    </label>
  );
}

function ConsentField({
  templates,
  selectedId,
  onChange,
}: {
  templates: ConsentTemplate[] | null;
  selectedId: string | null;
  onChange: (id: string | null) => void;
}) {
  if (templates === null) {
    return <p className="error">동의 템플릿을 불러오지 못했어요. 새로고침해 주세요.</p>;
  }
  if (templates.length === 0) {
    return (
      <p className="error">
        사용할 수 있는 동의 템플릿이 없어요. 초안은 저장되지만 이 상태로는 배포할 수 없어요.
      </p>
    );
  }
  const selected = templates.find((t) => t.consent_template_id === selectedId);
  return (
    <>
      <label className="builder-field">
        <span>동의 템플릿</span>
        <select
          value={selected?.consent_template_id ?? ''}
          onChange={(e) => onChange(e.target.value || null)}
        >
          {!selected && <option value="">선택해 주세요</option>}
          {templates.map((template, index) => (
            <option key={template.consent_template_id} value={template.consent_template_id}>
              {templateLabel(template, index)}
            </option>
          ))}
        </select>
      </label>
      {selected && (
        <div className="small muted">
          <p>목적: {selected.purpose}</p>
          <p className="keep-lines">보유 기간: {selected.retention}</p>
        </div>
      )}
    </>
  );
}

export function FormSettingsPanel({
  definition: def,
  firstPublished,
  templates,
  onChange,
}: {
  definition: FormDefinition;
  firstPublished: boolean;
  templates: ConsentTemplate[] | null;
  onChange: (change: (def: FormDefinition) => FormDefinition) => void;
}) {
  const settings = def.settings;
  const setSettings = (next: Partial<FormSettings>) =>
    onChange((d) => ({ ...d, settings: { ...d.settings, ...next } }));
  return (
    <Panel className="builder-section builder-settings">
      <h3>폼 설정</h3>
      <label className="builder-field">
        <span>마감 (한국 시간, 선택)</span>
        <input
          type="datetime-local"
          value={isoToKstInput(def.deadline)}
          onChange={(e) => {
            const deadline = kstInputToIso(e.target.value);
            onChange((d) => ({ ...d, deadline }));
          }}
        />
      </label>
      <label className="builder-field">
        <span>선착순 인원 (선택)</span>
        <input
          type="number"
          min={1}
          step={1}
          value={settings.response_limit ?? ''}
          onChange={(e) => {
            const limit = parsePositiveInt(e.target.value);
            if (limit !== undefined) setSettings({ response_limit: limit });
          }}
        />
      </label>
      <label className="builder-check">
        <input
          type="checkbox"
          checked={settings.editable}
          onChange={(e) => setSettings({ editable: e.target.checked })}
        />{' '}
        제출 후 수정 허용
      </label>
      <label className="builder-check">
        <input
          type="checkbox"
          checked={settings.cancellable}
          onChange={(e) => setSettings({ cancellable: e.target.checked })}
        />{' '}
        제출 취소 허용
      </label>
      <DisplayModeField
        mode={settings.display.default_mode}
        branchCount={countBranchRules(def)}
        onChange={(mode) => setSettings({ display: { default_mode: mode } })}
      />
      <label className="builder-check">
        <input
          type="checkbox"
          checked={settings.ai_summary?.enabled ?? false}
          disabled={firstPublished}
          onChange={(e) => setSettings({ ai_summary: { enabled: e.target.checked } })}
        />{' '}
        AI 요약 사용
      </label>
      {firstPublished && (
        <small className="muted">AI 요약 사용 여부는 첫 배포 뒤에는 바꿀 수 없어요.</small>
      )}
      <ConsentField
        templates={templates}
        selectedId={def.consent_template_id}
        onChange={(id) => onChange((d) => ({ ...d, consent_template_id: id }))}
      />
      <label className="builder-field">
        <span>송금 링크 (선택)</span>
        <input
          type="url"
          placeholder="https://"
          value={def.payment_link ?? ''}
          onChange={(e) => {
            const link = e.target.value.trim() || null;
            onChange((d) => ({ ...d, payment_link: link }));
          }}
        />
        <small className="muted">
          응답자에게 링크만 보여 줘요. Passform은 송금을 처리하거나 확인하지 않아요.
        </small>
      </label>
    </Panel>
  );
}
