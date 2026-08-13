# ydsz-common-domain

> YDSZ DDD 领域模型基类库（L3 基础服务层）— 分页查询、领域事件注册、状态机契约、树构建器、类型安全 ID
>
> **v1.9.0 变更（2026-08-13）：** 恢复可构建 + 规范对齐 + 测试全绿——
> - **修复编译阻断**：BaseEntity 混用 @EqualsAndHashCode(of=...) 与 @Exclude 的 Lombok 冲突已修复
> - **基类抽象化**：BaseEntity / BaseQuery 改为 abstract（Base 前缀类应为抽象类）
> - **PageQuery 风险评估去缓存**：assessPaginationRisk 退化为纯函数，修复哨兵值 bug 与 equals/hashCode 污染
> - **DeepPaginationRisk 阈值契约**：非法阈值（负数 / reject < warn）抛 IllegalArgumentException 快速失败
> - **TreeBuilder**：build() 拆分降低圈复杂度；buildSimple 6 参收敛为 5 参（内联根父 ID "0"）
> - **规范对齐**：CRLF→LF、import 4 组重排、@param/@return 补全、幽灵配置键清理、ArchUnit import 修正
> - **质量门禁**：checkstyle 0 违规；编译 -Werror（豁免 TreeNode 设计性 unchecked）；127 个测试全绿

为业务模块提供领域驱动设计的基础设施：分页查询模型、状态枚举契约、O(n) 树构建器、类型安全 ID、领域事件注册能力。

> **历史变更：**
> - v1.8.0（2026-08-13）：README 对齐现状、深度分页防护闭环、BaseEntity equals 语义修正、新增 SliceQuery/SliceResult
> - v1.7.0（2026-08-06）：消除静态配置耦合、PageQuery 职责收缩、BaseEntity 纯领域化
> - v1.6.0（2026-08-06）：深度分页保护 + PageSlice + 状态机 pathTo/successors
> - v1.5.0（2026-08-06）：游标分页 + DomainProperties 嵌套分组
> - v1.0.0（2026-08-02）：初始版本

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 分页查询、领域事件、状态机、树构建、类型安全 ID |
| **依赖** | common-core、common-json；Jakarta Validation、Spring Context |
| **版本** | 1.9.0 |
| **源文件数** | 20 |

## API 生命周期状态

| 类 | 状态 | 说明 |
|---|---|---|
| `PageQuery` | ✅ **ACTIVE** | 主力分页查询，全业务模块使用 |
| `BaseQuery` | ✅ **ACTIVE** | 查询基类（searchKey/status/时间范围） |
| `OrderItem` | ✅ **ACTIVE** | 结构化排序项 |
| `BaseEntity<T>` | ✅ **ACTIVE** | 领域实体基类（纯领域 + 事件注册） |
| `EventRegistry` | ✅ **ACTIVE** | 领域事件注册接口 |
| `TreeBuilder` / `TreeNode` | ✅ **ACTIVE** | O(n) 树构建 |
| `PageConstants` | ✅ **ACTIVE** | 分页参数归一化（位于 common-core） |
| `DeepPaginationRisk` | ✅ **ACTIVE** | 深度分页风险评估枚举 |
| `DeepPaginationException` | ✅ **ACTIVE** | 深度分页拒绝异常 |
| `DomainProperties` | ✅ **ACTIVE** | 领域配置（深度分页阈值等） |
| `BaseStatusEnum` | ✅ **ACTIVE** | 状态枚举契约 |
| `SliceQuery` / `SliceResult` | 🧪 **EXPERIMENTAL** | 游标分页专用 API，暂未有业务落地 |
| `TypedId<T>` | 🔬 **ADVANCED** | 编译期类型安全 ID，需业务方主动使用 |
| `ValidationGroups` | 🔌 **PLUGGABLE** | 校验分组，需在 Web 层 @Validated 配置后生效 |
| `StateTransitionUtil` | 🔬 **ADVANCED** | 状态机路径推导工具（BFS），独立工具类 |
| `CursorDirection` | 🧪 **EXPERIMENTAL** | 游标方向（NEXT/PREV），配合 SliceQuery 使用 |
| `TypeEnum` | ✅ **ACTIVE** | 通用枚举接口（code + desc） |
| `DomainAutoConfiguration` | ✅ **ACTIVE** | Spring Boot 自动装配入口 |
| `Query` (contract) | 🗑️ **REMOVED** | v1.8.0 已删除孤立标记接口 |

## 核心能力

### 1. 分页查询（query 包）

| 类 | 说明 |
|---|---|
| `BaseQuery` | 查询基类，含 searchKey/status/startDateTime/endDateTime/tenantId；提供时间范围校验 |
| `PageQuery` | 分页查询（承载参数 + 排序项 + 深度分页评估） |
| `OrderItem` | 排序项 record（column + Direction），提供 `of/asc/desc` 静态工厂 |
| `DeepPaginationRisk` | 深度分页风险等级枚举（SAFE/WARN/REJECT） |
| `DeepPaginationException` | 深度分页拒绝时抛出的领域异常 |
| `CursorDirection` | 游标方向枚举（NEXT / PREV） |
| `SliceQuery` | 游标分页入参（ Seek 模式，无 total） |
| `SliceResult` | 游标分页出参（向后兼容 offset 分页） |

**使用方式：**
```java
// 标准分页
PageQuery query = PageQuery.builder()
        .pageNum(1).pageSize(20)
        .addDescOrder("created_at")
        .build();

// 深度分页风险评估
DeepPaginationRisk risk = query.assessPaginationRisk();
if (risk == DeepPaginationRisk.WARN) {
    log.warn("建议使用游标分页");
}

// 游标分页（实验性）
SliceQuery sliceQuery = SliceQuery.builder()
        .pageSize(20).cursor(lastId).direction(CursorDirection.NEXT)
        .build();
```

### 2. 深度分页防护链路

```text
PageQuery.assessPaginationRisk()          # 业务层主动评估（可选）
    ↓
DomainProperties.cursor*WarningThreshold  # 运行时配置阈值
    ↓
SafeQueryInnerInterceptor (common-jdbc)    # MyBatis 执行层自动拦截
    ├─ offset >= warnThreshold  → WARN 日志
    └─ offset >= rejectThreshold → 抛 DeepPaginationException
```

**配置项：**
```yaml
ydsz:
  domain:
    page:
      cursor-warning-threshold: 10000     # 超过此值打 WARN
      cursor-reject-threshold: 50000      # 超过此值抛异常
  jdbc:
    safe-query:
      enabled: true                       # 启用安全查询拦截（默认 true）
      strict-mode: false                  # true=拒绝非法排序字段, false=忽略
```

**异常处理：** `DeepPaginationException` 抛出后，由全局异常处理器（`BaseGlobalResponseAdvice`）转换为标准错误响应。

### 3. 状态机契约（enums 包）

| 类 | 说明 |
|---|---|
| `BaseStatusEnum<E extends Enum<E>>` | 状态枚举接口，定义 `canTransitTo/isTerminal/requireTransitTo` |
| `TypeEnum<T>` | 通用枚举接口（code + desc），提供 `buildCodeMap/codeOf` 静态工具 |
| `StateTransitionUtil` | BFS 路径推导工具（基于 `Function<T, Set<T>>` 转移函数） |

**BaseStatusEnum 实现示例：**
```java
public enum OrderStatus implements BaseStatusEnum<OrderStatus> {
    CREATED, PAID, SHIPPED, COMPLETED, CANCELLED;

    @Override
    public Set<OrderStatus> getAllowedTransitions() {
        return switch (this) {
            case CREATED -> Set.of(PAID, CANCELLED);
            case PAID -> Set.of(SHIPPED, CANCELLED);
            case SHIPPED -> Set.of(COMPLETED);
            default -> Set.of();
        };
    }
}
```

### 4. 树形结构（tree 包）

| 类 | 说明 |
|---|---|
| `TreeNode<T, ID>` | 树节点基类（递归泛型），迭代式实现无栈溢出风险 |
| `TreeBuilder<T, ID>` | 树构建器，`build()` 方法 O(n) 复杂度，基于 HashMap 索引 |

**使用示例：**
```java
List<MenuDO> menus = menuMapper.selectAll();
List<TreeNode<MenuDO, Long>> roots = TreeBuilder.build(menus);
```

### 5. 领域事件（entity 包）

```java
public class Order extends BaseEntity<Long> {

    public void pay() {
        this.status = "PAID";
        // 注册领域事件（非 @DomainEvent 注解方式）
        registerEvent(new OrderPaidEvent(this.id, LocalDateTime.now()));
    }
}
```

**事件分派（Repository 层）：**
```java
orderRepository.save(order);                    // 持久化
order.pullDomainEvents().forEach(event ->       // 取出并清空
    eventPublisher.publishEvent(event));        // 发布
```

> **注意：** v1.7.0 曾引入 `@DomainEvent` 注解 + AOP 切面方式，已在 v1.8.0 移除。
> 当前推荐直接使用 `registerEvent()` 显式注册。

### 6. 聚合根基类

```java
public class BaseEntity<T extends Serializable> implements Serializable, EventRegistry {
    private T id;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private transient List<Object> domainEvents;  // 领域事件暂存

    // 仅以 id 参与 equals/hashCode（DDD 实体语义）
}
```

**持久化继承选择：**
- MyBatis-Plus → 业务实体继承 `com.njydsz.common.jdbc.entity.MpBaseEntity`
- JPA → 继承此类 + 自定义 JPA 注解
- 纯内存/事件溯源 → 直接继承此类

### 7. 类型安全 ID（identity 包，实验性）

```java
public record TypedId<T>(Long value) implements Comparable<TypedId<T>> {
    public TypedId { if (value == null || value <= 0) throw ...; }
}

// 使用：编译期区分 ProjectId / UserId / OrderId
public class Project extends BaseEntity<TypedId<Project>> { }
```

> **状态：** 设计理念符合 DDD（Vlad Mihalcea 推荐模式），但需业务模块主动落地。

### 8. 校验分组（validation 包）

```java
public interface ValidationGroups {
    interface Create { }
    interface Update { }
    interface PageQuery { }
    interface Export { }
    interface Delete { }
}

// 使用：在 Controller 中指定分组
@PostMapping public Result<?> create(@Validated(ValidationGroups.Create) @RequestBody ...) { }
```

> **状态：** 接口已定义，但需在 Web 层全局配置 `@Validated` 或 AOP 拦截后才生效。

## 自动装配

模块通过 `spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册：

```
com.njydsz.common.domain.config.DomainAutoConfiguration
```

启用条件：无（无条件自动装配，DomainProperties 默认配置即可工作）。

通过配置关闭：
```yaml
ydsz:
  domain:
    enabled: false   # 关闭领域模块自动装配（默认 true）
```

## 配置项

```yaml
ydsz:
  domain:
    enabled: true                               # 启用领域模块
    page:
      cursor-warning-threshold: 10000           # 深度分页警告阈值（0 表示关闭）
      cursor-reject-threshold: 50000            # 深度分页拒绝阈值（0 表示关闭）
```

## 变更记录

- **v1.8.0**（2026-08-13）：对标审查后优化——
  - 重写 README，删除已移除 API 描述（PageQueryFactory/@DomainEvent/Command/DTO/VO）
  - 删除孤立 `contract/Query.java` 接口
  - 修正 `BaseEntity.equals/hashCode`：仅以 id 判同（DDD 实体语义）
  - 新增 `SliceQuery/SliceResult` 游标分页实验性 API
  - 深度分页防护链路文档化（PageQuery → SafeQueryInnerInterceptor）
  - 移除 `DomainProperties.tree.maxDepth`（已无消费方）
  - 移除死配置 `DomainProperties.Page.maxSearchKeyLength`（0 消费方）
  - `PageQuery.assessPaginationRisk()` 增加缓存优化，防止重复计算
- **v1.7.0**（2026-08-06）：对标大厂规范，执行过度设计治理——
  - PageQuery 职责收缩，SQL 安全下沉至 SafeQueryInnerInterceptor
  - BaseEntity 纯领域化，移除持久化字段
  - 业务枚举标记 @Deprecated，计划迁移至所属业务模块
  - 新增 @DomainEvent + DomainEventAspect（已在 v1.8.0 移除）
- **v1.6.0**（2026-08-06）：深度分页保护 + PageSlice + 状态机 pathTo/successors
- **v1.5.0**（2026-08-06）：游标分页 + DomainProperties 嵌套分组
- **v1.0.0**（2026-08-02）：初始版本
