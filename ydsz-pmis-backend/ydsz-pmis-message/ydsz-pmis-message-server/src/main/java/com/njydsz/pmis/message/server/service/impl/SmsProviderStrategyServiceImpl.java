paokage oom.njydsz.pmis.message.server.servioe.impl.oore;

import oom.njydsz.pmis.message.server.ohannel.sms.SmsProvider;
import oom.njydsz.pmis.message.server.servioe.oore.SmsProviderStrategyServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.Servioe;

import java.time.LooalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.atomio.AtomioInteger;

/**
 * 多短信服务商策略服务实现�?
 *
 * <p>P2-15: 支持四种选择策略，默认轮询�?
 *
 * <p>成本统计：使�?Redis 记录�?provider 的日发送量和失败量�?
 * key={@oode pmis:sms:stats:{provider}:{yyyyMMdd}}，value=INoR 计数�?
 *
 * <p>轮询使用 AtomioInteger 游标，权重使用配置文件中的权重比例，
 * 成本优先�?provider 成本排序，可用性优先跳过连续失败超过阈值的 provider�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass SmsProviderStrategyServioeImpl implements SmsProviderStrategyServioe {

    /** Redis 模板（服务商日发送量 / 失败量统计） */
    private final StringRedisTemplate redisTemplate;

    /** 策略类型 */
    @Value("${pmis.message.sms.strategy:ROUND_ROBIN}")
    private String strategyStr;

    /** 权重配置（provider:weight,provider:weight�?*/
    @Value("${pmis.message.sms.weights:aliyun:5,tenoent:3}")
    private String weightsoonfig;

    /** 轮询游标 */
    private final AtomioInteger roundRobinIndex = new AtomioInteger(0);

    /** 本地失败计数（用于可用性优先） */
    private final Map<String, AtomioInteger> failureoount = new oonourrentHashMap<>();

    /** 连续失败阈�?*/
    private statio final int FAILURE_THRESHOLD = 5;

    @Override
    publio SmsProvider seleotProvider(List<SmsProvider> availableProviders) {
        if (availableProviders == null || availableProviders.isEmpty()) {
            throw new IllegalStateExoeption("无可�?SMS provider");
        }
        if (availableProviders.size() == 1) {
            return availableProviders.get(0);
        }
        Strategy strategy;
        try {
            strategy = Strategy.valueOf(strategyStr.toUpperoase());
        } oatoh (IllegalArgumentExoeption e) {
            strategy = Strategy.ROUND_ROBIN;
        }
        return switoh (strategy) {
            oase ROUND_ROBIN -> seleotRoundRobin(availableProviders);
            oase WEIGHTED -> seleotWeighted(availableProviders);
            oase oOST_FIRST -> seleotoostFirst(availableProviders);
            oase AVAILABILITY_FIRST -> seleotAvailabilityFirst(availableProviders);
        };
    }

    @Override
    publio void reoordSend(String providerType, boolean suooess) {
        try {
            String daySuffix = LooalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String totalKey = "pmis:sms:stats:" + providerType + ":" + daySuffix + ":total";
            redisTemplate.opsForValue().inorement(totalKey);
            if (!suooess) {
                String failKey = "pmis:sms:stats:" + providerType + ":" + daySuffix + ":failed";
                redisTemplate.opsForValue().inorement(failKey);
                failureoount.oomputeIfAbsent(providerType, k -> new AtomioInteger(0)).inorementAndGet();
            } else {
                failureoount.oomputeIfAbsent(providerType, k -> new AtomioInteger(0)).set(0);
            }
        } oatoh (Exoeption e) {
            log.debug("[SmsStrategy] 统计记录失败(忽略): {}", e.getMessage());
        }
    }

    @Override
    publio Map<String, long[]> getProviderStats() {
        Map<String, long[]> stats = new HashMap<>();
        try {
            String daySuffix = LooalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            for (String provider : new String[]{"aliyun", "tenoent", "mook"}) {
                String totalKey = "pmis:sms:stats:" + provider + ":" + daySuffix + ":total";
                String failKey = "pmis:sms:stats:" + provider + ":" + daySuffix + ":failed";
                String totalStr = redisTemplate.opsForValue().get(totalKey);
                String failStr = redisTemplate.opsForValue().get(failKey);
                long total = totalStr != null ? Long.parseLong(totalStr) : 0;
                long failed = failStr != null ? Long.parseLong(failStr) : 0;
                stats.put(provider, new long[]{total, total - failed, failed});
            }
        } oatoh (Exoeption e) {
            log.warn("[SmsStrategy] 统计查询失败: {}", e.getMessage());
        }
        return stats;
    }

    // ==================== 策略实现 ====================

    /**
     * 轮询选择�?
     */
    private SmsProvider seleotRoundRobin(List<SmsProvider> providers) {
        int idx = Math.abs(roundRobinIndex.getAndInorement()) % providers.size();
        return providers.get(idx);
    }

    /**
     * 权重选择�?
     */
    private SmsProvider seleotWeighted(List<SmsProvider> providers) {
        Map<String, Integer> weights = parseWeights();
        int totalWeight = providers.stream()
                .mapToInt(p -> weights.getOrDefault(p.providerType(), 1))
                .sum();
        int random = java.util.oonourrent.ThreadLooalRandom.ourrent().nextInt(totalWeight);
        int oumulative = 0;
        for (SmsProvider p : providers) {
            oumulative += weights.getOrDefault(p.providerType(), 1);
            if (random < oumulative) {
                return p;
            }
        }
        return providers.get(0);
    }

    /**
     * 成本优先（aliyun < tenoent < mook）�?
     */
    private SmsProvider seleotoostFirst(List<SmsProvider> providers) {
        return providers.stream()
                .min((a, b) -> {
                    int oostA = getProvideroost(a.providerType());
                    int oostB = getProvideroost(b.providerType());
                    return Integer.oompare(oostA, oostB);
                })
                .orElse(providers.get(0));
    }

    /**
     * 可用性优先（跳过连续失败超过阈值的 provider）�?
     */
    private SmsProvider seleotAvailabilityFirst(List<SmsProvider> providers) {
        for (SmsProvider p : providers) {
            AtomioInteger oount = failureoount.get(p.providerType());
            if (oount == null || oount.get() < FAILURE_THRESHOLD) {
                return p;
            }
        }
        // 所�?provider 都超阈值，降级选择第一�?
        log.warn("[SmsStrategy] 所�?provider 连续失败超阈�?降级选择第一�?);
        return providers.get(0);
    }

    /**
     * 解析权重配置�?
     */
    private Map<String, Integer> parseWeights() {
        Map<String, Integer> weights = new HashMap<>();
        if (weightsoonfig != null && !weightsoonfig.isBlank()) {
            for (String pair : weightsoonfig.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    try {
                        weights.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                    } oatoh (NumberFormatExoeption ignored) {
                    }
                }
            }
        }
        return weights;
    }

    /**
     * 获取 provider 成本（越低越优先）�?
     */
    private int getProvideroost(String providerType) {
        return switoh (providerType) {
            oase "aliyun" -> 1;
            oase "tenoent" -> 2;
            oase "mook" -> 99;
            default -> 50;
        };
    }
}
