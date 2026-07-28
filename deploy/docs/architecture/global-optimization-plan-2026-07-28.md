# PMIS 全局架构统筹与跨模块联动优化整改方案

> **日期**：2026-07-28
> **范围**：全项目（9 个业务模块 + 27 个公共模块 + 前端 9 个子应用）
> **对标**：阿里中台架构、钉钉/飞书微服务架构、华为 ROMA 平台

---

## 一、现状审计结论

### 1.1 已有基础能力清单（27 个公共模块）

| 层级 | 模块 | 核心能力 |
|---|---|---|
| L1 | common-core | 统一响应/请求模型、TraceId、请求上下文、DAG、特性开关、重试模板 |
| L2 | common-util / common-json | 99 个工具类 / 高性能 JSON 引擎（ASM 字节码） |
| L3 | common-domain / common-exception | DDD 基类、领域事件、规范模式、树形结构 / 统一异常体系 |
| L4 | common-jdbc / common-redis / common-lock / common-cache | MyBatis-Plus 增强、Redis 6 种 ops、分布式锁、多策略缓存 |
| L5 | common-auth / common-safe / common-feign / common-audit | JWT 认证、安全防护、Feign 增强、审计日志 |
| L5 | common-file / common-notify / common-queue / common-docs | 文件存储、通知渠道、消息队列、文档解析 |
| L5 | common-excel / common-netty / common-socket / common-search | Excel 读写、Netty TCP、WebSocket、统一搜索 |
| L5 | common-event / common-config / common-seata / common-sentry | Outbox 事件、配置管理、分布式事务、监控告警 |
| L5 | common-tenant / common-thread | 多租户隔离、线程池治理 |
| L6 | common-base / common-web / common-app | HTTP 基座、Web 基座、App 基座 |

### 1.2 公共能力复用矩阵（✅=已接入 ❌=未接入 ⚠️=有依赖但未实际使用）

| 公共模块 | gateway | userinfo | system | project | message | cronjob | workflow | nextwiki | literule | agent |
|---|---|---|---|---|---|---|---|---|---|---|
| common-queue | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **❌** |
| common-event | ❌ | ✅⚠️ | ✅ | ✅⚠️ | ✅⚠️ | ✅⚠️ | ✅ | ✅⚠️ | ✅⚠️ | ✅⚠️ |
| common-notify | ✅ | ✅ | ✅ | ✅ | ✅ | ✅* | ✅ | ✅ | ✅ | ✅ |
| common-search | ❌ | ✅ | ✅ | ✅ | ✅ | **❌** | ✅ | ✅ | ✅ | ✅ |
| common-seata | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| common-file | ❌ | ✅ | ✅ | ✅ | **❌** | **❌** | ✅ | ✅ | ❌ | ✅ |
| common-docs | ❌ | ❌ | ❌ | **❌** | **❌** | ❌ | **❌** | ✅ | ❌ | ✅ |
| common-excel | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| common-cache | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ |
| common-thread | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| common-tenant | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

> \* cronjob 使用了自建的 `CronjobNotifyHelper` 而非 common-notify 的 `NotifyHelper`

### 1.3 跨模块联动现状

| 联动场景 | 状态 | 实现方式 |
|---|---|---|
| project → workflow（项目立项触发审批流） | ✅ | `ProjectInitiationFlowListener` 直接调用 |
| cronjob → message（任务失败告警） | ✅ | `CrossModuleEventListener`（message 模块） |
| nextwiki → agent（文件上传自动索引 RAG） | ✅ | `CrossModuleEventListener`（agent 模块） |
| workflow → message（审批通知） | ✅ | `FlowNotificationService` 委托 |
| workflow → cronjob（流程超时触发定时任务） | ⚠️ | 通道常量已定义，无实际消费方 |
| project ← workflow（流程完成更新项目状态） | **❌** | 无监听器 |
| literule ← system（配置变更触发规则热更新） | **❌** | 无事件发布/监听 |
| system → all（配置变更广播通知） | **❌** | ConfigServiceImpl 有 OutboxService 但未实际写入事件 |
| message ← cronjob（任务结果触发消息推送） | **❌** | 无监听器 |
| agent → workflow（AI 审批集成） | ⚠️ | CrossModuleEventListener 存在但仅记录日志 |

---

## 二、优化整改方案

### P0 级（4 项）— 核心架构缺陷

#### P0-1：Agent 模块补全 common-queue 依赖

**问题**：Agent 模块有 common-event（Outbox 模式）但缺少 common-queue（消息队列抽象）。Agent 的异步任务执行、工具调用结果回传、Human-in-the-Loop 审批等场景需要消息队列支持。

**整改**：
1. `ydsz-agent-server/pom.xml` 添加 `ydsz-common-queue` 依赖
2. 新建 `AgentQueueChannels` 常量类，定义通道：
   - `AGENT_TASK_RESULT` — Agent 任务执行结果
   - `AGENT_APPROVAL_REQUEST` — Human-in-the-Loop 审批请求
   - `AGENT_KNOWLEDGE_UPDATE` — 知识库更新事件
3. `bootstrap.yml` 添加 `ydsz.queue.enabled=true` 配置
4. `AgentAutoConfiguration` 的 `@EnableYdszFeign` 确认包含 queue 包扫描

**工作量**：1 文件修改 + 1 新文件，约 30 行

---

#### P0-2：Cronjob 模块补全 common-search 依赖 + SearchProvider

**问题**：9 个业务模块中唯独 cronjob 没有 SearchProvider，任务定义和 DAG 定义无法被全局搜索。

**整改**：
1. `ydsz-cronjob-server/pom.xml` 添加 `ydsz-common-search` 依赖
2. 新建 `JobSearchProvider implements SearchProvider<Job>`：
   - `getType()` → `"job"`
   - `toIndexDocument()` — 将 Job 实体转为索引文档（jobName/jobKey/cronExpression/desc）
   - `getFilters()` — 租户隔离过滤
3. 新建 `JobDagSearchProvider implements SearchProvider<JobDag>`：
   - `getType()` → `"job_dag"`
   - `toIndexDocument()` — DAG 名称/节点/描述

**工作量**：1 文件修改 + 2 新文件，约 120 行

---

#### P0-3：清理重复 SearchProvider 实现

**问题**：同一实体存在多个 SearchProvider 实现，会导致 `SearchProviderRegistry` 注册冲突（后注册覆盖先注册）。

| 模块 | 重复类 | 实体类型 | 处理方式 |
|---|---|---|---|
| userinfo | `UserinfoSearchProvider` + `UserSearchProvider` | `UserAccount` | 保留 `UserSearchProvider`（命名更简洁），删除 `UserinfoSearchProvider` |
| system | `ConfigSearchProvider` + `SystemSearchProvider` | `Config` | 合并为 `ConfigSearchProvider`（功能更完整），删除 `SystemSearchProvider` |

**整改**：
1. 对比两个重复实现的 `toIndexDocument()` / `getFilters()` / `getSearchFields()` 方法
2. 保留功能更完整的实现，将另一实现中独有的逻辑合并到保留类
3. 删除冗余文件
4. 全局搜索确认无其他类引用被删除的 Provider

**工作量**：2 文件删除 + 2 文件修改，约 -80 行

---

#### P0-4：前端 initSharedAuth 函数提取到公共包

**问题**：9 个子应用的 `main.ts` 中都有完全相同的 `initSharedAuth()` 函数（约 40 行），包含相同的 `doReAuthenticate` 和 `doRefreshToken` 回调逻辑。

**整改**：
1. 在 `@ydsz/shared-auth` 包中新增 `createDefaultInitOptions()` 工厂函数：
   ```typescript
   export function createDefaultInitOptions(): {
     onReAuthenticate: () => Promise<void>;
     onRefreshToken: () => Promise<null | string>;
   }
   ```
2. 9 个子应用 `main.ts` 改为：
   ```typescript
   const opts = createDefaultInitOptions();
   initSharedRequest(opts.onReAuthenticate, opts.onRefreshToken);
   ```
3. 子应用可覆盖默认行为（传入自定义 options）

**工作量**：1 文件新增 + 9 文件修改，约 -300 行重复代码消除

---

### P1 级（6 项）— 跨模块联动打通

#### P1-1：Outbox 事件模式全量接入

**问题**：common-event 已被 9 个模块引入依赖，但实际注入 `OutboxService` 并写入事件的只有 workflow 和 system 两个模块。其余 7 个模块"有依赖不使用"。

**整改清单**：

| 模块 | 事件类型 | 触发场景 | 写入位置 |
|---|---|---|---|
| project | `PROJECT_INITIATION_CREATED` | 项目立项创建 | `ProjectInitiationServiceImpl.create()` |
| project | `PROJECT_CONTRACT_SIGNED` | 合同签订 | `ProjectContractServiceImpl.sign()` |
| message | `MESSAGE_SENT` | 消息发送成功 | `NotifyServiceImpl.send()` |
| cronjob | `JOB_EXECUTION_COMPLETED` | 任务执行完成 | `DefaultTaskDispatcher.execute()` |
| cronjob | `JOB_EXECUTION_FAILED` | 任务执行失败 | `JobExecutionRecorder.recordFailure()` |
| literule | `RULE_TRIGGERED` | 规则触发 | `DefaultRuleEngine.execute()` |
| agent | `AGENT_CONVERSATION_CREATED` | 对话创建 | `ChatService.chat()` |
| nextwiki | `FILE_UPLOADED` | 文件上传完成 | `FileApplicationService.upload()` |
| userinfo | `USER_LOGIN` | 用户登录 | `AuthServiceImpl.login()` |

**整改方式**：每个模块在对应的 Service 方法中注入 `ObjectProvider<OutboxService>`（可选依赖），在 `@Transactional` 方法中调用 `outboxService.appendToOutbox()`。

**工作量**：约 9 文件修改，每处约 15 行，共约 135 行

---

#### P1-2：跨模块事件监听器补全

**问题**：仅有 message 和 agent 两个模块有 `CrossModuleEventListener`。关键联动场景缺失。

**整改清单**：

| 监听方模块 | 监听事件 | 来源模块 | 联动行为 |
|---|---|---|---|
| project | `FLOW_INSTANCE_COMPLETED` | workflow | 更新项目立项审批状态 |
| project | `FLOW_INSTANCE_REJECTED` | workflow | 更新项目立项状态为已驳回 |
| project | `JOB_EXECUTION_FAILED` | cronjob | 数据同步任务失败告警 |
| message | `JOB_EXECUTION_COMPLETED` | cronjob | 任务完成通知 |
| literule | `CONFIG_CHANGED` | system | 规则配置热更新 |
| workflow | `JOB_RESULT` | cronjob | DAG 节点任务完成推进流程 |
| system | `FILE_UPLOADED` | nextwiki | 更新文件索引配置 |
| userinfo | `AGENT_CONVERSATION_CREATED` | agent | 记录用户 AI 使用日志 |

**整改方式**：每个模块新建 `CrossModuleEventListener` 类，通过 `@EventListener` 监听 `DomainEvent`（从 Outbox 投递的 MQ 消息反序列化）或直接监听 Spring `ApplicationEvent`（同 JVM 内）。

**工作量**：约 6 新文件 + 2 文件修改，共约 300 行

---

#### P1-3：CronjobNotifyHelper 统一到 NotifyHelper

**问题**：cronjob 模块自建 `CronjobNotifyHelper` 封装通知逻辑，但 common-notify 已有统一的 `NotifyHelper`。

**整改**：
1. 对比 `CronjobNotifyHelper` 与 `NotifyHelper` 的方法签名
2. 将 cronjob 特有的通知模板逻辑（如任务失败告警模板）迁移到 `NotifyHelper` 或作为配置
3. `AlertDispatcher` 改为直接注入 `NotifyHelper`
4. 删除 `CronjobNotifyHelper.java`

**工作量**：1 文件删除 + 2 文件修改，约 -50 行

---

#### P1-4：common-file 补全 message/cronjob 接入

**问题**：message 模块需要邮件附件上传、cronjob 模块需要脚本文件管理，但都未引入 common-file。

**整改**：
1. `ydsz-message-server/pom.xml` 添加 `ydsz-common-file` 依赖
2. `ydsz-cronjob-server/pom.xml` 添加 `ydsz-common-file` 依赖
3. message 模块的邮件附件处理改为使用 `FileStorage` API
4. cronjob 模块的脚本文件管理改为使用 `FileStorage` API

**工作量**：2 文件修改 + 约 4 文件代码调整，约 80 行

---

#### P1-5：common-docs 扩展接入

**问题**：common-docs 提供文档解析能力，但仅 nextwiki 和 agent 使用。project 模块有合同文档解析需求、message 模块有邮件附件解析需求。

**整改**：
1. `ydsz-project-server/pom.xml` 添加 `ydsz-common-docs` 依赖
2. `ydsz-message-server/pom.xml` 添加 `ydsz-common-docs` 依赖
3. project 模块合同管理接入 `DocumentService.parse()` 解析合同 PDF/Word
4. message 模块邮件附件接入 `DocumentService.parse()` 提取附件文本内容

**工作量**：2 文件修改 + 约 4 文件代码调整，约 100 行

---

#### P1-6：Feign 客户端补全与跨模块调用链打通

**问题**：部分跨模块调用场景缺少 Feign 客户端接口。

**整改清单**：

| Feign 客户端 | 所属 API 模块 | 调用方 | 用途 |
|---|---|---|---|
| `JobQueryClient` | cronjob-api | workflow, project | 查询任务执行状态/历史 |
| `RuleQueryClient` | literule-api | project, system | 查询规则定义/执行结果 |
| `AgentQueryClient` | agent-api | workflow, project | 查询 Agent 定义/对话历史 |
| `ConfigQueryClient` | system-api | 所有模块 | 查询系统配置（替代直接 DB 查询） |

**整改方式**：在对应 api 模块新建 Feign 客户端接口 + Fallback 类，FeignClientConstants 添加路径常量。

**工作量**：约 8 新文件（4 Client + 4 Fallback）+ 1 文件修改，约 200 行

---

### P2 级（5 项）— 重复编码清理

#### P2-1：HealthIndicator 统一继承 AbstractModuleHealthIndicator

**问题**：common-base 已有 `AbstractModuleHealthIndicator` 基类，但部分模块的 HealthIndicator 未继承。

**整改**：
1. 全局扫描所有 `*HealthIndicator.java` 文件
2. 逐个检查是否继承 `AbstractModuleHealthIndicator`
3. 未继承的改为继承，提取公共逻辑（依赖检查、状态报告格式）

**工作量**：约 5-8 文件修改，每处约 10 行调整

---

#### P2-2：Metrics 统一继承 AbstractModuleMetrics

**问题**：common-base 已有 `AbstractModuleMetrics` 基类（含 `incrementCounter()`/`gaugeRef()`/`safe()` 方法），但部分模块的 Metrics 类未继承。

**整改**：
1. 全局扫描所有 `*Metrics.java` 文件
2. 逐个检查是否继承 `AbstractModuleMetrics`
3. 未继承的改为继承，删除重复的 Counter 缓存逻辑

**工作量**：约 3-5 文件修改，每处约 15 行调整

---

#### P2-3：common-seata 接入评估与按需引入

**问题**：common-seata 仅 workflow 和 project 引入。涉及跨服务数据一致性的 message/cronjob 未引入。

**整改**：
1. 评估 message 模块的消息发送+DB 记录是否需要分布式事务（建议用 Outbox 替代 Seata）
2. 评估 cronjob 模块的任务执行+日志记录是否需要分布式事务（建议用幂等+重试替代）
3. 对确实需要强一致性的场景引入 common-seata
4. 对弱一致性场景明确标注"使用事件+Outbox 模式"

**工作量**：评估文档 + 约 2 文件修改，约 30 行

---

#### P2-4：前端共享业务组件包创建

**问题**：9 个子应用各自实现相似的业务组件（表格 CRUD 页面、表单弹窗、详情抽屉等），缺少公共业务组件包。

**整改**：
1. 新建 `@ydsz/shared-ui` 包（`comm/effects/shared-ui/`）
2. 提取通用业务组件：
   - `CrudTable.vue` — 标准 CRUD 表格（搜索+表格+分页+新增/编辑弹窗）
   - `FormDialog.vue` — 标准表单弹窗
   - `DetailDrawer.vue` — 标准详情抽屉
   - `StatusTag.vue` — 状态标签
   - `PermissionButton.vue` — 权限按钮
3. 9 个子应用 package.json 添加 `@ydsz/shared-ui: workspace:*` 依赖

**工作量**：约 5 新组件文件 + 9 文件修改，约 500 行

---

#### P2-5：模块间常量与 DTO 共享治理

**问题**：跨模块事件类型常量分散在各模块内部，缺少统一的事件类型注册中心。部分 DTO 在多个模块中重复定义。

**整改**：
1. `common-core` 新建 `ModuleEventTypes` 常量类（已有 `StandardEventTypes`，需检查是否重复）
2. 各模块的事件类型常量统一引用 `StandardEventTypes`
3. 跨模块共享的 DTO（如 `MessageRequest`、`UserInfo`）统一到对应 api 模块
4. 删除各模块内部的重复 DTO 定义

**工作量**：约 3-5 文件修改，约 -50 行重复代码

---

### P3 级（3 项）— 工程规范化

#### P3-1：ArchUnit 架构约束规则扩充

**问题**：当前仅 11 条 ArchUnit 规则，缺少对公共模块依赖方向的强制约束。

**整改**：新增以下规则：
- R12：业务模块 server 层必须依赖 common-thread（线程池统一治理）
- R13：业务模块 server 层必须依赖 common-tenant（多租户隔离）
- R14：业务模块 web 层 Controller 必须使用 `@Audit` 注解（写操作）
- R15：禁止业务模块直接依赖其他业务模块的 domain/infra（只能依赖 api）
- R16：所有 Outbox 事件类型必须定义在 `StandardEventTypes` 中

**工作量**：1 文件修改，约 50 行

---

#### P3-2：配置元数据与 API 文档全量覆盖

**问题**：部分新增配置项缺少 `additional-spring-configuration-metadata.json`，部分模块的 `ydsz.doc.enabled` 未启用。

**整改**：
1. 全局扫描 `@ConfigurationProperties` 类，检查是否有对应的元数据文件
2. 缺失的模块补充 `additional-spring-configuration-metadata.json`
3. 各模块 `application.yml` / `bootstrap.yml` 添加 `ydsz.doc.enabled=true`（dev/sit 环境）

**工作量**：约 3-5 文件新增/修改，约 100 行

---

#### P3-3：全局 API 路径与 Feign 客户端对齐验证

**问题**：FeignClientConstants 中定义了路径常量，但部分 Feign 客户端接口的 `@PostMapping` 路径可能与实际 Controller 路径不一致。

**整改**：
1. 全局扫描所有 `@FeignClient` 接口的 `@PostMapping`/`@GetMapping` 路径
2. 与对应 Controller 的 `@RequestMapping` 路径逐一比对
3. 不一致的修复为引用 `FeignClientConstants` 常量

**工作量**：约 5-10 文件修改，约 30 行

---

## 三、实施优先级与排期

| 优先级 | 项目数 | 预计工作量 | 影响范围 |
|---|---|---|---|
| P0（核心架构缺陷） | 4 项 | 约 500 行 | Agent/Cronjob/Userinfo/System + 前端全量 |
| P1（跨模块联动） | 6 项 | 约 1000 行 | 全部 9 个业务模块 |
| P2（重复编码清理） | 5 项 | 约 800 行 | 全部 9 个业务模块 + 前端 |
| P3（工程规范化） | 3 项 | 约 200 行 | 全项目 |
| **合计** | **18 项** | **约 2500 行** | — |

### 建议实施顺序

```
第一批（P0）：P0-1 → P0-2 → P0-3 → P0-4
第二批（P1）：P1-1 → P1-2 → P1-3 → P1-4 → P1-5 → P1-6
第三批（P2）：P2-1 → P2-2 → P2-3 → P2-4 → P2-5
第四批（P3）：P3-1 → P3-2 → P3-3
```

---

## 四、验收标准

1. **公共能力复用矩阵**：所有 ❌ 项消除（gateway 除外，网关按需引入）
2. **跨模块联动**：P1-2 清单中 8 个联动场景全部实现
3. **重复代码**：SearchProvider 无重复实现，前端 initSharedAuth 无重复
4. **ArchUnit 测试**：R12-R16 全部通过
5. **编译检查**：全项目 `mvn compile -DskipTests` 零 ERROR
6. **Lint 检查**：零 @SuppressWarnings，零行内 FQN 违规
