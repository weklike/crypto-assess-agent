-- 知识库、模型调用记录、审计日志（开发计划 §5.2）

CREATE TABLE kb_document (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    doc_code          VARCHAR(64)  NOT NULL COMMENT '标准号，如 GB/T 39786-2021',
    title             VARCHAR(255) NOT NULL,
    source_sha256     CHAR(64)     NULL COMMENT '原始 PDF 的 SHA256，取自 front matter',
    normalized_sha256 CHAR(64)     NOT NULL COMMENT '规范化文本的 SHA256，导入幂等键',
    parser_version    VARCHAR(16)  NOT NULL,
    status            VARCHAR(16)  NOT NULL COMMENT 'ACTIVE / SUPERSEDED',
    clause_count      INT          NOT NULL DEFAULT 0,
    imported_at       DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kb_document_code_sha (doc_code, normalized_sha256)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE kb_clause (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    document_id BIGINT        NOT NULL,
    clause_ref  VARCHAR(128)  NOT NULL COMMENT '标准号#条款号',
    clause_no   VARCHAR(32)   NOT NULL,
    parent_no   VARCHAR(32)   NULL,
    path        VARCHAR(1024) NOT NULL COMMENT '章节路径，如 6 技术要求 > 6.2 网络和通信安全',
    title       VARCHAR(512)  NOT NULL,
    body        MEDIUMTEXT    NOT NULL,
    layer       VARCHAR(32)   NULL COMMENT '安全层面',
    levels      VARCHAR(16)   NULL COMMENT '适用等级，逗号分隔',
    clause_type VARCHAR(16)   NULL COMMENT '要求 / 说明 等',
    ordinal     INT           NOT NULL COMMENT '在文档中的顺序',
    body_sha256 CHAR(64)      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kb_clause_ref (clause_ref),
    KEY idx_kb_clause_document (document_id, ordinal),
    CONSTRAINT fk_kb_clause_document FOREIGN KEY (document_id) REFERENCES kb_document (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE llm_call (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    purpose        VARCHAR(32)   NOT NULL COMMENT 'qa / judge / exam 等',
    model          VARCHAR(64)   NOT NULL,
    prompt_version VARCHAR(64)   NULL,
    latency_ms     INT           NULL,
    input_tokens   INT           NULL,
    output_tokens  INT           NULL,
    cost_cny       DECIMAL(12, 6) NULL,
    status         VARCHAR(16)   NOT NULL COMMENT 'OK / TIMEOUT / ERROR / INVALID_OUTPUT',
    error_code     VARCHAR(64)   NULL,
    created_at     DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_llm_call_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor       VARCHAR(64)  NOT NULL,
    channel     VARCHAR(16)  NOT NULL COMMENT 'mcp / admin',
    action      VARCHAR(64)  NOT NULL,
    target      VARCHAR(255) NULL,
    args_sha256 CHAR(64)     NULL COMMENT '只存参数哈希，不存原文',
    result      VARCHAR(16)  NOT NULL,
    created_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_log_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
