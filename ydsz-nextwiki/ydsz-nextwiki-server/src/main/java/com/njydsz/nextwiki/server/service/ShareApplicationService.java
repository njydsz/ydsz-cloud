package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.dto.ShareAccessLogDTO;
import com.njydsz.nextwiki.domain.dto.ShareLinkDTO;
import com.njydsz.nextwiki.domain.dto.ShareRecipientDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.ShareAccessLogRepository;
import com.njydsz.nextwiki.domain.repository.ShareLinkRepository;
import com.njydsz.nextwiki.domain.repository.ShareRecipientRepository;
import com.njydsz.nextwiki.domain.service.ShareAccessLogDomainService;
import com.njydsz.nextwiki.domain.service.ShareLinkDomainService;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.ShareAccessLogVO;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;
import com.njydsz.nextwiki.domain.vo.ShareRecipientVO;

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
  private final ShareLinkRepository shareLinkRepository;
  private final ShareAccessLogRepository shareAccessLogRepository;
  private final ShareRecipientRepository shareRecipientRepository;
  private final FilePermissionService filePermissionService;
  private final FileNodeRepository fileNodeRepository;

  /**
   * 创建文件分享链接。
   *
   * @param fileNodeId 文件节点 ID
   * @param shareType 分享类型（PUBLIC/PASSWORD/LIMITED）
   * @param password 访问密码（可为空，PUBLIC 类型忽略）
   * @param expireTime 过期时间（可为空表示永不过期）
   * @param maxAccessCount 最大访问次数（可为空表示不限）
   * @param userId 创建者 ID
   * @return 分享链接 VO
   * @throws 由 {@link ShareLinkDomainService} 在节点不存在/无权限时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  public ShareLinkVO createShare(
      String fileNodeId,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      String userId) {
    FileNodeVO node = fileNodeRepository.findById(fileNodeId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("fileNodeId", fileNodeId));
    filePermissionService.checkShare(fileNodeId, userId);

    ShareLinkDomainService.CreateShareResult result = shareLinkDomainService.createShare(
        node, shareType, password, expireTime, maxAccessCount, userId);
    ShareLinkVO saved = shareLinkRepository.save(result.shareLink());
    if (result.recipients() != null && !result.recipients().isEmpty()) {
      shareRecipientRepository.saveBatch(result.recipients());
    }
    return saved;
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
   * @return 分享链接 VO
   */
  @Transactional(rollbackFor = Exception.class)
  public ShareLinkVO createShareWithTargets(
      String fileNodeId,
      String shareType,
      String password,
      LocalDateTime expireTime,
      Integer maxAccessCount,
      List<String> targetUserIds,
      String title,
      String userId) {
    FileNodeVO node = fileNodeRepository.findById(fileNodeId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("fileNodeId", fileNodeId));
    filePermissionService.checkShare(fileNodeId, userId);

    ShareLinkDomainService.CreateShareResult result = shareLinkDomainService.createShare(
        node, shareType, password, expireTime, maxAccessCount, targetUserIds, title, userId);
    ShareLinkVO saved = shareLinkRepository.save(result.shareLink());
    if (result.recipients() != null && !result.recipients().isEmpty()) {
      shareRecipientRepository.saveBatch(result.recipients());
    }
    return saved;
  }

  /**
   * 验证分享链接访问权限。
   *
   * <p>校验分享码、提取码（如有）、访问密码，并判断过期时间与访问次数上限。
   *
   * @param shareCode 分享码（分享链接唯一标识）
   * @param extractCode 提取码（可为空；与分享码配合用于 LIMITED 类型）
   * @param password 访问密码（可为空；PUBLIC 类型忽略）
   * @return 分享链接 VO；任一校验不通过返回 {@code null}
   * @complexity O(1)（一次分享记录查询 + 内存校验）
   * @note 无事务边界；验证失败时抛业务异常
   */
  public ShareLinkVO verifyAccess(String shareCode, String extractCode, String password) {
    ShareLinkVO vo = shareLinkRepository.findByShareCode(shareCode)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.SHARE_NOT_FOUND).data("shareCode", shareCode));
    ShareLinkDTO dto = shareLinkToDTO(vo);
    ShareLinkDTO verified = shareLinkDomainService.verifyAccess(dto, extractCode, password);
    return shareLinkRepository.save(verified);
  }

  /**
   * 撤销分享链接（使分享码立即失效）。
   *
   * @param shareId 分享 ID
   * @param userId 操作者 ID（需具备该分享的撤销权限）
   * @throws 由 {@link ShareLinkDomainService} 在分享不存在/无权限时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   */
  @Transactional(rollbackFor = Exception.class)
  public void revoke(String shareId, String userId) {
    ShareLinkVO vo = shareLinkRepository.findById(shareId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.SHARE_NOT_FOUND).data("shareId", shareId));
    ShareLinkDTO dto = shareLinkToDTO(vo);
    shareLinkDomainService.revoke(dto, userId);
    shareLinkRepository.update(dto);
  }

  /**
   * 查询某用户创建的全部分享链接列表。
   *
   * @param userId 用户 ID
   * @return 分享链接 VO 列表（可能为空，非 {@code null}）
   */
  public List<ShareLinkVO> findByUserId(String userId) {
    return shareLinkRepository.findActiveSharesByUserId(userId);
  }

  /**
   * 查询分享链接的访问日志。
   *
   * @param shareId 分享链接 ID
   * @param limit 返回条数限制
   * @return 访问日志 VO 列表
   */
  public List<ShareAccessLogVO> getAccessLogs(String shareId, int limit) {
    return shareAccessLogRepository.findByShareId(shareId, limit);
  }

  /**
   * 查询分享链接的目标用户列表。
   *
   * @param shareId 分享链接 ID
   * @return 目标用户 VO 列表
   */
  public List<ShareRecipientVO> getRecipients(String shareId) {
    return shareRecipientRepository.findByShareId(shareId);
  }

  /**
   * 查询用户收到的分享列表。
   *
   * @param userId 用户 ID
   * @return 分享接收记录 VO 列表
   */
  public List<ShareRecipientVO> getReceivedShares(String userId) {
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
      ShareAccessLogDTO accessLog =
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

  // ==================== 私有方法 ====================

  /** ShareLinkVO → ShareLinkDTO 转换（用于调用领域服务） */
  private ShareLinkDTO shareLinkToDTO(ShareLinkVO vo) {
    return ShareLinkDTO.builder()
        .id(vo.getId())
        .fileNodeId(vo.getFileNodeId())
        .shareCode(vo.getShareCode())
        .extractCode(vo.getExtractCode())
        .shareType(vo.getShareType())
        .password(vo.getPassword())
        .expireTime(vo.getExpireTime())
        .maxAccessCount(vo.getMaxAccessCount())
        .accessCount(vo.getAccessCount())
        .status(vo.getStatus())
        .shareTargetType(vo.getShareTargetType())
        .title(vo.getTitle())
        .reminderSent(vo.getReminderSent())
        .createdBy(vo.getCreatedBy())
        .updatedBy(vo.getUpdatedBy())
        .createdAt(vo.getCreatedAt())
        .build();
  }
}
