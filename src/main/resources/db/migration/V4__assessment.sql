-- 评估项目、测评对象、差距项、工作流步骤、评分（开发计划 §5.2、§7.4）

CREATE TABLE assess_project (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    system_name VARCHAR(128) NOT NULL,
    level       TINYINT      NOT NULL COMMENT '等级 1-4',
    status      VARCHAR(16)  NOT NULL,
    last_error  VARCHAR(1000) NULL COMMENT '最近一次分析失败的原因',
    version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assess_object (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    project_id    BIGINT       NOT NULL,
    layer         VARCHAR(32)  NOT NULL COMMENT '安全层面',
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(2000) NULL,
    measures_json JSON         NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_assess_object_project (project_id),
    CONSTRAINT fk_assess_object_project FOREIGN KEY (project_id) REFERENCES assess_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assess_finding (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    project_id        BIGINT       NOT NULL,
    object_id         BIGINT       NOT NULL,
    clause_ref        VARCHAR(128) NOT NULL,
    judgment          VARCHAR(8)   NOT NULL COMMENT '符合 / 部分符合 / 不符合 / 不适用',
    evidence          TEXT         NULL,
    rule_hits_json    JSON         NULL COMMENT '规则检查命中记录',
    rationale         TEXT         NULL,
    remediation       TEXT         NULL,
    missing_info_json JSON         NULL,
    source            VARCHAR(16)  NOT NULL COMMENT 'MODEL / RULE / REVIEWER',
    llm_call_id       BIGINT       NULL,
    reviewed          BOOLEAN      NOT NULL DEFAULT FALSE,
    reviewer_note     VARCHAR(1000) NULL,
    reviewed_at       DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_at        DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assess_finding_object_clause (object_id, clause_ref),
    KEY idx_assess_finding_project (project_id),
    CONSTRAINT fk_assess_finding_object FOREIGN KEY (object_id) REFERENCES assess_object (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assess_step (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL,
    object_id   BIGINT       NOT NULL,
    step        VARCHAR(16)  NOT NULL COMMENT 'MATCH / RULE_CHECK / JUDGE',
    step_key    VARCHAR(160) NOT NULL COMMENT '同一对象同一步骤下的子项，如 JUDGE 的 clause_ref；无子项时为 -',
    status      VARCHAR(16)  NOT NULL COMMENT 'RUNNING / SUCCEEDED / FAILED',
    attempts    INT          NOT NULL DEFAULT 0,
    output_json JSON         NULL COMMENT '步骤结果，断点续跑时复用',
    error       VARCHAR(1000) NULL,
    started_at  DATETIME(3)  NULL,
    finished_at DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assess_step (project_id, object_id, step, step_key),
    CONSTRAINT fk_assess_step_project FOREIGN KEY (project_id) REFERENCES assess_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assess_score (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    project_id   BIGINT        NOT NULL,
    scope        VARCHAR(8)    NOT NULL COMMENT 'object / layer / total',
    scope_key    VARCHAR(160)  NOT NULL,
    score        DECIMAL(9, 4) NULL COMMENT '全部不适用时为空',
    detail_json  JSON          NOT NULL,
    rule_version VARCHAR(32)   NOT NULL,
    created_at   DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assess_score (project_id, scope, scope_key),
    CONSTRAINT fk_assess_score_project FOREIGN KEY (project_id) REFERENCES assess_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
