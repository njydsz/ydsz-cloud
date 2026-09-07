# ydsz-common-domain

> 领域基础组件（L3 基础服务层）— 分页查询 / 树形结构 / 类型化 ID / 规约模式

提供分页查询对象（`PageQuery` / `BaseQuery`）、树形结构构建器（`TreeBuilder` / `TreeNode`）、强类型 ID（`TypedId`）、数据权限上下文、规约模式（`Specification`）等 DDD 领域基础组件，是所有业务模块领域层的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供分页查询、树形结构、强类型 ID、数据权限上下文、规约模式等 DDD 基础组件 |
| **依赖** | ydsz-common-json、ydsz-common-core、lombok、jakarta.validation-api、spring-context |
| **版本** | 1.2.0 |

## 核心能力

### 1. 分页查询

| 类 | 说明 |
|---|---|
| `PageQuery` | 分页查询对象（pageNum / pageSize / sortBy / sortOrder / @Max 防深度分页） |
| `BaseQuery` | 查询基类（继承 PageQuery，业务 Query 可扩展） |
| `OrderItem` | 排序字段项（field / direction（ASC/DESC）） |
| `PageQueryRiskAssessor` | 深度分页风险评估器（当 pageSize > 阈值时记录 WARN 日志） |
| `DeepPaginationException` | 深度分页异常（超过 maxDepth 时抛出） |
| `DeepPaginationRisk` | 深度分页风险记录（pageNum / maxDepth / recommend 信息） |

**配置分页**：

```yaml
ydsz:
  domain:
    page:
      default-page-size: 20
      max-page-size: 500
      risk-assessor-enabled: true
      max-depth: 100
```

### 2. 树形结构

| 类 | 说明 |
|---|---|
| `TreeNode` | 树节点接口（getId / getParentId / getChildren / setChildren / getOrder） |
| `TreeBuilder` | 树构建器（List<T> → 嵌套树，O(n) 复杂度，使用 Map 索引） |

**使用示例**：

```java
@Data
 public class MenuDTO implements TreeNode<Long> {
     private Long id;
     private Long parentId;
     private String name;
     private Integer order;
     private List<MenuDTO> children;

     @Override public Long getId() { return id; }
     @Override public Long getParentId() { return parentId; }
     @Override public List<MenuDTO> getChildren() { return children; }
     @Override public void setChildren(List<MenuDTO> children) { this.children = children; }
     @Override public Integer getOrder() { return order; }
 }

 // 构建树
 List<MenuDTO> menuTree = TreeBuilder.build(rootNodes, allNodes);
 // 空安全
 List<MenuDTO> safeTree = TreeBuilder.buildSafely(list);
```

### 3. 强类型 ID

| 类 | 说明 |
|---|---|
| `TypedId` | 类型化 ID 抽象类（value / type），避免 Long ID 跨域混用 |

**使用示例**：

```java
public class UserId extends TypedId {
    public UserId(Long value) { super(value); }
}

public class OrderId extends TypedId {
    public OrderId(Long value) { super(value); }
}

// 编译期类型安全，不会把 OrderId 当 UserId 传
public UserDetail loadUser(UserId userId) { ... }
```

### 4. 数据权限上下文

| 类 | 说明 |
|---|---|
| `DataPermissionContext` | 数据权限上下文（当前用户的数据范围：deptIds / projectIds / companyIds） |
| `DataScopeContextHolder` | 数据权限上下文持有器（ThreadLocal） |
| `DataPermissionHeaderConstants` | 数据权限相关 Header 常量（X-Data-Scope-*） |

### 5. 规约模式

| 类 | 说明 |
|---|---|
| `Specification<T>` | 规约接口（业务规则组合，用于复杂查询条件构建） |

**规约组合**：

```java
public class ActiveUserSpec implements Specification<User> {
    @Override
    public boolean isSatisfiedBy(User user) {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}

// 组合规约
Specification<User> spec = new ActiveUserSpec().and(new InDeptSpec(deptId));
List<User> filtered = users.stream().filter(spec::isSatisfiedBy).toList();
```

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-domain</artifactId>
</dependency>
```

### 2. 配置属性

```yaml
ydsz:
  domain:
    page:
      default-page-size: 20
      max-page-size: 500
      risk-assessor-enabled: true
      max-depth: 100
    data-permission:
      enabled: true
      cache-ttl-seconds: 300
```

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `Specification<T>` **SPI** | 业务规约（可组合业务规则） | `@Component` |
| `Repository` **SPI** | DDD 聚合根仓储接口 | `@Component` |
| `TreeNodeProvider` **SPI** | 树节点懒加载（大数据量时使用） | `@Component` |

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `DomainAutoConfiguration` | `ydsz-common-domain` 在 classpath（始终激活，绑定 DomainProperties） |

## 注意事项

1. **深度分页保护**：默认 pageSize 上限 500；翻页深度超过 `max-depth`（默认 100）时评估器写入 WARN 日志。
2. **树构建性能**：`TreeBuilder.build` 使用 Map 索引 O(n)，避免嵌套循环；超大数据集请使用 `TreeNodeProvider` SPI 懒加载。
3. **TypedId 序列化**：Jackson 序列化时自动转为 value（Long），反自动包装为对应 TypedId 子类。
4. **DataScopeContextHolder**：每次请求后由 TenantInterceptor / WebFilter 清理，避免跨请求污染。

## 变更记录

- **1.2.0**（2026-09-07）：PageQuery 深度分页风险评估器集成 `max-depth` 默认 100；TreeNode 新增 `buildSafely` 空安全方法；DataPermissionContext 拆分为 Header 常量。
- **1.1.0**（2026-08-20）：新增 TypedId 类型化 ID 体系；新增 DataPermissionContext / DataScopeContextHolder 数据权限上下文。
- **1.0.0**（2026-08-02）：初始版本（PageQuery / BaseQuery / TreeBuilder / TreeNode / Specification）。
