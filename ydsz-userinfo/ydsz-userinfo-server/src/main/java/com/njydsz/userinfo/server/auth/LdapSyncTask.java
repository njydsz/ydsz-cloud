package com.njydsz.userinfo.server.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.server.auth.LdapOrgSyncService.SyncResult;

/**
 * LDAP/AD 组织架构同步定时任务。
 *
 * <p>通过 {@link Scheduled} 注解按配置的 cron 表达式定期触发 {@link LdapOrgSyncService#syncAll()}，
 * 实现部门与用户的自动同步。
 *
 * <p><b>启用条件：</b>{@code ydsz.userinfo.ldap.sync.enabled=true}。
 *
 * <p><b>cron 表达式：</b>通过 {@code ydsz.userinfo.ldap.sync.cron} 配置，默认每天凌晨 2 点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.userinfo.ldap.sync", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LdapSyncTask {

  private final LdapOrgSyncService ldapOrgSyncService;

  /**
   * 定时执行 LDAP 组织架构同步。
   *
   * <p>cron 表达式从配置中读取，默认每天凌晨 2 点执行。同步过程由 {@link LdapOrgSyncService} 负责获取分布式锁，
   * 防止并发执行。
   */
  @Scheduled(cron = "${ydsz.userinfo.ldap.sync.cron:0 0 2 * * ?}")
  public void syncLdapOrg() {
    log.info("Scheduled LDAP sync task started");
    try {
      SyncResult result = ldapOrgSyncService.syncAll();
      log.info(
          "Scheduled LDAP sync task completed: total={}, created={}, updated={}, deactivated={}, failed={}",
          result.totalProcessed(),
          result.created(),
          result.updated(),
          result.deactivated(),
          result.failed());
    } catch (Exception e) {
      log.error("Scheduled LDAP sync task failed: {}", e.getMessage(), e);
    }
  }
}
