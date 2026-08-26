# ydsz-cronjob 数据库版本管理

> 本目录存放 ydsz-cronjob 模块的数据库 Schema 变更脚本（版本化管理）。

## 规范

| 项 | 约定 |
|---|---|
| **目录** | `ydsz-cronjob/db/changelog/` |
| **命名** | `V{主版本}__{描述}.sql`（Flyway 约定，如 `V1__init_schema.sql`） |
| **基线** | `V1__init_schema.sql` 为初始建表脚本（MySQL 8.0+，与 `data/mysql/ydsz-cronjob.sql` 保持同源） |
| **增量** | 每次 Schema 变更新增一个版本文件，**禁止修改已发布的版本文件**（保证可重复回放） |
| **方言** | 基线脚本为 MySQL 8.0+；PostgreSQL 部署时由各环境在应用层（Flyway 方言配置或手工执行）适配 |
| **一致性** | Schema 变更必须同步更新 `ydsz-cronjob-infra` 实体类与 MyBatis Mapper XML，保持代码-SQL 零漂移 |

## 变更流程

1. 新增文件：`db/changelog/V{n+1}__{变更描述}.sql`
2. 变更脚本必须幂等（`IF NOT EXISTS` / 幂等更新），可重复执行
3. 同步更新 `ydsz-cronjob-infra/src/main/resources/mapper/` 下相关 SQL
4. 若涉及既有数据，脚本内提供数据迁移与回滚注释
5. PR 描述中列出 Schema 变更清单，评审人核对实体类一致性

## 当前版本

| 版本 | 说明 |
|---|---|
| V1 | 初始建表：ydsz_job / ydsz_job_glue / ydsz_job_task / ydsz_job_history / ydsz_job_dag / ydsz_job_dag_version / ydsz_job_dag_instance / ydsz_job_dag_node_instance / ydsz_job_log / ydsz_job_log_content / ydsz_alert_dispatch / ydsz_job_alert_rule / ydsz_job_node / ydsz_job_webhook / ydsz_job_artifact / ydsz_job_daily_stats / ydsz_tenant_quota / ydsz_job_outbox（18 张表） |
