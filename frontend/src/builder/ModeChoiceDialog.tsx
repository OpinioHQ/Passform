import React from 'react';
import { Icon } from '../components/UI';

/** "새 폼 만들기"를 누르면 기본·빠른 모드를 고른다(사람 결정, #58). 두 모드는 같은 폼 정의로 저장한다(AC42). */
export function ModeChoiceDialog({
  onBasic,
  onQuick,
  onClose,
}: {
  onBasic: () => void;
  onQuick: () => void;
  onClose: () => void;
}) {
  React.useEffect(() => {
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [onClose]);
  return (
    <div className="modal-scrim" role="presentation" onClick={onClose}>
      <div
        className="modal mode-choice"
        role="dialog"
        aria-modal="true"
        aria-label="만드는 방식 고르기"
        onClick={(event) => event.stopPropagation()}
      >
        <button className="modal-close" onClick={onClose} aria-label="닫기">
          <Icon name="x" />
        </button>
        <h2>어떻게 만들까요?</h2>
        <button type="button" className="mode-choice-option" onClick={onQuick}>
          <strong>빠른 모드</strong>
          <span className="small muted">목적과 대상을 고르면 알맞은 문항을 추천해 드려요.</span>
        </button>
        <button type="button" className="mode-choice-option" onClick={onBasic}>
          <strong>기본 모드</strong>
          <span className="small muted">섹션과 분기까지 직접 구성해요.</span>
        </button>
      </div>
    </div>
  );
}
