package com.njydsz.nextwiki.domain.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.entity.ShareRecipient;
import com.njydsz.nextwiki.domain.enums.NextwikiEnums.ShareStatus;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.ShareLinkRepository;
import com.njydsz.nextwiki.domain.repository.ShareRecipientRepository;

/**
 * 分享链接领域服务。
 *
 * <p>管理分享链接的生命周期：创建、验证访问、撤销、到期提醒。
 *
 * <p><b>防暴力破解：</b>通过 Redis 记录连续失败次数，超过阈值临时锁定。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareLinkDomainService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final ShareLinkRepository shareLinkRepository;
  private final FileNodeRepository fileNodeRepository;
  private final ShareRecipientRepository shareRecipientRepository;
  private final RedisStringOps stringOps;

  private final BCryptPasswordEncoder passwordEncoder;

  /** 防暴力破解 Redis Key 前缀 */
  private static final String KEY_SHARE_FAIL = "nextwiki:share:fail:";

  /** 最大失败次数 */
  private static final int MAX_FAIL_COUNT = 5;

  /** 锁定时长（分钟） */
  private static final long LOCK_DURATION_MINUTES = 30;

  /**
   * 创建公开分享链接。
   *
   * @param fileNodeId    文件节点 ID
   * @param shareType     分享类型（view/download/edit）
   * @param password      访问密码（可为空）
   * @param expireTime    过期时间（可为空）
   * @param maxAccessCount 最大访问次数（可为空）
   * @param userId        创建者 ID
   * @return 分享链接实体
   */
  @Transactional(rollbackFor = Exception.class)
  public ShareLink createShare(
      String fileNodeId,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      String userId) {
    return createShare(fileNodeId, shareType, password, expireTime, maxAccessCount, null, null, userId);
  }

  /**
   * 创建分享链接（增强版，支持定向分享和自定义标题）。
   *
   * @param fileNodeId    文件节点 ID
   * @param shareType     分享类型（view/download/edit）
   * @param password      访问密码（可为空）
   * @param expireTime    过期时间（可为空）
   * @param maxAccessCount 最大访问次数（可为空）
   * @param targetUserIds 目标用户 ID 列表（定向分享，可为空）
   * @param title         分享标题（可为空）
   * @param userId        创建者 ID
   * @return 分享链接实体
   */
  @Transactional(rollbackFor = Exception.class)
  public ShareLink createShare(
      String fileNodeId,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      List<String> targetUserIds,
      String title,
      String userId) {
    FileNode fileNode = fileNodeRepository.findById(fileNodeId);
    if (fileNode == null) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND)
          .data("fileNodeId", fileNodeId);
    }

    // 生成分享码和提取码
    String shareCode = String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "");
    String extractCode = generateExtractCode();

    // 确定分享目标类型
    String shareTargetType =
        (targetUserIds != null && !targetUserIds.isEmpty()) ? "USER" : "PUBLIC";

    ShareLink shareLink =
        ShareLink.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
            .fileNodeId(fileNodeId)
            .shareCode(shareCode)
            .extractCode(extractCode)
            .shareType(shareType)
            .expireTime(expireTime)
            .maxAccessCount(maxAccessCount)
            .accessCount(0)
            .status(ShareStatus.ACTIVE.getCode())
            .password(
                password != null && !password.isEmpty() ? passwordEncoder.encode(password) : null)
            .shareTargetType(shareTargetType)
            .reminderSent(false)
            .title(title)
            .revision(0)
            .deleted(0)
            .build();

    shareLink.setCreatedBy(userId);
    shareLink.setUpdatedBy(userId);

    ShareLink saved = shareLinkRepository.save(shareLink);

    // 处理定向分享目标用户
    if (targetUserIds != null && !targetUserIds.isEmpty()) {
      List<ShareRecipient> recipients = new java.util.ArrayList<>(targetUserIds.size());
      for (String targetUserId : targetUserIds) {
        ShareRecipient recipient =
            ShareRecipient.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
                .shareId(saved.getId())
                .recipientType("USER")
                .recipientId(targetUserId)
                .status("ACTIVE")
                .deleted(0)
                .build();
        recipient.setCreatedBy(userId);
        recipient.setUpdatedBy(userId);
        recipients.add(recipient);
      }
      shareRecipientRepository.saveBatch(recipients);
      log.info(
          "[ShareLinkDomainService] 创建定向分享: fileNodeId={}, shareCode={}, recipients={}",
          fileNodeId,
          shareCode,
          targetUserIds.size());
    }

    log.info(
        "[ShareLinkDomainService] 创建分享: fileNodeId={}, shareCode={}, target={}",
        fileNodeId,
        shareCode,
        shareTargetType);
    return saved;
  }

  /**
   * 验证分享链接访问（含防暴力破解）。
   *
   * @param shareCode  分享码
   * @param extractCode 提取码
   * @param password   访问密码
   * @return 分享链接实体
   * @throws BusinessException 链接不存在/过期/访问受限/密码错误/提取码错误/已锁定时抛出
   */
  public ShareLink verifyAccess(String shareCode, String extractCode, String password) {
    // 防暴力破解——检查失败次数是否超限
    String failKey = KEY_SHARE_FAIL + shareCode;
    String failCountStr = stringOps.get(failKey, String.class);
    if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_FAIL_COUNT) {
      log.warn("[ShareLinkDomainService] 分享链接已被临时锁定: shareCode={}", shareCode);
      throw new BusinessException(NextwikiExceptionCode.SHARE_LOCKED);
    }

    ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode);
    if (shareLink == null) {
      throw new BusinessException(NextwikiExceptionCode.SHARE_NOT_FOUND);
    }

    ShareStatus currentStatus = ShareStatus.fromCode(shareLink.getStatus());
    if (currentStatus == null || currentStatus.isTerminal()) {
      throw new BusinessException(NextwikiExceptionCode.SHARE_EXPIRED);
    }

    if (shareLink.getExpireTime() != null
        && shareLink.getExpireTime().isBefore(LocalDateTime.now())) {
      if (currentStatus.canTransitTo(ShareStatus.EXPIRED)) {
        shareLink.setStatus(ShareStatus.EXPIRED.getCode());
        shareLinkRepository.update(shareLink);
      }
      throw new BusinessException(NextwikiExceptionCode.SHARE_EXPIRED);
    }

    if (shareLink.getMaxAccessCount() != null
        && shareLink.getAccessCount() != null
        && shareLink.getAccessCount() >= shareLink.getMaxAccessCount()) {
      throw new BusinessException(NextwikiExceptionCode.SHARE_ACCESS_LIMIT);
    }

    boolean verifyFailed = false;

    if (shareLink.getExtractCode() != null && !shareLink.getExtractCode().equals(extractCode)) {
      verifyFailed = true;
    }

    if (!verifyFailed && shareLink.getPassword() != null && !shareLink.getPassword().isEmpty()) {
      if (password == null || !passwordEncoder.matches(password, shareLink.getPassword())) {
        verifyFailed = true;
      }
    }

    if (verifyFailed) {
      // 记录失败次数
      Long failCount = stringOps.incr(failKey, 1);
      if (failCount != null && failCount == 1) {
        stringOps.expire(failKey, Duration.ofMinutes(LOCK_DURATION_MINUTES));
      }
      log.warn("[ShareLinkDomainService] 验证失败: shareCode={}, failCount={}", shareCode, failCount);
      throw new BusinessException(
          shareLink.getExtractCode() != null
              ? NextwikiExceptionCode.SHARE_EXTRACT_CODE_ERROR
              : NextwikiExceptionCode.SHARE_PASSWORD_ERROR);
    }

    // 验证成功，清除失败计数
    stringOps.del(failKey);

    shareLinkRepository.incrementAccessCount(shareLink.getId());

    return shareLink;
  }

  /**
   * 撤销分享链接。
   *
   * @param shareId 分享链接 ID
   * @param userId  操作人 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void revoke(String shareId, String userId) {
    ShareLink shareLink = shareLinkRepository.findById(shareId);
    if (shareLink == null) {
      throw new BusinessException(NextwikiExceptionCode.SHARE_NOT_FOUND);
    }
    shareLinkRepository.revoke(shareId);
    log.info("[ShareLinkDomainService] 撤销分享: shareId={}, userId={}", shareId, userId);
  }

  /**
   * 查询即将到期的分享链接（用于到期提醒）。
   *
   * @param withinHours 多少小时内即将到期
   * @return 即将到期的分享链接列表
   */
  public List<ShareLink> findExpiringShares(int withinHours) {
    return shareLinkRepository.findExpiringShares(withinHours);
  }

  /**
   * 标记分享链接的到期提醒已发送。
   *
   * @param shareId 分享链接 ID
   */
  public void markReminderSent(String shareId) {
    ShareLink shareLink = shareLinkRepository.findById(shareId);
    if (shareLink != null) {
      shareLink.setReminderSent(true);
      shareLinkRepository.update(shareLink);
    }
  }

  /**
   * 查询用户的分享列表。
   *
   * @param userId 用户 ID
   * @return 分享链接列表
   */
  public List<ShareLink> findByUserId(String userId) {
    return shareLinkRepository.findActiveSharesByUserId(userId);
  }

  /**
   * 根据分享码查询分享链接。
   *
   * @param shareCode 分享码
   * @return 分享链接实体，不存在返回 null
   */
  public ShareLink findByShareCode(String shareCode) {
    return shareLinkRepository.findByShareCode(shareCode);
  }

  // ==================== 私有方法 ====================

  /**
   * 生成 4 位数字提取码。
   *
   * @return 提取码字符串
   */
  private String generateExtractCode() {
    int code = (int) (Math.random() * 9000) + 1000;
    return String.valueOf(code);
  }
}
