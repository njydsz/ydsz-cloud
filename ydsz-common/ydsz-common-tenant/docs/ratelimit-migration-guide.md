# 限流统一迁移指南

> **版本**：v1.0  
> **生效日期**：2026-08-15  
> **目标**：统一使用 TenantRateLimiter 作为租户级限流入口

---

## 1. 背景

自 v2.0.0 起，租户级限流统一收口至 `TenantRateLimiter`，
各模块私有限流器逐步标记为 @Deprecated。

---

## 2. 现状

### 2.1 统一的 TenantRateLimiter

```java
@Autowired
private TenantRateLimiter tenantRateLimiter;

// 使用示例
public void doSomething() {
    if (!tenantRateLimiter.tryAcquireTokenBucket("api:invoke", 100, 10)) {
        throw new TenantIsolationException("租户 API 调用配额已用尽");
    }
    // 执行业务逻辑
}
```

### 2.2 已废弃的限流器

| 模块 | 类 | 状态 |
|------|-----|------|
| common-safe | `RedisClusterRateLimiter` | @Deprecated |
| common-safe | `ClusterRateLimiter` | @Deprecated |
| common-queue | `ConsumerRateLimiter` | @Deprecated |
| common-notify | `NotifyRateLimiterManager` | @Deprecated |
| common-socket | `WebSocketRateLimiter` | @Deprecated |
| common-auth | `RateLimiter` | @Deprecated |
| common-util | `RateLimiter` | @Deprecated |
| message-server | `RateLimitServiceImpl` | @Deprecated |
| nextwiki-server | `DownloadRateLimitService` | @Deprecated |
| workflow-server | `FlowUrgeLimiter` | @Deprecated |

---

## 3. 迁移示例

### 3.1 从 ClusterRateLimiter 迁移

```java
// ❌ 旧版：使用 ClusterRateLimiter
@Autowired
private ClusterRateLimiter clusterRateLimiter;

public void oldMethod() {
    boolean allowed = clusterRateLimiter.tryAcquire("api:invoke", 100, 60);
}

// ✅ 新版：使用 TenantRateLimiter
@Autowired
private TenantRateLimiter tenantRateLimiter;

public void newMethod() {
    boolean allowed = tenantRateLimiter.tryAcquireTokenBucket("api:invoke", 100, 10);
}
```

### 3.2 从 RateLimitServiceImpl 迁移

```java
// ❌ 旧版：使用模块自定义限流服务
@Autowired
private RateLimitServiceImpl rateLimitService;

public void oldMethod() {
    boolean allowed = rateLimitService.tryAcquire("key", 100, 60);
}

// ✅ 新版：使用 TenantRateLimiter
@Autowired
private TenantRateLimiter tenantRateLimiter;

public void newMethod() {
    boolean allowed = tenantRateLimiter.tryAcquireSlidingWindow(
            "key", 100, Duration.ofSeconds(60));
}
```

---

## 4. API 对照表

| 旧 API | 新 API（TenantRateLimiter） | 说明 |
|--------|---------------------------|------|
| `tryAcquire(key, limit, seconds)` | `tryAcquireSlidingWindow(key, limit, Duration.ofSeconds(seconds))` | 滑动窗口 |
| `tryAcquireTokenBucket(key, rate, capacity)` | `tryAcquireTokenBucket(key, rate, capacity)` | 令牌桶 |
| `tryAcquireFixedWindow(key, limit, seconds)` | `tryAcquireFixedWindow(key, limit, Duration.ofSeconds(seconds))` | 固定窗口 |
| `tryAcquireConcurrent(key, limit)` | 无直接替代，使用 Redis 原子操作 | 并发数限流 |

---

## 5. 算法选择指南

| 场景 | 推荐算法 | 说明 |
|------|---------|------|
| API 限流 | 令牌桶 | 允许突发流量 |
| 严格频率限制 | 滑动窗口 | 限流平滑，无 2x 突发 |
| 粗粒度限流 | 固定窗口 | 实现简单 |
| 流量整形 | 令牌桶 | 控制速率 |

---

## 6. 迁移检查清单

- [ ] 识别模块中的私有限流器
- [ ] 注入 `TenantRateLimiter` Bean
- [ ] 替换限流调用
- [ ] 移除私有限流器的 @Autowired
- [ ] 运行集成测试验证限流行为
- [ ] 更新单元测试

---

## 7. 注意事项

1. **Key 命名规范**：统一使用 `租户:业务:操作` 格式
2. **配额管理**：建议在配置中心统一管理限流配额
3. **监控告限流**：限流触发时记录日志，便于排查
4. **降级策略**：限流异常时的行为（放行/拒绝/抛异常）

---

## 8. 参考

- [TenantRateLimiter 源码](../src/main/java/com/njydsz/common/tenant/ratelimit/TenantRateLimiter.java)
- [多租户最佳实践](./multi-tenant-best-practices.md)
- [云顶编码规范](../../docs/云顶编码规范.md)
