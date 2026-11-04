import { useState } from 'react';

import { api } from '../api/client';
import type { AnalysisResponse, Evidence, OwnerResponse, Summary, Theme } from '../api/types';
import { formatAnswer, formatDateTime, versionLabel } from '../lib/format';
import { countCaption, evidenceLines, interpretedConditions, resultNotes } from '../lib/resultText';
import { Bar, Button, Card, Chip, Explained, Notice } from './ui';

const MATCH_LABELS: Record<string, string> = { any: '하나라도 해당', not: '해당하지 않음' };
const OPERATION_LABELS: Record<string, string> = { count: '인원 수', list: '응답 목록', summarize: '요약' };
const OTHER_THEME = '기타';
const LIST_PAGE = 10;

export default function AnalysisResultCard({ formId, response }: { formId: string; response: AnalysisResponse }) {
  const { context, matched, coverage, result } = response;
  if (!result || !coverage || !context) {
    return null;
  }
  const lines = evidenceLines(matched ?? []);
  return (
    <Card className="grid gap-4">
      {/* 파서가 잘못 이해해도 제작자가 바로 알아보도록 해석을 숫자 위에 먼저 보여 준다. */}
      <div>
        <p className="text-xs text-muted">이렇게 이해했어요</p>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          {context.conditions.length === 0 ? <Chip>전체 응답</Chip> : null}
          {interpretedConditions(context.conditions, matched ?? []).map((condition) => <Chip key={condition}>{condition}</Chip>)}
          {context.match && MATCH_LABELS[context.match] ? <Chip>{MATCH_LABELS[context.match]}</Chip> : null}
          <Chip>{OPERATION_LABELS[result.operation]}</Chip>
          {context.sort ? <Chip>{context.sort.by} {context.sort.order === 'desc' ? '내림차순' : '오름차순'}</Chip> : null}
          {context.target ? <Chip>{context.target}</Chip> : null}
          {context.topic ? <Chip>의견: {context.topic}</Chip> : null}
        </div>
      </div>

      <div>
        <p className="text-3xl font-semibold tabular-nums">{result.count}<span className="ml-1 text-base font-normal">건</span></p>
        <div className="mt-1">
          <Explained notes={resultNotes(response)}>{countCaption(response)}</Explained>
        </div>
      </div>

      {lines.length > 0 ? (
        <details className="text-sm">
          <summary className="cursor-pointer text-muted">어떤 문항으로 찾았나요?</summary>
          <ul className="mt-2 grid gap-1">
            {lines.map((line) => <li key={line}>{line}</li>)}
          </ul>
        </details>
      ) : null}
      {result.operation === 'list' && result.response_ids ? <ResponseList formId={formId} responseIds={result.response_ids} /> : null}
      {result.summary ? <SummaryView summary={result.summary} /> : null}
    </Card>
  );
}

function ResponseList({ formId, responseIds }: { formId: string; responseIds: string[] }) {
  const [shown, setShown] = useState(LIST_PAGE);
  const [open, setOpen] = useState<Record<string, OwnerResponse | 'failed'>>({});

  const toggle = async (responseId: string) => {
    if (open[responseId]) {
      setOpen(({ [responseId]: _closed, ...rest }) => {
        void _closed;
        return rest;
      });
      return;
    }
    try {
      const detail = await api.ownerResponse(formId, responseId);
      setOpen((previous) => ({ ...previous, [responseId]: detail }));
    } catch {
      setOpen((previous) => ({ ...previous, [responseId]: 'failed' }));
    }
  };

  if (responseIds.length === 0) {
    return <Notice>일치한 응답이 없습니다.</Notice>;
  }
  return (
    <div>
      <ul className="grid gap-2">
        {responseIds.slice(0, shown).map((responseId, index) => {
          const detail = open[responseId];
          return (
            <li key={responseId} className="rounded-lg border border-line text-sm">
              <button type="button" onClick={() => void toggle(responseId)} className="flex min-h-11 w-full items-center justify-between px-3 py-2 text-left hover:bg-canvas">
                <span>응답 {index + 1}</span>
                <span className="text-xs text-muted">{detail ? '접기' : '답 보기'}</span>
              </button>
              {detail === 'failed' ? <p className="px-3 pb-3 text-danger">답을 불러오지 못했습니다.</p> : null}
              {detail && detail !== 'failed' ? (
                <dl className="grid gap-2 border-t border-line px-3 py-3">
                  <p className="text-xs text-muted">{formatDateTime(detail.submitted_at)} 제출 · {versionLabel([detail.form_version])}</p>
                  {detail.content.map((answer) => (
                    <div key={answer.question_id}>
                      <dt className="text-xs text-muted">{answer.question_note}</dt>
                      <dd className="whitespace-pre-wrap">{formatAnswer(answer.value)}</dd>
                    </div>
                  ))}
                </dl>
              ) : null}
            </li>
          );
        })}
      </ul>
      {shown < responseIds.length ? (
        <Button className="mt-2" onClick={() => setShown(shown + LIST_PAGE)}>더 보기 ({responseIds.length - shown}건 남음)</Button>
      ) : null}
    </div>
  );
}

const SUMMARY_STATUS: Record<Summary['status'], string | null> = {
  ok: null,
  no_matches: '조건에 일치한 응답이 없어 요약하지 않았습니다.',
  no_consented_responses: '일치한 응답이 모두 AI 요약에 동의하지 않아 요약하지 않았습니다.',
  insufficient_evidence: '이 의견과 관련된 답을 충분히 찾지 못해 요약을 만들지 않았습니다.',
  too_many_responses: '요약할 응답이 너무 많습니다. 조건을 좁혀 다시 요청해 주세요.',
};

function SummaryView({ summary }: { summary: Summary }) {
  const notice = SUMMARY_STATUS[summary.status];
  return (
    <div className="grid gap-4 border-t border-line pt-4">
      {notice ? <Notice tone="warn">{notice}</Notice> : null}
      {summary.topic && summary.status === 'ok' ? (
        <div>
          <h3 className="font-medium">"{summary.topic.query}" 관련 의견</h3>
          <p className="mt-1 text-sm leading-relaxed">{summary.topic.text}</p>
          <EvidenceList evidence={summary.topic.evidence} />
        </div>
      ) : null}
      {summary.items.map((item) => (
        <div key={`${item.type}-${item.target.question_id}-${item.target.question_note}`}>
          <h3 className="font-medium">{item.target.question_note}</h3>
          <p className="mb-2 text-xs text-muted">{item.type === 'themes' ? '답을 주제별로 묶었어요' : '선택지별 응답 수'} · {versionLabel(item.target.form_versions)}</p>
          {item.themes?.length === 0 ? <p className="text-sm text-muted">요약할 답이 없습니다.</p> : null}
          <div className="grid gap-3">
            {item.themes?.map((theme) => <ThemeCard key={theme.label} theme={theme} />)}
            {item.options?.map((option) => (
              <Bar key={option.label} label={option.label} count={option.count} max={Math.max(1, ...(item.options ?? []).map((other) => other.count))} />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

function ThemeCard({ theme }: { theme: Theme }) {
  const emptyText = theme.label === OTHER_THEME ? '한 주제로 묶기 어려운 답을 모았어요.' : '이 주제를 요약할 만큼 비슷한 답을 찾지 못했어요.';
  return (
    <div className="rounded-lg border border-line p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium">{theme.label}</span>
        <span className="text-sm tabular-nums">{theme.count}건</span>
      </div>
      {theme.text ? <p className="mt-1 text-sm leading-relaxed">{theme.text}</p> : <p className="mt-1 text-xs text-muted">{emptyText}</p>}
      <EvidenceList evidence={theme.evidence} />
    </div>
  );
}

function EvidenceList({ evidence }: { evidence: Evidence[] }) {
  if (evidence.length === 0) {
    return null;
  }
  return (
    <details className="mt-2 text-sm">
      <summary className="cursor-pointer text-xs text-muted">실제 답 {evidence.length}개 보기</summary>
      <ul className="mt-1 grid gap-1">
        {evidence.map((item) => (
          <li key={`${item.response_id}-${item.question_id}`} className="border-l-2 border-line pl-2">{item.excerpt}</li>
        ))}
      </ul>
    </details>
  );
}
