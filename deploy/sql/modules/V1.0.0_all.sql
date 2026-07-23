-- ============================================================
-- YDSZ Full Database Initialization Script (V1.0.0 版本)
-- Executes all module scripts in dependency order
-- ============================================================

-- V1.0.0 模块 SQL（按后端服务拆分）
\i V1.0.0_system.sql
\i V1.0.0_userinfo.sql
\i V1.0.0_project.sql     -- 项目执行服务 (port 9003, 34 张表, 含原 sales 6 张 + 原 finance 8 张)
\i V1.0.0_cronjob.sql
\i V1.0.0_message.sql
\i V1.0.0_workflow.sql
\i V1.0.0_agent.sql
\i V1.0.0_nextwiki.sql    -- NextWiki 网盘知识库服务 (9 张表, 2026-07-13 规范化命名)
\i V1.0.0_literule.sql    -- 规则引擎服务 (含 8 张业务表, 2026-07-12 从 project 迁移)

-- V1.0.0 架构优化：废弃重复表清理与数据迁移（已合并至各模块 DDL）
-- V1.0.0 架构优化：统一 DAG 引擎表（已合并至 V1.0.0_cronjob.sql）
