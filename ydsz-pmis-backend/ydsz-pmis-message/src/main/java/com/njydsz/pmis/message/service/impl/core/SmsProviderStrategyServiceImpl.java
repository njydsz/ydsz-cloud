package com.njydsz.pmis.message.service.impl.core;

import com.njydsz.pmis.message.channel.sms.SmsProvider;
import com.njydsz.pmis.message.service.core.SmsProviderStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多短信服务商策略服务实现。
 *
 * <p>P2-15: 支持四种选择策略，默认轮询。
 *
 * <p>成本统计：使用 Redis 记录各 provider 的日发送量和失败量，
 * key={@code pmis:sms:stats:{provider}:{yyyyMMdd}}，value=INCR 计数。
 *
 * <p>轮询使用 AtomicInteger 游标，权重使用配置文件中的权重比例，
 * 成本优先按 provider 成本排序，可用性优先跳过连续失败超过阈值的 provider。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsProviderStrategyServiceImpl implements SmsProviderStrategyService {

    /** Redis 模板（服务商日发送量 / 失败量统计） */
    private final StringRedisTemplate redisTemplate;

    /** 策略类型 */
    @Value("${pmis.message.sms.strategy:ROUND_ROBIN}")
    private String strategyStr;

    /** 权重配置（provider:weight,provider:weight） */
    @Value("${pmis.message.sms.weights:aliyun:5,tencent:3}")
    private String weightsConfig;

    /** 轮询游标 */
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    /** 本地失败计数（用于可用性优先） */
    private final Map<String, AtomicInteger> failureCount = new ConcurrentHashMap<>();

    /** 连续失败阈值 */
    private static final int FAILURE_THRESHOLD = 5;

    @Override
    public SmsProvider selectProvider(List<SmsProvider> availableProviders) {
        if (availableProviders == null || availableProviders.isEmpty()) {
            throw new IllegalStateException("无可用 SMS provider");
        }
        if (availableProviders.size() == 1) {
            return availableProviders.get(0);
        }
        Strategy strategy;
        try {
            strategy = Strategy.valueOf(strategyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            strategy = Strategy.ROUND_ROBIN;
        }
        return switch (strategy) {
            case ROUND_ROBIN -> selectRoundRobin(availableProviders);
            case WEIGHTED -> selectWeighted(availableProviders);
            case COST_FIRST -> selectCostFirst(availableProviders);
            case AVAILABILITY_FIRST -> selectAvailabilityFirst(availableProviders);
        };
    }

    @Override
    public void recordSend(String providerType, boolean success) {
        try {
            String daySuffix = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            String totalKey = "pmis:sms:stats:" + providerType + ":" + daySuffix + ":total";
            redisTemplate.opsForValue().increment(totalKey);
            if (!success) {
                String failKey = "pmis:sms:stats:" + providerType + ":" + daySuffix + ":failed";
                redisTemplate.opsForValue().increment(failKey);
                failureCount.computeIfAbsent(providerType, k -> new AtomicInteger(0)).incrementAndGet();
            } else {
                failureCount.computeIfAbsent(providerType, k -> new AtomicInteger(0)).set(0);
            }
        } catch (Exception e) {
            log.debug("[SmsStrategy] 统计记录失败(忽略): {}", e.getMessage());
        }
    }

    @Override
    public Map<String, long[]> getProviderStats() {
        Map<String, long[]> stats = new java.util.HashMap<>();
        try {
            String daySuffix = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            for (String provider : new String[]{"aliyun", "tencent", "mock"}) {
                String totalKey = "pmis:sms:stats:" + provider + ":" + daySuffix + ":total";
                String failKey = "pmis:sms:stats:" + provider + ":" + daySuffix + ":failed";
                String totalStr = redisTemplate.opsForValue().get(totalKey);
                String failStr = redisTemplate.opsForValue().get(failKey);
                long total = totalStr != null ? Long.parseLong(totalStr) : 0;
                long failed = failStr != null ? Long.parseLong(failStr) : 0;
                stats.put(provider, new long[]{total, total - failed, failed});
            }
        } catch (Exception e) {
            log.warn("[SmsStrategy] 统计查询失败: {}", e.getMessage());
        }
        return stats;
    }

    // ==================== 策略实现 ====================

    /**
     * 轮询选择。
     */
    private SmsProvider selectRoundRobin(List<SmsProvider> providers) {
        int idx = Math.abs(roundRobinIndex.getAndIncrement()) % providers.size();
        return providers.get(idx);
    }

    /**
     * 权重选择。
     */
    private SmsProvider selectWeighted(List<SmsProvider> providers) {
        Map<String, Integer> weights = parseWeights();
        int totalWeight = providers.stream()
                .mapToInt(p -> weights.getOrDefault(p.providerType(), 1))
                .sum();
        int random = java.util.concurrent.ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (SmsProvider p : providers) {
            cumulative += weights.getOrDefault(p.providerType(), 1);
            if (random < cumulative) {
                return p;
            }
        }
        return providers.get(0);
    }

    /**
     * 成本优先（aliyun < tencent < mock）。
     */
    private SmsProvider selectCostFirst(List<SmsProvider> providers) {
        return providers.stream()
                .min((a, b) -> {
                    int costA = getProviderCost(a.providerType());
                    int costB = getProviderCost(b.providerType());
                    return Integer.compare(costA, costB);
                })
                .orElse(providers.get(0));
    }

    /**
     * 可用性优先（跳过连续失败超过阈值的 provider）。
     */
    private SmsProvider selectAvailabilityFirst(List<SmsProvider> providers) {
        for (SmsProvider p : providers) {
            AtomicInteger count = failureCount.get(p.providerType());
            if (count == null || count.get() < FAILURE_THRESHOLD) {
                return p;
            }
        }
        // 所有 provider 都超阈值，降级选择第一个
        log.warn("[SmsStrategy] 所有 provider 连续失败超阈值,降级选择第一个");
        return providers.get(0);
    }

    /**
     * 解析权重配置。
     */
    private Map<String, Integer> parseWeights() {
        Map<String, Integer> weights = new java.util.HashMap<>();
        if (weightsConfig != null && !weightsConfig.isBlank()) {
            for (String pair : weightsConfig.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    try {
                        weights.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return weights;
    }

    /**
     * 获取 provider 成本（越低越优先）。
     */
    private int getProviderCost(String providerType) {
        return switch (providerType) {
            case "aliyun" -> 1;
            case "tencent" -> 2;
            case "mock" -> 99;
            default -> 50;
        };
    }
}
