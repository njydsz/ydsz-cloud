# 多租户模块 Troubleshooting 指南

> **版本**：v1.0  
> **生效日期**：2026-08-15  
> **适用模块**：ydsz-common-tenant

---

## 1. 常见问题

### 1.1 租户上下文为 null

**现象**：`TenantContextHolder.getTenantId()` 返回 null。

**可能原因**：
1. 过滤器未正确解析租户信息
2. 请求头缺少 `X-Tenant-Id`
3. JWT 中不包含 `tenantId` claim

**排查步骤**：

```java
// 1. 检查过滤器是否注册
@Bean
public FilterRegistrationBean<TenantContextWebFilter> tenantFilter() {
    FilterRegistrationBean<TenantContextWebFilter> reg = new FilterRegistrationBean<>();
    reg.setFilter(new TenantContextWebFilter(...));
    reg.addUrlPatterns("/*");
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return reg;
}

// 2. 检查请求头
// 确保客户端发送了 X-Tenant-Id 头
curl -H "X-Tenant-Id: tenant_001" http://localhost:8080/api/xxx

// 3. 检查配置
ydsz:
  tenant:
    enabled: true
    mode: SINGLE
    default-header: X-Tenant-Id
```

### 1.2 异步任务中租户丢失

**现象**：`@Async` 方法或线程池中 `TenantContextHolder.getTenantId()` 返回 null。

**原因**：线程池未注入 TaskDecorator。

**解决方案**：

```java
@Configuration
public class ThreadPoolConfig {

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        // TenantContextTaskDecorator 会自动注入
        // 如果使用 ydsz-common-thread，则自动配置
        return executor;
    }
}
```

**验证方法**：

```java
@Async("taskExecutor")
public void asyncMethod() {
    String tenantId = TenantContextHolder.getTenantId();
    log.info("异步任务中 tenantId={}", tenantId);
    // 不应为 null
}
```

### 1.3 SQL 改写未生效

**现象**：SQL 查询未自动添加租户条件。

**可能原因**：
1. 未启用多租户（`enabled: false`）
2. 表在 `ignore-tables` 列表中
3. 当前用户是超级管理员或跳过了隔离

**排查步骤**：

```yaml
# 1. 确认配置
ydsz:
  tenant:
    enabled: true
    ignore-tables:
      - ydsz_tenant    # 检查表是否在忽略列表中
```

```java
// 2. 检查健康指标
// GET /actuator/health/tenant
// 查看 interceptPassCount、interceptSkippedCount
```

### 1.4 多字段租户 SQL 异常

**现象**：启用 MULTI 模式后，SQL 报 "column not found" 错误。

**原因**：表缺少多字段租户所需的列。

**解决方案**：

```sql
-- 添加缺失的列
ALTER TABLE ydsz_flow_definition ADD COLUMN company_id VARCHAR(64);
ALTER TABLE ydsz_flow_definition ADD COLUMN dept_id VARCHAR(64);

-- 更新已有数据
UPDATE ydsz_flow_definition SET company_id = 'default' WHERE company_id IS NULL;

-- 创建复合索引
CREATE INDEX idx_flow_def_tenant ON ydsz_flow_definition (tenant_id, company_id, dept_id);
```

### 1.5 Feign 调用丢失租户

**现象**：跨服务调用时，下游服务获取不到租户上下文。

**原因**：Feign 拦截器未配置。

**解决方案**：

```java
// 确认 TenantAutoConfiguration 自动注入了 TenantContextFeignInterceptor
// 检查依赖中是否包含 ydsz-common-tenant
```

**验证方法**：

```java
// 在下游服务的 Controller 中检查
@GetMapping("/api/test")
public String test() {
    return TenantContextHolder.getTenantId();
    // 不应为 null
}
```

---

## 2. 监控指标

### 2.1 Prometheus 指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `tenant.sql.intercept.total` | Counter | SQL 拦截次数（tag: result=pass/blocked/skipped） |
| `tenant.failclosed.total` | Counter | fail-closed 拒绝次数 |
| `tenant.context.skip.total` | Counter | 跳过隔离次数 |
| `tenant.superadmin.total` | Counter | 超级管理员绕过次数 |
| `tenant.datasource.switch.total` | Counter | 数据源切换次数 |
| `tenant.active` | Gauge | 当前活跃租户上下文数 |

### 2.2 健康检查

```bash
# 查看租户健康状态
curl http://localhost:8080/actuator/health/tenant
```

**正常响应示例**：

```json
{
  "status": "UP",
  "details": {
    "enabled": true,
    "mode": "SINGLE",
    "tenantColumn": "tenant_id",
    "superTenantId": "0",
    "systemTenantId": "0",
    "interceptPassCount": 1000,
    "interceptBlockedCount": 0,
    "interceptSkippedCount": 50,
    "failClosedCount": 0,
    "activeContexts": 10
  }
}
```

---

## 3. 日志排查

### 3.1 开启 DEBUG 日志

```yaml
logging:
  level:
    com.njydsz.common.tenant: DEBUG
```

### 3.2 关键日志

| 日志内容 | 级别 | 说明 |
|---------|------|------|
| `[TenantContextWebFilter] 解析租户上下文` | DEBUG | 请求进入时解析租户 |
| `[TenantIsolationInterceptor] SQL 改写` | DEBUG | SQL 改写详情 |
| `[TenantContextHolder] 设置租户上下文` | DEBUG | 上下文设置 |
| `[TenantContextHolder] 清除租户上下文` | DEBUG | 上下文清除 |

---

## 4. 调试工具

### 4.1 租户上下文调试端点

```java
@RestController
@RequestMapping("/debug/tenant")
public class TenantDebugController {

    @GetMapping("/context")
    public Map<String, Object> getContext() {
        Map<String, Object> result = new HashMap<>();
        TenantContext context = TenantContextHolder.get();
        if (context != null) {
            result.put("tenantId", context.getTenantId());
            result.put("isSuperAdmin", context.isSuperAdmin());
            result.put("isSkipIsolation", context.isSkipIsolation());
            result.put("isSystemTenant", context.isSystemTenant());
            result.put("fields", context.getFields());
        } else {
            result.put("status", "no context");
        }
        return result;
    }
}
```

### 4.2 MDC 日志追踪

确保 `TenantMdcFilter` 已注册，日志中会包含 `[tenantId=xxx]` 标记：

```xml
<!-- logback-spring.xml -->
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{tenantId}] %-5level %logger{36} - %msg%n</pattern>
```

---

## 5. 配置检查清单

部署前检查：

- [ ] `ydsz.tenant.enabled=true`
- [ ] `mode` 配置正确（SINGLE/MULTI/ISOLATE_DB/SCHEMA）
- [ ] `tenant-fields` 与数据库列名匹配
- [ ] `ignore-tables` 配置完整
- [ ] `anon-urls` 包含登录/注册等公开接口
- [ ] 数据库表已添加租户字段
- [ ] 已有数据已补充租户字段值
- [ ] Feign 拦截器已配置
- [ ] 线程池 TaskDecorator 已注入
- [ ] 监控指标已接入 Prometheus/Grafana

---

## 6. 参考

- [TenantContextHolder API](../src/main/java/com/njydsz/common/tenant/TenantContextHolder.java)
- [TenantProperties 配置](../src/main/java/com/njydsz/common/tenant/config/TenantProperties.java)
- [多字段租户最佳实践](./multi-tenant-best-practices.md)
- [配置迁移指南](./config-migration-guide.md)
