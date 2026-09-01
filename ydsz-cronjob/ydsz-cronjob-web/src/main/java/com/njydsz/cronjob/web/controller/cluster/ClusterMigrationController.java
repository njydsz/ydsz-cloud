package com.njydsz.cronjob.web.controller.cluster;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.cronjob.domain.dto.BatchResultDTO;
import com.njydsz.cronjob.domain.dto.job.JobClusterMigrationDTO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.service.cluster.ClusterMigrationService;

/**
 * 集群漂移管理 Controller（P2-5）。
 *
 * <p>提供多云/多集群任务漂移能力：将任务从当前集群迁移到目标集群。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #migrate} — 触发任务漂移（源集群注销 → 目标集群注册 → DB 更新）
 *   <li>{@link #listClusters} — 查询可用目标集群列表
 * </ul>
 *
 * <h3>权限</h3>
 *
 * <p>需要 {@code CRONJOB_JOB_MIGRATE} 权限（管理员级别）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "集群漂移", description = "多云/多集群任务漂移管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/cronjob/cluster")
@RequiredArgsConstructor
@Validated
public class ClusterMigrationController {

  private final ClusterMigrationService clusterMigrationService;
  private final CronjobProperties cronjobProperties;

  /**
   * 将一批任务漂移到目标集群。
   *
   * <p>迁移流程：校验目标集群配置 → 校验可达性 → 逐任务注销本机调度器 → 调用目标集群注册 → 更新 DB。
   * 单条失败不影响其他任务。
   *
   * @param dto 漂移请求（含任务 ID 列表 + 目标集群名）
   * @return 批量操作结果
   */
  @Operation(summary = "任务集群漂移")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_MIGRATE)
  @Idempotent(key = "ydsz:cronjob:cluster:migrate:lock", ttlSeconds = 30)
  @Audit(
      module = "集群漂移",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'clusterMigrate'")
  @RateLimit(resource = "cronjob.cluster.migrate", threshold = 10)
  @PostMapping("/migrate")
  public YdszResponse<BatchResultDTO<String>> migrate(@RequestBody @Valid JobClusterMigrationDTO dto) {
    return YdszResponse.success(clusterMigrationService.migrateToCluster(dto));
  }

  /**
   * 查询可用的目标集群列表。
   *
   * @return 远程集群名称列表
   */
  @Operation(summary = "查询可用目标集群")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping("/list")
  public YdszResponse<List<String>> listClusters() {
    return YdszResponse.success(clusterMigrationService.listAvailableClusters());
  }

  /**
   * 查询集群漂移功能是否启用。
   *
   * @return true 表示已启用
   */
  @Operation(summary = "集群漂移开关状态")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping("/enabled")
  public YdszResponse<Boolean> enabled() {
    return YdszResponse.success(
        cronjobProperties.getMultiCluster() != null && cronjobProperties.getMultiCluster().isEnabled());
  }
}
