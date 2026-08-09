# ydsz-common-domain

> YDSZ DDD 领域模型基类库（L3 基础服务层）— 分页查询、统一 Slice 分页、状态机契约、树构建器、聚合根接口、领域事件、幂等契约
>
> **v1.7.0 重大变更（2026-08-06）：** 对标互联网大厂规范（Spring Data、Axon Framework、阿里巴巴 Java 开发手册），执行过度设计治理，完成以下优化——
> - **消除静态配置耦合**：废弃 `PageQuery.initProperties()`，改用 `PageQueryFactory` 工厂类实例级注入
> - **CQRS 元数据时机固定**：Command/Query 接口移除 `UUID.randomUUID()` default 实现，要求业务方显式定义
> - **PageQuery 职责收缩**：移除 SQL 转义、ORDER BY 拼接、深度分页评估，下沉至 `SafeQueryInnerInterceptor`
> - **业务枚举标记弃用**：`IdentityType`/`ServiceType`/`DataScopeType` 迁移至业务模块
> - **统一分页结果**：合并 `PageSlice` + `CursorPage` 为统一 `Slice<T>`
> - **BaseEntity 纯领域化**：移除持久化字段（revision/deleted/tenantId/status）
> - **声明式领域事件**：新增 `@DomainEvent` 注解 + AOP 切面，对标 Spring Data `@DomainEvents`

为业务模块提供 DDD 领域驱动设计的基础设施：分页查询模型（含 offset 分页 + 可选的游标/seek 分页）、状态枚举契约（BaseStatusEnum）、O(n) 树构建器、CQRS 契约接口（Command/DTO/VO/Query）、幂等操作契约（IdempotentOperation）、声明式领域事件。

> **历史变更（v1.6.0 以前）**：
> - v1.6.0：深度分页保护 + PageSlice + pathTo/successors 状态机方法
> - v1.5.0：游标分页 + DomainProperties 嵌套分组 + 契约元数据升级
> - v1.4.0：移除 8 个零引用注解 + BaseDTO + 精简 EventStore/TreeBuilder

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 分页查询模型、状态机契约、领域事件、树形结构 |
| **依赖** | common-core、common-json；Jakarta Validation、Spring Context |
| **版本** | 1.7.0 |
| **源文件数** | 24 |

## 核心能力

### 1. PageQuery 工厂类（query 包，v1.7.0 新增）

```java
@Component
public class PageQueryFactory {
    private final DomainProperties props;

    public PageQueryFactory(DomainProperties props) {
        this.props = props;
    }

    public PageQuery create(Integer pageNum, Integer pageSize) {
        PageQuery query = PageQuery.of(pageNum, pageSize);
        query.setRuntimeProperties(props); // 实例级绑定
        return query;
    }
}
```

**使用方式：**
```java
@Autowired
private PageQueryFactory pageQueryFactory;

public void handleRequest(Integer pageNum, Integer pageSize) {
    PageQuery query = pageQueryFactory.create(pageNum, pageSize);
    // query 已绑定运行时配置
}
```

### 2. 查询模型（query 包）

| 类 | 说明 |
|---|---|
| `BaseQuery` | 查询基类，含 searchKey/status/startDateTime/endDateTime/tenantId/ascending；提供时间范围校验方法 |
| `PageQuery` | 分页查询（v1.7.0 职责精简）：仅承载分页参数 + 排序项，SQL 安全由拦截器统一处理 |
| `OrderItem` | 排序项 record（column + ASC/DESC），提供 `of/asc/desc` 静态工厂与 `toSql()` |
| `PageResult<T>` | 分页结果封装（含 total），提供 `convert()` 类型转换 |
| `Slice<T>` | **统一分页结果**（v1.7.0 新增），融合 offset 分页 + cursor 分页语义，对齐 Spring Data |

### 3. 状态机契约（enums 包）

| 类 | 说明 |
|---|---|
| `BaseStatusEnum<E>` | 状态枚举接口，定义 `canTransitTo/isTerminal/requireTransitTo/pathTo(BFS)/successors()` |
| `TypeEnum<T>` | 通用枚举接口（code + desc），提供 `buildCodeMap/codeOf` 静态工具 |

**已弃用枚举（计划迁移至业务模块）：**
| 类 | 状态 | 迁移目标 |
|---|---|---|
| `IdentityType` | @Deprecated 1.7.0 | ydsz-userinfo-api |
| `ServiceType` | @Deprecated 1.7.0 | ydsz-gateway |
| `DataScopeType` | @Deprecated 1.7.0 | common-rbac 或 common-tenant |

### 4. 树形结构（tree 包）

与旧版本相同，保持 O(n) 复杂度 + 迭代式实现。

| 类 | 说明 |
|---|---|
| `TreeNode<T, ID>` | 树节点基类（递归泛型），提供 `addChild/findById/containsChild`（迭代式） |
| `TreeBuilder<T, ID>` | 树构建器，提供 `build()`（O(n)）和 `buildSimple()`（静态工具方法，支持不继承 TreeNode 的 VO） |

### 5. CQRS 契约接口（contract 包，v1.7.0 变更）

**设计变更：** 移除 default 实现，要求业务方显式定义元数据，确保幂等语义正确。

```java
// v1.7.0 推荐写法
public class CreateOrderCommand implements Command {
    private final String commandId;
    private final Instant issuedAt;

    public CreateOrderCommand() {
        // 优先从 MDC 获取 traceId，保持链路追踪一致
        this.commandId = MDC.get("traceId") != null
            ? MDC.get("traceId")
            : "cmd-" + System.nanoTime();
        this.issuedAt = Instant.now();
    }

    @Override public String commandId() { return commandId; }
    @Override public Instant issuedAt() { return issuedAt; }
}
```

| 接口 | 说明 |
|---|---|
| `Command` | 写操作入参标记接口，需显式实现 `commandId()` 和 `issuedAt()` |
| `Query` | 读操作入参标记接口，需显式实现 `queryId()` 和 `submittedAt()` |
| `DTO` | 数据传输对象标记接口（层间传递） |
| `VO` | 视图对象标记接口（API 响应封装） |
| `IdempotentOperation` | 幂等操作契约，支持 scope 隔离 + conflict policy |

### 6. 聚合根（entity 包，v1.7.0 变更）

```java
// 纯领域 BaseEntity（v1.7.0）
public class BaseEntity<T extends Serializable> implements Serializable {
    private T id;
    private String createdBy/updatedBy;
    private LocalDateTime createdAt/updatedAt;
    private transient List<Object> domainEvents;

    protected void registerEvent(Object event) { ... }
    public List<Object> pullDomainEvents() { ... }
}
```

**持久化基类选择：**
- MyBatis-Plus → 使用 `com.njydsz.common.jdbc.entity.MpBaseEntity`（含 @TableId、审计填充、乐观锁）
- JPA → 在此类基础上添加 JPA 注解
- 纯内存/事件溯源 → 直接使用此类

### 7. 声明式领域事件（event 包，v1.7.0 新增）

```java
public class Order extends BaseEntity<Long> {
    @DomainEvent("OrderPaid")
    public void pay() {
        this.status = OrderStatus.PAID.name();
        // DomainEventAspect 自动注册事件 + 发布
    }
}
```

| 组件 | 说明 |
|---|---|
| `@DomainEvent` | 标注领域事件方法（value/delaySeconds/async） |
| `DomainEventAspect` | AOP 切面，自动捕获状态变更并发布 Spring ApplicationEvent |

## 配置项

```yaml
ydsz:
  domain:
    page:
      max-search-key-length: 200              # 搜索关键字最长长度（1~500，默认 200）
      cursor-warning-threshold: 10000         # 深度分页警告阈值
      cursor-reject-threshold: 50000         # 深度分页拒绝阈值
    tree:
      max-depth: 10                           # 树构建最大深度限制（1~100，默认 10）
    idempotent:
      default-expire-seconds: 86400           # 幂等键默认过期（秒，默认 86400=24h）
```

## 使用示例

### 1. 分页查询（推荐 Factory 模式）

```java
@Autowired
private PageQueryFactory pageQueryFactory;

public PageResult<UserVO> page(UserQueryRequest request) {
    PageQuery query = pageQueryFactory.create(request.getPageNum(), request.getPageSize());
    query.addDescOrder("created_at");
    PageResult<UserDO> doPage = userService.page(query);
    return doPage.convert(UserVO::new);
}
```

### 2. 统一 Slice 分页

```java
// Offset 分页（无 total）
Slice<User> offsetSlice = Slice.of(users, query.getPageNum(), query.getPageSize(), hasNext);

// Cursor 分页（无限滚动）
Slice<User> cursorSlice = Slice.of(users, nextCursor, hasNext);

// 双向游标（支持向上/向下滚动）
Slice<User> biCursorSlice = Slice.of(users, nextCursor, prevCursor, hasNext, hasPrevious);
```

### 3. 声明式领域事件

```java
@Service
public class OrderService {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public void payOrder(Long orderId) {
        Order order = orderRepository.findById(orderId);
        order.pay(); // @DomainEvent 自动注册事件
        orderRepository.save(order); // Repository 需调用 pullDomainEvents 后发布剩余事件
    }
}
```

## 注意事项

1. **PageQuery 职责变更（v1.7.0）：** 不再内置 SQL 转义/ORDER BY 拼接/深度分页评估，这些职责已下沉至 `SafeQueryInnerInterceptor`（common-jdbc 模块）
2. **Command/Query 元数据（v1.7.0）：** 必须在构造时固定 `commandId/issuedAt`，不再使用 default 的 `UUID.randomUUID()`，否则幂等判断会失效
3. **BaseEntity 继承选择：** 数据库实体请继承 `common-jdbc` 的 `MpBaseEntity`（含完整持久化能力），纯领域场景继承此类
4. **业务枚举迁移：** `IdentityType`/`ServiceType`/`DataScopeType` 已标记 @Deprecated，业务模块应定义自己的枚举

## SPI 扩展点

与旧版本相同，参见原 SPI 表格。

## 变更记录

- **v1.7.0**（2026-08-06）：对标互联网大厂规范，执行过度设计治理——
  - 消除静态配置耦合：新增 `PageQueryFactory`，废弃 `PageQuery.initProperties()`
  - CQRS 元数据时机固定：Command/Query 移除 UUID.randomUUID() default 实现
  - PageQuery 职责收缩：SQL 安全处理下沉至 `SafeQueryInnerInterceptor`
  - 新增统一 `Slice<T>` 分页结果：合并 `PageSlice` + `CursorPage`
  - BaseEntity 纯领域化：移除 revision/deleted/tenantId/status 持久化字段
  - 新增 `@DomainEvent` 注解 + `DomainEventAspect` 声明式事件机制
  - 业务枚举标记 @Deprecated，计划迁移至所属业务模块
  - 移除 `DomainProperties.enabled` 虚假开关
- **v1.6.0**（2026-08-06）：深度分页保护 + PageSlice + 状态机 pathTo/successors
- **v1.5.0**（2026-08-06）：DomainProperties 扩展 + 游标分页 + 契约元数据升级
- **v1.4.0**（2026-08-04）：移除大量零引用组件 + TreeBuilder 精简
- **v1.0.0**（2026-08-02）：初始版本
