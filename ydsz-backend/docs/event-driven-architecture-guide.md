# 事件驱动架构使用规范

> **强制规范** — 适用于所有业务模块的跨模块通信场景

## 1. 背景与目的

业务模块间的数据变更通知（如项目创建、用户信息变更、审批状态变更）应采用**事件驱动架构**而非同步 Feign 调用，以实现：
- **模块解耦**：发布方无需知道消费方是谁
- **异步处理**：不阻断主流程事务
- **最终一致性**：通过事务性发件箱保证可靠性
- **可扩展性**：新增消费方无需修改发布方代码

**禁止在业务写路径中直接使用 Feign 调用通知其他模块**，必须通过事件机制。

## 2. 事件类型分类

### 2.1 本地事件（Spring Event）

**适用场景**：
- 同一模块内的异步处理（如审计日志记录）
- 不需要跨服务传播的事件

**使用方式**：
```java
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl {
    private final ApplicationEventPublisher eventPublisher;

    public void createProject(ProjectDTO dto) {
        // 1. 主业务逻辑
        ProjectDO entity = saveProject(dto);
        
        // 2. 发布本地事件（异步处理）
        eventPublisher.publishEvent(new ProjectCreatedEvent(entity.getId(), entity.getName()));
    }
}

// 监听方
@Component
public class ProjectNotificationListener {
    @EventListener
    @Async  // 异步处理，不阻断主流程
    public void onProjectCreated(ProjectCreatedEvent event) {
        // 发送通知、更新缓存等
    }
}
```

### 2.2 跨服务事件（EventPublishGateway）

**适用场景**：
- 跨模块的数据变更通知（如项目创建通知财务模块）
- 需要保证最终一致性的场景

**使用方式**：
```java
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl {
    private final EventPublishGateway eventGateway;

    @Transactional(rollbackFor = Exception.class)
    public void createProject(ProjectDTO dto) {
        // 1. 主业务逻辑
        ProjectDO entity = saveProject(dto);
        
        // 2. 发布跨服务事件（事务性发件箱）
        eventGateway.publish(StandardEventTypes.PROJECT_CREATED, 
                entity.getId(), 
                new ProjectCreatedPayload(entity.getId(), entity.getName()));
    }
}
```

## 3. 事务性发件箱模式（Outbox Pattern）

### 3.1 核心机制

`ydsz-common-event` 提供的事务性发件箱保证：
- **原子性**：业务数据和事件消息在同一事务中写入
- **可靠性**：事务提交后由 OutboxProcessor 异步投递到消息队列
- **幂等性**：消费方需实现幂等处理

### 3.2 配置方式

```yaml
# application.yml
ydsz:
  event:
    enabled: true
    gateway-type: rocketmq  # 或 noop（本地开发）
    outbox:
      enabled: true
      poll-interval-ms: 1000  # 发件箱轮询间隔
      batch-size: 100
```

### 3.3 发件箱表结构

```sql
CREATE TABLE ydsz_outbox_message (
    id VARCHAR(20) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    retry_count INT DEFAULT 0,
    error_message TEXT
);

CREATE INDEX idx_outbox_status ON ydsz_outbox_message(status);
CREATE INDEX idx_outbox_created_at ON ydsz_outbox_message(created_at);
```

## 4. 标准事件类型

`ydsz-common-event` 定义了标准事件类型常量：

```java
public final class StandardEventTypes {
    // 项目模块
    public static final String PROJECT_CREATED = "project.created";
    public static final String PROJECT_UPDATED = "project.updated";
    public static final String PROJECT_DELETED = "project.deleted";
    public static final String PROJECT_STAGE_CHANGED = "project.stage.changed";
    
    // 用户模块
    public static final String USER_CREATED = "user.created";
    public static final String USER_UPDATED = "user.updated";
    public static final String USER_DELETED = "user.deleted";
    
    // 审批模块
    public static final String APPROVAL_CREATED = "approval.created";
    public static final String APPROVAL_APPROVED = "approval.approved";
    public static final String APPROVAL_REJECTED = "approval.rejected";
    
    // 财务模块
    public static final String EXPENSE_CREATED = "expense.created";
    public static final String INVOICE_CREATED = "invoice.created";
}
```

**自定义事件类型命名规范**：
- 格式：`{module}.{entity}.{action}`
- 示例：`project.initiation.created`、`workflow.instance.approved`

## 5. 事件 Payload 设计

### 5.1 基本原则

- **最小化**：只包含消费方必需的字段
- **不可变**：事件发布后不应修改
- **自描述**：包含足够的上下文信息

### 5.2 示例

```java
// ✅ 正确：包含必要字段
public record ProjectCreatedPayload(
    String projectId,
    String projectCode,
    String projectName,
    String pmId,
    String customerId,
    LocalDateTime createdAt
) implements Serializable {}

// ❌ 错误：包含过多冗余字段
public record ProjectCreatedPayload(
    String projectId,
    String projectCode,
    String projectName,
    String description,      // 不需要
    String pmId,
    String pmName,           // 消费方自行解析
    String customerId,
    String customerName,     // 消费方自行解析
    String deptId,
    String deptName,         // 消费方自行解析
    LocalDateTime createdAt,
    LocalDateTime updatedAt  // 创建事件不需要
) implements Serializable {}
```

## 6. 事件监听规范

### 6.1 本地事件监听

```java
@Component
public class ProjectCreatedListener {
    
    @EventListener
    @Async  // 推荐：异步处理，不阻断主流程
    public void onProjectCreated(ProjectCreatedEvent event) {
        try {
            // 处理逻辑
            log.info("处理项目创建事件: projectId={}", event.getProjectId());
        } catch (Exception e) {
            // 记录错误，但不抛出异常（避免影响其他监听器）
            log.error("处理项目创建事件失败: projectId={}", event.getProjectId(), e);
        }
    }
}
```

### 6.2 跨服务事件监听

```java
@Component
@RequiredArgsConstructor
public class ProjectCreatedEventHandler {
    private final FinanceService financeService;

    @RocketMQMessageListener(
        topic = "ydsz-project-events",
        consumerGroup = "finance-consumer-group"
    )
    public void onProjectCreated(ProjectCreatedPayload payload) {
        try {
            // 1. 幂等检查
            if (financeService.isProjectProcessed(payload.projectId())) {
                log.info("项目已处理，跳过: projectId={}", payload.projectId());
                return;
            }
            
            // 2. 业务处理
            financeService.initProjectBudget(payload);
            
            // 3. 标记已处理
            financeService.markProjectProcessed(payload.projectId());
        } catch (Exception e) {
            // 记录错误，消息队列会自动重试
            log.error("处理项目创建事件失败: projectId={}", payload.projectId(), e);
            throw e;  // 抛出异常触发重试
        }
    }
}
```

## 7. 适用场景 vs 不适用场景

### 7.1 适用场景（✅ 使用事件）

| 场景 | 事件类型 | 示例 |
|-----|---------|------|
| 项目创建/更新/删除 | 跨服务事件 | 通知财务、工作流、消息模块 |
| 用户信息变更 | 跨服务事件 | 通知所有依赖用户名称的模块 |
| 审批状态变更 | 跨服务事件 | 通知业务模块更新状态 |
| 审计日志记录 | 本地事件 | 异步写入审计表 |
| 缓存失效通知 | 本地事件 | 清除本地缓存 |
| 异步任务触发 | 本地事件 | 生成报表、发送邮件 |

### 7.2 不适用场景（❌ 使用同步调用）

| 场景 | 原因 | 推荐方式 |
|-----|------|---------|
| 查询其他模块数据 | 需要实时返回结果 | Feign 调用 |
| 调用其他模块的计算逻辑 | 需要实时返回结果 | Feign 调用 |
| 同一事务内的数据一致性 | 需要强一致性 | 数据库事务 |
| 配置中心通知 | 基础设施层面 | Nacos 监听 |

## 8. 错误处理与重试

### 8.1 本地事件

- 使用 `@Async` 异步处理
- 捕获异常并记录日志
- 不影响主流程和其他监听器

### 8.2 跨服务事件

- 消息队列自动重试（默认 3 次）
- 重试间隔指数退避（1s、5s、30s）
- 超过重试次数进入死信队列
- 人工介入处理死信消息

## 9. 性能优化

### 9.1 批量发布

```java
// ❌ 错误：循环中逐条发布
for (ProjectDO project : projects) {
    eventGateway.publish(StandardEventTypes.PROJECT_CREATED, 
            project.getId(), 
            new ProjectCreatedPayload(project));
}

// ✅ 正确：批量发布
List<OutboxMessage> messages = projects.stream()
        .map(p -> OutboxMessage.of(
                StandardEventTypes.PROJECT_CREATED,
                p.getId(),
                new ProjectCreatedPayload(p)))
        .toList();
outboxService.saveBatch(messages);
```

### 9.2 事件聚合

```java
// ❌ 错误：每个字段变更都发布事件
public void updateProject(ProjectDTO dto) {
    updateName(dto.getName());
    eventGateway.publish(PROJECT_NAME_CHANGED, ...);
    
    updatePm(dto.getPmId());
    eventGateway.publish(PROJECT_PM_CHANGED, ...);
    
    updateDept(dto.getDeptId());
    eventGateway.publish(PROJECT_DEPT_CHANGED, ...);
}

// ✅ 正确：聚合为一次更新事件
public void updateProject(ProjectDTO dto) {
    updateName(dto.getName());
    updatePm(dto.getPmId());
    updateDept(dto.getDeptId());
    
    eventGateway.publish(PROJECT_UPDATED, 
            dto.getId(), 
            new ProjectUpdatedPayload(dto));
}
```

## 10. 模块采用清单

### 已采用（✅）

- [x] `ydsz-project`：项目创建/更新发布事件
- [x] `ydsz-workflow`：审批状态变更发布事件
- [x] `ydsz-literule`：规则配置变更发布事件
- [x] `ydsz-cronjob`：任务执行结果发布事件

### 待采用（⚠️）

以下模块的写路径应逐步引入事件机制：

- [ ] `ydsz-system`：配置变更、字典变更
- [ ] `ydsz-userinfo`：用户信息变更、部门变更
- [ ] `ydsz-message`：消息发送结果
- [ ] `ydsz-nextwiki`：文档变更

## 11. 验收标准

1. **所有跨模块数据变更通知**必须通过事件机制，禁止直接 Feign 调用
2. **事务性写路径**必须使用事务性发件箱（EventPublishGateway）
3. **事件 Payload**必须最小化，不包含冗余字段
4. **事件监听方**必须实现幂等处理
5. **事件类型命名**必须符合 `{module}.{entity}.{action}` 规范

## 12. 常见问题

### Q1: 事件发布失败怎么办？

**A**: 事务性发件箱保证事件和业务数据在同一事务中写入。如果事务提交成功但事件投递失败，OutboxProcessor 会自动重试。

### Q2: 如何保证事件消费的幂等性？

**A**: 消费方应通过业务主键（如 projectId）检查是否已处理。可以使用数据库唯一约束或 Redis 分布式锁。

### Q3: 事件和 Feign 调用如何选择？

**A**: 
- **需要实时返回结果** → Feign 调用（如查询用户信息）
- **仅通知数据变更** → 事件机制（如项目创建通知财务）
- **需要强一致性** → 数据库事务（同一模块内）

### Q4: 本地事件和跨服务事件如何选择？

**A**: 
- **同一模块内** → 本地事件（Spring Event）
- **跨模块** → 跨服务事件（EventPublishGateway）

## 13. 相关文件

- 事件发布网关：[EventPublishGateway.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-event/src/main/java/com/njydsz/common/event/gateway/EventPublishGateway.java)
- 事务性发件箱：[OutboxService.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-event/src/main/java/com/njydsz/common/event/service/OutboxService.java)
- 标准事件类型：[StandardEventTypes.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-event/src/main/java/com/njydsz/common/event/model/StandardEventTypes.java)
- 示例代码：[ProjectInitiationServiceImpl.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-project/ydsz-project-server/src/main/java/com/njydsz/project/server/service/impl/ProjectInitiationServiceImpl.java)

---

**最后更新**: 2026-07-27  
**维护团队**: ydsz-architecture-team
