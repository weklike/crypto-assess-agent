-- 按《商用密码应用安全性评估量化评估规则（2023 版）》记录技术测评对象的 D/A/K 判定与修正参数
ALTER TABLE assess_finding
    ADD COLUMN dim_d        BOOLEAN       NULL COMMENT '密码使用有效性（技术层面）',
    ADD COLUMN dim_a        BOOLEAN       NULL COMMENT '密码算法/技术合规性',
    ADD COLUMN dim_k        BOOLEAN       NULL COMMENT '密钥管理安全',
    ADD COLUMN ra           DECIMAL(4, 2) NULL COMMENT 'A 不满足时的修正参数',
    ADD COLUMN rk           DECIMAL(4, 2) NULL COMMENT 'K 不满足时的修正参数',
    ADD COLUMN module_level TINYINT       NULL COMMENT '所用密码模块的安全等级';

ALTER TABLE assess_score MODIFY COLUMN scope VARCHAR(8) NOT NULL COMMENT 'object / unit / layer / group / total';
