# ADR-003：禁止 Flyway/Liquibase

**状态：** 接受
**日期：** 2025-02-01
**决策者：** ydsz-team

## 背景与问题陈述

ydsz-cloud 数据库以 MSSQL 为主，支持 MySQL 作为可选方言。多租户场景下需频繁执行 DDL 变更（新增租户 Schema、表结构演进）。历史上曾评估 Flyway/Liquibase 等数据库迁移工具，但发现其自动化 DDL 与 DBA 审核流程、多租户 Schema 管理、MyBatis-Plus `@TableField` 映射优先原则存在根本性冲突，需明确禁止其使用。

## 决策驱动因素

* 多租户 MSSQL 迁移场景下每个租户需独立执行 Schema 变更，Flyway/Liquibase 的单一 changelog 模型不直接适用
* DBA 部门要求所有 DDL 必须经过线上审核通道，不允许应用启动时自动执行
* MyBatis-Plus `@TableField` / XML 映射优先于实体类注解，手动 SQL 可精确控制映射关系
* 数据库版本变更涉及锁表、回滚预案、数据迁移等复杂手工步骤，自动化工具覆盖不足
* 编码规范 YDIZ-DB-001 明确要求数据库迁移脚本纳入 DBA 工单系统

## 考虑的选项

* **Flyway：** 社区成熟，SQL-based 脚本，但 changelog 版本链在多租户场景下管理复杂
* **Liquibase：** XML/JSON/YAML 抽象层，跨数据库兼容，但抽象 DDL 可能生成不兼容 T-SQL 的语句
* **MyBatis-Plus SchemaHelper：** 实体注解自动生成 DDL，但无法生成复杂索引、约束、分区方案
* **手工 SQL 脚本 + DBA 审核：** 传统流程，完全可控但缺乏 CI 自动追踪

## 决策结果

选择 **手工 SQL 脚本 + DBA 审核流程**，因为多租户 MSSQL 场景下 DDL 变更必须经过 DBA 审核通道，且 MyBatis-Plus 映射优先原则要求 DDL 与实体映射解耦，Flyway/Liquibase 无法在应用启动时自动执行 DDL 变更。

### 正面后果

* DDL 变更完全可控，满足 DBA 部门审核合规要求
* SQL 脚本可充分利用 MSSQL 特有语法（分区方案、全文索引、列存储索引）
* 与 MyBatis-Plus `@TableField` 映射解耦，DDL 优先保证查询性能
* 回滚预案可随脚本一同提交审核，安全可控

### 负面后果

* 无 CI 自动追踪 Schema 版本，需人工维护 `docs/sql/` 目录中的脚本清单
* 多租户场景下 DDL 变更需脚本化处理（遍历所有 Schema），增加脚本复杂度
* 开发环境与生产环境 Schema 一致性依赖团队纪律，缺乏自动化检测
* 新成员上手需要了解 MSSQL 方言特性，学习成本上升

## 各选项详细对比

### Flyway

* **优点：** SQL-based 简单直观，社区插件丰富，Spring Boot 自动集成
* **缺点：** changelog 版本链在多租户场景下需为每个 Schema 维护独立 history 表；DBA 审核流程无法嵌入启动自动执行
* **评价：** 单库场景优选，多租户 MSSQL 场景下管理成本过高

### Liquibase

* **优点：** XML/JSON/YAML 抽象层，跨数据库兼容性好
* **缺点：** 抽象 DDL 生成的 T-SQL 可能非最优（如索引包含列顺序、分区函数引用）；与 `@TableField` 映射解耦存在两层抽象偏差
* **评价：** 跨数据库迁移场景适配，ydsz-cloud 以 MSSQL 为主无跨库需求

### MyBatis-Plus SchemaHelper

* **优点：** 实体注解自动生成 DDL，零脚本维护
* **缺点：** 无法处理复杂索引、约束、分区方案；生成的 DDL 过度依赖默认值，生产环境不适用；与 XML 映射存在竞态
* **评价：** 仅适合原型开发阶段，生产环境禁止

### 手工 SQL 脚本 + DBA 审核

* **优点：** 完全可控，DBA 专业审核保证生产安全，充分利用 MSSQL 特性
* **缺点：** 无自动化追踪，依赖人工纪律
* **评价：** 符合团队现状和 DBA 流程，综合可接受

## 合规性对照

- [云顶编码规范 YDIZ-DB-001] — 数据库版本变更禁止通过代码 ORM 映射完成，采用手工 SQL 脚本 + DBA 审核
- [云顶编码规范 YDIZ-DB-002] — 多租户场景 DDL 变更需脚本化，支持批量 Schema 执行
- [云顶编码规范 YDIZ-MT-003] — 数据库 Schema 隔离策略与 ADR-005 一致

## 参考

* [ADR-005](./ADR-005-multi-tenancy.md) — 多租户隔离策略（Schema 隔离场景）
* [云顶编码规范 - 数据库章节]()
* `D:\Code\open\ydsz-cloud\docs\sql/` — 数据库脚本目录
