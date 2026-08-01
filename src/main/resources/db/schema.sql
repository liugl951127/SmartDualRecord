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
    -- v1.5 跨渠道补录字段
    failed_at_node      VARCHAR(16) NULL,            -- 线下失败的节点 (01-08)
    failed_reason       VARCHAR(32) NULL,            -- FORBIDDEN_PHRASE / NO_AFFIRMATIVE / BLACK_FRAME / FACE_MISSING / OTHER
    failed_detail       TEXT NULL,                   -- 失败明细 JSON
    resume_token        VARCHAR(64) NULL,            -- 线上补录 token (UUID, 24h 有效)
    started_channel     VARCHAR(16) NULL,            -- 起始渠道
    `deleted`             TINYINT NOT NULL DEFAULT 0,
    INDEX idx_business_id (business_id),
    INDEX idx_biz_customer (customer_id_hash),
    INDEX idx_state (state),
    INDEX idx_resume_token (resume_token)
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
    -- v1.2 录像合规增强字段
    quality_score       INT,                          -- 质量总分 0-100
    quality_status      VARCHAR(32),                  -- PASS / PASS_WITH_FINDINGS / FAIL
    resolution          VARCHAR(16),                  -- 1920x1080 / 1280x720
    fps                 INT,                          -- 帧率
    audio_bitrate       INT,                          -- 比特率 bps
    black_frame_ratio   DECIMAL(5,2),                 -- 黑屏帧占比 %
    customer_face_ratio DECIMAL(5,2),                 -- 客户人脸在场率 %
    third_party_count   INT DEFAULT 0,                -- 第三方人脸出现次数
    location_lat        DECIMAL(10,6),                -- GPS 纬度
    location_lng        DECIMAL(10,6),                -- GPS 经度
    ip_address          VARCHAR(45),                  -- IPv4/IPv6
    device_fingerprint  VARCHAR(64),                  -- 设备指纹
    encryption_iv       VARCHAR(32),                  -- SM4 IV
    signed_hash         VARCHAR(128),                 -- 业务方签名
    preservation_id     VARCHAR(64),                  -- 证据保全 ID
    retention_notified_at TIMESTAMP,                  -- 到期通知时间
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`         TINYINT NOT NULL DEFAULT 0,
    INDEX idx_rec_business (business_id),
    INDEX idx_rec_rec (rec_id),
    INDEX idx_rec_quality (quality_status),
    INDEX idx_rec_retention (retention_until)
);

-- ---------- 2.1 录像事件标注表 (8 节点进度 + 关键事件) ----------
DROP TABLE IF EXISTS tb_recording_annotation;
CREATE TABLE tb_recording_annotation (
    id              VARCHAR(32) PRIMARY KEY,
    rec_id          VARCHAR(64) NOT NULL,
    business_id     VARCHAR(64) NOT NULL,
    annotation_type VARCHAR(32) NOT NULL,            -- NODE_START / NODE_END / RISK_DISCLOSED / CUSTOMER_AFFIRMATIVE / SIGNED / MANUAL_FLAG
    node_id         VARCHAR(32),                     -- 关联 8 节点 ID
    timestamp_ms    BIGINT NOT NULL,                 -- 录像内时间偏移 (ms)
    note            VARCHAR(512),
    operator_id     VARCHAR(64),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ann_rec (rec_id),
    INDEX idx_ann_biz (business_id),
    INDEX idx_ann_type (annotation_type),
    `deleted`       TINYINT NOT NULL DEFAULT 0
);

-- ---------- 2.2 录像访问审计日志 (谁看了 + 多久 + 操作) ----------
DROP TABLE IF EXISTS tb_recording_access_log;
CREATE TABLE tb_recording_access_log (
    id              VARCHAR(32) PRIMARY KEY,
    rec_id          VARCHAR(64) NOT NULL,
    business_id     VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    user_role       VARCHAR(16) NOT NULL,            -- CUSTOMER / SELLER / AUDITOR / REGULATOR / ADMIN
    access_type     VARCHAR(16) NOT NULL,            -- PLAYBACK / DOWNLOAD / SCREENSHOT / EXPORT / PRESERVE
    duration_sec    INT,                             -- 看了多久
    ip_address      VARCHAR(45),
    access_token    VARCHAR(128),                    -- DRM token
    accessed_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_rec (rec_id),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_time (accessed_at),
    `deleted`       TINYINT NOT NULL DEFAULT 0
);

-- ---------- 2.3 断点续传 Session ----------
DROP TABLE IF EXISTS tb_upload_session;
CREATE TABLE tb_upload_session (
    id              VARCHAR(32) PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL UNIQUE,
    business_id     VARCHAR(64) NOT NULL,
    rec_id          VARCHAR(64),
    channel         VARCHAR(32) NOT NULL,
    total_chunks    INT NOT NULL,
    uploaded_chunks INT NOT NULL DEFAULT 0,
    chunk_size      INT NOT NULL DEFAULT 5242880,    -- 5MB
    total_size_bytes BIGINT,
    `status`          VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS / COMPLETED / EXPIRED / FAILED
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_chunk_at   TIMESTAMP,
    completed_at    TIMESTAMP,
    expires_at      TIMESTAMP NOT NULL,
    INDEX idx_us_sess (session_id),
    INDEX idx_us_biz (business_id),
    INDEX idx_us_status (`status`),
    `deleted`       TINYINT NOT NULL DEFAULT 0
);

-- ---------- 2.4 证据保全记录 (司法/公证) ----------
DROP TABLE IF EXISTS tb_preservation_record;
CREATE TABLE tb_preservation_record (
    id                  VARCHAR(32) PRIMARY KEY,
    preservation_id     VARCHAR(64) NOT NULL UNIQUE,
    rec_id              VARCHAR(64) NOT NULL,
    business_id         VARCHAR(64) NOT NULL,
    requester_id        VARCHAR(64) NOT NULL,
    requester_role      VARCHAR(16) NOT NULL,        -- AUDITOR / REGULATOR / COURT / CUSTOMER
    reason              VARCHAR(512) NOT NULL,
    notary_org          VARCHAR(64),                  -- 公证机构 (北京公证处 / 司法鉴定中心)
    notary_cert_no      VARCHAR(64),                  -- 公证书编号
    preserved_at        TIMESTAMP NOT NULL,
    preservation_hash   VARCHAR(128) NOT NULL,        -- 保全 hash
    file_sha256         VARCHAR(128),
    expires_at          TIMESTAMP,                    -- 保全有效期
    `status`              VARCHAR(16) NOT NULL,        -- SUBMITTED / NOTARIZED / REJECTED / EXPIRED
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pr_rec (rec_id),
    INDEX idx_pr_biz (business_id),
    INDEX idx_pr_status (`status`),
    `deleted`       TINYINT NOT NULL DEFAULT 0
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
    `deleted`         TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_business_node (business_id, rec_id, node_id)
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
    `deleted`             TINYINT NOT NULL DEFAULT 0,
    INDEX idx_qa_business (business_id),
    INDEX idx_qa_rec (rec_id)
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
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evt_business (business_id),
    INDEX idx_created (created_at),
    `deleted`       TINYINT NOT NULL DEFAULT 0
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
    `status`              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT / APPROVED / FROZEN
    approved_by         VARCHAR(64),
    approved_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`             TINYINT NOT NULL DEFAULT 0,
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
    date_add            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_upd            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`             TINYINT NOT NULL DEFAULT 0,
    INDEX idx_ra_customer (customer_id_hash),
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
    `deleted`         TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_phrase (phrase)
);

-- ---------- 9. 坐席推送文件表 (v1.5) ----------
DROP TABLE IF EXISTS tb_pushed_file;
CREATE TABLE tb_pushed_file (
    id              VARCHAR(32) PRIMARY KEY,
    business_id     VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL UNIQUE,
    file_name       VARCHAR(256) NOT NULL,
    file_type       VARCHAR(16) NOT NULL,            -- PDF / PNG / JPG / MP4 / TXT
    file_url        VARCHAR(512) NOT NULL,
    file_size       BIGINT,
    file_category   VARCHAR(32),                     -- BROCHURE / DISCLOSURE / CONTRACT / ID_CARD / OTHER
    pushed_by       VARCHAR(64),
    pushed_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    viewed_at       TIMESTAMP,
    signed_at       TIMESTAMP,
    rejected_at     TIMESTAMP,
    `status`          VARCHAR(16) NOT NULL DEFAULT 'PUSHED',  -- PUSHED / VIEWED / SIGNED / REJECTED
    signature_data  TEXT,                            -- 客户签字 base64
    remark          VARCHAR(512),
    `deleted`         TINYINT NOT NULL DEFAULT 0,
    INDEX idx_pf_business (business_id),
    INDEX idx_pf_status (`status`)
);

-- ---------- 10. 客户-理财经理会话表 (v1.5 H5 → PC 转接) ----------
DROP TABLE IF EXISTS tb_advisor_session;
CREATE TABLE tb_advisor_session (
    id              VARCHAR(32) PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL UNIQUE,
    business_id     VARCHAR(64) NOT NULL,
    customer_id_hash VARCHAR(64),
    customer_name   VARCHAR(64),
    customer_mobile VARCHAR(32),
    advisor_id      VARCHAR(64),
    advisor_name    VARCHAR(64),
    advisor_branch  VARCHAR(128),
    reason          VARCHAR(32),                -- TECH_ISSUE / PRODUCT_QUESTION / COMPLIANCE_QUERY / OTHER
    description     VARCHAR(512),
    `status`          VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING / ACCEPTED / DECLINED / ACTIVE / ENDED / TIMEOUT
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at     TIMESTAMP,
    ended_at        TIMESTAMP,
    end_reason      VARCHAR(32),
    `deleted`         TINYINT NOT NULL DEFAULT 0,
    INDEX idx_adv_business (business_id),
    INDEX idx_adv_advisor (advisor_id),
    INDEX idx_adv_status (`status`)
);
