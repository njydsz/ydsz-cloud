package com.njydsz.cronjob.server.service.cluster;

import java.util.List;

import com.njydsz.cronjob.domain.dto.BatchResultDTO;
import com.njydsz.cronjob.domain.dto.job.JobClusterMigrationDTO;

/**
 * 集群漂移服务接口（P2-5）。
 *
 * <p>负责将任务从当前集群迁移到目标集群，包括调度器注销、远程注册、DB 更新。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ClusterMigrationService {

  /**
   * 将一批任务漂移到目标集群。
   *
   * <p>迁移流程：校验目标集群配置 → 校验可达性 → 逐任务执行漂移。
   * 单条失败不影响其他任务。
   *
   * @param dto 漂移请求参数（含任务 ID 列表 + 目标集群名）
   * @return 批量操作结果（含成功/失败明细）
   */
  BatchResultDTO<String> migrateToCluster(JobClusterMigrationDTO dto);

  /**
   * 查询已配置的远程集群名称列表。
   *
   * @return 远程集群名称列表
   */
  List<String> listAvailableClusters();
}
