# remi-common-domain

> REMI DDD 领域模型基类库（L3 基础服务层）— 查询模型、状态机契约、领域事件、树形结构

为业务模块提供 DDD 领域驱动设计的基础设施：分页查询模型（PageQuery/PageResult）、状态枚举契约（BaseStatusEnum）、领域事件基类与跨模块事件类型注册表、O(n) 树构建器。所有业务模块的查询对象、状态枚举、领域事件均以本模块为统一基座。

> **重要说明（v1.4.0）**：本模块已完成过度设计治理，移除了以下组件——
> 8 个零引用注解（@DomainService/@SoftDelete/@Version/@TenantId/@CreatedBy/@CreateAt/@UpdatedBy/@UpdateAt）、
> BaseDTO、TokenConstants/FilterIgnoreConstant（与 common-core 重复）、DAG/Job 框架（归属 remi-cronjob）、
> EventStore 事件溯源查询方法（Outbox 为 append-only）。
> 数据库实体的持久化基类请使用 `remi-common-jdbc` 的 `MpBaseEntity`（见下文「实体持久化」）。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 分页查询模型、状态机契约、领域事件、树形结构 |
| **依赖** | common-core、common-json；Jakarta Validation、MyBatis-Plus Annotation、Spring TX、Spring Context |
| **版本** | 1.4.0 |
| **源文件数** | 15 |

## 核心能力

### 1. 查询模型（query 包）

| 类 | 说明 |
|---|---|
| `BaseQuery` | 查询基类，含 searchKey/status/startDateTime/endDateTime/tenantId/ascending；提供 `hasTimeRange/isValidTimeRange/validateTimeRange/hasSearchKey/hasStatus/statusEnum` 方法；时间范围合法性校验（startDateTime 不晚于 endDateTime） |
| `PageQuery` | 分页查询（继承 `BaseQuery`），含 pageNum/pageSize/orderItems；`@NotNull/@Min/@Max` 校验；排序字段采用结构化 `OrderItem`（列名 + 方向），SQL 注入防护（`SAFE_COLUMN_PATTERN` 正则 + `allowedOrderByFields()` 白名单钩子）；LIKE 通配符转义（`%`/`_`/`\`）；超长 searchKey 截断（200 字符）；`getOffsetLong()` 支持 long 类型避免超大分页溢出 |
| `OrderItem` | 排序项 record（column + ASC/DESC），提供 `of/asc/desc` 静态工厂与 `toSql()`；替代旧版字符串拼接排序 |
| `PageResult<T>` | 分页结果封装，含 records/total/pageNum/pageSize/totalPages/hasPrevious/hasNext/startRow/endRow；`of()` 静态工厂；`empty()` 空结果；`convert(Function)` 类型转换（DO → VO）；`isEmpty()` 判空 |

**与 `common-core` `PageRequest` 的关系**：`PageRequest` 位于 core 模块，用于 HTTP API 层，分页字段为 `Long` 类型，与 MyBatis-Plus `Page<T>` 对齐；`PageQuery` 位于 domain 模块，用于 Service/Repository 层，分页字段为 `Integer` 类型，并集成搜索/过滤/排序白名单等业务能力。两者共用 `PageConstants` 中的默认值与上限，避免出现不一致的分页约束。

### 2. 状态机契约（enums 包）

| 类 | 说明 |
|---|---|
| `BaseStatusEnum<E>` | 状态枚举接口，定义 `canTransitTo(E)` 状态流转校验契约；`isTerminal()` 终态判断（默认 false）；`requireTransitTo(E)` 校验非法时抛 `IllegalStateException` |
| `TypeEnum<T>` | 通用枚举接口（code + desc），提供 `buildCodeMap/codeOf` 静态工具消除重复 CODE_MAP 初始化代码 |

业务状态枚举实现示例见「使用示例」章节。

### 3. 领域事件（event 包）

| 类 | 说明 |
|---|---|
| `DomainEvent` | 领域事件基类，继承 Spring `ApplicationEvent`，可直接通过 `ApplicationEventPublisher` 发布并由 `@EventListener` 消费；Builder 模式 + `Clock` 注入（便于测试）；不可变设计（`metadata` 为不可变 Map）；字段精简为 eventId/occurredAt/eventType/aggregateId/aggregateType/metadata（上下文由 RequestContext/MDC 传透，不再冗余存储） |
| `ModuleEventTypes` | 跨模块事件类型常量注册表（单一来源），包含 WORKFLOW_INSTANCE_STARTED/COMPLETED/REJECTED、WORKFLOW_TASK_CREATED/COMPLETED/URGED/TRANSFERRED、UNIFIED_ALERT、CRONJOB_EXECUTION_FAILED/TIMEOUT、PERMISSION_CHANGED、CONFIG_CHANGED、OPERATION_LOG、DATA_EXPORT_AUDIT |
| `EventStore` | 领域事件追加存储接口（append-only）。默认实现由 `remi-common-event` 的 OutboxEventStore 提供。**注意**：本接口仅承诺写入能力，不承诺事件回放（Outbox 为 forward-only）；如需事件溯源请扩展本接口 |

事件发布直接使用 Spring 内置机制，不引入额外 `EventPublisher` 抽象：

```java
// 同步发布
applicationEventPublisher.publishEvent(new OrderCreatedEvent(orderId));

// 事务提交后发布（推荐，避免事件消费者读到未提交数据）
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderCreated(OrderCreatedEvent event) { ... }
```

### 4. 树形结构（tree 包）

| 类 | 说明 |
|---|---|
| `TreeNode<T, ID>` | 树节点基类（递归泛型 `TreeNode<T extends TreeNode<T, ID>, ID>`），含 id/parentId/children/sort/level/path/leaf 字段；提供 `addChild/addChildren`（链式）、`isRootNode/isLeaf/getChildCount/containsChild/findById`（迭代避免栈溢出） |
| `TreeBuilder<T, ID>` | 树构建器（O(n) 时间复杂度，HashMap 索引；无缓存、无锁，每次调用独立构建）；`build()` 自动层级计算 + 多根容错 + 排序；`findById/getDescendants/getAncestors/flatten` 查询能力；`buildSimple` 静态方法支持不实现 `TreeNode` 接口的 VO 类 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-domain</artifactId>
</dependency>
```

### 2. 自动配置

通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动注册 `DomainAutoConfiguration`，无需手动 `@EnableXxx`。`remi.domain.enabled=false` 可关闭自动配置。

### 3. 实体持久化

> **重要**：数据库实体的持久化基类在 `remi-common-jdbc` 模块，请继承 `MpBaseEntity`（含 `@TableId` 雪花算法、审计字段自动填充、乐观锁 revision、软删除 deleted、租户 tenant_id），**不要**继承本模块的历史基类（`BaseEntity/BaseLong/BaseString` 已在 v1.4.0 移除）。

```java
import com.remisoft.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SysConfig extends MpBaseEntity {
    private String configKey;
    private String configValue;
}
```

> 数据库实体类命名遵循 `entity-naming.md` 规则：直接使用业务名称作为类名，**不加 `DO` 后缀**。例外见规则文件中的同名冲突清单。

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.domain.enabled` | true | 是否启用 domain 模块自动配置 |

> SpEL 评估器及其缓存配置（`remi.domain.spel.*`）已随 DAG 引擎迁移至 `remi-cronjob` 模块。

## 使用示例

### 1. 分页查询（含白名单校验）

```java
import com.remisoft.common.domain.query.PageQuery;
import com.remisoft.common.domain.query.PageResult;

public class UserPageQuery extends PageQuery {
    @Override
    protected Set<String> allowedOrderByFields() {
        // 排序字段白名单，防止 SQL 注入
        return Set.of("id", "username", "created_at", "updated_at");
    }
}

// 使用
UserPageQuery query = new UserPageQuery();
query.setPageNum(1);
query.setPageSize(20);
query.setSearchKey("admin");          // 自动转义 LIKE 通配符 + 截断 200 字符
query.addDescOrder("created_at");    // 经白名单 + 正则校验，结构化 OrderItem

PageResult<User> page = userService.page(query);
PageResult<UserVO> voPage = page.convert(UserVO::new);  // DO → VO 转换
```

### 2. 状态枚举实现

```java
import com.remisoft.common.domain.enums.BaseStatusEnum;

public enum OrderStatus implements BaseStatusEnum<OrderStatus> {
    PENDING, PAID, SHIPPED, DELIVERED, CANCELLED;

    @Override
    public boolean canTransitTo(OrderStatus target) {
        if (this == target) return true;
        return switch (this) {
            case PENDING -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;  // 终态
        };
    }

    @Override
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}

// 使用
OrderStatus.PENDING.requireTransitTo(OrderStatus.PAID);  // 合法，无异常
OrderStatus.DELIVERED.requireTransitTo(OrderStatus.PAID);  // 非法，抛 IllegalStateException
```

### 3. 领域事件

```java
// 自定义领域事件（子类继承，业务字段自持）
public class OrderCreatedEvent extends DomainEvent {
    private final Long orderId;
    private final String totalAmount;

    public OrderCreatedEvent(Long orderId, String totalAmount) {
        super(UUID.randomUUID().toString(), LocalDateTime.now(),
              "OrderCreated",
              String.valueOf(orderId), "Order",
              Collections.emptyMap());
        this.orderId = orderId;
        this.totalAmount = totalAmount;
    }
}

// 发布事件
applicationEventPublisher.publishEvent(new OrderCreatedEvent(orderId, amount));

// 事务提交后消费（推荐）
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderCreated(OrderCreatedEvent event) {
    notificationService.notify(event.getOrderId());
}
```

### 4. 树形结构构建

```java
import com.remisoft.common.domain.tree.TreeNode;
import com.remisoft.common.domain.tree.TreeBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Menu extends TreeNode<Menu, Long> {
    private String menuName;
    private String routePath;
}

// 构建（自动层级计算 + 排序，O(n)）
List<Menu> allMenus = menuMapper.selectList(null);
List<Menu> tree = new TreeBuilder<>(0L, allMenus).build();

// 静态便捷方法（VO 类无需继承 TreeNode）
List<MenuVO> treeVo = TreeBuilder.buildSimple(
        flatList,
        MenuVO::getId,
        MenuVO::getParentId,
        MenuVO::setChildren,
        MenuVO::getSort);
```

## SPI 扩展点

| 扩展点 | 用途 | 实现方 |
|---|---|---|
| `DomainEvent` | 跨模块领域事件基类，子类继承后通过 `ApplicationEventPublisher` 发布 | 业务模块（如 workflow、notify、project 等） |
| `ModuleEventTypes` | 跨模块事件类型常量注册表，新增模块事件时在此添加常量 | 业务模块新增事件类型时修改本类 |
| `EventStore` | 领域事件追加存储契约（append-only） | `remi-common-event` 的 OutboxEventStore（默认） |
| `BaseStatusEnum<E>` | 状态枚举契约，业务模块的状态枚举实现此接口获得状态流转校验 | 业务模块（订单状态、流程状态、任务状态等） |
| `TypeEnum<T>` | 通用枚举契约（code + desc），配合 `buildCodeMap/codeOf` 快速反查 | 业务模块的码值枚举 |
| `TreeNode<T, ID>` | 树节点基类，业务实体继承后获得树能力 | 业务模块（菜单、组织架构、区域等树形数据） |
| `TreeBuilder.buildSimple(...)` | 静态便捷方法，支持不继承 `TreeNode` 的 VO 类构建树 | 业务模块（已有 VO 结构不便修改的场景） |
| `PageQuery.allowedOrderByFields()` | 排序字段白名单钩子，业务子类覆写返回允许排序的字段集合 | 业务模块的查询对象子类 |

## 注意事项

1. **与 `common-jdbc` `MpBaseEntity` 的关系**：业务模块的数据库实体应直接继承 `common-jdbc` 的 `MpBaseEntity`（含 `@TableId` 雪花算法、`tenant_id` 字段、与 MyBatis-Plus 注解对齐）。本模块不再提供实体基类。
2. **领域事件发布时机**：推荐使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 在事务提交后消费事件，避免事件消费者读到未提交数据或事务回滚后事件已发出的语义错误。
3. **`EventStore` 为 append-only**：Outbox 模式只支持追加投递，不支持事件回放/事件溯源；`findByAggregate` 等查询方法在 v1.4.0 已移除。
4. **`PageQuery` SQL 注入防护**：排序字段经过 `SAFE_COLUMN_PATTERN`（`^[a-zA-Z_][a-zA-Z0-9_.]*$`）正则校验 + `allowedOrderByFields()` 白名单双重过滤；`searchKey` 自动转义 LIKE 通配符（`%`/`_`/`\`）并截断到 200 字符。子类务必覆写 `allowedOrderByFields()` 启用白名单。
5. **`DomainEvent` 不可变性**：`metadata` 字段在构造时包装为 `Collections.unmodifiableMap`，Builder 的 `metadata(key, value)` 在 `build()` 时拷贝到不可变 Map。子类不应提供可变字段 setter。
6. **`TreeNode` 递归泛型模式限制**：`TreeNode<T extends TreeNode<T, ID>, ID>` 内部存在 `(T) this` 未经检查强转，由于类型擦除不会立即抛 `ClassCastException`，而是在返回值被使用时触发。子类必须确保泛型参数 `T` 与自身类型一致（如 `class Menu extends TreeNode<Menu, Long>`），否则运行时抛 `ClassCastException`。
7. **DAG/Job 归属**：DAG 工作流引擎（SpELConditionEvaluator、DagInstanceStatus、DagNodeStatus）与 Job 分布式处理框架（JobHandler、MapReduceProcessor 等）已迁移至 `remi-cronjob` 模块，请勿在本模块引用。

## 变更记录

- **v1.4.0**（2026-08-04）：过度设计治理完成——
  - 移除 8 个零引用注解（@DomainService/@SoftDelete/@Version/@TenantId/@CreatedBy/@CreateAt/@UpdatedBy/@UpdateAt）
  - 移除 BaseDTO（8 个跨切面字段与 RequestContext 重复，6 个继承方改为普通 DTO）
  - 移除 TokenConstants/FilterIgnoreConstant（与 common-core 同名类重复）
  - 精简 EventStore 为 append-only（删除 4 个未实现的事件溯源查询方法）
  - 精简 DomainEvent（删除 version/tenantId/userId/traceId，Outbox 写入方自行解析上下文）
  - PageQuery 排序项重构为结构化 `OrderItem`（替代字符串拼接 + 重复解析）
  - TreeBuilder 从 700 行精简至 ~280 行（移除缓存/DCL/链式配置/统计 API）
  - TreeNode 精简（移除 traverseDFS/BFS/copy/cloneSubTree/moveTo 等未使用 API）
- **v1.3.0**：移除 AggregateRoot/RootEntity 接口，内联事件管理；移除 EntityCapabilities/Specification/BaseValueObject/BaseConverter/LazyTreeNode/CursorPageResult/TenantAware/BaseVO/TypeEnumConverterFactory 等历史组件
- **v1.0.0**（2026-08-02）：对标 `remi-common-jdbc` 标准格式重构 README，聚焦当前实际能力
