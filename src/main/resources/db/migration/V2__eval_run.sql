-- 评测运行元数据（开发计划 §5.2）
CREATE TABLE eval_run (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    suite           VARCHAR(32)   NOT NULL COMMENT 'retrieval / qa / gap / scoring / summary',
    dataset_version VARCHAR(64)   NOT NULL,
    status          VARCHAR(16)   NOT NULL COMMENT 'COMPLETED / FAILED',
    config_json     JSON          NOT NULL COMMENT 'git commit、模型、提示词版本、检索参数等',
    metrics_json    JSON          NULL,
    output_dir      VARCHAR(255)  NOT NULL,
    created_at      DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_eval_run_suite (suite, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
