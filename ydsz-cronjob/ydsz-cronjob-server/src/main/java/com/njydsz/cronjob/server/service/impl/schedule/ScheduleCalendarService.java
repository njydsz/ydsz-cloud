package com.njydsz.cronjob.server.service.impl.schedule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * 调度日历服务实现。
 *
 * <p>维护任务调度日历（节假日、工作日、自定义例外日），
 *
 * <p>用于定时任务的「跳过节假日」「仅工作日执行」策略，
 *
 * <p>支持租户级自定义。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleCalendarService {

  private final JobRepository jobRepository;

  /**
   * 预计算任务的未来触发时间列表。
   *
   * @param jobKey 任务 KEY
   * @param from 起始时间
   * @param maxCount 最多计算次数
   * @return 触发时间列表
   */
  public List<LocalDateTime> getUpcomingFireTimes(String jobKey, LocalDateTime from, int maxCount) {
    JobVO job = jobRepository.findByJobKey(jobKey).orElse(null);
    if (job == null || job.getCronExpression() == null || job.getCronExpression().isBlank()) {
      return List.of();
    }
    return computeUpcomingFireTimes(job.getCronExpression(), from, maxCount);
  }

  /**
   * 计算所有 CRON 任务在未来时间段内的触发时间。
   *
   * @param from 起始时间
   * @param hours 时间窗口（小时）
   * @param maxPerJob 每个任务最多计算次数
   * @return 触发时间列表（含 jobKey）
   */
  public List<ScheduleItem> getScheduleCalendar(LocalDateTime from, int hours, int maxPerJob) {
    List<JobVO> normalJobs = jobRepository.findAllNormal();
    LocalDateTime to = from.plusHours(hours);
    List<ScheduleItem> items = new ArrayList<>(16);
    for (JobVO job : normalJobs) {
      if (job.getCronExpression() == null || job.getCronExpression().isBlank()) {
        continue;
      }
      List<LocalDateTime> fireTimes =
          computeUpcomingFireTimes(job.getCronExpression(), from, maxPerJob);
      for (LocalDateTime fireTime : fireTimes) {
        if (fireTime.isAfter(to)) {
          break;
        }
        items.add(new ScheduleItem(job.getJobKey(), job.getJobName(), fireTime));
      }
    }
    items.sort((a, b) -> a.fireTime().compareTo(b.fireTime()));
    return items;
  }

  /** 计算 Cron 表达式的未来触发时间。 */
  private List<LocalDateTime> computeUpcomingFireTimes(
      String cron, LocalDateTime from, int maxCount) {
    List<LocalDateTime> result = new ArrayList<>(maxCount);
    try {
      CronExpression expr = CronExpression.parse(cron);
      LocalDateTime next = from;
      for (int i = 0; i < maxCount; i++) {
        next = expr.next(next);
        if (next == null) {
          break;
        }
        result.add(next);
      }
    } catch (Exception e) {
      log.warn("[CalendarService] 计算 fire times 失败: cron={} err={}", cron, e.getMessage());
    }
    return result;
  }

  /**
   * 调度日历项。
   *
   * @param jobKey 任务 KEY
   * @param jobName 任务名称
   * @param fireTime 计划触发时间
   */
  public record ScheduleItem(String jobKey, String jobName, LocalDateTime fireTime) {}
}
