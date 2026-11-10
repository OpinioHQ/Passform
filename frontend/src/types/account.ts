import type { ProfileKey } from './form';
import type { ResponseAnswer } from './response';

/** `GET /me` (`docs/SPEC.md` 5절) */
export interface MeResponse {
  user_id: string;
  name: string;
  email: string;
  deadline_reminder: boolean;
}

/** `PATCH /me/settings`의 입력(SPEC 101행). 빠진 값은 서버가 유지한다. */
export interface SettingsInput {
  deadline_reminder?: boolean;
}

/** `GET /me/notifications`의 항목(SPEC 201행). 순서는 서버가 정한다(created_at 내림차순 → id 오름차순). */
export interface NotificationView {
  notification_id: string;
  form_id: string;
  type: 'deadline';
  created_at: string;
  read: boolean;
}

/** `GET /me/storage` (SPEC 100행) */
export interface StorageUsage {
  used_bytes: number;
  limit_bytes: number;
}

export type PersonalInfo = Partial<Record<ProfileKey, string>>;

/** `GET /me/profiles`의 항목(SPEC 102행). */
export interface Profile {
  profile_id: string;
  label: string;
  personal_info: PersonalInfo;
}

/** `POST /me/profiles`, `PUT /me/profiles/{profile_id}`의 입력(SPEC 103행). */
export interface ProfileInput {
  label: string;
  personal_info: PersonalInfo;
}

export interface CreatedProfile {
  profile_id: string;
}

/** `POST /forms/{form_id}/autofill`의 출력(SPEC 106행). 저장·제출하지 않는 제안이다. */
export interface AutofillResult {
  answers: ResponseAnswer[];
  unfilled: string[];
}
