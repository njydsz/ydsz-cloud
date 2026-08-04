# YDSZ 缓存最佳实践（P1-8）

> 目标：统一缓存使用规范，杜绝缓存穿透/击穿/雪崩三大经典问题。
> 相关模块：`ydsz-common-cache`（本地缓存）+ `ydsz-common-redis`（分布式缓存）

## 一、防护能力速查

| 问题 | 防护手段 | API | 状态 |
|------|---------|-----|------|
| **穿透**（查不存在的数据） | 空值占位（短 TTL 随机） | `CacheProtectionGuard` / `getWithProtection` | ✅ 内置 |
| **击穿**（热点 key 过期瞬间并发） | 同 key 单飞加载（信号 Future） | `getWithProtection` | ✅ 内置 |
| **雪崩**（大量 key 同时过期） | 过期时间随机抖动 | `getWithProtection(minMs, maxMs)` | ✅ 内置 |
| **冷启动穿透** | 启动预热 | `CacheWarmer` | ✅ 内置 |

## 二、正确使用姿势

### 2.1 热点缓存一律用 `getWithProtection`

```java
// ❌ 错误：手动 getIfPresent + put（存在击穿/穿透风险）
UserInfo user = userCache.getIfPresent(userId);
if (user == null) {
    user = userService.load(userId);   // 并发下 N 个线程同时加载
    userCache.put(userId, user);
}

// ✅ 正确：单飞加载 + 空值占位 + 随机 TTL
UserInfo user = userCache.getWithProtection(
    userId,
    id -> userService.load(id),   // 同一 key 并发仅执行一次
    30_000,                       // 空值占位最小 30s
    60_000                        // 最大 60s（随机抖动防雪崩）
);
```

### 2.2 启动预热用 `CacheWarmer`

```java
@Bean
public CacheWarmer cacheWarmer(SystemDictService dictService, DictCache dictCache) {
    CacheWarmer warmer = new CacheWarmer();
    // 预热字典缓存（启动后 5 分钟刷新的热点数据）
    warmer.registerWarmTask("dictCache", dictCache,
        dictService.listActiveDictCodes(), dictService::getByCode);
    return warmer;
}
```

### 2.3 缓存 TTL 建议

| 数据类型 | TTL | 说明 |
|---------|-----|------|
| 字典/配置（低频变更） | 10-30 分钟 | 变更时主动 invalidate |
| 用户信息（中频变更） | 5 分钟 | 与 JWT 缓存窗口一致 |
| 项目详情（高频读取） | 5-10 分钟 | 变更事件驱动失效 |
| 权限/角色（低频变更） | 10 分钟 | 变更时 Redis Pub/Sub 广播 |
| 验证码/临时态 | 2-5 分钟 | 短 TTL + 一次性消费 |

## 三、多级缓存（本地 + Redis）注意事项

1. **一致性**：本地缓存变更必须广播失效（Redis Pub/Sub），参考网关 `CachedJwtValidator`
2. **容量**：本地缓存设置 `maximumSize` 上限，防止内存膨胀
3. **指标**：开启 `recordStats()`，监控 `getHitRate`，低于 80% 需排查
4. **降级**：Redis 不可用时本地缓存继续服务（fail-open），参考网关限流兜底

## 四、Redis 使用规范

| 场景 | 推荐命令 | 原因 |
|------|---------|------|
| 计数 | `INCR` + 过期时间 | 原子自增 |
| 限流 | Lua 脚本 | 原子性 |
| 布隆过滤器 | Redisson `RBloomFilter` | 省内存 |
| 批量查询 | `MGET` / Pipeline | 减少 RTT |
| **禁止** | `KEYS *` | 阻塞 Redis，用 `SCAN` |
| **禁止** | 大 Value（>10KB） | 改为拆分或压缩 |
