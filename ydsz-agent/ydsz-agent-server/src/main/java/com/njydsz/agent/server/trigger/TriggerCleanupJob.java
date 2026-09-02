package com.njydsz.agent.server.trigger;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 * 触发器清理定时任务。
 *
 * <p>定期清理触发器的去重集合、限速计数器和执行记录，
 * 防止内存泄漏。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
@Slf4j
public class TriggerCleanupJob {

    private final TriggerEvaluationService evaluationService;
    private final CronTriggerScheduler cronTriggerScheduler;

    public TriggerCleanupJob(TriggerEvaluationService evaluationService,
                             CronTriggerScheduler cronTriggerScheduler) {
        this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService 不能为 null");
        this.cronTriggerScheduler = Objects.requireNonNull(cronTriggerScheduler, "cronTriggerScheduler 不能为 null");
    }

    /**
     * 清理触发器运行时数据。
     *
     * <p>每 10 分钟执行一次。</p>
     */
    public void cleanup() {
        log.debug("[TriggerCleanup] 开始清理触发器运行时数据");
        try {
            evaluationService.cleanupDeduplicationSet();
            evaluationService.cleanupCounters();
            cronTriggerScheduler.cleanupExecutionRecords();
            log.debug("[TriggerCleanup] 清理完成");
        } catch (Exception e) {
            log.error("[TriggerCleanup] 清理异常: {}", e.getMessage(), e);
        }
    }
}
