import React from 'react';
import { responseAttachmentUrl, useLiveApi } from '../api/client';
import { Badge, Button } from '../components/UI';
import type { ResponseDetail } from '../types/api';
import { paths } from '../routes/paths';
import {
  type FileLink,
  filesOfQuestion,
  filesWithoutAnswer,
  ResponseFileList,
} from './ResponseFileList';
import { versionLabel } from './versionLabel';

/** 목록은 이만큼씩 상세를 읽는다. 순서는 result.response_ids 그대로다(AC62). */
const LIST_PAGE = 20;

const answerText = (value: string | string[]) => (Array.isArray(value) ? value.join(', ') : value);

/**
 * 응답 목록. AI 결과의 `list`와 결과 대시보드의 응답별 보기가 함께 쓴다.
 * 이미 불러온 상세의 첨부를 문항별로 보이고(SPEC 5절 198행),
 * 응답마다 상세 화면을 새 탭으로 연다(사람 결정: 결과 화면의 질문과 목록을 잃지 않게).
 * `formId`가 없거나 가짜 데이터 모드면 내려받기 링크 없이 이름만 보인다.
 */
export function ListResult({
  ids,
  loadResponse,
  formId,
}: {
  ids: string[];
  loadResponse: (responseId: string) => Promise<ResponseDetail>;
  formId?: string;
}) {
  const [shown, setShown] = React.useState(Math.min(LIST_PAGE, ids.length));
  const [details, setDetails] = React.useState<Map<string, ResponseDetail | 'failed'>>(new Map());
  React.useEffect(() => {
    let active = true;
    const pending = ids.slice(0, shown).filter((id) => !details.has(id));
    if (pending.length === 0) return;
    Promise.all(
      pending.map((id) =>
        loadResponse(id).then(
          (detail): [string, ResponseDetail | 'failed'] => [id, detail],
          (): [string, ResponseDetail | 'failed'] => [id, 'failed'],
        ),
      ),
    ).then((loaded) => {
      if (!active) return;
      setDetails((prev) => {
        const next = new Map(prev);
        for (const [id, detail] of loaded) next.set(id, detail);
        return next;
      });
    });
    return () => {
      active = false;
    };
  }, [ids, shown, details, loadResponse]);
  const fileLink = (responseId: string): FileLink =>
    useLiveApi && formId
      ? (attachmentId) => responseAttachmentUrl(formId, responseId, attachmentId)
      : null;
  return (
    <>
      <ol className="explore-list">
        {ids.slice(0, shown).map((id, index) => {
          const detail = details.get(id);
          return (
            <li key={id} className="explore-response">
              <div className="explore-response-head">
                <b>응답 {index + 1}</b>
                {detail && detail !== 'failed' && (
                  <Badge tone="blue">{versionLabel([detail.form_version])}</Badge>
                )}
                {formId && (
                  <a
                    className="small"
                    href={paths.formResponse(formId, id)}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    응답 {index + 1} 상세 보기
                  </a>
                )}
              </div>
              {detail === undefined && <p className="muted small">불러오는 중이에요.</p>}
              {detail === 'failed' && <p className="muted small">이 응답을 불러오지 못했어요.</p>}
              {detail && detail !== 'failed' && (
                <>
                  <dl>
                    {detail.content.map((item) => (
                      <div key={item.question_id}>
                        <dt>{item.question_note}</dt>
                        <dd>
                          {answerText(item.value) || '—'}
                          <ResponseFileList
                            files={filesOfQuestion(detail.attachments, item.question_id)}
                            linkOf={fileLink(id)}
                          />
                        </dd>
                      </div>
                    ))}
                  </dl>
                  <ResponseFileList
                    files={filesWithoutAnswer(detail.content, detail.attachments)}
                    linkOf={fileLink(id)}
                  />
                </>
              )}
            </li>
          );
        })}
      </ol>
      {shown < ids.length && (
        <Button
          variant="secondary"
          onClick={() => setShown(Math.min(shown + LIST_PAGE, ids.length))}
        >
          더 보기
        </Button>
      )}
    </>
  );
}
