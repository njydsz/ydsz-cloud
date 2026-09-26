package com.njydsz.cronjob.server.service.impl.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.LeaderConfig;
import com.njydsz.cronjob.server.core.JobLockManager;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;
import com.njydsz.cronjob.server.core.scheduler.NextFireTimeCalculator;
import com.njydsz.cronjob.server.core.scheduler.ScheduleType;
import com.njydsz.cronjob.server.service.job.JobHistoryService;

/**
 * JobServiceImpl 单元测试（纯 Mockito 模式，MockitoAnnotations.openMocks 启动）。
 *
 * <p>覆盖任务调度核心分支：
 *
 * <ul>
 *   <li>新增 job — 验证 taskScheduler.schedule 注册 CronTrigger</li>
 *   <li>cron 表达式非法 — 抛出 SysException</li>
 *   <li>暂停/恢复 — 状态变更 NORMAL ↔ PAUSED 验证</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
class JobServiceImplTest {

  @Mock private JobRepository jobRepository;
  @Mock private JobLogRepository jobLogRepository;
  @Mock private ApplicationContext applicationContext;
  @Mock private CronjobProperties cronjobProperties;
  @Mock private JobLockManager jobLockManager;
  @Mock private NextFireTimeCalculator nextFireTimeCalculator;
  @Mock private ObjectProvider<TaskDispatcher> taskDispatcherProvider;
  @Mock private TenantQuotaService tenantQuotaService;
  @Mock private ObjectProvider<JobHistoryService> jobHistoryServiceProvider;
  @Mock private ObjectProvider<SearchIndexEventBridge> searchIndexEventBridgeProvider;
  @Mock private TaskScheduler taskScheduler;
  @Mock private ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  @InjectMocks private JobServiceImpl jobService;

  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    // 默认非 Leader 模式
    LeaderConfig leaderConfig = new LeaderConfig();
    leaderConfig.setEnabled(false);
    lenient().when(cronjobProperties.getLeader()).thenReturn(leaderConfig);
    lenient().when(cronjobProperties.normalizeTtl(any())).thenReturn(java.time.Duration.ofMinutes(5));
    // 默认 ObjectProvider 返回 null（降级路径）
    lenient().when(taskDispatcherProvider.getIfAvailable()).thenReturn(null);
    lenient().when(jobHistoryServiceProvider.getIfAvailable()).thenReturn(null);
    lenient().when(searchIndexEventBridgeProvider.getIfAvailable()).thenReturn(null);
    lenient().when(eventPublisherProvider.getIfAvailable()).thenReturn(null);
    lenient().doNothing().when(tenantQuotaService).checkJobQuota(anyString());
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  /** 构建合法 CRON job DTO */
  private JobPostDTO buildCronJobDto() {
    JobPostDTO dto = new JobPostDTO();
    dto.setJobName("测试任务");
    dto.setJobKey("test_job_001");
    dto.setHandler("testHandler");
    dto.setCronExpression("0 0/5 * * * ?");
    dto.setScheduleType(ScheduleType.CRON.name());
    dto.setStatus("NORMAL");
    return dto;
  }

  /** 构建用于暂停/恢复测试的 JobVO */
  private JobVO buildJobVo(String id, String status) {
    JobVO vo = new JobVO();
    vo.setId(id);
    vo.setJobKey("test_job_001");
    vo.setJobName("测试任务");
    vo.setJobGroup("DEFAULT");
    vo.setHandler("testHandler");
    vo.setCronExpression("0 0/5 * * * ?");
    vo.setScheduleType(ScheduleType.CRON.name());
    vo.setStatus(status);
    vo.setShardTotal(1);
    vo.setMisfirePolicy("FIRE_NOW");
    vo.setTenantId("tenant-1");
    vo.setCreatedBy("admin");
    vo.setUpdatedBy("admin");
    return vo;
  }

  @Nested
  @DisplayName("create — 新增任务")
  class Create {

    @Test
    @DisplayName("合法 CRON 任务应注册到调度器并返回 job ID")
    void shouldRegisterSchedulerWhenCreateValidCronJob() {
      JobPostDTO dto = buildCronJobDto();
      // 无重名
      when(jobRepository.findByJobKey("test_job_001")).thenReturn(Optional.empty());
      // repo.insert 返回新 ID
      when(jobRepository.insert(any(JobPostDTO.class))).thenReturn("job-new-123");
      // 回读 DB
      JobVO freshVo = buildJobVo("job-new-123", "NORMAL");
      when(jobRepository.findById("job-new-123")).thenReturn(Optional.of(freshVo));
      // nextFireTime
      when(nextFireTimeCalculator.calculate(any(JobVO.class)))
          .thenReturn(LocalDateTime.now().plusMinutes(5));
      // taskScheduler 返回 mock future
      ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
      when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
          .thenReturn(mockFuture);

      String result = jobService.create(dto);

      assertThat(result).isEqualTo("job-new-123");
      verify(jobRepository).insert(any(JobPostDTO.class));
      verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    @DisplayName("jobKey 已存在时应抛出 SysException")
    void shouldThrowWhenJobKeyExists() {
      JobPostDTO dto = buildCronJobDto();
      JobVO existing = buildJobVo("existing-id", "NORMAL");
      when(jobRepository.findByJobKey("test_job_001")).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> jobService.create(dto))
          .isInstanceOf(SysException.class);

      verify(jobRepository, never()).insert(any(JobPostDTO.class));
    }
  }

  @Nested
  @DisplayName("pause / resume — 暂停恢复")
  class PauseResume {

    @Test
    @DisplayName("暂停 NORMAL 任务应将状态变更为 PAUSED")
    void shouldChangeStatusToPaused() {
      JobVO vo = buildJobVo("job-1", "NORMAL");
      when(jobRepository.findById("job-1")).thenReturn(Optional.of(vo));

      jobService.pause("job-1");

      verify(jobRepository).updateById(ArgumentMatchers.argThat(j -> "PAUSED".equals(j.getStatus())));
    }

    @Test
    @DisplayName("恢复 PAUSED 任务应将状态变更为 NORMAL")
    void shouldChangeStatusToNormalOnResume() {
      JobVO vo = buildJobVo("job-1", "PAUSED");
      when(jobRepository.findById("job-1")).thenReturn(Optional.of(vo));
      when(nextFireTimeCalculator.calculate(any(JobVO.class)))
          .thenReturn(LocalDateTime.now().plusMinutes(5));
      ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
      when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
          .thenReturn(mockFuture);

      jobService.resume("job-1");

      verify(jobRepository).updateById(ArgumentMatchers.argThat(j -> "NORMAL".equals(j.getStatus())));
    }

    @Test
    @DisplayName("暂停非 NORMAL 状态任务时应抛出 SysException")
    void shouldThrowWhenPauseNonNormalJob() {
      JobVO vo = buildJobVo("job-1", "PAUSED");
      when(jobRepository.findById("job-1")).thenReturn(Optional.of(vo));

      assertThatThrownBy(() -> jobService.pause("job-1"))
          .isInstanceOf(SysException.class);

      verify(jobRepository, never()).updateById(any(JobVO.class));
    }

    @Test
    @DisplayName("暂停不存在的任务时应抛出 SysException")
    void shouldThrowWhenPauseNonExistentJob() {
      when(jobRepository.findById("ghost-job")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> jobService.pause("ghost-job"))
          .isInstanceOf(SysException.class);
    }
  }
}
