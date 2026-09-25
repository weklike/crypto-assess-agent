-- 异步入库任务（开发计划 §5.2，二期由 Kafka 消费者推进）
CREATE TABLE ingest_job (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    file_name         VARCHAR(255) NOT NULL,
    normalized_sha256 CHAR(64)     NOT NULL,
    parser_version    VARCHAR(16)  NOT NULL,
    document_id       BIGINT       NULL,
    status            VARCHAR(16)  NOT NULL COMMENT 'PENDING / RUNNING / SUCCEEDED / FAILED',
    attempts          INT          NOT NULL DEFAULT 0,
    last_error        VARCHAR(1000) NULL,
    created_at        DATETIME(3)  NOT NULL,
    updated_at        DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingest_job_content (normalized_sha256, parser_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
