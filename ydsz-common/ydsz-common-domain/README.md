# ydsz-common-domain

> 领域基础组件（L3 基础服务层）— 分页查询 / 树形结构 / 类型化 ID / 规约模式

提供分页查询对象（`PageQuery` / `BaseQuery`）、树形结构构建器（`TreeBuilder` / `TreeNode`）、强类型 ID（`TypedId`）、数据权限上下文、规约模式（`Specification`）等 DDD 领域基础组件，是所有业务模块领域层的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供分页查询、树形结构、强类型 ID、数据权限上下文、规约模式等 DDD 基础组件 |
| **依赖** | ydsz-common-json、ydsz-common-core、lombok、jakarta.validation-api、spring-context、jackson-annotations |
| **版本** | 1.3.0 |

## 核心能力

### 1. 分页查询

| 类 | 说明 |
|---|---|
| `PageQuery` | 分页查询对象（pageNum / pageSize / orderItems / @Max 防深度分页 / 游标模式） |
| `BaseQuery` | 查询基类（Fluent API：withStatus / withSearchKey / withTimeRange / withTenantId） |
| `OrderItem` | 结构化排序项（预计算 SQL 片段，避免重复拼接） |
| `PageQueryRiskAssessor` | 深度分页风险评估器（SAFE / WARN / REJECT 三级） |
| `DeepPaginationException` | 深度分页异常（提供 `getMessageKey()` / `getMessageParams()` 支持 i18n） |
| `DeepPaginationRisk` | 深度分页风险枚举 |

**配置属性**：

```yaml
ydsz:
  domain:
    enabled: true  # 是否启用 domain 模块自动装配（false 关闭后 DomainProperties 不绑定）
    page:
      cursor-warning-threshold: 10000  # offset 超过此值记录 WARN 日志
      cursor-reject-threshold: 50000    # offset 超过此值抛出 DeepPaginationException
```

**DeepPaginationException i18n 用法**：

```java
// 消费方通过 getMessageKey() 获取 i18n 消息键
try {
    // ... 分页查询
} catch (DeepPaginationException e) {
    String i18nMsg = MessageSourceHolder.resolve(e.getMessageKey(), e.getMessageParams());
    // 返回给前端或记录日志
}
```

### 2. 树形结构

| 类 | 说明 |
|---|---|
| `TreeNode` | 树节点基类（level / path 字段由 TreeBuilder.build() 自动填充；isLeaf() 动态计算） |
| `TreeBuilder` | 树构建器（build 全量构建 / buildLazy 懒加载构建） |
| `TreeNodeProvider` | 懒加载 SPI（大数据量场景按需加载子节点） |

**使用示例**：

```java
// 定义菜单树节点
@Data
@EqualsAndHashCode(callSuper = true)
public class Menu extends TreeNode<Menu, Long> {
    private String menuName;
}

// 构建树（自动填充 level 和 path）
List<Menu> allMenus = menuMapper.selectList();
List<Menu> tree = new TreeBuilder<>(0L, allMenus).build();

// 懒加载构建（大数据量场景）
List<Menu> tree = new TreeBuilder<Long>(0L, Collections.emptyList())
    .buildLazy(menuMapper::selectByParentId, 3);

// 空安全构建
List<Menu> safeTree = new TreeBuilder<>(nodeList).build(); // 自动处理
```

### 3. 强类型 ID

| 类 | 说明 |
|---|---|
| `TypedId<T>` | 编译期类型安全 ID（Phantom Type 模式） |

**使用示例**：

```java
// Phantom Type 定义
class Project {}
class User {}

// 编译期阻止混用
TypedId<Project> projectId = TypedId.of(123L);
TypedId<User> userId = TypedId.parse("456");

public UserDetail loadUser(TypedId<User> userId) { ... }
```

### 4. 数据权限上下文

| 类 | 说明 |
|---|---|
| `DataPermissionContext` | 数据权限上下文（EMPTY 常量 / empty() 工厂 / ofSpaceId 工厂） |
| `DataScopeContextHolder` | 数据权限上下文 ThreadLocal 持有器 |
| `DataPermissionHeaderConstants` | 数据权限相关 Header 常量 |

**使用示例**：

```java
// 获取不可变空常量（安全降级，无需 new）
DataPermissionContext empty = DataPermissionContext.empty(); // 返回共享 EMPTY 常量

// 创建可变空上下文（后续填充数据）
DataPermissionContext mutable = DataPermissionContext.emptyMutable();
mutable.setUserId(currentUserId);

// 创建仅包含空间ID的上下文
DataPermissionContext spaceCtx = DataPermissionContext.ofSpaceId(spaceId);

// 行级权限判断（null-safe）
if (context.isEmptyRowScope()) {
    // 无行级权限要求，不拦截 SQL
}
```

### 5. 规约模式

| 类 | 说明 |
|---|---|
| `Specification<T>` | 规约接口（支持 and/or/negate 二元组合 + allOf/anyOf/noneOf 批量组合） |

**规约组合示例**：

```java
// 1. 实现具体规约
public class ActiveUserSpec implements Specification<User> {
    @Override
    public boolean isSatisfiedBy(User user) {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}

// 2. Lambda 快速构建
Specification<User> ageSpec = Specification.fromPredicate(u -> u.getAge() >= 18);

// 3. 批量组合
Specification<User> combined = Specification.allOf(activeSpec, deptSpec, ageSpec);

// 4. 使用
List<User> filtered = users.stream()
    .filter(combined::isSatisfiedBy)
    .toList();
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
    enabled: true  # 默认 true；false 关闭 DomainAutoConfiguration
    page:
      cursor-warning-threshold: 10000
      cursor-reject-threshold: 50000
```

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `Specification<T>` | 业务规约（可组合业务规则） | `@Component` |
| `TreeNodeProvider<T, ID>` | 子节点懒加载（大数据量树时使用） | 匿名 Lambda |
| `Repository` | DDD 聚合根仓储接口 | `@Component` |

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `DomainAutoConfiguration` | `ydsz.domain.enabled != false` 时激活（默认激活，绑定 DomainProperties） |

## 注意事项

1. **深度分页保护**：offset 超过 `cursor-reject-threshold`（默认 50000）时抛出 `DeepPaginationException`；消费方可通过 `getMessageKey()` 获取 i18n 消息键。
2. **树构建性能**：`TreeBuilder.build()` 使用 Map 索引 O(n)，自动填充 level 和 path；超大数据集请使用 `buildLazy(provider, maxDepth)` 按需加载。
3. **TypedId 序列化**：Jackson 序列化时输出为 long 数值。
4. **DataScopeContextHolder**：每次请求后由 WebFilter 清理，避免跨请求污染。
5. **DataPermissionContext.EMPTY**：共享不可变常量，禁止通过 setter 修改。
6. **OrderItem**：不可变对象，SQL 片段在构造时预计算。

## 变更记录

- **1.3.0**（2026-09-19）：
  - 修复 TreeNode `isLeaf` 字段歧义：移除独立 `isLeaf` 字段，统一由 `isLeaf()` 动态计算；新增 `@JsonProperty("isLeaf")` 保证 JSON 序列化输出
  - 新增 `TreeNodeProvider` SPI 接口与 `TreeBuilder.buildLazy()` 懒加载能力
  - 新增 `TreeBuilder.build()` 自动填充节点的 `level` 和 `path` 字段
  - 重命名 BaseQuery `statusEnum` → `fillStatusByEnum`，新增 `withStatus/withSearchKey/withTimeRange/withTenantId` Fluent API
  - 新增 Specification `allOf/anyOf/noneOf/fromPredicate` 批量组合 API
  - 新增 OrderItem `toSql()` 构造时预计算优化
  - 新增 DataPermissionContext `EMPTY` 不可变常量与 `emptyMutable()` 工厂
  - DomainProperties `isEnabled` → `enabled`，对齐 `@ConditionalOnProperty(name="enabled")`
  - DeepPaginationException 新增 `getMessageKey()` / `getMessageParams()` 支持 i18n
  - DataPermissionContext 新增 `readObject` 反序列化 null 安全防御
  - 清理 `additional-spring-configuration-metadata.json` 未实现的 deprecated 属性
- **1.2.0**（2026-09-07）：PageQuery 深度分页风险评估器集成；TreeNode 新增 `buildSafely` 空安全方法；DataPermissionContext 拆分为 Header 常量。
- **1.1.0**（2026-08-20）：新增 TypedId 类型化 ID 体系；新增 DataPermissionContext / DataScopeContextHolder 数据权限上下文。
- **1.0.0**（2026-08-02）：初始版本（PageQuery / BaseQuery / TreeBuilder / TreeNode / Specification）。
