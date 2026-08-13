package com.njydsz.common.tenant.lifecycle;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 版租户生命周期管理器。
 *
 * <p>使用 Redis Hash 存储租户状态，多实例共享，适合生产环境。
 *
 * <p><b>自动装配逻辑：</b>当 classpath 中存在 {@link StringRedisTemplate} 时，
 * 此实现以 {@code @Primary} 优先选中；{@link InMemoryTenantLifecycleManager}
 * 以 {@code @ConditionalOnMissingBean} 作为兜底。
 *
 * <h3>Redis Key 设计</h3>
 * <ul>
 *   <li>Hash Key: {@code ydsz:tenant:lifecycle}</li>
 *   <li>Hash Field: {@code {tenantId}}</li>
 *   <li>Hash Value: {@code ACTIVE / SUSPENDED / OFFLINE / DELETED}</li>
 *   <li>TTL: 7 天（通过定时任务心跳续期）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see TenantLifecycleManager
 */
@Component
@Primary
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisTenantLifecycleManager implements TenantLifecycleManager {

    private static final String REDIS_KEY = "ydsz:tenant:lifecycle";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final InMemoryTenantLifecycleManager localCache;

    public RedisTenantLifecycleManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.localCache = new InMemoryTenantLifecycleManager();
    }

    @Override
    public void doActivate(String tenantId) {
        writeStatus(tenantId, TenantStatus.ACTIVE);
        localCache.doActivate(tenantId);
    }

    @Override
    public void doSuspend(String tenantId, String reason) {
        writeStatus(tenantId, TenantStatus.SUSPENDED);
        localCache.doSuspend(tenantId, reason);
    }

    @Override
    public void doOffline(String tenantId) {
        writeStatus(tenantId, TenantStatus.OFFLINE);
        localCache.doOffline(tenantId);
    }

    @Override
    public TenantStatus doGetStatus(String tenantId) {
        // 优先查本地缓存（最终一致性）
        TenantStatus local = localCache.doGetStatus(tenantId);
        if (local != null) return local;
        // 回退到 Redis
        String value = (String) redisTemplate.opsForHash().get(REDIS_KEY, tenantId);
        if (value == null) return null;
        try {
            return TenantStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void doRegister(String tenantId, TenantStatus status) {
        writeStatus(tenantId, status);
        localCache.doRegister(tenantId, status);
    }

    @Override
    public void doRegisterAll(Map<String, TenantStatus> entries) {
        if (entries == null || entries.isEmpty()) return;
        Map<String, String> batch = new HashMap<>(entries.size());
        entries.forEach((id, status) -> batch.put(id, status.name()));
        redisTemplate.opsForHash().putAll(REDIS_KEY, batch);
        redisTemplate.expire(REDIS_KEY, TTL);
        entries.forEach(localCache::doRegister);
    }

    @Override
    public boolean isDistributed() {
        return true;
    }

    private void writeStatus(String tenantId, TenantStatus status) {
        redisTemplate.opsForHash().put(REDIS_KEY, tenantId, status.name());
        redisTemplate.expire(REDIS_KEY, TTL);
    }
}
