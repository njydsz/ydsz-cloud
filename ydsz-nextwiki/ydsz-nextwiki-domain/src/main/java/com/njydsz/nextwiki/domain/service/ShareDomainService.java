package com.njydsz.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.domain.entity.FileAcl;
import com.njydsz.nextwiki.domain.entity.ShareAccessLog;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.entity.ShareRecipient;

/**
 * NextWiki 分享领域服务（已废弃）。
 *
 * <p>该类已按单一职责原则拆分为以下三个独立领域服务：
 *
 * <ul>
 *   <li>{@link ShareLinkDomainService} — 分享链接创建、验证、撤销、到期管理
 *   <li>{@link FilePermissionDomainService} — 文件 ACL 权限授予与校验
 *   <li>{@link ShareAccessLogDomainService} — 分享访问日志记录与查询
 * </ul>
 *
 * <p>保留本类的唯一原因是为平滑过渡：调用方更新依赖期间调用本类方法将抛出 {@link
 * UnsupportedOperationException}，提示迁移至新类。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 已拆分为 {@link ShareLinkDomainService}、{@link FilePermissionDomainService}、{@link
 *     ShareAccessLogDomainService}，请使用新类替换
 */
@Slf4j
@Service
@Deprecated
public class ShareDomainService {

  /**
   * @deprecated 使用 {@link ShareLinkDomainService#createShare(String, String, String,
   *     LocalDateTime, Integer, String)} 替代
   */
  @Deprecated
  public ShareLink createShare(
      String fileNodeId,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      String userId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareLinkDomainService#createShare");
  }

  /**
   * @deprecated 使用 {@link ShareLinkDomainService} 八参数 {@code createShare} 替代
   */
  @Deprecated
  public ShareLink createShare(
      String fileNodeId,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      List<String> targetUserIds,
      String title,
      String userId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareLinkDomainService#createShare");
  }

  /**
   * @deprecated 使用 {@link ShareLinkDomainService#verifyAccess(String, String, String)} 替代
   */
  @Deprecated
  public ShareLink verifyAccess(String shareCode, String extractCode, String password) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareLinkDomainService#verifyAccess");
  }

  /**
   * @deprecated 使用 {@link ShareAccessLogDomainService#recordAccessLog} 替代
   */
  @Deprecated
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
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareAccessLogDomainService#recordAccessLog");
  }

  /**
   * @deprecated 使用 {@link ShareLinkDomainService#findExpiringShares(int)} 替代
   */
  @Deprecated
  public List<ShareLink> findExpiringShares(int withinHours) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareLinkDomainService#findExpiringShares");
  }

  /**
   * @deprecated 使用 {@link ShareLinkDomainService#markReminderSent(String)} 替代
   */
  @Deprecated
  public void markReminderSent(String shareId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareLinkDomainService#markReminderSent");
  }

  /**
   * @deprecated 使用 {@link ShareLinkDomainService#revoke(String, String)} 替代
   */
  @Deprecated
  public void revoke(String shareId, String userId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareLinkDomainService#revoke");
  }

  /**
   * @deprecated 使用 {@link FilePermissionDomainService#grantPermission} 替代
   */
  @Deprecated
  public FileAcl grantPermission(
      String fileNodeId, String granteeType, String granteeId, int permissionMask, String userId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 FilePermissionDomainService#grantPermission");
  }

  /**
   * @deprecated 使用 {@link FilePermissionDomainService#checkPermission} 替代
   */
  @Deprecated
  public boolean checkPermission(
      String fileNodeId, String userId, List<String> roleIds, int permission) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 FilePermissionDomainService#checkPermission");
  }

  /**
   * @deprecated 使用 {@link FilePermissionDomainService#findEffectiveAcls} 替代
   */
  @Deprecated
  public List<FileAcl> checkPermissionAcls(String fileNodeId, String userId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 FilePermissionDomainService#findEffectiveAcls");
  }

  /**
   * @deprecated 使用 {@link ShareLinkDomainService#findByUserId(String)} 替代
   */
  @Deprecated
  public List<ShareLink> findByUserId(String userId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareLinkDomainService#findByUserId");
  }

  /**
   * @deprecated 使用 {@link ShareAccessLogDomainService#getAccessLogs} 替代
   */
  @Deprecated
  public List<ShareAccessLog> getAccessLogs(String shareId, int limit) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareAccessLogDomainService#getAccessLogs");
  }

  /**
   * @deprecated 使用 {@link ShareAccessLogDomainService#getRecipients} 替代
   */
  @Deprecated
  public List<ShareRecipient> getRecipients(String shareId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareAccessLogDomainService#getRecipients");
  }

  /**
   * @deprecated 使用 {@link ShareAccessLogDomainService#getReceivedShares} 替代
   */
  @Deprecated
  public List<ShareRecipient> getReceivedShares(String recipientId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 ShareAccessLogDomainService#getReceivedShares");
  }

  /**
   * @deprecated 使用 {@link FilePermissionDomainService#setOwner} 替代
   */
  @Deprecated
  public FileAcl setOwner(String fileNodeId, String userId) {
    throw new UnsupportedOperationException(
        "ShareDomainService 已废弃，请使用 FilePermissionDomainService#setOwner");
  }
}
