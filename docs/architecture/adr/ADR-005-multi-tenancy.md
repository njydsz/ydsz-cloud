# ADR-005：多租户隔离策略

**状态：** 待决策
**日期：** 2025-02-15
**决策者：** ydsz-team

## 背景与问题陈述

ydsz-cloud 作为中台型 SaaS 系统，多租户隔离是核心架构关注点。当前项目已通过 `ydsz-common-tenant` 实现了独立 Schema（Schema-per-Tenant）的上下文切换机制。但随着租户数量增长和性能数据采集推进，需要对比独立 Schema、行级隔离（Row-Level Security）、分库三种策略的最终适合方案。本 ADR 记录现状与待验证的决策点。

## 决策驱动因素

* 数据安全性要求：租户数据必须物理或逻辑隔离，不允许交叉读取
* 运维复杂度：DBA 审核流程下 Schema 管理成本与租户数量成正比
* 性能表现：行级隔离依赖数据库引擎 RLE/RLS 特性，分库需引入 ShardingSphere
* TenantContext 切换机制已存在于 `ydsz-common-tenant`，改造范围需评估

## 考虑的选项

* **独立 Schema（Schema-per-Tenant，当前方案）：** 每个租户绑定一个 MSSQL Schema，通过 TenantContext 动态切换连接字符串中的 `currentSchema`
* **行级隔离（Row-Level Security）：** 所有租户共享同一 Schema，通过 MCP SQL Server RLS 或 MyBatis-Plus 多租户拦截器自动注入 `tenant_id` 过滤条件
* **分库（Database-per-Tenant + 分库中间件）：** 每个租户绑定独立数据库实例，通过 ShardingSphere 或 DynamicRoutingDataSource 路由

## 决策结果

选择 **[待决策]**。当前以独立 Schema（Schema-per-Tenant）为临时方案运行，需在补充性能基准对比数据后再做最终决定。

### 正面后果（独立 Schema 现状）

* 实现简单，`ydsz-common-tenant` 已提供 TenantContext + Schema 切换
* 数据物理隔离，安全性高
* 租户级别迁移灵活（仅迁移对应 Schema）

### 负面后果（独立 Schema 现状）

* 租户数量增长后 Schema 数量爆炸，DBA 运维成本高
* 跨租户统计查询需 union all 多个 Schema，性能劣势
* 全局配置表同步到每个 Schema 时同步成本高
* 与 ADR-003 手工 SQL 脚本流程结合，管理复杂度叠加

## 各选项详细对比

### 独立 Schema（当前方案）

* **优点：** 物理隔离安全性最高；已实现无需重构；租户级迁移灵活
* **缺点：** Schema 数量随租户增长线性上升；全局配置同步跨 Schema 成本高；跨租户统计 union all 性能差
* **评价：** 适合初期租户数量 < 50 的场景，长期需重新评估

### 行级隔离

* **优点：** Schema 数量固定；跨租户统计无需 union all；RLS 在应用层可自动化拦截
* **缺点：** MSSQL RLS 版本依赖（需 SQL Server 2016+ Enterprise 或 Standard Edition 部分版本）；MyBatis-Plus 拦截器注入存在误注入风险；无法物理备份单一租户
* **评价：** 适合租户数量 > 100、数据量中等、接受逻辑隔离的场景

### 分库

* **优点：** 物理隔离 + 性能最优；支持 ShardingSphere 自动路由；水平扩展无上限
* **缺点：** ShardingSphere 引入新架构组件；跨分片查询复杂；与 DynamicRoutingDataSource、Seata AT 兼容性需深度测试；编码规范 YDIZ-MT-003 需扩展
* **评价：** 适合租户数量 > 1000 或单租户数据量超亿级，短期引入成本过高

## 合规性对照

- [云顶编码规范 YDIZ-MT-001] — 租户上下文传递机制，TenantContext 必须透传全链路
- [云顶编码规范 YDIZ-MT-002] — 多租户场景下禁止跨租户数据访问，拦截器必须生效
- [云顶编码规范 YDIZ-MT-003] — 租户 Schema 隔离策略（待本决策定稿后更新规范）
- [云顶编码规范 YDIZ-DB-002] — 与 ADR-003 多租户 DDL 脚本化管理联动

## 参考

* [ADR-003](./ADR-003-no-db-migration-tool.md) — 数据库迁移策略（Schema 数量影响脚本管理复杂度）
* [ADR-004](./ADR-004-seata-mode.md) — Seata 分布式事务（分库场景下跨分片事务需 SAGA 模式）
* `ydsz-common-tenant/` — 当前多租户模块源码
