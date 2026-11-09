# 배포 설정 준비 위임 기록 (#47)

## 위임 프롬프트 원본

작성: 사람이 검토해 발행. 2026-11-09 실행.

```text
이번 작업은 이슈 #47(E9 도메인 연결과 배포) 중 "저장소 쪽 배포 준비"뿐이다. 계정 콘솔·서버 접속·비밀값 입력은 사람이 한다. 다른 이슈는 구현하지 않는다. 나는 ye-nni이고 리뷰어는 Psoyeon1117이다. 셸은 Windows PowerShell이다.

# 사람이 정한 것 (바꾸지 않는다)
- 도메인은 passform.kr이다. 사람이 콘솔 준비를 끝냈다(DNS, Vercel 프로젝트, Google 콜백 등록, OpenAI 키·한도).
  - 프런트: https://app.passform.kr(Vercel, 루트 frontend/)
  - 백엔드: https://api.passform.kr(AWS EC2 서울, Ubuntu 24.04, t3.small)
- EC2 한 대에 docker compose로 백엔드, Postgres, Caddy(자동 HTTPS)를 둔다. DB 포트는 밖에 열지 않는다.
- 비밀값(DB 비밀번호, GOOGLE_CLIENT_ID/SECRET, OPENAI_API_KEY)은 서버의 .env(권한 600)에만 둔다. 저장소, 이슈, PR, 로그, VITE_ 변수, Vercel 설정에는 넣지 않는다. ARCHITECTURE 10절의 SSM 제안은 이 결정으로 바꿔 적는다.
- 쿠키와 접근 설정:
  - 세션 쿠키는 Secure, SameSite=Lax다.
  - CORS는 https://app.passform.kr만 허용한다.
  - CSRF 쿠키 도메인은 .passform.kr이다.
  - Google 콜백은 https://api.passform.kr/login/oauth2/code/google이다.
- OpenAI 데이터 설정(Residency Global, 데이터 공유 꺼짐)은 사람이 확인했다. 고지 문구는 PR #96으로 이미 고쳤다. 이번 작업에서 고지를 고치지 않는다.

# 비용 규칙 (주간 한도가 빠듯하다. 지킨다)
- 서브에이전트를 띄우지 않는다.
- SPEC 전체를 읽지 않는다.
- 새 라이브러리를 더하지 않는다.
- 단계마다 로컬에 커밋한다.
- 최종 보고는 40줄 안팎으로 쓴다.

# 시작 확인 (하나라도 어긋나면 멈추고 알린다)
1. C:\Opinio는 다른 PR(#95)의 브랜치이고 다른 세션이 쓴다. 그 폴더의 파일과 브랜치는 건드리지 않는다.
2. C:\Opinio에서 git fetch origin을 한 뒤 다음을 실행한다. 이후 작업은 C:\Opinio-deploy에서만 한다.
   git worktree add ..\Opinio-deploy -b chore/deploy-setup origin/main
3. C:\Opinio\.env를 C:\Opinio-deploy\.env로 복사한다. 이 복사본은 worktree를 지울 때까지 지우지 않는다. 값은 출력하지 않는다.
4. .\gradlew.bat cleanTest build가 지금 통과하는지 본다.
   - DB는 실행 중인 opinio-db-1을 쓴다.
   - docker compose로 개발 DB를 새로 띄우거나 내리지 않는다.
   - 다른 세션과 동시에 테스트를 돌리지 않는다. 다른 세션이 테스트 중인지 모르면 묻는다.

# 먼저 읽을 것 (이곳만)
- AGENTS.md 7절
- gh issue view 47
- docs/ARCHITECTURE.md 10절의 "배포"
- docs/SPEC.md 360~363행(/health와 OpenAI 운영 확인)
- src/main/resources/application.properties, .env.example, docker-compose.yml, build.gradle
- src/main/java/com/opinio/passform/config/SecurityConfig.java
- frontend/package.json, frontend/vercel.json, frontend/src/api/client.ts 앞 40줄, frontend/vite.config.*
- .github/workflows/ci.yml

# 절대 규칙
- 판정 기준 파일과 기존 테스트를 고치지 않는다. 기존 테스트가 깨지면 고치지 말고 멈춰 묻는다.
- 비밀값을 만들거나 파일·출력에 넣지 않는다. 예시 값은 change-me 같은 자리표시만 쓴다.
- 다음은 하지 않는다: 서버 접속(ssh), AWS·Vercel·가비아·Google·OpenAI 콘솔 조작, 실제 배포.
- 인증 우회, demo 프로필, X-Demo-User를 배포 설정에 넣지 않는다.
- 로컬 개발 동작(localhost:5173 + 8080, 기존 docker-compose.yml)을 바꾸지 않는다. 배포 설정은 별도 파일로 둔다.
- 커밋과 PR 규칙:
  - 에이전트 이름이나 생성 문구를 넣지 않는다.
  - PR 본문에 사람을 @로 언급하지 않는다.
  - _local/과 docs/SPEC_followups.md는 커밋하지 않는다.
  - git add는 경로를 명시한다.
```

## 사람 결정 (2026-11-08)

- 백엔드는 EC2 한 대에 docker compose(백엔드, Postgres, Caddy)로 둔다.
- 비밀값은 서버의 `deploy/.env`(권한 600)에만 둔다. ARCHITECTURE 10절의 SSM 제안 대신이다.

## 지시와 다르게 한 것과 사람의 수용 (2026-11-09)

| 지시와 다른 점 | 이유 | 수용 |
|---|---|---|
| CSRF 쿠키 도메인 `.passform.kr` → `passform.kr` | Tomcat이 앞 점을 거부해 모든 요청이 실패했다. RFC 6265에서 같은 뜻이다 | 수용 |
| `deploy/README.md` 추가 | 서버·콘솔에서 사람이 할 순서를 한곳에 둔다 | 수용 |
| `frontend/.env.production` 추가 | 공개 값(`VITE_USE_API`, `VITE_API_BASE_URL`)을 저장소에서 검토하게 한다 | 수용 |

## 미룸

- 실제 배포(서버 접속, 콘솔 작업)는 사람이 `deploy/README.md` 순서로 한다.
- S3 설정: `build.gradle`에 AWS SDK가 없어 업로드 구현 때 더한다.
- AC34(동시 300명): 이 구성으로는 보장하지 않는다.
- 자동 배포(CI에서 서버 반영)
- SSM Parameter Store
- AGENTS.md 7절 보충(서버 `.env`가 컨테이너 환경변수가 된다는 설명): 11/11 뒤
- `SecurityConfigTest`의 CSRF 도메인 예시(`.example.test`, 실제 Tomcat에서는 거부됨): 고치지 않음
