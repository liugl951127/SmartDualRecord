-- =================================================================
-- 双录一体化中台 · 数据库 Schema
-- 8 张核心表 + 4 张过程表 · 涵盖：业务/录像/质检/话术/产品/合规
-- 适配 H2 MODE=MySQL + MariaDB
-- =================================================================

SET MODE MySQL;

-- ---------- 1. 业务主表 ----------
DROP TABLE IF EXISTS tb_business;
CREATE TABLE tb_business (
    id                  VARCHAR(32) PRIMARY KEY,
    business_id         VARCHAR(64) NOT NULL UNIQUE,
    business_type       VARCHAR(32) NOT NULL,        -- INSURANCE / WEALTH / FUND
    product_id          VARCHAR(64) NOT NULL,
    customer_id_hash    VARCHAR(64) NOT NULL,
    seller_id_hash      VARCHAR(64),
    channel             VARCHAR(32) NOT NULL,        -- OFFLINE / REMOTE_VIDEO / SELF_AI / INTERNET_TEXT
    state               VARCHAR(32) NOT NULL,        -- 状态机当前状态
    current_node        VARCHAR(32),                 -- 当前 8 节点中的哪一个
    amount              DECIMAL(18,2),
    risk_level          VARCHAR(8),                  -- 客户风险等级 C1-C5
    product_risk_level  VARCHAR(8),                  -- 产品风险等级 P1-P5 / R1-R5
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at         TIMESTAMP NULL,
    deleted             TINYINT NOT NULL DEFAULT 0,
    INDEX idx_business_id (business_id),
    INDEX idx_customer (customer_id_hash),
    INDEX idx_state (state)
);

-- ---------- 2. 录像表（可一对多：跨段）----------
DROP TABLE IF EXISTS tb_recording;
CREATE TABLE tb_recording (
    id              VARCHAR(32) PRIMARY KEY,
    rec_id          VARCHAR(64) NOT NULL UNIQUE,
    business_id     VARCHAR(64) NOT NULL,
    channel         VARCHAR(32) NOT NULL,
    seller_type     VARCHAR(16) NOT NULL,            -- HUMAN / AI_DIGITAL_HUMAN
    rec_start_utc   TIMESTAMP(3) NOT NULL,
    rec_end_utc     TIMESTAMP(3),
    duration_ms     BIGINT,
    file_path       VARCHAR(512),
    file_sha256     VARCHAR(128),
    file_size_bytes BIGINT,
    encryption      VARCHAR(32),
    blockchain_tx   VARCHAR(128),
    watermark_visible TINYINT(1) DEFAULT 0,
    audio_id_per_minute INT DEFAULT 0,
    linked_rec_id   VARCHAR(64),                    -- 跨段关联
    location_branch VARCHAR(64),
    retention_until DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    INDEX idx_business (business_id),
    INDEX idx_rec (rec_id)
);

-- ---------- 3. 录像节点明细（8 节点各 1 条）----------
DROP TABLE IF EXISTS tb_rec_node;
CREATE TABLE tb_rec_node (
    id              VARCHAR(32) PRIMARY KEY,
    business_id     VARCHAR(64) NOT NULL,
    rec_id          VARCHAR(64) NOT NULL,
    node_id         VARCHAR(32) NOT NULL,           -- 01-identity / 02-disclosure / ...
    node_name       VARCHAR(64) NOT NULL,
    start_utc       TIMESTAMP(3),
    end_utc         TIMESTAMP(3),
    duration_ms     BIGINT,
    completed       TINYINT(1) NOT NULL DEFAULT 0,
    evidence_ts     TIMESTAMP(3),                   -- 证据时间戳
    operator_id     VARCHAR(64),
    deleted         TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_business_node (business_id, rec_id, node_id),
    INDEX idx_business (business_id)
);

-- ---------- 4. 质检结果表 ----------
DROP TABLE IF EXISTS tb_qa_result;
CREATE TABLE tb_qa_result (
    id                  VARCHAR(32) PRIMARY KEY,
    qa_id               VARCHAR(64) NOT NULL UNIQUE,
    rec_id              VARCHAR(64) NOT NULL,
    business_id         VARCHAR(64) NOT NULL,
    checker_type        VARCHAR(16) NOT NULL,       -- AI / HUMAN / AI_PLUS_HUMAN
    ai_model_version    VARCHAR(32),
    ai_qa_score         DECIMAL(5,2),
    ai_qa_result        VARCHAR(32),                -- PASS / PASS_WITH_FINDINGS / FAIL
    issues_json         TEXT,                       -- JSON 数组
    human_reviewer_id   VARCHAR(64),
    human_review_status VARCHAR(32),
    rectification_status VARCHAR(32),
    check_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT NOT NULL DEFAULT 0,
    INDEX idx_business (business_id),
    INDEX idx_rec (rec_id)
);

-- ---------- 5. 事件流（事件溯源）----------
DROP TABLE IF EXISTS tb_event;
CREATE TABLE tb_event (
    id              VARCHAR(32) PRIMARY KEY,
    business_id     VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    event_data      TEXT,                           -- JSON
    from_state      VARCHAR(32),
    to_state        VARCHAR(32),
    actor_id        VARCHAR(64),
    actor_type      VARCHAR(16),                    -- SYSTEM / HUMAN / AI
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_business (business_id),
    INDEX idx_created (created_at)
);

-- ---------- 6. 话术模板表 ----------
DROP TABLE IF EXISTS tb_script_template;
CREATE TABLE tb_script_template (
    id                  VARCHAR(32) PRIMARY KEY,
    product_id          VARCHAR(64) NOT NULL,
    product_type        VARCHAR(32) NOT NULL,
    version             VARCHAR(32) NOT NULL,
    risk_level          VARCHAR(8),
    mandatory_disclosure TEXT,                      -- JSON array
    forbidden_phrases   TEXT,                       -- JSON array
    required_questions  TEXT,                       -- JSON array
    channel_overrides   TEXT,                       -- JSON
    content_hash        VARCHAR(128),               -- 跨渠道 hash 校验
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT / APPROVED / FROZEN
    approved_by         VARCHAR(64),
    approved_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_product_version (product_id, version)
);

-- ---------- 7. 风险评估表 ----------
DROP TABLE IF EXISTS tb_risk_assessment;
CREATE TABLE tb_risk_assessment (
    id                  VARCHAR(32) PRIMARY KEY,
    business_id         VARCHAR(64),
    customer_id_hash    VARCHAR(64) NOT NULL,
    assessment_id       VARCHAR(64) NOT NULL UNIQUE,
    answers_json        TEXT,                       -- JSON
    overall_score       DECIMAL(5,2),
    risk_level          VARCHAR(8) NOT NULL,        -- C1-C5
    valid_until         DATE NOT NULL,
    assessed_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT NOT NULL DEFAULT 0,
    INDEX idx_customer (customer_id_hash),
    INDEX idx_valid (valid_until)
);

-- ---------- 8. 禁播词表（全局）----------
DROP TABLE IF EXISTS tb_forbidden_phrase;
CREATE TABLE tb_forbidden_phrase (
    id              VARCHAR(32) PRIMARY KEY,
    phrase          VARCHAR(256) NOT NULL,
    severity        VARCHAR(16) NOT NULL,           -- HIGH / MEDIUM / LOW
    product_types   VARCHAR(256),                  -- 适用产品类型，逗号分隔；ALL=全适用
    regulation_ref  VARCHAR(256),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_phrase (phrase)
);
