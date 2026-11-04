import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { api } from '../api/client';
import type { ResponseDetail } from '../api/types';
import { Card, Notice, PageTitle } from '../components/ui';
import { formatAnswer, formatDateTime } from '../lib/format';

export default function HistoryDetailPage() {
  const { responseId = '' } = useParams();
  const [detail, setDetail] = useState<ResponseDetail | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    api
      .historyDetail(responseId)
      .then((value) => {
        if (active) {
          setDetail(value);
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
  }, [responseId]);

  if (failed) {
    return <Notice tone="danger">이 제출 내역을 볼 수 없습니다. 본인이 제출한 응답만 열람할 수 있습니다.</Notice>;
  }
  if (!detail) {
    return <p className="text-sm text-muted">불러오는 중…</p>;
  }
  return (
    <>
      <PageTitle
        title={detail.form_title}
        description={`제출: ${formatDateTime(detail.submitted_at)} · 제출 당시 폼 버전 ${detail.form_version}`}
      />
      <Card>
        <dl className="grid gap-4">
          {detail.content.map((answer) => (
            <div key={answer.question_id}>
              <dt className="text-sm text-muted">{answer.question_note}</dt>
              <dd className="mt-1 whitespace-pre-wrap">{formatAnswer(answer.value)}</dd>
            </div>
          ))}
        </dl>
      </Card>
      <Link to="/me/forms" className="mt-4 inline-block text-sm text-accent">내 폼함으로</Link>
    </>
  );
}
