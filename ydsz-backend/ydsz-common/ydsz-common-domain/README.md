# ydsz-common-domain

YDSZ DDD 领域模型基类库 — 实体基类、聚合根、值对象、领域事件、树形结构、分页查询、Job 框架、DAG 引擎。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 40 |

## 核心能力

### 实体基类体系

| 类 | 说明 |
|---|---|
| `BaseIdEntity<T>` | 带主键 ID 的基础实体 |
| `BaseAuditEntity<T>` | 审计实体（继承 BaseIdEntity + 创建/更新审计字段） |
| `BaseEntity<T>` | 标准实体（继承 BaseAuditEntity + 软删除 + 乐观锁 + 状态标识 + 聚合根事件管理） |
| `Base` | String 主键实体基类（继承 BaseEntity<String>） |
| `BaseLong` | Long 主键实体基类（继承 BaseEntity<Long>） |
| `LogBase` | 日志型实体基类（继承 BaseAuditEntity<String>，不含乐观锁/软删除） |
| `AggregateRoot<T>` | DDD 聚合根接口（管理领域事件） |
| `RootEntity<T>` | 根实体组合接口（Persistable + Versionable + SoftDeletable） |

### 实体能力接口（组合式设计）

| 接口 | 说明 |
|---|---|
| `Persistable<T>` | 可持久化标记（ID + isNew） |
| `Auditable` | 可审计标记（创建/更新审计字段） |
| `Versionable` | 可版本化标记（乐观锁） |
| `SoftDeletable` | 可软删除标记 |
| `TenantAware` | 多租户感知标记 |
| `ProjectAware` | 项目维度感知标记 |
| `RegionAware` | 区域维度感知标记 |
| `GroupAware` | 分组维度感知标记 |
| `EntityCapabilities` | 实体能力检测工具类（反射 + 缓存 + DCL 安全） |

### DDD 战术模式

| 类 / 接口 | 说明 |
|---|---|
| `ValueObject` | 值对象标记接口 |
| `BaseValueObject` | 值对象抽象基类（基于属性值的 equals/hashCode） |
| `DomainEvent` / `DomainEventPublisher` | 领域事件与发布器（同步/异步/事务后/多阶段发布） |
| `TransactionPhase` | 事务阶段枚举（BEFORE_COMMIT/AFTER_COMMIT/AFTER_ROLLBACK/AFTER_COMPLETION） |
| `EventStore` | 事件存储 SPI 接口（事件溯源） |
| `ModuleEventTypes` | 跨模块事件类型常量注册表 |
| `@DomainService` | 领域服务注解 |

### 注解驱动审计

| 注解 | 说明 |
|---|---|
| `@CreatedBy` / `@CreateAt` | 创建人 / 创建时间自动填充 |
| `@UpdatedBy` / `@UpdateAt` | 更新人 / 更新时间自动填充 |
| `@Version` | 乐观锁版本号 |
| `@SoftDelete` | 软删除标记（类级注解） |
| `@TenantId` | 租户 ID 字段 |

### 查询模型

| 类 | 说明 |
|---|---|
| `BaseQuery` | 查询基类（搜索关键字、状态、时间范围、排序） |
| `PageQuery` | 分页查询（pageNum / pageSize / 排序白名单 / SQL 注入防护） |
| `PageResult<T>` | 分页结果（支持 convert 类型转换） |
| `CursorPageResult<T>` | 游标分页结果（适用于深分页场景，支持 convert） |
| `BaseDTO` | 数据传输对象基类 |
| `BaseVO<T>` | 视图对象基类（泛型主键支持） |

### 树形结构

| 类 | 说明 |
|---|---|
| `TreeNode<T, ID>` | 树节点接口（DFS/BFS 遍历、查找、复制） |
| `LazyTreeNode<T, ID>` | 懒加载树节点（线程安全、分批加载） |
| `TreeBuilder<T, ID>` | 树构建器（O(n) 构建、循环引用检测、路径生成、缓存） |
| `TreeNodeProvider<T, ID>` | 树节点数据提供者接口 |
| `TreeLazyConfig` | 懒加载配置 |

### DAG 工作流引擎

| 类 | 说明 |
|---|---|
| `DagInstanceStatus` | DAG 实例状态枚举（PENDING/RUNNING/SUCCESS/FAILED/PARTIAL_SUCCESS/CANCELLED/TIMEOUT） |
| `DagNodeStatus` | DAG 节点状态枚举（PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/TIMEOUT） |
| `SpELConditionEvaluator` | SpEL 条件表达式评估器（DAG 条件分支节点使用，实例级缓存） |

### Job 分布式处理框架

| 类 / 接口 | 说明 |
|---|---|
| `JobHandler` | 任务处理器接口（支持分片执行） |
| `MapProcessor` | Map 任务处理器接口 |
| `MapReduceProcessor` | MapReduce 任务处理器接口 |
| `MapContext` | Map 执行上下文 |
| `MapTask` | Map 子任务封装 |
| `ProcessResult` | 处理结果封装 |
| `ShardingContext` | 分片上下文 |
| `JobLogger` | 任务日志接口 |
| `JobContextHolder` | 任务上下文持有者（ThreadLocal） |
| `JobLoggerHolder` | 任务日志持有者（ThreadLocal） |

### 异常体系

| 类 | 说明 |
|---|---|
| `DomainException` | 领域异常基类（含错误码） |
| `AggregateNotFoundException` | 聚合根未找到异常 |
| `ConcurrencyConflictException` | 并发冲突异常（乐观锁失败） |

### 枚举体系

| 类 | 说明 |
|---|---|
| `BaseStatusEnum<E>` | 状态枚举接口（状态流转校验） |
| `TypeEnumConverterFactory` | TypeEnum 转换器工厂（code <-> enum 转换） |

### Converter 规范

| 接口 | 说明 |
|---|---|
| `BaseConverter<PostDTO, PutDTO, E, V>` | Converter 基类接口，统一 DTO ↔ Entity ↔ VO 转换方法命名 |
| `TypeEnumConverterFactory` | TypeEnum 通用转换工厂 |

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `DomainAutoConfiguration` | `ydsz.domain.enabled=true`（默认激活） |

### 配置项

```yaml
ydsz:
  domain:
    enabled: true                    # 启用 domain 模块自动配置
    event:
      async-enabled: true            # 启用异步事件发布（需 TaskExecutor）
      default-phase: AFTER_COMMIT    # 默认事务发布阶段
    spel:
      cache-enabled: true            # 启用 SpEL 表达式缓存
      cache-max-size: 1024           # SpEL 缓存最大容量
  tree:
    lazy:
      max-lazy-depth: 10             # 最大懒加载深度
      batch-size: 100                # 懒加载批次大小
      enabled: false                 # 是否启用懒加载
```

## 领域事件发布

### 四种发布模式

```java
// 1. 同步发布（默认）
domainEventPublisher.publish(new OrderCreatedEvent(orderId));

// 2. 异步发布（通过 TaskExecutor）
domainEventPublisher.publishAsync(new OrderCreatedEvent(orderId));

// 3. 事务提交后发布（推荐，确保数据一致性）
domainEventPublisher.publishAfterCommit(new OrderCreatedEvent(orderId));

// 4. 多阶段事务发布（1.1.0 新增）
domainEventPublisher.publishWithPhase(new OrderFailedEvent(orderId), TransactionPhase.AFTER_ROLLBACK);

// 5. 批量发布
domainEventPublisher.publishAll(order.getDomainEvents());
domainEventPublisher.publishAllAfterCommit(order.getDomainEvents());
domainEventPublisher.publishAllWithPhase(events, TransactionPhase.AFTER_COMPLETION);
```

### TransactionPhase 枚举

| 阶段 | 触发时机 | 典型场景 |
|---|---|---|
| `BEFORE_COMMIT` | 事务提交前 | 事务内验证事件 |
| `AFTER_COMMIT` | 事务成功提交后 | 发布业务变更事件（推荐） |
| `AFTER_ROLLBACK` | 事务回滚后 | 补偿操作、失败告警 |
| `AFTER_COMPLETION` | 事务完成后（无论提交或回滚） | 资源清理、审计日志 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-domain</artifactId>
</dependency>
```
