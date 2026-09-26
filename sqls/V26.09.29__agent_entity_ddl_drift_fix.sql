-- ============================================================================
-- V26.09.29: Agent 模块 Entity↔DDL 漂移修复（全维度分析 P0 阻断级）
--
-- 问题：
--   1. AgentApproval 继承 MpBaseAuditEntity 导致缺少 is_deleted/revision 列映射
--   2. InsightReport 缺少 tenant_id/is_deleted/revision，且主键用 BIGSERIAL 而非雪花
--   3. UserProfile 缺少 tenant_id/is_deleted/revision
--   4. AgentTrace 缺少 tenant_id/created_by/updated_by/is_deleted/revision 等字段
--
-- 修复策略：
--   - 同步 DDL 与 Entity 基类 MpBaseEntity 的全字段体系
--   - 所有变更均为 ADD COLUMN（IF NOT EXISTS），向前兼容已存在数据
--   - 补充缺失的复合索引，对齐其他模块标准
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 修复 ydsz_agt_approval：补充 is_deleted + revision（AgentApproval 已改为继承 MpBaseEntity）
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_approval
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_approval_is_deleted ON ydsz_agt_approval (is_deleted);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_approval_tenant_is_deleted ON ydsz_agt_approval (tenant_id, is_deleted);

-- ----------------------------------------------------------------------------
-- 修复 ydsz_agt_insight_report：补充 tenant_id + is_deleted + revision
--   （InsightReport 已改为继承 MpBaseEntity<Long>，BIGSERIAL 主键与 IdType.AUTO 对齐）
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_insight_report
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户 ID（多租户隔离）',
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_insight_report_tenant_id ON ydsz_agt_insight_report (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_insight_report_tenant_is_deleted ON ydsz_agt_insight_report (tenant_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_insight_report_tenant_status ON ydsz_agt_insight_report (tenant_id, status);

-- ----------------------------------------------------------------------------
-- 修复 ydsz_agt_user_profile：补充 tenant_id + is_deleted + revision
--   user_id 为主键（VARCHAR(32)），仍补充标准多租户字段
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_user_profile
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户 ID（多租户隔离）',
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_user_profile_tenant_id ON ydsz_agt_user_profile (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_user_profile_tenant_is_deleted ON ydsz_agt_user_profile (tenant_id, is_deleted);

-- ----------------------------------------------------------------------------
-- 修复 ydsz_agt_trace：补充 tenant_id + 审计字段 + is_deleted + revision
--   （tenant_id 在原 V26.09-26 脚本中遗漏，现合并到本子集中统一补充）
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_trace
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户 ID（多租户隔离）',
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人 ID',
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64) DEFAULT NULL COMMENT '最后更新人 ID',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间';

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_trace_is_deleted ON ydsz_agt_trace (is_deleted);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_trace_tenant_is_deleted ON ydsz_agt_trace (tenant_id, is_deleted);

-- 自动更新 updated_at 触发器
CREATE OR REPLACE FUNCTION fn_ydsz_agt_trace_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ydsz_agt_trace_updated_at ON ydsz_agt_trace;
CREATE TRIGGER trg_ydsz_agt_trace_updated_at
    BEFORE UPDATE ON ydsz_agt_trace
    FOR EACH ROW
    EXECUTE FUNCTION fn_ydsz_agt_trace_set_updated_at();

-- ----------------------------------------------------------------------------
-- 修复 ydsz_agt_trace_step：补充 tenant_id + 审计字段 + is_deleted + revision
--   （tenant_id 在原 V26.09-26 脚本中遗漏，现合并到本子集中统一补充）
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_trace_step
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户 ID（多租户隔离）',
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人 ID',
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64) DEFAULT NULL COMMENT '最后更新人 ID',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间';

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_trace_step_is_deleted ON ydsz_agt_trace_step (is_deleted);
CREATE INDEX IF NOT EXISTS idx_ydsz_agt_trace_step_tenant_is_deleted ON ydsz_agt_trace_step (tenant_id, is_deleted);

-- 自动更新 updated_at 触发器
CREATE OR REPLACE FUNCTION fn_ydsz_agt_trace_step_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ydsz_agt_trace_step_updated_at ON ydsz_agt_trace_step;
CREATE TRIGGER trg_ydsz_agt_trace_step_updated_at
    BEFORE UPDATE ON ydsz_agt_trace_step
    FOR EACH ROW
    EXECUTE FUNCTION fn_ydsz_agt_trace_step_set_updated_at();
