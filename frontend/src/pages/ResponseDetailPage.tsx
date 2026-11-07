import { Heading, Panel } from '../components/UI';
import type { ResponseDetail } from '../types/api';
import { versionLabel } from './ResultsPage';

/** 표시는 Asia/Seoul이다(AGENTS 5절). */
export function seoulTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' });
}

/** 제작자가 보는 응답 한 건(SPEC 236행, AC62). 문구는 그 응답의 제출 버전 것이다. 응답 ID는 보이지 않는다. */
export function ResponseDetailPage({ detail }: { detail: ResponseDetail }) {
  return (
    <div className="page-shell">
      <Heading
        eyebrow="제작자 워크스페이스 / 응답 상세"
        title="응답 상세"
        desc={`${versionLabel([detail.form_version])}의 문항으로 제출된 응답이에요.`}
      />
      <Panel>
        <p className="muted">
          제출 {seoulTime(detail.submitted_at)}
          {detail.updated_at && ` · 수정 ${seoulTime(detail.updated_at)}`}
        </p>
        {detail.content.length === 0 ? (
          <p className="muted">답한 문항이 없어요.</p>
        ) : (
          <dl className="answer-list">
            {detail.content.map((a) => (
              <div key={a.question_id} className="answer-line">
                <dt>
                  <small>{a.question_note}</small>
                </dt>
                <dd>
                  <b>{Array.isArray(a.value) ? a.value.join(', ') : a.value}</b>
                </dd>
              </div>
            ))}
          </dl>
        )}
      </Panel>
    </div>
  );
}
