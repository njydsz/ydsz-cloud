# ydsz-pmis-common-audit

PMIS 操作审计框架 — @Audit + @OperationLog 声明式审计、AOP 切面、事件驱动异步落库、Disruptor 高性能批写、4 种分表策略、敏感字段脱敏、审计查询服务。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 35 |

## 核心能力

### 声明式审计

| 注解 | 说明 |
|---|---|
| `@Audit` | 操作审计注解（记录方法调用 / 参数 / 返回值 / 异常） |
| `@OperationLog` | 操作日志注解（业务语义审计） |
| `@ApiMetrics` | API 指标注解（调用统计） |
| `@DataExportAudit` | 数据导出审计注解 |
| `@EnableAudit` / `@EnableYdszAudit` | 审计开关注解 |

### AOP 切面

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

### 审计存储

| 类 | 说明 |
|---|---|
| `AuditStorage` | 存储接口 |
| `DefaultAuditStorage` | 默认存储（日志输出） |
| `JdbcAuditStorage` | JDBC 存储（数据库持久化） |

### 分表策略

| 类 | 说明 |
|---|---|
| `TableShardingStrategy` | 分表策略接口 |
| `YearlyShardingStrategy` | 按年分表 |
| `MonthlyShardingStrategy` | 按月分表 |
| `DailyShardingStrategy` | 按日分表 |

### 敏感字段脱敏

| 类 | 说明 |
|---|---|
| `SensitiveFieldMask` | 敏感字段脱敏处理器 |
| `MaskField` | 脱敏字段注解 |

### 审计查询

| 类 | 说明 |
|---|---|
| `AuditQueryService` / `DefaultAuditQueryService` | 审计日志查询服务 |

### 领域模型

| 类 | 说明 |
|---|---|
| `AuditLog` | 审计日志实体 |
| `AuditEvent` / `OperationLogEvent` | 审计事件 |
| `AuditContext` | 审计上下文 |
| `AuditType` / `AuditAction` / `AuditStatus` | 审计类型 / 操作 / 状态枚举 |

## 使用示例

```java
@Audit(type = AuditType.UPDATE, description = "更新项目 '#{#project.name}'")
@OperationLog(businessType = "PROJECT", action = "UPDATE")
public Project updateProject(Project project) {
    // 业务逻辑
    return project;
}
```

## 配置项

```yaml
pmis:
  audit:
    enabled: true
    mode: disruptor                # default / async / disruptor
    storage: jdbc                  # default / jdbc
    sharding: monthly              # yearly / monthly / daily
    table-prefix: t_audit_log
    buffer-size: 4096              # Disruptor 环形缓冲区大小
    batch-size: 100                # 批量写入大小
    flush-interval: 5s             # 刷新间隔
    mask-sensitive: true           # 敏感字段脱敏
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `AuditAutoConfiguration` | 总是激活 |
| `AuditEventListener` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-audit</artifactId>
</dependency>
```
