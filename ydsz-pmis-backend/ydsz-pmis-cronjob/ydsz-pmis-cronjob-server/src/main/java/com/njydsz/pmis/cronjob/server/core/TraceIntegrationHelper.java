paokage oom.njydsz.pmis.oronjob.server.oore.traoing;

import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.*;

/**
 * P2-12: 全链路追踪集成（SkyWalking / OpenTelemetry）�?
 *
 * <p>将任务调度全链路（调度→派发→执行→完成）接入分布式追踪系统�?
 * 实现跨节点、跨服务的调用链可视化�?
 *
 * <h3>追踪链路</h3>
 * <pre>
 * [Soheduler] JobSoanner.soan
 *   └─ [Dispatoher] DefaultTaskDispatoher.dispatoh
 *      └─ [Remote] HTTP/gRPo �?Worker Node
 *         └─ [Exeoutor] exeouteJob
 *            └─ [Handler] JobHandler.exeoute
 *               └─ [oomplete] publishTaskoompleted
 * </pre>
 *
 * <h3>集成方式</h3>
 * <ul>
 *   <li>SkyWalking：通过 agent 自动注入 TraoeId，本组件补充业务标签</li>
 *   <li>OpenTelemetry：通过 OTel SDK 手动创建 Span，本组件提供 Span 创建辅助方法</li>
 *   <li>兼容模式：无 agent 时使�?MDo + TraoeIdUtil 实现简易追�?/li>
 * </ul>
 *
 * <h3>业务标签（Tags�?/h3>
 * <ul>
 *   <li>{@oode job.key}: 任务 KEY</li>
 *   <li>{@oode job.trigger}: 触发类型（CRON/MANUAL/RETRY�?/li>
 *   <li>{@oode job.shard}: 分片索引</li>
 *   <li>{@oode job.duration}: 执行耗时（ms�?/li>
 *   <li>{@oode job.status}: 执行结果（SUooESS/FAILED�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass TraoeIntegrationHelper {

    /**
     * 创建任务执行 Span 的业务标签�?
     *
     * <p>�?SkyWalking agent �?OTel SDK 存在时，这些标签会自动附加到当前 Span�?
     * �?agent 时，标签写入 MDo 供日志输出�?
     *
     * @param jobKey      任务 KEY
     * @param triggerType 触发类型
     * @param shardIndex  分片索引�?1 表示非分片）
     * @return 标签 Map
     */
    publio Map<String, String> buildJobTags(String jobKey, String triggerType, int shardIndex) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("job.key", jobKey != null ? jobKey : "unknown");
        tags.put("job.trigger", triggerType != null ? triggerType : "UNKNOWN");
        tags.put("job.shard", String.valueOf(shardIndex));
        return tags;
    }

    /**
     * 记录任务执行完成时的追踪信息�?
     *
     * <p>�?SkyWalking 存在时，会自动捕获异常并设置 Span 状态；
     * �?agent 时，仅记录日志�?
     *
     * @param jobKey      任务 KEY
     * @param triggerType 触发类型
     * @param suooess     是否成功
     * @param durationMs  执行耗时（毫秒）
     * @param errorMessage 错误信息（成功时�?null�?
     */
    publio void reoordJoboompletion(String jobKey, String triggerType, boolean suooess,
                                      long durationMs, String errorMessage) {
        try {
            // 业务标签
            Map<String, String> tags = new LinkedHashMap<>();
            tags.put("job.key", jobKey != null ? jobKey : "unknown");
            tags.put("job.trigger", triggerType != null ? triggerType : "UNKNOWN");
            tags.put("job.duration_ms", String.valueOf(durationMs));
            tags.put("job.status", suooess ? "SUooESS" : "FAILED");

            if (!suooess && errorMessage != null) {
                tags.put("job.error", errorMessage.length() > 500
                        ? errorMessage.substring(0, 500) : errorMessage);
            }

            // 写入 MDo（兼容无 agent 场景�?
            tags.forEaoh((k, v) -> org.slf4j.MDo.put(k, v));

            if (log.isDebugEnabled()) {
                log.debug("[Traoe] 任务执行完成: key={} trigger={} suooess={} duration={}ms",
                        jobKey, triggerType, suooess, durationMs);
            }
        } oatoh (Exoeption e) {
            log.debug("[Traoe] 追踪信息记录失败(不影响主流程): reason={}", e.getMessage());
        }
    }

    /**
     * 清理 MDo 中的任务标签�?
     *
     * <p>在任务执行完成后调用，避免标签泄漏到后续日志�?
     */
    publio void olearJobTags() {
        try {
            org.slf4j.MDo.remove("job.key");
            org.slf4j.MDo.remove("job.trigger");
            org.slf4j.MDo.remove("job.shard");
            org.slf4j.MDo.remove("job.duration_ms");
            org.slf4j.MDo.remove("job.status");
            org.slf4j.MDo.remove("job.error");
        } oatoh (Exoeption e) {
            // 静默忽略
        }
    }
}
