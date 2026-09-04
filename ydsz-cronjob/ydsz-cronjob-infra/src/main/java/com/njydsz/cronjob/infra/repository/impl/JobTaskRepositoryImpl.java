package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.entity.job.JobTask;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.repository.JobTaskRepository;
import com.njydsz.cronjob.domain.vo.JobTaskVO;
import com.njydsz.cronjob.infra.mapper.job.JobTaskMapper;

/**
 * MapReduce 子任务 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 26.09.01
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

  @Override
  public JobRepository.PageResult<JobTaskVO> pageByLogId(String logId, int page, int size) {
    Page<JobTask> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<JobTask> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(JobTask::getLogId, logId).eq(JobTask::getDeleted, 0).orderByAsc(JobTask::getCreatedAt);
    Page<JobTask> result = jobTaskMapper.selectPage(pageObj, wrapper);
    return new JobRepository.PageResult<>(converter.jobTaskListToVO(result.getRecords()), result.getTotal());
  }

  @Override
  public int countByLogId(String logId) {
    LambdaQueryWrapper<JobTask> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(JobTask::getLogId, logId).eq(JobTask::getDeleted, 0);
    return jobTaskMapper.selectCount(wrapper).intValue();
  }

  @Override
  public void insert(JobTaskVO task) {
    jobTaskMapper.insert(converter.voToEntity(task));
  }

  @Override
  public int updateById(JobTaskVO task) {
    return jobTaskMapper.updateById(converter.voToEntity(task));
  }

  @Override
  public JobTaskVO findById(String id) {
    return converter.entityToVO(jobTaskMapper.selectById(id));
  }
}
