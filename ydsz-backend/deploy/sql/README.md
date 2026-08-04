# 数据库 Schema 管理规范

> 本目录是 ydsz-backend 所有数据库对象的**唯一事实来源（Source of Truth）**。
> 遵循项目规范：**禁止引入 Flyway / Liquibase**，Schema 变更通过脚本版本化管理 + CI 校验。

## 目录结构

```
deploy/sql/
├── schema/                    # 版本化 DDL（增量，只追加不修改）
│   ├── V1.0.0__init.sql       # 初始建表（126+ 张表 + 5 视图）
│   ├── V1.0.1__*.sql          # 后续增量变更
│   └── ...
├── seed/                      # 种子数据（幂等，可重复执行）
│   └── seed_data.sql          # 字典/菜单/默认租户/超级管理员
├── verify/
│   └── schema_check.sh        # CI 中校验 Schema 一致性（对比导出与期望）
└── README.md                  # 本文件
```

## 版本化规则

1. **文件命名**：`V<主>.<次>.<补丁>__<描述>.sql`（如 `V1.0.1__add_alert_tables.sql`）
2. **只追加不修改**：已发布的版本文件**禁止编辑**。如需修正，追加新版本
3. **幂等性**：`seed/` 目录脚本必须可重复执行（使用 `ON CONFLICT DO NOTHING` 或先查后插）
4. **执行顺序**：按版本号升序执行，一个脚本一个事务
5. **回滚**：每个版本文件顶部注释注明 `-- ROLLBACK: <逆向 DDL>`，供人工回滚

## 首次初始化流程

> ⚠️ 注意：`V1.0.0__init.sql` 当前为**占位文件**。真实 DDL 需从现有开发/生产库导出后粘贴：

```bash
# 1. 从已有数据库导出完整 Schema（不含数据）
pg_dump --host=<PG_HOST> --port=5432 --username=ydsz \
  --schema-only --no-owner --no-privileges \
  --file=V1.0.0__init.sql ydsz

# 2. 检查导出结果（表数应 >= 126，视图 >= 5）
grep -c "CREATE TABLE" V1.0.0__init.sql

# 3. 放入 schema/ 目录并执行校验
./verify/schema_check.sh
```

## CI 校验机制（verify/schema_check.sh）

每次 CI 构建执行：

```bash
# 1. 启动临时 PostgreSQL 容器
# 2. 依次执行 schema/*.sql + seed/*.sql
# 3. pg_dump --schema-only 导出实际结果
# 4. 与期望 Schema 文件对比（diff）
# 5. 不一致 → CI 失败，提示开发者补充/修正版本脚本
```

## 变更流程（日常开发）

```bash
# 1. 创建增量版本脚本（禁止改旧版本）
cat > schema/V1.0.1__add_alert_tables.sql <<'EOF'
-- ROLLBACK: DROP TABLE IF EXISTS ydsz_alert_dispatch;
CREATE TABLE ydsz_alert_dispatch (...);
EOF

# 2. 本地应用并验证
psql -h localhost -U ydsz -d ydsz -f schema/V1.0.1__add_alert_tables.sql

# 3. 提交 PR，CI 会自动校验 Schema 一致性
```

## 约定

- 表名统一小写下划线：`ydsz_<domain>_<entity>`
- 主键统一 `BIGSERIAL`/`BIGINT GENERATED ALWAYS AS IDENTITY`
- 审计字段统一：`created_by / created_at / updated_by / updated_at / deleted`
- 所有表必须带注释（`COMMENT ON TABLE` / `COMMENT ON COLUMN`）
- 时间字段统一 `TIMESTAMPTZ`（带时区），禁止 `TIMESTAMP WITHOUT TIME ZONE`
