# ydsz-common-domain 全局引用分析与模块集成度审计

> 审计对象：`ydsz-backend/ydsz-common/ydsz-common-domain`（49 个源文件）
> 审计维度：**其他模块与本公共模块的集成程度 + 能力利用率**
> 审计日期：2026-08-04
> 数据来源：pom.xml 依赖声明 + 源码 `import` 全文检索 + 关键类 `extends` / `implements` 实际绑定
> 关系定位：与 `DOMAIN_MODULE_OVERDESIGN_AUDIT.md`（已存在）互补——
> - 旧报告聚焦 **domain 模块自身的过度设计问题**（做错了什么）
> - **本报告聚焦其他模块与 domain 模块的集成度与能力利用率**（用得怎么样）

---

## 一、TL;DR —— 一页结论

| 维度 | 现状 | 严重度 |
|---|---|---|
| 隐式传递依赖（3 个模块 import 但 pom 不声明） | ydsz-cronjob、ydsz-workflow、ydsz-message 全部走 `ydsz-common-jdbc → ydsz-common-domain` 传递链 | 🔴 高 |
| 死依赖（声明了但 0 import） | ydsz-literule 显式声明，但代码 0 处直接 import | 🟡 中 |
| 实体基类（BaseLong/BaseString/BaseEntity/BaseAuditEntity）业务模块直继承 | **0 处**（全部走 `MpBaseEntity`） | 🟡 预期内 |
| annotation 包（@CreateAt/@CreatedBy/@Version/@SoftDelete/@TenantId/@DomainService）业务模块引用 | **0–1 处** | 🔴 高 |
| `BaseStatusEnum` 业务模块实现覆盖率 | 2/10 模块（20%） | 🟡 中 |
| `DomainEvent` 业务模块使用 | 1/10 模块 | 🟡 中 |
| `MapProcessor` / `MapReduceProcessor` 业务模块实现 | **0 处** | 🟡 中（疑似过度设计） |
| `PageQuery` + `PageResult` 业务模块使用 | 6/10 模块 | 🟢 健康 |
| `TreeBuilder` / `TreeNode` 业务模块使用 | 1/10 模块（仅 userinfo） | 🟡 中 |
| `BaseDTO` 业务模块使用 | 2/10 模块 | 🟡 中 |
| DAG 引擎业务模块使用 | 1/10 模块（仅 cronjob） | 🟢 合理 |
| `JobHandler` 业务模块实现 | 2/10 模块（11 处） | 🟢 合理 |

**关键结论**：

1. **5 个核心子能力**（PageQuery/PageResult、JobHandler、DAG 三件套、TreeBuilder、StatusEnum）被使用，**4 个子能力处于"框架做好但极少业务落地"状态**（DomainEvent、MapReduce、annotation 包、BaseDTO）。
2. **3 个模块处于"用了但没显式声明依赖"的隐式契约状态**，是首要修复项。
3. **annotation 包 8 个注解业务模块几乎 0 引用**，属于"提供但不可见"，是 domain 模块存在感薄弱的核心原因。
4. **ydsz-literule 声明了依赖但 0 import**，是无效依赖。

---

## 二、模块能力图谱（domain 对外暴露的 11 个子包）

```
ydsz-common-domain (49 类)
├── annotation  (8)  字段/类注解：审计、版本、软删除、租户、领域服务
├── entity      (10) 实体基类与标记接口
├── query       (3)  BaseQuery + PageQuery + PageResult
├── dto         (1)  BaseDTO
├── event       (3)  DomainEvent + EventStore + ModuleEventTypes
├── tree        (2)  TreeNode + TreeBuilder
├── dag         (3)  DagInstanceStatus + DagNodeStatus + SpELConditionEvaluator
├── job         (10) JobHandler/MapProcessor/MapReduceProcessor + 上下文 + 日志 + 结果
├── enums       (4)  BaseStatusEnum + DataScopeType + IdentityType + ServiceType
├── constant    (1)  TokenConstants
├── config      (3)  DomainAutoConfiguration + DomainProperties + FilterIgnoreConstant
└── health      (1)  DomainHealthIndicator
```

---

## 三、模块级集成度矩阵（10 业务模块 × 12 子能力）

> **✓** = 至少 1 处真实使用 | **△** = 部分使用 | **✗** = 完全未用 | **n/a** = 模块不涉及
> ✗\* = 通过 `ydsz-common-jdbc/MpBaseEntity` 间接实现（功能等价，但与 domain 解耦）

| 子能力 \ 模块 | system | userinfo | workflow | cronjob | project | literule | agent | nextwiki | message | gateway |
|---|---|---|---|---|---|---|---|---|---|---|
| **pom 显式声明依赖** | ✓ | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ | ✓ | ✗ | n/a |
| **源码 import 次数** | 9 | 12 | 5 | **30** | 2 | **0** | 0 | 9 | 15 | 0 |
| 实体基类（BaseLong/BaseString） | ✗* | ✗* | ✗* | ✗* | ✗* | ✗* | ✗* | ✗* | ✗* | n/a |
| 审计注解（@CreateAt/@CreatedBy/@UpdateAt/@UpdatedBy） | ✗* | ✗* | ✗* | ✗* | ✗* | ✗* | ✗* | ✗* | ✗* | n/a |
| 乐观锁+软删除（@Version/@SoftDelete） | ✗* | ✗* | ✗* | ✗* | ✗* | △ (@Version×1) | ✗* | ✗* | ✗* | n/a |
| 多租户（@TenantId） | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | n/a |
| 状态枚举（BaseStatusEnum） | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | **✓×2** | **✓×2** | n/a |
| 领域事件（DomainEvent） | ✗ | ✗ | **✓×1** | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | n/a |
| 事件类型表（ModuleEventTypes） | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | n/a |
| 分页（PageQuery/PageResult） | **✓** | **✓** | △ | ✗ | △ | ✗ | ✗ | **✓** | △ | n/a |
| DTO（BaseDTO） | **✓×5** | ✗ | ✗ | ✗ | **✓×1** | ✗ | ✗ | ✗ | ✗ | n/a |
| 树形（TreeNode/TreeBuilder） | ✗ | △ (仅 buildSimple) | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | n/a |
| DAG 引擎 | ✗ | ✗ | ✗ | **✓** | ✗ | ✗ | ✗ | ✗ | ✗ | n/a |
| Job 框架（JobHandler 等） | ✗ | ✗ | **✓×3** | **✓×8** | ✗ | ✗ | ✗ | ✗ | ✗ | n/a |
| MapReduceProcessor 实现 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | n/a |
| 身份/数据范围枚举（DataScopeType/IdentityType/ServiceType） | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a |
| 常量（TokenConstants） | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a |
| 配置（FilterIgnoreConstant） | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a |
| 计数 | 2/12 | 1.5/12 | 2/12 | 3/12 | 1/12 | 0.5/12 | 0/12 | 2/12 | 1.5/12 | — |

**模块健康度排序**（按实际利用能力项数 / 总能力项数）：

| 排名 | 模块 | 利用率 | 评价 |
|---|---|---|---|
| 1 | ydsz-cronjob | 25% | 用得最深（30 import / 8 JobHandler / DAG 全套） |
| 2 | ydsz-system | 17% | 用了分页 + DTO，但缺状态枚举/事件 |
| 3 | ydsz-workflow | 17% | 用 JobHandler 做周期任务 + 1 个 DomainEvent |
| 4 | ydsz-nextwiki | 17% | 分页 + 状态枚举 = 标准范式 |
| 5 | ydsz-userinfo | 13% | 分页 + 树（但没用对） |
| 6 | ydsz-message | 13% | 分页 + 状态枚举，但 Service 层未统一 PageResult |
| 7 | ydsz-project | 8% | 用了分页/DTO 但不完整 |
| 8 | ydsz-literule | 4% | pom 声明但 0 import，**典型死依赖** |
| 9 | ydsz-agent | 0% | **完全未集成** domain 模块（虽然依赖了 jdbc） |
| — | ydsz-gateway | n/a | 无业务代码 |

---

## 四、按子能力反向统计（谁用了什么）

### 4.1 真核心：分页/查询（PageQuery/PageResult）

| 类 | 总使用 | 模块分布 |
|---|---|---|
| `PageQuery` | 15 | userinfo(7)、message(4)、system(2)、project(1)、workflow(1) |
| `PageResult` | 16 | nextwiki(7)、system(6)、userinfo(3) |
| `BaseQuery` | 0 业务 | 仅 domain 内部 `PageQuery extends BaseQuery` |
| `BaseDTO` | 6 | system(5)、project(1) |

**关键观察**：
- ✅ `PageQuery` / `PageResult` 是 domain 唯一被广泛使用的能力。
- ⚠️ **架构不一致**：`ydsz-project` 和 `ydsz-message` 的 Service 层在某些场景仍返回 `IPage<T>`（MyBatis-Plus 原生），未统一为 `PageResult<T>`，导致 Controller 层需要二次转换。
- ⚠️ **BaseDTO 覆盖率仅 2/10**：仅 `ydsz-system`（5 个 DTO）和 `ydsz-project`（1 个 DTO）使用，userinfo/workflow/cronjob/agent 的 DTO 都是 `@Data` + 自定义字段。`BaseDTO` 提供的 operatorId/operatorName/requestId/traceId/tenantId/language 切面上下文能力被严重闲置。

### 4.2 真核心：Job 框架（cronjob + workflow）

| 类/接口 | 业务实现次数 | 模块 |
|---|---|---|
| `JobHandler implements` | 11 | cronjob(8) + workflow(3) |
| `JobContextHolder` | 2 | cronjob |
| `JobLogger` | 3 | cronjob |
| `JobLoggerHolder` | 4 | cronjob |
| `ShardingContext` | 1 | cronjob |
| `MapContext` | 2 | cronjob |
| `ProcessResult` | 5 | cronjob |
| `MapTask` | 1 | cronjob |
| **`MapProcessor` implements** | **0** | 无 |
| **`MapReduceProcessor` implements` | **0** | 无 |

**关键观察**：
- ✅ `JobHandler` 是 domain 模块第二被广泛使用的能力，**2 个模块 11 处实现**。
- ❌ **`MapProcessor` / `MapReduceProcessor` 提供完整接口与上下文（含 README 示例），但全代码库 0 处实现**——属于典型"框架做好但业务没落地"。可能 cronjob 当前业务简单到不需要 MapReduce，但也可能是不清楚怎么用。

### 4.3 真核心：DAG 引擎（仅 cronjob）

| 类 | 业务使用 | 模块 |
|---|---|---|
| `DagInstanceStatus` | 4 | cronjob |
| `DagNodeStatus` | 2 | cronjob |
| `SpELConditionEvaluator` | 1 业务 + 2 内部 | cronjob |

**关键观察**：
- ✅ 仅 cronjob 使用，**独占性强**，定位清晰。
- ✅ 与 workflow 模块自研 BPMN 引擎无冲突——workflow 走 `BpmnXmlParser` / `FlowAdvancer`，cronjob 走 DAG，**架构边界清楚**。
- ⚠️ 风险点：cronjob pom 没有显式声明 domain 依赖，全靠 `ydsz-common-jdbc` 传递，未来 jdbc 调整会立刻崩溃。

### 4.4 半核心：状态枚举（BaseStatusEnum）

| 业务枚举 | 实现模块 | 业务场景 |
|---|---|---|
| `MessageStatusEnum` | ydsz-message | 消息状态机 |
| `AggregateBatchStatusEnum` | ydsz-message | 聚合批次状态机 |
| `ShareStatus` | ydsz-nextwiki | 分享状态机 |
| `TrashStatus` | ydsz-nextwiki | 回收站状态机 |

**关键观察**：
- ⚠️ **覆盖率仅 20%（2/10 模块）**。其它 8 个模块都有自己的状态字段，但都是 String/Integer 裸字段或自维护枚举。
- ❌ 缺失场景：用户/角色/部门（userinfo）、流程实例/任务（workflow）、项目/合同/风险（project）、Agent 启用/停用（agent）、规则启用/停用（literule）、字典启用/停用（system）。

### 4.5 半核心：树形（TreeNode/TreeBuilder）

| 业务模块 | 使用方式 | 评估 |
|---|---|---|
| ydsz-userinfo (Menu) | `TreeBuilder.buildSimple(MenuVO::getId, ...)` | 静态便捷方式，**未继承 TreeNode** |
| ydsz-userinfo (Department) | `TreeBuilder.buildSimple(DepartmentVO::getId, ...)` | 同上 |
| ydsz-system (菜单/字典) | **未使用**（菜单在 userinfo） | n/a |
| ydsz-agent (DagOrchestration) | 自研 DSL，**未用 TreeBuilder** | 自治系统 |

**关键观察**：
- ⚠️ `TreeNode` 提供的强类型递归 API（`findById`/`getAncestorIds`/`getDescendants`/`getLeafNodes`）**未发挥**。
- ⚠️ 业务实体（Menu、Department）继承的是 `MpBaseEntity`，**未继承 `TreeNode<T, ID>`**——失去了原生 children/findById 等强类型 API。
- 注解 `@TreeNode` 如果存在可用于静态分析/文档生成。

### 4.6 冷能力：领域事件（DomainEvent / ModuleEventTypes）

| 业务使用 | 模块 |
|---|---|
| `FlowWorkflowEvent extends DomainEvent` | ydsz-workflow（1） |
| `UnifiedAlertEvent extends DomainEvent` | ydsz-common-notify（基础设施） |
| `EventStore` 实现 | ydsz-common-event（Outbox 模式） |
| `ModuleEventTypes` 使用 | ydsz-common-notify（1） |

**关键观察**：
- ❌ **10 个业务模块中只有 1 个（workflow）真正发了领域事件**。
- ❌ **`ModuleEventTypes` 注册表**（README 中列举了 11 类事件常量：`WORKFLOW_INSTANCE_STARTED/COMPLETED/REJECTED`、`UNIFIED_ALERT`、`CRONJOB_EXECUTION_FAILED/TIMEOUT` 等）但只有 `common-notify` 引用 1 次。**注册表形同虚设**。
- ❌ 与 `ydsz-common-event` 的 `OutboxEventStore` 存在**双轨并存**问题：domain 走 Spring `ApplicationEvent`（同步/事务后），common-event 走 Outbox（异步/可靠投递）。两边都能用，但业务模块不知道用哪个。

### 4.7 冷能力：annotation 包

| 注解 | 业务模块使用 | 状态 |
|---|---|---|
| `@CreateAt` | 0 | ❌ 死注解（MP `@TableField(fill)` 替代） |
| `@CreatedBy` | 0 | ❌ 同上 |
| `@UpdateAt` | 0 | ❌ 同上 |
| `@UpdatedBy` | 0 | ❌ 同上 |
| `@Version` | 1（literule） | △ 冲突注解（与自定义 OptimisticLockInterceptor） |
| `@SoftDelete` | 0 | ❌ 死注解（MP 拦截器替代） |
| `@TenantId` | 0 | ❌ 死注解（jdbc MpBaseEntity.tenantId + common-tenant 替代） |
| `@DomainService` | 0 | ❌ 死注解（与 `@Service` 同义） |

**关键观察**：
- ❌ **8 个注解中 7 个业务模块使用次数为 0**。这是 ydsz-common-domain 模块存在感薄弱的核心原因——**提供了一组注解，但业务代码看不到它们**。
- ✅ 业务模块实际通过 `MpBaseEntity extends BaseEntity` 间接获得审计/版本/软删除/租户字段。功能等价，但**注解价值归零**。
- ❌ `@DomainService` 注解是 domain 模块定义的"领域服务语义标识"，**0 业务模块使用**。业务模块的 Service 类仍用 Spring `@Service`。
- ❌ `@Version` 在 literule 模块的 1 处使用是**历史错误**——README 第 458 行明说"不与自定义拦截器同时使用"，应删除。

### 4.8 边界能力：身份/数据范围/服务类型枚举

| 枚举 | 引用模块 | 评价 |
|---|---|---|
| `DataScopeType` | common-auth(4)、common-util(3)、common-jdbc(3)、common-feign(1) | ✅ 公共基础设施 |
| `IdentityType` | common-util(3)、common-feign(1) | ✅ |
| `ServiceType` | common-util(1)、common-web(3)、common-app(1) | ✅ |

**关键观察**：
- ✅ 这三个枚举**只被 common-* 子模块使用**，不跨业务模块渗透——是**合理的跨层抽象**。

### 4.9 配置/常量/健康检查

| 设施 | 业务模块使用 | 评价 |
|---|---|---|
| `TokenConstants` | 2（common-util） | ⚠️ 错位（与 domain 无关） |
| `FilterIgnoreConstant` | 3（common-auth/web） | ⚠️ 错位（属于 auth，不属于 domain） |
| `DomainProperties` | 0 业务 | ⚠️ 仅自身 SpEL 缓存配置 |
| `DomainAutoConfiguration` | 0 业务 | ⚠️ 1 个 Bean 注册 |
| `DomainHealthIndicator` | 0 业务 | ⚠️ 仅暴露 SpEL 缓存计数 |

**关键观察**：
- ❌ `TokenConstants` / `FilterIgnoreConstant` 与"领域"无关，**应迁出 domain 模块**到 `ydsz-common-auth` 或 `ydsz-common-web`。
- ⚠️ `DomainAutoConfiguration` + `DomainProperties` + `DomainHealthIndicator` 三件套**只为 SpEL 评估器服务**，但 SpEL 评估器**仅 cronjob 1 个业务模块使用**——配置面 vs 业务面严重倒挂。

---

## 五、依赖图谱：谁依赖了谁

### 5.1 显式依赖图（pom 声明）

```
                          ydsz-common-domain (本模块)
                                    ▲
                                    │ pom 显式声明
        ┌───────────────────────────┼───────────────────────────────┐
        │                           │                               │
ydsz-common-jdbc   ydsz-common-event   ydsz-common-notify   ydsz-common-app
ydsz-common-web    ydsz-common-util   ydsz-common-tenant   ydsz-common-search
ydsz-common-feign  ydsz-common-auth
        │
        │ pom 显式声明
        ▼
ydsz-system-domain   ydsz-userinfo-domain   ydsz-nextwiki-domain
ydsz-project-domain  ydsz-literule-domain   (5 个 business-domain 模块)
```

### 5.2 实际 import 图（隐式传递）

```
ydsz-common-domain
        ▲
        │ 显式 import
        ├── ydsz-common-jdbc（直接）
        ├── ydsz-common-event（直接）
        ├── ydsz-common-notify（直接）
        ├── ydsz-common-web（直接）
        ├── ydsz-common-util（直接）
        ├── ydsz-common-tenant（直接）
        ├── ydsz-common-search（直接）
        ├── ydsz-common-feign（直接）
        ├── ydsz-common-auth（直接）
        ├── ydsz-system-domain（直接）
        ├── ydsz-userinfo-domain（直接）
        ├── ydsz-nextwiki-domain（直接）
        ├── ydsz-project-domain（直接）
        ├── ydsz-literule-domain（直接，但 0 import）
        │
        │ 隐式传递 import（⚠️ 风险）
        ├── ydsz-cronjob-domain (30 import, 0 pom)
        ├── ydsz-cronjob-infra / server
        ├── ydsz-workflow-domain (5 import, 0 pom)
        ├── ydsz-workflow-infra / server
        ├── ydsz-message-domain (15 import, 0 pom)
        └── ydsz-message-infra / server
```

### 5.3 隐式传递依赖的传播路径

| 业务模块 | 入口依赖 | 中间依赖 | 终点 |
|---|---|---|---|
| ydsz-cronjob-* | ydsz-common-jdbc | 直接 | ydsz-common-domain |
| ydsz-workflow-* | ydsz-common-jdbc | 直接 | ydsz-common-domain |
| ydsz-message-* | ydsz-common-jdbc | 直接 | ydsz-common-domain |

业务模块对 `JobHandler` / `DomainEvent` / `PageQuery` / `BaseStatusEnum` 的 import 全部**通过 `MpBaseEntity extends BaseEntity` 这条路径**间接获得。

**风险**：
- 一旦 `ydsz-common-jdbc` 调整（移除 `ydsz-common-domain` 依赖），3 个业务模块立刻编译失败
- IDE 自动完成时，开发者不知道这些类是 `ydsz-common-domain` 提供的，认知错位

---

## 六、典型问题与改造项清单

### 6.1 🔴 P0：必须修复（影响编译稳定性或架构正确性）

#### 6.1.1 ydsz-cronjob / ydsz-workflow / ydsz-message 隐式依赖（编译脆弱性）

**问题**：3 个模块的源码深度使用 ydsz-common-domain（**50 处 import + 11 个 `implements`**），但 pom.xml **没有任何一处** 声明 `ydsz-common-domain` 依赖。完全依赖 `ydsz-common-jdbc → ydsz-common-domain` 的传递链。

**风险**：
- 任意调整 jdbc 依赖关系即可能连锁崩溃
- 新人无法从 pom 看到"本模块依赖了 domain"，认知错位
- `mvn dependency:tree` 显示传递路径冗长，定位问题慢

**改造项**：
```xml
<!-- ydsz-cronjob/ydsz-cronjob-domain/pom.xml -->
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-domain</artifactId>
</dependency>
<!-- 同时在 ydsz-cronjob-{infra,server,web} 中按需声明 -->
```

**适用范围**：ydsz-cronjob-{domain,infra,server,web}、ydsz-workflow-{domain,infra,server,web}、ydsz-message-{domain,infra,server,web}

**验收标准**：`mvn dependency:tree | grep ydsz-common-domain` 在三个模块的 domain 子模块中**直接出现**（不再仅通过 jdbc 中转）。

#### 6.1.2 ydsz-literule 死依赖清理

**问题**：`ydsz-literule-domain/pom.xml:21` 显式声明 `ydsz-common-domain`，但**全代码库 0 处 import**。唯一相关引用 `RuleDefinitionDO.java:93` 使用 `@Version` 注解——但 `@Version` 由 `ydsz-common-jdbc/MpBaseEntity` 的自定义拦截器处理，**直接用 `com.njydsz.common.domain.annotation.Version` 与 README 第 458 行的设计原则相悖**（"不使用 @Version 注解，避免双重处理"）。

**改造项**：
1. 删除 `RuleDefinitionDO.version` 字段上的 `@Version` 注解（或删除整个字段，由 `MpBaseEntity.revision` 替代）
2. 删除 `ydsz-literule-domain/pom.xml` 中对 `ydsz-common-domain` 的依赖声明
3. 如未来 literule 需要状态机/分页，再按需重新引入

**验收标准**：`grep -rn "import com.njydsz.common.domain" ydsz-literule/` 输出为空。

#### 6.1.3 annotation 包 7 个死注解处理

**问题**：`@CreateAt` / `@CreatedBy` / `@UpdateAt` / `@UpdatedBy` / `@Version` / `@SoftDelete` / `@TenantId` / `@DomainService` 共 8 个注解，**业务模块使用次数：0 + 0 + 0 + 0 + 1 + 0 + 0 + 0 = 1**。

**改造项**（三选一）：

**方案 A（推荐）：保留必要、删除冗余**
- 保留 `@DomainService`（语义化标注，价值大于冗余）
- 删除 `@Version`（与 jdbc 拦截器冲突）
- 删除 `@SoftDelete`（同上）
- 删除 `@TenantId`（jdbc MpBaseEntity 已含）
- 删除 `@CreateAt` / `@CreatedBy` / `@UpdateAt` / `@UpdatedBy`（MP `@TableField(fill)` 替代）

**方案 B：将注解下沉到 ydsz-common-jdbc**
- 在 `MpBaseEntity` 的字段上添加 `@CreateAt` / `@CreatedBy` / `@UpdateAt` / `@UpdatedBy`（文档化价值）
- 同时为新业务实体提供"加注解可获得提示"的能力

**方案 C：彻底删除 annotation 包**
- 若 8 个注解都不需要，整体删除该包
- 简化模块结构

**推荐**：方案 A + 部分 B。

### 6.2 🟡 P1：建议修复（影响架构一致性和能力落地）

#### 6.2.1 状态枚举 BaseStatusEnum 覆盖率从 20% → 80%

**问题**：10 个业务模块中仅 2 个（message、nextwiki）实现了 `BaseStatusEnum`，其余 8 个模块的状态字段用 String/Integer 或自维护枚举，**状态流转校验（canTransitTo / isTerminal / requireTransitTo）能力未被利用**。

**改造项**：
- **ydsz-system**：`DictType` 启用/停用 → `DictTypeStatusEnum implements BaseStatusEnum<DictTypeStatusEnum>`
- **ydsz-system**：`Config` 启用/停用 → 同上模式
- **ydsz-userinfo**：`User` 启用/停用、角色/部门启用/停用 → 实现
- **ydsz-workflow**：`FlowInstance` / `FlowTask` 状态（运行/挂起/终止/已完成）→ 实现
- **ydsz-project**：`Project` 状态、`Contract` 状态、`Risk` 状态 → 实现
- **ydsz-agent**：`Agent` 启用/停用、`Conversation` 状态 → 实现
- **ydsz-cronjob**：`Job` 状态、`JobTask` 状态、DAG 实例/节点状态 → 实现
- **ydsz-literule**：`RuleDefinition` 状态、规则生效状态 → 实现
- 优先级：先做 cronjob / workflow / system（高业务价值），后做 userinfo / project / agent

**配套**：
- 在 `ydsz-common-domain` 提供 `BaseStatusEnum` 的额外工具方法（如批量校验、状态机图可视化辅助）
- 在 README 增加"状态机落地指南"章节

#### 6.2.2 DomainEvent 体系贯通（与 common-event Outbox 模式统一）

**问题**：
- `ydsz-common-domain/DomainEvent`（走 Spring `ApplicationEvent` 同步/事务后）
- `ydsz-common-event/OutboxEventStore`（走 Outbox 异步/可靠投递）
- 业务模块（cronjob 失败、workflow 完成、userinfo 用户变更、agent 任务完成）当前**都没有走任何一种事件机制**

**改造项**：
1. **决策统一**：明确一种主推模式。推荐 **`DomainEvent`（轻量同步事件）+ `@TransactionalEventListener(AFTER_COMMIT)`** 为主，**Outbox 模式**为对可靠性要求高的场景（如金融、计费）补充。
2. **扩展 `ModuleEventTypes`**：按业务模块补全事件类型常量：
   - `USER_CREATED` / `USER_UPDATED` / `USER_DELETED`（userinfo）
   - `ROLE_CHANGED` / `PERMISSION_CHANGED`（userinfo）
   - `PROJECT_CREATED` / `PROJECT_STATUS_CHANGED`（project）
   - `AGENT_EXECUTION_STARTED` / `AGENT_EXECUTION_COMPLETED` / `AGENT_EXECUTION_FAILED`（agent）
   - `RULE_DEFINITION_PUBLISHED` / `RULE_DEFINITION_DEPRECATED`（literule）
   - `JOB_EXECUTION_FAILED` / `JOB_EXECUTION_TIMEOUT`（cronjob）
3. **每个业务模块至少发 1 个领域事件**作为试点（如 userinfo 发 `UserCreatedEvent`）
4. 在 `ydsz-common-domain` 文档增加"事件命名规范"（`<Domain><Action>[PastTense]`）

**验收标准**：
- `ModuleEventTypes` 常量从 11 个扩展到 30+ 个
- 业务模块 import `DomainEvent` / `ModuleEventTypes` 覆盖率 ≥ 50%

#### 6.2.3 PageResult / BaseDTO 在 project / message / workflow 的统一

**问题**：
- `ydsz-project` Service 层部分返回 `IPage<T>`（MyBatis-Plus 原生），未用 `PageResult`
- `ydsz-message` Service 层同上
- `ydsz-workflow` 全部 DTO 自定义，无 `BaseDTO`
- `ydsz-userinfo` 全部 DTO 自定义，无 `BaseDTO`

**改造项**：
- 在 `ydsz-project` / `ydsz-message` / `ydsz-cronjob` 的 Service 层强制将 `IPage<T>` 转换为 `PageResult<T>`
- 在 `ydsz-userinfo` / `ydsz-workflow` / `ydsz-agent` / `ydsz-cronjob` 推广 `BaseDTO` 使用
- 配套在 `BaseDTO` 上提供 Jackson 序列化配置（在 common-json 中已有 `YdszJsonFormat`/`YdszJsonField` 注解的复用）

**验收标准**：
- 所有 Service 层分页返回类型统一为 `PageResult<T>`
- 业务模块 DTO 继承 `BaseDTO` 覆盖率 ≥ 60%

#### 6.2.4 TreeNode 强类型 API 落地（userinfo 菜单/部门实体）

**问题**：
- ydsz-userinfo 的 Menu / Department 实体继承 `MpBaseEntity`，**未继承 `TreeNode<T, ID>`**
- 当前用 `TreeBuilder.buildSimple(VO::getId, ...)` 静态方式构建树，**强类型 API（`findById` / `getAncestorIds` / `getDescendants` / `getLeafNodes`）未发挥**
- ydsz-system 字典树形结构（如果存在）也未使用

**改造项**：
- 让 `Menu extends BaseString + TreeNode<Menu, String>`（**双继承不可行**，需要调整 `Menu` 的继承策略）
- 方案 A（推荐）：Menu 的持久化层继承 `MpBaseEntity`，**业务模型层**单独定义 `MenuNode extends TreeNode<MenuNode, String>`，由 Repository 返回 `List<MenuNode>`
- 方案 B：让 `Menu` 实体直接 `extends MpBaseEntity implements TreeNodeCompatible<Menu, String>`——需要 domain 新增 `TreeNodeCompatible` 接口

**验收标准**：
- `ydsz-userinfo` 至少有 1 处用 `treeBuilder.findById(...)` / `getDescendants(...)` 替代手写递归
- 强类型 API 文档化

#### 6.2.5 ydsz-agent 模块从 0% → 30% 集成

**问题**：ydsz-agent 是 10 个业务模块中**唯一 0 import 任何 ydsz-common-domain 能力**的模块，但其业务复杂度（多智能体、对话、长任务）反而最需要分页/状态枚举/事件。

**改造项**：
- `Agent` 实体状态 → `AgentStatusEnum implements BaseStatusEnum`
- `Conversation` 消息列表 → `extends PageQuery`
- `AgentExecution` 完成/失败 → 发 `AgentExecutionCompletedEvent extends DomainEvent`
- `Agent` / `Tool` 列表查询 DTO → 继承 `BaseDTO`

**验收标准**：ydsz-agent 引用 ydsz-common-domain import 次数从 0 提升到 ≥ 5

### 6.3 🟢 P2：可选优化（影响 API 一致性和长期可维护性）

#### 6.3.1 MapProcessor / MapReduceProcessor 业务落地

**问题**：`MapProcessor` / `MapReduceProcessor` 提供完整接口与示例，但全代码库 0 实现。可能是 cronjob 当前业务用不到，但也可能是**缺少业务场景示范**。

**改造项**：
- 在 `ydsz-cronjob-domain` 提供 1 个参考实现（如 `AgentDailyStatMapReduceJob`）作为模板
- 在 README 增加"MapReduce 适用场景"指南
- 标识 cronjob 中**已存在的可改造场景**（如"按租户统计" → MapReduce）

**验收标准**：cronjob 至少有 1 个真实 `implements MapReduceProcessor` 类

#### 6.3.2 TokenConstants / FilterIgnoreConstant 迁出 domain 模块

**问题**：`TokenConstants`（2 处使用，在 common-util）和 `FilterIgnoreConstant`（3 处使用，在 common-auth/common-web）与"领域"无关，**错位在 domain 模块**。

**改造项**：
- `TokenConstants` → 迁到 `ydsz-common-util`（或保留在 domain，作为常量集合）
- `FilterIgnoreConstant` → 迁到 `ydsz-common-web`（与 `FilterIgnoreProperties` 同一模块）

**验收标准**：`grep -rn "TokenConstants\|FilterIgnoreConstant" ydsz-common-domain` 仅保留定义文件

#### 6.3.3 DomainAutoConfiguration 简化

**问题**：`DomainAutoConfiguration` + `DomainProperties` + `DomainHealthIndicator` 三件套**只为 SpEL 评估器服务**，但 SpEL 评估器仅 cronjob 使用，配置面 vs 业务面倒挂。

**改造项**：
- 将 SpEL 相关配置直接并入 `ydsz-cronjob` 模块（cronjob 才是唯一使用方）
- 或在 domain 模块中**保留接口契约**（`SpELConditionEvaluator`），但将 Bean 注册移到 cronjob

#### 6.3.4 README 能力图谱同步更新

**问题**：现有 README 列出了 11 个子包，但**没有标注每个能力的实际使用度**。开发者难以快速判断"哪些是稳定核心、哪些是试验性、哪些是死代码"。

**改造项**：
- 在 README 增加"使用度热力图"章节
- 每个子包增加 `使用度: ⭐⭐⭐ / ⭐⭐ / ⭐` 标记
- 增加"推荐 / 不推荐"标签（如 `@DomainService` 推荐用，`@Version` 不推荐用）

---

## 七、改造路线图（90 天分阶段）

### 第一阶段（Week 1-2）：基础设施修复
- [ ] P0-1: ydsz-cronjob / ydsz-workflow / ydsz-message 显式依赖（**3 个模块 × 4 个子模块 = 12 个 pom**）
- [ ] P0-2: ydsz-literule 死依赖清理（**1 个 pom + 1 个文件**）
- [ ] P0-3: annotation 包 7 个死注解处理（**8 个注解文件**）

**预期收益**：编译稳定性 100% 提升，模块边界清晰化

### 第二阶段（Week 3-6）：能力贯通（高频价值）
- [ ] P1-1: BaseStatusEnum 覆盖 8 个新业务模块（**预计 15-20 个新枚举类**）
- [ ] P1-2: DomainEvent 体系贯通 + ModuleEventTypes 扩展（**30+ 事件常量，5+ 业务事件**）
- [ ] P1-3: PageResult / BaseDTO 架构统一（**5 个模块**

**预期收益**：状态机/事件/分页三大基础设施全面铺开

### 第三阶段（Week 7-10）：中频能力落地
- [ ] P1-4: TreeNode 强类型 API 落地（userinfo 菜单/部门）
- [ ] P1-5: ydsz-agent 集成（5+ import）
- [ ] P2-1: MapReduceProcessor 参考实现

**预期收益**：Tree/Agent 业务深度集成

### 第四阶段（Week 11-12）：文档与治理
- [ ] P2-2: TokenConstants / FilterIgnoreConstant 迁出
- [ ] P2-3: DomainAutoConfiguration 简化
- [ ] P2-4: README 能力图谱 + 使用度热力图

**预期收益**：模块结构清晰化 + 文档可视化

---

## 八、关键 KPI 验收

| 指标 | 当前 | 目标 | 测量方式 |
|---|---|---|---|
| 隐式依赖模块数 | 3 | 0 | `mvn dependency:tree` |
| 死依赖模块数 | 1 | 0 | `grep` |
| 死注解数 | 7 | 0-1 | grep 业务模块 import |
| BaseStatusEnum 业务模块覆盖率 | 20% | 80% | grep `implements BaseStatusEnum` |
| DomainEvent 业务模块覆盖率 | 10% | 50% | grep `extends DomainEvent` |
| PageResult 业务模块使用率 | 50% | 100% | grep Service 层返回类型 |
| BaseDTO 业务模块覆盖率 | 20% | 60% | grep `extends BaseDTO` |
| TreeNode 强类型 API 使用 | 0 | ≥3 | grep `treeBuilder.findById/getDescendants` |
| ModuleEventTypes 常量扩展 | 11 | 30+ | 静态计数 |

---

## 九、附录：引用溯源（精选）

### 9.1 业务模块真实 import（按模块汇总）

| 模块 | 关键引用类 | 关键文件 |
|---|---|---|
| ydsz-system | `PageQuery`, `PageResult`, `BaseDTO` | `DictPageQuery:42`, `ConfigPageQuery:42`, `AppInfoDTO`, `DictItemDTO` 等 |
| ydsz-userinfo | `PageQuery` (×7), `PageResult` (×3), `TreeBuilder` (×2) | `MenuServiceImpl:174`, `DepartmentServiceImpl:193`, 5 个 query 类 |
| ydsz-workflow | `PageQuery` (×1), `DomainEvent` (×1), `JobHandler` (×3) | `FlowCcQueryDTO:23`, `FlowWorkflowEvent`, `FlowTimeoutJobHandler` 等 |
| ydsz-cronjob | `JobHandler` (×8), `DagInstanceStatus` (×4), `DagNodeStatus` (×2), `SpELConditionEvaluator` (×1) | `JobDagServiceImpl`, `DagInstanceExecutor` 等 |
| ydsz-project | `PageQuery` (×1), `BaseDTO` (×1) | `ProjectInitiationPageQuery:16`, `ProjectInitiationPostDTO` |
| ydsz-literule | **0** | （pom 声明但 0 import） |
| ydsz-agent | **0** | （无任何 import） |
| ydsz-nextwiki | `PageResult` (×7), `BaseStatusEnum` (×2) | `NextwikiEnums.ShareStatus`, `NextwikiEnums.TrashStatus`, Repository/Service/Controller |
| ydsz-message | `PageQuery` (×4), `BaseStatusEnum` (×2) | `TemplateQueryDTO`, `NotificationQueryDTO`, `MessageStatusEnum`, `AggregateBatchStatusEnum` |

### 9.2 common-* 子模块的"中介"引用

| 子模块 | 引用 domain 的类 | 作用 |
|---|---|---|
| ydsz-common-jdbc | `BaseEntity`, `BaseAuditEntity`, `BaseIdEntity` | `MpBaseEntity extends BaseEntity` |
| ydsz-common-event | `DomainEvent` (×2) | 事件桥接 |
| ydsz-common-notify | `DomainEvent` (×1), `ModuleEventTypes` (×1) | 告警事件 |
| ydsz-common-web | `ServiceType` (×3), `FilterIgnoreConstant` (×1) | Web 基础设施 |
| ydsz-common-util | `DataScopeType` (×3), `IdentityType` (×3), `ServiceType` (×1), `TokenConstants` (×2) | 工具类 |
| ydsz-common-auth | `DataScopeType` (×4), `FilterIgnoreConstant` (×2) | 权限基础设施 |
| ydsz-common-feign | `DataScopeType` (×1), `IdentityType` (×1) | Feign 拦截器 |
| ydsz-common-app | `ServiceType` (×1) | 应用聚合 |
| ydsz-common-tenant | 通过 transitive | SPI 集成 |

---

**报告结束。** 全部数据来自源码静态扫描，可逐条复现验证。
