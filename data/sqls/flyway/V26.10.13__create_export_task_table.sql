-- =============================================================================
-- Flyway Migration: V26.10.13
-- Description: 创建通用异步导出任务表 ydsz_comm_export_task，支撑大数据量异步导出场景
-- Author:      ydsz-team
-- Created:     2026-10-08
-- =============================================================================
--
-- 背景: 大数据量 Excel 导出同步执行容易超时（HTTP 30s 限制）且占用请求线程。
--       新增通用导出任务表，支持提交 → 异步处理 → 下载 → 清理 完整生命周期。
--       适用于 cronjob/job-export、workflow/flow-export、message/log-export 等场景。
--
-- 表设计:
--   - 按模块分业务（module 字段区分来源模块）
--   - 支持进度追踪（progress_percent 0-100）
--   - 支持任务过期自动清理（expire_at）
--   - 存储下载链接（storage_key + bucket_name，对接 IFileStorageProvider）
-- =============================================================================

-- ===========================================================================
-- 第 1 步：创建通用导出任务表
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

-- ===========================================================================
-- 第 2 步：创建索引
-- ===========================================================================
CREATE INDEX "idx_export_task_tenant_module" ON "ydsz_comm_export_task" ("tenant_id", "module", "status");
CREATE INDEX "idx_export_task_tenant_user"  ON "ydsz_comm_export_task" ("tenant_id", "created_by", "created_at");
CREATE INDEX "idx_export_task_expire"       ON "ydsz_comm_export_task" ("expire_at") WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX "idx_export_task_status"       ON "ydsz_comm_export_task" ("status", "created_at");

-- ===========================================================================
-- 第 3 步：注释
-- ===========================================================================
COMMENT ON TABLE "ydsz_comm_export_task" IS '通用异步导出任务表（多模块共享）';
COMMENT ON COLUMN "ydsz_comm_export_task"."module" IS '来源模块标识（如 cronjob/workflow/message/system）';
COMMENT ON COLUMN "ydsz_comm_export_task"."task_type" IS '导出任务类型（如 JOB_EXPORT/LOG_EXPORT/FLOW_EXPORT）';
COMMENT ON COLUMN "ydsz_comm_export_task"."status" IS '任务状态：PENDING/PROCESSING/SUCCEEDED/FAILED/CANCELED';
COMMENT ON COLUMN "ydsz_comm_export_task"."storage_key" IS '生成文件存储键（对接 IFileStorageProvider）';
COMMENT ON COLUMN "ydsz_comm_export_task"."expire_at" IS '任务过期时间（过期后可清理文件和记录）';
