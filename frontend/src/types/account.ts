import type { ProfileKey } from './form';
import type { ResponseAnswer } from './response';

/** `GET /me` (`docs/SPEC.md` 5절) */
export interface MeResponse {
  user_id: string;
  name: string;
  email: string;
  deadline_reminder: boolean;
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
