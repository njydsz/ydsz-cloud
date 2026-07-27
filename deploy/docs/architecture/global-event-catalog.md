# PMIS 全局事件目录

> 版本: 1.1.0 | 更新日期: 2026-07-27
>
> 本文档汇总 PMIS 项目所有标准领域事件，包括事件类型常量、发布方、消费方及负载结构。
> 事件类型常量统一定义在 `com.njydsz.common.event.model.StandardEventTypes`。

---

## 目录

| 模块 | 事件数量 | 说明 |
|------|---------|------|
| [Workflow](#workflow-事件) | 5 | 流程审批引擎事件 |
| [Userinfo](#userinfo-事件) | 8 | 用户中心事件 |
| [System](#system-事件) | 3 | 系统配置事件 |
| [Cronjob](#cronjob-事件) | 4 | 定时任务事件 |
| [Nextwiki](#nextwiki-事件) | 3 | 网盘知识库事件 |
| [Literule](#literule-事件) | 1 | 规则引擎事件 |
| [Agent](#agent-事件) | 2 | AI Agent 事件 |
| [Project](#project-事件) | 5 | 项目管理事件 |

---

## Workflow 事件

### FLOW_INSTANCE_APPROVED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.FLOW_INSTANCE_APPROVED` |
| **发布方** | `FlowInstanceServiceImpl` (workflow-server) |
| **消费方** | project（立项审批通过触发项目创建）、message（通知发起人） |
| **负载** | `flowInstanceId`, `processDefinitionKey`, `businessKey`, `approvedBy`, `approvedAt` |
| **路由键** | `flow.instance.approved` |

### FLOW_INSTANCE_REJECTED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.FLOW_INSTANCE_REJECTED` |
| **发布方** | `FlowInstanceServiceImpl` |
| **消费方** | message（通知发起人） |
| **负载** | `flowInstanceId`, `processDefinitionKey`, `businessKey`, `rejectedBy`, `reason`, `rejectedAt` |
| **路由键** | `flow.instance.rejected` |

### FLOW_INSTANCE_TERMINATED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.FLOW_INSTANCE_TERMINATED` |
| **发布方** | `FlowInstanceServiceImpl` |
| **消费方** | message（通知相关人） |
| **负载** | `flowInstanceId`, `terminatedBy`, `reason`, `terminatedAt` |
| **路由键** | `flow.instance.terminated` |

### FLOW_TASK_COMPLETED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.FLOW_TASK_COMPLETED` |
| **发布方** | `FlowInstanceServiceImpl` |
| **消费方** | message（通知下一审批人） |
| **负载** | `taskId`, `flowInstanceId`, `completedBy`, `comment`, `completedAt` |
| **路由键** | `flow.task.completed` |

### FLOW_URGE_TRIGGERED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.FLOW_URGE_TRIGGERED` |
| **发布方** | `FlowAutoUrgeScheduler` |
| **消费方** | message（催办通知） |
| **负载** | `flowInstanceId`, `taskId`, `assigneeId`, `urgeCount`, `triggeredAt` |
| **路由键** | `flow.urge.triggered` |

---

## Userinfo 事件

### USER_CREATED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.USER_CREATED` |
| **发布方** | `UserAccountServiceImpl` (userinfo-server) |
| **消费方** | message（欢迎通知）、agent（用户初始化） |
| **负载** | `userId`, `username`, `realName`, `deptId`, `tenantId`, `createdAt` |
| **路由键** | `user.created` |

### USER_UPDATED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.USER_UPDATED` |
| **发布方** | `UserAccountServiceImpl` |
| **消费方** | system（缓存失效） |
| **负载** | `userId`, `username`, `updatedFields[]`, `updatedBy`, `updatedAt` |
| **路由键** | `user.updated` |

### USER_DELETED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.USER_DELETED` |
| **发布方** | `UserAccountServiceImpl` |
| **消费方** | workflow（挂起用户任务）、message（注销通知） |
| **负载** | `userId`, `username`, `deletedBy`, `deletedAt` |
| **路由键** | `user.deleted` |

### USER_LOGIN

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.USER_LOGIN` |
| **发布方** | `AuthServiceImpl` |
| **消费方** | system（登录日志）、sentry（安全审计） |
| **负载** | `userId`, `username`, `loginIp`, `deviceId`, `loginAt` |
| **路由键** | `user.login` |

### USER_LOGOUT

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.USER_LOGOUT` |
| **发布方** | `AuthServiceImpl` |
| **消费方** | system（登出日志） |
| **负载** | `userId`, `username`, `logoutAt` |
| **路由键** | `user.logout` |

### USER_ENABLED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.USER_ENABLED` |
| **发布方** | `UserAccountServiceImpl` |
| **消费方** | workflow（恢复用户任务） |
| **负载** | `userId`, `username`, `enabledBy`, `enabledAt` |
| **路由键** | `user.enabled` |

### USER_DISABLED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.USER_DISABLED` |
| **发布方** | `UserAccountServiceImpl` |
| **消费方** | workflow（挂起用户任务）、message（通知用户） |
| **负载** | `userId`, `username`, `disabledBy`, `reason`, `disabledAt` |
| **路由键** | `user.disabled` |

### ORG_STRUCTURE_CHANGED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.ORG_STRUCTURE_CHANGED` |
| **发布方** | `DepartmentServiceImpl` |
| **消费方** | workflow（审批人重解析）、project（项目归属更新） |
| **负载** | `deptId`, `changeType` (CREATE/UPDATE/DELETE/MOVE), `parentId`, `changedBy`, `changedAt` |
| **路由键** | `org.structure.changed` |

---

## System 事件

### CONFIG_CHANGED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.CONFIG_CHANGED` |
| **发布方** | `ConfigServiceImpl` (system-server) |
| **消费方** | 所有模块（配置热更新） |
| **负载** | `configGroup`, `configKey`, `oldValue`, `newValue`, `changedBy`, `changedAt` |
| **路由键** | `system.config.changed` |

### DICT_CHANGED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.DICT_CHANGED` |
| **发布方** | `DictItemServiceImpl` |
| **消费方** | 所有模块（字典缓存刷新） |
| **负载** | `typeCode`, `itemCode`, `changeType` (ADD/UPDATE/DELETE), `changedBy`, `changedAt` |
| **路由键** | `system.dict.changed` |

### VARIABLE_CHANGED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.VARIABLE_CHANGED` |
| **发布方** | `VariableServiceImpl` |
| **消费方** | 所有模块（变量缓存刷新） |
| **负载** | `variableKey`, `oldValue`, `newValue`, `changedBy`, `changedAt` |
| **路由键** | `system.variable.changed` |

---

## Cronjob 事件

### JOB_EXECUTION_FAILED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.JOB_EXECUTION_FAILED` |
| **发布方** | `DefaultTaskDispatcher` (cronjob-server) |
| **消费方** | message（告警通知）、sentry（错误追踪） |
| **负载** | `jobId`, `jobName`, `executionId`, `errorMessage`, `failedAt`, `retryCount` |
| **路由键** | `cronjob.execution.failed` |

### JOB_EXECUTION_SUCCESS

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.JOB_EXECUTION_SUCCESS` |
| **发布方** | `DefaultTaskDispatcher` |
| **消费方** | — （用于统计） |
| **负载** | `jobId`, `jobName`, `executionId`, `duration`, `succeededAt` |
| **路由键** | `cronjob.execution.success` |

### DAG_NODE_COMPLETED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.DAG_NODE_COMPLETED` |
| **发布方** | `DagInstanceExecutor` |
| **消费方** | — （用于 DAG 可视化） |
| **负载** | `dagId`, `dagInstanceId`, `nodeId`, `nodeName`, `status`, `completedAt` |
| **路由键** | `cronjob.dag.node.completed` |

### JOB_TIMEOUT

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.JOB_TIMEOUT` |
| **发布方** | `TimeoutMonitor` |
| **消费方** | message（超时告警）、sentry（性能追踪） |
| **负载** | `jobId`, `jobName`, `executionId`, `timeoutMs`, `timedOutAt` |
| **路由键** | `cronjob.timeout` |

---

## Nextwiki 事件

### FILE_UPLOADED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.FILE_UPLOADED` |
| **发布方** | `FileApplicationService` (nextwiki-server) |
| **消费方** | search（索引更新）、agent（知识库摄入） |
| **负载** | `fileId`, `fileName`, `fileSize`, `fileHash`, `uploadedBy`, `uploadedAt` |
| **路由键** | `nextwiki.file.uploaded` |

### FILE_DELETED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.FILE_DELETED` |
| **发布方** | `FileApplicationService` |
| **消费方** | search（索引删除） |
| **负载** | `fileId`, `fileName`, `deletedBy`, `deletedAt` |
| **路由键** | `nextwiki.file.deleted` |

### FILE_SHARED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.FILE_SHARED` |
| **发布方** | `ShareDomainService` |
| **消费方** | message（分享通知） |
| **负载** | `fileId`, `fileName`, `sharedTo`, `shareType` (USER/LINK), `sharedBy`, `sharedAt` |
| **路由键** | `nextwiki.file.shared` |

---

## Literule 事件

### RULE_CONFIG_CHANGED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.RULE_CONFIG_CHANGED` |
| **发布方** | `RuleAdminController` (literule-server) |
| **消费方** | literule（规则缓存刷新） |
| **负载** | `ruleSetId`, `ruleId`, `changeType` (ADD/UPDATE/DELETE), `changedBy`, `changedAt` |
| **路由键** | `literule.rule.config.changed` |

---

## Agent 事件

### CONVERSATION_CREATED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.CONVERSATION_CREATED` |
| **发布方** | `ChatService` (agent-server) |
| **消费方** | — （用于统计） |
| **负载** | `conversationId`, `userId`, `agentType`, `title`, `createdAt` |
| **路由键** | `agent.conversation.created` |

### AGENT_APPROVAL_REQUESTED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.AGENT_APPROVAL_REQUESTED` |
| **发布方** | `HumanApprovalService` (agent-server) |
| **消费方** | workflow（审批流程发起）、message（审批通知） |
| **负载** | `approvalId`, `conversationId`, `agentType`, `requestedAction`, `requestedBy`, `requestedAt`, `expiresAt` |
| **路由键** | `agent.approval.requested` |

---

## Project 事件

### PROJECT_INITIATION_CREATED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.PROJECT_INITIATION_CREATED` |
| **发布方** | `ProjectInitiationServiceImpl` (project-server) |
| **消费方** | workflow（发起审批流程）、message（通知相关人） |
| **负载** | `projectInitiationId`, `projectCode`, `projectName`, `customerId`, `createdBy`, `createdAt` |
| **路由键** | `project.initiation.created` |

### PROJECT_INITIATION_APPROVED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.PROJECT_INITIATION_APPROVED` |
| **发布方** | workflow（审批通过回调） |
| **消费方** | project（创建正式项目记录）、message（通知发起人） |
| **负载** | `projectInitiationId`, `projectCode`, `projectName`, `approvedBy`, `approvedAt` |
| **路由键** | `project.initiation.approved` |

### PROJECT_STAGE_CHANGED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.PROJECT_STAGE_CHANGED` |
| **发布方** | `ProjectInitiationServiceImpl` |
| **消费方** | workflow（触发阶段审批）、message（阶段变更通知） |
| **负载** | `projectId`, `projectName`, `oldStage`, `newStage`, `changedBy`, `changedAt` |
| **路由键** | `project.stage.changed` |

### PROJECT_CLOSED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.PROJECT_CLOSED` |
| **发布方** | `ProjectInitiationServiceImpl` |
| **消费方** | message（项目关闭通知）、agent（项目归档） |
| **负载** | `projectId`, `projectName`, `closeReason`, `closedBy`, `closedAt` |
| **路由键** | `project.closed` |

### PROJECT_CONTRACT_SIGNED

| 属性 | 值 |
|------|-----|
| **常量** | `StandardEventTypes.PROJECT_CONTRACT_SIGNED` |
| **发布方** | `ProjectContractServiceImpl` (project-server) |
| **消费方** | message（合同签订通知）、project（收入计划生成） |
| **负载** | `contractId`, `projectId`, `projectName`, `contractAmount`, `signedBy`, `signedAt` |
| **路由键** | `project.contract.signed` |

---

## 事件发布规范

### 1. Outbox 模式

所有事件通过 `OutboxService.appendToOutbox()` 发布，确保事件与业务数据在同一事务中持久化：

```java
@Autowired
private ObjectProvider<OutboxService> outboxServiceProvider;

private void publishEvent(String eventType, Map<String, Object> payload) {
    OutboxService outboxService = outboxServiceProvider.getIfAvailable();
    if (outboxService != null) {
        outboxService.appendToOutbox(eventType, YdszJson.toJson(payload));
    }
}
```

### 2. 事件命名规范

- 格式：`MODULE_ENTITY_ACTION`
- 全大写，下划线分隔
- 常量定义在 `StandardEventTypes`
- 路由键：`module.entity.action`（小写，点分隔）

### 3. 事件负载规范

- 所有事件负载为 JSON 字符串
- 必须包含操作时间戳（`xxxAt`）
- 必须包含操作人（`xxxBy`）
- 金额字段使用分（整数）或元（字符串，保留精度）

### 4. 新增事件流程

1. 在 `StandardEventTypes` 中新增常量
2. 在本文档中补充事件描述
3. 在发布方 Service 中调用 `publishEvent()`
4. 在消费方实现事件监听（`@EventListener` 或 MQ Consumer）
5. 更新本文档的版本号和日期
