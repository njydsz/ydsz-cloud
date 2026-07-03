# 多租户架构评估报告

## 1. 评估背景

YDSZ PMIS 当前为单租户私有化部署架构。本评估分析 SaaS 多租户化的可行性、成本和路线图。

## 2. 当前架构分析

### 单租户现状
- 所有数据共享同一 PostgreSQL 数据库（ydsz-pmis）和 schema（public）
- 76 个实体表均无 tenant_id 字段
- 认证/授权基于全局 RBAC，无租户隔离
- 配置通过 Nacos 集中管理，无租户级配置

## 3. 多租户方案对比

| 方案 | 数据隔离 | 改造成本 | 运维成本 | 适用场景 |
|------|---------|---------|---------|---------|
| **行级隔离** | tenant_id 字段过滤 | 中 | 低 | 中小租户量（<100） |
| **Schema 隔离** | 每租户独立 schema | 高 | 中 | 中等租户量（100-1000） |
| **数据库隔离** | 每租户独立数据库 | 极高 | 高 | 大租户量/合规要求 |

## 4. 推荐方案：行级隔离（渐进式）

### 阶段一：基础架构（2-3 周）
1. 核心表添加 tenant_id 字段（ALTER TABLE ... ADD COLUMN tenant_id BIGINT）
2. 引入 MyBatis-Plus 租户拦截器（TenantLineInnerInterceptor）
3. 所有查询自动追加 WHERE tenant_id = ?
4. JWT Token 中携带 tenant_id

### 阶段二：权限隔离（1-2 周）
1. RBAC 权限模型扩展为租户级
2. 超管管理所有租户，租户管理员管理本租户
3. 数据权限 DataScope 扩展租户维度

### 阶段三：配置隔离（1 周）
1. Nacos 配置支持 namespace 级租户隔离
2. 特性开关支持租户级覆盖
3. 文件存储 MinIO bucket 按租户隔离

## 5. 影响范围

### 必须修改的模块
- ydsz-pmis-common — 租户拦截器、Token 扩展
- ydsz-pmis-userinfo — 用户/角色/权限增加租户维度（含原 user + auth）
- ydsz-pmis-project — 项目/合同/商机/执行数据增加 tenant_id（含原 project + execution）
- ydsz-pmis-system — 配置/审计/通知/消息模板增加租户维度（含原 config + audit + notification + message；file 仅 bucket 隔离）
- ydsz-pmis-gateway — 路由层注入 tenant_id

### 不需要修改的模块
- ydsz-pmis-cronjob — 定时任务无租户概念

## 6. 成本估算

| 项目 | 人力 | 工时 |
|------|------|------|
| 数据库迁移脚本 | 1 人 | 5 人日 |
| MyBatis-Plus 拦截器 | 1 人 | 3 人日 |
| 权限模型扩展 | 1 人 | 5 人日 |
| 前端租户切换 | 1 人 | 3 人日 |
| 测试与验证 | 2 人 | 5 人日 |
| **合计** | | **约 21 人日** |

## 7. 结论与建议

### 当前阶段（v1.x）：保持单租户
PMIS 当前客户为单组织私有化部署，多租户改造成本约 21 人日，但短期内无 SaaS 化需求。

### 远期规划（v2.0+）：渐进式改造
若出现以下信号，启动多租户改造：
1. 客户提出多组织/多子公司管理需求
2. 产品计划 SaaS 化运营
3. 出现需要数据隔离的合规要求

### 改造前置条件
- 76 个核心表全部添加 tenant_id 字段
- 所有 MyBatis-Plus 查询走租户拦截器
- 前端支持租户切换（超管视角）
- 数据迁移脚本支持按 tenant_id 拆分历史数据
