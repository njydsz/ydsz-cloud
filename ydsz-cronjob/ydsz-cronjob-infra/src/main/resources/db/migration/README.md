# 数据库迁移（Flyway）

## 概述

ydsz-cronjob 使用 Flyway 管理数据库版本演化，迁移脚本位于 `src/main/resources/db/migration/` 目录。

## 版本历史

| 版本 | 文件名 | 描述 |
|------|--------|------|
| 1.0.0 | V1__initial_schema.sql | 初始表结构：任务主表、执行日志、节点注册、任务分片 |
| 1.1.0 | V2__dag_tables.sql | DAG 工作流表：定义、实例、节点实例、版本、上下文 |
| 1.2.0 | V3__auxiliary_tables.sql | 辅助功能表：Webhook、告警、统计、事件存储、审计日志、Outbox、租户配额 |

## 命名规范

Flyway 迁移脚本命名格式：`V{版本号}__{描述}.sql`

- 版本号：数字递增（支持小数点，如 1.0.0、1.1.0）
- 双下划线分隔版本号与描述
- 描述：英文或拼音，单词间用下划线分隔

## 使用方式

### 自动迁移（推荐）

Spring Boot 集成 Flyway 后，应用启动时自动执行未应用的迁移脚本：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

### 手动验证

```bash
# 查看迁移状态
mvn flyway:info

# 执行迁移
mvn flyway:migrate

# 验证迁移完整性
mvn flyway:validate
```

## 注意事项

1. 迁移脚本应保持幂等（使用 `CREATE TABLE IF NOT EXISTS`）
2. 禁止修改已应用的迁移脚本（如需变更，创建新版本脚本）
3. 大表 DDL 建议使用 `pt-online-schema-change` 或 `gh-ost` 执行
4. 所有表使用 InnoDB 引擎 + utf8mb4 字符集
