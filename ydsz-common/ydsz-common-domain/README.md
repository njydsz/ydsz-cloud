# ydsz-common-domain

> YDSZ DDD 领域模型基类库（L3 基础服务层）— 分页查询、领域事件注册、状态枚举契约、树构建器、类型安全 ID

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 分页查询、领域事件、状态枚举、树构建、类型安全 ID |
| **依赖** | common-core、common-json；Jakarta Validation、Spring Context |
| **版本** | 1.10.0 |

## 源文件清单

```
com/njydsz/common/domain/
├── config/
│   ├── DomainAutoConfiguration.java    # Spring Boot 自动装配入口
│   └── DomainProperties.java           # 深度分页阈值等配置
├── enums/
│   └── BaseStatusEnum.java             # 状态枚举统一抽象
├── identity/
│   └── TypedId.java                    # 编译期类型安全 ID
├── query/
│   ├── BaseQuery.java                  # 查询基类（searchKey/status/时间范围）
│   ├── PageQuery.java                  # 分页查询（参数承载 + 排序 + 偏移量计算）
│   ├── PageQueryRiskAssessor.java      # 深度分页风险评估器（纯函数）
│   ├── OrderItem.java                  # 结构化排序项 record
│   ├── DeepPaginationRisk.java         # 深度分页风险等级枚举（SAFE/WARN/REJECT）
│   └── DeepPaginationException.java    # 深度分页拒绝异常
├── tree/
│   ├── TreeNode.java                   # 树节点基类（递归泛型）
│   └── TreeBuilder.java                # 树构建器（O(n)，HashMap 索引）
└── .gitkeep                            # validation 包已清理
```

> 说明：`BaseEntity` / `EventRegistry` / `TypeEnum` / `SliceQuery` / `SliceResult` / `CursorDirection` 6 个类当前**不存在**（历史规划中的领域实体基类与游标分页 API 未落地），实际共 12 个类。

## API 生命周期状态

| 类 | 状态 | 说明 |
|---|---|---|
| `PageQuery` | ✅ ACTIVE | 主力分页查询，全业务模块使用 |
| `PageQueryRiskAssessor` | ✅ ACTIVE | 深度分页风险评估器（纯函数工具），承担原 PageQuery.assessPaginationRisk 职责 |
| `BaseQuery` | ✅ ACTIVE | 查询基类 |
| `OrderItem` | ✅ ACTIVE | 结构化排序项 |
| `TreeBuilder` / `TreeNode` | ✅ ACTIVE | O(n) 树构建 |
| `DeepPaginationRisk` | ✅ ACTIVE | 深度分页风险评估枚举 |
| `DeepPaginationException` | ✅ ACTIVE | 深度分页拒绝异常 |
| `DomainProperties` | ✅ ACTIVE | 领域配置 |
| `BaseStatusEnum` | ✅ ACTIVE | 状态枚举统一抽象 |
| `DomainAutoConfiguration` | ✅ ACTIVE | Spring Boot 自动装配入口 |
| `TypedId<T>` | 🔬 ADVANCED | 编译期类型安全 ID，需业务方主动落地 |

## 核心能力

### 1. 分页查询（query 包）

```java
// 标准分页
PageQuery query = PageQuery.builder()
        .pageNum(1).pageSize(20)
        .addDescOrder("created_at")
        .build();

// 深度分页风险评估（委托 PageQueryRiskAssessor）
DeepPaginationRisk risk = query.assessPaginationRisk();
if (risk == DeepPaginationRisk.WARN) {
    log.warn("建议使用游标分页");
}
```

或使用 Assessor 直接评估（适用于拦截器层）：

```java
DeepPaginationRisk risk = PageQueryRiskAssessor.assess(query);
```

### 2. 深度分页防护链路

```text
PageQueryRiskAssessor.assess(query)   # 业务层/拦截层评估（纯函数）
    ↓
SafeQueryInnerInterceptor (common-jdbc) # MyBatis 执行层自动拦截
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
```

**异常处理：** `DeepPaginationException` 由全局异常处理器（`BaseGlobalResponseAdvice`）转换为标准错误响应。

### 3. 状态枚举契约（enums 包）

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

```java
List<MenuDO> menus = menuMapper.selectAll();
List<TreeNode<MenuDO, Long>> roots = new TreeBuilder<>(0L, menus).build();
```

VO 类无需继承 TreeNode（推荐）：

```java
List<MenuVO> tree = TreeBuilder.buildSimple(
        flatList,
        MenuVO::getId,
        MenuVO::getParentId,
        MenuVO::setChildren,
        MenuVO::getSort);
```

### 5. 类型安全 ID（identity 包）

```java
public record TypedId<T>(Long value) implements Comparable<TypedId<T>> {
    public TypedId { if (value == null || value <= 0) throw ...; }
}

// 使用：编译期区分 ProjectId / UserId / OrderId
public class Project { private TypedId<Project> id; }
```

> 说明：本模块未提供 `BaseEntity` / `EventRegistry` 领域实体基类（历史规划未落地）；领域实体基类统一使用 `ydsz-common-jdbc` 的 `MpBaseEntity`，领域事件由 `ydsz-common-event` 的 `DomainEvent` / `DomainEventPublisher` 提供。

## 自动装配

模块通过 `spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册：

```
com.njydsz.common.domain.config.DomainAutoConfiguration
```

启用条件：无（无条件自动装配）。

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
    enabled: true                               # 启用领域模块（默认 true）
    page:
      cursor-warning-threshold: 10000           # 深度分页警告阈值（0 表示关闭）
      cursor-reject-threshold: 50000            # 深度分页拒绝阈值（0 表示关闭）
```
