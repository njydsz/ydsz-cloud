package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.repository.JobTaskRepository;
import com.njydsz.cronjob.domain.vo.JobTaskVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.JobTaskMapper;

/**
 * MapReduce 子任务 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobTaskRepository} 接口，封装 JobTaskMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobTaskRepositoryImpl implements JobTaskRepository {

  private final JobTaskMapper jobTaskMapper;

  private final CronjobConverter converter;

  @Override
  public List<JobTaskVO> findByLogId(String logId) {
    return converter.jobTaskListToVO(jobTaskMapper.selectByLogId(logId));
  }

  @Override
  public List<JobTaskVO> findPendingByLogId(String logId) {
    return converter.jobTaskListToVO(jobTaskMapper.selectPendingByLogId(logId));
  }

  @Override
  public int countByLogIdAndStatus(String logId, String status) {
    return jobTaskMapper.countByLogIdAndStatus(logId, status);
  }

  @Override
  public int updateStatus(
      String taskId,
      String status,
      String resultJson,
      String errorMessage,
      LocalDateTime updatedAt) {
    return jobTaskMapper.updateStatus(taskId, status, resultJson, errorMessage, updatedAt);
  }

  @Override
  public int updateExecNodeId(String taskId, String nodeId, LocalDateTime updatedAt) {
    return jobTaskMapper.updateExecNodeId(taskId, nodeId, updatedAt);
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobTaskMapper.cleanExpiredLogs(before, limit);
  }

  // ===== Web 层查询方法实现 =====

  @Override
  public JobRepository.PageResult<JobTaskVO> pageByLogId(String logId, int page, int size) {
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.njydsz.cronjob.domain.entity.job.JobTask> pageObj =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.njydsz.cronjob.domain.entity.job.JobTask> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper
        .eq(com.njydsz.cronjob.domain.entity.job.JobTask::getLogId, logId)
        .eq(com.njydsz.cronjob.domain.entity.job.JobTask::getDeleted, 0)
        .orderByAsc(com.njydsz.cronjob.domain.entity.job.JobTask::getCreatedAt);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.njydsz.cronjob.domain.entity.job.JobTask> result =
        jobTaskMapper.selectPage(pageObj, wrapper);
    return new JobRepository.PageResult<>(converter.jobTaskListToVO(result.getRecords()), result.getTotal());
  }

  @Override
  public int countByLogId(String logId) {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.njydsz.cronjob.domain.entity.job.JobTask> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper
        .eq(com.njydsz.cronjob.domain.entity.job.JobTask::getLogId, logId)
        .eq(com.njydsz.cronjob.domain.entity.job.JobTask::getDeleted, 0);
    Long count = jobTaskMapper.selectCount(wrapper);
    return count != null ? count.intValue() : 0;
  }
}
