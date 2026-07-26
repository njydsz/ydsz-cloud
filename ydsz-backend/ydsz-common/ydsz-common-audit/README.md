# ydsz-common-audit

YDSZ 操作审计框架 — `@Audit` 声明式 AOP 审计、事件驱动异步落库、Disruptor 高性能批写、3 种分表策略、变更差异计算（before/after）、敏感字段脱敏、审计查询服务、Micrometer 指标桥接、健康检查。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 38 |

## 核心能力

### 声明式审计

| 注解 | 说明 |
|---|---|
| `@Audit` | 操作审计注解（记录方法调用 / 参数 / 返回值 / 异常，SpEL 描述） |
| `@EnableYdszAudit` | 审计开关 / 自动装配入口 |

> 早期文档曾提及 `@OperationLog` / `@ApiMetrics` / `@DataExportAudit` / `@EnableAudit` 注解，**当前版本未实现**。等价能力以事件类提供：`OperationLogEvent` / `DataExportAuditEvent`（见「领域模型」），业务方可手动 `publishEvent` 触发落库；如需声明式入口，请使用 `@Audit` 并在 `module` / `action` 中携带语义。

### AOP 切面与模板

| 类 | 说明 |
|---|---|
| `AuditAspect` | 审计切面（方法拦截 → 事件构建 → 异步发布） |
| `AuditTemplateProcessor` | SpEL 模板解析（审计描述动态渲染） |

### 审计记录器

| 类 | 说明 |
|---|---|
| `AuditRecorder` | 审计记录器接口 |
| `DefaultAuditRecorder` | 默认同步记录器 |
| `AsyncAuditRecorder` | 异步记录器（线程池 + 队列） |
| `DisruptorAuditRecorder` | Disruptor 高性能批写记录器（无锁环形缓冲区） |
| `AuditFallbackWriter` | 降级写入器（主存储失败时落到本地文件 / 日志） |

### 审计存储

| 类 | 说明 |
|---|---|
| `AuditStorage` | 存储接口 |
| `DefaultAuditStorage` | 默认存储（日志输出） |
| `JdbcAuditStorage` | JDBC 存储（数据库持久化，对齐 `ydsz_operation_log`） |

### 分表策略

| 类 | 说明 |
|---|---|
| `TableShardingStrategy` | 分表策略接口 |
| `YearlyShardingStrategy` | 按年分表 |
| `MonthlyShardingStrategy` | 按月分表 |
| `DailyShardingStrategy` | 按日分表 |

### 变更差异计算

| 类 | 说明 |
|---|---|
| `DiffCalculator` | 差异计算入口（before / after 对比） |
| `DiffReport` | 差异报告 |
| `FieldDiff` | 字段级差异 |
| `DiffField` | 差异字段注解（标记参与对比的属性） |
| `DiffValueFormatter` | 差异值格式化（支持自定义渲染） |

### 敏感字段脱敏

| 类 | 说明 |
|---|---|
| `SensitiveFieldMask` | 敏感字段脱敏处理器 |
| `MaskField` | 脱敏字段注解 |

### 审计查询与可观测性

| 类 | 说明 |
|---|---|
| `AuditQueryService` / `DefaultAuditQueryService` | 审计日志查询服务 |
| `AuditMetricsBinder` | Micrometer 指标桥接器（审计 QPS / 耗时 / 失败率） |
| `AuditHealthIndicator` | 健康检查（存储连通性 / 队列积压） |

### 领域模型

| 类 | 说明 |
|---|---|
| `AuditLog` | 审计日志实体 |
| `AuditEvent` | 通用审计事件（携带 `AuditLog`） |
| `OperationLogEvent` | 操作日志事件（对齐 `ydsz_operation_log`，含 beforeData / afterData） |
| `DataExportAuditEvent` | 数据导出审计事件（对齐 `ydsz_data_export_audit`） |
| `AuditContext` | 审计上下文（traceId / tenantId / userId） |
| `AuditType` / `AuditAction` / `AuditStatus` | 审计类型 / 操作 / 状态枚举 |
| `AuditException` | 审计模块异常 |

## 使用示例

```java
@Audit(module = "PROJECT", type = AuditType.UPDATE, action = AuditAction.UPDATE,
       content = "'更新项目：' + #project.name")
public Project updateProject(Project project) {
    // 业务逻辑
    return project;
}
```

手动发布操作日志 / 数据导出事件（无需注解）：

```java
applicationEventPublisher.publishEvent(
    OperationLogEvent.builder()
        .module("PROJECT").action("EXPORT").bizType("PROJECT").bizId(projectId)
        .userId(userId).username(username).status("SUCCESS")
        .build()
);
```

## 配置项

```yaml
ydsz:
  audit:
    enabled: true
    mode: disruptor                # default / async / disruptor
    storage: jdbc                  # default / jdbc
    sharding: monthly              # yearly / monthly / daily
    table-prefix: ydsz_operation_log
    buffer-size: 4096              # Disruptor 环形缓冲区大小
    batch-size: 100                # 批量写入大小
    flush-interval: 5s             # 刷新间隔
    mask-sensitive: true           # 敏感字段脱敏
```

## 自动配置

| 配置类 / 监听器 | 激活条件 |
|---|---|
| `AuditAutoConfiguration` | 总是激活 |
| `AuditEventListener` | 总是激活（消费 `OperationLogEvent` / `DataExportAuditEvent`） |
| `AuditProperties` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-audit</artifactId>
</dependency>
```
