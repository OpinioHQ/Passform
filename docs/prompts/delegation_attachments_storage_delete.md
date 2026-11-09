# 위임 기록: T12 첨부, 저장 공간, 삭제 (#63)

일이 커서 사람이 3단계로 나눴다(2026-11-09). 이 문서는 단계마다 이어 적는다.

| 단계 | 범위 | 상태 |
|---|---|---|
| 1 | 저장소 연결, 파일 형식 판정, 제작자 첨부, 폼 첨부 다운로드, 사용량, 빌더·응답 화면 표시 | 구현, 자동 검증, 실제 S3 화면 확인 끝 |
| 2 | 응답 파일 제출·수정·취소, 초안 사용량, 응답 첨부 다운로드, 응답 화면(브라우저 보관) | 다음 세션 |
| 3 | 받은 응답 전체 삭제, 응답 첨부 전체 삭제, 폼 삭제, 조회 전용 이력, 정리 화면 | 다음 세션 |

## 사람이 정한 답 (2026-11-09)

| 질문 | 답 |
|---|---|
| 작업 분할 | 3세션, PR 3개. #63은 3번째 PR에서 닫는다 |
| 폼 삭제 뒤 내 폼함 | 모두 제거(`saved_forms` 행 삭제). 제출 내용은 제출 이력 화면에서 본다. 작성 중 응답은 SPEC 208행대로 지운다 |
| 새 의존성 | `software.amazon.awssdk:s3`만 추가. Tika는 쓰지 않는다 |
| 어떤 문항에 응답 파일을 받나 | 모든 문항. 화면은 작은 첨부 버튼으로 둔다 |
| 파일만 올린 답의 필수 판정 | 답 값으로만 판정한다. 파일은 필수를 채우지 않는다 |
| ZIP·OLE 기반 형식의 내용 판별 | 서명까지만 |
| 폼 정의·답의 저장 크기 | JSON UTF-8 바이트. 작업 정의와 모든 배포 버전을 합산한다 |
| S3 실패 오류 | `503 storage_unavailable`. EXPECTED 표에 한 줄 더 추가하는 것을 승인 |
| 커밋 뒤 S3 삭제 실패 | 짧게 재시도(최대 3회) 후 폼 ID와 개수만 경고 로그 |
| Presigned URL 유효 시간 | 5분 |
| `PATCH /forms`의 다른 첨부 목록 | 무시하고 서버 값을 유지 |
| 저장 공간 표시 위치 | 내가 만든 폼 목록 위 |
| 응답 0건인 폼 삭제 | 내보내기를 묻지 않고 삭제 확인만 |
| `deploy/compose.yml` | 버킷·리전 두 줄 추가(프롬프트 밖 파일, 승인). 자격 증명은 넣지 않고 EC2 서버 역할을 쓴다 |
| 복사본의 첨부 | 복사 API가 main에 없어 묻지 않았다 |

## 1단계 회차 표

| 회차 | 한 일 | 결과 |
|---|---|---|
| 1 | 선행 작업 확인(T3·T5·T10·T11 닫힘, E9는 이슈가 열려 있으나 배포 설정 PR #99가 main에 있음). 결정 질문 | 사람 답 수신 |
| 2 | 서버 구현, `AttachmentApiTest`·`FileTypesTest` | 106건 통과 |
| 3 | 프런트 구현 | 로컬 `.env.local`의 `VITE_USE_API` 때문에 데모 모드 테스트 28건 실패. 값을 끄면 전부 통과 |
| 4 | 전체 빌드 | `AccessGoldenTest` 6건 실패. 이 테스트는 `service` 패키지만 스캔하고 트랜잭션 관리자가 없다 |
| 5 | 테스트는 그대로 두고 구현 수정: 저장소 빈 설정을 `service`로 이동, DB 구간을 `AttachmentTransactions`로 분리 | 전체 빌드 통과 |
| 6 | 결함 주입 스크립트 1차 | 변수 이름 충돌로 주입 0건. 무효 |
| 7 | 결함 주입 스크립트 2차 | Gradle 호출 실패로 테스트 0건. 무효 |
| 8 | 결함 주입 3차 + 1번 항목 재확인 | 11건 모두 유효 |
| 9 | 사람이 실제 S3 버킷으로 화면 확인 | 첫 시도는 `.env`에 `S3_BUCKET`이 없어 503(설정 누락, 코드 결함 아님). 설정 뒤 7개 항목 통과 |

## 결함 주입 결과 (1단계 해당 11건)

XML로 기대한 테스트가 실행돼 실패했음을 확인했다. 주입은 커밋하지 않고 항목마다 되돌렸다.

| 주입 | 실패한 테스트(대표) | 실패 수 |
|---|---|---|
| 확장자만 검사한다 | `확장자만_바꾼_파일은_거부한다` 5건, `AC63_creator_attachment_rejects_type` | 6 |
| 클라이언트 `Content-Type`을 저장한다 | `AC63_업로드_응답과_저장값은` | 2 |
| `>=`로 정확히 500MB를 거부한다 | `AC65_storage_quota_at_limit` | 1 |
| 실패한 요청의 S3 원본을 지우지 않는다 | `DB_저장이_실패하면` | 3 |
| 다운로드에서 소속 검사를 뺀다 | `AC63_경로의_폼_문항과` | 4 |
| 과거 버전 파일을 공개로 내준다 | `AC63_과거_버전_파일은` | 1 |
| 배포 폼의 첨부 변경이 새 버전을 만들지 않는다 | `AC63_attachment_api_version_atomic` | 6 |
| 첨부 제거가 과거 버전이 참조하는 원본을 지운다 | `AC63_attachment_change_creates_immutable_version` | 3 |
| 취소로 끊긴 행을 제작자 사용량에 센다 | `응답_파일은_제작자에게만` | 1 |
| DB 커밋 전에 S3 원본을 지운다 | `첨부_제거의_DB가_실패하면` | 1 |
| 첨부 API에서 소유권 검사를 뺀다 | `AC2_제작자가_아니면` | 1 |

나머지 15건은 응답 파일(2단계)과 삭제(3단계) 코드가 생긴 뒤 한다.

## 테스트별 통과 이유

`AttachmentApiTest`는 실제 Postgres와 메모리 저장소, `FileTypesTest`는 순수 함수다. 판정 위치는 패키지 `com.opinio.passform` 아래 파일이다.

| 테스트 | 왜 통과하는가 |
|---|---|
| `AC63_draft_attachment_keeps_version` | `FormService.replaceAttachmentLists`가 초안이면 `publishIfChanged`를 부르지 않는다 |
| `AC63_업로드_응답과_저장값은` | `AttachmentService.add`가 `FileTypes.detect`의 값을 저장하고 키는 UUID로 만든다 |
| `AC63_attachment_api_version_atomic` | 같은 트랜잭션에서 `publishIfChanged`가 새 버전을 저장한다. `FormVersion`은 불변이다 |
| `AC63_attachment_change_creates_immutable_version` | `StorageQuotaService.releaseUnreferenced`가 버전이 참조하는 ID를 건너뛴다 |
| `없는_문항이나_목록에_없는_첨부는_404다` | `AttachmentTransactions.requireLocation`과 `remove`의 `FormAttachments.contains` |
| `AC63_creator_attachment_rejects_type` | `FileTypes.detect`가 S3 호출 전에 거부한다 |
| `S3_저장이_실패하면_503` | `storage.put`이 DB 구간 전에 예외를 던진다 |
| `DB_저장이_실패하면` | `AttachmentService.add`의 catch가 `cleanup.deleteNow`를 부르고 트랜잭션은 롤백된다 |
| `첨부_제거의_DB가_실패하면` | `FileCleanup.deleteAfterCommit`은 커밋 콜백에서만 삭제를 예약한다 |
| `AC2_제작자가_아니면` | `FormService.requireOwner`, 비로그인은 `SecurityConfig`의 인증 요구 |
| `삭제된_폼에는_첨부를` | `FormService.requireOwner`의 `isDeleted` |
| `AC63_현재_버전의_열린_폼_첨부는` | `AttachmentTransactions.requireReadable`의 현재 버전·열림 판정 |
| `AC63_이미지만_바로_열고` | `FileTypes.opensInline`이 `image/`만 참이다 |
| `AC63_question_attachment_owner_only_draft` | `requireReadable`의 초안 분기: 비로그인 401, 다른 사용자 403 |
| `AC63_과거_버전_파일은` | `holding`(참조 버전)과 `ResponseRepository.existsSubmittedInVersions` |
| `AC63_닫힌_폼의_첨부는` | `requireReadable`의 `currentAndOpen`이 거짓이면 제출자 판정으로 간다 |
| `AC63_경로의_폼_문항과` | `readableAttachment`의 폼·문항 필터와 정의 포함 검사 |
| `응답에_연결된_파일은` | `readableAttachment`의 `getResponseId() == null` 필터 |
| `AC63_삭제된_폼의_첨부는` | `readableAttachment`의 `isDeleted` 필터 |
| `PATCH의_오래된_첨부_목록은` | `FormAttachments.keepStoredLists`가 저장된 목록을 넣는다 |
| `PATCH와_새_폼으로_다른_폼의` | 같은 함수가 저장값(새 폼은 빈 목록)으로 바꾼다 |
| `PATCH로_문항을_지우면` | `FormService.update`의 `removedAttachments`와 `releaseUnreferenced` |
| `배포_폼에서_문항을_지워도` | `releaseUnreferenced`가 새 버전 저장 뒤 버전 참조를 본다 |
| `AC65_storage_quota_at_limit` | `requireWithinLimit`의 `usedAfter > LIMIT_BYTES` |
| `AC65_storage_quota_rejects_overflow` | 사전 검사는 파일 크기만 보고, 잠금 안 판정이 정의 JSON까지 세어 거부한 뒤 원본을 지운다 |
| `파일만으로_한도를_넘으면` | `AttachmentService.add`의 사전 검사가 S3 호출 전에 거부한다 |
| `AC65_배포_폼의_첨부_추가가` | `requireWithinLimit` 실패로 버전 저장까지 롤백된다 |
| `AC65_폼_생성이` | `FormService.create`의 한도 검사 |
| `AC65_폼_수정이` | `requireWithinLimit`의 `usedAfter > usedBefore`: 늘리면 409, 줄이면 허용 |
| `AC65_동시_저장_두_건이` | `lockAndMeasure`의 소유자 행 잠금으로 직렬화된다 |
| `응답_파일은_제작자에게만` | `AttachmentRepository.usedBytes`의 소유자 조인과 정의 참조 조건 |
| `AC65_작성_중_답은` | `usedBytes`의 `r.user_id = :userId and IN_PROGRESS` |
| `사용량_API는` | `StorageController`, 비로그인은 보안 필터 |
| `multipart_한도는` | `application.properties`의 501MB |
| `FileTypesTest` 허용 형식 | `FileTypes.RULES`의 확장자·서명·대상 표 |
| `FileTypesTest` 거부 형식 | 표에 없거나 대상이 다르거나 서명이 맞지 않으면 `detect`가 던진다 |
| `FileTypesTest` 파일 이름 | `FileTypes.cleanFilename`이 경로·제어 문자를 떼고 255자로 자른다 |
| 화면 `AttachmentEditor.test` | 서버 성공 뒤에만 `onChange`를 부르고 오류는 `friendlyError`로 보인다 |
| 화면 `BuilderPage.attachments.test` | 첨부는 `dirty`를 켜지 않고, 배포 폼은 버전을 다시 읽으며, `dirty`면 `locked`다 |
| 화면 `FormAttachmentList.test` | `content_type`이 `image/`면 `img`, 아니면 다운로드 링크다 |
| 화면 `CreatedPage.storage.test` | `storage`가 있을 때만 사용량 칸을 그린다 |

## AC 처리 (1단계 기준)

| 항목 | 처리 |
|---|---|
| AC63 첨부·버전·실패 불변·권한 | 자동 |
| AC63 응답 화면의 이미지·PDF·MP4 다운로드 | 자동(화면 테스트) + 사람 확인 |
| AC63 폼 전체 삭제 뒤 원본 다운로드 불가 | `deleted_at` 404는 자동. 삭제 API는 3단계 |
| AC65 폼 생성·수정·첨부의 한도, 정확히 500MB, 동시 저장 | 자동 |
| AC65 초안 저장·제출·응답 수정의 한도 | 2단계 |
| AC64, AC82의 파일, AC68의 파일 첨부, 수정·취소의 파일 | 2단계 |
| AC66, AC67, AC70, AC83·AC17·AC19의 삭제 | 3단계 |
| 실제 S3 동작(`S3FileStorage`) | 사람 확인(자동 테스트 없음) |
| 추적표 골든 id | `golden_cases.yaml`에 없어 일반 테스트 이름에 적었다 |

## 1단계 사람 화면 확인 (2026-11-09, 실제 S3 버킷, 서울 리전)

| 확인 | 결과 |
|---|---|
| 초안에 이미지·PDF 첨부 뒤 버킷 | 객체 2개. 키는 `attachments/<폼ID>/<무작위 값>`이고 파일 이름이 없다 |
| 배포 뒤 비로그인 응답 화면 | 이미지는 바로 보이고 PDF는 링크만 있다. PDF는 저장되고 한글 파일 이름이 유지된다 |
| MP4 첨부 | 업로드되고, 응답자 화면에 내려받기 링크로 보이며 저장된다 |
| 버킷 객체 URL 직접 접근 | `AccessDenied` |
| 사용량 표시 | `4.2MB / 500MB` |
| 확장자만 PDF인 텍스트 파일 | 안내 문구, 목록·폼 버전·버킷 객체 불변 |
| 배포 폼에서 PDF 빼기 | 폼 버전 1 증가, 버킷 객체 유지. 그 버전 제출자는 받고 비로그인은 못 받는다. 공유 링크에 PDF 링크 없음 |
| 초안에서 파일 빼기 | 버킷 객체 삭제, 사용량 복귀, 다른 폼의 객체 유지 |

저장 실패 뒤 원본 삭제와 500MB 초과는 실제로 재현하기 어려워 자동 테스트로 확인했다. 360px 파일 첨부는 응답 파일이 생기는 2단계 범위다.

## 지시와 다르게 한 것

- 사람 결정으로 PR을 3개로 나눈다. 브랜치·PR 제목은 단계마다 다르다.
- 프롬프트의 새 오류 코드 4개 외에 `storage_unavailable`을 더했다(사람 승인, EXPECTED 표 한 줄 추가).
- `deploy/compose.yml`, `deploy/.env.example`을 바꿨다(사람 승인).

## 다음 단계가 알아야 할 것

- 잠금 순서: 제출에 소유자 잠금을 넣을 때 **폼 행 → 소유자 행 → 초안 응답 행** 순서로 한다. `DraftService.save`가 사용자 행 → 초안 행 순서라, 제출이 초안 행을 먼저 잠그면 교착이 생긴다.
- `SubmissionService`도 `SubmissionGoldenTest`가 5인자 생성자로 만든다. `FormService`처럼 기존 생성자를 유지한다.
- `AccessGoldenTest`는 `service` 패키지만 스캔하고 `PlatformTransactionManager`가 없다. 새 서비스도 `@Transactional` 클래스로 나눈다.
- T10의 취소는 연결만 끊는다. 2단계에서 행과 원본을 정리하도록 바꾼다. 이미 끊긴 행은 사용량 쿼리의 정의 참조 조건으로 제외된다.
- 실제 서버 multipart 테스트(비로그인 CSRF, 25MB 판정)는 비로그인 제출이 생기는 2단계에서 한다. Tomcat의 multipart 부분 수 제한도 그때 확인한다.
- 커밋 뒤 삭제는 별도 스레드에서 돈다. 서버가 그 사이 종료되면 원본이 남을 수 있고, 이 경우도 삭제 실패와 같이 수동 정리 대상이다.
- 배포: 컨테이너에서 EC2 서버 역할을 쓰려면 인스턴스 메타데이터 hop limit이 2 이상이어야 한다.

## 원본 프롬프트

````text
[구현 내용]
T12(#63): 제작자 첨부와 응답 파일의 서버 multipart 업로드, S3 저장, Presigned 다운로드, `GET /me/storage`와 500MB 한도, 받은 응답 전체 삭제, 응답 첨부 전체 삭제, 폼 삭제와 삭제 전 내보내기, 화면 연결을 만든다. 저장소 C:\Users\namhu\claude-practice\Passform, 브랜치 feat/attachments-storage(최신 origin/main에서 분기), 셸 Windows PowerShell.

[목표]
제작자와 응답자가 실제 화면에서 파일을 올리고 받으며, 제작자는 사용량을 보고 정리한다. 실패한 요청은 응답·첨부 목록·버전·사용량·S3 원본을 남기거나 바꾸지 않는다. 삭제는 일부만 처리된 상태를 사용자에게 보이지 않는다. AC63~AC67, AC70을 테스트와 실제 화면으로 보인다.

[절대 규칙]
- AGENTS.md 3~5절을 따른다.
- 시작 전에 T3(#51), T5(#53), T10(#61), T11(#62), E9(#47)가 main에 있는지 확인한다. 없으면 구현하지 말고 알린다.
- 작업이 한 세션에 크면 구현 전에 나눌 안을 제안하고 승인받는다(AGENTS.md 세션 규칙).
- 구현 전에 먼저 묻는다: 폼 삭제 뒤 `saved_forms`와 작성 중 응답(`in_progress`)을 내 폼함에 어떻게 보일지(이슈 #63, ARCHITECTURE 10절 삭제 규칙의 미정 항목). 맥락과 선택지를 적어 객관식으로 묻고, 답을 받기 전에는 폼 삭제를 구현하지 않는다.
- 인증 우회(X-Demo-User 등)와 새 permitAll을 넣지 않는다. 현재 사용자는 CurrentUserService로 얻고, 테스트는 spring-security-test 모의 인증을 쓴다.
- 파일 형식 판정은 확장자와 실제 파일 내용을 모두 본다. 클라이언트가 보낸 `Content-Type`을 믿지 않는다. ZIP은 서버에서 자동으로 압축 해제하거나 실행하지 않는다.
- S3 키는 서버가 새로 만들고 파일 이름을 넣지 않는다. 버킷은 공개하지 않는다.
- 다운로드는 서버가 권한을 확인한 뒤에만 Presigned URL을 만든다. 폼 첨부는 경로의 폼과 그 버전 정의에 실제로 있는 `attachment_id`만, 응답 첨부는 경로의 폼·응답에 연결된 파일만 내준다.
- 제작자 첨부의 판정을 `response_id IS NULL`만으로 하지 않는다. T10의 취소로 연결이 끊긴 행이 제작자 첨부로 보이거나 사용량에 잡히면 안 된다.
- S3 호출 동안 DB 연결과 폼·사용자 행 잠금을 잡지 않는다. 동의·마감·버전처럼 파일 없이 판정할 수 있는 검사는 업로드 전에 하고, 잠금 안에서 최종 판정한다. 최종 판정이 실패하면 올린 원본을 지운다.
- S3 원본 삭제는 DB 커밋 뒤에만 한다. 커밋 전에 지우면 롤백돼도 원본이 사라진다. 다운로드 가능 여부는 DB 값이 기준이다.
- 500MB 판정은 동시 요청에서도 넘치지 않게 소유자 기준으로 직렬화한다. 기존 잠금(폼 행, 사용자 행)과 순서를 맞춰 교착을 만들지 않는다.
- 배포된 폼의 첨부 추가·제거는 FormService의 버전 생성 경로를 재사용해 한 번의 성공으로 처리한다. 첨부 목록은 첨부 API로만 바뀌어야 하며, 빌더의 `PATCH /forms`가 오래된 목록으로 덮어쓰거나 다른 폼의 `attachment_id`를 끌어오면 안 된다.
- 받은 응답 전체 삭제와 폼 삭제의 이력 복사는 각 응답의 제출 버전 정의에서 하고, 저장 모양은 ResponseHistoryService가 읽는 형태를 따른다.
- 파일 내용, 답, 파일 이름, S3 키, Presigned URL을 로그와 오류 응답에 남기지 않는다.
- AWS 자격 증명은 OS 환경변수 또는 서버 역할로만 받고 저장소·이슈·PR·로그·`VITE_` 변수·Vercel 설정에 넣지 않는다. `.env` 값을 출력하지 않는다. AWS 설정이 없어도 빌드, 테스트, 앱 시작은 되어야 한다. 테스트와 CI는 실제 S3를 부르지 않고 메모리 저장소로 대체한다.
- 기존 테스트 변경 금지의 예외는 아래뿐이다.
  - 새 오류 코드 `invalid_file_type`, `file_too_large`, `storage_quota_exceeded`, `attachment_removed`를 ApiExceptionHandlerTest의 EXPECTED 표에 한 줄씩 더한다(이슈 #63 지시).
  - ResponseChangeApiTest의 `attachments를_보내면_invalid_request이고_바뀌지_않는다`, `첨부가_연결된_응답을_취소하면_연결만_끊고_행을_남긴다`는 T10이 T12 전까지의 임시 동작으로 고정한 것이다. SPEC 222행에 맞게 바꿀 수 있고, 바꾼 내용을 보고한다.
- 다음은 바꾸지 않는다: SecurityConfig, DB 스키마와 Flyway, CI, 스키마 파일, 프롬프트, SPEC, ARCHITECTURE, AGENTS, data/seed, tests/harness, ResponseRepository의 기존 쿼리. 새 조회·삭제 메서드 추가와 multipart 한도 설정은 할 수 있다. 그 밖을 바꿔야만 끝나면 멈추고 묻는다.
- 새 의존성은 이유를 적어 승인받은 뒤에만 추가한다(예: AWS SDK, 파일 형식 판별 라이브러리). 이미 런타임에 있는 라이브러리를 먼저 확인한다.
- SPEC에 없는 동작은 추측하지 말고, 맥락과 선택지를 적어 객관식으로 묻는다. 예: 스키마에 파일 문항 표시가 없을 때 어떤 문항에 응답 파일을 받을지, 파일만 올린 답이 필수 문항을 채우는지, HWPX·DOCX처럼 ZIP 구조인 형식의 내용 판별 범위, 폼 정의와 답의 "저장 크기" 계산 방식, S3 실패 때 오류 코드, 커밋 뒤 S3 삭제 실패분의 처리, Presigned URL 유효 시간, `PATCH /forms`에 다른 첨부 목록이 올 때, 복사본의 첨부 처리, 저장 공간 화면의 위치, 응답이 0건인 폼을 삭제할 때의 내보내기 질문.
- 같은 실패가 3회차를 넘거나 사람을 기다리는 시간을 뺀 작업이 2시간을 넘으면 멈추고 보고한다. 작업 트리는 그대로 둔다.

[참조]
- 이슈 #63
- AGENTS.md 3~6절, 세션 규칙과 공통 파일 97~105행, 파일과 환경변수 117~120행
- docs/SPEC.md
  - 범위: 43~44행
  - API: `GET /me/storage` 100행, 폼과 첨부 115~124행, 제출·수정 188·194행, 파일 조회 196~198행, 전체 삭제 237~238행
  - 첨부와 용량: 169~172행, 폼 삭제 흐름 173행
  - 초안과 파일: 205·208행, 제출 검증 216행
  - 파일 형식·표시·한도·연결: 219~222행, 수정과 삭제 223~224행, 이력 조건 226행
  - 수용 기준과 테스트 줄: AC2(374행), AC14(445~446행), AC63(454~457행), AC64(458~459행), AC65(460~461행), AC66(462~463행), AC67(464~465행), AC70(466~467행), AC82(471~472행), AC83(473~474행), AC68(483~484행), AC16(485~486행), AC17(494~495행), AC19의 삭제 항목(505행)
  - 추적표: 640~644·647·659·660행
- docs/ARCHITECTURE.md 3절, 10절의 표, 삭제 규칙, 파일
- docs/prompts/delegation_response_edit_cancel.md(취소한 첨부), delegation_response_draft.md(T12로 넘긴 항목), delegation_response_screen.md, delegation_my_forms.md(T12로 넘긴 항목), delegation_export.md
- src/passform/schemas/form.schema.json
- src/main/resources/db/migration/V1__create_schema.sql의 forms, responses, attachments, saved_forms
- src/main/resources/application.properties, .env.example
- src/main/java/com/opinio/passform/의 domain/attachment/Attachment.java, domain/response/Response.java·ResponseChangePolicy.java, domain/form/Form.java, domain/error/, repository/AttachmentRepository.java·ResponseRepository.java·FormRepository.java·UserRepository.java, service/FormService.java·SubmissionService.java·ResponseChangeService.java·DraftService.java·ResponseHistoryService.java·ResultService.java·ExportService.java·InboxService.java·PublicFormService.java·CurrentUserService.java, controller/FormController.java·SubmissionController.java·ResponseChangeController.java·ResultController.java, controller/advice/ApiExceptionHandler.java, config/SecurityConfig.java
- src/test/java/com/opinio/passform/의 controller/ResponseChangeApiTest.java·MyFormsApiTest.java, controller/advice/ApiExceptionHandlerTest.java, config/CsrfRealServerTest.java·PublicPathRealServerTest.java, support/
- frontend/src/의 pages/BuilderPage.tsx·CreatedPage.tsx·ResultsPage.tsx·ResponsePage.tsx·ResponseDetailPage.tsx·HistoryPage.tsx·ExportDialog.tsx·InboxPage.tsx, builder/QuestionEditor.tsx·formDefinition.ts, response/draftSync.ts, api/client.ts, types/form.ts·response.ts·results.ts, routes/, demo/
- frontend/docs/ROUTES_AND_API.md

[완료 기준]
- 이슈 #63 완료 조건과 AGENTS.md 6절을 충족한다.
- 테스트 근거(DB 동작은 실제 Postgres, 파일은 메모리 저장소로 확인):
  - AC63, AC64, AC65, AC66, AC67, AC70 테스트 줄의 각 항목
  - 위임에서 T12로 넘어온 항목: AC14의 사용량·파일 보존, AC82의 파일 항목, AC83의 삭제 항목, AC68의 파일 첨부, AC17의 `bulk_delete_keeps_submitted_history`, AC19의 전체 삭제 뒤 응답 수
  - 모든 실패 경로(형식, 25MB, 500MB, 동의, 버전, 마감, S3 저장 실패, DB 저장 실패) 뒤 응답·첨부 행·폼 버전·사용량·저장소 객체가 그대로다. 저장소 객체 수까지 단언한다.
  - 파일 형식: 허용 형식 각각의 통과, 확장자만 바꾼 실행 파일 거부, 제작자와 응답자의 허용 목록 차이
  - 다운로드: 공개 현재 버전, 과거 버전의 제작자·해당 버전 제출자 허용과 그 밖 거부, 초안의 제작자 전용, 경로의 폼·문항·응답과 맞지 않는 `attachment_id`는 404, 다른 응답자 404, 비로그인 401, 지운 응답 첨부 410, 삭제된 폼 404
  - PDF·MP4는 다운로드로 전달되고 이미지만 화면에 바로 보인다. 저장한 `content_type`은 판별한 값이다.
  - 수정: 첨부 생략이면 유지, 새 목록이면 교체, 교체로 끊긴 파일 중 참조 없는 것만 삭제, 실패 시 기존 응답과 첨부 불변
  - 취소: 취소한 응답의 파일이 다운로드되지 않고 어느 사용량에도 잡히지 않는다.
  - 사용량: 정확히 500,000,000바이트 허용, 1바이트 초과 409. 실제로 500MB를 보내지 않고 첨부 행으로 채운다. 폼 생성·수정, 첨부, 초안 저장, 제출, 응답 수정의 각 경로에서 확인한다. 복사 API가 main에 있으면 복사도 확인한다.
  - 동시 저장 두 건이 합쳐서 한도를 넘을 때 하나만 성공하고 500이 나지 않는다. 같은 폼의 제출과 전체 삭제가 겹쳐도 일부만 삭제되지 않는다.
  - 삭제: DB 실패면 S3 삭제 호출 0회이고 모든 행이 그대로다. 비로그인 응답과 그 첨부 행은 FK 오류 없이 지워진다. 작성 중 응답은 받은 응답 삭제 대상이 아니다. 제작자가 아니면 403이고 설정 `editable`·`cancellable`과 무관하다.
  - 폼 삭제: 정의와 버전 제거, `forms` 행 유지, 공유 링크와 재제출 404, 제작자 목록·결과·분석·내보내기에서 제외, 응답자 이력의 제출 버전 문구 유지, 수정·취소 `409 history_only`
  - multipart: 실제 서버 테스트로 비로그인 제출의 CSRF 통과와, Spring 기본 업로드 한도에 걸리지 않고 25MB 합계가 `413 file_too_large`로 판정되는지 확인한다.
  - 추적표의 골든 id가 golden_cases.yaml에 있으면 실행기로 돌린다. 없으면 일반 테스트로 하고 테스트 이름이나 보고에 id를 적는다. YAML에 케이스를 더하지 않는다.
  - 화면: 빌더의 설명란·문항 첨부 추가·제거, 응답 화면의 이미지 표시와 PDF·MP4 다운로드 링크, 응답 파일 선택·복원·재첨부 안내(계정·폼별 구분), 오류별 안내, 저장 공간 표시, 응답 전체 삭제·응답 첨부 전체 삭제 확인, 폼 삭제 전 내보내기 질문과 첨부 미포함 안내, 내보내기 실패 시 삭제 안 함, 건너뛰기 뒤 삭제, 조회 전용 이력, 삭제된 첨부 표시
  - 기존 데모 모드 테스트가 그대로 통과한다.
- 실제 화면 확인을 사람에게 요청하고 결과를 기록한다. 실제 S3 버킷과 자격 증명은 사람이 준비한다.
  - 초안과 배포 폼에 첨부하고 응답 화면에서 보기·받기, 첨부 제거 뒤 과거 버전 응답자의 다운로드
  - 로그인·비로그인 파일 제출, 제작자와 제출자의 다운로드, 새로고침 복원, 다른 브라우저의 재첨부 안내
  - 실패한 업로드 뒤 버킷에 객체가 남지 않음
  - 사용량 표시, 응답 전체 삭제·응답 첨부 전체 삭제 뒤 사용량 감소와 버킷 객체 삭제
  - 폼 삭제 흐름 세 가지(내보내기 성공, 내보내기 실패, 건너뛰기)와 조회 전용 이력
  - 360px 폭에서 파일 첨부와 제출을 가로 스크롤 없이 터치로 사용
  - 시험 데이터용 스크립트는 에이전트가 준비하되, 승인 없이 개발 DB나 실제 버킷에 실행하지 않는다.
- 결함 주입: 아래를 하나씩 넣었을 때 테스트가 실패해야 한다. 기대한 테스트가 실제로 실행됐는지 build/test-results XML(프런트는 Vitest 결과)로 확인하고, 실행되지 않았으면 판정 무효로 다시 돌린다. 주입은 커밋하지 않고, 항목마다 되돌린다.
  - 확장자만 검사한다
  - 클라이언트 `Content-Type`을 저장한다
  - 응답 파일 한도를 파일 하나 기준으로 판정한다
  - 25MB 한도를 응답 전체 합계로 판정한다
  - 사용량 비교에서 `>=`로 정확히 500MB를 거부한다
  - 제출에서 500MB 검사를 뺀다
  - 응답 파일을 응답자의 사용량에 센다
  - 제출 성공 뒤 초안 사용량을 해제하지 않는다
  - 실패한 요청의 S3 원본을 지우지 않는다
  - 다운로드에서 폼·응답과 첨부의 소속 검사를 뺀다
  - 과거 버전 파일을 공개로 내준다
  - 지운 첨부를 410 대신 404로 낸다
  - 배포 폼의 첨부 변경이 새 버전을 만들지 않는다
  - 첨부 제거가 과거 버전이 참조하는 원본을 지운다
  - 취소로 끊긴 행을 제작자 사용량에 센다
  - 수정에서 첨부 생략 시 기존 파일을 지운다
  - DB 커밋 전에 S3 원본을 지운다
  - 응답 전체 삭제가 로그인 응답의 이력 복사를 빠뜨린다
  - 비로그인 응답을 이력으로 남긴다
  - 응답 첨부 전체 삭제가 응답 수를 줄인다
  - 응답 첨부 전체 삭제가 사용량을 줄이지 않는다
  - 폼 삭제가 `forms` 행까지 지운다
  - 폼 삭제 뒤 공유 링크가 열린다
  - 삭제 API에서 소유권 검사를 뺀다
  - 화면에서 내보내기 실패 뒤 삭제를 진행한다
  - 화면에서 첨부 미포함 안내를 뺀다
- 위임 기록을 docs/prompts/delegation_attachments_storage_delete.md에 남긴다. 넣을 것: 이 프롬프트 원본, 회차 표, 결함 주입 결과, 사람이 정한 답.

[범위 밖]
- 특정 응답이나 응답자만 골라 파일을 지우는 기능(SPEC에서 제공하지 않음)
- 내보내기에 첨부 원본 포함
- ZIP 내부 검사와 바이러스 검사
- 도메인 연결과 배포 자체(E9), 마감 알림 발송(E12)
- 폼 복사 API 자체. 이번 작업은 복사 API가 있을 때 한도 검사만 더한다.

[깃허브 규칙]
- 사람의 명시적 승인을 받은 뒤에만 커밋·push·PR을 한다. 승인 요청에는 `git status --short`, `git diff --stat`, 핵심 diff, 테스트 집계, 제안 커밋 메시지를 보여 준다. 커밋 승인은 push 승인이 아니다.
- 커밋은 AGENTS.md 5절 협업 항목과 README.md를 따른다. 공통 파일(paths, types, client, 공통 컴포넌트) 변경은 별도 커밋으로 나눈다. `git add`는 경로를 명시한다.
- PR 제목: `[feat] 첨부, 저장 공간, 삭제 (#63)`. 본문은 README.md를 따른다.
- 리뷰어는 gh pr create --reviewer ye-nni로 지정한다.
- main에 직접 push하거나 머지하지 않는다.

[보고]
- 보고 순서: 실행한 명령과 결과 / 완료 기준 항목별 결과 / 테이블 표 / 추가한 의존성과 이유 / 지시와 다르게 한 것 / 판단이 필요해 멈춘 것 / PR 링크와 CI 결과.
- 테스트마다 통과 이유를 코드 위치와 함께 한 줄로 적는다.
- AC63~AC67, AC70의 테스트 줄과 위임에서 넘어온 항목마다 자동 테스트, 사람 확인, 범위 밖 중 무엇으로 처리했는지 적는다.
- 바꾼 기존 테스트가 있으면 이름과 이유를 적는다.
````
