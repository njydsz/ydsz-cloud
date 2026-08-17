package com.njydsz.nextwiki.domain.service;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.infra.entity.ShareAccessLogDO;

/**
 * 分享访问日志领域服务。
 *
 * <p>管理分享链接的访问日志记录，负责构造访问日志实体（含分布式 ID 生成与默认值填充）。
 *
 * <p><b>设计原则：</b>本服务仅包含纯领域逻辑，不直接依赖 Repository 接口。数据持久化由 server 层负责，
 * 构造完成的实体通过方法返回值传递给 server 层。
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

  /**
   * 构造分享链接访问日志实体。
   *
   * <p>根据传入的访问信息生成完整的 {@link ShareAccessLogDO} 实体，包含分布式 ID、访问时间、删除标记等
   * 领域默认值。持久化由 server 层负责。
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
   * @return 构造完成的访问日志实体（未持久化）
   */
  public ShareAccessLogDO buildAccessLog(
      String shareId,
      String shareCode,
      String fileNodeId,
      String visitorId,
      String visitorIp,
      String userAgent,
      String accessType,
      String status,
      String failReason) {
    ShareAccessLogDO accessLog =
        ShareAccessLogDO.builder()
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
    log.info(
        "[ShareAccessLogDomainService] 构造访问日志: shareCode={}, accessType={}, status={}",
        shareCode,
        accessType,
        status);
    return accessLog;
  }
}
