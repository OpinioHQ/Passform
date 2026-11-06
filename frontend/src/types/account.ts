/** `GET /me` (`docs/SPEC.md` 5절) */
export interface MeResponse {
  user_id: string;
  name: string;
  email: string;
  deadline_reminder: boolean;
}
