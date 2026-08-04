package com.remisoft.cronjob.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.feign.FeignClientConstants;
import com.remisoft.cronjob.api.fallback.CronjobServiceClientFallback;

/**
 * 定时任务服务 Feign 客户端（P1-2 规则与定时任务联动）
 *
 * <p>供 literule 等模块通过 Feign 远程触发 cronjob 定时任务。
 * 当规则触发时，可通过此客户端立即执行一次指定的定时任务，
 * 实现规则命中 → 自动触发数据采集/报表生成/告警处理等后续动作。
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>预算超支规则命中 → 触发"预算重算"定时任务</li>
 *   <li>风险预警规则命中 → 触发"风险分析报告生成"定时任务</li>
 *   <li>EVM 偏差规则命中 → 触发"EVM 数据刷新"定时任务</li>
 * </ul>
 *
 * <p>使用 {@link CronjobServiceClientFallback} 保证 cronjob 服务不可用时
 * 不影响调用方主流程（降级为 WARN 日志）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@FeignClient(
        name = FeignClientConstants.CRONJOB,
        contextId = "cronjobServiceClient",
        fallbackFactory = CronjobServiceClientFallback.class)

public interface CronjobServiceClient {

    /**
     * 立即触发执行一次定时任务（不抢占分布式锁）
     *
     * <p>对应 cronjob 模块: POST /api/v1/cronjob/{id}/trigger?holdLock=false
     *
     * @param jobId 任务 ID
     * @return 执行日志 ID（触发失败时为 null）
     */
    @PostMapping("/api/v1/cronjob/{id}/trigger")
    BaseResponse<String> trigger(@PathVariable("id") String jobId);

    /**
     * 立即触发执行一次定时任务（可选抢占分布式锁）
     *
     * @param jobId   任务 ID
     * @param holdLock 是否抢占分布式锁
     * @return 执行日志 ID
     */
    @PostMapping("/api/v1/cronjob/{id}/trigger")
    BaseResponse<String> trigger(@PathVariable("id") String jobId,
                           @RequestParam("holdLock") boolean holdLock);
}
