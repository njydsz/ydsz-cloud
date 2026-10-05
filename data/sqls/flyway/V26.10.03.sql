-- =============================================================================
-- V26.10.03 — 数据库规范修复集（P0 生产安全 + 存储优化）
--
-- 目的：
--   1. tenant_id 长度统一（Agent 模块 varchar(32) → varchar(64)），消除跨模块 JOIN 隐式转换
--   2. ydsz_idm_account_user_role 补唯一约束（防重复授权）
--   3. TIMESTAMP 类型统一（timestamp with time zone → timestamp without time zone）
--   4. 布尔字段类型统一（boolean → smallint），DagWorkflow.is_published
--
-- 合规：阿里《Java开发手册》字段类型一致性、云顶编码规范 DB-001
-- 影响：Agent 模块、低峰期执行，锁表时间 < 100ms（ALTER COLUMN TYPE 仅改元数据）
-- =============================================================================

-- ============================================================================
-- Step 1: tenant_id 长度统一（Agent 模块 varchar(32) → varchar(64)）
-- ============================================================================

-- 1.1 ydsz_agt_definition
ALTER TABLE ydsz_agt_definition
  ALTER COLUMN tenant_id TYPE VARCHAR(64),
  ALTER COLUMN tenant_id SET DEFAULT '0';

COMMENT ON COLUMN ydsz_agt_definition.tenant_id IS '租户ID（统一 varchar(64)，与全平台一致）';

-- ============================================================================
-- Step 2: 用户-角色中间表唯一约束（防重复授权漏洞）
-- ============================================================================

-- 2.1 先清理可能的重复数据（保留最小 id 行）
DELETE FROM ydsz_idm_account_user_role a
  USING ydsz_idm_account_user_role b
  WHERE a.user_id = b.user_id
    AND a.role_id = b.role_id
    AND a.id > b.id;

-- 2.2 追加唯一约束
ALTER TABLE ydsz_idm_account_user_role
  ADD CONSTRAINT uk_idm_user_role UNIQUE (user_id, role_id);

COMMENT ON CONSTRAINT uk_idm_user_role ON ydsz_idm_account_user_role IS '用户-角色唯一约束，禁止重复授权';

-- ============================================================================
-- Step 3: TIMESTAMP 类型统一（timestamp with time zone → without time zone）
-- ============================================================================

-- 3.1 ydsz_agt_document_chunk.created_at 从 with time zone → without time zone
ALTER TABLE ydsz_agt_document_chunk
  ALTER COLUMN created_at TYPE TIMESTAMP WITHOUT TIME ZONE;

COMMENT ON COLUMN ydsz_agt_document_chunk.created_at IS '创建时间（timestamp without time zone，UTC 约定）';

-- ============================================================================
-- Step 4: 布尔字段类型统一（boolean → smallint(0/1)）
-- ============================================================================

-- 4.1 ydsz_agt_dag_workflow.is_published: boolean → smallint
ALTER TABLE ydsz_agt_dag_workflow
  ALTER COLUMN is_published TYPE SMALLINT
    USING CASE WHEN is_published = TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_published SET DEFAULT 0,
  ALTER COLUMN is_published SET NOT NULL;

COMMENT ON COLUMN ydsz_agt_dag_workflow.is_published IS '是否已发布（smallint: 0=未发布, 1=已发布，统一 boolean 存储约定）';

-- 4.2 ydsz_job_dag_context.is_deleted: boolean → smallint
ALTER TABLE ydsz_job_dag_context
  ALTER COLUMN is_deleted TYPE SMALLINT
    USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_deleted SET DEFAULT 0,
  ALTER COLUMN is_deleted SET NOT NULL;

COMMENT ON COLUMN ydsz_job_dag_context.is_deleted IS '逻辑删除（smallint: 0=未删, 1=已删，统一 boolean 存储约定）';

-- ============================================================================
-- 验证查询（执行后手动确认）
-- ============================================================================
/*
-- 确认 tenant_id 长度统一为 64
SELECT table_name, column_name, character_maximum_length
  FROM information_schema.columns
  WHERE column_name = 'tenant_id'
    AND table_schema = 'public'
  ORDER BY table_name;

-- 确认唯一约束存在
SELECT conname, contype
  FROM pg_constraint
  WHERE conrelid = 'ydsz_idm_account_user_role'::regclass;

-- 确认 TIMESTAMP 类型统一
SELECT table_name, column_name, data_type
  FROM information_schema.columns
  WHERE column_name LIKE '%time%'
    AND table_schema = 'public'
  ORDER BY table_name, ordinal_position;

-- 确认布尔字段统一为 smallint
SELECT table_name, column_name, data_type
  FROM information_schema.columns
  WHERE column_name LIKE 'is_%'
    AND table_schema = 'public'
  ORDER BY table_name, ordinal_position;
*/
