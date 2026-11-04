import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { api, ApiError } from '../api/client';
import type { HistoryItem, SavedForm } from '../api/types';
import { Card, Chip, Notice, PageTitle } from '../components/ui';
import { formatDateTime } from '../lib/format';

function isNearDeadline(deadline: string | null): boolean {
  if (!deadline) {
    return false;
  }
  const hours = (new Date(deadline).getTime() - Date.now()) / 3_600_000;
  return hours > 0 && hours <= 24 * 7;
}

export default function MyFormsPage() {
  const [forms, setForms] = useState<SavedForm[] | null>(null);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([api.myForms(), api.history()])
      .then(([saved, responses]) => {
        if (active) {
          setForms(saved);
          setHistory(responses);
        }
      })
      .catch((reason: unknown) => {
        if (active) {
          setError(reason instanceof ApiError ? reason.message : '불러오지 못했습니다.');
        }
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <>
      <PageTitle title="내 폼함" description="받은 폼의 마감과 제출 상태를 한곳에서 봅니다." />
      {error ? <Notice tone="danger">내 폼함을 불러오지 못했습니다. ({error})</Notice> : null}
      <div className="grid gap-3">
        {forms === null && !error ? <p className="text-sm text-muted">불러오는 중…</p> : null}
        {forms?.length === 0 ? <Notice>아직 저장한 폼이 없습니다.</Notice> : null}
        {forms?.map((form) => (
          <Card key={form.form_id} className="flex flex-wrap items-center justify-between gap-3">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="font-medium">{form.title}</h2>
                <Chip>{form.state === 'submitted' ? '제출함' : '미제출'}</Chip>
                {form.state === 'saved' && isNearDeadline(form.deadline) ? <Chip>마감 임박</Chip> : null}
              </div>
              <p className="mt-1 text-sm text-muted">마감: {formatDateTime(form.deadline)}</p>
            </div>
            <Link
              to={`/forms/${form.form_id}`}
              className="rounded-lg border border-line px-4 py-2 text-sm hover:bg-canvas"
            >
              {form.state === 'submitted' ? '다시 작성' : '작성하기'}
            </Link>
          </Card>
        ))}
      </div>

      <h2 className="mb-3 mt-8 font-semibold">제출 이력</h2>
      {history.length === 0 ? <Notice>제출한 응답이 없습니다.</Notice> : null}
      <div className="grid gap-2">
        {history.map((item) => (
          <Link
            key={item.response_id}
            to={`/me/responses/${item.response_id}`}
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-line bg-surface px-4 py-3 text-sm hover:bg-canvas"
          >
            <span className="font-medium">{item.form_title}</span>
            <span className="text-muted">{formatDateTime(item.submitted_at)}</span>
          </Link>
        ))}
      </div>
    </>
  );
}

