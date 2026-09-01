package com.njydsz.cronjob.server.service.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.cronjob.domain.dto.BatchResultDTO;
import com.njydsz.cronjob.domain.dto.job.JobClusterMigrationDTO;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.cluster.ClusterMigrationClient;
import com.njydsz.cronjob.server.service.job.JobService;

/**
 * 集群漂移服务实现（P2-5）。
 *
 * <p>实现 {@link ClusterMigrationService} 接口，负责将任务从当前集群迁移到目标集群。
 *
 * <h3>迁移流程</h3>
 *
 * <ol>
 *   <li>校验目标集群配置存在且可达
 *   <li>逐任务：读取任务完整信息 → 注销本机调度器 → 调用目标集群注册 → 更新 DB cluster 字段
 *   <li>单条失败不影响其他任务，失败任务保持原状态
 * </ol>
 *
 * <h3>回滚策略</h3>
 *
 * <p>若远程注册失败，任务仍保留在当前集群（调度器已注销但 DB cluster 未更新），
 * 下次集群启动时会自动加载 cluster 字段不匹配的任务。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterMigrationServiceImpl implements ClusterMigrationService {

  private final JobRepository jobRepository;
  private final JobService jobService;
  private final CronjobProperties cronjobProperties;
  private final ClusterMigrationClient migrationClient;

  @Override
  public BatchResultDTO<String> migrateToCluster(JobClusterMigrationDTO dto) {
    // 1. 校验目标集群配置
    String targetCluster = dto.getTargetCluster();
    if (cronjobProperties.getMultiCluster() == null
        || cronjobProperties.getMultiCluster().getClusters() == null
        || !cronjobProperties.getMultiCluster().getClusters().containsKey(targetCluster)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("目标集群未配置: " + targetCluster)
          .build();
    }

    // 2. 校验目标集群可达性
    if (!migrationClient.checkReachable(targetCluster)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.SERVICE_UNAVAILABLE)
          .message("目标集群不可达: " + targetCluster)
          .build();
    }

    // 3. 逐任务执行漂移
    List<String> jobIds = dto.getJobIds();
    List<BatchResultDTO.ItemResult<String>> details = new ArrayList<>(jobIds.size());
    int success = 0;

    for (String jobId : jobIds) {
      try {
        migrateSingleJob(jobId, targetCluster);
        details.add(BatchResultDTO.ItemResult.success(jobId));
        success++;
      } catch (Exception e) {
        log.warn(
            "[ClusterMigration] 任务漂移失败: jobId={} target={} reason={}",
            jobId,
            targetCluster,
            e.getMessage());
        details.add(BatchResultDTO.ItemResult.failure(jobId, e.getMessage()));
      }
    }

    log.info(
        "[ClusterMigration] 集群漂移完成: total={} success={} target={}",
        jobIds.size(),
        success,
        targetCluster);
    return new BatchResultDTO<>(jobIds.size(), success, jobIds.size() - success, details);
  }

  @Override
  public List<String> listAvailableClusters() {
    if (cronjobProperties.getMultiCluster() == null
        || cronjobProperties.getMultiCluster().getClusters() == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(cronjobProperties.getMultiCluster().getClusters().keySet());
  }

  /**
   * 漂移单个任务到目标集群。
   *
   * <p>迁移步骤：读取任务 → 注销本机调度器 → 远程注册 → 更新 DB cluster。
   * 远程注册失败时抛出异常，由调用方决定是否继续。
   *
   * @param jobId 任务 ID
   * @param targetCluster 目标集群名称
   */
  private void migrateSingleJob(String jobId, String targetCluster) {
    JobVO job =
        jobRepository
            .findById(jobId)
            .orElseThrow(
                () ->
                    SysException.builder()
                        .resultCode(YdszResultCode.NOT_FOUND)
                        .message("任务不存在: " + jobId)
                        .build());

    // 1. 注销本机调度器（停止在当前集群的调度）
    jobService.unregister(job.getJobKey());

    // 2. 调用目标集群注册任务
    String requestBody = YdszJson.toJson(job);
    boolean registered = migrationClient.registerJob(targetCluster, requestBody);
    if (!registered) {
      throw SysException.builder()
          .resultCode(YdszResultCode.SERVICE_UNAVAILABLE)
          .message("目标集群注册失败: " + targetCluster)
          .build();
    }

    // 3. 更新 DB cluster 字段（标记任务已迁移到目标集群）
    job.setCluster(targetCluster);
    jobRepository.updateById(job);

    log.info(
        "[ClusterMigration] 任务漂移成功: jobId={} jobKey={} target={}",
        jobId,
        job.getJobKey(),
        targetCluster);
  }
}
