package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.infra.entity.job.Job;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;

/**
 * 任务定义 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobRepository} 接口，封装 JobMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobRepositoryImpl implements JobRepository {

  private final JobMapper jobMapper;

  private final CronjobConverter converter;

  @Override
  public Optional<JobVO> findByJobKey(String jobKey) {
    return Optional.ofNullable(jobMapper.selectByJobKey(jobKey)).map(converter::entityToVO);
  }

  @Override
  public List<JobVO> findAllNormal() {
    return converter.jobListToVO(jobMapper.selectAllNormal());
  }

  @Override
  public List<JobVO> findDueJobs(LocalDateTime now, int limit) {
    return converter.jobListToVO(jobMapper.selectDueJobs(now, limit));
  }

  @Override
  public List<JobVO> findDueJobsInWindow(LocalDateTime now, LocalDateTime windowEnd, int limit) {
    return converter.jobListToVO(jobMapper.selectDueJobsInWindow(now, windowEnd, limit));
  }

  @Override
  public int advanceNextFireTime(
      String id,
      LocalDateTime oldNextFireTime,
      LocalDateTime newNextFireTime,
      LocalDateTime lastFireTime) {
    return jobMapper.advanceNextFireTime(id, oldNextFireTime, newNextFireTime, lastFireTime);
  }

  @Override
  public int updateStats(
      String id,
      LocalDateTime lastFireTime,
      LocalDateTime nextFireTime,
      Long fireCount,
      Long successCount,
      Long failCount,
      String status) {
    return jobMapper.updateStats(id, lastFireTime, nextFireTime, fireCount, successCount, failCount, status);
  }

  @Override
  public int resetConsecutiveFail(String id) {
    return jobMapper.resetConsecutiveFail(id);
  }

  @Override
  public int incrementConsecutiveFail(String id) {
    return jobMapper.incrementConsecutiveFail(id);
  }

  @Override
  public int markAutoPaused(String id) {
    return jobMapper.markAutoPaused(id);
  }

  @Override
  public Optional<Integer> findConsecutiveFailCount(String id) {
    return Optional.ofNullable(jobMapper.selectConsecutiveFailCount(id));
  }

  @Override
  public List<JobVO> findAutoResumeCandidates(LocalDateTime now) {
    return converter.jobListToVO(jobMapper.selectAutoResumeCandidates(now));
  }

  @Override
  public int resumeAutoPaused(String id) {
    return jobMapper.resumeAutoPaused(id);
  }

  // ===== Web 层查询方法实现（Controller 停止 Mapper 直注） =====

  @Override
  public PageResult<JobVO> pageByGroup(String jobGroup, int page, int size) {
    // 使用 MyBatis-Plus 分页插件
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Job> pageObj =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(Job::getJobGroup, jobGroup).eq(Job::getDeleted, 0).orderByDesc(Job::getCreatedAt);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Job> result =
        jobMapper.selectPage(pageObj, wrapper);
    return new PageResult<>(converter.jobListToVO(result.getRecords()), result.getTotal());
  }

  @Override
  public List<JobVO> findByGroupAndStatus(String jobGroup, String status) {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(Job::getJobGroup, jobGroup).eq(Job::getDeleted, 0);
    if (status != null) {
      wrapper.eq(Job::getStatus, status);
    }
    wrapper.orderByDesc(Job::getCreatedAt);
    return converter.jobListToVO(jobMapper.selectList(wrapper));
  }

  @Override
  public List<String> listDistinctGroups() {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(Job::getDeleted, 0).select(Job::getJobGroup);
    return jobMapper.selectList(wrapper).stream()
        .map(Job::getJobGroup)
        .filter(g -> g != null && !g.isBlank())
        .distinct()
        .sorted()
        .toList();
  }

  @Override
  public long countByGroup(String jobGroup) {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(Job::getJobGroup, jobGroup).eq(Job::getDeleted, 0);
    return jobMapper.selectCount(wrapper);
  }

  @Override
  public long countByStatus(String status) {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(Job::getStatus, status).eq(Job::getDeleted, 0);
    return jobMapper.selectCount(wrapper);
  }

  @Override
  public long countAll() {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(Job::getDeleted, 0);
    return jobMapper.selectCount(wrapper);
  }

  // ===== 实体 CRUD 实现（JobServiceImpl / JobHistoryServiceImpl 使用） =====

  @Override
  public Job selectById(String id) {
    return jobMapper.selectById(id);
  }

  @Override
  public int insert(Job job) {
    return jobMapper.insert(job);
  }

  @Override
  public int updateById(Job job) {
    return jobMapper.updateById(job);
  }

  @Override
  public int deleteById(String id) {
    return jobMapper.deleteById(id);
  }

  @Override
  public List<Job> selectAllNormal() {
    return jobMapper.selectAllNormal();
  }

  @Override
  public Job selectByJobKey(String jobKey) {
    return jobMapper.selectByJobKey(jobKey);
  }

  @Override
  public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Job> selectPage(
      com.baomidou.mybatisplus.extension.plugins.pagination.Page<Job> page,
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job> wrapper) {
    return jobMapper.selectPage(page, wrapper);
  }

  @Override
  public long selectCount(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job> wrapper) {
    return jobMapper.selectCount(wrapper);
  }
}
