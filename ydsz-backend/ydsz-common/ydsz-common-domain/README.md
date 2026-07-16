# ydsz-common-domain

PMIS DDD 领域模型基类库 — 实体基类、聚合根、值对象、领域事件、规范模式、仓储接口、树形结构、分页查询。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L3 基础服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 42 |

## 核心能力

### 实体基类体系

| 类 | 说明 |
|---|---|
| `BaseIdEntity` | 带主键 ID 的基础实体 |
| `BaseEntity` | 标准实体（ID + 创建/更新时间 + 创建/更新人） |
| `BaseAuditEntity` | 审计实体（继承 BaseEntity + 软删除标记 + 版本号） |
| `BaseDO` | 数据对象基类（面向数据库表） |
| `LogBaseDO` | 日志数据对象基类 |
| `AggregateRoot` | DDD 聚合根接口（管理领域事件） |
| `RootEntity` | 根实体抽象类 |

### 实体能力接口（组合式设计）

| 接口 | 说明 |
|---|---|
| `Persistable` | 可持久化标记 |
| `Auditable` | 可审计标记（创建/更新审计字段） |
| `Versionable` | 可版本化标记（乐观锁） |
| `SoftDeletable` | 可软删除标记 |
| `TenantAware` | 多租户感知标记 |
| `ProjectAware` | 项目维度感知标记 |
| `RegionAware` | 区域维度感知标记 |
| `GroupAware` | 分组维度感知标记 |
| `EntityCapabilities` | 实体能力组合接口 |

### DDD 战术模式

| 类 / 接口 | 说明 |
|---|---|
| `ValueObject` | 值对象标记接口 |
| `DomainEvent` / `DomainEventPublisher` | 领域事件与发布器 |
| `Specification<T>` | 规范模式（AND / OR / NOT 组合） |
| `Repository<T>` | 仓储接口 |
| `@DomainService` | 领域服务注解 |

### 注解驱动审计

| 注解 | 说明 |
|---|---|
| `@CreatedBy` / `@CreateTime` | 创建人 / 创建时间自动填充 |
| `@UpdatedBy` / `@UpdateTime` | 更新人 / 更新时间自动填充 |
| `@Version` | 乐观锁版本号 |
| `@SoftDelete` | 软删除标记字段 |
| `@TenantId` | 租户 ID 字段 |

### 查询模型

| 类 | 说明 |
|---|---|
| `BaseQuery` | 查询基类 |
| `PageQuery` | 分页查询（pageNum / pageSize / 排序） |
| `PageResult<T>` | 分页结果 |
| `CursorPageResult<T>` | 游标分页结果（适用于深分页场景） |
| `BaseDTO` | 数据传输对象基类 |
| `BaseVO` | 视图对象基类 |

### 树形结构

| 类 | 说明 |
|---|---|
| `TreeNode<T>` | 树节点接口 |
| `LazyTreeNode<T>` | 懒加载树节点 |
| `TreeBuilder<T>` | 树构建器（列表 → 树） |
| `TreeNodeProvider<T>` | 树节点数据提供者 |
| `TreeLazyConfig` | 懒加载配置 |

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `DomainAutoConfiguration` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-domain</artifactId>
</dependency>
```
