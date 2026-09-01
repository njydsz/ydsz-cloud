package com.njydsz.cronjob.api.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.cronjob.api.fallback.CronjobServiceClientFallback;

/**
 * 定时任务服务 Feign 客户端（P1-2 规则与定时任务联动）
 *
 * <p>供 literule 等模块通过 Feign 远程触发 cronjob 定时任务。 当规则触发时，可通过此客户端立即执行一次指定的定时任务， 实现规则命中 →
 * 自动触发数据采集/报表生成/告警处理等后续动作。
 *
 * <h3>典型场景</h3>
 *
 * <ul>
 *   <li>预算超支规则命中 → 触发"预算重算"定时任务
 *   <li>风险预警规则命中 → 触发"风险分析报告生成"定时任务
 *   <li>EVM 偏差规则命中 → 触发"EVM 数据刷新"定时任务
 * </ul>
 *
 * <p>使用 {@link CronjobServiceClientFallback} 保证 cronjob 服务不可用时 不影响调用方主流程（降级为 WARN 日志）。
 *
 * @author ydsz-team
 * @since 26.09.01
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
  @PostMapping(FeignClientConstants.CRONJOB_PATH_TRIGGER)
  YdszResponse<String> trigger(@PathVariable("id") String jobId);

  /**
   * 立即触发执行一次定时任务（可选抢占分布式锁）
   *
   * @param jobId 任务 ID
   * @param holdLock 是否抢占分布式锁
   * @return 执行日志 ID
   */
  @PostMapping(FeignClientConstants.CRONJOB_PATH_TRIGGER)
  YdszResponse<String> trigger(
      @PathVariable("id") String jobId, @RequestParam("holdLock") boolean holdLock);

  /**
   * P2-F8: 查询任务详情（含 status / cron 等定义信息）。
   *
   * <p>对应 cronjob 模块: GET /api/v1/cronjob/{id}。返回 Map 以解耦 api 模块对 domain VO 的依赖。
   *
   * @param jobId 任务 ID
   * @return 任务详情（字段: id / jobKey / jobName / status / cronExpression / scheduleType 等）
   */
  @GetMapping(FeignClientConstants.CRONJOB_PATH_GET)
  YdszResponse<Map<String, Object>> getJobInfo(@PathVariable("id") String jobId);

  /**
   * P2-F8: 暂停任务。
   *
   * <p>对应 cronjob 模块: POST /api/v1/cronjob/{id}/pause。暂停后调度器不再触发该任务，
   * 正在执行的任务继续完成。
   *
   * @param jobId 任务 ID
   * @return 统一响应结果
   */
  @PostMapping(FeignClientConstants.CRONJOB_PATH_PAUSE)
  YdszResponse<Void> pauseJob(@PathVariable("id") String jobId);

  /**
   * P2-F8: 恢复任务。
   *
   * <p>对应 cronjob 模块: POST /api/v1/cronjob/{id}/resume。恢复后按 cron 表达式重新排程。
   *
   * @param jobId 任务 ID
   * @return 统一响应结果
   */
  @PostMapping(FeignClientConstants.CRONJOB_PATH_RESUME)
  YdszResponse<Void> resumeJob(@PathVariable("id") String jobId);
}
