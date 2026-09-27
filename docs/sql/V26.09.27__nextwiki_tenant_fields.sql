-- ============================================================================
-- V26.09.27 — nextwiki 模块补全多租户字段（tenant_id / is_deleted）
-- ============================================================================
-- 本次变更：
--   为 nextwiki 模块 5 张表缺失多租户字段的表追加 tenant_id 与 is_deleted 列
--   （脚本幂等：IF NOT EXISTS，重复执行安全）
--
-- 涉及表（共 5 张，其中 2 张缺失字段）：
--   1. ydzsz_nwiki_space
--   2. ydzsz_nwiki_space_member
--   3. ydzsz_nwiki_space_template
--   4. ydzsz_nwiki_user_recent
--   5. ydzsz_nwiki_user_favorite
--
-- 执行环境：PostgreSQL 11+
-- 执行时间：< 1 秒（纯元数据变更，不锁表）
-- 回滚策略：见文件末尾回滚 SQL 块
-- ============================================================================

-- ============================================================================
-- 1. ydzsz_nwiki_space
-- ============================================================================
ALTER TABLE ydzsz_nwiki_space
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT '0';
ALTER TABLE ydzsz_nwiki_space
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN ydzsz_nwiki_space.tenant_id IS '租户 ID（多租户隔离）';
COMMENT ON COLUMN ydzsz_nwiki_space.is_deleted IS '逻辑删除标识（0=未删除，1=已删除）';

-- ============================================================================
-- 2. ydzsz_nwiki_space_member
-- ============================================================================
ALTER TABLE ydzsz_nwiki_space_member
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT '0';
ALTER TABLE ydzsz_nwiki_space_member
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN ydzsz_nwiki_space_member.tenant_id IS '租户 ID（多租户隔离）';
COMMENT ON COLUMN ydzsz_nwiki_space_member.is_deleted IS '逻辑删除标识（0=未删除，1=已删除）';

-- ============================================================================
-- 3. ydzsz_nwiki_space_template
-- ============================================================================
ALTER TABLE ydzsz_nwiki_space_template
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) DEFAULT NULL;
ALTER TABLE ydzsz_nwiki_space_template
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN ydzsz_nwiki_space_template.tenant_id IS '租户 ID（系统模板为 NULL）';
COMMENT ON COLUMN ydzsz_nwiki_space_template.is_deleted IS '逻辑删除标识（0=未删除，1=已删除）';

-- ============================================================================
-- 4. ydzsz_nwiki_user_recent
-- ============================================================================
ALTER TABLE ydzsz_nwiki_user_recent
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT '0';
ALTER TABLE ydzsz_nwiki_user_recent
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN ydzsz_nwiki_user_recent.tenant_id IS '租户 ID（多租户隔离）';
COMMENT ON COLUMN ydzsz_nwiki_user_recent.is_deleted IS '逻辑删除标识（0=未删除，1=已删除）';

-- ============================================================================
-- 5. ydzsz_nwiki_user_favorite
-- ============================================================================
ALTER TABLE ydzsz_nwiki_user_favorite
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) NOT NULL DEFAULT '0';
ALTER TABLE ydzsz_nwiki_user_favorite
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN ydzsz_nwiki_user_favorite.tenant_id IS '租户 ID（多租户隔离）';
COMMENT ON COLUMN ydzsz_nwiki_user_favorite.is_deleted IS '逻辑删除标识（0=未删除，1=已删除）';

-- ============================================================================
-- 回滚 SQL（需要时逐条执行）
-- ============================================================================
-- ALTER TABLE ydzsz_nwiki_space              DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ydzsz_nwiki_space              DROP COLUMN IF EXISTS is_deleted;
-- ALTER TABLE ydzsz_nwiki_space_member       DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ydzsz_nwiki_space_member       DROP COLUMN IF EXISTS is_deleted;
-- ALTER TABLE ydzsz_nwiki_space_template     DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ydzsz_nwiki_space_template     DROP COLUMN IF EXISTS is_deleted;
-- ALTER TABLE ydzsz_nwiki_user_recent        DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ydzsz_nwiki_user_recent        DROP COLUMN IF EXISTS is_deleted;
-- ALTER TABLE ydzsz_nwiki_user_favorite      DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ydzsz_nwiki_user_favorite      DROP COLUMN IF EXISTS is_deleted;
-- ============================================================================

