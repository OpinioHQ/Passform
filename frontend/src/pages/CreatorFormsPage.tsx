import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { api } from '../api/client';
import type { CreatedForm } from '../api/types';
import { Card, Chip, Notice, PageTitle } from '../components/ui';
import { formatDateTime } from '../lib/format';

export default function CreatorFormsPage() {
  const [forms, setForms] = useState<CreatedForm[] | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    api
      .createdForms()
      .then((value) => {
        if (active) {
          setForms(value);
        }
      })
      .catch(() => {
        if (active) {
          setFailed(true);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <>
      <PageTitle title="내가 만든 폼" description="응답을 통계로 보거나 AI에게 자연어로 물어봅니다." />
      {failed ? <Notice tone="danger">목록을 불러오지 못했습니다.</Notice> : null}
      {forms?.length === 0 ? <Notice>이 계정이 만든 폼이 없습니다.</Notice> : null}
      <div className="grid gap-3">
        {forms?.map((form) => (
          <Card key={form.form_id} className="flex flex-wrap items-center justify-between gap-3">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="font-medium">{form.title}</h2>
                <Chip>응답 {form.response_count}건</Chip>
              </div>
              <p className="mt-1 text-sm text-muted">마감: {formatDateTime(form.deadline)}</p>
              <a href={form.share_url} className="mt-1 inline-block text-sm text-accent">{form.share_url}</a>
            </div>
            <Link to={`/creator/forms/${form.form_id}`} className="rounded-lg bg-accent px-4 py-2 text-sm text-white">결과 보기</Link>
          </Card>
        ))}
      </div>
    </>
  );
}
