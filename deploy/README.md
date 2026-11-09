# 배포 절차 (이슈 #47)

사람이 서버와 콘솔에서 하는 일을 순서대로 적는다. 결정과 이유는 `docs/ARCHITECTURE.md` 10절.
로컬 개발(`localhost:5173` + `8080`, 루트 `docker-compose.yml`)은 이 폴더와 무관하다.

| 구성 | 위치 |
|---|---|
| 프론트 | `https://app.passform.kr` — Vercel, 루트 디렉터리 `frontend/` |
| 백엔드 | `https://api.passform.kr` — EC2 한 대, `deploy/compose.yml`(app, db, caddy) |

## 1. EC2 준비 (한 번)

1. 보안 그룹 인바운드는 22(내 IP만), 80, 443(TCP·UDP)만 연다. 5432는 열지 않는다.
2. DNS `api.passform.kr` A 레코드가 이 서버의 고정 IP를 가리키는지 확인한다(Caddy 인증서 발급 조건).
3. Docker와 스왑(t3.small에서 이미지 빌드 중 메모리 부족 방지)을 준비한다.

   ```sh
   sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git
   sudo usermod -aG docker "$USER"   # 다시 로그인
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```

## 2. 비밀값 (서버에서만)

```sh
git clone https://github.com/OpinioHQ/Passform.git && cd Passform/deploy
cp .env.example .env && chmod 600 .env
vi .env   # change-me를 모두 실제 값으로 바꾼다
```

- `.env`는 `.gitignore` 대상이다. 값을 화면 공유, 메신저, 이슈, PR에 붙이지 않는다.
- `docker compose config`는 값을 그대로 출력하므로 그 출력을 공유하지 않는다.

## 3. 백엔드 띄우기

```sh
cd ~/Passform/deploy
docker compose up -d --build
docker compose ps
curl -s https://api.passform.kr/health   # {"status":"ok"}
```

첫 빌드는 몇 분 걸린다. Flyway가 첫 실행에서 스키마를 만든다.

## 4. 프론트 (Vercel)

- Root Directory `frontend`, Framework Vite, Build `npm run build`, Output `dist`.
- API 주소는 `frontend/.env.production`에 있다. Vercel에 `VITE_USE_API`·`VITE_API_BASE_URL`을 넣었다면 `.env.production`과 같은 값이어야 한다(Vercel 값이 우선한다). 그 밖의 값은 Vercel에 넣지 않는다.
- 새로고침 라우팅은 `frontend/vercel.json`이 처리한다.

## 5. 첫날 확인 (이슈 #47 완료 조건)

1. `https://api.passform.kr/health`가 200과 `{"status":"ok"}`.
2. `https://app.passform.kr`에서 Google 로그인 → 다시 `app.passform.kr`로 돌아와 내 이름이 보인다.
3. 개발자 도구 쿠키: `JSESSIONID`(api.passform.kr, Secure, HttpOnly, SameSite=Lax), `XSRF-TOKEN`(passform.kr, Secure, SameSite=Lax).
4. 로그인 없이 공유 링크로 응답 제출이 된다(CSRF 확인).
5. Google 테스트 사용자로 등록하지 않은 계정으로도 로그인되는지 본다(ARCHITECTURE 10절).
6. 저장소, Vercel 환경변수, `VITE_` 값 어디에도 비밀값이 없다.

## 6. 첫 폼 배포 전 확인

1. OpenAI 프로젝트의 데이터 설정(Residency, 데이터 공유 꺼짐)이 SPEC 211행의 AI 요약 고지(처리 국가 "OpenAI가 정하는 국외 지역…")와 같은지 확인한다.
2. 다르면 폼을 배포하기 전에 고지를 먼저 고친다(SPEC 362행). 고지는 폼을 처음 배포할 때 그 폼에 고정되어 나중에 바뀌지 않는다(SPEC 158행).

## 7. 업데이트와 운영

```sh
cd ~/Passform && git pull
cd deploy && docker compose up -d --build app
docker compose logs --tail 100 app
```

- 세션은 메모리에 있어서 app을 다시 띄우면 모두 다시 로그인한다.
- DB 백업: `mkdir -p ~/passform-backups`를 한 번 하고 `crontab -e`에 아래 한 줄을 넣는다. 서버 시계는 UTC라 한국 시각 03:30이다. 백업은 7일 보관한다.

  ```
  30 18 * * * $HOME/Passform/deploy/backup.sh >> $HOME/passform-backups/backup.log 2>&1
  ```

- 백업은 같은 서버에 있으므로 서버를 잃으면 함께 잃는다. 필요하면 EBS 스냅샷을 콘솔에서 따로 건다.
- 복원: app을 멈추고 한 트랜잭션으로 복원한 뒤 다시 띄운다. 복원 중에는 서비스가 멈추고, 실패하면 트랜잭션이 되돌려져 원래 데이터가 남는다.

  ```sh
  cd ~/Passform/deploy
  docker compose stop app
  docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --single-transaction' < 백업파일.dump
  docker compose start app
  ```
