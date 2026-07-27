# ydsz 全局 Feign 调用拓扑图

> 版本: 1.0.0 | 更新日期: 2026-07-27
>
> 本文档描述 ydsz 项目各微服务模块之间的 Feign 调用关系。

---

## 调用拓扑总览

所有业务服务均直接注册在 `gateway` 之下，不存在二级网关代理。图中红框标出的 `cronjob`、`nextwiki`、`agent`、`message` 与第一行的 `workflow`、`userinfo`、`system`、`project`、`literule` 处于同一层级，均为 gateway 的直接下游服务。

```
                         ┌──────────────┐
                         │   gateway    │
                         └──────┬───────┘
                                │
       ┌───────────┬────────────┼────────────┬───────────┐
       ▼           ▼            ▼            ▼           ▼
 ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
 │ workflow │ │ userinfo │ │  system  │ │ project  │ │ literule │
 └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘
       │                         │            │            │
       │                         │            │            │
       ▼                         ▼            ▼            ▼
 ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
 │ cronjob  │ │ nextwiki │ │  agent   │ │ message  │
 └──────────┘ └──────────┘ └──────────┘ └──────────┘
```

## 各服务 Feign 出边

> 以下依据各服务 `Application` 类上的 `@EnableYdszFeign(basePackages = ...)` 以及实际注入的 Feign Client 整理。

| 服务 | 依赖的 `*-api` | 说明 |
|------|---------------|------|
| **workflow** | `userinfo-api`、`common-feign` | 审批人解析（`OrgQueryClient`）、通知推送（`NotificationClient`） |
| **userinfo** | — | 无外部 Feign 依赖 |
| **system** | `userinfo-api`、`project-api` | 组织机构查询、立项信息查询 |
| **project** | `userinfo-api`、`system-api` | 组织机构查询、配置/应用信息查询 |
| **literule** | `cronjob-api`、`workflow-api` | 规则触发后联动定时任务、工作流审批 |
| **cronjob** | `userinfo-api`、`system-api`、`common-feign` | 组织机构查询、配置查询、通知推送（`NotificationClient`） |
| **nextwiki** | `userinfo-api` | 组织机构查询 |
| **agent** | `project-api`、`userinfo-api` | 立项信息查询、组织机构查询 |
| **message** | — | 无外部 Feign 依赖，被 workflow / cronjob 通过 `NotificationClient` 调用 |

---

## 各模块 @EnableYdszFeign 扫描包

| 模块 | 扫描的 basePackages |
|------|-------------------|
| workflow | `workflow.api`, `common.feign`, `userinfo.api` |
| userinfo | `userinfo.api`, `common.feign` |
| system | `system.api`, `common.feign`, `userinfo.api`, `project.api` |
| project | `project.api`, `common.feign`, `userinfo.api`, `system.api` |
| literule | `literule.api`, `common.feign`, `cronjob.api`, `workflow.api` |
| nextwiki | `nextwiki.api`, `common.feign`, `userinfo.api` |
| message | `message.api`, `common.feign` |
| cronjob | `cronjob.api`, `common.feign`, `userinfo.api`, `system.api` |
| agent | `agent.api`, `common.feign`, `project.api`, `userinfo.api` |

---

## Feign 客户端清单

### common-feign (`com.njydsz.common.feign`)

| 客户端 | contextId | 目标服务 | 调用方模块 |
|--------|-----------|---------|-----------|
| `NotificationClient` | notificationClient | ydsz-message | workflow, cronjob |

**核心方法**: `sendMessage`, `pushRealtime`, `broadcast`

> `MessageServiceClient` 已标记为 `@Deprecated`，P1-5 后统一使用 `NotificationClient`。

### userinfo-api (`com.njydsz.userinfo.api`)

| 客户端 | contextId | 目标服务 | 调用方模块 |
|--------|-----------|---------|-----------|
| `OrgQueryClient` | orgQueryClient | ydsz-userinfo | workflow, system, project, nextwiki, cronjob, agent |

**核心方法**: `queryUserById`, `queryUsersByDept`, `queryDeptById`, `queryDeptTree`

### system-api (`com.njydsz.system.api`)

| 客户端 | contextId | 目标服务 | 调用方模块 |
|--------|-----------|---------|-----------|
| `ConfigClient` | configClient | ydsz-system | project, cronjob |
| `AppInfoClient` | appInfoClient | ydsz-system | project |

### project-api (`com.njydsz.project.api`)

| 客户端 | contextId | 目标服务 | 调用方模块 |
|--------|-----------|---------|-----------|
| `ProjectInitiationClient` | projectInitiationClient | ydsz-project | system, agent |
| `ProjectContractClient` | projectContractClient | ydsz-project | — (预留) |
| `EvmMeasureClient` | evmMeasureClient | ydsz-project | — (预留) |
| `ExecutionTimeEntryClient` | executionTimeEntryClient | ydsz-project | — (预留) |
| `ExecutionWbsClient` | executionWbsClient | ydsz-project | — (预留) |
| `RateCardClient` | rateCardClient | ydsz-project | — (预留) |
| `FinanceClient` | financeClient | ydsz-project | — (预留) |

### workflow-api (`com.njydsz.workflow.api`)

| 客户端 | contextId | 目标服务 | 调用方模块 |
|--------|-----------|---------|-----------|
| `WorkflowServiceClient` | workflowServiceClient | ydsz-workflow | literule |

### cronjob-api (`com.njydsz.cronjob.api`)

| 客户端 | contextId | 目标服务 | 调用方模块 |
|--------|-----------|---------|-----------|
| `CronjobServiceClient` | cronjobServiceClient | ydsz-cronjob | literule |

### literule-api (`com.njydsz.literule.api`)

| 客户端 | contextId | 目标服务 | 调用方模块 |
|--------|-----------|---------|-----------|
| `LiteRuleClient` | liteRuleClient | ydsz-literule | — (当前无外部模块调用，预留/内部使用) |

---

## 调用关系矩阵

| 调用方 \ 被调用方 | userinfo | system | workflow | project | cronjob | literule | message |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **workflow** | ✅ | — | — | — | — | — | ✅ |
| **userinfo** | — | — | — | — | — | — | — |
| **system** | ✅ | — | — | ✅ | — | — | — |
| **project** | ✅ | ✅ | — | — | — | — | — |
| **literule** | — | — | ✅ | — | ✅ | — | — |
| **nextwiki** | ✅ | — | — | — | — | — | — |
| **message** | — | — | — | — | — | — | — |
| **cronjob** | ✅ | ✅ | — | — | — | — | ✅ |
| **agent** | ✅ | — | — | ✅ | — | — | — |

---

## Feign 调用规范

### 1. 客户端定义规范

- 所有 Feign 客户端定义在各模块的 `xxx-api` 子模块中
- 必须指定 `contextId` 避免同名 Bean 冲突
- 必须指定 `name` 指向目标服务名（`ydsz-xxx`）
- Fallback 类必须实现对应接口，返回降级响应

### 2. @EnableYdszFeign 配置规范

- `basePackages` 必须包含 `com.njydsz.common.feign`（公共 Feign 配置）
- 按需添加其他模块的 `xxx.api` 包
- 禁止使用 `@EnableFeignClients` 原生注解

### 3. 新增 Feign 客户端流程

1. 在 `xxx-api` 模块中创建 Feign 接口
2. 创建 Fallback 实现类
3. 在调用方模块的 Application 类中添加 `basePackages`
4. 在调用方模块的 `pom.xml` 中添加 `xxx-api` 依赖
5. 更新本文档的调用关系矩阵与 Feign 客户端清单
