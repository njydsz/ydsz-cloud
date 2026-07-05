# PMIS · 数据库 Schema 变更与版本化交付

> 本目录是 PMIS 主库 schema 的**唯一事实源(Single Source of Truth)**
> 本文回答三个问题:用什么管 schema、怎么发版、怎么在新环境初始化
> 文档版本:v1.0 · 2026-07-05(显式排除 Flyway 决策落地)

---

## 目录

1. [核心决策:不使用 Flyway / Liquibase](#1-核心决策不使用-flyway--liquibase)
2. [为什么不用](#2-为什么不用)
3. [当前的 Schema 管理方式](#3-当前的-schema-管理方式)
4. [文件命名与目录约定](#4-文件命名与目录约定)
5. [新环境初始化流程](#5-新环境初始化流程)
6. [存量环境升级流程](#6-存量环境升级流程)
7. [回滚策略](#7-回滚策略)
8. [禁止事项清单](#8-禁止事项清单)
9. [常见问题(FAQ)](#9-常见问题faq)

---

## 1. 核心决策:不使用 Flyway / Liquibase

**PMIS 项目不引入任何自动化的 schema migration 框架**(包括但不限于):

- ❌ Flyway
- ❌ Liquibase
- ❌ jOOQ DDL
- ❌ 自研 `db-migration` 启动器

理由详见 [§2 为什么不用](#2-为什么不用)。

任何 PR 不得新增以下内容:

| 禁止项 | 位置 |
|---|---|
| `org.flywaydb:flyway-core` / `flyway-mysql` / `flyway-postgresql` | 任何 `pom.xml` |
| `org.liquibase:liquibase-core` | 任何 `pom.xml` |
| `spring.flyway.*` / `spring.liquibase.*` 配置 | `application*.yml` / Nacos `ydsz-pmis-common.yaml` |
| `db/migration/` / `src/main/resources/db/` 目录 | 任何业务模块 |
| 启动类里 `@PostConstruct` 里跑 DDL | 任何 `*Application.java` |

CI 会通过 `mvn dependency:tree \| grep -iE 'flyway\|liquibase'` 做静态扫描拦截。

---

## 2. 为什么不用

| 维度 | Flyway / Liquibase 自动迁移 | 当前方案(版本化 SQL + psql 手动执行) |
|---|---|---|
| **启动时耦合** | 应用启动 → 自动执行 DDL → 启动时长 + 网络抖动风险 | 应用启动 → 仅做 SQL 校验 → 启动快 |
| **多实例并发** | 第一个实例抢锁,其它阻塞,容易触发 lock wait timeout | 升级窗口先停服或切只读,确定性更强 |
| **变更可审计** | 写入 `flyway_schema_history` / `DATABASECHANGELOG`,但和 PR 脱钩 | 每次升级 = 一次 `git commit` 推送 V*N*.sql,可 PR Review |
| **回滚粒度** | Flyway 需要 `U*.sql` 配套,Liquibase 可 forward-roll back | 一份回滚 SQL 与正向 SQL 配对提交,人工执行 |
| **跨库兼容** | 框架替你生成 PG/MySQL 方言差异,但 PMIS 只用 PG | 单库,SQL 直写,无歧义 |
| **Schema 漂移检测** | 框架 hash 校验当前 DB schema vs 期望 | 通过 `pmis_schema_snapshot` 视图 + 人工 review |
| **CI 集成复杂度** | 需要 testcontainer 起 PG + 跑完整迁移链 | 直接对 snapshot 做 diff,无需起容器 |
| **故障爆炸半径** | 一个错误的 V*N*.sql 升级 → 全集群自动执行 → 业务雪崩 | 人工执行前在 SIT 演练,出问题立即 `psql` 退出 |

**结论**:PMIS 处于 **28 批次 + 强可控** 阶段,DB 升级频率 ≈ 每 2-4 周一次,体量小、可人工编排,自动迁移框架带来的"启动耦合 + 并发风险 + 漂移检测负担"大于收益。

如果未来达到 **月 4 次以上 DB 升级 + 多团队并行改表** 的规模,再评估引入 Flyway(届时本 README 同步更新)。

---

## 3. 当前的 Schema 管理方式

### 3.1 交付物

```
deploy/sql/
├── V1.0.0.sql              # 单文件合并版(给"新环境一次性初始化"用)
├── V1.0.1__add_xxx.sql     # 增量升级(从 V1.0.0 升到 V1.0.1)
├── V1.0.2__fix_yyy.sql     # 增量升级(从 V1.0.1 升到 V1.0.2)
└── README.md               # 本文件
```

> **注意**:`V1.0.0.sql` **不等于** "V1.0.0 版本的 schema"。它是"截至 V1.0.0 时刻全部 58 个 V1.0.0_NNN 文件按顺序合并"的单文件等价产物,**专给全新环境一键初始化**。
> 存量环境必须**按顺序跑增量 V1.0.1、V1.0.2…**,不能用 V1.0.0.sql 覆盖。

### 3.2 单一事实源

| 角色 | 读取路径 | 用途 |
|---|---|---|
| 新环境部署 | `deploy/sql/V1.0.0.sql` | `psql -f V1.0.0.sql` 一次性建库 |
| 存量环境升级 | `deploy/sql/V*__*.sql`(按版本号顺序) | DBA 维护窗口执行 |
| CI Schema Diff | `deploy/sql/` 全量文件 | 与测试库 `pg_dump --schema-only` 做 diff |
| Code Review | PR 中 `deploy/sql/V*.sql` | DBA + 后端联合 review |

### 3.3 校验机制(无 Flyway 时的漂移检测)

通过 PostgreSQL 自带能力 + 项目自建快照:

```sql
-- 1. 当前 schema 的 fingerprint(部署到任何环境都跑一次)
SELECT pg_catalog.pg_get_userbyid(relowner) AS owner,
       relname AS table_name,
       pg_catalog.pg_total_relation_size(oid) AS size
  FROM pg_catalog.pg_class
 WHERE relkind = 'r' AND relnamespace = 'public'::regnamespace
 ORDER BY relname;

-- 2. 项目计划加视图 pmis_schema_snapshot(批次 29 评估中)
--    用于在 CI 中跑 SELECT * FROM pmis_schema_snapshot
--    与 deploy/sql/ 解析出的期望 schema 做 diff
```

---

## 4. 文件命名与目录约定

### 4.1 命名规范

```
V{主版本}.{次版本}.{修订版本}__{语义化描述}.sql
```

- **版本号必须单调递增**,不允许回退、覆盖
- **双下划线 `__`** 分隔版本与描述(Liquibase 风格,仅用于命名,不代表用了 Liquibase)
- 描述用英文 snake_case,3-5 个单词,如 `add_employee_tag_table` / `fix_invoice_status_index`
- 必须 **commit 一个 PR**,不允许 hot-fix 跳过 review

### 4.2 文件模板

```sql
-- ====================================================================
-- V1.0.1__add_employee_tag_table.sql
-- --------------------------------------------------------------------
-- 目的:新增员工标签表(批次 28 评估)
-- 兼容性: PostgreSQL 18+,与 V1.0.0 之后所有 schema 兼容
- 回滚: DROP TABLE IF EXISTS pmis_employee_tag;  -- 见 V1.0.1__rollback.sql
-- 影响行数: ~500 (DDL)
-- 维护窗口: < 5s
-- ====================================================================

BEGIN;

-- DDL ...
CREATE TABLE IF NOT EXISTS pmis_employee_tag (
    id          BIGSERIAL PRIMARY KEY,
    ...
);

-- 元数据
COMMENT ON TABLE pmis_employee_tag IS '员工标签表';

-- 数据修复(若有)
-- INSERT INTO pmis_employee_tag (...) SELECT ... FROM ...;

COMMIT;
```

### 4.3 必须包含的注释块

每个 V 文件顶部必须写清 **目的 / 兼容性 / 回滚 / 影响行数 / 维护窗口**,便于运维 + DBA review。

---

## 5. 新环境初始化流程

```bash
# 1. 创建库
PGPASSWORD=Limw1020 createdb -h 127.0.0.1 -U postgres ydsz-pmis

# 2. 单文件初始化(等价于按顺序跑 V1.0.0_001..V1.0.0_059)
PGPASSWORD=Limw1020 psql -h 127.0.0.1 -U postgres -d ydsz-pmis \
  -v ON_ERROR_STOP=1 \
  -f deploy/sql/V1.0.0.sql

# 3. 导入 Nacos 配置
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# 4. 启动 PMIS 7 个服务
./deploy/ubuntu/scripts/start-all.sh
```

详细中间件部署见 [`../README.md §3`](../README.md#3-一键快速开始3-步)。

---

## 6. 存量环境升级流程

```bash
# 1. 停服 + 切只读(应用层 + DB 层)
./deploy/ubuntu/scripts/stop-all.sh
psql -c "ALTER SYSTEM SET default_transaction_read_only = on; SELECT pg_reload_conf();"

# 2. 备份(强制)
PGPASSWORD=Limw1020 pg_dump -h 127.0.0.1 -U postgres -d ydsz-pmis \
  --schema-only --no-owner > backup_$(date +%Y%m%d_%H%M%S)_schema.sql
PGPASSWORD=Limw1020 pg_dump -h 127.0.0.1 -U postgres -d ydsz-pmis \
  --data-only --no-owner > backup_$(date +%Y%m%d_%H%M%S)_data.sql

# 3. 顺序执行增量(从当前版本到目标版本)
PGPASSWORD=Limw1020 psql -h 127.0.0.1 -U postgres -d ydsz-pmis \
  -v ON_ERROR_STOP=1 \
  -f deploy/sql/V1.0.1__add_xxx.sql
PGPASSWORD=Limw1020 psql -h 127.0.0.1 -U postgres -d ydsz-pmis \
  -v ON_ERROR_STOP=1 \
  -f deploy/sql/V1.0.2__fix_yyy.sql

# 4. 校验 + 解除只读
psql -c "ALTER SYSTEM RESET default_transaction_read_only; SELECT pg_reload_conf();"

# 5. 启动服务 + 冒烟
./deploy/ubuntu/scripts/start-all.sh
./deploy/scripts/smoke-test.sh
```

---

## 7. 回滚策略

| 场景 | 策略 |
|---|---|
| **DDL 回滚** | 升级时同步提交 `V*__rollback.sql`(反向 DROP / ALTER) |
| **数据回滚** | 升级前必跑 `pg_dump --data-only`,出错时 `pg_restore` 还原 |
| **应用层不兼容** | 先回滚代码到上一个 tag,再回滚 DB,顺序不可颠倒 |
| **跨多个 V 文件** | 按**倒序**执行 rollback,一个一个回 |

```bash
# 示例:从 V1.0.2 回滚到 V1.0.0
psql -v ON_ERROR_STOP=1 -f deploy/sql/V1.0.2__rollback.sql
psql -v ON_ERROR_STOP=1 -f deploy/sql/V1.0.1__rollback.sql
# 此时 schema 与 V1.0.0 末尾一致
```

---

## 8. 禁止事项清单

| # | 禁止项 | 反例 |
|---|---|---|
| 1 | 应用代码中执行 DDL | `@PostConstruct` 里 `jdbcTemplate.execute("CREATE TABLE...")` |
| 2 | 在 `application.yml` 中配置 `spring.sql.init.*` | 用 Spring 自带 init 脚本 |
| 3 | 在 `application.yml` 中配置 `spring.flyway.*` / `spring.liquibase.*` | 启用自动迁移 |
| 4 | 在 `pom.xml` 中新增 `flyway-core` / `liquibase-core` | 任何模块 |
| 5 | 在 `src/main/resources/db/migration/` 放 SQL | 任何业务模块 |
| 6 | 不写 rollback 就合并 DDL | 升级后无法回滚 |
| 7 | 跳过 PR Review 直接 push V 文件 | DBA + 后端未确认 |
| 8 | 用 `V1.0.0.sql` 给存量环境"覆盖升级" | 覆盖会丢数据 |

---

## 9. 常见问题(FAQ)

### Q1.为什么不用 Flyway 自动迁移省事?

A:详见 [§2](#2-为什么不用)。核心是 PMIS 升级频率低、可控性强,自动迁移的"启动耦合 + 并发风险"弊大于利。

### Q2.如果以后要引入呢?

A:重新评估以下条件**全部满足**时,再开新批次讨论:
- 月均 DB 升级 ≥ 4 次
- 多个团队并行改表
- DBA 资源紧张,无法人工 review

届时把本 README 同步更新,并把 `V1.0.0.sql` 拆为 Flyway 的 `V1__init.sql` + `V1.0.1__*.sql` 系列。

### Q3.`pmis_migration_log` 表是什么?

A:**和 DB schema migration 无关**。它是 [ydsz-pmis-common::EncryptedFieldMigrationService](../ydsz-pmis-backend/ydsz-pmis-common/src/main/java/com/njydsz/pmis/common/migration/EncryptedFieldMigrationService.java) 用来跟踪"敏感字段从明文 → 密文"灰度切换的审计表,业务向的、一次性的、可清空。

### Q4.`pmis_database_change_log` 表为什么在 `PmisTenantLineHandler` 的忽略列表里?

A:**历史占位**。该类提到 `pmis_database_change_log*` 是 Liquibase 默认的 changelog 表名,代码里"兼容预留"以防万一。但 PMIS **从未启用 Liquibase**,该表在生产环境中**不存在**,此忽略项实际是 no-op。**未来若引入 Liquibase,该忽略项即可激活;不引入则保持原样。**

### Q5.CI 怎么拦截 Flyway / Liquibase 依赖?

A:`.github/workflows/backend-ci.yml` 中加入以下 step(批次 29 评估):

```yaml
- name: 静态扫描 DB Migration 依赖
  run: |
    if mvn -pl ydsz-pmis-backend -am dependency:tree | grep -iE 'flyway|liquibase'; then
      echo "::error::检测到 Flyway / Liquibase 依赖,违反项目规范"
      exit 1
    fi
```

---

## 10. 相关链接

- [`../README.md`](../README.md) — 部署总入口
- [`../common/README.md`](../common/README.md) — 中间件配置 + 通用 SQL(XXL-Job)
- [`../../ydsz-pmis-backend/Dockerfile`](../../ydsz-pmis-backend/Dockerfile) — 后端多阶段构建
- [`../../README.md`](../../README.md) — 项目仓库入口
- 项目记忆:`.trae-cn/memory/projects/-d-Code-ydsz-ydsz-pmis/project_memory.md` — Hard Constraints
