-- =============================================================================
-- V26.10.02 — UserAccount 生命周期状态字段重构
--
-- 目的：
--   1. 新增 life_cycle 列（VARCHAR，存储枚举字面量如 ENABLED/DISABLED/…）
--   2. 迁移旧 status 列（INTEGER 0/1，历史遗留）数据到 life_cycle
--   3. 旧 status 列保留在表中（平台基类 MpBaseEntity.status 映射需要），标记 deprecated
--
-- 合规：YDIZ-DDD-008 所有业务 Entity 不覆盖平台基础字段 status
--       UserAccount.java 中不再声明 status 字段，业务通过 lifecycleStatus 读写 life_cycle
-- =============================================================================

-- Step 1: 新增 life_cycle 列（业务使用此列读写生命周期状态）
ALTER TABLE ydsz_idm_account_user
  ADD COLUMN IF NOT EXISTS life_cycle VARCHAR(32);

-- Step 2: 迁移旧 status (VARCHAR '0'/'1' 或 INTEGER) → life_cycle (VARCHAR 枚举字面量)
UPDATE ydsz_idm_account_user
  SET life_cycle = CASE
    WHEN status IN ('1', 'ENABLED') THEN 'ENABLED'
    ELSE 'DISABLED'
  END
  WHERE life_cycle IS NULL;

-- Step 3: 设置 life_cycle 非空约束与默认值（数据迁移后生效）
ALTER TABLE ydsz_idm_account_user
  ALTER COLUMN life_cycle SET DEFAULT 'ENABLED';

ALTER TABLE ydsz_idm_account_user
  ALTER COLUMN life_cycle SET NOT NULL;

-- Step 4: 旧 status 列保留在表中（平台基类 MpBaseEntity.status 需映射列存在）
--         业务代码不再读写此列，标记 deprecated 待后续版本删除
COMMENT ON COLUMN ydsz_idm_account_user.status IS
  '【DEPRECATED 26.10.02】已迁移至 life_cycle 列。平台基类 MpBaseEntity 映射占位保留，禁止业务读写。待确认所有实例迁移完成后可 ALTER TABLE DROP COLUMN status。';

-- Step 5: 新列注释
COMMENT ON COLUMN ydsz_idm_account_user.life_cycle IS
  '用户生命周期状态（VARCHAR 枚举：PENDING/ENABLED/SUSPENDED/DISABLED/RESIGNED）';
