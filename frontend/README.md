# Passform Frontend Scaffold

Figma `Passform 무드보드`의 `ver1(blue color)` 화면을 바탕으로 만든 React + TypeScript + Tailwind CSS/Vite 프론트엔드 틀입니다.

## 실행

```bash
cd frontend                  # GitHub Passform에 병합한 경우
cp .env.example .env
npm install
npm run dev
# http://localhost:5173
```

Windows PowerShell은 `Copy-Item .env.example .env`로 복사합니다. Node.js 20 이상을 권장합니다.

## 정적 미리보기

- ZIP의 `preview.html`을 브라우저로 열면 `npm install` 없이도 화면 전환과 데모 조작이 됩니다.
- 렌더링 정적 캡처: `passform-inbox-preview.png` (압축 파일 외 별도 제공)
- 미리보기 번들은 기존 React 컴포넌트 소스를 브라우저용으로 변환한 것입니다. 프리뷰용 React 런타임은 Figma 디자인 원본과 무관한 테스트 의존성입니다.

## 실제 API 연결

- `src/api/client.ts`의 API 호출 예시를 사용합니다.
- `.env`에 `VITE_USE_API=true`, `VITE_API_BASE_URL=http://localhost:8080`을 지정합니다.
- 현재 API 실제 호출은 `/me/forms`, `/forms/{id}`, `POST /forms/{id}/responses`에 제한됩니다. 다른 페이지는 데모 데이터 기반입니다.
- Figma의 데모 사용자/제출 내역은 실제 인증 결과가 아닙니다. 세션·OAuth·CORS·CSRF가 동작하도록 백엔드와 함께 통합해야 합니다.

## 설계 근거

- Figma: `ver1(blue color)`: `5:6468`, `5:6640`, `5:6811`, `5:6940`, `5:7219`, 랜딩 `34:75`
- GitHub: `docs/SPEC.md` §4–5, `AGENTS.md` §3–7, `docs/PROBLEM.md`, `src/response_analysis/schemas/request_context.schema.json`
- 실제 `main` 브랜치에는 프론트엔드가 없어서 신규 `frontend/` 폴더로 들어가도록 구조화함.

## 개발 우선순위

1. FormSchema 전체와 DTO 계약 동기화, 질문 타입/분기 규칙 구현
2. 인증/권한/세션, 사용자별 프로필, 폼함 데이터 실제 API 연동
3. 폼 작성·제출과 초안 복원, 결과 대시보드 API 연동
4. AI 분석: 프롬프트 파서·매처·ConditionEvaluator 결과/clarify UI 연결
5. Playwright / Vitest 접근성·API·상태 테스트 추가

**안전:** 데모 모드의 화면 값은 가상 값이며 운영 품질의 근거가 아닙니다. 비공개 개인정보를 데모 모드로 넣지 마세요.
