# ydsz-common-domain DDD 建模基类使用指南

> 基于 ydsz-common-core 全局引用分析后的深化优化建议

## 一、实体继承体系使用指南

### 1.1 当前使用情况
```java
// ✅ 已广泛使用：MpBaseEntity<String>
// 80+ 个业务实体继承此基类
public class ProjectEntity extends MpBaseEntity<String> { ... }
```

### 1.2 建议：日志表使用 LogBase
```java
// ❌ 当前：日志表继承 MpBaseEntity（包含无意义的乐观锁/软删除字段）
public class AuditLog extends MpBaseEntity<String> { ... }

// ✅ 建议：日志表继承 LogBase（仅审计字段，无 version/deleted）
public class AuditLog extends LogBase { ... }
```

LogBase 提供：`createdBy/createdAt/updatedBy/updatedAt`，不含 `revision/deleted/status`。

---

## 二、BaseDTO / BaseQuery 继承指南

### 2.1 DTO 使用 BaseDTO

```java
// ❌ 当前：手动在每个 DTO 中重复声明字段
public class ProjectDTO {
    private String operatorId;
    private String operatorName;
    private String requestId;
    private String traceId;
    private String tenantId;
    private String language;
    // ... 业务字段
}

// ✅ 建议：继承 BaseDTO
public class ProjectDTO extends BaseDTO {
    // 仅声明业务特有字段
    private String projectName;
    private BigDecimal budget;
}
```

### 2.2 查询对象使用 BaseQuery / PageQuery

```java
// ❌ 当前：手动声明 searchKey/status/时间范围
public class ProjectQuery {
    private String searchKey;
    private Integer status;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String tenantId;
    // ... 业务字段
}

// ✅ 建议：继承 BaseQuery
public class ProjectQuery extends BaseQuery {
    // 仅声明业务特有字段
    private String projectType;
    private BigDecimal minBudget;
}
```

```java
// ✅ 分页查询继承 PageQuery
public class ProjectPageQuery extends PageQuery {
    private String projectType;
    // ... 其他筛选字段
}

// 使用时自动获得：
// - normalizePageNum() / normalizePageSize() / calcOffset()
// - searchKey SQL 注入安全处理
// - orderItems 白名单校验
// - tenantId 自动过滤
```

---

## 三、树形结构使用指南

项目中存在大量树形数据（菜单、组织、分类等），应统一使用 `TreeNode` + `TreeBuilder`。

### 3.1 使用示例

```java
// 1. 实体继承 TreeNode
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDept extends MpBaseEntity<String> implements TreeNode<SysDept, String> {
    private String name;
    private Integer sort;
    
    @Override
    public String getId() { return this.id; }
    
    @Override
    public String getParentId() { return this.parentId; }
    
    @Override
    public void setChildren(List<SysDept> children) { this.children = children; }
    
    @Override
    public List<SysDept> getChildren() { return this.children; }
    
    // ... 其他字段和 getter/setter
}

// 2. 使用 TreeBuilder 构建树（O(n) 时间复杂度）
List<SysDept> flatList = deptMapper.selectAll();
List<SysDept> tree = TreeBuilder.buildSimple(flatList, "0");

// 3. 内置能力：
// DFS/BFS 遍历
tree.forEach(SysDept::walkDFS);  
// 循环检测
TreeBuilder.hasCycle(flatList);
// 查找节点
SysDept node = TreeNode.find(tree, "dept_001");
// 深拷贝
List<SysDept> copy = TreeNode.deepCopy(tree);
// 移动节点
node.moveTo(newParent);
```

---

## 四、状态机使用指南

### 4.1 实现 BaseStatusEnum

```java
// ✅ 实现状态流转约束
public enum ProjectStatus implements BaseStatusEnum<ProjectStatus> {
    DRAFT("草稿"),
    SUBMITTED("已提交"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String desc;

    @Override
    public Set<ProjectStatus> canTransitTo() {
        return switch (this) {
            case DRAFT -> Set.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> Set.of(); // 终态
        };
    }

    @Override
    public Set<ProjectStatus> requireTransitTo() {
        return Set.of(); // 无必须经过的中间态
    }

    @Override
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}

// 使用
if (!currentStatus.canTransitTo(targetStatus)) {
    throw new BusinessException(ProjectResultCode.PROJECT_STATUS_INVALID);
}
```

---

## 五、领域事件使用指南

### 5.1 发布领域事件

```java
// 在领域服务中发布事件
@Service
public class ProjectDomainService {
    
    @Transactional
    public void completeProject(String projectId) {
        ProjectEntity project = projectRepository.findById(projectId);
        project.setStatus(ProjectStatus.COMPLETED);
        
        // 注册领域事件（BaseEntity 内置能力）
        project.registerEvent(DomainEvent.builder()
            .eventType("project.completed")
            .aggregateId(project.getId())
            .aggregateType("Project")
            .metadata(Map.of("projectName", project.getName()))
            .build());
        
        projectRepository.save(project);
    }
}
```

### 5.2 监听领域事件

```java
// 在任意模块中监听事件
@Component
public class ProjectCompletedListener {
    
    @EventListener
    @Async
    public void onProjectCompleted(DomainEvent event) {
        if (!"project.completed".equals(event.getEventType())) {
            return;
        }
        
        String projectId = event.getAggregateId();
        // 发送消息通知
        // 触发后续定时任务
        // 记录审计日志
        log.info("[DomainEvent] 项目完成: {}", projectId);
    }
}
```

### 5.3 与 Feign 调用对比

```java
// ❌ 当前：模块间强耦合（直接 Feign 调用）
// ProjectService → Feign → MessageService.sendNotification()
// ProjectService → Feign → CronjobService.triggerNextTask()  
// ProjectService → Feign → AuditService.recordLog()

// ✅ 建议：领域事件解耦
// ProjectService → DomainEvent("project.completed")
//   ├── MessageModule listens → sendNotification()
//   ├── CronjobModule listens → triggerNextTask()
//   └── AuditModule listens → recordLog()
```

---

## 六、注解使用指南

```java
// @DomainService 标记领域服务（替代 @Service）
@DomainService
public class ProjectDomainService { ... }

// @SoftDelete / @Version 替代手写注解
@Data
@SoftDelete  // MyBatis-Plus 拦截器自动处理软删除
@Version     // 乐观锁自动处理
public class ProjectEntity extends MpBaseEntity<String> {
    // 无需再手写 @TableLogic 和 @Version
}
```

---

## 七、迁移优先级建议

| 改进项 | 影响模块 | 难度 | 收益 |
|--------|---------|------|------|
| DTO 继承 BaseDTO | 全部 9 个模块 | 低 | 消除重复字段声明 |
| Query 继承 BaseQuery | 全部有查询的模块 | 低 | 统一过滤/搜索规范 |
| 树形使用 TreeNode | system/userinfo/project | 中 | 消除手写递归/排序代码 |
| 状态实现 BaseStatusEnum | 全部有状态机的模块 | 中 | 防止非法状态跃迁 |
| 日志表继承 LogBase | 全部有日志表的模块 | 低 | 避免无用乐观锁字段 |
| 领域事件解耦 | project→message/cronjob | 高 | 降低模块间耦合 |
| @DomainService 标记 | 全部模块 | 低 | DDD 风格标识 |
