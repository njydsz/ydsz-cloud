package com.njydsz.cronjob.server.service.impl.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;

import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.JobLockManager;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;
import com.njydsz.cronjob.server.core.scheduler.NextFireTimeCalculator;
import com.njydsz.cronjob.server.service.job.JobHistoryService;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

  @InjectMocks
  private JobServiceImpl jobService;

  @Mock
  private JobRepository jobRepository;

  @Mock
  private JobLogRepository jobLogRepository;

  @Mock
  private ApplicationContext applicationContext;

  @Mock
  private CronjobProperties cronjobProperties;

  @Mock
  private JobLockManager jobLockManager;

  @Mock
  private NextFireTimeCalculator nextFireTimeCalculator;

  @Mock
  private ObjectProvider<TaskDispatcher> taskDispatcherProvider;

  @Mock
  private com.njydsz.cronjob.server.service.job.TenantQuotaService tenantQuotaService;

  @Mock
  private ObjectProvider<JobHistoryService> jobHistoryServiceProvider;

  @Mock
  private ObjectProvider<SearchIndexEventBridge> searchIndexEventBridgeProvider;

  @Mock
  private TaskScheduler taskScheduler;

  @Mock
  private ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  @Test
  @DisplayName("暂停任务 - 正常状态NORMAL可暂停成功")
  void pause_normalStatus_success() {
    JobVO job = new JobVO();
    job.setId("job-001");
    job.setJobKey("test-job");
    job.setStatus("NORMAL");
    when(jobRepository.findById("job-001")).thenReturn(Optional.of(job));
    when(jobRepository.updateById(org.mockito.ArgumentMatchers.any(JobVO.class))).thenReturn(1);

    jobService.pause("job-001");

    verify(jobRepository).updateById(org.mockito.ArgumentMatchers.any(JobVO.class));
  }

  @Test
  @DisplayName("暂停任务 - 非NORMAL状态抛出异常")
  void pause_notNormalStatus_throwsException() {
    JobVO job = new JobVO();
    job.setId("job-002");
    job.setJobKey("test-job-02");
    job.setStatus("PAUSED");
    when(jobRepository.findById("job-002")).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> jobService.pause("job-002"))
        .isInstanceOf(SysException.class);
  }

  @Test
  @DisplayName("恢复任务 - PAUSED状态恢复为NORMAL成功")
  void resume_pausedStatus_success() {
    JobVO job = new JobVO();
    job.setId("job-003");
    job.setJobKey("test-job-03");
    job.setStatus("PAUSED");
    job.setScheduleType("API");
    when(jobRepository.findById("job-003")).thenReturn(Optional.of(job));
    when(jobRepository.updateById(org.mockito.ArgumentMatchers.any(JobVO.class))).thenReturn(1);

    jobService.resume("job-003");

    verify(jobRepository).updateById(org.mockito.ArgumentMatchers.any(JobVO.class));
  }
}
