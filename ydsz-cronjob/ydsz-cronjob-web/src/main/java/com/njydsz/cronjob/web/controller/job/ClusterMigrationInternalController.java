package com.njydsz.cronjob.web.controller.job;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;

/**
 * 集群漂移内部接收端 Controller（P2-5）。
 *
 * <p>目标集群暴露此接口，接收源集群通过 {@code ClusterMigrationClient} 发送的注册/注销请求。
 *
 * <h3>安全</h3>
 *
 * <ul>
 *   <li>本端点受 {@code InternalTokenFilter} 保护（校验 X-Ydsz-Internal-Token）
 *   <li>不走 @AuthApiPermission（节点间内部通信）
 *   <li>注册请求幂等（重复注册会覆盖）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Tag(name = "集群漂移（内部接收端）", description = "接收远程集群的漂移注册/注销请求")
@RestController
@RequestMapping("/api/v1/cronjob/internal/migrate")
@RequiredArgsConstructor
public class ClusterMigrationInternalController {

  private final JobService jobService;

  /**
   * 接收远程集群的任务注册请求。
   *
   * <p>将任务注册到本机调度器。如果任务已存在则覆盖（幂等）。
   *
   * @param jobVO 任务完整信息（源集群序列化的 JobVO JSON）
   * @return 注册结果
   */
  @Operation(summary = "接收漂移任务注册")
  @Idempotent(key = "ydsz:cronjob:migrate:register:lock", ttlSeconds = 10)
  @Audit(
      module = "集群漂移",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'migrateRegister'")
  @RateLimit(resource = "cronjob.migrate.register", threshold = 100)
  @PostMapping("/register")
  public YdszResponse<Boolean> register(@RequestBody JobVO jobVO) {
    try {
      // 将 JobVO 转换为 JobPostDTO 后注册
      JobPostDTO dto = new JobPostDTO();
      dto.setJobKey(jobVO.getJobKey());
      dto.setJobName(jobVO.getJobName());
      dto.setJobGroup(jobVO.getJobGroup());
      dto.setScheduleType(jobVO.getScheduleType());
      dto.setCronExpression(jobVO.getCronExpression());
      dto.setFixedRateMs(jobVO.getFixedRateMs());
      dto.setFixedDelayMs(jobVO.getFixedDelayMs());
      dto.setHandler(jobVO.getHandler());
      dto.setParamsJson(jobVO.getParamsJson());
      dto.setShardTotal(jobVO.getShardTotal());
      dto.setSlowThresholdMs(jobVO.getSlowThresholdMs());
      dto.setCluster(jobVO.getCluster());
      dto.setTimezone(jobVO.getTimezone());
      dto.setStatus(jobVO.getStatus());
      boolean result = jobService.register(dto);
      return YdszResponse.success(result);
    } catch (Exception e) {
      log.warn("[ClusterMigration] 接收注册失败: jobKey={} reason={}", jobVO.getJobKey(), e.getMessage());
      return YdszResponse.error(e.getMessage());
    }
  }

  /**
   * 接收远程集群的任务注销请求。
   *
   * <p>从本机调度器注销任务（停止调度）。
   *
   * @param request 注销请求（含 jobKey）
   * @return 注销结果
   */
  @Operation(summary = "接收漂移任务注销")
  @Audit(
      module = "集群漂移",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'migrateUnregister'")
  @PostMapping("/unregister")
  public YdszResponse<Boolean> unregister(@RequestBody UnregisterRequest request) {
    try {
      boolean result = jobService.unregister(request.getJobKey());
      return YdszResponse.success(result);
    } catch (Exception e) {
      log.warn(
          "[ClusterMigration] 接收注销失败: jobKey={} reason={}",
          request.getJobKey(),
          e.getMessage());
      return YdszResponse.error(e.getMessage());
    }
  }

  /**
   * 注销请求 DTO。
   *
   * @param jobKey 任务唯一标识
   */
  public record UnregisterRequest(String jobKey) {
    public String getJobKey() {
      return jobKey;
    }
  }
}
