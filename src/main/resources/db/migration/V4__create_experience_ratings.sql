-- 테스트 기간 한정 사용 경험 평가(SPEC 4절, AC95). 응답과 분리해 둔다.
-- 행은 제출 직후 일회용 표를 만들 때 생기고(표 원문 없이 해시만), 평가를 보내면 rated_at·ease·improvement가 채워진다.
-- 응답이 취소·삭제되면 연결만 끊고 평가는 남긴다(사람 결정 11/24). 최종 평가 집계가 끝나면 사람이 지운다(docs/experience_survey.md).
-- form_id에는 외래 키를 걸지 않는다. 폼 행이 지워지는 경로가 이 표 때문에 막히지 않게 하기 위해서다.
CREATE TABLE experience_ratings (
    experience_rating_id uuid        PRIMARY KEY,
    response_id          uuid        UNIQUE REFERENCES responses (response_id) ON DELETE SET NULL,
    form_id              uuid        NOT NULL,
    token_hash           varchar(64) NOT NULL,
    token_issued_at      timestamptz NOT NULL,
    rated_at             timestamptz,
    ease                 varchar(20) CHECK (ease IN ('very_hard', 'hard', 'easy', 'very_easy')),
    improvement          text        CHECK (char_length(improvement) <= 1000)
);
