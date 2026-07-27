# ydsz-common-tenant 后续优化完善建议

> **基准对标**: Salesforce 多租户引擎 / 阿里云 SaaS 引擎 / AWS SaaS Factory / SAP MT
> **调研日期**: 2026-07-27

---

## 差距分析矩阵

| 能力维度 | Salesforce | 阿里云 SaaS | AWS SaaS | 当前 PMIS | 差距等级 |
|---|---|---|---|---|---|
| 租户生命周期管理 | 完整 CRUD + 状态机 | 完整 + 配额 | 简化 | 无 | P0 |
| 租户上下文安全校验 | JWT 签名验证 | JWT + 网关清洗 | 网关清洗 | 仅 AuthInfoUtils | P0 |
| WebFilter 执行顺序 | @Order 显式 | @Order + FilterRegistrationBean | @Order | 无 @Order | P0 |
| MDC 日志可观测性 | traceId+tenantId | traceId+tenantId | traceId+tenantId | 无 MDC | P1 |
| 健康检查 + 指标 | 完整 | 完整 | 完整 | 无 HealthIndicator | P1 |
| Feign 下游恢复 | 双向 | 双向 | 双向 | 仅出站，入站缺 | P1 |
| 数据源路由缓存 | 缓存 + 预热 | 缓存 + 预热 | 缓存 | 硬编码拼接 | P1 |
| 代码规范 (FQN) | - | - | - | 2 处 FQN 违规 | P2 |
| 租户配额/限流 | 内置 | 内置 | API Gateway | 无 | P2 |
| 租户级配置隔离 | 内置 | 内置 | 内置 | 无 | P2 |
| 审计日志 | 内置 | 内置 | CloudTrail | 无 | P3 |

---

## P0 级修复（安全 / 编译）— 4 项

### P0-1: WebFilter 缺少 @Order 导致执行顺序不确定

**问题**: `TenantContextWebFilter` 未标注 `@Order`，Spring Boot 自动注册 Filter 时执行顺序不确定，可能在认证 Filter 之前执行，导致 `AuthInfoUtils.getTenantId()` 返回 null。

**修复**: 在 `TenantAutoConfiguration` 中使用 `FilterRegistrationBean` 包装，显式指定 order。

### P0-2: WebFilter 缺少 MDC 日志上下文注入

**问题**: 所有大厂方案都在 Filter 中注入 `MDC.put("tenantId", ...)`，确保日志/链路追踪/告警都携带租户维度。当前 WebFilter 未做 MDC 注入，日志无法区分租户。

### P0-3: Feign 下游恢复缺失

**问题**: `TenantContextFeignInterceptor` 只做出站 header 注入，但 `TenantContextWebFilter` 的入站逻辑仅从 `AuthInfoUtils.getTenantId()`（JWT）取值。当 Feign 调用经过网关时 JWT 可能不被透传，应增加从 `X-Tenant-Id` header 恢复上下文的降级路径。

### P0-4: TenantIsolationInterceptor 2 处 FQN 违规

**问题**: 第 325、327 行使用了行内全限定类名 `com.njydsz.common.tenant.TenantDimension.GROUP` / `COMPANY`，违反项目禁止 FQN 规范 [[memory:17838234369756323145]]。

---

## P1 级增强（可观测性 / 健壮性）— 5 项

### P1-1: 缺少 TenantHealthIndicator

**问题**: 所有公共模块都有 HealthIndicator，但 common-tenant 没有。大厂方案都要求多租户模块暴露健康状态（当前活跃租户数、拦截器状态、数据源路由状态）。

### P1-2: 缺少 TenantMetrics 指标

**问题**: 大厂方案都按租户维度上报 Micrometer 指标（SQL 拦截次数、fail-closed 次数、跳过次数、数据源切换次数）。当前模块无任何指标。

### P1-3: ISOLATE_DB 数据源路由硬编码拼接

**问题**: `TenantDataSourceRouter` 第 60 行 `datasourceKey = "tenant_" + tenantId` 是硬编码拼接，实际应查询 `ydsz_tenant` 表的 `datasource_key` 字段。大厂方案使用缓存 + 预热机制。

### P1-4: TenantProperties 缺少 @Validated 校验

**问题**: 配置类缺少 JSR-303 校验注解，不符合项目规范（其他 Properties 如 `FlowProperties`、`CronjobProperties` 都有 `@Validated + @Min/@Max`）。

### P1-5: TenantContextTaskDecorator 未自动注册到线程池

**问题**: `TenantContextTaskDecorator` 作为 Bean 注册了，但没有自动装配到现有的 `ThreadPoolTaskExecutor` 上。大厂方案通过 `BeanPostProcessor` 自动注入 TaskDecorator 到所有线程池。

---

## P2 级增强（安全加固 / 运营能力）— 4 项

### P2-1: 租户上下文 Header 安全清洗

**问题**: 大厂方案在网关层清洗外部请求的 `X-Tenant-Id` header（防止伪造）。当前 WebFilter 从 header 恢复上下文时缺少安全校验，应只信任网关写入的 header。

### P2-2: TenantAwareRedisKey 未接入 Redis 操作层

**问题**: `TenantAwareRedisKey.resolve()` 已实现，但没有实际接入 RedisTemplate 的 Key 序列化器或 RedisConfig，需要业务代码手动调用。大厂方案通过 RedisSerializer 层面自动注入。

### P2-3: 租户配额/限流

**问题**: 大厂方案支持 per-tenant 限流（API 调用次数、存储配额、并发数）。当前模块无此能力，可对接 `common-lock` 的 `RedisRateLimiter` 实现 per-tenant 限流。

### P2-4: 租户级配置隔离

**问题**: 大厂方案支持 per-tenant 配置覆盖（不同租户可以有不同的 feature flag / 参数配置）。当前 `TenantProperties` 是全局配置，无法按租户差异化。

---

## P3 级增强（长期演进）— 3 项

### P3-1: 租户审计日志

**问题**: 大厂方案记录租户操作审计（哪个租户、谁、何时、做了什么）。可对接 `common-audit` 模块的 `@Audit` 注解。

### P3-2: 租户优雅上下线

**问题**: 大厂方案支持租户暂停/恢复/下线，暂停后拒绝该租户所有请求。需要与 `ydsz_tenant` 表状态字段联动。

### P3-3: 租户级缓存隔离策略可选

**问题**: 当前 Redis Key 隔离是 `{tenantId}:` 前缀方案，大厂还支持 Redis DB 切换（SELECT 0-N）和独立 Redis 实例方案，可扩展为可配置策略。

---

## 优先级排序与工时

| 优先级 | 项 | 工时 |
|---|---|---|
| P0-1 | WebFilter @Order + FilterRegistrationBean | 0.5d |
| P0-2 | MDC 日志上下文注入 | 0.5d |
| P0-3 | Feign 下游恢复降级 | 0.5d |
| P0-4 | FQN 违规修复 | 0.1d |
| P1-1 | TenantHealthIndicator | 0.5d |
| P1-2 | TenantMetrics | 0.5d |
| P1-3 | ISOLATE_DB 数据源路由缓存 | 1d |
| P1-4 | TenantProperties @Validated | 0.3d |
| P1-5 | TaskDecorator 自动注册 | 0.5d |
| P2-1 | Header 安全清洗 | 0.5d |
| P2-2 | RedisKey 自动接入 | 1d |
| P2-3 | 租户配额/限流 | 1d |
| P2-4 | 租户级配置隔离 | 1d |
| P3-1 | 租户审计日志 | 0.5d |
| P3-2 | 租户上下线 | 1d |
| P3-3 | 缓存隔离策略 | 0.5d |
| **总计** | | **9.3d** |
