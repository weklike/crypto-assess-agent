-- 问答留痕（开发计划 §5.2），供评测和复盘
CREATE TABLE qa_record (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    question               TEXT         NOT NULL,
    mode                   VARCHAR(16)  NOT NULL,
    answer                 MEDIUMTEXT   NULL,
    citations_json         JSON         NULL COMMENT '校验通过的引用与被剔除的无效引用',
    invalid_citation_count INT          NOT NULL DEFAULT 0,
    refused                BOOLEAN      NOT NULL DEFAULT FALSE,
    top_score              DOUBLE       NULL COMMENT '拒答判断所用的最高分',
    prompt_version         VARCHAR(64)  NULL,
    llm_call_id            BIGINT       NULL,
    first_token_ms         INT          NULL,
    latency_ms             INT          NULL,
    created_at             DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_qa_record_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
