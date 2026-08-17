package com.njydsz.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.entity.ShareAccessLog;
import com.njydsz.nextwiki.domain.entity.ShareRecipient;
import com.njydsz.nextwiki.domain.repository.ShareAccessLogRepository;
import com.njydsz.nextwiki.domain.repository.ShareRecipientRepository;

/**
 * 分享访问日志领域服务。
 *
 * <p>管理分享链接的访问日志记录与查询，以及分享目标用户（定向分享）的查询。
 *
 * <p><b>容错策略：</b>访问日志记录失败不影响主流程（捕获异常仅记录 warn 日志），
 * 确保日志问题不会阻断核心业务。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareAccessLogDomainService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final ShareAccessLogRepository shareAccessLogRepository;
  private final ShareRecipientRepository shareRecipientRepository;

  /**
   * 记录分享链接访问日志。
   *
   * <p><b>容错设计：</b>日志记录失败时捕获异常并记录 warn 日志，
   * 不影响主业务流程（如文件下载、预览等）。
   *
   * @param shareId     分享链接 ID
   * @param shareCode   分享码
   * @param fileNodeId  文件节点 ID
   * @param visitorId   访问者 ID（可为空）
   * @param visitorIp   访问者 IP
   * @param userAgent   User-Agent
   * @param accessType  访问类型（VIEW/DOWNLOAD/EDIT）
   * @param status      访问状态（SUCCESS/FAIL）
   * @param failReason  失败原因
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
          ShareAccessLog.builder()
              .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
              .shareId(shareId)
              .shareCode(shareCode)
              .fileNodeId(fileNodeId)
              .visitorId(visitorId)
              .visitorIp(visitorIp)
              .userAgent(userAgent)
              .accessType(accessType)
              .accessStatus(status)
              .failReason(failReason)
              .accessTime(LocalDateTime.now())
              .deleted(0)
              .build();
      shareAccessLogRepository.save(accessLog);
    } catch (Exception e) {
      // 访问日志记录失败不应影响主流程
      log.warn(
          "[ShareAccessLogDomainService] 记录访问日志失败: shareCode={}, error={}",
          shareCode,
          e.getMessage());
    }
  }

  /**
   * 查询分享链接的访问日志。
   *
   * @param shareId 分享链接 ID
   * @param limit   返回条数限制
   * @return 访问日志列表
   */
  public List<ShareAccessLog> getAccessLogs(String shareId, int limit) {
    return shareAccessLogRepository.findByShareId(shareId, limit);
  }

  /**
   * 查询分享链接的目标用户列表（定向分享）。
   *
   * @param shareId 分享链接 ID
   * @return 目标用户列表
   */
  public List<ShareRecipient> getRecipients(String shareId) {
    return shareRecipientRepository.findByShareId(shareId);
  }

  /**
   * 查询用户作为接收者的分享列表（定向分享）。
   *
   * @param recipientId 接收者用户 ID
   * @return 分享接收记录列表
   */
  public List<ShareRecipient> getReceivedShares(String recipientId) {
    return shareRecipientRepository.findByRecipientId(recipientId);
  }
}
