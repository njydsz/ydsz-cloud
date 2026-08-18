package com.njydsz.nextwiki.domain.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.dto.ShareLinkDTO;
import com.njydsz.nextwiki.domain.dto.ShareRecipientDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiEnums.ShareStatus;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

/**
 * 分享链接领域服务。
 *
 * <p>管理分享链接的生命周期：创建、验证访问、撤销、到期提醒。
 *
 * <p><b>防暴力破解：</b>通过 Redis 记录连续失败次数，超过阈值临时锁定。
 *
 * <p><b>设计原则：</b>本服务仅包含纯领域逻辑，不直接依赖 Repository 接口。数据访问由 server 层负责，
 * 所需数据通过方法参数传入。
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

  private final RedisStringOps stringOps;

  private final BCryptPasswordEncoder passwordEncoder;

  /** 防暴力破解 Redis Key 前缀 */
  private static final String KEY_SHARE_FAIL = "nextwiki:share:fail:";

  /** 最大失败次数 */
  private static final int MAX_FAIL_COUNT = 5;

  /** 锁定时长（分钟） */
  private static final long LOCK_DURATION_MINUTES = 30;

  // ==================== 创建分享 ====================

  /**
   * 创建分享链接（基础版，公开分享）。
   *
   * @param node           文件节点 VO（由 server 层查询后传入）
   * @param shareType      分享类型（view/download/edit）
   * @param password       访问密码（可为空）
   * @param expireTime     过期时间（可为空）
   * @param maxAccessCount 最大访问次数（可为空）
   * @param userId         创建者 ID
   * @return 分享链接实体与接收者列表
   */
  public CreateShareResult createShare(
      FileNodeVO node,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      String userId) {
    return createShare(node, shareType, password, expireTime, maxAccessCount, null, null, userId);
  }

  /**
   * 创建分享链接（增强版，支持定向分享和自定义标题）。
   *
   * @param node           文件节点 VO（由 server 层查询后传入）
   * @param shareType      分享类型（view/download/edit）
   * @param password       访问密码（可为空）
   * @param expireTime     过期时间（可为空）
   * @param maxAccessCount 最大访问次数（可为空）
   * @param targetUserIds  目标用户 ID 列表（定向分享，可为空）
   * @param title          分享标题（可为空）
   * @param userId         创建者 ID
   * @return 分享链接实体与接收者列表
   */
  public CreateShareResult createShare(
      FileNodeVO node,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      List<String> targetUserIds,
      String title,
      String userId) {
    String fileNodeId = node.getId();

    // 生成分享码和提取码
    String shareCode = String.valueOf(snowflakeIdGenerator.nextId());
    String extractCode = generateExtractCode();

    // 确定分享目标类型
    String shareTargetType =
        (targetUserIds != null && !targetUserIds.isEmpty()) ? "USER" : "PUBLIC";

    ShareLinkDTO shareLink =
        ShareLinkDTO.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
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
            .build();

    shareLink.setCreatedBy(userId);
    shareLink.setUpdatedBy(userId);

    // 处理定向分享目标用户
    List<ShareRecipientDTO> recipients = new ArrayList<>();
    if (targetUserIds != null && !targetUserIds.isEmpty()) {
      for (String targetUserId : targetUserIds) {
        ShareRecipientDTO recipient =
            ShareRecipientDTO.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()))
                .shareId(shareLink.getId())
                .recipientType("USER")
                .recipientId(targetUserId)
                .status("ACTIVE")
                .build();
        recipient.setCreatedBy(userId);
        recipient.setUpdatedBy(userId);
        recipients.add(recipient);
      }
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
    return new CreateShareResult(shareLink, recipients);
  }

  // ==================== 验证访问 ====================

  /**
   * 验证分享链接访问（含防暴力破解）。
   *
   * <p>注意：本方法仅执行领域验证逻辑，不执行持久化操作。若状态变更为 EXPIRED，调用方（server 层）
   * 应通过 Repository 更新 DTO。
   *
   * @param shareLink  分享链接 DTO（由 server 层查询后传入）
   * @param extractCode 提取码
   * @param password   访问密码
   * @return 验证通过的分享链接 DTO（状态可能已更新为 EXPIRED）
   * @throws BusinessException 链接不存在/过期/访问受限/密码错误/提取码错误/已锁定时抛出
   */
  public ShareLinkDTO verifyAccess(ShareLinkDTO shareLink, String extractCode, String password) {
    String shareCode = shareLink.getShareCode();

    // 防暴力破解——检查失败次数是否超限
    String failKey = KEY_SHARE_FAIL + shareCode;
    String failCountStr = stringOps.get(failKey, String.class);
    if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_FAIL_COUNT) {
      log.warn("[ShareLinkDomainService] 分享链接已被临时锁定: shareCode={}", shareCode);
      throw new BusinessException(NextwikiExceptionCode.SHARE_LOCKED);
    }

    ShareStatus currentStatus = ShareStatus.fromCode(shareLink.getStatus());
    if (currentStatus == null || currentStatus.isTerminal()) {
      throw new BusinessException(NextwikiExceptionCode.SHARE_EXPIRED);
    }

    if (shareLink.getExpireTime() != null
        && shareLink.getExpireTime().isBefore(LocalDateTime.now())) {
      if (currentStatus.canTransitTo(ShareStatus.EXPIRED)) {
        shareLink.setStatus(ShareStatus.EXPIRED.getCode());
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

    return shareLink;
  }

  // ==================== 撤销分享 ====================

  /**
   * 撤销分享链接。
   *
   * <p>注意：本方法仅修改 DTO 状态，持久化由 server 层负责。
   *
   * @param shareLink 分享链接 DTO（由 server 层查询后传入）
   * @param userId    操作人 ID
   */
  public void revoke(ShareLinkDTO shareLink, String userId) {
    shareLink.setStatus(ShareStatus.REVOKED.getCode());
    shareLink.setUpdatedBy(userId);
    log.info("[ShareLinkDomainService] 撤销分享: shareId={}, userId={}", shareLink.getId(), userId);
  }

  // ==================== 到期提醒 ====================

  /**
   * 从给定列表中筛选即将到期的分享链接（用于到期提醒）。
   *
   * @param shareLinks 分享链接列表（由 server 层查询后传入）
   * @param withinHours 多少小时内即将到期
   * @return 即将到期的分享链接列表
   */
  public List<ShareLinkDTO> findExpiringShares(List<ShareLinkDTO> shareLinks, int withinHours) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime threshold = now.plusHours(withinHours);
    return shareLinks.stream()
        .filter(link -> ShareStatus.ACTIVE.getCode().equals(link.getStatus()))
        .filter(link -> link.getExpireTime() != null)
        .filter(link -> !link.getExpireTime().isBefore(now))
        .filter(link -> link.getExpireTime().isBefore(threshold))
        .filter(link -> link.getReminderSent() == null || !link.getReminderSent())
        .collect(Collectors.toList());
  }

  /**
   * 标记分享链接的到期提醒已发送。
   *
   * <p>注意：本方法仅修改实体状态，持久化由 server 层负责。
   *
   * @param ShareLinkDO 分享链接实体（由 server 层查询后传入）
   */
  public void markReminderSent(ShareLinkDTO shareLink) {
    shareLink.setReminderSent(true);
  }

  // ==================== 查询过滤 ====================

  /**
   * 从给定列表中筛选用户的有效分享链接。
   *
   * @param shareLinks 分享链接列表（由 server 层查询后传入）
   * @param userId     用户 ID
   * @return 该用户的有效分享链接列表
   */
  public List<ShareLinkDTO> findByUserId(List<ShareLinkDTO> shareLinks, String userId) {
    return shareLinks.stream()
        .filter(link -> ShareStatus.ACTIVE.getCode().equals(link.getStatus()))
        .filter(link -> userId.equals(link.getCreatedBy()))
        .collect(Collectors.toList());
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

  // ==================== 内部记录 ====================

  /**
   * 创建分享链接的结果。
   *
   * @param ShareLinkDO  分享链接实体
   * @param recipients 定向分享接收者列表（公开分享时为空列表）
   */
  public record CreateShareResult(ShareLinkDTO shareLink, List<ShareRecipientDTO> recipients) {}
}
