# ydsz-common-audit

> YDSZ 操作审计框架（L5 业务服务层）

提供 `@Audit` 声明式 AOP 审计、事件驱动异步落库、LinkedBlockingQueue 批量写入、3 种分表策略、敏感字段脱敏、审计查询服务、Micrometer 指标桥接、健康检查等能力，是 YDSZ 项目合规审计与操作轨迹记录的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 为所有业务服务提供操作审计、合规追踪、审计轨迹记录能力（AOP + 异步批写 + 分表 + 差异计算） |
| **依赖** | common-core、common-util、common-exception、common-json；可选依赖 spring-jdbc、micrometer-core、spring-boot-actuator、spring-boot-health、spring-webmvc |
| **版本** | 1.0.0 |

## 核心能力

### 1. 声明式审计注解

| 注解 | 说明 |
|---|---|
| `@Audit` | 操作审计注解（记录方法调用 / 参数 / 返回值 / 异常，支持 SpEL 描述、参数排除、同步异步切换） |
| `@EnableYdszAudit` | 审计开关 + 自动装配入口（`@Import(AuditAutoConfiguration.class)`） |

### 2. AOP 切面与模板

| 类 | 说明 |
|---|---|
| `AuditAspect` | 审计切面（方法拦截 → 事件构建 → 异步发布） |
| `AuditTemplateProcessor` | SpEL 模板解析（审计描述动态渲染，支持 `#param` / `#result`） |

### 3. 审计记录器（策略模式）

| 类 | 说明 |
|---|---|
| `AuditRecorder` | 审计记录器接口（record / recordAsync / recordBatch） |
| `DefaultAuditRecorder` | 默认同步记录器（阻塞业务主链路，仅用于低 QPS 场景） |
| `AsyncAuditRecorder` | 异步记录器（LinkedBlockingQueue + 线程池 + 批量刷盘 + 兜底降级） |
| `AuditFallbackWriter` | 降级写入器（主存储失败时落到本地文件 / 日志） |

### 4. 审计存储

| 类 | 说明 |
|---|---|
| `AuditWriter` | 存储抽象接口（save / saveBatch / getType / isAvailable） |
| `DefaultAuditStorage` | 默认存储（控制台日志输出，无 DataSource 时降级使用），实现 `AuditWriter` |
| `JdbcAuditStorage` | JDBC 存储（数据库持久化，对齐 `sys_audit_log` 表结构，支持分表），实现 `AuditWriter` |

### 5. 分表解析

| 类 | 说明 |
|---|---|
| `TableNameResolver` | 分表解析器（替代原 `TableShardingStrategy` 族，按日/月/年策略解析目标表名） |

### 6. 变更差异计算

> **说明**：差异计算能力由 `ydsz-common-util` 提供（`com.njydsz.common.util.diff` 包），非本模块类。

| 类 / 注解 | 说明 |
|---|---|
| `DiffCalculator` | 差异计算入口（before / after 对比，自动识别 `@DiffField` 注解） |
| `DiffReport` | 差异报告（字段级变更集合） |
| `FieldDiff` | 字段级差异模型（字段名 / 旧值 / 新值 / 变更类型） |
| `@DiffField` | 差异字段注解（标记参与对比的属性） |
| `DiffValueFormatter` | 差异值格式化（支持自定义渲染） |

### 7. 敏感字段脱敏

| 类 | 说明 |
|---|---|
| `SensitiveFieldMask` | 敏感字段脱敏处理器（参数名 + 字段名双维度，`AuditProperties.sensitiveParams` 配置） |

> 说明：`@MaskField` 注解未提供，脱敏按参数名/字段名规则处理。

### 8. 审计查询与可观测性

| 类 | 说明 |
|---|---|
| `AuditQueryService` | 审计日志查询服务接口 |
| `DefaultAuditQueryService` | 默认 JDBC 查询实现（支持跨分表 UNION ALL） |
| `AuditMetricsBinder` | Micrometer 指标桥接器（队列大小 / 使用率 / 成功失败计数 / 写入延迟） |
| `AuditHealthIndicator` | 健康检查指示器（暴露 `/actuator/health/audit`） |

### 9. 领域模型与事件

| 类 | 说明 |
|---|---|
| `AuditLog` | 审计日志实体（对齐 `sys_audit_log` 表） |
| `OperationLogEvent` | 操作日志事件（含 beforeData / afterData 变更差异） |
| `DataExportAuditEvent` | 数据导出审计事件 |
| `GatewayAuditEventBridge` | 网关审计事件桥接器（接收网关侧审计事件，转换为本地审计事件） |
| `AuditContext` | 审计上下文（traceId / tenantId / userId） |
| `AuditType` | 审计类型枚举（OPERATION / LOGIN / DATA / PERMISSION / CONFIG / FILE / API / SYSTEM / CUSTOM） |
| `AuditAction` | 审计操作枚举（CREATE / UPDATE / DELETE / QUERY / IMPORT / EXPORT / LOGIN / GRANT 等 24 种） |
| `AuditStatus` | 审计状态枚举 |
| `AuditException` | 审计模块异常 |
| `AuditWriteException` | 审计写入异常 |

### 10. 事件监听与异步执行

| 类 | 说明 |
|---|---|
| `AuditEventListener` | 审计事件监听器（同步监听 `OperationLogEvent` / `DataExportAuditEvent`，委托 `AuditRecorder` 异步批量写入） |
| `auditAsyncExecutor` | 审计专用异步线程池（与主业务线程池隔离，避免审计 IO 影响核心链路） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-audit</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  audit:
    enabled: true
    storage-type: LOCAL                  # DEFAULT / LOCAL / REMOTE / MQ
    record-request: true
    record-response: false
    record-async: true                   # 异步记录，避免阻塞业务主链路
    mask-enabled: true                   # 敏感字段脱敏
    sharding:
      enabled: true
      type: monthly                      # yearly / monthly / daily
    async:
      thread-core-size: 2
      queue-capacity: 10000
      batch-interval-millis: 3000
```

### 3. 代码启用

在 Spring Boot 主类上添加 `@EnableYdszAudit` 注解即可启用审计模块自动装配：

```java
import com.njydsz.common.audit.annotation.EnableYdszAudit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableYdszAudit
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 配置项

### 总开关与基础（`ydsz.audit`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | true | 是否启用审计模块（关闭后切面直接放行） |
| `app-key` | 空 | 应用标识（应用 ID/编码/名称已合并为单一 app-key，区分审计数据归属） |
| `storage-type` | LOCAL | 存储策略类型：`DEFAULT` / `LOCAL` / `REMOTE` / `MQ` |
| `record-request` | true | 是否记录请求参数 |
| `record-response` | false | 是否记录响应结果（默认关闭，响应体可能含敏感数据） |
| `record-async` | true | 是否异步记录 |
| `sensitive-params` | password,token,secret,apiKey 等 11 个 | 敏感参数名称列表（命中名称的参数不序列化） |
| `ip-location-enabled` | false | 是否启用 IP 归属地解析 |
| `user-agent-enabled` | false | 是否启用 User-Agent 解析 |
| `async.batch-interval-millis` | 3000 | 批量刷新间隔（毫秒） |
| `async.reject-policy` | DISCARD_OLDEST | 队列满拒绝策略：`DISCARD_OLDEST` / `DISCARD_NEWEST` / `CALLER_RUNS` |
| `async.shutdown-timeout` | 30 | 优雅停机超时（秒） |
| `mask-enabled` | true | 是否启用敏感字段脱敏 |
| `retention-days` | 90 | 审计日志保留天数 |

### 异步线程池（`ydsz.audit.async`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `async.thread-core-size` | 2 | 异步记录线程池核心线程数 |
| `async.thread-max-size` | 4 | 异步记录线程池最大线程数 |
| `async.queue-capacity` | 200 | 异步记录线程池等待队列容量 |

### 分表（`ydsz.audit.sharding`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `enabled` | false | 是否启用分表 |
| `type` | monthly | 分表类型：`yearly` / `monthly` / `daily` |
| `base-table-name` | sys_audit_log | 基础表名 |

### 异步批量写入（`ydsz.audit.async`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `batch-size` | 100 | 批量写入阈值（条数，达到后立即触发刷盘） |
| `batch-interval-millis` | 5000 | 批量写入间隔（毫秒，超过此间隔即使未满也会写入） |
| `queue-capacity` | 10000 | 异步队列最大容量（满后按 `async-reject-policy` 处理） |

## 使用示例

### 1. 声明式审计（`@Audit` 注解）

```java
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController {

    @PostMapping("/projects")
    @Audit(module = "PROJECT",
           type = AuditType.OPERATION,
           action = AuditAction.CREATE,
           content = "'创建项目：' + #project.name",
           recordRequest = true,
           recordResponse = false,
           excludeParams = {"password", "secretKey"})
    public Result<Project> createProject(@RequestBody ProjectDTO project) {
        return Result.success(projectService.create(project));
    }
}
```

### 2. 手动发布操作日志事件（无需注解）

```java
import com.njydsz.common.audit.event.OperationLogEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectExportService {
    private final ApplicationEventPublisher eventPublisher;

    public void exportProject(String projectId, String userId, String username) {
        eventPublisher.publishEvent(
            OperationLogEvent.builder()
                .module("PROJECT").action("EXPORT")
                .bizType("PROJECT").bizId(projectId)
                .userId(userId).username(username)
                .status("SUCCESS")
                .build()
        );
    }
}
```

### 3. 变更差异计算（before / after）

```java
import com.njydsz.common.util.diff.DiffCalculator;
import com.njydsz.common.util.diff.DiffReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectUpdateService {
    private final DiffCalculator diffCalculator;

    public DiffReport compare(Project before, Project after) {
        // 自动识别 @DiffField 注解字段，生成字段级差异报告
        return diffCalculator.calculate(before, after);
    }
}
```

### 4. 数据导出审计事件

```java
import com.njydsz.common.audit.event.DataExportAuditEvent;
import org.springframework.context.ApplicationEventPublisher;

eventPublisher.publishEvent(
    DataExportAuditEvent.builder()
        .module("PROJECT")
        .exportType("EXCEL")
        .totalRecords(10000L)
        .userId(userId)
        .build()
);
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `AuditRecorder` | 审计记录器抽象（同步 / 异步 / 批量） | `DefaultAuditRecorder`（同步）、`AsyncAuditRecorder`（LinkedBlockingQueue） |
| `AuditWriter` | 审计存储抽象 | `DefaultAuditStorage`（控制台输出）、`JdbcAuditStorage`（JDBC 持久化） |
| `TableNameResolver` | 分表解析（日/月/年） | 内置按策略解析 |
| `AuditQueryService` | 审计日志查询服务抽象 | `DefaultAuditQueryService`（JDBC + 跨分表 UNION ALL） |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/audit` | 审计模块健康检查：记录器类型、存储策略、队列积压量、队列使用率、累计丢弃次数（队列使用率 > 80% 标记 DOWN） | `ydsz.audit.enabled=true`（默认 true）+ classpath 存在 `HealthIndicator` + 存在 `AuditRecorder` Bean |

健康检查返回示例：

```json
{
  "status": "UP",
  "details": {
    "module": "audit",
    "recorder": "AsyncAuditRecorder",
    "storageType": "LOCAL",
    "queueSize": 12,
    "queueUsageRatio": "0.1%",
    "droppedCount": 0
  }
}
```

> 当队列使用率超过 80% 时状态为 `DOWN`，明细 `error` 字段提示"队列使用率超过80%，审计日志可能被丢弃"；有丢弃但水位正常时为 `UP` + `warning`。

## 注意事项

1. **存储降级**：classpath 中无 `DataSource` 时，`AuditWriter` 自动降级为 `DefaultAuditStorage`（控制台输出），生产环境务必配置 JDBC 存储。
4. **分表键固定为时间戳**：分表策略根据操作时间计算目标表名，跨分表查询走 `getTableNamesInRange` 枚举后 UNION ALL 合并。
5. **敏感参数双维度脱敏**：`AuditProperties.sensitiveParams`（参数名维度）+ `SensitiveFieldMask`（字段名维度）共同生效，默认覆盖 password / token / secret / apiKey / privateKey 等 11 个常见敏感词。
6. **优雅停机**：`AuditAutoConfiguration` 通过 `@PreDestroy` 调用 `AsyncAuditRecorder.shutdown()`，确保队列中剩余日志全部写入；超时由 `async-shutdown-timeout` 控制。
7. **队列满拒绝策略**：默认 `DISCARD_OLDEST`（丢弃最旧日志），可配置为 `DISCARD_NEWEST` 或 `CALLER_RUNS`（调用者阻塞等待）。健康检查会累计 `droppedCount`。
8. **审计专用线程池隔离**：`auditAsyncExecutor` 与主业务线程池隔离，避免审计 IO 阻塞核心链路；线程名前缀 `audit-async-`。
9. **事件驱动解耦**：`AuditEventListener` 同步监听 `OperationLogEvent` / `DataExportAuditEvent`，委托 `AuditRecorder` 异步批量写入。业务方也可手动 `publishEvent(OperationLogEvent)` / `publishEvent(DataExportAuditEvent)` 触发审计，无需 `@Audit` 注解。
10. **可选依赖降级**：`spring-jdbc` / `micrometer-core` / `spring-boot-actuator` / `spring-boot-health` 均为 optional，未引入时对应能力自动降级或不可用。

## 变更记录

- **v1.0.1**（2026-08-17）：补全 `GatewayAuditEventBridge`（网关审计事件桥接器）、`AuditWriteException` 文档；澄清差异计算类属于 `ydsz-common-util` 包
- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节，覆盖 10 项核心能力、3 个配置分组、4 个 SPI 接口、1 个 HealthIndicator。
