-- =============================================================================
-- 迁移脚本：V26.09.08 — YDIZ-OOP-006 布尔字段统一 is 前缀
-- 规范：数据库布尔列名 is_xxx ↔ Java 字段 isXxx（强制带 is 前缀）
-- 日期：2026-09-08
-- 
-- 本次 Java 代码变更覆盖了 8 个模块 59 处字段重命名。
-- 大部分模块通过 @TableField 注解或 Mapper XML column 映射保留了原 DB 列名，
-- 仅 generator 模板组模块需要执行实际 DDL 变更。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 模块：ydsz-generator
-- 表：ydsz_gen_template_group
-- 原因：该表使用 MyBatis-Plus 自动驼峰→蛇形映射，Java 字段 isSystem 自动映射
--       到 is_system 列，但现有列名为 system，需要 DDL 同步
-- ----------------------------------------------------------------=============

-- PostgreSQL
ALTER TABLE ydsz_gen_template_group RENAME COLUMN system TO is_system;
COMMENT ON COLUMN ydsz_gen_template_group.is_system IS '是否系统内置分组';

-- MySQL（如果使用）
-- ALTER TABLE ydsz_gen_template_group CHANGE COLUMN system is_system TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否系统内置分组';

-- Oracle（如果使用）
-- RENAME COLUMN ydsz_gen_template_group.system TO is_system;
-- COMMENT ON COLUMN ydsz_gen_template_group.is_system IS '是否系统内置分组';

-- =============================================================================
-- 以下为已通过代码层 @TableField / XML column 映射保持列名不变的字段清单
-- （无需 DDL，但列名与 is 前缀对齐的规范列标记录如下）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 模块：ydsz-literule（全部通过 @TableField("enabled") / @TableField("required") 保持映射）
-- -----------------------------------------------------------------------------
-- ydsz_rule_def.enabled        → Java: RuleDefinition.isEnabled       (DB列名不变)
-- ydsz_decision_table.enabled  → Java: DecisionTable.isEnabled       (DB列名不变)
-- ydsz_rule_decision_tree.enabled → Java: RuleDecisionTree.isEnabled (DB列名不变)
-- ydsz_rule_pack.enabled       → Java: RulePack.isEnabled            (DB列名不变)
-- ydsz_rule_scorecard.enabled  → Java: RuleScorecard.isEnabled       (DB列名不变)
-- ydsz_rule_script.enabled     → Java: RuleScript.isEnabled          (DB列名不变)
-- ydsz_rule_variable_def.enabled  → Java: RuleVariableDef.isEnabled  (DB列名不变)
-- ydsz_rule_variable_def.required → Java: RuleVariableDef.isRequired (DB列名不变)

-- -----------------------------------------------------------------------------
-- 模块：ydsz-message（全部通过 XML <result column="deleted" property="isDeleted"/> 保持映射）
-- -----------------------------------------------------------------------------
-- ydsz_msg_template.deleted    → Java: MsgTemplate.isDeleted      (DB列名不变)
-- ydsz_msg_notification.deleted → Java: MsgNotification.isDeleted  (DB列名不变)
-- ydsz_msg_log.deleted         → Java: MsgLog.isDeleted            (DB列名不变)

-- -----------------------------------------------------------------------------
-- 模块：ydsz-nextwiki（通过 XML <result column="deleted" property="isDeleted"/> 保持映射）
-- -----------------------------------------------------------------------------
-- ydsz_wiki_space.deleted            → Java: Space.isDeleted           (DB列名不变)
-- ydsz_wiki_space_member.deleted     → Java: SpaceMember.isDeleted     (DB列名不变)
-- ydsz_wiki_space_template.deleted   → Java: SpaceTemplate.isDeleted   (DB列名不变)
-- ydsz_wiki_user_favorite.deleted    → Java: UserFavorite.isDeleted    (DB列名不变)
-- ydsz_wiki_user_recent.deleted      → Java: UserRecent.isDeleted      (DB列名不变)
-- ydsz_wiki_file_version.is_active   → Java: FileVersion.isActive      (DB列名不变，已为 is_active)

-- -----------------------------------------------------------------------------
-- 模块：ydsz-userinfo（通过 @TableField("enabled") 保持映射）
-- -----------------------------------------------------------------------------
-- ydsz_userinfo_api_key.enabled  → Java: ApiKey.isEnabled       (DB列名不变)
-- ydsz_userinfo_api_key.deleted  → Java: ApiKey.isDeleted       (从 MpBaseEntity 继承)

-- -----------------------------------------------------------------------------
-- 模块：ydsz-workflow（通过 XML column 别名 + @TableField("enabled") 保持映射）
-- -----------------------------------------------------------------------------
-- ydsz_flow_admin_role.enabled  → Java: FlowAdminRole.isEnabled  (DB列名不变)
