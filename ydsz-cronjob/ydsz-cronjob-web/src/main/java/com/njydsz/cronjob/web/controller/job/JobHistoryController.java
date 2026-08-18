package com.njydsz.cronjob.web.controller.job;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.domain.vo.JobHistoryVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobHistoryService;

/**
 * 任务配置历史版本 Controller（P1-6 任务版本管理）。
 *
 * <p>提供任务配置历史版本的查询、详情、回滚、对比等 HTTP 接口。
 *
 * <h3>版本机制</h3>
 *
 * 每次任务配置变更（create/update/import/rollback）都会写入 {@code ydsz_job_history} 快照，
 * 形成完整的版本谱系。回滚不删除中间版本，而是基于历史版本创建新版本。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #versions} - 获取任务版本列表（按版本号降序）
 *   <li>{@link #detail} - 获取指定版本完整配置
 *   <li>{@link #rollback} - 回滚到指定版本
 *   <li>{@link #compare} - 对比两个版本的差异字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "任务配置历史版本", description = "版本列表、版本详情、回滚、版本对比")
@RestController
@RequestMapping("/api/v1/cronjob/history")
@RequiredArgsConstructor
public class JobHistoryController {

  /** 任务配置历史版本服务 */
  private final JobHistoryService jobHistoryService;

  /**
   * 获取指定任务的版本列表（按版本号降序）。
   *
   * <p>用于版本管理 UI：用户选择某个历史版本 → 调用 {@link #detail} 或 {@link #rollback}。
   *
   * @param jobId 任务 ID
   * @return 历史版本列表（含 version/createdBy/createdAt/changeReason）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @Operation(summary = "获取任务版本列表")
  @GetMapping("/versions")
  public BaseResponse<List<JobHistoryVO>> versions(@RequestParam String jobId) {
    return BaseResponse.success(
        CronjobConverter.INSTANT.jobHistoryListToVO(jobHistoryService.listVersions(jobId)));
  }

  /**
   * 获取指定任务的指定历史版本详情。
   *
   * <p>返回完整任务配置（含 cron/handler/params/status 等），与当前版本对比即可识别差异。
   *
   * @param jobId 任务 ID
   * @param version 版本号
   * @return 历史版本记录
   */
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @Operation(summary = "获取指定版本详情")
  @GetMapping("/detail")
  public BaseResponse<JobHistoryVO> detail(
      @RequestParam String jobId, @RequestParam Integer version) {
    return BaseResponse.success(
        CronjobConverter.INSTANT.entityToVO(jobHistoryService.getVersion(jobId, version)));
  }

  /**
   * 回滚到指定版本。
   *
   * <p>基于目标历史版本创建新版本（version = current + 1），保留完整版本谱系。 已运行的实例继续按其启动时的配置执行；新触发的实例使用新版本。
   *
   * @param jobId 任务 ID
   * @param version 目标版本号
   * @return 回滚后的任务定义
   */
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
  @Operation(summary = "回滚到指定版本")
  @Idempotent(key = "ydsz:cronjob:JobHistoryController:rollback:lock", ttlSeconds = 5)
  @RateLimit(resource = "cronjob.jobhistory.rollback", threshold = 50)
  @PostMapping("/rollback")
  @Audit(
      module = "任务历史",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'rollback'")
  public BaseResponse<JobVO> rollback(@RequestParam String jobId, @RequestParam Integer version) {
    return BaseResponse.success(
        CronjobConverter.INSTANT.entityToVO(jobHistoryService.rollback(jobId, version)));
  }

  /**
   * 对比两个版本的差异。
   *
   * <p>深度比较两个版本的字段差异，返回新增/修改/删除的字段列表， 供前端"版本对比"对话框使用。版本顺序不影响结果。
   *
   * @param jobId 任务 ID
   * @param v1 旧版本号
   * @param v2 新版本号
   * @return 差异字段列表（含 field/oldValue/newValue/changeType）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @Operation(summary = "对比两个版本差异")
  @GetMapping("/compare")
  public BaseResponse<List<Map<String, Object>>> compare(
      @RequestParam String jobId,
      @RequestParam("v1") Integer version1,
      @RequestParam("v2") Integer version2) {
    return BaseResponse.success(jobHistoryService.compareVersions(jobId, version1, version2));
  }
}
