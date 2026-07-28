# 数据库 Schema 变更管理

## 规范

本项目 **不引入 Flyway / Liquibase** 等自动化 schema migration 框架（见 `deploy/sql/README.md`），但通过以下流程实现结构化的 DDL 变更管理：

### 变更流程

1. **开发者创建变更 SQL 文件**：
   ```
   deploy/sql/changelog/YYYY-MM-DD_<描述>.sql
   ```
   例如：`deploy/sql/changelog/2026-07-29_add_user_avatar.sql`

2. **创建回滚 SQL 文件**（必须）：
   ```
   deploy/sql/rollback/YYYY-MM-DD_<描述>_rollback.sql
   ```
   例如：`deploy/sql/rollback/2026-07-29_add_user_avatar_rollback.sql`

3. **提交 PR** → CI 自动执行：
   - DDL 语法校验（`pg_amcheck`）
   - Schema 漂移检测（`pg_dump diff`）
   - 变更影响范围分析

4. **合并后执行**：
   - SIT 环境：CI 自动执行 `pg-apply-schema.sh --env=sit`
   - 生产环境：DBA 审批后手动执行 `pg-apply-schema.sh --env=prod`

### 变更脚本

```bash
# 执行变更（SIT 环境）
bash deploy/scripts/pg-apply-schema.sh deploy/sql/changelog/2026-07-29_add_user_avatar.sql --env=sit

# 执行变更（生产环境，需 DBA 审批）
bash deploy/scripts/pg-apply-schema.sh deploy/sql/changelog/2026-07-29_add_user_avatar.sql --env=prod

# 漂移检测
bash deploy/scripts/schema-drift-check.sh
```

### 安全规则

| 规则 | 说明 |
|------|------|
| 新增列必须 nullable 或有默认值 | 避免锁表 |
| 删除列必须先标记弃用 ≥ 1 个迭代 | 渐进式删除 |
| 索引创建使用 `CREATE INDEX CONCURRENTLY` | 不阻塞写入 |
| 大表 DDL 必须在低峰期执行 | 避免影响在线业务 |
| 每个 DDL 变更必须有配套回滚 SQL | 保障可回滚 |

### 目录结构

```
deploy/sql/
├── V1.0.0.sql                    # 完整建库脚本（全量）
├── V1.0.0_*.sql                  # 模块级 DDL（全量）
├── changelog/                    # 变更记录（增量）
│   └── 2026-07-29_add_user_avatar.sql
├── rollback/                     # 回滚脚本
│   └── 2026-07-29_add_user_avatar_rollback.sql
└── .schema-drift.diff            # 漂移检测报告（CI 生成）
```
