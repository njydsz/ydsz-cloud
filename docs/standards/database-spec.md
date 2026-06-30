# 数据库设计规范

> 文档版本: V1.0 | 编制日期: 2026-06-30
> 数据库: PostgreSQL 18

## 1. 命名规范

### 1.1 数据库

- 数据库名：小写下划线，业务系统前缀 `pmis_`
- 模式名（Schema）：`pmis` (主业务)、`pmis_log` (日志)、`pmis_cfg` (配置)

### 1.2 表

- 业务表：`pmis_<业务域>_<实体>`，例：`pmis_project_main`, `pmis_user_employee`
- 关联表：`pmis_<主>_<从>_rel`
- 日志表：`pmis_<实体>_<动作>_log`，例：`pmis_user_login_log`
- 字典表：`pmis_dict_<字典名>`，例：`pmis_dict_industry`
- 配置表：`pmis_cfg_<配置名>`，例：`pmis_cfg_system`
- 临时表：`tmp_<业务>_<日期>`
- 表名总长度 ≤ 30 字符

### 1.3 字段

- 主键：`id`，BIGSERIAL 或 BIGINT
- 外键：`<关联实体>_id`
- 状态：`status` SMALLINT 或 VARCHAR(32)
- 逻辑删除：`deleted` SMALLINT DEFAULT 0
- 审计字段：见 §3
- 金额：`amount` NUMERIC(18,2)
- 时间：`created_at` / `updated_at` TIMESTAMP
- 布尔：`is_<属性>` 或 `deleted`/`status`
- 字段名总长度 ≤ 30 字符

### 1.4 索引

- 主键索引：`pk_<表名>`
- 唯一索引：`uk_<表名>_<字段>`
- 普通索引：`idx_<表名>_<字段>`
- 联合索引：`idx_<表名>_<字段1>_<字段2>`
- 索引名 ≤ 63 字符

### 1.5 约束

- 主键：`pk_<表名>`
- 外键：`fk_<表名>_<关联表>`
- 检查：`ck_<表名>_<字段>`

## 2. 字段类型规范

| 用途 | PostgreSQL 类型 | 说明 |
|------|------------------|------|
| 主键 | BIGSERIAL 或 BIGINT | 单库自增；分布式用雪花算法 |
| 短字符串 | VARCHAR(N) | N 按业务定，≤255 |
| 长文本 | TEXT | 避免使用 |
| 整数 | INTEGER | 状态、计数 |
| 大整数 | BIGINT | 主键、外键 |
| 小数 | NUMERIC(18,2) | **金额** |
| 比例 | NUMERIC(5,4) | 0.0000 - 1.0000 |
| 时间戳 | TIMESTAMP | 默认 `CURRENT_TIMESTAMP` |
| 日期 | DATE | 仅日期 |
| 布尔 | BOOLEAN | 业务标志 |
| JSON | JSONB | 灵活扩展 |
| 数组 | ARRAY | 谨慎使用 |

**禁止**：
- ❌ 使用 FLOAT / DOUBLE 存储金额
- ❌ 使用 VARCHAR 存储日期
- ❌ 使用 TEXT 替代 VARCHAR
- ❌ 使用 BIGINT 存储时间戳

## 3. 必含审计字段

每张业务表必须包含：

```sql
created_by    BIGINT       NOT NULL DEFAULT 0,        -- 创建人 ID
created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_by    BIGINT       NOT NULL DEFAULT 0,        -- 最后修改人
updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
deleted       SMALLINT     NOT NULL DEFAULT 0,         -- 逻辑删除 0/1
```

`status` 与 `deleted` 必须独立：
- `status` 描述业务状态（草稿、进行中、已结束等）
- `deleted` 仅表示数据是否被逻辑删除

## 4. 表设计模板

```sql
-- 主业务表
CREATE TABLE pmis_project_main (
    id              BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    level           VARCHAR(16)  NOT NULL,
    contract_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    owner_id        BIGINT,
    start_date      DATE,
    end_date        DATE,
    description     TEXT,

    created_by      BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,

    CONSTRAINT uk_pmis_project_main_code UNIQUE (code, deleted)
);

CREATE INDEX idx_pmis_project_main_name ON pmis_project_main (name);
CREATE INDEX idx_pmis_project_main_status ON pmis_project_main (status) WHERE deleted = 0;
CREATE INDEX idx_pmis_project_main_owner ON pmis_project_main (owner_id);
```

## 5. 索引规范

### 5.1 必须建索引的场景

- 主键（自动）
- 外键
- 业务查询字段（按 `WHERE` 高频字段）
- 排序字段
- 联合查询：建联合索引，遵循最左前缀原则

### 5.2 不应建索引的场景

- 频繁更新的字段（索引维护成本高）
- 区分度低的字段（性别、状态等）
- 小表（< 1000 行）
- 不在 WHERE 中使用的字段

### 5.3 联合索引顺序

- 等值查询字段在前，范围查询字段在后
- 高区分度字段在前，低区分度字段在后
- 常用查询字段在前

## 6. SQL 编写规范

### 6.1 必须遵守

- 关键字大写：`SELECT * FROM pmis_user WHERE id = 1`
- 表别名简短有意义：`SELECT u.* FROM pmis_user u`
- 多表 JOIN 显式指定连接类型：`INNER JOIN` / `LEFT JOIN`
- `LIMIT` 必须带 `ORDER BY`
- **禁止** `SELECT *`，必须显式列出字段
- **禁止** 在 `WHERE` 中对字段使用函数（导致索引失效）
- **禁止** 使用 `NOT IN`（用 `NOT EXISTS` 替代）
- **禁止** 隐式类型转换

### 6.2 分页查询

```sql
SELECT id, name, status
FROM pmis_project_main
WHERE deleted = 0
  AND status = 'ACTIVE'
  AND created_at >= '2026-01-01'
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
```

### 6.3 禁止

- ❌ 多表 UPDATE / DELETE（拆为单表）
- ❌ 大事务
- ❌ 长事务（> 5 秒）
- ❌ 全表扫描（> 100 万行表必须走索引）
- ❌ `DELETE FROM table`（必须带 WHERE）

## 7. 性能规范

- 单表数据量 > 500 万行：考虑分表
- 单表数据量 > 1 亿行：必须分表（按年度/季度）
- 长事务：必须拆分为短事务
- 大批量操作：分批次（每批 1000 行）
- 慢查询：> 200ms 必须优化

## 8. 数据迁移

- 所有 DDL 必须写迁移文件，按版本号管理
- 工具：Flyway 或 Liquibase
- 迁移文件命名：`V1_0_0_001__init_user_table.sql`
- DDL 与 DML 分文件
- 迁移文件**不可**修改，只可新增（按 Flyway 规范）

## 9. 字符集与排序

- 数据库字符集：UTF8
- 排序规则：`zh_CN.UTF-8`（按需）
- 时区：所有时间戳存储为 UTC，应用层按用户时区展示

## 10. 备份与恢复

- 每日凌晨全量备份
- 每小时增量备份
- 备份保留周期：≥ 30 天
- 异地容灾存储
- 恢复演练：每季度一次
