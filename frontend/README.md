# Passform Frontend

Figma `Passform 무드보드`의 `ver1(blue color)` 화면을 바탕으로 만든 프론트엔드입니다. React 19, TypeScript 5, Vite 6, React Router 7, Tailwind CSS 3을 씁니다. 지금은 화면 틀과 가상 데이터만 있고, 기능과 서버 연결은 담당 이슈에서 채웁니다.

## 실행

```bash
cd frontend
cp .env.example .env
npm ci
npm run dev
# http://localhost:5173
```

Windows PowerShell은 `Copy-Item .env.example .env`로 복사합니다. Node.js 20 이상이 필요합니다.

## 검사

| 명령                | 내용                                  |
| ------------------- | ------------------------------------- |
| `npm run lint`      | ESLint (경고도 실패)                  |
| `npm run typecheck` | TypeScript strict 검사                |
| `npm test`          | Vitest                                |
| `npm run build`     | 타입 검사 뒤 Vite 빌드                |
| `npm run format`    | Prettier로 전체 포맷 (CI에서는 안 씀) |

GitHub Actions의 `frontend` job이 Node 20에서 `npm ci`, lint, typecheck, test, build를 실행합니다.

## 라우트와 작업 규칙

라우트 표, 담당 이슈, 공통 파일 규칙은 [docs/ROUTES_AND_API.md](docs/ROUTES_AND_API.md)에 있습니다. 작업 항목은 GitHub 이슈로 관리합니다.

## 실제 API 연결

- API 호출은 `src/api/client.ts`에 둡니다.
- `.env`에 `VITE_USE_API=true`, `VITE_API_BASE_URL=http://localhost:8080`을 지정합니다.
- 화면이 실제로 호출하는 것은 내 폼함 화면의 `GET /me/forms`, 응답 화면의 `GET /f/{slug}`·`GET /forms/{form_id}`·`POST /forms/{form_id}/responses`뿐입니다. 다른 화면은 가짜 데이터 기반입니다.
- Figma의 데모 사용자/제출 내역은 실제 인증 결과가 아닙니다. 세션·OAuth·CORS·CSRF가 동작하도록 백엔드와 함께 통합해야 합니다.

## 설계 근거

- Figma: `ver1(blue color)`: `5:6468`, `5:6640`, `5:6811`, `5:6940`, `5:7219`, 랜딩 `34:75`
- GitHub: `docs/SPEC.md` §4–5, `AGENTS.md` §3–7, `docs/PROBLEM.md`, `docs/ARCHITECTURE.md` §10, `docs/api/analyze_api.md`, `src/passform/schemas/form.schema.json`, `src/response_analysis/schemas/`

**안전:** 데모 모드의 화면 값은 가상 값이며 운영 품질의 근거가 아닙니다. 비공개 개인정보를 데모 모드로 넣지 마세요.
