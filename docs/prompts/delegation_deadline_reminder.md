# E12 마감 알림 위임 기록 (#57)

작성: 프롬프트 세션 초안을 사람이 검토해 발행. 2026-11-10 실행.

## 사람이 정한 것

- 메일은 실제로 보낸다. 발송은 `ReminderMailer` 인터페이스 뒤에 두고 운영 구현은 Gmail SMTP(smtp.gmail.com:587, STARTTLS, 앱 비밀번호). 새 의존성은 `spring-boot-starter-mail` 하나(사람 승인).
- 환경변수 `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`(없으면 `MAIL_USERNAME`), `MAIL_HOST`(기본 smtp.gmail.com), `MAIL_PORT`(기본 587). 사용자 이름이나 비밀번호가 비면 메일 없이 사이트 알림만 만든다. 값은 `.env`와 서버 `deploy/.env`에만 둔다.
- 메일 발송이 실패해도 알림 행은 남기고 다시 보내지 않는다(중복 메일 방지가 우선). 실패는 받는 주소 없이 로그 한 줄.
- 메일은 한국어 일반 텍스트: 제목 "[Passform] 마감 24시간 전: <폼 제목>", 본문은 폼 제목, 마감 시각(Asia/Seoul), 폼 링크(share_url), "알림은 Passform 화면 오른쪽 위, 사용자명을 눌러 끌 수 있어요"(처음엔 "내 프로필에서", 회차 2에서 사람이 바꿈). 폼 제목의 줄바꿈은 지운다(헤더 주입 방지).
- 메일 인터페이스에 넘기는 값은 받는 주소(`User.email`), 폼 제목, 마감 시각, 폼 링크뿐이다.
- 카카오톡 알림(AC59)은 하지 않는다. Flyway를 더하지 않는다(`notifications` 표와 UNIQUE(user_id, form_id, type)는 V1에 있다).

## 구현 판단(SPEC·AGENTS 안에서)

- 중복 방지: 알림 행은 `insert … on conflict (user_id, form_id, type) do nothing`으로 한 건씩 자기 트랜잭션에서 커밋하고, 1행을 넣은 실행만 메일을 보낸다. 동시에 두 번 돌아도 알림 1건·메일 1통(`DeadlineReminderServiceTest`).
- 스케줄러는 `test` 프로필에서 돌지 않는다. 테스트는 고정 시계로 `run()`을 직접 부르고 메일은 모의 객체다.
- `GET /me/notifications`는 SPEC 201행 모양 그대로다. 화면은 알림 목록을 열 때 내 폼함(`GET /me/forms`)에서 제목과 마감을 찾는다.
- 회차 2(사람 결정): 알림 켜기·끄기는 사용자명을 누르면 펼쳐지는 계정 메뉴에만 둔다(종 목록·내 프로필에서 뺌). 사이트 알림과 메일은 스위치 하나로 함께 켜고 끈다(마감이 가까워 설정 분리·Flyway는 하지 않음). 계정 메뉴에 로그아웃을 넣는다.
- 로그아웃은 로그인 사용자만 부르는 `POST /me/logout`(CSRF 필요, 204, 세션 무효화)이다. 기존 시험 `SecurityConfigTest.로그아웃_API는_없다`가 지키는 Spring 기본 `/logout`은 열지 않는다.
- SPEC 5절 API 표에 `POST /me/logout` 한 행을 `PATCH /me/settings` 바로 아래에 더했다(사람 승인, 다른 행은 그대로, CRLF 유지).
- 로그아웃 시험(`LogoutApiTest`): 로그인 없이 401, CSRF 토큰 없이 403(세션 유지), 정상 로그아웃 204와 세션 무효화, 세션에 저장한 로그인으로 `/me` 200 → 로그아웃 → 같은 세션으로 `/me` 401.

## 회차

1. 2026-11-10: 스케줄러·메일 인터페이스, 알림 API, 헤더 알림·설정 화면, README·처리방침·배포 문서. 결함 주입 3건 모두 시험이 잡았다. 사람 확인 1(앱 알림·메일 각 1)과 3(중복 없음) 통과.
2. 2026-11-10: PR #108 머지 뒤 `origin/main`으로 rebase(`SavedFormRepository` 충돌 해소). 내 프로필 스위치 제거, 계정 메뉴(알림 스위치·로그아웃), 메일 끝줄 변경, README 7절 삭제·첨부 줄 정리. 사람 확인 2(알림 누르면 폼 열림·읽음), 4(알림 끄면 생기지 않음), 5(360px), 로그아웃 통과.
3. 2026-11-10: SPEC 로그아웃 행 추가와 "로그아웃 뒤 `/me` 401" 시험 추가.

## 미룸

- 카카오톡 알림(AC59).
- 알림별 삭제(쓰레기통 버튼): 알림 행이 중복 발송 방지 기록이라 지우면 같은 폼에 다시 보내게 된다. 숨김 표시 칸(Flyway)과 `DELETE /me/notifications/{notification_id}`, SPEC 행이 필요해 이후 기능 개발로 미룬다(사람 결정).
- 사이트 알림과 메일 설정 분리(Flyway·SPEC 변경 필요, 사람 결정으로 미룸).
- 운영 서버의 실제 메일 발송: 머지 뒤 사람이 서버 `deploy/.env`에 `MAIL_USERNAME`·`MAIL_PASSWORD`를 넣고 재배포해야 시작한다.
