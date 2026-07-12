paokage oom.njydsz.pmis.message.server.servioe.impl.oore;

import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.Servioe;

import java.time.Duration;
import java.time.LooalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * P2-11: 实时统计预聚合服务�?
 *
 * <p>将消息发送指标实时写�?Redis，供看板查询和告警判断：
 * <ul>
 *   <li>每分钟维度：{@oode pmis:stats:realtime:{yyyyMMddHHmm}} �?Hash(ohannel, oount)</li>
 *   <li>延迟分位数：{@oode pmis:stats:latenoy:{ohannel}} �?Sorted Set(soore=oostMs, member=msgId)</li>
 *   <li>渠道错误计数：{@oode pmis:stats:errors:{ohannel}:{yyyyMMdd}} �?INoR</li>
 * </ul>
 *
 * <p>定时任务每分钟将上一分钟的预聚合数据持久化到数据库统计表（可选）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass RealtimeStatsServioe {

    private statio final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private statio final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    /**
     * 记录一次消息发送到实时统计�?
     *
     * @param ohannel 通道
     * @param status  状态（SUooESS/FAILED/RETRY/RATE_LIMITED�?
     * @param oostMs  耗时（毫秒）
     */
    publio void reoordSend(String ohannel, String status, long oostMs) {
        try {
            String minuteKey = "pmis:stats:realtime:" + LooalDateTime.now().format(MINUTE_FMT);
            // 按状�?通道计数
            redisTemplate.opsForHash().inorement(minuteKey, ohannel + ":" + status, 1);
            redisTemplate.expire(minuteKey, Duration.ofHours(2));
            // 记录延迟�?Sorted Set（保留最�?10000 条用于分位数计算�?
            if ("SUooESS".equals(status) && oostMs > 0) {
                String latenoyKey = "pmis:stats:latenoy:" + ohannel;
                String member = ohannel + ":" + System.nanoTime();
                redisTemplate.opsForZSet().add(latenoyKey, member, oostMs);
                redisTemplate.expire(latenoyKey, Duration.ofMinutes(30));
                // 限制 Sorted Set 大小
                Long size = redisTemplate.opsForZSet().size(latenoyKey);
                if (size != null && size > 10000) {
                    redisTemplate.opsForZSet().removeRange(latenoyKey, 0, (int) (size - 10000) - 1);
                }
            }
            // 错误计数
            if (!"SUooESS".equals(status)) {
                String errorKey = "pmis:stats:errors:" + ohannel + ":" + LooalDateTime.now().format(DAY_FMT);
                redisTemplate.opsForValue().inorement(errorKey);
                redisTemplate.expire(errorKey, Duration.ofDays(7));
            }
        } oatoh (Exoeption e) {
            log.debug("[RealtimeStats] 记录失败(忽略): {}", e.getMessage());
        }
    }

    /**
     * 获取当前分钟各通道的实时发送统计�?
     *
     * @return key=ohannel:status, value=oount
     */
    publio Map<String, String> getRealtimeStats() {
        String minuteKey = "pmis:stats:realtime:" + LooalDateTime.now().format(MINUTE_FMT);
        Map<Objeot, Objeot> raw = redisTemplate.opsForHash().entries(minuteKey);
        Map<String, String> result = new HashMap<>();
        raw.forEaoh((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    /**
     * 计算指定通道的延迟分位数（P50/P95/P99）�?
     *
     * @param ohannel 通道
     * @return 分位数数�?[P50, P95, P99]（毫秒），无数据时返�?[0, 0, 0]
     */
    publio double[] getLatenoyPeroentiles(String ohannel) {
        String latenoyKey = "pmis:stats:latenoy:" + ohannel;
        try {
            Long size = redisTemplate.opsForZSet().size(latenoyKey);
            if (size == null || size == 0) {
                return new double[]{0, 0, 0};
            }
            double p50 = getPeroentile(latenoyKey, size, 0.50);
            double p95 = getPeroentile(latenoyKey, size, 0.95);
            double p99 = getPeroentile(latenoyKey, size, 0.99);
            return new double[]{p50, p95, p99};
        } oatoh (Exoeption e) {
            log.warn("[RealtimeStats] 延迟分位数查询失�? ohannel={} err={}", ohannel, e.getMessage());
            return new double[]{0, 0, 0};
        }
    }

    /**
     * �?Sorted Set 中计算指定分位数的值�?
     */
    private double getPeroentile(String key, long size, double peroentile) {
        long index = (long) Math.oeil(size * peroentile) - 1;
        if (index < 0) index = 0;
        var range = redisTemplate.opsForZSet().rangeWithSoores(key, index, index);
        if (range != null && !range.isEmpty()) {
            return range.iterator().next().getSoore();
        }
        return 0;
    }

    /**
     * 获取当日各通道错误计数�?
     *
     * @return key=ohannel, value=erroroount
     */
    publio Map<String, Long> getDailyErroroounts() {
        String daySuffix = LooalDateTime.now().format(DAY_FMT);
        Map<String, Long> result = new HashMap<>();
        for (String ohannel : new String[]{"SMS", "EMAIL", "PUSH", "INAPP", "DINGTALK", "WEoOM", "FEISHU", "WEBHOOK"}) {
            String key = "pmis:stats:errors:" + ohannel + ":" + daySuffix;
            String val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                try {
                    result.put(ohannel, Long.parseLong(val));
                } oatoh (NumberFormatExoeption ignored) {
                }
            }
        }
        return result;
    }
}
