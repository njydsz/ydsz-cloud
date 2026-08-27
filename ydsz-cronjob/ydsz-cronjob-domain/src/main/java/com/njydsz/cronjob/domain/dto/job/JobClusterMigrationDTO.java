package com.njydsz.cronjob.domain.dto.job;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务集群漂移 DTO（P2-5）。
 *
 * <p>将一个或多个任务从当前集群迁移到目标集群。迁移流程：
 *
 * <ol>
 *   <li>校验目标集群可达性
 *   <li>逐任务：注销本机调度器 → 调用目标集群注册接口 → 更新 DB cluster 字段
 *   <li>单条失败不影响其他任务
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "任务集群漂移请求 DTO")
public class JobClusterMigrationDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @NotEmpty(message = "任务 ID 列表不能为空")
  @Size(max = 50, message = "单次漂移上限 50 条")
  @Schema(description = "待漂移的任务 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
  private List<String> jobIds;

  @NotBlank(message = "目标集群名称不能为空")
  @Schema(description = "目标集群名称（对应 multi-cluster.clusters 中的 key）", requiredMode = Schema.RequiredMode.REQUIRED)
  private String targetCluster;
}
