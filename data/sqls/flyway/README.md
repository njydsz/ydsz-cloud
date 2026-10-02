# Flyway 数据库版本管理

本目录存放每个版本更新的数据库脚本文件，由 Flyway 自动执行。

## 脚本命名规范

- 格式：`V{版本号}_{描述}.sql`（推荐） 或 `V{版本号}.sql`
- 版本号格式：`YY.MM.DD`（与项目版本号一致）
- 示例：`V26.11.01_add_user_avatar.sql`

## 当前版本

- **基线版本**：`V26.10.01`（当前数据库全量快照）
- 后续变更需在对应版本脚本中编写增量 DDL/DML

## Flyway 配置（application.yml）

每个业务模块的 `application.yml` 需添加：

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: "26.10.01"
    locations: "filesystem:./data/sqls/flyway"
    out-of-order: false
    validate-on-migrate: true
```

## 注意事项

1. 脚本一旦被执行，**禁止修改**（Flyway 会校验 checksum）
2. 需要撤销时，创建新版本的修正脚本
3. 版本号必须递增，不可重复
4. 基线版本 `V26.10.01.sql` 为当前全量快照，仅用于初始化已存在的数据库
