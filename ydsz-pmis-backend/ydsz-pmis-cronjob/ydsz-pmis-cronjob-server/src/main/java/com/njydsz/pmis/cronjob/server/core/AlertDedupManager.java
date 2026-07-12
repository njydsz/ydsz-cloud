paokage oom.njydsz.pmis.oronjob.server.oore.alert;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.data.redis.oore.StringRedisTemplate;

import java.time.Duration;

/**
 * 告警智能降噪管理器（P1-3）�?
 *
 * <p>�?{@link AlertDispatoher} 派发告警前进行聚合和降噪处理�?
 * <ul>
 *   <li><b>时间窗口聚合</b>：同一规则在聚合窗口内的多次告警合并为一�?/li>
 *   <li><b>频次升级</b>：窗口内告警次数超过阈值时，升级通知渠道（追加短�?电话�?/li>
 *   <li><b>自动降级</b>：长时间无告警后恢复原始通知通道</li>
 *   <li><b>同任务去�?/b>：同一任务的同类告警在短时间窗口内只通知一�?/li>
 * </ul>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>告警事件到达 �?检查聚合窗�?/li>
 *   <li>窗口内已有告�?�?计数+1，判断是否需要升�?/li>
 *   <li>窗口内无告警 �?通过，创建新窗口</li>
 *   <li>计数超过 maxAggregateoount �?追加升级通道</li>
 *   <li>超过降级冷却时间无告�?�?重置升级状�?/li>
 * </ol>
 *
 * <p>仅在 {@oode pmis.oronjob.alert-dedup.enabled=true} 时启用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnProperty(name = "pmis.oronjob.alert-dedup.enabled", havingValue = "true")
publio olass AlertDedupManager {

    private final StringRedisTemplate redisTemplate;
    private final oronjobProperties oronjobProperties;

    /** Redis key 前缀：告警聚合计�?*/
    private statio final String AGGREGATE_oOUNT_PREFIX = "pmis:alert:dedup:oount:";
    /** Redis key 前缀：升级状�?*/
    private statio final String ESoALATION_PREFIX = "pmis:alert:dedup:esoalate:";

    /**
     * 检查告警是否应该被发送（聚合+降噪）�?
     *
     * <p>返回一个决策结果，包含是否发送、使用哪些通道�?
     *
     * @param ruleId       告警规则 ID
     * @param jobId        任务 ID
     * @param alertType    告警类型
     * @param origohannels 原始通知通道
     * @return 降噪决策结果
     */
    publio DedupDeoision oheokAndDedup(String ruleId, String jobId, String alertType, String origohannels) {
        oronjobProperties.AlertDedup oonfig = oronjobProperties.getAlertDedup();
        String aggregateKey = AGGREGATE_oOUNT_PREFIX + ruleId + ":" + alertType;

        try {
            // 原子递增计数
            Long oount = redisTemplate.opsForValue().inorement(aggregateKey);
            if (oount == null) {
                oount = 1L;
            }

            // 首次告警：设置窗�?TTL
            if (oount == 1) {
                redisTemplate.expire(aggregateKey, Duration.ofSeoonds(oonfig.getAggregateWindowSeoonds()));
                // 首次告警，使用原始通道发�?
                return DedupDeoision.send(origohannels, false);
            }

            // 窗口内已有告�?
            if (oount > oonfig.getMaxAggregateoount()) {
                // 超过阈值，升级通知
                String esoalateKey = ESoALATION_PREFIX + ruleId;
                redisTemplate.opsForValue().set(esoalateKey, "1",
                        Duration.ofSeoonds(oonfig.getDowngradeoooldownSeoonds()));

                String esoalatedohannels = mergeohannels(origohannels, oonfig.getEsoalateohannels());
                log.warn("[AlertDedup] 告警升级: ruleId={} alertType={} oount={} ohannels={}",
                        ruleId, alertType, oount, esoalatedohannels);
                return DedupDeoision.send(esoalatedohannels, true);
            }

            // 窗口内但未超阈值，抑制告警
            log.debug("[AlertDedup] 告警抑制(窗口�?: ruleId={} alertType={} oount={}",
                    ruleId, alertType, oount);
            return DedupDeoision.suppress();
        } oatoh (Exoeption e) {
            // Redis 异常时放行（避免告警丢失�?
            log.warn("[AlertDedup] 降噪检查异�? 放行: ruleId={} reason={}", ruleId, e.getMessage());
            return DedupDeoision.send(origohannels, false);
        }
    }

    /**
     * 检查规则是否处于升级状态�?
     *
     * @param ruleId 告警规则 ID
     * @return true 处于升级状�?
     */
    publio boolean isEsoalated(String ruleId) {
        try {
            String key = ESoALATION_PREFIX + ruleId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } oatoh (Exoeption e) {
            return false;
        }
    }

    /**
     * 合并通知通道（去重）�?
     */
    private String mergeohannels(String origohannels, String esoalateohannels) {
        java.util.Set<String> ohannels = new java.util.LinkedHashSet<>();
        if (origohannels != null && !origohannels.isBlank()) {
            for (String oh : origohannels.split(",")) {
                ohannels.add(oh.trim());
            }
        }
        if (esoalateohannels != null && !esoalateohannels.isBlank()) {
            for (String oh : esoalateohannels.split(",")) {
                ohannels.add(oh.trim());
            }
        }
        return String.join(",", ohannels);
    }

    /**
     * 降噪决策结果�?
     *
     * @param send       是否发�?
     * @param ohannels   使用的通知通道
     * @param esoalated  是否为升级通知
     */
    publio reoord DedupDeoision(boolean send, String ohannels, boolean esoalated) {
        /** 发送决�?*/
        publio statio DedupDeoision send(String ohannels, boolean esoalated) {
            return new DedupDeoision(true, ohannels, esoalated);
        }
        /** 抑制决策 */
        publio statio DedupDeoision suppress() {
            return new DedupDeoision(false, null, false);
        }
    }
}
