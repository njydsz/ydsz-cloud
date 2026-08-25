package com.njydsz.nextwiki.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.ShareAccessLogDTO;

/**
 * 分享访问日志领域服务
 *
 * <p>封装分享链接访问日志的核心业务逻辑：日志构建、访问频率计算。
 * 本服务为纯领域逻辑组件，不执行任何数据访问；数据由应用层加载后传入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ShareAccessLogmainService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 构建访问日志条目（纯领域逻辑，不执行持久化）。
   *
   * @param shareId 分享链接 ID
   * @param shareCode 分享码
   * @param fileNodeId 文件节点 ID
   * @param visitorId 访问者 ID（可为 null，匿名访问为 null）
   * @param visitorIp 访问者 IP
   * @param visitorAgent 访问者 User-Agent
   * @param accessType 访问类型（VIEW / DOWNLOAD）
   * @param accessStatus 访问状态（SUCCESS / FAILED）
   * @param failReason 失败原因（访问成功时为 null）
   * @return 构建完成的 {@link ShareAccessLogDTO} 实例（未持久化）
   */
  public ShareAccessLogDTO buildAccessLog(
      String shareId,
      String shareCode,
      String fileNodeId,
      String visitorId,
      String visitorIp,
      String visitorAgent,
      String accessType,
      String accessStatus,
      String failReason) {
    ShareAccessLogDTO logEntry = new ShareAccessLogDTO();
    logEntry.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    logEntry.setShareId(shareId);
    logEntry.setShareCode(shareCode);
    logEntry.setFileNodeId(fileNodeId);
    logEntry.setVisitorId(visitorId);
    logEntry.setVisitorIp(visitorIp);
    logEntry.setUserAgent(visitorAgent);
    logEntry.setAccessType(accessType);
    logEntry.setAccessStatus(accessStatus);
    logEntry.setFailReason(failReason);
    return logEntry;
  }

  /**
   * 判断访问频率是否异常（纯领域逻辑）。
   *
   * <p>简单规则：短时间内访问次数超过阈值视为异常。
   *
   * @param recentAccessCount 最近时间段内的访问次数
   * @param threshold 阈值
   * @return {@code true} 表示访问频率异常
   */
  public boolean isAbnormalAccessFrequency(int recentAccessCount, int threshold) {
    return recentAccessCount > threshold;
  }
}
