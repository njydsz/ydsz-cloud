package com.njydsz.workflow.server.idempotent;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.domain.repository.FlowIdempotentRepository;

/**
 * 幂等记录清理定时任务。
 *
 * <p>每天凌晨清理已过期的 SUCCESS 状态幂等记录（ttl_at &lt; now），
 * 防止 ydsz_flow_idempotent 表无限增长。
 *
 * <p><b>设计说明：</b>仅清理 SUCCESS 状态记录。PROCESSING 和 FAILED 状态保留供排查，
 * 由运维人员按需手动清理（超时未更新的 PROCESSING 记录为异常状态）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentCleanupTask {

  private final FlowIdempotentRepository idempotentRepository;

  /**
   * 每天 03:00 清理过期幂等记录。
   *
   * <p>Cron: 秒 分 时 日 月 周（Spring 6 字段格式）。
   */
  @Scheduled(cron = "0 0 3 * * ?")
  public void purgeExpired() {
    try {
      LocalDateTime now = LocalDateTime.now();
      int purged = idempotentRepository.purgeExpired(now);
      if (purged > 0) {
        log.info("[IdempotentCleanup] 清理幂等过期记录: count={}", purged);
      }
    } catch (Exception e) {
      log.warn("[IdempotentCleanup] 幂等清理任务异常: {}", e.getMessage());
    }
  }
}
