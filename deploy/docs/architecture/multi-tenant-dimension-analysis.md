# 多维度租户字段组合支持能力分析与优化建议

> **调研日期**: 2026-07-27
> **对标**: Salesforce Multi-Tenant / 阿里云 SaaS Engine / SAP MT / 钉钉租户体系

---

## 一、当前能力矩阵

### 1.1 已支持的维度

| 维度 | TenantSource 枚举 | TenantDimension 枚举 | Header 常量 | JWT 字段 | 数据库列示例 | SQL 改写 |
|---|---|---|---|---|---|---|
| 单租户 | `TENANT` | `TENANT` | `X-Tenant-Id` | `tenantId` | `tenant_id` | `WHERE tenant_id = ?` |
| 集团租户 | `GROUP` | `GROUP` | `X-Tenant-GROUP`(Feign) | 无 | `group_tenant_id` | `WHERE group_tenant_id = ?` |
| 公司租户 | `COMPANY` | `COMPANY` | `X-Tenant-COMPANY`(Feign) | 无 | `company_tenant_id` | `WHERE company_tenant_id = ?` |
| 用户租户 | `USER` | 无 | `X-Unique-Id` | `uniqueId` | `user_id` | 回退 tenantId（未真正实现） |

### 1.2 未支持的维度

| 维度 | 期望 Header | 期望数据库列 | 期望 SQL | 当前状态 |
|---|---|---|---|---|
| 部门租户 | `X-Dept-Ids` | `dept_id` | `WHERE dept_id = ?` | 枚举缺失 |
| 项目租户 | `X-Project-Ids` | `project_id` | `WHERE project_id = ?` | 枚举缺失 |
| 区域租户 | `X-Region-Ids` | `region_id` | `WHERE region_id = ?` | 枚举缺失 |
| 自定义租户 | 用户自定义 | 用户自定义 | 用户自定义 | 无扩展机制 |

### 1.3 关键断链分析

| 断链点 | 现状 | 影响 |
|---|---|---|
| **TenantDimension 枚举只有 3 个值** | TENANT/GROUP/COMPANY | 无法表达 DEPT/PROJECT/REGION/CUSTOM |
| **TenantSource 枚举只有 4 个值** | TENANT/GROUP/COMPANY/USER | 无法表达 DEPT/PROJECT/REGION/CUSTOM |
| **WebFilter 只设置 tenantId** | 不设置 dimensions | MULTI 模式下 GROUP/COMPANY 等维度值为空 → fail-closed 抛异常 |
| **WebFilter 不从 Header 恢复维度** | 只恢复 X-Tenant-Id | Feign 透传的 X-Tenant-GROUP 等维度无人消费 |
| **USER source 回退到 tenantId** | `case USER: return context.getTenantId()` | 无法按用户 ID 隔离 |
| **维度不支持 Set 类型** | dimensions 是 `Map<TenantDimension, String>` | X-Dept-Ids/X-Project-Ids 是逗号分隔多值，需 Set 支持 |
| **无自定义维度扩展机制** | 硬编码枚举 | 无法在不修改源码的情况下增加新维度 |

---

## 二、差距对比

| 能力 | Salesforce | 阿里云 SaaS | 当前 PMIS | 差距 |
|---|---|---|---|---|
| 维度数量 | 无限（元数据驱动） | 8+ 预定义 + 自定义 | 3 预定义 | P0 |
| 多值维度 | Set/IN 查询 | Set/IN 查询 | 仅单值 | P0 |
| 上下文注入 | 网关统一解析 | 网关统一解析 | WebFilter 只设 tenantId | P0 |
| 跨服务恢复 | 全维度透传 | 全维度透传 | 只透传不恢复 | P0 |
| 自定义维度 | 元数据表驱动 | 配置化 | 无 | P1 |
| per-table 维度组合 | 不同表不同维度组合 | 不同表不同维度组合 | 全局统一 | P1 |

---

## 三、优化建议

### P0 级（核心断链修复）— 4 项

#### P0-1: 扩展 TenantDimension + TenantSource 枚举

新增 `DEPARTMENT`、`PROJECT`、`REGION` 三个维度，并新增 `CUSTOM` 用于自定义维度扩展。

#### P0-2: TenantContext 支持 Set 类型多值维度

将 `Map<TenantDimension, String>` 改为 `Map<TenantDimension, Object>`，
单值为 String，多值为 `Set<String>`。SQL 改写时多值使用 `IN (...)` 而非 `= ?`。

#### P0-3: WebFilter 注入全维度上下文

WebFilter 从 JWT + Header 解析全部维度（集团/公司/部门/项目/区域），
一次性设置到 `TenantContextHolder`，而非只设置 tenantId。

#### P0-4: Feign 透传 + 下游恢复全维度

Feign 拦截器透传全部维度 header，WebFilter 从 header 恢复全部维度。

### P1 级（扩展能力）— 3 项

#### P1-1: 自定义维度扩展机制

提供 `TenantDimensionProvider` SPI 接口，允许业务模块注册自定义维度，
不修改 common-tenant 源码即可扩展新维度。

#### P1-2: per-table 维度组合配置

不同表使用不同的维度组合。例如：
- `ydsz_file` 表只需 `tenant_id`
- `ydsz_project` 表需要 `tenant_id + project_id`
- `ydsz_contract` 表需要 `tenant_id + company_id + dept_id`

#### P1-3: 数据权限联动

多维度租户与 `common-jdbc` 的 `RowPermissionInnerInterceptor` 联动，
将多维度上下文传递给数据权限拦截器，实现 `dataScope = TENANT + DEPT` 等组合查询。

### P2 级（长期演进）— 2 项

#### P2-1: 元数据驱动的维度管理

参考 Salesforce 的 Metadata API，维度定义存储在数据库表（`ydsz_tenant_dimension`），
运行时动态加载，无需重启即可新增维度。

#### P2-2: 维度组合可视化配置

提供管理界面，允许运维人员为不同表配置维度组合，
自动生成 SQL 拦截策略，无需修改 YAML 配置。
