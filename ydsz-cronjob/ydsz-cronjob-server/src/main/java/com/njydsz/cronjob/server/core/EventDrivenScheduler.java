package com.njydsz.cronjob.server.core;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.service.job.JobService;

/**
 * 事件驱动调度器。
 *
 * <p>接收外部事件（如 MQ 消息）并触发对应的定时任务执行。 使用 DistributedLocker 进行消息去重，确保同一事件不会重复触发。
 *
 * <p><b>P0-10</b>：强制要求调用方传 {@code msgId}，无 msgId 时拒绝触发，避免去重失效导致任务重复执行。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDrivenScheduler {

  /** 事件去重锁 TTL（毫秒） */
  private static final long DEDUP_TTL_MS = TimeUnit.MINUTES.toMillis(30);

  /** 事件去重锁 key 前缀 */
  private static final String DEDUP_LOCK_PREFIX = "ydsz:job:event:dedup:";

  private final JobLockManager jobLockManager;
  private final JobRepository jobRepository;
  private final JobService jobService;

  /**
   * 通过事件触发任务执行。
   *
   * <p>使用 DistributedLocker（ydsz-common-lock）进行去重，同一 msgId 在 TTL 内不会重复触发。
   *
   * <p><b>P0-10</b>：msgId 为必填参数，为空时拒绝触发并返回 false，避免去重键退化为 {@code jobKey:timestamp} 导致
   * 同一事件重复触发。调用方应保证 msgId 全局唯一（如 MQ messageId、业务流水号）。
   *
   * @param jobKey 任务 Key（必填）
   * @param msgId 消息 ID（<b>必填</b>，用于去重，建议全局唯一）
   * @param payload 负载数据（可为 null）
   * @return true 表示触发成功，false 表示已去重、msgId 为空或触发失败
   */
  public boolean triggerByEvent(String jobKey, String msgId, String payload) {
    if (jobKey == null || jobKey.isBlank()) {
      log.warn("[EventScheduler] jobKey 为空, 跳过触发");
      return false;
    }
    // P0-10: 强制要求 msgId，避免去重失效
    if (msgId == null || msgId.isBlank()) {
      log.warn("[EventScheduler] msgId 为空, 拒绝触发（P0-10 强制要求）: jobKey={}", jobKey);
      return false;
    }

    String dedupLockKey = DEDUP_LOCK_PREFIX + msgId;
    String lockValue = jobLockManager.tryAcquireLock(dedupLockKey, DEDUP_TTL_MS);
    if (lockValue == null) {
      log.info("[EventScheduler] 事件已去重, 跳过触发: jobKey={} msgId={}", jobKey, msgId);
      return false;
    }
    // 注意：去重锁不在 finally 中释放，保留 TTL 窗口内的去重语义（与原始 SETNX + TTL 行为一致）

    try {
      Optional<JobVO> jobOpt = jobRepository.findByJobKey(jobKey);
      if (jobOpt.isEmpty()) {
        log.warn("[EventScheduler] jobKey 不存在: {}", jobKey);
        return false;
      }
      String logId = jobService.trigger(jobOpt.get().getId());
      log.info("[EventScheduler] 事件触发任务成功: jobKey={} msgId={} logId={}", jobKey, msgId, logId);
      return true;
    } catch (Exception e) {
      log.error(
          "[EventScheduler] 事件触发任务失败: jobKey={} msgId={} err={}", jobKey, msgId, e.getMessage(), e);
      return false;
    }
  }
}
