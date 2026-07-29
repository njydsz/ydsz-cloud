# ydsz-common-domain

YDSZ DDD 领域模型基类库 — 实体基类、领域事件、树形结构、分页查询、Job 框架、DAG 引擎。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 43 |

## 设计原则

本模块遵循"简单够用"原则，只保留被业务模块实际使用的组件。在 1.3.0 版本中进行了大规模简化，
删除了 24 个零引用文件（占总量的 36%），消除了约 2200 行无效代码。

### 保留的组件（100% 有业务引用）

| 组件 | 引用模块 |
|---|---|
| 实体基类体系 | 全项目所有 DO 继承 |
| 审计注解 | MyBatis-Plus MetaObjectHandler 使用 |
| 查询模型（PageQuery/PageResult） | 50+ 文件引用 |
| TreeBuilder + TreeNode | userinfo 模块使用 |
| Job 框架 | cronjob + workflow 模块使用 |
| SpELConditionEvaluator | cronjob DAG 使用 |
| DomainEvent | workflow/notify 事件基类 |

### 已删除的组件（1.3.0 简化）

| 已删除 | 原因 | 替代方案 |
|---|---|---|
| DomainEventPublisher | 0 业务调用 | Spring `@TransactionalEventListener` |
| EventStore / TransactionPhase | 事件溯源 SPI，0 实现 | 如需事件溯源，届时再定义 |
| EntityCapabilities | 0 调用 | MyBatis-Plus 内置注解机制 |
| Specification | 0 使用 | Java Stream + Predicate |
| BaseValueObject / ValueObject | 0 子类 | 无值对象场景 |
| BaseConverter | 0 实现 | 各模块自有 Converter 模式 |
| DomainException 系列 | 0 使用 | `common-exception` BusinessException |
| AggregateRoot / RootEntity | 0 外部实现 | 内联到 BaseEntity |
| LazyTreeNode / TreeLazyConfig / TreeNodeProvider | 0 使用 | TreeBuilder 全量构建已满足 |
| CursorPageResult | 0 使用 | PageResult 偏移分页 |
| TenantAware / ProjectAware / RegionAware / GroupAware | 0 使用 | common-tenant 模块处理 |
| BaseVO | 0 使用 | 各模块直接定义 VO |
| TypeEnumConverterFactory | 0 使用 | MyBatis-Plus `@EnumValue` |

## 核心能力

### 实体基类体系

```
Persistable<T>                    (接口: ID + isNew)
  └─ BaseIdEntity<T>              (ID 字段)
       └─ BaseAuditEntity<T>     (审计字段, implements Auditable)
            └─ BaseEntity<T>     (乐观锁 + 软删除 + status + 领域事件)
                 ├─ Base          (String 主键)
                 ├─ BaseLong      (Long 主键)
                 └─ LogBase       (日志实体, String 主键, 无乐观锁/软删除)
```

### 能力标记接口

| 接口 | 说明 |
|---|---|
| `Persistable<T>` | 可持久化标记（ID + isNew，支持 UUID 主键判断） |
| `Auditable` | 可审计标记（创建/更新审计字段） |
| `Versionable` | 可版本化标记（乐观锁） |
| `SoftDeletable` | 可软删除标记 |

### 注解驱动审计

| 注解 | 说明 |
|---|---|
| `@CreatedBy` / `@CreateAt` | 创建人 / 创建时间自动填充 |
| `@UpdatedBy` / `@UpdateAt` | 更新人 / 更新时间自动填充 |
| `@Version` | 乐观锁版本号 |
| `@SoftDelete` | 软删除标记（类级注解） |
| `@TenantId` | 租户 ID 字段 |

### 领域事件

| 类 | 说明 |
|---|---|
| `DomainEvent` | 领域事件基类（继承 Spring ApplicationEvent，Builder 模式 + Clock 注入） |
| `ModuleEventTypes` | 跨模块事件类型常量注册表 |

事件发布直接使用 Spring 内置机制：

```java
// 同步发布
applicationEventPublisher.publishEvent(new OrderCreatedEvent(orderId));

// 事务提交后发布（推荐）
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderCreated(OrderCreatedEvent event) { ... }
```

### 查询模型

| 类 | 说明 |
|---|---|
| `BaseQuery` | 查询基类（搜索关键字、状态、时间范围、枚举联动、时间范围校验） |
| `PageQuery` | 分页查询（pageNum / pageSize / 排序白名单 / SQL 注入防护） |
| `PageResult<T>` | 分页结果（支持 convert 类型转换） |
| `BaseDTO` | 数据传输对象基类 |

### 树形结构

| 类 | 说明 |
|---|---|
| `TreeNode<T, ID>` | 树节点接口（DFS/BFS 遍历、查找、复制） |
| `TreeBuilder<T, ID>` | 树构建器（O(n) 构建、循环引用检测、路径生成按层级排序） |

### DAG 工作流引擎

| 类 | 说明 |
|---|---|
| `DagInstanceStatus` | DAG 实例状态枚举 |
| `DagNodeStatus` | DAG 节点状态枚举 |
| `SpELConditionEvaluator` | SpEL 条件表达式评估器（LRU 缓存，可配置容量） |

### Job 分布式处理框架

| 类 / 接口 | 说明 |
|---|---|
| `JobHandler` | 任务处理器接口（支持分片执行） |
| `MapProcessor` / `MapReduceProcessor` | Map/MapReduce 任务处理器接口 |
| `MapContext` / `MapTask` / `ProcessResult` | Map 执行上下文、子任务封装、处理结果 |
| `ShardingContext` | 分片上下文 |
| `JobLogger` | 任务日志接口 |
| `JobContextHolder` | 任务上下文持有者（InheritableThreadLocal） |
| `JobLoggerHolder` | 任务日志持有者（InheritableThreadLocal） |

### 枚举体系

| 类 | 说明 |
|---|---|
| `BaseStatusEnum<E>` | 状态枚举接口（状态流转校验） |

## 自动配置

```yaml
ydsz:
  domain:
    enabled: true                # 启用 domain 模块自动配置（默认 true）
    spel:
      cache-enabled: true        # 启用 SpEL 表达式缓存（默认 true）
      cache-max-size: 1024       # SpEL 缓存最大容量（LRU 淘汰，默认 1024）
```

`DomainAutoConfiguration` 自动注册：
- `SpELConditionEvaluator` — DAG 条件分支评估器
- `DomainHealthIndicator` — SpEL 评估器健康状态（需 spring-boot-health）

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-domain</artifactId>
</dependency>
```

## 变更日志

### 1.3.0 — 过度设计简化（删除 24 个零引用文件，减少 ~2200 行无效代码）

**P0 核心删除（4 项）**

| 编号 | 操作 | 理由 |
|---|---|---|
| P0-1 | 删除 `DomainEventPublisher`（281 行） | 0 业务调用，Spring `@TransactionalEventListener` 已覆盖 |
| P0-2 | 删除 `EventStore` + `TransactionPhase` | 事件溯源 SPI，0 实现 0 使用 |
| P0-3 | 删除 `EntityCapabilities`（257 行） | 0 调用，MyBatis-Plus 内置注解机制已覆盖 |
| P0-4 | 删除 `DomainException` 系列 3 个文件 | 0 使用，已被 `common-exception` BusinessException 取代 |

**P1 架构简化（6 项）**

| 编号 | 操作 | 理由 |
|---|---|---|
| P1-1 | 删除 `Specification` + `BaseValueObject` + `ValueObject` | 0 使用 |
| P1-2 | 删除 `BaseConverter` | 0 实现，各模块自有 Converter 模式 |
| P1-3 | 删除 `LazyTreeNode` + `TreeLazyConfig` + `TreeNodeProvider` | 0 使用，TreeBuilder 全量构建已满足 |
| P1-4 | 删除 `CursorPageResult` | 0 使用，PageResult 偏移分页已满足 |
| P1-5 | 删除 `AggregateRoot` + `RootEntity` 接口 | 内联到 BaseEntity，减少接口层级 |
| P1-6 | 简化 `BaseEntity` | 移除 AggregateRoot/RootEntity 继承，直接 implements Versionable + SoftDeletable，内联 registerEvent/getDomainEvents/clearDomainEvents |

**P2 工程清理（4 项）**

| 编号 | 操作 | 理由 |
|---|---|---|
| P2-1 | 删除 `TenantAware` / `ProjectAware` / `RegionAware` / `GroupAware` | 0 使用，多租户由 common-tenant 模块处理 |
| P2-2 | 删除 `BaseVO` + `TypeEnumConverterFactory` | 0 使用 |
| P2-3 | 简化 `DomainHealthIndicator` / `DomainProperties` / `DomainAutoConfiguration` | 移除对已删除组件的引用 |
| P2-4 | 清理测试目录 | 删除 InMemoryEventStore/DomainExceptionTest/TypeEnumConverterFactoryTest |

### 1.2.0 — 质量优化与功能增强

| 编号 | 变更 |
|---|---|
| P0-1 | DomainProperties 配置接入 DomainEventPublisher + SpELConditionEvaluator |
| P0-2 | EntityCapabilities 改用 ClassValue 消除 ClassLoader 泄漏 |
| P0-3 | CursorPageResult subList 视图泄漏修复 |
| P1-1 | 删除 AuditInfo 死代码 |
| P1-2 | PageQuery getOrderBy 从 orderItems 派生 |
| P1-3 | DomainEventPublisher 批量事件注册优化 |
| P1-4 | JobContextHolder/JobLoggerHolder 改用 InheritableThreadLocal |
| P1-6 | BaseQuery 新增 statusEnum 方法 |
| P2-1 | DomainEvent.Builder Clock 注入 |
| P2-5 | TreeBuilder.generatePaths 按 level 排序 |
| P2-6 | Persistable.isNew 支持 UUID 主键 |
| P2-7 | BaseQuery 时间范围校验 |
