package com.njydsz.userinfo.server.provision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.provision.IdentityProvisionConnector;
import com.njydsz.userinfo.domain.provision.ProvisionResult;

/**
 * 身份供给调度器（P0-1 Identity Provisioning 管道）。
 *
 * <p>按配置周期触发所有已注册连接器的同步执行，获取分布式锁防止并发执行，
 * 增量同步令牌持久化到 Redis 供下次使用。
 *
 * <p><b>调度逻辑：</b>
 *
 * <ol>
 *   <li>获取分布式锁（防止多实例并发执行）</li>
 *   <li>遍历所有可用（{@link IdentityProvisionConnector#isAvailable()} = true）的连接器</li>
 *   <li>读取上次增量令牌（Redis key: {@code userinfo:provision:sync-token:{type}}）</li>
 *   <li>执行增量同步，保存新令牌</li>
 *   <li>锁释放，结果日志输出</li>
 * </ol>
 *
 * <p><b>注意：</b>LDAP 的完整组织架构同步仍由 {@code LdapSyncTask} 独立执行。
 * 本调度器专门处理非 LDAP 连接器（JDBC/SCIM 等）的用户供给。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
@ConditionalOnBean(IdentityProvisionConnector.class)
@RequiredArgsConstructor
public class ProvisionScheduler {

  /** 同步锁 key */
  private static final String LOCK_KEY = "ydsz:userinfo:provision:lock";

  /** 增量令牌 Redis key 前缀 */
  private static final String TOKEN_KEY_PREFIX = "userinfo:provision:sync-token:";

  /** 令牌 Redis 过期时间（秒）：7 天 */
  private static final long TOKEN_TTL_SECONDS = 604800L;

  /** 锁租约时间（分钟） */
  private static final long LOCK_LEASE_MINUTES = 30;

  private final ProvisionConnectorRegistry registry;
  private final ProvisionOrchestrator orchestrator;
  private final DistributedLocker distributedLocker;
  private final RedisStringOps redisStringOps;

  /**
   * 定时同步任务。
   *
   * <p>默认每小时执行一次（cron 可通过 ydsz.userinfo.provision.schedule.cron 配置）。
   */
  @Scheduled(cron = "${ydsz.userinfo.provision.schedule.cron:0 0 * * * ?}")
  public void scheduledProvisioning() {
    String lockValue = distributedLocker.tryLock(LOCK_KEY, LOCK_LEASE_MINUTES, TimeUnit.MINUTES);
    if (lockValue == null) {
      log.warn("Provision scheduling skipped: unable to acquire distributed lock");
      return;
    }

    try {
      log.info("Provision scheduling started");
      List<ProvisionResult> results = executeAllConnectors();
      log.info("Provision scheduling completed: connectors={}", results.size());
    } catch (Exception e) {
      log.error("Provision scheduling failed: error={}", e.getMessage(), e);
    } finally {
      distributedLocker.unlock(LOCK_KEY, lockValue);
    }
  }

  /**
   * 执行所有可用连接器的同步。
   *
   * @return 各连接器同步结果列表
   */
  private List<ProvisionResult> executeAllConnectors() {
    List<ProvisionResult> results = new ArrayList<>(registry.size());

    for (IdentityProvisionConnector connector : registry.getAllConnectors()) {
      String type = connector.getConnectorType();
      if (!connector.isAvailable()) {
        log.info("Provision connector 不可用，跳过: type={}", type);
        continue;
      }

      try {
        // 读取上次增量令牌
        String lastToken = getSyncToken(type);
        // 执行增量同步
        ProvisionResult result = orchestrator.executeSync(connector, true, lastToken);
        // 保存新令牌
        if (result.syncToken() != null) {
          saveSyncToken(type, result.syncToken());
        }
        results.add(result);

        if (result.hasErrors()) {
          log.warn("Provision 同步存在错误: type={}, failed={}, errors={}",
              type, result.failed(), result.errors().size());
        }
      } catch (Exception e) {
        log.error("Provision 同步异常: type={}, error={}", type, e.getMessage(), e);
      }
    }

    return results;
  }

  /**
   * 读取上次增量同步令牌。
   *
   * @param connectorType 连接器类型
   * @return 同步令牌，未找到返回 null
   */
  private String getSyncToken(String connectorType) {
    try {
      return redisStringOps.get(TOKEN_KEY_PREFIX + connectorType, String.class);
    } catch (Exception e) {
      log.warn("读取增量令牌失败: type={}, error={}", connectorType, e.getMessage());
      return null;
    }
  }

  /**
   * 保存增量同步令牌。
   *
   * @param connectorType 连接器类型
   * @param token 同步令牌
   */
  private void saveSyncToken(String connectorType, String token) {
    try {
      redisStringOps.set(TOKEN_KEY_PREFIX + connectorType, token, TOKEN_TTL_SECONDS);
    } catch (Exception e) {
      log.warn("保存增量令牌失败: type={}, error={}", connectorType, e.getMessage());
    }
  }
}
