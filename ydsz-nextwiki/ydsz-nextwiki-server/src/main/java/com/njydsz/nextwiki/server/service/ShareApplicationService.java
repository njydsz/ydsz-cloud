package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.entity.ShareAccessLog;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.entity.ShareRecipient;
import com.njydsz.nextwiki.domain.service.ShareAccessLogDomainService;
import com.njydsz.nextwiki.domain.service.ShareLinkDomainService;
import com.njydsz.nextwiki.infra.repository.ShareAccessLogRepository;
import com.njydsz.nextwiki.infra.repository.ShareRecipientRepository;

/**
 * 分享应用服务。
 *
 * <p>创建/校验/撤销分享链接，管理访问日志与目标用户。
 *
 * <p>按职责拆分为 {@link ShareLinkDomainService}（链接生命周期）和 {@link
 * ShareAccessLogDomainService}（日志构造）两个领域服务。数据访问由本层通过 Repository 接口完成。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareApplicationService {

  private final ShareLinkDomainService shareLinkDomainService;
  private final ShareAccessLogDomainService shareAccessLogDomainService;
  private final ShareAccessLogRepository shareAccessLogRepository;
  private final ShareRecipientRepository shareRecipientRepository;

  /**
   * 创建文件分享链接。
   *
   * @param fileNodeId 文件节点 ID
   * @param shareType 分享类型（PUBLIC/PASSWORD/LIMITED）
   * @param password 访问密码（可为空，PUBLIC 类型忽略）
   * @param expireTime 过期时间（可为空表示永不过期）
   * @param maxAccessCount 最大访问次数（可为空表示不限）
   * @param userId 创建者 ID
   * @return 分享链接实体 {@link ShareLink}
   * @throws 由 {@link ShareLinkDomainService} 在节点不存在/无权限时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次分享记录写入）
   * @note 委托 {@link ShareLinkDomainService} 实现；创建后通常由事件/通知触达被分享方
   */
  @Transactional(rollbackFor = Exception.class)
  public ShareLink createShare(
      String fileNodeId,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      String userId) {
    return shareLinkDomainService.createShare(
        fileNodeId, shareType, password, expireTime, maxAccessCount, userId);
  }

  /**
   * 创建定向分享链接（支持指定目标用户和自定义标题）。
   *
   * @param fileNodeId 文件节点 ID
   * @param shareType 分享类型（view/download/edit）
   * @param password 访问密码（可为空）
   * @param expireTime 过期时间（可为空表示永久）
   * @param maxAccessCount 最大访问次数（可为空表示不限）
   * @param targetUserIds 目标用户 ID 列表（可为空；非空时创建定向分享）
   * @param title 分享标题（可为空）
   * @param userId 创建者 ID
   * @return 分享链接实体 {@link ShareLink}
   */
  @Transactional(rollbackFor = Exception.class)
  public ShareLink createShareWithTargets(
      String fileNodeId,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      List<String> targetUserIds,
      String title,
      String userId) {
    return shareLinkDomainService.createShare(
        fileNodeId, shareType, password, expireTime, maxAccessCount, targetUserIds, title, userId);
  }

  /**
   * 验证分享链接访问权限。
   *
   * <p>校验分享码、提取码（如有）、访问密码，并判断过期时间与访问次数上限。
   *
   * @param shareCode 分享码（分享链接唯一标识）
   * @param extractCode 提取码（可为空；与分享码配合用于 LIMITED 类型）
   * @param password 访问密码（可为空；PUBLIC 类型忽略）
   * @return 分享链接实体 {@link ShareLink}；任一校验不通过返回 {@code null}
   * @complexity O(1)（一次分享记录查询 + 内存校验）
   * @note 无事务边界；验证失败时抛业务异常
   */
  public ShareLink verifyAccess(String shareCode, String extractCode, String password) {
    return shareLinkDomainService.verifyAccess(shareCode, extractCode, password);
  }

  /**
   * 撤销分享链接（使分享码立即失效）。
   *
   * @param shareId 分享 ID
   * @param userId 操作者 ID（需具备该分享的撤销权限）
   * @return 无返回值
   * @throws 由 {@link ShareLinkDomainService} 在分享不存在/无权限时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次分享状态更新）
   * @note 撤销后原分享码不可再访问；已发出的访问不再有效
   */
  @Transactional(rollbackFor = Exception.class)
  public void revoke(String shareId, String userId) {
    shareLinkDomainService.revoke(shareId, userId);
  }

  /**
   * 查询某用户创建的全部分享链接列表。
   *
   * @param userId 用户 ID
   * @return 分享链接列表 {@link ShareLink}（可能为空，非 {@code null}）
   * @complexity O(1)（一次按用户查询）
   * @note 只读，无事务边界
   */
  public List<ShareLink> findByUserId(String userId) {
    return shareLinkDomainService.findByUserId(userId);
  }

  /**
   * 查询分享链接的访问日志。
   *
   * @param shareId 分享链接 ID
   * @param limit 返回条数限制
   * @return 访问日志列表
   */
  public List<ShareAccessLog> getAccessLogs(String shareId, int limit) {
    return shareAccessLogRepository.findByShareId(shareId, limit);
  }

  /**
   * 查询分享链接的目标用户列表。
   *
   * @param shareId 分享链接 ID
   * @return 目标用户列表
   */
  public List<ShareRecipient> getRecipients(String shareId) {
    return shareRecipientRepository.findByShareId(shareId);
  }

  /**
   * 查询用户收到的分享列表。
   *
   * @param userId 用户 ID
   * @return 分享接收记录列表
   */
  public List<ShareRecipient> getReceivedShares(String userId) {
    return shareRecipientRepository.findByRecipientId(userId);
  }

  /**
   * 记录分享链接访问日志。
   *
   * <p><b>容错设计：</b>日志记录失败时捕获异常并记录 warn 日志，
   * 不影响主业务流程（如文件下载、预览等）。
   *
   * @param shareId 分享链接 ID
   * @param shareCode 分享码
   * @param fileNodeId 文件节点 ID
   * @param visitorId 访问者 ID（可为空）
   * @param visitorIp 访问者 IP
   * @param userAgent User-Agent
   * @param accessType 访问类型
   * @param status 访问状态
   * @param failReason 失败原因
   */
  public void recordAccessLog(
      String shareId,
      String shareCode,
      String fileNodeId,
      String visitorId,
      String visitorIp,
      String userAgent,
      String accessType,
      String status,
      String failReason) {
    try {
      ShareAccessLog accessLog =
          shareAccessLogDomainService.buildAccessLog(
              shareId, shareCode, fileNodeId, visitorId, visitorIp, userAgent, accessType, status,
              failReason);
      shareAccessLogRepository.save(accessLog);
    } catch (Exception e) {
      // 访问日志记录失败不应影响主流程
      log.warn(
          "[ShareApplicationService] 记录访问日志失败: shareCode={}, error={}",
          shareCode,
          e.getMessage());
    }
  }
}
