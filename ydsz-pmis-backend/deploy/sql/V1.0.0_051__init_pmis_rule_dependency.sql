-- 规则依赖关系表（P1-8：规则依赖图 + 依赖/被依赖面板）
-- 一条规则（rule_code）依赖另一条规则（depends_on_rule_code）。
-- 规则 A dependsOn B 表示：A 评估时若需要 B 的结果（如合并决策），需要先评估 B。
-- 规则禁用时级联：若 B 禁用，A 应有配置项（cascade）决定是否级联停用。

CREATE TABLE IF NOT EXISTS pmis_rule_dependency (
    id                       BIGSERIAL PRIMARY KEY,
    rule_code                VARCHAR(128) NOT NULL,
    depends_on_rule_code     VARCHAR(128) NOT NULL,
    dependency_type          VARCHAR(32)  NOT NULL DEFAULT 'EXECUTE',
    cascade_on_disable       BOOLEAN      NOT NULL DEFAULT FALSE,
    description              VARCHAR(256),
    tenant_id                BIGINT       NOT NULL DEFAULT 1,
    created_by               VARCHAR(64),
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_rule_dep UNIQUE (rule_code, depends_on_rule_code, dependency_type)
);

-- 正向查询：某条规则依赖了哪些规则
CREATE INDEX IF NOT EXISTS idx_rule_dep_rule_code
    ON pmis_rule_dependency (rule_code);

-- 反向查询：被哪些规则依赖
CREATE INDEX IF NOT EXISTS idx_rule_dep_depends_on
    ON pmis_rule_dependency (depends_on_rule_code);

COMMENT ON TABLE  pmis_rule_dependency IS '规则依赖关系（P1-8）';
COMMENT ON COLUMN pmis_rule_dependency.dependency_type IS '依赖类型：EXECUTE（先执行）/ READ_RESULT（读结果）/ SOFT（仅配置参考）';
COMMENT ON COLUMN pmis_rule_dependency.cascade_on_disable IS '被依赖规则禁用时是否级联禁用本规则';
