# remi-common-domain

> REMI DDD 领域模型基类库（L3 基础服务层）— 分页查询、游标分页、状态机契约、树构建器、聚合根接口、幂等契约

为业务模块提供 DDD 领域驱动设计的基础设施：分页查询模型（含 offset 分页 + 可选的游标/seek 分页）、状态枚举契约（BaseStatusEnum）、O(n) 树构建器、CQRS 契约接口（Command/DTO/VO/Query）、幂等操作契约（IdempotentOperation）。

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
| **版本** | 1.6.0 |
| **源文件数** | 21 |

## 核心能力

### 1. 查询模型（query 包）

| 类 | 说明 |
|---|---|
| `BaseQuery` | 查询基类，含 searchKey/status/startDateTime/endDateTime/tenantId/ascending；提供 `hasTimeRange/isValidTimeRange/validateTimeRange/hasSearchKey/hasStatus/statusEnum` 方法；时间范围合法性校验（startDateTime 不晚于 endDateTime） |
| `PageQuery` | 分页查询（继承 `BaseQuery`），含 pageNum/pageSize/orderItems；`@NotNull/@Min/@Max` 校验；排序字段采用结构化 `OrderItem`（列名 + 方向），SQL 注入防护（`SAFE_COLUMN_PATTERN` 正则 + `allowedOrderByFields()` 白名单钩子）；LIKE 通配符转义（`%`/`_`/`\`）；超长 searchKey 截断（200 字符）；`getOffsetLong()` 支持 long 类型避免超大分页溢出 |
| `OrderItem` | 排序项 record（column + ASC/DESC），提供 `of/asc/desc` 静态工厂与 `toSql()`；替代旧版字符串拼接排序 |
| `PageResult<T>` | 分页结果封装，含 records/total/pageNum/pageSize/totalPages/hasPrevious/hasNext/startRow/endRow；`of()` 静态工厂；`empty()` 空结果；`convert(Function)` 类型转换（DO → VO，复用元数据减少计算）；`isEmpty()` 判空 |

**与 `common-core` `PageRequest` 的关系**：`PageRequest` 位于 core 模块，用于 HTTP API 层，分页字段为 `Long` 类型，与 MyBatis-Plus `Page<T>` 对齐；`PageQuery` 位于 domain 模块，用于 Service/Repository 层，分页字段为 `Integer` 类型，并集成搜索/过滤/排序白名单等业务能力。两者共用 `PageConstants` 中的默认值与上限，避免出现不一致的分页约束。

### 2. 状态机契约（enums 包）

| 类 | 说明 |
|---|---|
| `BaseStatusEnum<E>` | 状态枚举接口，定义 `canTransitTo(E)` 状态流转校验契约；`isTerminal()` 终态判断（默认 false）；`requireTransitTo(E)` 校验非法时抛 `IllegalStateException`；`allStates()` / `pathTo(E)`（BFS 最短路径推导）/ `successors()` 合法下一跳集合 |
| `TypeEnum<T>` | 通用枚举接口（code + desc），提供 `buildCodeMap/codeOf` 静态工具消除重复 CODE_MAP 初始化代码；新增 `codeOfOptional()` / `codeOfOrDefault()` Optional 安全查找 |

业务状态枚举实现示例见「使用示例」章节。

### 3. 树形结构（tree 包）

| 类 | 说明 |
|---|---|
| `TreeNode<T, ID>` | 树节点基类（递归泛型 `TreeNode<T extends TreeNode<T, ID>, ID>`），含 id/parentId/children/sort/level/path/leaf 字段；提供 `addChild/addChildren`（链式）、`isRootNode/isLeaf/getChildCount/containsChild/findById`（迭代避免栈溢出） |
| `TreeBuilder<T, ID>` | 树构建器（O(n) 时间复杂度，HashMap + HashSet 索引；无缓存、无锁，每次调用独立构建）；`build()` 自动层级计算 + 多根容错 + 排序；`findById/getDescendants/getAncestors/flatten` 查询能力；`buildSimple` 静态方法支持不实现 `TreeNode` 接口的 VO 类 |

### 4. CQRS 契约接口（contract 包）

| 类 | 说明 |
|---|---|
| `Command` | 写操作入参标记接口；携带元数据方法 `commandId()`（UUID 默认）/`issuedAt()`（Instant.now 默认），用于幂等判重、日志追踪 |
| `Query` | 读操作入参标记接口；携带元数据方法 `queryId()`/`submittedAt()`，用于链路关联 |
| `DTO` | 数据传输对象标记接口（层间传递） |
| `VO` | 视图对象标记接口（API 响应封装） |
| `IdempotentOperation` | 幂等操作契约，`getIdempotencyKey()` + `getExpireSeconds()`（默认 24h，参考 Stripe/支付宝/微信支付）；新增 `getScope()` 作用域隔离 + `getConflictPolicy()` 冲突策略（RETURN_PREVIOUS_RESULT / REJECT / FORCE_REPLAY） |

### 5. 聚合根与值对象（entity 包）

| 类 | 说明 |
|---|---|
| `BaseEntity<ID>` | 领域实体基类（纯 POJO，不含持久化基类职责—持久化基类请继承 common-jdbc 的 MpBaseEntity）；含 id/createdBy/updatedBy/createdTime/updatedTime/revision/deleted/status；领域事件支持（`registerEvent/pullDomainEvents/hasDomainEvents`，transient 不参与持久化），DDD 聚合根事件寄存器语义 |

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

```yaml
remi:
  domain:
    enabled: true                              # 是否启用（默认 true）
    page:
      max-search-key-length: 200              # 搜索关键字最长长度（1~500，默认 200）
      cursor-warning-threshold: 10000         # 深度分页警告阈值（offset ≥ 此值时打 WARN，默认 10000）
      cursor-reject-threshold: 50000          # 深度分页拒绝阈值（offset ≥ 此值时抛异常，默认 50000）
    tree:
      max-depth: 10                           # 树构建最大深度限制（1~100，默认 10）
    idempotent:
      default-expire-seconds: 86400           # 幂等键默认过期（秒，默认 86400 = 24h）
```

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.domain.enabled` | true | 是否启用 domain 模块自动配置 |
| `remi.domain.page.max-search-key-length` | 200 | 搜索关键字最大长度（1~500） |
| `remi.domain.page.cursor-warning-threshold` | 10000 | 触发深度分页警告的 offset 阈值（参考阿里规范） |
| `remi.domain.page.cursor-reject-threshold` | 50000 | 强制拒绝深度分页的 offset 阈值（必须改用游标分页） |
| `remi.domain.tree.max-depth` | 10 | 树构建最大深度限制（1~100） |
| `remi.domain.idempotent.default-expire-seconds` | 86400 | 幂等键过期时间（秒，参考 Stripe/支付宝） |

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

### 3. 树形结构构建

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

### 4. 游标分页（无限滚动场景）

```java
import com.remisoft.common.domain.query.PageQuery;
import com.remisoft.common.domain.query.CursorPage;

// Controller
@GetMapping("/feeds")
public CursorPage<FeedVO> listFeeds(PageQuery query) {
    // cursor 非空时走 seek 模式
    if (query.isCursorBased()) {
        return feedService.cursorPage(query);
    }
    return feedService.offsetPage(query);
}

// Service
public CursorPage<Feed> cursorPage(PageQuery query) {
    List<Feed> feeds = feedMapper.selectAfterCursor(
            query.getCursor(), query.getPageSize() + 1);
    boolean hasNext = feeds.size() > query.getPageSize();
    if (hasNext) {
        feeds = feeds.subList(0, query.getPageSize()); // 去掉多查的 1 条
    }
    String nextCursor = hasNext ? encodeCursor(feeds.get(feeds.size() - 1)) : null;
    return CursorPage.of(feeds, nextCursor, hasNext);
}
```

## SPI 扩展点

| 扩展点 | 用途 | 实现方 |
|---|---|---|
| `BaseStatusEnum<E>` | 状态枚举契约，业务模块的状态枚举实现此接口获得状态流转校验 | 业务模块（订单状态、流程状态、任务状态等） |
| `TypeEnum<T>` | 通用枚举契约（code + desc），配合 `buildCodeMap/codeOf` 快速反查 | 业务模块的码值枚举 |
| `TreeNode<T, ID>` | 树节点基类，业务实体继承后获得树能力 | 业务模块（菜单、组织架构、区域等树形数据） |
| `TreeBuilder.buildSimple(...)` | 静态便捷方法，支持不继承 `TreeNode` 的 VO 类构建树 | 业务模块（已有 VO 结构不便修改的场景） |
| `PageQuery.allowedOrderByFields()` | 排序字段白名单钩子，业务子类覆写返回允许排序的字段集合 | 业务模块的查询对象子类 |
| `Command` | 写操作入参标记接口，业务方可覆盖默认元数据方法（commandId/issuedAt） | 业务模块的 Command 对象 |
| `Query` | 读操作入参标记接口，业务方可覆盖默认元数据方法（queryId/submittedAt） | 业务模块的 Query 对象 |
| `IdempotentOperation` | 幂等操作契约，业务 Command 实现后获得幂等框架统一支持 | 业务模块的幂等写操作 |
| `CursorPage<T>` | 游标分页结果封装，适用于无限滚动等无 offset 场景 | 业务模块的高并发列表接口 |

## 注意事项

1. **与 `common-jdbc` `MpBaseEntity` 的关系**：业务模块的数据库实体应直接继承 `common-jdbc` 的 `MpBaseEntity`（含 `@TableId` 雪花算法、`tenant_id` 字段、与 MyBatis-Plus 注解对齐）。本模块的 `BaseEntity` 只包含 id 等字段，不含持久化职责。
2. **`PageQuery` SQL 注入防护**：排序字段经过 `SAFE_COLUMN_PATTERN`（`^[a-zA-Z_][a-zA-Z0-9_.]*$`）正则校验 + `allowedOrderByFields()` 白名单双重过滤；`searchKey` 自动转义 LIKE 通配符（`%`/`_`/`\`）并截断到 200 字符。子类务必覆写 `allowedOrderByFields()` 启用白名单。
3. **`TreeNode` 递归泛型模式限制**：`TreeNode<T extends TreeNode<T, ID>, ID>` 内部存在 `(T) this` 未经检查强转，由于类型擦除不会立即抛 `ClassCastException`，而是在返回值被使用时触发。子类必须确保泛型参数 `T` 与自身类型一致（如 `class Menu extends TreeNode<Menu, Long>`），否则运行时抛 `ClassCastException`。
4. **DAG/Job 归属**：DAG 工作流引擎与 Job 分布式处理框架已迁移至 `remi-cronjob` 模块，请勿在本模块引用。
5. **领域事件**：v1.4.0 已移除 DomainEvent/EventStore/ModuleEventTypes，如需领域事件请直接使用 Spring `ApplicationEventPublisher` 或引入 `remi-common-event` 模块。
6. **游标分页**：`CursorPage` 为无状态设计，不涉及 offset 总量计算。适用于大数据量列表接口，建议与 `PageQuery.cursor` 配合使用。
7. **`IdempotentOperation` 默认过期 24h**：与 Stripe / 支付宝 / 微信支付业界惯例对齐，可通过 `remi.domain.idempotent.default-expire-seconds` 全局配置。

## 变更记录

- **v1.6.0**（2026-08-06）：深度分页保护 + 领域事件恢复 + 业务研发体验增强——
  - **P0-2**：深度分页保护机制——`assessPaginationRisk()` 三态评估（SAFE/WARN/REJECT），`DeepPaginationException` + `DeepPaginationRisk` 枚举
  - **P1-1.2**：`BaseEntity` 恢复领域事件暂存能力（transient + @JsonIgnore，不影响持久化），参考 Spring Data @DomainEvents / Axon AbstractAggregateRoot
  - **P1-2.1**：新增 `PageSlice<T>` 轻量分页结果（无 total），适用于无限滚动/流式加载，省略 count SQL
  - **P1-2.2**：`BaseStatusEnum` 新增 `pathTo(E)`（BFS 最短路径）/ `successors()`（合法下一跳集合），流程画布/审批路径演示场景
  - **P1-3.2**：`PageResult.convert()` 复用元数据（totalPages/hasPrevious/hasNext），新增 `ofWithMetadata` 私有工厂
  - **P1-4.2**：新增 `ValidationGroups` 分组校验接口（Create/Update/PageQuery/Export/Delete），Jakarta Validation Groups 统一规范
  - **P2-1.3**：`TreeBuilder` 支持自定义 `Comparator<T>` 排序（不再局限于 sort 字段，按业务字段排序）
  - **P2-2.3**：`IdempotentOperation` 新增 `getScope()` 作用域隔离 + `IdempotentConflictPolicy` 冲突策略枚举
  - **P2-2.4**：`TypeEnum` 新增 `codeOfOptional()` / `codeOfOrDefault()` Optional 安全查找
  - README 更新为 v1.6.0，新增配置项文档与深度分页治理说明
- **v1.5.0**（2026-08-06）：DomainProperties 扩展 + 游标分页 + 契约元数据升级——
  - **P0-1**：`TreeBuilder.containsId` O(n) 全表遍历改为 O(1) HashSet 查询，修复 build() 的 O(n²) 性能退化
  - **P0-2**：新增 4 个核心测试类（PageQuery/TreeBuilder/PageResult/OrderItem），覆盖 SQL 注入防护、LIKE 转义、树构建正确性等安全逻辑
  - **P1-3**：PageQuery 增加 `cursor` / `cursorDirection` 字段支持游标分页（向后兼容，不影响现有 offset 语义）；新增 `CursorPage<T>` 结果包装类
  - **P1-4**：Command/Query 接口增加 `commandId/issuedAt/queryId/submittedAt` 元数据约定（default 实现，向后兼容）
  - **P1-5**：DomainProperties 扩展为嵌套分组（page/tree/idempotent），对齐 Spring Boot 配置命名风格，引入 additional-spring-configuration-metadata.json
  - README：移除 event 包历史描述、更新版本号与能力清单、精简注意事项
- **v1.4.0**（2026-08-04）：过度设计治理完成——
  - 移除 8 个零引用注解（@DomainService/@SoftDelete/@Version/@TenantId/@CreatedBy/@CreateAt/@UpdatedBy/@UpdateAt）
  - 移除 BaseDTO（8 个跨切面字段与 RequestContext 重复，6 个继承方改为普通 DTO）
  - 移除 TokenConstants/FilterIgnoreConstant（与 common-core 同名类重复）
  - 精简 EventStore 为 append-only（删除 4 个未实现的事件溯源查询方法）
  - 精简 DomainEvent（删除 version/tenantId/userId/traceId）
  - PageQuery 排序项重构为结构化 `OrderItem`（替代字符串拼接 + 重复解析）
  - TreeBuilder 从 700 行精简至 ~280 行（移除缓存/DCL/链式配置/统计 API）
  - TreeNode 精简（移除 traverseDFS/BFS/copy/cloneSubTree/moveTo 等未使用 API）
- **v1.3.0**：移除 AggregateRoot/RootEntity 接口，内联事件管理；移除 EntityCapabilities/Specification/BaseValueObject/BaseConverter/LazyTreeNode/CursorPageResult/TenantAware/BaseVO/TypeEnumConverterFactory 等历史组件
- **v1.0.0**（2026-08-02）：对标 `remi-common-jdbc` 标准格式重构 README，聚焦当前实际能力
