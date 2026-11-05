# Figma 화면 ↔ GitHub SPEC.md 라우트 매핑

기준: Figma `Omj6sDT7BKq5ExD7ukJw9F`, `ver1(blue color)` 주요 프레임. `SPEC.md` §5는 변경 전 다시 확인.

| 화면 | 데모 라우트 | Figma node | 연동할 서버 API | 구현 상태 |
|---|---|---|---|---|
| 랜딩페이지 | `#/landing` | `34:75` | 없음 | 정적 반응형 화면 |
| 내 폼함 | `#/inbox` | `5:6468` | `GET /me/forms`, `POST /me/saved-forms` | UI+일부 실제 클라이언트 스켈레톤 |
| 응답 작성 | `#/response` | `5:6640` | `GET /f/{slug}`, `POST /forms/{form_id}/responses`, 응답 초안 API | UI+제출 클라이언트 연결점 |
| 제출 내역 | `#/history` | `5:6811` | `GET /me/responses`, `GET /me/responses/{id}` | 데모 전용, 서버 연결 필요 |
| 폼 편집 | `#/builder` | `5:6940` | `GET /forms/{id}/definition`, `POST /forms`, `PATCH /forms/{id}` | 편집 UI 상태만, 저장 연결 필요 |
| 내가 만든 폼 | `#/created` | Figma 메뉴/폼 편집 | `GET /me/created-forms` | 데모 전용, 서버 연결 필요 |
| 응답 탐색 | `#/explore` | `5:7219` | `GET /forms/{id}/results`, `POST /forms/{id}/analyze`, `POST /forms/{id}/export` | 데모 전용, 서버 연결 필요 |
| 내 프로필 | `#/profile` | Figma 메뉴/자동채우기 | `GET /me/profiles` 등 | 데모 전용, 서버 연결 필요 |

## 주의
- 프론트의 데이터 예시는 모두 `src/demo/data.ts`의 가상 데이터이며, **MVP API가 구현·연결됐다는 뜻이 아니다**.
- `api/client.ts`는 실제 백엔드 엔드포인트를 위한 호출 함수 초안이다. CORS, 세션, OAuth2, CSRF 및 정확한 응답 타입은 백엔드와 통합하며 검증해야 한다.
- `VITE_USE_API=true`로 켜도 API를 일부 화면에만 연결했다. 통합 전에는 `false` 유지.
- 실제 `Form` 스키마에 있는 분기/파일 첨부/전체 설정을 생략한 **화면용 TypeScript 타입 서브셋**이다. 서버 DTO 확정 후 타입 동기화가 필요하다.
- Figma의 모든 세부 상태를 구현한 것은 아니다. 이번 산출물은 **front-end scaffold**이다.
- 입력값 유효성, 분기 경로, 미응답/건너뜀, 개인정보·권한은 **서버에서 반드시 재검증**해야 한다.
- 데모 분석은 실제 LLM·ConditionEvaluator를 호출하지 않고, 미지의 요청에 대해 수치를 임의로 반환하지 않는다.
