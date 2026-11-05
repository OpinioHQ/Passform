-- docs/ARCHITECTURE.md 10절의 MVP 테이블. 초기 데이터는 넣지 않는다(E2, E13이 사람이 쓴 문구로 더한다).
-- 시각은 모두 UTC로 저장하고 값은 주입한 Clock에서 온다. 그래서 DEFAULT now()를 쓰지 않는다.
-- enum은 Java enum 이름(대문자)으로 저장한다. API의 소문자 표기는 DTO가 맡는다.

CREATE TABLE users (
    user_id           uuid         PRIMARY KEY,
    name              text         NOT NULL,
    email             text         NOT NULL,
    deadline_reminder boolean      NOT NULL,
    oauth_provider    varchar(50)  NOT NULL,
    oauth_subject     varchar(255) NOT NULL,
    created_at        timestamptz  NOT NULL,
    CONSTRAINT uq_users_oauth UNIQUE (oauth_provider, oauth_subject)
);

CREATE TABLE profiles (
    profile_id    uuid        PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES users (user_id),
    label         text        NOT NULL,
    personal_info jsonb       NOT NULL,
    created_at    timestamptz NOT NULL
);
CREATE INDEX ix_profiles_user ON profiles (user_id);

-- deadline, editable, cancellable, response_limit은 현재 운영 설정의 정본이다.
-- working_definition은 폼을 삭제하면 지우고 행은 남긴다(이력이 form_id를 돌려준다).
CREATE TABLE forms (
    form_id            uuid         PRIMARY KEY,
    owner_id           uuid         NOT NULL REFERENCES users (user_id),
    status             varchar(20)  NOT NULL,
    slug               varchar(100),
    definition_version integer,
    deadline           timestamptz,
    editable           boolean      NOT NULL,
    cancellable        boolean      NOT NULL,
    response_limit     integer,
    working_definition jsonb,
    ai_summary_notice  jsonb,
    deleted_at         timestamptz,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT uq_forms_slug UNIQUE (slug)
);
CREATE INDEX ix_forms_owner ON forms (owner_id);

CREATE TABLE form_versions (
    form_id    uuid        NOT NULL REFERENCES forms (form_id),
    version    integer     NOT NULL,
    definition jsonb       NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_form_versions PRIMARY KEY (form_id, version)
);

-- form_versions로 가는 FK는 두지 않는다. 폼을 삭제하면 버전은 지워지고 로그인 응답의 이력은 남기 때문이다.
CREATE TABLE responses (
    response_id        uuid        PRIMARY KEY,
    form_id            uuid        NOT NULL REFERENCES forms (form_id),
    user_id            uuid        REFERENCES users (user_id),
    form_version       integer     NOT NULL,
    status             varchar(20) NOT NULL,
    content            jsonb       NOT NULL,
    ai_summary_agreed  boolean     NOT NULL,
    saved_at           timestamptz,
    submitted_at       timestamptz,
    updated_at         timestamptz,
    creator_deleted_at timestamptz,
    history_form_title text,
    history_questions  jsonb
);
CREATE INDEX ix_responses_form_status ON responses (form_id, status);
CREATE INDEX ix_responses_user_status ON responses (user_id, status);
-- 로그인 사용자의 작성 중 응답은 폼당 하나(SPEC 5절 206행). 제출 응답과 비로그인 응답은 여러 건이다(AC51).
CREATE UNIQUE INDEX uq_responses_login_draft ON responses (user_id, form_id)
    WHERE status = 'IN_PROGRESS' AND user_id IS NOT NULL;

CREATE TABLE saved_forms (
    user_id    uuid        NOT NULL REFERENCES users (user_id),
    form_id    uuid        NOT NULL REFERENCES forms (form_id),
    state      varchar(20) NOT NULL,
    saved_at   timestamptz NOT NULL,
    CONSTRAINT pk_saved_forms PRIMARY KEY (user_id, form_id)
);

CREATE TABLE notifications (
    notification_id uuid        PRIMARY KEY,
    user_id         uuid        NOT NULL REFERENCES users (user_id),
    form_id         uuid        NOT NULL REFERENCES forms (form_id),
    type            varchar(20) NOT NULL,
    read            boolean     NOT NULL,
    created_at      timestamptz NOT NULL,
    CONSTRAINT uq_notifications_user_form_type UNIQUE (user_id, form_id, type)
);

CREATE TABLE consent_templates (
    consent_template_id varchar(100) PRIMARY KEY,
    purpose             text         NOT NULL,
    retention           text         NOT NULL
);

CREATE TABLE question_templates (
    template_id   varchar(100) PRIMARY KEY,
    purpose       varchar(100) NOT NULL,
    audience      varchar(100) NOT NULL,
    question_note text         NOT NULL,
    question_type varchar(20)  NOT NULL,
    options       jsonb,
    input_format  jsonb
);
CREATE INDEX ix_question_templates_purpose_audience ON question_templates (purpose, audience);

-- 원본은 S3에 있다. response_id가 없으면 제작자 첨부, question_id가 없으면 폼 설명란 첨부다.
CREATE TABLE attachments (
    attachment_id uuid        PRIMARY KEY,
    form_id       uuid        NOT NULL REFERENCES forms (form_id),
    response_id   uuid        REFERENCES responses (response_id),
    question_id   varchar(100),
    filename      text        NOT NULL,
    content_type  varchar(255) NOT NULL,
    size_bytes    bigint      NOT NULL,
    s3_key        text        NOT NULL,
    available     boolean     NOT NULL,
    created_at    timestamptz NOT NULL
);
CREATE INDEX ix_attachments_form ON attachments (form_id);
CREATE INDEX ix_attachments_response ON attachments (response_id);
