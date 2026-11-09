import React from 'react';
import { api, ApiError } from '../api/client';
import { Button, Icon } from '../components/UI';
import type { Coverage, ExportFormat, ExportRequest, RequestContext } from '../types/api';

const formats: Array<{ value: ExportFormat; label: string }> = [
  { value: 'csv', label: 'CSV' },
  { value: 'json', label: 'JSON' },
  { value: 'yaml', label: 'YAML' },
];

/** 다른 도메인의 화면은 서버의 파일 이름을 읽을 수 없어 여기서 정한다. 날짜는 화면 표시 기준인 Asia/Seoul이다. */
export function exportFilename(format: ExportFormat, now: Date = new Date()): string {
  const date = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
  return `passform-export-${date.replace(/-/g, '')}.${format}`;
}

function saveFile(file: Blob, filename: string) {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(file);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}

function failureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return '로그인이 필요해요.';
    if (error.status === 403) return '이 폼을 만든 사람만 내보낼 수 있어요.';
    if (error.status === 404) return '폼을 찾을 수 없어요.';
    if (error.status === 503) return 'AI 분석을 지금 쓸 수 없어요. 잠시 뒤 다시 시도해 주세요.';
  }
  return '내보내지 못했어요. 잠시 뒤 다시 시도해 주세요.';
}

/**
 * 일부만 확인했다는 사실은 항상 보이고 이유는 물음표를 눌러야 보인다(AGENTS 91~92행).
 * 파일 형식에는 이 범위를 넣지 않으므로(SPEC 241~242행) 안내하는 곳은 이 화면뿐이다.
 */
function CoverageNotice({ coverage }: { coverage: Coverage }) {
  const [open, setOpen] = React.useState(false);
  const missing = coverage.not_evaluable_count;
  const checked = coverage.total_submitted_count - missing;
  return (
    <div className="export-coverage" role="status">
      <p className="small">
        전체 응답 {coverage.total_submitted_count}건 중 {checked}건만 확인했어요. 확인하지 못한{' '}
        {missing}건은 파일에 들어가지 않아요.{' '}
        <button
          type="button"
          className="icon-btn"
          aria-label="이유 보기"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          ?
        </button>
      </p>
      {open && (
        <ul className="small muted">
          {coverage.not_evaluable_by_version.map((version) => (
            <li key={version.form_version}>
              폼 버전 {version.form_version} 응답 {version.response_count}건은{' '}
              {version.unavailable_conditions.join(', ')} 조건을 확인할 수 없어요.
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * 결과 화면은 `versions`로 버전 범위를 고르고, 탐색 화면은 `context`와 분석의 `coverage`를 넘긴다.
 * `context`가 있으면 범위를 고르지 않는다. 서버가 둘을 함께 받지 않는다.
 */
export function ExportDialog({
  formId,
  versions = [],
  context,
  coverage,
  onClose,
}: {
  formId: string;
  versions?: number[];
  context?: RequestContext;
  coverage?: Coverage | null;
  onClose: () => void;
}) {
  const [format, setFormat] = React.useState<ExportFormat>('csv');
  const [selected, setSelected] = React.useState<number[]>(versions);
  const [busy, setBusy] = React.useState(false);
  const [message, setMessage] = React.useState('');
  const showsRange = !context && versions.length > 0;

  function toggle(version: number) {
    setSelected((current) =>
      current.includes(version)
        ? current.filter((value) => value !== version)
        : [...current, version].sort((a, b) => a - b),
    );
  }

  function requestBody(): ExportRequest {
    if (context) return { format, context };
    return selected.length === versions.length ? { format } : { format, form_versions: selected };
  }

  async function submit() {
    setBusy(true);
    setMessage('');
    try {
      const result = await api.exportForm(formId, requestBody());
      if (result.status === 'clarify') {
        setMessage(`${result.clarify.message} 파일은 만들지 않았어요.`);
        return;
      }
      saveFile(result.file, exportFilename(format));
      onClose();
    } catch (error: unknown) {
      setMessage(failureMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-scrim" role="presentation">
      <div className="modal" role="dialog" aria-label="내보내기">
        <button className="modal-close" onClick={onClose} aria-label="닫기">
          <Icon name="x" />
        </button>
        <h2>내보내기</h2>
        <div className="option-stack export-formats" role="radiogroup" aria-label="파일 형식">
          {formats.map((option) => (
            <label
              className={`option ${format === option.value ? 'checked' : ''}`}
              key={option.value}
            >
              <input
                type="radio"
                name="export-format"
                value={option.value}
                checked={format === option.value}
                onChange={() => setFormat(option.value)}
              />
              {option.label}
            </label>
          ))}
        </div>
        {showsRange && (
          <div className="option-stack export-formats" role="group" aria-label="폼 버전">
            {versions.map((version) => (
              <label
                className={`option ${selected.includes(version) ? 'checked' : ''}`}
                key={version}
              >
                <input
                  type="checkbox"
                  checked={selected.includes(version)}
                  onChange={() => toggle(version)}
                />
                폼 버전 {version}
              </label>
            ))}
          </div>
        )}
        {coverage && coverage.not_evaluable_count > 0 && <CoverageNotice coverage={coverage} />}
        {message && (
          <p className="small" role="alert">
            {message}
          </p>
        )}
        <Button
          onClick={() => void submit()}
          disabled={busy || (showsRange && selected.length === 0)}
        >
          <Icon name="download" size={15} /> {busy ? '내보내는 중' : '파일 받기'}
        </Button>
      </div>
    </div>
  );
}
