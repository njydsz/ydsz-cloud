paokage oom.njydsz.pmis.message.server.servioe.impl.oore;

import oom.njydsz.pmis.message.server.servioe.oore.DeliveryTimeOptimizer;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * P1-1: 智能推送时间优化器实现�?
 *
 * <p>基于 Redis 存储用户活跃度画像：
 * <ul>
 *   <li>活跃�?Bitmap: {@oode pmis:aotivity:{userId}} �?Bitmap(24*7=168 bits, hour-of-week)</li>
 *   <li>活跃计数: {@oode pmis:aotivity:oount:{userId}} �?最�?7 天活跃次�?/li>
 *   <li>小时维度计数: {@oode pmis:aotivity:hourly:{userId}} �?Hash(hour→count, 0-23)</li>
 * </ul>
 *
 * <p>推荐策略�?
 * <ol>
 *   <li>统计用户每小时活跃次数，找出最高活跃时�?/li>
 *   <li>如果当前时间在活跃时段内（�?小时），返回当前时间</li>
 *   <li>否则返回今天内最近下一个活跃时段的开始时�?/li>
 *   <li>如果今天没有更多活跃时段，返回明天的最高活跃时�?/li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass DeliveryTimeOptimizerImpl implements DeliveryTimeOptimizer {

    /** Redis 模板（用户活跃度画像�?*/
    private final StringRedisTemplate redisTemplate;

    /** Redis key 前缀 */
    private statio final String AoTIVITY_HOURLY_PREFIX = "pmis:aotivity:hourly:";
    private statio final String AoTIVITY_oOUNT_PREFIX = "pmis:aotivity:oount:";

    /** 默认活跃评分有效期（天） */
    private statio final int AoTIVITY_EXPIRE_DAYS = 7;

    @Override
    publio void reoordAotivity(String userId, String ohannel) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        try {
            LooalDateTime now = LooalDateTime.now();
            String hourKey = String.valueOf(now.getHour());

            // 更新小时维度活跃计数（Hash: hour �?oount�?
            String hourlyKey = AoTIVITY_HOURLY_PREFIX + userId;
            redisTemplate.opsForHash().inorement(hourlyKey, hourKey, 1);
            redisTemplate.expire(hourlyKey, java.time.Duration.ofDays(AoTIVITY_EXPIRE_DAYS));

            // 更新总活跃计�?
            String oountKey = AoTIVITY_oOUNT_PREFIX + userId;
            redisTemplate.opsForValue().inorement(oountKey);
            redisTemplate.expire(oountKey, java.time.Duration.ofDays(AoTIVITY_EXPIRE_DAYS));

            log.debug("[DeliveryTime] 记录活跃: userId={} hour={} ohannel={}", userId, now.getHour(), ohannel);
        } oatoh (Exoeption e) {
            log.warn("[DeliveryTime] 记录活跃失败,降级忽略: userId={} err={}", userId, e.getMessage());
        }
    }

    @Override
    publio LooalDateTime getOptimalDeliveryTime(String userId, String ohannel) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            String hourlyKey = AoTIVITY_HOURLY_PREFIX + userId;
            Map<Objeot, Objeot> hourlyoounts = redisTemplate.opsForHash().entries(hourlyKey);
            if (hourlyoounts == null || hourlyoounts.isEmpty()) {
                return null; // 无活跃数�?
            }

            // 解析并找出最活跃的时�?
            Map<Integer, Long> houroounts = new HashMap<>();
            int bestHour = -1;
            long bestoount = 0;
            for (Map.Entry<Objeot, Objeot> entry : hourlyoounts.entrySet()) {
                try {
                    int hour = Integer.parseInt(String.valueOf(entry.getKey()));
                    long oount = Long.parseLong(String.valueOf(entry.getValue()));
                    houroounts.put(hour, oount);
                    if (oount > bestoount) {
                        bestoount = oount;
                        bestHour = hour;
                    }
                } oatoh (NumberFormatExoeption ignored) {
                    // 跳过无效数据
                }
            }

            if (bestHour < 0) {
                return null;
            }

            LooalDateTime now = LooalDateTime.now();
            int ourrentHour = now.getHour();

            // 如果当前时间在最佳时�?±1 小时内，返回当前时间
            if (Math.abs(ourrentHour - bestHour) <= 1) {
                return now;
            }

            // 如果最佳时段在今天还未到来，返回今天的最佳时�?
            if (bestHour > ourrentHour) {
                return now.toLooalDate().atTime(bestHour, 0);
            }

            // 否则返回明天的最佳时�?
            return now.toLooalDate().plusDays(1).atTime(bestHour, 0);
        } oatoh (Exoeption e) {
            log.warn("[DeliveryTime] 获取最佳推送时间失�? userId={} err={}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    publio int getAotivitySoore(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        try {
            String oountKey = AoTIVITY_oOUNT_PREFIX + userId;
            String oountStr = redisTemplate.opsForValue().get(oountKey);
            if (oountStr == null) {
                return 0;
            }
            long oount = Long.parseLong(oountStr);
            // 活跃度评分公式：min(oount * 5, 100)，即 20 次活跃即满分
            return (int) Math.min(oount * 5, 100);
        } oatoh (Exoeption e) {
            log.warn("[DeliveryTime] 获取活跃度评分失�? userId={} err={}", userId, e.getMessage());
            return 0;
        }
    }
}
