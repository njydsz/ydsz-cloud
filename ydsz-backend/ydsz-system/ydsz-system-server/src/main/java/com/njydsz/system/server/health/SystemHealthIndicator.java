package com.njydsz.system.server.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Component;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.common.redis.service.RedisService;
import com.njydsz.system.infra.mapper.ConfigMapper;
import com.njydsz.system.infra.mapper.DictItemMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统模块健康检查。
 *
 * <p>使用轻量级探针检查 Redis 连通性和数据库表可达性，避免全表 COUNT 扫描。
 *
 * <p>检查项：
 * <ul>
 *   <li>Redis — PING 命令</li>
 *   <li>配置表 — SELECT 1 LIMIT 1（轻量探针，不走 COUNT）</li>
 *   <li>字典表 — SELECT 1 LIMIT 1（轻量探针，不走 COUNT）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.system", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SystemHealthIndicator extends AbstractModuleHealthIndicator {

    private final RedisService redisService;
    private final ConfigMapper configMapper;
    private final DictItemMapper dictItemMapper;

    @Override
    protected void doHealthCheck(org.springframework.boot.health.contributor.Health.Builder builder) {
        checkRedis(builder, () -> redisService.getRedisTemplate().execute((RedisCallback<String>) conn -> conn.ping()));
        checkTableProbe(builder, "config", () -> configMapper.selectByConfigKey("__health_probe__"));
        checkTableProbe(builder, "dict", () -> dictItemMapper.selectByTypeAndCode("__health_probe__", "__health_probe__"));
    }
}
