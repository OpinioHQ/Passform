import { useRef, useState } from 'react';

import { api, ApiError } from '../api/client';
import type { AnalysisResponse, RequestContext } from '../api/types';
import type { ExampleQuestion } from '../lib/exampleQuestions';
import AnalysisResultCard from './AnalysisResultCard';
import ClarifyCard from './ClarifyCard';
import { Button, Card, Notice } from './ui';

interface Turn {
  id: number;
  question: string;
  response: AnalysisResponse | null;
  error: string | null;
}

function errorMessage(reason: unknown): string {
  if (reason instanceof ApiError) {
    if (reason.code === 'llm_unavailable') {
      return 'AI를 호출하지 못해 결과를 만들지 않았습니다. 잠시 뒤 다시 시도해 주세요.';
    }
    if (reason.status === 403) {
      return '이 폼의 제작자만 분석할 수 있습니다.';
    }
  }
  return '요청을 처리하지 못했습니다.';
}

interface Props {
  formId: string;
  examples: ExampleQuestion[];
  onShowDashboard: () => void;
}

export default function AnalysisPanel({ formId, examples, onShowDashboard }: Props) {
  const [query, setQuery] = useState('');
  const [turns, setTurns] = useState<Turn[]>([]);
  const [pending, setPending] = useState(false);
  const lastTurnId = useRef(0);

  const run = async (question: string, call: () => Promise<AnalysisResponse>) => {
    lastTurnId.current += 1;
    const id = lastTurnId.current;
    setPending(true);
    setTurns((previous) => [{ id, question, response: null, error: null }, ...previous]);
    try {
      const response = await call();
      setTurns((previous) => previous.map((turn) => (turn.id === id ? { ...turn, response } : turn)));
    } catch (reason) {
      const error = errorMessage(reason);
      setTurns((previous) => previous.map((turn) => (turn.id === id ? { ...turn, error } : turn)));
    } finally {
      setPending(false);
    }
  };

  const ask = (text: string) => {
    const trimmed = text.trim();
    if (trimmed && !pending) {
      setQuery('');
      void run(trimmed, () => api.analyzeQuery(formId, trimmed));
    }
  };

  const followUp = (label: string, context: RequestContext) => {
    if (!pending) {
      void run(label, () => api.analyzeContext(formId, context));
    }
  };

  return (
    <div className="grid gap-4">
      <Card>
        <form
          className="flex flex-wrap gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            ask(query);
          }}
        >
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="받은 응답에 대해 물어보세요"
            aria-label="응답에 대해 묻기"
            className="min-h-11 min-w-0 flex-1 basis-64 rounded-lg border border-line px-3 py-2 text-base"
          />
          <Button type="submit" variant="primary" disabled={pending || !query.trim()}>{pending ? '분석 중…' : '묻기'}</Button>
        </form>
        {/* 이 폼에서 실제로 되는 요청만 버튼으로 둔다. 지원하지 않는 요청은 직접 물었을 때만 안내한다. */}
        <div className="mt-3 flex flex-wrap gap-2">
          {examples.map((example) => (
            <button
              key={example.label}
              type="button"
              disabled={pending}
              onClick={() => followUp(example.label, example.context)}
              className="rounded-full border border-line px-3 py-1 text-xs text-muted hover:bg-canvas disabled:opacity-50"
            >
              {example.label}
            </button>
          ))}
        </div>
      </Card>

      {turns.map((turn) => (
        <div key={turn.id} className="grid gap-2">
          <p className="text-sm font-medium">{turn.question}</p>
          {!turn.response && !turn.error ? <p className="text-sm text-muted">분석 중…</p> : null}
          {turn.error ? <Notice tone="danger">{turn.error}</Notice> : null}
          {turn.response?.status === 'clarify' && turn.response.clarify ? (
            <ClarifyCard
              clarify={turn.response.clarify}
              context={turn.response.context}
              disabled={pending}
              onFollowUp={followUp}
              onShowDashboard={onShowDashboard}
            />
          ) : null}
          {turn.response?.status === 'ok' ? <AnalysisResultCard formId={formId} response={turn.response} /> : null}
        </div>
      ))}
    </div>
  );
}
