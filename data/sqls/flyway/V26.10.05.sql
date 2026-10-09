-- =============================================================================
-- V26.10.05 — 布尔字段类型全面统一（boolean → smallint）
--
-- 目的：
--   将全库剩余 ~40 个 boolean 类型字段统一为 SMALLINT，消除 BOOLEAN 与 SMALLINT
--   混用，达成数据库布尔类型一致性（YDIZ-DB-007 P0 红线规则）。
--
-- 设计：
--   0 = 否/关闭/未删除，1 = 是/开启/已删除
--   所有列加 NOT NULL DEFAULT 0（原本 NOT NULL 的保持，原本 nullable 的也加 NOT NULL）
--   每列追加 CHECK (col IN (0, 1)) 约束保证数据完整性
--
-- 合规：YDIZ-DB-007（P0 布尔类型统一 SMALLINT）；YDIZ-OOP-006（数据库列必须 SMALLINT）
-- 影响：低峰期执行，ALTER COLUMN TYPE 仅改元数据，不重建表
-- 前置：V26.10.03 已修复 ydsz_agt_dag_workflow.is_published / ydsz_job_dag_context.is_deleted
-- =============================================================================

-- ============================================================================
-- 一、Common 模块（7 个字段，3 张表）
-- ============================================================================

-- 1. ydsz_comm_outbox: compressed（boolean → smallint）
-- 注意：TYPE 变更和 SET DEFAULT 必须分两步，否则 PG 会用旧类型(boolean)校验默认值
ALTER TABLE ydsz_comm_outbox
  ALTER COLUMN compressed TYPE SMALLINT
    USING CASE WHEN compressed = TRUE THEN 1 ELSE 0 END;

ALTER TABLE ydsz_comm_outbox
  ALTER COLUMN compressed SET DEFAULT 0,
  ALTER COLUMN compressed SET NOT NULL;

ALTER TABLE ydsz_comm_outbox
  ADD CONSTRAINT chk_comm_outbox_compressed CHECK (compressed IN (0, 1));

COMMENT ON COLUMN ydsz_comm_outbox.compressed IS '是否压缩（smallint: 0=未压缩, 1=已压缩）';

-- 2. ydzs_comm_outbox_archive: compressed（boolean → smallint）
-- 注意：TYPE 变更和 SET DEFAULT 必须分两步，否则 PG 会用旧类型(boolean)校验默认值
ALTER TABLE ydsz_comm_outbox_archive
  ALTER COLUMN compressed TYPE SMALLINT
    USING CASE WHEN compressed = TRUE THEN 1 ELSE 0 END;

ALTER TABLE ydsz_comm_outbox_archive
  ALTER COLUMN compressed SET DEFAULT 0,
  ALTER COLUMN compressed SET NOT NULL;

ALTER TABLE ydsz_comm_outbox_archive
  ADD CONSTRAINT chk_comm_outbox_archive_compressed CHECK (compressed IN (0, 1));

-- 3. ydsz_comm_search_dead_letter: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 4. ydsz_comm_search_index_partitioned: active_flag（boolean → smallint）
-- 注意：TYPE 变更和 SET DEFAULT 必须分两步，否则 PG 会用旧类型(boolean)校验默认值
ALTER TABLE ydsz_comm_search_index_partitioned
  ALTER COLUMN active_flag TYPE SMALLINT
    USING CASE WHEN active_flag = TRUE THEN 1 ELSE 0 END;

ALTER TABLE ydsz_comm_search_index_partitioned
  ALTER COLUMN active_flag SET DEFAULT 1,
  ALTER COLUMN active_flag SET NOT NULL;

ALTER TABLE ydsz_comm_search_index_partitioned
  ADD CONSTRAINT chk_comm_search_ip_active_flag CHECK (active_flag IN (0, 1));

COMMENT ON COLUMN ydsz_comm_search_index_partitioned.active_flag IS '是否激活（smallint: 0=未激活, 1=激活）';

-- ============================================================================
-- 二、Agent 模块（2 个字段，1 张表，不含 V26.10.03 已修复）
-- ============================================================================

-- 5. ydzs_agt_prompt_template: is_ab_test_enabled（boolean → smallint）
-- 注意：TYPE 变更和 SET DEFAULT 必须分两步，否则 PG 会用旧类型(boolean)校验默认值
ALTER TABLE ydsz_agt_prompt_template
  ALTER COLUMN is_ab_test_enabled TYPE SMALLINT
    USING CASE WHEN is_ab_test_enabled = TRUE THEN 1 ELSE 0 END;

ALTER TABLE ydsz_agt_prompt_template
  ALTER COLUMN is_ab_test_enabled SET DEFAULT 0,
  ALTER COLUMN is_ab_test_enabled SET NOT NULL;

ALTER TABLE ydsz_agt_prompt_template
  ADD CONSTRAINT chk_agt_prompt_is_ab_test_enabled CHECK (is_ab_test_enabled IN (0, 1));

COMMENT ON COLUMN ydzs_agt_prompt_template.is_ab_test_enabled IS '是否启用 AB 测试（smallint: 0=禁用, 1=启用）';

-- ============================================================================
-- 三、Userinfo / Identity 模块（6 个字段，3 张表）
-- ============================================================================

-- 6. ydzs_idm_account_login_history: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 7. ydsz_idm_auth_apikey: is_enabled（boolean → smallint）
-- 注意：TYPE 变更和 SET DEFAULT 必须分两步，否则 PG 会用旧类型(boolean)校验默认值
ALTER TABLE ydsz_idm_auth_apikey
  ALTER COLUMN is_enabled TYPE SMALLINT
    USING CASE WHEN is_enabled = TRUE THEN 1 ELSE 0 END;

ALTER TABLE ydsz_idm_auth_apikey
  ALTER COLUMN is_enabled SET DEFAULT 1,
  ALTER COLUMN is_enabled SET NOT NULL;

ALTER TABLE ydsz_idm_auth_apikey
  ADD CONSTRAINT chk_idm_apikey_is_enabled CHECK (is_enabled IN (0, 1));

COMMENT ON COLUMN ydsz_idm_auth_apikey.is_enabled IS '是否启用（smallint: 0=禁用, 1=启用）';

-- 8. ydsz_idm_auth_policy: 4 个布尔字段（boolean → smallint）
-- 注意：TYPE 变更和 SET DEFAULT 必须分两步，否则 PG 会用旧类型(boolean)校验默认值
ALTER TABLE ydsz_idm_auth_policy
  ALTER COLUMN is_password_require_uppercase TYPE SMALLINT
    USING CASE WHEN is_password_require_uppercase = TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_password_require_digit TYPE SMALLINT
    USING CASE WHEN is_password_require_digit = TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_mfa_enabled TYPE SMALLINT
    USING CASE WHEN is_mfa_enabled = TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_captcha_enabled TYPE SMALLINT
    USING CASE WHEN is_captcha_enabled = TRUE THEN 1 ELSE 0 END;

ALTER TABLE ydsz_idm_auth_policy
  ALTER COLUMN is_password_require_uppercase SET DEFAULT 1,
  ALTER COLUMN is_password_require_uppercase SET NOT NULL,
  ALTER COLUMN is_password_require_digit SET DEFAULT 1,
  ALTER COLUMN is_password_require_digit SET NOT NULL,
  ALTER COLUMN is_mfa_enabled SET DEFAULT 0,
  ALTER COLUMN is_mfa_enabled SET NOT NULL,
  ALTER COLUMN is_captcha_enabled SET DEFAULT 1,
  ALTER COLUMN is_captcha_enabled SET NOT NULL;

ALTER TABLE ydsz_idm_auth_policy
  ADD CONSTRAINT chk_idm_policy_pw_uppercase CHECK (is_password_require_uppercase IN (0, 1)),
  ADD CONSTRAINT chk_idm_policy_pw_digit CHECK (is_password_require_digit IN (0, 1)),
  ADD CONSTRAINT chk_idm_policy_mfa_enabled CHECK (is_mfa_enabled IN (0, 1)),
  ADD CONSTRAINT chk_idm_policy_captcha_enabled CHECK (is_captcha_enabled IN (0, 1));

COMMENT ON COLUMN ydsz_idm_auth_policy.is_password_require_uppercase IS '密码要求大写字母（smallint: 0=不要求, 1=要求）';
COMMENT ON COLUMN ydsz_idm_auth_policy.is_password_require_digit IS '密码要求数字（smallint: 0=不要求, 1=要求）';
COMMENT ON COLUMN ydsz_idm_auth_policy.is_mfa_enabled IS '是否启用 MFA（smallint: 0=禁用, 1=启用）';
COMMENT ON COLUMN ydsz_idm_auth_policy.is_captcha_enabled IS '是否启用验证码（smallint: 0=禁用, 1=启用）';

-- ============================================================================
-- 四、Workflow / Flow 模块（2 个字段，2 张表）
-- ============================================================================

-- 9. ydzs_flow_archive_cursor: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 10. ydzs_flow_idempotent: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- ============================================================================
-- 五、Message 模块（4 个字段，4 张表）
-- ============================================================================

-- 11. ydzs_msg_outbox: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 12. ydzs_msg_tenant_config: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 13. ydzs_msg_trace: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- ============================================================================
-- 六、Job / Cronjob 模块（5 个字段，4 张表，不含 V26.10.03 已修复）
-- ============================================================================

-- 14. ydzs_job_daily_stats: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 15. ydzs_job_event_store: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 16. ydzs_job_log_content: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 17. ydzs_job_outbox: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- ============================================================================
-- 七、Rule / Literule 模块（2 个字段，2 张表）
-- ============================================================================

-- 18. ydzs_rule_execution_trace: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 19. ydzs_rule_version_history: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- ============================================================================
-- 八、Generator 模块（7 个字段，6 张表）
-- ============================================================================

-- 20. ydzs_gen_column_meta: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 21. ydzs_gen_datasource: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 22. ydzs_gen_history: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 23. ydzs_gen_history_file: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 24. ydzs_gen_table_meta: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 25. ydzs_gen_template: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- 26. ydzs_gen_template_group: is_deleted 已在 V26.10.01 基线中为 smallint，跳过转换

-- ============================================================================
-- 九、特殊字段修正（命名遗漏）
-- ============================================================================

-- 27. ydsz_comm_outbox.compressed → is_compressed（命名规范对齐）
-- 注意：仅在列名变更不影响业务代码时执行
ALTER TABLE ydsz_comm_outbox RENAME COLUMN compressed TO is_compressed;
ALTER TABLE ydsz_comm_outbox_archive RENAME COLUMN compressed TO is_compressed;

-- 重命名后需更新约束名
ALTER TABLE ydsz_comm_outbox RENAME CONSTRAINT chk_comm_outbox_compressed TO chk_comm_outbox_is_compressed;
ALTER TABLE ydsz_comm_outbox_archive RENAME CONSTRAINT chk_comm_outbox_archive_compressed TO chk_comm_outbox_archive_is_compressed;

COMMENT ON COLUMN ydsz_comm_outbox.is_compressed IS '是否压缩（smallint: 0=未压缩, 1=已压缩）';
COMMENT ON COLUMN ydsz_comm_outbox_archive.is_compressed IS '是否压缩（smallint: 0=未压缩, 1=已压缩）';

-- ============================================================================
-- 验证查询（执行后手动确认）
-- ============================================================================
/*
-- 确认所有 is_xxx / xxx_flag 字段均为 SMALLINT（不应再有 boolean）
SELECT table_name, column_name, data_type, is_nullable, column_default
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND (column_name LIKE 'is_%' OR column_name LIKE '%_flag' OR column_name LIKE '%_enabled')
    AND data_type = 'boolean'
  ORDER BY table_name;

-- 应返回 0 行。

-- 确认所有 CHECK 约束创建成功
SELECT conname, conrelid::regclass AS table_name
  FROM pg_constraint
  WHERE conname LIKE 'chk_%is_%' OR conname LIKE 'chk_%_flag%'
  ORDER BY conrelid::regclass;

-- 确认 CHECK 约束限制 (0,1)
SELECT conname, pg_get_constraintdef(oid) AS def
  FROM pg_constraint
  WHERE conname LIKE 'chk_%is_deleted%'
  LIMIT 5;
*/
