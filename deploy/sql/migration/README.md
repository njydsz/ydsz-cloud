# 数据库迁移版本管理 (Flyway)

## 概述

本项目使用 [Flyway](https://flywaydb.org/) 管理数据库 Schema 变更，确保所有环境（dev/sit/uat/prod）的数据库结构一致且可追溯。

## 目录结构

```
deploy/sql/migration/
├── V1.0.0__init_schema.sql        # 初始 Schema（从 V1.0.0.sql 迁移）
├── V1.1.0__add_workflow_tables.sql
├── V1.2.0__add_agent_tables.sql
├── V1.3.0__add_skywalking_trace_table.sql
└── README.md                       # 本文件
```

## 命名规范

```
V{主版本}.{次版本}.{补丁}__{简短描述}.sql
```

- **版本号**：与发版版本一致，如 `V1.3.0`
- **描述**：用下划线分隔的英文描述，如 `add_user_avatar_column`
- **示例**：`V1.3.1__add_user_avatar_column.sql`

## 使用方法

### 1. 新增迁移脚本

```bash
# 在 deploy/sql/migration/ 下创建新文件
touch deploy/sql/migration/V1.3.1__add_new_feature.sql
```

### 2. 编写 SQL

```sql
-- V1.3.1 添加用户头像字段
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_user_avatar ON sys_user(avatar_url);
```

### 3. 部署

Flyway 在微服务启动时自动执行未应用的迁移脚本，无需手动操作。

## 注意事项

1. **不可修改已发布的脚本**：已执行过的迁移脚本不可修改，如需变更请创建新脚本
2. **向前兼容**：迁移脚本应向前兼容，先部署新代码再切换数据库
3. **DDL 与 DML 分离**：Schema 变更（DDL）和数据迁移（DML）应分开脚本
4. **使用 IF NOT EXISTS**：所有 DDL 语句使用 `IF NOT EXISTS` / `IF EXISTS` 确保幂等
5. **事务安全**：Flyway 默认在事务中执行每个脚本，DDL 语句注意事务兼容性
