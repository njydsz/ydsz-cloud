# 数据库版本脚本（人工按序执行，无迁移框架）

> **事实校正（2026-10-28）**：本文件此前写"本目录脚本由 Flyway 自动执行"，并给出
> `spring.flyway.*` 配置示例。核对代码后三处都与事实不符：
>
> 1. `pom.xml:61` 与 `pom.xml:1163` 明文规定"**项目规范禁止 Flyway / Liquibase**，不引入任何
>    schema-migration 框架"；
> 2. 全仓 pom 里**没有任何 flyway 依赖**（`grep -r flyway --include=pom.xml` 仅命中上面两条注释）；
> 3. 各业务模块的 `application.yml` 也没有 `spring.flyway` 配置。
>
> 结论：这些脚本**从来没有被自动执行过**。本文件按事实重写，避免"以为有迁移框架兜底"的误解。

## 执行方式

1. 变更数据库前，在本目录新建 `V{版本号}.sql`（见下方命名规范）；
2. 由 DBA / 发布负责人按**版本号升序人工执行**，执行记录写在发布单/变更单里；
3. 脚本一旦执行**不再修改**；需要撤销时新建更高版本的修正脚本；
4. 版本号必须递增，不可重复（重复会使"按序执行"失去唯一锚点）。

## 命名规范

- 格式：`V{版本号}.sql`，或带描述 `V{版本号}_{描述}.sql`
- 版本号格式：`YY.MM.DD`（与项目版本号一致）
- 示例：`V26.11.01_add_user_avatar.sql`

## 目录职责划分（2026-11-03 起）

| 目录 | 用途 | 是否允许 DROP |
|------|------|--------------|
| `data/sqls/bootstrap/` | **空库初始化脚本**（全量快照），仅用于全新空库首次建表 | 允许（文件头有警告） |
| `data/sqls/flyway/` | **增量变更脚本**，按版本号升序人工执行 | **禁止**（由门禁判红） |
| `data/sqls/postgre/` | 按模块拆分的表结构参考（无 DROP，不含数据） | — |

- **初始化脚本**：`data/sqls/bootstrap/V26.10.01.bootstrap.sql`
  —— 含 173 组 `DROP TABLE ... CASCADE`，**禁止在任何已有数据的库上执行**。
- **增量变更**：在 `data/sqls/flyway/` 新建 `V{YY.MM.DD}.sql`。

## 危险变更门禁

`data/scripts/check-sql-migrations.py` 会扫描 `data/sqls/flyway/` 下全部 `V*.sql`，
对以下语句判红（CI 强制）：

- `DROP TABLE` / `DROP SCHEMA` / `DROP DATABASE` / `DROP EXTENSION`
- 无备份的 `DROP COLUMN`
- `TRUNCATE`
- 无 `WHERE` 子句的 `DELETE` / `UPDATE`
- 无替代索引的 `DROP INDEX CONCURRENTLY`

**例外白名单**：

- 被 `--` 注释掉的语句不算；
- `bootstrap/` 目录不参与扫描；
- **在线索引替换**：同一脚本内先 `CREATE INDEX CONCURRENTLY` 新索引，再 `DROP INDEX CONCURRENTLY` 旧索引，且新建数量 ≥ 删除数量时允许，避免索引空窗。

## 历史脚本治理策略

- **V26.10.02.sql**：已采用 expand/contract 模式（新增 `life_cycle` 列 → 迁移数据 → 标记旧 `status` 列 deprecated），无直接删除，保留为历史记录；
- **V26.10.03/V26.10.04/V26.10.07 等**：仅包含 `ALTER COLUMN TYPE` 等在线 DDL，不触发表重写，可保留；
- **V26.11.03.sql**：在线索引重命名，采用“先建后删”模式，门禁允许。

新增脚本必须遵循 expand/contract：先新增字段/表，迁移数据，再标记废弃，最后在新版本中删除。

## 目录名的历史包袱

目录仍叫 `flyway/`，但其中并不使用 Flyway —— 属历史命名。改名会影响发布脚本、备份工具与
既有文档的引用路径，**未擅自重命名**；如需改名，建议连同 `pom.xml` 的两条红线注释一并调整。

## 与红线的关系

- **禁止引入** Flyway / Liquibase（`pom.xml:61`、`pom.xml:1163`）；
- 方言差异（PostgreSQL 生产 / MySQL、Oracle 历史脚本）见 `data/sqls/legacy-dialects/`；
- 需要"脚本是否齐全、方言是否同步"的自动校验，属报告 B1-10（DDL 治理脚本，需 DBA 复核）。
