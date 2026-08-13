# ydsz-common-domain 架构说明

> API 生命周期状态总览、模块边界、依赖关系、设计决策文档。

## 1. 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层（领域基础） |
| **类型** | 公共依赖库，不独立部署 |
| **作用** | 分页查询模型、领域事件、状态机、树构建、类型安全 ID |
| **上游** | common-core (PageConstants)、common-json (@JsonClass/@JsonIgnore) |
| **下游消费** | common-jdbc (SafeQueryInnerInterceptor 消费 DomainProperties) |

## 2. API 生命周期状态

### 2.1 图例

| 标记 | 含义 |
|---|---|
| ✅ ACTIVE | 稳定可用，全业务模块使用 |
| 🧪 EXPERIMENTAL | 设计完成但未经生产验证，暂不推荐 |
| 🔬 ADVANCED | 理念先进，需业务方主动集成才生效 |
| 🔌 PLUGGABLE | 接口已定义，需在消费端配置后才生效 |
| 🗑️ REMOVED | 已删除，仅作历史记录保留 |

### 2.2 核心 API 矩阵

```
┌─────────────────────────────────┬──────────────┬────────────────────────────────────┐
│ 类                              │ 状态         │ 说明                               │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ query 包                        │              │                                    │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ PageQuery                       │ ✅ ACTIVE    │ 主力分页查询，约 23 处引用          │
│ BaseQuery                       │ ✅ ACTIVE    │ 查询基类                           │
│ OrderItem                       │ ✅ ACTIVE    │ 结构化排序项                       │
│ SliceQuery                      │ 🧪 EXPERIMENTAL │ 游标分页入参，0 业务引用        │
│ SliceResult                     │ 🧪 EXPERIMENTAL │ 游标分页出参，0 业务引用        │
│ DeepPaginationRisk              │ ✅ ACTIVE    │ 风险评估枚举，被拦截器消费         │
│ DeepPaginationException         │ ✅ ACTIVE    │ 深度分页异常，被拦截器抛出         │
│ CursorDirection                 │ 🧪 EXPERIMENTAL │ 游标方向枚举                     │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ enums 包                        │              │                                    │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ BaseStatusEnum                  │ ✅ ACTIVE    │ 状态枚举契约                       │
│ TypeEnum                        │ ✅ ACTIVE    │ 通用枚举接口                       │
│ StateTransitionUtil             │ 🔬 ADVANCED  │ BFS 状态路径工具，0 引用           │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ entity 包                       │              │                                    │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ BaseEntity<T>                   │ ✅ ACTIVE    │ 领域实体基类（纯领域）             │
│ EventRegistry                   │ ✅ ACTIVE    │ 领域事件接口                       │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ tree 包                         │              │                                    │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ TreeNode<T, ID>                 │ ✅ ACTIVE    │ 树节点基类                         │
│ TreeBuilder<T, ID>              │ ✅ ACTIVE    │ O(n) 树构建器                      │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ identity 包                     │              │                                    │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ TypedId<T>                      │ 🔬 ADVANCED  │ 编译期类型安全 ID，0 引用          │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ validation 包                   │              │                                    │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ ValidationGroups                │ 🔌 PLUGGABLE│ 校验分组，需 Web 层 @Validated     │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ config 包                       │              │                                    │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ DomainProperties                │ ✅ ACTIVE    │ 深度分页阈值等配置                 │
│ DomainAutoConfiguration         │ ✅ ACTIVE    │ 自动装配入口                       │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ 已删除（历史）                  │              │                                    │
├─────────────────────────────────┼──────────────┼────────────────────────────────────┤
│ contract/Query                  │ 🗑️ REMOVED  │ v1.8.0 删除（孤立标记接口）         │
│ contract/Command                │ 🗑️ REMOVED  │ v1.7.0 删除                        │
│ contract/DTO                    │ 🗑️ REMOVED  │ v1.7.0 删除                        │
│ contract/VO                     │ 🗑️ REMOVED  │ v1.7.0 删除                        │
│ PageQueryFactory                │ 🗑️ REMOVED  │ v1.8.0 删除（不再需要实例注入）     │
│ @DomainEvent / DomainEventAspect│ 🗑️ REMOVED  │ v1.8.0 删除（改用 registerEvent）  │
│ Slice<T> (旧版)                 │ 🗑️ REMOVED  │ v1.8.0 替换为 SliceQuery+SliceResult│
│ DomainEvent (class)             │ 🗑️ REMOVED  │ v1.7.0 删除（BaseEvent 不纳入 domain）│
└─────────────────────────────────┴──────────────┴────────────────────────────────────┘
```

## 3. 分层架构

```
┌──────────────────────────────────────────────────────────────┐
│                  Controller / Web 层                          │
│  接收 PageQuery / SliceQuery 参数，响应绑定到 SliceResult     │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│                  Service / UseCase 层                         │
│  调用 query.assessPaginationRisk()，获取风险评估              │
│  使用 TreeBuilder 构建树形结构                                │
│  继承 BaseEntity 的实体调用 registerEvent()                   │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│              Repository / Mapper 层 (common-jdbc)             │
│  SafeQueryInnerInterceptor 拦截 SQL:                          │
│    ├─ ORDER BY 字段安全校验                                   │
│    └─ 深度分页 offset >= rejectThreshold → 抛异常             │
│  执行 order.pullDomainEvents() 后发布事件                     │
└──────────────────────────────────────────────────────────────┘
```

## 4. 关键依赖边界

### 4.1 允许依赖（compile scope）
- `common-core`（PageConstants 归一化工具）
- `common-json`（@JsonClass / @JsonIgnore 注解）
- `jakarta.validation`（@NotNull / @Min / @Max）
- `spring-context`（@ConfigurationProperties）

### 4.2 禁止依赖
- `common-jdbc`（通过 SafeQueryInnerInterceptor 间接触发，不直接依赖）
- `common-web`（Web 层注入 VO/Command，由 Web 层依赖 domain）
- `common-tenant`（租户隔离由 Web/Filter 层完成）
- 任何持久化框架（MyBatis-Plus / JPA）

### 4.3 深度分页链路

```
PageQuery.assessPaginationRisk()
    │
    ├── warnThreshold / rejectThreshold (DomainProperties)
    │
    ▼
DeepPaginationRisk.assess(offset, warn, reject)   ←── 纯函数，无副作用
    │
    ├── SAFE   → 放行，正常执行 OFFSET 分页
    ├── WARN   → 仅日志告警（PageConstants.calcOffset 中 log.warn）
    └── REJECT → SafeQueryInnerInterceptor 抛 DeepPaginationException
                                                    │
                                                    ▼
                                          全局异常处理转换为
                                          标准错误响应（500 → 业务码）
```

## 5. 设计决策记录（ADR）

### ADR-001：为什么 BaseEntity.hashCode 仅以 id 判同？

**状态：** 已采纳

**背景：** Lombok `@Data` 默认生成包含所有字段的 equals/hashCode。这意味着同一 DB 行在不同时间点（createdAt/updatedAt 不同）被判为不等，导致 HashSet/HashMap 行为异常。

**决策：** `@EqualsAndHashCode(of = {"id"}, callSuper = false)`

**理由：** 
- DDD 规范中，实体身份由 ID 定义（Eric Evans, "Domain-Driven Design" Section 5）
- Vlad Mihalcea 推荐实体仅以 ID 判同
- 避免持久化框架加载的同一行在不同时间变为不等的 bug

### ADR-002：为什么废弃 @DomainEvent 注解改用 registerEvent()？

**状态：** 已采纳

**背景：** v1.7.0 引入 `@DomainEvent` 注解 + AOP 切面注册事件。但 AOP 存在以下限制：
- 内部方法调用不触发 AOP（自调用问题）
- 测试场景下驱动事件注册复杂
- 事件类型依赖字符串，无编译期约束

**决策：** 改用 `registerEvent(Object event)` 显式调用，更透明、可控。

### ADR-003：为什么 PageQuery.assessPaginationRisk() 使用默认阈值？

**状态：** 已采纳

**背景：** Spring 上下文中配置值覆盖默认值，但非 Spring 场景（单元测试、独立工具）需要"开箱即用"。

**决策：** 提供 `assess(offset)` 使用 DEFAULT 常量，提供 `assess(offset, warn, reject)` 供运行时覆盖。

### ADR-004：为什么保留 SliceQuery/SliceResult 但暂不推荐？

**状态：** 已采纳（待观察）

**背景：** 对标 Spring Data `Pageable/Slice` 的设计理念正确，但当前无业务需求驱动落地。过早推广无收益，删除则浪费设计投入。

**决策：** 保留并标注 EXPERIMENTAL 状态，等首个实际业务落地（如表格无限滚动）时再推广。

## 6. 文件组织结构

```
src/main/java/com/njydsz/common/domain/
├── config/              # 配置与装配
│   ├── DomainAutoConfiguration.java
│   └── DomainProperties.java
├── entity/              # 聚合根基类 + 事件接口
│   ├── BaseEntity.java
│   └── EventRegistry.java
├── enums/               # 状态枚举契约 + 工具
│   ├── BaseStatusEnum.java
│   ├── StateTransitionUtil.java
│   └── TypeEnum.java
├── identity/            # 类型安全 ID
│   └── TypedId.java
├── query/               # 分页查询模型（核心）
│   ├── BaseQuery.java
│   ├── CursorDirection.java
│   ├── DeepPaginationException.java
│   ├── DeepPaginationRisk.java
│   ├── OrderItem.java
│   ├── PageQuery.java
│   ├── SliceQuery.java
│   └── SliceResult.java
├── tree/                # 树形结构工具
│   ├── TreeBuilder.java
│   └── TreeNode.java
└── validation/          # 校验分组
    └── ValidationGroups.java
```

## 7. 下游使用规范

### 7.1 分页查询
```java
// 正确：使用 PageQuery，按枚举实现状态枚举
PageQuery query = PageQuery.builder()
    .pageNum(request.getPageNum())    // 来自 HTTP 参数
    .pageSize(request.getPageSize())
    .searchKey(request.getKeyword())
    .addDescOrder("created_at")
    .build();

// 调用拦截器评估风险（可选，SafeQueryInnerInterceptor 已自动拦截）
DeepPaginationRisk risk = query.assessPaginationRisk();
```

### 7.2 BaseEntity 使用
```java
// 正确：继承 BaseEntity 获得领域能力
public class Order extends BaseEntity<Long> {
    private String status;

    public void markAsPaid() {
        // 业务规则校验...
        this.status = "PAID";
        // 显式注册领域事件（v1.8.0 方式）
        registerEvent(new OrderPaidEvent(this.id, LocalDateTime.now()));
    }
}

// Repository 层分派事件
orderMapper.updateById(order);
order.pullDomainEvents().forEach(eventPublisher::publishEvent);
```

### 7.3 TypedId 使用（可选）
```java
// 可选：落地类型安全 ID 减少 ID 混用 bug
public class Project extends BaseEntity<TypedId<Project>> {
    // getId() 返回 TypedId<Project>（编译期区分于 TypedId<User>）
}

TypedId<Project> projectId = Project.of(123L);
projectRepository.findById(projectId);
```
