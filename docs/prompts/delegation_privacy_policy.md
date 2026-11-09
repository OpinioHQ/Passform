# 개인정보처리방침 위임 기록 (#48)

작성: 프롬프트 세션 초안을 사람이 검토해 발행. 2026-11-09 실행.

## 목적

Google 로그인을 프로덕션으로 게시하려면 개인정보처리방침 URL이 필요하다. 저장소 루트 `PRIVACY.md`를 `main`에 먼저 넣고, 그 GitHub 주소를 Google 콘솔에 넣는다. README 사용자 가이드(#48) 브랜치와 따로 작은 PR로 올린다.

## 사람이 정한 것

- 위치는 저장소 루트 `PRIVACY.md` 하나. 그 밖의 파일은 바꾸지 않는다.
- 맨 위에 "법률 자문을 받지 않은 학생 프로젝트의 안내" 한 줄.
- (가) Passform이 직접 처리하는 정보와 (나) 폼 응답을 나눈다. 폼 응답은 폼별 동의가 따로 있고 이 방침이 그것을 대신하지 않는다.
- 외부 처리: Google, AWS(서울), Vercel, OpenAI. 위치·보유 기간은 저장소 근거가 있는 것만 쓰고 없으면 "확인 필요".
- 제3자 제공은 하지 않는다. 폼 응답이 제작자에게 보이는 것과 구분한다.
- 없는 기능이나 하지 않는 일을 약속하지 않는다.
- (2회차, 사람이 PRIVACY.md에 직접 넣음) 시행일 2026-11-11, 운영 종료일 2026-12-09, 문의처 이메일.
- (2회차, 추천안 승인) 계정 삭제는 문의처로 로그인한 Google 이메일과 함께 요청하면 계정·프로필·내 폼함 기록을 지운다.
- (2회차, 추천안 승인) 종료일 뒤 서버 DB와 백업을 모두 지우고 서비스를 닫는다. 폼 응답도 함께 지운다는 것을 4절에 따로 적고, V2 동의 템플릿 문구는 바꾸지 않는다.
- (2회차, 추천안 승인) Google·Vercel의 처리 위치·보유 기간은 각 회사 개인정보처리방침을 따른다고 쓰고 링크를 단다.
- (2회차, 승인) 탐색 질문을 OpenAI로 보낼 때의 보유 기간도 AI 요약 고지와 같은 제공사 정책 문구로 둔다.

## 문장별 근거

| PRIVACY.md | 근거 |
|---|---|
| 1절 시험 서비스, 운영 기간 | README 프로젝트 소개, 사람 지시(11/11 시연, 11/18 동료 사용, 교수 평가) |
| 1절 주소 | `deploy/README.md` 6~9행 |
| 2절 폼별 동의 후 제출 | SPEC 37행, 210행, `ConsentNotices.REFUSAL_NOTICE` |
| 3절 계정 항목 | `User.java`(name, email, oauth_provider, oauth_subject, created_at, deadline_reminder), `application.properties` 23행(scope openid·email·profile), SPEC 99행 |
| 3절 알림 기본값 켜기 | `User.java` 48행, SPEC 101행 |
| 3절 프로필 7개 항목 | `PersonalInfo.java` 17행 `ALLOWED_KEYS`, SPEC 103·104행 |
| 3절 프로필 직접 삭제 | `ProfileController` `DELETE /me/profiles/{profile_id}` |
| 3절 내 폼함 기록 | `SavedForm`, `InboxService` 69행, `DraftService` 89행, `SubmissionService` 115행 |
| 3절 자동채우기는 저장·제출 안 함 | SPEC 105행, AGENTS 3절 1항 |
| 3절 계정 삭제 기능 없음 | 컨트롤러의 `/me` 매핑에 삭제 없음(`AccountController`) |
| 4절 목적·보유 문구 | `V2__insert_consent_default.sql` purpose, retention 그대로 |
| 4절 제작자만 열람 | AGENTS 3절 1항, SPEC 234~239행 인증 열 |
| 4절 작성 중 답 | SPEC 40행, `frontend/src/response/draftSync.ts` 4행 |
| 5절 AWS 서울, 서버 한 대 | ARCHITECTURE 160행 |
| 5절 백업 하루 1번 7일 | `deploy/backup.sh` 2~3·16행, `deploy/README.md` 79·85행 |
| 5절 Vercel은 화면 파일만 | `frontend/.env.production`(API 주소 api.passform.kr), `frontend/src/api/client.ts` 35행 |
| 5절 OpenAI 처리 위치·보유 | SPEC 211행, `ConsentNotices.aiSummaryNotice()` 그대로 |
| 5절 AI 요약 대상·마스킹 | SPEC 24·211행, AGENTS 3절 1항·5절, `SummaryWriter` 15행, `ThemeClassifier` 50행 |
| 5절 탐색 질문 | `OpenAiQueryParser` 30행, `LlmConditionMatcher` 23행 |
| 8절 쿠키 두 개와 속성 | `SecurityConfig` 33·60~68행, `application.properties` 26·27행, `deploy/README.md` 57행. 만료 설정이 없어 세션 쿠키 |
| 8절 광고·분석 없음 | `frontend` 소스·`index.html`·`package.json`에 분석 도구 없음(검색) |
| 8절 비로그인 초안 localStorage, 제출 시 삭제 | `draftSync.ts` 4·30행, `DraftGate.test.tsx` 208행 |
| 9절 HTTPS, DB 비공개, 비밀값 | ARCHITECTURE 160·161행, `deploy/README.md` 14·33행 |
| 9절 접근 권한 | `SecurityConfig` 34~43행, AGENTS 3절 1항 |

## 회차

1. 초안 작성, 로컬 커밋. 종료일·연락처·계정 삭제·Google·Vercel 위치를 "확인 필요"로 두고 사람에게 물음.
2. 사람이 시행일·종료일·문의처를 직접 넣음. 나머지는 추천안대로 반영하고 날짜 표기(종료일, 변경 이력)를 맞춤.
3. 사람 결정: 데모 뒤에도 종료일 없이 운영하고, 닫을 때는 최소 14일 전에 이 문서와 화면에 알린 뒤 DB와 백업을 지운다. 2회차의 종료일(2026-12-09)과 종료 뒤 삭제 문구를 이것으로 바꿈.

## 확인 필요로 남긴 것

- 없음. Google·Vercel의 실제 처리 위치는 저장소에 근거가 없어 각 회사 정책 링크로 대신했다.

## 미룸

- 파일 첨부(S3)는 `main`에서 아직 쓰지 않아 넣지 않았다. T12(#63)가 머지되면 5절에 더한다.
- 마감 알림 메일 발송(E12, #57)이 머지되면 메일 발송 수단을 5절에 더한다.
