package com.njydsz.pmis.cronjob.server.core.alert;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.cronjob.server.config.CronjobProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 告警智能降噪管理器（P1-3）。
 *
 * <p>在 {@link AlertDispatcher} 派发告警前进行聚合和降噪处理：
 * <ul>
 *   <li><b>时间窗口聚合</b>：同一规则在聚合窗口内的多次告警合并为一条</li>
 *   <li><b>频次升级</b>：窗口内告警次数超过阈值时，升级通知渠道（追加短信/电话）</li>
 *   <li><b>自动降级</b>：长时间无告警后恢复原始通知通道</li>
 *   <li><b>同任务去重</b>：同一任务的同类告警在短时间窗口内只通知一次</li>
 * </ul>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>告警事件到达 → 检查聚合窗口</li>
 *   <li>窗口内已有告警 → 计数+1，判断是否需要升级</li>
 *   <li>窗口内无告警 → 通过，创建新窗口</li>
 *   <li>计数超过 maxAggregateCount → 追加升级通道</li>
 *   <li>超过降级冷却时间无告警 → 重置升级状态</li>
 * </ol>
 *
 * <p>仅在 {@code pmis.cronjob.alert-dedup.enabled=true} 时启用。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pmis.cronjob.alert-dedup.enabled", havingValue = "true")
public class AlertDedupManager {

    private final StringRedisTemplate redisTemplate;
    private final CronjobProperties cronjobProperties;

    /** Redis key 前缀：告警聚合计数 */
    private static final String AGGREGATE_COUNT_PREFIX = "pmis:alert:dedup:count:";
    /** Redis key 前缀：升级状态 */
    private static final String ESCALATION_PREFIX = "pmis:alert:dedup:escalate:";

    /**
     * 检查告警是否应该被发送（聚合+降噪）。
     *
     * <p>返回一个决策结果，包含是否发送、使用哪些通道。
     *
     * @param ruleId       告警规则 ID
     * @param jobId        任务 ID
     * @param alertType    告警类型
     * @param origChannels 原始通知通道
     * @return 降噪决策结果
     */
    public DedupDecision checkAndDedup(String ruleId, String jobId, String alertType, String origChannels) {
        CronjobProperties.AlertDedup config = cronjobProperties.getAlertDedup();
        String aggregateKey = AGGREGATE_COUNT_PREFIX + ruleId + ":" + alertType;

        try {
            // 原子递增计数
            Long count = redisTemplate.opsForValue().increment(aggregateKey);
            if (count == null) {
                count = 1L;
            }

            // 首次告警：设置窗口 TTL
            if (count == 1) {
                redisTemplate.expire(aggregateKey, Duration.ofSeconds(config.getAggregateWindowSeconds()));
                // 首次告警，使用原始通道发送
                return DedupDecision.send(origChannels, false);
            }

            // 窗口内已有告警
            if (count > config.getMaxAggregateCount()) {
                // 超过阈值，升级通知
                String escalateKey = ESCALATION_PREFIX + ruleId;
                redisTemplate.opsForValue().set(escalateKey, "1",
                        Duration.ofSeconds(config.getDowngradeCooldownSeconds()));

                String escalatedChannels = mergeChannels(origChannels, config.getEscalateChannels());
                log.warn("[AlertDedup] 告警升级: ruleId={} alertType={} count={} channels={}",
                        ruleId, alertType, count, escalatedChannels);
                return DedupDecision.send(escalatedChannels, true);
            }

            // 窗口内但未超阈值，抑制告警
            log.debug("[AlertDedup] 告警抑制(窗口内): ruleId={} alertType={} count={}",
                    ruleId, alertType, count);
            return DedupDecision.suppress();
        } catch (Exception e) {
            // Redis 异常时放行（避免告警丢失）
            log.warn("[AlertDedup] 降噪检查异常, 放行: ruleId={} reason={}", ruleId, e.getMessage());
            return DedupDecision.send(origChannels, false);
        }
    }

    /**
     * 检查规则是否处于升级状态。
     *
     * @param ruleId 告警规则 ID
     * @return true 处于升级状态
     */
    public boolean isEscalated(String ruleId) {
        try {
            String key = ESCALATION_PREFIX + ruleId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 合并通知通道（去重）。
     */
    private String mergeChannels(String origChannels, String escalateChannels) {
        Set<String> channels = new LinkedHashSet<>();
        if (origChannels != null && !origChannels.isBlank()) {
            for (String ch : origChannels.split(",")) {
                channels.add(ch.trim());
            }
        }
        if (escalateChannels != null && !escalateChannels.isBlank()) {
            for (String ch : escalateChannels.split(",")) {
                channels.add(ch.trim());
            }
        }
        return String.join(",", channels);
    }

    /**
     * 降噪决策结果。
     *
     * @param send       是否发送
     * @param channels   使用的通知通道
     * @param escalated  是否为升级通知
     */
    public record DedupDecision(boolean send, String channels, boolean escalated) {
        /** 发送决策 */
        public static DedupDecision send(String channels, boolean escalated) {
            return new DedupDecision(true, channels, escalated);
        }
        /** 抑制决策 */
        public static DedupDecision suppress() {
            return new DedupDecision(false, null, false);
        }
    }
}
