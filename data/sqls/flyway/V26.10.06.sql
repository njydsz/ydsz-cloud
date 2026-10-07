-- =============================================================================
-- Flyway Migration: V26.10.06
-- Description: 业务码唯一索引软删除兼容 (partial unique index WHERE is_deleted = 0)
-- Author:      ydsz-team
-- Created:     2026-10-06
-- =============================================================================
--
-- 背景: 现有业务码 unique 约束 (tenant_id, code) 不含 is_deleted，
--       软删除后无法重新创建同码记录。为此在核心业务表新增部分唯一索引
--       (WHERE is_deleted = 0)，允许已删除记录与新记录共存。
--
-- 仅对 "删除-重建" 常见场景的 8 张核心表生效，其余表沿用现有约束。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ydsz_idm_role: 角色编码 (用户常删除测试角色后重建)
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_idm_role_code_active
    ON ydsz_idm_role (role_code, tenant_id)
    WHERE is_deleted = 0;

-- -----------------------------------------------------------------------------
-- 2. ydsz_sys_dict_type: 字典类型编码
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_dict_type_code_active
    ON ydsz_sys_dict_type (type_code)
    WHERE is_deleted = 0;

-- -----------------------------------------------------------------------------
-- 3. ydsz_sys_dict_item: 字典条目编码 (type_code + item_code)
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_dict_item_code_active
    ON ydsz_sys_dict_item (type_code, item_code)
    WHERE is_deleted = 0;

-- -----------------------------------------------------------------------------
-- 4. ydsz_flow_definition: 流程定义编码 + 版本
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_flow_definition_code_ver_active
    ON ydsz_flow_definition (flow_code, flow_version, tenant_id)
    WHERE is_deleted = 0;

-- -----------------------------------------------------------------------------
-- 5. ydsz_flow_category: 流程分类编码
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_flow_category_code_active
    ON ydsz_flow_category (category_code, tenant_id)
    WHERE is_deleted = 0;

-- -----------------------------------------------------------------------------
-- 6. ydsz_msg_template: 消息模板编码
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_msg_template_code_active
    ON ydsz_msg_template (template_code, tenant_id)
    WHERE is_deleted = 0;

-- -----------------------------------------------------------------------------
-- 7. ydsz_rule_def: 规则定义编码
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_rule_def_code_active
    ON ydsz_rule_def (rule_code, tenant_id)
    WHERE is_deleted = 0;

-- -----------------------------------------------------------------------------
-- 8. ydsz_sys_app_info: 应用 app_key (租户内唯一)
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_app_key_active
    ON ydsz_sys_app_info (tenant_id, app_key)
    WHERE is_deleted = 0;
