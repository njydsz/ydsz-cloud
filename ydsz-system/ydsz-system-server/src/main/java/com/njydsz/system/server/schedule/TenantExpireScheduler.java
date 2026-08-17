package com.njydsz.system.server.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.system.infra.mapper.TenantMapper;

/**
 * 租户到期自动锁定调度任务（P1-3 多租户能力补课）。
 *
 * <p>周期性扫描 {@code ydsz_tenant} 中已到期（{@code expire_at < now}）且仍处于 {@code ENABLED}
 * 状态的租户，统一置为 {@code DISABLED}，实现「订阅到期 → 自动降级锁定」的生命周期闭环，避免过期租户
 * 继续占用资源。
 *
 * <p><b>执行策略：</b>
 *
 * <ul>
 *   <li>使用 {@code TenantMapper#disableExpiredTenants} 单条原子 UPDATE 完成批量停用，无 N+1
 *   <li>间隔可通过 {@code ydzs.system.tenant.expire-check-interval-ms} 配置（默认 30 分钟）
 *   <li>幂等：已停用租户不再命中 {@code status=ENABLED} 条件
 * </ul>
 *
 * <p><b>多租户视角：</b>租户管理是平台级超级管理员能力，原生 UPDATE 不注入 {@code tenant_id} 过滤，
 * 保证全量扫描。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see com.njydsz.system.infra.mapper.TenantMapper 租户 Mapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpireScheduler {

  /** 租户 Mapper（原子停用已到期租户） */
  private final TenantMapper tenantMapper;

  /**
   * 扫描并停用已到期租户。
   *
   * <p>默认每 30 分钟执行一次（启动 60s 后首次执行），扫描成本为单条索引 UPDATE。
   */
  @Scheduled(
      fixedDelayString = "${ydsz.system.tenant.expire-check-interval-ms:1800000}",
      initialDelayString = "${ydsz.system.tenant.expire-check-initial-delay-ms:60000}")
  public void disableExpiredTenants() {
    int affected;
    try {
      affected = tenantMapper.disableExpiredTenants();
    } catch (Exception e) {
      log.error("[TenantExpireScheduler] 停用到期租户失败", e);
      return;
    }
    if (affected > 0) {
      log.warn("[TenantExpireScheduler] 已自动停用 {} 个到期租户", affected);
    }
  }
}
