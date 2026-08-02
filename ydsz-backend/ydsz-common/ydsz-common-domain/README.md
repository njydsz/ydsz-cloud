# ydsz-common-domain

> YDSZ DDD 领域模型基类库（L3 基础服务层）— 实体基类体系、领域事件、查询模型、树形结构、DAG 引擎、Job 框架

为业务模块提供 DDD 领域驱动设计的基础设施：实体继承层次（ID → 审计 → 乐观锁/软删除/领域事件）、领域事件基类与跨模块事件类型注册表、分页查询与时间范围校验、O(n) 树构建器、SpEL 条件评估器、分布式任务处理器接口。所有业务模块的实体类、查询对象、领域事件、任务处理器均以本模块为统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 DDD 领域模型基础设施：实体基类体系、领域事件、查询模型、树形结构、DAG 引擎、Job 框架、状态枚举契约 |
| **依赖** | common-core、common-json；Lombok、Jakarta Validation、MyBatis-Plus Annotation、Spring TX、Spring Context；可选依赖 Spring Boot Health |
| **版本** | 1.0.0 |
| **源文件数** | 43 |

## 核心能力

### 1. 实体基类体系（entity 包）

```
Persistable<T>                    (接口: ID + isNew，支持 UUID 主键判断)
  └─ BaseIdEntity<T>              (ID 字段，泛型主键类型，支持雪花算法 / UUID / 自增)
       └─ BaseAuditEntity<T>     (审计字段 createdBy/createdAt/updatedBy/updatedAt，implements Auditable)
            └─ BaseEntity<T>     (revision 乐观锁 + deleted 软删除 + status + 领域事件，implements Versionable + SoftDeletable)
                 ├─ BaseString    (String 主键，全项目 String 主键实体基类)
                 ├─ BaseLong      (Long 主键，全项目 Long 主键实体基类)
                 └─ LogBase       (日志型实体，String 主键，仅审计字段无乐观锁/软删除)
```

| 类 | 说明 |
|---|---|
| `Persistable<T>` | 可持久化标记接口（Spring Data 风格），定义 `getId/setId/isNew` 契约；`isNew` 支持 UUID/String 主键场景（通过 `Auditable.createdAt` 判断） |
| `BaseIdEntity<T>` | 主键基础实体，仅含 ID 字段，适用于字典表、配置表等简单场景 |
| `BaseAuditEntity<T>` | 审计字段基础实体，含 createdBy/createdAt/updatedBy/updatedAt，由 MyBatis-Plus 自动填充；`isFresh()` 判断新建状态 |
| `BaseEntity<T>` | 完整实体基类，含 `revision` 乐观锁、`deleted` 软删除、`status` 状态、`domainEvents` 瞬态领域事件列表；标注 `@SoftDelete` |
| `Base` | String 主键实体基类（继承 `BaseEntity<String>`） |
| `BaseLong` | Long 主键实体基类（继承 `BaseEntity<Long>`），适用于雪花算法 / 自增 BIGINT |
| `LogBase` | 日志型实体基类（继承 `BaseAuditEntity<String>`），仅审计字段不含乐观锁/软删除，适用于日志表、操作记录表 |

### 2. 能力标记接口（entity 包）

| 接口 | 说明 |
|---|---|
| `Persistable<T>` | 可持久化标记（ID + isNew，支持 UUID 主键判断） |
| `Auditable` | 可审计标记（createdBy/createdAt/updatedBy/updatedAt 标准访问方法） |
| `Versionable` | 可版本化标记（`getRevision/setRevision`，配合乐观锁拦截器） |
| `SoftDeletable` | 可软删除标记（`getDeleted/isDeleted`，0=未删除，1=已删除） |

### 3. 注解驱动审计（annotation 包）

| 注解 | 目标 | 说明 |
|---|---|---|
| `@CreatedBy` / `@CreateAt` | FIELD | 创建人 / 创建时间字段标记，框架在 INSERT 时自动填充 |
| `@UpdatedBy` / `@UpdateAt` | FIELD | 更新人 / 更新时间字段标记，框架在 INSERT/UPDATE 时自动填充 |
| `@Version` | FIELD | 乐观锁版本字段标记，配合 SQL 拦截器自动递增 + `WHERE revision = oldRevision` |
| `@SoftDelete` | TYPE | 软删除标记（类级注解），默认字段名 `deleted`，配合 SQL 拦截器自动改写 DELETE → UPDATE |
| `@TenantId` | FIELD | 租户字段标记，默认字段名 `tenant_id`，配合 SQL 拦截器自动注入条件 |
| `@DomainService` | TYPE | 领域服务标识注解（继承 Spring `@Component`），与 `@Service` 区分 DDD 领域语义 |

### 4. 领域事件（event 包）

| 类 | 说明 |
|---|---|
| `DomainEvent` | 领域事件基类，继承 Spring `ApplicationEvent`，可直接通过 `ApplicationEventPublisher` 发布并由 `@EventListener` 消费；Builder 模式 + `Clock` 注入（便于测试）；自动从 `RequestContext` 填充 tenantId/userId/traceId；不可变设计（`metadata` 为不可变 Map） |
| `ModuleEventTypes` | 跨模块事件类型常量注册表（单一来源），包含 WORKFLOW_INSTANCE_STARTED/COMPLETED/REJECTED、WORKFLOW_TASK_CREATED/COMPLETED/URGED/TRANSFERRED、UNIFIED_ALERT、CRONJOB_EXECUTION_FAILED/TIMEOUT、PERMISSION_CHANGED、CONFIG_CHANGED、OPERATION_LOG、DATA_EXPORT_AUDIT |

事件发布直接使用 Spring 内置机制，不引入额外 `EventPublisher` 抽象：

```java
// 同步发布
applicationEventPublisher.publishEvent(new OrderCreatedEvent(orderId));

// 事务提交后发布（推荐，避免事件消费者读到未提交数据）
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderCreated(OrderCreatedEvent event) { ... }
```

### 5. 查询模型（query 包）

| 类 | 说明 |
|---|---|
| `BaseQuery` | 查询基类，含 searchKey/status/startDateTime/endDateTime/tenantId/ascending；提供 `hasTimeRange/isValidTimeRange/validateTimeRange/hasSearchKey/hasStatus/statusEnum` 方法；时间范围合法性校验（startDateTime 不晚于 endDateTime） |
| `PageQuery` | 分页查询（继承 `BaseQuery`），含 pageNum/pageSize/orderItems；`@NotNull/@Min/@Max` 校验；SQL 注入防护（`SAFE_COLUMN_PATTERN` 正则 + `allowedOrderByFields()` 白名单钩子）；LIKE 通配符转义（`%`/`_`/`\`）；超长 searchKey 截断（200 字符）；`getOffsetLong()` 支持 long 类型避免超大分页溢出 |
| `PageResult<T>` | 分页结果封装，含 records/total/pageNum/pageSize/totalPages/hasPrevious/hasNext/startRow/endRow；`of()` 静态工厂；`empty()` 空结果；`convert(Function)` 类型转换（DO → VO）；`isEmpty()` 判空 |
| `BaseDTO` | 数据传输对象基类，含 operatorId/operatorName/requestId/traceId/tenantId/language/source/remark 跨切面上下文字段 |

**与 `common-core` `PageRequest` 的关系**：`PageRequest` 位于 core 模块，用于 HTTP API 层，分页字段为 `Long` 类型，与 MyBatis-Plus `Page<T>` 对齐；`PageQuery` 位于 domain 模块，用于 Service/Repository 层，分页字段为 `Integer` 类型，并集成搜索/过滤/排序白名单等业务能力。两者共用 `PageConstants` 中的默认值与上限，避免出现不一致的分页约束。

### 6. 树形结构（tree 包）

| 类 | 说明 |
|---|---|
| `TreeNode<T, ID>` | 树节点基类（递归泛型 `TreeNode<T extends TreeNode<T, ID>, ID>`），含 id/parentId/children/sort/level/path/leaf 字段；提供 DFS/BFS 遍历、`findById`（迭代避免栈溢出）、`getAncestorIds`（HashMap 索引 O(n)）、`getRoot`、`containsChild`、`addChild/addChildren`（链式）、`moveTo`、`copy/cloneSubTree`（浅拷贝/深拷贝） |
| `TreeBuilder<T, ID>` | 树构建器（O(n) 时间复杂度，HashMap 缓存）；双重检查锁（DCL）+ `ReentrantLock`（避免 JDK21 虚拟线程 Pinning）；循环引用检测（visited 集合 + maxDepth 兜底）；自动层级计算、路径生成（按 level 升序，消除对 nodeList 顺序的隐含依赖）；多根支持；缓存机制（`cachedRoots/cachedNodeMap/cachedAllNodes` + `dirty` 标记）；超 10000 节点切换迭代模式；`buildSimple` 静态方法支持不实现 `TreeNode` 接口的 VO 类 |

### 7. DAG 工作流引擎（dag 包）

| 类 | 说明 |
|---|---|
| `DagInstanceStatus` | DAG 实例状态枚举（PENDING/RUNNING/SUCCESS/FAILED/PARTIAL_SUCCESS/CANCELLED/TIMEOUT） |
| `DagNodeStatus` | DAG 节点状态枚举（PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/TIMEOUT） |
| `SpELConditionEvaluator` | SpEL 条件表达式评估器（DAG CONDITION 节点使用），支持 `${...}` 包裹或纯 SpEL；实例级 LRU 缓存（`LinkedHashMap` accessOrder=true + `removeEldestEntry`，避免 classloader 级内存泄漏）；`clearCache()` 运行时清理；`getCacheSize()` 监控诊断；评估失败返回 false（保守策略） |

### 8. Job 分布式处理框架（job 包）

> 由 `ydsz-cronjob` 调度框架实现，本模块仅提供契约接口与上下文持有者。

| 类 / 接口 | 说明 |
|---|---|
| `JobHandler` | 任务处理器核心接口，`execute(String paramsJson)` 与 `execute(String, ShardingContext)` 默认实现（支持分片执行） |
| `MapProcessor` | Map 任务处理器接口，`process(MapContext)` 返回 `ProcessResult` |
| `MapReduceProcessor` | MapReduce 任务处理器接口（继承 `MapProcessor`），`reduce(List<MapContext>, MapContext)` 汇总子任务结果 |
| `MapContext` | MapReduce 任务上下文，含 jobId/logId/jobKey/taskName/taskParams/root/subTasks/results；`addSubTask(name, params)` 添加子任务 |
| `MapTask` | MapReduce 子任务定义（taskName + taskParams JSON） |
| `ProcessResult` | 任务执行结果（success/result JSON/errorMessage），`success()/success(result)/failed(errorMsg)` 静态工厂 |
| `ShardingContext` | 分片上下文（shardTotal/shardIndex/jobId/logId） |
| `JobLogger` | 任务执行日志器接口（info/warn/error/debug + 格式化重载 + `flush`） |
| `JobContextHolder` | 分片上下文 ThreadLocal 持有者（`InheritableThreadLocal`，子线程自动继承） |
| `JobLoggerHolder` | 任务日志器 ThreadLocal 持有者（`InheritableThreadLocal`，子线程自动继承） |

### 9. 枚举体系（enums 包）

| 类 | 说明 |
|---|---|
| `BaseStatusEnum<E>` | 状态枚举接口，定义 `canTransitTo(E)` 状态流转校验契约；`isTerminal()` 终态判断（默认 false）；`requireTransitTo(E)` 校验非法时抛 `IllegalStateException`；配合 `StatusTransitionAspect` 或业务层显式调用实现状态机 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-domain</artifactId>
</dependency>
```

### 2. 自动配置

通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动注册 `DomainAutoConfiguration`，无需手动 `@EnableXxx`。`ydsz.domain.enabled=false` 可关闭自动配置。

### 3. 实体类继承

```java
import com.njydsz.common.domain.entity.Base;
import com.njydsz.common.domain.entity.BaseLong;
import lombok.Data;
import lombok.EqualsAndHashCode;

// String 主键实体（UUID / 业务主键）
@Data
@EqualsAndHashCode(callSuper = true)
public class SysConfig extends Base {
    private String configKey;
    private String configValue;
}

// Long 主键实体（雪花算法 / 自增 BIGINT）
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseLong {
    private String username;
    private String email;
}
```

> 数据库实体类命名遵循 `entity-naming.md` 规则：直接使用业务名称作为类名，**不加 `DO` 后缀**。例外见规则文件中的同名冲突清单。

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.domain.enabled` | true | 是否启用 domain 模块自动配置 |
| `ydsz.domain.spel.cache-enabled` | true | 是否启用 SpEL 表达式解析缓存 |
| `ydsz.domain.spel.cache-max-size` | 1024 | SpEL 表达式缓存最大容量（LRU 淘汰，0 表示无限制） |

## 使用示例

### 1. 实体继承 + 领域事件

```java
import com.njydsz.common.domain.entity.BaseLong;
import com.njydsz.common.domain.event.DomainEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseLong {
    private String username;
    private String email;

    public void changeEmail(String newEmail) {
        String oldEmail = this.email;
        this.email = newEmail;
        // 注册领域事件，由业务层统一发布
        registerEvent(DomainEvent.builder()
                .eventType("UserEmailChanged")
                .aggregateId(String.valueOf(getId()))
                .aggregateType("User")
                .metadata("oldEmail", oldEmail)
                .metadata("newEmail", newEmail)
                .build());
    }
}
```

### 2. 自定义领域事件

```java
import com.njydsz.common.domain.event.DomainEvent;

public class OrderCreatedEvent extends DomainEvent {
    private final Long orderId;
    private final String totalAmount;

    public OrderCreatedEvent(Long orderId, String totalAmount) {
        super(UUID.randomUUID().toString(), LocalDateTime.now(),
              "OrderCreated",
              String.valueOf(orderId), "Order", 1,
              null, null, null, Collections.emptyMap());
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

### 3. 分页查询（含白名单校验）

```java
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.domain.query.PageResult;

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
query.addDescOrder("created_at");    // 经白名单 + 正则校验

PageResult<User> page = userService.page(query);
PageResult<UserVO> voPage = page.convert(UserVO::new);  // DO → VO 转换
```

### 4. 树形结构构建

```java
import com.njydsz.common.domain.tree.TreeNode;
import com.njydsz.common.domain.tree.TreeBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Menu extends TreeNode<Menu, Long> {
    private String menuName;
    private String routePath;
}

// 构建（自动层级计算 + 路径生成 + 循环引用检测）
List<Menu> allMenus = menuMapper.selectList(null);
List<Menu> tree = new TreeBuilder<>(0L, allMenus).build();

// 查询能力
Menu node = new TreeBuilder<>(0L, allMenus).findById(1L);
List<Menu> descendants = new TreeBuilder<>(0L, allMenus).getDescendants(node);
int depth = new TreeBuilder<>(0L, allMenus).getTreeDepth();
List<Menu> leaves = new TreeBuilder<>(0L, allMenus).getLeafNodes();

// 静态便捷方法（VO 类无需继承 TreeNode）
List<MenuVO> treeVo = TreeBuilder.buildSimple(
        flatList,
        MenuVO::getId,
        MenuVO::getParentId,
        MenuVO::setChildren,
        MenuVO::getSort);
```

### 5. DAG SpEL 条件评估

```java
import com.njydsz.common.domain.dag.SpELConditionEvaluator;

SpELConditionEvaluator evaluator = new SpELConditionEvaluator(true, 1024);

Map<String, Object> context = new HashMap<>();
context.put("nodeA", Map.of("result", "success", "status", "SUCCESS"));
context.put("nodeB", Map.of("result", "failed"));

// 支持 ${...} 包裹或纯 SpEL 表达式
boolean shouldExecute = evaluator.evaluate("${nodeA.result=='success'}", context);
// shouldExecute = true

// 表达式解析结果缓存，重复评估零开销
int cacheSize = evaluator.getCacheSize();
evaluator.clearCache();  // 运行时配置变更时清理
```

### 6. Job 处理器实现

```java
import com.njydsz.common.domain.job.JobHandler;
import com.njydsz.common.domain.job.ShardingContext;
import com.njydsz.common.domain.job.JobContextHolder;
import com.njydsz.common.domain.job.JobLoggerHolder;

public class DataSyncJobHandler implements JobHandler {

    @Override
    public Object execute(String paramsJson) throws Exception {
        // 通过 JobContextHolder 获取分片上下文（cronjob 框架注入）
        ShardingContext ctx = JobContextHolder.get();
        if (ctx != null) {
            int shardIndex = ctx.getShardIndex();
            int shardTotal = ctx.getShardTotal();
            // 按分片处理数据
            return doShardingSync(shardIndex, shardTotal, paramsJson);
        }
        return doFullSync(paramsJson);
    }

    @Override
    public Object execute(String paramsJson, ShardingContext ctx) throws Exception {
        // 显式接收分片上下文
        JobLoggerHolder.getLogger().info("分片 {}/{} 开始执行", ctx.getShardIndex(), ctx.getShardTotal());
        return doShardingSync(ctx.getShardIndex(), ctx.getShardTotal(), paramsJson);
    }
}
```

### 7. MapReduce 任务实现

```java
import com.njydsz.common.domain.job.MapContext;
import com.njydsz.common.domain.job.MapReduceProcessor;
import com.njydsz.common.domain.job.ProcessResult;

public class UserStatMapReduceJob implements MapReduceProcessor {

    @Override
    public ProcessResult process(MapContext ctx) {
        if (ctx.isRoot()) {
            // Root 任务：按城市拆分子任务
            for (String city : cityList) {
                ctx.addSubTask("statByCity", city);
            }
            return ProcessResult.success();
        }
        // 子任务：统计指定城市用户数
        long count = userMapper.countByCity(ctx.getTaskParams());
        ctx.getResults().put(ctx.getTaskParams(), count);
        return ProcessResult.success(String.valueOf(count));
    }

    @Override
    public ProcessResult reduce(List<MapContext> subContexts, MapContext rootContext) {
        // 汇总所有子任务结果
        Map<String, Long> stat = new HashMap<>();
        for (MapContext sub : subContexts) {
            sub.getResults().forEach((k, v) -> stat.put(k, (Long) v));
        }
        return ProcessResult.success(JSON.toJson(stat));
    }
}
```

### 8. 状态枚举实现

```java
import com.njydsz.common.domain.enums.BaseStatusEnum;

public enum OrderStatus implements BaseStatusEnum<OrderStatus> {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

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

## SPI 扩展点

> 本模块以"基类 + 接口契约"为核心扩展模式，业务模块通过继承/实现获得能力。

| 扩展点 | 用途 | 实现方 |
|---|---|---|
| `DomainEvent` | 跨模块领域事件基类，子类继承后通过 `ApplicationEventPublisher` 发布 | 业务模块（如 workflow、notify、project 等） |
| `ModuleEventTypes` | 跨模块事件类型常量注册表，新增模块事件时在此添加常量 | 业务模块新增事件类型时修改本类 |
| `JobHandler` | 任务处理器核心契约，业务模块实现后由 `ydsz-cronjob` 调度框架扫描注册 | 业务模块（如 cronjob、workflow） |
| `MapProcessor` / `MapReduceProcessor` | 分布式 Map/MapReduce 任务契约 | 业务模块（大数据量分片处理场景） |
| `JobLogger` | 任务日志器接口，由 `ydsz-cronjob` 提供实现并注入 `JobLoggerHolder` | `ydsz-cronjob` 模块 |
| `BaseStatusEnum<E>` | 状态枚举契约，业务模块的状态枚举实现此接口获得状态流转校验 | 业务模块（订单状态、流程状态、任务状态等） |
| `TreeNode<T, ID>` | 树节点基类，业务实体继承后获得 DFS/BFS/路径/查找能力 | 业务模块（菜单、组织架构、区域等树形数据） |
| `TreeBuilder.buildSimple(...)` | 静态便捷方法，支持不继承 `TreeNode` 的 VO 类构建树 | 业务模块（已有 VO 结构不便修改的场景） |
| `PageQuery.allowedOrderByFields()` | 排序字段白名单钩子，业务子类覆写返回允许排序的字段集合 | 业务模块的查询对象子类 |
| `TreeNode.newInstance()` / `copyFieldsTo(T)` | 节点拷贝扩展点，子类覆写以避免反射开销并复制自定义字段 | 业务模块的 `TreeNode` 子类 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health` | Domain 模块健康检查作为整体 health 端点的一部分 | `spring-boot-health` 在类路径（可选依赖） |

**`DomainHealthIndicator` 暴露信息**：

- `spELConditionEvaluator.available` — SpEL 评估器是否已注册（true/false）
- `spELConditionEvaluator.cacheSize` — 当前 SpEL 表达式缓存数量

**健康状态规则**：

- SpEL 评估器未注册 → UP（带 `available=false` 详情，但不影响整体健康状态）
- 评估器正常 → UP

> `DomainHealthIndicator` 仅读取 SpEL 评估器缓存计数，不执行表达式评估，避免对运行中的 DAG 实例产生副作用。

## 注意事项

1. **与 `common-jdbc` `MpBaseEntity` 的关系**：业务模块的数据库实体应直接继承 `common-jdbc` 的 `MpBaseEntity`（含 `@TableId` 雪花算法、`tenant_id` 字段、与 MyBatis-Plus 注解对齐）；本模块的 `Base/BaseLong/BaseEntity` 用于不需要 MyBatis-Plus 强绑定的领域实体场景。两者不可同时继承。
2. **乐观锁与软删除**：`BaseEntity.revision` 与 `deleted` 字段由 `common-jdbc` 的自定义拦截器（`OptimisticLockInterceptor` / `LogicalDeleteInterceptor`）处理，**不**使用 `@Version` 与 `@TableLogic` 注解，避免双重处理冲突。
3. **领域事件发布时机**：推荐使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 在事务提交后消费事件，避免事件消费者读到未提交数据或事务回滚后事件已发出的语义错误。
4. **`JobContextHolder` / `JobLoggerHolder` 线程池限制**：使用 `InheritableThreadLocal`，仅在线程首次创建时继承父线程的值，**不会**在线程复用时更新。线程池场景请使用 `TaskDecorator` 或在提交任务前显式调用 `set(...)` / `clear()`，避免上下文泄漏到下一个任务。
5. **`SpELConditionEvaluator` 缓存为实例级**：缓存 Map 随 `SpELConditionEvaluator` Bean 实例生命周期存活（非静态），避免 classloader 级内存泄漏。运行时配置变更时调用 `clearCache()` 清理。
6. **`TreeNode` 递归泛型模式限制**：`TreeNode<T extends TreeNode<T, ID>, ID>` 内部存在 `(T) this` 未经检查强转，由于类型擦除不会立即抛 `ClassCastException`，而是在返回值被使用时触发。子类必须确保泛型参数 `T` 与自身类型一致（如 `class Menu extends TreeNode<Menu, Long>`），否则运行时抛 `ClassCastException`。
7. **`PageQuery` SQL 注入防护**：排序字段经过 `SAFE_COLUMN_PATTERN`（`^[a-zA-Z_][a-zA-Z0-9_.]*$`）正则校验 + `allowedOrderByFields()` 白名单双重过滤；`searchKey` 自动转义 LIKE 通配符（`%`/`_`/`\`）并截断到 200 字符。子类务必覆写 `allowedOrderByFields()` 启用白名单。
8. **`DomainEvent` 不可变性**：`metadata` 字段在构造时包装为 `Collections.unmodifiableMap`，Builder 的 `metadata(key, value)` 在 `build()` 时拷贝到不可变 Map。子类不应提供可变字段 setter。
9. **`TreeBuilder` 大数据量阈值**：节点数超过 10000 时打印 WARN 日志并切换迭代模式构建（不递归），避免栈溢出。生产环境如需构建超大树（>10万节点），建议分批构建或采用懒加载方案。
10. **配置项历史遗留**：`additional-spring-configuration-metadata.json` 中包含 `ydsz.domain.event.async-enabled` / `ydsz.domain.event.default-phase` / `ydsz.tree.lazy.*` 等历史配置项的描述，但对应的 `DomainEventPublisher` / `LazyTreeNode` 等组件已在简化重构中删除，这些配置项不再生效，仅保留元数据避免 IDE 配置提示报错。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 `ydsz-common-jdbc` 标准格式重构 README，补全全部 9 个章节；移除对已删除组件（`DomainEventPublisher` / `EventStore` / `EntityCapabilities` / `Specification` / `BaseValueObject` / `BaseConverter` / `LazyTreeNode` / `CursorPageResult` / `AggregateRoot` / `RootEntity` / `TenantAware` / `BaseVO` / `TypeEnumConverterFactory` 等）的历史删除记录，聚焦当前实际能力
