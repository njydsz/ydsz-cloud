-- ============================================================
-- V1.0.0_051__init_rule_variable_def.sql
-- P2-4 变量空间元数据：规则表达式中可引用的变量定义表
-- @author ydsz-pmis-team
-- @since 1.4.0
-- ============================================================

CREATE TABLE IF NOT EXISTS pmis_rule_variable_def (
    id              BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    var_name        VARCHAR(128) NOT NULL,
    var_type        VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    sample_value    TEXT,
    category        VARCHAR(64)  NOT NULL DEFAULT 'GENERAL',
    required        BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP
);

-- 唯一约束：同租户下变量名唯一
ALTER TABLE pmis_rule_variable_def
    ADD CONSTRAINT uk_rule_variable_name UNIQUE (tenant_id, var_name);

-- 索引：按类别查询
CREATE INDEX IF NOT EXISTS idx_rule_variable_category ON pmis_rule_variable_def (category);

-- 索引：按租户查询
CREATE INDEX IF NOT EXISTS idx_rule_variable_tenant ON pmis_rule_variable_def (tenant_id);

COMMENT ON TABLE  pmis_rule_variable_def IS '规则变量定义表：规则表达式中可引用的变量元数据';
COMMENT ON COLUMN pmis_rule_variable_def.var_name     IS '变量名（如 cpi / budgetAmount / evmRedCount）';
COMMENT ON COLUMN pmis_rule_variable_def.var_type     IS '变量类型（java.lang.Number / java.lang.String 等）';
COMMENT ON COLUMN pmis_rule_variable_def.description   IS '变量描述（中文，供前端编辑器提示）';
COMMENT ON COLUMN pmis_rule_variable_def.sample_value  IS '示例值（用于前端编辑器预览和 dryRun 默认 facts）';
COMMENT ON COLUMN pmis_rule_variable_def.category      IS '变量来源类别（EVM / PROJECT / FINANCE / BENCH 等）';
COMMENT ON COLUMN pmis_rule_variable_def.required       IS '是否必填（前端编辑器可标记必填变量）';
COMMENT ON COLUMN pmis_rule_variable_def.enabled        IS '是否启用';
COMMENT ON COLUMN pmis_rule_variable_def.tenant_id      IS '租户 ID（单租户部署默认 1）';

-- 内置变量种子数据（EVM 类）
INSERT INTO pmis_rule_variable_def (var_name, var_type, description, sample_value, category, required) VALUES
    ('cpi',                'java.lang.Number',  '成本绩效指数（CPI），>1 表示成本节约',     '0.85', 'EVM',     TRUE),
    ('spi',                'java.lang.Number',  '进度绩效指数（SPI），>1 表示进度超前',     '0.92', 'EVM',     TRUE),
    ('evmRedCount',        'java.lang.Integer', '红色 EVM 预警数量',                       '3',    'EVM',     FALSE),
    ('evmYellowCount',     'java.lang.Integer', '黄色 EVM 预警数量',                       '5',    'EVM',     FALSE),
    ('benchIdleCost',      'java.lang.Number',  '闲置设备成本',                            '1000', 'BENCH',   FALSE),
    ('benchIdleCount',     'java.lang.Integer', '闲置设备数量',                            '2',    'BENCH',   FALSE),
    ('grossMargin',        'java.lang.Number',  '毛利率（0~1）',                            '0.05', 'FINANCE', TRUE),
    ('confirmedRevenue',   'java.lang.Number',  '已确认收入',                               '5000', 'FINANCE', TRUE),
    ('budgetAmount',        'java.lang.Number',  '项目预算金额',                              '500000','BUDGET', TRUE),
    ('budgetUsageRatio',   'java.lang.Number',  '预算使用率（0~1）',                          '0.85', 'BUDGET', TRUE),
    ('projectName',        'java.lang.String',  '项目名称',                                  '示例项目','PROJECT',TRUE),
    ('projectStatus',      'java.lang.String',  '项目状态（IN_PROGRESS/Delayed/Completed）','IN_PROGRESS','PROJECT',TRUE),
    ('tenantId',           'java.lang.String',  '租户 ID',                                   'T001','PROJECT',FALSE)
ON CONFLICT (tenant_id, var_name) DO NOTHING;
