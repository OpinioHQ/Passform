# 사용 경험 평가 운영 (테스트 기간 한정)

최종 평가 지표 "폼 작성 경험 긍정 평가율"(`docs/PROBLEM.md` 112~115행)을 재는 선택 설문의 켜기·끄기, 제외 기준, 집계·삭제 방법이다. 제품 동작은 `docs/SPEC.md` 4절 "테스트 기간 한정 — 사용 경험 평가"와 AC95가 정본이다. 관리자 화면은 없고, 집계는 서버 DB에서 사람이 SQL로 한다.

## 1. 무엇인가

- 새 응답 제출에 성공한 사람(로그인·비로그인 모두)에게 제출 완료 화면에서 두 문항을 선택으로 받는다. 제출 후 수정·빌더 미리보기·가짜 데이터 모드에는 없고, 건너뛴 평가는 다시 띄우지 않는다.
  - "이번 폼을 작성하는 과정은 얼마나 쉬웠나요?" — 매우 어려움 / 어려움 / 쉬움 / 매우 쉬움 (DB 값 `very_hard`·`hard`·`easy`·`very_easy`)
  - "어떤 점을 개선하면 좋을까요?" — 자유 서술, 최대 1000자
- 척도를 5단계에서 4단계로 바꾼 이유: 테스트 때 '보통'의 기준을 응답자가 헷갈려할 수 있어, '쉬움'·'어려움'이 분명하도록 팀원 합의 하에 측정 시작 전에 4단계로 바꿨다.
- 저장: 표 `experience_ratings`(V4). 제출 직후 일회용 표의 해시와 함께 한 행이 생기고, 평가를 보내면 `rated_at`·`ease`·`improvement`가 채워진다. 응답과 분리되어 제작자의 결과 통계·AI 분석·내보내기·`response_count`·저장 공간 사용량에 들어가지 않고, 제작자 화면 어디에도 보이지 않는다. 평가 글은 LLM·임베딩 등 외부로 보내지 않는다.
- 응답이 취소·삭제되면 `response_id`만 `null`이 되고 평가 행은 남는다. 이런 평가는 아래 집계에서 빠진다(지금 남아 있는 제출에 연결된 평가만 센다).

## 2. 켜기·끄기

서버 설정 `EXPERIENCE_SURVEY_ENABLED`(기본 `false`) 하나로 켜고 끈다. 꺼져 있으면 화면에 설문이 보이지 않고, 서버도 평가 저장을 `409 survey_closed`로 거부한다.

1. 켜기 전에 3절의 제외 목록(계정 ID, 폼 ID)을 확정한다.
2. 서버에서 `deploy/.env`의 값을 바꾼다: `EXPERIENCE_SURVEY_ENABLED=true`(끌 때는 `false`).
3. app만 다시 띄운다: `cd ~/Passform/deploy && docker compose up -d app`. 다시 띄우면 로그인 세션이 모두 풀린다(`deploy/README.md` 7절).
4. 확인: 시험용 폼에 제출해 완료 화면에 설문이 보이는지(끌 때는 안 보이는지) 본다. 이 시험 제출은 제외 목록의 폼에서 한다.
5. 아래 표에 시각을 적는다. 이 시각이 4절 SQL의 기간이 된다.

| 구분 | 시각(KST) | 시각(UTC) | 한 사람 | 비고 |
|---|---|---|---|---|
| 켬 | | | | |
| 끔 | | | | |

## 3. 제외 기준

자유 사용 규모와 같은 기준으로 개발용·시드 데이터와 관찰 과제 기록을 뺀다(`docs/PROBLEM.md` 85·106행).

- 팀원 두 계정이 제출한 응답 (계정 ID로 뺀다)
- 팀 시험용 폼에 들어온 응답 (폼 ID로 뺀다)
- 관찰 과제 폼에 들어온 응답 (폼 ID로 뺀다)
- 시드 데이터 폼에 들어온 응답 (폼 ID로 뺀다)

실제 ID 목록은 저장소에 적지 않고 켜기 전에 사람이 확정한다. 목록은 4절 SQL 앞부분의 값으로만 넣는다.

한계: 팀원이 로그인하지 않고 제출한 응답은 계정으로 가릴 수 없다. 팀원은 시험 제출을 제외 목록의 폼에서만 한다.

## 4. 집계 SQL

서버에서 아래 SQL을 저장소 밖 파일(예: `~/experience_aggregate.sql`)로 복사하고, 맨 앞의 기간과 제외 목록만 채운 뒤 실행한다. 읽기만 하고 아무것도 바꾸지 않는다.

```sh
cd ~/Passform/deploy
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < ~/experience_aggregate.sql
```

```sql
WITH params AS (
    SELECT timestamptz '2026-11-25 09:00:00+09' AS started_at,  -- 켠 시각(2절 표)
           timestamptz '2026-12-01 18:00:00+09' AS ended_at     -- 끈 시각(2절 표)
),
-- 제외 목록. 한 줄에 하나씩 ('ID'::uuid) 를 쉼표로 더한다. 비어 있으면 (NULL::uuid) 한 줄만 둔다.
excluded_users (user_id) AS (
    VALUES (NULL::uuid)
),
excluded_forms (form_id) AS (
    VALUES (NULL::uuid)
),
-- 집계 대상 제출: 기간 안에 제출되었고 지금 남아 있는 제출 응답 중 제외 목록 밖의 것
counted AS (
    SELECT r.response_id
    FROM responses r
    CROSS JOIN params p
    WHERE r.status = 'SUBMITTED'
      AND r.creator_deleted_at IS NULL
      AND r.submitted_at >= p.started_at
      AND r.submitted_at < p.ended_at
      AND r.form_id NOT IN (SELECT form_id FROM excluded_forms WHERE form_id IS NOT NULL)
      AND (r.user_id IS NULL
           OR r.user_id NOT IN (SELECT user_id FROM excluded_users WHERE user_id IS NOT NULL))
),
-- 유효 평가: 집계 대상 제출에 연결되고 첫 문항(작성 용이성)에 답한 평가. 제출 1건당 1행이다.
valid AS (
    SELECT e.ease
    FROM experience_ratings e
    JOIN counted c ON c.response_id = e.response_id
    WHERE e.rated_at IS NOT NULL
      AND e.ease IS NOT NULL
),
totals AS (
    SELECT (SELECT count(*) FROM counted)                          AS submissions,
           count(*)                                                AS valid_ratings,
           count(*) FILTER (WHERE ease = 'very_hard')              AS very_hard,
           count(*) FILTER (WHERE ease = 'hard')                   AS hard,
           count(*) FILTER (WHERE ease = 'easy')                   AS easy,
           count(*) FILTER (WHERE ease = 'very_easy')              AS very_easy,
           count(*) FILTER (WHERE ease IN ('easy', 'very_easy'))   AS positive
    FROM valid
)
SELECT submissions                                                 AS "집계 대상 제출 수",
       valid_ratings                                               AS "유효 평가 수",
       very_hard                                                   AS "매우 어려움",
       hard                                                        AS "어려움",
       easy                                                        AS "쉬움",
       very_easy                                                   AS "매우 쉬움",
       round(100.0 * positive / nullif(valid_ratings, 0), 1)       AS "긍정 평가율(%)",
       round(100.0 * valid_ratings / nullif(submissions, 0), 1)    AS "유효 평가 응답률(%)",
       CASE
           WHEN valid_ratings < 10 THEN '판정 보류(유효 평가 10건 미만)'
           WHEN 100.0 * positive / valid_ratings >= 90 THEN '목표 달성(90% 이상)'
           ELSE '목표 미달(90% 미만)'
       END                                                         AS "판정"
FROM totals;
```

- 긍정 평가율 = (쉬움 + 매우 쉬움) / 유효 평가 수. 첫 문항을 비우고 개선 의견만 쓴 평가와 평가하지 않은 제출은 분자·분모 어디에도 넣지 않는다(PROBLEM 114행).
- 유효 평가가 10건보다 적으면 목표 달성 판정을 보류한다(PROBLEM 112행). 유효 평가 수, 선택지별 건수, 응답률을 함께 공개한다.
- 사용자 비율이 아니라 평가 건수의 비율이다. 같은 사람이 여러 폼을 평가할 수 있다(PROBLEM 115행).

## 5. 집계가 끝나면 지우기

최종 평가 집계가 끝나면 평가 행을 모두 지운다(처리방침 3절). 지우기 전에 2절대로 설정을 끈다. 표 자체는 Flyway가 관리하므로 남기고 행만 지운다.

```sql
BEGIN;
SELECT count(*) AS "지울 평가 행 수" FROM experience_ratings;
DELETE FROM experience_ratings;
COMMIT;
SELECT count(*) AS "남은 평가 행 수" FROM experience_ratings;  -- 0이어야 한다
```

- 서버 백업은 같은 서버에 하루 1번 만들고 만든 지 8일이 지나면 지운다(처리방침 5절). 지운 날로부터 8일이 지나면 백업에서도 평가가 사라진다.
- 지운 날짜를 2절 표의 비고나 이 문서에 적는다.

### 개별 삭제 요청

집계 전에 응답자가 1절 문의처로 자기 평가를 지워 달라고 하면, 로그인한 채 제출한 응답의 평가만 처리한다(처리방침 7절). 평가 표에는 사용자 ID가 없으므로 요청한 Google 이메일 → `users` → `responses`(그 폼, `user_id` 일치) → `experience_ratings`(`response_id` 일치) 순으로 찾는다. 제작자가 받은 응답을 지운 로그인 응답은 행이 남아 연결이 유지되므로 찾을 수 있다.

비로그인으로 제출했거나 제출을 취소해 연결이 끊긴(`response_id`가 `null`) 평가는 본인 것인지 확인할 수 없어 개별로 지우지 않는다. 최종 집계 뒤 위의 전체 삭제로 함께 지운다.

COMMIT 전에 건수를 확인해야 하므로 파일로 한 번에 넣지 않고, psql 대화 창에서 맨 앞 두 값만 채워 한 단계씩 실행한다. 실제 이메일·폼 ID는 이 문서나 저장소에 적지 않는다.

```sh
cd ~/Passform/deploy
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

```sql
\set email '요청한 Google 이메일'
\set form_id '평가를 보낸 폼 ID'
-- 폼 ID를 모르면 공유 링크의 slug로 찾는다: SELECT form_id, title FROM forms WHERE slug = '공유 링크의 slug';

-- 1) 찾기. 0이면 여기서 멈추고 지우지 않는다.
SELECT count(*) AS "찾은 평가 행 수"
FROM experience_ratings e
JOIN responses r ON r.response_id = e.response_id
JOIN users u ON u.user_id = r.user_id
WHERE lower(u.email) = lower(:'email')
  AND r.form_id = :'form_id'::uuid;

-- 2) 지우기. DELETE 건수가 1)의 수와 같을 때만 COMMIT하고, 다르면 ROLLBACK한다.
BEGIN;
DELETE FROM experience_ratings
WHERE response_id IN (
    SELECT r.response_id
    FROM responses r
    JOIN users u ON u.user_id = r.user_id
    WHERE lower(u.email) = lower(:'email')
      AND r.form_id = :'form_id'::uuid
);
COMMIT;

-- 3) 확인. 0이어야 한다.
SELECT count(*) AS "남은 평가 행 수"
FROM experience_ratings e
JOIN responses r ON r.response_id = e.response_id
JOIN users u ON u.user_id = r.user_id
WHERE lower(u.email) = lower(:'email')
  AND r.form_id = :'form_id'::uuid;
```

- 1)에서 찾은 행이 없으면 지우지 않고, 그 계정으로 그 폼에 로그인해 제출한 응답의 평가가 없다고 답한다(비로그인·취소한 제출의 평가는 처리방침 7절대로 집계 뒤 함께 지운다).
- 처리한 날짜와 지운 건수만 기록하고, 요청자 이메일은 저장소에 남기지 않는다.
