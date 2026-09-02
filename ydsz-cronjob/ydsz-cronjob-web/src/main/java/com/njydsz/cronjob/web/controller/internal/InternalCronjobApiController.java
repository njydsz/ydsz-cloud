package com.njydsz.cronjob.web.controller.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;

/**
 * 内部 API Controller（服务间 Feign 调用）
 *
 * <p>为 <b>跨服务 Feign 调用</b> 提供统一 HTTP 入口。端点<b>仅用于服务间通信</b>，不应直接对外暴露。
 *
 * <p><b>接口路径：</b>{@code /api/internal/**}
 *
 * <p><b>安全要求：</b>
 *
 * <ul>
 *   <li>Gateway 应限制 {@code /api/internal/**} 仅允许<b>内部服务 IP</b>调用（白名单），对公网不可访问
 *   <li>所有接口启用 {@link RateLimit} 接口级限流（100 QPS），防止被恶意刷接口
 *   <li>触发 / 暂停 / 恢复接口启用 {@link Idempotent} 幂等保护（5 秒），避免重试风暴
 * </ul>
 *
 * <p><b>响应契约：</b>所有端点统一返回 {@link YdszResponse} 包装，与 {@code ydsz-cronjob-api} 模块中
 * {@code CronjobServiceClient} 的 Feign 声明严格对齐。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.cronjob.api.client.CronjobServiceClient Feign Client 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "cronjob:internal:api")
public class InternalCronjobApiController {

  private final JobService jobService;

  /**
   * 立即触发执行一次定时任务（不抢占分布式锁）
   *
   * <p>对应 cronjob 模块：POST /api/internal/cronjob/{id}/trigger?holdLock=false
   *
   * @param jobId 任务 ID
   * @return 执行日志 ID
   */
  @RateLimit(resource = "cronjob.internalapi.trigger", threshold = 100)
  @Idempotent(
      key = "'ydsz:cronjob:internal-api:trigger:' + #jobId",
      ttlSeconds = 5)
  @PostMapping("/cronjob/{id}/trigger")
  public YdszResponse<String> trigger(@PathVariable("id") String jobId) {
    return YdszResponse.success(jobService.trigger(jobId));
  }

  /**
   * 立即触发执行一次定时任务（可选抢占分布式锁）
   *
   * <p>多实例部署场景下传入 {@code holdLock=true} 走锁路径，避免与定时触发并发执行。
   *
   * @param jobId 任务 ID
   * @param holdLock 是否抢占分布式锁
   * @return 执行日志 ID
   */
  @RateLimit(resource = "cronjob.internalapi.triggerWithLock", threshold = 100)
  @Idempotent(
      key = "'ydsz:cronjob:internal-api:trigger-lock:' + #jobId + ':' + #holdLock",
      ttlSeconds = 5)
  @PostMapping(value = "/cronjob/{id}/trigger", params = "holdLock")
  public YdszResponse<String> triggerWithLock(
      @PathVariable("id") String jobId, @RequestParam("holdLock") boolean holdLock) {
    return YdszResponse.success(jobService.trigger(jobId, holdLock));
  }

  /**
   * 查询任务详情
   *
   * <p>对应 cronjob 模块：GET /api/internal/cronjob/{id}
   *
   * @param jobId 任务 ID
   * @return 任务详情
   */
  @RateLimit(resource = "cronjob.internalapi.getJobInfo", threshold = 100)
  @Idempotent(
      key = "'ydsz:cronjob:internal-api:get-job-info:' + #jobId",
      ttlSeconds = 5)
  @GetMapping("/cronjob/{id}")
  public YdszResponse<JobVO> getJobInfo(@PathVariable("id") String jobId) {
    return YdszResponse.success(jobService.getById(jobId));
  }

  /**
   * 暂停任务
   *
   * <p>暂停后调度器不再触发该任务，正在执行的任务继续完成。
   *
   * @param jobId 任务 ID
   * @return 操作结果
   */
  @RateLimit(resource = "cronjob.internalapi.pauseJob", threshold = 50)
  @Idempotent(
      key = "'ydsz:cronjob:internal-api:pause-job:' + #jobId",
      ttlSeconds = 5)
  @PostMapping("/cronjob/{id}/pause")
  public YdszResponse<Void> pauseJob(@PathVariable("id") String jobId) {
    jobService.pause(jobId);
    return YdszResponse.success(null);
  }

  /**
   * 恢复任务
   *
   * <p>恢复后按 cron 表达式重新排程。
   *
   * @param jobId 任务 ID
   * @return 操作结果
   */
  @RateLimit(resource = "cronjob.internalapi.resumeJob", threshold = 50)
  @Idempotent(
      key = "'ydsz:cronjob:internal-api:resume-job:' + #jobId",
      ttlSeconds = 5)
  @PostMapping("/cronjob/{id}/resume")
  public YdszResponse<Void> resumeJob(@PathVariable("id") String jobId) {
    jobService.resume(jobId);
    return YdszResponse.success(null);
  }
}
