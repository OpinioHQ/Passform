# 라우트와 API 매핑

기준: Figma `Omj6sDT7BKq5ExD7ukJw9F`, `ver1(blue color)` 주요 프레임. API는 `docs/SPEC.md` 5절이 정본이므로 변경 전 다시 확인한다.

| 경로                                           | 화면         | Figma node            | 연동할 서버 API                                                          | 현재 상태                   | 담당 이슈 |
| ---------------------------------------------- | ------------ | --------------------- | ------------------------------------------------------------------------ | --------------------------- | --------- |
| `/`                                            | 랜딩페이지   | `34:75`               | 없음                                                                     | 정적 반응형 화면            | —         |
| `/login`                                       | 로그인       | —                     | `GET /oauth2/authorization/{provider}`                                   | Google 로그인 연결          | T2 #50    |
| `/me/forms`                                    | 내 폼함      | `5:6468`              | `GET /me/forms`, `POST /me/saved-forms`                                  | 가짜 데이터 (아래 주 1)     | E5 #43    |
| `/me/responses`                                | 제출 이력    | `5:6811`              | `GET /me/responses`                                                      | 가짜 데이터                 | E5 #43    |
| `/me/responses/:responseId`                    | 이력 상세    | `5:6811`              | `GET /me/responses/{response_id}`                                        | 가짜 데이터                 | E5 #43    |
| `/me/profile`                                  | 내 프로필    | Figma 메뉴/자동채우기 | `GET /me/profiles` 등                                                    | 가짜 데이터                 | T9 #60    |
| `/f/:slug`                                     | 응답 작성    | `5:6640`              | `GET /f/{slug}`, `POST /forms/{form_id}/responses`, 응답 초안 API        | 가짜 데이터 (아래 주 1)     | T4 #52    |
| `/forms/:formId`                               | 응답 작성    | `5:6640`              | `GET /forms/{form_id}`, `POST /forms/{form_id}/responses`, 응답 초안 API | 가짜 데이터 (아래 주 1)     | T4 #52    |
| `/creator`                                     | 내가 만든 폼 | Figma 메뉴/폼 편집    | `GET /me/created-forms`                                                  | 가짜 데이터                 | E3 #41    |
| `/creator/forms/new`                           | 빌더(새 폼)  | `5:6940`              | `POST /forms`, `PATCH /forms/{form_id}`                                  | 빈 폼, 저장 연결 필요       | E3 #41    |
| `/creator/forms/:formId/edit`                  | 빌더(편집)   | `5:6940`              | `GET /forms/{form_id}/definition`, `PATCH /forms/{form_id}`              | 가짜 데이터, 저장 연결 필요 | E3 #41    |
| `/creator/forms/:formId/results`               | 결과         | —                     | `GET /forms/{form_id}/results`                                           | 실제 API (아래 주 4)        | T6 #54    |
| `/creator/forms/:formId/responses/:responseId` | 응답 상세    | —                     | `GET /forms/{form_id}/responses/{response_id}`                           | 실제 API (아래 주 4)        | T6 #54    |
| `/creator/forms/:formId/explore`               | 응답 탐색    | `5:7219`              | `POST /forms/{form_id}/analyze`, `POST /forms/{form_id}/export`          | 가짜 데이터                 | E8 #46    |
| 그 밖의 경로                                   | 찾을 수 없음 | —                     | 없음                                                                     | `/`로 가는 안내 화면        | —         |

주 1: `VITE_USE_API=true`일 때 이 화면만 실제로 호출한다. `/me/forms`는 `GET /me/forms`, `/f/:slug`는 `GET /f/{slug}`, `/forms/:formId`는 `GET /forms/{form_id}`, 응답 제출은 `POST /forms/{form_id}/responses`다. 공개 경로의 화면은 로그인 전용 API를 부르지 않는다. 예외로 헤더는 로그인 상태를 표시하려고 모든 화면에서 `GET /me`를 부른다(비로그인이면 401이고 로그인 링크를 보인다).

주 2: 헤더 종 아이콘의 "마감 하루 전 알림" 스위치는 화면 상태로만 동작한다. 서버 저장(`PATCH /me/settings`의 `deadline_reminder`)과 알림 목록은 E12 #57이 연결한다.

주 3: 탐색 화면의 내보내기 창은 표에 보이는 가상 응답을 CSV로 받는 데모다. 실제 JSON·YAML·CSV 내보내기와 범위 선택(전체 응답, AI 필터 결과)은 T11 #62가 연결한다.

주 4: 결과와 응답 상세는 가짜 데이터가 없다. `VITE_USE_API=true`일 때만 서버를 부르고, `false`이면 API를 부르지 않고 안내 문구만 보인다. 목록에서 응답 상세로 가는 링크는 E6·E7이 단다.

## 공통 파일 규칙

- 공통 파일(`src/routes/paths.ts`, `src/App.tsx`, `src/components/`, `src/api/client.ts`, `src/types/`)을 바꿀 때는 작은 PR로 먼저 올린다(`AGENTS.md` 5절).
- 경로는 `src/routes/paths.ts` 한 곳에 두고, 라우트 등록은 `src/App.tsx`에 한다. 화면에서 주소를 문자열로 쓰지 않고 `paths`를 쓴다.
- 라우트와 화면의 연결은 `src/routes/respondentRoutes.tsx`(응답자)와 `src/routes/creatorRoutes.tsx`(제작자)에 둔다.
- 공통 레이아웃은 `src/components/AppLayout.tsx`다. 화면이 상단 알림을 띄울 때는 `useNotice()`를 쓴다.
- 헤더 메뉴는 모든 사용자에게 같다. 화면에서 권한을 판정하지 않고 라우트 접근도 막지 않는다. 로그인 상태는 헤더가 `GET /me`로 표시한다.
- API 호출은 `src/api/client.ts`에만 둔다. CSRF 헤더는 `csrfHeaders()` 한 곳에서 `XSRF-TOKEN` 쿠키를 옮겨 넣는다.
- 가짜 데이터는 `src/demo/`에만 둔다. 화면을 서버 데이터로 바꾼 담당자는 그 값을 지우고, 가짜 값이 없어진 경로를 `src/demo/demoRoutes.ts`에서 뺀다.
- 타입 필드 이름은 백엔드 DTO와 같은 snake_case를 쓴다. 화면 전용 축약 타입은 `...ViewModel`로 이름을 구분해 `src/types/` 밖에 둔다.

## 주의

- 프론트의 데이터 예시는 모두 `src/demo/`의 가상 데이터이며, **MVP API가 구현·연결됐다는 뜻이 아니다**.
- `src/api/client.ts`는 실제 백엔드 엔드포인트를 위한 호출 함수다. CORS, 세션, OAuth2, CSRF는 백엔드와 통합하며 검증해야 한다.
- `VITE_USE_API=true`로 켜도 API를 일부 화면에만 연결했다. 가짜 값이 남은 화면에는 안내 띠가 계속 보인다. 통합 전에는 `false` 유지.
- `src/types/`는 `form.schema.json`, `analyze_response.schema.json`, `SPEC.md` 5절의 구조를 그대로 옮겼다. `PublicForm`, `CreatorFormDefinition`, `ResponseHistoryItem` 같은 타입 이름은 문서에 없는 새 이름이므로, 백엔드 DTO도 같은 이름을 쓴다.
- Figma의 모든 세부 상태를 구현한 것은 아니다. 지금 화면은 **front-end scaffold**이다.
- 입력값 유효성, 분기 경로, 미응답/건너뜀, 개인정보·권한은 **서버에서 반드시 재검증**해야 한다.
- 데모 분석은 실제 LLM·ConditionEvaluator를 호출하지 않고, 미지의 요청에 대해 수치를 임의로 반환하지 않는다.
