import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { deadlineText } from '../pages/ResponsePage';
import { paths } from '../routes/paths';
import type { NotificationView, SavedFormSummary } from '../types/api';
import { Icon } from './UI';

interface Props {
  /** 열림 상태는 헤더가 정한다. 계정 메뉴와 동시에 열리지 않는다. */
  open: boolean;
  onToggle: () => void;
  onClose: () => void;
}

/**
 * 로그인한 사용자의 마감 알림(AC18, SPEC 201~202행). 화면을 옮길 때마다 목록을 다시 읽어 안 읽은 개수를 보인다.
 * 알림에는 폼 제목·마감이 없어 목록을 열 때 내 폼함(`GET /me/forms`)에서 찾는다. 알림 대상은 내 폼함에 저장한 폼이다.
 * 알림 켜기·끄기는 여기 두지 않고 계정 메뉴에 둔다(사람 결정).
 */
export function NotificationBell({ open, onToggle, onClose }: Props) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [items, setItems] = React.useState<NotificationView[] | null>(null);
  const [forms, setForms] = React.useState<Map<string, SavedFormSummary> | null>(null);
  const [problem, setProblem] = React.useState('');

  React.useEffect(() => {
    let active = true;
    api.notifications().then(
      (list) => {
        if (active) setItems(list);
      },
      () => undefined,
    );
    return () => {
      active = false;
    };
  }, [pathname]);

  React.useEffect(() => {
    if (!open) return;
    let active = true;
    api.savedForms().then(
      (list) => {
        if (active) setForms(new Map(list.map((form) => [form.form_id, form])));
      },
      () => {
        if (active) setForms(new Map());
      },
    );
    return () => {
      active = false;
    };
  }, [open]);

  const unread = items?.filter((n) => !n.read).length ?? 0;

  async function openNotification(notification: NotificationView) {
    setProblem('');
    if (!notification.read) {
      try {
        await api.readNotification(notification.notification_id);
        setItems((list) =>
          (list ?? []).map((n) =>
            n.notification_id === notification.notification_id ? { ...n, read: true } : n,
          ),
        );
      } catch {
        setProblem('알림을 읽음으로 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.');
        return;
      }
    }
    if (forms?.has(notification.form_id)) {
      onClose();
      navigate(paths.form(notification.form_id));
    }
  }

  return (
    <>
      <button
        className="icon-btn notification-bell"
        aria-label={unread > 0 ? `마감 알림, 안 읽은 알림 ${unread}개` : '마감 알림'}
        aria-expanded={open}
        onClick={onToggle}
      >
        <Icon name="bell" />
        {unread > 0 && (
          <span className="notification-count" aria-hidden="true">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="account-popover reminder-popover notification-popover">
          <h2 className="notification-heading">알림</h2>
          {items === null || (items.length > 0 && forms === null) ? (
            <p className="muted">알림을 불러오는 중이에요.</p>
          ) : items.length === 0 ? (
            <p className="muted">아직 알림이 없어요.</p>
          ) : (
            <ul className="notification-list">
              {items.map((n) => {
                const form = forms?.get(n.form_id);
                return (
                  <li key={n.notification_id}>
                    <button
                      type="button"
                      className={`notification-item ${n.read ? '' : 'unread'}`}
                      onClick={() => void openNotification(n)}
                    >
                      <span className="notification-title">
                        {form ? form.title : '열 수 없는 폼이에요'}
                      </span>
                      {form && <span className="muted small">{deadlineText(form.deadline)}</span>}
                      {!n.read && <span className="notification-unread">안 읽음</span>}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          {problem && (
            <p className="error" role="alert">
              {problem}
            </p>
          )}
        </div>
      )}
    </>
  );
}
