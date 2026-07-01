# PMIS 权限矩阵（role-matrix.md）

> 版本：v1.0（批次 19）  
> 用途：RBAC 权限模型 + 9 大业务模块的读 / 写 / 审批 / 删除 4 级控制  
> 关联：`PermissionCodes` 常量 / `pmis_role` / `pmis_role_permission` 表

---

## 0. 角色定义（8 角色）

| 角色代码 | 角色名称 | 默认权限范围 | 备注 |
|----------|----------|--------------|------|
| `super_admin` | 超级管理员 | `*.*.*`（全部） | 系统初始化自动创建 |
| `gm` | 总经理 | 全部（只读 + 关键审批） | 重大变更双审批 |
| `cfo` | 财务总监 | 财务模块 + 收款审批 | 重大变更双审批 |
| `pmo` | PMO 经理 | 项目全模块 + 跨部门数据 | 变更审批权 |
| `pm` | 项目经理 | 本部门项目（DataScope=DEPT） | 立项/合同创建权 |
| `sales` | 销售 | 商机 + 客户 + 立项提交 | 不可审批 |
| `finance` | 财务专员 | 财务模块 | 不可审批 |
| `employee` | 普通员工 | 工时 + 个人数据 | 仅看自己 |

---

## 1. 9 大模块 × 4 级动作矩阵

### 1.1 项目立项（initiation）
| 角色 | VIEW | CREATE | UPDATE | APPROVE | DELETE |
|------|------|--------|--------|---------|--------|
| super_admin | ✅ | ✅ | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ✅ | ✅ | ⚠️ 二次确认 |
| cfo | ✅ | ❌ | ❌ | ❌ | ❌ |
| pmo | ✅ | ✅ | ✅ | ✅ | ⚠️ 二次确认 |
| pm | ✅（本部门） | ✅ | ✅（本部门） | ❌ | ❌ |
| sales | ✅（本部门） | ✅ | ✅（本部门） | ❌ | ❌ |
| finance | ✅ | ❌ | ❌ | ❌ | ❌ |
| employee | ❌ | ❌ | ❌ | ❌ | ❌ |

权限码：`project:initiation:view / create / update / approve / delete`

### 1.2 商机（opportunity）
| 角色 | VIEW | CREATE | UPDATE | CONVERT | DELETE |
|------|------|--------|--------|---------|--------|
| super_admin | ✅ | ✅ | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| cfo | ✅ | ❌ | ❌ | ❌ | ❌ |
| pmo | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| pm | ✅（本部门） | ✅ | ✅ | ✅ | ❌ |
| sales | ✅（本部门） | ✅ | ✅ | ✅ | ✅（仅 DRAFT） |
| finance | ✅ | ❌ | ❌ | ❌ | ❌ |
| employee | ❌ | ❌ | ❌ | ❌ | ❌ |

权限码：`project:opportunity:view / create / update / convert / delete`

### 1.3 合同（contract）
| 角色 | VIEW | CREATE | UPDATE | APPROVE | DELETE |
|------|------|--------|--------|---------|--------|
| super_admin | ✅ | ✅ | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| cfo | ✅ | ❌ | ✅（财务字段） | ✅ | ❌ |
| pmo | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| pm | ✅（本部门） | ✅ | ✅（本部门） | ❌ | ❌ |
| sales | ✅（本部门） | ✅ | ✅（本部门） | ❌ | ❌ |
| finance | ✅ | ✅（财务字段） | ✅（财务字段） | ✅ | ❌ |
| employee | ❌ | ❌ | ❌ | ❌ | ❌ |

权限码：`project:contract:view / create / update / approve / delete`

### 1.4 合同模板（contract-template）
| 角色 | VIEW | CREATE | UPDATE | PUBLISH | DEPRECATE |
|------|------|--------|--------|---------|-----------|
| super_admin | ✅ | ✅ | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ✅ | ✅ | ✅ |
| cfo | ✅ | ❌ | ❌ | ❌ | ❌ |
| pmo | ✅ | ✅ | ✅ | ✅ | ✅ |
| pm | ✅ | ⚠️ 仅 DRAFT | ⚠️ 仅 DRAFT | ❌ | ❌ |
| sales | ✅ | ⚠️ 仅 DRAFT | ⚠️ 仅 DRAFT | ❌ | ❌ |
| finance | ✅ | ❌ | ❌ | ❌ | ❌ |
| employee | ✅ | ❌ | ❌ | ❌ | ❌ |

权限码：`project:contract-template:view / create / update / publish / deprecate`

### 1.5 项目变更（change）
| 角色 | VIEW | CREATE | UPDATE | STATUS | DELETE |
|------|------|--------|--------|--------|--------|
| super_admin | ✅ | ✅ | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| cfo | ✅ | ❌ | ❌ | ✅（重大变更） | ❌ |
| pmo | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| pm | ✅（本部门） | ✅ | ✅（DRAFT） | ⚠️ SUBMITTED | ❌ |
| sales | ✅（本部门） | ✅ | ✅（DRAFT） | ❌ | ❌ |
| finance | ✅ | ❌ | ❌ | ❌ | ❌ |
| employee | ❌ | ❌ | ❌ | ❌ | ❌ |

权限码：`project:change:view / create / update / status / delete`

### 1.6 项目交付 / 结项（closure / delivery）
| 角色 | VIEW | CREATE | UPDATE | APPROVE |
|------|------|--------|--------|---------|
| super_admin | ✅ | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ✅ | ✅ |
| cfo | ✅（结项） | ❌ | ❌ | ✅（结项财务部分） |
| pmo | ✅ | ✅ | ✅ | ✅ |
| pm | ✅（本部门） | ✅ | ✅ | ❌ |
| sales | ✅ | ❌ | ❌ | ❌ |
| finance | ✅（结项） | ❌ | ❌ | ✅（结项财务） |
| employee | ❌ | ❌ | ❌ | ❌ |

权限码：`project:closure:view / create / approve`

### 1.7 工时 / 成本（time-entry / cost）
| 角色 | VIEW（自己） | VIEW（他人） | CREATE | APPROVE |
|------|-------------|-------------|--------|----------|
| super_admin | ✅ | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ❌ | ✅ |
| cfo | ✅ | ✅ | ❌ | ✅ |
| pmo | ✅ | ✅ | ❌ | ✅ |
| pm | ✅ | ✅（本部门） | ✅ | ✅（本部门） |
| sales | ✅ | ❌ | ✅ | ❌ |
| finance | ✅ | ✅ | ❌ | ❌ |
| employee | ✅ | ❌ | ✅ | ❌ |

权限码：`execution:time-entry:view / create / approve`

### 1.8 财务对账（invoice / payment / customer-credit）
| 角色 | VIEW | CREATE | UPDATE | APPROVE | WRITE_OFF |
|------|------|--------|--------|---------|-----------|
| super_admin | ✅ | ✅ | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ✅ | ✅ | ✅ |
| cfo | ✅ | ✅ | ✅ | ✅ | ✅ |
| pmo | ✅ | ❌ | ❌ | ❌ | ❌ |
| pm | ✅（本部门合同） | ❌ | ❌ | ❌ | ❌ |
| sales | ✅（本部门合同） | ❌ | ❌ | ❌ | ❌ |
| finance | ✅ | ✅ | ✅ | ⚠️ 仅 RECEIVED | ❌ |
| employee | ❌ | ❌ | ❌ | ❌ | ❌ |

权限码：`finance:invoice:view / create / approve / red-reverse / write-off`

### 1.9 售后（aftersales / warranty / ops-ticket）
| 角色 | WARRANTY | OPS_TICKET | SATISFACTION |
|------|----------|------------|--------------|
| super_admin | ✅ 全部 | ✅ 全部 | ✅ 全部 |
| gm | ✅ 全部 | ✅ 全部 | ✅ 全部 |
| cfo | ⚠️ 仅财务字段 | ❌ | ❌ |
| pmo | ✅ 全部 | ✅ 全部 | ✅ 全部 |
| pm | ✅（本部门） | ✅（本部门） | ❌ |
| sales | ✅ | ✅ | ❌ |
| finance | ⚠️ 仅财务 | ❌ | ❌ |
| employee | ❌ | ✅（自己创建的） | ❌ |

权限码：
- `aftersales:warranty:view / create / update / delete`
- `aftersales:ops-ticket:view / create / update / close / delete`
- `aftersales:satisfaction:view / submit`

### 1.10 报表 / 驾驶舱
| 角色 | BASIC | COCKPIT | ADVANCED | EXPORT |
|------|-------|---------|----------|--------|
| super_admin | ✅ | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ✅ | ✅ |
| cfo | ✅ | ✅（财务维度） | ✅ | ✅ |
| pmo | ✅ | ✅ | ✅ | ✅ |
| pm | ✅（本部门） | ✅（本部门） | ✅（本部门） | ✅ |
| sales | ✅ | ✅（销售维度） | ⚠️ | ⚠️ |
| finance | ✅ | ✅（财务维度） | ✅ | ✅ |
| employee | ❌ | ❌ | ❌ | ❌ |

权限码：`report:basic / cockpit / advanced / export`

### 1.11 AI Agent
| 角色 | RUN | VIEW_HISTORY | PREDICTION_VIEW |
|------|-----|--------------|-----------------|
| super_admin | ✅ | ✅ | ✅ |
| gm | ✅ | ✅ | ✅ |
| cfo | ✅ | ✅ | ✅（财务 Agent） |
| pmo | ✅ | ✅ | ✅ |
| pm | ✅（本项目） | ✅（本项目） | ✅（本项目） |
| sales | ✅（商机） | ✅（商机） | ✅（商机） |
| finance | ✅（财务） | ✅（财务） | ✅（财务） |
| employee | ❌ | ❌ | ❌ |

权限码：
- `agent:run`
- `agent:orchestration:run / view`
- `agent:prediction:view`
- `agent:history`

---

## 2. 权限码总册（PermissionCodes 完整定义）

> 前端 `src/constants/permissionCodes.ts` / 后端 `PermissionCodes` 枚举
> 命名规范：`<module>:<resource>:<action>`

| 权限码 | 描述 | 涉及角色 |
|--------|------|----------|
| `*.*.*` | 超级管理员通配 | super_admin |
| `project:opportunity:view` | 商机查看 | PMO+ |
| `project:opportunity:create` | 商机创建 | PMO, PM, sales |
| `project:opportunity:update` | 商机更新 | PMO, PM, sales |
| `project:opportunity:convert` | 商机转立项 | PMO, PM, sales |
| `project:opportunity:delete` | 商机删除 | PMO+ |
| `project:opportunity:evaluate-winrate` | 赢率评估 | PMO, PM, sales |
| `project:initiation:view` | 立项查看 | PMO+ |
| `project:initiation:create` | 立项创建 | PMO, PM |
| `project:initiation:update` | 立项更新 | PMO, PM |
| `project:initiation:approve` | 立项审批 | GM, PMO |
| `project:initiation:delete` | 立项删除 | PMO+ |
| `project:contract:view` | 合同查看 | PMO+ |
| `project:contract:create` | 合同创建 | PMO, PM, sales, finance |
| `project:contract:update` | 合同更新 | PMO, PM, sales, finance |
| `project:contract:approve` | 合同审批 | GM, CFO, PMO |
| `project:contract:delete` | 合同删除 | PMO+ |
| `project:contract-template:view` | 模板查看 | ALL |
| `project:contract-template:create` | 模板创建 | PMO+ |
| `project:contract-template:update` | 模板更新 | PMO+, PM, sales |
| `project:contract-template:publish` | 模板发布 | PMO+ |
| `project:contract-template:deprecate` | 模板废弃 | PMO+ |
| `project:change:view` | 变更查看 | PMO+ |
| `project:change:create` | 变更创建 | PMO, PM, sales |
| `project:change:update` | 变更更新 | PMO, PM, sales |
| `project:change:status` | 变更状态 | PMO+, CFO（重大） |
| `project:change:delete` | 变更删除 | PMO+ |
| `project:delivery:view` | 交付查看 | PMO+ |
| `project:delivery:create` | 交付创建 | PMO, PM |
| `project:delivery:approve` | 交付审批 | GM, PMO |
| `project:closure:view` | 结项查看 | PMO+ |
| `project:closure:create` | 结项创建 | PMO, PM |
| `project:closure:approve` | 结项审批 | GM, PMO |
| `execution:time-entry:view` | 工时查看 | PMO+ |
| `execution:time-entry:create` | 工时填报 | ALL |
| `execution:time-entry:approve` | 工时审批 | PM, PMO |
| `execution:utilization:view` | 利用率查看 | PMO+ |
| `execution:utilization:recompute` | 利用率重算 | PMO+ |
| `execution:delivery:view` | 交付查看 | PMO+ |
| `execution:closure:view` | 结项查看 | PMO+ |
| `execution:alert:view` | 预警查看 | PMO+ |
| `execution:reconcile:view` | 对账查看 | CFO, finance, PMO |
| `execution:reconcile:run` | 手动对账 | CFO, finance, PMO |
| `finance:invoice:view` | 发票查看 | PMO+ |
| `finance:invoice:create` | 发票创建 | finance, CFO, PM |
| `finance:invoice:approve` | 发票审批 | CFO, finance |
| `finance:invoice:red-reverse` | 红冲 | CFO, finance |
| `finance:payment:view` | 收款查看 | PMO+ |
| `finance:payment:create` | 收款创建 | finance, CFO |
| `finance:payment:allocate` | 核销 | finance, CFO |
| `finance:payment:write-off` | 坏账核销 | CFO, GM |
| `finance:customer-credit:view` | 客户信用查看 | PMO+ |
| `finance:customer-credit:recompute` | 信用重算 | finance, CFO |
| `aftersales:warranty:view` | 质保查看 | PMO+ |
| `aftersales:warranty:create` | 质保创建 | PMO, PM |
| `aftersales:ops-ticket:view` | 工单查看 | PMO+ |
| `aftersales:ops-ticket:create` | 工单创建 | ALL |
| `aftersales:ops-ticket:close` | 工单关闭 | PMO, PM |
| `aftersales:ops-ticket:assign` | 工单分配 | PMO |
| `aftersales:satisfaction:view` | 满意度查看 | PMO+ |
| `aftersales:satisfaction:submit` | 满意度提交 | ALL |
| `report:basic:view` | 基础报表 | PMO+ |
| `report:basic:export` | 基础报表导出 | PMO+ |
| `report:cockpit:view` | 驾驶舱 | PMO+ |
| `report:cockpit:drill-down` | 驾驶舱下钻 | PMO+ |
| `report:advanced:view` | 高级报表 | PMO+ |
| `agent:run` | AI Agent 运行 | PMO+ |
| `agent:orchestration:run` | 编排运行 | PMO+ |
| `agent:orchestration:view` | 编排查看 | PMO+ |
| `agent:prediction:view` | 预测查看 | PMO+ |
| `agent:history` | Agent 历史 | PMO+ |
| `system:user:view` | 用户查看 | PMO+ |
| `system:user:create` | 用户创建 | PMO+ |
| `system:user:assign-role` | 分配角色 | PMO+ |
| `system:role:view` | 角色查看 | PMO+ |
| `system:role:create` | 角色创建 | PMO+ |
| `system:permission:view` | 权限查看 | PMO+ |
| `system:audit:view` | 审计查看 | PMO+ |
| `system:notification:view` | 通知查看 | ALL |

## 3. 数据范围（DataScope）

| 值 | 含义 | 适用角色 |
|----|------|----------|
| `*` | 全部 | super_admin |
| `DEPT` | 本部门 | PM, sales |
| `DEPT_AND_SUB` | 本部门 + 下级部门 | 部门负责人 |
| `SELF` | 仅自己 | employee |
| `CUSTOM` | 自定义（按部门列表） | GM, CFO, PMO |

## 4. 角色继承

```
super_admin
    └── gm
            └── pmo
                    ├── pm
                    │       └── employee
                    ├── sales
                    └── finance
```

- `pmo` 继承 `pm` 的部分权限
- `pm` 继承 `employee` 的部分权限
- `sales` 仅继承 `employee` 的工时权限

## 5. 权限变更审计

- 任何权限变更记录到 `pmis_operation_log`
- 必填字段：操作人 / 目标用户 / 变更前权限 / 变更后权限 / 变更时间 / 原因
- 二次确认（GM 审批后生效）

## 6. 权限测试

参见 `mask-verify.sh`（脱敏验证） + `permission-test.sh`（权限矩阵验证）。
