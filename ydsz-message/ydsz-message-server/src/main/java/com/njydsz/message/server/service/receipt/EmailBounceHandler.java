package com.njydsz.message.server.service.receipt;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 邮件退信处理器。
 *
 * <p>承担两项职责：
 *
 * <ol>
 *   <li><b>退信回调处理</b>：处理 SMTP 退信事件，更新消息日志状态为 FAILED 并记录退信原因
 *   <li><b>退信黑名单追踪</b>：通过 Redis 维护退信邮箱黑名单（90 天 TTL），供发送前校验跳过已知无效邮箱
 * </ol>
 *
 * <p>硬退信（HARD）表示邮箱永久不可达，建议后续联动用户通道绑定状态更新。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailBounceHandler {

  private final MsgLogRepository msgLogRepository;

  private final RedisStringOps redisStringOps;

  /** 退信黑名单 Redis Key 前缀 */
  private static final String BOUNCE_KEY_PREFIX = "email:bounce:";

  /** 退信黑名单 TTL（天） */
  private static final long BOUNCE_TTL_DAYS = 90L;

  /**
   * 处理邮件退信回调。
   *
   * <p>根据退信类型更新消息日志状态，硬退信时同步加入黑名单。
   *
   * @param logId 消息日志 ID
   * @param bounceType 退信类型: HARD(硬退信,邮箱不存在) / SOFT(软退信,临时失败)
   * @param reason 退信原因
   * @param recipient 退信收件人
   */
  public void handleBounce(String logId, String bounceType, String reason, String recipient) {
    if (!StringUtils.hasText(logId)) {
      log.warn("[EmailBounce] logId 为空,跳过处理");
      return;
    }
    String fullReason = (StringUtils.hasText(bounceType) ? "[" + bounceType + "] " : "") + reason;
    Optional<MsgLogVO> voOpt = msgLogRepository.findById(logId);
    if (voOpt.isPresent()) {
      MsgLogVO vo = voOpt.get();
      vo.setStatus("FAILED");
      vo.setReceiptStatus("FAILED");
      vo.setReceiptAt(LocalDateTime.now());
      vo.setErrorMessage(fullReason);
      msgLogRepository.update(vo);
    }
    log.info(
        "[EmailBounce] 退信处理: logId={} type={} recipient={} reason={}",
        logId,
        bounceType,
        recipient,
        reason);

    // 硬退信时加入黑名单，阻止后续继续发送
    if ("HARD".equalsIgnoreCase(bounceType) && StringUtils.hasText(recipient)) {
      recordBounce(recipient, fullReason);
    }
  }

  /**
   * 记录邮箱退信到 Redis 黑名单。
   *
   * @param email 退信邮箱
   * @param bounceReason 退信原因
   */
  public void recordBounce(String email, String bounceReason) {
    if (!StringUtils.hasText(email)) {
      return;
    }
    String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
    redisStringOps.set(
        key, bounceReason != null ? bounceReason : "unknown", Duration.ofDays(BOUNCE_TTL_DAYS));
    log.warn("[Bounce] 邮件退信已记录: email={} reason={}", email, bounceReason);
  }

  /**
   * 检查邮箱是否在退信黑名单中。
   *
   * @param email 邮箱地址
   * @return true 表示在黑名单中，应跳过发送
   */
  public boolean isBounced(String email) {
    if (!StringUtils.hasText(email)) {
      return false;
    }
    String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
    return Boolean.TRUE.equals(redisStringOps.hasKey(key));
  }

  /**
   * 从退信黑名单中移除（用户更新邮箱后可手动清除）。
   *
   * @param email 邮箱地址
   */
  public void removeFromBounceList(String email) {
    String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
    redisStringOps.del(key);
    log.info("[Bounce] 邮箱已从退信黑名单移除: email={}", email);
  }

  /**
   * 获取退信原因。
   *
   * @param email 邮箱地址
   * @return 退信原因，null 表示不在黑名单中
   */
  public String getBounceReason(String email) {
    if (!StringUtils.hasText(email)) {
      return null;
    }
    String key = BOUNCE_KEY_PREFIX + email.toLowerCase().trim();
    return redisStringOps.get(key, String.class);
  }
}
