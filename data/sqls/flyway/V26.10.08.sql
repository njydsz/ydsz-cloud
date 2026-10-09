-- =============================================================================
-- Flyway Migration: V26.10.08
-- Description: 并行网关 join token 持久化表 + V09~V15 净唯一变更合并
-- Author:      ydsz-team
-- Created:     2026-10-08
-- =============================================================================
--
-- 背景: 本脚本合并了两部分变更：
--   A) 并行网关 join token 持久化表（ydsz_flow_join_token）
--      - FlowJoinTokenService 当前使用 Redis 跟踪并行网关的分支到达状态。
--      - Redis 重启或服务重启时可能丢失进行中的 join token，导致并行实例卡死。
--      - 新增 ydsz_flow_join_token 表将 join token 状态持久化到数据库，确保可恢复。
--
--   B) V26.10.09 ~ V26.10.15 净唯一变更合并
--      - 原 8 个脚本（V09~V15）存在重复变更和未来日期占位问题
--      - 本次提取净唯一变更合并到本脚本，删除 8 个冗余文件
--      - 去重排除：ydsz_idm_auth_apikey.is_enabled、ydsz_idm_auth_policy.*、
--        ydsz_agt_dag_workflow.is_published、ydsz_agt_prompt_template.is_ab_test_enabled
--        （已在 V26.10.03/V26.10.05 完成）
--
-- 各 Section 变更一览：
--   Section 1: pg_trgm GIN 索引（12 个索引）
--   Section 2: Boolean→SMALLINT 净唯一转换（33 张表）
--   Section 3: FK 外键约束（6 个约束）
--   Section 4: ydzs_msg_log / ydzs_msg_outbox 按月 RANGE 分区
--   Section 5: 残留 BOOLEAN 二次扫描 + 时间戳风格审计
--   Section 6: 通用异步导出任务表（ydsz_comm_export_task）
--   Section 7: ID 类字段统一 VARCHAR(36)（Generator/Agent/Userinfo 模块）
--   Section 8: ydzs_agt_token_usage 新增 bot_id 列
--
-- 执行建议:
--   - 建议在低峰期执行，Section 4 分区迁移涉及数据拷贝
--   - 表数据量 = 运行中实例的并行网关数，通常 < 1000 条
-- =============================================================================

CREATE TABLE "ydsz_flow_join_token" (
  "id" character varying(36) NOT NULL,
  "instance_id" character varying(36) NOT NULL,
  "join_node_code" character varying(255) NOT NULL,
  "total_branches" integer NOT NULL DEFAULT 0,
  "required_branches" integer NOT NULL DEFAULT 0,
  "arrived_count" integer NOT NULL DEFAULT 0,
  "join_status" character varying(32) NOT NULL DEFAULT 'PENDING'::character varying,
  "tenant_id" character varying(36) NOT NULL DEFAULT '0'::character varying,
  "is_deleted" smallint NOT NULL DEFAULT 0,
  "created_by" character varying(36) DEFAULT NULL::character varying,
  "created_at" timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_by" character varying(36) DEFAULT NULL::character varying,
  "updated_at" timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "revision" integer NOT NULL DEFAULT 0,
  CONSTRAINT "pk_ydsz_flow_join_token" PRIMARY KEY (id)
);
COMMENT ON TABLE "ydsz_flow_join_token" IS '并行网关 join 令牌持久化表（跟踪分支到达状态）';
COMMENT ON COLUMN "ydsz_flow_join_token"."id" IS '主键 ID（Snowflake）';
COMMENT ON COLUMN "ydsz_flow_join_token"."instance_id" IS '流程实例 ID';
COMMENT ON COLUMN "ydsz_flow_join_token"."join_node_code" IS 'join 节点编码（并行/包容网关）';
COMMENT ON COLUMN "ydsz_flow_join_token"."total_branches" IS '总分支数（入边数）';
COMMENT ON COLUMN "ydsz_flow_join_token"."required_branches" IS '聚合所需到达数（0 表示全部分支）';
COMMENT ON COLUMN "ydsz_flow_join_token"."arrived_count" IS '已到达分支计数';
COMMENT ON COLUMN "ydsz_flow_join_token"."join_status" IS '聚合状态（PENDING=聚合中 / COMPLETED=已完成 / CANCELLED=已取消）';
COMMENT ON COLUMN "ydsz_flow_join_token"."tenant_id" IS '租户 ID';
COMMENT ON COLUMN "ydsz_flow_join_token"."is_deleted" IS '逻辑删除标识（0=未删除，1=已删除）';
COMMENT ON COLUMN "ydsz_flow_join_token"."created_by" IS '创建人 ID';
COMMENT ON COLUMN "ydsz_flow_join_token"."created_at" IS '创建时间';
COMMENT ON COLUMN "ydsz_flow_join_token"."updated_by" IS '最后更新人 ID';
COMMENT ON COLUMN "ydsz_flow_join_token"."updated_at" IS '最后更新时间';
COMMENT ON COLUMN "ydsz_flow_join_token"."revision" IS '乐观锁版本号';

CREATE INDEX "idx_ydsz_flow_join_token_instance_node" ON "ydsz_flow_join_token" USING btree ("instance_id" ASC NULLS LAST, "join_node_code" ASC NULLS LAST, "is_deleted" ASC NULLS LAST);
CREATE INDEX "idx_ydsz_flow_join_token_status" ON "ydsz_flow_join_token" USING btree ("join_status" ASC NULLS LAST, "is_deleted" ASC NULLS LAST);
CREATE INDEX "idx_ydsz_flow_join_token_tenant_deleted" ON "ydsz_flow_join_token" USING btree ("tenant_id" ASC NULLS LAST, "is_deleted" ASC NULLS LAST);
-- Section 1: pg_trgm GIN 索引（来自 V26.10.09 第一个脚本，唯一）
-- ===========================================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ydsz_file_search_index（nextwiki 搜索索引表）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_file_search_index_name_trgm
    ON ydsz_file_search_index USING gin (name gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_file_search_index_path_trgm
    ON ydsz_file_search_index USING gin (path gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_file_search_index_content_trgm
    ON ydsz_file_search_index USING gin (content gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_file_search_index_tags_trgm
    ON ydsz_file_search_index USING gin (tags gin_trgm_ops);

-- ydsz_rule_def（规则引擎规则定义表）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_rule_code_trgm
    ON ydsz_rule_def USING gin (rule_code gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_rule_name_trgm
    ON ydsz_rule_def USING gin (rule_name gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_description_trgm
    ON ydsz_rule_def USING gin (description gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_condition_expr_trgm
    ON ydsz_rule_def USING gin (condition_expression gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_category_trgm
    ON ydsz_rule_def USING gin (category gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_category_path_trgm
    ON ydsz_rule_def USING gin (category_path gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_owner_trgm
    ON ydsz_rule_def USING gin (owner gin_trgm_ops);


-- ===========================================================================
-- Section 2: Boolean → SMALLINT 净唯一变更
--         （来自 V26.10.09 第二个脚本，已排除4项重复）
-- ===========================================================================

-- 模块: sys
ALTER TABLE ydsz_sys_config
  ALTER COLUMN is_public SET DATA TYPE SMALLINT USING CASE WHEN is_public IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_public SET NOT NULL,
  ALTER COLUMN is_public SET DEFAULT 0;

-- 模块: idm（排除已重复的 auth_apikey.is_enabled 和 auth_policy 4项）
ALTER TABLE ydsz_idm_role
  ALTER COLUMN is_built_in SET DATA TYPE SMALLINT USING CASE WHEN is_built_in IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_built_in SET NOT NULL,
  ALTER COLUMN is_built_in SET DEFAULT 0;

-- 模块: job
ALTER TABLE ydsz_job_tenant_quota
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_job_alert_rule
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_job_history
  ALTER COLUMN is_deleted SET DATA TYPE SMALLINT USING CASE WHEN is_deleted IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_deleted SET NOT NULL,
  ALTER COLUMN is_deleted SET DEFAULT 0;

-- 模块: rule
ALTER TABLE ydsz_rule_decision_table
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_ab_policy
  ALTER COLUMN is_auto_rollback_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_auto_rollback_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_auto_rollback_enabled SET NOT NULL,
  ALTER COLUMN is_auto_rollback_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_ab_rollback
  ALTER COLUMN is_from_canary SET DATA TYPE SMALLINT USING CASE WHEN is_from_canary IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_from_canary SET NOT NULL,
  ALTER COLUMN is_from_canary SET DEFAULT 0;

ALTER TABLE ydsz_rule_decision_tree
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_def
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0,
  ALTER COLUMN is_drilldown_available SET DATA TYPE SMALLINT USING CASE WHEN is_drilldown_available IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_drilldown_available SET NOT NULL,
  ALTER COLUMN is_drilldown_available SET DEFAULT 0;

ALTER TABLE ydsz_rule_dependency
  ALTER COLUMN is_cascade_on_disable SET DATA TYPE SMALLINT USING CASE WHEN is_cascade_on_disable IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_cascade_on_disable SET NOT NULL,
  ALTER COLUMN is_cascade_on_disable SET DEFAULT 0;

ALTER TABLE ydsz_rule_execution_trace
  ALTER COLUMN is_triggered SET DATA TYPE SMALLINT USING CASE WHEN is_triggered IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_triggered SET NOT NULL,
  ALTER COLUMN is_triggered SET DEFAULT 0;

ALTER TABLE ydsz_rule_pack
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0,
  ALTER COLUMN is_official SET DATA TYPE SMALLINT USING CASE WHEN is_official IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_official SET NOT NULL,
  ALTER COLUMN is_official SET DEFAULT 0;

ALTER TABLE ydsz_rule_scorecard
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_script
  ALTER COLUMN is_sandbox_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_sandbox_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_sandbox_enabled SET NOT NULL,
  ALTER COLUMN is_sandbox_enabled SET DEFAULT 0,
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_rule_variable_def
  ALTER COLUMN is_required SET DATA TYPE SMALLINT USING CASE WHEN is_required IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_required SET NOT NULL,
  ALTER COLUMN is_required SET DEFAULT 0,
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

-- 模块: msg
ALTER TABLE ydsz_msg_preference
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0,
  ALTER COLUMN is_dnd_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_dnd_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_dnd_enabled SET NOT NULL,
  ALTER COLUMN is_dnd_enabled SET DEFAULT 0,
  ALTER COLUMN is_digest_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_digest_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_digest_enabled SET NOT NULL,
  ALTER COLUMN is_digest_enabled SET DEFAULT 0;

-- 模块: wiki（文件引擎）
ALTER TABLE ydsz_file_file_acl
  ALTER COLUMN is_inherited SET DATA TYPE SMALLINT USING CASE WHEN is_inherited IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_inherited SET NOT NULL,
  ALTER COLUMN is_inherited SET DEFAULT 0,
  ALTER COLUMN is_owner SET DATA TYPE SMALLINT USING CASE WHEN is_owner IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_owner SET NOT NULL,
  ALTER COLUMN is_owner SET DEFAULT 0;

ALTER TABLE ydsz_file_file_comment
  ALTER COLUMN is_resolved SET DATA TYPE SMALLINT USING CASE WHEN is_resolved IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_resolved SET NOT NULL,
  ALTER COLUMN is_resolved SET DEFAULT 0,
  ALTER COLUMN is_edited SET DATA TYPE SMALLINT USING CASE WHEN is_edited IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_edited SET NOT NULL,
  ALTER COLUMN is_edited SET DEFAULT 0;

ALTER TABLE ydsz_file_file_node
  ALTER COLUMN is_preview_ready SET DATA TYPE SMALLINT USING CASE WHEN is_preview_ready IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_preview_ready SET NOT NULL,
  ALTER COLUMN is_preview_ready SET DEFAULT 0,
  ALTER COLUMN is_starred SET DATA TYPE SMALLINT USING CASE WHEN is_starred IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_starred SET NOT NULL,
  ALTER COLUMN is_starred SET DEFAULT 0;

ALTER TABLE ydsz_file_file_version
  ALTER COLUMN is_active SET DATA TYPE SMALLINT USING CASE WHEN is_active IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_active SET NOT NULL,
  ALTER COLUMN is_active SET DEFAULT 0;

ALTER TABLE ydsz_file_share_link
  ALTER COLUMN is_reminder_sent SET DATA TYPE SMALLINT USING CASE WHEN is_reminder_sent IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_reminder_sent SET NOT NULL,
  ALTER COLUMN is_reminder_sent SET DEFAULT 0;

ALTER TABLE ydsz_file_space_template
  ALTER COLUMN is_system SET DATA TYPE SMALLINT USING CASE WHEN is_system IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_system SET NOT NULL,
  ALTER COLUMN is_system SET DEFAULT 0,
  ALTER COLUMN is_public_access SET DATA TYPE SMALLINT USING CASE WHEN is_public_access IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_public_access SET NOT NULL,
  ALTER COLUMN is_public_access SET DEFAULT 0;

-- 模块: flow（流程引擎）
ALTER TABLE ydsz_flow_admin_role
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_flow_auto_trigger
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

ALTER TABLE ydsz_flow_cc_rule
  ALTER COLUMN is_enabled SET DATA TYPE SMALLINT USING CASE WHEN is_enabled IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_enabled SET NOT NULL,
  ALTER COLUMN is_enabled SET DEFAULT 0;

-- 模块: gen（代码生成器 -- V05 只做了 is_deleted，本次补充其他布尔列）
ALTER TABLE ydsz_gen_datasource
  ALTER COLUMN is_default SET DATA TYPE SMALLINT USING CASE WHEN is_default IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_default SET NOT NULL,
  ALTER COLUMN is_default SET DEFAULT 0;

ALTER TABLE ydsz_gen_column_meta
  ALTER COLUMN is_nullable SET DATA TYPE SMALLINT USING CASE WHEN is_nullable IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_nullable SET NOT NULL,
  ALTER COLUMN is_nullable SET DEFAULT 0,
  ALTER COLUMN is_pk SET DATA TYPE SMALLINT USING CASE WHEN is_pk IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_pk SET NOT NULL,
  ALTER COLUMN is_pk SET DEFAULT 0,
  ALTER COLUMN is_dto_skipped SET DATA TYPE SMALLINT USING CASE WHEN is_dto_skipped IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_dto_skipped SET NOT NULL,
  ALTER COLUMN is_dto_skipped SET DEFAULT 0,
  ALTER COLUMN is_vo_skipped SET DATA TYPE SMALLINT USING CASE WHEN is_vo_skipped IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_vo_skipped SET NOT NULL,
  ALTER COLUMN is_vo_skipped SET DEFAULT 0,
  ALTER COLUMN is_query_skipped SET DATA TYPE SMALLINT USING CASE WHEN is_query_skipped IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_query_skipped SET NOT NULL,
  ALTER COLUMN is_query_skipped SET DEFAULT 0;

ALTER TABLE ydsz_gen_template
  ALTER COLUMN is_folder SET DATA TYPE SMALLINT USING CASE WHEN is_folder IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_folder SET NOT NULL,
  ALTER COLUMN is_folder SET DEFAULT 0,
  ALTER COLUMN is_active SET DATA TYPE SMALLINT USING CASE WHEN is_active IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_active SET NOT NULL,
  ALTER COLUMN is_active SET DEFAULT 0;

ALTER TABLE ydsz_gen_template_group
  ALTER COLUMN is_system SET DATA TYPE SMALLINT USING CASE WHEN is_system IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_system SET NOT NULL,
  ALTER COLUMN is_system SET DEFAULT 0,
  ALTER COLUMN is_active SET DATA TYPE SMALLINT USING CASE WHEN is_active IS TRUE THEN 1 ELSE 0 END,
  ALTER COLUMN is_active SET NOT NULL,
  ALTER COLUMN is_active SET DEFAULT 0;


-- ===========================================================================
-- Section 3: FK 外键约束（来自 V26.10.10，唯一）
-- ===========================================================================
-- 1. ydzsz_flow_instance.definition_id -> ydsz_flow_definition(id)
ALTER TABLE "ydsz_flow_instance"
    ADD CONSTRAINT "fk_flow_instance_definition_id"
    FOREIGN KEY ("definition_id") REFERENCES "ydsz_flow_definition" ("id")
    NOT VALID;

-- 2. ydzsz_flow_run_task.instance_id -> ydsz_flow_instance(id)
ALTER TABLE "ydsz_flow_run_task"
    ADD CONSTRAINT "fk_flow_run_task_instance_id"
    FOREIGN KEY ("instance_id") REFERENCES "ydsz_flow_instance" ("id")
    NOT VALID;

-- 3. ydzsz_flow_node.definition_id -> ydsz_flow_definition(id)
ALTER TABLE "ydsz_flow_node"
    ADD CONSTRAINT "fk_flow_node_definition_id"
    FOREIGN KEY ("definition_id") REFERENCES "ydsz_flow_definition" ("id")
    NOT VALID;

-- 4. ydzsz_flow_his_instance.definition_id -> ydsz_flow_definition(id)
ALTER TABLE "ydsz_flow_his_instance"
    ADD CONSTRAINT "fk_flow_his_instance_definition_id"
    FOREIGN KEY ("definition_id") REFERENCES "ydsz_flow_definition" ("id")
    NOT VALID;

-- 5. ydzsz_flow_his_task.instance_id -> ydsz_flow_his_instance(id)
ALTER TABLE "ydsz_flow_his_task"
    ADD CONSTRAINT "fk_flow_his_task_instance_id"
    FOREIGN KEY ("instance_id") REFERENCES "ydsz_flow_his_instance" ("id")
    NOT VALID;

-- 6. ydsz_job_history.job_id -> ydsz_job_main(id)
ALTER TABLE "ydsz_job_history"
    ADD CONSTRAINT "fk_job_history_job_id"
    FOREIGN KEY ("job_id") REFERENCES "ydsz_job_main" ("id")
    NOT VALID;

-- 创建 FK（NOT VALID → VALIDATE）
ALTER TABLE "ydsz_flow_instance" VALIDATE CONSTRAINT "fk_flow_instance_definition_id";
ALTER TABLE "ydsz_flow_run_task" VALIDATE CONSTRAINT "fk_flow_run_task_instance_id";
ALTER TABLE "ydsz_flow_node" VALIDATE CONSTRAINT "fk_flow_node_definition_id";
ALTER TABLE "ydsz_flow_his_instance" VALIDATE CONSTRAINT "fk_flow_his_instance_definition_id";
ALTER TABLE "ydsz_flow_his_task" VALIDATE CONSTRAINT "fk_flow_his_task_instance_id";
ALTER TABLE "ydsz_job_history" VALIDATE CONSTRAINT "fk_job_history_job_id";


-- ===========================================================================
-- Section 4: ydsz_msg_log / ydsz_msg_outbox 按月 RANGE 分区（来自 V26.10.11，唯一）
-- ===========================================================================

-- 4.1 重命名旧表
ALTER TABLE "ydsz_msg_log" RENAME TO "ydsz_msg_log_old";

-- 4.2 创建新分区表
CREATE TABLE "ydsz_msg_log" (
  "id"              character varying(36)  NOT NULL,
  "channel"         character varying(32)  NOT NULL,
  "biz_type"        character varying(36)  DEFAULT NULL::character varying,
  "biz_id"          character varying(36)  DEFAULT NULL::character varying,
  "receiver"        character varying(128) DEFAULT NULL::character varying,
  "template_code"   character varying(36)  DEFAULT NULL::character varying,
  "template_params" jsonb,
  "content"         text,
  "status"          character varying(32)  NOT NULL DEFAULT 'PENDING'::character varying,
  "error_message"   text,
  "priority"        character varying(32)  NOT NULL DEFAULT 'NORMAL'::character varying,
  "sender_id"       character varying(32)  DEFAULT NULL::character varying,
  "message_group"   character varying(64)  DEFAULT NULL::character varying,
  "batch_id"        character varying(64)  DEFAULT NULL::character varying,
  "route_rule_id"   character varying(32)  DEFAULT NULL::character varying,
  "canary"          smallint              DEFAULT 0,
  "canary_key"      character varying(128) DEFAULT NULL::character varying,
  "dedup_key"       character varying(128) DEFAULT NULL::character varying,
  "recall_status"   character varying(32)  NOT NULL DEFAULT 'NONE'::character varying,
  "recall_at"       timestamp without time zone,
  "receipt_status"  character varying(32)  NOT NULL DEFAULT 'NONE'::character varying,
  "receipt_at"      timestamp without time zone,
  "retry_count"     integer               NOT NULL DEFAULT 0,
  "next_retry_at"   timestamp without time zone,
  "provider_trace_id" character varying(128) DEFAULT NULL::character varying,
  "cost_ms"         bigint,
  "cost"            numeric(20,6)         DEFAULT NULL::numeric,
  "trace_id"        character varying(64)  DEFAULT NULL::character varying,
  "msg_id"          character varying(36)  DEFAULT NULL::character varying,
  "topic"           character varying(128) DEFAULT NULL::character varying,
  "reconsume_times" integer,
  "parent_msg_id"   character varying(36)  DEFAULT NULL::character varying,
  "scheduled_at"    timestamp without time zone,
  "sort"            integer               DEFAULT 0,
  "revision"        integer               DEFAULT 0,
  "tenant_id"       character varying(36)  NOT NULL DEFAULT '0'::character varying,
  "is_deleted"      smallint              NOT NULL DEFAULT 0,
  "created_by"      character varying(36)  DEFAULT NULL::character varying,
  "created_at"      timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_by"      character varying(36)  DEFAULT NULL::character varying,
  "updated_at"      timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_ydsz_msg_log" PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 4.3 创建月份分区（2026-10 ~ 2027-09）
CREATE TABLE "ydsz_msg_log_2026_10" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE "ydsz_msg_log_2026_11" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE "ydsz_msg_log_2026_12" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE "ydsz_msg_log_2027_01" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE "ydsz_msg_log_2027_02" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE "ydsz_msg_log_2027_03" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE "ydsz_msg_log_2027_04" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE "ydsz_msg_log_2027_05" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE "ydsz_msg_log_2027_06" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE "ydsz_msg_log_2027_07" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE "ydsz_msg_log_2027_08" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE "ydsz_msg_log_2027_09" PARTITION OF "ydsz_msg_log"
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE "ydsz_msg_log_default" PARTITION OF "ydsz_msg_log" DEFAULT;

-- 4.4 重建索引
CREATE INDEX "idx_ydsz_msg_log_batch_id"            ON "ydsz_msg_log" ("batch_id");
CREATE INDEX "idx_ydsz_msg_log_biz"                 ON "ydsz_msg_log" ("biz_type", "biz_id");
CREATE INDEX "idx_ydsz_msg_log_dedup_key"           ON "ydsz_msg_log" ("dedup_key");
CREATE INDEX "idx_ydsz_msg_log_msg_id"              ON "ydsz_msg_log" ("msg_id");
CREATE INDEX "idx_ydsz_msg_log_provider_trace_id"   ON "ydsz_msg_log" ("provider_trace_id");
CREATE INDEX "idx_ydsz_msg_log_receiver"            ON "ydsz_msg_log" ("receiver");
CREATE INDEX "idx_ydsz_msg_log_scheduled_at"        ON "ydsz_msg_log" ("scheduled_at");
CREATE INDEX "idx_ydsz_msg_log_status_created"      ON "ydsz_msg_log" ("status", "created_at");
CREATE INDEX "idx_ydsz_msg_log_template_code"       ON "ydsz_msg_log" ("template_code");
CREATE INDEX "idx_ydsz_msg_log_tenant_channel_time" ON "ydsz_msg_log" ("tenant_id", "channel", "created_at");
CREATE INDEX "idx_ydsz_msg_log_tenant_is_deleted"   ON "ydsz_msg_log" ("tenant_id", "is_deleted");
CREATE INDEX "idx_ydsz_msg_log_trace_id"           ON "ydsz_msg_log" ("trace_id");

-- 4.5 数据迁移（按月分批）
DO $$
DECLARE
    v_min_date date;
    v_max_date date;
    v_cursor  date;
    v_end     date;
    v_count   bigint;
BEGIN
    SELECT MIN(created_at)::date, MAX(created_at)::date
      INTO v_min_date, v_max_date
      FROM "ydsz_msg_log_old";
    IF v_min_date IS NULL THEN
        RAISE NOTICE 'ydsz_msg_log_old 为空，跳过数据迁移';
        RETURN;
    END IF;
    v_cursor := date_trunc('month', v_min_date)::date;
    v_end    := (date_trunc('month', v_max_date) + interval '1 month')::date;
    WHILE v_cursor < v_end LOOP
        INSERT INTO "ydsz_msg_log"
            SELECT * FROM "ydsz_msg_log_old"
             WHERE created_at >= v_cursor
               AND created_at <  v_cursor + interval '1 month';
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE '迁移 % 数据: % 行', v_cursor, v_count;
        COMMIT;
        v_cursor := v_cursor + interval '1 month';
    END LOOP;
END$$;

-- 4.6 数据校验
DO $$
DECLARE
    v_old_count bigint;
    v_new_count bigint;
BEGIN
    SELECT COUNT(*) INTO v_old_count FROM "ydsz_msg_log_old";
    SELECT COUNT(*) INTO v_new_count FROM "ydsz_msg_log";
    IF v_old_count != v_new_count THEN
        RAISE EXCEPTION '行数不一致! old=%, new=%', v_old_count, v_new_count;
    END IF;
    RAISE NOTICE '行数校验通过: % 行', v_new_count;
END$$;

-- 4.7 ydsz_msg_outbox 分区改造
ALTER TABLE "ydsz_msg_outbox" RENAME TO "ydsz_msg_outbox_old";

CREATE TABLE "ydsz_msg_outbox" (
  "id"              character varying(36)  NOT NULL,
  "aggregate_type"  character varying(128) NOT NULL,
  "aggregate_id"    character varying(128) NOT NULL,
  "event_type"      character varying(128) NOT NULL,
  "payload"         jsonb                  NOT NULL,
  "status"          character varying(32)  NOT NULL DEFAULT 'PENDING'::character varying,
  "publish_attempts" integer               NOT NULL DEFAULT 0,
  "published_at"    timestamp without time zone,
  "sort"            integer               DEFAULT 0,
  "revision"        integer               DEFAULT 0,
  "tenant_id"       character varying(36)  DEFAULT NULL::character varying,
  "is_deleted"      smallint              NOT NULL DEFAULT 0,
  "created_by"      character varying(36),
  "created_at"      timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_by"      character varying(36),
  "updated_at"      timestamp without time zone DEFAULT now(),
  CONSTRAINT "pk_ydsz_msg_outbox" PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE "ydsz_msg_outbox_2026_10" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE "ydsz_msg_outbox_2026_11" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE "ydsz_msg_outbox_2026_12" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE "ydsz_msg_outbox_2027_01" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE "ydsz_msg_outbox_2027_02" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE "ydsz_msg_outbox_2027_03" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE "ydsz_msg_outbox_2027_04" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE "ydsz_msg_outbox_2027_05" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE "ydsz_msg_outbox_2027_06" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE "ydsz_msg_outbox_2027_07" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE "ydsz_msg_outbox_2027_08" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE "ydsz_msg_outbox_2027_09" PARTITION OF "ydsz_msg_outbox"
    FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE "ydsz_msg_outbox_default" PARTITION OF "ydsz_msg_outbox" DEFAULT;

CREATE INDEX "idx_ydsz_msg_outbox_aggregate"      ON "ydsz_msg_outbox" ("aggregate_type", "aggregate_id");
CREATE INDEX "idx_ydsz_msg_outbox_status_created" ON "ydsz_msg_outbox" ("status", "created_at");
CREATE INDEX "idx_ydsz_msg_outbox_status_published" ON "ydsz_msg_outbox" ("status", "published_at");
CREATE INDEX "idx_ydsz_msg_outbox_tenant_status"  ON "ydsz_msg_outbox" ("tenant_id", "status");

-- outbox 数据迁移
DO $$
DECLARE
    v_min_date date;
    v_max_date date;
    v_cursor  date;
    v_end     date;
    v_count   bigint;
BEGIN
    SELECT MIN(created_at)::date, MAX(created_at)::date
      INTO v_min_date, v_max_date
      FROM "ydsz_msg_outbox_old";
    IF v_min_date IS NULL THEN
        RAISE NOTICE 'ydsz_msg_outbox_old 为空，跳过数据迁移';
        RETURN;
    END IF;
    v_cursor := date_trunc('month', v_min_date)::date;
    v_end    := (date_trunc('month', v_max_date) + interval '1 month')::date;
    WHILE v_cursor < v_end LOOP
        INSERT INTO "ydsz_msg_outbox"
            SELECT * FROM "ydsz_msg_outbox_old"
             WHERE created_at >= v_cursor
               AND created_at <  v_cursor + interval '1 month';
        GET DIAGNOSTICS v_count = ROW_COUNT;
        RAISE NOTICE '迁移 % 数据: % 行', v_cursor, v_count;
        COMMIT;
        v_cursor := v_cursor + interval '1 month';
    END LOOP;
END$$;

-- outbox 数据校验
DO $$
DECLARE
    v_old_count bigint;
    v_new_count bigint;
BEGIN
    SELECT COUNT(*) INTO v_old_count FROM "ydsz_msg_outbox_old";
    SELECT COUNT(*) INTO v_new_count FROM "ydsz_msg_outbox";
    IF v_old_count != v_new_count THEN
        RAISE EXCEPTION '行数不一致! old=%, new=%', v_old_count, v_new_count;
    END IF;
    RAISE NOTICE '行数校验通过: % 行', v_new_count;
END$$;


-- ===========================================================================
-- Section 5: 残留 BOOLEAN 二次扫描 + 时间戳风格审计（来自 V26.10.12 净唯一部分）
-- ===========================================================================
-- 说明：分区表 tenant_id 在 V11 创建时已是 varchar(36)，故不重复 ALTER

-- 5.1 残留 BOOLEAN 字段补充修复
DO $$
DECLARE
    boolean_column RECORD;
BEGIN
    FOR boolean_column IN
        SELECT c.relname AS table_name, a.attname AS column_name
          FROM pg_catalog.pg_attribute a
          JOIN pg_catalog.pg_class c ON a.attrelid = c.oid
          JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid
         WHERE a.atttypid = 'boolean'::regtype
           AND a.attnum > 0
           AND NOT a.attisdropped
           AND n.nspname = 'public'
           AND c.relkind IN ('r', 'p')
    LOOP
        RAISE NOTICE '发现残留 BOOLEAN 字段: %.%，正在修复...',
            boolean_column.table_name, boolean_column.column_name;
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN %I SET DATA TYPE SMALLINT USING CASE WHEN %I IS TRUE THEN 1 ELSE 0 END, ALTER COLUMN %I SET NOT NULL, ALTER COLUMN %I SET DEFAULT 0',
            boolean_column.table_name,
            boolean_column.column_name,
            boolean_column.column_name,
            boolean_column.column_name,
            boolean_column.column_name
        );
        RAISE NOTICE '已修复: %.% -> SMALLINT NOT NULL DEFAULT 0',
            boolean_column.table_name, boolean_column.column_name;
    END LOOP;
END$$;

-- 5.2 时间字段风格审计（审计模式，仅输出 NOTICE）
DO $$
DECLARE
    ts_column RECORD;
BEGIN
    FOR ts_column IN
        SELECT c.relname AS table_name, a.attname AS column_name,
               pg_get_expr(d.adbin, d.adrelid) AS default_expr
          FROM pg_catalog.pg_attribute a
          JOIN pg_catalog.pg_class c ON a.attrelid = c.oid
          JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid
          LEFT JOIN pg_catalog.pg_attrdef d ON a.attrelid = d.adrelid AND a.attnum = d.adnum
         WHERE a.atttypid IN ('timestamp without time zone'::regtype)
           AND a.attnum > 0
           AND NOT a.attisdropped
           AND n.nspname = 'public'
           AND c.relkind IN ('r', 'p')
           AND pg_get_expr(d.adbin, d.adrelid) ILIKE '%CURRENT_TIMESTAMP%'
           AND c.relname LIKE 'ydsz\_%'
    LOOP
        RAISE NOTICE '时间字段风格修正: %.% (当前: %)',
            ts_column.table_name, ts_column.column_name, ts_column.default_expr;
    END LOOP;
END$$;


-- ===========================================================================
-- Section 6: 通用异步导出任务表（来自 V26.10.13，唯一）
-- ===========================================================================
CREATE TABLE "ydsz_comm_export_task" (
  "id"              character varying(36)  NOT NULL,
  "module"          character varying(64)  NOT NULL,
  "task_name"       character varying(128) NOT NULL,
  "task_type"       character varying(64)  NOT NULL,
  "params"          jsonb,
  "status"          character varying(32)  NOT NULL DEFAULT 'PENDING',
  "progress_percent" smallint              NOT NULL DEFAULT 0,
  "error_message"   text,
  "file_name"       character varying(256),
  "file_size"       bigint,
  "storage_bucket"  character varying(128),
  "storage_key"     character varying(512),
  "mime_type"       character varying(64),
  "download_url"    text,
  "expire_at"       timestamp without time zone,
  "started_at"      timestamp without time zone,
  "completed_at"    timestamp without time zone,
  "retry_count"     smallint              NOT NULL DEFAULT 0,
  "max_retry"       smallint              NOT NULL DEFAULT 3,
  "sort"            integer               DEFAULT 0,
  "revision"        integer               DEFAULT 0,
  "tenant_id"       character varying(36) NOT NULL DEFAULT '0',
  "is_deleted"      smallint              NOT NULL DEFAULT 0,
  "created_by"      character varying(36),
  "created_at"      timestamp without time zone NOT NULL DEFAULT now(),
  "updated_by"      character varying(36),
  "updated_at"      timestamp without time zone NOT NULL DEFAULT now(),
  CONSTRAINT "pk_ydsz_comm_export_task" PRIMARY KEY (id)
);

CREATE INDEX "idx_export_task_tenant_module" ON "ydsz_comm_export_task" ("tenant_id", "module", "status");
CREATE INDEX "idx_export_task_tenant_user"  ON "ydsz_comm_export_task" ("tenant_id", "created_by", "created_at");
CREATE INDEX "idx_export_task_expire"       ON "ydsz_comm_export_task" ("expire_at") WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX "idx_export_task_status"       ON "ydsz_comm_export_task" ("status", "created_at");

COMMENT ON TABLE "ydsz_comm_export_task" IS '通用异步导出任务表（多模块共享）';


-- ===========================================================================
-- Section 7: ID 类字段统一 VARCHAR(36)（来自 V26.10.14，已排除 is_deleted 重复）
-- ===========================================================================

-- 7.1 Generator 模块（排除 is_deleted：V05 已完成）
BEGIN;

ALTER TABLE ydsz_gen_datasource ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_datasource ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_datasource ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_datasource ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_datasource ADD CONSTRAINT pk_ydsz_gen_datasource PRIMARY KEY (id);

ALTER TABLE ydsz_gen_template_group ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_template_group ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_template_group ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_template_group ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_template_group ADD CONSTRAINT pk_ydsz_gen_template_group PRIMARY KEY (id);

ALTER TABLE ydsz_gen_history ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_history ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_history ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_history ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_history ADD CONSTRAINT pk_ydsz_gen_history PRIMARY KEY (id);

ALTER TABLE ydsz_gen_history_file ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_history_file ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_history_file ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_history_file ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_history_file ADD CONSTRAINT pk_ydsz_gen_history_file PRIMARY KEY (id);

ALTER TABLE ydsz_gen_table_meta ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_table_meta ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_table_meta ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_table_meta ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_table_meta ADD CONSTRAINT pk_ydsz_gen_table_meta PRIMARY KEY (id);

ALTER TABLE ydsz_gen_template ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_template ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_template ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_template ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_template ADD CONSTRAINT pk_ydsz_gen_template PRIMARY KEY (id);

ALTER TABLE ydsz_gen_column_meta ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydsz_gen_column_meta ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydsz_gen_column_meta ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydzs_gen_column_meta ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydsz_gen_column_meta ADD CONSTRAINT pk_ydsz_gen_column_meta PRIMARY KEY (id);

-- 删除 Generator 废弃序列
DROP SEQUENCE IF EXISTS ydsz_gen_datasource_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_template_group_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_history_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_history_file_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_table_meta_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_template_id_seq;
DROP SEQUENCE IF EXISTS ydsz_gen_column_meta_id_seq;

COMMIT;

-- 7.2 Agent 模块 ydzs_agt_insight_report
BEGIN;
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN tenant_id TYPE VARCHAR(36);
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydzs_agt_insight_report ALTER COLUMN updated_by TYPE VARCHAR(36);
DROP SEQUENCE IF EXISTS ydzs_agt_insight_report_id_seq;
COMMIT;

-- 7.3 Userinfo 模块
BEGIN;
ALTER TABLE ydzs_idm_auth_credential ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydzs_idm_auth_credential ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydzs_idm_auth_credential ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_credential ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_credential ADD CONSTRAINT pk_ydsz_idm_auth_credential PRIMARY KEY (id);

ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN id DROP DEFAULT;
ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN user_id TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN created_by TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_apikey ALTER COLUMN updated_by TYPE VARCHAR(36);
ALTER TABLE ydzs_idm_auth_apikey ADD CONSTRAINT pk_ydsz_idm_auth_apikey PRIMARY KEY (id);

DROP SEQUENCE IF EXISTS ydzs_idm_auth_credential_id_seq;
DROP SEQUENCE IF EXISTS ydzs_idm_auth_apikey_id_seq;
COMMIT;

-- 7.4 全平台 tenant_id VARCHAR(36) 动态扫描（跳过已为 varchar(36) 的表）
DO $$
DECLARE
    col_record  RECORD;
    current_max information_schema.character_maximum_length;
BEGIN
    FOR col_record IN
        SELECT table_schema, table_name, column_name
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND column_name = 'tenant_id'
           AND table_name LIKE 'ydsz\\_%'
           AND data_type = 'character varying'
    LOOP
        SELECT character_maximum_length INTO current_max
          FROM information_schema.columns
         WHERE table_schema = col_record.table_schema
           AND table_name = col_record.table_name
           AND column_name = 'tenant_id';
        IF current_max IS NULL OR current_max != 36 THEN
            EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I TYPE VARCHAR(36)',
                           col_record.table_schema, col_record.table_name, col_record.column_name);
        END IF;
    END LOOP;
END$$;

-- 7.5 全平台 created_by / updated_by VARCHAR(36) 动态扫描
DO $$
DECLARE
    col_record  RECORD;
    current_max information_schema.character_maximum_length;
BEGIN
    FOR col_record IN
        SELECT table_schema, table_name, column_name
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND column_name IN ('created_by', 'updated_by')
           AND table_name LIKE 'ydsz\\_%'
           AND data_type = 'character varying'
    LOOP
        SELECT character_maximum_length INTO current_max
          FROM information_schema.columns
         WHERE table_schema = col_record.table_schema
           AND table_name = col_record.table_name
           AND column_name = col_record.column_name;
        IF current_max IS NULL OR current_max != 36 THEN
            EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I TYPE VARCHAR(36)',
                           col_record.table_schema, col_record.table_name, col_record.column_name);
        END IF;
    END LOOP;
END$$;

-- 7.6 刷新统计信息
ANALYZE ydsz_gen_datasource;
ANALYZE ydsz_gen_template_group;
ANALYZE ydsz_gen_history;
ANALYZE ydsz_gen_history_file;
ANALYZE ydsz_gen_table_meta;
ANALYZE ydsz_gen_template;
ANALYZE ydsz_gen_column_meta;
ANALYZE ydzs_agt_insight_report;
ANALYZE ydzs_idm_auth_credential;
ANALYZE ydzs_idm_auth_apikey;


-- ===========================================================================
-- Section 8: ydzs_agt_token_usage 新增 bot_id 列（来自 V26.10.15，唯一）
-- ===========================================================================
ALTER TABLE ydsz_agt_token_usage
    ADD COLUMN IF NOT EXISTS bot_id VARCHAR(36) DEFAULT NULL;

COMMENT ON COLUMN ydsz_agt_token_usage.bot_id IS '关联的 Agent 定义 ID（botId），由 RequestContext 在记录时注入，支持按 Agent 维度聚合指标';

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_token_usage_bot_created
    ON ydsz_agt_token_usage (bot_id, created_at) WHERE bot_id IS NOT NULL;


-- ===========================================================================
-- 完成
-- ===========================================================================
