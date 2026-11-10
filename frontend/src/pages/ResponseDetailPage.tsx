import { responseAttachmentUrl } from '../api/client';
import { Heading, Panel } from '../components/UI';
import type { ResponseDetail } from '../types/api';
import {
  type FileLink,
  filesOfQuestion,
  filesWithoutAnswer,
  ResponseFileList,
} from './ResponseFileList';
import { versionLabel } from './ResultsPage';

/**
 * 표시는 Asia/Seoul이다(AGENTS 5절). 예: "2026. 10. 8. 오전 11:30:00".
 * 실행 환경에 한국어 로캘 데이터가 없으면 "AM"이 나오므로 숫자만 받아 "오전/오후"를 직접 붙인다.
 */
export function seoulTime(iso: string): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(iso));
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? '';
  const hour = Number(part('hour'));
  const period = hour < 12 ? '오전' : '오후';
  const hour12 = hour % 12 === 0 ? 12 : hour % 12;
  return `${part('year')}. ${part('month')}. ${part('day')}. ${period} ${hour12}:${part('minute')}:${part('second')}`;
}

/** 제작자가 보는 응답 한 건(SPEC 236행, AC62). 문구는 그 응답의 제출 버전 것이다. 응답 ID는 보이지 않는다. */
export function ResponseDetailPage({
  detail,
  formId,
}: {
  detail: ResponseDetail;
  /** 있으면 첨부를 내려받을 수 있다. */
  formId?: string;
}) {
  const linkOf: FileLink = formId
    ? (attachmentId) => responseAttachmentUrl(formId, detail.response_id, attachmentId)
    : null;
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
                  <ResponseFileList
                    files={filesOfQuestion(detail.attachments, a.question_id)}
                    linkOf={linkOf}
                  />
                </dd>
              </div>
            ))}
          </dl>
        )}
        <ResponseFileList
          files={filesWithoutAnswer(detail.content, detail.attachments)}
          linkOf={linkOf}
        />
      </Panel>
    </div>
  );
}
