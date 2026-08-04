# ydsz-common-domain 过度设计评估报告

> 审计对象：`ydsz-backend/ydsz-common/ydsz-common-domain`（43 个源文件，5147 行 Java 代码）
> 审计日期：2026-08-04
> 审计范围：实体基类体系、领域事件、Query 模型、Tree / DAG、Job 框架、注解、Enum、常量
> 对标基线：阿里 / 腾讯 / 字节内部 DDD 实践 + MyBatis-Plus / Spring 生态主流规范
> 项目前提：本项目为内网自研框架（参考 `PROJECT_CAPABILITY_MODEL.md`），评估方向聚焦**内部精简、拆分、删除冗余**，不建议整体替换为外部框架

---

## 一、模块画像与使用度审计（数据说话）

### 1.1 模块体量

| 子包 | 文件数 | 实际行数（含注释） | 用途定位 |
|---|---|---|---|
| `entity` | 12 | ≈ 950 行 | 实体基类 + 标记接口 |
| `annotation` | 8 | ≈ 200 行 | 8 个字段/类注解 |
| `event` | 3 | ≈ 600 行 | 领域事件基类 + 类型注册表 + EventStore |
| `query` | 3 | ≈ 800 行 | 分页/查询模型 |
| `tree` | 2 | ≈ 1300 行 | 树节点 + 构建器 |
| `dag` | 3 | ≈ 250 行 | DAG 状态 + SpEL 评估器 |
| `job` | 10 | ≈ 350 行 | JobHandler / MapReduce 契约 + ThreadLocal |
| `enums` | 4 | ≈ 320 行 | 通用枚举 + 状态机 + 身份/数据范围/服务类型 |
| `constant` / `dto` / `config` / `health` | 8 | ≈ 400 行 | 常量/DTO/自动配置/健康检查 |

### 1.2 业务模块引用频次（来自 grep 实际统计）

| 设施 | 引用次数 | 实际业务方 | 健康度 |
|---|---|---|---|
| `PageQuery` / `BaseQuery` / `PageResult` | **178** | common-audit、common-auth、message、workflow、cronjob 等 | ✅ **真核心**，无可争议 |
| `BaseEntity` / `BaseLong` / `BaseString` / `LogBase` | **0**（业务模块 0 直接继承，只通过 `MpBaseEntity` 间接） | 无直接业务使用者 | ⚠️ **孤儿基类** |
| `Persistable` / `Auditable` / `Versionable` / `SoftDeletable`（接口） | **0** | 无业务模块 `implements` | ⚠️ **空接口** |
| `TreeNode` / `TreeBuilder` | **3** | nextwiki Folder / userinfo Menu / userinfo Department | ✅ 必要但实现过度 |
| `DomainEvent` | **4** | common-event（Outbox）/ common-notify UnifiedAlertEvent / workflow FlowWorkflowEvent | ⚠️ 低使用率 + 与 Outbox 双轨 |
| `EventStore`（接口） | **0 实现** | 无实现方，commons-event 有自己的 `OutboxEventStore` | ❌ **死接口** |
| `SpELConditionEvaluator` / `DagInstanceStatus` / `DagNodeStatus` | **3** | cronjob-server 内部 DAG | ⚠️ **越界**（应归 cronjob） |
| `JobHandler` / `JobContextHolder` / `JobLogger` / `MapReduceProcessor` / `MapContext` / `MapProcessor` / `ProcessResult` / `ShardingContext` / `MapTask` / `JobLoggerHolder` | **≈15**（cronjob-server） | cronjob 内部 | ⚠️ **越界**（应归 cronjob） |
| `@DomainService` 注解 | **0** | 无业务方 | ❌ **死注解** |
| `@CreatedBy` / `@CreateAt` / `@UpdatedBy` / `@UpdateAt` | **0**（业务模块 0 处；均由 MP `@TableField(fill)` 处理） | 无业务方 | ❌ **死注解** |
| `@Version` 注解 | **1**（literule RuleDefinitionDO） | 与 README 第 458 行「不使用 @Version」相悖 | ❌ **冲突注解** |
| `@SoftDelete` 注解 | **0**（由 MP 自定义拦截器按字段名处理） | 无业务方 | ❌ **死注解** |
| `@TenantId` 注解 | **0** | 无业务方 | ❌ **死注解** |
| `BaseStatusEnum` | **3** 枚举实现 | nextwiki、message MessageStatus、message AggregateBatchStatus | ✅ 必要但接口可收紧 |
| `IdentityType` / `ServiceType` / `DataScopeType` | 定义在本模块但业务模块使用 | 身份/服务/数据范围 | ⚠️ **跨层污染** |
| `TokenConstants` / `FilterIgnoreConstant` | **0 处**（仅本模块定义） | 与 domain 无关 | ❌ **错位** |
| `BaseDTO` | **0**（业务模块都直接用 `@Data`/`@SuperBuilder` 自建 DTO） | 无业务方 | ⚠️ **过度抽象** |
| `DomainAutoConfiguration` / `DomainProperties` / `DomainHealthIndicator` | 注册 SpEL 评估器、健康指标 | cronjob 强依赖 | ⚠️ **配置 + 1 个 Bean** |

### 1.3 README 自承的"历史残留"（已删除但保留元数据）

> 见 README 第 466 行：
> "additional-spring-configuration-metadata.json 中包含 `ydsz.domain.event.async-enabled` / `ydsz.domain.event.default-phase` / `ydsz.tree.lazy.*` 等历史配置项的描述，但对应的 `DomainEventPublisher` / `EventStore` / `EntityCapabilities` / `Specification` / `BaseValueObject` / `BaseConverter` / `LazyTreeNode` / `CursorPageResult` / `AggregateRoot` / `RootEntity` / `TenantAware` / `BaseVO` / `TypeEnumConverterFactory` 等组件已在简化重构中删除"

也就是说：**README 已经知道一半的清理工作没做完**。这是一面镜子。

---

## 二、过度设计的 6 大病灶

### 病灶 1：实体基类"双层寄生"——基类被 Mp 实体架空

**事实**：
- `BaseEntity` / `BaseLong` / `BaseString` / `LogBase` 是"理论基类"
- 业务模块实际继承链：`BusinessEntity → MpBaseEntity (jdbc) → BaseEntity (domain)`
- 也就是说 `BaseEntity` 不直接面对业务，只作为 `MpBaseEntity` 的"父壳"
- 任何业务实体要新增字段（比如 `tenantId`），改动要横跨两个模块（domain + jdbc）

**问题**：
- **抽象反向**：业务团队写实体时从不关心 `BaseEntity`，它变成了 jdbc 模块的私有"继承锚点"
- **跨模块依赖**：让 `ydsz-common-domain` 间接承担了"jdbc 包的私有内部基类"角色，定位模糊
- **审计字段重复声明**：`createdBy/createdAt/updatedBy/updatedAt/revision/deleted/status/tenantId` 在 `BaseAuditEntity` 和 `MpBaseAuditEntity`、`BaseEntity` 和 `MpBaseEntity` 上**几乎完全重复**

**对比大厂/主流做法**：
- 阿里/TapTap：`MyBatis-Plus BaseEntity` 直接作为单一基类（无中间层）
- Spring Data JPA：基类直接带 `@MappedSuperclass` 与 `@EntityListeners`
- 字节：业务实体内联字段，避免多级继承带来"字段在哪个父类"的心智负担

### 病灶 2：8 个注解中 7 个是装饰品

**事实清单**：

| 注解 | 真实作用 | 实际使用者 |
|---|---|---|
| `@CreatedBy` | 提示"这是创建人字段" | 0 处（由 MP `FieldFill.INSERT` 在 `MpBaseEntity.createdBy` 上承担） |
| `@CreateAt` | 同上 | 0 处 |
| `@UpdatedBy` | 同上 | 0 处 |
| `@UpdateAt` | 同上 | 0 处 |
| `@Version` | 提示乐观锁字段 | 1 处，且与 README 第 458 行"不使用 @Version"明确冲突；拦截器按"revision 列"约定处理，与注解**无关** |
| `@SoftDelete` | 提示软删除 | 0 处（由 `LogicalDeleteInterceptor` 按"deleted 列"处理） |
| `@TenantId` | 提示租户字段 | 0 处（`MpBaseEntity.tenantId` 已有 `@TableField("tenant_id")`） |
| `@DomainService` | 标识领域服务 = 继承 `@Component` | 0 处（业务模块用 Spring `@Service`） |

**问题**：这些注解只增加编译期负担，不改变运行期行为。它们存在 5 年（README 1.0.0 ~ 1.3.0）+ 永远不被使用 = **纯过设计**。

### 病灶 3：DAG 引擎与 Job 框架越界，污染领域基座

**事实**：
- `SpELConditionEvaluator` + `DagInstanceStatus` + `DagNodeStatus` 这 3 个类只为 cronjob-server 的 DAG 引擎服务
- `JobHandler` / `MapReduceProcessor` / `JobContextHolder` / `MapContext` / `ProcessResult` / `ShardingContext` / `MapTask` / `JobLogger` / `JobLoggerHolder` 这 9 个类只为 cronjob 框架服务
- 它们被塞进 `ydsz-common-domain`，造成：
  - 任何引入 `ydsz-common-domain` 的模块会**被迫依赖这些非领域设施的 API**（强耦合）
  - cronjob 模块不依赖 `ydsz-common-domain` 也能工作（只依赖 jdbc/core/json）
  - 也就是说：**这些类在 domain 模块里没有"领域"逻辑，只是借位**

**对标主流做法**：
- 阿里 `scheduleX`、`XXL-Job`：Job 契约在 `schedule-domain` 模块，不污染 common-domain
- JDK：`ExecutorService` / `Future` 在 `java.util.concurrent`，绝不放进 `java.lang`
- 字节内部调度框架：Job/DAG 与"领域"分层清晰

### 病灶 4：TreeBuilder 700 行 + LRU + 缓存 + 双重检查锁，70 行就能解决 95% 业务需求

**事实**：
- `TreeBuilder.java` ≈ 700 行：含配置字段、缓存、链式 build、DFS/BFS、循环引用检测、迭代阈值（1 万节点切换迭代模式）、路径生成（按 level 排序消除隐含依赖）、多根、idExtractor/parentIdExtractor 函数式配置、`buildSimple` 静态便捷方法
- 业务实际引用 3 处：Menu、Department、Folder —— 平均节点数 < 200
- 提供"双重检查锁（DCL）+ ReentrantLock"的原因写在第 93 行注释："替代 synchronized(this) 以避免 JDK21 虚拟线程 Pinning"
- 然而：当前 Spring Boot 版本是 4.1.0 + JDK？，业务调用频度低到根本不需要多线程场景的并发锁

**问题**：
- **复杂度远超 ROI**：对 Menu 这种 < 100 节点的递归构建，根本不需要 LRU、不需要 DCL、不需要路径缓存
- **大量"防御性实现"**：JDK21 虚拟线程、1 万节点栈溢出 —— Menu/Department 都碰不到
- **可读性惩罚**：新人读到第 200 行已经放弃
- **测试噩梦**：每个 corner case（多根、循环引用、迭代阈值）都需要独立测试

**对标主流做法**：
- Apache Commons Collections `MultiValuedMap` + 简单两遍循环
- Hutool `TreeUtil.build()`
- 阿里 SDM：单纯递归 + 一次 pass

### 病灶 5：PageQuery 排序字段"字符串存储 + 多次重新解析"

**事实**（`PageQuery.java`）：
```java
// orderItems 是 List<String>，存储 "created_at DESC"
private List<String> orderItems = new ArrayList<>();

// getOrderSql() 每次都重新解析
public String getOrderSql() {
    if (orderItems == null || orderItems.isEmpty()) return "";
    List<String> safeItems = new ArrayList<>();
    for (String item : orderItems) {
        String trimmed = item.trim();
        String column = trimmed.replaceAll("\\s+(ASC|DESC)$", "").trim();
        if (SAFE_COLUMN_PATTERN.matcher(column).matches() && isColumnAllowed(column)) {
            safeItems.add(trimmed);   // 重新塞回去——你解析了个寂寞
        }
    }
    ...
}
```

**3 个问题**：
1. **数据模型反范式**：用字符串表示"列 + 方向"二元组，应该用 `record OrderItem(String column, Direction dir)`
2. **每次读取都解析**：N+1 次调用 → N+1 次正则 + split + 校验，浪费 CPU
3. **多入口不一致**：`setOrderItems()`、`setOrderBy()`、`addOrder()`、`addAscOrder()`、`addDescOrder()` 5 个入口都校验一遍，逻辑分散

### 病灶 6：DomainEvent 11 字段 + 12 个 Builder setter，业务模块几乎不用

**事实**：
- `DomainEvent` 自身 11 final 字段（eventId/occurredAt/eventType/aggregateId/aggregateType/version/tenantId/userId/traceId/metadata）+ Builder 12 setter + 自动从 `RequestContext` 填充
- 实际业务使用仅 4 处：
  - common-event 的 Outbox 体系
  - common-notify 的 UnifiedAlertEvent
  - workflow 的 FlowWorkflowEvent
- 业务模块压根不继承 DomainEvent —— 它们直接用 Spring `ApplicationEvent`

**问题**：
- 自动填充 `tenantId/userId/traceId` 与 Spring `RequestContext` 的用途重叠，构造一个事件就触发跨 ThreadLocal 读取
- `aggregateType/aggregateId` 是 event sourcing 概念，业务根本没做 event sourcing
- `version` 字段从 1 开始，与业务乐观锁 `revision` 字段语义混淆
- 双重身份：既是 Spring `ApplicationEvent`（行为），又是 DDD Aggregate Event（语义）—— 两套契约同时承担

**对标主流做法**：阿里 `event-sdk` 等都是**薄壳层**（仅暴露 eventId/eventType/occurredAt + payload）；Tenant/User/Trace 通过 MDC/Spring `RequestContext` 自动传透，不需要塞进事件 payload。

### 病灶 7（加分项）：跨层常量 / DTO 污染

- **`TokenConstants`**：`AUTHENTICATION = "Authorization"` —— 与 common-auth 重复（HeaderConstants 里就有）
- **`FilterIgnoreConstant`**：定义过滤器白名单，但本质是 Gateway/Web 模块的配置项
- **`IdentityType / ServiceType / DataScopeType`**：身份/服务类型/数据范围维度，应归 common-auth / common-tenant
- **`BaseDTO`**：operatorId/operatorName/requestId/traceId/tenantId/language/source/remark 8 字段 —— 与 `RequestContext` 重复，业务模块继承 `BaseDTO` 等于绕过 RequestContext

---

## 三、对标行业主流规范的核心差距

> 本节按主流框架（不指明外部依赖名）总结最佳实践，与本项目自研做法对比。

| 设计维度 | 主流做法 | 当前做法 | 差距 |
|---|---|---|---|
| **实体基类层级** | 1~2 级（id + audit），base class 在 ORM 模块内 | 4 级（domain 层 id/audit/entity/long/string + jdbc MpBaseEntity/MpBaseAuditEntity） | 层级多 1 倍，跨模块 |
| **审计字段填充** | ORM 原生注解 + `MetaObjectHandler` 一处搞定 | 2 套（domain 注解 + jdbc `@TableField(fill)`）重复声明 | 重复实现 |
| **乐观锁** | ORM 原生 `@Version` 或拦截器（单选一）| 自研拦截器 + 自定义 `@Version` 注解（README 自承冲突） | **配置 drift 风险** |
| **软删除** | ORM 原生 `@TableLogic` 或拦截器（单选一）| 自研拦截器 + 自定义 `@SoftDelete` 注解（同样冲突） | 同上 |
| **领域事件** | 薄壳 + MDC 透传 | 11 字段 + Builder + 跨 ThreadLocal 自动填充 | 过度包装 |
| **Job/MapReduce 契约** | 在调度框架域（如 `schedule-domain`）| 在 `common-domain`（跨模块被引用） | 越界 |
| **DAG 引擎** | 在流程框架域（如 `workflow-domain` 或 `cronjob-domain`）| 在 `common-domain` | 越界 |
| **分页/排序** | `PageRequest` + `OrderItem` 结构化 | `PageQuery` + `List<String>`（运行时重新解析） | 反范式 |
| **树构建** | 简单递归 + 二遍扫描，< 100 行 | 700 行（缓存 + DCL + LRU + 迭代切换 + 配置 builder） | 复杂度超 ROI |
| **常量分层** | 按层（common-auth 持有 auth/header，常用工具在 core）| domain 模块持有 token/filter/身份等常量 | 错位 |
| **历史配置保留** | 废弃配置项应当一次性清理 | `additional-spring-configuration-metadata.json` 保留废弃配置 | 已经是过度维护 |

---

## 四、可落地的优化建议（按 ROI 分级）

### P0（立即清理）—— 风险最低、收益最高

#### P0-1 删除 7 个无引用注解
**文件**：
- `annotation/DomainService.java`
- `annotation/CreateAt.java`
- `annotation/CreatedBy.java`
- `annotation/UpdateAt.java`
- `annotation/UpdatedBy.java`
- `annotation/SoftDelete.java`
- `annotation/TenantId.java`
- `annotation/Version.java`（与 README 冲突，但保留 1 个 lombok/docs 兜底字段映射会留下小坑，可一并删除，业务模块全改走字段名约定）

**实施**：
```bash
# 1. 全工程检索每个注解的真实引用
grep -rn "@DomainService\|@SoftDelete\|@Version\|@TenantId\|@CreatedBy\|@CreateAt\|@UpdatedBy\|@UpdateAt" \
  --include="*.java" /d/Code/ydsz/ydsz-pmis/ydsz-backend \
  | grep -v "/target/" | grep -v "/ydsz-common-domain/"

# 2. 确认 0 引用后整文件删除，并清理 README 第 53~62 行表格
```

**收益**：减少 8 个源文件、~200 行代码、README 一张表消失；新人不必面对"这注解到底干嘛用"的困惑。

#### P0-2 删除 `EventStore` 接口（无实现）
**文件**：`event/EventStore.java`（88 行）

**事实**：
- 工程内 0 个实现
- 持久化事件走 `ydsz-common-event` 的 `OutboxEventStore`，是两套不兼容体系
- README 已注明"组件已在简化重构中删除"

**实施**：直接删除。如果未来真的需要 ES，可在新模块重建接口（按届时设计）。

#### P0-3 删除 `TokenConstants` 与 `FilterIgnoreConstant`
**文件**：
- `constant/TokenConstants.java`
- `config/FilterIgnoreConstant.java`

**事实**：
- 这两个常量类与 `domain` 半毛钱关系都没有
- HeaderConstants 已在 common-core 中承载 token 相关常量
- 过滤器白名单应在 Gateway 模块自带

**实施**：
1. 把 `TokenConstants` 整段挪到 `common-auth`（或删除，业务用 HeaderConstants）
2. 把 `FilterIgnoreConstant` 删除，保留 default 配置在 Gateway 模块的 application.yml 中

#### P0-4 收紧 `BaseStatusEnum` 接口
**事实**：接口内的 `canTransitTo`、`isTerminal`、`requireTransitTo` 都用 default 方法提供，与一个 `Enum`+"状态机工厂"组合是等效的。当前实现可以让业务枚举更轻盈，但仍有过度文档化的注释。

**建议**：保留接口，但要求每个业务枚举用 `enum + static Map<Enum, Set<Enum>>` 表达，禁止在 `canTransitTo` 里写长 switch。详见 P1-2。

#### P0-5 删除 `BaseDTO`
**事实**：业务模块都自建 DTO，0 个继承 `BaseDTO`。8 个跨切面字段与 `RequestContext` 重复。Spring 拦截器/AOP 应承载这些上下文的自动注入（已经有 `RequestContext`，再写一遍 DTO 字段只会引起"两份数据可能不一致"）。

**实施**：直接删除。如果未来真需要 DTO 基类，按业务场景专建（比如 `UserDTO`/`OrderDTO`）。

### P1（结构性修复）—— 改不改由架构负责人拍板

#### P1-1 实体基类"扁平化"——`BaseEntity` 退化为 jdbc 内部类

**当前问题**：
```
BusinessEntity → MpBaseEntity (jdbc) → BaseEntity (domain) → BaseAuditEntity → BaseIdEntity
```

**重构后**：
```
BusinessEntity → MpBaseEntity (jdbc，直接定义 id/createdBy/createdAt/updatedBy/updatedAt/revision/deleted/status/tenantId)
```

**步骤**：
1. 在 `ydsz-common-jdbc` 内部把 `MpBaseEntity` 全部字段内联或定义 `JdbcBase*` 三个类（同包私有）
2. `domain` 包的 `BaseEntity/BaseLong/BaseString/BaseAuditEntity/BaseIdEntity/LogBase/Persistable/Auditable/Versionable/SoftDeletable` 全部删除
3. 任何使用这些类的内部类（比如 `MpBaseAuditEntity extends BaseAuditEntity`）改为 jdbc 包内合并
4. 业务实体改动：让 `class UserDO extends MpBaseEntity` 这一行更简洁

**收益**：
- 实体继承深度从 5 级变为 2 级
- 字段声明只在一个地方
- 跨模块依赖解除（jdbc 不再 import domain）
- 文档从"先看 BaseEntity 再看 MpBaseEntity"变为"直接看 MpBaseEntity"

**风险**：需全工程 grep `domain.entity.Base*` 验证确实无业务直接继承（已验证：业务都走 jdbc.MpBaseEntity）。过渡期内可保留 `@Deprecated` 的 BaseEntity 一个月。

#### P1-2 把 DAG 与 Job 全部迁出 `domain` 模块

**目标**：
- `SpELConditionEvaluator` + `DagInstanceStatus` + `DagNodeStatus` → `ydsz-cronjob/ydsz-cronjob-domain`
- `JobHandler` / `MapReduceProcessor` / `JobContextHolder` / `MapContext` / `ProcessResult` / `ShardingContext` / `MapTask` / `JobLogger` / `JobLoggerHolder` → `ydsz-cronjob/ydsz-cronjob-domain`

**步骤**：
1. 在 `ydsz-cronjob-domain` 中创建对应类（先复制再改包名）
2. `domain` 模块的 `job` / `dag` / `event.EventStore` 包全部删除
3. `DomainAutoConfiguration` 不再注册 `SpELConditionEvaluator`，由 `ydsz-cronjob` 自己注册
4. `DomainHealthIndicator` 移走或删除（只反映 SpEL 缓存计数，价值低）
5. `DomainProperties` 只保留 `enabled`（开关），不再持有 `spel.cache*`

**收益**：
- `ydsz-common-domain` 从 43 个文件缩到 **15~18 个文件**
- 业务模块不再"看似依赖 cronjob 但又不得不引入 domain"
- DAG 与 Job 改 Cronjob 自己持有，避免"common 模块里有业务引擎逻辑"

#### P1-3 PageQuery 排序字段结构化

**改动点**：
```java
// 引入 record
public record OrderItem(String column, Direction direction) {
    public enum Direction { ASC, DESC }
    public String toSql() { return column + " " + direction.name(); }
}

// 替代
private List<OrderItem> orderItems = new ArrayList<>();
```

**步骤**：
1. 新增 `OrderItem` record
2. `PageQuery.orderItems` 改为 `List<OrderItem>`
3. `addOrder/addAscOrder/addDescOrder/setOrderItems/setOrderBy` 全部返回/接受 `OrderItem`，验证逻辑集中到 `OrderItem.of(String)` 静态方法
4. `getOrderSql()` 改为一次 `map(OrderItem::toSql).collect(...)`，零解析零反射
5. 提供 1~2 个 Sprint 兼容层（读旧 List<String> 自动转换），给业务迁移窗口

**收益**：每次读 `getOrderSql()` 不再正则 split；新增排序条件只需增 1 个字段类型。

#### P1-4 TreeBuilder 拆 70% 实现，只保留 80% 业务在用的能力

**保留**：
- `TreeNode<T, ID>` 基类（含 id/parentId/children/sort/level/path —— 必须）
- `TreeBuilder.buildSimple(List, id, parentId, children, sort)`（VO 适配，多数业务用）
- `TreeBuilder.findById`、`flatten`、`getAncestors`、`getDescendants`

**删除**：
- `cachedRoots/cachedNodeMap/cachedAllNodes` 缓存（DCL + ReentrantLock + dirty 标记）
- `autoCalcLevel / autoBuildPath / multiRoot / rootId` 配置（用不到）
- `idExtractor / parentIdExtractor` 配置函数（违反少即是多）
- `getTreeDepth / countLeafNodes / countNodes / countRootNodes / getLeafNodes / getDescendantCount / filterByLevel` 大量统计 API（业务用不到）
- `traverseDFS / traverseBFS` 实例方法（业务模块用 forEach 即可）
- `copy / newInstance / copyFieldsTo / cloneSubTree / moveTo`（与 DDD 没关系，与 TreeBuilder 没关系）
- `PATH_SEPARATOR / ROOT_LEVEL` 常量、完整 Javadoc

**目标**：TreeBuilder 缩到 ~150 行，单文件清晰可读。

#### P1-5 DomainEvent 减字段 + 减 Builder 复杂度

**当前 11 字段**：`eventId/occurredAt/eventType/aggregateId/aggregateType/version/tenantId/userId/traceId/metadata` + 父类 `ApplicationEvent.source`

**收紧到 5 字段**：`eventId/occurredAt/eventType/metadata/payload(Object)`
- 删除 `aggregateId/aggregateType/version`（业务不用 event sourcing）
- 删除 `tenantId/userId/traceId`（跨 ThreadLocal 自动传透，事件内不重复存）
- 保留 `metadata` 作为扩展兜底
- `payload` 用 `Object`，业务可塞任意 JSON 序列化对象

**简化 Builder**：只保留 `eventType/metadata/payload/occurredAt/eventId/clock` —— 6 个 setter。

**收益**：业务写一个事件从 12 行降到 4 行。

#### P1-6 收敛跨层常量与 DTO 到对应模块
- `IdentityType` / `ServiceType` → `common-auth`（已是身份/服务类型）
- `DataScopeType` → `common-tenant`（已是数据范围）
- `BaseDTO` 整文件删除（P0-5）
- `TokenConstants` 删除 / 收敛到 `common-core` HeaderConstants（P0-3）
- `FilterIgnoreConstant` 移到 Gateway 模块配置（P0-3）

### P2（深度重构）—— 可选，需架构 review

#### P2-1 DomainEvent 与 Spring ApplicationEvent 合并
不再让 DomainEvent 继承 `ApplicationEvent`，只作为业务事件的"语义载体"；跨进程流转走 common-event 的 Outbox。

#### P2-2 状态机工厂升级
`BaseStatusEnum.canTransitTo` 改为基于 "静态查表"：
```java
private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
    PENDING, Set.of(PAID, CANCELLED),
    PAID, Set.of(SHIPPED, CANCELLED),
    ...
);
```
统一在每个枚举里初始化 `TRANSITIONS_MAP`，`canTransitTo` 退化为 `TRANSITIONS.get(this).contains(target)`。

#### P2-3 在 cronjob-domain 引入 DAG Schema 模型
把 `DagInstanceStatus/DagNodeStatus/SpELConditionEvaluator` 重写为完整 DAG 引擎（含节点定义、超时、重试），不依赖 domain 包。

### P3（治理与文档同步）

#### P3-1 README 重写
重写后预期章节：
1. 模块定位（明确"只提供 PageQuery/BaseQuery/PageResult/TypeEnum/BaseStatusEnum/OrderItem/ModuleEventTypes"）
2. 接入方式（保留）
3. 实体继承（说明：业务模块继承 jdbc.MpBaseEntity，不再介绍 domain.Base*）
4. 查询模型（重点）
5. 枚举与状态机（次重点）
6. 注意事项（精简）

#### P3-2 移除 `additional-spring-configuration-metadata.json` 中废弃配置项
把 `ydsz.domain.event.async-enabled / event.default-phase / tree.lazy.*` 全部删除。Spring Boot 启动期不再有误导提示。

#### P3-3 补单元测试
- `PageQuery` 安全校验（白名单/正则/通配符转义）
- `OrderItem` 边界
- `TreeBuilder` 极简单测（5~10 个 case 即可，原代码过于复杂无法全面测试，简化为先单测覆盖主路径）

---

## 五、推荐的最终模块结构

```
ydsz-common-domain/
├── dto/                       — 删除
├── constant/                  — 删除
├── annotation/                — 全部删除（8 个）
├── entity/                    — 删除 BaseEntity/BaseLong/BaseString/BaseAuditEntity/BaseIdEntity
│                               — 删除 LogBase/Persistable/Auditable/Versionable/SoftDeletable
├── event/
│   ├── DomainEvent            — 简化为 5 字段 + 6 setter Builder
│   └── ModuleEventTypes       — 保留（跨模块事件类型）
├── config/
│   ├── DomainAutoConfiguration— 只持有 enabled 开关
│   └── DomainProperties       — 只保留 enabled
├── query/
│   ├── BaseQuery              — 保留
│   ├── PageQuery              — 改用 List<OrderItem>
│   ├── OrderItem              — 新增 record
│   └── PageResult             — 保留
├── enums/
│   ├── BaseStatusEnum         — 收紧
│   └── TypeEnum               — 保留
├── dag/                       — 整个包删除（迁 cronjob-domain）
├── tree/                      — TreeBuilder 大幅简化到 150 行，删除未使用 API
├── job/                       — 整个包删除（迁 cronjob-domain）
└── health/                    — 整个包删除（迁 cronjob 或删除）
```

**模块剩下的 6 个包 ≈ 15 个文件**，减少 65% 体积，依赖图从 7 个变 4 个。

---

## 六、迁移路线图（建议）

| 阶段 | Sprint | 内容 | 影响面 |
|---|---|---|---|
| **Phase 0**（立即，本周） | 0 | 删除 8 个注解 + EventStore + TokenConstants + FilterIgnoreConstant + BaseDTO + IdentityType/ServiceType（迁 common-auth） | 低，业务零感知 |
| **Phase 1**（1~2 周） | 1 | 改 PageQuery 用 List<OrderItem>，兼容旧 List<String> 一个 Sprint 后删除 | 低，改 query 模块 + 调用方 |
| **Phase 2**（2~3 周） | 2 | DAG + Job 全量迁移到 cronjob-domain；domain 模块相关包清空 | 中，cronjob 内部重排 |
| **Phase 3**（3~4 周） | 3 | 实体基类扁平化：domain 实体基类全部下沉 jdbc，jdbc 不再 import domain | 中，全工程改 inheritance 一行 |
| **Phase 4**（4~5 周） | 4 | TreeBuilder 大幅精简；DomainEvent 字段收敛；剩余跨层常量迁完 | 低 |
| **Phase 5**（5~6 周） | 5 | README 重写 + 补充单元测试 + 配置元数据清理 | 文档 |

**安全网**：每个 Phase 之间保留 1 个 Sprint 兼容期（类标 `@Deprecated`、旧 API 自动转发到新 API），新模块开发不会因为清理阻塞。

---

## 七、回报预估

| 维度 | 当前 | 重构后 | 收益 |
|---|---|---|---|
| 模块文件数 | 43 | ≈ 15 | **-65%** |
| 模块代码行数 | 5147 | ≈ 1700 | **-67%** |
| 类数量（含内部类） | 50+ | ≈ 18 | **-64%** |
| 业务模块强耦合的"非领域设施" | 11 个 API | 0 | **新人不必面对 cronjob 的 Job 概念** |
| 构建时间（增量） | 影响 0（已经在 common 包）| 略快（少 65%）| 边际正收益 |
| IDE 补全歧义 | 高（PageQuery 5 入口、TreeBuilder 30 方法） | 低 | **开发体感升级** |
| 单元测试可覆盖度 | 几乎为 0（模块太复杂） | 单测可覆盖 80%+ 关键路径 | **业务安全网** |

---

## 八、结论

**ydsz-common-domain 是一个典型的"曾经过度设计、已经轻装了一半、但没走到头"的模块**：
- 删除过 `DomainEventPublisher / EventStore / Specification / BaseValueObject / CursorPageResult / AggregateRoot / TenantAware / BaseVO / LazyTreeNode` 等 13+ 类（README 自承）
- 但仍然留下了**`8 个死注解 + TreeBuilder 700 行 + PageQuery 字符串排序 + DomainEvent 11 字段 + DAG/Job 越界 + 跨层常量`**这套"装饰 + 越界 + 错位"组合

按"对标行业主流规范 + 满足业务需求 + 控制复杂度"的三角原则，本报告建议：
- **P0 立即清理**：4 个零成本动作即可砍掉 ~600 行死代码
- **P1 结构修复**：DAG/Job 迁出 + 实体扁平化 + PageQuery 结构化（保留 1 个 Sprint 兼容期）
- **P2 深度重构**：DomainEvent 进一步整合、状态机工厂升级
- **P3 治理与文档**：README 重写 + 配置清理 + 补单测

按本路线执行 5~6 Sprint 后，模块从"4347 + 5147 行"→"15 个文件 + 1700 行"，定位从"杂货间"→"纯 PageQuery + 状态机契约"，开发效率与维护成本双向受益。
