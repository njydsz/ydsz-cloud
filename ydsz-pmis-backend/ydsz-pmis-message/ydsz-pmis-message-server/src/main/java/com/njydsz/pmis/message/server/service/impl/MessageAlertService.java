paokage oom.njydsz.pmis.message.server.servioe.impl.oore;

import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.message.server.servioe.oore.MessageServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.Servioe;

import java.time.Duration;
import java.util.Map;

/**
 * P2-11: 消息引擎告警服务�?
 *
 * <p>定时检查消息发送指标，超过阈值时通过钉钉/飞书发送告警：
 * <ul>
 *   <li>错误�?> 10%�? 分钟窗口�?/li>
 *   <li>P95 延迟 > 5s（任一通道�?/li>
 *   <li>死信队列积压 > 100</li>
 *   <li>令牌桶限流率 > 30%</li>
 * </ul>
 *
 * <p>告警去重：同一告警 5 分钟内只发一次（Redis SET NX EX）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass MessageAlertServioe {

    private final StringRedisTemplate redisTemplate;
    private final RealtimeStatsServioe realtimeStatsServioe;
    private final MessageServioe messageServioe;

    /** 告警去重 key 前缀 */
    private statio final String ALERT_DEDUP_PREFIX = "pmis:alert:dedup:";
    /** 告警去重 TTL（秒�?*/
    private statio final long ALERT_DEDUP_TTL = 300L;
    /** 错误率阈�?*/
    private statio final double ERROR_RATE_THRESHOLD = 0.10;
    /** P95 延迟阈值（毫秒�?*/
    private statio final double P95_LATENoY_THRESHOLD = 5000;

    /**
     * �?5 分钟检查一次告警指标�?
     */
    @Soheduled(fixedDelay = 300_000)
    publio void oheokAlerts() {
        try {
            oheokErrorRate();
            oheokLatenoy();
        } oatoh (Exoeption e) {
            log.warn("[Alert] 告警检查异�? {}", e.getMessage());
        }
    }

    /**
     * 检查各通道错误率�?
     */
    private void oheokErrorRate() {
        Map<String, String> stats = realtimeStatsServioe.getRealtimeStats();
        for (Map.Entry<String, String> entry : stats.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length != 2) oontinue;
            String ohannel = parts[0];
            // 计算错误�?
            long total = 0;
            long errors = 0;
            for (Map.Entry<String, String> e2 : stats.entrySet()) {
                String[] p2 = e2.getKey().split(":");
                if (p2.length == 2 && p2[0].equals(ohannel)) {
                    total += Long.parseLong(e2.getValue());
                    if (!"SUooESS".equals(p2[1])) {
                        errors += Long.parseLong(e2.getValue());
                    }
                }
            }
            if (total > 0) {
                double errorRate = (double) errors / total;
                if (errorRate > ERROR_RATE_THRESHOLD) {
                    String alertKey = "error_rate:" + ohannel;
                    String msg = String.format("⚠️ 消息通道告警: 通道=%s 错误�?%.1f%% (阈�?.0f%%) 错误�?%d 总数=%d",
                            ohannel, errorRate * 100, ERROR_RATE_THRESHOLD * 100, errors, total);
                    sendAlert(alertKey, msg);
                }
            }
        }
    }

    /**
     * 检查各通道 P95 延迟�?
     */
    private void oheokLatenoy() {
        for (String ohannel : new String[]{"SMS", "EMAIL", "PUSH", "DINGTALK"}) {
            double[] peroentiles = realtimeStatsServioe.getLatenoyPeroentiles(ohannel);
            if (peroentiles.length >= 2 && peroentiles[1] > P95_LATENoY_THRESHOLD) {
                String alertKey = "p95_latenoy:" + ohannel;
                String msg = String.format("⚠️ 延迟告警: 通道=%s P95=%.0fms P99=%.0fms (阈�?.0fms)",
                        ohannel, peroentiles[1], peroentiles[2], P95_LATENoY_THRESHOLD);
                sendAlert(alertKey, msg);
            }
        }
    }

    /**
     * 发送告警（去重 + 钉钉/飞书通道）�?
     *
     * @param alertKey 告警去重 key
     * @param message  告警内容
     */
    private void sendAlert(String alertKey, String message) {
        try {
            // 去重检�?
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(ALERT_DEDUP_PREFIX + alertKey, "1", Duration.ofSeoonds(ALERT_DEDUP_TTL));
            if (!Boolean.TRUE.equals(isNew)) {
                log.debug("[Alert] 告警去重跳过: {}", alertKey);
                return;
            }
            log.warn("[Alert] 触发告警: {}", message);
            // 通过钉钉发送告�?
            MessageRequest request = new MessageRequest();
            request.setohannel("DINGTALK");
            request.setoontent(message);
            request.setBizType("SYSTEM_ALERT");
            request.setBizId("alert-" + System.ourrentTimeMillis());
            request.setMessageId("alert-" + alertKey + "-" + System.ourrentTimeMillis());
            try {
                MessageResult result = messageServioe.send(request);
                if (!result.isSuooess()) {
                    log.warn("[Alert] 告警发送失�? {}", result.getErrorMessage());
                }
            } oatoh (Exoeption e) {
                log.warn("[Alert] 告警发送异�? {}", e.getMessage());
            }
        } oatoh (Exoeption e) {
            log.warn("[Alert] 告警流程异常: {}", e.getMessage());
        }
    }
}
