-- =============================================================================
-- Flyway Migration: V26.10.08
-- Description: 并行网关 join token 持久化表（ydsz_flow_join_token）
-- Author:      ydsz-team
-- Created:     2026-10-08
-- =============================================================================
--
-- 背景: FlowJoinTokenService 当前使用 Redis 跟踪并行网关的分支到达状态。
--       Redis 重启或服务重启时可能丢失进行中的 join token，导致并行实例卡死。
--       新增 ydsz_flow_join_token 表将 join token 状态持久化到数据库，确保可恢复。
--
-- 设计:
--   - 每条记录对应一个 (instance_id, join_node_code) 的 join 聚合状态
--   - arrived_count 原子递增（UPDATE ... SET arrived_count = arrived_count + 1）
--   - join_status: PENDING=聚合中 / COMPLETED=已聚合 / CANCELLED=已取消
--   - 实例终止/聚合通过后标记 COMPLETED，由定期任务清理历史记录
--
-- 执行建议:
--   - 表数据量 = 运行中实例的并行网关数，通常 < 1000 条
--   - 建议在低峰期执行
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
