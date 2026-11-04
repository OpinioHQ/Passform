import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';

import { api, ApiError } from '../api/client';
import type { PublicForm, Results } from '../api/types';
import AnalysisPanel from '../components/AnalysisPanel';
import { Bar, Button, Card, Notice, PageTitle } from '../components/ui';
import { buildExamples } from '../lib/exampleQuestions';
import { versionLabel } from '../lib/format';

type Tab = 'ai' | 'combined' | 'versions';

export default function FormResultsPage() {
  const { formId = '' } = useParams();
  const [results, setResults] = useState<Results | null>(null);
  const [form, setForm] = useState<PublicForm | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('ai');
  const examples = useMemo(() => (results ? buildExamples(results, form) : []), [results, form]);

  // 요약 버튼을 만들 때만 쓴다. 마감된 폼은 공개 조회가 409이므로 요약 버튼 없이 진행한다.
  useEffect(() => {
    let active = true;
    api
      .formById(formId)
      .then((value) => {
        if (active) {
          setForm(value);
        }
      })
      .catch(() => {
        if (active) {
          setForm(null);
        }
      });
    return () => {
      active = false;
    };
  }, [formId]);

  useEffect(() => {
    let active = true;
    api
      .results(formId)
      .then((value) => {
        if (active) {
          setResults(value);
        }
      })
      .catch((reason: unknown) => {
        if (active) {
          setError(
            reason instanceof ApiError && reason.status === 403
              ? '이 폼의 제작자만 결과를 볼 수 있습니다.'
              : '결과를 불러오지 못했습니다.',
          );
        }
      });
    return () => {
      active = false;
    };
  }, [formId]);

  if (error) {
    return <Notice tone="danger">{error}</Notice>;
  }
  if (!results) {
    return <p className="text-sm text-muted">불러오는 중…</p>;
  }
  return (
    <>
      <PageTitle title={results.title} description={`모든 버전의 제출 응답 ${results.response_count}건`} />
      <div className="mb-4 flex flex-wrap gap-2">
        <Button variant={tab === 'ai' ? 'primary' : 'secondary'} onClick={() => setTab('ai')}>AI에게 묻기</Button>
        <Button variant={tab === 'combined' ? 'primary' : 'secondary'} onClick={() => setTab('combined')}>통합 통계</Button>
        <Button variant={tab === 'versions' ? 'primary' : 'secondary'} onClick={() => setTab('versions')}>버전별 통계</Button>
      </div>
      {/* 탭을 바꿔도 질문 기록이 남도록 AI 패널은 숨기기만 한다. */}
      <div className={tab === 'ai' ? '' : 'hidden'}>
        <AnalysisPanel formId={formId} examples={examples} onShowDashboard={() => setTab('versions')} />
      </div>
      {tab === 'combined' ? <CombinedStats results={results} /> : null}
      {tab === 'versions' ? <VersionStats results={results} /> : null}
    </>
  );
}

function CombinedStats({ results }: { results: Results }) {
  return (
    <div className="grid gap-3">
      {results.combined.questions.map((question) => {
        const max = Math.max(1, ...question.options.map((option) => option.count));
        return (
          <Card key={`${question.question_id}-${question.question_note}`}>
            <h2 className="font-medium">{question.question_note}</h2>
            <p className="mb-3 text-xs text-muted">{versionLabel(question.form_versions)}</p>
            <div className="grid gap-3">
              {question.options.map((option) => (
                <Bar
                  key={option.label}
                  label={option.label}
                  count={option.count}
                  max={max}
                  hint={option.form_versions.length < question.form_versions.length ? `${versionLabel(option.form_versions)}에만 있음` : undefined}
                />
              ))}
            </div>
          </Card>
        );
      })}
    </div>
  );
}

function VersionStats({ results }: { results: Results }) {
  return (
    <div className="grid gap-5">
      {results.versions.map((version) => (
        <div key={version.form_version} className="grid gap-3">
          <h2 className="font-semibold">버전 {version.form_version} · 응답 {version.response_count}건</h2>
          {version.questions.map((question) => {
            const entries = Object.entries(question.counts);
            const max = Math.max(1, ...entries.map(([, count]) => count));
            return (
              <Card key={question.question_id}>
                <h3 className="mb-3 font-medium">{question.question_note}</h3>
                <div className="grid gap-3">
                  {entries.map(([label, count]) => <Bar key={label} label={label} count={count} max={max} />)}
                </div>
              </Card>
            );
          })}
        </div>
      ))}
    </div>
  );
}
