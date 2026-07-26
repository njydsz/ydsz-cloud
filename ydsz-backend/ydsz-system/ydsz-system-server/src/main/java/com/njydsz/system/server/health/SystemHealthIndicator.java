package com.njydsz.system.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Component;

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
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.system", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SystemHealthIndicator implements HealthIndicator {

    private final RedisService redisService;
    private final ConfigMapper configMapper;
    private final DictItemMapper dictItemMapper;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // 检查 Redis 连通性（使用 execute 确保连接释放）
        try {
            String ping = redisService.execute(conn -> conn.ping(), true);
            details.put("redis", "UP - " + ping);
        } catch (Exception e) {
            details.put("redis", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // 检查配置表可达性（轻量探针，仅查 1 条记录，不走 COUNT）
        try {
            configMapper.selectByConfigKey("__health_probe__");
            details.put("config", "UP - table reachable");
        } catch (Exception e) {
            details.put("config", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // 检查字典表可达性（轻量探针）
        try {
            dictItemMapper.selectByTypeAndCode("__health_probe__", "__health_probe__");
            details.put("dict", "UP - table reachable");
        } catch (Exception e) {
            details.put("dict", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        return Health.up().withDetails(details).build();
    }
}
